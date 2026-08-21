# Plan — Media durability + test harness

**Date:** 2026-08-21 · **Design:** [`specs/2026-08-21-media-durability-design.md`](../specs/2026-08-21-media-durability-design.md)

Pre-Phase-3 hardening. Phase 3 builds the thing that reads media at publish time, so
the media has to be trustworthy first.

---

## Tasks

- [x] **1. Test harness** — JUnit4, coroutines-test, Turbine (unit); androidx.test,
      Room in-memory, Compose UI test (instrumented). Enable `lint { abortOnError = true }`.
- [x] **2. Extract validation** — move the rules out of `ComposePostViewModel` into a
      pure `domain/PostValidator.kt` with the clock passed in. ViewModel keeps only
      the reason→sentence mapping.
- [x] **3. Unit tests for the rules** — `PostValidatorTest`, 14 cases including the
      boundaries (1 vs 2 carousel items, 10 vs 11, now vs now+1ms).
- [x] **4. `MediaFileStore`** — copy picked media into `filesDir/media/<uuid>.<ext>`.
      Stream copy, no re-encode. Refuses to delete anything outside its own directory.
- [x] **5. Repository wiring** — `MediaToSave` carries `alreadyImported` so edits don't
      re-copy. `deletePost` and `updatePost` clean up files, not just rows.
- [x] **6. Regression tests** — `MediaDurabilityTest` asserts scheduled media is
      readable as a plain file, is byte-identical to the source, survives the original
      being deleted, and leaves no orphaned files.
- [x] **7. Coil fix** — `mediaModel()` wraps app-private paths in `File` so previews
      still render after the storage change.
- [x] **8. Docs** — spec, this plan, `STATUS.md`, `ROADMAP.md`; fold the workflow rules
      into `CLAUDE.md`.
- [x] **9. Verify** — 14/14 unit tests, 14/14 instrumented tests, lint 0 errors,
      `assembleDebug` green. **Regression proven:** all 7 durability tests fail on the
      pre-fix code (`scheduledMediaIsReadableAsAPlainFile` reported the stored value as
      `file:///.../original.jpg` rather than a readable path).
- [ ] **10. Ship** — conventional commit, private GitHub repo, push.

## Files touched

**New**
- `domain/PostValidator.kt` — pure validation rules + `PostValidation` result type
- `data/media/MediaFileStore.kt` — app-private media copies
- `ui/components/MediaModel.kt` — Coil model helper
- `app/src/test/.../PostValidatorTest.kt`
- `app/src/androidTest/.../MediaDurabilityTest.kt`
- `app/src/androidTest/.../MediaFileStoreTest.kt`
- `docs/specs/`, `docs/plans/`, `docs/STATUS.md`, `docs/ROADMAP.md`

**Modified**
- `data/repository/PostRepository.kt` — `MediaToSave`, import on save, file cleanup
- `ui/composepost/ComposePostViewModel.kt` — uses `PostValidator`; `PickedMedia.isImported`
- `ui/composepost/ComposePostScreen.kt` — `AutoMirrored` back icon, `PostValidator` limits, `mediaModel`
- `ui/home/HomeScreen.kt` — `mediaModel`
- `AutoInstaApp.kt` — owns `MediaFileStore`
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — test deps, lint config

## Out of scope (deliberately)

- Folder restructure to feature-first — decided against; layer-first stays.
- Compose UI tests — harness is in place, tests deferred to Phase 6 (logged in ROADMAP).
- Real Room migrations — logged as debt in ROADMAP.

## Noticed (not fixing now)

- `prompt.md` at the repo root is a stray scratchpad superseded by `docs/ROADMAP.md`.
- `docs/_Architecture.md`, `_Current_Task.md`, `_Task_History.md` are the old docs
  layout, now replaced by spec/plan/STATUS/ROADMAP.
