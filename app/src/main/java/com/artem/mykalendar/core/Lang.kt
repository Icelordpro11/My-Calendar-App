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
import org.json.JSONArray
import org.json.JSONObject

internal var lang = "en"
internal fun appLocale(): Locale = Locale.forLanguageTag(lang)
private val T: Map<String, Array<String>> = mapOf(
    "Delete?" to arrayOf("Verwijderen?", "Удалить?"),
    "Business calendar" to arrayOf("Zakelijke agenda", "Рабочий календарь"),
    "Personal calendar" to arrayOf("Persoonlijke agenda", "Личный календарь"),
    "Today" to arrayOf("Vandaag", "Сегодня"),
    "Export data (JSON)" to arrayOf("Gegevens exporteren (JSON)", "Экспорт данных (JSON)"),
    "Import data" to arrayOf("Gegevens importeren", "Импорт данных"),
    "Month" to arrayOf("Maand", "Месяц"), "Week" to arrayOf("Week", "Неделя"),
    "Day" to arrayOf("Dag", "День"), "Year" to arrayOf("Jaar", "Год"),
    "Calendar" to arrayOf("Agenda", "Календарь"), "Clients" to arrayOf("Klanten", "Клиенты"),
    "Budget" to arrayOf("Budget", "Бюджет"), "Notes" to arrayOf("Notities", "Заметки"),
    "Settings" to arrayOf("Instellingen", "Настройки"),
    "Language" to arrayOf("Taal", "Язык"), "Data" to arrayOf("Gegevens", "Данные"),
    "Speech input not available" to arrayOf("Spraakinvoer niet beschikbaar", "Голосовой ввод недоступен"),
    "Amount" to arrayOf("Bedrag", "Сумма"),
    "Edit plan" to arrayOf("Afspraak bewerken", "Изменить запись"),
    "Color & amount" to arrayOf("Kleur & bedrag", "Цвет и сумма"),
    "Delete" to arrayOf("Verwijderen", "Удалить"),
    "Delete this plan?" to arrayOf("Deze afspraak verwijderen?", "Удалить эту запись?"),
    "Yes" to arrayOf("Ja", "Да"), "No" to arrayOf("Nee", "Нет"),
    "No plans" to arrayOf("Geen afspraken", "Нет записей"),
    "New plan" to arrayOf("Nieuwe afspraak", "Новая запись"),
    "Write your plan…" to arrayOf("Schrijf je afspraak…", "Напишите запись…"),
    "Cancel" to arrayOf("Annuleren", "Отмена"), "Save" to arrayOf("Opslaan", "Сохранить"),
    "Close" to arrayOf("Sluiten", "Закрыть"), "Add" to arrayOf("Toevoegen", "Добавить"),
    "Client name" to arrayOf("Naam klant", "Имя клиента"), "Write here" to arrayOf("Schrijf hier", "Пишите здесь"),
    "All" to arrayOf("Alles", "Все"), "Favorites" to arrayOf("Favorieten", "Избранные"),
    "History" to arrayOf("Geschiedenis", "История"),
    "Search clients" to arrayOf("Klanten zoeken", "Поиск клиентов"),
    "No visits found" to arrayOf("Geen bezoeken gevonden", "Визиты не найдены"),
    "No favorites yet — tap a star" to arrayOf("Nog geen favorieten — tik op een ster", "Пока нет избранных — нажмите на звезду"),
    "No clients found" to arrayOf("Geen klanten gevonden", "Клиенты не найдены"),
    "No visits yet" to arrayOf("Nog geen bezoeken", "Пока нет визитов"),
    "Edit client" to arrayOf("Klant bewerken", "Изменить клиента"),
    "Name" to arrayOf("Naam", "Имя"), "Info" to arrayOf("Info", "Инфо"),
    "Phone, address, anything you want to remember…" to arrayOf("Telefoon, adres, alles wat je wilt onthouden…", "Телефон, адрес, всё, что хотите запомнить…"),
    "No info yet" to arrayOf("Nog geen info", "Пока нет информации"),
    "No income in this period" to arrayOf("Geen inkomsten in deze periode", "Нет доходов за этот период"),
    "Total" to arrayOf("Totaal", "Итого"), "Date" to arrayOf("Datum", "Дата"),
    "Goal for %s" to arrayOf("Doel voor %s", "Цель на %s"),
    "Dashed line = monthly target (goal ÷ 12)" to arrayOf("Streeplijn = maanddoel (doel ÷ 12)", "Пунктир = цель на месяц (цель ÷ 12)"),
    "Earned %s of %s  ·  %s%%" to arrayOf("Verdiend %s van %s  ·  %s%%", "Заработано %s из %s  ·  %s%%"),
    "Earned %s this year" to arrayOf("Dit jaar verdiend %s", "Заработано за год %s"),
    "Goal reached" to arrayOf("Doel bereikt", "Цель достигнута"),
    "Left to earn  %s" to arrayOf("Nog te verdienen  %s", "Осталось заработать  %s"),
    "Fill in a goal to follow your progress" to arrayOf("Vul een doel in om je voortgang te volgen", "Укажите цель, чтобы следить за прогрессом"),
    "Save everything to a file" to arrayOf("Alles in een bestand opslaan", "Сохранить всё в файл"),
    "Load everything from a file" to arrayOf("Alles uit een bestand laden", "Загрузить всё из файла"),
    "No notes yet" to arrayOf("Nog geen notities", "Пока нет заметок"),
    "Write a note…" to arrayOf("Schrijf een notitie…", "Напишите заметку…"),
    "Unlock Budget" to arrayOf("Budget ontgrendelen", "Разблокировать бюджет"),
    "Use your fingerprint or phone PIN" to arrayOf("Gebruik je vingerafdruk of pincode", "Используйте отпечаток пальца или PIN-код"),
    "Set a screen lock (PIN or fingerprint) on your phone first" to arrayOf("Stel eerst een schermvergrendeling (pincode of vingerafdruk) in op je telefoon", "Сначала установите блокировку экрана (PIN или отпечаток) на телефоне"),
    "Delete client" to arrayOf("Klant verwijderen", "Удалить клиента"),
    "Delete this client?" to arrayOf("Deze klant verwijderen?", "Удалить этого клиента?"),
    "Old plans stay in the calendar" to arrayOf("Oude afspraken blijven in de agenda", "Старые записи остаются в календаре"),
    "Reminders" to arrayOf("Herinneringen", "Напоминания"),
    "1 day before" to arrayOf("1 dag van tevoren", "За 1 день"),
    "1 hour before" to arrayOf("1 uur van tevoren", "За 1 час"),
    "Get a notification before a plan" to arrayOf("Ontvang een melding vóór een afspraak", "Уведомление перед записью"),
    "Tomorrow" to arrayOf("Morgen", "Завтра"),
    "In 1 hour" to arrayOf("Over 1 uur", "Через 1 час"),
    "At the time" to arrayOf("Op het moment zelf", "В момент начала"),
    "Add reminder" to arrayOf("Herinnering toevoegen", "Добавить напоминание"),
    "Hold the button and speak" to arrayOf("Houd de knop ingedrukt en praat", "Удерживайте кнопку и говорите"),
    "Listening… release to send" to arrayOf("Luisteren… laat los om te versturen", "Слушаю… отпустите, чтобы отправить"),
    "Thinking…" to arrayOf("Bezig…", "Думаю…"),
    "Allow the microphone, then hold the button again" to arrayOf("Sta de microfoon toe en houd de knop opnieuw ingedrukt", "Разрешите микрофон и снова удерживайте кнопку"),
    "Could not reach the assistant" to arrayOf("Assistent niet bereikbaar", "Ассистент недоступен"),
    "I did not understand that" to arrayOf("Dat heb ik niet begrepen", "Не удалось понять"),
    "Already booked at %s" to arrayOf("Al bezet om %s", "Время %s уже занято"),
    "Choose a different time" to arrayOf("Kies een andere tijd", "Выберите другое время"),
    "Choose a time" to arrayOf("Kies een tijd", "Выберите время"),
    "Added %s" to arrayOf("Toegevoegd: %s", "Добавлено: %s"),
    "No details yet" to arrayOf("Nog geen details", "Пока без деталей"),
    "Appointment" to arrayOf("Afspraak", "Запись"),
    "Add details to your appointment" to arrayOf("Voeg details toe aan je afspraak", "Добавьте детали к записи"),
    "%s appointments need details" to arrayOf("%s afspraken wachten op details", "Записей без деталей: %s"),
    "Voice assistant" to arrayOf("Spraakassistent", "Голосовой ассистент"),
    "Hold the mic button and speak" to arrayOf("Houd de microfoonknop ingedrukt en praat", "Удерживайте кнопку микрофона и говорите"),
    "Assistant link (n8n)" to arrayOf("Assistent-link (n8n)", "Ссылка ассистента (n8n)"),
    "Secret code (optional)" to arrayOf("Geheime code (optioneel)", "Секретный код (необязательно)"),
    "Assistant not reachable — check the tunnel and n8n" to arrayOf("Assistent niet bereikbaar — controleer de tunnel en n8n", "Ассистент недоступен — проверьте туннель и n8n"),
    "Connected" to arrayOf("Verbonden", "Подключено"),
    "Not reachable" to arrayOf("Niet bereikbaar", "Недоступно"),
    "Checking…" to arrayOf("Controleren…", "Проверка…"),
    "Other time" to arrayOf("Andere tijd", "Другое время"),
    "Saved to file" to arrayOf("Opgeslagen in bestand", "Сохранено в файл"),
    "Could not save the file" to arrayOf("Kon het bestand niet opslaan", "Не удалось сохранить файл"),
    "Replace ALL data with this file?" to arrayOf("Alle gegevens vervangen door dit bestand?", "Заменить ВСЕ данные этим файлом?"),
    "Imported" to arrayOf("Geïmporteerd", "Импортировано"),
    "Import failed" to arrayOf("Importeren mislukt", "Ошибка импорта"),
    "That is not a calendar file" to arrayOf("Dit is geen agendabestand", "Это не файл календаря")
)
internal fun tr(s: String): String = if (lang == "en") s else (T[s]?.get(if (lang == "nl") 0 else 1) ?: s)
internal fun trf(s: String, vararg a: Any): String = String.format(tr(s), *a)
internal fun visitsText(n: Int): String = when (lang) {
    "nl" -> "$n " + (if (n == 1) "bezoek" else "bezoeken")
    "ru" -> "$n " + (if (n % 10 == 1 && n % 100 != 11) "визит" else if (n % 10 in 2..4 && n % 100 !in 12..14) "визита" else "визитов")
    else -> "$n visit" + (if (n > 1) "s" else "")
}
