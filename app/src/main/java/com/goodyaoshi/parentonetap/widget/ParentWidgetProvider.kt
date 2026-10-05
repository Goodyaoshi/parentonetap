package com.goodyaoshi.parentonetap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.goodyaoshi.parentonetap.MainActivity
import com.goodyaoshi.parentonetap.R

/**
 * 桌面小组件（方案 3.5）：2x2 纯色大按钮「爸妈一键通」，
 * 点击拉起 MainActivity（singleTask 不会重复压栈），爸妈从首页点具体功能。
 */
class ParentWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.parent_widget).apply {
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
