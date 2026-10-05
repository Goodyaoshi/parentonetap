package com.goodyaoshi.parentonetap.location

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lastFixDataStore by preferencesDataStore(name = "last_fix")

/** 历史成功定位缓存（方案 3.2）：断网/超时时用「上次位置」兜底 */
@Singleton
class LastFixStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun save(fix: Fix) {
        context.lastFixDataStore.edit { p ->
            p[KEY_LAT] = fix.lat
            p[KEY_LNG] = fix.lng
            p[KEY_ACC] = fix.accuracyMeters
            p[KEY_PROVIDER] = fix.provider
            p[KEY_TIME] = fix.timestampMs
        }
    }

    suspend fun load(): Fix? {
        val p = context.lastFixDataStore.data.first()
        val lat = p[KEY_LAT] ?: return null
        val lng = p[KEY_LNG] ?: return null
        return Fix(
            lat = lat,
            lng = lng,
            accuracyMeters = p[KEY_ACC] ?: 0f,
            provider = p[KEY_PROVIDER] ?: "",
            timestampMs = p[KEY_TIME] ?: 0L
        )
    }

    private companion object {
        val KEY_LAT = doublePreferencesKey("lat")
        val KEY_LNG = doublePreferencesKey("lng")
        val KEY_ACC = floatPreferencesKey("accuracy")
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_TIME = longPreferencesKey("time")
    }
}
