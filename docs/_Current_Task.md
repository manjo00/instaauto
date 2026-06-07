# Current Task — Project Bootstrap + Android Skeleton

**Date:** 2026-06-07
**Branch:** main

## Goal
Stand up the project the same way the `antigravity-clone` project is run: a `docs/`
brain (`_Architecture.md`, `_Current_Task.md`, `_Task_History.md`), a `CLAUDE.md`
operating manual, a master plan/roadmap, and a **buildable** Android skeleton so we
have a green baseline to grow features on.

## Files to touch
- `CLAUDE.md` — operating manual (git rule, how-a-session-works, conventions)
- `docs/*` — architecture, current task, task history
- `autoinsta_Master_Plan.md` — full roadmap (phases) + feature spec
- `prompt.md` — open-items scratchpad
- Android skeleton — gradle config, manifest, minimal Compose app that launches

## Acceptance criteria
- [x] git repo initialized, `.gitignore` in place
- [x] `docs/` brain created
- [x] `CLAUDE.md` written and adapted for Android/Kotlin + Graph API
- [x] `autoinsta_Master_Plan.md` written with phased roadmap
- [x] `assembleDebug` produces an APK (verified from CLI — BUILD SUCCESSFUL,
      app/build/outputs/apk/debug/app-debug.apk)
- [x] App installs + launches on Pixel_10_Pro emulator, Home screen renders, no crash
      (verified via adb install + screencap; process alive). **PHASE 0 DONE.**

## Build note (IMPORTANT — non-ASCII project path)
The project sits under `C:\سطح المكتب\` (Arabic "Desktop"). Two consequences:
1. `gradlew` / `gradlew.bat` FAIL here — cmd/bash mangle the non-ASCII path in the
   classpath. Build via direct java + relative classpath instead (see CLAUDE.md
   "BUILD & RUN").
2. `android.overridePathCheck=true` is set in gradle.properties — required, or AGP
   refuses to build. Verified the full native pipeline (AAPT2/dex/native-libs) works.

## Noticed (not fixing now)
- gradlew wrapper scripts unusable at this path (documented; not blocking — direct
  java build works, and Android Studio invokes Gradle via its own embedded runner).
