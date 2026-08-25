# SETUP GUIDE — step by step

**What this is:** Instagram won't let a random app post to your account. You have to
register your app with Meta (Instagram's owner) and give it permission. These steps get
you two passwords that let autoinsta prove it's allowed to post for you.

**Time:** ~30 minutes · **Cost:** free · **You'll need:** your phone and your computer

Work through this in order. If a screen doesn't look like what I describe, stop and tell
me what you see — Meta changes this dashboard often.

---

# STEP 1 — Switch Instagram to a Creator account
### 📱 On your phone

Instagram blocks apps from posting to *personal* accounts. Creator accounts are free and
your followers see no difference.

1. Open **Instagram**
2. Tap your **profile picture** (bottom right)
3. Tap the **☰ three lines** (top right)
4. Tap **Settings and privacy**
5. In the search box at the top, type: **account type**
6. Tap **Account type and tools**
7. Tap **Switch to professional account**
8. Tap through the intro screens
9. Choose a category — **Digital Creator** or **Artist** is fine
10. Choose **Creator** (not Business)
11. If it asks to connect a Facebook Page → **Skip**. You don't need one.

✅ **Done when:** your profile shows professional tools (you'll see "Professional
dashboard" on your profile).

---

# STEP 2 — Create your Meta app
### 💻 On your computer

1. Go to **https://developers.facebook.com/**
2. Click **Log in** (top right) and log in with **Facebook**
   - No Facebook account? You'll need to make one. It doesn't need to be used for anything else.
3. First time only: it may ask you to verify your phone number or email → do that
4. Click **My Apps** (top right)
5. Click the green **Create app** button
6. **App name:** type `autoinst` — note the missing final "a"
   - Meta **blocks any name containing "insta"** as a trademark term. The name here is
     cosmetic; only you see it, and it has no connection to what your phone app is called.
7. **Contact email:** your email
8. Click **Next**
9. It asks *what do you want your app to do?* → choose the option about
   **managing messaging and content on Instagram**
   - If instead it shows a list of app *types*, pick **Business**
10. Click **Next** → **Create app**
11. It may ask for your Facebook password → enter it

✅ **Done when:** you're looking at a dashboard with your app's name at the top.

---

# STEP 3 — Get your two passwords
### 💻 On your computer

This is the important step. **Read carefully — there are two similar-looking values and
picking the wrong one is the #1 thing that breaks.**

1. In the left sidebar, find and click **Instagram**
   - Don't see it? Look for **Add product** or **+ Add products**, find **Instagram**, click **Set up**
2. Click **API setup with Instagram login**
3. You'll see numbered sections. Find the one called
   **3. Set up Instagram business login**
4. Click **Business login settings**

You'll now see a panel with several values. You want these two:

| Look for | What it looks like |
|---|---|
| **Instagram App ID** | a long number, e.g. `990602627938098` |
| **Instagram App Secret** | letters and numbers — click **Show** to reveal it |

> ⚠️ **These are NOT the "App ID" and "App Secret" on the *Settings → Basic* page.**
> Those are different numbers and will not work. Make sure you're on the
> **Instagram → API setup with Instagram login** page.

5. Copy the **Instagram App ID**
6. Open the file `secrets.properties` in your project folder
   (it's already made for you, sitting next to `CLAUDE.md`)
7. Paste it after `META_APP_ID=` so the line reads e.g. `META_APP_ID=990602627938098`
8. Go back, click **Show** next to **Instagram App Secret**, copy it
9. Paste it after `META_APP_SECRET=`
10. **Save the file**

### Now the redirect address

Still on that same **Business login settings** panel:

11. Find the box labelled **OAuth redirect URIs**
12. Type or paste exactly: `https://localhost/oauth`  ← confirmed accepted
13. Click **Add** (or press Enter), then **Save changes**

> **"But that website doesn't exist?"** Correct, and that's fine. It's not a real
> website — it's just a signpost. When you finish logging in, Instagram tries to send
> you to that address, and autoinsta grabs the login code out of it before anything
> actually loads. Nothing is ever downloaded from it.

> The dialog may be titled **"Set up Instagram business login"** with a single
> **Redirect URL** box. That's the right place.

> Note: an earlier draft of this guide suggested `https://autoinsta.local/oauth`.
> Don't use it — `.local` is not a real internet domain and Meta may reject it.

✅ **Done when:** `secrets.properties` has two values filled in, and the redirect
address is saved in the dashboard.

---

# STEP 4 — Let your Instagram account use the app
### 💻 computer, then 📱 phone

Right now the app exists but your Instagram account isn't allowed to use it. Two halves:
invite, then accept.

### Part A — send the invite (computer)

1. Still in your app's dashboard, look in the left sidebar for **App roles** → **Roles**
   - Or, under the Instagram product, look for an **Instagram testers** section
2. Find **Instagram testers** and click **Add people** / **Add Instagram testers**
3. Type your **Instagram username** (the @name, without the @)
4. Click **Submit**

### Part B — accept the invite (phone) — ⚠️ people always forget this one

5. Open **Instagram** on your phone
6. Tap your **profile picture** → **☰** → **Settings and privacy**
7. In the search box, type: **apps**
8. Tap **Apps and websites**
9. Tap the **Tester invites** tab
10. You should see your app → tap **Accept**

✅ **Done when:** the invite says accepted. If you skip Part B, login will fail later
and the error won't explain why.

---

# STEP 5 — Cloudinary (free image hosting)
### 💻 On your computer

Instagram doesn't let apps upload a file directly. Instead it goes and *fetches* your
photo from a web address. So your art needs to sit somewhere public for a moment.
Cloudinary does that for free.

1. Go to **https://cloudinary.com/users/register_free**
2. Sign up (email or Google)
3. When you land on the dashboard, find **Cloud name** — a short word or phrase
4. Copy it → paste after `CLOUDINARY_CLOUD_NAME=` in `secrets.properties`
5. Click the **⚙️ gear icon** (Settings)
6. Click **Upload** in the settings menu
7. Scroll to **Upload presets** → click **Add upload preset**
8. **Change "Signing Mode" to `Unsigned`** ← this one matters
   - This lets the app upload without carrying your Cloudinary password inside it
9. Give it a name, e.g. `autoinsta`
10. **Don't turn on any resizing or quality options.** We want your art stored exactly
    as you exported it.
11. Click **Save**
12. Copy the preset **name** → paste after `CLOUDINARY_UPLOAD_PRESET=`
13. **Save the file**

✅ **Done when:** all four values in `secrets.properties` are filled in.

---

# STEP 6 — Tell me you're done

Your `secrets.properties` should now look like this (with your own values):

```properties
META_APP_ID=990602627938098
META_APP_SECRET=a1b2c3d4e5f6a7b8c9d0
META_GRAPH_VERSION=v21.0
CLOUDINARY_CLOUD_NAME=dxy123abc
CLOUDINARY_UPLOAD_PRESET=autoinsta
OAUTH_REDIRECT_URI=https://localhost/oauth
```

Message me **"setup done"** and I'll build the Connect Instagram button.

That file stays on your computer only — git is set to ignore it, so it never gets
uploaded to GitHub. I checked.

---

# If you get stuck

Tell me **which step number** and **what you see on screen**. Meta redesigns this
dashboard regularly, so the buttons may be named slightly differently than above —
that's normal and I can work from a description.

---

# Three things to know before you rely on this

**Your login expires after 60 days of not opening the app.** Meta gives out a pass that
lasts 60 days. autoinsta renews it every time you open the app, so opening it once every
couple of months is plenty. Leave it shut for two months straight and you'll have to
reconnect.

**PNG files lose a little quality; JPEG files don't.** Instagram only accepts JPEG.
If your art is a PNG, it has to be converted, and converting costs a small amount of
quality. If you can export as JPEG, do — it passes through untouched.

**Carousels crop to the first image.** In a multi-photo post, every image gets cropped
to match the shape of the **first** one. Put your best-framed piece first.

---

## Sources
- [Instagram Platform overview](https://developers.facebook.com/docs/instagram-platform/overview/)
- [Business Login for Instagram](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login/)
- [Content publishing](https://developers.facebook.com/docs/instagram-platform/content-publishing/)
