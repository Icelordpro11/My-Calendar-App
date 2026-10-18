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

internal val CASH = 0xFF3FA66B.toInt()     // green = cash
internal val CARDC = 0xFFFF9800.toInt()    // orange = card
internal val CASH_BG = 0xFFDDF2E4.toInt()
internal val CARD_BG = 0xFFFFE6C4.toInt()

internal val BLUE = 0xFF111111.toInt()
internal val CARD = 0xFFF2F2F2.toInt()

internal val PASTELS = intArrayOf(0xFFFFE0E0.toInt(), 0xFFDDEBFF.toInt(), 0xFFE0F3DD.toInt(), 0xFFFFF0CF.toInt(), 0xFFEADFFF.toInt(), 0xFFFFE1F0.toInt())
internal val SOFT = 0xFFEDEDED.toInt()
internal val SOFT_INK = Color.BLACK

