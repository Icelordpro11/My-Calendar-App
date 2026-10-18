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

// ---------- small drop window, appears next to the finger (right-thumb friendly) ----------
internal fun MainActivity.showDrop(content: View, aboveKeyboard: Boolean = false) {
    dropLayer.removeAllViews()
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; isClickable = true; elevation = dp(10).toFloat()
        background = shape(Color.WHITE, 0xFFD0D0D0.toInt(), 1, 16); setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(content)
    }
    val sw = resources.displayMetrics.widthPixels; val sh = resources.displayMetrics.heightPixels
    card.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(sh, View.MeasureSpec.AT_MOST))
    val w = card.measuredWidth; val h = card.measuredHeight
    val parent = dropLayer.parent as View
    val loc = IntArray(2); parent.getLocationOnScreen(loc)
    val ox = loc[0] + parent.paddingLeft; val oy = loc[1] + parent.paddingTop
    val x = (tx - w).toInt().coerceIn(dp(8), maxOf(dp(8), sw - w - dp(8)))      // right edge at the finger
    var y = (ty - h - dp(12)).toInt()                                           // above the finger
    if (y < dp(60)) y = (ty + dp(16)).toInt()
    if (aboveKeyboard) y = minOf(y, (sh * 0.28).toInt())
    y = y.coerceIn(dp(40), maxOf(dp(40), sh - h - dp(8)))
    dropLayer.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
        leftMargin = x - ox; topMargin = y - oy
    })
    dropLayer.visibility = View.VISIBLE
}

// tap a plan: ONE bar on top of the keyboard: amount + two colour squares on the right (thumb side)
internal fun MainActivity.colorDrop(d: LocalDate, i: Int) = payDialog(d, i)

internal fun MainActivity.payDialog(d: LocalDate, i: Int) {
    val x = plans(d)[i]
    val dlg = Dialog(this)
    dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val e = EditText(this).apply {
        hint = "0"; textSize = 24f; gravity = Gravity.CENTER_VERTICAL or Gravity.START; setTextColor(Color.BLACK)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        filters = arrayOf(InputFilter.LengthFilter(9)); imeOptions = EditorInfo.IME_ACTION_DONE
        background = shape(0xFFF2F2F2.toInt(), 0, 0, 14); setPadding(dp(16), dp(12), dp(16), dp(12))
        setText(x[4]); setSelection(text.length)
    }
    fun commit(st: String) { dlg.dismiss(); setStatus(d, i, st, e.text.toString().trim()) }
    e.setOnEditorActionListener { _, _, _ -> if (x[3].isEmpty()) dlg.dismiss() else commit(x[3]); true }
    fun tile(color: Int, st: String) = View(this).apply {
        background = shape(color, if (x[3] == st) Color.BLACK else color, 3, 14)
        layoutParams = lp(dp(54), dp(54)).apply { marginStart = dp(10) }
        setOnClickListener { commit(st) }
    }
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    if (x[3].isNotEmpty()) row.addView(tv("✕", 20f, false, 0xFF777777.toInt()).apply {
        gravity = Gravity.CENTER; setOnClickListener { dlg.dismiss(); setStatus(d, i, "", "") }
    }, lp(dp(40), dp(54)))
    row.addView(e, lp(0, -2).apply { weight = 1f })
    row.addView(tile(CASH, "cash")); row.addView(tile(CARDC, "card"))
    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(14))
        val r = dp(22).toFloat()
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
    }
    bar.addView(tv(x[0], 13f, false, 0xFF888888.toInt()).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(4), 0, 0, dp(8)) })
    bar.addView(row)
    dlg.setContentView(bar)
    dlg.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM); setDimAmount(0.3f)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    dlg.show()
    e.requestFocus()
}

// step 2: amount bar docked right on top of the keyboard (nothing else resizes)
internal fun MainActivity.amountDialog(d: LocalDate, i: Int, st: String) {
    val x = plans(d)[i]
    val color = if (st == "cash") CASH else CARDC
    val bg = if (st == "cash") CASH_BG else CARD_BG
    val dlg = Dialog(this)
    dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val e = EditText(this).apply {
        hint = tr("Amount"); textSize = 24f; gravity = Gravity.CENTER_VERTICAL or Gravity.START; setTextColor(Color.BLACK)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        filters = arrayOf(InputFilter.LengthFilter(9)); imeOptions = EditorInfo.IME_ACTION_DONE
        background = shape(bg, color, 2, 14); setPadding(dp(16), dp(12), dp(16), dp(12))
        if (x[3] == st) { setText(x[4]); setSelection(text.length) }
    }
    fun commit() { dlg.dismiss(); setStatus(d, i, st, e.text.toString().trim()) }
    e.setOnEditorActionListener { _, _, _ -> commit(); true }
    val bar = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        val r = dp(22).toFloat()
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
    }
    bar.addView(View(this).apply { background = shape(color, 0, 0, 10) }, lp(dp(14), dp(46)).apply { marginEnd = dp(10) })
    bar.addView(e, lp(0, -2).apply { weight = 1f })
    bar.addView(btn("OK", true) { commit() }, lp(-2, -2).apply { marginStart = dp(10) })
    dlg.setContentView(bar)
    dlg.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM); setDimAmount(0.3f)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    dlg.show()
    e.requestFocus()
}

// three dots: edit plan (message + time), color & amount, delete
internal fun MainActivity.moreDrop(d: LocalDate, i: Int, x: Array<String>) {
    fun item(label: String, color: Int, act: () -> Unit) = tv(label, 18f, false, color).apply {
        setPadding(dp(14), dp(12), dp(24), dp(12)); setOnClickListener { act() }
    }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    col.addView(item(tr("Edit plan"), Color.BLACK) { dropLayer.visibility = View.GONE; planPopup(d, i, x[0], x[1]) })
    col.addView(item(tr("Color & amount"), Color.BLACK) { colorDrop(d, i) })
    col.addView(item(tr("Delete"), 0xFFD32F2F.toInt()) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(tv(tr("Delete this plan?"), 17f, true).apply { setPadding(dp(6), dp(4), dp(6), dp(10)) })
        val b = LinearLayout(this)
        b.addView(btn(tr("No"), false) { dropLayer.visibility = View.GONE }, lp(-2, -2).apply { marginEnd = dp(8) })
        b.addView(btn(tr("Yes"), true) { dropLayer.visibility = View.GONE; save(d, plans(d).filterIndexed { j, _ -> j != i }); render() })
        c.addView(b)
        showDrop(c)
    })
    showDrop(col)
}

// ---------- plan row: time | line | text | amount | ⋮ ; tap = color ----------
internal fun MainActivity.planRow(d: LocalDate, i: Int, x: Array<String>): View {
    val r = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56); setPadding(dp(22), dp(6), dp(6), dp(6))
        when (x[3]) { "cash" -> setBackgroundColor(CASH_BG); "card" -> setBackgroundColor(CARD_BG) }
        setOnClickListener { colorDrop(d, i) }
    }
    r.addView(tv(x[1].ifEmpty { "–" }, 16f, true), lp(dp(54), -2))
    r.addView(View(this).apply { setBackgroundColor(0xFF9E9E9E.toInt()) }, lp(dp(2), dp(32)).apply { marginEnd = dp(14) })
    r.addView(tv(x[0].ifEmpty { tr("No details yet") }, 17f, x[0].isNotEmpty(), if (x[0].isEmpty()) 0xFF9A9A9A.toInt() else Color.BLACK).apply { maxLines = 4; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, -2).apply { weight = 1f })
    r.addView(tv("⋮", 22f, false, 0xFF777777.toInt()).apply {
        gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { moreDrop(d, i, x) }
    }, lp(-2, dp(44)))
    return r
}

internal fun MainActivity.rowsOf(d: LocalDate, pad: Int): View {
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, pad) }
    val p = plans(d)
    if (p.isEmpty()) l.addView(tv(tr("No plans"), 15f, false, 0xFF9A9A9A.toInt()).apply { setPadding(dp(22), dp(20), 0, dp(14)) })
    p.forEachIndexed { i, x ->
        l.addView(planRow(d, i, x))
        l.addView(View(this).apply { setBackgroundColor(0xFFD9D9D9.toInt()) }, lp(-1, dp(1)).apply { setMargins(dp(22), 0, dp(22), 0) })
    }
    return l
}

// week + plans: all 7 days under each other, each with its plans
internal fun MainActivity.weekPlansView(monday: LocalDate): View {
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(16)) }
    for (i in 0..6) {
        val d = monday.plusDays(i.toLong())
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(9), dp(14), dp(9))
            background = if (d == cur) shape(0xFFF5F5F5.toInt(), 0xFF9A9A9A.toInt(), 1, 12) else null
            setOnClickListener { cur = d; render() }
        }
        head.addView(tv(d.format(DateTimeFormatter.ofPattern("EEEE d", appLocale())).replaceFirstChar { it.uppercase() }, 16f, true,
            if (d.dayOfWeek.value == 7) 0xFFE53935.toInt() else Color.BLACK), lp(0, -2).apply { weight = 1f })
        if (d == LocalDate.now()) head.addView(tv(tr("Today"), 12f, false, 0xFF777777.toInt()))
        l.addView(head, lp(-1, -2).apply { topMargin = dp(6) })
        val p = plans(d)
        if (p.isEmpty()) l.addView(tv(tr("No plans"), 14f, false, 0xFFAAAAAA.toInt()).apply { setPadding(dp(14), dp(2), 0, dp(6)) })
        p.forEachIndexed { j, x ->
            l.addView(planRow(d, j, x))
            l.addView(View(this).apply { setBackgroundColor(0xFFE2E2E2.toInt()) }, lp(-1, dp(1)).apply { setMargins(dp(22), 0, dp(22), 0) })
        }
    }
    return ScrollView(this).apply { addView(l) }
}

// week/day: plain list
internal fun MainActivity.planList(d: LocalDate): View = ScrollView(this).apply { addView(rowsOf(d, dp(16))) }

// "9 Arina Pedicure" or "Arina Pedicure 9" -> time 09:00 + text "Arina Pedicure"
internal fun MainActivity.toTime(t: String): String? {
    val m = Regex("^(\\d{3,4}|\\d{1,2})(?:[:.](\\d{2}))?$").find(t) ?: return null
    val tm = if (m.groupValues[2].isNotEmpty()) m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2] else fixTime(m.groupValues[1])
    return if (tm.isNotEmpty() && validTime(tm)) tm else null
}
internal fun MainActivity.splitTime(raw: String): Pair<String, String>? {
    val st = Regex("^\\s*(\\S+)\\s+(\\S[\\s\\S]*)$").find(raw)
    if (st != null) toTime(st.groupValues[1])?.let { return it to st.groupValues[2].trim() }
    val en = Regex("^([\\s\\S]*\\S)\\s+(\\S+)\\s*$").find(raw)
    if (en != null) toTime(en.groupValues[2])?.let { return it to en.groupValues[1].trim() }
    return null
}

// ---------- NEW / EDIT plan: big note field, time in the top-right corner ----------
internal fun MainActivity.planPopup(d: LocalDate, idx: Int?, pre: String, preTime: String) {
    popup.removeAllViews()
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; isClickable = true; elevation = dp(12).toFloat()
        background = shape(Color.WHITE, 0, 0, 24); setPadding(dp(20), dp(18), dp(20), dp(16))
    }
    val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    left.addView(tv(tr(if (idx == null) "New plan" else "Edit plan"), 22f, true))
    left.addView(tv(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", appLocale())), 14f, false, 0xFF888888.toInt()))
    head.addView(left, lp(0, -2).apply { weight = 1f })
    val s = EditText(this).apply {
        hint = "09:00"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
        inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(5))
        background = shape(0xFFF2F2F2.toInt(), 0, 0, 12); setPadding(dp(8), dp(10), dp(8), dp(10))
        setText(preTime)
    }
    head.addView(s, lp(dp(92), -2))
    card.addView(head)
    val t = EditText(this).apply {
        hint = tr("Write your plan…")
        textSize = 18f; setTextColor(Color.BLACK); gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 5; maxLines = 8
        background = shape(0xFFF6F6F6.toInt(), 0, 0, 16); setPadding(dp(16), dp(14), dp(16), dp(14))
        setText(pre)
    }
    card.addView(t, lp(-1, -2).apply { topMargin = dp(14) })
    val btns = LinearLayout(this).apply { gravity = Gravity.END }
    btns.addView(btn(tr("Cancel"), false) { hideKb(); popup.visibility = View.GONE }, lp(-2, -2).apply { marginEnd = dp(10) })
    btns.addView(btn(tr("Save"), true) {
        var txt = t.text.toString().trim()
        var tm = fixTime(s.text.toString())
        if (!validTime(tm)) tm = ""
        splitTime(txt)?.let { tm = it.first; txt = it.second }   // a time typed in the note wins
        if (txt.isNotEmpty()) {
            val list = plans(d).toMutableList()
            if (idx == null) list.add(arrayOf(txt, tm, "", "", ""))
            else list[idx] = arrayOf(txt, tm, "", list[idx][3], list[idx][4])
            save(d, list)
            cur = d
        }
        hideKb(); popup.visibility = View.GONE; render()
    })
    card.addView(btns, lp(-1, -2).apply { topMargin = dp(16) })
    popup.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(dp(16), dp(60), dp(16), 0) })
    popup.visibility = View.VISIBLE
    t.post {
        t.requestFocus(); t.setSelection(t.text.length)
        try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(t, 0) } catch (e: Exception) {}
    }
}

