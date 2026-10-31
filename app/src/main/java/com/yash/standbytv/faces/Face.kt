package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.View
import com.yash.standbytv.Prefs

/**
 * One full-screen StandBy "screen". Subclasses draw with Canvas and are re-drawn
 * automatically every [tickMs] (aligned to the wall clock) while [running].
 * D-pad UP/DOWN cycles [style] through [styleNames], like swiping vertically on iOS StandBy.
 */
abstract class Face(ctx: Context) : View(ctx) {
    abstract val id: String
    abstract val title: String
    abstract val styleNames: List<String>

    open val tickMs: Long = 1000L

    val prefs = Prefs(ctx)
    var running = false
        set(v) {
            field = v
            if (v) { onStart(); invalidate() } else onStop()
        }

    var style: Int = 0
        set(v) {
            val n = styleNames.size.coerceAtLeast(1)
            field = ((v % n) + n) % n
            onStyleChanged()
            invalidate()
        }

    /** Let a face handle remote keys first (e.g. music controls). Return true if consumed. */
    open fun onKey(e: android.view.KeyEvent): Boolean = false

    /** Used by the screenshot test: advance animations to a settled state. */
    open fun settleForPreview() {}

    open fun onStart() {}
    open fun onStop() {}
    open fun onStyleChanged() {}

    /** Draw the face. Canvas is already cleared to black. */
    protected abstract fun draw(c: Canvas, w: Float, h: Float)

    final override fun onDraw(canvas: Canvas) {
        draw(canvas, width.toFloat(), height.toFloat())
        if (running) {
            val now = System.currentTimeMillis()
            val delay = tickMs - (now % tickMs)
            postInvalidateDelayed(delay.coerceAtLeast(16L))
        }
    }

    companion object {
        val THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val BLACK: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val MONO: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
}
