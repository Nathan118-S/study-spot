import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import cron from 'node-cron';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { attachUser } from './middleware/auth.js';
import authRoutes from './routes/auth.js';
import googleAuthRoutes from './routes/googleAuth.js';
import entityRoutes from './routes/entities.js';
import functionRoutes from './routes/functions.js';
import deviceRoutes from './routes/devices.js';
import publicRoutes from './routes/public.js';
import { syncAllIcalFeeds } from './functions/ical.js';
import { sendAssignmentReminders } from './jobs/reminders.js';
import { checkStreakRecords } from './jobs/streaks.js';
import { sendClassActivities } from './jobs/classActivities.js';

const app = express();
app.use(cors({ origin: process.env.FRONTEND_URL, credentials: true }));
// 12 MB: calendar files (.ics / Google Calendar export .zip) are uploaded as base64.
app.use(express.json({ limit: '12mb' }));
app.use(attachUser);

app.get('/api/health', (req, res) => res.json({ ok: true }));

// Apple App Site Association, so the native iOS app can create and use
// passkeys for this domain (associated domain "webcredentials:<host>").
// Set IOS_APP_ID to "<TEAM_ID>.<bundle id>", e.g. "ABCDE12345.com.studyspot.app".
// Serve it from the domain users sign in on (proxy /.well-known/ to the API).
app.get('/.well-known/apple-app-site-association', (req, res) => {
  const appId = process.env.IOS_APP_ID;
  if (!appId) return res.status(404).json({ error: 'Not configured' });
  res.json({ webcredentials: { apps: [appId] } });
});

// Digital Asset Links, so the native Android app (android/) can create and use
// passkeys for this domain through Credential Manager. Set
// ANDROID_APP_FINGERPRINTS to the app's signing-certificate SHA-256
// fingerprints, comma-separated ("AB:CD:…" as printed by `keytool -list -v`
// or `./gradlew signingReport`; include the debug and Play app-signing keys as
// needed). ANDROID_APP_ID is the application ID (default education.studyspot.app).
app.get('/.well-known/assetlinks.json', (req, res) => {
  const fingerprints = String(process.env.ANDROID_APP_FINGERPRINTS || '')
    .split(',')
    .map((f) => f.trim().toUpperCase())
    .filter(Boolean);
  if (!fingerprints.length) return res.status(404).json({ error: 'Not configured' });
  const packageName = (process.env.ANDROID_APP_ID || '').trim() || 'education.studyspot.app';
  res.json([
    {
      relation: ['delegate_permission/common.handle_all_urls', 'delegate_permission/common.get_login_creds'],
      target: { namespace: 'android_app', package_name: packageName, sha256_cert_fingerprints: fingerprints },
    },
  ]);
});

app.use('/api/auth', authRoutes);
app.use('/api', googleAuthRoutes);
app.use('/api/entities', entityRoutes);
app.use('/api/functions', functionRoutes);
app.use('/api/devices', deviceRoutes);
app.use('/api/public', publicRoutes);

// Serve the built website (dist/) from the same origin when it's present, as
// in the Docker image. Assets are fingerprinted, so they can be cached long;
// every other non-API path gets index.html for client-side routing.
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = process.env.STATIC_DIR || path.resolve(__dirname, '../../dist');
if (fs.existsSync(path.join(distDir, 'index.html'))) {
  app.use('/assets', express.static(path.join(distDir, 'assets'), { immutable: true, maxAge: '1y' }));
  // Root files (favicon.ico, manifest, app icons) keep fixed names, so make
  // browsers and Cloudflare re-check them instead of holding old copies.
  app.use(express.static(distDir, {
    index: false,
    setHeaders: (res) => res.set('Cache-Control', 'no-cache'),
  }));
  app.get(/^\/(?!api\/|\.well-known\/).*/, (req, res) => {
    res.set('Cache-Control', 'no-cache');
    res.sendFile(path.join(distDir, 'index.html'));
  });
  console.log(`Serving website from ${distDir}`);
}

app.use('/api', (req, res) => res.status(404).json({ error: 'Not found' }));

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
});

const port = process.env.PORT || 8787;
app.listen(port, () => {
  console.log(`Study Spot API listening on :${port}`);
});

// Recurring jobs: assignment due-date reminders and streak record alerts.
cron.schedule('*/15 * * * *', () => {
  sendAssignmentReminders().catch((err) => console.error('sendAssignmentReminders failed', err));
});
// iOS "class starts soon" Live Activities (no-op until APNs is configured).
cron.schedule('* * * * *', () => {
  sendClassActivities().catch((err) => console.error('sendClassActivities failed', err));
});
// Google Calendar links (iCal) pasted by people who can't connect Google.
cron.schedule('20 */2 * * *', () => {
  syncAllIcalFeeds().catch((err) => console.error('syncAllIcalFeeds failed', err));
});
cron.schedule('0 * * * *', () => {
  checkStreakRecords().catch((err) => console.error('checkStreakRecords failed', err));
});
