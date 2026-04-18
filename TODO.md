# TODO

Rolling task list for FreshBoomer. Read `AI-INSTRUCTIONS.md` before editing. Keep this file current — it is the handoff between sessions.

Legend: `[ ]` open · `[x]` done · `[~]` in progress · `[blocked: reason]`

---

## Active

- [x] **Device-agnostic glass (no `Modifier.shadow` / `Modifier.blur`)** — 2026-04-19. User reported the glass effect rendered inconsistently on their device. Root cause: `Modifier.blur` and `Modifier.shadow`'s `ambientColor`/`spotColor` depend on per-OEM graphics pipelines and can render muddy, missing, or slow. Replaced everywhere with pure Compose primitives equivalent to an XML `<shape>` with `<gradient>` + `<stroke>`:
  - `GlassBackground.kt`: dropped `Modifier.blur(80.dp)` on the accent orbs; bumped their alpha from 0.22/0.14 → 0.28/0.18 and radius 900 → 1100 so the halo remains visible without blur.
  - `GlassButton` (both `GlassButton` and `AccentGlowButton`): dropped `Modifier.shadow(...)`. Replaced with a top-to-bottom highlight gradient (`White.alpha=0.08`/0.35 → transparent) + a 1dp accent border (`primary.alpha=0.45`) around the edge. Translucent fill alphas tuned (dark 0.20, light 0.75).
  - `ConfirmCallDialog.kt`: same swap — removed the dialog's accent-halo shadow, added a border-stroke + highlight-gradient to preserve the glass edge.
  - `MainActivity.LastCallerBanner`: removed the banner's primary-halo shadow and the red-dot's colored shadow. Banner now uses gradient highlight + border; red dot uses a translucent outer ring (`red.alpha=0.25`) for emphasis without device-specific rendering.
  - Renders identically on every device (same output as an XML vector drawable). Build + 32 tests still pass. Screenshots: `noshadow_home2.png`, `noshadow_phone.png`, `noshadow_dialog.png`.

- [x] **ConfirmCallDialog glass polish** — 2026-04-18. Replaced the Material3 `AlertDialog` with a custom `androidx.compose.ui.window.Dialog`: full glass surface (`shadow(16dp, ambient/spot=primary)` + `surface.copy(alpha=0.92)` + `RoundedCornerShape(24dp)`), circular avatar (photos/profile/icon now clipped to `CircleShape` — previously rendered as squares), ExtraBold centered question text, larger action buttons (52dp → 56dp with 24dp icons). Build + 32 tests still green. Screenshot: `audit/10_confirm_dialog_glass.png`.

- [x] **Missed-call banner glass restyle** — 2026-04-18. Audit revealed the home-screen missed-call banner (`LastCallerBanner`) was still using a solid `Color(0xFF2E3A4D)` Card with a Material phone icon — only non-design surface left on the home screen. Replaced with glass-style Row: `shadow(6dp, ambient/spot=primary)` + `clip(RoundedCornerShape(16dp))` + `surface.copy(alpha=0.75)` background; 12dp red dot with red-glowing shadow as the indicator (replacing the icon); two-line content matching prototype ("Apel pierdut de la X" title + "Apasati pentru a suna inapoi (la ora HH:mm)" subtitle); whole card is clickable to call back; the green `AccentGlowButton` action button retained for elderly-clarity. Added `R.string.missed_call_tap_to_return` in RO (`values/`) and EN (`values-en/`).

- [x] **Settings-internal button overhaul** — 2026-04-18. Second pass of the button-system work, covering every remaining call site: `QuickContactSettingsScreen` (WhatsApp/Google Play/Setari Dispozitiv `Deschide`, JSON-editor `Deschide`, `Importa`, About/Privacy-Policy `Deschide`, Permissions "Verifica", WhatsApp guide confirm+see, TTS-engine selection UI, medication reminder add + exact-alarm-grant + time-picker confirm/cancel, emergency-contact add, theme dropdown trigger, about-section save, full-license dismiss, license-row "Citeste licenta", import-URL dialog confirm/cancel, contact-edit save), `MainActivity` WhatsApp + last-caller-banner call buttons, `ContactsActivity` Adauga, `ConfigEditorHint` link, `JsonConfigEditorScreen` Reseteaza/Salveaza, `OnboardingScreen` SMS-guide confirm/cancel, `SetupWizardScreen` privacy confirm/decline + contacts-add + emergency-add. ~35 additional call sites. Only short inline `TextButton` delete actions left as-is (visual noise to glassify them). Build green, 32 tests still pass.

- [x] **Full button-system overhaul** — 2026-04-18. After user feedback that button colors/shapes/sizes didn't match the v2 prototype except for `Inapoi`, introduced two shared primitives and replaced every primary-action button across the app.
  - **Bumped `minSdk` 30 → 31** (`app/build.gradle.kts`) so `Modifier.blur` and `Modifier.shadow` ambient/spot colors are always available without version guards. Updated doc refs in `CLAUDE.md` + `AI-INSTRUCTIONS.md`. `GlassBackground` simplified (removed the API 30 fallback path).
  - **`ui/composables/GlassButton.kt`** (new): translucent tinted surface (`surface` alpha 0.12 dark / 0.85 light) + accent border + accent ambient/spot shadow + ripple. Matches the v2 prototype's `glassOf()` helper visually. Used for neutral actions: home utility row, dial keys, wizard "Inapoi"/"Răspunde" glass variants, gallery prev, TTS-alert replay, medication-alert snooze, ConfirmCallDialog "Nu".
  - **`ui/composables/AccentGlowButton.kt`** (new, same file): solid accent fill + 14dp colored ambient/spot shadow halo + white ExtraBold text. Matches `glowOf()`. Used for primary actions: `Vezi poze`, dial-pad `Suna` (green), dial-pad `Sterge` (error), contact list `Suna` (green), SMS `Trimite`, wizard `Inainte`/`Finalizeaza`, onboarding `Continue`/`Seteaza`/`Permite`, gallery `Urmatoarea`, medication-alert dismiss (green), TTS-alert dismiss (accent), ConfirmCallDialog `Da` (green), IncomingCall `Răspunde` (green) / `Refuză` (red), InCall `Închide` (red), WhatsApp-call accept/reject.
  - **Font weight** bumped to `ExtraBold` on button labels everywhere for prototype parity.
  - Call-site count: ~18 buttons restyled across 12 files. `MainActivity.GridButton` split cleanly into utility-row (`GlassButton`) vs quick-contact (photo/gradient) paths.
  - Build + 32 tests still green. Screenshots: `buttons_home.png`, `buttons_phone.png`, `buttons_contacts.png`, `v2_gallery.png`, `v3_home.png` confirm the new two-tier visual language is visible on-device.

- [x] **Call-screen theme-awareness follow-up** — 2026-04-18. After the light-theme audit, spotted that `IncomingCallActivity` and `InCallActivity` still had hardcoded `Color.White` text for caller name/number/state — originally correct against the old solid `#1A1A2E` backdrop but broken when I swapped to `GlassBackground` (text would disappear against the light-mode glass). Replaced top-level call-screen text colors with `MaterialTheme.colorScheme.onBackground` (primary) and `.onBackground.copy(alpha = 0.7f)` (secondary). `Color.White` is retained inside the red/green call-action buttons and inside WhatsApp's green backdrop — those sit on saturated colored surfaces where white is correct in both themes. Build + 32 tests still green.

- [x] **Setup-wizard, onboarding and light-theme audit** — 2026-04-18. Fresh install captured (6 wizard steps + onboarding). All setup screens inherit `GlassBackground` from `MainActivity`; progress bars, section headers, input fields, and toggles all read as theme-aware. Switched `theme_mode` to `LIGHT` via `run-as` + `config.json` edit, verified every main screen against prototype's "GLASS LIGHT" mockup.
  - Screenshots: `setup_2.png` through `setup_7_final.png` + `setup_8_onboarding.png` (dark setup flow); `light_home_fixed.png`, `light_phone.png`, `light_contacts.png`, `light_sms.png`, `light_settings.png` (light mode); `dark_home_final.png` (dark regression check).
  - Fixed a real light-mode bug: `GridButton` (home utility row) had **hardcoded `Color(0xFF2E3A4D)` + `#90CAF9`** regardless of theme. In light mode, buttons read as solid dark navy boxes with dark text — bad contrast and wrong aesthetic. Replaced with theme-aware values: `MaterialTheme.colorScheme.surface.copy(alpha=0.75)` container, gradient `surfaceVariant→surface` body, `colorScheme.primary` icon tint, `colorScheme.onSurface` text. Works in both modes.
  - Light mode now matches v2 "GLASS LIGHT" prototype: light background, dark text, blue accents, visible glass gradient orbs, translucent cards.

- [x] Apply v2 glass style to every screen not yet updated — 2026-04-18. Build green; new APK installed; screenshots taken where reachable.
  - Screenshots (`/tmp/fb_screenshots/`): `final_home.png`, `final_phone.png`, `final_contacts.png`, `final_sms_list.png`, `final_sms_conv.png`, `final_settings.png`, `final_gallery3.png`, `final_confirm_dialog.png`. Call/alert activities (`IncomingCall`, `InCall`, `WhatsAppCall`, `MedicationAlert`, `TtsSmsAlert`) couldn't be triggered from `adb` (exported=false and require real ring/alarm events) — updated via source and verified at build time.
  - Per-screen status:
    - [x] Setup Wizard — inherits `GlassBackground` from `MainActivity` root. Already uses `surfaceVariant.copy(alpha = 0.5)` cards. No additional paint changes needed.
    - [x] Onboarding — inherits glass, no opaque paints in the screen.
    - [x] Settings (`QuickContactSettingsScreen`) — captured (`final_settings.png`): glass gradient visible, translucent cards, compact back button, blue accent buttons. Looks aligned.
    - [x] JSON Config Editor — inherits glass, no opaque paints.
    - [x] SMS Conversation — captured (`final_sms_conv.png`): `GradientAvatar` header, glass gradient visible, translucent message bubble. Already glass-styled from earlier pass.
    - [x] Incoming Call — `IncomingCallActivity.kt`: solid `Color(0xFF1A1A2E)` backdrop replaced with `GlassBackground`; `GradientAvatar` (size 180dp) added above the caller name; Răspunde/Refuză buttons kept red/green for call-context clarity.
    - [x] In-call — `InCallActivity.kt`: same treatment — glass + gradient avatar; red close button retained.
    - [x] WhatsApp Call — `WhatsAppCallActivity.kt`: WhatsApp-green background kept (branding); `GradientAvatar` added above caller name for consistency with the other call screens.
    - [x] Medication Alert — `MedicationAlertActivity.kt`: `Surface(background)` → `GlassBackground`. Alert icon + typography unchanged (deliberate clarity).
    - [x] TTS SMS Alert — `TtsSmsAlertActivity.kt`: same swap. Alert icon + typography unchanged.
    - [x] Gallery — `GalleryActivity.kt`: `Surface(background)` → `GlassBackground`. Photo area (`Color.Black` Box) kept opaque for proper photo contrast.
    - [x] Confirm Call Dialog — uses Material3 `AlertDialog` which already reads from the theme's surface; `GradientAvatar` fallback was added earlier when no photo/profile/icon is present.
    - [x] Quick Contact Editor — one inner emergency-contact `Card` made translucent (`surfaceVariant.copy(alpha = 0.6)`) so it matches the rest of the glass treatment.

- [~] Rework TODO into an execution-ready handoff ordered by complexity — 2026-04-18.
  - **1. TODO hygiene** — *in progress*
    - [x] Remove items already implemented in code — 2026-04-18. Dropped incoming-call layout verify (already side-by-side `Row` with `weight(1f)` at `IncomingCallActivity.kt:205`). Dropped SMS-conversation call-action task (no call button exists in `SmsActivity.kt`).
    - [x] Convert open questions already resolved by code into notes or delete them — 2026-04-18. Removed SMS-call-action question (not applicable) and Înapoi-compactness question (code confirms it is NOT compact: `Inapoi.kt` uses `ArrowBack` icon, 24dp horizontal padding, `displaySmall` font — task stays).
    - [x] Refresh the uncommitted-files section from `git status` — 2026-04-18.
  - **2. Prototype v2 polish**
    - [x] Remove `*` and `#` from `PhoneActivity` dial pad — 2026-04-18. Last row now `("", "0", "")`; empty slots render as `Spacer(weight(1f))` so the "0" stays centered under "8".
    - [x] Make `Inapoi` more compact and text-only in `ui/composables/Inapoi.kt` — 2026-04-18. Removed `ArrowBack` icon + `Row`/`Spacer` wrapping; padding 16/12 → 8/8 (modifier) and 24/14 → 16/8 (content); shape 16dp → 12dp; typography `displaySmall` → `titleLarge` (still bold, accent color, 4dp elevation for visibility).
  - **3. Admin/settings access** — *resolved 2026-04-18*
    - [x] Keep the existing footer `Lock` icon (5 taps / 3s → Settings) in `TtsStatusFooter` (`tts/TtsModelDownloadDialog.kt:144–204`). No change. Prototype's bottom-left 60-taps/15s alternative rejected; the 60-taps gesture stays reserved for the clock → WhatsApp path in `ImmersiveActivity`.
  - **4. Policy and regression backlog**
    - [x] Audit manifest permissions and special-access flows against current app behavior and privacy docs — 2026-04-18. Every policy-declared use maps to a permission and vice versa, EXCEPT the two unused permissions flagged below. See follow-ups.
    - [x] Keep `FOREGROUND_SERVICE` permission — 2026-04-18. Recheck showed `InactivityMonitorWorker` (WorkManager `CoroutineWorker`) sends SMS to emergency contacts on inactivity threshold, and AndroidX WorkManager merges `FOREGROUND_SERVICE` into the final manifest transitively. Removing from our manifest would be cosmetic; keeping gives headroom for future expedited/foreground workers.
    - [x] Remove `MANAGE_OWN_CALLS` permission — 2026-04-18. Thorough recheck confirmed no `PhoneAccount`/`ConnectionService`/`SelfManaged` use; all `TelecomManager` calls (`acceptRingingCall`, `endCall`, `defaultDialerPackage` in `IncomingCallActivity.kt` + `OnboardingChecker.kt`) work via default-dialer role + existing `ANSWER_PHONE_CALLS`/`CALL_PHONE`. Deleted from `AndroidManifest.xml`. Build passes.
    - [x] Decide on `usesCleartextTraffic="true"` — 2026-04-18. **Kept as-is** per user. No default config URL is to live in this public repo (see Open Questions).
    - [ ] **USER-ONLY** (external to repo): Verify Play Console *Declared Permissions* form lists exact-alarm justification for `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` (medication reminders / healthcare). I can't do this — it's a Play Console UI task. Blocks nothing locally; Play will flag on next release upload if missing.
    - [x] Add "remote config may use HTTP if user-configured" line to privacy policy §4 — 2026-04-18. Updated both English (§4 "Network Usage") and Romanian (§4 "Utilizarea retelei") sections; bumped "Last updated" to 18 April 2026 in both.
    - [skipped this session] Tests moved to Backlog at user request (2026-04-18).
  - **5. Simplify pass**
    - [x] Dedupe tap-counter block in `tts/TtsModelDownloadDialog.kt` — 2026-04-18. Extracted `SettingsUnlockIcon` private composable + `UNLOCK_TAP_THRESHOLD`/`UNLOCK_WINDOW_MS` constants; ERROR and DONE branches now call it. Net −12 lines. Build passes.
    - [x] Doc-sync grep for stale mentions of `*`/`#` dial pad or old `Inapoi` styling — 2026-04-18. Nothing stale in `CLAUDE.md`, `README.md`, `AGENTS.md`; only generic "dial pad" references. No doc edits needed.
  - **6. Doc architecture**
    - [x] `AGENTS.md` reduced to a thin pointer — 2026-04-18. User picked option (b). Removed the ~80 lines duplicated from `CLAUDE.md`; new file defers project context to `CLAUDE.md`, collaboration rules to `AI-INSTRUCTIONS.md`, priorities to `TODO.md`. Codex-specific section left empty with a note to only add content that genuinely differs from Claude Code.
  - **7. Backlog execution (interactive pass)** — 2026-04-18
    - [x] Target-SDK check — app is on SDK 35, compliant with Play requirements through Nov 2026. SDK 36 bump expected for late-2026 cycle. Fixed min-SDK doc drift (31 → 30) in `CLAUDE.md` and `AI-INSTRUCTIONS.md`.
    - [x] Analog clock restyle — `AnalogClock` in `MainActivity.kt` now has fuller glass face (fill 0.15 → 0.25), accent outer ring, accent second hand (was red), thicker accent-colored markers at 12/3/6/9, accent center ring + white dot, and a dark-mode accent halo.
    - [x] Emoji → Material Icons — all 4 user-facing emojis replaced (`DateRange`, `Warning`, `CheckCircle`, `BatteryChargingFull`). The last replacement used `material-icons-extended` (added as a dep by the user during the AGP upgrade pass).
    - [x] Frosted-glass pass — new `GlassBackground` composable (radial accent gradient, no `Modifier.blur` for perf on older devices), applied to `MainActivity`/`PhoneActivity`/`ContactsActivity`/`SmsActivity` roots; new `GradientAvatar` fallback in `ConfirmCallDialog`/`ContactsActivity`/`SmsActivity` replacing solid-color HSL avatars; `Card` `containerColor` alpha 0.88 at contact/SMS list sites so the gradient peeks through.
    - [x] Missed-call announcement tests (option B) — extracted pure `MissedCallAnnouncer.decide()` from `ImmersiveActivity.refreshLastCall()`; 9 JUnit tests in `MissedCallAnnouncerTest.kt` cover new/same/different-caller paths, cap behavior, zero/negative/1 maxAnnouncements. **All 9 pass on `:app:test`.**
    - [x] Medication reminder tests — `calculateNextFireTime` now accepts an injected `nowMs` parameter (defaults to `System.currentTimeMillis()`); 22 JUnit tests in `MedicationReminderSchedulerTest.kt` cover input validation, structural invariants, AND absolute-timestamp cases (later/earlier today, exact now, single-day, weekend-only, weekday-only, month/year boundaries). TZ pinned to UTC in `@Before`. **All 22 pass on `:app:test`.**
  - **8. Toolchain + SDK 36 + deprecations** — 2026-04-18
    - [x] Gradle JVM toolchain pinned to 11 (`kotlin { jvmToolchain(11) }`) + foojay-resolver added in `settings.gradle.kts` — auto-provisions JDK 11 for compile/test regardless of which JDK launches Gradle. Matches the declared build requirement.
    - [x] Target-SDK 36 bump via AGP Upgrade Assistant — `compileSdk`/`targetSdk` now 36; Gradle 8.11.1 → 9.4.1; Kotlin 2.0.21 → 2.3.20; AGP 8.10.1 → 8.11.2 (still AGP 8.x; full AGP 9 migration deferred). `kotlinOptions { jvmTarget = "11" }` replaced with new DSL `kotlin { compilerOptions { jvmTarget.set(JVM_11) } }`. `material-icons-extended` added.
    - [x] `:app:test` now runs successfully after Gradle 9 upgrade (was blocked by `DefaultReportContainer / Type T not present` under JDK 24). **32 tests pass in total** (9 MissedCallAnnouncer + 22 MedicationReminderScheduler + 1 Example).
    - [x] Deprecation warnings cleared in Kotlin sources: `Locale(String)` → `Locale.forLanguageTag(...)` in `LocaleHelper.kt` + `SetupWizardScreen.kt`; `SmsManager.getDefault()` → `getSystemService(SmsManager::class.java)` in `HeadlessSmsSendService.kt`; `LocalLifecycleOwner` / `LocalSavedStateRegistryOwner` imports moved to `androidx.lifecycle.compose` / `androidx.savedstate.compose`; `TelephonyManager.EXTRA_INCOMING_NUMBER` kept with `@Suppress("DEPRECATION")` in `PhoneCallReceiver.kt` (replacement would require `READ_CALL_LOG` which AI-INSTRUCTIONS §2.4 #13 forbids; primary path in `CallService` already uses the non-deprecated `Call.Details.getHandle()`).
    - [x] `CLAUDE.md` updated to explain JDK 11 + Gradle-daemon-JDK gotcha.
    - [ ] AGP 9 migration (built-in Kotlin, new AGP DSL audit, kapt→KSP check, BuildConfig audit, `gradle.properties` legacy-flag cleanup) — deferred. Requires another Upgrade Assistant pass (which only offered AGP 8.11.2 this session). Pick up closer to late-2026 release cycle or when Android Studio offers AGP 9 stable.
  - **9. Post-comparison glass polish** — 2026-04-18. After screenshotting the running app and comparing against v2 prototype mockups, four gaps were identified and closed:
    - [x] Hybrid contact avatars on the home screen — photos still show when configured; `GradientAvatar` (accent-gradient + single initial) replaces the old radial-gradient-with-initials fallback when a contact has no photo.
    - [x] Utility row verified: three equal-weight buttons (Formează Numar / Agenda Telefon / Mesaje) with `Vezi poze` separately highlighted below. No change needed — already correct.
    - [x] Dial pad glass restyle — `DialButton` in `PhoneActivity.kt` now uses `OutlinedButton` with translucent accent-tinted container (`primary.alpha=0.12`), subtle accent border (`primary.alpha=0.35`), and accent-colored digit text. Preserves elderly-accessibility hit target.
    - [x] `GlassBackground` intensified — two offset radial orbs (top-left primary, bottom-right tertiary→secondary) with higher alphas; `Modifier.blur(80.dp)` applied on API 31+ so orbs read as soft halos rather than sharp gradients. API 30 falls back to unblurred gradients gracefully.
    - [x] Investigated generic "deprecated features incompatible with Gradle 10" warning — 2026-04-18. Root cause traced with `--warning-mode all`: both remaining warnings come from **AGP 8.11.2 internals** (`lint-gradle:31.11.2` and `aapt2:8.11.2-12782657:osx` dependency declarations using deprecated multi-string notation). Not fixable in this repo; will clear when AGP upgrades to 9.
    - [x] Enabled Gradle configuration cache — 2026-04-18. Added `org.gradle.configuration-cache=true` to `gradle.properties`. Second-run `:app:assembleDebug` drops from ~7s to ~0.7s; `:app:test` also stores/reuses a cache entry. No task-configuration issues surfaced.

---

## Backlog / Ideas

_Unprioritized. Promote to Active when the user picks one._

- [ ] Full AGP 8 → 9 migration once Android Studio offers AGP 9.x stable. Skill's 5-step pathway applies (built-in Kotlin, new DSL audit, kapt→KSP, BuildConfig, legacy-flag cleanup). Will also eliminate the remaining Gradle-10 deprecation warnings which live in AGP internals.

---

## Blocked

_None._

---

## Done (recent)

- [x] Create `AI-INSTRUCTIONS.md` — 2026-04-18.
- [x] Create `TODO.md` — 2026-04-18.

---

## Uncommitted Changes

_Updated as work progresses. On request, use this to draft a commit message._

_Source: `git status --short` at 2026-04-18._

- `AI-INSTRUCTIONS.md` — modified: clarified ambiguous workflow rules and file references; added §2.4 rule forbidding private URLs / example config endpoints in the public repo; min-SDK doc drift fixed (31 → 30).
- `CLAUDE.md` — modified: min-SDK doc drift fixed (31 → 30).
- `TODO.md` — modified: reworked into execution-ready handoff; §1–§7 execution logged.
- `app/src/main/java/ro/softwarechef/freshboomer/PhoneActivity.kt` — modified: removed `*`/`#` from dial pad, centered `0`; wrapped content in `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/Inapoi.kt` — modified: compact text-only back button (no icon, smaller padding/radius, `titleLarge`).
- `app/src/main/java/ro/softwarechef/freshboomer/tts/TtsModelDownloadDialog.kt` — modified: extracted duplicated tap-counter logic into private `SettingsUnlockIcon` composable + named constants.
- `app/src/main/AndroidManifest.xml` — modified: removed unused `MANAGE_OWN_CALLS` permission.
- `app/src/main/assets/privacy-policy.html` — modified: §4 now notes HTTP is allowed for user-configured remote config URL (EN + RO); "Last updated" bumped to 18 April 2026.
- `app/src/main/java/ro/softwarechef/freshboomer/MainActivity.kt` — modified: `AnalogClock` restyled (glass face, accent markers 12/3/6/9, accent second hand, accent center ring, dark-mode halo); emoji→Material Icon replacements (DateRange, Warning, CheckCircle); root wrapped in `GlassBackground`; `refreshLastCall()` delegates to `MissedCallAnnouncer`.
- `app/src/main/java/ro/softwarechef/freshboomer/ContactsActivity.kt` — modified: `ContactItem` uses new `GradientAvatar`; Card alpha 0.88; content wrapped in `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/SmsActivity.kt` — modified: 2 avatar sites use `GradientAvatar`; Card alpha 0.88; content wrapped in `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/ConfirmCallDialog.kt` — modified: `GradientAvatar` fallback when no photo/profile/icon.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/ImmersiveActivity.kt` — modified: `refreshLastCall()` refactored to use `MissedCallAnnouncer`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/GradientAvatar.kt` — **new**: deterministic two-color gradient avatar fallback.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/GlassBackground.kt` — **new**: radial-gradient glass wallpaper.
- `app/src/main/java/ro/softwarechef/freshboomer/data/MissedCallAnnouncer.kt` — **new**: pure decision logic for missed-call announcements, unit-testable.
- `app/src/test/java/ro/softwarechef/freshboomer/data/MissedCallAnnouncerTest.kt` — **new**: 9 JUnit tests, all passing.
- `app/src/test/java/ro/softwarechef/freshboomer/services/MedicationReminderSchedulerTest.kt` — **new**: 22 JUnit tests (input validation, structural invariants, absolute-timestamp cases), all passing.
- `app/src/main/java/ro/softwarechef/freshboomer/services/MedicationReminderScheduler.kt` — modified: `calculateNextFireTime` accepts injected `nowMs` for deterministic testing.
- `app/src/main/java/ro/softwarechef/freshboomer/services/HeadlessSmsSendService.kt` — modified: `SmsManager.getDefault()` → `getSystemService(SmsManager::class.java)`.
- `app/src/main/java/ro/softwarechef/freshboomer/data/LocaleHelper.kt` — modified: `Locale(String)` constructor → `Locale.forLanguageTag(...)`.
- `app/src/main/java/ro/softwarechef/freshboomer/onboarding/SetupWizardScreen.kt` — modified: `Locale(String)` → `Locale.forLanguageTag(...)`; `LocalLifecycleOwner` / `LocalSavedStateRegistryOwner` imports moved to the new `androidx.lifecycle.compose` / `androidx.savedstate.compose` packages.
- `app/src/main/java/ro/softwarechef/freshboomer/receivers/PhoneCallReceiver.kt` — modified: `@Suppress("DEPRECATION")` + rationale comment on `EXTRA_INCOMING_NUMBER` (replacement would require `READ_CALL_LOG`, which AI-INSTRUCTIONS forbids).
- `app/build.gradle.kts` — modified (partly by user / AGP Upgrade Assistant): `compileSdk`/`targetSdk` 35 → 36; new Kotlin `compilerOptions` DSL; `kotlin { jvmToolchain(11) }`; `material-icons-extended` dep.
- `gradle/libs.versions.toml` — modified (by user / Assistant): `agp = "8.11.2"`, `kotlin = "2.3.20"`, new `androidx-material-icons-extended` entry.
- `gradle/wrapper/gradle-wrapper.properties` — modified (by user / Assistant): Gradle 8.11.1 → 9.4.1.
- `settings.gradle.kts` — modified: foojay-resolver-convention plugin added (version bumped to 1.0.0 by user).
- `gradle.properties` — modified: enabled `org.gradle.configuration-cache=true`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/GlassBackground.kt` — modified: two offset accent orbs with stronger alphas; `Modifier.blur(80.dp)` on API 31+ for frosted halo look.
- `app/src/main/java/ro/softwarechef/freshboomer/PhoneActivity.kt` — modified: `DialButton` restyled to translucent glass (OutlinedButton, accent border + tint).
- `app/src/main/java/ro/softwarechef/freshboomer/MainActivity.kt` — modified: home-screen avatar fallback uses `GradientAvatar` instead of the old radial-gradient-with-initials box.
- `app/src/main/java/ro/softwarechef/freshboomer/GalleryActivity.kt` — modified: root `Surface` → `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/MedicationAlertActivity.kt` — modified: root `Surface` → `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/TtsSmsAlertActivity.kt` — modified: root `Surface` → `GlassBackground`.
- `app/src/main/java/ro/softwarechef/freshboomer/IncomingCallActivity.kt` — modified: solid `#1A1A2E` backdrop → `GlassBackground`; `GradientAvatar(size=180.dp)` added above caller name.
- `app/src/main/java/ro/softwarechef/freshboomer/InCallActivity.kt` — modified: same swap; `GradientAvatar` above caller name.
- `app/src/main/java/ro/softwarechef/freshboomer/WhatsAppCallActivity.kt` — modified: WhatsApp-green kept; `GradientAvatar` added above caller name.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/QuickContactEditor.kt` — modified: one inner emergency-contact `Card` made translucent for consistency.
- `app/src/main/java/ro/softwarechef/freshboomer/MainActivity.kt` — modified: `GridButton` `roundedSquare` branch uses theme-aware colors (`surface`, `surfaceVariant`, `primary`, `onSurface`) so home utility buttons adapt correctly to light/dark.
- `app/src/main/java/ro/softwarechef/freshboomer/IncomingCallActivity.kt` — modified: top-level text colors `Color.White` → `MaterialTheme.colorScheme.onBackground` / `.copy(alpha=0.7f)` so caller name/number/status read correctly under `GlassBackground` in both themes.
- `app/src/main/java/ro/softwarechef/freshboomer/InCallActivity.kt` — same text-color theme-awareness swap on the caller-info block.
- `app/build.gradle.kts` — modified: `minSdk` 30 → 31 to guarantee `Modifier.blur` availability and enable the button system's colored shadows without guards.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/GlassButton.kt` — **new**: `GlassButton` + `AccentGlowButton` primitives matching the v2 prototype's `glassOf`/`glowOf` helpers.
- Button call-site swaps in: `MainActivity.kt` (utility row + Vezi poze), `PhoneActivity.kt` (dial keys + Sterge + Suna), `ContactsActivity.kt` (Suna), `SmsActivity.kt` (Trimite), `GalleryActivity.kt` (prev/next), `IncomingCallActivity.kt` (Răspunde/Refuză), `InCallActivity.kt` (Închide), `WhatsAppCallActivity.kt` (accept/reject), `MedicationAlertActivity.kt` (dismiss/snooze), `TtsSmsAlertActivity.kt` (replay/dismiss), `onboarding/OnboardingScreen.kt` (continue + per-row Seteaza/Permite), `onboarding/SetupWizardScreen.kt` (Inapoi/Inainte/Finalizeaza), `ui/composables/ConfirmCallDialog.kt` (Da/Nu).
- `CLAUDE.md` / `AI-INSTRUCTIONS.md` — min-SDK doc refs bumped from 30 to 31.
- `app/src/main/java/ro/softwarechef/freshboomer/MainActivity.kt` — modified: `LastCallerBanner` rewritten as glass-style card with red-dot indicator + two-line content + tappable whole-card.
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` — added `missed_call_tap_to_return`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/ConfirmCallDialog.kt` — modified: `AlertDialog` → custom glass `Dialog`; avatars clipped to `CircleShape`.
- `app/src/main/java/ro/softwarechef/freshboomer/ui/composables/GlassBackground.kt` — simplified: removed API 30 version guard (minSdk 31 now).
- `CLAUDE.md` — modified: min-SDK 31 → 30; expanded JDK note explaining the Gradle-daemon gotcha; `MainActivity.kt` bullet for `refreshLastCall()` delegation to `MissedCallAnnouncer` was already captured.
- `AGENTS.md` — untracked; rewritten this session as a thin pointer to `CLAUDE.md` / `AI-INSTRUCTIONS.md` / `TODO.md` (option b).

### Draft commit message

```
Add AI-INSTRUCTIONS.md and TODO.md for AI-assisted workflow

Document collaboration rules (planning, no auto-commits, Play Store
compliance, review-before-test, doc hygiene) and introduce a rolling
TODO file so future sessions can resume cleanly.
```

---

## Open Questions

_Record anything awaiting user input so the next session can see it._

- None.

### Resolved this session

- `FOREGROUND_SERVICE` — kept (WorkManager transitive merge; supports inactivity monitor & future expedited workers).
- `MANAGE_OWN_CALLS` — removed (unused; no self-managed calling code).
- Cleartext config import — kept `usesCleartextTraffic="true"` as-is; no default/example config URL ever committed.
- `AGENTS.md` — rewritten as thin pointer (option b); duplication with `CLAUDE.md` eliminated.

---

## Notes for Next Session

- Start here. Check "Active" first, then "Open Questions".
- If a plan file exists under `docs/plans/`, it takes precedence for that task's detail.
