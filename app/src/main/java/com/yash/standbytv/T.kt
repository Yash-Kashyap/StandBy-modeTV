package com.yash.standbytv

import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Time formatting helpers (digits always Latin so fonts render consistently). */
object T {
    fun now(): Calendar = Calendar.getInstance()
    fun now(tz: TimeZone): Calendar = Calendar.getInstance(tz)

    fun hour(c: Calendar, h24: Boolean): String {
        val h = c.get(Calendar.HOUR_OF_DAY)
        return if (h24) String.format(Locale.US, "%02d", h) else {
            val x = h % 12; (if (x == 0) 12 else x).toString()
        }
    }

    fun min(c: Calendar) = String.format(Locale.US, "%02d", c.get(Calendar.MINUTE))
    fun sec(c: Calendar) = String.format(Locale.US, "%02d", c.get(Calendar.SECOND))
    fun ampm(c: Calendar) = if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    fun hm(c: Calendar, h24: Boolean) = hour(c, h24) + ":" + min(c)

    fun fmt(c: Calendar, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = c.timeZone }.format(c.time)

    fun dateLong(c: Calendar) = fmt(c, "EEEE, d MMMM")
    fun dateShort(c: Calendar) = fmt(c, "EEE d MMM")
}

/** A colour scheme a face can be rendered in. */
data class Palette(
    val name: String,
    val bg1: Int, val bg2: Int,   // background gradient top / bottom
    val fg1: Int, val fg2: Int,   // text gradient start / end
    val accent: Int,
    val glow: Boolean = false
)

object Palettes {
    private fun c(hex: Long) = hex.toInt()

    val all = listOf(
        Palette("Classic", c(0xFF000000), c(0xFF000000), c(0xFFFFFFFF), c(0xFFD8D8D8), c(0xFFFF9F0A)),
        Palette("Sunset", c(0xFF14061F), c(0xFF000000), c(0xFFFF7E5F), c(0xFFFEB47B), c(0xFFFF7E5F)),
        Palette("Rajasthan", c(0xFF2A0A06), c(0xFF0A0201), c(0xFFFFB300), c(0xFFE8452C), c(0xFFFF6F00)),
        Palette("Ocean", c(0xFF001B2E), c(0xFF000814), c(0xFF4FC3F7), c(0xFF1DE9B6), c(0xFF4FC3F7)),
        Palette("Neon", c(0xFF05000A), c(0xFF000000), c(0xFFFF2BD6), c(0xFF00E5FF), c(0xFFFF2BD6), glow = true),
        Palette("Gold", c(0xFF0F0B05), c(0xFF000000), c(0xFFF6E27A), c(0xFFB8860B), c(0xFFD4AF37)),
        Palette("Sakura", c(0xFF1C0A12), c(0xFF000000), c(0xFFFFB7C5), c(0xFFFF6F91), c(0xFFFF8FAB)),
        Palette("Emerald", c(0xFF02140C), c(0xFF000000), c(0xFF50FA7B), c(0xFF0B8457), c(0xFF50FA7B)),
        Palette("Ruby Noir", c(0xFF120002), c(0xFF000000), c(0xFFFF3B3B), c(0xFF8B0000), c(0xFFFF3B3B)),
        Palette("Lavender", c(0xFF0E0A1F), c(0xFF000000), c(0xFFC3B1FF), c(0xFF7F5AF0), c(0xFFB39DFF)),
        Palette("Cyber", c(0xFF00040A), c(0xFF000000), c(0xFFFCEE0A), c(0xFF00F0FF), c(0xFFFCEE0A), glow = true),
        Palette("Mono", c(0xFF000000), c(0xFF000000), c(0xFF8E8E93), c(0xFF48484A), c(0xFF8E8E93)),
        Palette("Diwali", c(0xFF1F0A00), c(0xFF050100), c(0xFFFFD54F), c(0xFFFF6D00), c(0xFFFFB300), glow = true),
        Palette("Holi", c(0xFF14001A), c(0xFF020004), c(0xFFFF4FA3), c(0xFFFFE14D), c(0xFF4DD2FF)),
        Palette("Aurora", c(0xFF001A1F), c(0xFF02010A), c(0xFF5CFFB1), c(0xFF9B5CFF), c(0xFF5CFFB1), glow = true),
        Palette("Peacock", c(0xFF001A24), c(0xFF000508), c(0xFF00B3A4), c(0xFF1E5BFF), c(0xFF00D1C1)),
        Palette("Himalaya", c(0xFF0A1622), c(0xFF000000), c(0xFFFFFFFF), c(0xFF9CC9F5), c(0xFFBFE0FF)),
        Palette("Monsoon", c(0xFF0C1B1E), c(0xFF020607), c(0xFF8FD3C7), c(0xFF4A7C8C), c(0xFF8FD3C7)),
    )

    fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
