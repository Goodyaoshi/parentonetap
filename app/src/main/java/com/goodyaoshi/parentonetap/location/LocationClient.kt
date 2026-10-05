package com.goodyaoshi.parentonetap.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.goodyaoshi.parentonetap.core.perm.PermissionGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 定位客户端（方案 3.2）：LocationManager 方案，不用 play-services（国产机无 GMS）。
 * 策略：
 * 1) 优先 network provider 的 lastKnownLocation，5 分钟内视为可用（秒出，国产 ROM 已是 GCJ-02）
 * 2) 否则同时向 network/gps 请求单次定位，超时（默认 10s，可配）取最先返回的结果
 * 3) 结果带 provider 标记与精度（米）；GPS 结果做 WGS84→GCJ-02 转换，
 *    network 结果默认视为已是 GCJ-02
 */
@Singleton
class LocationClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lm: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun getFix(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocateOutcome =
        withContext(Dispatchers.Main) {
            if (!PermissionGate.hasLocationPermission(context)) {
                return@withContext LocateOutcome.Failure(LocateOutcome.Reason.NO_PERMISSION)
            }
            val providers = enabledProviders()
            if (providers.isEmpty()) {
                return@withContext LocateOutcome.Failure(LocateOutcome.Reason.PROVIDERS_OFF)
            }

            // 1) network lastKnownLocation 秒出路径
            if (LocationManager.NETWORK_PROVIDER in providers) {
                val last = runCatching {
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }.getOrNull()
                if (last != null && isFresh(last)) {
                    return@withContext LocateOutcome.Success(last.toFix(convertFromWgs84 = false))
                }
            }

            // 2) 并发单次定位，取最先返回
            val fix = withTimeoutOrNull(timeoutMs) { awaitFirstFix(providers) }
            if (fix != null) LocateOutcome.Success(fix)
            else LocateOutcome.Failure(LocateOutcome.Reason.TIMEOUT)
        }

    private fun enabledProviders(): List<String> = buildList {
        for (name in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            runCatching { if (lm.isProviderEnabled(name)) add(name) }
        }
    }

    private fun isFresh(location: Location): Boolean =
        System.currentTimeMillis() - location.time <= FRESH_WINDOW_MS

    private fun Location.toFix(convertFromWgs84: Boolean): Fix {
        val gcj = if (convertFromWgs84) {
            Gcj02Converter.toGcj02(latitude, longitude)
        } else {
            latitude to longitude
        }
        return Fix(
            lat = gcj.first,
            lng = gcj.second,
            accuracyMeters = accuracy,
            provider = provider ?: "",
            timestampMs = time
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitFirstFix(providers: List<String>): Fix =
        suspendCancellableCoroutine { cont ->
            val listeners = mutableListOf<LocationListener>()
            val cleanup = {
                listeners.forEach { runCatching { lm.removeUpdates(it) } }
            }
            val listener = LocationListener { location ->
                if (cont.isActive) {
                    cleanup()
                    val convert = location.provider == LocationManager.GPS_PROVIDER
                    cont.resume(location.toFix(convertFromWgs84 = convert))
                }
            }
            providers.forEach { name ->
                runCatching {
                    lm.requestLocationUpdates(name, 0L, 0f, listener, Looper.getMainLooper())
                    listeners.add(listener)
                }
            }
            cont.invokeOnCancellation { cleanup() }
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L // Prompt 5 接配置 locationTimeoutSeconds
        private const val FRESH_WINDOW_MS = 5 * 60 * 1000L
    }
}
