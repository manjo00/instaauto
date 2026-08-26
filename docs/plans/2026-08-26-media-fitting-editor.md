# Plan — Phase 5b: Media fitting editor

**Date:** 2026-08-26 · **Design:** [`specs/2026-08-26-publishing-and-media-fitting-design.md`](../specs/2026-08-26-publishing-and-media-fitting-design.md)

The owner asked for this directly: *"preview in the app, manual crop, make sure what I
want is apparent and not cropped, per-photo option of bars or manual crop, with a guide
on what Instagram accepts."*

---

## Tasks

- [x] **1. Schema v3** — `widthPx`, `heightPx`, `fitMode`, `cropOffset` on media items;
      `Migration(2,3)`; converter; schema exported.
- [x] **2. Migration tests** — v2→v3 keeps media rows and defaults sensibly; v1→v3 as a chain.
- [x] **3. Crop maths** — `MediaFit.cropWindow` / `croppedAwayFraction`, and a crop
      transformation carrying the owner's offset.
- [x] **4. Unit tests** — 36 on `MediaFit` now, including that a crop never scales up and
      that the offset survives into the URL.
- [x] **5. Measurement** — dimensions read from the file header (`inJustDecodeBounds`) at
      pick time *and* import time, so the shape is known before anything is uploaded.
- [x] **6. Editor screen** — full-screen preview, Instagram's frame drawn over the image,
      rule-of-thirds guides, Fit/Crop toggle, drag to position, "about N% won't be shown".
- [x] **7. Compose integration** — tap a thumbnail to open it; a badge marks anything
      Instagram would reject and says what will happen to it.
- [x] **8. Carousel caveat surfaced** — Meta crops every carousel image to match the
      first, so the editor says so rather than offering a per-image ratio Instagram
      would silently override.
- [x] **9. Verify** — 119 unit, 33 instrumented on the Fold 7, lint 0 errors, installed.
- [ ] **10. Real-world check** — owner tries it on actual artwork.

## Design notes worth keeping

**Fitting is a delivery-URL transformation, never a change to the stored file.** The
original stays exactly as exported; the fit can be changed later without re-uploading.
That is what makes "try crop, change your mind, use bars instead" free.

**Crop uses an explicit window, not Cloudinary's `g_auto`.** An earlier version let
Cloudinary pick the subject automatically. That silently overrode the owner's positioning,
which defeats the point of the editor — the test that asserted `g_auto` was replaced.

**0×0 means "not measured", not "square".** Videos and pre-v3 rows have no dimensions;
`MediaFit` treats that as Unknown and falls back to a plain width cap rather than
guessing at a shape it cannot see.

## Noticed (not fixing now)

- No pinch-to-zoom in the editor — the crop window is always the largest allowed
  rectangle, so the owner chooses *which part*, not *how much*. Zooming in would mean
  scaling up, which loses quality on a piece of art.
- The editor has no instrumented test. Its logic is pure and covered by `MediaFitTest`;
  the gestures are not. Worth a Compose UI test in Phase 6.
- Videos skip fitting entirely. Instagram's video rules are about codec, duration and
  bitrate, none of which a crop would address.
