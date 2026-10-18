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

internal fun MainActivity.moneyMenu() = openMenu(listOf(
    tr("Day") to { moneyPeriod = 0; moneyScreen() },
    tr("Week") to { moneyPeriod = 1; moneyScreen() },
    tr("Month") to { moneyPeriod = 2; moneyScreen() },
    tr("Year") to { moneyPeriod = 3; moneyScreen() }
), Gravity.END, moneyPeriod)

internal fun MainActivity.moneyScreen() {
    openPage = "Money"
    clientOpen = null
    val pays = payments()
    val a = moneyAnchor
    val df = DateTimeFormatter.ofPattern("dd-MM-yyyy", appLocale())
    val dm = DateTimeFormatter.ofPattern("dd-MM", appLocale())
    val monday = a.minusDays((a.dayOfWeek.value - 1).toLong())
    val pFrom: LocalDate; val pTo: LocalDate; val label: String
    when (moneyPeriod) {
        0 -> { pFrom = a; pTo = a; label = a.format(DateTimeFormatter.ofPattern("EEE dd-MM-yyyy", appLocale())) }
        1 -> { pFrom = monday; pTo = monday.plusDays(6); label = monday.format(dm) + " – " + monday.plusDays(6).format(df) }
        2 -> { pFrom = a.withDayOfMonth(1); pTo = a.withDayOfMonth(a.lengthOfMonth())
               label = a.format(DateTimeFormatter.ofPattern("MMMM yyyy", appLocale())).replaceFirstChar { it.uppercase() } }
        else -> { pFrom = LocalDate.of(a.year, 1, 1); pTo = LocalDate.of(a.year, 12, 31); label = "${a.year}" }
    }
    val items = pays.filter { !it.date.isBefore(pFrom) && !it.date.isAfter(pTo) }
    val total = items.sumOf { it.amount }

    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(30)) }
    l.addView(tv(tr("Budget"), 26f, true).apply { setPadding(dp(4), 0, 0, dp(6)) })

    // ‹  label  ›
    fun go(n: Int) {
        moneyAnchor = when (moneyPeriod) {
            0 -> a.plusDays(n.toLong()); 1 -> a.plusWeeks(n.toLong()); 2 -> a.plusMonths(n.toLong()); else -> a.plusYears(n.toLong())
        }
        moneyScreen()
    }
    val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    nav.addView(tv("‹", 32f).apply { gravity = Gravity.CENTER; setOnClickListener { go(-1) } }, lp(dp(52), dp(44)))
    nav.addView(tv(label, 18f, true).apply { gravity = Gravity.CENTER }, lp(0, -2).apply { weight = 1f })
    nav.addView(tv("›", 32f).apply { gravity = Gravity.CENTER; setOnClickListener { go(1) } }, lp(dp(52), dp(44)))
    l.addView(nav)

    // total
    val totBox = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14)); background = shape(0xFFF4F4F4.toInt(), 0, 0, 14)
    }
    totBox.addView(tv(tr("Total"), 17f, true), lp(0, -2).apply { weight = 1f })
    totBox.addView(tv(fmt(total), 26f, true))
    l.addView(totBox, lp(-1, -2).apply { topMargin = dp(6) })

    if (!moneyGraph) {
        // ---- tables: green + orange, Date and Amount ----
        val rr = dp(12).toFloat()
        fun table(color: Int, bg: Int, rows: List<Pair<String, Double>>): View {
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 0xFFE2E2E2.toInt(), 1, 12) }
            val head = LinearLayout(this).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { setColor(color); cornerRadii = floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f) }
            }
            head.addView(tv(tr("Date"), 12f, true, Color.WHITE), lp(0, -2).apply { weight = 1f })
            head.addView(tv(tr("Amount"), 12f, true, Color.WHITE))
            t.addView(head)
            if (rows.isEmpty()) t.addView(tv("–", 14f, false, 0xFFAAAAAA.toInt()).apply { setPadding(dp(10), dp(10), dp(10), dp(10)) })
            rows.forEach { (d, v) ->
                val r = LinearLayout(this).apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
                r.addView(tv(d, 13f).apply { maxLines = 1 }, lp(0, -2).apply { weight = 1f })
                r.addView(tv(fmt(v), 14f, true).apply { maxLines = 1 })
                t.addView(r)
                t.addView(View(this).apply { setBackgroundColor(0xFFF0F0F0.toInt()) }, lp(-1, dp(1)))
            }
            val foot = LinearLayout(this).apply {
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = GradientDrawable().apply { setColor(bg); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, rr, rr, rr, rr) }
            }
            foot.addView(tv(tr("Total"), 13f, true), lp(0, -2).apply { weight = 1f })
            foot.addView(tv(fmt(rows.sumOf { it.second }), 15f, true))
            t.addView(foot)
            return t
        }
        fun rowsOf(color: String): List<Pair<String, Double>> {
            val f = items.filter { it.color == color }
            return if (moneyPeriod == 3)
                f.groupBy { it.date.monthValue }.entries.sortedBy { it.key }
                    .map { e -> String.format(Locale.US, "%02d-%d", e.key, a.year) to e.value.sumOf { it.amount } }
            else f.map { it.date.format(df) to it.amount }
        }
        val row = LinearLayout(this)
        row.addView(table(CASH, CASH_BG, rowsOf("cash")), lp(0, -2).apply { weight = 1f; marginEnd = dp(5) })
        row.addView(table(CARDC, CARD_BG, rowsOf("card")), lp(0, -2).apply { weight = 1f; marginStart = dp(5) })
        l.addView(row, lp(-1, -2).apply { topMargin = dp(14) })
    } else {
        // ---- graph ----
        val n = when (moneyPeriod) { 0 -> 1; 1 -> 7; 2 -> a.lengthOfMonth(); else -> 12 }
        val cashB = DoubleArray(n); val cardB = DoubleArray(n)
        items.forEach {
            val idx = when (moneyPeriod) { 0 -> 0; 1 -> it.date.dayOfWeek.value - 1; 2 -> it.date.dayOfMonth - 1; else -> it.date.monthValue - 1 }
            if (it.color == "cash") cashB[idx] += it.amount else cardB[idx] += it.amount
        }
        val labels = (0 until n).map { i ->
            when (moneyPeriod) {
                0 -> "${a.dayOfMonth}"
                1 -> DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, appLocale()).take(2).replaceFirstChar { it.uppercase() }
                2 -> if (i == 0 || (i + 1) % 5 == 0) "${i + 1}" else ""
                else -> Month.of(i + 1).getDisplayName(TextStyle.NARROW, appLocale())
            }
        }
        val today = LocalDate.now()
        val mark = when (moneyPeriod) {
            0 -> if (a == today) 0 else -1
            1 -> if (!today.isBefore(pFrom) && !today.isAfter(pTo)) today.dayOfWeek.value - 1 else -1
            2 -> if (today.year == a.year && today.month == a.month) today.dayOfMonth - 1 else -1
            else -> if (today.year == a.year) today.monthValue - 1 else -1
        }
        val chart = MoneyChart(this).apply { cash = cashB; card = cardB; this.labels = labels; this.mark = mark }

        if (moneyPeriod == 3) {
            // yearly goal: type a number, see the progress
            val gRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            gRow.addView(tv(trf("Goal for %s", a.year), 16f, true), lp(0, -2).apply { weight = 1f })
            val ge = EditText(this).apply {
                hint = "0"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                filters = arrayOf(InputFilter.LengthFilter(9)); setSingleLine()
                background = shape(0xFFF4F4F4.toInt(), 0, 0, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
                setText(mstore().getString(goalKey(a.year), ""))
            }
            gRow.addView(ge, lp(dp(130), -2))
            l.addView(gRow, lp(-1, -2).apply { topMargin = dp(18) })
            val summary = tv("", 16f, true)
            val bar = MoneyBar(this)
            val left = tv("", 14f, false, 0xFF666666.toInt())
            val note = tv(tr("Dashed line = monthly target (goal ÷ 12)"), 12f, false, 0xFF888888.toInt())
            l.addView(summary, lp(-1, -2).apply { topMargin = dp(14) })
            l.addView(bar, lp(-1, dp(14)).apply { topMargin = dp(8) })
            l.addView(left, lp(-1, -2).apply { topMargin = dp(6) })
            fun update() {
                val g = num(ge.text.toString())
                bar.frac = if (g > 0.0) (total / g).toFloat() else 0f
                bar.invalidate()
                summary.text = if (g > 0.0) trf("Earned %s of %s  ·  %s%%", fmt(total), fmt(g), Math.round(total / g * 100)) else trf("Earned %s this year", fmt(total))
                left.text = if (g > 0.0) (if (total >= g) tr("Goal reached") else trf("Left to earn  %s", fmt(g - total))) else tr("Fill in a goal to follow your progress")
                chart.target = g / 12.0
                chart.invalidate()
                note.visibility = if (g > 0.0) View.VISIBLE else View.GONE
            }
            ge.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(c: CharSequence?, s: Int, b: Int, n: Int) {}
                override fun onTextChanged(c: CharSequence?, s: Int, b: Int, n: Int) {}
                override fun afterTextChanged(e: Editable?) {
                    mstore().edit().putString(goalKey(a.year), e?.toString() ?: "").apply(); mirror(); update()
                }
            })
            l.addView(chart, lp(-1, dp(220)).apply { topMargin = dp(18) })
            l.addView(note, lp(-1, -2).apply { topMargin = dp(4) })
            update()
        } else {
            l.addView(chart, lp(-1, dp(240)).apply { topMargin = dp(18) })
        }
    }

    showOverlay(ScrollView(this).apply { addView(l) })
    onMoney = true
    styleTop()
}

