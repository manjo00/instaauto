# STATUS

**Living document.** What's shipped, what's in flight, and every gotcha that cost us
time — recorded with its *root cause*, so it never has to be rediscovered.

Last updated: 2026-08-21

---

## Shipped

| Phase | What it does | Verified by |
|---|---|---|
| 0 — Bootstrap | Compose app builds, installs, launches | Ran on emulator, screenshot |
| 1 — Data layer | Room DB: 5 tables, 5 DAOs, 4 repositories | `kspDebugKotlin` clean, `assembleDebug` green |
| 2 — Compose UI | Create / edit / delete a scheduled post; live queue | Manual run on emulator |
| 2.5 — Hardening | Media durability fix, pure validation rules, test harness | 14 unit + 14 instrumented tests, lint 0 errors |
| 3 — Scheduling engine | Alarms fire posts; stub publish + notification; survives reboot | 32 unit + 25 instrumented on the Fold 7, alarms verified via `dumpsys alarm` |

## In flight

Nothing. Phase 4 (Instagram account connect) is next and not started.

**Phase 3 caveat:** the app now fires posts, but `PostWorker` is a **stub** — it marks
POSTED, writes history and notifies, without contacting Instagram. Phase 5 replaces that
one block.

## Not built yet

- **Phase 4** — Instagram account connect (OAuth).
- **Phase 5** — Cloudinary upload + real Graph API publish.
- **Phase 6** — Polish, hashtag-preset management screen, in-app manual.
- **Phase 7** — Release prep.

---

## Gotchas — root cause, not just the fix

### 🔴 Photo Picker URIs expire with the process
**Symptom:** would have surfaced as a `SecurityException` in `PostWorker` at publish
time — hours or days after the post was created, with no way to reproduce on demand.

**Root cause:** `ActivityResultContracts.PickVisualMedia` returns a
`content://media/picker/...` address carrying a read grant scoped to the *calling
process*. Kill the process, lose the grant. The address string survives in the
database and still looks perfectly valid, which is what makes this so easy to miss.

**Why the standard fix doesn't apply:** `takePersistableUriPermission` only works on
URIs from a `DocumentsProvider` (`ACTION_OPEN_DOCUMENT`). Photo Picker URIs are not
persistable.

**Rule:** *any* media the app must read later than "right now" gets copied into
app-private storage at save time via `MediaFileStore`. Never persist a picker URI
and expect to read it later.

### 🔴 `gradlew` / `gradlew.bat` fail on this machine
**Root cause:** the project lives at `C:\سطح المكتب\autoinsta` — the parent folder is
Arabic ("Desktop"). cmd and bash mangle the non-ASCII path when the wrapper scripts
build their classpath string, producing `ClassNotFoundException:
org.gradle.wrapper.GradleWrapperMain` even though the jar is intact.

**Fix:** invoke the wrapper's main class directly with a **relative** classpath from
the project root (see CLAUDE.md → BUILD & RUN). Android Studio's own Run button is
unaffected — it uses its embedded Gradle runner.

**Also required:** `android.overridePathCheck=true` in `gradle.properties`. AGP
refuses non-ASCII paths by default. Empirically verified the full pipeline (AAPT2,
manifest merge, native libs, dex) works with it — the project does **not** need moving.

### 🟠 Lint was right once and wrong once — both cost a build

**Right:** `NotificationManagerCompat.notify()` requires `POST_NOTIFICATIONS`. The guard
existed but lived in a helper method, and lint only proves safety when the check is
visible *in the same method*. Inlined it.

**Wrong:** `SCHEDULE_EXACT_ALARM` is flagged `ProtectedPermissions` ("only granted to
system apps"). Its protection level is `signature|privileged|**appop**` — lint reads the
first two flags and stops. The `appop` part is precisely what lets a normal app hold it
once the user enables it in Settings. Proven on the device before suppressing: 87 regular
apps request it, six hold it, live alarms show `exactAllowReason=policy_permission`.

**Rule:** treat a lint error as correct until the platform proves otherwise — and when
suppressing, record the evidence next to the suppression, not in a commit message.

### 🔴 Forked JVMs get a mangled classpath — build output must stay ASCII
**Symptom:** `testDebugUnitTest` failed with
`ClassNotFoundException: com.autoinsta.domain.PostValidatorTest` — for a class Gradle
had just compiled and whose `.class` file was sitting on disk.

**Root cause:** this machine reports `sun.jnu.encoding=Cp1252`. Windows-1252 cannot
represent Arabic, so when Gradle **forks** a JVM (test workers, lint, R8) and hands it a
classpath containing the project path, that path arrives corrupted. Compilation always
worked because it happens in-process, where the path never round-trips through the OS
command line — which is exactly why this stayed hidden until the first test run.

**Fix:** the root `build.gradle.kts` points all build output at `C:/autoinsta-build`
(ASCII). Source stays where it is. Override with `-PbuildRoot=<path>`.
Side effect: the APK now lives at
`C:/autoinsta-build/app/outputs/apk/debug/app-debug.apk`, not under the project folder.

**Rule:** anything that forks a JVM hits this. On this machine "it compiles" is not
evidence that a task works — run the actual task.

**Bonus:** build cycle dropped from 5m23s to ~1m20s once output left the non-ASCII path.

### 🔴 Instagram's login will not render in a WebView — use Custom Tabs
**Symptom:** tapping "Connect Instagram" showed a blank white page. No error, no log
line, nothing in logcat.

**What the evidence showed**, via WebView remote debugging over adb (CDP):
- The WebView itself paints fine — a `data:` URL with a red background filled the screen.
- The page loads: `readyState: complete`, correct URL, title "Instagram".
- The DOM is fully present: `input[name=username]`, `input[name=password]`, a "Log in"
  button, all with real on-screen positions.
- The page runs: **74 animation frames in 1200ms**, `visibilityState: visible`.
- Yet `<html>` computes to `height: 0px` with `overflow: auto scroll` — a zero-height
  scroll container, which clips everything inside it.
- Forcing `height: 100%` had **no effect** (the containing block itself is zero-height).
  Forcing an explicit `753px` fixed the computed heights but the page still painted white.
- Blank under both `LAYER_TYPE_HARDWARE` and `LAYER_TYPE_SOFTWARE`.

**Root cause:** Meta server-renders the login markup (hence the DOM) but their
client-side Bloks runtime (`wbloks_*` classes) declines to display it inside an embedded
browser. This is deliberate on Meta's part, not a rendering bug — and it presents as a
blank page rather than an error message, which is what made it expensive to diagnose.

**Rule:** **never host an OAuth login in a WebView.** Use Chrome Custom Tabs, which runs
the real browser. This was flagged as an unproven risk in the Phase 4 design and accepted
anyway; the design should have treated "provider blocks embedded webviews" as the default
assumption rather than something to test later.

**Worth keeping:** `WebView.setWebContentsDebuggingEnabled(true)` plus
`adb forward tcp:PORT localabstract:webview_devtools_remote_<pid>` makes a WebView fully
inspectable over the DevTools protocol from the command line. That is the only reason
this was diagnosable at all.

### 🟠 Meta sends ids as JSON numbers, not strings
**Symptom:** a successful, user-approved login was thrown away with
`Unexpected JSON token at offset 245: Expected quotation mark '"', but had '2' instead at
path: $.user_id`.

**Root cause:** `user_id` was modelled as `String`. The token endpoint actually returns
`"user_id": 28044336998528158` — a bare number — while Graph endpoints return
`"id": "1784…"` quoted. kotlinx.serialization fails the **entire** parse on a type
mismatch, so one wrong field discarded a completed login.

**Fix:** `FlexibleIdSerializer` reads either form via `jsonPrimitive.content`. Covered by
`AuthDtosTest`, which also pins the `permissions` field (a list in some responses, a
comma-separated string in others — deliberately not modelled so `ignoreUnknownKeys`
drops it).

**Rule:** nullable DTOs are not enough on their own — the *type* has to tolerate what the
provider actually sends. For any external API, assume ids may arrive as either strings or
numbers and parse permissively.

### 🟠 Editing `secrets.properties` does not rebuild BuildConfig
**Symptom:** filled in real credentials, rebuilt, and `BuildConfig.META_APP_ID` was still
`""`. No error — the app would just fail to log in with a confusing API error.

**Root cause:** `secrets.properties` is read at Gradle *configuration* time, but it is not
declared as an input to `generateDebugBuildConfig`. Gradle sees the task as UP-TO-DATE and
skips it, so the generated file keeps the previous values.

**Fix:** after changing `secrets.properties`, force it:
`:app:generateDebugBuildConfig --rerun-tasks` (or a clean build).

**Rule:** after editing secrets, always verify the value actually landed:
`C:/autoinsta-build/app/generated/source/buildConfig/debug/com/autoinsta/BuildConfig.java`.
Never assume a credential reached the app because the file on disk looks right.

### 🟠 Android Studio's Run can silently not install
**Symptom:** "I don't see the app in the emulator" — but `assembleDebug` had succeeded
and the APK was on disk.

**Root cause:** the build step ran; the install/deploy step didn't. `adb shell pm list
packages | grep autoinsta` returned nothing.

**Fix:** `adb install -r C:/autoinsta-build/app/outputs/apk/debug/app-debug.apk` then
`adb shell am start -n com.autoinsta/com.autoinsta.MainActivity`. When installing over
a running build, `adb shell am force-stop com.autoinsta` first or you may look at the
old process and think nothing changed.

### 🟠 Coil renders nothing for a bare file path
**Root cause:** `AsyncImage(model = "/data/user/0/.../media/x.jpg")` treats the string
as a URI. With no scheme it isn't resolvable, so Coil draws an empty box — **no error,
no log**. Content URIs work fine, which is why this only appeared after the media-durability
change swapped stored addresses from `content://` to plain paths.

**Fix:** `ui/components/MediaModel.kt` → `mediaModel()` wraps a path in a `File`.
Every media preview goes through it.

### 🟡 Compose/AndroidX APIs that don't exist where you'd expect
Cost a full failed build each. All three are import problems, not logic problems:

| Wrong | Right |
|---|---|
| `ActivityResultContracts.PickVisualMediaRequest(...)` | `androidx.activity.result.PickVisualMediaRequest(...)` |
| `viewModelFactory { initializer { … } }` | Plain `object : ViewModelProvider.Factory` (the DSL didn't resolve against lifecycle 2.8.7 here) |
| `ExposedDropdownMenuBox` / `ExposedDropdownMenu` | Unresolved against Compose BOM 2024.10.01 — used `Box` + `DropdownMenu` instead |

**Rule:** when a Compose/AndroidX symbol won't resolve, check whether it lives in a
*different artifact* before assuming a version bump is needed.

### 🟡 A `@Composable` must not share a name with a class
`@Composable fun AutoInstaApp()` alongside `class AutoInstaApp : Application()` gives
`Overload resolution ambiguity`. Renamed the composable to `AppRoot()`.

---

## Known risks still open

- **App Standby buckets.** Measured active on the Fold 7. autoinsta is a
  "set it and forget it" app, the usage pattern most likely to be demoted to a low-priority
  bucket. Doze itself is *not* the constraint (72 wake-ups/hour with the permission) — this
  is. Needs measuring over several days.
- **Exact-alarm permission can be revoked** at any time in Settings, silently degrading
  timing. The queue banner is the mitigation.
- **Meta App Review** may be required for `instagram_content_publish` on non-developer
  accounts. To be confirmed in Phase 4 against the real API before building on it.
- **No in-app manual exists.** Per project convention a feature isn't done until the
  manual describes it — currently nothing is described.
