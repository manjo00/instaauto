# ROADMAP

Ideas captured so they aren't lost. **Nothing here is started without the owner's
say-so.** Each item is sized and sketched so it can be picked up cold.

Sizes: **S** ≈ half a session · **M** ≈ one session · **L** ≈ multiple sessions

Last updated: 2026-08-21

---

## Committed — the phased plan

These are in `autoinsta_Master_Plan.md` and are the default order of work.

| | Item | Size |
|---|---|---|
| Phase 3 | Scheduling engine — `PostScheduler`, `PostWorker`, `BootReceiver` | **L** |
| Phase 4 | Instagram account connect (Facebook Login → long-lived token) | **L** |
| Phase 5 | Cloudinary upload + real Graph API publish (image / Reel / carousel) | **L** |
| Phase 6 | Polish, hashtag-preset management screen, error states | **M** |
| Phase 7 | Release prep — signing, R8 | **M** |

---

## Now scheduled into Phase 6 — in-app manual

**Size: M.** A feature isn't finished until the manual describes it, so this needs to
exist before Phase 6 features land, not after.

Sketch: a `ui/manual/` screen fed by a structured list (not free prose) so entries can
be searched. Each entry carries a title, plain-language body, and **search keywords in
the owner's words** rather than the code's. Two special sections:

- **Hidden gems** — anything undiscoverable by tapping around (long-press, swipe, gestures).
- **What's new** — populated per release, kept until the next release replaces it,
  shown once automatically after an update.

Content rule: describe **what the code actually does**, never what it was meant to do.

---

## Unsized / unowned ideas

Nothing captured yet. Passing thoughts land here rather than getting lost in chat.

---

## Deliberately out of scope for v1

Recorded so they don't get re-litigated — see the Master Plan's locked decisions.

| Idea | Why not now |
|---|---|
| Backend / cloud scheduling | v1 is on-device only; adds cost, hosting, and an account system |
| Multiple Instagram accounts | Single account in v1; the DB schema already allows more later |
| UI-automation posting (tapping the real IG app) | Violates Instagram ToS and risks the account. Graph API only. **Never revisit.** |
| Analytics / insights, comment or DM automation | Out of scope; different product |
| iOS | Out of scope |

---

## Technical debt worth paying down

| Item | Size | Why it matters |
|---|---|---|
| Replace `fallbackToDestructiveMigration()` with real Room migrations | **S** | Today any schema change silently wipes the user's queue |
| Compose UI tests for the create/edit screen and queue | **M** | Currently only the data layer and validation rules are covered |
| Media disk-usage cap / cleanup of orphaned files | **S** | A big carousel queue could sit on hundreds of MB |
| Dependency injection (Hilt) instead of manual wiring in `AutoInstaApp` | **M** | Fine at 4 repositories; gets awkward once the scheduler and network layers land |
