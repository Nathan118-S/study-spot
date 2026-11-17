// Notifications on a user's phones: iPhones through APNs (the app registers
// at POST /api/devices/alerts) and Android phones through Firebase Cloud
// Messaging (POST /api/devices/android). Each needs its key in Admin ->
// Integration Secrets; without one, that platform is skipped.
import { pool } from '../db.js';
import { apnsConfigured, isDeadToken, sendAlertPush } from './apns.js';
import { fcmConfigured, isDeadFcmToken, sendFcm } from './fcm.js';

// Android notification channels (created by the app).
export const ANDROID_CHANNELS = {
  'assignment-reminders': 'reminders',
  streaks: 'streaks',
};

/**
 * Sends a notification to every phone the user is signed in on.
 * @returns {Promise<number>} how many devices accepted it
 */
export async function sendUserPush(userId, message) {
  return (await sendUserPushDetailed(userId, message)).filter((r) => r.ok).length;
}

/** Like sendUserPush, but returns each device's result. */
export async function sendUserPushDetailed(userId, { title, subtitle, body, data = {}, collapseId, threadId, channel }) {
  const [apns, fcm] = await Promise.all([apnsConfigured(), fcmConfigured()]);
  if (!apns && !fcm) return [];
  const res = await pool.query(
    "SELECT * FROM device_push_tokens WHERE user_id = $1 AND kind = ANY($2)",
    [userId, [...(apns ? ['alert'] : []), ...(fcm ? ['fcm'] : [])]]
  );
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
    const android = t.kind === 'fcm';
    const result = android
      // Data-only: the app builds the notification (channel, tap target).
      ? await sendFcm({
        token: t.token,
        data: { type: 'notification', title, subtitle, body, channel: channel || ANDROID_CHANNELS[threadId] || 'general', ...data },
        collapseKey: collapseId,
      })
      : await sendAlertPush({ token: t.token, environment: t.environment, bundleId: t.bundle_id, payload, collapseId });
    results.push({ device: `…${t.token.slice(-8)}`, platform: android ? 'android' : 'ios', environment: t.environment, ...result });
    if (result.ok) continue;
    if (android ? isDeadFcmToken(result) : isDeadToken(result)) await pool.query('DELETE FROM device_push_tokens WHERE token = $1', [t.token]);
    else console.warn(`Push failed (${result.status} ${result.reason || ''})`);
  }
  return results;
}

/**
 * Sends a data-only FCM message to the user's Android phones (the app turns
 * it into a live countdown notification, etc.).
 */
export async function sendAndroidData(userId, data, { collapseKey, ttlSeconds } = {}) {
  if (!(await fcmConfigured())) return [];
  const res = await pool.query("SELECT * FROM device_push_tokens WHERE user_id = $1 AND kind = 'fcm'", [userId]);
  const results = [];
  for (const t of res.rows) {
    const result = await sendFcm({ token: t.token, data, collapseKey, ttlSeconds });
    results.push({ device: `…${t.token.slice(-8)}`, platform: 'android', environment: t.environment, ...result });
    if (!result.ok && isDeadFcmToken(result)) await pool.query('DELETE FROM device_push_tokens WHERE token = $1', [t.token]);
  }
  return results;
}
