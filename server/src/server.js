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

// Replaces the Base44 "Assignment Reminders" and "Streak Record Alerts"
// scheduled workflows.
cron.schedule('*/15 * * * *', () => {
  sendAssignmentReminders().catch((err) => console.error('sendAssignmentReminders failed', err));
});
cron.schedule('0 * * * *', () => {
  checkStreakRecords().catch((err) => console.error('checkStreakRecords failed', err));
});
