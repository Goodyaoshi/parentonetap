package com.goodyaoshi.parentonetap.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentonetap.location.Fix
import com.goodyaoshi.parentonetap.location.LastFixStore
import com.goodyaoshi.parentonetap.location.LocateOutcome
import com.goodyaoshi.parentonetap.location.LocationClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页 / 联系人页共用：读同一份配置，改完全局生效（方案 4） */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val store: AppConfigStore,
    private val locationClient: LocationClient,
    private val lastFixStore: LastFixStore
) : ViewModel() {

    val config: StateFlow<AppConfig> = store.configFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    /** 任意字段变更：transform 旧配置 → 新配置 → 落盘 */
    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch { store.save(transform(config.value)) }
    }

    /** 「设当前位置为家」：复用定位模块，超时读配置 */
    suspend fun locate(): LocateOutcome {
        val timeout = config.value.locationTimeoutSeconds.coerceIn(3, 120) * 1000L
        return locationClient.getFix(timeout)
    }

    suspend fun lastFix(): Fix? = lastFixStore.load()
}
