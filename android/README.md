# Study Spot for Android

Native Android app for Study Spot (Kotlin, Jetpack Compose, Material 3). It talks
to the same Express/PostgreSQL backend as the website and the iOS app (`ios/`),
which is the reference for features and behaviour.

- Application ID: `education.studyspot.app`
- minSdk 26, target/compile SDK 36
- Single activity, Navigation Compose, kotlinx.serialization, OkHttp, DataStore

## Open, build and run

**Android Studio:** File → Open → select this `android/` folder. Let Gradle sync,
pick the `app` run configuration and a device or emulator, and press Run.

**Command line** (JDK 17+; the wrapper downloads Gradle 8.14.3):

```sh
cd android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug           # install on a connected device/emulator
./gradlew :app:testDebugUnitTest      # JVM unit tests (JSON, config, timer, class schedule, push, export, 2FA, admin, calendar links)
./gradlew :app:lintDebug
```

Release builds (`assembleRelease`) use R8; add your own signing config.

## Server address

The app uses `https://studyspot.education` by default.

- **In the app:** on the welcome screen, **long-press the logo** to open the
  hidden Server settings (server address, optional separate website address,
  Test connection, Reset). Changing the server signs you out.
- **At build time:** set `studyspot.serverUrl` in `local.properties` (or
  `gradle.properties`), e.g. `studyspot.serverUrl=http://10.0.2.2:3001` to
  reach a backend running on your computer from the emulator. A saved
  `localhost` address is ignored when the build's default is a real server.

## Sign-in

Like iOS, sign-in happens on the website: Log in / Sign up open
`<website>/login?client=android` (or `/register`) in a Custom Tab. When any
sign-in method succeeds, the website redirects to
`studyspot://oauth/callback#token=…` (see `src/lib/iosHandoff.js`); the app
stores the token encrypted with an Android Keystore key and loads
`/api/auth/me`. Accounts with two-factor authentication then verify natively.

## Passkeys (Digital Asset Links)

Passkeys (2FA at sign-in, Settings → Security, and the admin unlock) use
Android Credential Manager with the **server's host as the relying party**
(`AppConfig.passkeyRelyingParty`, shown in Settings → Server). Android only
lets an app use passkeys for a domain that vouches for it, so the server must
serve `https://<server host>/.well-known/assetlinks.json` naming this app's
signing certificate:

1. Get the SHA-256 fingerprint of every key that signs the app you install:
   `./gradlew signingReport` (debug key) or
   `keytool -list -v -keystore <your.jks>`; for Play builds also add the
   **app signing key** from Play Console → Setup → App signing.
2. On the server set (see `server/.env.example`):
   ```properties
   ANDROID_APP_FINGERPRINTS=AB:CD:...:EF,12:34:...:56
   ANDROID_APP_ID=education.studyspot.app   # the default
   ```
   `server/src/server.js` then serves the statement (404 while unset). Behind a
   proxy, forward `/.well-known/` to the API, as for iOS.
3. Check it with Google's tester:
   `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<host>&relation=delegate_permission/common.get_login_creds`.

The device needs Google Play services (or another passkey provider) and a
screen lock. The server address must be `https://` (passkeys don't work
against `http://10.0.2.2` emulator backends).

**Server-side origin check:** a native Android passkey reports its origin as
`android:apk-key-hash:<base64url SHA-256 of the signing cert>` rather than
`https://<host>`. `server/src/lib/passkeys.js` (`originAllowed`, and the rpId
taken from the origin) currently accepts only `https:` origins, so it must also
accept `android:apk-key-hash:` origins whose hash matches one of
`ANDROID_APP_FINGERPRINTS` (and then compare the rpIdHash with the server's
own host) before Android passkeys verify.

## Settings, security and admin

- Settings sub-pages live in `ui/settings/<Name>SettingsScreen.kt`; shared
  building blocks (sections, toggles, busy buttons, confirm dialogs, the code
  field, time picker) are in `SettingsComponents.kt`.
- `SchoolAccessNote(service)` (`ui/settings/SchoolAccessNote.kt`) is the
  "Some schools block…" notice with the *Email my school's IT team* button; the
  email text is in `SchoolITEmail.kt` (same text as iOS and the website).
- **Google Calendar links** (for schools that block connecting Google):
  `IcalLinksSection.kt` in Settings → Google (and onboarding) lists the added
  links (Sync now, Rename, Import every event, Remove); `IcalGuide.kt` is the
  5-step full-screen guide (make the calendar public, copy its public URL,
  paste it), also opened from `SchoolAccessNote`'s *Use a calendar link
  instead*. Calls `icalListFeeds` / `icalAddFeed` / `icalUpdateFeed` /
  `icalRemoveFeed` / `icalSyncFeeds` through `DataRepository`, which also runs
  `icalSyncFeeds` in every sync when `connections.ical` is true.
- **Canvas, Schoology & Brightspace** (`LmsSettingsScreen.kt`, Settings list
  after Blackboard, and a compact card in onboarding): a row per system opens
  its 3-step guide in `IcalGuide.kt` (`IcalKind.Canvas` / `Schoology` /
  `Brightspace`); the links of those kinds are listed with the system's name in
  the status line (`IcalFeedRows(showKind = true)`).
- Data export (`data/ExcelExport.kt`) writes SpreadsheetML (`.xls`) to the
  cache and shares it through the `FileProvider` declared in the manifest
  (`res/xml/file_paths.xml`).
- `auth/PasskeyService.kt` wraps Credential Manager; `auth/OtpAuth.kt` parses
  the `otpauth://` setup URI (the QR code is drawn with ZXing).
- Admin (`ui/admin/`) keeps its own back stack inside the single `admin`
  route. Account management needs the 2FA step-up: `AdminUnlock.run { … }`
  shows the unlock dialog when needed, stores the token in
  `ApiClient.adminElevation`, and re-prompts and retries once on
  `elevationRequired`.

## Push notifications (optional)

Push uses Firebase Cloud Messaging **without** `google-services.json` or the
google-services Gradle plugin; Firebase is initialised at runtime from
`BuildConfig` (`push/PushManager.kt`). With no values set, push is simply
disabled and everything else still works: the study timer, its notifications
and the class countdowns, which the app then schedules itself (see below).

**1. Firebase project (app side)**

1. In the [Firebase console](https://console.firebase.google.com), create a
   project (Analytics isn't needed).
2. Add an **Android app** with package name `education.studyspot.app`.
   Skip the `google-services.json` download and the Gradle plugin steps.
3. In Project settings → General → Your apps, copy four values into
   `local.properties` (gitignored):

```properties
studyspot.fcm.projectId=your-project-id            # Project ID
studyspot.fcm.appId=1:1234567890:android:abcdef123456   # App ID
studyspot.fcm.apiKey=AIza...                        # Web API key
studyspot.fcm.senderId=1234567890                   # Project number / Sender ID
```

4. Rebuild. The four values are compiled in as `BuildConfig.FCM_*`.

**2. Server side.** In Firebase, Project settings → Service accounts →
*Generate new private key*. On the website, sign in as an admin and paste the
whole JSON file into **Admin → Integration Secrets → Firebase (Android notifications)**
(`FCM_SERVICE_ACCOUNT`; see `server/src/lib/fcm.js`). `GET /api/devices/config`
then reports `androidPush: true`.

**What the app does.** While signed in to a non-demo account with
reminders and push reminders on (`reminders_enabled`, `push_reminders`), the
app gets its FCM token and registers it with `POST /api/devices/android
{token, packageName}`; it re-registers when the token changes and when those
settings change, and sends `DELETE /api/devices/android` on sign-out or when
push is turned off. Notification permission (Android 13+) is asked once on
the first signed-in launch, after a short explanation.

The server only sends **data messages**; the app builds every notification
itself (`push/StudySpotMessagingService.kt`, parsing in `push/PushPayload.kt`):

| `type` | Shown as |
| --- | --- |
| `notification` | A notification on the `reminders`, `streaks` or `general` channel; tapping opens `url` (a `studyspot://` link) or the assignment |
| `class_start` | The class countdown live notification |
| `class_end` | Ends all class countdowns |
| `study_timer` | A running study-timer live notification (display only; used by Admin → Simulate) |

Notification channels: Assignment reminders, Streaks, General, Class
countdowns (`live_updates`) and Study timer (`timer`).

## Study timer

The timer screen (`ui/timer/`, dashboard top bar → timer icon, or
`studyspot://timer`) is the iOS Pomodoro: focus / short break / long break
with preset or custom lengths (1–180 min), start / pause / resume / reset /
skip, a long break after every 4th focus session, and an optional assignment
to study for. Each finished focus session is saved as a `StudySession`.

The timer lives outside the UI (`timer/TimerController.kt`): its state is
saved on every change and driven by the wall clock, and while it's running or
paused a foreground service (`specialUse`) shows an ongoing notification with
a countdown, a progress bar and Pause/Resume, Skip and Stop. An alarm at the
phase end finishes the phase (and alerts) even if the app was killed; a phase
that ended while the app was closed is finished and logged on the next launch.

## Live updates (Android's Live Activities)

The class countdown and the study timer are ongoing notifications with a
count-down chronometer (`liveupdates/LiveUpdates.kt`):

- **Android 16+ (API 36):** they request promotion
  (`setRequestPromotedOngoing`, `NotificationCompat.ProgressStyle`) and show
  as **Live Updates**: a status-bar chip with the countdown, pinned to the top
  of the shade and the lock screen. Users can turn promotion off per app in
  system settings; they then look like the older versions.
- **Older versions:** a regular ongoing notification with a count-down
  chronometer (the class countdown also lists what's due; the timer has a
  progress bar).

**Class countdown:** "Biology starts in 10 min", subject and room, a
countdown to the start and "N due today" with up to three titles. It goes
away 10 minutes after class starts. It comes from the server's `class_start`
push when push is set up, and is **also scheduled locally**
(`liveupdates/ClassAlarmScheduler.kt`): an alarm 10 minutes before the next
start of every weekly meeting, re-set when classes change, on app start,
after reboot / time changes and by a twice-daily WorkManager job. A push and
an alarm for the same meeting use the same notification id, so they never
double up. It follows the user's `class_live_activities` setting. Alarms are
exact when the user allows *Alarms & reminders* (`SCHEDULE_EXACT_ALARM`;
the timer screen offers a shortcut), otherwise inexact and possibly a few
minutes late. (`USE_EXACT_ALARM` isn't declared: Google Play reserves it for
alarm-clock and calendar apps.)

## Widgets

Two Glance widgets (`widget/`), in the Study Spot palette with light and dark
variants that follow the system theme:

- **Up Next** (resizable): small shows the next assignment with due-today /
  overdue counts; medium adds the next three; large shows six plus Due today,
  Overdue, This week and Done stats. Tapping an assignment opens it
  (`studyspot://assignment/<id>`).
- **Streak** (small): the best class streak, the class, and how many streaks
  are active or at risk. Opens the Streaks tab.

They read a small JSON snapshot (`widget/WidgetSnapshot.kt`) that the app
rewrites whenever assignments, classes or the user change, and clears on
sign-out. The widget picker shows previews (generated ones on Android 15+,
static layouts on 12–14).

## Screenshot tests

JVM screenshot tests (Robolectric + [Roborazzi](https://github.com/takahirom/roborazzi),
test-only dependencies) render the real screens with the demo data on a
Pixel 8–sized screen, light and dark, with a drawn status bar and gesture
handle so edge-to-edge problems show. No emulator or network is needed:
`ScreenshotApp` gives the real `AppContainer` an HTTP client that answers from
canned JSON (`FakeBackend`), and `DataRepository.seed(…)` fills the data.

```sh
./gradlew :app:recordRoborazziDebug     # write PNGs to app/build/outputs/roborazzi/
./gradlew :app:testDebugUnitTest        # renders every screen, writes/compares nothing
```

- Tests live in `app/src/test/java/education/studyspot/app/screenshots/`:
  `AppScreenshotTest` (welcome, the five tabs, empty states, assignment detail
  and form, class sheet, notifications, settings pages, timer, onboarding,
  admin people), `WidgetScreenshotTest` (Up Next widget at its three sizes,
  through Glance's RemoteViews) and `AppIconScreenshotTest` (adaptive icon
  layers with the 72 dp visible area and 66 dp safe-zone guides).
- No reference images are committed, so the normal test run never fails for
  lack of them. To use them as regression tests, record once, commit the PNGs
  somewhere stable (set `roborazzi.outputDir`), then run
  `./gradlew :app:verifyRoborazziDebug` / `compareRoborazziDebug`.
- Robolectric renders bottom sheets and dialogs in their own window without the
  fake insets, and widget corner radii (outline clipping) aren't drawn; check
  those on a device.

## Project layout

All code lives in `app/src/main/java/education/studyspot/app/`:

| Package | What's there |
| --- | --- |
| (root) | `StudySpotApplication`, `MainActivity` (deep links), `AppContainer` (app singletons, `LocalAppContainer`) |
| `core` | `AppConfig` (server/website URLs), `TokenStore` (Keystore-encrypted token), `ApiClient` (auth, entities, functions, connectors), `ApiException`, `Json` (lenient decoding, dates) |
| `model` | Server JSON models (`User`, `Assignment`, `SchoolClass`, `ClassMeeting`, `AssignmentTemplate`, `StudySession`, `AppNotification`, `Connections`, admin models) and `Grading` |
| `data` | `DataRepository` (shared state + mutations for every screen), `ThemeStore`, `MessageCenter` (snackbars), `DemoData` |
| `auth` | `AuthStore` (session state), `WebSignIn` (Custom Tabs) |
| `navigation` | `AppRoot`, `AppNavHost`, `MainScaffold` (top bar + bottom nav), `Routes`, `AppNavigator`, `Router`, `DeepLinkHandler` |
| `ui/theme` | Material 3 light/dark schemes from the Study Spot palette, `StudySpotTheme.colors` |
| `ui/components` | Shared composables (`SubScreenScaffold`, `LogoMark`, `EmptyState`, `Pill`, `StatTile`, `DateText`, …) |
| `ui/<feature>` | One package per feature: `dashboard`, `classes`, `calendar`, `streaks`, `insights`, `notifications`, `settings`, `admin`, `auth`, `onboarding`, `timer`, `templates` |
| `timer` | Study timer state machine (`TimerState`), `TimerController`, foreground service and notification actions |
| `widget`, `push`, `liveupdates` | Home-screen widgets (Glance), FCM and notification channels, live countdown notifications and class alarms |

Screens read state from `LocalAppContainer.current.repository` and move around
with the `AppNavigator` they receive. Dynamic color is off: the app always uses
the Study Spot palette (60% warm base, 20% navy, 20% orange accent), with an
in-app dark-mode override in Settings → Appearance.

Deep links: `studyspot://assignment/<id>`, `studyspot://tab/<dashboard|classes|calendar|streaks|insights>`,
`studyspot://timer`, `studyspot://oauth/callback#token=…`, `studyspot://blackboard?code=…`,
`studyspot://connected`. Try one with
`adb shell am start -a android.intent.action.VIEW -d "studyspot://tab/streaks"`.

## Library versions

Versions are in `gradle/libs.versions.toml`: AGP 8.13.2, Gradle 8.14.3,
Kotlin 2.3.21, Compose BOM 2026.06.01. Newer AndroidX releases need
compileSdk 37 and AGP 9; upgrade those together.
