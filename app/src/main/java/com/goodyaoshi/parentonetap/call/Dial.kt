package com.goodyaoshi.parentonetap.call

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 用系统拨号盘预填号码，由用户按绿色键拨出（不申请 CALL_PHONE，方案 3.7） */
fun dial(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}
