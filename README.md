<div align="center">

# My Calendar

**A personal appointment and business management app built for my mom.**

An Android application that makes it easier to manage appointments, clients, and finances through a simple, user-friendly interface. It also supports optional voice-powered appointment creation using n8n and Google Gemini.

<img src="Docs/App%20Banner.png" alt="My Calendar app banner" width="850"/>

</div>

---

## Why I Built It

I built My Calendar to make my mom's daily work at her pedicure salon easier. Instead of switching between multiple applications, she can manage appointments, clients, and finances in one place.

The application is designed to be straightforward and easy to understand, even for users who are not particularly familiar with technology.

It supports manual appointment management and optional voice input. When the AI assistant is configured, spoken commands can be sent to an n8n workflow, where Google Gemini interprets the request and extracts the relevant appointment information.

This project demonstrates my ability to develop practical applications tailored to clients' needs, with a focus on usability, maintainable code, and solving real-world problems.

I also use the application personally, allowing me to test its features and continuously improve the user experience based on practical use.

## Features

| Feature | Description |
|---|---|
| Calendar Views | Navigate dates and view scheduled appointments. |
| Appointment Management | Create and manage appointments. |
| Voice Input | Record spoken appointment requests. |
| AI Voice Assistant | Process voice commands using n8n and Google Gemini. |
| Multilingual Input | Support voice commands in Russian, Dutch, and English through the AI workflow. |
| Client Management | Organize client information and appointment history. |
| Budget Tracking | Review recorded payments across different periods. |
| Notes | Store additional information in the application. |
| Reminders | Appointment reminder functionality. |
| Language Settings | Choose a supported application language. |
| Data Portability | Export and import application data using JSON. |
| Protected Finance Area | Restrict access to the budget area using an authentication mechanism. |
| AI Connection Settings | Configure the optional n8n assistant connection. |
| Research Tab | Present information about AI model selection, API limits, and model research. |

---

## Architecture

The project consists of two main components: the Android application and an optional n8n voice automation workflow.

```text
┌────────────────────────────────────┐
│        Android Application         │
│                                    │
│  Kotlin · Android Studio            │
│  Calendar · Clients · Budget        │
│  Notes · Settings · Reminders       │
│  Research · Voice Input              │
└─────────────────┬──────────────────┘
                  │
                  │ Audio + date/time context
                  ▼
┌────────────────────────────────────┐
│            n8n Workflow             │
│                                    │
│  Webhook                            │
│      ↓                             │
│  Prepare Request                    │
│      ↓                             │
│  Google Gemini                      │
│      ↓                             │
│  Resolve Date                       │
│      ↓                             │
│  Return Structured JSON             │
└─────────────────┬──────────────────┘
                  │
                  │ JSON response
                  ▼
┌────────────────────────────────────┐
│        Android Application         │
│                                    │
│  Validate response                  │
│  Prepare appointment                 │
│  Allow user to manage the result    │
└────────────────────────────────────┘
```

The AI workflow is optional. When it is not configured, the application can use the phone's built-in speech recognition as a fallback.

---

## Android Application

The application is developed in Kotlin using Android Studio.

The code is organized into separate areas for screens, shared utilities, data handling, and voice functionality to keep the project easier to maintain and extend.

| File or Package | Responsibility |
|---|---|
| `MainActivity.kt` | Main activity, application state, navigation, and voice recording state. |
| `screens/` | Calendar, clients, budget, settings, and other screens. |
| `core/` | Shared utilities, language support, and application helpers. |
| `data/` | Application data operations and related models. |
| `voice/` | Speech input, audio recording, and communication with n8n. |
| `ui/` | Reusable UI components and styling. |

### Calendar Navigation

The calendar rendering logic selects a layout based on the active view mode.

```kotlin
when (mode) {
    0 -> {
        page.addView(weekdayHeader())
        page.addView(monthGrid())
    }

    1 -> {
        page.addView(weekStrip(monday))
        page.addView(planList(cur))
    }

    3 -> {
        page.addView(weekPlansView(monday))
    }

    else -> {
        page.addView(planList(cur))
    }
}
```

This is an illustrative excerpt showing how different calendar views can be selected. Helper names and parameters should match the actual project implementation.

### Voice Assistant Connection

The application stores the assistant URL and optional token in local preferences.

```kotlin
internal fun MainActivity.assistantOn(): Boolean =
    getSharedPreferences("app", Context.MODE_PRIVATE)
        .getBoolean("assist_on", true)

internal fun MainActivity.voiceUrl(): String =
    if (!assistantOn()) {
        ""
    } else {
        getSharedPreferences("app", Context.MODE_PRIVATE)
            .getString("n8n_url", "")
            ?.trim()
            .orEmpty()
    }

internal fun MainActivity.voiceToken(): String =
    getSharedPreferences("app", Context.MODE_PRIVATE)
        .getString("n8n_token", "")
        ?.trim()
        .orEmpty()
```

These snippets illustrate the connection settings. Real credentials and webhook tokens should never be committed to a public repository.

---

## n8n Voice Assistant

The voice assistant connects the Android application to an n8n workflow and Google Gemini.

It is designed to interpret short appointment commands in Russian, Dutch, and English.

### Workflow Steps

1. **Webhook** — receives the request from the Android application.
2. **Prepare Request** — validates the incoming payload and prepares the model request.
3. **Gemini Processing** — sends audio and date/time context to Google Gemini.
4. **Date Resolution** — interprets the model response and calculates relative dates.
5. **Return Response** — sends structured JSON back to the Android application.

Relative dates such as "tomorrow" or "in six weeks" should be resolved using date calculations in the workflow and the date/time context supplied by the phone.

### Example Voice Command

> Book Anna tomorrow at 14:00.

The workflow interprets the command, identifies the appointment details, resolves the date, and returns a structured response.

### Example JSON Response

```json
{
  "ok": true,
  "date": "2026-10-30",
  "time": "14:00",
  "text": "Anna",
  "heard": "Book Anna tomorrow at 14:00."
}
```

| Field | Purpose |
|---|---|
| `ok` | Indicates whether the request was processed successfully. |
| `date` | Resolved date in `YYYY-MM-DD` format. |
| `time` | Appointment time in 24-hour `HH:MM` format. |
| `text` | Extracted client name or appointment text. |
| `heard` | Original recognized command for feedback or troubleshooting. |

The date above is an example. The workflow should calculate the actual date using the context supplied by the phone.

### Error Handling

If the request cannot be understood, the workflow can return a consistent error response.

```json
{
  "ok": false,
  "error": "Could not understand the appointment date."
}
```

Consistent response formats make it easier for the Android application to validate the result and handle errors.

### Example Gemini Structured Output

The AI can return a structured object describing the user's intent and date expression.

```json
{
  "intent": "create",
  "heard": "Book Anna tomorrow at 14:00.",
  "kind": "relative",
  "amount": 1,
  "unit": "days",
  "weekday": 0,
  "which": "this",
  "day": 0,
  "month": 0,
  "year": 0,
  "time": "14:00",
  "text": "Anna"
}
```

The workflow can validate these fields, resolve the date, and format the final response before sending it to the application.

---

## Research Tab

The Research tab explains the AI integration, model selection, and API usage limitations.

It also documents the research and testing performed to find a suitable Gemini model for this project.

### Gemini Model Research

I researched and tested multiple Google Gemini models to determine which one was best suited to the application's voice assistant.

The main goal was to find a model that could interpret appointment requests, extract relevant information, and return structured data while remaining practical for a personal application.

After researching and testing different models, I selected **Gemini 3.1 Flash Lite** as the model for this project.

The selection was based on its suitability for the application's requirements and the results of my practical testing.

This process helped me understand the importance of evaluating AI models based on their intended use, response quality, speed, and API limitations rather than selecting a model based on its name alone.

### Gemini API Rate Limits

The application uses the Google Gemini API on the free tier.

According to my Google AI Studio rate-limit dashboard, **Gemini 3.1 Flash Lite has a displayed limit of 500 requests per day (RPD)** for the selected project.

You can view the rate-limit dashboard here:

[Google AI Studio — Gemini API Rate Limits](https://aistudio.google.com/rate-limit?timeRange=last-28-days&project=gen-lang-client-0332428040)

The dashboard also displays other limits:

| Metric | Meaning |
|---|---|
| RPM | Requests per minute. |
| TPM | Tokens per minute. |
| RPD | Requests per day. |

These limits are separate. Staying below the daily request limit does not necessarily mean the application can exceed the per-minute or token limits.

**Important:** The 500 requests per day figure is the limit displayed for Gemini 3.1 Flash Lite in my selected Google AI Studio project. API quotas can vary by project, account, model, and Google policy, so the dashboard should be checked for the current limit.

### Handling API Limits

The Research tab provides information about the current model and its limitations.

If the application needs more requests, possible options include:

- Using a different model with suitable available quotas.
- Reviewing available paid API plans.
- Connecting an alternative AI provider.
- Optimizing the workflow to avoid unnecessary API calls.

Alternative providers and paid plans may offer higher quotas, but **unlimited requests cannot be guaranteed**. Actual usage depends on the provider's policies, pricing, account limits, and technical restrictions.

---

## Data and Privacy

- The application supports JSON export and import for application data.
- The voice assistant is optional.
- Without a configured assistant URL, the application can use the device's built-in speech recognition as a fallback.
- When the AI workflow is enabled, audio is sent to the configured n8n endpoint and then to Google Gemini for processing.
- Use HTTPS when accessing the n8n endpoint over an untrusted network.
- Never publish webhook secrets, API keys, access tokens, private client information, or exported personal data in the repository.

---

## Technologies Used

- **Kotlin** — Android application development.
- **Android Studio** — Android development environment.
- **n8n** — workflow automation and AI integration.
- **Google Gemini API** — voice-command interpretation and structured data extraction.
- **JSON** — communication between the application and the voice assistant.
- **Speech Recognition** — voice input and appointment creation.

---

## Project Goal

I achieved my original goal of creating an application that makes my mom's daily work at her pedicure salon easier. The app allows her to manage appointments, clients, and finances in one place through a simple and easy-to-understand interface.

This project demonstrates my ability to develop practical applications tailored to clients' needs, focusing on making software accessible and user-friendly.

I also use the application myself, allowing me to test its features, identify improvements, and continue developing it based on real-world experience.

What started as a request from my mom has become a practical project that demonstrates my ability to turn a real-world problem into a usable software solution.
