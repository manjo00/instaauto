# ROADMAP

Ideas and debt captured so they aren't lost. **Nothing here is started without the
owner's say-so.** Each item is sized and sketched so it can be picked up cold.

Sizes: **S** ≈ half a session · **M** ≈ one session · **L** ≈ multiple sessions

Last updated: 2026-08-29

---

## Committed — the phased plan

| | Item | Size | State |
|---|---|---|---|
| Phase 6 | Polish, hashtag-preset screen, history screen, **in-app manual** | **L** | ⏳ Next |
| Phase 7 | Release prep — signing, R8, versioning | **M** | ⏳ Planned |

Phases 0–5c are done. See `autoinsta_Master_Plan.md` and `docs/STATUS.md`.

---

## Phase 6 — what's actually in it

Nothing here is built. Sub-items so it can be taken in pieces:

| Item | Size | Notes |
|---|---|---|
| **In-app manual** | **M** | A feature isn't finished until the manual describes it — this is the debt for everything built so far. The queue's entries are already written (`docs/manual/queue.md`); the screen that renders them is not. Sketch below. |
| ~~History screen~~ | — | **Done 2026-09-07** — the Done tab. |
| ~~Hashtag preset screen~~ | — | **Done 2026-09-07.** |
| **Retry/backoff tuning** | **S** | `MAX_RETRIES = 4` was chosen, not measured. |
| **Empty/error states** | **S** | Several screens assume the happy path. |
| **App icon** | **S** | Still the default adaptive placeholder. |

### The in-app manual, sketched

A `ui/manual/` screen fed by a structured list (not free prose) so entries are searchable.
Each carries a title, plain-language body, and **search keywords in the owner's words**.

Two special sections:
- **Hidden gems** — anything undiscoverable by tapping: the ⚡ debug fire-now bolt,
  tapping a thumbnail to open the fitting editor, the per-post missed-time rule,
  **press-and-hold to reorder the queue**, and **the catch-up window** (a slot stays open
  after it passes, so a post added late can still fill it).
- **What's new** — per release, kept until the next replaces it, shown once after update.

Content rule: describe **what the code actually does**, never what it was meant to do.

---

## Posting queue — deliberate limitations

Decisions, not bugs. Worth revisiting only if they prove annoying in use.

| Limitation | Why | Size to change |
|---|---|---|
| The list does not reflow while dragging | The card follows the finger and a line shows where it lands, but the others stay put. Live reflow means correcting the drag offset by the height of every displaced card — with variable-height cards that is the exact arithmetic that produces jitter no unit test can catch. | **M** |
| Only one post catches up at a time | Several posts landing minutes apart reads as a glitch and wastes the reach on all but the first. If a genuinely missed backlog should drain faster, this is the knob. | **S** |
| Only the head of the queue can fill an open slot | If it declines via "wait for the next slot", the slot goes unfilled rather than passing to the second post — which would reorder the queue behind the owner's back. | **S** |
| The catch-up window is queue-wide, not per post | It is a property of the slot ("is Monday 7pm still open?"). Per-post windows would make "which post decides" unanswerable. | **M** |
| No shuffle | The owner asked for control over order, not the absence of it. | **S** |
| Slots have no rules | "Saturdays are Reels only", "skip if the last post was a carousel" — no evidence yet that any of this is wanted. | **M** |

## Ideas worth considering (2026-09-07)

Raised while looking at what an art account actually needs. Nothing here is started.

| Idea | Size | Why it might matter | Confidence |
|---|---|---|---|
| ~~Hashtag preset screen~~ | — | **Done 2026-09-07** — and applying a set *merges* rather than replaces, so tags typed for the specific piece survive. | — |
| ~~Hashtags in the first comment~~ | — | **Dropped 2026-09-07.** Instagram's five-hashtag cap applies across caption *and* comments — first-comment placement buys no extra slots, so the whole point of the idea is gone. | Verified against three sources |
| **Choose a Reel's cover frame** | **M** | Now that thumbnails pull a real frame, picking *which* frame becomes the Instagram cover is the natural next step. The container API appears to take a `thumb_offset`. | **Unverified** — confirm against the live API first |
| **Caption sign-off / templates** | **S** | Same idea as hashtag presets applied to the caption: a saved block appended to every post. | Certain |
| **Duplicate a post** | **S** | For a series, most of the work is the same. The Done tab's "put back in queue" is nearly this already. | Certain |
| **Save as draft** | **S** | Compose a post without committing it to the queue. Today the only options are queue it or pin it. | Certain |

## ✅ Shipped 2026-09-08 — the caption & title coach

**Decided and built.** The owner answered both open questions: *"yes build the coach, ask
me first."* Ask-first it is.

**The constraint that shaped the whole design**, in the owner's words: *"I want to learn how
to write them myself so naming my pieces at least comes from me."* It is a **coach, not a
ghostwriter** — success is the owner needing it less each month.

What shipped, against the sketch:

1. ✅ Asks two questions first — *"What was hard about this one?"* and *"What's it about, in
   three words?"* — so their words exist before any suggestion does. Both optional; skipping
   is a labelled path and makes the answer more tentative.
2. ✅ Three titles, each labelled with which of the five sources it used.
3. ✅ Five tags labelled by portfolio role with post volumes.
4. ✅ Nothing auto-fills — and nothing is even pre-ticked. Accepting only ever *adds*;
   `CaptionCoach.captionWith` and `HashtagSet.merge` cannot destroy what is already typed.
5. ✅ Reads the last 8 **published** captions from `post_history` for voice (drafts are
   excluded on purpose — an abandoned draft is not their voice).

**As built:** `claude-opus-5` with `effort: low`, on hand-written Retrofit rather than the
official SDK (measured: the SDK costs +6.9 MB — see `docs/STATUS.md`). Measured **2,716 in /
507 out per call ≈ 1–2p a post**. Optional by construction: with no API key the entry point
does not appear and nothing else changes.

**Still open (small):** the coach cannot be reached when *editing* a saved post whose media
is a video too long to read a frame from, and it always uses the first item of a carousel.
Neither has bitten yet.

### What the first real use taught (2026-09-08)

The owner used it, took **"Held Gaze"**, and posted with the **title only**. Two pieces of
feedback that the design had assumed away:

1. **The caption's voice is wrong for them.** In their words: *"am not the type who talk in
   social media... 'i woke up and felt like painting' or 'Felt a little stuck and doing this
   challenge was a fun experience!' — i hate talking like this its just not me."*
   The coach's `hook` is specified as *"a thought, a confession, a question"* — which is
   exactly that register. **A convention was imported without checking it was theirs.** A
   gallery label is also a caption: medium, hours, what changed. It performs nothing.
2. **Language is an unasked question.** Their followers are friends and family; writing
   English at them feels wrong, and mixing two languages feels worse. The whole caption
   feature quietly assumed English. Hashtags are a separate question again — they classify,
   and they classify by language.

The **titles** half is working as intended, which is the half they actually asked for:
*"so naming my pieces at least comes from me."*

| Item | Size | Notes |
|---|---|---|
| ~~Caption voice options~~ | ~~**S**~~ | ✅ Shipped 2026-09-08 as the gallery-label register. |
| **Arabic / bilingual captions** | **M** | Real decision, not just a translation: audience, reach and hashtag classification all move together. Needs the owner's call, not a default. |

### 🔜 Taste memory — the coach learns which titles the owner keeps

**Requested 2026-09-08**, in the owner's words: *"we need to think of a way to train this ai
to come up with good result that maches what i know… maybe like deslike and notes attached
to each result it gives so it can improve."* **Not started — do not build without a design
review.**

**First, the honest framing.** Nothing here fine-tunes or trains a model; that is not
available and not needed. What works is **few-shot from their own history** — showing the
coach the titles they kept and the ones they threw away. `CoachRepository.recentCaptions()`
already does exactly this for caption voice, so the pattern is proven in this codebase.

**The waste it fixes:** every judgement the owner has already made is currently discarded.
"Held Gaze" was kept. "Wand Up", "Second Volley", "Empty Yet Longing" were rejected. That is
the single most valuable signal available and none of it is stored. Three prompt-tuning
passes were spent guessing at preferences that a dozen kept/rejected pairs would state
outright.

**Sketch:**

1. New table `coach_feedback` — `titleText`, `source`, `kept`, `note?`, `postId?`, `createdAt`.
   Schema v5, so a real `Migration(4,5)` plus a `MigrationTest` case.
2. **Signals that cost the owner nothing**, because they are already tapping:
   - ticking a title and applying it → `kept = true`
   - "Show me three different ones" → the three on screen become `kept = false`
3. **An explicit signal for when it matters:** 👍/👎 per title, plus an optional one-line
   note ("too literal", "doesn't say what I wrote"). The note is the part worth the most —
   a rejection with a reason is worth ten without.
4. `CaptionCoach.userPrompt` grows a section: titles this artist kept, titles they rejected
   and why. A few hundred tokens; no measurable cost change.

**Second-order benefit, and the reason to prioritise it:** it removes the need to burn API
calls on synthetic verification. Today, checking a prompt change means paying for runs
against a made-up image and invented answers — **~$0.35 of the ~$0.49 spent so far.** With
this table, real use *is* the test set, and the exchange is already logged.

**Watch out for:** rejection ≠ dislike. A title can be good and simply not the one they
picked, so weighting all three shown titles as failures would poison the examples. Only an
explicit 👎, or a "show me three different ones" with none applied, is a real negative.

| Item | Size | Notes |
|---|---|---|
| **Taste memory (kept/rejected + notes)** | **M** | The above. Highest-value next step for the coach by some distance. |

| Item | Size | Notes |
|---|---|---|
| ~~Caption & title coach~~ | ~~**M**~~ | ✅ Shipped 2026-09-08. |
| **Working notes per post** | **S** | A scratch field to add to *while painting*, implementing the "write the caption while you work" habit. No API, no key, no cost — attacks the cause rather than the symptom, and was the recommended first step. **Still the better long-term answer than the coach**, which treats the blank box after the fact. |

## Technical debt

Ordered by how likely it is to bite.

| Item | Size | Why it matters |
|---|---|---|
| **Measure App Standby impact** | **S** | Buckets are active on the Fold 7 and autoinsta is a "set it and forget it" app — the usage pattern most likely to be demoted. Needs days of real observation, not a test. Could silently delay posts. |
| **Compose UI tests** | **M** | The fitting editor's drag gesture, the compose screen, and the queue have no automated coverage. Their *logic* is covered by pure tests; the interactions are not. |
| **Media disk-usage cap** | **S** | A queue of 10-item carousels could sit on hundreds of MB. Nothing prunes orphaned files beyond per-post cleanup. |
| **Hilt instead of manual DI** | **M** | `AutoInstaApp` hand-wires everything. It works, but `publishRepositoryOverride` exists purely as a test seam — a DI framework would give that properly. |
| **A queue card's semantics are one merged blob** | **S** | `Card(onClick)` merges its children, so TalkBack announces the caption, the date, "Delete", "Drag to reorder" and the debug bolt as a single node. Discovered while fixing the reorder test. Not a correctness bug; a real accessibility smell. |
| **Meta error parsing uses a regex** | **S** | `AccountRepository.extractMetaMessage` and `PublishRepository.metaMessage` scrape the body because Meta's error shape varies. `MetaErrorEnvelopeDto` exists and could be tried first with the regex as fallback. |
| **`PostWorkerTest` uses the real app instance** | **S** | It substitutes a fake publisher (safe), but still reads/writes the real database because `PostWorker` reaches for `applicationContext as AutoInstaApp`. The queue tests now also create and restore real posting slots — see STATUS. |
| **`QueueReorderTest` is the most fragile test in the suite** | **S** | It drives a real gesture on a real screen. It drags well past the top so the target index clamps rather than depending on card heights, and it targets the unmerged tree — but it is still the first thing to break if the Home layout changes. |
| **Alarm horizon is 7 days, chosen not measured** | **S** | Same class of guess as `MAX_RETRIES = 4`. Arming a queue three months deep would be wasteful; 7 days is comfortable but arbitrary. |

---

## Fitting editor — deliberate limitations

Not bugs; decisions worth revisiting only if they prove annoying in use.

| Limitation | Why | Size to change |
|---|---|---|
| No pinch-to-zoom | The crop window is always the largest allowed rectangle, so the owner picks *which part*, not *how much*. Zooming in means scaling up, which softens artwork. | **M** |
| Videos skip fitting entirely | Instagram's video rules are codec/duration/bitrate, none of which a crop addresses. | **M** |
| Pad colour is always white | A sampled edge colour or a dark option would suit some art better. | **S** |

---

## Environment quirks (not app problems)

| Item | Notes |
|---|---|
| **Emulator dies after boot** | `Pixel_10_Pro` exits shortly after booting on this machine. Worked around by testing on the physical Fold 7. Undiagnosed. |
| **`connectedAndroidTest` uninstalls the app** | Takes the database and Instagram token with it. Reinstall and reconnect afterwards. See STATUS. |
| **The OAuth bounce page lives in `docs/`** | Enabling GitHub Pages therefore publishes the project docs too. Harmless (no secrets), but a dedicated `gh-pages` branch would be tidier if the repo ever needs to be private. |

---

## Deliberately out of scope for v1

Recorded so they aren't re-litigated.

| Idea | Why not |
|---|---|
| Backend / cloud scheduling | v1 is on-device only; adds cost, hosting, an account system |
| **A watched device folder that auto-imports art** | A post needs a caption, and a folder cannot supply one. The pool is the "folder". |
| Multiple Instagram accounts | Single account in v1; the schema already allows more later |
| **UI-automation posting** (tapping the real IG app) | Violates Instagram's ToS and risks the account. Graph API only. **Never revisit.** |
| Stories, product tagging, collaborators, `alt_text` | Supported by the API; out of scope for v1 |
| Analytics / insights, comment or DM automation | Different product |
| iOS | Out of scope |
