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
import android.text.style.ForegroundColorSpan
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

// Hold the mic button and speak. The recording goes to your n8n workflow (Gemini),
// which answers with the date, time and text. The app then adds the plan itself.
internal fun MainActivity.assistantOn(): Boolean = getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("assist_on", true)
// switched off = no link: the mic then uses the phone's own speech recognizer
internal fun MainActivity.voiceUrl(): String = if (!assistantOn()) "" else getSharedPreferences("app", Context.MODE_PRIVATE).getString("n8n_url", "")?.trim().orEmpty()
internal fun MainActivity.voiceToken(): String = getSharedPreferences("app", Context.MODE_PRIVATE).getString("n8n_token", "")?.trim().orEmpty()

// message bar at the top of the screen (dark = ok, red = problem)
internal fun MainActivity.say(t: String, error: Boolean = false) {
    noteBar.removeCallbacks(hideBar)
    noteBar.text = t
    noteBar.background = shape(if (error) 0xFFD32F2F.toInt() else 0xEE111111.toInt(), 0, 0, 22)
    noteBar.visibility = View.VISIBLE
    noteBar.postDelayed(hideBar, 3500)
}

// without an assistant link the mic works like before (phone speech recognizer)
internal fun MainActivity.speechFallback() {
    try {
        speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, appLocale().toLanguageTag()))
    } catch (e: Exception) { say(tr("Speech input not available"), true) }
}

// ---------- is the assistant reachable?  (mic turns red when it is not) ----------
// asks <scheme>://<host:port>/healthz of the n8n server; repeats every 30 s while the app is open
internal fun MainActivity.checkAssistant() {
    window.decorView.removeCallbacks(checkRunnable)
    val link = voiceUrl()
    if (link.isEmpty()) { assistantState = 0; styleMic(); return }
    Thread {
        val ok = try {
            val u = URL(link)
            val port = if (u.port > 0) ":" + u.port else ""
            val c = URL(u.protocol + "://" + u.host + port + "/healthz").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000
            val code = c.responseCode
            c.disconnect()
            code in 200..399
        } catch (e: Exception) { false }
        runOnUiThread {
            // ignore the answer if the link was changed while we were asking
            if (link == voiceUrl()) {
                assistantState = if (ok) 1 else 2
                styleMic()
                if (watching) window.decorView.postDelayed(checkRunnable, 30000)
            }
        }
    }.start()
}

// mic button: white = fine / not set up, red = assistant cannot be reached; plus the status line in Settings
internal fun MainActivity.styleMic() {
    val bad = assistantState == 2
    micBtn?.apply {
        background = shape(if (bad) 0xFFD32F2F.toInt() else Color.WHITE, 0, 0, 16)
        setColorFilter(if (bad) Color.WHITE else Color.BLACK)
    }
    voiceStatusView?.apply {
        when (assistantState) {
            1 -> { text = "●  " + tr("Connected"); setTextColor(0xFF2E7D32.toInt()) }
            2 -> { text = "●  " + tr("Not reachable"); setTextColor(0xFFD32F2F.toInt()) }
            else -> text = ""
        }
    }
}

internal fun MainActivity.attachVoice(v: View) {
    var fallback = false
    v.setOnTouchListener { view, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                fallback = voiceUrl().isEmpty()
                if (!fallback) {
                    if (assistantState == 2) {                 // red mic: cannot be used right now
                        say(tr("Assistant not reachable — check the tunnel and n8n"), true); checkAssistant()
                    } else startVoice()
                }
            }
            MotionEvent.ACTION_UP -> if (fallback) speechFallback() else stopVoice(true)
            MotionEvent.ACTION_CANCEL -> if (!fallback) stopVoice(false)
        }
        true
    }
}

private fun MainActivity.startVoice() {
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8)
        say(tr("Allow the microphone, then hold the button again"), true); return
    }
    try {
        val f = File(cacheDir, "voice.aac")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1); r.setAudioSamplingRate(16000); r.setAudioEncodingBitRate(32000)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        recorder = r; recFile = f; recStart = SystemClock.elapsedRealtime()
        voiceBar.visibility = View.VISIBLE
        micBtn?.animate()?.scaleX(1.35f)?.scaleY(1.35f)?.setDuration(150)?.start()
        tickVoice()
    } catch (e: Exception) {
        recorder = null; say(tr("Speech input not available"), true)
    }
}

private fun MainActivity.tickVoice() {
    if (recorder == null) return
    val ms = SystemClock.elapsedRealtime() - recStart
    val dot = if ((ms / 500) % 2 == 0L) "●" else "○"
    val sp = SpannableString("$dot  " + tr("Listening… release to send") + "    " + "%d:%02d".format(ms / 60000, ms / 1000 % 60))
    sp.setSpan(ForegroundColorSpan(0xFFFF5252.toInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    voiceBar.text = sp
    voiceBar.postDelayed({ tickVoice() }, 400)
}

private fun MainActivity.stopVoice(send: Boolean) {
    val r = recorder ?: return
    recorder = null
    micBtn?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
    voiceBar.visibility = View.GONE
    val ms = SystemClock.elapsedRealtime() - recStart
    try { r.stop() } catch (e: Exception) { /* too short */ }
    r.release()
    val f = recFile ?: return
    if (!send) return
    if (ms < 700) { say(tr("Hold the button and speak")); return }
    sendVoice(f)
}

private fun MainActivity.sendVoice(f: File) {
    voiceBar.text = tr("Thinking…"); voiceBar.visibility = View.VISIBLE
    val url = voiceUrl(); val token = voiceToken()
    val now = LocalDate.now()
    val body = JSONObject()
        .put("audio", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)).put("mime", "audio/aac")
        .put("today", now.toString()).put("weekday", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        .put("time_now", java.time.LocalTime.now().withSecond(0).withNano(0).toString())
        .put("tz", java.time.ZoneId.systemDefault().id)
    Thread {
        var result: String? = null; var code = 0
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "application/json")
            if (token.isNotEmpty()) c.setRequestProperty("X-Token", token)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            code = c.responseCode
            result = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) { result = null }
        runOnUiThread {
            voiceBar.visibility = View.GONE
            assistantState = if (result != null || code != 0) 1 else 2      // no answer at all = not reachable
            styleMic()
            handleVoice(result, code)
        }
    }.start()
}

// answer of the workflow: { ok, date, time, text, heard, message }
private fun MainActivity.handleVoice(text: String?, code: Int) {
    val o = try { JSONObject(text ?: "") } catch (e: Exception) { null }
    if (o == null || code !in 200..299) { say(tr("Could not reach the assistant"), true); return }
    if (!o.optBoolean("ok", false)) { say(o.optString("message").ifEmpty { tr("I did not understand that") }, true); return }
    val d = try { LocalDate.parse(o.getString("date")) } catch (e: Exception) { say(tr("I did not understand that"), true); return }
    val tm = fixTime(o.optString("time")).let { if (validTime(it)) it else "" }
    val txt = o.optString("text").trim()

    addSpokenPlan(d, tm, txt)
}

// 1. the calendar jumps to the date  2. a moment later the plan appears  (no popup)
internal fun MainActivity.addSpokenPlan(d: LocalDate, tm: String, txt: String) {
    cur = d; hideOverlay(); render()
    window.decorView.postDelayed({
        val clash = if (tm.length >= 2) plans(d).firstOrNull { it[1].length >= 2 && it[1].substring(0, 2) == tm.substring(0, 2) } else null
        if (clash != null) { bookedDialog(d, txt, clash); return@postDelayed }
        placePlan(d, tm, txt)
    }, 600)
}

internal fun MainActivity.placePlan(d: LocalDate, tm: String, txt: String) {
    val list = plans(d).toMutableList(); list.add(arrayOf(txt, tm, "", "", "")); save(d, list); render()
    val day = d.format(DateTimeFormatter.ofPattern("EEE d MMM", appLocale()))
    say(trf("Added %s", day + (if (tm.isEmpty()) "" else "  ·  $tm") + (if (txt.isEmpty()) "" else "  ·  $txt")))
}

// text from the phone's own speech recognizer (no assistant link): same parsing, done on the phone
internal fun MainActivity.handleSpokenText(t: String) {
    val p = parseSpoken(t)
    val d = p.date
    if (d == null) { planPopup(cur, null, t, ""); return }
    addSpokenPlan(d, p.time, p.text)
}

// "11:00 is booked": a card ABOVE the plans with the free hours as big buttons (one-hand reach)
private fun MainActivity.bookedDialog(d: LocalDate, txt: String, clash: Array<String>) {
    val taken = plans(d).filter { it[1].length >= 2 }.map { it[1].substring(0, 2) }.toSet()
    val wanted = clash[1].substring(0, 2).toIntOrNull() ?: 12
    val hours = (8..20).toList()
    val nearest = hours.filter { "%02d".format(it) !in taken }.minByOrNull { abs(it - wanted) }

    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; isClickable = true; elevation = dp(14).toFloat()
        background = shape(Color.WHITE, 0, 0, 24); setPadding(dp(16), dp(16), dp(16), dp(12))
    }
    card.addView(tv(trf("Already booked at %s", clash[1]), 18f, true).apply { setPadding(dp(4), 0, dp(4), 0) })
    card.addView(tv(clash[0].ifEmpty { tr("No details yet") }, 14f, false, 0xFF777777.toInt()).apply { setPadding(dp(4), 0, dp(4), 0) })
    card.addView(tv(tr("Choose a different time"), 15f).apply { setPadding(dp(4), dp(10), dp(4), dp(6)) })

    hours.chunked(4).forEach { rowHours ->
        val row = LinearLayout(this)
        for (h in rowHours) {
            val tm = "%02d:00".format(h)
            val free = "%02d".format(h) !in taken
            val best = h == nearest
            val chip = tv(tm, 17f, best, if (!free) 0xFFB5B5B5.toInt() else if (best) Color.WHITE else Color.BLACK).apply {
                gravity = Gravity.CENTER
                background = shape(if (!free) 0xFFF1F1F1.toInt() else if (best) Color.BLACK else Color.WHITE, 0xFFBBBBBB.toInt(), if (free && !best) 1 else 0, 14)
                if (free) setOnClickListener { dropLayer.visibility = View.GONE; placePlan(d, tm, txt) }
            }
            row.addView(chip, lp(0, dp(50)).apply { weight = 1f; setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        repeat(4 - rowHours.size) { row.addView(View(this), lp(0, dp(50)).apply { weight = 1f; setMargins(dp(3), dp(3), dp(3), dp(3)) }) }
        card.addView(row)
    }
    val b = LinearLayout(this).apply { gravity = Gravity.END }
    b.addView(btn(tr("Cancel"), false) { dropLayer.visibility = View.GONE }, lp(-2, -2).apply { marginEnd = dp(8) })
    b.addView(btn(tr("Edit plan"), false) { dropLayer.visibility = View.GONE; planPopup(d, null, txt, "") })
    card.addView(b, lp(-1, -2).apply { topMargin = dp(8) })

    dropLayer.removeAllViews()
    dropLayer.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), sheetHolder.height + dp(8)) })
    dropLayer.visibility = View.VISIBLE
}
