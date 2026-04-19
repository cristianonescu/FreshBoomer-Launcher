# TODO

Rolling task list for FreshBoomer. Read `AI-INSTRUCTIONS.md` before editing. Keep this file current — it is the handoff between sessions.

Legend: `[ ]` open · `[x]` done · `[~]` in progress · `[blocked: reason]`

---

## Active

- [ ] **USER-ONLY** (external to repo): Verify Play Console *Declared Permissions* form lists exact-alarm justification for `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` (medication reminders / healthcare). Can't be automated — Play Console UI task. Blocks nothing locally; Play will flag on next release upload if missing.

- [ ] **AGP 8 → 9 migration** — deferred until Android Studio offers AGP 9.x stable. Skill's 5-step pathway applies (built-in Kotlin, new DSL audit, kapt→KSP, BuildConfig, legacy-flag cleanup). Will also eliminate the remaining Gradle-10 deprecation warnings that live in AGP internals.

---

## Backlog / Ideas

_Unprioritized. Promote to Active when picked._

_None._

---

## Blocked

_None._

---

## Done (recent)

### 2026-04-19 session — architecture polish, navigation, file splits, dark-mode glass fix, Play Store screenshots

- Navigation centralization: new `data/LauncherNavigator.kt` owns all in-app Intent construction (`go()` / `launch()` / `intentFor()`); 13 ad-hoc Intent sites across activities/receivers/services routed through it; system back unified with `Inapoi` via `ImmersiveActivity.backReturnsToHome` flag; `announceAndGo(screen, ttsResId)` collapses four announce+navigate call sites in `MainActivity`.
- Threading + data: `ImmersiveActivity` inactivity timer rewritten with `lifecycleScope.launch { delay(...) }`; `GalleryActivity` / `ContactsActivity` / `SmsActivity` content-provider queries moved to `Dispatchers.IO`; `ContactsActivity`'s 1-second main-thread polling loop replaced with a `ContentObserver`; `ImmersiveActivity`'s inline missed-call SharedPreferences access extracted to `data/MissedCallAnnouncementStore`.
- Logging normalized to per-file `TAG = "FB/<ClassName>"` across 12 files (~50 call sites).
- File splits: `MainActivity.kt` 1296 → 397 LOC (new `home/` subpackage); `QuickContactEditor.kt` 2486 → 714; `SetupWizardScreen.kt` 1338 → 307; `JsonConfigEditorScreen.kt` 1044 → 637. Nine new files, 32/32 tests still pass.
- Dark-mode fix: `GlassBackground` now provides `LocalContentColor = colorScheme.onBackground` so default-coloured Text reads correctly in dark mode (was literal black, invisible).
- Quick-contact avatar bug: `GradientAvatar.size` became nullable so fallback avatars can fill the whole button — no more gray ring around smaller gradient circle.
- Narrow-screen fixes on `SystemClock` (date wrap → single-line ellipsis) and `TtsStatusFooter` (progress bar fixed `width(200.dp)` instead of `fillMaxWidth(0.6f)`).
- Battery overlays redesigned: new `GlassStatusBanner` helper matches `LastCallerBanner` style — translucent accent-tinted surface + 1dp border + 16dp radius + inset padding + theme-aware text. Red/green/blue state colors kept.
- Play Store / README screenshots captured for tablet (800×1340) and phone (720×1560) in `screenshots/tablet/` and `screenshots/phone/`, all in dark mode, placeholder contacts only (Bunica/Bunicu/Mama/Tata/Sora/Vecina).
- Committed as `811a54e` + follow-up UI fixes.

### 2026-04-18 session — v2 glass design + SDK 36 + toolchain upgrade

- Full button-system overhaul: new `GlassButton` + `AccentGlowButton` primitives; ~35 call sites across every screen; `ExtraBold` labels.
- Device-agnostic glass: removed every `Modifier.blur` and `Modifier.shadow` ambient/spot use (rendered inconsistently across OEMs); replaced with pure Compose gradients + borders.
- `LastCallerBanner` rewritten as glass card + red-dot indicator + tappable whole card.
- `ConfirmCallDialog` → custom glass `Dialog` (replaces Material3 `AlertDialog`).
- `GradientAvatar` fallback added everywhere (ContactsActivity, SmsActivity, ConfirmCallDialog, home).
- `AnalogClock` restyled with glass face + accent markers + accent second hand.
- Light-theme audit: fixed `GridButton` hardcoded colours; every screen now theme-aware.
- Call-screen text colour fix: `Color.White` → `MaterialTheme.colorScheme.onBackground` under `GlassBackground`.
- Dial pad: removed `*` / `#`, centered `0`; `DialButton` restyled to glass.
- `Inapoi` back button: compact text-only (no icon, smaller padding, `titleLarge`).
- `TtsStatusFooter`: extracted `SettingsUnlockIcon` dedupe.
- Manifest: removed unused `MANAGE_OWN_CALLS`; kept `FOREGROUND_SERVICE`.
- Privacy policy §4 updated (EN + RO) to note HTTP allowed for user-configured remote config URL.
- Toolchain: `minSdk` 30 → 31; `compileSdk`/`targetSdk` → 36; Gradle 8.11.1 → 9.4.1; Kotlin 2.0.21 → 2.3.20; AGP 8.10.1 → 8.11.2; `kotlin { jvmToolchain(11) }` + foojay-resolver; `material-icons-extended` added; configuration cache enabled.
- Deprecation warnings cleared in Kotlin sources.
- Tests: extracted pure `MissedCallAnnouncer.decide()` (9 tests) and `MedicationReminderScheduler.calculateNextFireTime(nowMs)` (22 tests); 32/32 pass.
- Docs: `AGENTS.md` reduced to thin pointer to `CLAUDE.md` / `AI-INSTRUCTIONS.md` / `TODO.md`.
- Committed as `7569f97`.

---

## Open Questions

_Record anything awaiting user input so the next session can see it._

_None._

---

## Notes for Next Session

- Start here. Check "Active" first, then "Open Questions".
- If a plan file exists under `docs/plans/`, it takes precedence for that task's detail.
