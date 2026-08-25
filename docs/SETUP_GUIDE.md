# SETUP GUIDE — accounts and credentials

**This is the one part of the project that has to be done by hand.** Nothing in Phase 4
or 5 can be tested until it's finished, because the app needs real credentials to talk
to Instagram.

Budget **30–45 minutes**. Everything here is free.

Last updated: 2026-08-25 · verified against Meta's docs on that date

---

## What you're setting up and why

Instagram doesn't let apps post by pretending to be you. Instead you register an
"app" with Meta, and your Instagram account grants that app permission to post on your
behalf. The credentials below are how autoinsta identifies itself as that app.

Two pieces of good news, both confirmed in Meta's current docs:

- **No Facebook Page needed.** Meta's newer "Business Login for Instagram" works with
  an Instagram account on its own. (Their older path required a linked Facebook Page.)
- **No App Review needed.** Review and Business Verification are only required for apps
  serving accounts *someone else* owns. Yours serves your own account, which Meta calls
  **Standard Access** — the default.

---

## Part 1 — Make your Instagram account a Creator account

Required: the API refuses to publish to a personal account.

1. Instagram app → your profile → **☰** → **Settings and privacy**
2. Search for **Account type** (sometimes under *Creator tools* or *Account type and tools*)
3. Tap **Switch to professional account** → choose **Creator**
4. Pick any category (e.g. *Digital Creator* or *Artist*)
5. If it offers to connect a Facebook Page, you can **skip it** — we don't need one

> Your followers see no difference. You can switch back any time.

---

## Part 2 — Create the Meta app

1. Go to **https://developers.facebook.com/** and log in with Facebook
   - First time only: accept the developer terms and verify by phone/email
2. **My Apps** → **Create App**
3. **App name:** anything (e.g. `autoinsta`) — only you see it
4. When asked what you want to do, choose the use case about
   **managing messaging and content on Instagram**
   - If you're shown app *types* instead, pick **Business**
5. Create the app

---

## Part 3 — Turn on Instagram login

1. In your new app's dashboard, find **Instagram** in the left sidebar
   (add the **Instagram** product if it isn't there yet)
2. Open **API setup with Instagram login**
3. Work through the numbered steps on that page. The one that matters is
   **Set up Instagram business login** → **Business login settings**

### The two values autoinsta needs

On that same **Business login settings** panel:

| Copy this | Into `secrets.properties` as |
|---|---|
| **Instagram App ID** | `META_APP_ID` |
| **Instagram App Secret** | `META_APP_SECRET` |

> ⚠️ These are **not** the same as the "App ID / App Secret" shown on the app's general
> Settings → Basic page. Use the ones under **Instagram → API setup with Instagram
> login**, or login will fail with a confusing error.

### The redirect URI

Still in **Business login settings**, find **OAuth redirect URIs** and add:

```
https://autoinsta.local/oauth
```

That address doesn't need to exist or resolve anywhere. autoinsta opens Instagram's
login page inside the app and intercepts the moment Instagram tries to redirect there,
reading the login code straight out of the URL. Nothing is ever actually loaded from it.

> If Meta rejects that value, try `https://localhost/oauth`. Tell me which one it
> accepted — it has to match the app's code exactly, including any trailing slash Meta
> adds for you.

---

## Part 4 — Add your Instagram account to the app

This is what keeps you on Standard Access (no review needed).

1. In the app dashboard, look for where Instagram accounts are added — usually
   **App roles → Roles**, or an **Instagram Tester** section under the Instagram product
2. Add your Instagram account
3. **Then accept the invite from Instagram itself:** Instagram app → **Settings and
   privacy** → search **Apps and websites** → **Tester invites** → **Accept**

> People miss step 3 constantly and then can't work out why login fails. If anything
> goes wrong later, check here first.

---

## Part 5 — Cloudinary (needed for Phase 5, do it now)

Instagram won't accept a file upload directly — it fetches your media from a public
URL. Meta's words: *"we cURL media used in publishing attempts, so the media must be
hosted on a publicly accessible server."* Cloudinary provides that free.

1. Sign up at **https://cloudinary.com/users/register_free**
2. On the dashboard, copy your **Cloud name** → `CLOUDINARY_CLOUD_NAME`
3. **Settings** (gear) → **Upload** → **Upload presets** → **Add upload preset**
   - **Signing Mode: Unsigned** ← the important one; lets the app upload without
     embedding your Cloudinary API secret in the APK
   - Name it something memorable → `CLOUDINARY_UPLOAD_PRESET`
   - **Leave every transformation off.** No resizing, no quality reduction. We want
     Cloudinary storing your art exactly as you exported it.
4. Save

---

## Part 6 — Put it all together

Create `secrets.properties` in the project root (next to `CLAUDE.md`). It is
git-ignored, so it never leaves your machine:

```properties
META_APP_ID=<Instagram App ID from Part 3>
META_APP_SECRET=<Instagram App Secret from Part 3>
META_GRAPH_VERSION=v21.0
CLOUDINARY_CLOUD_NAME=<from Part 5>
CLOUDINARY_UPLOAD_PRESET=<from Part 5>
OAUTH_REDIRECT_URI=https://autoinsta.local/oauth
```

Then tell me it's done and I'll wire up the connect flow.

---

## Things worth knowing before you rely on this

**Your login expires every 60 days.** Meta issues a token good for 60 days, refreshable
for another 60 — but *"tokens that have not been refreshed in 60 days will expire and can
no longer be refreshed."* autoinsta refreshes on launch, so opening the app every couple
of months is enough. Leave it closed for two months and you'll have to reconnect.

**JPEG only.** Meta: *"JPEG is the only image format supported."* PNG is rejected
outright. Since digital art is often exported as PNG, autoinsta will convert on the way
through — which does cost a little quality on those files. JPEG originals pass through
untouched.

**Posting limit.** Meta's docs say 100 posts per 24 hours in one place and 50 in
another. autoinsta assumes the lower number. Either is far above normal use.

**Carousels:** 2–10 items, and every image is cropped to match the **first** one's
aspect ratio. Put your best-framed piece first.

---

## Sources

- [Instagram Platform overview](https://developers.facebook.com/docs/instagram-platform/overview/)
- [Business Login for Instagram](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login/)
- [Content publishing](https://developers.facebook.com/docs/instagram-platform/content-publishing/)
