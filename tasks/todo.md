# Tasks: Nothing Poweruser Remapper

Commands used throughout:
- Full gate: `./scripts/verify.sh` (lint, unit tests, debug build)
- Focused tests: `./gradlew testDebugUnitTest --tests '<pattern>'`
- On device: `./gradlew installDebug`

Terms (Profile, Fallback, Unassigned, Do Nothing, Foreground App, …) are defined in
[CONTEXT.md](../CONTEXT.md).

---

## Phase 1: Fork identity

## Task 1: Rename package and applicationId

**Description:** Change `namespace`, `applicationId` and the Kotlin package from
`dev.koukeneko.essentialkeytools` to `io.github.alessiocpt.powerremapper`, and change the app's
display name to "Nothing Poweruser Remapper". This is a mechanical move only, with no behaviour
change, done in one commit so later diffs stay clean.

**Acceptance criteria:**
- [x] No `dev.koukeneko` remains in `app/` (`grep -r dev.koukeneko app/` is empty), including the AIDL package and the manifest.
- [ ] The debug build installs next to upstream EssentialKeyTools as a separate app.
- [x] Internal class names (`EssentialKeyToolsApplication`, `EssentialKeyToolsTheme`) and the DataStore name stay as they are. The new `applicationId` already gives the app its own data directory, so renaming them would only add churn.

**Verification:**
- [ ] `./scripts/verify.sh` passes
- [ ] Manual check: install, enable the accessibility service, run the existing onboarding, confirm a double press still runs its action

**Dependencies:** None

**Files likely touched:** every Kotlin file (moved), `app/build.gradle.kts`, `AndroidManifest.xml`, `res/values/strings.xml`, `*.aidl`

**Estimated scope:** Large by file count, but mechanical (IDE move + sed). Allowed as a deliberate exception to the 5-file guideline.

---

## Task 2: Point links, update checker and crash reports at the fork

**Description:** Repoint every GitHub reference to `alessiocpt/Nothing-Power-Remapper`: update
checker, issues, privacy link, repository link, crash-report issue target. Replace the live
contributors list with a static "Based on" credits section naming EssentialKeyTools (KoukeNeko)
and NothingButtonRemapper (THE-TECH-CHANGER). Add both MIT copyright notices to `LICENSE`.

**Acceptance criteria:**
- [x] `grep -ri koukeneko app/src` only matches the credits text.
- [x] The `contributors/` package and its test are deleted; Settings shows the static credits.
- [x] `LICENSE` contains the fork's notice plus both upstream MIT notices.

**Verification:**
- [ ] `./scripts/verify.sh` passes
- [ ] Manual check: Settings › Check for updates queries the fork's releases; the credits section renders

**Dependencies:** Task 1

**Files likely touched:** `updates/GitHubReleasesService.kt`, `ui/ExternalLinks.kt`, `ui/screens/SettingsScreen.kt`, `contributors/*` (deleted), `LICENSE`

**Estimated scope:** Medium

---

## Task 3: Remove Play distribution, fastlane and the zh-TW translation

**Description:** Delete `fastlane/`, `distribution/whatsnew/`,
`.github/workflows/sync-play-metadata.yml`, and the Play upload steps and AAB build in
`release.yml`. Also remove the app's Play update path: `UpdateSource.PLAY_STORE`,
`PlayStoreUpdateChecker`, `openPlayStoreListing` and the Play strings. (The zh-TW translation was
already removed in `b252534`, together with the debug source set's copy.) Rewrite the README briefly for the fork: what it is, credits, how to install
from GitHub releases. Remove its screenshots, which pointed into `fastlane/`.

**Acceptance criteria:**
- [x] The release workflow builds and publishes only the signed APK to GitHub releases.
- [x] No `values-*` locale directories remain; lint has no missing-translation errors.
- [x] README has no links into deleted paths or upstream badges.

**Verification:**
- [ ] `./scripts/verify.sh` passes
- [ ] `actionlint` (or a careful read) on `release.yml`

**Dependencies:** Task 1

**Files likely touched:** `.github/workflows/release.yml`, `.github/workflows/sync-play-metadata.yml`, `README.md`, deleted dirs

**Estimated scope:** Medium

### Checkpoint A: after Tasks 1–3
- [x] `./scripts/verify.sh` passes
- [x] The app installs next to upstream; existing actions work unchanged
- [x] Review with the human before starting Profiles

---

## Phase 2: Profiles

## Task 4: Foreground App tracking

**Description:** Subscribe the accessibility service to `TYPE_WINDOW_STATE_CHANGED`.
A pure `ForegroundAppTracker` takes window-change events (package name, window type) and keeps
the Foreground App. It ignores SystemUI, the active keyboard's package, this app, and null
packages, so the last real app stays current. Show the current Foreground App live on the Key
Test screen, for diagnosis.

**Acceptance criteria:**
- [x] Opening the shade, keyboard or this app over Camera leaves the Foreground App as Camera.
- [x] The service config still does not request window content (`canRetrieveWindowContent` absent).
- [x] Unit tests cover the ignore rules and the "keep last real app" behaviour.

**Verification:**
- [x] `./gradlew testDebugUnitTest --tests '*ForegroundAppTracker*'`
- [x] Manual check: Key Test's recent-apps list was right after Camera, shade over Camera, keyboard and home screen (Recents is part of the launcher, so Key Test shows a short history)

**Dependencies:** Checkpoint A

**Files likely touched:** `service/ForegroundAppTracker.kt` (new), its test, `service/EssentialKeyDetectionService.kt`, `res/xml/accessibility_service_config.xml`, `ui/screens/KeyTestScreen.kt`

**Estimated scope:** Medium

---

## Task 5: Profile domain model and resolution

**Description:** Rename `KeyAction.None` to `KeyAction.DoNothing`, keeping persisted id `"none"`.
Add pure types:
- `GestureSlot`: Unassigned, or Assigned(action).
- `Fallback`: INHERIT or ISOLATED.
- `Profile`: package name, Fallback, slots.
- `ProfileSet`: the Default Profile, a `GestureActionMap`, plus app Profiles keyed by package.

`ProfileSet.resolve(foregroundApp, gesture)` returns the Action to run.
`ProfileSet.activeGestures(foregroundApp)` drives the classifier.

**Acceptance criteria:**
- [x] Inherit with an Unassigned slot gives the Default's action. Isolated with an Unassigned slot gives Do Nothing. An Assigned Do Nothing always gives Do Nothing.
- [x] A null Foreground App, or one with no Profile, uses the Default Profile.
- [x] New Profiles start as INHERIT with every slot Unassigned.

**Verification:**
- [x] `./gradlew testDebugUnitTest --tests '*Profile*' --tests '*GestureActionMap*'`

**Dependencies:** Checkpoint A (independent of Task 4)

**Files likely touched:** `actions/KeyAction.kt`, `settings/Profile.kt` (new; holds `GestureSlot`, `Fallback`, `Profile` and `ProfileSet`), tests, callers of `KeyAction.None`

**Estimated scope:** Medium

---

## Task 6: Profile persistence

**Description:** Store app Profiles as one JSON string in DataStore using `org.json`, with
encode/decode in a pure `ProfileSerialization` object. Unknown action ids decode to Do Nothing,
following `KeyAction.fromPersisted`. Expose `profileSet: Flow<ProfileSet>` and writers on
`SettingsRepository`: upsert profile, delete profile, set slot, set fallback.

**Acceptance criteria:**
- [x] A round trip preserves every slot state, the payload (Launch App's package) and the Fallback.
- [x] Corrupt JSON decodes to "no app Profiles", not a crash.
- [x] The Default Profile still reads and writes the existing keys.

**Verification:**
- [x] `./gradlew testDebugUnitTest --tests '*ProfileSerialization*' --tests '*SettingsRepository*'`

**Dependencies:** Task 5

**Files likely touched:** `settings/ProfileSerialization.kt` (new), its test, `settings/SettingsRepository.kt`, `SettingsRepositoryTest.kt`

**Estimated scope:** Medium

---

## Task 7: Service resolves actions through the active Profile

**Description:** The service collects `profileSet` instead of `gestureActionMap`. It resolves each
classified Gesture with the current Foreground App, and rebuilds the classifier's enabled Gestures
whenever the Foreground App changes, but only at the start of a new press, as the existing
key-test rebuild does. On the lock screen it uses the Default Profile. Haptic gating ("no active
gestures, no feedback") uses the active Profile's gestures.

**Acceptance criteria:**
- [x] With a Profile for app X that sets single press and is Isolated, double press does nothing in X and runs the Default action elsewhere.
- [x] An app switch during a multi-tap does not reset the press in progress.
- [x] A debug-only path seeds a test Profile, since the editor UI does not exist yet.

**Verification:**
- [x] `./scripts/verify.sh` passes
- [x] Manual check: the scenario above on the phone

**Dependencies:** Tasks 4, 6

**Files likely touched:** `service/EssentialKeyDetectionService.kt`, possibly a small debug hook in `DiagnosticsScreen.kt`

**Estimated scope:** Small–Medium

### Checkpoint B: after Tasks 4–7
- [x] All tests pass; a seeded Profile changes behaviour only inside its app
- [x] Review with the human before building UI

---

## Phase 3: Profile UI

## Task 8: Profile list

**Description:** Add a Profiles screen: a Default Profile entry plus one row per app Profile, with
the app icon and label. "Add profile" picks from installed apps, reusing `InstalledApps`, and
excludes apps that already have a Profile. Delete has a confirmation. Apps that are no longer
installed show "not installed" and are kept.

**Acceptance criteria:**
- [x] Add, delete and list all work, and the list survives an app restart.
- [x] An app with a Profile cannot be added twice.
- [x] An uninstalled app's Profile shows "not installed".

**Verification:**
- [x] `./scripts/verify.sh` passes
- [x] Manual check on the phone

**Dependencies:** Checkpoint B

**Files likely touched:** `ui/screens/ProfilesScreen.kt` (new), `MainActivity.kt` (navigation), `ui/components/FloatingNavBar.kt`, `res/values/strings.xml`

**Estimated scope:** Medium

---

## Task 9: Profile editor

**Description:** The Profile editor shows the four Gestures. Each row is Unassigned, Do Nothing,
or an Action from the existing `ActionPickerScreen`. An Inherit/Isolated Fallback switch sits at
the top. Unassigned rows show what will actually fire, greyed out (for example, "Flashlight (from
Default)" or "Does nothing (Isolated)"). The Home screen's gesture card keeps editing the Default
Profile.

**Acceptance criteria:**
- [x] All three slot states can be set and cleared.
- [x] The greyed preview updates immediately when the Fallback or the Default Profile changes.
- [x] The action picker is shared, not copied.

**Verification:**
- [x] `./scripts/verify.sh` passes
- [x] Manual check: build a Camera Profile from the UI only, then test it in Nothing Camera

**Dependencies:** Task 8

**Files likely touched:** `ui/screens/ProfileEditorScreen.kt` (new), `ui/screens/ActionPickerScreen.kt`, `ui/UiLabels.kt`, `res/values/strings.xml`

**Estimated scope:** Medium

---

## Task 10: Disclosure and privacy text

**Description:** Update the accessibility disclosure screen, the accessibility service description
and `PRIVACY.md`. They must now say the app reads which app is in the foreground (package names
only, never screen content, never stored or sent) in order to pick a Profile.

**Acceptance criteria:**
- [x] All three texts mention Foreground App reading and say what is not read.
- [x] The `AccessibilityDisclosureScreenTest` instrumented test still compiles; any text assertions are updated.

**Verification:**
- [x] `./scripts/verify.sh` passes

**Dependencies:** Task 4

**Files likely touched:** `res/values/strings.xml`, `PRIVACY.md`, `ui/screens/AccessibilityDisclosureScreen.kt`

**Estimated scope:** Small

### Checkpoint C: after Tasks 8–10
- [x] Profiles can be managed entirely from the UI on the phone
- [x] Review with the human

---

## Phase 4: Actions

## Task 11: Port the system actions

**Description:** Add `Home`, `Back`, `Recents` and `PowerMenu` (accessibility global actions;
`SplitScreen` was dropped: Nothing OS does not support it as a system action), and `AdjustVolume(direction)`, which raises or lowers the volume with the system
volume panel shown. Each one needs its id, persistence, picker entry, label and executor branch.

**Acceptance criteria:**
- [x] Each action works on the phone from any Gesture.
- [x] `AdjustVolume` persists its direction as the payload, and the round trip is tested.

**Verification:**
- [x] `./gradlew testDebugUnitTest --tests '*KeyAction*' --tests '*ProfileSerialization*'`, then `./scripts/verify.sh`
- [x] Manual check on the phone

**Dependencies:** Checkpoint C (independent of Tasks 12–15)

**Files likely touched:** `actions/KeyAction.kt`, `actions/ActionExecutor.kt`, `ui/screens/ActionPickerScreen.kt`, `ui/UiLabels.kt`, `res/values/strings.xml`

**Estimated scope:** Medium

---

## Task 12: Toggle Notifications and Toggle Quick Settings

**Description:** Add `flagRetrieveInteractiveWindows`. A pure helper decides whether the shade is
open from the window list: a SystemUI window covering the screen. If the shade is open in any
state, both actions close it with `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`. Otherwise each opens
its own panel.

**Acceptance criteria:**
- [x] A press opens, the next press closes, and so does a press after a manual swipe-down.
- [x] Toggle Quick Settings closes a shade that was opened as notifications.
- [x] The Foreground App does not change while the shade is open (relies on Task 4).

**Verification:**
- [x] Unit test for the shade-open helper; `./scripts/verify.sh`
- [x] Manual check on the phone, including on the lock screen

**Dependencies:** Checkpoint C

**Files likely touched:** `actions/KeyAction.kt`, `actions/ActionExecutor.kt`, `actions/ShadeState.kt` (new) + test, `res/xml/accessibility_service_config.xml`, `ui/UiLabels.kt`

**Estimated scope:** Medium

---

## Task 13: Launch Assistant

**Description:** Add an action that sends `ACTION_ASSIST` to the default assistant's package, read from
`Settings.Secure` (`assistant`, then `voice_interaction_service`). Sent without a package it opens a
chooser, since several apps answer it; `VOICE_COMMAND` is not answered by Claude. The picker shows a
hint when no assistant is set, and a press with none set shows a toast.

**Acceptance criteria:**
- [x] With Claude as the default assistant, the action opens Claude.
- [x] With no assistant set, the hint shows and the press shows a toast without crashing.

**Verification:**
- [x] `./scripts/verify.sh`; manual check on the phone with Claude set as the assistant

**Dependencies:** Checkpoint C

**Files likely touched:** `actions/KeyAction.kt`, `actions/ActionExecutor.kt`, `ui/screens/ActionPickerScreen.kt`, `res/values/strings.xml`

**Estimated scope:** Small

---

## Task 14: Launch Camera

**Description:** Add `LaunchCamera(mode)` with PHOTO, VIDEO or SELFIE. Photo and video use
`INTENT_ACTION_STILL_IMAGE_CAMERA[_SECURE]` and `INTENT_ACTION_VIDEO_CAMERA`; the secure variant
is used while the keyguard is locked. Selfie adds the front-facing extras that common camera apps
read, and the picker notes that some apps ignore them.

**Acceptance criteria:**
- [ ] All three modes open Nothing Camera in the right mode.
- [ ] On the lock screen, with "only when unlocked" off, photo mode opens over the keyguard.
- [x] The mode persists as the payload, and the round trip is tested.

**Verification:**
- [ ] `./scripts/verify.sh`; manual check on the phone

**Dependencies:** Checkpoint C

**Files likely touched:** `actions/KeyAction.kt`, `actions/ActionExecutor.kt`, `ui/screens/ActionPickerScreen.kt`, `res/values/strings.xml`

**Estimated scope:** Small

---

## Task 15: Shizuku key injection and the Camera Shutter action

**Description:** First, check by hand that `adb shell input keyevent 25` (volume down) and `27`
(camera) take a photo in Nothing Camera; pick the one that works, or stop and ask if neither does.
Then rename `IPackageToggleService` to `IShellService` and add `injectKey(keyCode)`. It runs
`input keyevent` and only accepts an allowlist containing the chosen key code. Add the
`CameraShutter` action, which calls it through the existing Shizuku binding.

**Acceptance criteria:**
- [ ] The manual key test result is recorded in the commit message.
- [ ] `injectKey` rejects key codes outside the allowlist, and a unit test covers the command builder.
- [ ] With Shizuku running, Camera Shutter takes a photo in Nothing Camera, and starts and stops recording in video mode.

**Verification:**
- [ ] `./gradlew testDebugUnitTest --tests '*Shell*'`, then `./scripts/verify.sh`
- [ ] Manual check on the phone

**Dependencies:** Checkpoint C

**Files likely touched:** `unlock/*.aidl`, `unlock/PackageToggleService.kt` (renamed), `unlock/ShizukuPackageToggler.kt`, `actions/KeyAction.kt`, `actions/ActionExecutor.kt`

**Estimated scope:** Medium

---

## Task 16: Camera Shutter UX

**Description:** Assigning Camera Shutter asks for Shizuku permission if it hasn't been granted.
Profiles and Gesture rows that use Camera Shutter show a warning badge while Shizuku isn't running.
A press while Shizuku is down shows "Shizuku isn't running" and does nothing else. The action's
description mentions that single press needs the unlock wizard.

**Acceptance criteria:**
- [ ] Shizuku is never requested unless Camera Shutter is assigned.
- [ ] The badge appears and disappears as Shizuku stops and starts, re-checked on resume.
- [ ] The toast appears on a press while Shizuku is stopped.

**Verification:**
- [ ] `./scripts/verify.sh`; manual check: reboot the phone, press without Shizuku, then start Shizuku

**Dependencies:** Task 15

**Files likely touched:** `ui/screens/ActionPickerScreen.kt`, `ui/screens/ProfilesScreen.kt`, `ui/screens/ProfileEditorScreen.kt`, `actions/ActionExecutor.kt`, `res/values/strings.xml`

**Estimated scope:** Medium

### Checkpoint D (Complete): after Tasks 11–16
- [ ] `./scripts/verify.sh` passes
- [ ] Every Action verified on the phone, in both a Profile and the Default Profile
- [ ] README lists the features; tag a first GitHub release

---

## Later
- [ ] Remove the onboarding language step and the Settings language card. The app is English only, so the step offers only "Device language" and "English". (Task 3 only removed the Traditional Chinese option.)
- [ ] Spike: cycle Nothing Camera zoom presets by clicking them (needs window-content access scoped to Nothing Camera; decide after the spike)
