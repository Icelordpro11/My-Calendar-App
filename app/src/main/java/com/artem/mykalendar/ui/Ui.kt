package com.artem.mykalendar.ui

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

// ---------- helpers ----------
internal fun Context.dp(i: Int) = (i * resources.displayMetrics.density).toInt()
internal fun View.dp(i: Int) = context.dp(i)
internal fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
internal fun Context.shape(fill: Int, stroke: Int, w: Int, r: Int, oval: Boolean = false) = GradientDrawable().apply {
    if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(r).toFloat()
    setColor(fill); if (w > 0) setStroke(dp(w), stroke)
}
internal fun Context.tv(t: String, size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextView(this).apply {
    text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
}
internal fun Context.box(hint: String, time: Boolean) = EditText(this).apply {
    this.hint = hint; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
    background = shape(Color.WHITE, 0xFFCCCCCC.toInt(), 1, 12)
    setPadding(dp(10), dp(10), dp(10), dp(10))
    if (time) { inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(5)) }
    else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
}
internal fun Context.btn(t: String, dark: Boolean, click: () -> Unit) = TextView(this).apply {
    text = t; textSize = 16f; gravity = Gravity.CENTER; setPadding(dp(24), dp(10), dp(24), dp(10))
    setTextColor(if (dark) Color.WHITE else Color.BLACK)
    background = shape(if (dark) Color.BLACK else 0xFFEEEEEE.toInt(), 0, 0, 12)
    setOnClickListener { click() }
}
internal fun Context.delBtn(action: () -> Unit) = TextView(this).apply {
    text = "✕"; textSize = 16f; setTextColor(0xFF999999.toInt()); setPadding(dp(14), dp(8), dp(14), dp(8))
    var armed = false
    setOnClickListener {
        if (armed) action() else { armed = true; text = tr("Delete?"); setTextColor(0xFFD32F2F.toInt()) }
    }
}
internal fun fixTime(x: String): String {
    val t = x.trim(); if (t.isEmpty()) return t
    if (t.contains(':')) {
        val p = t.split(':')
        return p[0].padStart(2, '0') + ":" + p.getOrElse(1) { "00" }.padEnd(2, '0')
    }
    val d = t.filter { it.isDigit() }; if (d.isEmpty()) return t
    return when {
        d.length <= 2 -> d.padStart(2, '0') + ":00"
        d.length == 3 -> "0" + d[0] + ":" + d.substring(1)
        else -> d.substring(0, 2) + ":" + d.substring(2, 4)
    }
}
internal fun validTime(t: String) =
    t.isEmpty() || (Regex("\\d{2}:\\d{2}").matches(t) && t.substring(0, 2).toInt() <= 23 && t.substring(3).toInt() <= 59)

internal fun MainActivity.hideKb() {
    try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(popup.windowToken, 0) } catch (e: Exception) {}
}

internal fun num(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
internal fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

internal fun Context.avatar(name: String, size: Int, ts: Float) = TextView(this).apply {
    text = name.take(1).uppercase(); textSize = ts; gravity = Gravity.CENTER
    setTextColor(0xFF444444.toInt()); setTypeface(null, Typeface.BOLD)
    background = shape(PASTELS[(name.lowercase().hashCode() and 0x7fffffff) % PASTELS.size], 0, 0, 0, true)
    layoutParams = lp(dp(size), dp(size))
}

internal fun Context.softBtn(t: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
    text = t; textSize = 16f; gravity = Gravity.CENTER; setPadding(dp(24), dp(12), dp(24), dp(12))
    setTextColor(if (primary) SOFT_INK else 0xFF444444.toInt()); setTypeface(null, Typeface.BOLD)
    background = shape(if (primary) SOFT else 0xFFF0F0F0.toInt(), 0, 0, 14)
    setOnClickListener { click() }
}

internal fun compact(v: Double): String =
    if (v >= 1000) String.format(Locale.US, "%.1fk", v / 1000).replace(".0k", "k") else Math.round(v).toString()

