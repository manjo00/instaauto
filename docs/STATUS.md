# STATUS

**Living document.** What's shipped, what's in flight, and every gotcha that cost us
time — recorded with its *root cause*, so it never has to be rediscovered.

Last updated: 2026-09-03

---

## Shipped

| Phase | What it does | Verified by |
|---|---|---|
| 0 — Bootstrap | Compose app builds, installs, launches | Ran on emulator, screenshot |
| 1 — Data layer | Room DB: 5 tables, 5 DAOs, 4 repositories | `kspDebugKotlin` clean, `assembleDebug` green |
| 2 — Compose UI | Create / edit / delete a scheduled post; live queue | Manual run on emulator |
| 2.5 — Hardening | Media durability fix, pure validation rules, test harness | 14 unit + 14 instrumented tests, lint 0 errors |
| 3 — Scheduling engine | Alarms fire posts; stub publish + notification; survives reboot | 32 unit + 25 instrumented on the Fold 7, alarms verified via `dumpsys alarm` |
| 4 — Account connect | Instagram login via Custom Tabs; 60-day token, auto-renewed | 59 unit + 28 instrumented on the Fold 7; **connected to the real account**, token encrypted, weekly renewal job verified in `dumpsys jobscheduler` |
| 5a — Real publishing | Cloudinary upload + Graph API publish; image / Reel / carousel | 107 unit + 31 instrumented; **a real post reached the live account**; PNG→JPEG and 9:16→4:5 fitting proven against live Cloudinary |
| 5b — Fitting editor | Per-image preview, manual crop against Instagram's frame, pad/crop choice | 119 unit + 33 instrumented; schema v3 with migration tests |
| 5c — Posting queue | Recurring slots + an ordered pool; drag to reorder, catch-up window, pause | 169 unit + 52 instrumented, lint 0 errors; schema v4 with migration tests; drag proven end to end on the device |

## In flight

**Phase 6 — polish, presets + history screens, in-app manual.** Not started.

**Phase 5c shipped the posting queue.** Set the days and times once; finished pieces join
a pool and take the next free slot. An empty pool means the day is simply skipped — no
post, no alarm, nothing fires. Drag to reorder, pause, and a configurable catch-up window
that keeps a just-missed slot open for a post the phone could not publish *or* one added
afterwards.

**What is proven:** 169 unit tests (the planner, including both daylight-saving edges) and
52 instrumented tests — the v3→v4 migration, the queued-post worker cases, the drag
gesture, and the publish pipeline's readiness check. Lint 0 errors.

**And proven against the live account, 2026-09-03 16:01:** a queued post published for
real through the catch-up path — the owner widened the window to 2 days, which reopened a
slot that had passed six hours earlier, and the head of the queue filled it. Instagram
returned media id `18619848802020190`; the post then left the pool exactly as designed.
That is the queue *and* the publish-race fix confirmed end to end against the real API.

**What is still not proven:** a real *week*. One post through the queue is not a rhythm
kept while nobody is watching — that is still the open question, along with App Standby.

Two things built but never surfaced: `post_history` is written on every publish and has no
screen, and hashtag presets have a table and repository but no way to create one — so the
picker on the compose screen is always empty.

**Where the app actually is: it works.** It schedules a post, fires it on time, uploads the
media, publishes to Instagram, and records the result. Verified with a real post to the
live account on 2026-08-26.

5b added control over *shape*: tap any thumbnail for a full-screen preview with
Instagram's frame drawn over the artwork, choose Fit (bars) or Crop, and drag to pick what
survives. **Not yet tried on real artwork by the owner** — that is still open.

## Not built yet

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

### 🔴 A test suite that can post to the live account
**What happened:** `PostWorkerTest` drove the real publish path through
`AutoInstaApp.publishRepository`. Harmless while publishing was a stub — and a genuine
hazard the moment Cloudinary credentials existed, because the device test suite could
then upload media and publish to the owner's actual Instagram account.

**Fix:** `PublishRepository.publish` is `open` and `AutoInstaApp` exposes
`publishRepositoryOverride`. Tests substitute a fake that returns a canned
`PublishResult` and never touches the network. This also made the tests *better* — they
assert exact outcomes again (POSTED / FAILED / retry) instead of "whichever happened".

**Rule:** anything that performs a real-world side effect — posting, sending, paying —
needs a substitution seam **before** the credentials that make it live exist. Do not rely
on "the test data is invalid so it will fail anyway."

### 🟠 `connectedAndroidTest` uninstalls the app, wiping its data
**Symptom:** after a device test run, `run-as com.autoinsta` reported
`unknown package`, and the connected Instagram account was gone — database, encrypted
token and all.

**Root cause:** Gradle uninstalls both the app and test APKs when
`connectedAndroidTest` finishes. Uninstalling takes app-private storage with it.

**Rule:** the device test suite destroys app state. Never run it against a device holding
anything worth keeping without expecting to reinstall and reconnect afterwards, and
reinstall immediately after so the owner is not left with a missing app.

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

### 🔴 A container must be ready before you publish it — for every media type, not just video

**Symptom:** a real post failed overnight with the notification *"Post didn't go out —
Media ID is not available"*. Found 2026-09-03 on the tablet.

**What the evidence showed:** the post fired at exactly 10:00:00 and the failure was
recorded at 10:00:11. Eleven seconds covered uploading a 6.6 MB JPEG to Cloudinary,
creating the container **and** publishing it.

**Root cause:** publishing is always two steps — create a container, then publish it — and
Instagram has to go and *fetch* the media from Cloudinary in between. `publishReel` waited
for that (`awaitReady`). `publishSingle` and `publishCarousel` did not; they called
`media_publish` immediately. "Media ID is not available" is Meta's wording for *this
container is not ready yet*. It worked on 2026-08-26 only because the race happened to go
our way — the worst kind of bug, one that passes once and fails later.

**A second defect turned a blip into a lost post:** `classify()` mapped every 4xx to
`PermanentFailure`, so the worker never retried. The post was marked FAILED and dropped
out of the queue — and because both Home lists filter on `status = SCHEDULED`, it then
became invisible in the app entirely.

**Fix:** `awaitReady` now runs for all three pipelines, with a `PollCadence` per media
type — video keeps Meta's one-check-a-minute-for-five, images use 2s x 15. Crucially the
two cadences differ in what *running out* means: for video it is a failure, for an image
it publishes anyway, because refusing there would lose a post that was almost certainly
fine. And `PublishPolicy.isTransientRejection` now catches Meta's "not yet" wordings
before the 4xx rule, so it retries instead of giving up.

Proven by `PublishRepositoryTest`: on the old code the call log reads
`[getPublishingLimit, createImageContainer, publishContainer]` — no status check at all —
and the "Media ID is not available" case comes back `PermanentFailure`.

**Rule:** when an API hands you a two-step create-then-commit, assume the gap is real and
wait for it. And a provider saying "not yet" is not a 4xx-shaped "never" — read the body
before deciding a failure is permanent.

### 🔴 A catch-up slot stayed "open" after it was used — the queue would drain itself

**Symptom:** none yet, and that is the point. Caught 2026-09-03 by the owner asking the
right question — *"with a 2-day window, will it loop and post the whole queue?"* It would
have.

**Root cause:** `openCatchUpSlot` decided a slot was open purely from *time*: the most
recent slot within the window. Nothing recorded that a post had already gone out into it.
Every publish triggers a `replan()`, so the sequence was:

1. Post A fires at Wed 19:00 and publishes.
2. A leaves the pool, which replans.
3. Wed 19:00 is seconds ago — still inside the window — so it still looks open.
4. B is now head of the queue, takes it, and publishes ~10 seconds later. Then C.

**The whole pool would empty in minutes.** Worse, this needed no wide window: even the
2-hour default does it, because the loop runs at publish speed rather than clock speed.
The existing "only one catch-up" rule only ever prevented a burst *within a single plan*,
not across the replans each publish triggers.

**Fix:** a slot is open only if nothing has been published into it.
`ScheduledPostDao.getFilledSlotTimes` returns the `scheduledAt` of POSTED queued posts and
the planner excludes them. If the most recent slot is filled the answer is "none" rather
than an older one — falling back would be the same burst by another route.

Proven by `QueuePlannerTest`: three cases fail on the old logic, including
*publishing does not cascade through the whole queue*.

**Rule:** "is this slot available?" is a question about **what has happened**, not just
what time it is. Any rule derived from the clock alone will be re-satisfied on the next
tick — and a publish loop re-ticks immediately.

### 🟠 A test that assumes an empty device is a test that fails on a real one

**Symptom:** `QueueReorderTest` failed with `expected:<[first, second, third]> but was:
<[🚉 waiting for the train, …]>` — the owner's own post was in the queue.

**Root cause, in two parts.** The test asserted on the *whole* queue, which is only ever
empty on a fresh install. And `connectedAndroidTest` uninstalls and reinstalls the app,
which triggers Android auto-backup to **restore the Room database** — bringing back a
snapshot taken while that post was still SCHEDULED.

**Fix:** the test now asserts only about the three posts it created, and finds its drag
target by looking up its own caption's position rather than assuming index 2.

**Rule:** an instrumented test shares the device with real data. Assert about what the
test created, never about the state of the whole table.

### 🔴 An encrypted file in a backup is worse than no file at all

**Symptom:** the whole instrumented suite died with
`javax.crypto.AEADBadTagException` / `KeyStoreException: Signature/MAC verification failed`
from inside `TokenStore`, in a background coroutine on launch. Found on 2026-08-29 while
running the Phase 5c tests — `connectedAndroidTest` uninstalls and reinstalls the app, and
the app came back under a **new UID** each time (10701, then 10703).

**Root cause:** `android:allowBackup="true"` with no exclusions, so Android's backup and
device-transfer included `autoinsta_secure_prefs.xml` — the *encrypted* token file.
**Keystore keys are never backed up.** The ciphertext therefore comes back on a device
that has no key able to read it. `androidx.security` throws while **opening** the file,
before any of our code runs, so the failure lands wherever the store is first touched.
For this app that is `AutoInstaApp.onCreate`'s background refresh: a permanent crash on
launch, with no way out but clearing app data.

This was never queue-specific. **Samsung Smart Switch is the likeliest way it would have
reached the owner** — move to a new phone, and autoinsta never opens again.

**Fix, in two layers:**
1. `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` exclude the file from
   both cloud backup and device transfer. That is the real fix.
2. `TokenStore.openOrReset()` catches a failure to open, deletes the file *and* the
   Keystore alias, and starts fresh — so a wiped Keystore or a changed lock screen costs
   only the Instagram login, not the app.

Proven by `TokenStoreTest`: both recovery cases **fail on the old code**
(`CharConversionException: ... is not a valid hex string`) and pass on the new.

**Rule:** anything encrypted with a Keystore key must be excluded from backup, and must
have a recovery path for the day the key is gone. Encryption at rest buys a new failure
mode; budget for it.

**Also fixed alongside:** `AutoInstaApp`'s application scope had no
`CoroutineExceptionHandler`. `SupervisorJob` stops one child killing its siblings, but an
unhandled exception still reaches the thread's default handler and takes the process down.
Background upkeep must never be able to crash the app in front of the owner.

### 🟠 Compose merges a clickable card's semantics — tests aim at the wrong node

**Symptom:** `QueueReorderTest` found the drag handle, dragged it, and nothing happened.
No exception; the assertion simply never came true.

**What the evidence showed:** logging the matched nodes' bounds gave **984 × 294** — the
whole card, not a 24dp icon.

**Root cause:** `Card(onClick = …)` sets `mergeDescendants`, so every child's
`contentDescription` is merged into one node. `onAllNodesWithContentDescription("Drag to
reorder")` therefore returned the *card*, and `performTouchInput { down(center) }` landed
in the middle of it — where the only gesture is a long-press drag, which a plain drag never
triggers. The list just scrolled instead.

**Fix:** `useUnmergedTree = true` when aiming at a child inside a clickable container.

**Worth keeping:** hit-testing is *not* affected by semantics merging — a real finger on
the handle always worked. The bug was only ever in what the test was pointing at, which is
exactly the kind of failure that reads as "the feature is broken".

**Rule:** when a UI test does nothing at all, check *which node* it matched before
suspecting the code. Log `fetchSemanticsNodes()` bounds — it takes one run.

### 🟡 `@Before fun setUp() = runBlocking { … }` can stop being void

**Symptom:** `Failed to instantiate test runner class AndroidJUnit4ClassRunner`, which
aborted the entire class — 42 tests reported as one `initializationError`.

**Root cause:** buried three `Caused by`s down: `Method tearDown() should be void`. An
expression-bodied `= runBlocking { … }` adopts the block's last expression as its return
type, and the last line was `sourceDir.deleteRecursively()` — a `Boolean`. JUnit requires
`void`.

**Fix:** `= runBlocking<Unit> { … }`.

**Rule:** JUnit lifecycle methods must be explicitly `Unit` when written as expressions.
And when a runner fails to instantiate, read to the last `Caused by` — the top of that
stack says nothing useful.

### 🟡 Two fields that both look like "when"

**The trap:** a queued post has a `queuePosition` *and* a `scheduledAt`. Only the first is
the truth. `scheduledAt` is **derived** by `QueuePlanner` on every replan and read only by
the alarm machinery and the UI.

**Why it matters:** anything that writes a queued post's time outside `QueueRepository`
puts the two into disagreement, and the symptom is a post firing at a moment the queue
screen never showed. `PostRepository` therefore arms alarms only for `FIXED` posts, and
the queue DAO queries order by `queuePosition`, never by `scheduledAt`.

**Rule:** when a value is derived, say so where it is declared, and give exactly one class
permission to write it.

### 🟡 A test suite that edits the owner's posting schedule

**What happened:** `PostWorkerTest` and `QueueReorderTest` need slots to exist before the
planner has anywhere to put a post — and they run against the real database on the real
phone, same as everything else in `androidTest`.

**Fix:** both snapshot the queue settings in `@Before` and restore them in `@After`, and
track every slot they create so it can be deleted. This is the same class of hazard as the
publish seam below, one step milder: not "a test can post to the live account", but "a test
can quietly change how the live account posts".

**Rule:** an instrumented test touches real user state. Anything it creates, it deletes;
anything it changes, it puts back.

## Known risks still open

- **App Standby buckets.** Measured active on the Fold 7. autoinsta is a
  "set it and forget it" app, the usage pattern most likely to be demoted to a low-priority
  bucket. Doze itself is *not* the constraint (72 wake-ups/hour with the permission) — this
  is. Needs measuring over several days.
- **Exact-alarm permission can be revoked** at any time in Settings, silently degrading
  timing. The queue banner is the mitigation.
- ~~**Meta App Review**~~ — **resolved**: only needed for apps serving accounts you do not
  own. Standard Access covers this app.
- ~~**The login expires after 60 days**~~ — **mitigated**: renewal now runs on app launch,
  on a weekly background job (`TokenRefreshWorker`), and immediately before each publish.
  Weekly against a 60-day window means ~8 consecutive misses before any danger. Residual
  risk: an OEM that aggressively kills background work could still starve it, which is
  the same App Standby concern that affects posting.
- **The OAuth bounce page depends on GitHub Pages staying up** and the repo staying
  public. If either changes, login breaks until the redirect URI is re-pointed.
- **No in-app manual screen exists.** Per project convention a feature isn't done until
  the manual describes it. The queue's entries **are** written — `docs/manual/queue.md`,
  including its hidden gems — but there is nothing in the app that renders them yet, and
  nothing is written for Phases 0–5b. The screen is Phase 6.
- **The queue has not run a real week.** Everything is proven by test and by hand; what is
  unproven is the thing that only time can show — that a rhythm set once keeps working
  while nobody is watching. This is the same App Standby question as above, now with more
  riding on it.
