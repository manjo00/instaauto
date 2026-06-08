# Current Task — Phase 1: Data Layer

**Date:** 2026-06-08
**Branch:** main

## Goal
Build the Room database that stores every persistent piece of state the app needs:
scheduled posts, their media files, hashtag presets, post history, and the connected
Instagram account. Expose each table through a repository so ViewModels and
PostWorker have a clean single access point. Nothing UI-facing yet — pure data plumbing.

## Files to touch

### New — domain models
- `domain/model/PostType.kt`     — enum: SINGLE_IMAGE, REEL, CAROUSEL
- `domain/model/PostStatus.kt`   — enum: SCHEDULED, POSTING, POSTED, FAILED, CANCELLED
- `domain/model/MediaType.kt`    — enum: IMAGE, VIDEO

### New — Room entities
- `data/db/entities/ScheduledPostEntity.kt`
- `data/db/entities/MediaItemEntity.kt`
- `data/db/entities/HashtagPresetEntity.kt`
- `data/db/entities/PostHistoryEntity.kt`
- `data/db/entities/AccountEntity.kt`
- `data/db/relations/ScheduledPostWithMedia.kt`

### New — DAOs
- `data/db/dao/ScheduledPostDao.kt`
- `data/db/dao/MediaItemDao.kt`
- `data/db/dao/HashtagPresetDao.kt`
- `data/db/dao/PostHistoryDao.kt`
- `data/db/dao/AccountDao.kt`

### New — DB setup
- `data/db/Converters.kt`    — TypeConverters for enums
- `data/db/AppDatabase.kt`   — RoomDatabase, version 1

### New — Repositories
- `data/repository/PostRepository.kt`
- `data/repository/PresetRepository.kt`
- `data/repository/AccountRepository.kt`
- `data/repository/HistoryRepository.kt`

### Modified
- `gradle/libs.versions.toml`  — add KSP + Room versions
- `build.gradle.kts` (root)    — add KSP plugin apply false
- `app/build.gradle.kts`       — apply KSP, add Room deps
- `AutoInstaApp.kt`            — instantiate AppDatabase singleton

## Acceptance criteria
- [x] AppDatabase compiles (KSP generates Room code without error — kspDebugKotlin ✅)
- [x] All 5 entities present with correct relations and indices
- [x] All 5 DAOs compile with Flow-returning queries
- [x] Repositories wrap DAOs cleanly (no Room calls escape the repository layer)
- [x] Builds: assembleDebug — BUILD SUCCESSFUL in 1m 38s, 38 tasks executed
- [x] Git committed

## Noticed (not fixing now)
(none)
