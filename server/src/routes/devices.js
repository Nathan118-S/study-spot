// iOS device registration: notification tokens for assignment reminders, and
// ActivityKit push tokens for class Live Activities.
import { Router } from 'express';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { apnsConfigured } from '../lib/apns.js';

const router = Router();
router.use(requireAuth);

const TOKEN_RE = /^[0-9a-fA-F]{32,256}$/;
const BUNDLE_RE = /^[A-Za-z0-9.\-]{3,155}$/;
const ENVIRONMENTS = new Set(['production', 'development']);

function validDevice(body) {
  const { token, environment = 'production', bundleId } = body || {};
  if (!TOKEN_RE.test(token || '')) return 'Invalid token';
  if (!ENVIRONMENTS.has(environment)) return 'Invalid environment';
  if (!BUNDLE_RE.test(bundleId || '')) return 'Invalid bundle ID';
  return null;
}

// Whether the server can start Live Activities itself (APNs configured).
router.get('/config', async (req, res) => {
  const push = await apnsConfigured();
  res.json({ classActivitiesPush: push, alertPush: push });
});

// The app's notification device token (assignment reminders).
router.post('/alerts', async (req, res) => {
  const error = validDevice(req.body);
  if (error) return res.status(400).json({ error });
  const { token, environment = 'production', bundleId } = req.body;
  await pool.query(
    `INSERT INTO device_push_tokens (token, user_id, kind, environment, bundle_id)
     VALUES ($1, $2, 'alert', $3, $4)
     ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, kind = 'alert',
       environment = EXCLUDED.environment, bundle_id = EXCLUDED.bundle_id, updated_at = now()`,
    [token, req.user.id, environment, bundleId]
  );
  res.json({ ok: true });
});

// Sign-out: stop sending this device reminders.
router.delete('/alerts', async (req, res) => {
  const token = req.body?.token;
  if (token) await pool.query('DELETE FROM device_push_tokens WHERE token = $1 AND user_id = $2', [token, req.user.id]);
  res.json({ ok: true });
});

// The app's push-to-start token (Activity<ClassStartAttributes>.pushToStartToken).
router.post('/push-to-start', async (req, res) => {
  const error = validDevice(req.body);
  if (error) return res.status(400).json({ error });
  const { token, environment = 'production', bundleId } = req.body;
  // activity: 'class' (ClassStartAttributes, default) or 'timer' (StudyTimerAttributes).
  const kind = req.body.activity === 'timer' ? 'timer-activity-start' : 'live-activity-start';
  await pool.query(
    `INSERT INTO device_push_tokens (token, user_id, kind, environment, bundle_id)
     VALUES ($1, $2, $5, $3, $4)
     ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, kind = EXCLUDED.kind, environment = EXCLUDED.environment,
       bundle_id = EXCLUDED.bundle_id, updated_at = now()`,
    [token, req.user.id, environment, bundleId, kind]
  );
  res.json({ ok: true });
});

// Sign-out: stop sending this device class activities.
router.delete('/push-to-start', async (req, res) => {
  const token = req.body?.token;
  if (token) await pool.query('DELETE FROM device_push_tokens WHERE token = $1 AND user_id = $2', [token, req.user.id]);
  res.json({ ok: true });
});

// A class activity's own push token, so the server can end it after class
// starts (also covers activities the app started locally).
router.post('/class-activity', async (req, res) => {
  const error = validDevice(req.body);
  if (error) return res.status(400).json({ error });
  const { token, environment = 'production', bundleId, classId, startsAt } = req.body;
  const starts = Number(startsAt);
  if (!classId || !Number.isFinite(starts)) return res.status(400).json({ error: 'classId and startsAt are required' });
  const owns = await pool.query('SELECT 1 FROM classes WHERE id = $1 AND created_by_id = $2', [classId, req.user.id]);
  if (!owns.rows.length) return res.status(404).json({ error: 'Class not found' });
  await pool.query(
    `INSERT INTO class_activity_sends (user_id, class_id, starts_at, update_token, environment, bundle_id)
     VALUES ($1, $2, to_timestamp($3), $4, $5, $6)
     ON CONFLICT (user_id, class_id, starts_at) DO UPDATE SET update_token = EXCLUDED.update_token,
       environment = EXCLUDED.environment, bundle_id = EXCLUDED.bundle_id`,
    [req.user.id, classId, starts, token, environment, bundleId]
  );
  res.json({ ok: true });
});

export default router;
