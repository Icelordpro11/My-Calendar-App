package com.artem.mykalendar.screens

import com.artem.mykalendar.ui.*
import com.artem.mykalendar.screens.*
import com.artem.mykalendar.data.*
import com.artem.mykalendar.core.*
import com.artem.mykalendar.voice.*
import com.artem.mykalendar.MainActivity
import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

internal fun MainActivity.setLang(code: String) {
    lang = code
    getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("lang", code).apply()
    styleProfile(); render(); reopenPage()
}

// Settings: big rows, one per choice, easy to read and tap
internal fun MainActivity.settingsScreen() {
    openPage = "Settings"
    clientOpen = null
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
    l.addView(tv(tr("Settings"), 26f, true).apply { setPadding(dp(4), 0, 0, dp(6)) })

    fun section(title: String) = l.addView(tv(tr(title), 15f, true, 0xFF666666.toInt()),
        lp(-2, -2).apply { topMargin = dp(22); bottomMargin = dp(8); marginStart = dp(4) })

    fun row(title: String, sub: String?, mark: String, selected: Boolean, click: () -> Unit) {
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            background = shape(0xFFF4F4F4.toInt(), Color.BLACK, if (selected) 2 else 0, 14)
            setOnClickListener { click() }
        }
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.addView(tv(title, 18f, selected))
        if (sub != null) t.addView(tv(sub, 13f, false, 0xFF777777.toInt()))
        r.addView(t, lp(0, -2).apply { weight = 1f })
        if (mark.isNotEmpty()) r.addView(tv(mark, 22f, true))
        l.addView(r, lp(-1, -2).apply { bottomMargin = dp(8) })
    }

    section("Language")
    listOf("en" to "English", "nl" to "Nederlands", "ru" to "Русский").forEach { (code, label) ->
        row(label, null, if (lang == code) "✓" else "", lang == code) { setLang(code) }
    }
    section("Reminders")
    val ap = getSharedPreferences("app", Context.MODE_PRIVATE)
    val remOn = ap.getBoolean("rem_on", false)
    fun mins() = reminderMinutes(ap)
    fun saveMins(m: List<Int>) { ap.edit().putString("rem_list", m.distinct().sorted().joinToString(",")).apply(); rescheduleReminders(); settingsScreen() }

    // on / off switch
    val head = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(0xFFF4F4F4.toInt(), 0, 0, 14)
        setOnClickListener {
            ap.edit().putBoolean("rem_on", !remOn).apply()
            if (!remOn && Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
            rescheduleReminders(); settingsScreen()
        }
    }
    val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    ht.addView(tv(tr("Reminders"), 18f, true))
    ht.addView(tv(tr("Get a notification before a plan"), 13f, false, 0xFF777777.toInt()))
    head.addView(ht, lp(0, -2).apply { weight = 1f })
    head.addView(toggleView(remOn), lp(dp(52), dp(30)))
    l.addView(head, lp(-1, -2).apply { bottomMargin = dp(8) })

    if (remOn) {
        mins().forEachIndexed { i, m ->
            val r = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(14), dp(8), dp(14))
                background = shape(Color.WHITE, 0xFFBBBBBB.toInt(), 1, 14)
                setOnClickListener {
                    openMenu(REMINDER_CHOICES.map { c -> remLabel(c) to { val x = mins().toMutableList(); x[i] = c; saveMins(x) } },
                        Gravity.END, REMINDER_CHOICES.indexOf(m))
                }
            }
            r.addView(tv(remLabel(m), 18f), lp(0, -2).apply { weight = 1f })
            r.addView(tv("⌄", 20f, true, 0xFF555555.toInt()).apply { setPadding(dp(8), 0, dp(8), 0) })
            if (mins().size > 1) r.addView(tv("✕", 16f, false, 0xFF999999.toInt()).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6)); setOnClickListener { saveMins(mins().filterIndexed { j, _ -> j != i }) }
            })
            l.addView(r, lp(-1, -2).apply { bottomMargin = dp(8) })
        }
        val free = REMINDER_CHOICES.firstOrNull { it !in mins() }
        if (free != null) l.addView(btn("+  " + tr("Add reminder"), false) { saveMins(mins() + free) }, lp(-2, -2).apply { topMargin = dp(2) })
    }
    section("Voice assistant")
    val aOn = ap.getBoolean("assist_on", true)
    val ahead = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        background = shape(0xFFF4F4F4.toInt(), 0, 0, 14)
        setOnClickListener { ap.edit().putBoolean("assist_on", !aOn).apply(); assistantState = 0; checkAssistant(); settingsScreen() }
    }
    val at = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    at.addView(tv(tr("Voice assistant"), 18f, true))
    at.addView(tv(tr("Hold the mic button and speak"), 13f, false, 0xFF777777.toInt()))
    ahead.addView(at, lp(0, -2).apply { weight = 1f })
    ahead.addView(toggleView(aOn), lp(dp(52), dp(30)))
    l.addView(ahead, lp(-1, -2).apply { bottomMargin = dp(8) })
    if (aOn) {
    fun field(hintText: String, key: String, uri: Boolean) = EditText(this).apply {
        hint = hintText; textSize = 16f; setSingleLine(); setTextColor(Color.BLACK)
        background = shape(0xFFF4F4F4.toInt(), 0, 0, 14); setPadding(dp(16), dp(14), dp(16), dp(14))
        inputType = InputType.TYPE_CLASS_TEXT or (if (uri) InputType.TYPE_TEXT_VARIATION_URI else 0)
        setText(ap.getString(key, ""))
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                ap.edit().putString(key, e.toString().trim()).apply()
                if (key == "n8n_url") {                       // check the new link a moment after typing stops
                    assistantState = 0; styleMic()
                    window.decorView.removeCallbacks(checkRunnable); window.decorView.postDelayed(checkRunnable, 800)
                }
            }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
    }
    l.addView(field(tr("Assistant link (n8n)"), "n8n_url", true), lp(-1, -2).apply { bottomMargin = dp(8) })
    l.addView(field(tr("Secret code (optional)"), "n8n_token", false), lp(-1, -2).apply { bottomMargin = dp(8) })
    val status = tv("", 14f, true)
    voiceStatusView = status; styleMic()
    l.addView(status, lp(-2, -2).apply { marginStart = dp(4); bottomMargin = dp(4) })
    }
    section("Data")
    row(tr("Export data (JSON)"), tr("Save everything to a file"), "›", false) { exportFile() }
    row(tr("Import data"), tr("Load everything from a file"), "›", false) { importFile() }
    showOverlay(ScrollView(this).apply { addView(l) })
}

// round on/off switch: black when on, grey when off
private fun MainActivity.toggleView(on: Boolean): View = FrameLayout(this).apply {
    background = shape(if (on) Color.BLACK else 0xFFCFCFCF.toInt(), 0, 0, 15)
    addView(View(context).apply { background = shape(Color.WHITE, 0, 0, 12, true) },
        FrameLayout.LayoutParams(dp(24), dp(24), (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply { setMargins(dp(3), 0, dp(3), 0) })
}
