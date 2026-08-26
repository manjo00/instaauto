# CLAUDE.md — autoinsta
# Read this entire file before touching any code or asking questions.

autoinsta is a native **Android (Kotlin + Jetpack Compose)** app that **schedules and
auto-publishes Instagram posts, Reels, and carousels** at a chosen date/time, each with
its own pre-written caption + hashtags. On-device scheduling, **official Instagram Graph
API**, no backend server. Target user: a **digital-art Instagram account** (Creator account).

---

## 👤 WHO YOU'RE WORKING WITH

The owner is a **beginner at mobile development**. Before using a new concept, explain
in 2–3 sentences what it does and why it's the right tool here. Explain; don't lecture —
they follow along fine once the idea is named.

Be direct. If an idea is wrong or risky, say so in a sentence or two, then either do it
their way if they confirm, or propose the better path. When you get something wrong,
name it briefly and move on — no long apologies.

**Report failures plainly.** If something didn't work, say what broke and show the
output. Never claim success you haven't verified.

---

## 🔴 EVERY SESSION STARTS WITH GIT

Before touching any file:
```bash
git add -A && git commit -m "checkpoint: before [task name]"
```
Commit after each meaningful unit, and again when done. **Conventional commits**
(`feat:`, `fix:`, `docs:`, `test:`, `chore:`). `main` stays green. Push after each
working feature.

---

## 📋 HOW A SESSION WORKS

1. **Challenge first.** Is the idea complete? Anything ambiguous? Does it contradict a
   locked decision in `autoinsta_Master_Plan.md`? Push back with **one focused
   question** rather than proceeding on assumptions.

2. **Brainstorm before building.** For anything non-trivial: ask questions **one batch
   at a time** (multiple-choice where possible), propose **2–3 approaches with
   trade-offs and your recommendation**, write a short design, get an OK.
   **Don't write feature code before the design is approved.**
   Shorthand: if the prompt ends with **"full power"**, skip the asking and run the
   full pipeline (plan → execute → review).

3. **Write it down** — the design goes in `docs/specs/`, the task breakdown in
   `docs/plans/`. See the docs layout below.

4. **Then build it in batches.** Don't stop and ask after every small step. Group the
   work, run tests and analysis throughout, come back when there's something real to
   look at. Only interactive/device steps wait for the owner.

5. **Spotted an unrelated bug?** Log it under `## Noticed (not fixing now)` in the plan
   file, or in `docs/ROADMAP.md` if it's a real piece of work. Do **not** fix it inline.

6. **No silent changes.** If you touch a file outside the task, say what and why.
   Never mass-reformat the codebase as a side effect.

7. **When done — always, unprompted:** update `docs/STATUS.md`, update the Master Plan
   and `docs/ARCHITECTURE.md` if a roadmap item or the architecture changed, install on
   the device, commit, push.

**Token discipline:** read `docs/ARCHITECTURE.md` + the specific files for the task.
Don't re-read the whole codebase. Use Grep/Glob for targeted lookups.

---

## ✅ QUALITY BAR (non-negotiable)

- **Lint clean** and **tests green** before you call anything done.
- **Pure logic gets a unit test** (`app/src/test`, runs on the JVM in seconds).
  **Real UI/gestures get an instrumented test** (`app/src/androidTest`, needs a device).
- **When you fix a bug, prove the fix**: write the regression test, verify it **FAILS on
  the old code**, then passes on the new. Say that you did that.
- If an existing test asserted the buggy behaviour, say so explicitly and replace it —
  don't bend the fix to keep a wrong test green.
- **Anything with real decision-making goes in `domain/` as a pure function** so it can
  be tested without a device. UI stays thin. `PostValidator` is the pattern to copy:
  no Android imports, clock passed in as a parameter.
- **Never mark a task done without a passing `assembleDebug`.**

## 📲 SHIPPING

- **Install on the emulator/device yourself** after each feature — don't wait to be
  asked. If the device is disconnected or unauthorized, say so and say what to tap.
- **Always end with `### 🧪 Manual Test Steps`** — numbered, exactly what to tap and
  what they should see.
- **Release ritual**, in this order: bump version → build → publish release with real
  notes → install. Ask for a quick "works?" before publishing anything public.

## 📖 IN-APP MANUAL

A feature isn't finished until the manual describes it — **same batch as the feature**,
not later. Describe **what the code actually does**, never what it was meant to do.
- Flag anything undiscoverable by tapping around as a **hidden gem** (gestures,
  long-press, swipe, shortcuts) with **search keywords in the owner's words**.
- Use **general, everyday examples** in user-facing text.
- After an update, show **what's new once**, kept in its own section until the next
  release replaces it.

*(The manual screen doesn't exist yet — it's scheduled into Phase 6. See ROADMAP.)*

## 🔬 RESEARCH BEFORE BUILDING ON PLATFORM FEATURES

If a feature depends on the OS or a third party (background uploads, notifications,
exact alarms, an API's rules), **check what's actually possible first — on the real
device/API, not from memory** — and give a go/no-go with options before writing it.
This project has already been bitten once by assuming a platform behaviour
(see Photo Picker URIs in `docs/STATUS.md`).

---

## 📁 DOCS — the project's memory

Keep them current; they are how the work survives a new chat, a compaction, or a month away.

```
docs/
  specs/YYYY-MM-DD-<topic>-design.md   the APPROVED design, written before building
  plans/YYYY-MM-DD-<feature>.md        implementation plan: bite-sized tasks
  STATUS.md                            living: shipped / in-flight / hard-won gotchas
  ROADMAP.md                           future ideas, sized S/M/L + rough approach
  ARCHITECTURE.md                      1-page technical map — read this, don't grep
  SETUP_GUIDE.md                       Meta + Cloudinary account steps (written)
  oauth/index.html                     OAuth bounce page, served by GitHub Pages
autoinsta_Master_Plan.md               phased roadmap + locked decisions
CLAUDE.md                              this file — the first thing a fresh chat reads
```

- **STATUS.md is where a mistake goes to die.** When something breaks, record the
  **root cause and the rule that prevents it**, not just the fix.
- **ROADMAP.md** catches ideas mentioned in passing so they aren't lost. Nothing there
  is started without the owner's say-so.

---

## 🧱 BUILT vs PLANNED

| Phase | What | State |
|---|---|---|
| 0 | Compose skeleton, builds + runs | ✅ Built |
| 1 | Room data layer — 5 tables, 5 DAOs, 4 repositories | ✅ Built |
| 2 | Compose-post UI — create/edit/delete, live queue | ✅ Built |
| 2.5 | Media durability, pure validation, test harness | ✅ Built |
| 3 | Scheduling engine — alarms, worker, boot re-arm, notifications | ✅ Built (publish is a stub) |
| 4 | Account connect — Business Login for Instagram, 60-day token | ✅ Built |
| 5a | Cloudinary upload + real Graph API publish | ✅ Built — **posts for real** |
| 5b | Media fitting editor — preview, manual crop, per-item pad/crop | ⏳ **Next** |
| 6 | Polish, presets screen, **in-app manual** | ⏳ Planned |
| 7 | Release prep — signing, R8 | ⏳ Planned |

**Today the app works end to end**: schedule a post, it fires on time, uploads to
Cloudinary, publishes to Instagram, records the result and notifies. Verified with a real
post to the live account on 2026-08-26.

⚠️ **Instagram accepts only 4:5 to 1.91:1 images, JPEG only.** `MediaFit` handles both by
transforming the Cloudinary *delivery URL* — the stored original is never touched.

⚠️ **Never put an OAuth login in a WebView** — Instagram's renders blank inside one, with
no error. Custom Tabs only. And **Meta rejects custom-scheme redirect URIs**; the app uses
an https bounce page (`docs/oauth/index.html`, served by GitHub Pages) that forwards to
`autoinsta://oauth`. See `docs/STATUS.md`.

## 🗄 DATA / SCHEMA HISTORY

| Version | Change |
|---|---|
| 1 | Initial: `scheduled_posts`, `media_items`, `hashtag_presets`, `post_history`, `account` |
| 1 (semantic) | `media_items.localUri` changed meaning: was a `content://` picker address, now an **app-private file path**. No schema change — the column is still `TEXT`. |
| 2 | `scheduled_posts.missedPolicy` added (per-post rule for a post whose time passed while the device was off). Real `Migration(1,2)`; **`fallbackToDestructiveMigration()` removed**. |

✅ Real migrations are in place (`data/db/Migrations.kt`). **Every schema change now needs
a migration there plus a case in `MigrationTest`** — schemas are exported to `app/schemas/`
and committed, so the test can prove an upgrade preserves data.

---

## 🧠 CODE CONVENTIONS

- **Kotlin + Compose (Material 3)** only. No Java, no XML layouts.
- **Layer-first packages** (`data/`, `domain/`, `ui/`) — this is a deliberate,
  confirmed decision. Don't migrate to feature-first.
- **MVVM:** Composables are dumb; state + logic in ViewModels; data access in Repositories.
- **All versions in `gradle/libs.versions.toml`** — never hardcode dep versions.
- **Coroutines + Flow** for async; `Dispatchers.IO` for DB/network/file I/O.
- **No network calls outside `data/remote`.** No Room calls outside `data/db`/repositories.
  **No file I/O outside `data/media`.**
- **No secrets in source** — always `BuildConfig.*`.
- **Graph API**: always via `InstagramApi`; never hardcode the version
  (use `BuildConfig.META_GRAPH_VERSION`).
- **Nullability:** model API responses as nullable DTOs; validate before use.
- One class per file; keep files small enough to hold in your head at once.
- Mirror the source structure in `test/` and `androidTest/`.

---

## 🧱 BUILD & RUN

Android Studio + SDK (platforms 33–36) and a bundled JDK are installed. Java/Gradle are
**not on PATH**, and **`gradlew`/`gradlew.bat` FAIL here** — the project path contains
non-ASCII chars (`C:\سطح المكتب\` = Arabic "Desktop") which cmd/bash mangle into a broken
classpath. Invoke the wrapper's main class directly with a **relative** classpath, from
the project root, in PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:JAVA_HOME\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dfile.encoding=UTF-8" `
  -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain `
  :app:assembleDebug --no-daemon --console=plain
```

Swap the task for `:app:testDebugUnitTest` (unit tests), `:app:connectedDebugAndroidTest`
(instrumented, needs a device), or `:app:lintDebug`.

Also required: `android.overridePathCheck=true` in `gradle.properties`.

**Build output goes to `C:/autoinsta-build`, not `app/build/`.** Anything Gradle *forks*
(test workers, lint, R8) receives a mangled classpath on this path because
`sun.jnu.encoding=Cp1252` cannot represent Arabic — see `docs/STATUS.md`. Source stays
put; only generated output moves. Override with `-PbuildRoot=<path>`.
Cycle time also dropped from ~5m to ~1m20s as a side effect.

**Install & launch** (Android Studio's Run has silently skipped the install step before):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\autoinsta-build\app\outputs\apk\debug\app-debug.apk"
& $adb shell am force-stop com.autoinsta
& $adb shell am start -n com.autoinsta/com.autoinsta.MainActivity
```
Emulator: `Pixel_10_Pro` via `$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe -avd Pixel_10_Pro`.

---

## 🔑 SECRETS
Real credentials live ONLY in `secrets.properties` (git-ignored) and reach code via
`BuildConfig`. Keys: `META_APP_ID`, `META_APP_SECRET`, `META_GRAPH_VERSION`,
`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET`, `OAUTH_REDIRECT_SCHEME`.
A committed `secrets.properties.example` documents the keys with empty values.

---

## 🚫 NEVER DO
1. Commit `secrets.properties` or any real API key / keystore.
2. Build a UI-automation / bot path that taps the real Instagram app — Graph API only (ToS-safe).
3. Add a backend server (v1 is on-device only — see Master Plan).
4. Put network, DB, or file I/O inside Composables or ViewModels — go through repositories.
5. Hardcode the Graph API version, account id, or any credential.
6. Fix unrelated bugs mid-task — log them under `## Noticed (not fixing now)`.
7. Skip the git checkpoint or the `docs/STATUS.md` update.
8. Mark a task done without a passing `assembleDebug` **and green tests**.
9. Persist a Photo Picker `content://` URI and expect to read it later — copy the file
   via `MediaFileStore` (see STATUS.md for why).

---

*Last updated: 2026-08-21. Update this file when architecture, a locked decision, or the
BUILT/PLANNED table changes.*
