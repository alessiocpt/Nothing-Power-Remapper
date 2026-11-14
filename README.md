# Nothing Poweruser Remapper

A power-user remapper for the Nothing Phone's **Essential Key**. Give single, double, triple and
long press their own actions, without root.

This is a personal hard fork of [EssentialKeyTools](https://github.com/KoukeNeko/EssentialKeyTools)
by KoukeNeko, with ideas from
[NothingButtonRemapper](https://github.com/THE-TECH-CHANGER/NothingButtonRemapper) by
THE-TECH-CHANGER. It is distributed on GitHub only. See
[ADR 0001](docs/adr/0001-hard-fork.md) for why it is a hard fork.

## Status

| Area | State |
|------|-------|
| Four gestures, haptics, "only when unlocked", Key Test, Diagnostics | Inherited from EssentialKeyTools, working |
| Optional single-press unlock wizard (manual or Shizuku) | Inherited, working |
| Per-app **Profiles** with Inherit / Isolated **Fallback** | Working |
| Home, Back, Recents, Power menu, Split screen, Adjust Volume | Planned |
| Toggle Notifications / Toggle Quick Settings | Planned |
| Launch Assistant, Launch Camera (photo / video), Camera Shutter | Planned |

The plan is in [tasks/plan.md](tasks/plan.md), and the vocabulary is in [CONTEXT.md](CONTEXT.md).

## Actions available today

Launch an app, toggle the flashlight, take a screenshot, lock the screen, play/pause, next or
previous track, cycle the ringer (ring → vibrate → silent), and toggle the Glyph light on Glyph
Matrix hardware.

Double, triple and long press work as soon as the accessibility service is on. Nothing OS reserves
the single press for Essential Space and Recorder. The built-in unlock wizard can disable those
apps, either through their App Info pages or through [Shizuku](https://shizuku.rikka.app/). They
stay disabled if you uninstall this app, so re-enable them from the wizard first.

## Install

Download the signed APK from the
[latest release](https://github.com/alessiocpt/Nothing-Power-Remapper/releases/latest). It installs
next to EssentialKeyTools, under its own application ID.

Getting started:
1. Read the accessibility disclosure, then turn on the service.
2. Learn your key in **Key setup**.
3. Map gestures on **Home**.
4. Check them in **Key test**. Key test shows the recognised gesture without running its action.

Requirements: Android 15 or newer, and a Nothing phone with an Essential Key. The app is English only.

## Privacy

- The app processes hardware-key events on the device. It never stores, uploads or shares them.
- It saves only the Essential Key identifier, on the device.
- To pick an app's profile, it reads which app is in front (package name only), kept in memory only.
- For the shade toggles it reads the size and type of on-screen windows. Android grants this only
  with window-content access, so it shows a "can view your screen" warning, but the app never reads
  what windows show, text fields or anything you type.
- There are no accounts, analytics, ads or tracking.

Read the full [Privacy Policy](PRIVACY.md).

## Technical reference

### How Essential Key detection works

The Essential Key reaches Android's input pipeline as `keyCode=0`, and the devices tested so far
report Linux `scanCode=250`. The public key-layout files leave it unmapped, so the accessibility
service can observe it. The service keeps only the identifier learned in Key setup, a classifier
recognises the gesture, and the mapped action runs. Consuming the event does not stop Nothing OS's
own single-press handling, which is why single press needs the separate unlock flow.

### Permissions and network access

- **Accessibility service:** observes Essential Key press and release events, recognises the
  gesture and runs the mapped action. It also receives window-state changes, reading only the
  package and class name, to know which app's profile applies.
- **`INTERNET`:** used only when you press **Check for updates**, to read this repository's
  GitHub releases.
- **`VIBRATE`:** haptic feedback, when you turn it on.
- **`ACCESS_NOTIFICATION_POLICY`:** lets the ringer-cycle action switch to silent.
- **`com.nothing.ketchum.permission.ENABLE`:** the Glyph light action.
- **`moe.shizuku.manager.permission.API_V23`:** optional. Lets the unlock wizard run `pm disable-user` /
  `pm enable` on Nothing's Essential packages.

### Development

You need JDK 21 and the Android SDK (platform 37). Point `local.properties` at the SDK, then run the
quality gate (lint, unit tests and a debug build):

```bash
./scripts/verify.sh
```

`scripts/simulate-key.sh` is a best-effort helper that injects a scancode-250 event with
`adb sendevent`. It usually needs root on stock firmware.

### Third-party software

The Glyph light action uses Nothing's official
[Glyph Matrix SDK](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit),
vendored as `app/libs/glyph-matrix-sdk-2.0.aar` under its own license (see
[`app/libs/GLYPH_MATRIX_SDK_LICENSE.md`](app/libs/GLYPH_MATRIX_SDK_LICENSE.md)).

## License

[MIT](LICENSE). Copyright alessiocpt, KoukeNeko (EssentialKeyTools) and THE-TECH-CHANGER
(NothingButtonRemapper).
