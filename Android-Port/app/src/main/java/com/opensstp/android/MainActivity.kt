package com.opensstp.android

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var server: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var remember: CompoundButton
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var pill: LinearLayout
    private lateinit var pillDot: View
    private lateinit var orb: PowerOrbView
    private lateinit var rxValue: TextView
    private lateinit var txValue: TextView
    private lateinit var rxSpark: SparklineView
    private lateinit var txSpark: SparklineView
    private lateinit var timerView: TextView
    private val tickHandler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private val tick = object : Runnable {
        override fun run() {
            updateTimer()
            if (timerRunning) tickHandler.postDelayed(this, 1000L)
        }
    }
    private lateinit var connectButton: Button
    private lateinit var configList: LinearLayout
    private var sessionCanBeStopped = false
    private var selectedConfig: String? = null
    private lateinit var appPrefs: SharedPreferences
    private lateinit var scroll: ScrollView
    private lateinit var scrim: View
    private lateinit var drawerPanel: LinearLayout
    private lateinit var drawerStatus: TextView
    private lateinit var drawerStatusDot: View
    private lateinit var backCallback: OnBackPressedCallback
    private var drawerOpen = false
    private var drawerWidthPx = 0
    private val pingExecutor = Executors.newFixedThreadPool(3)
    private val pingCache = HashMap<String, PingProbe.Result>()
    private val pingInFlight = HashSet<String>()
    private val geoInFlight = HashSet<String>()
    private val geoFailed = HashSet<String>()
    private var configsCollapsed = false

    private class Field(val block: View, val edit: EditText)

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SoftEtherVpnService.ACTION_STATUS) return
            val message = intent.getStringExtra(SoftEtherVpnService.EXTRA_STATUS) ?: ""
            val state = intent.getStringExtra(SoftEtherVpnService.EXTRA_STATE)
                ?: SoftEtherVpnService.STATE_DISCONNECTED
            // Traffic-only broadcasts intentionally omit the human-readable status text.
            if (intent.hasExtra(SoftEtherVpnService.EXTRA_STATUS)) renderStatus(message, state)
            if (intent.hasExtra(SoftEtherVpnService.EXTRA_RX_RATE)) {
                rxValue.text = intent.getStringExtra(SoftEtherVpnService.EXTRA_RX_RATE) ?: "0 B/s"
                rxSpark.push(intent.getLongExtra(SoftEtherVpnService.EXTRA_RX_BPS, 0L).toFloat())
            } else if (state == SoftEtherVpnService.STATE_DISCONNECTED || state == SoftEtherVpnService.STATE_ERROR) {
                rxValue.text = "0 B/s"
                txValue.text = "0 B/s"
                rxSpark.clear()
                txSpark.clear()
            }
            if (intent.hasExtra(SoftEtherVpnService.EXTRA_TX_RATE)) {
                txValue.text = intent.getStringExtra(SoftEtherVpnService.EXTRA_TX_RATE) ?: "0 B/s"
                txSpark.push(intent.getLongExtra(SoftEtherVpnService.EXTRA_TX_BPS, 0L).toFloat())
            }
        }
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
        else renderStatus(Lang.t("vpn_perm_denied"), SoftEtherVpnService.STATE_DISCONNECTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        // Theme is stored in plain prefs (not the encrypted ones, which "Connect" may clear).
        Ui.applyTheme(getSharedPreferences("ui_prefs", MODE_PRIVATE).getBoolean("dark", true))
        window.setBackgroundDrawable(ColorDrawable(Ui.BG0))
        window.statusBarColor = Ui.BG0
        window.navigationBarColor = Ui.BG1
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !Ui.dark
            isAppearanceLightNavigationBars = !Ui.dark
        }
        configsCollapsed = getSharedPreferences("ui_prefs", MODE_PRIVATE).getBoolean("configs_collapsed", false)
        val prefs = securePrefs()
        appPrefs = prefs
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = closeDrawer()
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.dir
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }

        // ───────── Header ─────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val logo = FrameLayout(this).apply {
            background = Ui.gradient(this@MainActivity, Ui.ACCENT, Ui.ACCENT2, 14f, android.graphics.drawable.GradientDrawable.Orientation.TL_BR)
        }
        logo.addView(IconView(this, IconKind.SHIELD, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(label("Open-SSTP", 21f, Ui.TEXT, bold = true, gravity = Gravity.LEFT))
        titles.addView(label("SOFTETHER VPN FOR ANDROID", 10f, Ui.MUTED, gravity = Gravity.LEFT).apply { letterSpacing = 0.1f })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val menuButton = FrameLayout(this).apply {
            isClickable = true
            contentDescription = Lang.t("menu")
            background = Ui.ripple(this@MainActivity, Ui.shape(this@MainActivity, Ui.CARD_HI, 14f, Ui.STROKE, 1f), 14f)
        }
        menuButton.addView(IconView(this, IconKind.MENU, Ui.ACCENT_SOFT), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        val themeIcon = IconView(this, if (Ui.dark) IconKind.SUN else IconKind.MOON, if (Ui.dark) Ui.AMBER else Ui.ACCENT_SOFT)
        val themeButton = FrameLayout(this).apply {
            isClickable = true
            contentDescription = if (Ui.dark) Lang.t("to_light") else Lang.t("to_dark")
            background = Ui.ripple(this@MainActivity, Ui.shape(this@MainActivity, Ui.CARD_HI, 14f, Ui.STROKE, 1f), 14f)
            setOnClickListener { toggleTheme(themeIcon) }
        }
        themeButton.addView(themeIcon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        header.addView(themeButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        header.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header, match())

        // ───────── Hero orb + status ─────────
        orb = PowerOrbView(this).apply {
            isClickable = true
            contentDescription = Lang.t("toggle_conn")
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                }
                false
            }
        }
        root.addView(orb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply { topMargin = dp(10) })

        pillDot = View(this).apply { background = Ui.oval(Ui.IDLE) }
        status = label(Lang.t("disconnected"), 13f, Ui.TEXT2, bold = true, gravity = Gravity.CENTER).apply { maxLines = 2 }
        pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.dir
            setPadding(dp(14), dp(8), dp(16), dp(8))
            background = Ui.shape(this@MainActivity, Ui.alpha(Ui.IDLE, 30), 22f, Ui.alpha(Ui.IDLE, 90), 1f)
        }
        pill.addView(pillDot, LinearLayout.LayoutParams(dp(8), dp(8)))
        pill.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        timerView = label("00:00:00", 13f, Ui.TEXT2, gravity = Gravity.CENTER).apply {
            textDirection = View.TEXT_DIRECTION_LTR
            visibility = View.GONE
        }
        pill.addView(timerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        root.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(2)
        })
        hint = label(Lang.t("tap_connect"), 12f, Ui.MUTED, gravity = Gravity.CENTER)
        root.addView(hint, params(top = 10))

        // ───────── Live speed cards (with mini charts) ─────────
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val rxCard = speedCard(IconKind.ARROW_DOWN, Ui.GREEN, "DOWNLOAD")
        val txCard = speedCard(IconKind.ARROW_UP, Ui.ACCENT_SOFT, "UPLOAD")
        rxValue = rxCard.value
        txValue = txCard.value
        rxSpark = rxCard.spark
        txSpark = txCard.spark
        metrics.addView(rxCard.view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        metrics.addView(txCard.view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
        root.addView(metrics, params(top = 18))

        // ───────── VPN Gate browser ─────────
        val gateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.dir
            isClickable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = Ui.ripple(this@MainActivity, Ui.shape(this@MainActivity, Ui.CARD, 18f, Ui.STROKE, 1f), 18f)
            setOnClickListener { openVpnGate() }
        }
        val gateBadge = FrameLayout(this).apply { background = Ui.shape(this@MainActivity, Ui.alpha(Ui.CYAN, 34), 12f) }
        gateBadge.addView(IconView(this, IconKind.GLOBE, Ui.CYAN), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        gateRow.addView(gateBadge, LinearLayout.LayoutParams(dp(40), dp(40)))
        val gateTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gateTexts.addView(label(Lang.t("gate_title"), 14f, Ui.TEXT, bold = true))
        gateTexts.addView(label(Lang.t("gate_sub"), 12f, Ui.TEXT2))
        gateRow.addView(gateTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        gateRow.addView(IconView(this, IconKind.CHEVRON, Ui.MUTED), LinearLayout.LayoutParams(dp(16), dp(16)))
        root.addView(gateRow, params(top = 18))

        // ───────── Connection form ─────────
        val form = card()
        form.addView(sectionHeader(Lang.t("conn_info"), null), match())
        val serverField = buildField(Lang.t("server"), IconKind.SERVER, "VPN Gate hostname or IP[:port]", input = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val userField = buildField(Lang.t("username"), IconKind.USER, "Username", input = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val passField = buildField(Lang.t("password"), IconKind.LOCK, "Password (VPN Gate often blank)", isPassword = true)
        server = serverField.edit.apply { setText(prefs.getString("server", "")) }
        username = userField.edit.apply { setText(prefs.getString("username", "")) }
        password = passField.edit.apply { setText(prefs.getString("password", "")) }
        form.addView(serverField.block, params(top = 14))
        form.addView(userField.block, params(top = 12))
        form.addView(passField.block, params(top = 12))

        remember = Switch(this).apply {
            isChecked = prefs.getBoolean("remember", false)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Ui.TEXT2))
            trackTintList = ColorStateList(states, intArrayOf(Ui.ACCENT, Ui.STROKE))
        }
        val rememberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.dir
            isClickable = true
            setPadding(dp(2), dp(4), dp(2), dp(4))
            setOnClickListener { remember.toggle() }
        }
        val rememberText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rememberText.addView(label(Lang.t("remember"), 14f, Ui.TEXT))
        rememberText.addView(label("Remember me", 11f, Ui.MUTED))
        rememberRow.addView(rememberText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rememberRow.addView(remember, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        form.addView(rememberRow, params(top = 16))
        root.addView(form, params(top = 8))

        // ───────── Connect button ─────────
        connectButton = Button(this).apply {
            text = "Connect"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = Ui.ripple(this@MainActivity, Ui.gradient(this@MainActivity, Ui.ACCENT, Ui.ACCENT2, 18f), 18f)
        }
        root.addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(18) })

        // ───────── Saved configs (under the form; can be collapsed) ─────────
        configList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.dir
        }
        root.addView(configList, params(top = 24))
        root.addView(
            label("Built-in SoftEther protocol engine. Enter a server and valid account details to connect.", 11f, Ui.MUTED, gravity = Gravity.CENTER),
            params(top = 14)
        )

        scroll = ScrollView(this).apply {
            isFillViewport = true
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.BG0, Ui.BG1)
            )
            addView(root)
        }

        val frame = FrameLayout(this)
        frame.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim = View(this).apply {
            setBackgroundColor(Color.argb(170, 2, 5, 11))
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { closeDrawer() }
        }
        frame.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawerWidthPx = minOf((resources.displayMetrics.widthPixels * 0.84f).toInt(), dp(330))
        drawerPanel = buildDrawer()
        drawerPanel.translationX = drawerWidthPx.toFloat()
        drawerPanel.visibility = View.INVISIBLE
        frame.addView(drawerPanel, FrameLayout.LayoutParams(drawerWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT))
        setContentView(frame)

        renderConfigs(prefs)
        menuButton.setOnClickListener { openDrawer() }
        orb.setOnClickListener { connectButton.performClick() }

        remember.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                prefs.edit()
                    .remove("server")
                    .remove("username")
                    .remove("password")
                    .putBoolean("remember", false)
                    .apply()
            }
        }

        connectButton.setOnClickListener {
            if (sessionCanBeStopped) {
                startService(
                    Intent(this, SoftEtherVpnService::class.java).setAction(SoftEtherVpnService.ACTION_DISCONNECT)
                )
                renderStatus(Lang.t("disconnecting"), SoftEtherVpnService.STATE_DISCONNECTED)
                return@setOnClickListener
            }

            if (server.text.isNullOrBlank()) {
                server.error = Lang.t("enter_server")
                server.requestFocus()
                return@setOnClickListener
            }
            if (remember.isChecked) {
                prefs.edit()
                    .putString("server", server.text.toString().trim())
                    .putString("username", username.text.toString().trim())
                    .putString("password", password.text.toString())
                    .putBoolean("remember", true)
                    .apply()
            } else {
                // Do not retain credentials after the user has unchecked Remember me.
                prefs.edit().clear().apply()
            }

            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent != null) vpnPermission.launch(permissionIntent) else startVpn()
        }

        renderStatus(Lang.t("disconnected"), SoftEtherVpnService.STATE_DISCONNECTED)

        // Gentle staggered entrance
        if (savedInstanceState == null) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                child.alpha = 0f
                child.translationY = dp(16).toFloat()
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(50L * i)
                    .setDuration(360L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SoftEtherVpnService.uiVisible = true
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(SoftEtherVpnService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Asking for status does not start a tunnel. It lets the UI catch up after being reopened.
        startService(Intent(this, SoftEtherVpnService::class.java).setAction(SoftEtherVpnService.ACTION_QUERY))
    }

    override fun onStop() {
        SoftEtherVpnService.uiVisible = false
        try { unregisterReceiver(statusReceiver) } catch (_: IllegalArgumentException) { }
        timerRunning = false
        tickHandler.removeCallbacks(tick)
        super.onStop()
    }

    /** Spins the sun/moon icon, saves the choice and rebuilds the screen with the other palette. */
    private fun toggleTheme(icon: View) {
        getSharedPreferences("ui_prefs", MODE_PRIVATE).edit().putBoolean("dark", !Ui.dark).apply()
        icon.animate().rotationBy(180f).scaleX(0.4f).scaleY(0.4f).alpha(0f).setDuration(220L)
            .withEndAction { recreate() }
            .start()
    }

    private fun updateTimer() {
        val since = SoftEtherVpnService.connectedSinceMs
        if (since <= 0L) {
            timerView.visibility = View.GONE
            return
        }
        val total = ((System.currentTimeMillis() - since) / 1000L).coerceAtLeast(0L)
        timerView.text = String.format(Locale.US, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        timerView.visibility = View.VISIBLE
    }

    private fun syncTimer(state: String) {
        tickHandler.removeCallbacks(tick)
        if (state == SoftEtherVpnService.STATE_CONNECTED) {
            timerRunning = true
            tickHandler.post(tick)
        } else {
            timerRunning = false
            timerView.visibility = View.GONE
        }
    }

    private fun startVpn() {
        val intent = Intent(this, SoftEtherVpnService::class.java).apply {
            action = SoftEtherVpnService.ACTION_CONNECT
            putExtra(SoftEtherVpnService.EXTRA_SERVER, server.text.toString().trim())
            putExtra(SoftEtherVpnService.EXTRA_USERNAME, username.text.toString().trim())
            putExtra(SoftEtherVpnService.EXTRA_PASSWORD, password.text.toString())
        }
        ContextCompat.startForegroundService(this, intent)
        renderStatus(Lang.t("connecting"), SoftEtherVpnService.STATE_CONNECTING)
    }

    private fun renderStatus(message: String, state: String) {
        // The pill stays short while connected (the timer sits next to it); the drawer shows the full text.
        status.text = if (state == SoftEtherVpnService.STATE_CONNECTED) Lang.t("connected") else message
        syncTimer(state)
        sessionCanBeStopped = state == SoftEtherVpnService.STATE_CONNECTED || state == SoftEtherVpnService.STATE_CONNECTING
        connectButton.text = if (sessionCanBeStopped) "Disconnect" else "Connect"
        connectButton.background = Ui.ripple(
            this,
            if (sessionCanBeStopped) Ui.gradient(this, Ui.DANGER_A, Ui.DANGER_B, 18f)
            else Ui.gradient(this, Ui.ACCENT, Ui.ACCENT2, 18f),
            18f
        )
        val color = when (state) {
            SoftEtherVpnService.STATE_CONNECTED -> Ui.GREEN
            SoftEtherVpnService.STATE_CONNECTING -> Ui.CYAN
            SoftEtherVpnService.STATE_ERROR -> Ui.RED
            else -> Ui.TEXT2
        }
        status.setTextColor(color)
        if (::drawerStatus.isInitialized) {
            drawerStatus.text = message
            drawerStatus.setTextColor(color)
            drawerStatusDot.background = Ui.oval(color)
        }
        pillDot.background = Ui.oval(color)
        pill.background = Ui.shape(this, Ui.alpha(color, 30), 22f, Ui.alpha(color, 90), 1f)
        orb.setState(state)
        hint.text = if (sessionCanBeStopped) Lang.t("tap_disconnect")
        else Lang.t("tap_connect")
        // While connecting, keep the form editable; the native service validates server/port.
        connectButton.isEnabled = true
    }

    private fun promptSaveConfig(prefs: SharedPreferences) {
        val serverValue = server.text.toString().trim()
        if (serverValue.isBlank()) {
            server.error = Lang.t("enter_server_first")
            server.requestFocus()
            Toast.makeText(this, Lang.t("enter_server_save"), Toast.LENGTH_SHORT).show()
            return
        }

        val nameInput = EditText(this).apply {
            hint = Lang.t("name_hint")
            setSingleLine(true)
            setText(Lang.t("config_n", readConfigs(prefs).length() + 1))
        }
        AlertDialog.Builder(this)
            .setTitle(Lang.t("save_cfg_title"))
            .setMessage(Lang.t("save_cfg_msg"))
            .setView(nameInput)
            .setNegativeButton(Lang.t("cancel"), null)
            .setPositiveButton(Lang.t("save")) { _, _ ->
                val configName = nameInput.text.toString().trim()
                if (configName.isBlank()) {
                    Toast.makeText(this, Lang.t("need_name"), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val configs = readConfigs(prefs)
                val item = JSONObject()
                    .put("name", configName)
                    .put("server", serverValue)
                    .put("username", username.text.toString().trim())
                    .put("password", password.text.toString())
                var replaced = false
                for (i in 0 until configs.length()) {
                    if (configs.optJSONObject(i)?.optString("name") == configName) {
                        configs.put(i, item)
                        replaced = true
                        break
                    }
                }
                if (!replaced) configs.put(item)
                prefs.edit().putString("configs_json", configs.toString()).apply()
                selectedConfig = configName
                renderConfigs(prefs)
                Toast.makeText(this, Lang.t("cfg_saved", configName), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ───────── Hamburger drawer ─────────

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        backCallback.isEnabled = true
        currentFocus?.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        scrim.visibility = View.VISIBLE
        scrim.animate().alpha(1f).setDuration(220L).start()
        drawerPanel.visibility = View.VISIBLE
        drawerPanel.animate().translationX(0f).setDuration(280L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        backCallback.isEnabled = false
        scrim.animate().alpha(0f).setDuration(200L).withEndAction {
            if (!drawerOpen) scrim.visibility = View.GONE
        }.start()
        drawerPanel.animate().translationX(drawerWidthPx.toFloat()).setDuration(220L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { if (!drawerOpen) drawerPanel.visibility = View.INVISIBLE }
            .start()
    }

    private fun buildDrawer(): LinearLayout {
        val r = dp(28).toFloat()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.dir
            isClickable = true
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Ui.BG1, Ui.BG0)
            ).apply {
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                setStroke(dp(1), Ui.STROKE)
            }
        }

        // Header: logo, title, live connection state, close button
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(44), dp(16), dp(18))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Ui.alpha(Ui.ACCENT, 70), Ui.alpha(Ui.ACCENT2, 34))
            ).apply { cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, 0f, 0f) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val logo = FrameLayout(this).apply {
            background = Ui.gradient(this@MainActivity, Ui.ACCENT, Ui.ACCENT2, 14f, android.graphics.drawable.GradientDrawable.Orientation.TL_BR)
        }
        logo.addView(IconView(this, IconKind.SHIELD, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        top.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(label("Open-SSTP", 20f, Ui.TEXT, bold = true, gravity = Gravity.LEFT))
        val ver = appVersion()
        titles.addView(label(if (ver.isBlank()) "SOFTETHER VPN" else "SOFTETHER VPN  ·  v$ver", 10f, Ui.TEXT2, gravity = Gravity.LEFT).apply { letterSpacing = 0.08f })
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val closeButton = FrameLayout(this).apply {
            isClickable = true
            contentDescription = Lang.t("close_menu")
            background = Ui.ripple(this@MainActivity, Ui.shape(this@MainActivity, Ui.alpha(Ui.BG0, 150), 14f, Ui.STROKE, 1f), 14f)
            setOnClickListener { closeDrawer() }
        }
        closeButton.addView(IconView(this, IconKind.CLOSE, Ui.TEXT2), FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
        top.addView(closeButton, LinearLayout.LayoutParams(dp(38), dp(38)))
        head.addView(top, match())

        drawerStatusDot = View(this).apply { background = Ui.oval(Ui.IDLE) }
        drawerStatus = label(Lang.t("disconnected"), 12f, Ui.TEXT2, bold = true, gravity = Gravity.LEFT).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = Ui.shape(this@MainActivity, Ui.alpha(Ui.BG0, 140), 14f, Ui.STROKE, 1f)
        }
        statusRow.addView(drawerStatusDot, LinearLayout.LayoutParams(dp(8), dp(8)))
        statusRow.addView(drawerStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
        head.addView(statusRow, params(top = 16))
        panel.addView(head, match())

        // Items
        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.dir
            setPadding(dp(12), dp(4), dp(12), dp(12))
        }
        items.addView(drawerSection(Lang.t("sec_configs")), params(top = 12, bottom = 6))
        items.addView(
            drawerGroup(
                drawerItem(IconKind.GLOBE, Ui.CYAN, Lang.t("m_gate"), Lang.t("m_gate_sub")) { openVpnGate() },
                drawerItem(IconKind.SAVE, Ui.ACCENT_SOFT, Lang.t("m_save"), Lang.t("m_save_sub")) { promptSaveConfig(appPrefs) },
                drawerItem(IconKind.SERVER, Ui.CYAN, Lang.t("m_saved"), Lang.t("m_saved_sub")) { showSavedConfigs() },
                drawerItem(IconKind.EYE_OFF, Ui.AMBER, Lang.t("m_toggle"), Lang.t("m_toggle_sub")) { setConfigsCollapsed(!configsCollapsed) },
                drawerItem(IconKind.CLIPBOARD, Ui.GREEN, Lang.t("m_clip"), Lang.t("m_clip_sub")) { importFromClipboard() },
                drawerItem(IconKind.SHARE, Ui.ACCENT2, Lang.t("m_share"), Lang.t("m_share_sub")) { shareServer() }
            ),
            match()
        )

        items.addView(drawerSection(Lang.t("sec_system")), params(top = 16, bottom = 6))
        items.addView(
            drawerGroup(
                drawerItem(IconKind.KEY, Ui.ACCENT_SOFT, Lang.t("m_vpnset"), Lang.t("m_vpnset_sub")) { openSystemScreen(Settings.ACTION_VPN_SETTINGS) },
                drawerItem(IconKind.BATTERY, Ui.AMBER, Lang.t("m_battery"), Lang.t("m_battery_sub")) { openSystemScreen(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            ),
            match()
        )

        items.addView(drawerSection(Lang.t("sec_app")), params(top = 16, bottom = 6))
        items.addView(
            drawerGroup(
                drawerItem(IconKind.GLOBE, Ui.ACCENT2, Lang.t("language"), Lang.t("language_sub")) { showLanguagePicker() },
                drawerItem(IconKind.HELP, Ui.CYAN, Lang.t("m_help"), Lang.t("m_help_sub")) { showHelp() },
                drawerItem(IconKind.INFO, Ui.TEXT2, Lang.t("m_about"), Lang.t("m_about_sub")) { showAbout() },
                drawerItem(IconKind.TRASH, Ui.RED, Lang.t("m_clear"), Lang.t("m_clear_sub"), Ui.RED) { confirmClearData() },
                drawerItem(IconKind.EXIT, Ui.TEXT2, Lang.t("m_exit"), Lang.t("m_exit_sub")) { finish() }
            ),
            match()
        )

        val itemsScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(items)
        }
        panel.addView(itemsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(
            label("Built-in SoftEther protocol engine", 10f, Ui.MUTED, gravity = Gravity.CENTER).apply {
                setPadding(dp(12), dp(10), dp(12), dp(16))
            },
            match()
        )
        return panel
    }

    private fun drawerSection(title: String): TextView =
        label(title, 12f, Ui.TEXT2, bold = true).apply {
            setPadding(dp(8), 0, dp(8), 0)
        }

    /** A rounded card that holds several drawer rows separated by hairline dividers. */
    private fun drawerGroup(vararg rows: View): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.dir
            background = Ui.shape(this@MainActivity, Ui.GROUP_FILL, 22f, Ui.GROUP_STROKE, 1f)
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                group.addView(
                    View(this).apply { setBackgroundColor(Ui.DIVIDER) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(14)
                        marginEnd = dp(14)
                    }
                )
            }
            group.addView(row, match())
        }
        return group
    }

    private fun drawerItem(
        kind: IconKind,
        color: Int,
        title: String,
        subtitle: String,
        titleColor: Int = Ui.TEXT,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.dir
            isClickable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = Ui.ripple(this@MainActivity, Ui.shape(this@MainActivity, Color.TRANSPARENT, 18f), 18f)
            setOnClickListener {
                closeDrawer()
                onClick()
            }
        }
        val badge = FrameLayout(this).apply { background = Ui.shape(this@MainActivity, Ui.alpha(color, 34), 12f) }
        badge.addView(IconView(this, kind, color), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        row.addView(badge, LinearLayout.LayoutParams(dp(40), dp(40)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 14f, titleColor, bold = true))
        texts.addView(label(subtitle, 12f, Ui.TEXT2, gravity = Lang.start).apply {
            textDirection = View.TEXT_DIRECTION_RTL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }, params(top = 1))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(8)
        })
        row.addView(IconView(this, IconKind.CHEVRON, Ui.MUTED), LinearLayout.LayoutParams(dp(16), dp(16)))
        return row
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun showSavedConfigs() {
        if (readConfigs(appPrefs).length() == 0) {
            toast(Lang.t("no_saved"))
            return
        }
        if (configsCollapsed) setConfigsCollapsed(false)
        scroll.post { scroll.smoothScrollTo(0, configList.top) }
    }

    private fun setConfigsCollapsed(collapsed: Boolean) {
        configsCollapsed = collapsed
        getSharedPreferences("ui_prefs", MODE_PRIVATE).edit().putBoolean("configs_collapsed", collapsed).apply()
        renderConfigs(appPrefs)
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (raw.isBlank()) {
            toast(Lang.t("clip_empty"))
            return
        }
        val firstToken = raw.lineSequence().first().trim().split(Regex("\\s+")).first()
        val value = firstToken.substringAfter("://").substringBefore('/')
        if (value.isBlank() || value.length > 255 || !value.matches(Regex("[A-Za-z0-9._:\\[\\]-]+"))) {
            toast(Lang.t("clip_not_server"))
            return
        }
        server.setText(value)
        selectedConfig = null
        renderConfigs(appPrefs)
        toast(Lang.t("clip_imported"))
    }

    private fun shareServer() {
        val serverValue = server.text.toString().trim()
        if (serverValue.isBlank()) {
            toast(Lang.t("enter_server_first"))
            return
        }
        val text = "Open-SSTP\nServer: $serverValue\nUsername: ${username.text.toString().trim()}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(send, Lang.t("share_title")))
        } catch (_: ActivityNotFoundException) {
            toast(Lang.t("no_share_app"))
        }
    }

    private fun openSystemScreen(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast(Lang.t("page_unavailable"))
        }
    }

    /** Language picker: stores the choice and rebuilds the screen in the new language/direction. */
    private fun showLanguagePicker() {
        closeDrawer()
        val names = Lang.LANGS.map { it.nativeName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(Lang.t("language"))
            .setSingleChoiceItems(names, Lang.current) { dialog, which ->
                dialog.dismiss()
                if (which != Lang.current) {
                    Lang.set(this, which)
                    recreate()
                }
            }
            .setNegativeButton(Lang.t("cancel"), null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(Lang.t("help_title"))
            .setMessage(
                Lang.t("help_body")
            )
            .setPositiveButton(Lang.t("ok"), null)
            .show()
    }

    private fun showAbout() {
        val ver = appVersion()
        AlertDialog.Builder(this)
            .setTitle("Open-SSTP")
            .setMessage(
                "SoftEther VPN for Android" + (if (ver.isBlank()) "" else "\n${Lang.t("version")}: $ver") + "\n\n" + Lang.t("about_extra")
            )
            .setPositiveButton(Lang.t("ok"), null)
            .show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle(Lang.t("clear_title"))
            .setMessage(Lang.t("clear_msg"))
            .setNegativeButton(Lang.t("cancel"), null)
            .setPositiveButton(Lang.t("clear_btn")) { _, _ ->
                appPrefs.edit().clear().apply()
                selectedConfig = null
                server.setText("")
                username.setText("")
                password.setText("")
                remember.isChecked = false
                renderConfigs(appPrefs)
                toast(Lang.t("cleared"))
            }
            .show()
    }

    private fun readConfigs(prefs: SharedPreferences): JSONArray {
        return try {
            JSONArray(prefs.getString("configs_json", "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun renderConfigs(prefs: SharedPreferences) {
        if (!::configList.isInitialized) return
        configList.removeAllViews()
        val configs = readConfigs(prefs)
        if (configs.length() == 0) return

        val headerRow = sectionHeader(Lang.t("saved_configs"), configs.length().toString()).apply {
            isClickable = true
            contentDescription = if (configsCollapsed) Lang.t("show_list") else Lang.t("hide_list")
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener { setConfigsCollapsed(!configsCollapsed) }
        }
        // Arrow at the far end: down = list is hidden (tap to show), up = list is shown (tap to hide).
        headerRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        headerRow.addView(
            IconView(this, IconKind.CHEVRON, Ui.ACCENT_SOFT).apply { rotation = if (configsCollapsed) -90f else 90f },
            LinearLayout.LayoutParams(dp(22), dp(22))
        )
        configList.addView(headerRow, params(bottom = 4))
        if (configsCollapsed) return
        configList.addView(
            label(Lang.t("ping_again"), 12f, Ui.ACCENT_SOFT, bold = true).apply {
                setPadding(dp(4), dp(6), dp(4), dp(2))
                isClickable = true
                setOnClickListener {
                    geoFailed.clear()
                    for (i in 0 until configs.length()) configs.optJSONObject(i)?.let { startPing(it.optString("server", ""), force = true) }
                }
            }
        )
        for (i in 0 until configs.length()) {
            val srv = configs.optJSONObject(i)?.optString("server", "") ?: ""
            if (srv.isNotBlank() && !pingCache.containsKey(srv)) startPing(srv)
            if (srv.isNotBlank() && configs.optJSONObject(i)?.optString("cc", "").isNullOrBlank()) startGeo(srv)
        }

        for (i in 0 until configs.length()) {
            val item = configs.optJSONObject(i) ?: continue
            val configName = item.optString("name", Lang.t("config_n", i + 1))
            val configServer = item.optString("server", "")
            val shownName = Lang.configLabel(configName, item.optString("cc", ""))
            val selected = configName == selectedConfig
            val hi = if (selected && sessionCanBeStopped) Ui.GREEN else Ui.ACCENT

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = Lang.dir
                setPadding(dp(8), dp(10), dp(12), dp(10))
                isClickable = true
                background = Ui.ripple(
                    this@MainActivity,
                    Ui.shape(
                        this@MainActivity,
                        if (selected) Ui.alpha(hi, 28) else Ui.CARD,
                        18f,
                        if (selected) hi else Ui.STROKE,
                        if (selected) 1.5f else 1f
                    ),
                    18f
                )
                setOnClickListener {
                    selectedConfig = configName
                    server.setText(item.optString("server", ""))
                    username.setText(item.optString("username", ""))
                    password.setText(item.optString("password", ""))
                    renderConfigs(prefs)
                    Toast.makeText(this@MainActivity, Lang.t("cfg_selected", shownName), Toast.LENGTH_SHORT).show()
                }
            }

            // Country from the server's IP (stored in the config) first; the config name is only a fallback.
            val flagCode = item.optString("cc", "").ifBlank { Flags.forName(configName) ?: "" }
            if (flagCode.isNotBlank()) {
                row.addView(FlagView(this, flagCode), LinearLayout.LayoutParams(dp(40), dp(40)))
            } else {
                val avatar = FrameLayout(this).apply {
                    background = Ui.oval(Ui.alpha(if (selected) hi else Ui.IDLE, 38))
                }
                avatar.addView(
                    IconView(this, IconKind.SERVER, if (selected) hi else Ui.TEXT2),
                    FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
                )
                row.addView(avatar, LinearLayout.LayoutParams(dp(40), dp(40)))
            }

            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(shownName, 14f, Ui.TEXT, bold = true).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
            texts.addView(label(configServer, 12f, Ui.TEXT2).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textDirection = View.TEXT_DIRECTION_LTR
            }, params(top = 2))
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })

            val (pingLevel, pingColor) = pingQuality(configServer)
            if (pingLevel > 0) {
                row.addView(
                    PingBarsView(this).apply { set(pingLevel, pingColor) },
                    LinearLayout.LayoutParams(dp(22), dp(16)).apply { marginStart = dp(6) }
                )
            }
            val pingView = pingLabel(configServer)
            row.addView(pingView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })

            val deleteButton = FrameLayout(this).apply {
                isClickable = true
                contentDescription = Lang.t("delete_desc", shownName)
                background = Ui.ripple(this@MainActivity, Ui.oval(Ui.alpha(Ui.RED, 28)), 19f)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(Lang.t("delete_ask", shownName))
                        .setNegativeButton(Lang.t("cancel"), null)
                        .setPositiveButton(Lang.t("delete")) { _, _ ->
                            val current = readConfigs(prefs)
                            val updated = JSONArray()
                            for (j in 0 until current.length()) {
                                val entry = current.optJSONObject(j) ?: continue
                                if (entry.optString("name") != configName) updated.put(entry)
                            }
                            prefs.edit().putString("configs_json", updated.toString()).apply()
                            if (selectedConfig == configName) selectedConfig = null
                            renderConfigs(prefs)
                        }
                        .show()
                }
            }
            deleteButton.addView(IconView(this, IconKind.CLOSE, Ui.RED), FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER))
            row.addView(deleteButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(8) })

            configList.addView(row, params(top = 8))
        }
    }

    /** Signal-bar level (0..4) and color for a server's last ping result. */
    private fun pingQuality(serverValue: String): Pair<Int, Int> {
        val result = pingCache[serverValue]
        if (result !is PingProbe.Result.Ok) return 0 to Ui.MUTED
        val ms = result.medianMs
        val level = when {
            ms < 150 -> 4
            ms < 250 -> 3
            ms < 400 -> 2
            else -> 1
        }
        val color = when {
            ms < 150 -> Ui.GREEN
            ms < 400 -> Ui.AMBER
            else -> Ui.RED
        }
        return level to color
    }

    private fun pingLabel(serverValue: String): TextView {
        val result = pingCache[serverValue]
        val (text, color) = when {
            result is PingProbe.Result.Ok -> {
                val c = when {
                    result.medianMs < 150 -> Ui.GREEN
                    result.medianMs < 400 -> Ui.AMBER
                    else -> Ui.RED
                }
                "${result.medianMs} ms" to c
            }
            result is PingProbe.Result.Failed -> result.reason to Ui.RED
            pingInFlight.contains(serverValue) -> "…" to Ui.MUTED
            else -> "—" to Ui.MUTED
        }
        return label(text, 13f, color, bold = true, gravity = Gravity.CENTER).apply {
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = Ui.shape(this@MainActivity, Ui.alpha(color, 30), 10f)
            isClickable = true
            if (result is PingProbe.Result.Ok) {
                contentDescription = Lang.t("ping_desc", result.minMs, result.received, result.sent)
            }
            setOnClickListener { startPing(serverValue, force = true) }
        }
    }

    /** Looks up the server's country from its IP and stores it in every saved config using that server. */
    private fun startGeo(serverValue: String) {
        if (serverValue.isBlank() || geoInFlight.contains(serverValue) || geoFailed.contains(serverValue)) return
        geoInFlight.add(serverValue)
        val appContext = applicationContext
        pingExecutor.execute {
            val cc = try { GeoLookup.countryOf(appContext, serverValue) } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                geoInFlight.remove(serverValue)
                if (cc == null) {
                    geoFailed.add(serverValue)
                    return@runOnUiThread
                }
                val configs = readConfigs(appPrefs)
                var changed = false
                for (i in 0 until configs.length()) {
                    val entry = configs.optJSONObject(i) ?: continue
                    if (entry.optString("server") == serverValue && entry.optString("cc") != cc) {
                        entry.put("cc", cc)
                        changed = true
                    }
                }
                if (changed) {
                    appPrefs.edit().putString("configs_json", configs.toString()).apply()
                    renderConfigs(appPrefs)
                }
            }
        }
    }

    private fun openVpnGate() {
        VpnGateScreen(
            this,
            onUse = { s ->
                server.setText(s.address)
                username.setText("vpn")
                password.setText("vpn")
                selectedConfig = null
                renderConfigs(appPrefs)
                toast(Lang.t("gate_picked", Flags.displayName(s.countryCode, s.countryName)))
            },
            onSave = { s -> saveVpnGateServer(s) }
        ).show()
    }

    /** Adds a VPN Gate server to the saved configs (with its flag). Returns false if it already exists. */
    private fun saveVpnGateServer(s: VpnGateServer): Boolean {
        val configs = readConfigs(appPrefs)
        val names = HashSet<String>()
        for (i in 0 until configs.length()) {
            val e = configs.optJSONObject(i) ?: continue
            if (e.optString("server") == s.address) {
                toast(Lang.t("already_saved"))
                return false
            }
            names.add(e.optString("name"))
        }
        val base = Flags.displayName(s.countryCode, s.countryName)
        var name = base
        var n = 2
        while (names.contains(name)) name = "$base $n".also { n++ }
        configs.put(
            JSONObject().put("name", name).put("server", s.address)
                .put("username", "vpn").put("password", "vpn").put("cc", s.countryCode)
        )
        appPrefs.edit().putString("configs_json", configs.toString()).apply()
        renderConfigs(appPrefs)
        toast(Lang.t("name_saved", name))
        return true
    }

    private fun startPing(serverValue: String, force: Boolean = false) {
        if (serverValue.isBlank()) return
        if (pingInFlight.contains(serverValue)) return
        if (!force && pingCache.containsKey(serverValue)) return
        pingInFlight.add(serverValue)
        pingCache.remove(serverValue)
        val appContext = applicationContext
        pingExecutor.execute {
            val result = try {
                PingProbe.measure(appContext, serverValue)
            } catch (e: Exception) {
                PingProbe.Result.Failed(Lang.t("error"))
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                pingInFlight.remove(serverValue)
                pingCache[serverValue] = result
                renderConfigs(appPrefs)
            }
        }
    }

    override fun onDestroy() {
        timerRunning = false
        tickHandler.removeCallbacks(tick)
        pingExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "opensstp_secure_prefs",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ───────── UI building blocks ─────────

    private fun label(t: String, sp: Float, color: Int, bold: Boolean = false, gravity: Int = Lang.start): TextView =
        TextView(this).apply {
            text = t
            textSize = sp
            setTextColor(color)
            this.gravity = gravity
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = Lang.dir
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = Ui.shape(this@MainActivity, Ui.CARD, 22f, Ui.STROKE, 1f)
    }

    private fun sectionHeader(title: String, badge: String?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = Lang.dir
        addView(label(title, 13f, Ui.TEXT2, bold = true))
        if (badge != null) {
            addView(
                label(badge, 11f, Ui.ACCENT_SOFT, bold = true, gravity = Gravity.CENTER).apply {
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    background = Ui.shape(this@MainActivity, Ui.alpha(Ui.ACCENT, 36), 10f)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
            )
        }
    }

    private class SpeedCard(val view: View, val value: TextView, val spark: SparklineView)

    private fun speedCard(kind: IconKind, color: Int, title: String): SpeedCard {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = Ui.shape(this@MainActivity, Ui.CARD, 20f, Ui.STROKE, 1f)
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = FrameLayout(this).apply { background = Ui.oval(Ui.alpha(color, 36)) }
        badge.addView(IconView(this, kind, color), FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER))
        head.addView(badge, LinearLayout.LayoutParams(dp(26), dp(26)))
        head.addView(
            label(title, 10f, Ui.MUTED, bold = true, gravity = Gravity.LEFT).apply { letterSpacing = 0.08f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
        )
        card.addView(head, match())
        val value = label("0 B/s", 17f, Ui.TEXT, bold = true, gravity = Gravity.LEFT).apply { isSingleLine = true }
        card.addView(value, params(top = 8))
        val spark = SparklineView(this, color)
        card.addView(spark, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)).apply { topMargin = dp(6) })
        return SpeedCard(card, value, spark)
    }

    private fun buildField(
        title: String,
        icon: IconKind,
        hintText: String,
        isPassword: Boolean = false,
        input: Int = InputType.TYPE_CLASS_TEXT
    ): Field {
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        block.addView(label(title, 12f, Ui.TEXT2), params(bottom = 6))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.dir
            setPadding(dp(8), 0, dp(14), 0)
            background = Ui.shape(this@MainActivity, Ui.FIELD, 14f, Ui.STROKE, 1f)
        }
        val iconView = IconView(this, icon, Ui.MUTED)
        row.addView(iconView, LinearLayout.LayoutParams(dp(20), dp(20)))

        val edit = EditText(this).apply {
            hint = hintText
            textSize = 15f
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            background = null
            inputType = if (isPassword) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else input
            isSingleLine = true
            gravity = Lang.start or Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        if (isPassword) {
            var visible = false
            val eye = FrameLayout(this).apply {
                isClickable = true
                contentDescription = Lang.t("show_password")
                background = Ui.ripple(this@MainActivity, Ui.oval(Color.TRANSPARENT), 19f)
            }
            val eyeIcon = IconView(this, IconKind.EYE, Ui.MUTED)
            eye.addView(eyeIcon, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            eye.setOnClickListener {
                visible = !visible
                val sel = edit.selectionEnd
                edit.transformationMethod = if (visible) null else PasswordTransformationMethod.getInstance()
                if (sel >= 0) edit.setSelection(sel)
                eyeIcon.changeKind(if (visible) IconKind.EYE_OFF else IconKind.EYE)
            }
            row.addView(eye, LinearLayout.LayoutParams(dp(38), dp(38)))
        }

        edit.setOnFocusChangeListener { _, focused ->
            row.background = Ui.shape(
                this, Ui.FIELD, 14f,
                if (focused) Ui.ACCENT else Ui.STROKE,
                if (focused) 1.5f else 1f
            )
            iconView.applyTint(if (focused) Ui.ACCENT else Ui.MUTED)
        }
        block.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        return Field(block, edit)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun match() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun params(top: Int = 0, bottom: Int = 0) = match().apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }
}
