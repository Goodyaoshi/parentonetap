package com.goodyaoshi.parentonetap.config

import android.net.Uri

/**
 * 常用地点输入解析（方案 4.2）：从子女粘贴的「地图 App 分享链接/文字」或纯坐标中提取坐标与名称。
 *
 * 为什么不做 App 内地图选点：本 App 无网络权限、无地图 SDK，瓦片渲染与选点都需要联网/Key，
 * 与「纯离线、位置数据不出手机」的设计冲突。改为解析分享文本——零依赖、纯本地、零门槛：
 * 子女在高德/百度地图里找到地点 → 分享 → 复制 → 粘贴到设置页，自动识别。
 *
 * 顺序差异（务必不弄反）：高德 position 是 lng,lat；百度 location 是 lat,lng。
 * 纯坐标文本按中国境内经纬度范围（纬度 18~54、经度 73~136）自动判序。
 */
object PlaceLinkParser {

    data class Parsed(val name: String?, val lat: Double, val lng: Double)

    private val URL_REGEX = Regex("""https?://[^\s，,、）)】]+""")
    private val NUM_REGEX = Regex("""\d{1,3}\.\d+""")

    /** 解析入口：先按地图链接解析（能取到名称），失败再按纯坐标解析 */
    fun parse(text: String): Parsed? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        parseMapUrl(raw)?.let { return it }
        return parseByNumbers(raw)
    }

    /**
     * 是否是地图 App「地点分享」出来的短链（如高德 `https://surl.amap.com/xxx`）。
     * 短链内不含坐标，必须联网请求才能拿到最终地址里的坐标——本 App 无 INTERNET 权限（纯离线），
     * 无法解析。UI 据此给出明确替代引导，而不是笼统报「没识别到」。
     */
    fun isUnresolvableMapLink(text: String): Boolean {
        val url = URL_REGEX.find(text.trim())?.value ?: return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty()
        if (!host.contains("amap.com") && !host.contains("baidu.com")) return false
        val hasCoord = !uri.getQueryParameter("position").isNullOrBlank() ||
            !uri.getQueryParameter("location").isNullOrBlank()
        return !hasCoord
    }

    private fun parseMapUrl(text: String): Parsed? {
        val url = URL_REGEX.find(text)?.value ?: return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty()

        if (host.contains("amap.com")) {
            val pair = parsePair(uri.getQueryParameter("position")) ?: return null
            val lng = pair.first
            val lat = pair.second   // 高德：经度在前
            if (inChina(lat, lng)) return Parsed(uri.getQueryParameter("name"), lat, lng)
        }
        if (host.contains("baidu.com")) {
            val pair = parsePair(uri.getQueryParameter("location")) ?: return null
            val lat = pair.first
            val lng = pair.second   // 百度：纬度在前
            if (inChina(lat, lng)) return Parsed(uri.getQueryParameter("title"), lat, lng)
        }
        return null
    }

    /** 纯坐标：取文本中前两组小数，按中国境内范围判断「纬度,经度」还是「经度,纬度」 */
    private fun parseByNumbers(text: String): Parsed? {
        val nums = NUM_REGEX.findAll(text).map { it.value.toDouble() }.take(2).toList()
        if (nums.size < 2) return null
        val a = nums[0]
        val b = nums[1]
        return when {
            inLat(a) && inLng(b) -> Parsed(null, a, b)
            inLng(a) && inLat(b) -> Parsed(null, b, a)
            else -> null
        }
    }

    private fun parsePair(raw: String?): Pair<Double, Double>? {
        val parts = raw?.split(',') ?: return null
        if (parts.size < 2) return null
        val x = parts[0].trim().toDoubleOrNull() ?: return null
        val y = parts[1].trim().toDoubleOrNull() ?: return null
        return x to y
    }

    private fun inLat(v: Double) = v in 18.0..54.0
    private fun inLng(v: Double) = v in 73.0..136.0
    private fun inChina(lat: Double, lng: Double) = inLat(lat) && inLng(lng)
}