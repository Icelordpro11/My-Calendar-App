package com.artem.mykalendar

import com.artem.mykalendar.ui.*
import com.artem.mykalendar.screens.*
import com.artem.mykalendar.data.*
import com.artem.mykalendar.core.*
import com.artem.mykalendar.voice.*
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    internal val prefs by lazy { getSharedPreferences("plans", MODE_PRIVATE) }
    internal var mode = 0 // 0 month, 1 week, 2 day
    internal var cur: LocalDate = LocalDate.now()
    internal var overview = false        // pulled down: every date shows its plans
    internal lateinit var todayBtn: TextView
    internal var panelState = 1           // 0 hidden, 1 shows max 3 plans, 2 all plans
    internal lateinit var tTitle: TextView
    internal lateinit var content: FrameLayout
    internal lateinit var sheetHolder: FrameLayout
    internal lateinit var overlay: FrameLayout   // Clients / Money / Notes
    internal lateinit var menuLayer: FrameLayout // top menus
    internal lateinit var popup: FrameLayout     // pop-ups (new/edit plan, choices)
    internal lateinit var dropLayer: FrameLayout // small drop window next to the finger
    internal var tx = 0f; internal var ty = 0f      // last touch position
    internal var openPage: String? = null          // null = calendar, else Clients / Money / Notes / Settings
    internal var biz = false                       // false = personal calendar, true = business calendar
    internal lateinit var barView: LinearLayout
    internal lateinit var profileBtn: ProfileIcon
    internal lateinit var plansBtn: PlansIcon

    internal val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) handleSpokenText(t)
    }

    // ---------- hold-to-talk recording ----------
    internal var recorder: android.media.MediaRecorder? = null
    internal var recFile: java.io.File? = null
    internal var recStart = 0L
    internal lateinit var voiceBar: TextView
    internal lateinit var noteBar: TextView           // message bar at the top
    internal val hideBar = Runnable { noteBar.visibility = View.GONE }
    internal var micBtn: ImageButton? = null          // turns red when the assistant cannot be reached
    internal var assistantState = 0                   // 0 unknown / not set up, 1 reachable, 2 NOT reachable
    internal var voiceStatusView: TextView? = null    // status line in Settings
    internal var watching = false
    internal val checkRunnable = Runnable { checkAssistant() }

    // ---------- lock for the Budget page (fingerprint or phone PIN) ----------
    internal var unlocked = false
    internal var pendingUnlock: (() -> Unit)? = null
    internal val keyguardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) { unlocked = true; pendingUnlock?.invoke() }
        pendingUnlock = null
    }

    // ---------- swipe ----------
    internal val gd by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = b.x - (a?.x ?: b.x); val dy = b.y - (a?.y ?: b.y)
                if (abs(dx) > abs(dy) * 1.5 && abs(dx) > dp(80)) { shift(if (dx < 0) 1 else -1); return true }
                return false
            }
        })
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) { tx = ev.rawX; ty = ev.rawY }
        if (popup.visibility != View.VISIBLE && overlay.visibility != View.VISIBLE && menuLayer.visibility != View.VISIBLE &&
            dropLayer.visibility != View.VISIBLE) gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
    internal fun shift(n: Int) {
        cur = when (mode) { 0 -> cur.plusMonths(n.toLong()); 1, 3 -> cur.plusWeeks(n.toLong()); else -> cur.plusDays(n.toLong()) }
        render()
    }

    // ---------- screen ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biz = true   // the business calendar always opens first
        getSharedPreferences("app", MODE_PRIVATE).let { ap -> mode = ap.getInt("mode", 0).coerceIn(0, 2); overview = false; moneyPeriod = ap.getInt("mperiod", 2).coerceIn(0, 3); moneyGraph = ap.getBoolean("mgraph", false); lang = ap.getString("lang", "en") ?: "en" }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE); fitsSystemWindows = true }
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun hBtn(t: String, size: Float, click: () -> Unit) = TextView(this).apply {
            text = t; textSize = size; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
            layoutParams = lp(dp(44), dp(44)); setOnClickListener { click() }
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(0)) }
        barView = bar
        profileBtn = ProfileIcon(this).apply {
            setOnClickListener {
                biz = !biz
                styleProfile(); render(); if (openPage != null) reopenPage()
            }
        }
        styleProfile()
        bar.addView(profileBtn, lp(dp(42), dp(42)).apply { marginEnd = dp(10) })
        tTitle = tv("", 19f, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            // hidden: long-press the month title for backup / restore
            setOnLongClickListener {
                openMenu(listOf(tr("Export data (JSON)") to { exportFile() }, tr("Import data") to { importFile() }), Gravity.START); true
            }
        }
        bar.addView(tTitle, lp(0, -2).apply { weight = 1f })
        todayBtn = tv(tr("Today"), 14f).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6)); background = shape(Color.WHITE, Color.BLACK, 1, 12)
            setOnClickListener { if (onMoney) { moneyAnchor = LocalDate.now(); moneyScreen() } else { hideOverlay(); cur = LocalDate.now(); render() } }
        }
        bar.addView(todayBtn, lp(-2, -2).apply { marginEnd = dp(6) })
        plansBtn = PlansIcon(this).apply {
            setOnClickListener {
                if (onMoney) { moneyGraph = !moneyGraph; moneyScreen() }
                else { when (mode) { 0 -> overview = !overview; 1 -> mode = 3; 3 -> mode = 1 }; render() }
            }
        }
        bar.addView(plansBtn, lp(dp(40), dp(40)).apply { marginEnd = dp(2) })
        bar.addView(CalIcon(this).apply {
            layoutParams = lp(dp(44), dp(44))
            setOnClickListener {
                if (onMoney) moneyMenu()
                else openMenu(listOf(tr("Month") to { mode = 0; overview = false; render() }, tr("Week") to { mode = 1; render() }, tr("Day") to { mode = 2; render() }), Gravity.END, if (mode == 3) 1 else mode)
            }
        })
        bar.addView(hBtn("⋮", 26f) {
            openMenu(listOf(tr("Calendar") to { render() }, tr("Clients") to { pageScreen("Clients") }, tr("Budget") to { pageScreen("Money") },
                tr("Notes") to { pageScreen("Notes") }, tr("Settings") to { settingsScreen() }), Gravity.END,
                when (openPage) { "Clients" -> 1; "Money" -> 2; "Notes" -> 3; "Settings" -> 4; else -> 0 })
        })
        main.addView(bar)
        content = FrameLayout(this)
        main.addView(content, lp(-1, 0).apply { weight = 1f })
        sheetHolder = FrameLayout(this)
        main.addView(sheetHolder, lp(-1, -2))
        root.addView(main)

        menuLayer = FrameLayout(this).apply { visibility = View.GONE; setOnClickListener { visibility = View.GONE } }
        popup = FrameLayout(this).apply {
            visibility = View.GONE; setBackgroundColor(0x66000000)
            setOnClickListener { hideKb(); visibility = View.GONE }
        }
        overlay = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(Color.WHITE); isClickable = true }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(menuLayer, FrameLayout.LayoutParams(-1, -1))
        root.addView(popup, FrameLayout.LayoutParams(-1, -1))
        dropLayer = FrameLayout(this).apply { visibility = View.GONE; setOnClickListener { hideKb(); visibility = View.GONE } }
        root.addView(dropLayer, FrameLayout.LayoutParams(-1, -1))
        voiceBar = TextView(this).apply {
            visibility = View.GONE; textSize = 18f; setTextColor(Color.WHITE); setPadding(dp(26), dp(16), dp(26), dp(16))
            background = shape(0xF0111111.toInt(), 0, 0, 30); elevation = dp(14).toFloat()
        }
        root.addView(voiceBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(130) })
        noteBar = TextView(this).apply {
            visibility = View.GONE; textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(20), dp(12), dp(20), dp(12))
            maxWidth = dp(330); elevation = dp(14).toFloat(); gravity = Gravity.CENTER
        }
        root.addView(noteBar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(70) })
        setContentView(root)
        mirror()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    dropLayer.visibility == View.VISIBLE -> { hideKb(); dropLayer.visibility = View.GONE }
                    popup.visibility == View.VISIBLE -> { hideKb(); popup.visibility = View.GONE }
                    menuLayer.visibility == View.VISIBLE -> menuLayer.visibility = View.GONE
                    overlay.visibility == View.VISIBLE -> { if (clientOpen != null) clientsScreen() else hideOverlay() }
                    mode == 0 && overview -> { overview = false; render() }
                    mode == 3 -> { mode = 1; render() }
                    else -> finish()
                }
            }
        })
        render()
        rescheduleReminders()
    }

    override fun onStart() {
        super.onStart()
        watching = true; checkAssistant()
    }

    override fun onStop() {
        super.onStop()
        watching = false; window.decorView.removeCallbacks(checkRunnable)
        unlocked = false                              // leaving the app locks the Budget again
        if (openPage == "Money") hideOverlay()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("app", MODE_PRIVATE).edit().putInt("mode", if (mode == 3) 1 else mode).putBoolean("overview", overview).putInt("mperiod", moneyPeriod).putBoolean("mgraph", moneyGraph).apply()
    }

    internal fun styleTop() {
        todayBtn.text = tr("Today")
        val ref = if (onMoney) moneyAnchor else cur
        todayBtn.visibility = if ((openPage == null || onMoney) && ref != LocalDate.now()) View.VISIBLE else View.GONE
        plansBtn.visibility = if (!onMoney && mode == 2) View.GONE else View.VISIBLE
        val on = if (onMoney) moneyGraph else ((mode == 0 && overview) || mode == 3)
        plansBtn.background = if (on) shape(0xFFF5F5F5.toInt(), 0xFF9A9A9A.toInt(), 1, 12) else null
    }

    internal fun styleProfile() {
        profileBtn.business = biz
        profileBtn.ink = Color.BLACK
        profileBtn.invalidate()
        profileBtn.background = shape(if (biz) 0xFFEDEDED.toInt() else Color.WHITE, 0xFF9A9A9A.toInt(), 1, 12)
    }

    internal fun openMenu(items: List<Pair<String, () -> Unit>>, g: Int, selected: Int = -1) {
        menuLayer.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true; elevation = dp(10).toFloat()
            background = shape(Color.WHITE, 0xFFE0E0E0.toInt(), 1, 18); setPadding(dp(6), dp(6), dp(6), dp(6))
            minimumWidth = dp(200)
        }
        items.forEachIndexed { i, (name, action) ->
            val on = i == selected
            card.addView(tv(name, 17f, on).apply {
                setPadding(dp(18), dp(13), dp(24), dp(13))
                background = if (on) shape(0xFFF5F5F5.toInt(), 0xFF9A9A9A.toInt(), 1, 12) else null
                setOnClickListener { menuLayer.visibility = View.GONE; hideOverlay(); action() }
            }, lp(-1, -2).apply { setMargins(0, dp(1), 0, dp(1)) })
        }
        menuLayer.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or g).apply { setMargins(dp(8), dp(56), dp(8), 0) })
        menuLayer.visibility = View.VISIBLE
    }

    internal var clientOpen: String? = null
    internal var clientTab = 0 // 0 All, 1 Favorites, 2 History

    internal var onMoney = false
    internal var moneyPeriod = 2          // 0 day, 1 week, 2 month, 3 year
    internal var moneyGraph = false
    internal var moneyAnchor: LocalDate = LocalDate.now()

    internal val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val u = r.data?.data
        if (u != null) {
            try { contentResolver.openOutputStream(u)?.use { it.write(buildJson().toByteArray()) }; Toast.makeText(this, tr("Saved to file"), Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(this, tr("Could not save the file"), Toast.LENGTH_LONG).show() }
        }
    }
    internal val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val u = r.data?.data
        if (u != null) {
            try {
                val t = contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: ""
                JSONObject(t).getJSONObject("calendars")
                val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                c.addView(tv(tr("Replace ALL data with this file?"), 17f, true).apply { setPadding(dp(6), dp(4), dp(6), dp(10)) })
                val b = LinearLayout(this)
                b.addView(btn(tr("No"), false) { dropLayer.visibility = View.GONE }, lp(-2, -2).apply { marginEnd = dp(8) })
                b.addView(btn(tr("Yes"), true) {
                    dropLayer.visibility = View.GONE
                    try { importJson(t); hideOverlay(); render(); Toast.makeText(this, tr("Imported"), Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Toast.makeText(this, tr("Import failed"), Toast.LENGTH_LONG).show() }
                })
                c.addView(b)
                showDrop(c)
            } catch (e: Exception) { Toast.makeText(this, tr("That is not a calendar file"), Toast.LENGTH_LONG).show() }
        }
    }
    internal fun showOverlay(v: View) {
        onMoney = false; styleTop()
        overlay.removeAllViews()
        overlay.addView(v)
        (overlay.layoutParams as FrameLayout.LayoutParams).topMargin = barView.height
        overlay.requestLayout()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        overlay.visibility = View.VISIBLE
    }
    internal fun hideOverlay() {
        openPage = null
        overlay.visibility = View.GONE
        onMoney = false; styleTop()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
}
