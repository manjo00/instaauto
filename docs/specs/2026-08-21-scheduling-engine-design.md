# Design — Phase 3: Scheduling engine

**Date:** 2026-08-21 · **Status:** proposed, awaiting OK · **Phase:** 3

This is the phase that changes what the app *is*. Today it remembers what you want to
post and does nothing at the appointed time.

---

## Platform research — measured on the real device, not assumed

Device: **Samsung Galaxy Z Fold 7 (SM-F741B), Android 16 / API 36**, `targetSdk 35`.

| Question | Measured answer |
|---|---|
| Is Doze active? | Yes (`dumpsys deviceidle enabled` → 1) |
| Is autoinsta battery-exempt? | **No** — not on the whitelist, so Doze applies |
| Can apps get exact alarms here? | **Yes.** 87 apps request `SCHEDULE_EXACT_ALARM`; six hold it as `allow` |
| Do exact alarms actually fire? | Yes — live alarms show `exactAllowReason=policy_permission` |
| Doze budget *with* the permission | `allow_while_idle_quota=72` per `allow_while_idle_window=1h` |
| Doze budget *without* it | `allow_while_idle_compat_quota=7` per hour |
| Soonest an alarm may be set | `min_futurity=+5s` |
| App Standby buckets active? | Yes (`app_standby_enabled=1`) |

### What this changes

The master plan lists **"Doze reliability — exact alarms can slip overnight"** as an open
risk. On this device that risk is **much smaller than assumed**: a permission-holding app
gets **72 wake-ups per hour**, against a real need of a handful of posts per day. Doze is
not the binding constraint.

The binding constraint is different and was not on the risk list:

> **`SCHEDULE_EXACT_ALARM` is denied by default on API 34+.** It is a *special app access*,
> not a normal permission — it cannot be granted by a dialog. The user must be sent to
> Settings → Apps → autoinsta → Alarms & reminders and switch it on.

A second, quieter risk: **App Standby buckets are enabled.** autoinsta is a
"set it up, then don't open it for days" app, which is exactly the usage pattern that
lands an app in a `rare` or `restricted` bucket, where background allowances shrink.
`setExactAndAllowWhileIdle` still fires for a permission holder, but this is the thing
to actually measure in testing rather than trust.

**Go/no-go: GO.** Exact scheduling is achievable on this device.

---

## Options considered

| | Approach | Precision | Cost |
|---|---|---|---|
| A | **WorkManager only** | ±minutes to ~an hour in deep Doze | Zero permissions. Simplest. Survives reboot for free |
| B | **Exact alarm + WorkManager**, fall back to A when not granted | To the minute when granted | One-time trip to a Settings screen |
| C | `USE_EXACT_ALARM` | To the minute, auto-granted | **Play Store restricts this to alarm-clock and calendar apps.** Using it here risks rejection |

### Chosen: B

B is a strict superset of A — the fallback *is* A. So the only real question is whether
a one-time permission trip is worth to-the-minute firing, and for an app whose entire
purpose is "post at the time I chose", it is.

C is rejected on policy grounds. Even though release is currently optional and sideload,
building on a permission we would have to rip out before publishing is a bad trade.

### What "fall back to A" means concretely

If the user never grants the permission, the app still works — posts fire late rather
than not at all — and the queue shows an honest warning instead of silently under-delivering.
This matters: a scheduling app that quietly misses its times is worse than one that says
it might.

---

## Components

Layer-first, per the locked convention. New package `scheduler/`.

```
scheduler/
  PostScheduler.kt    arms and cancels alarms for a post
  AlarmReceiver.kt    BroadcastReceiver the alarm fires into; enqueues the worker
  PostWorker.kt       CoroutineWorker — does the publish (a STUB this phase)
  BootReceiver.kt     re-arms every scheduled post after reboot
domain/
  ScheduleCalculator.kt   pure: what fires when, what is overdue, what to do about it
data/prefs/
  (none this phase)
```

**Why an alarm *and* a worker.** They do different jobs. The alarm is a precise
doorbell — it wakes the device at a moment but gives you only a few seconds of
execution. The worker is a reliable workhorse — it survives process death and
reboots and can retry, but its *timing* is loose. Using both gets precision from the
alarm and durability from the worker: the alarm rings, and its only job is to hand the
real work to WorkManager.

**Why `ScheduleCalculator` is separate and pure.** Scheduling logic is the worst thing
to debug on a device, because verifying it means waiting for real time to pass. Anything
that decides *when* — is this overdue, should a missed post still go out, how far past
is too far — goes in a pure function with the clock passed in, the same pattern as
`PostValidator`. Then the awkward cases (missed by 30 seconds vs. missed by three days)
are unit tests that run instantly.

### Missed posts — chosen per post

If the phone is off when a post was due, the right answer is not the same for every
post: a "good morning" piece going out at 4pm is worse than not going out, while an
evergreen art piece is fine whenever. So this is a **per-post choice**, set on the
compose screen:

| Choice | Behaviour when the phone comes back |
|---|---|
| **Post it anyway** | Fires immediately, however late |
| **Post if under an hour late** *(default)* | Fires if within the grace period, otherwise `FAILED` |
| **Ask me first** | Never fires on its own; waits in the queue for a decision |

Stored as `ScheduledPostEntity.missedPolicy`. The grace period is a constant in
`ScheduleCalculator`, unit-tested, one number to change.

### This forces a schema change — and the first real migration

Adding `missedPolicy` bumps the DB to **version 2**. `fallbackToDestructiveMigration()`
is currently on, which means that bump would **silently wipe the queue**. Since this
change is what forces the bump, the migration is part of this phase rather than deferred:

- `Migration(1, 2)` — `ALTER TABLE scheduled_posts ADD COLUMN missedPolicy TEXT NOT NULL DEFAULT 'POST_IF_RECENT'`
- **`fallbackToDestructiveMigration()` is removed.**

That retires the "any schema change wipes the user's queue" risk from `STATUS.md`
permanently, and it is a handful of lines now versus a data-loss incident later.

---

## What PostWorker does this phase

A **stub**, per the master plan — no network, no Cloudinary, no Graph API. It:

1. Loads the post and its media.
2. Verifies every media file still exists on disk (this is where the Phase 2.5 work pays off).
3. Marks the post `POSTED`, writes a `PostHistory` row.
4. Fires a notification: *"Would have posted now."*

That exercises the entire pipeline end to end except the one part Phase 5 replaces.

## Permissions to add

| Permission | Why | How obtained |
|---|---|---|
| `SCHEDULE_EXACT_ALARM` | to-the-minute firing | Special access — deep-link to Settings, asked when the **first post is scheduled** (the moment the reason is obvious), re-offered from the queue warning |
| `RECEIVE_BOOT_COMPLETED` | re-arm alarms after reboot | Granted at install |
| `POST_NOTIFICATIONS` | tell the user it fired or failed | Runtime dialog (API 33+) |

## Testing a scheduler without waiting

The main practical problem with this phase is that verifying it honestly means waiting.
Three things make that bearable:

1. `ScheduleCalculator` unit tests — all the decision logic, instant, no device.
2. Instrumented tests driving `PostWorker` directly via `TestListenableWorkerBuilder`,
   so the publish path is tested without any alarm at all.
3. A **debug-only "fire in 15 seconds"** action on a queued post, so end-to-end timing
   can be checked in one sitting. Debug builds only — it never ships.

## Acceptance

- A post scheduled 2 minutes out fires within a few seconds of its time, with the app closed.
- Rebooting the phone re-arms pending posts; nothing is lost.
- Each missed-post choice behaves as specified; the default is not posted late.
- Upgrading from DB v1 to v2 **keeps existing posts** (migration test proves it).
- Permission denied → app still functions, queue warns that timing will be approximate.
- `ScheduleCalculator` fully unit-tested; `PostWorker` instrumented-tested.
- Lint clean, all tests green, installed and verified on the Fold 7.

## Out of scope this phase

Real publishing (Phase 5), account connect (Phase 4), retry/backoff tuning (Phase 6).
