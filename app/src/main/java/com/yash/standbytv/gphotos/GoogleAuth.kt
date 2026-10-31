package com.yash.standbytv.gphotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.yash.standbytv.Prefs
import java.util.concurrent.TimeUnit

/**
 * Google sign-in using the Google account already on the TV (Google Play services).
 * Asks only for the Photos Picker permission = access to photos you explicitly pick.
 *
 * Google's "enter a code on your phone" TV sign-in does not support Google Photos, so this
 * uses the Android authorization API instead. It needs an OAuth client of type "Android"
 * (package com.yash.standbytv + the APK's SHA-1) in the Google Cloud project — no ID or
 * secret inside the app.
 */
object GoogleAuth {
    const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    const val REQUEST_CODE = 4711

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun isSignedIn(ctx: Context) = Prefs(ctx).sp.getBoolean("g_signed_in", false)

    fun signOut(ctx: Context) {
        Prefs(ctx).sp.edit().remove("g_signed_in").remove("g_access").remove("g_exp").apply()
    }

    private fun save(ctx: Context, r: AuthorizationResult): Boolean {
        val tok = r.accessToken ?: return false
        Prefs(ctx).sp.edit().putBoolean("g_signed_in", true).putString("g_access", tok)
            .putLong("g_exp", System.currentTimeMillis() + 50 * 60_000L).apply()
        return true
    }

    /**
     * Starts sign-in from [activity]. Shows Google's account/consent screen on the TV if needed;
     * the result arrives in the activity's onActivityResult → pass it to [handleResult].
     */
    fun signIn(activity: Activity, onDone: () -> Unit, onError: (String) -> Unit) {
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { r ->
                if (r.hasResolution()) {
                    try {
                        activity.startIntentSenderForResult(r.pendingIntent!!.intentSender, REQUEST_CODE, null, 0, 0, 0)
                    } catch (e: Exception) { onError(e.message ?: "Couldn't open Google sign-in") }
                } else if (save(activity, r)) onDone() else onError("Google returned no access")
            }
            .addOnFailureListener { e -> onError(explain(e)) }
    }

    /** Call from onActivityResult. Returns null on success, or an error message. */
    fun handleResult(activity: Activity, resultCode: Int, data: Intent?): String? {
        if (data == null) return "Sign-in was cancelled"
        // Even when Google's screen closes with an error, the intent carries the reason.
        return try {
            val r = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            if (save(activity, r)) null else "Google returned no access"
        } catch (e: Exception) { explain(e) }
    }

    private fun explain(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("28405") || m.contains("INVALID_SCOPE", true) ->
                "Google refused the Google Photos permission.\nIn Google Cloud: enable \"Photos Picker API\" in the SAME\nproject as the Android client, then wait 5 minutes.\n(Error: invalid scope)"

            m.contains("10:") || m.contains("DEVELOPER_ERROR") ->
                "Google doesn't recognise this app yet.\nCreate an \"Android\" OAuth client for com.yash.standbytv\nwith this APK's SHA-1 (see README)."
            m.contains("7:") || m.contains("NETWORK") -> "No internet connection"
            m.contains("16:") || m.contains("CANCELED") -> "Sign-in was cancelled"
            else -> m
        }
    }

    /** A valid access token (background thread). Refreshes silently once consent was given. */
    fun accessToken(ctx: Context): String {
        val sp = Prefs(ctx).sp
        val tok = sp.getString("g_access", null)
        if (tok != null && System.currentTimeMillis() < sp.getLong("g_exp", 0)) return tok
        if (!isSignedIn(ctx)) error("Not signed in")
        val r = Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request()), 30, TimeUnit.SECONDS)
        if (r.hasResolution()) { signOut(ctx); error("Please sign in with Google again") }
        if (!save(ctx, r)) error("Google returned no access")
        return sp.getString("g_access", null)!!
    }
}
