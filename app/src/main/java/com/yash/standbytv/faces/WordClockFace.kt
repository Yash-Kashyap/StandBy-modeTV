package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yash.standbytv.Palettes
import com.yash.standbytv.T
import java.util.Calendar

/** "IT IS HALF PAST TEN" on a glowing letter grid. */
class WordClockFace(ctx: Context) : Face(ctx) {
    override val id = "words"
    override val title = "Word Clock"
    override val styleNames = listOf("White", "Amber", "Cyan", "Mint", "Rose")
    private val accents = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFB000.toInt(), 0xFF64D2FF.toInt(), 0xFF66FFB2.toInt(), 0xFFFF6FA5.toInt()
    )

    private val grid = arrayOf(
        "ITLISASAMPM",
        "ACQUARTERDC",
        "TWENTYFIVEX",
        "HALFSTENFTO",
        "PASTERUNINE",
        "ONESIXTHREE",
        "FOURFIVETWO",
        "EIGHTELEVEN",
        "SEVENTWELVE",
        "TENSEOCLOCK",
    )

    /** word -> (row, startCol, endColInclusive) */
    private val W = mapOf(
        "IT" to intArrayOf(0, 0, 1), "IS" to intArrayOf(0, 3, 4),
        "A" to intArrayOf(1, 0, 0), "QUARTER" to intArrayOf(1, 2, 8),
        "TWENTY" to intArrayOf(2, 0, 5), "FIVE_M" to intArrayOf(2, 6, 9),
        "HALF" to intArrayOf(3, 0, 3), "TEN_M" to intArrayOf(3, 5, 7), "TO" to intArrayOf(3, 9, 10),
        "PAST" to intArrayOf(4, 0, 3), "NINE" to intArrayOf(4, 7, 10),
        "ONE" to intArrayOf(5, 0, 2), "SIX" to intArrayOf(5, 3, 5), "THREE" to intArrayOf(5, 6, 10),
        "FOUR" to intArrayOf(6, 0, 3), "FIVE" to intArrayOf(6, 4, 7), "TWO" to intArrayOf(6, 8, 10),
        "EIGHT" to intArrayOf(7, 0, 4), "ELEVEN" to intArrayOf(7, 5, 10),
        "SEVEN" to intArrayOf(8, 0, 4), "TWELVE" to intArrayOf(8, 5, 10),
        "TEN" to intArrayOf(9, 0, 2), "OCLOCK" to intArrayOf(9, 5, 10),
    )
    private val hours = arrayOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN")

    private val lit = Array(10) { BooleanArray(11) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = MEDIUM }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun words(cal: Calendar): List<String> {
        val m = cal.get(Calendar.MINUTE)
        var h = cal.get(Calendar.HOUR)
        val m5 = m / 5
        val out = mutableListOf("IT", "IS")
        when (m5) {
            0 -> {}
            1 -> out += listOf("FIVE_M", "PAST")
            2 -> out += listOf("TEN_M", "PAST")
            3 -> out += listOf("A", "QUARTER", "PAST")
            4 -> out += listOf("TWENTY", "PAST")
            5 -> out += listOf("TWENTY", "FIVE_M", "PAST")
            6 -> out += listOf("HALF", "PAST")
            7 -> out += listOf("TWENTY", "FIVE_M", "TO")
            8 -> out += listOf("TWENTY", "TO")
            9 -> out += listOf("A", "QUARTER", "TO")
            10 -> out += listOf("TEN_M", "TO")
            11 -> out += listOf("FIVE_M", "TO")
        }
        if (m5 >= 7) h = (h + 1) % 12
        out += hours[h]
        if (m5 == 0) out += "OCLOCK"
        return out
    }

    override fun draw(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.BLACK)
        val accent = accents[style]
        val cal = T.now()
        for (row in lit) row.fill(false)
        for (word in words(cal)) {
            val (r, s, e) = W.getValue(word)
            for (col in s..e) lit[r][col] = true
        }
        val cell = minOf(w * 0.86f / 11f, h * 0.84f / 10f)
        val gx = (w - cell * 11) / 2f
        val gy = (h - cell * 10) / 2f
        p.textSize = cell * 0.55f
        for (r in 0 until 10) for (col in 0 until 11) {
            val on = lit[r][col]
            p.color = if (on) accent else 0x26FFFFFF
            if (on) p.setShadowLayer(cell * 0.25f, 0f, 0f, Palettes.withAlpha(accent, 160)) else p.clearShadowLayer()
            p.typeface = if (on) BOLD else MEDIUM
            c.drawText(grid[r][col].toString(), gx + cell * (col + 0.5f), Draw.centerY(p, gy + cell * (r + 0.5f)), p)
        }
        p.clearShadowLayer()
        // one corner dot per extra minute past the 5-minute step
        val extra = cal.get(Calendar.MINUTE) % 5
        val dr = cell * 0.09f
        val pts = arrayOf(floatArrayOf(gx - cell * 0.3f, gy - cell * 0.3f), floatArrayOf(gx + cell * 11.3f, gy - cell * 0.3f),
            floatArrayOf(gx + cell * 11.3f, gy + cell * 10.3f), floatArrayOf(gx - cell * 0.3f, gy + cell * 10.3f))
        for (i in 0 until 4) {
            dot.color = if (i < extra) accent else 0x26FFFFFF
            c.drawCircle(pts[i][0], pts[i][1], dr, dot)
        }
    }
}
