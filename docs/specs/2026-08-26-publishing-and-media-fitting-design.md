# Design — Phase 5: Real publishing + media fitting

**Date:** 2026-08-26 · **Status:** proposed · **Phase:** 5

The phase that replaces *"would have posted now"* with an actual Instagram post.

---

## Research — measured against Meta's live docs, 2026-08-26

Verified before designing, per the rule taken from the Phase 4 WebView detour.

### What Instagram accepts

| | Requirement |
|---|---|
| Image format | **JPEG only.** PNG is rejected outright |
| Image size | 8 MB maximum |
| **Image aspect ratio** | **4:5 to 1.91:1** — outside this range is rejected |
| Image width | 320–1440 px (scaled automatically) |
| Colour space | sRGB (converted if not) |
| Reels | MP4/MOV, H264/HEVC, 3 s – 15 min, 300 MB, 23–60 fps |
| Reel aspect | 0.01:1 to 10:1; 9:16 recommended |
| Carousel | 2–10 items |
| Caption | 2200 chars, 30 hashtags, 20 @mentions. **Not supported on carousel children** |
| Rate limit | Docs say 100/24h in one place, 50 in another. Assume **50** |
| Containers | Expire **24 hours** after creation |

### The publish sequence

```
Single image   POST /<IG_ID>/media  (image_url, caption)        -> container id
               POST /<IG_ID>/media_publish  (creation_id)       -> media id

Reel           POST /<IG_ID>/media  (media_type=REELS, video_url, caption)
               GET  /<CONTAINER_ID>?fields=status_code          -> poll
               POST /<IG_ID>/media_publish  (creation_id)

Carousel       POST /<IG_ID>/media  (is_carousel_item=true, image_url)  x2..10
               POST /<IG_ID>/media  (media_type=CAROUSEL, children=..., caption)
               POST /<IG_ID>/media_publish  (creation_id)
```

Container `status_code` is one of `EXPIRED`, `ERROR`, `FINISHED`, `IN_PROGRESS`,
`PUBLISHED`. Meta's guidance: **poll once per minute, for no more than 5 minutes.**

### Cloudinary

Instagram never receives a file — it fetches from a public URL
(*"we cURL media used in publishing attempts"*). Cloudinary provides that.

- `POST https://api.cloudinary.com/v1_1/<cloud>/image/upload` (and `/video/upload`)
- Multipart: `file` + `upload_preset`
- **Unsigned uploads accept only a restricted parameter set** — transformations cannot be
  passed at upload time.

That last point shapes the design: **fitting happens on delivery, not upload.** The
original is stored untouched, and the URL handed to Instagram carries the transformation:

```
https://res.cloudinary.com/<cloud>/image/upload/<transform>/<public_id>.jpg
```

So the stored asset is always the artwork exactly as exported, and the fitting is a
property of the link — reversible, and re-doable without re-uploading.

---

## The problem this creates for an art account

**4:5 is a narrow window.** The tallest allowed image is 1080×1350. A 9:16 piece
(0.5625) is well outside it and Instagram rejects it. Digital art is frequently taller
than 4:5, so this is not an edge case here — it is the common case.

Failing at publish time is the worst possible moment: the post is scheduled, the owner is
asleep, and the only signal is an API error hours later.

## Decision: a fitting step the owner controls

Chosen over silent auto-crop (destroys composition), silent auto-pad (surprising), and
refuse-at-schedule (blocks work).

**Per media item, the owner chooses:**

| Mode | What it does |
|---|---|
| **Fit (pad)** | Whole artwork visible, bars fill the remainder. Nothing lost |
| **Crop** | Owner positions a 4:5 (or chosen ratio) frame over the image by hand |
| **As-is** | Already inside Instagram's range; nothing applied |

The compose screen shows each item with its current mode and a warning when a piece
cannot post as-is. The crop editor overlays the acceptable frame so the owner can see
exactly what survives.

### The carousel caveat, surfaced not hidden

Meta: *"Carousel images are all cropped based on the first image in the carousel."*
Every image in a carousel ends up matching **item #1's** aspect ratio. Per-item pad/crop
still applies, but the target ratio is shared and driven by the first item.

The editor must show this — offering per-image ratios that Instagram will silently
override would be worse than not offering the choice at all.

---

## Sequencing

The crop editor is a real piece of UI (gesture-driven pan/zoom, ratio overlay, per-item
state, a schema change to store the choice). Publishing itself is independent of it.

**Built in this order:**

1. **Phase 5a — publishing works.** Cloudinary upload, the three Graph API pipelines,
   history, error handling. Default fitting is **pad**, so nothing is ever cropped
   without consent and nothing fails for shape.
2. **Phase 5b — the fitting editor.** Preview, manual crop, per-item mode, carousel
   ratio surfaced.

Each step ships on its own. 5a is the milestone that makes the app real; 5b upgrades a
safe default into a controlled one. Nothing built in 5a is thrown away.

---

## Components (5a)

```
data/remote/
  CloudinaryUploader.kt    unsigned upload; returns public_id + secure_url
  InstagramApi.kt          media / media_publish / container status
  dto/PublishDtos.kt
domain/
  MediaFit.kt              pure: is this ratio publishable, what transform fixes it
  PublishPlan.kt           pure: the ordered steps for a given post type
scheduler/
  PostWorker.kt            the stub block replaced with the real pipeline
```

`MediaFit` and `PublishPlan` are pure so the awkward parts — a 9:16 image, a 3-item
carousel, a Reel that never finishes processing — are unit tests rather than things
discovered at 3am.

## Acceptance (5a)

- A real single image publishes to the connected account.
- A Reel publishes, with status polled to `FINISHED` before publishing.
- A 3-item carousel publishes as one post.
- A too-tall image is padded and still publishes.
- Failures write a `PostHistory` row with a readable reason and notify.
- Rate limit respected (checked against `content_publishing_limit` before posting).
- Lint clean, tests green, verified against the real account.

## Out of scope (5a)

The crop editor (5b), Stories, `alt_text`, collaborators, product tagging, resumable
upload for large video (Facebook-Login only anyway).
