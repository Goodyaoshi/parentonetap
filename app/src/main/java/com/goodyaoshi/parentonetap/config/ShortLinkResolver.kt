package com.goodyaoshi.parentonetap.config

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * 地图分享短链联网解析（方案 4.2）：高德/百度「地点分享」出来的多是短链
 * （如 https://surl.amap.com/xxx），短链内不含坐标，必须联网跟随跳转拿最终地址。
 *
 * 联网边界：仅设置页粘贴配置时使用（ShortLinkResolver + resolve）；
 * 日常功能（定位/发位置/打车/打电话）依旧零网络请求。
 *
 * 跳转链路可能遇到：HTTP 3xx、JS/meta 跳转、跳到自定义 scheme（amapuri:// 等）、
 * 明文 http 中转——各分支都要处理，且异常必须带上真实原因，否则真机没法定位。
 */
object ShortLinkResolver {

    /** 解析失败，message 可直接展示给用户 */
    class ResolveException(message: String) : IOException(message)

    private const val TAG = "ShortLinkResolver"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_HOPS = 8
    private const val MAX_BODY_BYTES = 512 * 1024

    private val URL_REGEX = Regex("""https?://[^\s，,、）)】"'<>]+""")

    // 坐标写法（按顺序尝试，取第一组落在中国境内的）
    private val COORD_PATTERNS = listOf(
        // URL/HTML 参数：position=113.46,23.17（高德，经度在前）
        Regex("""position[=:"'\s]+(\d{1,3}\.\d+)[,，\s]+(\d{1,3}\.\d+)"""),
        // location=23.17,113.46（百度，纬度在前）
        Regex("""location[=:"'\s]+(\d{1,3}\.\d+)[,，\s]+(\d{1,3}\.\d+)"""),
        // 自定义 scheme：?lat=23.17&lon=113.46 / &lng= / &longitude=
        Regex(
            """[?&](?:lat|latitude)=(\d{1,3}\.\d+)[^0-9]{1,20}?(?:lon|lng|longitude)=(\d{1,3}\.\d+)""",
            RegexOption.IGNORE_CASE
        ),
        // JSON： "lng":113.46,"lat":23.17
        Regex(
            """["']?(?:lng|longitude)["']?\s*[:=]\s*"?(\d{1,3}\.\d+)"?[\s\S]{0,80}?["']?(?:lat|latitude)["']?\s*[:=]\s*"?(\d{1,3}\.\d+)""",
            RegexOption.IGNORE_CASE
        )
    )
    private val TITLE_REGEX = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    // JS / meta 跳转：location.href="..."、location.replace("...")、<meta refresh content="0;url=...">
    private val JS_REDIRECT_REGEXES = listOf(
        Regex("""location(?:\.href|\.replace\s*\(|\s*=\s*)["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+http-equiv=["']?refresh["']?[^>]*content=["'][^"']*url=([^"'>\s]+)""", RegexOption.IGNORE_CASE)
    )
    private val TITLE_SUFFIXES =
        listOf(" - 高德地图", "-高德地图", " - 腾讯地图", " - 百度地图", "_百度地图", " - 地图")

    private fun inLat(v: Double) = v in 18.0..54.0
    private fun inLng(v: Double) = v in 73.0..136.0

    /**
     * 解析粘贴文本里的地图短链：跟随跳转（HTTP 3xx / JS / meta）→ 最终地址参数 → 页面正文提取。
     * 解析失败抛 [ResolveException]，message 可直接展示（含真实原因）。
     */
    fun resolve(text: String): PlaceLinkParser.Parsed {
        val firstUrl = URL_REGEX.find(text.trim())?.value ?: throw ResolveException("没识别到链接")
        // 短链本身可能就是长链接形式，先本地试一次（省一次网络请求）
        PlaceLinkParser.parse(firstUrl)?.let { return it }

        var current = firstUrl
        repeat(MAX_HOPS) { hop ->
            Log.d(TAG, "hop#$hop GET $current")
            val conn = openOrThrow(current)
            val code = readStatusCode(conn, current)
            val location = conn.getHeaderField("Location")
            Log.d(TAG, "hop#$hop -> HTTP $code, Location=$location")

            if (code in 300..399 && !location.isNullOrBlank()) {
                conn.disconnect()
                // 跳去自定义 scheme（amapuri:// 等）时，坐标往往就在 scheme 参数里
                if (!location.startsWith("http", ignoreCase = true)) {
                    parseFromText(location, null)?.let { return it }
                    throw ResolveException(
                        "短链跳到了地图 App（${location.substringBefore("://")}://），没能取到坐标，" +
                            "请改用「设当前位置为家」"
                    )
                }
                current = absolutize(current, location)
                return@repeat
            }

            // 4xx/5xx：部分 CDN 的错误页里仍带跳转脚本，继续尝试解析正文
            val body = readBodyQuietly(conn)
            conn.disconnect()
            Log.d(TAG, "hop#$hop body=${body.length} chars")

            parseFromText(current, body)?.let { return it }

            // HTML 里的 JS / meta 跳转
            extractRedirect(body)?.let { next ->
                if (!next.startsWith("http", ignoreCase = true)) {
                    parseFromText(next, body)?.let { return it }
                } else {
                    current = absolutize(current, next)
                    return@repeat
                }
            }

            throw ResolveException(
                if (code >= 400) "链接返回错误（HTTP $code），请检查链接是否有效"
                else "打开了链接，但页面里没找到坐标，请改用「设当前位置为家」"
            )
        }
        throw ResolveException("跳转次数过多，请改用「设当前位置为家」")
    }

    private fun openOrThrow(url: String): HttpURLConnection {
        val parsed = try {
            URL(url)
        } catch (e: Exception) {
            throw ResolveException("链接格式不对：${url.take(40)}")
        }
        return try {
            (parsed.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", ACCEPT)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Referer", "https://surl.amap.com/")
            }
        } catch (e: Exception) {
            throw ResolveException("打开链接失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 取状态码，失败时把真实原因带进报错（否则真机无法定位问题） */
    private fun readStatusCode(conn: HttpURLConnection, url: String): Int = try {
        conn.responseCode
    } catch (e: Exception) {
        conn.disconnect()
        val host = runCatching { URL(url).host }.getOrDefault(url)
        val reason = e.message ?: e.javaClass.simpleName
        throw ResolveException("访问 $host 失败：$reason")
    }

    /** 读取正文，失败不抛（错误响应也能尝试解析） */
    private fun readBodyQuietly(conn: HttpURLConnection): String {
        val stream: InputStream? = try {
            conn.inputStream
        } catch (e: Exception) {
            try {
                conn.errorStream
            } catch (e2: Exception) {
                null
            }
        }
        if (stream == null) return ""
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        try {
            stream.use { ins ->
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    total += n
                    if (total > MAX_BODY_BYTES) break
                }
            }
        } catch (e: Exception) {
            // 读到一半失败：已读到的内容仍可尝试解析
        }
        return out.toString("UTF-8")
    }

    /** 从「最终地址 + 页面正文」里找坐标与名称 */
    private fun parseFromText(url: String, body: String?): PlaceLinkParser.Parsed? {
        // 1) 长链接形式：URL 参数里直接带坐标
        PlaceLinkParser.parse(url)?.let { urlParsed ->
            val name = body?.let { cleanTitle(it) } ?: urlParsed.name
            return PlaceLinkParser.Parsed(name ?: urlParsed.name, urlParsed.lat, urlParsed.lng)
        }
        // 2) 页面/参数文本里按模式提取
        val haystack = buildString {
            append(url)
            if (!body.isNullOrBlank()) {
                append('\n')
                append(body)
            }
        }
        return parseFromHtml(haystack, body)
    }

    private fun parseFromHtml(haystack: String, body: String?): PlaceLinkParser.Parsed? {
        for (pattern in COORD_PATTERNS) {
            for (m in pattern.findAll(haystack).take(20)) {
                val a = m.groupValues[1].toDoubleOrNull() ?: continue
                val b = m.groupValues[2].toDoubleOrNull() ?: continue
                val pair = when {
                    inLat(a) && inLng(b) -> b to a
                    inLat(b) && inLng(a) -> a to b
                    else -> continue
                }
                val name = body?.let { cleanTitle(it) }
                return PlaceLinkParser.Parsed(name, pair.first, pair.second)
            }
        }
        return null
    }

    private fun extractRedirect(body: String): String? {
        for (regex in JS_REDIRECT_REGEXES) {
            val m = regex.find(body) ?: continue
            val target = m.groupValues[1].trim().replace("&amp;", "&")
            if (target.startsWith("http", ignoreCase = true) ||
                target.contains("://")
            ) return target
        }
        return null
    }

    private fun absolutize(base: String, target: String): String =
        try {
            URL(URL(base), target.replace("&amp;", "&")).toString()
        } catch (e: Exception) {
            target
        }

    /** 从 <title> 提取地点名（去掉「 - 高德地图」等后缀），失败返回 null */
    private fun cleanTitle(body: String): String? {
        val raw = TITLE_REGEX.find(body)?.groupValues?.get(1)?.trim() ?: return null
        var t = raw.replace(Regex("""\s+"""), " ")
        TITLE_SUFFIXES.forEach { suffix ->
            if (t.endsWith(suffix)) t = t.removeSuffix(suffix)
        }
        t = runCatching { URLDecoder.decode(t.replace("+", "%2B"), "UTF-8") }.getOrDefault(t).trim()
        return t.ifBlank { null }?.take(30)
    }
}