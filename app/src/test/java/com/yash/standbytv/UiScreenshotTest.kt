package com.yash.standbytv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the Settings and Google Photos screens to PNG (TV resolution, 1920x1080). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w960dp-h540dp-land-xhdpi")
class UiScreenshotTest {
    private fun snap(a: Activity, name: String) {
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        val out = File(System.getProperty("screens.out") ?: "screenshots").apply { mkdirs() }
        File(out, "ui_$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test fun settings() = snap(Robolectric.buildActivity(SettingsActivity::class.java).setup().get(), "settings")
    @Test fun gphotos() = snap(Robolectric.buildActivity(GPhotosActivity::class.java).setup().get(), "gphotos")
}
