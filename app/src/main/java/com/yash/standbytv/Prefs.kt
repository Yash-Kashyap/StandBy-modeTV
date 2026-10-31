package com.yash.standbytv

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat

/** All user settings, backed by SharedPreferences. */
class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences("standby", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
        }
        e.apply()
    }

    var use24h: Boolean
        get() = if (sp.contains("use24h")) b("use24h", false) else DateFormat.is24HourFormat(app)
        set(v) = put("use24h", v)

    var showSeconds: Boolean
        get() = b("showSeconds", false)
        set(v) = put("showSeconds", v)

    /** "C" or "F" */
    var tempUnit: String
        get() = s("tempUnit", "C")
        set(v) = put("tempUnit", v)

    /** "auto" = locate by internet (IP), "city" = fixed city below */
    var locMode: String
        get() = s("locMode", "auto")
        set(v) = put("locMode", v)
    var cityName: String
        get() = s("cityName", "")
        set(v) = put("cityName", v)
    var cityLat: Float
        get() = sp.getFloat("cityLat", 0f)
        set(v) = put("cityLat", v)
    var cityLon: Float
        get() = sp.getFloat("cityLon", 0f)
        set(v) = put("cityLon", v)

    /** 0 = off, 1 = scheduled, 2 = always */
    var nightMode: Int
        get() = i("nightMode", 1)
        set(v) = put("nightMode", v)
    var nightStart: Int
        get() = i("nightStart", 22)
        set(v) = put("nightStart", v)
    var nightEnd: Int
        get() = i("nightEnd", 7)
        set(v) = put("nightEnd", v)
    /** 0 = red tint (like iOS StandBy), 1 = dim */
    var nightStyle: Int
        get() = i("nightStyle", 0)
        set(v) = put("nightStyle", v)

    var burnIn: Boolean
        get() = b("burnIn", true)
        set(v) = put("burnIn", v)

    /** minutes between automatic face changes, 0 = off */
    var rotateMinutes: Int
        get() = i("rotateMinutes", 0)
        set(v) = put("rotateMinutes", v)

    var openOnBoot: Boolean
        get() = b("openOnBoot", false)
        set(v) = put("openOnBoot", v)

    /** face id shown by the screensaver, "" = last used */
    var dreamFace: String
        get() = s("dreamFace", "")
        set(v) = put("dreamFace", v)

    /** 0 = TV storage + Google Photos, 1 = TV storage only, 2 = Google Photos only */
    var photoSource: Int
        get() = i("photoSource", 0)
        set(v) = put("photoSource", v)

    var lastFace: String
        get() = s("lastFace", "")
        set(v) = put("lastFace", v)

    fun styleOf(faceId: String) = i("style_$faceId", 0)
    fun setStyle(faceId: String, style: Int) = put("style_$faceId", style)

    fun isFaceEnabled(faceId: String) = b("face_on_$faceId", true)
    fun setFaceEnabled(faceId: String, on: Boolean) = put("face_on_$faceId", on)

    fun isNightNow(hour: Int): Boolean = when (nightMode) {
        2 -> true
        1 -> {
            val s = nightStart; val e = nightEnd
            if (s == e) false else if (s < e) hour in s until e else (hour >= s || hour < e)
        }
        else -> false
    }
}
