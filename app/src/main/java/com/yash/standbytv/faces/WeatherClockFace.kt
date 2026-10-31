package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.yash.standbytv.T
import com.yash.standbytv.Weather
import com.yash.standbytv.WeatherRepo

/** Time on the left, live weather on the right, sky-coloured background. */
class WeatherClockFace(ctx: Context) : Face(ctx) {
    override val id = "weather"
    override val title = "Weather"
    override val styleNames = listOf("Sky", "Black")

    private val bg = Paint()
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var bgKey = ""

    private fun sky(w: Weather?): IntArray {
        if (w == null) return intArrayOf(0xFF1C1C1E.toInt(), 0xFF000000.toInt())
        if (!w.isDay) return intArrayOf(0xFF0B1026.toInt(), 0xFF000000.toInt())
        return when (w.kind) {
            Weather.Kind.CLEAR, Weather.Kind.PARTLY -> intArrayOf(0xFF2F7FD8.toInt(), 0xFF0B2E59.toInt())
            Weather.Kind.CLOUDY, Weather.Kind.FOG -> intArrayOf(0xFF5B6B7C.toInt(), 0xFF1E252D.toInt())
            Weather.Kind.DRIZZLE, Weather.Kind.RAIN -> intArrayOf(0xFF3C4A5C.toInt(), 0xFF10161E.toInt())
            Weather.Kind.SNOW -> intArrayOf(0xFF8FA6BF.toInt(), 0xFF2B3A4A.toInt())
            Weather.Kind.THUNDER -> intArrayOf(0xFF2A2440.toInt(), 0xFF07060C.toInt())
        }
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val wx = WeatherRepo.current
        val colors = if (style == 1) intArrayOf(Color.BLACK, Color.BLACK) else sky(wx)
        val key = "${colors[0]}:${colors[1]}:$h"
        if (key != bgKey) {
            bgKey = key
            bg.shader = LinearGradient(0f, 0f, 0f, h, colors[0], colors[1], Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, bg)

        val cal = T.now()
        val left = w * 0.07f
        txt.textAlign = Paint.Align.LEFT
        txt.typeface = THIN
        txt.color = Color.WHITE
        Draw.fit(txt, "00:00", w * 0.48f, h * 0.45f)
        c.drawText(T.hm(cal, prefs.use24h), left, h * 0.55f, txt)
        txt.typeface = MEDIUM
        txt.textSize = h * 0.05f
        val sub = (if (prefs.use24h) "" else T.ampm(cal) + "  ·  ") + T.dateLong(cal)
        c.drawText(sub.uppercase(), left + h * 0.01f, h * 0.66f, txt)

        val rx = w * 0.62f
        if (wx == null) {
            txt.color = Color.argb(200, 255, 255, 255)
            c.drawText(if (WeatherRepo.lastError != null) "Weather unavailable" else "Loading weather…", rx, h * 0.5f, txt)
            return
        }
        Draw.weatherIcon(c, rx + h * 0.12f, h * 0.30f, h * 0.24f, wx.kind, wx.isDay, colors[0])
        txt.typeface = LIGHT
        txt.textSize = h * 0.16f
        c.drawText(WeatherRepo.temp(wx.tempC, prefs.tempUnit), rx + h * 0.27f, h * 0.35f, txt)

        txt.typeface = BOLD
        txt.textSize = h * 0.055f
        c.drawText(wx.place.ifBlank { "Here" }, rx, h * 0.52f, txt)
        txt.typeface = MEDIUM
        txt.textSize = h * 0.045f
        txt.color = Color.argb(220, 255, 255, 255)
        val u = prefs.tempUnit
        val lines = listOf(
            wx.label,
            "H ${WeatherRepo.temp(wx.hiC, u)}   L ${WeatherRepo.temp(wx.loC, u)}",
            "Humidity ${wx.humidity}%   Wind ${wx.windKmh.toInt()} km/h",
            "Sunrise ${wx.sunrise}   Sunset ${wx.sunset}",
        )
        var y = h * 0.60f
        for (l in lines) { c.drawText(l, rx, y, txt); y += h * 0.07f }
    }
}
