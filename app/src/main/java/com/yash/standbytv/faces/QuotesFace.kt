package com.yash.standbytv.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yash.standbytv.T

/** A new quote or affirmation every 45 seconds, with a gentle cross-fade. */
class QuotesFace(ctx: Context) : Face(ctx) {
    override val id = "quotes"
    override val title = "Quotes"
    override val styleNames = listOf("Inspiration", "Stoic", "Calm", "Affirmations")
    override val tickMs = 100L

    private val sets: Array<List<Pair<String, String>>> = arrayOf(
        listOf(
            "Arise, awake, and stop not till the goal is reached." to "Swami Vivekananda",
            "Take up one idea. Make that one idea your life." to "Swami Vivekananda",
            "Dream is not that which you see while sleeping, it is something that does not let you sleep." to "A. P. J. Abdul Kalam",
            "If you want to shine like a sun, first burn like a sun." to "A. P. J. Abdul Kalam",
            "The journey of a thousand miles begins with one step." to "Lao Tzu",
            "Well done is better than well said." to "Benjamin Franklin",
            "Act as if what you do makes a difference. It does." to "William James",
            "Fall seven times, stand up eight." to "Japanese proverb",
            "It always seems impossible until it's done." to "Nelson Mandela",
            "In the middle of difficulty lies opportunity." to "Albert Einstein",
            "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
            "The best way to predict the future is to create it." to "Peter Drucker",
        ),
        listOf(
            "We suffer more often in imagination than in reality." to "Seneca",
            "Difficulties strengthen the mind, as labour does the body." to "Seneca",
            "Luck is what happens when preparation meets opportunity." to "Seneca",
            "The happiness of your life depends upon the quality of your thoughts." to "Marcus Aurelius",
            "Waste no more time arguing about what a good man should be. Be one." to "Marcus Aurelius",
            "If it is not right, do not do it; if it is not true, do not say it." to "Marcus Aurelius",
            "You have power over your mind, not outside events. Realise this, and you will find strength." to "Marcus Aurelius",
            "No man is free who is not master of himself." to "Epictetus",
            "First say to yourself what you would be; and then do what you have to do." to "Epictetus",
            "Wealth consists not in having great possessions, but in having few wants." to "Epictetus",
            "It's not what happens to you, but how you react to it that matters." to "Epictetus",
        ),
        listOf(
            "Nature does not hurry, yet everything is accomplished." to "Lao Tzu",
            "Adopt the pace of nature: her secret is patience." to "Ralph Waldo Emerson",
            "Within you, there is a stillness and a sanctuary to which you can retreat at any time." to "Hermann Hesse",
            "Almost everything will work again if you unplug it for a few minutes, including you." to "Anne Lamott",
            "Smile, breathe, and go slowly." to "Thich Nhat Hanh",
            "Rest is not idleness." to "John Lubbock",
            "Slow down and everything you are chasing will come around and catch you." to "John De Paola",
            "Peace comes from within. Do not seek it without." to "Buddha",
            "Be where you are; otherwise you will miss your life." to "Buddha",
            "Silence is a source of great strength." to "Lao Tzu",
        ),
        listOf(
            "I am calm, capable and ready for today." to "",
            "Small steps every day add up to big change." to "",
            "I am allowed to rest without earning it." to "",
            "I choose progress over perfection." to "",
            "My work matters, and so do I." to "",
            "I can do hard things." to "",
            "Today I will focus on what I can control." to "",
            "I am growing, even when it doesn't feel like it." to "",
            "I trust myself to figure it out." to "",
            "I am proud of how far I have come." to "",
            "Every day is a fresh start." to "",
            "Good things are on their way to me." to "",
        ),
    )
    private val bgs = arrayOf(
        intArrayOf(0xFF1A1033.toInt(), 0xFF050308.toInt()),
        intArrayOf(0xFF1E1A15.toInt(), 0xFF060504.toInt()),
        intArrayOf(0xFF0B2229.toInt(), 0xFF020709.toInt()),
        intArrayOf(0xFF2A1020.toInt(), 0xFF080306.toInt()),
    )
    private val accents = intArrayOf(0xFFB39DFF.toInt(), 0xFFD9B77E.toInt(), 0xFF7FD6C9.toInt(), 0xFFFF9EC4.toInt())

    private val SLOT = 45_000L
    private val FADE = 1_200f
    private val bg = Paint()
    private var bgKey = -1
    private val quoteP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedKey = ""
    private var layout: StaticLayout? = null

    override fun draw(c: Canvas, w: Float, h: Float) {
        val set = sets[style]
        if (bgKey != style) {
            bgKey = style
            bg.shader = LinearGradient(0f, 0f, w, h, bgs[style][0], bgs[style][1], Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, bg)

        val now = System.currentTimeMillis()
        val slot = now / SLOT
        val (q, author) = set[(slot % set.size).toInt()]
        val inSlot = (now % SLOT).toFloat()
        val alpha = when {
            inSlot < FADE -> inSlot / FADE
            inSlot > SLOT - FADE -> (SLOT - inSlot) / FADE
            else -> 1f
        }.coerceIn(0f, 1f)

        val text = "“$q”"
        val maxW = (w * 0.72f).toInt()
        val key = "$text|$maxW|$h"
        if (key != cachedKey) {
            cachedKey = key
            quoteP.textSize = if (q.length > 70) h * 0.065f else h * 0.08f
            layout = StaticLayout.Builder.obtain(text, 0, text.length, quoteP, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.18f).build()
        }
        val lay = layout ?: return
        val authorH = if (author.isEmpty()) 0f else h * 0.1f
        val top = (h - lay.height - authorH) / 2f
        quoteP.alpha = (255 * alpha).toInt()
        c.save(); c.translate((w - maxW) / 2f, top); lay.draw(c); c.restore()

        if (author.isNotEmpty()) {
            small.textAlign = Paint.Align.CENTER; small.typeface = MEDIUM
            small.textSize = h * 0.04f
            small.color = accents[style]; small.alpha = (255 * alpha).toInt()
            c.drawText("— $author", w / 2, top + lay.height + h * 0.08f, small)
        }

        val cal = T.now()
        small.alpha = 255
        small.textAlign = Paint.Align.LEFT; small.typeface = LIGHT; small.textSize = h * 0.06f; small.color = Color.WHITE
        c.drawText(T.hm(cal, prefs.use24h), w * 0.05f, h * 0.11f, small)
        small.typeface = MEDIUM; small.textSize = h * 0.028f; small.color = accents[style]
        c.drawText((styleNames[style] + "  ·  " + T.dateShort(cal)).uppercase(), w * 0.052f, h * 0.155f, small)
    }
}
