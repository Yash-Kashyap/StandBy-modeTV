package com.yash.standbytv

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.yash.standbytv.faces.AnalogFace
import com.yash.standbytv.faces.AntsClockFace
import com.yash.standbytv.faces.ClockOfClocksFace
import com.yash.standbytv.faces.NeonClockFace
import com.yash.standbytv.faces.NowPlayingFace
import com.yash.standbytv.faces.PixelArtClockFace
import com.yash.standbytv.faces.QuotesFace
import com.yash.standbytv.faces.SegmentedClockFace
import com.yash.standbytv.faces.SolarWatchFace
import com.yash.standbytv.faces.BigClockFace
import com.yash.standbytv.faces.DuoFace
import com.yash.standbytv.faces.Face
import com.yash.standbytv.faces.FlipClockFace
import com.yash.standbytv.faces.MatrixFace
import com.yash.standbytv.faces.PhotoFace
import com.yash.standbytv.faces.WeatherClockFace
import com.yash.standbytv.faces.WordClockFace
import com.yash.standbytv.faces.WorldClockFace
import java.util.Calendar
import kotlin.random.Random

/**
 * Hosts the faces. Shared by the full-screen app and the screensaver.
 *  ◀ ▶  switch face      ▲ ▼  change style      OK / MENU  settings
 * Also handles burn-in pixel shift, night-mode tint and auto-rotation.
 */
class StandbyView(ctx: Context, private val isDream: Boolean) : FrameLayout(ctx) {

    companion object {
        fun allFaces(ctx: Context): List<Face> = listOf(
            DuoFace(ctx), BigClockFace(ctx), FlipClockFace(ctx), AnalogFace(ctx), WeatherClockFace(ctx),
            NowPlayingFace(ctx), PhotoFace(ctx), ClockOfClocksFace(ctx), AntsClockFace(ctx), SolarWatchFace(ctx),
            NeonClockFace(ctx), SegmentedClockFace(ctx), PixelArtClockFace(ctx), WordClockFace(ctx), MatrixFace(ctx),
            WorldClockFace(ctx), QuotesFace(ctx),
        )
    }

    private val prefs = Prefs(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val stage = FrameLayout(ctx)
    private val label = TextView(ctx)
    private val hint = TextView(ctx)

    private var faces: List<Face> = emptyList()
    private var idx = 0
    private var current: Face? = null
    private var started = false
    private var nightOn: Boolean? = null

    var onOpenSettings: (() -> Unit)? = null
    var onNeedPhotoPermission: ((String) -> Unit)? = null

    private val weatherListener: () -> Unit = { current?.invalidate() }

    init {
        setBackgroundColor(0xFF000000.toInt())
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        fun pill(tv: TextView, sizeSp: Float) {
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            tv.typeface = Face.MEDIUM
            tv.setPadding(dp(22), dp(10), dp(22), dp(10))
            tv.background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(0xCC1C1C1E.toInt()) }
            tv.alpha = 0f
        }
        pill(label, 18f)
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(28) })
        pill(hint, 14f)
        hint.text = "◀ ▶  Switch screen      ▲ ▼  Change style      OK  Settings"
        addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** (Re)build the face list from settings. */
    fun reload() {
        val all = allFaces(context)
        faces = all.filter { prefs.isFaceEnabled(it.id) }.ifEmpty { all }
        faces.filterIsInstance<PhotoFace>().forEach { pf -> pf.onNeedPermission = { onNeedPhotoPermission?.invoke(pf.permission) } }
        val wanted = if (isDream && prefs.dreamFace.isNotEmpty()) prefs.dreamFace else prefs.lastFace
        idx = faces.indexOfFirst { it.id == wanted }.coerceAtLeast(0)
        show(idx, announce = false)
    }

    fun start() {
        if (started) return
        started = true
        WeatherRepo.loadCache(context)
        WeatherRepo.addListener(weatherListener)
        WeatherRepo.refresh(context)
        current?.running = true
        handler.post(minuteTask)
        scheduleRotate()
        if (!isDream) flashHint()
    }

    fun stop() {
        started = false
        WeatherRepo.removeListener(weatherListener)
        handler.removeCallbacksAndMessages(null)
        current?.running = false
    }

    private fun show(i: Int, announce: Boolean) {
        if (faces.isEmpty()) return
        idx = ((i % faces.size) + faces.size) % faces.size
        val f = faces[idx]
        val old = current
        old?.running = false
        f.style = prefs.styleOf(f.id)
        if (f.parent == null) stage.addView(f, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        current = f
        if (old != null && old !== f) {
            f.alpha = 0f
            f.animate().alpha(1f).setDuration(260).withEndAction { stage.removeView(old) }.start()
        }
        f.running = started
        if (!isDream) prefs.lastFace = f.id
        applyNight(force = true)
        if (announce) toast("${f.title}  ·  ${f.styleNames.getOrElse(f.style) { "" }}")
    }

    private fun changeStyle(delta: Int) {
        val f = current ?: return
        f.style = f.style + delta
        prefs.setStyle(f.id, f.style)
        toast("${f.title}  ·  ${f.styleNames[f.style]}   (${f.style + 1}/${f.styleNames.size})")
    }

    fun handleKey(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        val ok = e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER
        if (ok && e.repeatCount > 0) {          // hold OK (~1 s) = Settings, from any screen
            if (e.repeatCount == 10) onOpenSettings?.invoke()
            return true
        }
        if (current?.onKey(e) == true) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> { show(idx + 1, true); scheduleRotate() }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> { show(idx - 1, true); scheduleRotate() }
            KeyEvent.KEYCODE_DPAD_UP -> changeStyle(+1)
            KeyEvent.KEYCODE_DPAD_DOWN -> changeStyle(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU ->
                onOpenSettings?.invoke() ?: return false
            else -> return false
        }
        if (!isDream) flashHint()
        return true
    }

    // ------------------------------------------------------------- overlays

    private fun toast(text: String) {
        label.text = text
        label.animate().cancel()
        label.alpha = 1f
        label.animate().alpha(0f).setStartDelay(1800).setDuration(500).start()
    }

    private fun flashHint() {
        val okLabel = if (current is NowPlayingFace) "OK  Music controls      Hold OK  Settings" else "OK  Settings"
        hint.text = "◀ ▶  Switch screen      ▲ ▼  Change style      $okLabel"
        hint.animate().cancel()
        hint.alpha = 1f
        hint.animate().alpha(0f).setStartDelay(3500).setDuration(600).start()
    }

    // ------------------------------------------------------------- timers

    /** Runs every minute: burn-in shift, night-mode check, weather refresh. */
    private val minuteTask = object : Runnable {
        override fun run() {
            if (prefs.burnIn) {
                val max = dp(14).toFloat()
                stage.animate().translationX((Random.nextFloat() * 2 - 1) * max)
                    .translationY((Random.nextFloat() * 2 - 1) * max).setDuration(3000).start()
            } else {
                stage.translationX = 0f; stage.translationY = 0f
            }
            applyNight(force = false)
            WeatherRepo.refresh(context)
            handler.postDelayed(this, 60_000L)
        }
    }

    private val rotateTask = Runnable { show(idx + 1, announce = false); scheduleRotate() }

    private fun scheduleRotate() {
        handler.removeCallbacks(rotateTask)
        val m = prefs.rotateMinutes
        if (started && m > 0 && faces.size > 1) handler.postDelayed(rotateTask, m * 60_000L)
    }

    /** Night mode: red monochrome (like iOS StandBy) or dimming, applied as a colour filter on a HW layer. */
    private fun applyNight(force: Boolean) {
        val night = prefs.isNightNow(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        if (!force && night == nightOn) return
        nightOn = night
        if (!night) {
            stage.setLayerType(View.LAYER_TYPE_NONE, null)
            return
        }
        val cm = if (prefs.nightStyle == 0) {
            val k = 0.85f
            ColorMatrix(floatArrayOf(
                0.30f * k, 0.59f * k, 0.11f * k, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ))
        } else ColorMatrix().apply { setScale(0.35f, 0.35f, 0.35f, 1f) }
        stage.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
    }
}
