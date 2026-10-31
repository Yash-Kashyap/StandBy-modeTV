package com.yash.standbytv

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener. Android only lets an app read other apps' media sessions
 * (Spotify, YouTube Music, YouTube…) if the user grants it notification access.
 * We never read notifications themselves.
 */
class MediaListener : NotificationListenerService()
