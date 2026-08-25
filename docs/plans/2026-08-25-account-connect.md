# Plan — Phase 4: Account connect

**Date:** 2026-08-25 · **Design:** [`specs/2026-08-25-account-connect-design.md`](../specs/2026-08-25-account-connect-design.md)

Connecting a real Instagram account, so Phase 5 has something to publish with.

---

## Tasks

- [x] **1. Research** — verified against Meta's live docs, not memory. Overturned the
      master plan's Facebook Login decision and retired the App Review risk.
- [x] **2. Setup guide** — `docs/SETUP_GUIDE.md`, the account work only the owner can do.
- [x] **3. Deps** — Retrofit, OkHttp, kotlinx.serialization, security-crypto, browser.
- [x] **4. `TokenLifecycle`** — pure: healthy / should-refresh / too-young / expired.
- [x] **5. Unit tests** — 16 cases incl. the 24-hour and 60-day boundaries.
- [x] **6. `InstagramAuthApi` + DTOs** — the three token endpoints across two hosts.
- [x] **7. `TokenStore`** — token in EncryptedSharedPreferences, not Room.
- [x] **8. `AccountRepository`** — connect / refresh / disconnect as one atomic flow.
- [x] **9. Settings UI** — connect, status with days remaining, disconnect.
- [x] **10. ~~WebView login~~** — built, failed, removed. See below.
- [x] **11. Custom Tabs + bounce page** — the approach that actually works.
- [x] **12. Refresh on launch** — wired into `AutoInstaApp.onCreate`.
- [x] **13. Verify** — 59/59 unit tests, lint 0 errors, **connected to the real account
      on the Fold 7**: account row written, 60-day expiry, token encrypted at rest.
- [x] **14. Docs + ship** — spec, this plan, STATUS, master plan, CLAUDE.md, pushed.

## What went wrong, and what it cost

**The WebView was the wrong call and cost the most time in the project so far.**

The design listed "Meta may block embedded webviews" as an unproven risk and built on it
anyway. Meta does block them, and the failure mode gave nothing to go on: the login page
loaded, its DOM held the username and password fields at real screen positions, it
animated at 60fps — and painted pure white, with nothing in logcat.

It was only diagnosable by enabling `WebView.setWebContentsDebuggingEnabled(true)` and
driving the DevTools protocol over `adb forward`. A red `data:` URL proved the WebView
paints; Instagram's page collapsed `<html>` to `height: 0`. Blank under hardware *and*
software layers.

**Rule taken from it:** for anything that depends on a third party's behaviour, verify the
constraint *before* building on it. That rule was applied for the rest of the phase — the
custom-scheme redirect was tested in Meta's dashboard first (**rejected**), then the
GitHub Pages URL (**accepted**), and only then was code written.

## Two more bugs worth remembering

- **Meta rejects custom-scheme redirect URIs.** `autoinsta://oauth` was refused outright.
  Solved with an https bounce page on GitHub Pages that forwards to the custom scheme —
  Meta only ever sees the https address it approved, and no App Links verification is
  needed.
- **Meta sends ids as JSON numbers.** `"user_id": 28044336998528158` unquoted, against a
  `String` field, failed the entire parse and discarded a login the user had already
  approved. Nullable DTOs were not enough: nullability guards a *missing* field, not a
  *wrongly-typed* one.

## Files

**New:** `domain/TokenLifecycle.kt`, `data/prefs/TokenStore.kt`,
`data/remote/{InstagramAuthApi,NetworkModule,OAuthRedirectBus}.kt`,
`data/remote/dto/AuthDtos.kt`, `ui/settings/{SettingsScreen,SettingsViewModel,CustomTabLauncher}.kt`,
`docs/oauth/index.html`, tests: `TokenLifecycleTest`, `AuthDtosTest`

**Modified:** `AndroidManifest.xml` (intent-filter, singleTop), `MainActivity.kt`
(redirect handling), `AutoInstaApp.kt` (token store, refresh on launch),
`AccountRepository.kt`, `secrets.properties.example`, build files

## Noticed (not fixing now)

- The bounce page lives in `docs/`, which means enabling GitHub Pages publishes the
  project docs too. Harmless (no secrets), but worth a dedicated `gh-pages` branch if the
  repo ever needs to stay private.
- `AccountRepository.extractMetaMessage` parses Meta's error body with a regex rather than
  deserialising it, because the error shape varies. Works, but `MetaErrorEnvelopeDto`
  exists and could be used with a try/fallback.
- No instrumented test covers `TokenStore` against the real Keystore.
