package com.goodyaoshi.parentonetap.location

/** 一次定位结果：GCJ-02 坐标 + 精度 + provider 标记（方案 3.2） */
data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestampMs: Long
)

sealed interface LocateOutcome {
    data class Success(val fix: Fix) : LocateOutcome
    data class Failure(val reason: Reason) : LocateOutcome

    enum class Reason { NO_PERMISSION, PROVIDERS_OFF, TIMEOUT, UNKNOWN }
}
