# Setting up Study Spot's secrets

Everything optional Study Spot connects to, in the order worth doing it.
Each section says where to get the value and where to put it.

There are two places values go:

- **Admin → Integration Secrets** on studyspot.education (sign in as the
  admin). Values are encrypted and can't be viewed again. To change one, click
  **Clear**, then paste the new value. No restart needed.
- **The server's `.env` file** (`~/study-spot/.env`), for a few settings
  that aren't secrets. After editing it, restart:
  ```bash
  cd ~/study-spot && docker compose up -d
  ```

---

## 0. Already done: the basics in `.env`

`POSTGRES_PASSWORD`, `SESSION_SECRET`, `ENCRYPTION_KEY` and `PUBLIC_URL`
were set when the server was installed. Check `PUBLIC_URL=https://studyspot.education`.

**Back up `.env` somewhere safe** (a password manager). Losing
`ENCRYPTION_KEY` makes every Integration Secret and every saved calendar link
unreadable.

---

## 1. Google Classroom and Google Calendar (≈20 min + Google review)

Lets students connect their Google account.

1. Go to https://console.cloud.google.com and sign in with the Google account
   that should own the app (a personal or business account, not a student one).
2. Top bar → project picker → **New project** → name it `Study Spot` → Create,
   and select it.
3. **APIs & Services → Library**: enable **Google Calendar API** and
   **Google Classroom API**.
4. **Google Auth Platform** (or **APIs & Services → OAuth consent screen**) →
   **Get started**:
   - App name: `Study Spot`; user support email: yours.
   - Audience: **External**.
   - Contact email: yours → Create.
5. **Branding**: upload the app icon
   (`ios/StudySpot/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`),
   set:
   - Application home page: `https://studyspot.education`
   - Privacy policy: `https://studyspot.education/privacy`
   - Terms of service: leave empty (optional)
   - Authorized domains: `studyspot.education`
6. **Data Access → Add or remove scopes**, add exactly these three:
   - `https://www.googleapis.com/auth/calendar.readonly`
   - `https://www.googleapis.com/auth/classroom.courses.readonly`
   - `https://www.googleapis.com/auth/classroom.coursework.me`
7. **Clients → Create client**:
   - Application type: **Web application**, name `Study Spot server`.
   - Authorized redirect URIs: `https://studyspot.education/api/connectors/google/callback`
   - Create → copy the **Client ID** and **Client secret**.
8. In Study Spot, **Admin → Integration Secrets → Google**:

   | Field | Value |
   |---|---|
   | Client ID | the client ID (`…apps.googleusercontent.com`) |
   | Client secret | the client secret (`GOCSPX-…`) |
   | Connector redirect URI | `https://studyspot.education/api/connectors/google/callback` |

9. **Publishing.** While the app is in *Testing*, only test users you add
   under **Audience → Test users** can connect (up to 100), and their access
   expires after 7 days. To open it to everyone, click **Publish app**. Because
   these scopes are "sensitive", Google then reviews the app:
   - Verify you own `studyspot.education` in
     [Google Search Console](https://search.google.com/search-console)
     (DNS TXT record in Cloudflare).
   - Google asks for a short YouTube video (can be unlisted) showing sign-in,
     the consent screen, and how each permission is used, plus a sentence per
     scope. Use the explanations on `https://studyspot.education/schools`.
   - Review usually takes a few days to a few weeks.

   Schools still have to approve the app for their students; send them to
   `https://studyspot.education/schools`, which now shows your client ID.

---

## 2. Apple Push: iPhone reminders and Live Activities (≈10 min)

Needs the paid Apple Developer account.

1. https://developer.apple.com/account → **Certificates, IDs & Profiles →
   Keys → +**.
2. Name `Study Spot Push`, tick **Apple Push Notifications service (APNs)** →
   Continue → Register → **Download** the `AuthKey_XXXXXXXXXX.p8` file (only
   possible once — keep it safe).
3. Note the **Key ID** (10 characters, on that page) and your **Team ID**
   (top right, or **Membership details**).
4. **Admin → Integration Secrets → Apple Push**:

   | Field | Value |
   |---|---|
   | Auth key (.p8 contents) | open the `.p8` in a text editor and paste everything, including the `BEGIN`/`END` lines |
   | Key ID | the 10-character key ID |
   | Team ID | your team ID |

5. Build the iPhone app with push turned on (once, on your Mac):
   ```bash
   cd ~/study-spot/ios && python3 scripts/configure.py --push --team YOURTEAMID
   ```
6. Test: open the app on your iPhone (signed in), then **Admin → People →
   you → Simulate on iPhone → Send**.

---

## 3. Firebase: Android notifications (≈10 min)

1. https://console.firebase.google.com → **Add project** → name `Study Spot`
   (Google Analytics: off) → Create. You can reuse the Google Cloud project
   from step 1.
2. Project overview → **Add app → Android**:
   - Package name: `education.studyspot.app` → Register app.
   - You don't need to download `google-services.json` or change Gradle;
     skip those steps.
3. **Project settings (⚙) → General**, note:
   - Project ID
   - Project number
   - Web API key
   - under *Your apps*, the Android **App ID** (`1:…:android:…`)
4. Put them in `~/study-spot/android/local.properties` on the computer you
   build the Android app on:
   ```properties
   studyspot.fcm.projectId=your-project-id
   studyspot.fcm.appId=1:1234567890:android:abcdef123456
   studyspot.fcm.apiKey=AIza...
   studyspot.fcm.senderId=1234567890
   ```
5. **Project settings → Service accounts → Generate new private key** →
   downloads a `.json` file.
6. **Admin → Integration Secrets → Firebase**: paste the whole JSON file's
   contents into **Service account JSON**.
7. Rebuild and run the Android app, sign in, allow notifications, then test
   with **Admin → People → you → Simulate on phone**.

---

## 4. Blackboard Learn (only if your school uses it)

Blackboard integrations are per school: each school's admin must approve the
app (see `https://studyspot.education/schools`).

1. Register at https://developer.blackboard.com → **My Applications → +**.
2. Name `Study Spot`, domain `studyspot.education`, and fill in the
   description. Create it, then copy the **Application ID**, **Key** and
   **Secret** (the secret is shown once).
3. **Admin → Integration Secrets → Blackboard Learn**:

   | Field | Value |
   |---|---|
   | Client ID | the **Key** |
   | Client secret | the **Secret** |
   | Redirect URI | `https://studyspot.education/blackboard/callback` |
   | Application ID | the **Application ID** (shown to schools on /schools) |

---

## 5. Passkeys in the apps (optional)

Authenticator-app two-factor works without this.

**iPhone** — in `.env`:
```
IOS_APP_ID=YOURTEAMID.com.yourname.studyspot
```
(your team ID, a dot, and the bundle ID you set with `configure.py`). In Xcode,
add the **Associated Domains** capability to the StudySpot target with
`webcredentials:studyspot.education`.

**Android** — on the build computer:
```bash
cd ~/study-spot/android && ./gradlew signingReport
```
Copy the `SHA-256` line(s) (debug, and later your release / Play signing key),
then in `.env`:
```
ANDROID_APP_FINGERPRINTS=AB:CD:…:EF
```
(comma-separate several.)

Restart the server after editing `.env`, then check these load:
- `https://studyspot.education/.well-known/apple-app-site-association`
- `https://studyspot.education/.well-known/assetlinks.json`

---

## Checklist

- [ ] `.env` backed up
- [ ] Google: APIs enabled, consent screen, client created, 3 values pasted, test user added (later: published and verified)
- [ ] Apple Push: key pasted, app built with `--push`, test notification received
- [ ] Firebase: `local.properties` filled, service account pasted, test notification received
- [ ] Blackboard (if needed): 4 values pasted
- [ ] Passkeys (optional): `IOS_APP_ID`, `ANDROID_APP_FINGERPRINTS`, server restarted
