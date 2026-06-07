# CLAUDE.md — autoinsta
# Read this entire file before touching any code or asking questions.

autoinsta is a native **Android (Kotlin + Jetpack Compose)** app that **schedules and
auto-publishes Instagram posts, Reels, and carousels** at a chosen date/time, each with
its own pre-written caption + hashtags. On-device scheduling, **official Instagram Graph
API**, no backend server.

---

## 🔴 EVERY SESSION STARTS WITH GIT

Before touching any file, create a checkpoint yourself:
```bash
git add -A && git commit -m "checkpoint: before [task name]"
```
For large tasks touching many files, commit after each meaningful unit:
```bash
git commit -m "feat: [area] — [what changed]"
```
Final commit when done:
```bash
git add -A && git commit -m "feat: [task name] complete"
```
**Non-negotiable. We need rollback points. Commit yourself every session, no exceptions.**

---

## 📋 HOW A SESSION WORKS

The owner talks to you in plain language. No middleman, no ticket queue.

**When you get a task:**

1. **Challenge first.** Before agreeing: Is the idea complete? Anything ambiguous or
   underspecified? Does it contradict the architecture or a locked decision in
   `autoinsta_Master_Plan.md`? If anything is unclear — push back with **one focused
   question**, don't proceed on assumptions.

2. **Offer the pipeline (big tasks only).** For anything non-trivial, ask:
   > *"Full pipeline for this? (plan → execute → review)"*
   - **No** → proceed normally.
   - **Yes** → produce a written plan first, execute it, then self-review the diff before showing the result.
   - Shorthand: if the prompt ends with **"full power"**, treat as yes — skip asking.

3. **Restate** what you understood in ~2 sentences and **list the exact files** you'll touch.
4. **RUN the git checkpoint** before touching anything.
5. **Write the task to `docs/_Current_Task.md`** using the template below.
6. **Execute.** Follow every convention in this file.
7. **Spotted an unrelated bug?** Note it under `## Noticed (not fixing now)` in
   `docs/_Current_Task.md`. Do **not** fix it inline.
8. **When done — always, unprompted:**
   - Append one line to `docs/_Task_History.md`.
   - If the task completes/changes a roadmap item, update `autoinsta_Master_Plan.md`
     and `docs/_Architecture.md`.
   - `git add -A && git commit -m "feat/fix/docs: [task name]"`.

**Token discipline:** read `docs/_Architecture.md` + the specific files for the task.
Don't re-read the whole codebase. Use Grep/Glob for targeted lookups.

---

## 📝 TASK FILE TEMPLATE — `docs/_Current_Task.md`
```markdown
# Current Task — [SHORT NAME]
**Date:** [TODAY]
**Branch:** main

## Goal
[Request restated clearly in your own words]

## Files to touch
- `path` — [what and why]

## Acceptance criteria
- [ ] [Specific thing that proves it works]
- [ ] Builds: `./gradlew assembleDebug` succeeds
- [ ] Git committed

## Noticed (not fixing now)
[Unrelated bugs spotted — blank if none]
```

## 📝 TASK HISTORY FORMAT — `docs/_Task_History.md`
```
[DATE] | [task name] | files: A.kt, B.kt | notes: [one sentence — gotchas/decisions]
```

---

## 🗺 PROJECT OVERVIEW
See `autoinsta_Master_Plan.md` for the full phased roadmap and `docs/_Architecture.md`
for the 1-page technical map. The short version:

- **Compose** screens → **ViewModel** → **Repository** → **Room** (local) / **Retrofit** (network).
- **Room** stores scheduled posts, media refs, hashtag presets, history, account.
- **AlarmManager + WorkManager** fire each post at its time; **BootReceiver** reschedules after reboot.
- At fire time `PostWorker` uploads media to **Cloudinary** (gets a public URL) then calls the
  **Instagram Graph API** to create + publish the container.

---

## 📁 FILE STRUCTURE
```
autoinsta/
├── docs/
│   ├── _Current_Task.md      ← you write the active task here
│   ├── _Architecture.md      ← 1-page technical reference
│   ├── _Task_History.md      ← append-only completed-work log
│   └── SETUP_GUIDE.md        ← (Phase 4) Meta + Cloudinary account steps
├── autoinsta_Master_Plan.md  ← phased roadmap + locked decisions
├── prompt.md                 ← open-items scratchpad
├── CLAUDE.md                 ← this file
├── secrets.properties        ← API keys (GIT-IGNORED, never commit)
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml ← version catalog (single source of dep versions)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/autoinsta/
        │   ├── AutoInstaApp.kt   MainActivity.kt
        │   ├── data/  (db/ remote/ repository/ prefs/)
        │   ├── domain/model/
        │   ├── scheduler/  (PostScheduler, PostWorker, BootReceiver)
        │   └── ui/  (theme/ home/ composepost/ presets/ history/ settings/ components/)
        └── res/
```

---

## 🧱 BUILD & RUN
This machine has Android Studio + SDK (platforms 33–36, build-tools 36/37) and a bundled JDK.
Java/Gradle are **not on PATH**, and **`gradlew`/`gradlew.bat` FAIL here** because the
project path contains non-ASCII chars (`C:\سطح المكتب\` = Arabic "Desktop") which cmd/bash
mangle into a broken classpath. Build by invoking the wrapper's main class directly with a
**relative** classpath (run from the project root, e.g. in PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dfile.encoding=UTF-8" `
  -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain `
  :app:assembleDebug --no-daemon --console=plain
```
Also required: `android.overridePathCheck=true` in `gradle.properties` (AGP blocks
non-ASCII paths otherwise). Verified the full build works with it.

Easiest path for the owner: **open the folder in Android Studio and press Run** — AS uses
its own embedded Gradle runner and handles the path fine. **Always confirm a build passes
(BUILD SUCCESSFUL + an APK in `app/build/outputs/apk/debug/`) before logging a task complete.**

---

## 🔑 SECRETS
Real credentials live ONLY in `secrets.properties` (git-ignored) and reach code via
`BuildConfig`. Keys: `META_APP_ID`, `META_APP_SECRET`, `META_GRAPH_VERSION`,
`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET`, `OAUTH_REDIRECT_SCHEME`.
A committed `secrets.properties.example` documents the keys with empty values.

---

## 🧠 CODE CONVENTIONS
- **Kotlin + Compose (Material 3)** only. No Java, no XML layouts (Compose UI).
- **MVVM:** Composables are dumb; state + logic live in ViewModels; data access in Repositories.
- **All versions in `gradle/libs.versions.toml`** — never hardcode dep versions in `build.gradle.kts`.
- **Coroutines + Flow** for async; `Dispatchers.IO` for DB/network. No blocking the main thread.
- **No network calls outside `data/remote`.** No Room calls outside `data/db`/repositories.
- **No secrets in source** — always `BuildConfig.*`.
- **Graph API**: always go through `InstagramApi`; never hardcode the graph version (use `BuildConfig.META_GRAPH_VERSION`).
- **Nullability:** model API responses as nullable DTOs; validate before use.
- **Validate the build** after changes: `./gradlew assembleDebug`.

---

## 🚫 NEVER DO
1. Commit `secrets.properties` or any real API key / keystore.
2. Build a UI-automation / bot path that taps the real Instagram app — Graph API only (ToS-safe).
3. Add a backend server (v1 is on-device only — see Master Plan).
4. Put network or DB calls inside Composables or ViewModels directly — go through repositories.
5. Hardcode the Graph API version, account id, or any credential.
6. Fix unrelated bugs mid-task — log them under `## Noticed (not fixing now)`.
7. Skip the git checkpoint or the `_Task_History.md` log.
8. Mark a task done without a passing `./gradlew assembleDebug`.

---

*Last updated: 2026-06-07. Update this file when architecture or a locked decision changes.*
