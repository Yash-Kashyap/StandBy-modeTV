package com.yash.standbytv.faces

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.yash.standbytv.Weather
import com.yash.standbytv.WeatherRepo
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Shared drawing routines used by several faces (analog dial, month calendar, weather). */
object Draw {
    const val ORANGE = 0xFFFF9F0A.toInt()
    const val RED = 0xFFFF453A.toInt()
    const val PANEL = 0xFF1C1C1E.toInt()
    const val GREY = 0xFF8E8E93.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()

    /** Sets [paint]'s text size so [text] fits inside [maxW] x [maxH]. */
    fun fit(paint: Paint, text: String, maxW: Float, maxH: Float) {
        paint.textSize = 100f
        val w = paint.measureText(text).coerceAtLeast(1f)
        val fm = paint.fontMetrics
        val hh = (fm.descent - fm.ascent).coerceAtLeast(1f)
        paint.textSize = 100f * min(maxW / w, maxH / hh)
    }

    /** Baseline y that vertically centres text (cap height) on [cy]. */
    fun centerY(paint: Paint, cy: Float): Float {
        val fm = paint.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    // ---------------------------------------------------------------- analog

    /**
     * styles: 0 iOS StandBy (black, big numerals, orange seconds)
     *         1 Classic white dial
     *         2 Minimal (no markers)
     *         3 Ticks only
     */
    fun analog(c: Canvas, cx: Float, cy: Float, r: Float, cal: Calendar, style: Int, seconds: Boolean, accent: Int = ORANGE) {
        val h = cal.get(Calendar.HOUR); val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND); val ms = cal.get(Calendar.MILLISECOND)
        val face: Int; val ink: Int; val minor: Int
        when (style) {
            1 -> { face = Color.WHITE; ink = Color.BLACK; minor = 0xFF9A9A9A.toInt() }
            2 -> { face = PANEL; ink = Color.WHITE; minor = 0 }
            3 -> { face = Color.BLACK; ink = Color.WHITE; minor = 0xFF5A5A5E.toInt() }
            else -> { face = Color.BLACK; ink = Color.WHITE; minor = 0xFF48484A.toInt() }
        }
        p.style = Paint.Style.FILL
        p.color = face
        c.drawCircle(cx, cy, r, p)

        // markers
        p.strokeCap = Paint.Cap.ROUND
        if (style == 0 || style == 3 || style == 1) {
            for (i in 0 until 60) {
                val major = i % 5 == 0
                if (style == 0 && major) continue
                val a = Math.toRadians(i * 6.0)
                val outer = r * 0.95f
                val inner = if (major) r * 0.84f else r * 0.90f
                p.color = if (major) ink else minor
                p.strokeWidth = if (major) r * 0.022f else r * 0.008f
                c.drawLine(cx + (sin(a) * inner).toFloat(), cy - (cos(a) * inner).toFloat(),
                    cx + (sin(a) * outer).toFloat(), cy - (cos(a) * outer).toFloat(), p)
            }
        }
        if (style == 0 || style == 1) {
            tp.typeface = if (style == 0) Face.MEDIUM else Face.REGULAR
            tp.color = ink
            tp.textSize = r * (if (style == 0) 0.22f else 0.16f)
            val rr = r * (if (style == 0) 0.80f else 0.70f)
            for (i in 1..12) {
                val a = Math.toRadians(i * 30.0)
                val x = cx + (sin(a) * rr).toFloat()
                val y = cy - (cos(a) * rr).toFloat()
                c.drawText(i.toString(), x, centerY(tp, y), tp)
            }
        }

        // hands
        val hourA = (h + m / 60f) * 30f
        val minA = (m + s / 60f) * 6f
        hand(c, cx, cy, hourA, r * 0.50f, r * 0.055f, ink, r * 0.0f)
        hand(c, cx, cy, minA, r * 0.78f, r * 0.040f, ink, r * 0.0f)
        if (seconds) {
            val secA = (s + ms / 1000f) * 6f
            hand(c, cx, cy, secA, r * 0.88f, r * 0.014f, accent, r * 0.16f)
        }
        p.style = Paint.Style.FILL
        p.color = if (seconds) accent else ink
        c.drawCircle(cx, cy, r * 0.035f, p)
        p.color = face
        c.drawCircle(cx, cy, r * 0.014f, p)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, color: Int, tail: Float) {
        p.color = color
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        c.save()
        c.rotate(deg, cx, cy)
        c.drawLine(cx, cy + tail, cx, cy - len, p)
        c.restore()
        p.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------------- calendar

    /** iOS-style month grid with today circled. */
    fun month(c: Canvas, rect: RectF, cal: Calendar, accent: Int = RED, fg: Int = Color.WHITE) {
        val pad = rect.width() * 0.07f
        val left = rect.left + pad; val right = rect.right - pad
        val top = rect.top + pad
        val colW = (right - left) / 7f

        val header = T_upper(cal)
        tp.textAlign = Paint.Align.LEFT
        tp.typeface = Face.BOLD
        tp.color = accent
        tp.textSize = rect.height() * 0.085f
        c.drawText(header, left + colW * 0.15f, top + tp.textSize, tp)

        val rowTop = top + tp.textSize * 1.9f
        val rowH = (rect.bottom - pad - rowTop) / 7f
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = rowH * 0.42f
        tp.typeface = Face.MEDIUM
        tp.color = GREY

        val first = cal.firstDayOfWeek
        val names = arrayOf("S", "M", "T", "W", "T", "F", "S")
        for (i in 0 until 7) {
            val dow = (first - 1 + i) % 7
            c.drawText(names[dow], left + colW * (i + 0.5f), centerY(tp, rowTop + rowH * 0.5f), tp)
        }

        val m = cal.clone() as Calendar
        m.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (m.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        val days = m.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = cal.get(Calendar.DAY_OF_MONTH)
        tp.textSize = rowH * 0.48f
        for (d in 1..days) {
            val idx = offset + d - 1
            val col = idx % 7; val row = idx / 7
            val x = left + colW * (col + 0.5f)
            val y = rowTop + rowH * (row + 1.5f)
            if (d == today) {
                p.style = Paint.Style.FILL
                p.color = accent
                c.drawCircle(x, y, min(colW, rowH) * 0.44f, p)
                tp.color = Color.WHITE
                tp.typeface = Face.BOLD
            } else {
                val weekend = ((first - 1 + col) % 7).let { it == 0 || it == 6 }
                tp.color = if (weekend) GREY else fg
                tp.typeface = Face.MEDIUM
            }
            c.drawText(d.toString(), x, centerY(tp, y), tp)
        }
    }

    private fun T_upper(cal: Calendar) = com.yash.standbytv.T.fmt(cal, "MMMM").uppercase()

    // ---------------------------------------------------------------- weather

    fun weatherIcon(c: Canvas, cx: Float, cy: Float, size: Float, kind: Weather.Kind, isDay: Boolean, bg: Int = Color.BLACK) {
        val s = size
        p.style = Paint.Style.FILL
        fun sun(x: Float, y: Float, r: Float) {
            if (isDay) {
                p.color = 0xFFFFD60A.toInt()
                c.drawCircle(x, y, r, p)
                p.strokeWidth = r * 0.16f; p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine(x + (cos(a) * r * 1.35).toFloat(), y + (sin(a) * r * 1.35).toFloat(),
                        x + (cos(a) * r * 1.7).toFloat(), y + (sin(a) * r * 1.7).toFloat(), p)
                }
            } else {
                p.color = 0xFFE5E5EA.toInt()
                c.drawCircle(x, y, r, p)
                p.color = bg
                c.drawCircle(x + r * 0.5f, y - r * 0.35f, r * 0.85f, p)
            }
        }
        fun cloud(x: Float, y: Float, w: Float, color: Int) {
            p.color = color
            path.reset()
            path.addCircle(x - w * 0.22f, y + w * 0.02f, w * 0.20f, Path.Direction.CW)
            path.addCircle(x + w * 0.02f, y - w * 0.10f, w * 0.27f, Path.Direction.CW)
            path.addCircle(x + w * 0.27f, y + w * 0.04f, w * 0.18f, Path.Direction.CW)
            path.addRoundRect(RectF(x - w * 0.42f, y, x + w * 0.45f, y + w * 0.22f), w * 0.11f, w * 0.11f, Path.Direction.CW)
            c.drawPath(path, p)
        }
        fun drops(color: Int, n: Int, long: Boolean) {
            p.color = color; p.strokeWidth = s * 0.045f; p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until n) {
                val x = cx - s * 0.25f + i * (s * 0.5f / (n - 1).coerceAtLeast(1))
                val y = cy + s * 0.28f
                c.drawLine(x, y, x - s * 0.06f, y + (if (long) s * 0.2f else s * 0.1f), p)
            }
        }
        when (kind) {
            Weather.Kind.CLEAR -> sun(cx, cy, s * 0.26f)
            Weather.Kind.PARTLY -> { sun(cx - s * 0.12f, cy - s * 0.14f, s * 0.2f); cloud(cx + s * 0.05f, cy + s * 0.06f, s * 0.75f, 0xFFF2F2F7.toInt()) }
            Weather.Kind.CLOUDY -> { cloud(cx - s * 0.12f, cy - s * 0.12f, s * 0.6f, 0xFF8E8E93.toInt()); cloud(cx + s * 0.06f, cy + s * 0.02f, s * 0.8f, 0xFFE5E5EA.toInt()) }
            Weather.Kind.FOG -> {
                cloud(cx, cy - s * 0.1f, s * 0.75f, 0xFFC7C7CC.toInt())
                p.color = 0xFFAEAEB2.toInt(); p.strokeWidth = s * 0.05f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - s * 0.35f, cy + s * 0.24f, cx + s * 0.3f, cy + s * 0.24f, p)
                c.drawLine(cx - s * 0.25f, cy + s * 0.36f, cx + s * 0.38f, cy + s * 0.36f, p)
            }
            Weather.Kind.DRIZZLE -> { cloud(cx, cy - s * 0.1f, s * 0.8f, 0xFFD1D1D6.toInt()); drops(0xFF64D2FF.toInt(), 3, false) }
            Weather.Kind.RAIN -> { cloud(cx, cy - s * 0.1f, s * 0.8f, 0xFFAEAEB2.toInt()); drops(0xFF0A84FF.toInt(), 4, true) }
            Weather.Kind.SNOW -> {
                cloud(cx, cy - s * 0.1f, s * 0.8f, 0xFFE5E5EA.toInt())
                p.color = Color.WHITE
                for (i in 0 until 4) c.drawCircle(cx - s * 0.24f + i * s * 0.16f, cy + s * 0.32f + (i % 2) * s * 0.08f, s * 0.04f, p)
            }
            Weather.Kind.THUNDER -> {
                cloud(cx, cy - s * 0.1f, s * 0.8f, 0xFF8E8E93.toInt())
                p.color = 0xFFFFD60A.toInt()
                path.reset()
                path.moveTo(cx + s * 0.02f, cy + s * 0.05f)
                path.lineTo(cx - s * 0.12f, cy + s * 0.3f)
                path.lineTo(cx, cy + s * 0.3f)
                path.lineTo(cx - s * 0.06f, cy + s * 0.5f)
                path.lineTo(cx + s * 0.14f, cy + s * 0.22f)
                path.lineTo(cx + s * 0.02f, cy + s * 0.22f)
                path.close()
                c.drawPath(path, p)
            }
        }
    }

    /** A weather card filling [rect]. */
    fun weatherPanel(c: Canvas, rect: RectF, w: Weather?, unit: String) {
        val pad = rect.width() * 0.08f
        tp.textAlign = Paint.Align.LEFT
        if (w == null) {
            tp.color = GREY; tp.typeface = Face.MEDIUM
            tp.textSize = rect.height() * 0.07f
            val msg = if (WeatherRepo.lastError != null) "Weather unavailable\nCheck internet or set a city" else "Loading weather…"
            var y = rect.centerY()
            for (line in msg.split("\n")) { c.drawText(line, rect.left + pad, y, tp); y += tp.textSize * 1.3f }
            return
        }
        tp.color = Color.WHITE; tp.typeface = Face.BOLD
        tp.textSize = rect.height() * 0.08f
        c.drawText(w.place.ifBlank { "Weather" }, rect.left + pad, rect.top + pad + tp.textSize, tp)

        tp.typeface = Face.LIGHT
        tp.textSize = rect.height() * 0.30f
        val tempY = rect.top + rect.height() * 0.55f
        c.drawText(WeatherRepo.temp(w.tempC, unit), rect.left + pad * 0.8f, tempY, tp)

        weatherIcon(c, rect.right - pad - rect.height() * 0.12f, rect.top + rect.height() * 0.24f, rect.height() * 0.26f, w.kind, w.isDay, PANEL)

        tp.typeface = Face.MEDIUM
        tp.textSize = rect.height() * 0.075f
        c.drawText(w.label, rect.left + pad, rect.bottom - pad - tp.textSize * 1.35f, tp)
        tp.color = GREY
        c.drawText("H:${WeatherRepo.temp(w.hiC, unit)}  L:${WeatherRepo.temp(w.loC, unit)}", rect.left + pad, rect.bottom - pad, tp)
    }
}
