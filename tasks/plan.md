# Implementation Plan: Nothing Poweruser Remapper

## Overview

Turn this fork of EssentialKeyTools into Nothing Poweruser Remapper, a personal app distributed
on GitHub only. The Essential Key's four Gestures get per-app **Profiles**: each Profile has a
**Fallback** of Inherit or Isolated on top of the **Default Profile**. The plan also ports
NothingButtonRemapper's system actions and adds new Actions: Toggle Notifications, Toggle Quick
Settings, Camera Shutter, Launch Camera and Launch Assistant. The domain language is defined in
[CONTEXT.md](../CONTEXT.md), and the hard-fork decision is recorded in
[ADR 0001](../docs/adr/0001-hard-fork.md).

## Architecture Decisions

- **Rename first.** The package and `applicationId` rename touches every file. Doing it as task 1,
  in a single mechanical commit, keeps it from conflicting with later work.
- **Foreground App from accessibility window events.** The existing `EssentialKeyDetectionService`
  also subscribes to `TYPE_WINDOW_STATE_CHANGED`. Ignoring windows is a pure, JVM-tested function:
  SystemUI, the active keyboard and this app's own windows are skipped. The app only reads package
  names, never window content (no `canRetrieveWindowContent`).
- **The profile model is pure Kotlin.** `Profile` and `ProfileSet` hold no Android types, so
  resolving a Gesture to an Action is unit-tested on the JVM. This follows the existing
  `GestureActionMap` approach.
- **The Default Profile keeps its existing DataStore keys.** App Profiles are stored as a single
  JSON string, using `org.json`, which upstream already uses with a JVM test dependency. The app
  has a new `applicationId`, so there is no saved data to migrate.
- **`KeyAction.None` is renamed to `KeyAction.DoNothing`** to match the glossary. Its persisted id
  stays `"none"`. Unassigned is a gesture-slot state, not an Action.
- **Shade state comes from the window list.** It is read with `getWindows()`, which needs
  `flagRetrieveInteractiveWindows`. Opening and closing use `GLOBAL_ACTION_NOTIFICATIONS`,
  `GLOBAL_ACTION_QUICK_SETTINGS` and `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`.
- **Camera Shutter reuses the existing Shizuku user service.** A second method on the AIDL interface
  runs `input keyevent`. Like `setPackageEnabled`, it only accepts an allowlist of key codes,
  because the process has shell privileges.
- **No generic key picker.** Every key injection is exposed as a named Action.

## Task List

The detailed tasks are in [todo.md](todo.md).

### Phase 1: Fork identity
- [ ] Task 1: Rename package and applicationId
- [ ] Task 2: Point links, update checker and crash reports at the fork; static credits
- [ ] Task 3: Remove Play distribution, fastlane and the zh-TW translation

### Checkpoint A
- [ ] `./scripts/verify.sh` passes; the app installs next to upstream; Default-mapping actions still fire

### Phase 2: Profiles (fail-fast core)
- [ ] Task 4: Foreground App tracking (shown live on the Key Test screen)
- [ ] Task 5: Profile domain model and resolution
- [ ] Task 6: Profile persistence
- [ ] Task 7: Service resolves actions through the active Profile

### Checkpoint B
- [ ] A Profile created through a debug path changes behaviour inside its app only

### Phase 3: Profile UI
- [ ] Task 8: Profile list (add from installed apps, delete, "not installed")
- [ ] Task 9: Profile editor (Unassigned / Do Nothing / Action, Fallback, greyed inherited action)
- [ ] Task 10: Accessibility disclosure and PRIVACY.md describe Foreground App reading

### Checkpoint C
- [ ] Profiles can be created, edited and deleted entirely from the UI, on the phone

### Phase 4: Actions
- [ ] Task 11: Port the system actions (Home, Back, Recents, Power menu, Split screen, Adjust Volume)
- [ ] Task 12: Toggle Notifications and Toggle Quick Settings
- [ ] Task 13: Launch Assistant
- [ ] Task 14: Launch Camera (photo / video / selfie, works on the lock screen)
- [ ] Task 15: Shizuku key injection and the Camera Shutter action
- [ ] Task 16: Camera Shutter UX (permission on first assignment, warning badge, toast)

### Checkpoint D (Complete)
- [ ] Every Action has been verified on the phone; README describes the fork

### Later (not in v1)
- Spike: cycle Nothing Camera zoom presets by clicking them.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Nothing Camera does not take a photo on a volume or `KEYCODE_CAMERA` press | High for Camera Shutter | In Task 15, test `adb shell input keyevent 25` and `27` by hand before writing UI. If neither works, stop and ask. |
| `TYPE_WINDOW_STATE_CHANGED` floods, or the package is missing for some windows | Med | The pure filter in Task 4 ignores null or unknown packages and keeps the last real app. Check with the live display on the Key Test screen. |
| `getWindows()` cannot tell a partly pulled-down shade from Quick Settings | Low | Both toggles close the shade in any state, as agreed, so only "is the shade open" matters. |
| Shizuku has stopped after a reboot | Med | Toast and editor badge (Task 16). The action never blocks the key. |
| The single press is reserved by Nothing OS, and Camera Shutter is likely wanted on single press | Med | The existing unlock wizard handles it. Note this in the Camera Shutter description. |
| Claude loses the assistant role after an update | Low | Launch Assistant shows a hint when no assistant is set (Task 13). |

## Open Questions

- None blocking. The design was settled in the grilling session (CONTEXT.md and ADR 0001).
