package com.yash.standbytv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

/**
 * Full-screen StandBy TV that a launcher can start when the TV has been idle.
 * It behaves like the system screensaver: any remote button closes it and returns to the previous screen.
 */
class ScreensaverActivity : Activity() {
    private lateinit var host: StandbyView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        host = StandbyView(this, isDream = true)   // uses the "screensaver shows" setting, no hints
        setContentView(host)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onResume() {
        super.onResume()
        host.reload()
        host.start()
    }

    override fun onPause() {
        host.stop()
        super.onPause()
    }

    override fun onStop() {
        // Home pressed, another app opened, or the TV went to sleep: this screen has done its job.
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE ->
                return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) finish()
        return true
    }
}
