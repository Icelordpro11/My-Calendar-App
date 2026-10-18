# n8n Calendar Voice Assistant

An n8n workflow that connects the My Calendar Android application with Google Gemini to process voice commands and extract appointment details.

## Overview

The Calendar Voice Assistant allows the Android application to send recorded audio to an n8n webhook. Google Gemini processes the audio and extracts structured appointment information, including the date, time, person's name, and original transcript.

The workflow supports **Russian, English, and Dutch** voice commands.

## Workflow Architecture

```text
Android Calendar App
        |
        | HTTP POST (Audio)
        v
+----------------------+
| Webhook              |
| calendar-voice       |
+----------------------+
        |
        v
+----------------------+
| Prepare Request      |
| Build Gemini Prompt  |
+----------------------+
        |
        v
+----------------------+
| Gemini Listens       |
| Google Gemini API    |
+----------------------+
        |
        v
+----------------------+
| Find the Date        |
| Resolve Date & Data  |
+----------------------+
        |
        v
+----------------------+
| Answer the App       |
| Return JSON Response |
+----------------------+
        |
        v
Android Calendar App
```

## Features

- **Voice recognition:** Processes recorded audio using Google Gemini.
- **Multilingual input:** Supports Russian, English, and Dutch.
- **Appointment intent detection:** Determines whether the user wants to create an appointment.
- **Relative dates:** Understands phrases such as "tomorrow," "in six weeks," and "over two months."
- **Weekday recognition:** Handles instructions such as "next Monday" and "on Friday."
- **Absolute dates:** Extracts dates such as "18 November" or "5 January."
- **Time extraction:** Returns appointment times in 24-hour `HH:MM` format.
- **Name extraction:** Extracts the first name or first and last name of the person associated with the appointment.
- **Structured responses:** Returns JSON for the Android application to process.

## Technology Stack

| Technology | Purpose |
|---|---|
| n8n | Workflow automation |
| Google Gemini API | Audio understanding and structured data extraction |
| JavaScript | Request preparation and date calculations |
| HTTP Webhook | Communication with the Android application |
| JSON | Request and response format |
| Kotlin / Android | Client application |

## Workflow Nodes

### 1. Webhook

Receives HTTP `POST` requests at the `calendar-voice` endpoint.

The request body is expected to contain audio data and contextual information, including the current date, local time, weekday, and time zone.

### 2. Prepare Request

Prepares the request for Gemini.

It includes the voice-processing instructions, expected JSON schema, audio data, and date context supplied by the Android application.

The model is configured as `gemini-3.1-flash-lite`.

An optional `x-token` header check is available through the `SecretCode` setting in the JavaScript code.

### 3. Gemini Listens

Sends the prepared request to the Google Gemini API using an n8n Google Gemini credential.

The request asks Gemini to return structured JSON rather than free-form text.

### 4. Find the Date

Processes Gemini's response and calculates the appointment date in JavaScript.

It supports relative dates, weekdays, and absolute dates. Month and year calculations include safeguards for dates near the end of a month.

The node returns the appointment date, time, person's name, transcript, and success status.

### 5. Answer the App

Returns the final JSON response to the original webhook request.

## Example Response

A successful response follows this structure:

```json
{
  "ok": true,
  "date": "2026-11-18",
  "time": "14:00",
  "text": "Anna Smith",
  "heard": "Schedule Anna Smith for November 18 at 2 PM."
}
```

This is an illustrative example of the response format, not a recorded execution result.

If processing fails, the workflow can return an error response containing `ok: false`, a message, and optionally the recognized transcript.

