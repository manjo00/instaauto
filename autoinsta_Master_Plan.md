# autoinsta — Master Plan v1.0

Android app that **auto-publishes Instagram posts, Reels, and carousels** at a chosen
date & time, each with its own pre-written caption + hashtags. On-device scheduling,
official Instagram Graph API, no backend server.

Target user: a **digital-art Instagram account** (Creator account).

---

## 1. Product scope (v1)

### Must have
- Compose a scheduled post: pick media, write caption, attach hashtags, pick date+time.
- Three post types: **single image/video post**, **Reel**, **carousel / multi-post** (2–10 items).
- **Hashtag presets** — saved, pre-written hashtag sets reusable per post.
- A **queue** screen: upcoming scheduled posts + a **history** of what already posted.
- A **posting schedule**: recurring day+time slots that an ordered pool of posts fills,
  so a finished piece can be added without choosing a date. *(Added 2026-08-29.)*
- Edit / delete a scheduled post before it fires.
- Connect Instagram (Creator) account via Business Login for Instagram; auto-refresh token.
- Reliable-as-possible on-device firing (exact alarm + WorkManager + reschedule on reboot).
- Failure handling: retry, error surfaced, notification on success/failure.

### Out of scope (v1)
- Backend server / cloud scheduling.
- Multiple accounts (single account in v1; design DB to allow more later).
- Analytics / insights, comment automation, DM automation.
- iOS.

---

## 2. Phased roadmap

> Each phase = a few focused tasks. One `docs/plans/` file per task. Commit per phase.

### ✅ Phase 0 — Bootstrap
Git, docs brain, CLAUDE.md, this plan, **buildable Android skeleton** (launches to a
placeholder Home). Green baseline.

### ✅ Phase 1 — Data layer
Room entities + DAOs + DB: `ScheduledPost`, `MediaItem`, `HashtagPreset`,
`PostHistory`, `Account`. Repositories. `PostType`/`PostStatus` domain models.
No UI yet.

### ✅ Phase 2 — Compose-post UI (local only)
Screen to create/edit a scheduled post: media picker (single/multi), caption field,
hashtag preset picker + free hashtags, date+time picker. Saves to Room. Queue/Home
list shows scheduled posts. Delete/edit. **No real posting yet** — just persistence.

### ✅ Phase 2.5 — Hardening (unplanned, added 2026-08-21)
Media durability fix (Photo Picker URIs expire — copy into app storage instead),
scheduling rules extracted to a pure `PostValidator`, and the first test harness
(unit + instrumented). See `docs/specs/2026-08-21-media-durability-design.md`.

### ✅ Phase 3 — Scheduling engine
`PostScheduler` (exact alarms), `PostWorker` (CoroutineWorker stub that just marks
"would post now" + notification), `BootReceiver`. Prove the right post fires at the
right time end-to-end with a fake publish.

### ✅ Phase 4 — Account connect (OAuth)
**Business Login for Instagram** via **Chrome Custom Tabs** → authorization code →
short-lived token → long-lived (60-day) token → stored encrypted. Settings shows the
connected account and days remaining; refresh runs on launch. A WebView was tried first
and does not work — see `docs/plans/2026-08-25-account-connect.md`.
Account setup: `docs/SETUP_GUIDE.md`.

### ✅ Phase 5a — Media upload + real publish
`CloudinaryUploader` (unsigned) + the Graph API pipeline for all 3 types. Verified with a
real post to the live account 2026-08-26.

### ✅ Phase 5b — Media fitting editor
Instagram accepts only 4:5 to 1.91:1, which rejects a lot of art. 5a always pads. 5b adds
per-image preview, manual crop against a guide showing the accepted frame, and a
pad-or-crop choice per item. Note Meta crops every carousel image to match the **first**
item, so the target ratio is shared — the editor must surface that rather than let the
owner set something Instagram will override.

### ✅ Phase 5c — Posting queue (added 2026-08-29)
Recurring **posting slots** (a flat list of day+time) plus an ordered **pool**. Each slot
takes the next post; an empty pool means the day is skipped. Drag to reorder, pause, and a
configurable **catch-up window** that keeps a just-missed slot open — for a post the phone
could not publish *or* one added afterwards. Fixed-time posts still work alongside.
Design: `docs/specs/2026-08-29-posting-queue-design.md`.

### Phase 6 — Polish + hardening
Retry/backoff tuning, edge cases (token expired mid-post, network loss, Doze),
empty/error states, hashtag presets management screen, settings, app icon, about.

### Phase 7 — (optional) Release prep
Signing config, ProGuard/R8 rules, Play Store $25 (only if publishing publicly).

---

## 3. Key technical decisions (locked unless revisited)

| Decision | Choice | Why |
|---|---|---|
| Posting method | Official Graph API | Only ToS-safe path; supports Reels/carousels natively |
| Account type | Creator/Business | Required by Graph API; free; looks the same to followers |
| **Login type** | **Business Login for Instagram** | **Revised 2026-08-25** (was Facebook Login). Meta's Instagram-Login path needs **no Facebook Page** and 2 permissions instead of 5, removing the Page Publishing Authorization failure mode. Host is `graph.instagram.com`. |
| OAuth callback | **Chrome Custom Tabs + GitHub Pages bounce page** | **Revised 2026-08-25.** A WebView was tried first and failed — Meta's login renders blank inside one. Meta also rejects custom-scheme redirect URIs, so an https page on GitHub Pages forwards to `autoinsta://oauth`, which the app claims. Still no server. |
| Scheduling | On-device (Alarm+Work) | User wants no server; acceptable for art account |
| Media host | Cloudinary free tier | Graph API needs a public URL; 25GB free is plenty |
| compileSdk / minSdk | 35 / 26 | Modern APIs; covers ~95%+ devices |
| UI | Jetpack Compose + M3 | Modern, less boilerplate |

---

## 4. Secrets (never committed)
`secrets.properties` at project root (git-ignored), read in `app/build.gradle.kts`
into `BuildConfig`:
```
META_APP_ID=...
META_APP_SECRET=...
META_GRAPH_VERSION=v21.0
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_UPLOAD_PRESET=...
OAUTH_REDIRECT_URI=https://<user>.github.io/<repo>/oauth
```
Account setup steps live in **`docs/SETUP_GUIDE.md`** — written, and a hard
prerequisite for Phases 4 and 5.

---

## 5. Open risks
- ~~**Doze reliability**~~ — **measured, smaller than feared**: 72 wake-ups/hour with the
  exact-alarm permission on the test device. The real risk is **App Standby buckets** for a
  low-engagement app, plus the user revoking the exact-alarm permission.
- ~~**Meta app review**~~ — **resolved 2026-08-25**: Meta requires App Review + Business
  Verification only for apps serving accounts the developer does *not* own. Posting to
  your own account is **Standard Access**, the default. No review needed.
- ~~**JPEG only**~~ — **handled**: every Cloudinary delivery URL carries `f_jpg`, verified
  against the live service (a 1080×1920 PNG came back as 1440×1800 JPEG). PNG sources
  still take a one-time lossy conversion; JPEG sources pass through untouched.
- **Instagram accepts only 4:5 to 1.91:1** — narrow for art. 5a pads automatically so
  nothing fails; 5b hands the choice to the owner.
- ~~**Redirect URI format unconfirmed**~~ — **settled by testing**: Meta accepts https only
  and rejects custom schemes outright. Embedded webviews are blocked. Solved with Custom
  Tabs plus a GitHub Pages bounce page.
- **Reel processing time** — video containers take time; must poll status before publish.
- **Token expiry** — long-lived token ~60 days; must refresh proactively.

---

*Last updated: 2026-08-29. Update when scope or a locked decision changes.*
