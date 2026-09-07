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

## ⏸ Awaiting a decision — the caption & title coach

**Proposed 2026-09-07, not started. The owner was asked two questions and has not answered
yet**, so nothing should be built until they do:

1. Build it at all?
2. Should it **ask them first** (two prompts before it says anything), or just offer
   options and stay out of the way?

**The constraint that shapes the whole design**, in the owner's words: *"I want to learn how
to write them myself so naming my pieces at least comes from me."* This is a **coach, not a
ghostwriter** — success is the owner needing it less each month.

Sketch as proposed:

1. Asks the owner two questions first — *"What was hard about this one?"* and *"What's it
   about, in three words?"* — so their words exist before any suggestion does.
2. Offers three title options, each **labelled with which of the five sources it came from**
   (the feeling / a detail / time or place / what it almost was / borrowed language), so the
   method is being taught rather than the answer handed over. Sources are in
   `docs/manual/captions-and-hashtags.md`.
3. Offers five tags **labelled by portfolio role with post volumes**, teaching the 2 niche /
   2 topic / 1 flexible shape.
4. Nothing auto-fills. Everything editable or ignorable.
5. Reads previous captions from `post_history` so suggestions sound like the owner.

**Technically:** Claude API (`claude-opus-5`, vision — the artwork is already in app
storage), roughly **$0.02 per post, about a dollar a year** at one post a week. The API key
would ship inside the APK, which the owner has accepted because **they are not publishing
the app**. Read the `claude-api` skill before writing any of it.

| Item | Size | Notes |
|---|---|---|
| **Caption & title coach** | **M** | The above. Blocked on the owner's answer. |
| **Working notes per post** | **S** | A scratch field to add to *while painting*, implementing the "write the caption while you work" habit. No API, no key, no cost — attacks the cause rather than the symptom, and was the recommended first step. |

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
