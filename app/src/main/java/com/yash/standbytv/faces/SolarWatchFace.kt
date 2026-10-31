package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.yash.standbytv.T
import com.yash.standbytv.WeatherRepo
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Where the sun is right now: an arc across the sky, or a 24-hour solar dial. Uses real sunrise/sunset. */
class SolarWatchFace(ctx: Context) : Face(ctx) {
    override val id = "solar"
    override val title = "Solar Watch"
    override val styleNames = listOf("Sun Arc", "24-hour Dial")

    private val sky = Paint()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val oval = RectF()
    private val stars = Array(70) { floatArrayOf(Random.nextFloat(), Random.nextFloat() * 0.55f, 0.5f + Random.nextFloat()) }

    /** minutes since midnight */
    private fun parse(hm: String?, def: Int): Int = runCatching {
        val (a, b) = hm!!.split(":"); a.toInt() * 60 + b.toInt()
    }.getOrDefault(def)

    private fun fmtMin(m: Int): String {
        val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, (m / 60) % 24); c.set(Calendar.MINUTE, m % 60)
        return T.hm(c, prefs.use24h) + if (prefs.use24h) "" else " " + T.ampm(c).lowercase()
    }

    private fun dur(m: Int) = "${m / 60}h ${m % 60}m"

    override fun draw(c: Canvas, w: Float, h: Float) {
        val cal = T.now()
        val nowM = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        val wx = WeatherRepo.current
        val rise = parse(wx?.sunrise, 6 * 60)
        val set = parse(wx?.sunset, 18 * 60)
        if (style == 0) arc(c, w, h, cal, nowM, rise, set) else dial(c, w, h, cal, nowM, rise, set)
    }

    private fun arc(c: Canvas, w: Float, h: Float, cal: Calendar, nowM: Float, rise: Int, set: Int) {
        val day = nowM >= rise && nowM <= set
        val t: Float
        val nightLen = 1440 - (set - rise)
        t = if (day) (nowM - rise) / (set - rise) else ((if (nowM > set) nowM - set else nowM + 1440 - set) / nightLen)
        val golden = day && (t < 0.1f || t > 0.9f)
        val top: Int; val bottom: Int
        when {
            !day -> { top = 0xFF03050F.toInt(); bottom = 0xFF14204A.toInt() }
            golden -> { top = 0xFF2C2A62.toInt(); bottom = 0xFFFF9A5A.toInt() }
            else -> { top = 0xFF1458A8.toInt(); bottom = 0xFF8FC8FF.toInt() }
        }
        val horizon = h * 0.64f
        sky.shader = LinearGradient(0f, 0f, 0f, horizon, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon, sky)
        p.shader = null; p.color = if (day) 0xFF0D1B2A.toInt() else 0xFF010205.toInt()
        c.drawRect(0f, horizon, w, h, p)
        if (!day) { p.color = Color.WHITE; for (s in stars) { p.alpha = (120 * s[2]).toInt(); c.drawCircle(s[0] * w, s[1] * h, h * 0.0016f * s[2], p) }; p.alpha = 255 }

        // the path of the sun (or moon) across the sky
        val left = w * 0.1f; val right = w * 0.9f; val peak = h * 0.14f
        oval.set(left, peak, right, horizon + (horizon - peak))
        dash.pathEffect = DashPathEffect(floatArrayOf(h * 0.006f, h * 0.014f), 0f)
        dash.strokeWidth = h * 0.004f; dash.color = 0x66FFFFFF
        c.drawArc(oval, 180f, 180f, false, dash)
        dash.pathEffect = null; dash.strokeWidth = h * 0.005f
        dash.color = if (day) 0xFFFFD166.toInt() else 0xFFCAD6F2.toInt()
        c.drawArc(oval, 180f, 180f * t, false, dash)

        val ang = Math.toRadians((180 + 180 * t).toDouble())
        val bx = oval.centerX() + (oval.width() / 2 * cos(ang)).toFloat()
        val by = oval.centerY() + (oval.height() / 2 * sin(ang)).toFloat()
        val br = h * 0.045f
        if (day) {
            p.shader = RadialGradient(bx, by, br * 3.2f, intArrayOf(0x88FFE08A.toInt(), 0x00FFE08A), null, Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, br * 3.2f, p); p.shader = null
            p.color = 0xFFFFD60A.toInt(); c.drawCircle(bx, by, br, p)
        } else {
            p.color = 0xFFE9EEF8.toInt(); c.drawCircle(bx, by, br * 0.85f, p)
            p.color = top; c.drawCircle(bx + br * 0.4f, by - br * 0.25f, br * 0.72f, p)
        }

        txt.typeface = LIGHT; txt.color = Color.WHITE
        Draw.fit(txt, "00:00", w * 0.4f, h * 0.2f)
        c.drawText(T.hm(cal, prefs.use24h), w / 2, horizon + h * 0.19f, txt)
        txt.typeface = MEDIUM; txt.textSize = h * 0.038f; txt.color = 0xFFB8C4D6.toInt()
        val left2 = if (day) "Daylight left ${dur((set - nowM).toInt())}" else "Sunrise in ${dur(((rise - nowM + 1440) % 1440).toInt())}"
        c.drawText("☀ ${fmtMin(rise)}     $left2     ☾ ${fmtMin(set)}", w / 2, horizon + h * 0.29f, txt)
    }

    private fun dial(c: Canvas, w: Float, h: Float, cal: Calendar, nowM: Float, rise: Int, set: Int) {
        c.drawColor(0xFF05070D.toInt())
        val cx = w / 2; val cy = h / 2; val r = h * 0.40f
        // 24h ring: noon at top, midnight at bottom
        fun angOf(m: Float) = (m / 1440f) * 360f + 90f   // canvas degrees (0 = 3 o'clock)
        oval.set(cx - r, cy - r, cx + r, cy + r)
        dash.pathEffect = null; dash.strokeWidth = h * 0.05f
        dash.color = 0xFF1A2342.toInt(); c.drawArc(oval, 0f, 360f, false, dash)
        dash.color = 0xFFF2A93B.toInt(); c.drawArc(oval, angOf(rise.toFloat()), (set - rise) / 1440f * 360f, false, dash)

        txt.typeface = MEDIUM; txt.textSize = h * 0.032f; txt.color = 0xFF8E8E93.toInt()
        for (hr in 0 until 24 step 3) {
            val a = Math.toRadians(angOf(hr * 60f).toDouble())
            val tr = r - h * 0.075f
            c.drawText(hr.toString(), cx + (cos(a) * tr).toFloat(), Draw.centerY(txt, cy + (sin(a) * tr).toFloat()), txt)
        }
        val a = Math.toRadians(angOf(nowM).toDouble())
        val day = nowM >= rise && nowM <= set
        p.color = if (day) 0xFFFFD60A.toInt() else 0xFFE5E9F5.toInt()
        dash.strokeWidth = h * 0.006f; dash.color = p.color
        val r0 = r * 0.62f   // start the hand outside the centre text
        c.drawLine(cx + (cos(a) * r0).toFloat(), cy + (sin(a) * r0).toFloat(), cx + (cos(a) * (r - h * 0.03f)).toFloat(), cy + (sin(a) * (r - h * 0.03f)).toFloat(), dash)
        c.drawCircle(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), h * 0.035f, p)

        txt.typeface = LIGHT; txt.color = Color.WHITE; txt.textSize = h * 0.11f
        c.drawText(T.hm(cal, prefs.use24h), cx, cy - h * 0.06f, txt)
        txt.typeface = MEDIUM; txt.textSize = h * 0.032f; txt.color = 0xFFB8C4D6.toInt()
        c.drawText("Daylight ${dur(set - rise)}", cx, cy + h * 0.06f, txt)
        c.drawText("☀ ${fmtMin(rise)}   ☾ ${fmtMin(set)}", cx, cy + h * 0.11f, txt)
    }
}
