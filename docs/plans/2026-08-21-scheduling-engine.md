# Plan — Phase 3: Scheduling engine

**Date:** 2026-08-21 · **Design:** [`specs/2026-08-21-scheduling-engine-design.md`](../specs/2026-08-21-scheduling-engine-design.md)

The phase that makes the app fire.

---

## Tasks

- [x] **1. Deps** — WorkManager + `work-testing` in the version catalog.
- [x] **2. Schema v2** — `MissedPostPolicy` enum, `missedPolicy` column, `Migration(1,2)`,
      **remove `fallbackToDestructiveMigration()`**, converter, migration test.
- [x] **3. `ScheduleCalculator`** — pure: due/overdue/grace decisions. Clock passed in.
- [x] **4. Unit tests** for `ScheduleCalculator`, covering each `MissedPostPolicy`
      and the grace boundary.
- [x] **5. `PostScheduler`** — arm/cancel exact alarms; report whether exact is available.
- [x] **6. `AlarmReceiver`** — receives the alarm, enqueues `PostWorker`.
- [x] **7. `PostWorker`** — stub publish: verify media on disk → mark POSTED → history → notify.
- [x] **8. `BootReceiver`** — re-arm everything after reboot, applying missed-post policy.
- [x] **9. Notifications** — channel in `AutoInstaApp`, `POST_NOTIFICATIONS` runtime request.
- [x] **10. Permission UX** — ask for exact-alarm on first schedule; persistent queue
      warning with a re-ask when not granted.
- [x] **11. Compose UI** — missed-post picker on the compose screen; queue shows status
      and the permission warning.
- [x] **12. Debug affordance** — "fire in 15 seconds" on a queued post, debug builds only.
- [x] **13. Instrumented tests** — `PostWorker` via `TestListenableWorkerBuilder`;
      migration v1→v2 keeps rows.
- [x] **14. Verify** — 32/32 unit, 25/25 instrumented on the Fold 7, lint 0 errors,
      `assembleDebug` green, installed. Alarms confirmed registered on-device via
      `dumpsys alarm` (`tag=*walarm*:com.autoinsta.action.POST_DUE`), including the
      10-second clamp from `alarmTimeFor`. **Real firing is the manual test** — no
      automated test can prove AlarmManager's own timing.
- [x] **15. Docs + ship** — STATUS, ARCHITECTURE, master plan, commit, push.

## Files

**New**
- `domain/model/MissedPostPolicy.kt`
- `domain/ScheduleCalculator.kt`
- `scheduler/PostScheduler.kt`, `AlarmReceiver.kt`, `PostWorker.kt`, `BootReceiver.kt`
- `data/db/Migrations.kt`
- `ui/components/PermissionBanner.kt`
- tests: `ScheduleCalculatorTest`, `PostWorkerTest`, `MigrationTest`

**Modified**
- `AndroidManifest.xml` — 3 permissions, 2 receivers
- `data/db/AppDatabase.kt` — v2, migration, no destructive fallback
- `data/db/Converters.kt` — `MissedPostPolicy`
- `data/db/entities/ScheduledPostEntity.kt` — `missedPolicy`
- `data/repository/PostRepository.kt` — schedule/cancel on write
- `ui/composepost/*`, `ui/home/*`
- `AutoInstaApp.kt` — notification channel, scheduler
- `gradle/libs.versions.toml`, `app/build.gradle.kts`

## Found while building

- **Lint caught a real defect.** `NotificationManagerCompat.notify()` needs
  `POST_NOTIFICATIONS`, and the guard was in a helper method where lint could not follow
  it. Inlined so it is provably safe.
- **Lint was also wrong once.** `SCHEDULE_EXACT_ALARM` is flagged `ProtectedPermissions`
  (system-apps-only). Its protection level is `signature|privileged|appop` — the `appop`
  part is exactly what lets a normal app hold it. Suppressed with the device evidence
  recorded in the manifest.
- **A test was arming real device alarms.** `MediaDurabilityTest` used a throwaway
  in-memory database with the real `PostScheduler`, leaving alarms keyed on ids that could
  collide with real posts. `PostScheduler` is now `open` and the test substitutes a no-op.
  The worker's execution-time re-check meant this was harmless, but it should not happen.
- **The emulator died twice** mid-run; the instrumented suite was run on the phone instead.

## Noticed (not fixing now)

- App Standby buckets are active and autoinsta is a low-engagement app; a `rare`/
  `restricted` bucket could still affect delivery. Measure over days, tune in Phase 6.
- `PostWorkerTest` runs against the real app database (because `PostWorker` reaches for
  `applicationContext as AutoInstaApp`). It cleans up after itself, but a DI seam would
  be better — logged with the Hilt item in ROADMAP.
- The emulator (`Pixel_10_Pro`) exits shortly after boot on this machine. Unblocked by
  using the phone; worth diagnosing if emulator testing becomes necessary.
