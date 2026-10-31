package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.yash.standbytv.Palettes
import com.yash.standbytv.T
import com.yash.standbytv.WeatherRepo

/** Huge gradient digits that fill the whole TV. Up/Down cycles colour themes. */
class BigClockFace(ctx: Context) : Face(ctx) {
    override val id = "big"
    override val title = "Big Clock"
    override val styleNames = Palettes.all.map { it.name }

    private val bg = Paint()
    private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = BLACK }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = MEDIUM }
    private var cacheKey = ""

    override fun draw(c: Canvas, w: Float, h: Float) {
        val pal = Palettes.all[style]
        val key = "$style:$w:$h"
        if (key != cacheKey) {
            cacheKey = key
            bg.shader = LinearGradient(0f, 0f, 0f, h, pal.bg1, pal.bg2, Shader.TileMode.CLAMP)
            digits.shader = LinearGradient(0f, h * 0.15f, w, h * 0.85f, pal.fg1, pal.fg2, Shader.TileMode.CLAMP)
            if (pal.glow) digits.setShadowLayer(h * 0.04f, 0f, 0f, Palettes.withAlpha(pal.fg1, 170))
            else digits.clearShadowLayer()
        }
        c.drawRect(0f, 0f, w, h, bg)

        val cal = T.now()
        val time = T.hm(cal, prefs.use24h)
        // Size against a fixed template so digits don't jump around as the time changes.
        Draw.fit(digits, if (prefs.use24h) "00:00" else "10:00", w * 0.92f, h * 0.80f)
        val cy = h * 0.47f
        c.drawText(time, w / 2f, Draw.centerY(digits, cy), digits)

        small.color = Palettes.withAlpha(pal.fg1, 210)
        small.textSize = h * 0.05f
        val parts = mutableListOf(T.dateLong(cal))
        if (!prefs.use24h) parts.add(0, T.ampm(cal))
        if (prefs.showSeconds) parts.add(":" + T.sec(cal))
        WeatherRepo.current?.let { parts.add(WeatherRepo.temp(it.tempC, prefs.tempUnit) + " " + it.label) }
        c.drawText(parts.joinToString("   ·   ").uppercase(), w / 2f, h * 0.92f, small)
    }
}
