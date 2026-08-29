# Posting queue — implementation plan (Phase 5c)

Design: `docs/specs/2026-08-29-posting-queue-design.md`. Checkpoint: `5675896`.

Batches are commits. `main` stays green between them.

---

## Batch 1 — the pure core (JVM only)
- [ ] `domain/model/TimingMode.kt` — `FIXED` | `QUEUED`
- [ ] `domain/QueuePlanner.kt` — slot enumeration, catch-up, plan, `actionForQueued`
- [ ] `domain/DragReorder.kt` — `targetIndexFor`, `move`
- [ ] `test/domain/QueuePlannerTest.kt` (14+ cases incl. both DST edges)
- [ ] `test/domain/DragReorderTest.kt`
- [ ] `testDebugUnitTest` green

## Batch 2 — data layer
- [ ] `PostingSlotEntity`, `QueueSettingsEntity`, their DAOs
- [ ] 3 columns on `ScheduledPostEntity`, converters, `AppDatabase` v4
- [ ] `MIGRATION_3_4` (+ seed the settings row)
- [ ] `QueueRepository` with `replan()` behind a `Mutex`
- [ ] `MigrationTest` 3->4 and 1->4
- [ ] `assembleDebug` green

## Batch 3 — wiring
- [ ] `AutoInstaApp` wires `QueueRepository`, schedules `QueueMaintenanceWorker`
- [ ] `PostWorker` branches on `timingMode`; queued posts roll forward, never fail late
- [ ] Post leaves the queue on POSTED / permanent FAILED, then replan
- [ ] `BootReceiver` replans
- [ ] `PostWorkerTest`: a queued post past its window rolls forward (fake publisher)

## Batch 4 — schedule screen
- [ ] `ui/queue/ScheduleScreen.kt` + ViewModel: pause, window, slot list, add slot
- [ ] Route from Settings and from the queue header
- [ ] Plain-words preview of the next few slot times

## Batch 5 — compose-post + Home
- [ ] Compose: "Add to queue" (default) vs "Pick an exact time"
- [ ] Catch-up warning + "wait for the next slot instead" -> `notBeforeMillis`
- [ ] Hide the per-post missed rule for queued posts, and say why
- [ ] Home: Queue section (draggable, dated) + Set-times section + paused banner
- [ ] `androidTest` `QueueReorderTest`

## Batch 6 — docs, manual, ship
- [ ] `docs/manual/queue.md` (incl. hidden gems: long-press drag, catch-up, pause)
- [ ] STATUS, ARCHITECTURE, CLAUDE.md (schema v4 + BUILT table), Master Plan, ROADMAP
- [ ] lint 0 errors, unit + instrumented green, install on the Fold 7
- [ ] Manual test steps handed over

---

## Noticed (not fixing now)
_(anything unrelated spotted along the way goes here)_
