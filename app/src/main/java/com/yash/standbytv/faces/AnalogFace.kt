package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yash.standbytv.T
import com.yash.standbytv.WeatherRepo

/** Large analog dial with date/weather beside it. */
class AnalogFace(ctx: Context) : Face(ctx) {
    override val id = "analog"
    override val title = "Analog"
    override val styleNames = listOf("StandBy", "Classic White", "Minimal", "Ticks")
    override val tickMs: Long get() = if (prefs.showSeconds) 50L else 1000L

    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val cal = T.now()
        val r = h * 0.43f
        val cx = w * 0.36f
        val cy = h / 2f
        Draw.analog(c, cx, cy, r, cal, style, prefs.showSeconds)

        val x = cx + r + w * 0.07f
        txt.typeface = BOLD
        txt.color = Draw.ORANGE
        txt.textSize = h * 0.055f
        c.drawText(T.fmt(cal, "EEEE").uppercase(), x, h * 0.40f, txt)
        txt.color = Color.WHITE
        txt.typeface = LIGHT
        txt.textSize = h * 0.16f
        c.drawText(T.fmt(cal, "d"), x, h * 0.57f, txt)
        txt.typeface = MEDIUM
        txt.textSize = h * 0.05f
        txt.color = Draw.GREY
        c.drawText(T.fmt(cal, "MMMM yyyy"), x, h * 0.65f, txt)

        WeatherRepo.current?.let {
            Draw.weatherIcon(c, x + h * 0.045f, h * 0.76f, h * 0.09f, it.kind, it.isDay)
            txt.color = Color.WHITE
            txt.textSize = h * 0.05f
            c.drawText("${WeatherRepo.temp(it.tempC, prefs.tempUnit)}  ${it.label}", x + h * 0.11f, h * 0.775f, txt)
        }
    }
}
