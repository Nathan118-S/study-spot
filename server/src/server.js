import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import cron from 'node-cron';

import { attachUser } from './middleware/auth.js';
import authRoutes from './routes/auth.js';
import googleAuthRoutes from './routes/googleAuth.js';
import entityRoutes from './routes/entities.js';
import functionRoutes from './routes/functions.js';
import { sendAssignmentReminders } from './jobs/reminders.js';
import { checkStreakRecords } from './jobs/streaks.js';

const app = express();
app.use(cors({ origin: process.env.FRONTEND_URL, credentials: true }));
app.use(express.json({ limit: '2mb' }));
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

app.use('/api/auth', authRoutes);
app.use('/api', googleAuthRoutes);
app.use('/api/entities', entityRoutes);
app.use('/api/functions', functionRoutes);

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
cron.schedule('0 * * * *', () => {
  checkStreakRecords().catch((err) => console.error('checkStreakRecords failed', err));
});
