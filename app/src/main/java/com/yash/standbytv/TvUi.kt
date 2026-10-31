package com.yash.standbytv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.yash.standbytv.faces.Face

/** Shared remote-friendly UI bits: focusable rows with a white focus border, section headers, QR codes. */
object TvUi {
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rowBg(ctx: Context): StateListDrawable {
        val focused = GradientDrawable().apply {
            cornerRadius = dp(ctx, 14).toFloat(); setColor(0xFF2C2C2E.toInt()); setStroke(dp(ctx, 3), Color.WHITE)
        }
        val normal = GradientDrawable().apply { cornerRadius = dp(ctx, 14).toFloat(); setColor(0xFF141416.toInt()) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    fun section(parent: LinearLayout, title: String) {
        val ctx = parent.context
        parent.addView(TextView(ctx).apply {
            text = title.uppercase()
            setTextColor(0xFFFF9F0A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Face.BOLD
            letterSpacing = 0.08f
            setPadding(dp(ctx, 18), dp(ctx, 18), 0, dp(ctx, 6))
        })
    }

    /** Adds a row; returns the value TextView so callers can update it. */
    fun row(parent: LinearLayout, title: String, value: String, onClick: () -> Unit): TextView {
        val ctx = parent.context
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14))
            background = rowBg(ctx)
        }
        r.addView(TextView(ctx).apply {
            text = title; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); typeface = Face.MEDIUM
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = TextView(ctx).apply {
            text = value; setTextColor(0xFFAEAEB2.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); gravity = Gravity.END
        }
        r.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.setOnClickListener { onClick() }
        r.setOnFocusChangeListener { view, has -> view.animate().scaleX(if (has) 1.015f else 1f).scaleY(if (has) 1.015f else 1f).setDuration(120).start() }
        parent.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 4) })
        return v
    }

    /** Black-on-white QR code bitmap. */
    fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m[x, y]) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }
}
