package com.opensstp.android

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.animation.LinearInterpolator

/** Colors + small drawable helpers shared by the whole UI. */
object Ui {
    var BG0 = Color.parseColor("#060A13")
    var BG1 = Color.parseColor("#0C1424")
    var CARD = Color.parseColor("#111A2C")
    var FIELD = Color.parseColor("#0E1626")
    var CARD_HI = Color.parseColor("#16213A")
    var STROKE = Color.parseColor("#1F2B46")
    var TEXT = Color.parseColor("#F2F5FB")
    var TEXT2 = Color.parseColor("#93A1BC")
    var MUTED = Color.parseColor("#5F6E8A")
    var IDLE = Color.parseColor("#6B7FA3")
    val ACCENT = Color.parseColor("#3B82F6")
    val ACCENT2 = Color.parseColor("#7C5CFF")
    var ACCENT_SOFT = Color.parseColor("#7FB0FF")
    var GREEN = Color.parseColor("#2EE6A6")
    var CYAN = Color.parseColor("#4DD0FF")
    var RED = Color.parseColor("#FF6B7A")
    var AMBER = Color.parseColor("#FFBE5A")
    val DANGER_A = Color.parseColor("#E5484D")
    val DANGER_B = Color.parseColor("#FF7A59")
    var CORE_TOP = Color.parseColor("#1E2B49")
    var CORE_BOTTOM = Color.parseColor("#0E1626")

    // Extra theme-dependent tones (set by applyTheme)
    var GROUP_FILL = alpha(Color.WHITE, 13)
    var GROUP_STROKE = alpha(Color.WHITE, 24)
    var DIVIDER = alpha(Color.WHITE, 18)
    var DIM = alpha(Color.WHITE, 46)

    var dark = true
        private set

    /** Switches every themeable color. Call before any view is built, then recreate the activity. */
    fun applyTheme(isDark: Boolean) {
        dark = isDark
        if (isDark) {
            BG0 = Color.parseColor("#060A13"); BG1 = Color.parseColor("#0C1424")
            CARD = Color.parseColor("#111A2C"); FIELD = Color.parseColor("#0E1626")
            CARD_HI = Color.parseColor("#16213A"); STROKE = Color.parseColor("#1F2B46")
            TEXT = Color.parseColor("#F2F5FB"); TEXT2 = Color.parseColor("#93A1BC")
            MUTED = Color.parseColor("#5F6E8A"); IDLE = Color.parseColor("#6B7FA3")
            ACCENT_SOFT = Color.parseColor("#7FB0FF"); GREEN = Color.parseColor("#2EE6A6")
            CYAN = Color.parseColor("#4DD0FF"); RED = Color.parseColor("#FF6B7A")
            AMBER = Color.parseColor("#FFBE5A")
            CORE_TOP = Color.parseColor("#1E2B49"); CORE_BOTTOM = Color.parseColor("#0E1626")
            GROUP_FILL = alpha(Color.WHITE, 13); GROUP_STROKE = alpha(Color.WHITE, 24)
            DIVIDER = alpha(Color.WHITE, 18); DIM = alpha(Color.WHITE, 46)
        } else {
            BG0 = Color.parseColor("#F6F8FC"); BG1 = Color.parseColor("#E9EFF8")
            CARD = Color.parseColor("#FFFFFF"); FIELD = Color.parseColor("#F1F5FA")
            CARD_HI = Color.parseColor("#FFFFFF"); STROKE = Color.parseColor("#D8E0EC")
            TEXT = Color.parseColor("#0F1B2D"); TEXT2 = Color.parseColor("#51627A")
            MUTED = Color.parseColor("#5E6D84"); IDLE = Color.parseColor("#8393AD")
            ACCENT_SOFT = Color.parseColor("#3451C7"); GREEN = Color.parseColor("#0B8A68")
            CYAN = Color.parseColor("#0284C7"); RED = Color.parseColor("#D93A4A")
            AMBER = Color.parseColor("#B45309")
            CORE_TOP = Color.parseColor("#FFFFFF"); CORE_BOTTOM = Color.parseColor("#E3EAF4")
            GROUP_FILL = Color.WHITE; GROUP_STROKE = Color.parseColor("#D8E0EC")
            DIVIDER = Color.parseColor("#E6ECF4"); DIM = alpha(Color.BLACK, 28)
        }
    }

    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun density(ctx: Context) = ctx.resources.displayMetrics.density

    fun shape(ctx: Context, fill: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable {
        val d = density(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radiusDp * d
            if (strokeDp > 0f) setStroke(maxOf(1, (strokeDp * d).toInt()), strokeColor)
        }
    }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun gradient(
        ctx: Context,
        from: Int,
        to: Int,
        radiusDp: Float,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable = GradientDrawable(orientation, intArrayOf(from, to)).apply {
        cornerRadius = radiusDp * density(ctx)
    }

    fun ripple(ctx: Context, content: Drawable, radiusDp: Float, rippleColor: Int = 0x33FFFFFF): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = radiusDp * density(ctx)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }
}

enum class IconKind {
    PLUS, CLOSE, ARROW_DOWN, ARROW_UP, SERVER, USER, LOCK, EYE, EYE_OFF, SHIELD,
    MENU, SAVE, CLIPBOARD, SHARE, KEY, BATTERY, TRASH, INFO, HELP, EXIT, CHEVRON, SUN, MOON, GLOBE, REFRESH
}

/** Tiny vector icon set drawn on a 24x24 grid, so no drawable resources are needed. */
class IconView(context: Context, private var kind: IconKind, private var tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    fun applyTint(color: Int) {
        tint = color
        invalidate()
    }

    fun changeKind(newKind: IconKind) {
        kind = newKind
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        canvas.scale(s / 24f, s / 24f)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        when (kind) {
            IconKind.PLUS -> {
                canvas.drawLine(12f, 5f, 12f, 19f, paint)
                canvas.drawLine(5f, 12f, 19f, 12f, paint)
            }
            IconKind.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, paint)
                canvas.drawLine(18f, 6f, 6f, 18f, paint)
            }
            IconKind.ARROW_DOWN -> {
                canvas.drawLine(12f, 5f, 12f, 19f, paint)
                val p = Path()
                p.moveTo(6f, 13f); p.lineTo(12f, 19f); p.lineTo(18f, 13f)
                canvas.drawPath(p, paint)
            }
            IconKind.ARROW_UP -> {
                canvas.drawLine(12f, 19f, 12f, 5f, paint)
                val p = Path()
                p.moveTo(6f, 11f); p.lineTo(12f, 5f); p.lineTo(18f, 11f)
                canvas.drawPath(p, paint)
            }
            IconKind.SERVER -> {
                rect.set(4f, 4f, 20f, 10f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                rect.set(4f, 14f, 20f, 20f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(8f, 7f, 1f, paint)
                canvas.drawCircle(8f, 17f, 1f, paint)
            }
            IconKind.USER -> {
                canvas.drawCircle(12f, 8f, 4f, paint)
                rect.set(4f, 13f, 20f, 29f)
                canvas.drawArc(rect, 180f, 180f, false, paint)
            }
            IconKind.LOCK -> {
                rect.set(5f, 11f, 19f, 21f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                rect.set(8f, 4f, 16f, 12f); canvas.drawArc(rect, 180f, 180f, false, paint)
                canvas.drawLine(8f, 8f, 8f, 11f, paint)
                canvas.drawLine(16f, 8f, 16f, 11f, paint)
            }
            IconKind.EYE, IconKind.EYE_OFF -> {
                val p = Path()
                p.moveTo(2f, 12f); p.quadTo(12f, 3f, 22f, 12f); p.quadTo(12f, 21f, 2f, 12f)
                canvas.drawPath(p, paint)
                canvas.drawCircle(12f, 12f, 3f, paint)
                if (kind == IconKind.EYE_OFF) canvas.drawLine(4f, 4f, 20f, 20f, paint)
            }
            IconKind.MENU -> {
                canvas.drawLine(4f, 6f, 20f, 6f, paint)
                canvas.drawLine(10f, 12f, 20f, 12f, paint)
                canvas.drawLine(4f, 18f, 20f, 18f, paint)
            }
            IconKind.SAVE -> {
                rect.set(4f, 4f, 20f, 20f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                rect.set(8f, 4f, 16f, 9f); canvas.drawRect(rect, paint)
                rect.set(7f, 14f, 17f, 20f); canvas.drawRect(rect, paint)
            }
            IconKind.CLIPBOARD -> {
                rect.set(5f, 5f, 19f, 21f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                rect.set(9f, 3f, 15f, 7f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
                canvas.drawLine(9f, 12f, 15f, 12f, paint)
                canvas.drawLine(9f, 16f, 13f, 16f, paint)
            }
            IconKind.SHARE -> {
                canvas.drawCircle(6f, 12f, 2.5f, paint)
                canvas.drawCircle(18f, 6f, 2.5f, paint)
                canvas.drawCircle(18f, 18f, 2.5f, paint)
                canvas.drawLine(8.2f, 10.8f, 15.8f, 7.2f, paint)
                canvas.drawLine(8.2f, 13.2f, 15.8f, 16.8f, paint)
            }
            IconKind.KEY -> {
                canvas.drawCircle(8f, 15f, 4f, paint)
                canvas.drawLine(11f, 12f, 20f, 3f, paint)
                canvas.drawLine(16f, 7f, 19f, 10f, paint)
            }
            IconKind.BATTERY -> {
                rect.set(3f, 7f, 19f, 17f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                canvas.drawLine(21.5f, 10.5f, 21.5f, 13.5f, paint)
                paint.style = Paint.Style.FILL
                rect.set(5.5f, 9.5f, 11f, 14.5f); canvas.drawRoundRect(rect, 1f, 1f, paint)
            }
            IconKind.TRASH -> {
                canvas.drawLine(4f, 7f, 20f, 7f, paint)
                canvas.drawLine(9f, 4f, 15f, 4f, paint)
                val p = Path()
                p.moveTo(6f, 7f); p.lineTo(7f, 20f); p.lineTo(17f, 20f); p.lineTo(18f, 7f)
                canvas.drawPath(p, paint)
                canvas.drawLine(10f, 11f, 10f, 16f, paint)
                canvas.drawLine(14f, 11f, 14f, 16f, paint)
            }
            IconKind.INFO -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawLine(12f, 11f, 12f, 17f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 7.5f, 1.2f, paint)
            }
            IconKind.HELP -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                rect.set(9f, 7f, 15f, 13f); canvas.drawArc(rect, 180f, 220f, false, paint)
                canvas.drawLine(12f, 13f, 12f, 14.5f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 17.5f, 1.2f, paint)
            }
            IconKind.EXIT -> {
                val p = Path()
                p.moveTo(10f, 4f); p.lineTo(5f, 4f); p.lineTo(5f, 20f); p.lineTo(10f, 20f)
                canvas.drawPath(p, paint)
                canvas.drawLine(9f, 12f, 20f, 12f, paint)
                val head = Path()
                head.moveTo(16f, 8f); head.lineTo(20f, 12f); head.lineTo(16f, 16f)
                canvas.drawPath(head, paint)
            }
            IconKind.CHEVRON -> {
                val p = Path()
                p.moveTo(15f, 6f); p.lineTo(9f, 12f); p.lineTo(15f, 18f)
                canvas.drawPath(p, paint)
            }
            IconKind.SUN -> {
                canvas.drawCircle(12f, 12f, 4.2f, paint)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val c = Math.cos(a).toFloat()
                    val sn = Math.sin(a).toFloat()
                    canvas.drawLine(12f + 7.4f * c, 12f + 7.4f * sn, 12f + 9.8f * c, 12f + 9.8f * sn, paint)
                }
            }
            IconKind.MOON -> {
                val outer = Path().apply { addCircle(12f, 12f, 8.5f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(17f, 8f, 7f, Path.Direction.CW) }
                outer.op(cut, Path.Op.DIFFERENCE)
                paint.style = Paint.Style.FILL
                canvas.drawPath(outer, paint)
            }
            IconKind.GLOBE -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                rect.set(8f, 3f, 16f, 21f); canvas.drawOval(rect, paint)
                canvas.drawLine(3f, 12f, 21f, 12f, paint)
            }
            IconKind.REFRESH -> {
                rect.set(5f, 5f, 19f, 19f)
                canvas.drawArc(rect, -60f, 270f, false, paint)
                val head = Path()
                head.moveTo(12f, 1.5f); head.lineTo(16f, 5f); head.lineTo(12f, 8.5f)
                canvas.drawPath(head, paint)
            }
            IconKind.SHIELD -> {
                val p = Path()
                p.moveTo(12f, 3f); p.lineTo(19f, 6f); p.lineTo(19f, 12f)
                p.cubicTo(19f, 16.5f, 16f, 19.5f, 12f, 21f)
                p.cubicTo(8f, 19.5f, 5f, 16.5f, 5f, 12f)
                p.lineTo(5f, 6f); p.close()
                canvas.drawPath(p, paint)
                val tick = Path()
                tick.moveTo(9f, 12f); tick.lineTo(11f, 14f); tick.lineTo(15f, 10f)
                canvas.drawPath(tick, paint)
            }
        }
        canvas.restore()
    }
}

/**
 * The hero button: a glowing orb with a power icon.
 * idle = calm slate, connecting = rotating cyan arc + ripples, connected = green glow + ripples, error = red.
 */
class PowerOrbView(context: Context) : View(context) {
    private var state: String = SoftEtherVpnService.STATE_DISCONNECTED
    private var accent: Int = Ui.IDLE
    private var phase = 0f
    private var attached = false

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setState(newState: String) {
        state = newState
        accent = when (newState) {
            SoftEtherVpnService.STATE_CONNECTED -> Ui.GREEN
            SoftEtherVpnService.STATE_CONNECTING -> Ui.CYAN
            SoftEtherVpnService.STATE_ERROR -> Ui.RED
            else -> Ui.IDLE
        }
        rebuildShaders()
        syncAnimation()
        invalidate()
    }

    private fun isLive() =
        state == SoftEtherVpnService.STATE_CONNECTED || state == SoftEtherVpnService.STATE_CONNECTING

    private fun syncAnimation() {
        if (attached && isLive()) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    private fun rebuildShaders() {
        if (width == 0 || height == 0) return
        val u = minOf(width, height) / 220f
        val cx = width / 2f
        val cy = height / 2f
        val glowAlpha = when {
            isLive() -> 95
            state == SoftEtherVpnService.STATE_ERROR -> 60
            else -> 26
        }
        glowPaint.shader = RadialGradient(
            cx, cy, 110f * u,
            intArrayOf(Ui.alpha(accent, glowAlpha), Ui.alpha(accent, 0)),
            floatArrayOf(0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.shader = LinearGradient(
            cx, cy - 64f * u, cx, cy + 64f * u,
            Ui.CORE_TOP, Ui.CORE_BOTTOM,
            Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        attached = false
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val u = minOf(width, height) / 220f
        if (u <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val live = isLive()

        if (live) {
            for (i in 0..1) {
                val p = (phase + i * 0.5f) % 1f
                strokePaint.color = accent
                strokePaint.alpha = (110f * (1f - p)).toInt()
                strokePaint.strokeWidth = 2f * u
                canvas.drawCircle(cx, cy, (66f + 44f * p) * u, strokePaint)
            }
        }

        canvas.drawCircle(cx, cy, 110f * u, glowPaint)
        canvas.drawCircle(cx, cy, 64f * u, corePaint)

        strokePaint.color = Ui.alpha(accent, if (live) 230 else 140)
        strokePaint.strokeWidth = 2f * u
        canvas.drawCircle(cx, cy, 64f * u, strokePaint)

        rect.set(cx - 76f * u, cy - 76f * u, cx + 76f * u, cy + 76f * u)
        when (state) {
            SoftEtherVpnService.STATE_CONNECTING -> {
                strokePaint.color = Ui.alpha(accent, 50)
                strokePaint.strokeWidth = 3f * u
                canvas.drawCircle(cx, cy, 76f * u, strokePaint)
                strokePaint.color = accent
                strokePaint.strokeWidth = 4f * u
                canvas.drawArc(rect, phase * 360f - 90f, 100f, false, strokePaint)
            }
            SoftEtherVpnService.STATE_CONNECTED -> {
                strokePaint.color = Ui.alpha(accent, 210)
                strokePaint.strokeWidth = 3.5f * u
                canvas.drawCircle(cx, cy, 76f * u, strokePaint)
            }
            SoftEtherVpnService.STATE_ERROR -> {
                strokePaint.color = Ui.alpha(accent, 150)
                strokePaint.strokeWidth = 3f * u
                canvas.drawCircle(cx, cy, 76f * u, strokePaint)
            }
            else -> {
                strokePaint.color = Ui.alpha(accent, 70)
                strokePaint.strokeWidth = 1.5f * u
                canvas.drawCircle(cx, cy, 76f * u, strokePaint)
            }
        }

        // Power glyph
        strokePaint.color = if (state == SoftEtherVpnService.STATE_DISCONNECTED) Ui.TEXT2 else accent
        strokePaint.strokeWidth = 5f * u
        rect.set(cx - 21f * u, cy - 21f * u, cx + 21f * u, cy + 21f * u)
        canvas.drawArc(rect, -55f, 290f, false, strokePaint)
        canvas.drawLine(cx, cy - 30f * u, cx, cy - 7f * u, strokePaint)
    }
}

/**
 * Country helpers. Everything is keyed by the two-letter ISO country code ("DE", "JP", ...).
 * The code comes from the server's IP address (see [GeoLookup]) or, as a fallback, from the name
 * the user gave the config.
 */
object Flags {
    private val aliases: List<Pair<String, List<String>>> = listOf(
        "DE" to listOf("آلمان", "المان", "germany", "german", "deutsch"),
        "KR" to listOf("کره", "korea"),
        "JP" to listOf("ژاپن", "japan"),
        "FR" to listOf("فرانسه", "france"),
        "IT" to listOf("ایتالیا", "italy"),
        "NL" to listOf("هلند", "netherlands", "holland"),
        "RU" to listOf("روسیه", "russia"),
        "IR" to listOf("ایران", "iran"),
        "US" to listOf("آمریکا", "امریکا", "usa", "united states", "america"),
        "GB" to listOf("انگلیس", "بریتانیا", "england", "britain", "united kingdom"),
        "AE" to listOf("امارات", "emirates", "uae"),
        "TR" to listOf("ترکیه", "turkey", "türkiye")
    )

    /** Country display names (Persian + English) for every ISO country, built once on first use. */
    private val nameIndex: List<Pair<String, String>> by lazy {
        val fa = java.util.Locale("fa")
        val list = ArrayList<Pair<String, String>>()
        for (cc in java.util.Locale.getISOCountries()) {
            val loc = java.util.Locale("", cc)
            for (n in listOf(loc.getDisplayCountry(fa), loc.getDisplayCountry(java.util.Locale.ENGLISH))) {
                if (n.length >= 4 && n != cc) list.add(n.lowercase() to cc)
            }
        }
        // Longest names first so "south africa" wins over a shorter accidental match.
        list.sortedByDescending { it.first.length }
    }

    /** Guess a country code from a config name the user chose (e.g. "آلمان", "Japan 2"). */
    fun forName(name: String): String? {
        val n = name.lowercase()
        aliases.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.let { return it.first }
        return nameIndex.firstOrNull { n.contains(it.first) }?.second
    }

    /** Country name in Persian (falls back to [fallback] / the code itself). */
    fun displayName(cc: String, fallback: String = ""): String {
        val n = java.util.Locale("", cc).getDisplayCountry(java.util.Locale(Lang.LANGS[Lang.current].code))
        return if (n.isNotBlank() && n != cc) n else fallback.ifBlank { cc }
    }

    /** The flag as an emoji built from the two regional-indicator letters. */
    fun emoji(cc: String): String? {
        if (cc.length != 2) return null
        val up = cc.uppercase()
        if (up.any { it !in 'A'..'Z' }) return null
        val sb = StringBuilder()
        for (c in up) sb.appendCodePoint(0x1F1E6 + (c - 'A'))
        return sb.toString()
    }
}

/**
 * A round flag. A handful of countries are drawn as crisp geometric flags; every other country uses
 * its flag emoji cropped to a circle. If the device has no glyph for it, the country code is shown.
 */
class FlagView(context: Context, countryCode: String) : View(context) {
    private val cc = countryCode.uppercase()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()
    private val emoji = Flags.emoji(cc)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = minOf(w, h) / 2f
        clip.reset()
        clip.addCircle(w / 2f, h / 2f, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.style = Paint.Style.FILL

        fun bands(horizontal: Boolean, vararg colors: Int) {
            val n = colors.size
            for (i in 0 until n) {
                paint.color = colors[i]
                if (horizontal) canvas.drawRect(0f, h * i / n, w, h * (i + 1) / n, paint)
                else canvas.drawRect(w * i / n, 0f, w * (i + 1) / n, h, paint)
            }
        }

        when (cc) {
            "DE" -> bands(true, 0xFF111111.toInt(), 0xFFD6262D.toInt(), 0xFFF5C400.toInt())
            "FR" -> bands(false, 0xFF1F4FB8.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6262D.toInt())
            "IT" -> bands(false, 0xFF1E9E5A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6262D.toInt())
            "NL" -> bands(true, 0xFFAE1C28.toInt(), 0xFFFFFFFF.toInt(), 0xFF21468B.toInt())
            "RU" -> bands(true, 0xFFFFFFFF.toInt(), 0xFF0039A6.toInt(), 0xFFD52B1E.toInt())
            "IR" -> bands(true, 0xFF239F40.toInt(), 0xFFFFFFFF.toInt(), 0xFFDA0000.toInt())
            "JP" -> {
                canvas.drawColor(0xFFF7F7F7.toInt())
                paint.color = 0xFFBC002D.toInt()
                canvas.drawCircle(w / 2f, h / 2f, r * 0.5f, paint)
            }
            "KR" -> {
                canvas.drawColor(0xFFF7F7F7.toInt())
                rect.set(w / 2f - r * 0.5f, h / 2f - r * 0.5f, w / 2f + r * 0.5f, h / 2f + r * 0.5f)
                paint.color = 0xFFCD2E3A.toInt()
                canvas.drawArc(rect, 180f, 180f, true, paint)
                paint.color = 0xFF0047A0.toInt()
                canvas.drawArc(rect, 0f, 180f, true, paint)
            }
            else -> drawGeneric(canvas, w, h, r)
        }
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, r * 0.06f)
        paint.color = Ui.DIM
        canvas.drawCircle(w / 2f, h / 2f, r - paint.strokeWidth / 2f, paint)
    }

    private fun drawGeneric(canvas: Canvas, w: Float, h: Float, r: Float) {
        val glyph = emoji
        if (glyph != null && paint.hasGlyph(glyph)) {
            // Flag emoji are wider than tall and have margins; oversize and let the circle crop them.
            canvas.drawColor(0xFFE9EDF3.toInt())
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = r * 2f * 1.35f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            canvas.drawText(glyph, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, paint)
        } else {
            canvas.drawColor(Ui.alpha(Ui.IDLE, 90))
            paint.style = Paint.Style.FILL
            paint.color = Ui.TEXT
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = r * 0.9f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            canvas.drawText(if (cc.length == 2) cc else "?", w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT
        }
    }
}

/** Four small signal bars: [level] bars are lit in [color], the rest stay dim. */
class PingBarsView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var level = 0
    private var color = Ui.MUTED

    fun set(newLevel: Int, newColor: Int) {
        level = newLevel.coerceIn(0, 4)
        color = newColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val barW = w / 5.5f
        val gap = barW * 0.5f
        for (i in 0 until 4) {
            val barH = h * (0.38f + 0.62f * i / 3f)
            val left = i * (barW + gap)
            rect.set(left, h - barH, left + barW, h)
            paint.color = if (i < level) color else Ui.DIM
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
        }
    }
}

/** Rolling mini line chart with a soft gradient fill, used for live download/upload speed. */
class SparklineView(context: Context, private val color: Int) : View(context) {
    private val samples = FloatArray(SIZE)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Path()
    private val area = Path()
    private val pad = resources.displayMetrics.density * 3f

    init {
        linePaint.color = color
        linePaint.strokeWidth = resources.displayMetrics.density * 1.8f
    }

    fun push(value: Float) {
        System.arraycopy(samples, 1, samples, 0, SIZE - 1)
        samples[SIZE - 1] = value.coerceAtLeast(0f)
        invalidate()
    }

    fun clear() {
        samples.fill(0f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Ui.alpha(color, 90), Ui.alpha(color, 0),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        var peak = 4096f
        for (v in samples) if (v > peak) peak = v
        val step = w / (SIZE - 1)
        fun y(i: Int) = h - pad - (samples[i] / peak) * (h - pad * 2f)
        line.reset()
        line.moveTo(0f, y(0))
        for (i in 1 until SIZE) {
            val x0 = (i - 1) * step
            val x1 = i * step
            val midX = (x0 + x1) / 2f
            line.cubicTo(midX, y(i - 1), midX, y(i), x1, y(i))
        }
        area.set(line)
        area.lineTo(w, h)
        area.lineTo(0f, h)
        area.close()
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)
    }

    private companion object {
        const val SIZE = 24
    }
}
