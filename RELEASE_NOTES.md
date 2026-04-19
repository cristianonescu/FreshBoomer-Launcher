# v1.4 Release Notes

## New visual design — "Glass" theme v2
- Every screen adopts a consistent translucent "glass" look: soft accent halo background, translucent cards, 1 dp accent borders, 16–18 dp rounded corners, ExtraBold button labels. Matches the v2 prototype.
- Two shared button primitives introduced:
  - **GlassButton** — neutral actions (home utility row, dial pad keys, "Nu" on confirmations, wizard Inapoi, alert snooze/replay).
  - **AccentGlowButton** — primary actions (Suna / Trimite / Raspunde, gallery Urmatoarea, wizard Inainte / Finalizeaza, onboarding buttons, etc.).
- Dial pad restyled as translucent glass keys with large tap targets; "*" and "#" removed so the "0" stays centered under "8".
- Back button ("Inapoi") made compact and text-only.
- New gradient-avatar fallback (two-colour gradient + large initial) for any contact without a photo, used on home, contacts, SMS, and the call-confirmation dialog.
- Analog home-screen clock restyled with a glass face, accent outer ring, accent hour markers at 12/3/6/9, and an accent second-hand.
- Home missed-call banner redesigned: translucent glass card, red-dot indicator, two-line content ("Apel pierdut de la X" / "Apasati pentru a suna inapoi"), whole card tappable.
- Call-confirmation dialog replaced with a custom glass dialog — translucent surface, circular avatar (photos, drawables, and gradient fallback all clip to a circle), ExtraBold centered question, larger action buttons.

## Dark- and light-mode polish
- Full light-mode audit — home utility buttons, call-screen text, and setup-wizard section headers all now read from `MaterialTheme.colorScheme` instead of hardcoded values, so both themes look correct.
- Dark-mode fix: some `Text` labels (setup-wizard "Tema" / "Functionalitati" / "Comportament" headers) appeared nearly black on the dark background because the glass wrapper didn't set `LocalContentColor`. Fixed at the source — every glass-backed screen now picks up the theme-appropriate foreground automatically.
- Battery banners (low / charging / fully-charged) redesigned to match the rest of the app: translucent state-tinted glass with a coloured border and theme-aware text, instead of solid edge-to-edge coloured rectangles. Charging green, fully-charged blue, low-battery red — all subdued and inset.

## Emojis replaced with Material icons
- The four emoji characters that used to appear in status text (`📅`, `⚠️`, `✅`, `🔋⚡`) were replaced with proper Material icons (`DateRange`, `Warning`, `CheckCircle`, `BatteryChargingFull`) for consistent rendering on every device.

## Navigation improvements
- System back button on Phone / Contacts / SMS / Gallery now returns to the home screen consistently (matches the in-screen `Inapoi` button). Dialogs and conversation sub-views still consume back first, as before.
- All in-app screen transitions now go through a central `LauncherNavigator`, fixing several inconsistencies in how different entry points launched activities.
- Home-screen voice announcements ("Mamaie, deschid …") and screen transitions are now guaranteed to fire together or not at all.

## Performance and responsiveness
- Contact / gallery / SMS screens load their data off the main thread, so the list appears without a hitch even on devices with lots of contacts or photos.
- The contacts list used to poll the system contacts database once per second on the UI thread; it now uses a content observer and only reloads when contacts actually change.
- Inactivity auto-return to home migrated from `Handler.postDelayed` to a lifecycle-scoped coroutine, which cancels cleanly when the activity leaves the screen.

## Device compatibility
- `minSdk` lowered back to 30 — FreshBoomer runs on Android 11+ again.
- `targetSdk` bumped to 36 (Play Store compliance for the 2026 cycle).
- All glass effects use pure Compose gradients and borders — no `Modifier.blur` / `Modifier.shadow` with ambient or spot colours, which rendered inconsistently across OEM devices (Samsung, Xiaomi, Oppo all showed different results). The new look renders identically on every device.

## Tooling / under the hood
- Gradle 8.11.1 → 9.4.1; Kotlin 2.0.21 → 2.3.20; Android Gradle Plugin 8.10.1 → 8.11.2.
- Kotlin/Java toolchain pinned to JDK 11 via `kotlin { jvmToolchain(11) }` + foojay resolver, so the build picks up the right JDK automatically regardless of the one used to launch Gradle.
- Gradle configuration cache enabled (second-run builds drop from ~7 s to under 1 s).
- Deprecation warnings cleared in Kotlin sources (`Locale(String)`, `SmsManager.getDefault()`, `LocalLifecycleOwner`, `LocalSavedStateRegistryOwner`).
- Large screen files (main activity, setup wizard, settings screen, JSON editor) split into smaller per-component files for easier maintenance; no user-visible change.
- Two additional test suites added (`MissedCallAnnouncerTest`, `MedicationReminderSchedulerTest`); 32 unit tests pass.

## Privacy policy
- Added a note in §4 ("Network Usage" / "Utilizarea retelei", both EN and RO) clarifying that remote configuration import is allowed over HTTP when the user explicitly configures a URL. HTTPS is still preferred and recommended.

## Permissions cleanup
- Removed the unused `MANAGE_OWN_CALLS` permission. All call handling uses the default-dialer role with existing `CALL_PHONE` / `ANSWER_PHONE_CALLS` permissions.

## Play Store / GitHub assets
- New Play Store and README screenshot sets for phone (720×1560), 7″ tablet (800×1340), and 10″ tablet, all captured in dark mode with placeholder contacts. Previous screenshot set retired.

## Version
- Bumped `versionCode` to 7, `versionName` to **1.4**.
