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

// nicer calendar icon, drawn in code
// black & white profile icons drawn in code: briefcase = business, person = personal
internal class ProfileIcon(ctx: Context) : View(ctx) {
    var business = true
    var ink = Color.BLACK
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        p.color = ink
        val s = minOf(width, height) * 0.58f / 24f
        c.save(); c.translate((width - 24 * s) / 2, (height - 24 * s) / 2); c.scale(s, s)
        if (business) {
            c.drawRoundRect(RectF(3f, 8f, 21f, 20f), 2.5f, 2.5f, p)
            c.drawLine(9f, 8f, 9f, 5f, p); c.drawLine(9f, 5f, 15f, 5f, p); c.drawLine(15f, 5f, 15f, 8f, p)
            c.drawLine(3f, 13f, 21f, 13f, p)
        } else {
            c.drawCircle(12f, 8f, 4f, p)                                   // head
            c.drawArc(RectF(5f, 14f, 19f, 28f), 180f, 180f, false, p)      // shoulders
        }
        c.restore()
    }
}

// list icon: show / hide the plans inside the dates
internal class PlansIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.56f / 24f
        c.save(); c.translate((width - 24 * s) / 2, (height - 24 * s) / 2); c.scale(s, s)
        for (y in listOf(6f, 12f, 18f)) { c.drawPoint(4f, y, p); c.drawLine(9f, y, 21f, y, p) }
        c.restore()
    }
}

internal class SearchIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 2.2f; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save(); c.scale(s, s)
        c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(15.5f, 15.5f, 21f, 21f, p)
        c.restore()
    }
}

internal class HistIcon(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save(); c.scale(s, s)
        c.drawCircle(12f, 12f, 9f, p)
        c.drawLine(12f, 6.5f, 12f, 12f, p); c.drawLine(12f, 12f, 16f, 14.5f, p)
        c.restore()
    }
}

internal class CalIcon(ctx: Context) : View(ctx) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.56f / 24f
        c.save(); c.translate((width - 24 * s) / 2, (height - 24 * s) / 2); c.scale(s, s)
        c.drawRoundRect(RectF(3f, 5f, 21f, 21f), 3.5f, 3.5f, line)
        c.drawLine(3f, 10f, 21f, 10f, line)
        c.drawLine(8f, 2.5f, 8f, 6.5f, line); c.drawLine(16f, 2.5f, 16f, 6.5f, line)
        for ((x, y) in listOf(8f to 14f, 12f to 14f, 16f to 14f, 8f to 18f, 12f to 18f)) c.drawCircle(x, y, 1.15f, dot)
        c.restore()
    }
}

