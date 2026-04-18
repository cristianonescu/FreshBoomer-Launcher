# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Additional Instructions & Context

Before starting work, also consult these files at the repository root:

- **`AI-INSTRUCTIONS.md`** — AI-assisted workflow rules and conventions that must be followed.
- **`AGENTS.md`** — guidance for AI agents working in this repo.
- **`TODO.md`** — current outstanding tasks and priorities.
- **`README.md`** — user-facing project description and setup.
- **`RELEASE_NOTES.md`** — version history and notable changes.

Treat `AI-INSTRUCTIONS.md` and `AGENTS.md` as authoritative alongside this file. If guidance conflicts, ask the user to clarify rather than guessing.

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

Requires **JDK 11** (for the Gradle daemon). Compile/Target SDK 36, Min SDK 31. The Kotlin/Java toolchain is pinned to 11 via `kotlin { jvmToolchain(11) }`, auto-provisioned by the foojay resolver. If Gradle is launched with JDK 20+ the `:app:test` task fails at configuration time (`Type T not present` inside `DefaultReportContainer`) — set `JAVA_HOME` to a JDK 11 or 17 install before running Gradle, or upgrade Gradle + AGP.

## Architecture

Simple activity-based architecture with Jetpack Compose UI. No DI framework, no navigation library — activities communicate via Intents.

### Activities

All activities extend `ImmersiveActivity` (a `ComponentActivity` subclass) which provides:
- Fullscreen immersive mode (system bars hidden)
- Text-to-speech in Romanian (speech rate 0.85)
- Phone call initiation with automatic speaker routing and max volume
- Inactivity timeout (configurable, default 20s) that auto-navigates back to `MainActivity`
- Missed call detection with TTS announcements — decision logic extracted into pure `MissedCallAnnouncer` (max-N-times-per-unique-number rule), persisted via `SharedPreferences` (`LauncherPrefs`)
- WhatsApp secret unlock (60 taps in 15 seconds on the clock)
- Hidden settings unlock (5 taps in 3 seconds on the footer Lock icon in `TtsStatusFooter`)

| Activity | Role |
|---|---|
| `MainActivity` | Home screen — analog clock + date, quick contacts grid (photos when configured, gradient-avatar fallback), missed-call banner, utility buttons, optional WhatsApp / Gallery / admin-lock footer |
| `PhoneActivity` | Dial pad — registered as default phone app (`ACTION_DIAL`). Glass keys, digits 0–9 (no `*`/`#`) |
| `ContactsActivity` | Contact list with add/edit/delete — reads device contacts |
| `SmsActivity` | SMS conversations — registered as default SMS app (`SMS_DELIVER`) |
| `IncomingCallActivity` / `InCallActivity` | Incoming-ring + in-call UI (via `CallService` InCallService) |
| `WhatsAppCallActivity` | Simplified answer/reject UI for WhatsApp calls, triggered by `WhatsAppCallListenerService` (NotificationListener) |
| `GalleryActivity` | Device-photo gallery viewer (MediaStore) |
| `MedicationAlertActivity` | Medication reminder alert (scheduled by `MedicationReminderScheduler`) |
| `TtsSmsAlertActivity` | TTS playback alert for specially-prefixed incoming SMS |

### Default App Roles

The app registers as the device's **home launcher** (HOME category), **default phone** (DIAL handler), and **default SMS app** (SMS_DELIVER receiver + MmsService + HeadlessSmsSendService stubs).

### State Management

- UI state: Compose `mutableStateOf` / `remember`.
- Persistence:
  - `AppConfig` + `config.json` (JSON file in app's private storage) — main config (theme mode, language, feature toggles, quick contacts, emergency contacts, medication reminders, inactivity threshold, TTS choice, etc.). Supports import from a URL.
  - `QuickContactRepository` / `quick_contacts.json` — quick contacts with photo URIs.
  - `SharedPreferences` — misc flags: `LauncherPrefs` (missed-call announcement counter), `MissedCallPrefs` (most recent missed call), `MedicationSnoozePrefs` (snooze counters), etc.
- No ViewModel layer, no DI, no Room, no Retrofit — minimal dependencies by design.

### UI visual system (v2 "glass")

Three shared primitives in `ui/composables/` compose the prototype's glass look without device-specific shadow/blur:

- `GlassBackground` — wraps each activity root; two radial accent halos fade into `colorScheme.background`.
- `GlassButton` — translucent surface + top highlight gradient + 1dp accent border. Used for neutral/secondary actions.
- `AccentGlowButton` — solid accent fill + top highlight gradient + subtle bottom bevel. Used for primary positive actions. Pass `color = Color(0xFFD32F2F)` etc. for destructive variants.
- `GradientAvatar` — circular 2-color gradient with a single initial. Used as fallback when a contact has no photo.

All buttons use `FontWeight.ExtraBold` labels (800 weight), 18dp corner radius, and ripple with the accent color. No `Modifier.blur` and no `Modifier.shadow` with colored `ambientColor`/`spotColor` — both render inconsistently across OEM devices; pure Compose brushes + borders replace them.

### Source Layout

```
app/src/main/java/ro/softwarechef/freshboomer/
├── MainActivity.kt, PhoneActivity.kt, ContactsActivity.kt, SmsActivity.kt
├── IncomingCallActivity.kt, InCallActivity.kt, WhatsAppCallActivity.kt
├── GalleryActivity.kt, MedicationAlertActivity.kt, TtsSmsAlertActivity.kt
├── call/          CallManager
├── data/          AppConfig, ConfigData, LocaleHelper, MissedCallAnnouncer,
│                  MissedCallStore, NicknamePreference, QuickContactRepository,
│                  ThemePreference, TtsPreference, FeatureTogglePreference,
│                  InactivityTracker, etc.
├── models/        Contact, QuickContact
├── onboarding/    OnboardingScreen, OnboardingChecker, SetupWizardScreen
├── receivers/     SmsReceiver, MmsReceiver, PhoneCallReceiver,
│                  MedicationReminderReceiver, BootReceiver
├── services/      CallService (InCallService), InactivityMonitorWorker,
│                  MedicationReminderScheduler, WhatsAppCallListenerService,
│                  MmsService, HeadlessSmsSendService
├── tts/           TtsModelManager, TtsModelDownloadDialog (+ SettingsUnlockIcon),
│                  PiperTtsEngine, PiperVoice
└── ui/
    ├── composables/  ImmersiveActivity, HideSystemBars, Inapoi,
    │                 GlassBackground, GlassButton (+ AccentGlowButton),
    │                 GradientAvatar, ConfirmCallDialog (glass Dialog),
    │                 QuickContactEditor, QuickContactSettingsScreen,
    │                 JsonConfigEditorScreen, ConfigEditorHint
    └── theme/        Color, Theme, Type (large fonts 24–40sp for accessibility)
```

Unit tests live in `app/src/test/java/…` (`MissedCallAnnouncerTest`, `MedicationReminderSchedulerTest` — `nowMs` injection + pinned UTC TZ for determinism).

## Key Tech Stack

Kotlin 2.3.x, Jetpack Compose (Material3, Compose BOM), AGP 8.11.x, Gradle 9.4.x, Coil (photo loading), WorkManager (inactivity monitor). No Hilt, no Retrofit, no Room — minimal dependencies by design.

## Important Behaviors

- **All UI text is in Romanian** by default; English translations live in `values-en/`. Never translate UI copy in `values/` to English "for clarity". Preserve diacritics (ă, â, î, ș, ț).
- **Quick contacts are user-configurable** via the setup wizard, Settings screen, or by importing a `config.json` from a URL. They are no longer hardcoded.
- **Calls auto-route to speaker** with a 3-second delay after dialing, all audio streams set to max volume.
- **Portrait-only, singleTask launch mode** for `MainActivity`.
- **Hidden escape hatches**: 5 taps on the footer `Lock` icon → Settings; 60 taps on the clock within 15s → WhatsApp.
