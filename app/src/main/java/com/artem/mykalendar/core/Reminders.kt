package com.artem.mykalendar.core

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
import android.hardware.biometrics.BiometricManager
import com.artem.mykalendar.R
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.net.Uri
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

// Reminders: one or more notifications before a plan; the person chooses how long before (Settings)
internal val REMINDER_CHOICES = listOf(0, 15, 30, 60, 120, 180, 360, 720, 1440, 2880, 10080)   // minutes before

internal fun reminderMinutes(ap: android.content.SharedPreferences): List<Int> =
    (ap.getString("rem_list", "60") ?: "60").split(",").mapNotNull { it.toIntOrNull() }.distinct().sorted()

private fun ruPlural(n: Int, one: String, few: String, many: String) = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

// "2 hours" / "2 uur" / "2 часа" (correct plural in each language)
private fun durText(m: Int): String {
    val (n, u) = when { m % 10080 == 0 -> m / 10080 to 3; m % 1440 == 0 -> m / 1440 to 2; m % 60 == 0 -> m / 60 to 1; else -> m to 0 }
    return when (lang) {
        "nl" -> "$n " + listOf(if (n == 1) "minuut" else "minuten", "uur", if (n == 1) "dag" else "dagen", if (n == 1) "week" else "weken")[u]
        "ru" -> "$n " + when (u) {
            0 -> ruPlural(n, "минуту", "минуты", "минут"); 1 -> ruPlural(n, "час", "часа", "часов")
            2 -> ruPlural(n, "день", "дня", "дней"); else -> ruPlural(n, "неделю", "недели", "недель")
        }
        else -> "$n " + listOf("minute", "hour", "day", "week")[u] + (if (n == 1) "" else "s")
    }
}

// label in the Settings list: "2 hours before"
internal fun remLabel(m: Int): String {
    if (m == 0) return tr("At the time")
    return when (lang) { "nl" -> durText(m) + " van tevoren"; "ru" -> "За " + durText(m); else -> durText(m) + " before" }
}

// text of the notification, a full sentence: "Starts in 2 hours, at 09:00"
internal fun remBody(m: Int, time: String): String = when (lang) {
    "nl" -> when (m) { 0 -> "Begint nu"; 1440 -> "Begint morgen om $time"; else -> "Begint over ${durText(m)}, om $time" }
    "ru" -> when (m) { 0 -> "Начинается сейчас"; 1440 -> "Начинается завтра в $time"; else -> "Начинается через ${durText(m)}, в $time" }
    else -> when (m) { 0 -> "Starts now"; 1440 -> "Starts tomorrow at $time"; else -> "Starts in ${durText(m)}, at $time" }
}

internal const val CHANNEL = "plans"

internal fun Context.makeChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, tr("Reminders"), NotificationManager.IMPORTANCE_HIGH))
    }
}

private fun Context.alarmIntent(i: Int, title: String = "", body: String = ""): PendingIntent {
    val it = Intent(this, ReminderReceiver::class.java).setData(Uri.parse("plan://reminder/$i"))
        .putExtra("title", title).putExtra("body", body)
    return PendingIntent.getBroadcast(this, i, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

// removes the old alarms and sets new ones for the next plans of both calendars
internal fun Context.rescheduleReminders() {
    val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val ap = getSharedPreferences("app", Context.MODE_PRIVATE)
    lang = ap.getString("lang", "en") ?: "en"
    for (i in 0 until ap.getInt("rem_n", 0)) am.cancel(alarmIntent(i))

    val offsets: List<Int> = if (ap.getBoolean("rem_on", false)) reminderMinutes(ap) else emptyList()
    val now = System.currentTimeMillis()
    val items = ArrayList<Triple<Long, String, String>>()
    if (offsets.isNotEmpty()) for (key in listOf("d", "d_biz")) {
        val o = JSONObject(getSharedPreferences("plans", Context.MODE_PRIVATE).getString(key, "{}") ?: "{}")
        for (ds in o.keys()) {
            val a = o.optJSONArray(ds) ?: continue
            for (j in 0 until a.length()) {
                val p = a.getJSONObject(j)
                val s = p.optString("s")
                if (s.length != 5) continue
                val start = try {
                    LocalDateTime.of(java.time.LocalDate.parse(ds), LocalTime.parse(s)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (e: Exception) { continue }
                for (m in offsets) {
                    val at = start - m * 60_000L
                    if (at > now) items.add(Triple(at, p.optString("t").ifEmpty { tr("Appointment") }, remBody(m, s)))
                }
            }
        }
    }
    items.sortBy { it.first }
    val list = items.take(150)
    list.forEachIndexed { i, (at, title, body) ->
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(i, title, body)) } catch (e: Exception) {}
    }
    ap.edit().putInt("rem_n", list.size).apply()

    // plans without any details (made by voice, no context): a reminder every hour, 08:00 – 21:00
    am.cancel(nagIntent())
    if (plansWithoutDetails().isNotEmpty()) {
        var t = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        if (t.hour < 8) t = t.withHour(8) else if (t.hour >= 21) t = t.plusDays(1).withHour(8)
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), nagIntent()) } catch (e: Exception) {}
    }
}

private fun Context.nagIntent(): PendingIntent {
    val it = Intent(this, ReminderReceiver::class.java).setData(Uri.parse("plan://nag")).putExtra("nag", true)
    return PendingIntent.getBroadcast(this, 99999, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

// start times of future plans (both calendars) that have no text yet
internal fun Context.plansWithoutDetails(): List<LocalDateTime> {
    val now = LocalDateTime.now(); val out = ArrayList<LocalDateTime>()
    for (key in listOf("d", "d_biz")) {
        val o = JSONObject(getSharedPreferences("plans", Context.MODE_PRIVATE).getString(key, "{}") ?: "{}")
        for (ds in o.keys()) {
            val a = o.optJSONArray(ds) ?: continue
            for (j in 0 until a.length()) {
                val p = a.getJSONObject(j)
                if (p.optString("t").isNotBlank() || p.optString("s").length != 5) continue
                val start = try { LocalDateTime.of(java.time.LocalDate.parse(ds), LocalTime.parse(p.optString("s"))) } catch (e: Exception) { continue }
                if (start.isAfter(now)) out.add(start)
            }
        }
    }
    return out.sorted()
}

private fun Context.showNote(id: Int, title: String, body: String) {
    makeChannel()
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
    b.setSmallIcon(R.drawable.ic_notification).setColor(0xFF111111.toInt())
        .setContentTitle(title).setContentText(body).setContentIntent(open).setAutoCancel(true)
    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, b.build())
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.getSharedPreferences("app", Context.MODE_PRIVATE).getString("lang", "en")?.let { lang = it }
        if (i.getBooleanExtra("nag", false)) {
            val list = c.plansWithoutDetails()
            if (list.isNotEmpty()) {
                val body = if (list.size == 1) list[0].format(DateTimeFormatter.ofPattern("d MMM · HH:mm", appLocale()))
                           else trf("%s appointments need details", list.size)
                c.showNote(5001, tr("Add details to your appointment"), body)
            }
            c.rescheduleReminders()      // sets the next hourly reminder
            return
        }
        c.showNote((System.currentTimeMillis() % 100000).toInt(), i.getStringExtra("title") ?: "", i.getStringExtra("body") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { c.rescheduleReminders() }
}
