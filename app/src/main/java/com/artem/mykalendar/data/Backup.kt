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

// =====================================================================
//  JSON file: everything is mirrored to mykalendar.json (for the future voice assistant)
// =====================================================================
internal fun MainActivity.mirror() {
    try { java.io.File(getExternalFilesDir(null) ?: filesDir, "mykalendar.json").writeText(buildJson()) } catch (e: Exception) {}
}

internal fun MainActivity.buildJson(): String {
    val root = JSONObject().put("app", "mykalendar").put("version", 1).put("exported", java.time.LocalDateTime.now().toString())
    val cals = JSONObject()
    for ((name, suf) in listOf("business" to "_biz", "personal" to "")) {
        val c = JSONObject()
        val plansArr = JSONArray()
        val data = JSONObject(prefs.getString("d$suf", "{}")!!)
        for (dt in data.keys().asSequence().toList().sorted()) {
            val a = data.optJSONArray(dt) ?: continue
            for (j in 0 until a.length()) {
                val p = a.getJSONObject(j)
                plansArr.put(JSONObject().put("date", dt).put("time", p.optString("s")).put("text", p.optString("t"))
                    .put("color", p.optString("st")).put("amount", p.optString("am")))
            }
        }
        c.put("plans", plansArr)
        val ci = JSONObject(cstore().getString("info$suf", "{}")!!)
        val clients = JSONArray()
        for (k in ci.keys().asSequence().toList()) {
            val o = ci.optJSONObject(k) ?: continue
            clients.put(JSONObject().put("key", k).put("name", o.optString("name")).put("info", infoText(o)).put("favorite", o.optBoolean("fav", false)).put("hidden", o.optBoolean("hidden", false)))
        }
        c.put("clients", clients)
        val pg = getSharedPreferences("pages", Context.MODE_PRIVATE)
        c.put("notes", JSONArray(pg.getString("Notes$suf", "[]")))
        c.put("money", JSONArray(pg.getString("Money$suf", "[]")))
        val goals = JSONObject()
        val tag = if (suf == "") "p" else "b"
        for ((k, v) in mstore().all) if (k.startsWith("goal|$tag|")) goals.put(k.substringAfterLast('|'), v.toString())
        c.put("yearGoals", goals)
        cals.put(name, c)
    }
    root.put("calendars", cals)
    return root.toString(2)
}

internal fun MainActivity.importJson(text: String) {
    val cals = JSONObject(text).getJSONObject("calendars")
    val pe = prefs.edit(); val cs = cstore().edit(); val pg = getSharedPreferences("pages", Context.MODE_PRIVATE).edit(); val ms = mstore().edit()
    for ((name, suf) in listOf("business" to "_biz", "personal" to "")) {
        val c = cals.optJSONObject(name) ?: continue
        val data = JSONObject()
        val pa = c.optJSONArray("plans") ?: JSONArray()
        for (j in 0 until pa.length()) {
            val p = pa.getJSONObject(j)
            val dt = p.getString("date")
            val arr = data.optJSONArray(dt) ?: JSONArray().also { data.put(dt, it) }
            arr.put(JSONObject().put("t", p.optString("text")).put("s", p.optString("time")).put("e", "")
                .put("st", p.optString("color")).put("am", p.optString("amount")))
        }
        pe.putString("d$suf", data.toString())
        val info = JSONObject()
        val ca = c.optJSONArray("clients") ?: JSONArray()
        for (j in 0 until ca.length()) {
            val o = ca.getJSONObject(j)
            info.put(o.getString("key"), JSONObject().put("name", o.optString("name")).put("info", o.optString("info")).put("fav", o.optBoolean("favorite", false)).put("hidden", o.optBoolean("hidden", false)))
        }
        cs.putString("info$suf", info.toString())
        pg.putString("Notes$suf", (c.optJSONArray("notes") ?: JSONArray()).toString())
        pg.putString("Money$suf", (c.optJSONArray("money") ?: JSONArray()).toString())
        val gs = c.optJSONObject("yearGoals")
        if (gs != null) for (y in gs.keys().asSequence().toList()) ms.putString("goal|" + (if (suf == "") "p" else "b") + "|" + y, gs.optString(y))
    }
    pe.apply(); cs.apply(); pg.apply(); ms.apply(); mirror(); rescheduleReminders()
}

internal fun MainActivity.exportFile() = exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "mykalendar.json"))
internal fun MainActivity.importFile() = importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))

