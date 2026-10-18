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

internal fun MainActivity.render() {
    val monday = cur.minusDays((cur.dayOfWeek.value - 1).toLong())
    val f = DateTimeFormatter.ofPattern("d MMM", appLocale())
    styleTop()
    tTitle.text = when (mode) {
        0 -> "${cur.year} " + cur.month.getDisplayName(TextStyle.FULL_STANDALONE, appLocale()).replaceFirstChar { it.uppercase() }
        1, 3 -> monday.format(f) + " – " + monday.plusDays(6).format(f)
        else -> cur.format(DateTimeFormatter.ofPattern("EEE d MMM", appLocale()))
    }
    content.removeAllViews()
    sheetHolder.removeAllViews()
    val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    when (mode) {
        0 -> {
            page.addView(weekdayHeader()); page.addView(monthGrid(), lp(-1, 0).apply { weight = 1f })
            sheetHolder.addView(buildSheet(cur))
        }
        1 -> {
            page.addView(weekdayHeader()); page.addView(weekStrip(monday))
            page.addView(planList(cur), lp(-1, 0).apply { weight = 1f })
            sheetHolder.addView(shell().apply { addView(bottomBar()) })
        }
        3 -> {
            page.addView(weekPlansView(monday), lp(-1, 0).apply { weight = 1f })
            sheetHolder.addView(shell().apply { addView(bottomBar()) })
        }
        else -> {
            page.addView(planList(cur), lp(-1, 0).apply { weight = 1f })
            sheetHolder.addView(shell().apply { addView(bottomBar()) })
        }
    }
    content.addView(page)
}

// ---------- calendar: plain numbers, dot above days with plans ----------
internal fun MainActivity.weekdayHeader(): View {
    val head = LinearLayout(this).apply { setPadding(dp(8), dp(10), dp(8), dp(4)) }
    for (i in 1..7) head.addView(tv(
        DayOfWeek.of(i).getDisplayName(TextStyle.SHORT, appLocale()).take(2).replaceFirstChar { it.uppercase() },
        11f, false, if (i == 7) 0xFFE53935.toInt() else 0xFF8A8A8A.toInt()
    ).apply { gravity = Gravity.CENTER }, lp(0, dp(22)).apply { weight = 1f })
    return head
}

internal fun MainActivity.monthGrid(): View {
    val first = cur.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val rows = (offset + cur.lengthOfMonth() + 6) / 7
    val start = first.minusDays(offset.toLong())
    val g = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(4)) }
    for (r in 0 until rows) {
        val row = LinearLayout(this)
        for (c in 0..6) row.addView(dayCell(start.plusDays((r * 7 + c).toLong()), overview, rows), lp(0, -1).apply { weight = 1f; setMargins(dp(2), dp(2), dp(2), dp(2)) })
        g.addView(row, lp(-1, 0).apply { weight = 1f })
    }
    return g
}

internal fun MainActivity.weekStrip(monday: LocalDate): View {
    val row = LinearLayout(this).apply { setPadding(dp(8), 0, dp(8), dp(8)) }
    for (i in 0..6) row.addView(dayCell(monday.plusDays(i.toLong())), lp(0, dp(54)).apply { weight = 1f })
    return row
}

internal fun MainActivity.dayCell(d: LocalDate, big: Boolean = false, rows: Int = 6): View {
    val inMonth = d.month == cur.month && d.year == cur.year
    val selected = d == cur
    val today = d == LocalDate.now()
    val color = when {
        mode == 0 && !inMonth -> 0xFFC4C4C4.toInt()
        d.dayOfWeek.value == 7 -> 0xFFE53935.toInt()
        else -> Color.BLACK
    }
    val r = if (big) 10 else 12
    val grey = if (mode == 0 && !inMonth) 0xFFEFEFEF.toInt() else 0xFFD9D9D9.toInt()
    // every date: small grey outline. today: darker grey. selected: black.
    val ring = when {
        selected -> shape(Color.TRANSPARENT, Color.BLACK, 2, r)
        today -> shape(Color.TRANSPARENT, 0xFF8A8A8A.toInt(), 1, r)
        else -> shape(Color.TRANSPARENT, grey, 1, r)
    }
    if (big) {
        val nn = maxOf(plans(cur).size, 1)
        val cardH = dp(100) + (when (panelState) { 0 -> 0; 2 -> minOf(nn, 4); else -> minOf(nn, 3) } * dp(58))
        val avail = (resources.displayMetrics.heightPixels - dp(135) - cardH) / rows
        val two = (avail - dp(32)) >= 2 * dp(33)                 // room for time + text on two lines
        val maxChips = ((avail - dp(32)) / (if (two) dp(33) else dp(21))).coerceIn(0, 2)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(5), dp(2), dp(2)); background = ring
        }
        col.addView(TextView(this).apply {
            text = "${d.dayOfMonth}"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(color)
            if (today || selected) setTypeface(null, Typeface.BOLD)
        }, lp(-1, dp(24)))
        val p = plans(d)
        p.take(maxChips).forEach { x ->
            val bg = when (x[3]) { "cash" -> CASH_BG; "card" -> CARD_BG; else -> 0xFFF0F0F0.toInt() }
            col.addView(TextView(this).apply {
                text = if (two && x[1].isNotEmpty()) "${x[1]}\n${x[0]}" else x[1].ifEmpty { x[0] }
                textSize = if (two) 11f else 12f; maxLines = if (two) 2 else 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER; setTextColor(Color.BLACK); background = shape(bg, 0, 0, 6)
                setPadding(dp(1), dp(2), dp(1), dp(2))
                if (!two) setTypeface(null, Typeface.BOLD)
            }, lp(-1, -2).apply { topMargin = dp(3) })
        }
        if (maxChips == 0 && p.isNotEmpty()) col.addView(tv("●", 8f, false, 0xFF555555.toInt()).apply { gravity = Gravity.CENTER }, lp(-1, -2))
        else if (p.size > maxChips) col.addView(tv("+${p.size - maxChips}", 11f, false, 0xFF777777.toInt()).apply { gravity = Gravity.CENTER }, lp(-1, -2))
        col.setOnClickListener { cur = d; render() }
        return col
    }
    val box = FrameLayout(this).apply { background = ring }
    box.addView(TextView(this).apply {
        text = "${d.dayOfMonth}"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(color)
        if (today || selected) setTypeface(null, Typeface.BOLD)
    }, FrameLayout.LayoutParams(-1, -1))
    if (plans(d).isNotEmpty()) box.addView(View(this).apply { background = shape(color, 0, 0, 0, true) },
        FrameLayout.LayoutParams(dp(4), dp(4), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(5) })
    return FrameLayout(this).apply {
        addView(box, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        setOnClickListener { cur = d; render() }
    }
}

// ---------- bottom card: handle, plans (max 3), bar with Notes / mic / today / + ----------
internal fun MainActivity.shell() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; elevation = dp(8).toFloat()
    val r = dp(28).toFloat()
    background = GradientDrawable().apply { setColor(CARD); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
}

internal fun MainActivity.bottomBar(): View {
    val b = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(6), dp(16), dp(12)) }
    b.addView(View(this), lp(0, 1).apply { weight = 1f })
    val mic = ImageButton(this).apply { setImageResource(android.R.drawable.ic_btn_speak_now); background = shape(Color.WHITE, 0, 0, 16) }
    attachVoice(mic)                                   // hold to talk
    micBtn = mic; styleMic()                           // red when the assistant is not reachable
    b.addView(mic, lp(dp(42), dp(42)).apply { marginStart = dp(10) })
    b.addView(TextView(this).apply {
        text = "+"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = shape(Color.BLACK, 0, 0, 16)
        setOnClickListener { planPopup(cur, null, "", "") }
    }, lp(dp(46), dp(46)).apply { marginStart = dp(10) })
    return b
}

internal fun MainActivity.buildSheet(d: LocalDate): View {
    val n = plans(d).size
    val rowH = dp(58)
    val peek = minOf(maxOf(n, 1), 3) * rowH
    val full = maxOf(peek, minOf(maxOf(n, 1) * rowH, (resources.displayMetrics.heightPixels * 0.34).toInt()))
    fun target() = when (panelState) { 0 -> 0; 2 -> full; else -> peek }
    val card = shell()
    val scroll = ScrollView(this).apply { addView(rowsOf(d, dp(4))) }
    val grab = FrameLayout(this)
    grab.addView(View(this).apply { background = shape(0xFFBDBDC4.toInt(), 0, 0, 3) },
        FrameLayout.LayoutParams(dp(36), dp(5), Gravity.CENTER))
    var y0 = 0f; var h0 = 0; var moved = false; var lastDy = 0f
    grab.setOnTouchListener { _, ev ->
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> { y0 = ev.rawY; h0 = scroll.layoutParams.height; moved = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - y0; lastDy = dy
                if (abs(dy) > dp(6)) moved = true
                scroll.layoutParams.height = (h0 - dy).toInt().coerceIn(0, full); card.requestLayout()
            }
            MotionEvent.ACTION_UP -> {
                if (overview && moved && h0 - lastDy > full + dp(70)) { overview = false; panelState = 1; render(); return@setOnTouchListener true }
                val hh = scroll.layoutParams.height
                panelState = if (moved) listOf(0 to 0, 1 to peek, 2 to full).minByOrNull { abs(it.second - hh) }!!.first
                else when (panelState) { 0 -> 1; 1 -> if (full > peek) 2 else 0; else -> 0 }
                scroll.layoutParams.height = target(); card.requestLayout()
            }
        }
        true
    }
    card.addView(grab, lp(-1, dp(28)))
    card.addView(scroll, lp(-1, target()))
    card.addView(bottomBar())
    return card
}

