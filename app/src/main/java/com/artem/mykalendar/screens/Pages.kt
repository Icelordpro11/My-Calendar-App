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

// ---------- pages opened from the ⋮ menu ----------
internal fun MainActivity.pageScreen(name: String) {
    when (name) {
        "Clients" -> clientsScreen()
        "Money" -> requireUnlock { moneyAnchor = cur; moneyScreen() }
        else -> notesScreen()
    }
}

internal fun MainActivity.reopenPage() {
    when (openPage) {
        "Clients" -> clientsScreen()
        "Money" -> moneyScreen()
        "Notes" -> notesScreen()
        "Settings" -> settingsScreen()
    }
}

// ---------- Notes: list of cards, + button to add, tap a card to edit ----------
internal fun MainActivity.notesScreen() {
    openPage = "Notes"
    clientOpen = null
    val store = getSharedPreferences("pages", Context.MODE_PRIVATE)
    val key = "Notes" + if (biz) "_biz" else ""
    fun items() = JSONArray(store.getString(key, "[]"))
    fun write(a: JSONArray) { store.edit().putString(key, a.toString()).apply(); mirror() }

    fun editor(idx: Int?) {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(20)) }
        l.addView(tv(tr("Notes"), 26f, true).apply { setPadding(dp(4), 0, 0, dp(12)) })
        val input = EditText(this).apply {
            hint = tr("Write a note…"); textSize = 18f; setTextColor(Color.BLACK); gravity = Gravity.TOP or Gravity.START
            minLines = 8; background = shape(0xFFF4F4F4.toInt(), 0, 0, 14); setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            if (idx != null) setText(items().getString(idx))
        }
        l.addView(input, lp(-1, -2))
        val b = LinearLayout(this).apply { gravity = Gravity.END }
        b.addView(btn(tr("Cancel"), false) { hideKb(); notesScreen() }, lp(-2, -2).apply { marginEnd = dp(10) })
        b.addView(btn(tr("Save"), true) {
            val t = input.text.toString().trim()
            if (t.isNotEmpty()) {
                val a = items()
                if (idx == null) a.put(t) else a.put(idx, t)
                write(a)
            }
            hideKb(); notesScreen()
        })
        l.addView(b, lp(-1, -2).apply { topMargin = dp(14) })
        showOverlay(ScrollView(this).apply { addView(l) })
        input.post {
            input.requestFocus()
            try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) } catch (e: Exception) {}
        }
    }

    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(110)) }
    list.addView(tv(tr("Notes"), 26f, true).apply { setPadding(dp(4), 0, 0, dp(12)) })
    val a = items()
    if (a.length() == 0) list.addView(tv(tr("No notes yet"), 16f, false, 0xFF9A9A9A.toInt()).apply { setPadding(dp(4), dp(16), 0, 0) })
    for (i in 0 until a.length()) {
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(4), dp(10))
            background = shape(0xFFF4F4F4.toInt(), 0, 0, 14)
            setOnClickListener { editor(i) }
        }
        card.addView(tv(a.getString(i), 17f).apply { maxLines = 4; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, -2).apply { weight = 1f })
        card.addView(delBtn { val n = items(); n.remove(i); write(n); notesScreen() })
        list.addView(card, lp(-1, -2).apply { bottomMargin = dp(8) })
    }
    val root = FrameLayout(this)
    root.addView(ScrollView(this).apply { addView(list) }, FrameLayout.LayoutParams(-1, -1))
    root.addView(tv("+", 32f, false, Color.WHITE).apply {
        gravity = Gravity.CENTER; background = shape(Color.BLACK, 0, 0, 0, true)
        setOnClickListener { editor(null) }
    }, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(22), dp(26)) })
    showOverlay(root)
}
