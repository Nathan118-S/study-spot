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

The app connects to `http://localhost:8787` by default, which is right for the
simulator with `cd server && npm run dev`. To change the server:

- per device: long-press the logo on the welcome screen (or, once signed
  in, *Settings → Server*) and enter your server's address, or
- per build: set the `STUDYSPOT_SERVER_URL` build setting on the target.

**Signing in happens on the website.** *Log in* and *Create your account* open
the Study Spot website (`/login?client=ios` or `/register?client=ios`) in a
private in-app browser session. When any sign-in method succeeds there (email
and password, a new account with its email code, or Google), the website
hands the session to the app and closes the browser. Two-factor verification
then happens in the app. The website address is the server address
unless you set one: with Docker (or any setup where one address serves both)
there's nothing to configure. For local development, where the website runs
separately on Vite's port, pass `--web http://<mac-ip>:5173` to
`scripts/configure.py` or set it under *Server settings*.

Use `https://` for anything other than local development. App Transport
Security only allows plain `http://` for localhost/LAN addresses.

## Features

Everything in the web app is available natively:

- **Sign in:** on the website (email/password, sign-up with email code,
  password reset, Google), then back in the app. The app shows a
  disabled-account notice and its own two-factor prompt (authenticator code,
  emailed code, or passkey)
- **Dashboard:** stats, the Unfinished / Completed / Graded tabs, filters and
  sort, multi-select bulk edits, swipe to complete/edit/delete, pull to sync,
  and the welcome greeting
- **Assignments:** create and edit (with templates), and a detail sheet with
  notes, subtasks, progress, score with letter grade, Classroom attachments,
  and syncing completion back to Classroom
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
  links into the app
- **Settings:** appearance (dark mode, sync with device, animations,
  welcome style), Google Calendar/Classroom, Blackboard, sync, the
  leaderboard, re-running onboarding, Excel export (via the share sheet),
  email reminders and do-not-disturb, the grading scale, two-factor methods
  (TOTP with a QR code, email, passkey), account name, and the danger zone
- **Onboarding wizard** for new accounts
- **Admin** (admin role only): people management, sending notifications,
  integration secrets, and deploy
- **Demo role:** the same demo data as the web app

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

## How sign-in with Google, Classroom and Blackboard works

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
work on iOS, and the reverse is true too. Authenticator-app and email 2FA work
without any of this setup.
