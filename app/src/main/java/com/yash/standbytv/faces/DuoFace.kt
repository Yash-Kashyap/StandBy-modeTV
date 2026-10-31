package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.yash.standbytv.T
import com.yash.standbytv.WeatherRepo

/** The classic iOS StandBy look: two rounded widgets side by side. */
class DuoFace(ctx: Context) : Face(ctx) {
    override val id = "duo"
    override val title = "Duo"
    override val styleNames = listOf(
        "Analog + Calendar", "Digital + Calendar", "Analog + Weather", "Digital + Weather", "Weather + Calendar"
    )
    override val tickMs: Long get() = if (prefs.showSeconds && leftIsAnalog()) 100L else 1000L

    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Draw.PANEL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val l = RectF(); private val r = RectF()

    private fun leftIsAnalog() = style == 0 || style == 2

    override fun draw(c: Canvas, w: Float, h: Float) {
        val gap = w * 0.025f
        val ph = h - gap * 2.6f
        val pw = (w - gap * 3) / 2f
        val top = (h - ph) / 2f
        l.set(gap, top, gap + pw, top + ph)
        r.set(gap * 2 + pw, top, gap * 2 + pw * 2, top + ph)
        val rad = ph * 0.11f
        c.drawRoundRect(l, rad, rad, panel)
        c.drawRoundRect(r, rad, rad, panel)

        val cal = T.now()
        val w8 = WeatherRepo.current
        when (style) {
            0 -> { analog(c, l); Draw.month(c, r, cal) }
            1 -> { digital(c, l); Draw.month(c, r, cal) }
            2 -> { analog(c, l); Draw.weatherPanel(c, r, w8, prefs.tempUnit) }
            3 -> { digital(c, l); Draw.weatherPanel(c, r, w8, prefs.tempUnit) }
            else -> { Draw.weatherPanel(c, l, w8, prefs.tempUnit); Draw.month(c, r, cal) }
        }
    }

    private fun analog(c: Canvas, rect: RectF) {
        val rr = minOf(rect.width(), rect.height()) * 0.44f
        Draw.analog(c, rect.centerX(), rect.centerY(), rr, T.now(), 0, prefs.showSeconds)
    }

    private fun digital(c: Canvas, rect: RectF) {
        val cal = T.now()
        val time = T.hm(cal, prefs.use24h)
        txt.typeface = BOLD
        txt.color = Color.WHITE
        Draw.fit(txt, "88:88", rect.width() * 0.86f, rect.height() * 0.48f)
        c.drawText(time, rect.centerX(), Draw.centerY(txt, rect.centerY() - rect.height() * 0.04f), txt)

        txt.typeface = MEDIUM
        txt.color = Draw.ORANGE
        txt.textSize = rect.height() * 0.075f
        val sub = if (prefs.use24h) T.dateLong(cal) else "${T.ampm(cal)}  ·  ${T.dateShort(cal)}"
        c.drawText(sub.uppercase(), rect.centerX(), rect.bottom - rect.height() * 0.13f, txt)
    }
}
