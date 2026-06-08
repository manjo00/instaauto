# Current Task — Phase 2: Compose-post UI (local only)

**Date:** 2026-06-08
**Branch:** main

## Goal
Build the screen where the user creates/edits a scheduled post — pick media (single
file or 2–10 for a carousel), write a caption, attach hashtags (from a saved preset
or free text), and pick a date+time — and persist it via `PostRepository`. Rework
the Home screen into a live queue of scheduled posts with delete and tap-to-edit.
No real posting yet, no scheduling engine — pure Compose + Room persistence, wired
together with Navigation-Compose.

## Files to touch

### New
- `ui/composepost/ComposePostViewModel.kt` — form state, media picking, validation, save (insert or update)
- `ui/composepost/ComposePostScreen.kt` — post-type selector, media picker/preview, caption, hashtags + preset picker, date/time pickers
- `ui/home/HomeViewModel.kt` — observes scheduled queue, exposes delete

### Modified
- `ui/home/HomeScreen.kt` — rewrite: LazyColumn queue list, FAB to create, tap to edit, delete with confirm
- `MainActivity.kt` — NavHost wiring `home` ↔ `composePost?postId={id}`
- `data/repository/PostRepository.kt` — add `updatePost(post, mediaItems)` that replaces media on edit
- `gradle/libs.versions.toml` — add navigation-compose, lifecycle-viewmodel-compose, material-icons-extended, coil-compose
- `app/build.gradle.kts` — wire the new deps

## Acceptance criteria
- [x] Can create a SINGLE_IMAGE / REEL / CAROUSEL post: pick media, caption, hashtags (preset or free text), date+time, Save persists to Room
- [x] Home queue shows live list from `observeScheduled()`, newest-time first
- [x] Tap a queued post opens it pre-filled for edit; Save updates the row + media
- [x] Delete removes the post (and cascades media) with a confirmation dialog
- [x] Builds: assembleDebug — BUILD SUCCESSFUL in 58s, APK at app/build/outputs/apk/debug/app-debug.apk
- [x] Git committed

## Noticed (not fixing now)
- `Icons.Filled.ArrowBack` is deprecated in favor of `Icons.AutoMirrored.Filled.ArrowBack`
  (compiler warning in ComposePostScreen.kt). Cosmetic, harmless — fix opportunistically
  next time that file is touched.
