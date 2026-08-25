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
| Redirect URI | `autoinsta://oauth` — custom scheme, needed for Custom Tabs |
| Token lifetime | short-lived 1h → long-lived **60 days**, refreshable |

## The four endpoints

```
1. https://www.instagram.com/oauth/authorize      → authorization code (in a browser)
2. https://api.instagram.com/oauth/access_token   → code becomes a 1-hour token
3. https://graph.instagram.com/access_token       → 1-hour token becomes a 60-day token
4. https://graph.instagram.com/refresh_access_token → extends a 60-day token
```

## Why Chrome Custom Tabs (revised 2026-08-25, after the WebView failed)

**The original design used a WebView** to intercept the redirect, accepting "Meta may
block embedded webviews" as an unproven risk. It does block them, and the failure mode is
worse than an error: the login page loads, its DOM is fully populated with the username
and password fields, it animates at 60fps — and paints pure white. Nothing in logcat.

Diagnosed by enabling `WebView.setWebContentsDebuggingEnabled(true)` and driving the
DevTools protocol over `adb forward`. Meta server-renders the markup but their Bloks
runtime refuses to display inside an embedded browser, collapsing `<html>` to
`height: 0` with `overflow: auto scroll`. Forcing the height back did not help, and it
was blank under both hardware and software layers. Full write-up in `docs/STATUS.md`.

**Custom Tabs instead.** The login runs in real Chrome, so it renders exactly as it does
for any other site. It is also the safer choice — the login happens in Chrome's process,
so this app never has the chance to observe the password.

### Catching the return trip

Custom Tabs cannot be intercepted, so the redirect has to come back as an Android Intent.
The redirect URI is therefore a **custom scheme**, `autoinsta://oauth`, registered both in
the Meta dashboard and as an intent-filter in the manifest.

`MainActivity` is `launchMode="singleTop"`, so the redirect arrives at the running
instance via `onNewIntent` rather than stacking a second copy. It pulls out `code` (or
`error`) and publishes to `OAuthRedirectBus` — a small process-wide holder, needed
because the Intent arrives with no reference to the ViewModel waiting for it. The result
is consumed once and cleared, since an authorization code is single-use.

**Verified end to end** by firing the redirect with a fake code over adb: the app caught
it, extracted the code, and called `api.instagram.com/oauth/access_token`, which returned
400 as it should for a bogus code. Every link works except the real login page.

**Fallbacks if Meta rejects the custom scheme:** a GitHub Pages redirect plus a verified
Android App Link (needs `assetlinks.json` hosted, still no server), or keeping
`https://localhost/oauth` and having the user enable "Open by default" for the app.

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
  CustomTabLauncher.kt    opens Instagram's login in real Chrome
  SettingsViewModel.kt
data/remote/
  OAuthRedirectBus.kt     carries the result from MainActivity to the ViewModel
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
2. Chrome Custom Tab opens `.../oauth/authorize` with the two scopes
3. User logs in and approves
4. Instagram redirects to `autoinsta://oauth?code=...`; Android routes it to MainActivity
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
