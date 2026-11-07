package com.opensstp.android

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Full-screen browser for the public VPN Gate server list.
 * Tap a server to put it in the connection form; tap "+" to keep it in the saved configs.
 */
class VpnGateScreen(
    private val activity: android.app.Activity,
    private val onUse: (VpnGateServer) -> Unit,
    private val onSave: (VpnGateServer) -> Boolean
) {
    private enum class Sort { SCORE, PING, SPEED }

    private val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val executor = Executors.newSingleThreadExecutor()
    private var servers: List<VpnGateServer> = emptyList()
    private var sort = Sort.SCORE
    private var query = ""
    private var loading = false

    private lateinit var listBox: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var notice: TextView
    private lateinit var sortViews: Map<Sort, TextView>
    private val savedAddresses = HashSet<String>()

    fun show() {
        build()
        dialog.setOnDismissListener { executor.shutdownNow() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ui.BG0))
            statusBarColor = Ui.BG0
            navigationBarColor = Ui.BG1
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val cached = VpnGate.cached(activity)
        if (cached != null) {
            servers = cached.first
            subtitle.text = "ذخیره‌شده · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(cached.second))}"
            render()
        }
        refresh()
    }

    // ───────── data ─────────

    private fun refresh() {
        if (loading) return
        loading = true
        notice.visibility = View.VISIBLE
        notice.setTextColor(Ui.TEXT2)
        notice.text = "در حال دریافت فهرست سرورها…"
        executor.execute {
            val result = try {
                Result.success(VpnGate.fetch(activity.applicationContext))
            } catch (e: Exception) {
                Result.failure(e)
            }
            activity.runOnUiThread {
                loading = false
                if (!dialog.isShowing) return@runOnUiThread
                result.onSuccess {
                    servers = it
                    subtitle.text = "${it.size} سرور · همین حالا به‌روز شد"
                    notice.visibility = View.GONE
                    render()
                }.onFailure {
                    notice.visibility = View.VISIBLE
                    notice.setTextColor(Ui.AMBER)
                    notice.text = "دریافت فهرست ناموفق بود (${it.message ?: "خطا"}). " +
                        "اگر vpngate.net در شبکهٔ شما بسته است، اول به یکی از سرورهای ذخیره‌شده وصل شوید و دوباره «↻» را بزنید، " +
                        "یا متن CSV را از سایت کپی و با «چسباندن CSV» وارد کنید."
                    render()
                }
            }
        }
    }

    private fun pasteCsv() {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
        val list = VpnGate.importCsv(activity, text)
        if (list.isEmpty()) {
            Toast.makeText(activity, "در کلیپ‌بورد فهرست VPN Gate پیدا نشد.", Toast.LENGTH_SHORT).show()
            return
        }
        servers = list
        subtitle.text = "${list.size} سرور · از کلیپ‌بورد"
        notice.visibility = View.GONE
        render()
    }

    private fun editSource() {
        val input = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(VpnGate.sourceUrl(activity))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        AlertDialog.Builder(activity)
            .setTitle("آدرس منبع فهرست")
            .setMessage("خروجی باید همان قالب CSV سایت VPN Gate باشد و آدرس با https:// شروع شود.")
            .setView(input)
            .setNeutralButton("پیش‌فرض") { _, _ ->
                VpnGate.setSourceUrl(activity, VpnGate.DEFAULT_URL)
                refresh()
            }
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره") { _, _ ->
                VpnGate.setSourceUrl(activity, input.text.toString())
                refresh()
            }
            .show()
    }

    // ───────── UI ─────────

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun text(t: String, sp: Float, color: Int, bold: Boolean = false, gravity: Int = Gravity.RIGHT) =
        TextView(activity).apply {
            text = t
            textSize = sp
            setTextColor(color)
            this.gravity = gravity
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun iconButton(kind: IconKind, desc: String, onClick: () -> Unit): View {
        val b = FrameLayout(activity).apply {
            isClickable = true
            contentDescription = desc
            background = Ui.ripple(activity, Ui.shape(activity, Ui.CARD_HI, 14f, Ui.STROKE, 1f), 14f)
            setOnClickListener { onClick() }
        }
        b.addView(IconView(activity, kind, Ui.ACCENT_SOFT), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        return b
    }

    private fun build() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(36), dp(16), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.BG0, Ui.BG1))
        }

        // Header
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("سرورهای VPN Gate", 18f, Ui.TEXT, bold = true))
        subtitle = text("", 11f, Ui.MUTED)
        titles.addView(subtitle)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val gap = { LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) } }
        header.addView(iconButton(IconKind.CLIPBOARD, "چسباندن CSV از کلیپ‌بورد") { pasteCsv() }, gap())
        header.addView(iconButton(IconKind.KEY, "تنظیم آدرس منبع") { editSource() }, gap())
        header.addView(iconButton(IconKind.REFRESH, "به‌روزرسانی فهرست") { refresh() }, gap())
        header.addView(iconButton(IconKind.CLOSE, "بستن") { dialog.dismiss() }, gap())
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Search
        val search = EditText(activity).apply {
            hint = "جستجوی کشور یا نام سرور…"
            textSize = 14f
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            setSingleLine(true)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = Ui.shape(activity, Ui.FIELD, 14f, Ui.STROKE, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty().trim().lowercase()
                    render()
                }
            })
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(14) })

        // Sort chips
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val map = LinkedHashMap<Sort, TextView>()
        for ((s, title) in listOf(Sort.SCORE to "بهترین امتیاز", Sort.PING to "کمترین پینگ", Sort.SPEED to "سریع‌ترین")) {
            val chip = text(title, 12f, Ui.TEXT2, bold = true, gravity = Gravity.CENTER).apply {
                setPadding(dp(14), dp(7), dp(14), dp(7))
                isClickable = true
                setOnClickListener { sort = s; render() }
            }
            map[s] = chip
            chips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        sortViews = map
        root.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        notice = text("", 12f, Ui.TEXT2).apply { visibility = View.GONE }
        root.addView(notice, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        listBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(4), 0, dp(16))
        }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        dialog.setContentView(root)
    }

    private fun render() {
        for ((s, v) in sortViews) {
            val on = s == sort
            v.setTextColor(if (on) Color.WHITE else Ui.TEXT2)
            v.background = if (on) Ui.gradient(activity, Ui.ACCENT, Ui.ACCENT2, 16f)
            else Ui.shape(activity, Ui.CARD, 16f, Ui.STROKE, 1f)
        }
        listBox.removeAllViews()
        if (servers.isEmpty()) return

        val fa = Locale("fa")
        fun countryFa(s: VpnGateServer) = Flags.displayName(s.countryCode, s.countryName)
        val filtered = servers.filter { s ->
            query.isEmpty() ||
                s.countryName.lowercase().contains(query) ||
                countryFa(s).lowercase(fa).contains(query) ||
                s.countryCode.lowercase() == query ||
                s.host.lowercase().contains(query) ||
                s.ip.contains(query)
        }
        val sorted = when (sort) {
            Sort.SCORE -> filtered.sortedByDescending { it.score }
            Sort.PING -> filtered.sortedBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            Sort.SPEED -> filtered.sortedByDescending { it.speedBps }
        }
        if (sorted.isEmpty()) {
            listBox.addView(text("موردی پیدا نشد.", 13f, Ui.MUTED, gravity = Gravity.CENTER).apply { setPadding(0, dp(24), 0, 0) })
            return
        }
        for (s in sorted.take(150)) listBox.addView(row(s, countryFa(s)), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        if (sorted.size > 150) {
            listBox.addView(text("برای دیدن بقیه، جستجو کنید (${sorted.size - 150} مورد دیگر)", 12f, Ui.MUTED, gravity = Gravity.CENTER)
                .apply { setPadding(0, dp(14), 0, 0) })
        }
    }

    private fun row(s: VpnGateServer, country: String): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(10), dp(12), dp(10))
            isClickable = true
            background = Ui.ripple(activity, Ui.shape(activity, Ui.CARD, 18f, Ui.STROKE, 1f), 18f)
            setOnClickListener {
                onUse(s)
                dialog.dismiss()
            }
        }
        row.addView(FlagView(activity, s.countryCode), LinearLayout.LayoutParams(dp(40), dp(40)))

        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(country, 14f, Ui.TEXT, bold = true).apply { isSingleLine = true })
        texts.addView(text(s.address, 12f, Ui.TEXT2).apply {
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LTR
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })

        val stats = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val pingColor = when {
            s.pingMs <= 0 -> Ui.MUTED
            s.pingMs < 150 -> Ui.GREEN
            s.pingMs < 400 -> Ui.AMBER
            else -> Ui.RED
        }
        stats.addView(text(if (s.pingMs > 0) "${s.pingMs} ms" else "—", 13f, pingColor, bold = true, gravity = Gravity.LEFT).apply {
            textDirection = View.TEXT_DIRECTION_LTR
        })
        stats.addView(text(String.format(Locale.US, "%.1f Mbps", s.speedMbps), 11f, Ui.TEXT2, gravity = Gravity.LEFT).apply {
            textDirection = View.TEXT_DIRECTION_LTR
        })
        row.addView(stats, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })

        val already = savedAddresses.contains(s.address)
        val add = FrameLayout(activity).apply {
            isClickable = true
            contentDescription = "ذخیرهٔ کانفیگ"
            background = Ui.ripple(activity, Ui.oval(Ui.alpha(Ui.ACCENT, 34)), 19f)
        }
        val addIcon = IconView(activity, if (already) IconKind.SAVE else IconKind.PLUS, Ui.ACCENT_SOFT)
        add.addView(addIcon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        add.setOnClickListener {
            if (onSave(s)) {
                savedAddresses.add(s.address)
                addIcon.changeKind(IconKind.SAVE)
            }
        }
        row.addView(add, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(8) })
        return row
    }
}
