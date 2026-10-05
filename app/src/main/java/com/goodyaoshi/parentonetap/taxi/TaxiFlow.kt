package com.goodyaoshi.parentonetap.taxi

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.goodyaoshi.parentonetap.config.HomePlace

/** 出行方式（方案 3.3）：导航 / 顺风车 / 打车 */
enum class TaxiMode { NAVI, RIDESHARE, TAXI }

/**
 * 出行链路（方案 3.3）：按配置开关固定顺序尝试，deeplink 拉起失败降级下一项，
 * 打车/顺风车兜底拨 95128（全国出租车电召热线）。
 *
 * 真机实测结论（adb 拉起 + 截图 + dumpsys intent-filter 三重验证）：
 * - **高德没有任何可用的「直达打车页」deeplink**。实测以下写法全部只落到首页地图：
 *   `amapuri://drive`（旧写法，不认）、`amapuri://taxi`、`amapuri://openFeature?featureName=Taxi`、
 *   `amapuriucar://`（需 UCAR 动作）、`INTENT_ACTION_TAXISHORT` 动作。
 *   原因：高德把**所有** `amapuri://` 都交给同一个 `SchemeHandleActivity` 兜底分发（intent-filter
 *   不约束 authority/path），App 内部不认识的路径就忽略或短暂提示「不支持功能」。
 * - **可用写法**：`amapuri://route/plan?...&t=0` 打开**驾车路线页**，实测该页**顶部就有
 *   「打车 / 顺风车 / 公共交通」三个标签**，点一下即进对应页面，是能到达的最深路径。
 * - `amapuri://route/plan` 的 t 参数（官方定义）：驾车 0 / 公交 1 / 步行 2 / 骑行 3 / 火车 4 / 长途客车 5。
 *   导航默认步行（t=2），路线页内可自行切换出行方式。
 * - 不用 `amapuri://navi`：它只支持驾车导航，无法指定步行/公交。
 * - 腾讯地图：`qqmap://map/routeplan`，type=drive/bus/walk/bike，页内再点打车。
 * 注意：dname/to 等中文参数必须 Uri.encode，否则部分渠道拉起失败。
 */
object TaxiFlow {
    const val TAXI_HOTLINE = "95128"

    /** route/plan 出行方式：驾车 0 / 公交 1 / 步行 2（官方定义） */
    private const val T_DRIVE = 0
    private const val T_WALK = 2

    private fun amapRoute(place: HomePlace, t: Int): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(
            "amapuri://route/plan?sourceApplication=parentonetap" +
                "&dlat=${place.lat}&dlon=${place.lng}" +
                "&dname=${Uri.encode(place.name)}&dev=0&t=$t"
        )
    }

    /** 高德步行路线（导航回家默认，路线页内可切公交/驾车） */
    fun amapWalkIntent(place: HomePlace): Intent = amapRoute(place, T_WALK)

    /**
     * 高德驾车路线页：页内底部有「打车」入口，进打车页后车型里可选顺风车。
     * 这是无公开打车 deeplink 前提下能到达的最深路径。
     */
    fun amapDriveIntent(place: HomePlace): Intent = amapRoute(place, T_DRIVE)

    /** 腾讯地图路线规划页（type=drive/bus/walk/bike，页内可点打车） */
    fun tencentIntent(place: HomePlace, type: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(
            "qqmap://map/routeplan?type=$type&to=${Uri.encode(place.name)}" +
                "&tocoord=${place.lat},${place.lng}&referer=parentonetap"
        )
    }

    /** 探测可拉起（需 Manifest <queries> 声明） */
    private fun resolvable(context: Context, intent: Intent): Boolean = runCatching {
        intent.resolveActivity(context.packageManager) != null
    }.getOrDefault(false)

    /**
     * 按方式与配置开关生成有序渠道列表：
     * NAVI → 高德步行路线 → 腾讯步行路线
     * TAXI/RIDESHARE → 高德驾车路线页（页内打车/选顺风车）→ 腾讯驾车路线页（→ 95128 由上层兜底）
     */
    fun candidates(
        context: Context,
        place: HomePlace,
        mode: TaxiMode,
        useAmap: Boolean,
        useTencent: Boolean
    ): List<Intent> = buildList {
        if (mode == TaxiMode.NAVI) {
            if (useAmap) {
                val i = amapWalkIntent(place)
                if (resolvable(context, i)) add(i)
            }
            if (useTencent) {
                val i = tencentIntent(place, "walk")
                if (resolvable(context, i)) add(i)
            }
        } else {
            if (useAmap) {
                val i = amapDriveIntent(place)
                if (resolvable(context, i)) add(i)
            }
            if (useTencent) {
                val i = tencentIntent(place, "drive")
                if (resolvable(context, i)) add(i)
            }
        }
    }
}