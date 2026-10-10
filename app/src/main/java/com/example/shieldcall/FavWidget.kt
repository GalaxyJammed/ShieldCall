package com.example.shieldcall

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.shieldcall.R
import android.os.Bundle

data class FavItem(val key: String, val name: String, val photo: Bitmap?)

object FavWidget {
    private fun p(c: Context) = c.getSharedPreferences("favwidget", Context.MODE_PRIVATE)
    private fun slot(widgetId: Int) = "keys_$widgetId"

    fun keys(c: Context, widgetId: Int): List<String> =
        p(c).getString(slot(widgetId), "").orEmpty().split(",").filter { it.isNotBlank() }

    fun save(c: Context, widgetId: Int, keys: List<String>) {
        p(c).edit().putString(slot(widgetId), keys.take(6).joinToString(",")).commit()
    }

    fun load(c: Context, widgetId: Int): List<FavItem> = keys(c, widgetId).take(6).map { key ->
        try {
            val info = Reports.loadContactInfo(c, "+$key")
            FavItem(key, info.name?.takeIf { it.isNotBlank() } ?: "+$key", info.photo?.let { decode(c, it) })
        } catch (e: Exception) {
            FavItem(key, "+$key", null)
        }
    }

    private fun decode(c: Context, uri: String): Bitmap? = try {
        c.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    fun avatar(item: FavItem, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        val src = item.photo
        if (src != null) {
            val side = minOf(src.width, src.height)
            val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(crop, size, size, true)
            canvas.drawCircle(r, r, r, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            paint.xfermode = null
        } else {
            paint.color = Color.parseColor("#D1E4FF")
            canvas.drawCircle(r, r, r, paint)
            paint.color = Color.parseColor("#001D36")
            paint.textSize = size * 0.42f
            paint.textAlign = Paint.Align.CENTER
            val letter = item.name.firstOrNull()?.uppercase() ?: "?"
            canvas.drawText(letter, r, r - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return out
    }
}

class FavContactsWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        Thread { render(context, manager, widgetId) }.start()
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                ids.forEach { render(context, manager, it) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private val ROWS = intArrayOf(R.id.row0, R.id.row1)
        private val TILES = intArrayOf(R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5)
        private val AVATARS = intArrayOf(R.id.avatar0, R.id.avatar1, R.id.avatar2, R.id.avatar3, R.id.avatar4, R.id.avatar5)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5)

        private fun rowSizes(n: Int): List<Int> = when (n) {
            0 -> emptyList()
            1 -> listOf(1)
            2 -> listOf(2)
            3 -> listOf(3)
            4 -> listOf(2, 2)
            5 -> listOf(2, 3)
            else -> listOf(3, 3)
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val items = FavWidget.load(context, widgetId)
            val rv = RemoteViews(context.packageName, R.layout.fav_widget)
            if (items.isEmpty()) {
                rv.setViewVisibility(R.id.rows, View.GONE)
                rv.setViewVisibility(R.id.empty, View.VISIBLE)
                rv.setOnClickPendingIntent(
                    R.id.empty,
                    PendingIntent.getActivity(
                        context,
                        widgetId + 7000,
                        Intent(context, FavWidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                rv.setViewVisibility(R.id.rows, View.VISIBLE)
                rv.setViewVisibility(R.id.empty, View.GONE)
                TILES.forEach { rv.setViewVisibility(it, View.GONE) }
                val sizes = rowSizes(items.size)
                val opts = manager.getAppWidgetOptions(widgetId)
                val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                val cols = sizes.maxOrNull() ?: 1
                val showNames = (minH - 16) / sizes.size >= 80 && (minW - 16) / cols >= 70
                ROWS.forEachIndexed { r, rowId ->
                    rv.setViewVisibility(rowId, if (r < sizes.size) View.VISIBLE else View.GONE)
                }
                var index = 0
                sizes.forEachIndexed { r, count ->
                    repeat(count) { p ->
                        val item = items[index++]
                        val s = r * 3 + p
                        rv.setViewVisibility(TILES[s], View.VISIBLE)
                        rv.setTextViewText(NAMES[s], item.name)
                        rv.setViewVisibility(NAMES[s], if (showNames) View.VISIBLE else View.GONE)
                        rv.setImageViewBitmap(AVATARS[s], FavWidget.avatar(item, 128))
                        rv.setOnClickPendingIntent(
                            TILES[s],
                            PendingIntent.getActivity(
                                context,
                                widgetId * 10 + s,
                                Intent(context, MainActivity::class.java)
                                    .putExtra("dial", "+${item.key}")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    }
                }
            }
            manager.updateAppWidget(widgetId, rv)
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, FavContactsWidgetProvider::class.java))
            Thread { ids.forEach { render(context, mgr, it) } }.start()
        }
    }
}