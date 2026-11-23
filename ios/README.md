# Study Spot for iOS

A native SwiftUI app for Study Spot. It talks to the same self-hosted
Express/PostgreSQL backend in `server/` that the web app uses, so accounts and
data are shared between web and iPhone/iPad.

## Requirements

- Xcode 16 or newer (the project uses Xcode's synchronized folders, so any
  `.swift` file added under `StudySpot/` is picked up automatically)
- iOS / iPadOS 17 or newer (Live Activities need an iPhone with iOS 17;
  the Dynamic Island needs iPhone 14 Pro or newer)
- A running Study Spot server (see the root `README.md`)

## Quick setup

```bash
cd ios
python3 scripts/configure.py --bundle-id com.yourname.studyspot \
    --server https://studyspot.example.com
```

This sets your own bundle ID (they must be unique per Apple account), the
matching App Group for the widgets, and the default server address (the
website is assumed to be on the same address), then regenerates the Xcode
project. For local development with the website on Vite, also pass
`--web http://<your-mac-ip>:5173`. Add `--team <TEAMID>` to
preset your signing team. You can re-run it any time.

## Run it

1. Open `ios/StudySpot.xcodeproj` in Xcode.
2. Select the **StudySpot** target → *Signing & Capabilities* and choose your
   team. You may also want to change the bundle identifier
   (`com.studyspot.app`).
3. Pick a simulator or device and press **Run**.

The app connects to `https://studyspot.education` by default. To use a
different server (e.g. `http://localhost:8787` with `cd server && npm run dev`
in the simulator):

- per device: long-press the logo on the welcome screen (or, once signed
  in, *Settings → Server*) and enter your server's address, or
- per build: set the `STUDYSPOT_SERVER_URL` build setting on the target.

**Signing in happens on the website.** *Log in* and *Create your account* open
the Study Spot website (`/login?client=ios` or `/register?client=ios`) in a
private in-app browser session. When sign-in succeeds there (username and
password, a passkey, or a newly created account), the website hands the
session to the app and closes the browser. Two-factor verification
then happens in the app. The website address is the server address
unless you set one: with Docker (or any setup where one address serves both)
there's nothing to configure. For local development, where the website runs
separately on Vite's port, pass `--web http://<mac-ip>:5173` to
`scripts/configure.py` or set it under *Server settings*.

Use `https://` for anything other than local development. App Transport
Security only allows plain `http://` for localhost/LAN addresses.

## Features

Everything in the web app is available natively:

- **Sign in:** with a username and password on the website (accounts from
  before usernames existed use their old email address as their username),
  then back in the app. The app shows a disabled-account notice and its own
  two-factor prompt (authenticator code or passkey)
- **Dashboard:** stats, the Unfinished / Completed / Graded tabs, filters and
  sort, multi-select bulk edits, swipe to complete/edit/delete, pull to sync,
  and the welcome greeting
- **Assignments:** create and edit (with templates), and a detail sheet with
  notes, subtasks, progress, score with letter grade, Classroom attachments,
  and syncing completion back to Classroom
- **Scan a worksheet:** on the new-assignment form, scan a handout with the
  document camera (or pick a photo) to prefill the title, due date, class and
  description. Text recognition runs on the device (Vision); nothing is
  uploaded. Parsing rules: `Logic/WorksheetParser.swift` (same as Android's
  `logic/WorksheetParser.kt`)
- **Study timer:** Pomodoro with focus/short/long phases and custom lengths.
  Focus sessions are logged to the server, and a local notification fires when
  a phase ends while the app is in the background
- **Classes:** list, add, edit (color tags, teacher), delete
- **Calendar:** month grid. Long-press an assignment and drag it to another
  day to reschedule it
- **Streaks:** per-class streaks, verified/unverified status, summary, and the
  opt-in leaderboard
- **Insights:** weekly completion charts built with Swift Charts
- **Notifications:** in-app notification center, mark read, clear, and deep
  links into the app; assignment reminders as push notifications (see below)
- **Settings:** appearance (dark mode, sync with device, animations,
  welcome style), Google Calendar/Classroom (plus Google Calendar links, below),
  Blackboard, sync, the
  leaderboard, re-running onboarding, Excel export (via the share sheet),
  assignment reminders (iPhone notifications, reminder times) and
  do-not-disturb, the grading scale, two-factor methods (TOTP with a QR code,
  passkey), account name, username and password, and the danger zone
- **Onboarding wizard** for new accounts
- **Google Calendar links:** for schools that block connecting a Google
  account, *Settings → Google → Add a calendar link* (also in onboarding and
  the school-IT notice) opens a 5-step guide to making a calendar public and
  pasting its public URL (secret iCal addresses work too). Links are listed
  there with sync, rename, "import every event" and remove, and are synced
  with everything else (`icalSyncFeeds`). Code:
  `StudySpot/Views/Settings/CalendarLinkViews.swift`
- **Admin** (admin role only): a full account manager for each user (profile
  and username, role and disabling, 2FA methods, temporary or chosen
  passwords, signing out everywhere, every setting, connections, push
  devices, and browsing, editing and deleting their assignments, classes,
  templates, study sessions and notifications), plus simulating notifications
  and Live Activities on their iPhones (*Simulate on iPhone*), test notifications,
  merging, sending notifications, integration secrets, and deploy. Opening
  anyone's account needs a two-factor confirmation (authenticator code or
  passkey) that stays unlocked for 15 minutes; admins must have 2FA turned on
- **Demo role:** the same demo data as the web app

## Colors (60 / 20 / 20)

The app's colors follow the 60/20/20 rule, defined in `Palette`
(`StudySpot/State/ThemeStore.swift`), with light and dark variants:

- **60% base:** a near-white canvas with a hint of warmth (`#FEFDFB`; `#15120F` in dark mode)
  behind every screen. The live background's glows are kept faint so it
  stays dominant.
- **20% secondary:** deep navy (`#1F2B47`; `#A9B8DC` in dark mode) for
  structure: navigation titles, unselected tabs, settings icons, stat
  values, chips, secondary buttons and progress tracks.
- **20% accent:** Study Spot orange (`#F19D37`) for actions and highlights:
  main buttons, the selected tab, toggles, progress, today and selection.

Red, green and orange status colors (overdue, done, due today) keep their
meaning and sit outside the split.

## Liquid Glass

Built with **Xcode 26**, the whole app uses Liquid Glass on iOS 26: the tab
bar, toolbars, sheets and menus adopt it automatically, and the app's own
cards, stat tiles, buttons, toasts, calendar days and the floating bulk-edit
bar use `glassEffect` and the `.glass` / `.glassProminent` button styles.
Behind every screen is a live background of slowly drifting brand-colored
glows (the same as the website's). It stops moving when animations are
turned off in Settings or Reduce Motion is on. The helpers live in
`StudySpot/Components/Glass.swift`. On iOS 17–18, or when built with
Xcode 16, they fall back to frosted materials.

## Push notifications: assignment reminders and class Live Activities

Both features use Apple Push Notifications (APNs) from your server, so they
share one setup. It needs a paid Apple Developer account and a real device
(the simulator can't receive these pushes):

1. In the Apple Developer portal, under *Keys*, create a key with **Apple
   Push Notifications service (APNs)** enabled, and download the `.p8` file.
2. In **Admin → Integration Secrets → Apple Push**, paste the `.p8` file's
   contents, the key ID and your team ID.
3. Build the app with the Push Notifications capability:
   `python3 scripts/configure.py --push` (`--no-push` turns it off).

Without this setup, registration fails quietly and everything else works:
reminders still land in the in-app notification center, and class Live
Activities use the local fallback described below.

### Assignment reminders

At the reminder times chosen under *Settings → Reminders*, the server sends a
notification for each upcoming assignment (respecting do-not-disturb). It
shows on the Lock Screen, as a banner while the app is open, and in the
in-app notification center. Tapping it opens the assignment. After sign-in
the app asks for notification permission and registers the device; signing
out unregisters it. Users can turn reminders off entirely (*Assignment
reminders*) or keep them in the app only (*iPhone notifications*).

### Class Live Activities

Give a class its **meeting times** (and optionally a subject and room) in
the app or on the website. Ten minutes before each class, a Live Activity
appears on the Lock Screen and in the Dynamic Island. It shows the class,
subject and room, a circular countdown to the start, and anything due that
day. It ends shortly after class starts. Users can turn it off under
*Settings → Reminders*.

There are two ways it can start:

- **When the app is closed:** with the push setup above, the server starts
  it with an ActivityKit push.

The study timer's Live Activity can also be started by push (iOS 17.2+), which
admins use from *Admin → People → an account → Simulate on iPhone* to test
notifications and both Live Activities on a real device.
- **Without push:** the app schedules a notification 10 minutes before each
  class. Opening it, or having the app open at that time, starts the Live
  Activity.

## App icon (Liquid Glass)

`StudySpot/AppIcon.icon` is an Icon Composer icon: the website's yellow
graduation cap on a light background, split into three glass layers (cap
body at the back, tassel in the middle, mortarboard in front). On iOS 26 and
later, the system renders it with Liquid Glass and creates the dark, clear
and tinted variants. Open it in **Icon Composer** (bundled with Xcode 26) to
adjust the glass, shadows or colors; the layer artwork is the SVGs in
`AppIcon.icon/Assets/`.

`Assets.xcassets/AppIcon.appiconset` holds a flat render of the same design
for Xcode versions without Icon Composer support. If Xcode 26 warns about
two icons named `AppIcon`, delete the `.appiconset`; Xcode 26 generates the
icons for older iOS versions from the `.icon` file.

## Widgets, Lock Screen widgets and Live Activities

The **StudySpotWidgets** extension adds:

- **Up Next** for the Home Screen (small, medium, large): the next
  assignments, what's due today and what's overdue. Tap a row to open that
  assignment.
- **Up Next** for the Lock Screen: circular (count due), rectangular (next
  assignment) and inline (above the clock).
- **Streak** (small, and circular/rectangular on the Lock Screen): your best
  class streak and how many are active or at risk.
- **Study timer Live Activity:** starting the timer puts a countdown on the
  Lock Screen and in the Dynamic Island. It keeps counting while the app is in
  the background, shows when it's paused, and stays visible for a while after
  a phase finishes.

The app shares a small snapshot of your data with the widgets through the App
Group `group.com.studyspot.app`, and refreshes them whenever assignments or
classes change. If you change the bundle identifier, rename the App Group in
both `.entitlements` files and in `SharedStore.appGroup`
(`Shared/SharedModels.swift`). The project file is generated by
`scripts/gen_project.py`.

## How Google Calendar, Classroom and Blackboard connections work

These flows open in a secure in-app browser (`ASWebAuthenticationSession`) and
return to the app through the `studyspot://` URL scheme:

- The Google connectors call the server with
  `?client=ios`, and the server redirects to `studyspot://…` instead of the
  web frontend. No extra Google Cloud configuration is needed, because the
  redirect URIs registered with Google stay the same.
- Blackboard still redirects to the web app's `/blackboard/callback` page
  (`BLACKBOARD_REDIRECT_URI`). When the connection was started from iOS (its
  state starts with `ios.`), that page hands the code straight back to the
  app. Your web frontend therefore has to be deployed at the configured
  redirect URI.

If you change the URL scheme, update `CFBundleURLSchemes` in
`StudySpot/Info.plist`, `AppConfig.urlScheme`, the server's `IOS_URL_SCHEME`,
and the scheme hard-coded in `src/main.jsx`.

## Passkeys (optional)

Passkeys are tied to a domain, so they need a paid Apple Developer team and
some setup:

1. On the server, set `IOS_APP_ID=<TEAM_ID>.<bundle id>` in `server/.env`.
   The API then serves `/.well-known/apple-app-site-association`. Make sure
   `https://<your domain>/.well-known/apple-app-site-association` reaches it
   (proxy `/.well-known/` to the API).
2. In Xcode, add the *Associated Domains* capability to the **StudySpot**
   target with `webcredentials:<your server's domain>`.

The app uses the server's host as the relying-party ID. If the web app and
the API are served from the same domain, passkeys created on the web also
work on iOS, and the reverse is true too. Authenticator-app 2FA works without
any of this setup.
