package com.goodyaoshi.parentonetap.core.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentonetap.config.AppConfig
import com.goodyaoshi.parentonetap.config.AppConfigStore
import com.goodyaoshi.parentonetap.location.Fix
import com.goodyaoshi.parentonetap.location.LastFixStore
import com.goodyaoshi.parentonetap.location.LocateOutcome
import com.goodyaoshi.parentonetap.location.LocationClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 首页定位流程状态机（方案 3.2 全屏态） */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationClient: LocationClient,
    private val lastFixStore: LastFixStore,
    configStore: AppConfigStore
) : ViewModel() {

    /** 全局配置（方案 4）：超时秒数、分享文案等从这里读 */
    val config: StateFlow<AppConfig> = configStore.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    /** 引导是否已完成；null = DataStore 尚未加载，UI 先不渲染以免闪引导 */
    val onboardingDone: StateFlow<Boolean?> = configStore.configFlow
        .map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    sealed interface LocateUi {
        data object Idle : LocateUi
        data object Locating : LocateUi
        data class Done(val fix: Fix, val usedLast: Boolean) : LocateUi
        data class Timeout(val last: Fix?) : LocateUi
        data class Error(val reason: LocateOutcome.Reason) : LocateUi
    }

    private val _locateState = MutableStateFlow<LocateUi>(LocateUi.Idle)
    val locateState: StateFlow<LocateUi> = _locateState.asStateFlow()

    private var locateJob: Job? = null

    fun startLocate() {
        if (_locateState.value is LocateUi.Locating) return
        locateJob = viewModelScope.launch {
            _locateState.value = LocateUi.Locating
            val timeout = config.value.locationTimeoutSeconds.coerceIn(3, 120) * 1000L
            when (val outcome = locationClient.getFix(timeout)) {
                is LocateOutcome.Success -> {
                    lastFixStore.save(outcome.fix)
                    // 验收要求：打印定位日志（GCJ-02 坐标 + 精度 + provider）
                    Log.i(
                        TAG,
                        "定位成功: lat=${outcome.fix.lat}, lng=${outcome.fix.lng}, " +
                            "精度=${outcome.fix.accuracyMeters}米, " +
                            "provider=${outcome.fix.provider}, time=${outcome.fix.timestampMs}"
                    )
                    _locateState.value = LocateUi.Done(outcome.fix, usedLast = false)
                }
                is LocateOutcome.Failure -> {
                    Log.w(TAG, "定位失败: ${outcome.reason}")
                    _locateState.value = when (outcome.reason) {
                        LocateOutcome.Reason.TIMEOUT -> LocateUi.Timeout(lastFixStore.load())
                        else -> LocateUi.Error(outcome.reason)
                    }
                }
            }
        }
    }

    /** 超时时用「上次位置」兜底 */
    fun useLast() {
        viewModelScope.launch {
            val fix = lastFixStore.load()
            if (fix == null) {
                _locateState.value = LocateUi.Idle
            } else {
                Log.i(TAG, "使用上次位置: lat=${fix.lat}, lng=${fix.lng}, time=${fix.timestampMs}")
                _locateState.value = LocateUi.Done(fix, usedLast = true)
            }
        }
    }

    fun cancelLocate() {
        locateJob?.cancel()
        _locateState.value = LocateUi.Idle
    }

    /** UI 展示完结果后回 Idle */
    fun consumeResult() {
        if (_locateState.value !is LocateUi.Locating) {
            _locateState.value = LocateUi.Idle
        }
    }

    private companion object {
        const val TAG = "LocationFlow"
    }
}
