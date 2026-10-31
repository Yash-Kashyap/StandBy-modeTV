package com.yash.standbytv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yash.standbytv.faces.Face
import com.yash.standbytv.gphotos.GPhotos
import com.yash.standbytv.gphotos.GoogleAuth
import kotlin.concurrent.thread

/**
 * Google Photos for the slideshow. Left: your albums. Right: QR codes for sign-in and picking.
 * Every step happens on your phone — the TV remote is only used to choose menu items.
 */
class GPhotosActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var qr: ImageView
    private lateinit var big: TextView
    private lateinit var info: TextView
    @Volatile private var job = 0          // bump to cancel the running background flow

    private fun dp(v: Int) = TvUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(56), dp(32), dp(56), dp(24))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply {
            text = "Google Photos"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f); typeface = Face.BOLD
        })
        left.addView(TextView(this).apply {
            text = "Albums you add here play in the Photos screen"
            setTextColor(0xFF8E8E93.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, dp(4), 0, dp(10))
        })
        val scroll = ScrollView(this).apply { isFocusable = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(24)) }
        scroll.addView(list)
        left.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(40), 0, 0, 0)
        }
        qr = ImageView(this).apply { visibility = View.GONE }
        right.addView(qr, LinearLayout.LayoutParams(dp(230), dp(230)))
        big = TextView(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f); typeface = Face.BOLD
            gravity = Gravity.CENTER; letterSpacing = 0.12f; setPadding(0, dp(14), 0, dp(6))
        }
        right.addView(big)
        info = TextView(this).apply {
            setTextColor(0xFFC7C7CC.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        right.addView(info)
        root.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.9f))
        setContentView(root)
        build()
    }

    override fun onDestroy() { job++; super.onDestroy() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GoogleAuth.REQUEST_CODE) return
        val err = GoogleAuth.handleResult(this, resultCode, data)
        if (err == null) { panel(null, "Signed in ✓", ""); build() } else panel(null, "", err)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && qr.visibility == View.VISIBLE) { job++; panel(null, "", "Cancelled."); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun panel(qrText: String?, bigText: String, infoText: String) {
        if (qrText == null) qr.visibility = View.GONE
        else { qr.setImageBitmap(TvUi.qr(qrText, 460)); qr.visibility = View.VISIBLE }
        big.text = bigText
        info.text = infoText
    }

    private fun build() {
        list.removeAllViews()
        if (!GoogleAuth.isSignedIn(this)) {
            TvUi.section(list, "Account")
            TvUi.row(list, "Sign in with Google", "") { signIn() }.parent.let { (it as View).requestFocus() }
            TvUi.row(list, "Back", "") { finish() }
            if (big.text.isEmpty() && info.text.isEmpty())
                panel(null, "", "Sign in with the Google account on this TV\nto choose photos and albums from Google Photos.\n\nStandBy TV can only see the photos you pick.")
            return
        }
        TvUi.section(list, "Albums")
        val addRow = TvUi.row(list, "＋  New album from Google Photos", "") { newAlbum() }
        for (a in GPhotos.albums(this)) {
            TvUi.row(list, a.name, "${a.count} photos · ${if (a.enabled) "Shown" else "Hidden"}") { albumMenu(a) }
        }
        TvUi.section(list, "Account")
        TvUi.row(list, "Sign out of Google", "") { GoogleAuth.signOut(this); panel(null, "", ""); build() }
        (addRow.parent as View).requestFocus()
        if (qr.visibility != View.VISIBLE && big.text.isEmpty()) panel(null, "", "Pick photos on your phone and they'll play\nin the Photos screen of StandBy TV.")
    }

    // ------------------------------------------------------------------ sign-in

    /** Uses the Google account already on the TV; Google shows its own account + consent screen. */
    private fun signIn() {
        job++
        panel(null, "", "Opening Google sign-in…")
        GoogleAuth.signIn(this,
            onDone = { panel(null, "Signed in ✓", ""); build() },
            onError = { msg -> panel(null, "Couldn't sign in", msg) })
    }

    // ------------------------------------------------------------------ albums

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun askName(title: String, initial: String, done: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; selectAll()
        }
        dialog().setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ -> done(input.text.toString().trim().ifEmpty { initial }) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun newAlbum() {
        val n = GPhotos.albums(this).size + 1
        askName("Album name", "Album $n") { name ->
            val a = GPhotos.createAlbum(this, name)
            build()
            pick(a.id, a.name)
        }
    }

    private fun albumMenu(a: GPhotos.Album) {
        val items = arrayOf(if (a.enabled) "Hide from slideshow" else "Show in slideshow", "Add more photos", "Rename", "Delete")
        dialog().setTitle(a.name).setItems(items) { _, which ->
            when (which) {
                0 -> { GPhotos.setEnabled(this, a.id, !a.enabled); build() }
                1 -> pick(a.id, a.name)
                2 -> askName("Rename album", a.name) { GPhotos.rename(this, a.id, it); build() }
                3 -> dialog().setTitle("Delete \"${a.name}\"?").setMessage("Removes the downloaded copies from this TV. Your Google Photos are not touched.")
                    .setPositiveButton("Delete") { _, _ -> GPhotos.delete(this, a.id); build() }
                    .setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    /** Shows a picker QR, waits for "Done" on the phone, then downloads the picks into the album. */
    private fun pick(albumId: String, albumName: String) {
        val my = ++job
        panel(null, "", "Opening Google Photos picker…")
        thread {
            try {
                val s = GPhotos.createSession(this)
                main.post {
                    if (my != job) return@post
                    panel(s.pickerUri, "Scan with your phone",
                        "Pick photos for \"$albumName\".\nFor a whole album: search its name in the picker,\nselect all, then tap Done.\n\nPress BACK to cancel.")
                }
                var picked = false
                while (my == job && System.currentTimeMillis() < s.timeoutAt) {
                    Thread.sleep(s.pollMs)
                    if (my != job) { GPhotos.deleteSession(this, s); return@thread }
                    if (GPhotos.isPicked(this, s)) { picked = true; break }
                }
                if (!picked) { main.post { if (my == job) panel(null, "", "Picker timed out. Try again.") }; return@thread }
                main.post { panel(null, "Downloading…", "") }
                val added = GPhotos.importSession(this, s, albumId) { done, total ->
                    main.post { if (my == job) info.text = "$done of $total photos" }
                }
                main.post {
                    if (my == job) {
                        panel(null, "Done ✓", "Added $added photos to \"$albumName\".")
                        build()
                        Toast.makeText(this, "Google Photos album ready", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                main.post { if (my == job) panel(null, "", "Something went wrong:\n${t.message}") }
            }
        }
    }
}
