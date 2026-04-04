# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FreshBoomer is a single-module Android app that acts as a simplified home launcher for elderly users. It replaces the default launcher, phone, and SMS apps with a large-button, Romanian-language interface featuring text-to-speech accessibility.

**Package:** `ro.softwarechef.freshboomer`

## Build Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:test

# Run instrumented tests (requires device/emulator)
./gradlew :app:connectedAndroidTest
```

Requires **JDK 11**. Compile/Target SDK 35, Min SDK 31.

## Architecture

Simple activity-based architecture with Jetpack Compose UI. No DI framework, no navigation library — activities communicate via Intents.

### Activities

All activities extend `ImmersiveActivity` (a `ComponentActivity` subclass) which provides:
- Fullscreen immersive mode (system bars hidden)
- Text-to-speech in Romanian (speech rate 0.85)
- Phone call initiation with automatic speaker routing and max volume
- Inactivity timeout (20s) that auto-navigates back to `MainActivity`
- Missed call detection with TTS announcements (max 3 per unique number, persisted via SharedPreferences)
- WhatsApp secret unlock (60 taps in 15 seconds)

| Activity | Role |
|---|---|
| `MainActivity` | Home screen — clock, battery, quick-dial contacts (hardcoded), missed call banner, navigation buttons |
| `PhoneActivity` | Dial pad — registered as default phone app (`ACTION_DIAL`) |
| `ContactsActivity` | Contact list with add/edit/delete — reads device contacts |
| `SmsActivity` | SMS conversations — registered as default SMS app (`SMS_DELIVER`) |

### Default App Roles

The app registers as the device's **home launcher** (HOME category), **default phone** (DIAL handler), and **default SMS app** (SMS_DELIVER receiver + MmsService + HeadlessSmsSendService stubs).

### State Management

- UI state: Compose `mutableStateOf` / `remember`
- Persistence: `SharedPreferences` ("ElderAppPrefs") for call announcement tracking
- No ViewModel layer — state lives directly in activities and composables

### Source Layout

```
app/src/main/java/ro/softwarechef/freshboomer/
├── MainActivity.kt, ContactsActivity.kt, PhoneActivity.kt, SmsActivity.kt
├── models/Contact.kt
├── receivers/SmsReceiver.kt
├── services/HeadlessSmsSendService.kt, MmsService.kt
└── ui/
    ├── composables/  (ConfirmCallDialog, HideSystemBars, ImmersiveActivity, Inapoi)
    └── theme/        (Color, Theme, Type — large fonts 24-40sp for accessibility)
```

## Key Tech Stack

Kotlin, Jetpack Compose (Material3), AndroidX Activity Compose, AndroidX AppCompat. No Hilt, no Retrofit, no Room — minimal dependencies by design.

## Important Behaviors

- **All UI text is in Romanian** — TTS announcements, button labels, toasts.
- **Hardcoded quick contacts** in MainActivity (Gigi, Lili, Viorica, etc.) — these are specific to the target user.
- **Calls auto-route to speaker** with a 3-second delay after dialing, all audio streams set to max volume.
- **Portrait-only, singleTask launch mode** for MainActivity.
