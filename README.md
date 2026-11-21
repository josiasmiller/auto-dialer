# Auto Dialer

This app was made by Claude in response to a Prank Call I received.

An Android app that calls a number on repeat, at an interval you choose. It can
hang up as soon as the other side answers and can hide your caller ID.

Built for and tested against **Android 8.0 (API 26)**. Runs on Android 7.0+ (API 24).

## Features

- **Repeat calling:** set the seconds between calls and an optional maximum number of calls
- **Hang up when answered:** detects the moment the call connects and ends it
- **Hide caller ID:** dials `*67` in front of the number (on by default)
- **Pick a contact:** choose a number from your contacts 
- **Stop from anywhere:** the app, the call screen, or the notification's Stop button
- **Restore stock phone app:** one tap to hand calls back to the phone's normal dialer

## How it works

Android only tells an app when a call is answered if that app is the
**default phone app**. Regular apps only see "off hook", which starts the moment
dialing begins. So Auto Dialer can become the default phone app:

- `CallMonitorService` is an `InCallService`. Android binds it only while this app is the
  default phone app, and it receives the real call states. An outgoing call goes
  `DIALING → ACTIVE`. `ACTIVE` means answered, so the call is disconnected right then.
- `InCallActivity` is a minimal call screen (Answer / Hang up / Stop auto-dialing). The
  default phone app has to provide one, and it also handles normal incoming calls.
- `DialerService` is a foreground service that runs the calling loop. It waits for each
  call to end before starting the next countdown, so calls never stack up.
- `CallManager` holds the state shared between the two services.

| File | Purpose |
|---|---|
| `MainActivity.kt` | Main screen, settings, default-dialer switching |
| `DialerService.kt` | Calling loop and notification |
| `CallMonitorService.kt` | Watches call state, hangs up on answer |
| `InCallActivity.kt` | In-call screen |
| `CallManager.kt` | Shared state |

## Building

Requires the Android SDK and **JDK 17 or 21**. Gradle 8.7 does not run on newer JDKs
(Android Studio's bundled JDK may be too new; set Gradle's JDK to 17/21 in
*Settings → Build → Gradle*).

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Using it

1. Install the APK and open the app. Allow the phone-call permission.
2. Tap **Make this the default phone app** and accept the prompt.
3. Enter a number or tap **Pick a contact**.
4. Set the seconds between calls and, optionally, a maximum number of calls.
5. Tap **Start**. Tap **Stop** (in the app, the call screen or the notification) to end it.

When you're done, tap **Restore stock phone app** to switch back to the normal dialer.

## Limitations

- **Voicemail counts as answered.** The network connects the call either way, so the app
  can't tell a person from voicemail.
- **Some carriers report "answered" early**, while it's still ringing.
- **`*67` is a US/Canada code.** Most other countries use `#31#`. Carriers that don't
  support it may ignore it. The **Open phone's Caller ID setting** button takes you to the
  phone's own "Hide number" setting, which applies to all calls.
- **Many people auto-reject hidden-number calls**, so those calls may never ring through.
- **While it's the default phone app, it handles every call** on the phone, including
  incoming ones, with its basic call screen. A manual call made during a run is also hung
  up when answered.
- On Android 10+, **Restore stock phone app** opens Settings → Default apps instead of
  switching directly, because Android no longer allows apps to do that.

## Responsible use

Repeated calls, especially with caller ID hidden, can be illegal harassment, and
automated calling is regulated in many places (for example the TCPA in the US). Only
call numbers you own or have permission to call.
