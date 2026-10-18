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

// bar chart: green + orange stacked per bucket (day / week / month / year)
internal class MoneyChart(ctx: Context) : View(ctx) {
    var cash = DoubleArray(1)
    var card = DoubleArray(1)
    var labels: List<String> = listOf("")
    var target = 0.0
    var mark = -1
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF444444.toInt(); textSize = dp(10).toFloat() }
    private val base = Paint().apply { color = 0xFFCFCFCF.toInt(); strokeWidth = dp(1).toFloat() }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() * 1.5f
        pathEffect = DashPathEffect(floatArrayOf(dp(6).toFloat(), dp(4).toFloat()), 0f)
    }
    private val path = Path()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val n = cash.size
        val top = dp(22).toFloat(); val bottom = h - dp(26).toFloat()
        val tot = DoubleArray(n) { cash[it] + card[it] }
        var mx = target
        for (t in tot) if (t > mx) mx = t
        txt.isFakeBoldText = false
        if (mx <= 0.0) { c.drawText(tr("No income in this period"), w / 2f, h / 2f, txt); return }
        mx *= 1.1
        val slot = w / n; val bw = minOf(slot * 0.56f, dp(48).toFloat()); val ch = bottom - top
        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            var y = bottom
            val hc = (cash[i] / mx * ch).toFloat(); val hd = (card[i] / mx * ch).toFloat()
            if (hc > 0f) { bar.color = CASH; c.drawRect(cx - bw / 2f, y - hc, cx + bw / 2f, y, bar); y -= hc }
            if (hd > 0f) { bar.color = CARDC; c.drawRect(cx - bw / 2f, y - hd, cx + bw / 2f, y, bar); y -= hd }
            txt.isFakeBoldText = false
            if (n <= 12 && tot[i] > 0.0) c.drawText(compact(tot[i]), cx, y - dp(4).toFloat(), txt)
            txt.isFakeBoldText = (i == mark)
            c.drawText(labels.getOrElse(i) { "" }, cx, h - dp(8).toFloat(), txt)
        }
        c.drawLine(0f, bottom, w, bottom, base)
        if (target > 0.0) {
            val ty = bottom - (target / mx * ch).toFloat()
            path.reset(); path.moveTo(0f, ty); path.lineTo(w, ty); c.drawPath(path, dash)
        }
    }
}

// progress towards the yearly goal
internal class MoneyBar(ctx: Context) : View(ctx) {
    var frac = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = 0xFFEAEAEA.toInt(); c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, p)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0f) { p.color = 0xFF222222.toInt(); c.drawRoundRect(RectF(0f, 0f, maxOf(width * f, height.toFloat()), height.toFloat()), r, r, p) }
    }
}

