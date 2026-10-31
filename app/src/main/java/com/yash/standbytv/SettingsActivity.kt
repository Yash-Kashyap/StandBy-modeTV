package com.yash.standbytv

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yash.standbytv.faces.Face
import kotlin.concurrent.thread

/** D-pad friendly settings. Focused row gets a white border. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val rows = mutableListOf<Pair<TextView, () -> String>>()
    private val main = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(64), dp(32), dp(64), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "StandBy TV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Face.BOLD
        })
        root.addView(TextView(this).apply {
            text = "◀ ▶ switch screens · ▲ ▼ change style · OK opens this menu (hold OK on Now Playing)"
            setTextColor(0xFF8E8E93.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, dp(16))
        })
        val scroll = ScrollView(this).apply { isFocusable = false; clipToPadding = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(24)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        build()
    }

    private fun build() {
        list.removeAllViews(); rows.clear()

        section("Clock")
        row("Time format", { if (prefs.use24h) "24-hour" else "12-hour" }) { prefs.use24h = !prefs.use24h }
        row("Show seconds", { onOff(prefs.showSeconds) }) { prefs.showSeconds = !prefs.showSeconds }

        section("Weather")
        row("Temperature", { if (prefs.tempUnit == "C") "°C" else "°F" }) { prefs.tempUnit = if (prefs.tempUnit == "C") "F" else "C" }
        row("Location", { if (prefs.locMode == "city" && prefs.cityName.isNotBlank()) prefs.cityName else "Automatic (internet)" }) { pickLocation() }
        row("Refresh weather now", { WeatherRepo.current?.let { "${it.place} · ${WeatherRepo.temp(it.tempC, prefs.tempUnit)}" } ?: "" }) {
            WeatherRepo.refresh(this, force = true)
            Toast.makeText(this, "Updating weather…", Toast.LENGTH_SHORT).show()
            main.postDelayed({ refresh() }, 4000)
        }

        section("Night mode")
        row("Night mode", { arrayOf("Off", "Scheduled", "Always on")[prefs.nightMode] }) { prefs.nightMode = (prefs.nightMode + 1) % 3 }
        row("Night starts", { hourLabel(prefs.nightStart) }) {
            val opts = intArrayOf(18, 19, 20, 21, 22, 23, 0, 1, 2)
            prefs.nightStart = opts[(opts.indexOf(prefs.nightStart) + 1) % opts.size]
        }
        row("Night ends", { hourLabel(prefs.nightEnd) }) {
            val opts = intArrayOf(5, 6, 7, 8, 9, 10)
            prefs.nightEnd = opts[(opts.indexOf(prefs.nightEnd) + 1) % opts.size]
        }
        row("Night look", { if (prefs.nightStyle == 0) "Red tint" else "Dimmed" }) { prefs.nightStyle = 1 - prefs.nightStyle }

        section("Screen")
        row("Burn-in protection (pixel shift)", { onOff(prefs.burnIn) }) { prefs.burnIn = !prefs.burnIn }
        row("Auto-rotate screens", { if (prefs.rotateMinutes == 0) "Off" else "Every ${prefs.rotateMinutes} min" }) {
            val opts = intArrayOf(0, 1, 5, 15, 30, 60)
            prefs.rotateMinutes = opts[(opts.indexOf(prefs.rotateMinutes) + 1) % opts.size]
        }
        row("Open when TV starts", { onOff(prefs.openOnBoot) }) { prefs.openOnBoot = !prefs.openOnBoot }

        section("Photos")
        row("Google Photos albums", {
            val a = com.yash.standbytv.gphotos.GPhotos.albums(this)
            if (a.isEmpty()) "Add" else "${a.count { it.enabled }} of ${a.size} shown"
        }) { startActivity(Intent(this, GPhotosActivity::class.java)) }
        row("Slideshow source", { arrayOf("TV storage + Google Photos", "TV storage only", "Google Photos only")[prefs.photoSource] }) {
            prefs.photoSource = (prefs.photoSource + 1) % 3
        }

        section("Music")
        row("Music controls access", { if (hasMediaAccess()) "Allowed" else "Not allowed" }) { openMediaAccess() }

        val faces = StandbyView.allFaces(this)
        section("Screensaver")
        row("Screensaver shows", { faces.firstOrNull { it.id == prefs.dreamFace }?.title ?: "Last used screen" }) {
            val ids = listOf("") + faces.map { it.id }
            prefs.dreamFace = ids[(ids.indexOf(prefs.dreamFace) + 1) % ids.size]
        }
        row("Set as TV screensaver", { "" }) { openScreensaverSettings() }

        section("Screens")
        for (f in faces) {
            row(f.title, { if (prefs.isFaceEnabled(f.id)) "Shown" else "Hidden" }) {
                val on = prefs.isFaceEnabled(f.id)
                if (on && faces.count { prefs.isFaceEnabled(it.id) } <= 1) {
                    Toast.makeText(this, "Keep at least one screen", Toast.LENGTH_SHORT).show()
                } else prefs.setFaceEnabled(f.id, !on)
            }
        }

        section("About")
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrDefault("")
        row("StandBy TV $ver", { "by Yash Kashyap" }) {}

        (list.getChildAt(1) as? View)?.requestFocus()
    }

    private fun onOff(b: Boolean) = if (b) "On" else "Off"
    private fun hourLabel(h: Int) = if (prefs.use24h) String.format("%02d:00", h) else {
        val x = h % 12; "${if (x == 0) 12 else x}:00 ${if (h < 12) "AM" else "PM"}"
    }

    private fun refresh() { for ((tv, v) in rows) tv.text = v() }

    private fun section(title: String) {
        list.addView(TextView(this).apply {
            text = title.uppercase()
            setTextColor(0xFFFF9F0A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Face.BOLD
            letterSpacing = 0.08f
            setPadding(dp(18), dp(18), 0, dp(6))
        })
    }

    private fun row(title: String, value: () -> String, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rowBg()
        }
        val t = TextView(this).apply {
            text = title; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); typeface = Face.MEDIUM
        }
        val v = TextView(this).apply {
            text = value(); setTextColor(0xFFAEAEB2.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.END
        }
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.setOnClickListener { onClick(); refresh() }
        r.setOnFocusChangeListener { view, has -> view.animate().scaleX(if (has) 1.015f else 1f).scaleY(if (has) 1.015f else 1f).setDuration(120).start() }
        rows += v to value
        list.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    private fun rowBg(): StateListDrawable {
        val focused = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(0xFF2C2C2E.toInt()); setStroke(dp(3), Color.WHITE)
        }
        val normal = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF141416.toInt()) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun dialogBuilder() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun pickLocation() {
        dialogBuilder().setTitle("Weather location")
            .setItems(arrayOf("Automatic (from internet connection)", "Enter a city…")) { _, which ->
                if (which == 0) {
                    prefs.locMode = "auto"
                    prefs.sp.edit().remove("ip_loc_time").apply()
                    WeatherRepo.refresh(this, force = true)
                    refresh()
                } else enterCity()
            }.show()
    }

    private fun enterCity() {
        val input = EditText(this).apply {
            hint = "e.g. Nahan or Mumbai"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(prefs.cityName.substringBefore(","))
        }
        dialogBuilder().setTitle("City").setView(input)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                Toast.makeText(this, "Searching $q…", Toast.LENGTH_SHORT).show()
                thread {
                    val r = runCatching { WeatherRepo.geocode(q) }.getOrNull()
                    main.post {
                        if (r == null) {
                            Toast.makeText(this, "Couldn't find \"$q\"", Toast.LENGTH_LONG).show()
                        } else {
                            prefs.locMode = "city"
                            prefs.cityName = r.third
                            prefs.cityLat = r.first.toFloat()
                            prefs.cityLon = r.second.toFloat()
                            WeatherRepo.refresh(this, force = true)
                            Toast.makeText(this, "Location set to ${r.third}", Toast.LENGTH_SHORT).show()
                            refresh()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) refresh() }

    private fun hasMediaAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    /** Needed for the Now Playing screen to see Spotify / YouTube Music / YouTube. */
    private fun openMediaAccess() {
        try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); return } catch (_: Exception) {}
        val comp = "$packageName/${MediaListener::class.java.name}"
        dialogBuilder().setTitle("Allow music controls")
            .setMessage(
                "Android TV hides this setting. From a computer on the same Wi-Fi, run:\n\n" +
                    "adb connect <TV-IP>\n" +
                    "adb shell cmd notification allow_listener $comp\n\n" +
                    "On Android 8.0, use instead:\n" +
                    "adb shell settings put secure enabled_notification_listeners $comp"
            ).setPositiveButton("OK", null).show()
    }

    private fun openScreensaverSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent("android.settings.DREAM_SETTINGS"),
        )
        for (i in intents) {
            try { startActivity(i); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
        }
        dialogBuilder().setTitle("Set screensaver with ADB")
            .setMessage(
                "This TV hides the screensaver picker. From a computer on the same Wi-Fi, run:\n\n" +
                    "adb connect <TV-IP>\n" +
                    "adb shell settings put secure screensaver_components $packageName/.StandbyDream\n" +
                    "adb shell settings put secure screensaver_enabled 1"
            ).setPositiveButton("OK", null).show()
    }
}
