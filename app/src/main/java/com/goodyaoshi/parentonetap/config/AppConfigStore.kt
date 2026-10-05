package com.goodyaoshi.parentonetap.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.configDataStore by preferencesDataStore(name = "app_config")

/** 本地配置仓库（方案 4）：保存即写入 DataStore 并全局生效 */
@Singleton
class AppConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val configFlow: Flow<AppConfig> = context.configDataStore.data.map { it.toConfig() }

    suspend fun current(): AppConfig = configFlow.first()

    suspend fun save(config: AppConfig) {
        context.configDataStore.edit { p ->
            val home = config.home
            if (home == null) {
                p.remove(KEY_HOME_LAT)
                p.remove(KEY_HOME_LNG)
                p.remove(KEY_HOME_NAME)
            } else {
                p[KEY_HOME_NAME] = home.name
                p[KEY_HOME_LAT] = home.lat
                p[KEY_HOME_LNG] = home.lng
            }
            p[KEY_CONTACTS] = encodeContacts(config.contacts)
            p[KEY_EXTRA_PLACES] = encodePlaces(config.extraPlaces)
            p[KEY_TAXI_AMAP] = config.taxiAmap
            p[KEY_TAXI_TENCENT] = config.taxiTencent
            p[KEY_TAXI_HOTLINE] = config.taxiHotline
            p[KEY_SHARE_AMAP] = config.shareAmap
            p[KEY_SHARE_BAIDU] = config.shareBaidu
            p[KEY_PLACE_NAME] = config.placeName
            p[KEY_TIMEOUT] = config.locationTimeoutSeconds
            p[KEY_EMERGENCY_INDEX] = config.emergencyContactIndex
            p[KEY_EMERGENCY_CUSTOM] = config.emergencyCustomPhone
            p[KEY_ONBOARDING_DONE] = config.onboardingDone
        }
    }

    private fun Preferences.toConfig(): AppConfig {
        val lat = this[KEY_HOME_LAT]
        val lng = this[KEY_HOME_LNG]
        return AppConfig(
            home = if (lat != null && lng != null) {
                HomePlace(name = this[KEY_HOME_NAME] ?: "家", lat = lat, lng = lng)
            } else {
                null
            },
            contacts = decodeContacts(this[KEY_CONTACTS].orEmpty()),
            extraPlaces = decodePlaces(this[KEY_EXTRA_PLACES].orEmpty()),
            taxiAmap = this[KEY_TAXI_AMAP] ?: true,
            taxiTencent = this[KEY_TAXI_TENCENT] ?: true,
            taxiHotline = this[KEY_TAXI_HOTLINE] ?: true,
            shareAmap = this[KEY_SHARE_AMAP] ?: true,
            shareBaidu = this[KEY_SHARE_BAIDU] ?: false,
            placeName = this[KEY_PLACE_NAME] ?: "爸妈的位置",
            locationTimeoutSeconds = this[KEY_TIMEOUT] ?: 10,
            emergencyContactIndex = this[KEY_EMERGENCY_INDEX] ?: 0,
            emergencyCustomPhone = this[KEY_EMERGENCY_CUSTOM].orEmpty(),
            onboardingDone = this[KEY_ONBOARDING_DONE] ?: false
        )
    }

    /** 联系人序列化：每行「label|phone」，分隔符在保存时清洗掉 */
    private fun encodeContacts(contacts: List<ContactEntry>): String =
        contacts.joinToString("\n") { c ->
            val label = c.label.replace('|', ' ').replace('\n', ' ').trim()
            val phone = c.phone.replace('|', ' ').replace('\n', ' ').trim()
            "$label|$phone"
        }

    private fun decodeContacts(raw: String): List<ContactEntry> =
        raw.split('\n').mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val label = parts.getOrNull(0)?.trim().orEmpty()
            val phone = parts.getOrNull(1)?.trim().orEmpty()
            if (label.isNotEmpty() && phone.isNotEmpty()) ContactEntry(label, phone) else null
        }

    /** 常用地点序列化：每行「name|lat|lng」 */
    private fun encodePlaces(places: List<HomePlace>): String =
        places.joinToString("\n") { p ->
            "${p.name.replace('|', ' ').replace('\n', ' ').trim()}|${p.lat}|${p.lng}"
        }

    private fun decodePlaces(raw: String): List<HomePlace> =
        raw.split('\n').mapNotNull { line ->
            val parts = line.split('|')
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            val lng = parts.getOrNull(2)?.toDoubleOrNull()
            if (name.isNotEmpty() && lat != null && lng != null) HomePlace(name, lat, lng) else null
        }

    private companion object {
        val KEY_HOME_NAME = stringPreferencesKey("home_name")
        val KEY_HOME_LAT = doublePreferencesKey("home_lat")
        val KEY_HOME_LNG = doublePreferencesKey("home_lng")
        val KEY_CONTACTS = stringPreferencesKey("contacts")
        val KEY_EXTRA_PLACES = stringPreferencesKey("extra_places")
        val KEY_TAXI_AMAP = booleanPreferencesKey("taxi_amap")
        val KEY_TAXI_TENCENT = booleanPreferencesKey("taxi_tencent")
        val KEY_TAXI_HOTLINE = booleanPreferencesKey("taxi_hotline")
        val KEY_SHARE_AMAP = booleanPreferencesKey("share_amap")
        val KEY_SHARE_BAIDU = booleanPreferencesKey("share_baidu")
        val KEY_PLACE_NAME = stringPreferencesKey("place_name")
        val KEY_TIMEOUT = intPreferencesKey("location_timeout")
        val KEY_EMERGENCY_INDEX = intPreferencesKey("emergency_index")
        val KEY_EMERGENCY_CUSTOM = stringPreferencesKey("emergency_custom")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
