package com.yash.standbytv

import android.service.dreams.DreamService

/** Makes StandBy TV available as the TV's screensaver. Any remote button wakes the TV as usual. */
class StandbyDream : DreamService() {
    private var host: StandbyView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val v = StandbyView(this, isDream = true)
        host = v
        setContentView(v)
        v.reload()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        host?.start()
    }

    override fun onDreamingStopped() {
        host?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        host?.stop()
        host = null
        super.onDetachedFromWindow()
    }
}
