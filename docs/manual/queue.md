# Manual — The queue

> **Source of truth for the in-app manual.** The manual *screen* is Phase 6 and does not
> exist yet; this is the content it will render. Everything below describes **what the
> code actually does**, not what it was meant to do.
>
> Entries carry search keywords in the owner's own words.

---

## What's new — one at a time, and a reason for everything

*(Keep this section until the next release replaces it. Shown once after updating.)*

After the 9 September mix-up, three things changed.

**Only one post can publish at a time.** Publishing a Reel can take a minute or more, and
during that minute the queue used to forget it was busy — which is how two posts went out
46 seconds apart into the same slot. Now the queue holds a "someone is posting" marker
until that post finishes.

**A failure no longer runs down the list.** If a post fails because Instagram rejected
*that file*, the next one gets the slot — that part was right and still works. But if it
failed because of **no connection or a login problem**, the queue now stops, because the
next post would fail for the same reason and you'd lose the lot. Either way it never tries
more than three in one slot.

**The app now keeps a diary.** Every alarm, decision and publish is written down for two
weeks, so when something goes wrong there is a record of it rather than a guess.

⚠️ **Also check the battery banner on the queue screen** — that is why 9 September happened
at all. See the section near the end.

---

## Setting your posting times

**Keywords:** posting times, schedule, slots, when do my posts go out, days and times,
set a routine, posting days

Tap the **clock icon** at the top of the queue, or **Settings → Posting schedule**.

Tap **Add slot**, pick a day and a time, tap Add. Each slot is one day and one time —
"Monday 7:00 PM". Add as many as you want, and they can all be different: Monday and
Wednesday at 7pm with Saturday at 11am is fine.

Under **Next up** you'll see the real dates the next few posts would go out on. That's
worth a glance — it's the quickest way to notice a slot that says something other than
what you meant.

**A slot with nothing waiting is skipped.** The app doesn't post filler and doesn't warn
you; the day just passes.

---

## Adding a piece to the queue

**Keywords:** add post, new post, finished a drawing, timelapse, queue it, upload later

Tap **New post**, pick your media, write your caption — same as always. Under **When**,
**Add to queue** is already selected. The card underneath tells you exactly where it will
land: *"Goes out Wed, Sep 9 · 7:00 PM — number 3 in the queue."*

New posts go to the **back** of the queue, which is usually what you want after finishing
a piece.

If you'd rather pin something to a specific date — a collab, a launch, a birthday — tap
**Pick a time** instead. Those posts sit in their own **Set times** section on the queue
screen and the queue leaves them alone.

---

## 💎 Hidden gem — press and hold to reorder

**Keywords:** reorder, rearrange, move post, change order, drag, swap, which goes first,
put this one first

**Press and hold any card in the Queue, then drag it up or down.** The card lifts and
follows your finger, and a blue line shows where it will land. Let go and the order is
saved — every date reshuffles to match.

There's also a **drag handle** (the ≡ icon) on the right of each card if you'd rather grab
that. It starts dragging immediately, no holding needed.

Drag near the top or bottom edge and the list scrolls by itself, so you can move something
from the bottom of a long queue to the top in one gesture.

**Reels show a real frame, not a film icon.** So you can tell three timelapses apart at a
glance. The frame is taken about 85% of the way through — far enough that a speedpaint
shows the finished piece, before any outro card. A small ▶ badge marks it as a video.

**Only Queue cards move.** Posts under **Set times** aren't draggable — they have a date
you chose, so there's nothing to reorder.

---

## 💎 Hidden gem — a slot stays open for a while after it passes

**Keywords:** missed, phone was off, late, didn't post, caught up, posted late, why did it
post now, catch up

Under **Posting schedule → If a slot is missed** you choose how long a posting time stays
open after it passes: **1 hour, 2 hours, 1 day, or 2 days.**

While a slot is still open, two things can fill it:

- **A post that was waiting but couldn't go out** — your phone was off, or asleep. It
  publishes as soon as the phone is back.
- **A post you add afterwards.** If your 7pm Monday slot passed with nothing in the queue
  and you finish a piece at 8pm, that post can still take the 7pm slot — meaning it goes
  out straight away.

The app **tells you before that happens.** The New post screen will say *"This will post
now — it fills Mon, Sep 7 · 7:00 PM, which passed 1h ago"* in red, with a button to
**wait for the next slot instead**. Nothing goes out by surprise.

Once the window closes, the slot is gone and everything just waits for the next one.

**Only one post catches up at a time.** If your phone was off all weekend and two or three
slots went by, one post goes out and the rest shift forward. Three posts landing a minute
apart looks broken to your followers and wastes the reach on all but the first.

**A queued post is never marked failed for being late.** It keeps its place and takes the
next slot. That's different from a post with a set time, which *can* be marked missed —
because with those, the time was the whole point.

---

## Saved hashtag sets

**Keywords:** hashtags, tags, preset, saved set, reuse hashtags, same tags every time,
retyping hashtags

**Settings → Hashtag sets**, or **Manage sets** from the top-right of the Hashtags box on
any post.

Tap **New set**, give it a name ("Digital art", "Timelapses"), paste the tags, Save.
Anything that isn't a #tag is dropped when you save, so you can paste a messy block with
commas and line breaks and it comes out tidy.

On a post, **Add a saved set** drops the tags into the Hashtags box.

**It adds, it doesn't replace.** If you'd already typed two tags for that particular
piece, they stay — the set's tags are appended after them. Tags you already have are
skipped, so tapping the same set twice does nothing rather than doubling everything.
`#Art` and `#art` count as the same tag.

Under the box you'll see **"3 of 5 tags"**. **Five is Instagram's limit** — it dropped from
thirty in December 2025 and it's enforced, not advice. Go over and the box turns red; the
extra tags get dropped or the post is refused. Putting them in the first comment does *not*
buy extra slots.

Because of that, **don't build one big set and use it on everything.** Repeating the same
five tags on every post reads as spam to the algorithm. Keep a few small sets — one per
kind of piece — and pick the one that fits.

## The Done tab — everything that has gone out

**Keywords:** history, posted, what went out, failed, didn't post, past posts, send again,
repost, post it again, log

Along the top of the queue screen there are two tabs: **Queue** and **Done**. Done lists
everything that has been through the publisher, newest first — successes and failures both.

Each row tells you what happened: *"Posted Wed, Sep 9 · 7:00 PM"*, or the reason it didn't
go out in red. If a post failed a few times before it worked, the row says
*"after 1 failed attempt"* rather than filling the list with entries.

**One row per post, not per attempt.** A piece that failed on Tuesday and went out on
Wednesday is one piece of art with a bumpy history, not two things.

Two buttons on every row:

- **↩ Put back in the queue** — sends it to the back of the pool to take its turn again.
- **⚡ Post now** — publishes it again straight away, without waiting for a slot. It
  ignores the pause switch too, because you asked for it directly.

**If it already went out, both buttons ask first.** You'll get *"This already went out on
… Posting it now will put the same piece on your account twice."* That's a real duplicate
on your real account, so it's worth reading before tapping.

For a post that **failed**, there's no warning — nothing went out, so there's nothing to
duplicate.

## Pausing

**Keywords:** pause, stop posting, holiday, break, hiatus, turn off, hold everything

**Posting schedule → Pause the queue.** Nothing goes out, and your queue keeps its exact
order — nothing is lost or reshuffled. A banner appears at the top of the queue with a
**Resume** button.

Slots that pass while you're paused are **not** caught up when you resume. Pausing means
"don't post", and the app takes that literally.

---

## Switching a post between queue and set time

**Keywords:** change to queue, change the date, move to queue, unpin

Open the post and change **When**.

- **Set time → Add to queue**: it goes to the back of the queue.
- **Add to queue → Pick a time**: it leaves the queue and everything behind it moves up.

---

## When something looks wrong

**Keywords:** no dates, waiting, not posting, nothing happening, dates missing

| What you see | What it means |
|---|---|
| **"Waiting — no posting times set"** on every card | You have posts but no slots. Tap **Set them** on the banner. |
| **"Paused"** on every card | The queue is paused. Tap **Resume**. |
| A card's date is further out than you expected | Something ahead of it in the queue is taking the earlier slots — or a slot is switched off. |
| Two posts seem to want the same time | They can't. If a set-time post lands within half an hour of a slot, the queue skips that slot and uses the next one. |
| **"Android may not wake this app"** banner | The important one — see below. |

---

## ⚠️ The banner that matters most — "Android may not wake this app"

**Keywords:** didn't post, missed, late, battery, sleep, only posts when I open the app,
nothing happened overnight

**This already happened once**, on Wednesday 9 September. A post was due at 19:00 and
nothing occurred. It went out at 07:58 the next morning — the instant the app was opened.

Nothing was broken. **Android was holding the alarm back.** Because this app sits idle
between posts, Android files it as "rarely used" and starts delaying its alarms by hours,
even the exact ones. That is the behaviour the app is *designed* around — it should post
without you opening it — so it has to be switched off.

**If you see that banner, tap "Allow it to run in the background" and accept.** It costs
essentially no battery: the app wakes for a minute or two a week.

### Samsung has a second list, and the app cannot reach it

Samsung adds its own sleeping-apps list on top of Android's. No app can change it, so this
one is by hand:

1. **Settings → Battery → Background usage limits**
2. Check **Deep sleeping apps** and **Sleeping apps** — if *autoinsta* is in either,
   remove it.
3. Then **Settings → Apps → autoinsta → Battery → Unrestricted**.

Worth doing once. If a post is ever late again, this is the first place to look.

---

## Small things worth knowing

- **Switching a slot off keeps it.** Use the toggle next to a slot to skip it for a while
  without having to rebuild it from memory later. Delete is for good.
- **Deleting a slot doesn't delete posts.** They move to the next available slot.
- **The queue re-checks itself** when you open the app, when you change anything, after
  each post goes out, when the phone restarts, and once a day in the background.
- **Only about a week of alarms are set at a time.** A post two months out shows its date
  but doesn't have an alarm yet — it gets one as it comes closer. This is deliberate: it
  keeps the app light, and you'd probably have reordered it twice by then anyway.
