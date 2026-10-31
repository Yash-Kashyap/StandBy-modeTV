package com.yash.standbytv.gphotos

import android.content.Context
import com.yash.standbytv.BuildConfig
import com.yash.standbytv.Prefs
import org.json.JSONObject

/**
 * Google sign-in for TVs ("OAuth 2.0 for TV and limited-input devices"):
 * the TV shows a short code + QR, you approve on your phone, the TV gets a refresh token.
 * Only asks for the Photos Picker scope = access to photos you explicitly pick, nothing else.
 */
object GoogleAuth {
    const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    private const val DEVICE_URL = "https://oauth2.googleapis.com/device/code"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    val clientId: String get() = BuildConfig.GOOGLE_CLIENT_ID
    val clientSecret: String get() = BuildConfig.GOOGLE_CLIENT_SECRET
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    class DeviceCode(val deviceCode: String, val userCode: String, val verifyUrl: String, val intervalSec: Int, val expiresAt: Long)

    sealed class Poll {
        object Pending : Poll()
        object SlowDown : Poll()
        object Done : Poll()
        class Failed(val reason: String) : Poll()
    }

    fun isSignedIn(ctx: Context) = !Prefs(ctx).sp.getString("g_refresh", null).isNullOrEmpty()

    fun signOut(ctx: Context) {
        val sp = Prefs(ctx).sp
        sp.getString("g_refresh", null)?.let { tok ->
            Thread { runCatching { Http.request("https://oauth2.googleapis.com/revoke", "POST", Http.form("token" to tok), "application/x-www-form-urlencoded") } }.start()
        }
        sp.edit().remove("g_refresh").remove("g_access").remove("g_exp").apply()
    }

    /** Step 1 (background thread): ask Google for a code to show on the TV. */
    fun requestDeviceCode(): DeviceCode {
        val r = Http.request(DEVICE_URL, "POST", Http.form("client_id" to clientId, "scope" to SCOPE), "application/x-www-form-urlencoded")
        val j = JSONObject(r.body)
        if (!r.ok) error(j.optString("error_description", j.optString("error", "HTTP ${r.code}")))
        return DeviceCode(
            j.getString("device_code"), j.getString("user_code"),
            j.optString("verification_url", j.optString("verification_uri", "https://www.google.com/device")),
            j.optInt("interval", 5), System.currentTimeMillis() + j.optInt("expires_in", 1800) * 1000L
        )
    }

    /** Step 2 (background thread): poll until the user approves on their phone. */
    fun poll(ctx: Context, dc: DeviceCode): Poll {
        val r = Http.request(TOKEN_URL, "POST", Http.form(
            "client_id" to clientId, "client_secret" to clientSecret, "device_code" to dc.deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
        ), "application/x-www-form-urlencoded")
        val j = runCatching { JSONObject(r.body) }.getOrDefault(JSONObject())
        if (r.ok) { save(ctx, j); return Poll.Done }
        return when (j.optString("error")) {
            "authorization_pending" -> Poll.Pending
            "slow_down" -> Poll.SlowDown
            "access_denied" -> Poll.Failed("Sign-in was declined")
            "expired_token" -> Poll.Failed("The code expired — try again")
            else -> Poll.Failed(j.optString("error_description", j.optString("error", "HTTP ${r.code}")))
        }
    }

    private fun save(ctx: Context, j: JSONObject) {
        val e = Prefs(ctx).sp.edit()
        e.putString("g_access", j.getString("access_token"))
        e.putLong("g_exp", System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 60) * 1000)
        if (j.has("refresh_token")) e.putString("g_refresh", j.getString("refresh_token"))
        e.apply()
    }

    /** A valid access token, refreshing it if needed (background thread). */
    fun accessToken(ctx: Context): String {
        val sp = Prefs(ctx).sp
        val tok = sp.getString("g_access", null)
        if (tok != null && System.currentTimeMillis() < sp.getLong("g_exp", 0)) return tok
        val refresh = sp.getString("g_refresh", null) ?: error("Not signed in")
        val r = Http.request(TOKEN_URL, "POST", Http.form(
            "client_id" to clientId, "client_secret" to clientSecret,
            "refresh_token" to refresh, "grant_type" to "refresh_token"
        ), "application/x-www-form-urlencoded")
        val j = JSONObject(r.body)
        if (!r.ok) {
            if (j.optString("error") == "invalid_grant") sp.edit().remove("g_refresh").apply()
            error(j.optString("error_description", "Couldn't refresh Google sign-in"))
        }
        save(ctx, j)
        return j.getString("access_token")
    }
}
