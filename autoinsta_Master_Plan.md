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
- Edit / delete a scheduled post before it fires.
- Connect Instagram (Creator) account via Facebook Login; auto-refresh token.
- Reliable-as-possible on-device firing (exact alarm + WorkManager + reschedule on reboot).
- Failure handling: retry, error surfaced, notification on success/failure.

### Out of scope (v1)
- Backend server / cloud scheduling.
- Multiple accounts (single account in v1; design DB to allow more later).
- Analytics / insights, comment automation, DM automation.
- iOS.

---

## 2. Phased roadmap

> Each phase = a few focused tasks. One `_Current_Task.md` per task. Commit per phase.

### ✅ Phase 0 — Bootstrap
### ✅ Phase 1 — Data layer
Git, docs brain, CLAUDE.md, this plan, **buildable Android skeleton** (launches to a
placeholder Home). Green baseline.

### Phase 1 — Data layer
Room entities + DAOs + DB: `ScheduledPost`, `MediaItem`, `HashtagPreset`,
`PostHistory`, `Account`. Repositories. `PostType`/`PostStatus` domain models.
Unit-testable, no UI yet.

### Phase 2 — Compose-post UI (local only)
Screen to create/edit a scheduled post: media picker (single/multi), caption field,
hashtag preset picker + free hashtags, date+time picker. Saves to Room. Queue/Home
list shows scheduled posts. Delete/edit. **No real posting yet** — just persistence.

### Phase 3 — Scheduling engine
`PostScheduler` (exact alarms), `PostWorker` (CoroutineWorker stub that just marks
"would post now" + notification), `BootReceiver`. Prove the right post fires at the
right time end-to-end with a fake publish.

### Phase 4 — Account connect (OAuth)
Facebook Login via Chrome Custom Tabs → token → exchange for long-lived → discover
ig-user-id → store encrypted. Settings screen shows connected account. Token refresh
on launch.

### Phase 5 — Media upload + real publish
`CloudinaryUploader` (unsigned). Wire `PostWorker` to the real Graph API pipeline for
all 3 types (image, Reel w/ status polling, carousel). Write `PostHistory` on success.

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
OAUTH_REDIRECT_SCHEME=autoinsta
```
Account setup steps live in `docs/SETUP_GUIDE.md` (written in Phase 4).

---

## 5. Open risks
- **Doze reliability** — exact alarms can slip when phone sleeps overnight. Mitigated, not eliminated.
- **Meta app review** — `instagram_content_publish` may need Meta App Review for non-dev users; fine while the account is a test/developer-linked account. Document in Phase 4.
- **Reel processing time** — video containers take time; must poll status before publish.
- **Token expiry** — long-lived token ~60 days; must refresh proactively.

---

*Last updated: 2026-06-07. Update when scope or a locked decision changes.*
