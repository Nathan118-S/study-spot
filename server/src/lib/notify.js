// Notifications on a user's iPhones (the app registers its device token at
// POST /api/devices/alerts). Needs the APNs key in Admin -> Integration Secrets.
import { pool } from '../db.js';
import { apnsConfigured, isDeadToken, sendAlertPush } from './apns.js';

/**
 * Sends a notification to every iPhone the user is signed in on.
 * @returns {Promise<number>} how many devices accepted it
 */
export async function sendUserPush(userId, message) {
  return (await sendUserPushDetailed(userId, message)).filter((r) => r.ok).length;
}

/** Like sendUserPush, but returns each device's result. */
export async function sendUserPushDetailed(userId, { title, subtitle, body, data = {}, collapseId, threadId }) {
  if (!(await apnsConfigured())) return [];
  const res = await pool.query("SELECT * FROM device_push_tokens WHERE user_id = $1 AND kind = 'alert'", [userId]);
  const payload = {
    aps: {
      alert: { title, ...(subtitle ? { subtitle } : {}), body },
      sound: 'default',
      ...(threadId ? { 'thread-id': threadId } : {}),
    },
    ...data,
  };
  const results = [];
  for (const t of res.rows) {
    const result = await sendAlertPush({ token: t.token, environment: t.environment, bundleId: t.bundle_id, payload, collapseId });
    results.push({ device: `…${t.token.slice(-8)}`, environment: t.environment, ...result });
    if (result.ok) continue;
    if (isDeadToken(result)) await pool.query('DELETE FROM device_push_tokens WHERE token = $1', [t.token]);
    else console.warn(`Push failed (${result.status} ${result.reason || ''})`);
  }
  return results;
}
