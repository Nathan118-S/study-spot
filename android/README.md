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
./gradlew :app:testDebugUnitTest      # JVM unit tests (JSON decoding, config)
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

## Push notifications (optional)

Push uses Firebase Cloud Messaging **without** `google-services.json` or the
google-services Gradle plugin; Firebase is initialised at runtime from
`BuildConfig`. With no values set, push is simply disabled and the app works
normally. To enable it, add to `local.properties` (values from your Firebase
project's Android app settings):

```properties
studyspot.fcm.projectId=your-project-id
studyspot.fcm.appId=1:1234567890:android:abcdef123456
studyspot.fcm.apiKey=AIza...
studyspot.fcm.senderId=1234567890
```

The backend does not yet accept Android device tokens; see `push/PushManager.kt`.

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
| `widget`, `push`, `liveupdates` | Home-screen widget (Glance), FCM, ongoing notifications |

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
