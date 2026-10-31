package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yash.standbytv.T
import kotlin.math.cos
import kotlin.math.sin

/**
 * 75 little analog clocks whose hands line up to draw the time.
 * Every minute the hands spin (one clockwise, one anticlockwise) into the new digits.
 */
class ClockOfClocksFace(ctx: Context) : Face(ctx) {
    override val id = "clocks"
    override val title = "Clock of Clocks"
    override val styleNames = listOf("Dark", "Light", "Blueprint", "Copper")
    override val tickMs = 33L

    private val COLS = 15
    private val ROWS = 5
    private val N = COLS * ROWS

    // 3x5 digit font. Each lit cell's hands point to its lit neighbours.
    private val font = arrayOf(
        arrayOf("111", "101", "101", "101", "111"),
        arrayOf("110", "010", "010", "010", "111"),
        arrayOf("111", "001", "111", "100", "111"),
        arrayOf("111", "001", "111", "001", "111"),
        arrayOf("101", "101", "111", "001", "001"),
        arrayOf("111", "100", "111", "001", "111"),
        arrayOf("111", "100", "111", "101", "111"),
        arrayOf("111", "001", "001", "001", "001"),
        arrayOf("111", "101", "111", "101", "111"),
        arrayOf("111", "101", "111", "001", "111"),
    )
    /** Column layout: digit, gap, digit, colon, digit, gap, digit */
    private val glyphStart = intArrayOf(0, 4, 8, 11)
    private val COLON_COL = 7

    private val from1 = FloatArray(N); private val from2 = FloatArray(N)
    private val to1 = FloatArray(N); private val to2 = FloatArray(N)
    private val cur1 = FloatArray(N); private val cur2 = FloatArray(N)
    private val lit = BooleanArray(N)
    private var animStart = 0L
    private var shownKey = ""
    private var initialised = false
    private val DUR = 2600L
    private val STAGGER = 70L

    private val dial = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = MEDIUM }

    private class Scheme(val bg: Int, val dial: Int, val ring: Int, val hand: Int, val idle: Int, val text: Int)
    private val schemes = arrayOf(
        Scheme(Color.BLACK, 0xFF161618.toInt(), 0xFF26262A.toInt(), Color.WHITE, 0x40FFFFFF, 0xFF8E8E93.toInt()),
        Scheme(0xFFECECEC.toInt(), Color.WHITE, 0xFFD6D6D6.toInt(), 0xFF111111.toInt(), 0x30000000, 0xFF555555.toInt()),
        Scheme(0xFF0B2545.toInt(), 0xFF0E2E57.toInt(), 0xFF3E6AA0.toInt(), 0xFFEAF2FF.toInt(), 0x40EAF2FF, 0xFF9DB6D6.toInt()),
        Scheme(0xFF140B06.toInt(), 0xFF22140C.toInt(), 0xFF6B3E1F.toInt(), 0xFFF0A868.toInt(), 0x40F0A868, 0xFFB07A50.toInt()),
    )

    private fun neighbourAngles(g: Array<String>, r: Int, c: Int): Pair<Float, Float> {
        fun on(rr: Int, cc: Int) = rr in 0..4 && cc in 0..2 && g[rr][cc] == '1'
        val dirs = mutableListOf<Float>()
        val up = on(r - 1, c); val down = on(r + 1, c); val left = on(r, c - 1); val right = on(r, c + 1)
        // vertical strokes win at T-junctions so the digit outlines stay closed
        if (up) dirs += 0f
        if (down) dirs += 180f
        if (dirs.size < 2 && right) dirs += 90f
        if (dirs.size < 2 && left) dirs += 270f
        return when (dirs.size) {
            0 -> 0f to 180f
            1 -> dirs[0] to dirs[0]
            else -> dirs[0] to dirs[1]
        }
    }

    private fun targets(text: String) {
        // idle position: both hands at "7:30"
        for (i in 0 until N) { to1[i] = 225f; to2[i] = 225f; lit[i] = false }
        val digits = text.filter { it.isDigit() }.padStart(4, '0')
        for (d in 0 until 4) {
            val g = font[digits[d] - '0']
            if (d == 0 && digits[0] == '0' && !prefs.use24h) continue // hide leading zero in 12h mode
            for (r in 0 until ROWS) for (c in 0 until 3) {
                if (g[r][c] != '1') continue
                val i = r * COLS + glyphStart[d] + c
                val (a, b) = neighbourAngles(g, r, c)
                to1[i] = a; to2[i] = b; lit[i] = true
            }
        }
        // colon column stays idle — the gap between hours and minutes reads cleaner than slashes
    }

    private fun startAnim(now: Long) {
        for (i in 0 until N) {
            from1[i] = cur1[i]; from2[i] = cur2[i]
            // hand 1 spins clockwise, hand 2 anticlockwise, each at least one full turn
            val d1 = ((to1[i] - from1[i]) % 360 + 360) % 360
            to1[i] = from1[i] + d1 + 360f
            val d2 = ((from2[i] - to2[i]) % 360 + 360) % 360
            to2[i] = from2[i] - d2 - 360f
        }
        animStart = now
    }

    private fun ease(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - Math.pow((-2.0 * t + 2), 3.0).toFloat() / 2

    override fun settleForPreview() {
        val cal = T.now()
        targets(T.hour(cal, prefs.use24h).padStart(2, '0') + T.min(cal))
        for (i in 0 until N) { cur1[i] = to1[i]; cur2[i] = to2[i] }
        initialised = true; shownKey = T.hm(cal, prefs.use24h); animStart = 0
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        val sc = schemes[style]
        c.drawColor(sc.bg)
        val cal = T.now()
        val key = T.hm(cal, prefs.use24h)
        val now = System.currentTimeMillis()
        if (key != shownKey) {
            shownKey = key
            targets(T.hour(cal, prefs.use24h).padStart(2, '0') + T.min(cal))
            if (!initialised) {
                for (i in 0 until N) { cur1[i] = to1[i]; cur2[i] = to2[i]; from1[i] = to1[i]; from2[i] = to2[i] }
                initialised = true
                animStart = 0
            } else startAnim(now)
        }

        val cell = minOf(w * 0.95f / COLS, h * 0.80f / ROWS)
        val gx = (w - cell * COLS) / 2f
        val gy = (h - cell * ROWS) / 2f - h * 0.03f
        val r = cell * 0.47f
        dial.color = sc.dial
        ring.color = sc.ring; ring.strokeWidth = cell * 0.02f
        hand.strokeWidth = cell * 0.075f

        for (row in 0 until ROWS) for (col in 0 until COLS) {
            val i = row * COLS + col
            if (animStart > 0) {
                val t = ((now - animStart - col * STAGGER).toFloat() / DUR).coerceIn(0f, 1f)
                val e = ease(t)
                cur1[i] = from1[i] + (to1[i] - from1[i]) * e
                cur2[i] = from2[i] + (to2[i] - from2[i]) * e
            }
            val cx = gx + cell * (col + 0.5f)
            val cy = gy + cell * (row + 0.5f)
            c.drawCircle(cx, cy, r, dial)
            c.drawCircle(cx, cy, r, ring)
            hand.color = if (lit[i]) sc.hand else sc.idle
            val len = r * 0.98f
            for (a in floatArrayOf(cur1[i], cur2[i])) {
                val rad = Math.toRadians(a.toDouble())
                c.drawLine(cx, cy, cx + (sin(rad) * len).toFloat(), cy - (cos(rad) * len).toFloat(), hand)
            }
        }
        if (animStart > 0 && now - animStart > DUR + COLS * STAGGER) {
            for (i in 0 until N) { cur1[i] = ((to1[i] % 360) + 360) % 360; cur2[i] = ((to2[i] % 360) + 360) % 360 }
            animStart = 0
        }

        txt.color = sc.text
        txt.textSize = h * 0.04f
        val sub = (if (prefs.use24h) "" else T.ampm(cal) + "  ·  ") + T.dateLong(cal)
        c.drawText(sub.uppercase(), w / 2, gy + cell * ROWS + (h - gy - cell * ROWS) * 0.55f, txt)
    }
}
