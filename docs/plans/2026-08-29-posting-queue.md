# Posting queue — implementation plan (Phase 5c)

Design: `docs/specs/2026-08-29-posting-queue-design.md`. Checkpoint: `5675896`.

Batches are commits. `main` stays green between them.

---

## Batch 1 — the pure core (JVM only)
- [x] `domain/model/TimingMode.kt` — `FIXED` | `QUEUED`
- [x] `domain/QueuePlanner.kt` — slot enumeration, catch-up, plan, `actionForQueued`
- [x] `domain/DragReorder.kt` — `targetIndexFor`, `move`
- [x] `test/domain/QueuePlannerTest.kt` (14+ cases incl. both DST edges)
- [x] `test/domain/DragReorderTest.kt`
- [x] `testDebugUnitTest` green

## Batch 2 — data layer
- [x] `PostingSlotEntity`, `QueueSettingsEntity`, their DAOs
- [x] 3 columns on `ScheduledPostEntity`, converters, `AppDatabase` v4
- [x] `MIGRATION_3_4` (+ seed the settings row)
- [x] `QueueRepository` with `replan()` behind a `Mutex`
- [x] `MigrationTest` 3->4 and 1->4
- [x] `assembleDebug` green

## Batch 3 — wiring
- [x] `AutoInstaApp` wires `QueueRepository`, schedules `QueueMaintenanceWorker`
- [x] `PostWorker` branches on `timingMode`; queued posts roll forward, never fail late
- [x] Post leaves the queue on POSTED / permanent FAILED, then replan
- [x] `BootReceiver` replans
- [x] `PostWorkerTest`: a queued post past its window rolls forward (fake publisher)

## Batch 4 — schedule screen
- [x] `ui/queue/ScheduleScreen.kt` + ViewModel: pause, window, slot list, add slot
- [x] Route from Settings and from the queue header
- [x] Plain-words preview of the next few slot times

## Batch 5 — compose-post + Home
- [x] Compose: "Add to queue" (default) vs "Pick an exact time"
- [x] Catch-up warning + "wait for the next slot instead" -> `notBeforeMillis`
- [x] Hide the per-post missed rule for queued posts, and say why
- [x] Home: Queue section (draggable, dated) + Set-times section + paused banner
- [x] `androidTest` `QueueReorderTest`

## Batch 6 — docs, manual, ship
- [x] `docs/manual/queue.md` (incl. hidden gems: long-press drag, catch-up, pause)
- [x] STATUS, ARCHITECTURE, CLAUDE.md (schema v4 + BUILT table), Master Plan, ROADMAP
- [x] lint 0 errors, unit tests green (162)
- [x] instrumented green on the Fold 7 (46), installed
- [x] Manual test steps handed over

## Found while verifying (fixed, outside the original scope)

- **Crash on launch after any backup restore.** `allowBackup` included the encrypted token
  file; Keystore keys are never backed up. Excluded it from backup and device transfer, and
  gave `TokenStore` a recovery path. Regression test proven to fail on the old code.
- **`AutoInstaApp` had no `CoroutineExceptionHandler`**, so background upkeep could take
  the process down. It no longer can.

---

## Noticed (not fixing now)

- **`queue_settings` is never seeded on a fresh install.** The seeding `INSERT` lives only
  in `MIGRATION_3_4`, and a database created directly at v4 runs no migrations — so an
  upgraded install has the row and a fresh one does not. Verified on the tablet: the table
  is empty and the queue works anyway, because `settings()` falls back to
  `QueueSettingsEntity()` and `upsert` creates the row on the first write. Harmless today,
  but the two install paths differ, and the migration's seed is only ever exercised by the
  upgrade. Worth making one of them authoritative.
- **One unexplained "Add slot" that appeared not to save.** On the very first cold-start
  interaction on the tablet, adding Wednesday 7:00 PM left the screen showing "No slots
  yet". Two immediate retries of the identical sequence both saved correctly, and the DB
  read that seemed to confirm the loss was unreliable (the `-shm` was copied alongside a
  live `-wal`, and an earlier copy used `adb shell`, which corrupts binary). Most likely a
  slow first Flow emission on a cold start rather than a lost write — **but unproven**. If
  it recurs, the thing to measure is the latency between `slotDao.insert` and the
  `observeAll()` emission on first subscription.

- **`HomeViewModel.fireSoon` (the debug ⚡ bolt) can be undone by a replan.** It writes a
  time 20 seconds out; any replan before the alarm fires overwrites it for a queued post.
  In practice nothing triggers a replan in that window, so it works — but it is luck, not
  design. Would want its own path if the bolt ever became more than a debug tool.
- **`ScheduleCalculator.MISSED_GRACE_MILLIS` (1h) and the queue's catch-up window are two
  separate ideas with the same shape.** They apply to different timing modes and should
  stay separate, but someone will eventually try to merge them. They are not the same
  thing: one decides whether to *fail* a post, the other whether a *slot* is still open.
