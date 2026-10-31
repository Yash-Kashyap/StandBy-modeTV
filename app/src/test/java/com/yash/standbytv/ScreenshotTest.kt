package com.yash.standbytv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen + style to PNG at 1920x1080 so designs can be checked without a TV.
 * Run:  ./gradlew testReleaseUnitTest --tests '*ScreenshotTest*'   → output in /screenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {

    @Test
    fun renderAll() {
        val ctx = RuntimeEnvironment.getApplication()
        WeatherRepo.current = Weather(
            place = "Nahan", tempC = 24.0, code = 2, isDay = true, hiC = 27.0, loC = 15.0,
            humidity = 48, windKmh = 9.0, sunrise = "06:31", sunset = "17:38", fetchedAt = System.currentTimeMillis()
        )
        val out = File(System.getProperty("screens.out") ?: "screenshots").apply { mkdirs() }
        val w = 1920; val h = 1080
        // sample album art for the Now Playing preview
        val art = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).also {
            val cv = Canvas(it)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.shader = android.graphics.LinearGradient(0f, 0f, 600f, 600f, 0xFFFF5F6D.toInt(), 0xFF5B2BFF.toInt(), android.graphics.Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, 600f, 600f, p)
            p.shader = null; p.color = 0x55FFFFFF; cv.drawCircle(300f, 300f, 180f, p)
        }
        for (face in StandbyView.allFaces(ctx)) {
            if (face is com.yash.standbytv.faces.NowPlayingFace)
                face.preview = com.yash.standbytv.faces.NowPlayingFace.Preview("Midnight Drive", "The Night Owls", "Spotify", art, 214_000, 87_000)
            for (s in face.styleNames.indices) {
                face.style = s
                face.settleForPreview()
                face.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                face.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(0xFF000000.toInt())
                repeat(if (face.id == "matrix") 40 else 1) { face.draw(c) }
                File(out, "${face.id}_$s.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            }
        }
    }
}
