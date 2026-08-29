# Architecture Summary

> 1-page reference. Read this instead of grepping the whole codebase.

## Core idea
Native Android app (Kotlin + Jetpack Compose) that schedules and auto-publishes
Instagram **posts, Reels, and carousels** at a chosen date/time, each with its own
pre-written caption + hashtags. **On-device scheduling only** — no backend server.
Posting uses the **official Instagram Graph API** (ToS-safe). Media is uploaded to a
free host (Cloudinary unsigned preset) to get the public URL the API requires.

## Stack
| Concern | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) |
| Architecture | MVVM — Composable → ViewModel → Repository → (Room / Retrofit) |
| Local DB | Room (scheduled posts, presets, history, account) |
| Scheduling | AlarmManager (exact) + WorkManager (does the work, survives process death) |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Media host | Cloudinary unsigned upload → returns public `secure_url` |
| Posting API | Instagram Graph API (Facebook Login) |
| Token storage | EncryptedSharedPreferences |
| Auth flow | Chrome Custom Tabs (OAuth redirect to custom scheme) |
| Images | Coil |

## Package layout (`com.autoinsta`)
```
data/
  db/        Room: entities (ScheduledPost, MediaItem, HashtagPreset, PostHistory, Account) + DAOs + AppDatabase
  media/     MediaFileStore  — copies picked media into app-private storage  ✅
  remote/    InstagramAuthApi · InstagramApi · CloudinaryUploader
             NetworkModule · OAuthRedirectBus · dto/                            ✅
  repository/ PostRepository · PresetRepository · AccountRepository
             HistoryRepository · PublishRepository                              ✅
  prefs/     TokenStore (EncryptedSharedPreferences)                            ✅
domain/
  model/     PostType (SINGLE_IMAGE | REEL | CAROUSEL), PostStatus, MediaType
  PostValidator.kt      pure post rules                                          ✅
  ScheduleCalculator.kt when things fire, missed-post rules                       ✅
  TokenLifecycle.kt     when the Instagram login needs refreshing                 ✅
  MediaFit.kt           which shapes Instagram accepts, and how to fix them       ✅
  PublishPolicy.kt      polling cadence, quota, caption limits                    ✅
scheduler/                                                                      ✅
  PostScheduler   arms/cancels exact alarms; reports whether exact timing is allowed
  AlarmReceiver   receives the alarm, immediately hands off to the worker
  PostWorker      CoroutineWorker — the publish pipeline (STUB until Phase 5)
  BootReceiver    re-arms pending posts after reboot, applying each post's missed rule
  Notifier        success/failure notifications
  TokenRefreshWorker  weekly job that stops the Instagram login lapsing
ui/
  theme/  home/  composepost/  components/    ✅ built
  settings/                                  ✅ account connect
  composepost/ + MediaFitEditor              ✅ compose a post, fit each image
  presets/  history/  manual/                ⏳ planned (Phase 6)
AutoInstaApp.kt   Application (DB + WorkManager init)
MainActivity.kt   single-activity, Compose NavHost
```

## Media storage — why files, not URIs
`MediaItemEntity.localUri` holds an **app-private file path**
(`<filesDir>/media/<uuid>.<ext>`), not the `content://` address the Photo Picker
returned. The picker's read permission dies with the app process, and this app reads
its media days later — so `MediaFileStore` copies the bytes in at save time. The copy
is a raw stream copy: no decode, no re-encode, **no quality loss**. Files are deleted
when their post is deleted or its media replaced. Full reasoning in
[STATUS.md](STATUS.md#-photo-picker-uris-expire-with-the-process).

## How a post fires
An **alarm** is a precise doorbell but gives only seconds of execution and does not
survive reboot. A **worker** survives process death and retries but has loose timing.
So: `PostScheduler` arms an exact alarm → `AlarmReceiver` fires and does nothing but
enqueue → `PostWorker` does the real work. `BootReceiver` re-arms everything after a
restart, because alarms are forgotten on reboot.

Every "when" decision lives in the pure `domain/ScheduleCalculator`, including the
per-post [MissedPostPolicy] rule for posts whose time passed while the phone was off.

## Media fitting — why the delivery URL, not the file
Instagram accepts only **4:5 to 1.91:1**, and **JPEG only**. Rather than altering the
stored file, `MediaFit` produces a Cloudinary **delivery transformation** that is appended
to the URL handed to Instagram. The original stays exactly as exported, and the fit is
reversible and changeable without re-uploading. Per-item choice (pad / crop / as-is) plus
a crop offset live on `media_items`; the editor is `ui/composepost/MediaFitEditor.kt`.

## The publishing pipeline (the heart of the app)
Runs inside `PostWorker` when the scheduled time fires:
1. Load the `ScheduledPost` + its `MediaItem`s from Room (media is already local).
2. Upload each media file to Cloudinary → collect `secure_url`s.
3. Call Graph API by type:
   - **SINGLE_IMAGE** → `POST /{ig-user-id}/media` (image_url, caption) → container id → `POST /{ig-user-id}/media_publish`
   - **REEL** → `POST .../media` (media_type=REELS, video_url, caption) → **poll** `GET /{container}?fields=status_code` until `FINISHED` → publish
   - **CAROUSEL** → for each item `POST .../media` (is_carousel_item=true) → collect ids → `POST .../media` (media_type=CAROUSEL, children=[ids], caption) → publish
4. On success → write `PostHistory`, mark post `POSTED`.
5. On failure → retry w/ backoff, store error, fire a notification.

## Instagram Graph API (v21.0)
- Base: `https://graph.facebook.com/v21.0/`
- Account discovery: `GET /me/accounts` → page id → `GET /{page-id}?fields=instagram_business_account` → ig-user-id
- **Login: Business Login for Instagram** (no Facebook Page). Host `graph.instagram.com`.
- Required permissions: `instagram_business_basic`, `instagram_business_content_publish`
- **No App Review needed** for posting to an account you own (Standard Access).
- Long-lived token: ~60 days; refresh before expiry on app launch.
- Rate limit: ~50 published posts / 24h per account (well above any human need).

## Hard requirements (account side)
- IG account must be **Business or Creator**, linked to a **Facebook Page**.
- A **Meta Developer app** (App ID + Secret).
- A **Cloudinary** account (cloud name + unsigned upload preset).
- These go in `secrets.properties` (git-ignored) — see `docs/SETUP_GUIDE.md`.

## On-device scheduling caveat
Android Doze may delay exact alarms when the phone sleeps. We use
`setExactAndAllowWhileIdle` + `WorkManager` + a `BootReceiver`. Good enough for
art-account scheduling; to-the-minute guarantees would need a server (out of scope).
