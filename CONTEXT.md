# Nothing Poweruser Remapper

A power-user remapper for the Nothing Phone's Essential Key: each app can give the key's gestures
its own actions.

## Language

### Input

**Essential Key**:
The dedicated hardware key on Nothing phones, and the only physical input this app remaps.
_Avoid_: Power button, Nothing button, side key

**Gesture**:
One of the ways to press the Essential Key: single, double, triple or long press.
_Avoid_: Trigger, press type

### Mapping

**Action**:
Something the app does when a Gesture is recognised.
_Avoid_: Shortcut, command, intent

**Profile**:
A set of Gesture-to-Action assignments that applies while one particular app is the Foreground App.
_Avoid_: App rule, per-app mapping

**Default Profile**:
The Profile that applies when no app Profile matches the Foreground App.
_Avoid_: Global mapping, base profile

**Unassigned**:
The state of a Gesture that a Profile leaves open. It resolves according to the Profile's Fallback.
_Avoid_: Empty, unset

**Do Nothing**:
An Action that deliberately does nothing, used to block a Gesture in a Profile.
_Avoid_: None, disabled

**Fallback**:
An option on each Profile. **Inherit** means Unassigned Gestures use the Default Profile's
Action. **Isolated** means Unassigned Gestures Do Nothing.
_Avoid_: Override mode

**Foreground App**:
The app the user is working in. It ignores the system UI, the keyboard and this app itself, so
opening the notification shade over an app does not change the Foreground App.
_Avoid_: Current app, top window

### Actions

**Toggle Notifications**:
Opens the notification shade, or closes it if it is already open in any state.

**Toggle Quick Settings**:
Opens the fully expanded Quick Settings, or closes the shade if it is already open in any state.

**Adjust Volume**:
Raises or lowers the device's volume level.
_Avoid_: Volume up/down key

**Camera Shutter**:
Sends the shutter key to the Foreground App, so a camera app takes a photo or starts/stops video.
_Avoid_: Volume key, take photo

**Launch Assistant**:
Opens whichever app the device has set as its default digital assistant.
_Avoid_: Gemini, Google Assistant
