# AI-INSTRUCTIONS.md

Operating manual for any AI assistant (Claude Code, Codex, Cursor, etc.) working on this repository. Read this **before** touching code. It complements `CLAUDE.md` if present, or `AGENTS.md` otherwise — this file is about *how to collaborate*, not *what the code does*.

---

## 1. Project Snapshot

- **App:** FreshBoomer — single-module Android launcher for elderly users.
- **Package:** `ro.softwarechef.freshboomer`
- **Language:** Kotlin + Jetpack Compose (Material3).
- **UI language:** Romanian. All user-visible strings, TTS, toasts, labels.
- **Target user:** non-technical elderly person; simplicity and accessibility trump elegance.
- **Distribution:** Google Play Store. Policy compliance is mandatory.
- **Build:** JDK 11, Compile/Target SDK 36, Min SDK 31. See `CLAUDE.md` for build commands and architecture.

---

## 2. Non-Negotiable Rules

These rules override defaults and personal judgment. If a rule conflicts with an instruction in the moment, **ask** before breaking it.

### 2.1 Effort & Output
1. **Default to medium reasoning effort.** If the tool does not expose this control, follow the spirit of this rule: avoid both shallow guesses and excessive analysis.
2. **Tone:** professional and direct. Not chatty, not overly friendly, no filler, no congratulations. Short sentences.
3. **Strong solutions over quick fixes.** Prefer the root-cause fix, even if longer. Flag when something is a temporary patch and why.
4. **Ask when unclear.** Do not invent requirements. Do not guess at product behavior, UX copy, contact lists, or Google Play policy edges — ask.

### 2.2 Planning & Continuity
5. **Every task involving more than a small localized change, or any multi-step investigation/implementation, gets a plan file.** Before starting such work, create or update a markdown plan/TODO in the repo (e.g. `docs/plans/<task-name>.md` or update `TODO.md`). Include goal, steps, current status, open questions, next action.
6. **Design for session resumption.** Future sessions must be able to pick up from the plan file alone. Record decisions made, paths touched, and *why*. Mark steps `[ ]` / `[x]` / `[blocked: ...]`.
7. **Close the loop.** When a task ends, update its plan file: mark done, note deviations from plan, and summarize what changed.

### 2.3 Version Control
8. **Never commit unless explicitly told.** No `git commit`, no `git push`, no `--amend`, no tag creation without an explicit instruction from the user.
9. **Track uncommitted changes.** At any point you must be able to answer: "what's uncommitted and why?" Keep a running mental (or file) note of files touched since last commit. On request — or when the user says "commit" — produce a clean, accurate commit message summarizing *why*, not just *what*.
10. **Commit message format:** imperative mood, subject under 70 chars, followed by a short body explaining motivation. Follow the repo's existing style (see `git log`).
11. **Never force-push, reset --hard, or delete branches** without explicit confirmation.

### 2.4 Android & Play Store Compliance
12. **Respect Android development rules.** Activity lifecycle, Compose state hoisting, proper coroutine scopes, no main-thread I/O, no leaked context, and prefer `strings.xml` for new or changed user-visible strings unless the existing area intentionally keeps copy inline and the user has not asked for a localization cleanup.
13. **Do not break Google Play policies.** In particular:
    - No sensitive permissions that can't be justified (`CALL_LOG`, `READ_SMS`, etc.) — this app dropped `CALL_LOG` deliberately (see commit `30763ed`). Do **not** reintroduce it without user approval.
    - Default phone / SMS / launcher role declarations must stay consistent with actual functionality.
    - Respect foreground service restrictions, background location rules, exact alarm rules.
    - No obfuscated or dynamically loaded code paths.
    - Privacy policy link and data safety form must stay accurate — if you change data handling, flag it so `privacy-policy` and the Play Console data-safety form can be updated.
    - Target SDK must stay current with Play's requirements.
    - Accessibility service and `SYSTEM_ALERT_WINDOW` usage (if any) must be justified.
    - **No private URLs, hostnames, or example config endpoints in the repo.** This is a public GitHub repository. User-supplied config URLs are entered at runtime via the onboarding/settings flow; never hardcode a default or check in a sample URL in code, docs, tests, or commit messages.
14. **Test on-device assumptions.** Min SDK 31 — don't use APIs above 31 without a version guard, and call out any behavior that still needs device/emulator verification.

### 2.5 Documentation Hygiene
15. **Keep `.md` and `.html` files in sync with code.** Update affected docs when behavior, setup, permissions, privacy, or workflow changes. This may include `README.md`, `CLAUDE.md`, `AGENTS.md`, `AI-INSTRUCTIONS.md`, `privacy-policy.html`, and relevant plan files. Do not edit unrelated docs just to satisfy the rule. Stale docs are a bug.
16. **Update this file** when new collaboration rules emerge.

### 2.6 Review Before Test
17. **For tasks with code or config edits, always review changes before running tests or builds.** Read the diff (`git diff`), confirm the edits match intent, *then* run `./gradlew :app:assembleDebug` / `:app:test`. Catches accidental deletions, mis-scoped edits, and stray debug code before they waste a build cycle.

### 2.7 Suggestions & Ideas
18. **Proactively propose improvements**, but as a **numbered list the user picks from** — do not implement unsolicited. Examples: refactor opportunities, missing tests, accessibility wins, Play policy risks, dependency updates, dead code. One line per idea + one line of rationale.
19. **Propose relevant Claude Code skills** when they fit the task. Known useful ones in this environment:
    - `security-review` — run before shipping anything that touches permissions, intents, or SMS/phone roles.
    - `review` — PR review pass.
    - `simplify` — after a feature lands, look for reuse / dead code.
    - `fewer-permission-prompts` — to reduce friction on common bash/MCP calls.
    - `update-config` — for hooks, permissions, env vars in `settings.json`.
    - `codex:rescue` — when stuck, want a second opinion, or need a deeper diagnostic pass.
    - `init` — only if `CLAUDE.md` is ever lost.
    Mention the skill, say *why* it fits, let the user decide.

---

## 3. Workflow Template

For any task larger than a one-line fix:

1. **Clarify.** Restate the goal in one sentence. Ask questions if anything is ambiguous (UX copy, target behavior, whose phone numbers, Play policy interpretation).
2. **Plan.** Create/update a plan file under `docs/plans/` or append to `TODO.md`. Include: goal, steps, files likely touched, open questions, risk/policy notes.
3. **Execute.** Work through the plan. Keep edits focused — no drive-by refactors unless the user agreed.
4. **Self-review.** `git diff`. Check for: debug prints, commented-out code, accidental permission additions, unused imports, string hardcoding regressions, Compose recomposition traps, missing `remember`/`rememberSaveable`.
5. **Test.** Build debug, run unit tests, run instrumented tests if a device is available and the change warrants it.
6. **Document.** Update affected `.md` / `.html`. Update the plan file's status. Note uncommitted files.
7. **Report.** Short summary: what changed, why, what's still open, what you'd commit as (message draft), what you recommend next.

---

## 4. Project-Specific Gotchas

- **Romanian only.** Never translate UI copy to English "for clarity." Preserve diacritics (ă, â, î, ș, ț).
- **TTS rate is 0.85** intentionally (elderly comprehension). Don't "fix" it.
- **20s inactivity timeout** returns to `MainActivity`. Don't extend without reason.
- **Hardcoded quick-dial contacts** in `MainActivity` are for the specific end user. Do not generalize / add a settings screen without explicit ask.
- **WhatsApp secret unlock** (60 taps / 15s) is a deliberate hidden escape hatch. Don't refactor it away.
- **All audio streams set to max + speakerphone on call** is intentional accessibility, not a bug.
- **Portrait-only, singleTask** for `MainActivity` — don't change.
- **`ImmersiveActivity`** is the common base. New activities should extend it unless there's a strong reason not to.
- **No DI, no ViewModel, no Room, no Retrofit.** Minimal-deps is a design choice. Propose new deps as a *suggestion*, don't add them unilaterally.

---

## 5. What "Done" Looks Like

A task is done when **all applicable** items below hold:
- [ ] Code builds (`./gradlew :app:assembleDebug`).
- [ ] Relevant tests pass.
- [ ] Diff self-reviewed.
- [ ] Docs (`.md` / `.html`) updated.
- [ ] Plan file updated (status, deviations, next steps).
- [ ] Uncommitted changes list is accurate; commit message drafted.
- [ ] Play Store policy impact considered and noted if non-zero.
- [ ] User notified with a concise summary and any open questions.

---

## 6. When In Doubt

Ask. A 30-second clarification beats a 30-minute wrong implementation.
