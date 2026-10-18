package com.artem.mykalendar.voice

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
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
import android.hardware.biometrics.BiometricManager
import org.json.JSONArray
import org.json.JSONObject

// Understands a spoken sentence without any internet / AI: "in 6 weeks at 11 Arina pedicure",
// "через 2 недели в 11:00", "over drie weken om 14 uur", "tomorrow at 9", "next Friday", "18 november".
internal class Spoken(val date: LocalDate?, val time: String, val text: String)

private val NumberWords = mapOf(
    "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
    "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    "een" to 1, "twee" to 2, "drie" to 3, "vier" to 4, "vijf" to 5, "zes" to 6, "zeven" to 7, "acht" to 8,
    "negen" to 9, "tien" to 10, "elf" to 11, "twaalf" to 12,
    "один" to 1, "одну" to 1, "одна" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
    "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12,
)
private val MonthStems = listOf("jan|янв", "feb|фев", "mar|maa|мар", "apr|апр", "may|mei|мая|май", "jun|июн", "jul|июл",
    "aug|авг", "sep|сен", "oct|okt|окт", "nov|ноя", "dec|дек")
private val WeekdayStems = listOf("monday|maandag|понедельник", "tuesday|dinsdag|вторник", "wednesday|woensdag|сред",
    "thursday|donderdag|четверг", "friday|vrijdag|пятниц", "saturday|zaterdag|суббот", "sunday|zondag|воскресень")
private val Fillers = Regex("""(?iu)(?<![\p{L}\d])(please|pls|can|could|would|you|add|make|create|book|schedule|appointment|plan|i|want|need|like|to|an|a|""" +
    """пожалуйста|можешь|можно|запиши|записать|запись|добавь|создай|поставь|встреча|встречу|а|""" +
    """afspraak|maak|kun|je|zet|voeg|toe|een|ik|wil|graag|tomorrow|morgen|завтра|сегодня|today|vandaag|overmorgen|послезавтра|""" +
    """next|volgende|следующ\p{L}*|вечера|вечером|утра|утром|evening|morning|avond|ochtend|afternoon|middag|дня)(?![\p{L}\d])""")

private val LittleWords = setOf("for", "with", "on", "at", "of", "the", "met", "voor", "bij", "op", "van", "de", "het", "с", "со", "в", "на", "у", "к", "о", "по", "для", "от")

private fun matches(word: String, stems: String) = stems.split('|').any { word.startsWith(it) }

internal fun parseSpoken(raw: String, today: LocalDate = LocalDate.now()): Spoken {
    var s = " " + raw.lowercase() + " "          // used for matching
    var o = " " + raw + " "                       // same text with the original capital letters, used for the plan text
    if (o.length != s.length) o = s
    var date: LocalDate? = null
    fun cut(m: MatchResult) { s = s.replaceRange(m.range, " "); o = o.replaceRange(m.range, " ") }

    // 1. "in 6 weeks", "через 2 недели", "over drie weken", "через неделю"
    val unitPart = """(dag\p{L}*|day\p{L}*|д(?:ень|ня|ней)|wee?k\p{L}*|недел\p{L}*|month\p{L}*|maand\p{L}*|месяц\p{L}*|year\p{L}*|jaar|jaren|год\p{L}*|лет)"""
    fun addUnit(unit: String, n: Int): LocalDate = when {
        unit.startsWith("dag") || unit.startsWith("day") || unit.startsWith("д") -> today.plusDays(n.toLong())
        unit.startsWith("we") || unit.startsWith("нед") -> today.plusWeeks(n.toLong())
        unit.startsWith("mo") || unit.startsWith("maa") || unit.startsWith("мес") -> today.plusMonths(n.toLong())
        else -> today.plusYears(n.toLong())
    }
    Regex("""(?<![\p{L}\d])(?:in|over|binnen|через)\s+(?:([\p{L}\d]+)\s+)?$unitPart(?![\p{L}])""").find(s)?.let { m ->
        val w = m.groupValues[1]
        date = addUnit(m.groupValues[2], w.toIntOrNull() ?: NumberWords[w] ?: 1); cut(m)
    }
    // 2. "18 november", "5 января", "3rd of May"
    if (date == null) Regex("""(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(\p{L}+)""").findAll(s).forEach { m ->
        val month = MonthStems.indexOfFirst { matches(m.groupValues[2], it) } + 1
        val day = m.groupValues[1].toInt()
        if (date == null && month > 0 && day in 1..31) {
            var d = try { LocalDate.of(today.year, month, day) } catch (e: Exception) { null }
            if (d != null && d.isBefore(today)) d = d.plusYears(1)
            if (d != null) { date = d; cut(m) }
        }
    }
    // 3. next week / next month
    if (date == null) Regex("""(?:next|volgende|следующ\p{L}*)\s+(wee?k\p{L}*|maand\p{L}*|month\p{L}*|недел\p{L}*|месяц\p{L}*)""").find(s)?.let { m ->
        date = addUnit(m.groupValues[1], 1); cut(m)
    }
    // 4. day after tomorrow / tomorrow / today
    if (date == null) Regex("""(?<![\p{L}])(day after tomorrow|overmorgen|послезавтра)(?![\p{L}])""").find(s)?.let { date = today.plusDays(2); cut(it) }
    if (date == null) Regex("""(?<![\p{L}])(tomorrow|morgen|завтра)(?![\p{L}])""").find(s)?.let { date = today.plusDays(1); cut(it) }
    // 5. weekday
    if (date == null) Regex("""\p{L}+""").findAll(s).forEach { m ->
        val wd = WeekdayStems.indexOfFirst { matches(m.value, it) } + 1
        if (date == null && wd > 0) {
            val before = s.substring(maxOf(0, m.range.first - 14), m.range.first)
            val next = Regex("next|volgende|следующ").containsMatchIn(before)
            val todayNo = today.dayOfWeek.value
            var ahead = (wd - todayNo + 7) % 7
            if (ahead == 0) ahead = 7
            if (next && ahead <= 7 - todayNo) ahead += 7
            date = today.plusDays(ahead.toLong()); cut(m)
        }
    }
    if (date == null) Regex("""(?<![\p{L}])(today|vandaag|сегодня)(?![\p{L}])""").find(s)?.let { date = today; cut(it) }

    // time: 11:00 / 11.30 / at 11 / om 11 uur / в 11 / 11 часов / 11 o'clock
    var hour = -1; var minute = 0
    val clock = Regex("""(?:(?<![\p{L}])(?:at|om|в|к|rond|around)\s+)?(\d{1,2})[:.](\d{2})""").find(s)
    if (clock != null) { hour = clock.groupValues[1].toInt(); minute = clock.groupValues[2].toInt(); cut(clock) }
    else {
        val m = Regex("""(?:(?<![\p{L}])(?:at|om|в|к|rond|around)\s+(\d{1,2})(?!\d)|(\d{1,2})\s*(?:uur|часов|часа|час|o'clock|oclock))(?:\s*(?:uur|часов|часа|час|o'clock|oclock))?""").find(s)
        if (m != null) { hour = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt(); cut(m) }
    }
    if (hour in 0..23 && minute in 0..59) {
        val evening = Regex("""(?<![\p{L}])(?:вечер\p{L}*|дня|днём|днем|evening|avond\p{L}*|afternoon|middag\p{L}*)(?![\p{L}])""").containsMatchIn(s)
        val morning = Regex("""(?<![\p{L}])(?:утра|утром|morning|ochtend\p{L}*)(?![\p{L}])""").containsMatchIn(s)
        if (hour < 12 && (evening || (!morning && hour in 1..7))) hour += 12      // appointments: 1-7 means afternoon
    } else hour = -1
    val time = if (hour >= 0) "%02d:%02d".format(hour, minute) else ""

    // keep only the NAME: the first word that is left (skipping little words like "with", "met", "с")
    val left = o.replace(Fillers, " ").replace(Regex("""\s+"""), " ").trim(' ', '.', ',', ';', ':', '-').split(' ')
    val name = left.firstOrNull { it.length > 1 && it.lowercase() !in LittleWords } ?: ""
    val text = name.trim('.', ',', ';', ':', '-').replaceFirstChar { c -> c.uppercase() }
    return Spoken(date, time, text)
}
