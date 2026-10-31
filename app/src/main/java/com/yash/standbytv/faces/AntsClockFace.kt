package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.yash.standbytv.T
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hundreds of little ants walk into place to form the digits. When the minute changes
 * they wander off and march into the new time. Styles set the "weather" scene.
 */
class AntsClockFace(ctx: Context) : Face(ctx) {
    override val id = "ants"
    override val title = "Ants Clock"
    override val styleNames = listOf("Sunny", "Night", "Rainy", "Snowy", "AMOLED")
    override val tickMs = 33L

    private val ANTS = 760
    private val MW = 192; private val MH = 108          // low-res mask for digit shapes
    private val ax = FloatArray(ANTS); private val ay = FloatArray(ANTS)
    private val tx = FloatArray(ANTS); private val ty = FloatArray(ANTS)
    private val heading = FloatArray(ANTS); private val speed = FloatArray(ANTS)
    private val wander = FloatArray(ANTS)
    private var shownKey = ""
    private var lastFrame = 0L
    private var lw = 0f; private var lh = 0f

    private val mask = Bitmap.createBitmap(MW, MH, Bitmap.Config.ALPHA_8)
    private val maskCanvas = Canvas(mask)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = BLACK; textAlign = Paint.Align.CENTER; color = Color.BLACK }
    private val pix = ByteArray(MW * MH)

    private val bg = Paint()
    private var bgKey = ""
    private val ant = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = MEDIUM }
    private val dropX = FloatArray(140) { Random.nextFloat() }
    private val dropY = FloatArray(140) { Random.nextFloat() }

    private class Scene(val top: Int, val bottom: Int, val ant: Int, val text: Int)
    private val scenes = arrayOf(
        Scene(0xFFF6E2B3.toInt(), 0xFFDDB46F.toInt(), 0xFF2A1A0C.toInt(), 0xFF6B4A25.toInt()),
        Scene(0xFF0E1630.toInt(), 0xFF02040A.toInt(), 0xFFD9CDB8.toInt(), 0xFF8D97B5.toInt()),
        Scene(0xFF56636F.toInt(), 0xFF262D35.toInt(), 0xFF101418.toInt(), 0xFFC9D3DD.toInt()),
        Scene(0xFFEEF3F8.toInt(), 0xFFC9D6E2.toInt(), 0xFF1E2329.toInt(), 0xFF5D6B78.toInt()),
        Scene(Color.BLACK, Color.BLACK, 0xFFA0A0A0.toInt(), 0xFF6E6E73.toInt()),
    )

    init {
        for (i in 0 until ANTS) {
            ax[i] = Random.nextFloat(); ay[i] = Random.nextFloat()  // normalised until first layout
            heading[i] = Random.nextFloat() * 6.283f
            speed[i] = 0.75f + Random.nextFloat() * 0.6f
            wander[i] = Random.nextFloat() * 6.283f
        }
    }

    /** Draw the time into the mask, pick lit pixels as targets and hand them out to ants. */
    private fun retarget(w: Float, h: Float, text: String) {
        mask.eraseColor(Color.TRANSPARENT)
        Draw.fit(maskPaint, if (prefs.use24h) "00:00" else "10:00", MW * 0.92f, MH * 0.72f)
        maskCanvas.drawText(text, MW / 2f, Draw.centerY(maskPaint, MH * 0.46f), maskPaint)
        mask.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(pix))
        val pts = ArrayList<Int>()
        for (i in pix.indices) if ((pix[i].toInt() and 0xFF) > 140) pts += i
        if (pts.isEmpty()) return
        pts.shuffle()
        val sx = w / MW; val sy = h / MH
        for (i in 0 until ANTS) {
            val p = pts[i % pts.size]
            tx[i] = (p % MW + Random.nextFloat()) * sx
            ty[i] = (p / MW + Random.nextFloat()) * sy
        }
    }

    private fun step(dt: Float, w: Float, h: Float) {
        val base = h * 0.16f
        val near = w / MW * 0.9f
        for (i in 0 until ANTS) {
            val dx = tx[i] - ax[i]; val dy = ty[i] - ay[i]
            val d = hypot(dx, dy)
            val want: Float
            val v: Float
            if (d > near) {
                wander[i] += (Random.nextFloat() - 0.5f) * 1.2f * dt
                want = atan2(dy, dx) + sin(wander[i]) * 0.35f
                v = base * speed[i] * (if (d < near * 6) 0.5f else 1f)
            } else {
                // arrived: idle jitter around the target
                wander[i] += (Random.nextFloat() - 0.5f) * 6f * dt
                want = heading[i] + (Random.nextFloat() - 0.5f) * 2f * dt + sin(wander[i]) * 0.05f
                v = base * 0.04f
            }
            var diff = want - heading[i]
            while (diff > Math.PI) diff -= 6.2832f
            while (diff < -Math.PI) diff += 6.2832f
            heading[i] += diff * (6f * dt).coerceAtMost(1f)
            ax[i] += cos(heading[i]) * v * dt
            ay[i] += sin(heading[i]) * v * dt
        }
    }

    override fun settleForPreview() {
        val w = 1920f; val h = 1080f
        layoutAnts(w, h)
        retarget(w, h, T.hm(T.now(), prefs.use24h)); shownKey = T.hm(T.now(), prefs.use24h)
        repeat(500) { step(0.05f, w, h) }
        lastFrame = 0
    }

    private fun layoutAnts(w: Float, h: Float) {
        if (lw == w && lh == h) return
        if (lw == 0f) for (i in 0 until ANTS) { ax[i] *= w; ay[i] *= h }
        else for (i in 0 until ANTS) { ax[i] = ax[i] / lw * w; ay[i] = ay[i] / lh * h }
        lw = w; lh = h
        shownKey = ""
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val sc = scenes[style]
        val k = "$style:$h"
        if (k != bgKey) { bgKey = k; bg.shader = LinearGradient(0f, 0f, 0f, h, sc.top, sc.bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, bg)
        layoutAnts(w, h)

        val cal = T.now()
        val key = T.hm(cal, prefs.use24h)
        if (key != shownKey) { shownKey = key; retarget(w, h, key) }

        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) 0.033f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now
        if (running) step(dt, w, h)

        // ants: abdomen, thorax, head along the heading
        val s = h * 0.0042f
        ant.color = sc.ant
        for (i in 0 until ANTS) {
            val cx = cos(heading[i]); val cy = sin(heading[i])
            c.drawCircle(ax[i] - cx * s * 1.7f, ay[i] - cy * s * 1.7f, s * 1.05f, ant)
            c.drawCircle(ax[i], ay[i], s * 0.62f, ant)
            c.drawCircle(ax[i] + cx * s * 1.2f, ay[i] + cy * s * 1.2f, s * 0.7f, ant)
        }

        when (style) {
            2 -> { // rain streaks
                fx.color = 0x55D8E6F2; fx.strokeWidth = h * 0.0022f
                for (i in dropX.indices) {
                    dropY[i] = (dropY[i] + dt * 1.6f) % 1f
                    val x = dropX[i] * w; val y = dropY[i] * h
                    c.drawLine(x, y, x - h * 0.008f, y + h * 0.035f, fx)
                }
            }
            3 -> { // snowflakes
                fx.color = Color.WHITE
                for (i in dropX.indices) {
                    dropY[i] = (dropY[i] + dt * 0.08f * (0.5f + (i % 5) * 0.2f)) % 1f
                    val x = (dropX[i] + sin(dropY[i] * 12f + i) * 0.01f) * w
                    c.drawCircle(x, dropY[i] * h, h * (0.002f + (i % 3) * 0.0012f), fx)
                }
            }
            1 -> { // a few stars
                fx.color = 0x88FFFFFF.toInt()
                for (i in 0 until 50) c.drawCircle(dropX[i] * w, dropY[i] * h * 0.3f, h * 0.0015f, fx)
            }
        }

        txt.color = sc.text
        txt.textSize = h * 0.04f
        val sub = (if (prefs.use24h) "" else T.ampm(cal) + "  ·  ") + T.dateLong(cal)
        c.drawText(sub.uppercase(), w / 2, h * 0.9f, txt)
    }
}
