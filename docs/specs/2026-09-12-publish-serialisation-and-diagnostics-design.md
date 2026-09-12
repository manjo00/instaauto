# Design — one publish at a time, and an app that can explain itself

**Approved 2026-09-12.** Triggered by a real incident on Wednesday 2026-09-09.

---

## What actually happened

Three separate faults, established from the device rather than guessed.

### 1. The alarm never fired — Android throttled it

```
am get-standby-bucket com.autoinsta   →  40   (RARE)
dumpsys deviceidle whitelist          →  not listed
dumpsys alarm                         →  policyWhenElapsed: … app_standby=-2d8h44m21s …
```

The app sits in the **RARE** standby bucket and is not exempt from battery optimisation.
In that bucket `setExactAndAllowWhileIdle` is throttled to roughly **one firing per 24
hours**, and `dumpsys alarm` names `app_standby` as the policy that deferred it. The 19:00
Wednesday slot simply never fired. Opening the app on Thursday promoted the bucket and
released it ~13 hours late.

The alarm code was already correct. This is a device-policy problem, and it is the
long-flagged "App Standby risk" in `CLAUDE.md` — now confirmed, on a Samsung tablet, which
is the worst case for it.

### 2. A failed post hands its slot straight to the next post

From `post_history`, both rows carrying **the same** `scheduledAt` of Wed 19:00:

| Attempted | Post | Outcome |
|---|---|---|
| Thu 07:58:02 | 5 | FAILED — *"Only photo or video can be accepted"* |
| Thu 07:58:48 | 2 | POSTED |

`ScheduledPostDao.getFilledSlotTimes` selects `status = 'POSTED'` only, so a **failed** post
leaves its slot looking untouched. Post 5 failed → `leaveQueueIfQueued` → `replan()` → the
Wed 19:00 slot is still open inside the 48-hour catch-up window → post 2 inherits it → that
time is in the past → it fires immediately. 46 seconds.

It stopped at two only because post 2 *succeeded* and finally filled the slot. Had it also
failed, posts 3 and 4 would have followed within the minute.

### 3. A publishing post is invisible to the planner

`getQueuedIdsInOrder` is `status = 'SCHEDULED'`; `getFilledSlotTimes` is `status = 'POSTED'`.
A post in **POSTING** matches neither. For the 30–90 seconds a publish takes it exists in no
query the planner reads, so any concurrent `replan()` — and one runs on every app launch —
can hand its slot to another post. This is the window the owner described.

---

## The decisions taken

The owner chose all three recommendations.

1. **On failure, walk to the next post only when the failure was that post's fault.**
2. **Diagnostics: an event-log table plus a USB tool**, no extra UI.
3. **Include the battery-optimisation fix**, since it is why the incident happened at all.

---

## Part A — a publish lease

**One post publishes at a time, across the whole app.**

An in-memory mutex is not enough: the publisher runs in a `CoroutineWorker` that the system
can kill mid-flight, and a plain boolean flag in the database would then deadlock the queue
forever. So the lock is a **lease** — a holder plus a timestamp, which expires.

`queue_settings` gains two columns:

| Column | Meaning |
|---|---|
| `publishingPostId: Long?` | who holds the lease |
| `publishingSinceMillis: Long?` | when they took it |

Claimed with a single conditional `UPDATE` returning the row count, so the check and the
write cannot interleave:

```sql
UPDATE queue_settings
   SET publishingPostId = :postId, publishingSinceMillis = :now
 WHERE id = 1
   AND (publishingPostId IS NULL
     OR publishingPostId = :postId          -- our own retry
     OR publishingSinceMillis < :staleBefore)
```

`1` = acquired, `0` = someone else is publishing. Released in a `finally`, and only if we
still hold it, so a post that stole an expired lease is never cleared by the original owner
finishing late.

**Lease length** must exceed the longest legitimate publish. A Reel is polled for readiness
per `PublishPolicy.PollCadence`, so the lease is derived from that ceiling plus a margin
rather than being a guessed constant.

**A blocked post does not fail.** It logs, returns without publishing and stays SCHEDULED in
the pool. The holder's completion triggers `replan()`, which gives it a fresh time — by
which point the holder's slot is correctly marked spent, so it receives the *next* slot
rather than the one just used.

## Part B — slot accounting

The planner already accepts `filledSlotTimes`. The fix is to compute that set honestly.

A new pure function, `QueuePlanner.spentSlots`, decides per slot. Pure so it is unit
testable without a device — the rule is the whole feature, and it must not live in SQL:

| Condition | Slot spent? | Why |
|---|---|---|
| Any post POSTED into it | **yes** | it did its job |
| A post currently POSTING into it | **yes** | in flight — closes fault 3 |
| Latest attempt was a **transient** failure | **yes** | no network or a dead token will fail the next post identically; marching on would mark the whole queue FAILED |
| Attempts ≥ `MAX_ATTEMPTS_PER_SLOT` (3) | **yes** | a run of bad files must not drain the pool |
| Only permanent failures, under the cap | no | that post's media was the problem; the next deserves the slot |

Telling transient from permanent requires storing it, so `post_history` gains
`failureKind: TEXT?` (`PERMANENT` / `TRANSIENT` / null). It doubles as diagnostics.

## Part C — diagnostics

New table `app_events`: `atMillis`, `category`, `event`, `postId?`, `detail?`.

Written at every point that has ever been guessed at: alarm armed / fired / cancelled,
worker start and its decision, lease acquired / blocked / released / stolen, each publish
stage, every failure with its kind, replan summaries, boot, app launch, token refresh.

Capped by age so it cannot grow without bound. Read with a committed script that pulls the
database over `adb exec-out run-as` — the same route used to diagnose this incident — and
renders a timeline.

**The point:** this incident took a database pull and three `dumpsys` calls to understand,
and that only worked because the tablet was to hand and the evidence happened to survive.
The next one should be answerable from the log alone.

## Part D — battery optimisation

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, a one-tap request, and a banner whenever the app
  is not exempt, because this silently breaks unattended posting and nothing else reveals it.
- The standby bucket is recorded into `app_events` on every launch, so a repeat is visible
  rather than inferred.
- Samsung keeps a separate "Deep sleeping apps" list that **cannot** be changed from code;
  that one goes in the manual as steps.

This permission is Play-restricted. Irrelevant here — the app is not published, the same
basis on which the API key ships inside the APK.

---

## Schema v5

One migration covering: two columns on `queue_settings`, `failureKind` on `post_history`,
and the `app_events` table. Needs `Migration(4,5)` plus a `MigrationTest` case, per the
project rule that every schema change proves data survives the upgrade.

## What this explicitly does not do

- **It does not make the alarm fire in the RARE bucket.** Only the exemption does, and the
  user must grant it. The banner and the log make the state visible; they cannot force it.
- **It does not retry a permanently rejected post.** Post 5 failed because Instagram would
  not take that media; that is correct and stays correct.
