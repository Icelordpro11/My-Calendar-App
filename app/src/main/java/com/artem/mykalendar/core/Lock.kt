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
import org.json.JSONArray
import org.json.JSONObject

// Budget is protected by the phone's own lock: fingerprint, or the phone PIN / pattern.
internal fun MainActivity.requireUnlock(onOk: () -> Unit) {
    if (unlocked) { onOk(); return }
    val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (!km.isDeviceSecure) {
        Toast.makeText(this, tr("Set a screen lock (PIN or fingerprint) on your phone first"), Toast.LENGTH_LONG).show()
        return
    }
    if (Build.VERSION.SDK_INT >= 30) {
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(tr("Unlock Budget"))
                .setSubtitle(tr("Use your fingerprint or phone PIN"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; onOk() }
            })
            return
        } catch (e: Exception) { /* fall back to the phone PIN screen below */ }
    }
    @Suppress("DEPRECATION")
    val intent = km.createConfirmDeviceCredentialIntent(tr("Unlock Budget"), tr("Use your fingerprint or phone PIN"))
    if (intent != null) { pendingUnlock = onOk; keyguardLauncher.launch(intent) }
}
