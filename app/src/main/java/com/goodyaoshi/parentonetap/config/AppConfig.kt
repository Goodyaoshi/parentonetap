package com.goodyaoshi.parentonetap.config

/** 联系人（称呼 + 电话），最多 6 个（方案 4.1） */
data class ContactEntry(val label: String, val phone: String)

/** 地点：名称 + GCJ-02 坐标（家 / 常用地点共用） */
data class HomePlace(val name: String, val lat: Double, val lng: Double)

/** 全部本地配置（方案 4.1），存 DataStore，全字段安全默认值 */
data class AppConfig(
    val home: HomePlace? = null,
    val extraPlaces: List<HomePlace> = emptyList(),
    val placeName: String = "爸妈的位置",
    val contacts: List<ContactEntry> = emptyList(),
    val taxiAmap: Boolean = true,
    val taxiTencent: Boolean = true,
    val taxiHotline: Boolean = true,
    // 发位置链接包含哪些地图服务商（多选）：勾选哪几家就发哪几家，默认仅高德
    // （腾讯 marker 接口的 referer 需在腾讯位置服务注册 Key，自填标识静默失败，已整体移除腾讯）
    val shareAmap: Boolean = true,
    val shareBaidu: Boolean = false,
    val locationTimeoutSeconds: Int = 10,
    val emergencyContactIndex: Int = 0,
    val emergencyCustomPhone: String = "",
    // 首次启动「家人协助设置」分步引导是否已完成（方案 3.7）
    val onboardingDone: Boolean = false
) {
    /**
     * 紧急求助拨打对象（方案 3.1/4.1，农村场景：子女/邻居优先）：
     * 手填号码 > 选中的联系人 > 第 1 位联系人；都没有 → null（上层兜底 120）。
     */
    fun emergencyTarget(): ContactEntry? {
        emergencyCustomPhone.trim().takeIf { it.isNotEmpty() }?.let {
            return ContactEntry(label = "紧急联系人", phone = it)
        }
        return contacts.getOrNull(emergencyContactIndex) ?: contacts.firstOrNull()
    }

    /** 打卡名称兜底 */
    fun safePlaceName(): String = placeName.trim().ifBlank { "爸妈的位置" }

    /** 是否至少选中一个地图服务商 */
    fun hasShareProvider(): Boolean = shareAmap || shareBaidu
}