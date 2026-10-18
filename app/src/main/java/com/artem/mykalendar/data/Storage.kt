package com.artem.mykalendar.data

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

// ---------- storage: plan = [text, start, (unused), color, amount] color: "", cash, card ----------
internal fun MainActivity.pk() = if (biz) "d_biz" else "d"
internal fun MainActivity.all() = JSONObject(prefs.getString(pk(), "{}")!!)
internal fun MainActivity.plans(d: LocalDate): List<Array<String>> {
    val a = all().optJSONArray(d.toString()) ?: return emptyList()
    return (0 until a.length()).map {
        val o = a.getJSONObject(it)
        val st = o.optString("st", "").let { v -> if (v == "cash" || v == "card") v else "" }
        arrayOf(o.getString("t"), o.getString("s"), "", st, o.optString("am", ""))
    }.sortedBy { it[1] }
}
internal fun MainActivity.save(d: LocalDate, l: List<Array<String>>) {
    val o = all(); val a = JSONArray()
    l.sortedBy { it[1] }.forEach {
        a.put(JSONObject().put("t", it[0]).put("s", it[1]).put("e", "").put("st", it[3]).put("am", it[4]))
    }
    o.put(d.toString(), a)
    prefs.edit().putString(pk(), o.toString()).apply(); mirror(); rescheduleReminders()
}
internal fun MainActivity.setStatus(d: LocalDate, i: Int, st: String, am: String) {
    val l = plans(d); l[i][3] = st; l[i][4] = if (st.isEmpty()) "" else am; save(d, l); render()
}

// =====================================================================
//  CLIENTS: built automatically from the plans (first word of the plan = client name)
// =====================================================================
internal class Visit(val date: LocalDate, val time: String, val text: String, val color: String, val amount: String)

internal fun MainActivity.cstore() = getSharedPreferences("clients", Context.MODE_PRIVATE)
internal fun MainActivity.ckey() = "info" + if (biz) "_biz" else ""
internal fun MainActivity.cinfo() = JSONObject(cstore().getString(ckey(), "{}")!!)
internal fun MainActivity.infoOf(k: String): JSONObject = cinfo().optJSONObject(k) ?: JSONObject()
internal fun MainActivity.putInfo(k: String, o: JSONObject) { val a = cinfo(); a.put(k, o); cstore().edit().putString(ckey(), a.toString()).apply(); mirror() }
internal fun clientWord(text: String) = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty().trim(':', ',', '.', '-', ';')
// every visit of the current calendar, grouped per client (key = lowercase name)
internal fun MainActivity.allVisits(): Map<String, MutableList<Visit>> {
    val known = cinfo().keys().asSequence().toList().sortedByDescending { it.length }
    val m = LinkedHashMap<String, MutableList<Visit>>()
    val o = all()
    val iter = o.keys()
    while (iter.hasNext()) {
        val k = iter.next()
        val dt = try { LocalDate.parse(k) } catch (e: Exception) { continue }
        for (x in plans(dt)) {
            val low = x[0].trim().lowercase()
            val key = known.firstOrNull { low == it || low.startsWith("$it ") } ?: clientWord(low)
            if (key.isEmpty()) continue
            m.getOrPut(key) { mutableListOf() }.add(Visit(dt, x[1], x[0], x[3], x[4]))
        }
    }
    m.values.forEach { l -> l.sortByDescending { v -> v.date.toEpochDay() } }
    return m
}

internal fun MainActivity.displayName(k: String, v: List<Visit>?): String {
    val n = infoOf(k).optString("name", "")
    if (n.isNotEmpty()) return n
    val base = if (v != null && v.isNotEmpty() && v[0].text.trim().lowercase().startsWith(k)) v[0].text.trim().substring(0, k.length) else k
    return base.replaceFirstChar { c -> c.uppercase() }
}

// =====================================================================
//  MONEY: built from the amounts typed on the plans. They are NOT shown on the calendar or in clients.
//  Calendar icon = pick Day / Week / Month / Year. List icon = switch table <-> graph.
// =====================================================================
internal class Pay(val date: LocalDate, val color: String, val amount: Double)

internal fun MainActivity.mstore() = getSharedPreferences("money", Context.MODE_PRIVATE)
internal fun MainActivity.goalKey(year: Int) = "goal|" + (if (biz) "b" else "p") + "|" + year

internal fun MainActivity.payments(): List<Pay> {
    val out = ArrayList<Pay>()
    val o = all()
    val iter = o.keys()
    while (iter.hasNext()) {
        val k = iter.next()
        val dt = try { LocalDate.parse(k) } catch (e: Exception) { continue }
        val a = o.optJSONArray(k) ?: continue
        for (j in 0 until a.length()) {
            val p = a.getJSONObject(j)
            val st = p.optString("st", "")
            val v = num(p.optString("am", ""))
            if ((st == "cash" || st == "card") && v > 0.0) out.add(Pay(dt, st, v))
        }
    }
    out.sortBy { it.date.toEpochDay() }
    return out
}

