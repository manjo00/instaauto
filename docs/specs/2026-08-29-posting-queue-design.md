# Posting queue — design (Phase 5c)

**Approved 2026-08-29.** Written before any code, per the project workflow.

---

## The problem

Every post currently carries its own fixed date and time. That forces the "when"
decision at the moment a piece is finished — the worst moment to make it — and it
produces an irregular posting rhythm on an account whose reach depends on consistency.

## What we're building

The Buffer model, in the owner's words: *"a folder that always checks in the same days
and time appointed by the user; if in the pool of uploads there's a queued post or reel
it will post it, if not skip that day"*, plus *"the ability to rearrange queued posts at
any time and add to them after I finish a piece or a timelapse"*.

Two objects:

- a **posting schedule** — recurring slots, each one day-of-week + time-of-day
- a **pool** — finished posts in an order the owner controls

Each slot pulls the next post off the pool. Empty pool → nothing happens that day.

## Decisions

| Question | Decision |
|---|---|
| Queue vs fixed time | **Both.** Queue is the default on the compose screen; an exact time stays available for a dated drop. |
| Slot shape | A **flat list** of day+time slots. Handles "Saturday at a different time" that a days x times grid cannot. |
| Reordering | **Drag** (long-press, drag, release). |
| Missed slot | A configurable **catch-up window**: 1h / 2h / 1 day / 2 days. |
| Empty-pool catch-up | The open slot **is** filled by a newly added post — but the compose screen says so first, with "wait for the next slot instead" one tap away. |
| Several slots missed | Publish **one**, shift the rest forward. |
| Pause | **Yes.** One toggle; nothing fires, the pool is untouched. |

### The catch-up window, precisely

A slot that has passed stays **open** for the length of the window. An open slot can be
filled by either a post the phone failed to publish (it was off, or in Doze) or a post
added afterwards (the pool was empty at the time). Once the window closes, the slot is
gone and everything waits for the next one.

At most **one** catch-up publish per replan. Two or three posts landing minutes apart
reads as a glitch to followers and wastes the reach on all but the first.

Slots that passed while the queue was **paused** are never caught up on resume — pausing
means "do not post", and honouring it retroactively would be a betrayal of the toggle.

## Approach

**Keep the alarm to worker to publish chain exactly as it is.** A new pure `QueuePlanner`
hands out the times; queued posts still end up with a real `scheduledAt`, so nothing about
publishing changes and nothing already tested is disturbed.

"Skip the day when the pool is empty" then costs nothing: no post means no assignment,
which means no alarm, which means nothing fires. There is no "empty slot" object at all.

### The one rule that must not blur

`queuePosition` is the **truth** for order. `scheduledAt` on a queued post is **derived**
by the planner, and is read only by the alarm machinery and by the UI for display. Two
fields that both look like they mean "when" will drift into disagreement unless which one
is authoritative is stated and kept stated.

## Architecture

```
domain/QueuePlanner.kt     pure: slots + order + now -> times. No Android, no clock, no zone of its own.
domain/DragReorder.kt      pure: the index arithmetic behind the gesture.
data/db/...                posting_slots, queue_settings, 3 new columns on scheduled_posts (schema v4)
data/repository/QueueRepository.kt   the only thing that applies a plan
scheduler/PostWorker.kt    one branch: QUEUED posts roll forward instead of failing
scheduler/QueueMaintenanceWorker.kt  daily replan — the safety net
ui/queue/ScheduleScreen.kt slots, pause, catch-up window
ui/home/HomeScreen.kt      queue section (draggable) + fixed section
ui/composepost/            "Add to queue" vs "Pick an exact time"
```

### Planner rules, in order

1. Paused, or no enabled slots -> everything unassigned. No times, no alarms.
2. At most one catch-up: a slot time `t` with `t <= now`, `now - t <= window`, and
   `t >= resumedAtMillis` goes to the first eligible queued post.
3. Everyone else takes the next future slot times, in queue order.
4. A slot within 30 minutes of a **fixed** post's time is skipped, so the two never
   collide.
5. A post carrying `notBeforeMillis` skips any slot earlier than that.

Daylight saving is delegated to `java.time`: a local slot time that does not exist
(spring forward) shifts forward; one that happens twice (fall back) takes the first.

### When a plan is applied

`QueueRepository.replan()` runs on app launch, any queue mutation, a slot or settings
change, after a publish finishes, on boot, and from a **daily** `QueueMaintenanceWorker`.
The daily job covers the one gap the others miss: a pool left empty for weeks, where
nothing publishes and so nothing else would ever trigger a replan.

Alarms are armed only for assignments inside 7 days (plus always the first, so a sparse
schedule still fires). A post three months out has a displayed date and no alarm until it
comes into range.

## Error handling

- A queued post past its window is **never** marked FAILED for lateness. It keeps its
  place, the planner gives it a new time.
- A permanent publish failure removes the post from the queue (`queuePosition = null`,
  status FAILED) and the rest shuffle up — the existing failure notification still fires.
- A transient failure keeps today's behaviour: retry with backoff, post stays SCHEDULED.
- `replan()` holds a `Mutex`. It is triggered from six places and must not interleave.

## Testing

Everything with a decision in it is pure, so the interesting cases — a slot missed by
three days, a phone off over a DST change, a queue reordered mid-week — are unit tests
that run in milliseconds instead of situations that have to be waited for.

The gesture itself and the migration need a device; the arithmetic behind the gesture does
not, which is why `DragReorder` is separate from the composable that uses it.

## Deliberately not in scope

- Slot-specific rules ("Saturdays are Reels only") — no evidence it is wanted yet.
- Auto-filling the pool from a watched device folder — a post needs a caption, which a
  folder cannot supply.
- Shuffling the pool randomly — the owner asked for control over order, not the absence
  of it.
