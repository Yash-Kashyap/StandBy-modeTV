package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yash.standbytv.T
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/** Four cities with mini dials and the offset from local time. */
class WorldClockFace(ctx: Context) : Face(ctx) {
    override val id = "world"
    override val title = "World Clock"
    override val styleNames = listOf("Asia & Europe", "Americas", "Pacific")

    private val sets = arrayOf(
        arrayOf("London" to "Europe/London", "Dubai" to "Asia/Dubai", "Singapore" to "Asia/Singapore", "Tokyo" to "Asia/Tokyo"),
        arrayOf("New York" to "America/New_York", "Chicago" to "America/Chicago", "Los Angeles" to "America/Los_Angeles", "São Paulo" to "America/Sao_Paulo"),
        arrayOf("Sydney" to "Australia/Sydney", "Auckland" to "Pacific/Auckland", "Hong Kong" to "Asia/Hong_Kong", "Kolkata" to "Asia/Kolkata"),
    )
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Draw.PANEL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val list = sets[style]
        val gap = w * 0.02f
        val pw = (w - gap * 5) / 4f
        val ph = h * 0.78f
        val top = (h - ph) / 2f
        val local = T.now()
        val localOff = local.timeZone.getOffset(local.timeInMillis)
        for ((i, pair) in list.withIndex()) {
            val (city, tzId) = pair
            val tz = TimeZone.getTimeZone(tzId)
            val cal = T.now(tz)
            val x = gap + i * (pw + gap)
            rect.set(x, top, x + pw, top + ph)
            c.drawRoundRect(rect, pw * 0.1f, pw * 0.1f, panel)
            val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
            val night = hourOfDay < 6 || hourOfDay >= 19
            Draw.analog(c, rect.centerX(), top + ph * 0.32f, pw * 0.36f, cal, if (night) 3 else 1, false)

            txt.color = Color.WHITE; txt.typeface = BOLD
            txt.textSize = ph * 0.065f
            c.drawText(city, rect.centerX(), top + ph * 0.70f, txt)
            txt.typeface = LIGHT
            txt.textSize = ph * 0.11f
            c.drawText(T.hm(cal, prefs.use24h) + if (prefs.use24h) "" else " " + T.ampm(cal).lowercase(), rect.centerX(), top + ph * 0.84f, txt)

            val diffMin = (tz.getOffset(local.timeInMillis) - localOff) / 60000
            val dayDiff = cal.get(Calendar.DAY_OF_YEAR) - local.get(Calendar.DAY_OF_YEAR)
            val day = when { dayDiff == 0 -> "Today"; dayDiff == 1 || dayDiff < -300 -> "Tomorrow"; else -> "Yesterday" }
            val sign = if (diffMin >= 0) "+" else "−"
            val hh = abs(diffMin) / 60; val mm = abs(diffMin) % 60
            val off = if (diffMin == 0) "Same time" else "$sign${hh}h" + (if (mm != 0) " ${mm}m" else "")
            txt.typeface = MEDIUM; txt.color = Draw.GREY
            txt.textSize = ph * 0.05f
            c.drawText("$day, $off", rect.centerX(), top + ph * 0.93f, txt)
        }
    }
}
