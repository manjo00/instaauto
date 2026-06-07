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
- [ ] Android project opens in Android Studio
- [ ] `./gradlew assembleDebug` produces an APK (verified from CLI)
- [ ] App launches to a placeholder Home screen, no crash

## Noticed (not fixing now)
- (none yet)
