package com.goodyaoshi.parentonetap.share

import android.net.Uri
import com.goodyaoshi.parentonetap.location.Fix

/**
 * 高德/百度链接都必须带地点名称，否则不显示标记/标题：
 * - 百度：title 必填，缺了显示「没有标题」
 * - 高德：name 为标记名称
 * 坐标顺序严格按各家文档，写反了会落点错误或无反应：
 * 高德 lng,lat；百度 lat,lng + coord_type=gcj02。
 * （腾讯已整体移除：其 marker 接口的 referer 需在腾讯位置服务注册 Key，自填标识静默失败）
 *
 * 注意：微信纯文本消息只识别 https 链接为可点击，自定义 scheme（amapuri:// 等）不可点，
 * 因此分享出去必须用 https；这些 https 链接在手机浏览器打开后会自动尝试拉起对应地图 App。
 */
object MapLinkBuilder {

    /** 百度 src 参数要求标识来源 */
    private const val REFERER = "parentonetap"

    /** 高德：坐标 lng,lat（经度在前！写反了搜索无反应），name 为标记名称 */
    fun amapLink(fix: Fix, placeName: String): String =
        "https://uri.amap.com/marker?position=${fix.lng},${fix.lat}" +
            "&name=${Uri.encode(placeName)}"

    /** 百度：坐标 lat,lng + coord_type=gcj02（百度用 BD-09，必须声明源坐标系），title 必填 */
    fun baiduLink(fix: Fix, placeName: String): String =
        "https://api.map.baidu.com/marker?location=${fix.lat},${fix.lng}&coord_type=gcj02" +
            "&title=${Uri.encode(placeName)}&content=${Uri.encode(placeName)}" +
            "&output=html&src=$REFERER"

    /**
     * 按设置页勾选的服务商生成链接列表（固定顺序：高德、百度）。
     * 全部未勾选时兜底高德（上层 UI 已阻止，此处仅防御）。
     */
    fun selectedLinks(
        fix: Fix,
        placeName: String,
        amap: Boolean,
        baidu: Boolean
    ): List<Pair<String, String>> {
        val links = buildList {
            if (amap) add("高德" to amapLink(fix, placeName))
            if (baidu) add("百度" to baiduLink(fix, placeName))
        }
        return links.ifEmpty { listOf("高德" to amapLink(fix, placeName)) }
    }
}