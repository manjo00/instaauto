# Design — Phase 4: Account connect

**Date:** 2026-08-25 · **Status:** approved (research answered the open questions) · **Phase:** 4

Connecting the app to a real Instagram account, so Phase 5 has something to publish with.

---

## What the research settled

Measured against Meta's live docs on 2026-08-25, not from memory:

| Question | Answer |
|---|---|
| Login type | **Business Login for Instagram** — no Facebook Page needed |
| App Review needed? | **No.** Posting to an account you own is Standard Access, the default |
| Permissions | `instagram_business_basic`, `instagram_business_content_publish` |
| API host | `graph.instagram.com` (not `graph.facebook.com`) |
| Redirect URI | `https://localhost/oauth` — confirmed accepted by Meta's dashboard |
| Token lifetime | short-lived 1h → long-lived **60 days**, refreshable |

## The four endpoints

```
1. https://www.instagram.com/oauth/authorize      → authorization code (in a browser)
2. https://api.instagram.com/oauth/access_token   → code becomes a 1-hour token
3. https://graph.instagram.com/access_token       → 1-hour token becomes a 60-day token
4. https://graph.instagram.com/refresh_access_token → extends a 60-day token
```

## Why a WebView, and what that costs

Meta's redirect URIs are HTTPS-only in every documented example, and v1 has no server —
so there is nowhere for an HTTPS callback to land. Instead the app opens Instagram's own
login page in a WebView and watches every navigation. When Instagram tries to redirect to
`https://localhost/oauth?code=...`, the app **cancels that navigation** and reads the code
out of the URL. The page is never loaded, so it does not matter that nothing is hosted there.

Honest about the trade-off: a WebView is less safe than a Custom Tab, because the host app
can in principle observe what is typed into it. Two things make it acceptable here:

- The app is used by exactly one person, who owns the account and built the app.
- The alternative needs a hosted HTTPS page, which contradicts the locked "no server" decision.

**Unproven risk:** Meta has historically blocked embedded webviews for *Facebook* login.
Whether Instagram's Business Login does the same is not documented either way. If it is
blocked, the fallback is a GitHub Pages redirect + Android App Link, which needs no server
either — just a static file. This is the main thing Phase 4 testing must find out.

The WebView is cleared of cookies before use and destroyed after, so it holds no session.

## Components

```
data/remote/
  InstagramAuthApi.kt     Retrofit: the three token endpoints
  dto/                    nullable DTOs for every response
data/prefs/
  TokenStore.kt           the access token, in EncryptedSharedPreferences
data/repository/
  AccountRepository.kt    connect / disconnect / refresh; owns the flow
domain/
  TokenLifecycle.kt       pure: is it expired, should it refresh, how long left
ui/settings/
  SettingsScreen.kt       shows the connected account, connect/disconnect
  InstagramLoginScreen.kt the WebView that captures the code
  SettingsViewModel.kt
```

### Why the token is not in Room

Room's database file is readable by anything with device access (root, backup extraction,
a compromised debugger). `EncryptedSharedPreferences` keeps the value encrypted at rest
with a key held in the Android Keystore, which is hardware-backed on most devices. The
**account** (username, id, expiry date) stays in Room because it is not secret and the UI
needs to observe it; only the token itself moves.

### Why `TokenLifecycle` is pure

"Should this token be refreshed?" depends on the wall clock, and testing it otherwise means
waiting up to 60 days. Same pattern as `PostValidator` and `ScheduleCalculator`: clock
passed in, no Android imports, instant unit tests for the boundaries.

Meta's rules, encoded there:
- A token can only be refreshed if it is **at least 24 hours old**
- A token not refreshed within **60 days** expires permanently — reconnect required
- Refresh proactively, not at the last moment: **refresh when under 10 days remain**

## The connect flow

1. Settings → **Connect Instagram**
2. WebView opens `.../oauth/authorize` with the two scopes
3. User logs in and approves
4. Redirect to `https://localhost/oauth?code=...` is intercepted, navigation cancelled
5. Code → short-lived token (`api.instagram.com`)
6. Short-lived → long-lived 60-day token (`graph.instagram.com`)
7. `GET /me?fields=user_id,username,account_type` for the profile
8. Token → `TokenStore` (encrypted); account row → Room
9. Settings shows the username and when the connection expires

**Instagram appends `#_` to the redirect URL.** The code must be stripped of it or the
exchange fails with an unhelpful error. This is a documented quirk and easy to miss.

## Refresh on launch

`AutoInstaApp.onCreate` triggers a check. If `TokenLifecycle` says refresh is due and
allowed, it refreshes in the background. Failure is not fatal — the UI shows the account
as needing reconnection rather than crashing.

## Acceptance

- Connect works end to end against the real account, producing a 60-day token.
- Settings shows the real username and expiry.
- Disconnect clears both the token and the account row.
- `TokenLifecycle` fully unit-tested, including the 24-hour and 60-day boundaries.
- Refresh path exercised (can be forced by writing an old timestamp in a debug build).
- Lint clean, tests green, installed on the Fold 7.

## Out of scope

Publishing (Phase 5), Cloudinary (Phase 5), multiple accounts (v1 is single-account).
