package com.yash.standbytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Optional: open StandBy TV when the TV boots (works on Android TV 8/9; newer versions may block it). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).openOnBoot) return
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
