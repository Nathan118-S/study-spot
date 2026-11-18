# Study Spot — App Store Connect

Everything to fill in for the first iOS release, in the order App Store Connect
asks for it. Character limits are Apple's; every text below fits.

Before you start, update the server (so account deletion and the support page
are live) and make sure these load:

- https://studyspot.education/privacy
- https://studyspot.education/support
- Set up the support mailbox `support@studyspot.education` (e.g. Cloudflare →
  Email → Email Routing, forwarding to your own inbox), or change the address
  in `src/lib/support.js`.

---

## 1. New App (My Apps → +)

| Field | Value |
|---|---|
| Platforms | iOS |
| Name | **Study Spot** (if taken: **Study Spot: Homework Planner**) |
| Primary language | English (U.S.) |
| Bundle ID | the one you set with `configure.py --bundle-id` (e.g. `com.yourname.studyspot`) |
| SKU | `studyspot-ios` (anything unique; never shown) |
| User access | Full access |

## 2. App Information

| Field | Value |
|---|---|
| Subtitle (30) | **Assignments, all in one place** |
| Category | Primary: **Education** · Secondary: **Productivity** |
| Content rights | "No, it does not contain, show, or access third-party content" — it shows only the user's own coursework, imported at their request. |
| Age rating | Answer **None / No** to every question → **4+** (no unrestricted web access, no user-to-user messaging, no gambling, no mature content). |
| Made for Kids | No |
| License agreement | Apple's standard EULA |

## 3. Pricing and Availability

- Price: **Free** · Availability: all countries (or just yours).
- No in-app purchases or subscriptions.

## 4. App Privacy

**Privacy Policy URL:** `https://studyspot.education/privacy`

**Do you or your third-party partners collect data from this app?** Yes.

Then mark exactly these data types. For each one: **Used for: App
Functionality** only · **Linked to the user: Yes** · **Used for tracking: No**.

| Category | Data type | Why |
|---|---|---|
| Contact Info | **Name** | The name shown in the app (optional). |
| Identifiers | **User ID** | The username. |
| User Content | **Other User Content** | Assignments, classes, notes, subtasks, scores, study sessions. |

Leave everything else unchecked: no email, phone, location, contacts, health,
financial info, browsing/search history, purchases, analytics, diagnostics,
advertising data or device IDs. (Push tokens are only used to deliver your
own notifications and aren't a "Device ID" in Apple's sense.)

**Tracking:** No — the app doesn't track people across apps or websites.

## 5. Version 1.0 — App Store tab

### Screenshots

- **iPhone 6.9″ (1320 × 2868):** upload the six images in
  `marketing/ios-screenshots-6.9in/` in order (1 → 6). Apple scales them for
  smaller iPhones automatically.
- **iPad 13″ (2064 × 2752):** required because the app also runs on iPad. Take
  them in Xcode's **iPad Pro 13-inch** simulator: run the app, sign in, and
  press ⌘S on each screen (Dashboard, Calendar, Streaks, an assignment,
  Insights). Or, to skip iPad for now, make the app iPhone-only before
  archiving (Xcode → StudySpot target → General → Supported Destinations →
  remove iPad; also in the StudySpotWidgets target).

### App Preview (optional)

- `marketing/ios-app-previews/study-spot-app-preview.mp4` — 886 × 1920,
  24.5 s, 30 fps, H.264 with a silent stereo track (the 6.9″ preview size).
- Set the poster frame around 0:01.
- Note: Apple's guideline says previews should show the app's actual UI. These
  are built from screen designs of the app; if App Review objects, record the
  real app instead: `xcrun simctl io booted recordVideo preview.mov` while
  using an iPhone 16 Pro Max simulator, then trim to 15–30 s.

### Promotional Text (170)

> Bring Google Classroom, Google Calendar and Blackboard together. Get reminders before things are due, focus with a study timer, and keep your streaks going.

### Description (4000)

> Study Spot puts every assignment in one place, so you always know what's due and what to work on next.
>
> Connect Google Classroom, Google Calendar and Blackboard, and your coursework and due dates come in automatically. Add anything else yourself in seconds. Then plan your week, get reminded before things are due, and see your progress add up.
>
> EVERYTHING IN ONE PLACE
> • Assignments from Google Classroom, Google Calendar and Blackboard, plus your own
> • Unfinished, Completed and Graded lists, with filters and sorting
> • Overdue and due-today at a glance
> • Mark work done in Study Spot and turn it in on Google Classroom too
>
> PLAN YOUR WEEK
> • A month calendar of everything that's due
> • Drag an assignment to another day to reschedule it
> • Classes with colors, teachers, rooms and meeting times
>
> NEVER MISS A DUE DATE
> • Reminders hours or days before each due date, at the times you choose
> • Do Not Disturb hours for quiet evenings
> • Live Activity five minutes before class, with a countdown and what's due today
>
> FOCUS
> • Built-in study timer with focus sessions and short and long breaks
> • Keeps counting on the Lock Screen and in the Dynamic Island
> • Your study time is saved to your history
>
> STAY ORGANIZED
> • Subtasks, notes and progress for every assignment
> • Points and scores with letter grades, using your own grading scale
> • Templates for the assignments you create often
> • Classroom attachments right inside the assignment
>
> STAY MOTIVATED
> • Streaks for every class: finish everything on time to keep yours going
> • Insights that show how much you complete each week
> • An optional leaderboard
>
> WIDGETS
> • Up Next on your Home Screen and Lock Screen
> • Your best streak at a glance
>
> PRIVATE AND SECURE
> • Sign up with just a username — no email address needed
> • Two-factor authentication with an authenticator app or a passkey
> • No ads, no analytics, no tracking
> • Delete your account and all of its data at any time from Settings
>
> ON EVERY DEVICE
> Use the same account on iPhone, iPad, Android and the web at studyspot.education.
>
> Some schools only allow approved apps to connect to school Google or Blackboard accounts. If yours does, Study Spot can draft an email asking your school's IT team to approve it, and you can always add assignments yourself.

### Keywords (100)

```
homework,planner,assignment,school,student,classroom,blackboard,due date,study timer,grades,pomodoro
```

(Don't repeat words from the name or subtitle; Apple already indexes those.)

### URLs

| Field | Value |
|---|---|
| Support URL | `https://studyspot.education/support` |
| Marketing URL | `https://studyspot.education` |

### Other version fields

| Field | Value |
|---|---|
| Version | 1.0 |
| Copyright | `2026 <your name or company>` |
| Routing App Coverage File | leave empty |
| What's New | not shown for the first version |

## 6. Build

1. On your Mac:
   ```bash
   cd ~/study-spot && git pull origin claude/eager-heisenberg-m2pu7i
   ```
   ```bash
   cd ios && python3 scripts/configure.py --bundle-id com.yourname.studyspot --team YOURTEAMID --push
   ```
2. In Xcode: select **Any iOS Device (arm64)** → **Product → Archive** →
   **Distribute App → App Store Connect → Upload**.
3. Back in App Store Connect, pick the build under **Build** (it appears after
   processing, usually 5–30 minutes).
4. **Export compliance:** not asked — the app declares
   `ITSAppUsesNonExemptEncryption = NO` (it only uses Apple's built-in
   encryption). If asked anyway: *None of the algorithms mentioned above*.

## 7. App Review Information

**Sign-in required:** Yes. Create a review account first:

1. On studyspot.education, sign up as e.g. username `appreview` with a strong
   password. Leave two-factor authentication **off** for this account.
2. Add 2–3 classes and 6–8 assignments (some due today, one overdue, one
   completed with a score), so the reviewer sees a realistic dashboard,
   calendar and streaks.

| Field | Value |
|---|---|
| User name | `appreview` |
| Password | (the one you set) |
| Contact | your name, phone number and an email that reaches you |

**Notes:**

> Study Spot is a planner for students. Tap "Log in" on the first screen; sign-in happens on our website (studyspot.education) in a secure in-app browser, which then returns to the app. Please use the demo account provided above (two-factor authentication is off for it).
>
> Connecting Google Classroom, Google Calendar or Blackboard is optional and requires a school account at a school that allows third-party apps, so the demo account already contains sample classes and assignments to explore.
>
> Notifications: assignment reminders and the five-minute "class starts soon" Live Activity are scheduled from the reminder settings and class meeting times. The study timer (clock icon on the Dashboard) shows a Live Activity on the Lock Screen while it runs.
>
> Account deletion: Settings (person icon → Settings) → Danger Zone → Delete account. It permanently deletes the account and all of its data after confirming the password.
>
> The app does not collect email addresses by design; accounts use a username and password, with optional two-factor authentication.

**Attachment:** none needed.

## 8. Version Release

- **Manually release this version** (recommended for the first release), so
  you can check the live listing before it goes out.
- Phased release: not needed for 1.0.

---

## Checklist

- [ ] Server updated (`git pull … && docker compose up -d --build`)
- [ ] `/privacy` and `/support` load; support mailbox works
- [ ] Review account created with sample data, 2FA off
- [ ] Bundle ID and team set with `configure.py … --push`
- [ ] Apple Push key in Admin → Integration Secrets (for reminders / Live Activities)
- [ ] 6.9″ screenshots uploaded (and iPad 13″, or app made iPhone-only)
- [ ] Build archived, uploaded and selected
- [ ] App Privacy answered as above
- [ ] Submit for Review
