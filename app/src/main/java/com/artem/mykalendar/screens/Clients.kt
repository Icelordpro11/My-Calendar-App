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

internal fun MainActivity.clientsScreen() {
    openPage = "Clients"
    clientOpen = null
    val visits = allVisits()
    val info = cinfo()
    val keys = LinkedHashSet<String>(); keys.addAll(visits.keys); info.keys().forEach { keys.add(it) }
    keys.removeAll { info.optJSONObject(it)?.optBoolean("hidden", false) == true }   // deleted clients stay hidden
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
    l.addView(tv(tr("Clients"), 26f, true).apply { setPadding(dp(4), 0, 0, dp(10)) })

    val tabs = LinearLayout(this)
    listOf("All", "Favorites", "History").forEachIndexed { i, name ->
        val sel = clientTab == i
        tabs.addView(TextView(this).apply {
            text = tr(name); textSize = 15f; gravity = Gravity.CENTER; setPadding(dp(16), dp(9), dp(16), dp(9))
            setTextColor(if (sel) SOFT_INK else 0xFF555555.toInt()); if (sel) setTypeface(null, Typeface.BOLD)
            background = shape(if (sel) SOFT else 0xFFF3F3F3.toInt(), 0, 0, 12)
            setOnClickListener { hideKb(); clientTab = i; clientsScreen() }
        }, lp(-2, -2).apply { marginEnd = dp(8) })
    }
    l.addView(tabs)

    val search = EditText(this).apply {
        hint = tr("Search clients"); textSize = 16f; setTextColor(Color.BLACK); setHintTextColor(0xFF999999.toInt())
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS; setSingleLine()
        background = null; setPadding(dp(10), dp(12), dp(16), dp(12))
    }
    val sbox = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; background = shape(0xFFF3F3F3.toInt(), 0, 0, 14); setPadding(dp(14), 0, 0, 0)
    }
    sbox.addView(SearchIcon(this), lp(dp(20), dp(20)))
    sbox.addView(search, lp(0, -2).apply { weight = 1f })
    l.addView(sbox, lp(-1, -2).apply { topMargin = dp(14) })

    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun fill() {
        list.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        if (clientTab == 2) {
            val flat = ArrayList<Pair<String, Visit>>()
            visits.forEach { (k, v) -> if (k in keys && (q.isEmpty() || displayName(k, v).lowercase().contains(q))) v.forEach { flat.add(k to it) } }
            flat.sortByDescending { it.second.date.toEpochDay() }
            if (flat.isEmpty()) list.addView(tv(tr("No visits found"), 16f, false, 0xFF9A9A9A.toInt()).apply { setPadding(dp(4), dp(24), 0, 0) })
            flat.take(300).forEach { (k, v) -> list.addView(visitRow(displayName(k, visits[k]), v, true) { clientDetail(k) }) }
            return
        }
        val shown = keys.filter { (clientTab == 0 || infoOf(it).optBoolean("fav", false)) &&
                (q.isEmpty() || displayName(it, visits[it]).lowercase().contains(q)) }
            .sortedBy { displayName(it, visits[it]).lowercase() }
        if (shown.isEmpty()) list.addView(tv(if (clientTab == 1 && q.isEmpty()) tr("No favorites yet — tap a star") else tr("No clients found"), 16f, false, 0xFF9A9A9A.toInt()).apply { setPadding(dp(4), dp(24), 0, 0) })
        shown.forEach { k ->
            val v = visits[k] ?: mutableListOf()
            val nm = displayName(k, visits[k])
            val fav = infoOf(k).optBoolean("fav", false)
            val r = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(12), dp(4), dp(12))
                setOnClickListener { clientDetail(k) }
            }
            val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(tv(nm, 18f, true))
            mid.addView(tv(if (v.isEmpty()) tr("No visits yet") else visitsText(v.size), 13f, false, 0xFF888888.toInt()))
            r.addView(mid, lp(0, -2).apply { weight = 1f })
            if (v.size > 1) r.addView(tv("×${v.size}", 14f, true, SOFT_INK).apply {
                setPadding(dp(10), dp(4), dp(10), dp(4)); background = shape(SOFT, 0, 0, 10)
            }, lp(-2, -2).apply { marginEnd = dp(6) })
            r.addView(tv(if (fav) "★" else "☆", 24f, false, if (fav) Color.BLACK else 0xFFAAAAAA.toInt()).apply {
                gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { val o = infoOf(k); o.put("fav", !fav); if (o.optString("name").isEmpty()) o.put("name", nm); putInfo(k, o); fill() }
            }, lp(-2, dp(44)))
            list.addView(r)
            list.addView(View(this).apply { setBackgroundColor(0xFFEEEEEE.toInt()) }, lp(-1, dp(1)).apply { marginStart = dp(6) })
        }
    }
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
        override fun onTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
        override fun afterTextChanged(e: Editable?) { fill() }
    })
    fill()
    l.addView(list, lp(-1, -2).apply { topMargin = dp(8) })
    showOverlay(ScrollView(this).apply { addView(l) })
}

internal fun MainActivity.visitRow(who: String?, v: Visit, showWho: Boolean, click: (() -> Unit)?): View {
    val bg = when (v.color) { "cash" -> CASH_BG; "card" -> CARD_BG; else -> 0xFFF4F4F4.toInt() }
    val r = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(12), dp(10))
        background = shape(bg, 0, 0, 14)
        if (click != null) setOnClickListener { click() }
    }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    left.addView(tv(v.date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", appLocale())), 15f, true))
    left.addView(tv(v.time.ifEmpty { "–" }, 13f, false, 0xFF777777.toInt()))
    r.addView(left, lp(dp(104), -2))
    val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    if (showWho && who != null) mid.addView(tv(who, 16f, true))
    mid.addView(tv(v.text, if (showWho) 13f else 15f, !showWho, if (showWho) 0xFF666666.toInt() else Color.BLACK).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
    r.addView(mid, lp(0, -2).apply { weight = 1f })
    if (v.color.isNotEmpty()) r.addView(View(this).apply {
        background = shape(if (v.color == "cash") CASH else CARDC, 0, 0, 5)
    }, lp(dp(10), dp(10)).apply { marginEnd = dp(8) })
    val w = LinearLayout(this)
    w.addView(r, lp(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
    return w
}

// one free text for everything you want to remember about the client
internal fun MainActivity.infoText(o: JSONObject): String {
    if (o.has("info")) return o.optString("info")
    return listOf(o.optString("phone"), o.optString("addr"), o.optString("note")).filter { it.isNotEmpty() }.joinToString("\n")
}

// client page: name on the left, info on the right, history below. ⋮ → Edit client
internal fun MainActivity.clientDetail(k: String, edit: Boolean = false) {
    clientOpen = k
    val visits = allVisits()[k] ?: mutableListOf()
    val o = infoOf(k)
    val nm = displayName(k, visits)
    val info = infoText(o)
    val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(30)) }

    val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    top.addView(tv("‹  " + tr("Clients"), 17f).apply { setPadding(0, dp(8), dp(16), dp(8)); setOnClickListener { hideKb(); clientsScreen() } }, lp(0, -2).apply { weight = 1f })
    val fav = o.optBoolean("fav", false)
    top.addView(tv(if (fav) "★" else "☆", 28f, false, if (fav) Color.BLACK else 0xFF999999.toInt()).apply {
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { o.put("fav", !fav); if (o.optString("name").isEmpty()) o.put("name", nm); putInfo(k, o); clientDetail(k, edit) }
    })
    top.addView(tv("⋮", 26f).apply {
        gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(6), 0)
        setOnClickListener {
            val c = LinearLayout(applicationContext).apply { orientation = LinearLayout.VERTICAL }
            c.addView(tv(tr("Edit client"), 18f).apply { setPadding(dp(14), dp(12), dp(28), dp(12)); setOnClickListener { dropLayer.visibility = View.GONE; clientDetail(k, true) } })
            c.addView(tv(tr("Delete client"), 18f, false, 0xFFD32F2F.toInt()).apply {
                setPadding(dp(14), dp(12), dp(28), dp(12))
                setOnClickListener {
                    val q = LinearLayout(applicationContext).apply { orientation = LinearLayout.VERTICAL }
                    q.addView(tv(tr("Delete this client?"), 17f, true).apply { setPadding(dp(6), dp(4), dp(6), dp(4)) })
                    q.addView(tv(tr("Old plans stay in the calendar"), 13f, false, 0xFF777777.toInt()).apply { setPadding(dp(6), 0, dp(6), dp(10)) })
                    val b = LinearLayout(applicationContext)
                    b.addView(btn(tr("No"), false) { dropLayer.visibility = View.GONE }, lp(-2, -2).apply { marginEnd = dp(8) })
                    b.addView(btn(tr("Yes"), true) {
                        dropLayer.visibility = View.GONE
                        if (o.optString("name").isEmpty()) o.put("name", nm)
                        o.put("hidden", true); putInfo(k, o); clientsScreen()
                    })
                    q.addView(b); showDrop(q)
                }
            })
            showDrop(c)
        }
    }, lp(-2, dp(44)))
    l.addView(top)

    if (edit) {
        l.addView(tv(tr("Name"), 13f, true, 0xFF888888.toInt()), lp(-2, -2).apply { topMargin = dp(16); bottomMargin = dp(4) })
        val na = EditText(this).apply {
            textSize = 18f; setTextColor(Color.BLACK); setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = shape(0xFFF4F4F4.toInt(), 0, 0, 12); setPadding(dp(14), dp(12), dp(14), dp(12)); setText(nm)
        }
        l.addView(na, lp(-1, -2))
        l.addView(tv(tr("Info"), 13f, true, 0xFF888888.toInt()), lp(-2, -2).apply { topMargin = dp(14); bottomMargin = dp(4) })
        val inf = EditText(this).apply {
            hint = tr("Phone, address, anything you want to remember…")
            textSize = 17f; setTextColor(Color.BLACK); gravity = Gravity.TOP or Gravity.START; minLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = shape(0xFFF4F4F4.toInt(), 0, 0, 14); setPadding(dp(14), dp(12), dp(14), dp(12)); setText(info)
        }
        l.addView(inf, lp(-1, -2))
        val b = LinearLayout(this)
        b.addView(softBtn(tr("Cancel"), false) { hideKb(); clientDetail(k, false) }, lp(0, -2).apply { weight = 1f; marginEnd = dp(10) })
        b.addView(softBtn(tr("Save"), true) {
            o.put("name", na.text.toString().trim().ifEmpty { nm }); o.put("info", inf.text.toString().trim())
            o.remove("phone"); o.remove("addr"); o.remove("note")
            putInfo(k, o); hideKb(); clientDetail(k, false)
        }, lp(0, -2).apply { weight = 1f })
        l.addView(b, lp(-1, -2).apply { topMargin = dp(18) })
    } else {
        val head = LinearLayout(this).apply { setPadding(dp(4), dp(18), dp(4), dp(18)) }
        val avail = resources.displayMetrics.widthPixels - dp(44)
        val nameW = (avail * 0.36f).toInt()
        head.addView(tv(nm, 26f, true), lp(nameW, -2))
        head.addView(View(this).apply { setBackgroundColor(0xFFCFCFCF.toInt()) }, lp(dp(1), -1).apply { setMargins(dp(8), dp(2), dp(14), dp(2)) })
        head.addView(tv(info.ifEmpty { tr("No info yet") }, 18f, false, if (info.isEmpty()) 0xFF9A9A9A.toInt() else 0xFF222222.toInt()).apply {
            setLineSpacing(0f, 1.2f); setPadding(0, 0, 0, dp(2))
        }, lp(avail - nameW - dp(23), -2))
        l.addView(head)
        l.addView(View(this).apply { setBackgroundColor(0xFFEAEAEA.toInt()) }, lp(-1, dp(1)))
    }

    val hh = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    hh.addView(HistIcon(this), lp(dp(20), dp(20)))
    hh.addView(View(this).apply { setBackgroundColor(0xFFDDDDDD.toInt()) }, lp(0, dp(1)).apply { weight = 1f; marginStart = dp(10) })
    l.addView(hh, lp(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(10) })
    if (visits.isEmpty()) l.addView(tv(tr("No visits yet"), 15f, false, 0xFF9A9A9A.toInt()))
    visits.forEach { l.addView(visitRow(null, it, false, null)) }
    showOverlay(ScrollView(this).apply { addView(l) })
}

