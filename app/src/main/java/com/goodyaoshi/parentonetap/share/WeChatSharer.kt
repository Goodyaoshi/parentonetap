package com.goodyaoshi.parentonetap.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/**
 * 微信分享（方案 3.2/3.3）：系统文本分享，无需微信开放平台 Appid。
 * 兜底链：微信直发 → 未装微信走系统选择器 → 复制剪贴板。
 */
object WeChatSharer {
    const val WECHAT_PACKAGE = "com.tencent.mm"

    fun isWeChatInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** 微信已装 → 直发微信；未装 → 系统选择器（可选短信等其他渠道） */
    fun createIntent(context: Context, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return if (isWeChatInstalled(context)) {
            send.setPackage(WECHAT_PACKAGE)
            send
        } else {
            Intent.createChooser(send, "选择发送方式")
        }
    }

    /** 复制剪贴板兜底 */
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("位置", text))
    }
}

/** 大字 Toast（老年友好，居中显示） */
fun showBigToast(context: Context, message: String) {
    val density = context.resources.displayMetrics.density
    val view = TextView(context).apply {
        text = message
        textSize = 26f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(
            (24 * density).toInt(), (16 * density).toInt(),
            (24 * density).toInt(), (16 * density).toInt()
        )
        background = GradientDrawable().apply {
            setColor(0xDD333333.toInt())
            cornerRadius = 14f * density
        }
    }
    try {
        Toast(context).apply {
            this.view = view
            duration = Toast.LENGTH_LONG
            setGravity(Gravity.CENTER, 0, 0)
        }.show()
    } catch (e: Exception) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
