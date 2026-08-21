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

## In flight

Nothing. Phase 3 (scheduling engine) is next and not started.

## Not built yet

- **Phase 3** — `PostScheduler`, `PostWorker`, `BootReceiver`. *Nothing fires today.*
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

- **`fallbackToDestructiveMigration()` is on.** Any schema change wipes the user's
  scheduled posts. Must be replaced with a real migration before anyone relies on the app.
- **Doze / exact alarms** may slip overnight. Inherent to on-device scheduling;
  to be measured in Phase 3, not assumed.
- **Meta App Review** may be required for `instagram_content_publish` on non-developer
  accounts. To be confirmed in Phase 4 against the real API before building on it.
- **No in-app manual exists.** Per project convention a feature isn't done until the
  manual describes it — currently nothing is described.
