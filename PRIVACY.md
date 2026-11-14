# Privacy Policy — Nothing Poweruser Remapper

_Last updated: 14 November 2026_

Nothing Poweruser Remapper ("the app") is a free, open-source Android app that remaps the Nothing Phone
Essential Key. This policy explains what the app does — and does not do — with your data.

**In short: the developer does not collect, store, or sell personal information. The app has no
user accounts, analytics, advertising, or tracking. It makes no automatic network requests; the
only one is an update check that runs when you press its button.**

## Data the app collects

The developer collects none. The app has no analytics SDKs, advertising, telemetry, crash-reporting
services, accounts, or sign-in. It does not send your settings or hardware key-event data anywhere.
The app does record a crash report on your own device, described below, but nothing is transmitted
from it unless you choose to share it.

## Data stored on your device

Your onboarding progress and settings — which action each gesture (single, double, triple, or long
press) is mapped to, the per-app profiles you create (each names the app's package and the actions
you chose for it), your haptic feedback choices including any custom vibration pattern, whether
actions run only when the device is unlocked, your theme choice, and the learned scan code of your
key — are stored locally on your device using Android's DataStore. This data never leaves your device and is removed when you uninstall the app.

## Crash reports stay on your device

If the app crashes, it writes a diagnostic report to its private storage on your device so you can
see what happened. The report contains the app version, how the app was installed, your device model
and Android version, the time of the crash, and the technical stack trace. **It deliberately contains
nothing observed by the Accessibility Service** — no screen content, no window or notification text,
no record of which keys you pressed or which apps were in the foreground.

The report is never transmitted anywhere automatically. The app has no crash-reporting service and no
server to send it to. It leaves your device only if you open the Diagnostics screen and choose to copy
or share it yourself, in which case you decide where it goes and can read the whole report first. Only
the most recent report is kept, it is replaced by the next one, and you can delete it at any time with
the Clear report button or by uninstalling the app.

The Diagnostics screen's "Report on GitHub" button is the one place where a small part of this
leaves your device without a further step from you: opening the prefilled issue puts the app version,
your device model, and your Android version into the GitHub address as the browser opens it. The
crash report itself is only copied to your clipboard, so nothing else is transmitted unless you post
the issue yourself.

The Diagnostics screen also lists process exits that Android itself recorded for the app, such as
"Not responding" or a shutdown caused by low memory." That list comes from the operating system, stays
on your device, and is subject to the same rules.

## Data retention, deletion, and security

The local settings described above remain only until you change them, clear the app's storage, or
uninstall the app. The developer has no server-side copy to retain or delete. Android protects these
settings inside the app's private storage, and the app sends none of them over a network.

## Accessibility Service

The app uses an Accessibility Service to receive hardware key events, including the key code, scan
code, press or release state, and event time. These events are processed on your device, including
when the app is not open, to recognise a single, double, triple, or long press of your hardware
Essential Key and run the action you assigned. The service follows only the fixed gesture-to-action
mapping you set; it does not choose or plan actions. If you assign Screenshot or Lock screen, the
service invokes the corresponding Android accessibility system action only after it recognises that
assigned gesture. The service:

- does not read screen content or typed text;
- reads only the package and window class name of the app in front, to pick that app's profile (see
  below);
- does not keep a history or log of hardware key events;
- stores only the scan code you explicitly confirm in Key Setup, as described above;
- does not transmit or share hardware key event data.

### Which app is in front

To apply per-app profiles, the service is notified whenever the window in front changes. From each
notification it reads only the app's package name (for example `com.nothing.camera`) and the window's
class name, which it uses to tell an app's own screen from a dialog, the notification shade or the
keyboard. It never reads what the window shows. The current app, and on the Key Test screen the last
five, are kept in memory only while the service runs. They are not written to storage, not included
in crash reports, and never transmitted.

If you turn on **Run actions only when unlocked**, the service also asks Android whether the lock
screen is showing when a press or gesture would otherwise do something. The answer is used on the
spot and is neither stored nor transmitted.

Android requires an accessibility service to observe hardware keys, which is why the app needs this
permission. Immediately before opening Android's Accessibility Settings, the app presents a separate
in-app disclosure and asks you to choose "Agree: use accessibility service" or "Decline: don't use
accessibility service." Declining or dismissing the disclosure does not enable the service or open
Settings.

## Network access (INTERNET permission)

The app has a manual **Check for updates** button. It does not check until you press it:

- a stable copy requests the public stable-release list from the GitHub API; and
- a Preview copy requests the public pre-release list from the GitHub API.

GitHub receives ordinary connection metadata such as your IP address. The app compares GitHub version numbers on your device and
does not include your settings, mappings, scan code, or hardware key events in an update request. It
never downloads or installs an APK itself.

## Permissions used

- **INTERNET** — only when you press **Check for updates**, to fetch GitHub release metadata.
- **Accessibility Service** (`BIND_ACCESSIBILITY_SERVICE`) — to detect your Essential Key press.
- **Vibration** (`VIBRATE`) — to vibrate when you press the Essential Key or an assigned action runs,
  if you turn haptic feedback on.
- **Do Not Disturb access** (`ACCESS_NOTIFICATION_POLICY`) — so the ringer-cycle action can switch the
  phone to and from silent. You grant it in Android settings, and the app changes the ringer mode
  only when you trigger the gesture you assigned to it.
- **Glyph** (`com.nothing.ketchum.permission.ENABLE`) — to connect to Nothing's Glyph Matrix service
  for the Glyph light action on Nothing Phone (3).
- **Package visibility / queries** — to list your launchable apps in the action picker, and to check
  the enabled state of Nothing's Essential Space packages for the unlock wizard.
- **Shizuku** (`moe.shizuku.manager.permission.API_V23`, optional) — only if you have Shizuku and
  grant this app permission in it, the unlock wizard asks Shizuku to disable or enable Nothing's
  Essential Space packages. Nothing else is run through Shizuku, and nothing is sent over a network.

## Data sharing

The app does not share settings or hardware key-event data with third parties and has no backend
server of its own. It makes no automatic third-party connections. A manual update check contacts
GitHub. That request contains no settings or hardware key-event data, although the service provider
receives ordinary connection metadata.

## Children

The app is not directed at children and does not knowingly collect data from anyone.

## Changes to this policy

If this policy changes, the updated version will be published in this repository with a new
"last updated" date above.

## Contact

The developer is alessiocpt. Questions or privacy requests can be submitted through the public issue
tracker at
<https://github.com/alessiocpt/Nothing-Power-Remapper/issues>.

---

Nothing Poweruser Remapper is open-source — you can read the full source code at
<https://github.com/alessiocpt/Nothing-Power-Remapper> and verify everything described here.
