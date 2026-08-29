# ROADMAP

Ideas and debt captured so they aren't lost. **Nothing here is started without the
owner's say-so.** Each item is sized and sketched so it can be picked up cold.

Sizes: **S** ≈ half a session · **M** ≈ one session · **L** ≈ multiple sessions

Last updated: 2026-08-26

---

## Committed — the phased plan

| | Item | Size | State |
|---|---|---|---|
| Phase 6 | Polish, hashtag-preset screen, history screen, **in-app manual** | **L** | ⏳ Next |
| Phase 7 | Release prep — signing, R8, versioning | **M** | ⏳ Planned |

Phases 0–5b are done. See `autoinsta_Master_Plan.md` and `docs/STATUS.md`.

---

## Phase 6 — what's actually in it

Nothing here is built. Sub-items so it can be taken in pieces:

| Item | Size | Notes |
|---|---|---|
| **In-app manual** | **M** | A feature isn't finished until the manual describes it — this is the debt for everything built so far. Sketch below. |
| **History screen** | **S** | `post_history` is written on every publish and never shown. Success/failure, when, why. |
| **Hashtag preset screen** | **S** | The table and repository exist; there is no UI to create or edit presets, so the picker on the compose screen is always empty. |
| **Retry/backoff tuning** | **S** | `MAX_RETRIES = 4` was chosen, not measured. |
| **Empty/error states** | **S** | Several screens assume the happy path. |
| **App icon** | **S** | Still the default adaptive placeholder. |

### The in-app manual, sketched

A `ui/manual/` screen fed by a structured list (not free prose) so entries are searchable.
Each carries a title, plain-language body, and **search keywords in the owner's words**.

Two special sections:
- **Hidden gems** — anything undiscoverable by tapping: the ⚡ debug fire-now bolt,
  tapping a thumbnail to open the fitting editor, the per-post missed-time rule.
- **What's new** — per release, kept until the next replaces it, shown once after update.

Content rule: describe **what the code actually does**, never what it was meant to do.

---

## Technical debt

Ordered by how likely it is to bite.

| Item | Size | Why it matters |
|---|---|---|
| **Measure App Standby impact** | **S** | Buckets are active on the Fold 7 and autoinsta is a "set it and forget it" app — the usage pattern most likely to be demoted. Needs days of real observation, not a test. Could silently delay posts. |
| **Compose UI tests** | **M** | The fitting editor's drag gesture, the compose screen, and the queue have no automated coverage. Their *logic* is covered by pure tests; the interactions are not. |
| **Media disk-usage cap** | **S** | A queue of 10-item carousels could sit on hundreds of MB. Nothing prunes orphaned files beyond per-post cleanup. |
| **Hilt instead of manual DI** | **M** | `AutoInstaApp` hand-wires everything. It works, but `publishRepositoryOverride` exists purely as a test seam — a DI framework would give that properly. |
| **`TokenStore` has no instrumented test** | **S** | Encryption against the real Keystore is unverified by test; only by hand. |
| **Meta error parsing uses a regex** | **S** | `AccountRepository.extractMetaMessage` and `PublishRepository.metaMessage` scrape the body because Meta's error shape varies. `MetaErrorEnvelopeDto` exists and could be tried first with the regex as fallback. |
| **`PostWorkerTest` uses the real app instance** | **S** | It substitutes a fake publisher (safe), but still reads/writes the real database because `PostWorker` reaches for `applicationContext as AutoInstaApp`. |

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
| Multiple Instagram accounts | Single account in v1; the schema already allows more later |
| **UI-automation posting** (tapping the real IG app) | Violates Instagram's ToS and risks the account. Graph API only. **Never revisit.** |
| Stories, product tagging, collaborators, `alt_text` | Supported by the API; out of scope for v1 |
| Analytics / insights, comment or DM automation | Different product |
| iOS | Out of scope |
