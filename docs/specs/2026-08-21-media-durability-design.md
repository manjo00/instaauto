# Design — Media durability + test harness

**Date:** 2026-08-21 · **Status:** approved · **Phase:** pre-Phase-3 hardening

---

## The problem in one paragraph

When the user picks a photo, Android's Photo Picker returns an address like
`content://media/picker/0/com.android.providers.media.photopicker/media/1000000033`
together with a **temporary read permission that is scoped to the app's process**.
We store that address in `MediaItemEntity.localUri` and intend to open it later —
possibly days later — from `PostWorker`. By then the app process has died at least
once and the permission is gone. The address still looks valid; reading it throws
`SecurityException`. A scheduling app is the exact worst case for this quirk,
because the gap between "pick" and "read" is the whole point of the product.

## Why the obvious fix doesn't work

`ContentResolver.takePersistableUriPermission()` is the standard answer to "make a
URI grant survive." It does **not** apply here: persistable grants are only offered
by URIs that come from a `DocumentsProvider` via `ACTION_OPEN_DOCUMENT`. Photo
Picker URIs are not persistable, and calling `takePersistableUriPermission` on one
throws. Switching to `OpenDocument` to get persistability would trade the photo-grid
UI for a file-browser UI, and *still* wouldn't survive the user deleting the original.

## Options considered

| | Approach | Verdict |
|---|---|---|
| A | `takePersistableUriPermission` on the picker URI | ❌ Not supported for Photo Picker URIs |
| B | Switch to `ACTION_OPEN_DOCUMENT` for persistable grants | ❌ Worse UX; still dies if the user deletes the original |
| C | **Copy picked media into app-private storage at save time** | ✅ **Chosen** |
| D | Copy lazily at publish time | ❌ Permission is already gone by then |

## Chosen design — C

At **save** time (not pick time — a user may add and remove media while drafting),
each newly-picked item is copied byte-for-byte into app-private storage:

```
<app filesDir>/media/<uuid>.<ext>
```

`MediaItemEntity.localUri` then holds that **app-private file path** instead of a
content URI. Consequences:

- **Survives** process death, reboot, and the user deleting the original from their gallery.
- **No quality loss** — a byte-for-byte stream copy, no decode/re-encode. Relevant
  because the whole pipeline is meant to be lossless up to Instagram's own ingestion.
- **Costs disk.** A 10-item carousel of 5 MB photos is ~50 MB held until the post publishes.
  Accepted: the queue is small and files are freed on delete/publish.
- **Requires file cleanup.** Room's `CASCADE` deletes media *rows*; it knows nothing
  about files. `PostRepository.deletePost` must read the paths first, then delete
  rows, then delete files.

### New component

`data/media/MediaFileStore.kt` — the only thing that touches media files on disk.
Named `MediaFileStore`, not `MediaStore`, to avoid confusion with Android's own
`MediaStore` API.

```
suspend fun import(sourceUri: Uri): String   // copy in, return app-private path
suspend fun delete(path: String)
suspend fun deleteAll(paths: List<String>)
```

### Editing an existing post

Media already imported must not be re-imported (that would duplicate files on every
save). `PickedMedia` therefore carries `isImported` — true for items loaded from the
DB, false for freshly-picked ones. Save imports only the latter.

### Draft previews

While drafting, thumbnails still render from the original content URI. That is safe:
the grant is alive for as long as the process that picked it, which is the same
process doing the drawing.

---

## Second change in this batch — testable logic

`ComposePostViewModel.validate()` currently holds the real decision-making (media
required, carousel 2–10, time must be in the future) *and* calls
`System.currentTimeMillis()` internally. That combination cannot be unit-tested —
it needs a device, and its result depends on the wall clock.

Moving it to `domain/PostValidator.kt` as a pure function with the clock passed in:

```
fun validate(postType, mediaCount, scheduledAtMillis, nowMillis): ValidationResult
```

Same rules, now verifiable on the JVM in milliseconds with no device and no flakiness.
The ViewModel keeps only the wiring: read state → call validator → surface message.

## Test harness

None exists today (no `app/src/test/`, no `app/src/androidTest/`, no test libraries).
Adding:

- **JUnit4 + kotlinx-coroutines-test** — pure logic (`PostValidator`, file-path helpers)
- **Room in-memory + androidx.test** — DAO and repository behaviour
- **Compose UI test** — the create/edit screen and the queue list

## Acceptance

- A regression test demonstrates the stale-media failure, **fails on today's code**, passes after the fix.
- Validation rules covered by pure unit tests.
- Deleting a post removes its files, not just its rows.
- `assembleDebug` green, `lint` green, all tests green.
