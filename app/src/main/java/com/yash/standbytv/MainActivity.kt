package com.yash.standbytv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var host: StandbyView
    private var askedPhotos = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        host = StandbyView(this, isDream = false)
        host.onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        host.onNeedPhotoPermission = { perm ->
            if (!askedPhotos) { askedPhotos = true; requestPermissions(arrayOf(perm), 7) }
        }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        host.handleKey(event) || super.dispatchKeyEvent(event)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        host.stop(); host.reload(); host.start()
    }
}
