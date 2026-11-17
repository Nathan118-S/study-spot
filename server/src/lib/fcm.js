// Firebase Cloud Messaging (HTTP v1) for the Android app. No dependencies:
// the service account's private key signs a JWT that is exchanged for an
// OAuth access token (cached ~55 minutes).
//
// Configure in Admin -> Integration Secrets (or server env):
//   FCM_SERVICE_ACCOUNT  the Firebase service account JSON (Project settings ->
//                        Service accounts -> Generate new private key)
import crypto from 'node:crypto';
import { resolveSecret } from './secrets.js';

let tokenCache = null; // { accessToken, expiresAt, clientEmail }

async function serviceAccount() {
  const raw = await resolveSecret('FCM_SERVICE_ACCOUNT');
  if (!raw) return null;
  try {
    const sa = JSON.parse(raw);
    if (!sa.client_email || !sa.private_key || !sa.project_id) return null;
    // The admin field may have turned "\n" into real or escaped newlines.
    sa.private_key = String(sa.private_key).replace(/\\n/g, '\n');
    return sa;
  } catch {
    return null;
  }
}

export async function fcmConfigured() {
  return !!(await serviceAccount());
}

async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  if (tokenCache && tokenCache.clientEmail === sa.client_email && tokenCache.expiresAt - 300 > now) return tokenCache.accessToken;
  const b64u = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const unsigned = `${b64u({ alg: 'RS256', typ: 'JWT' })}.${b64u({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  })}`;
  const sig = crypto.sign('RSA-SHA256', Buffer.from(unsigned), sa.private_key).toString('base64url');
  const res = await fetch(process.env.FCM_TOKEN_URL || 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${sig}` }),
  });
  if (!res.ok) throw new Error(`FCM auth failed (${res.status})`);
  const j = await res.json();
  tokenCache = { accessToken: j.access_token, expiresAt: now + (j.expires_in || 3600), clientEmail: sa.client_email };
  return j.access_token;
}

// FCM data values must all be strings.
const stringify = (data) => Object.fromEntries(
  Object.entries(data || {}).filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => [k, typeof v === 'string' ? v : JSON.stringify(v)])
);

/**
 * Sends one FCM message to an Android device token.
 * - notification: { title, body } shown by the system (optional)
 * - data: key/values delivered to the app (strings after conversion)
 * - channel: Android notification channel id
 * @returns {Promise<{ ok: boolean, status: number, reason?: string }>}
 */
export async function sendFcm({ token, notification, data, channel, collapseKey, ttlSeconds }) {
  const sa = await serviceAccount();
  if (!sa) return { ok: false, status: 0, reason: 'FCM not configured' };
  try {
    const bearer = await accessToken(sa);
    const message = {
      token,
      data: stringify(data),
      android: {
        priority: 'HIGH',
        ...(collapseKey ? { collapse_key: String(collapseKey).slice(0, 64) } : {}),
        ...(ttlSeconds ? { ttl: `${ttlSeconds}s` } : {}),
        ...(notification ? { notification: { channel_id: channel || 'general', ...(collapseKey ? { tag: String(collapseKey).slice(0, 64) } : {}) } } : {}),
      },
      ...(notification ? { notification: { title: notification.title, body: notification.body || '' } } : {}),
    };
    const base = process.env.FCM_HOST || 'https://fcm.googleapis.com';
    const res = await fetch(`${base}/v1/projects/${sa.project_id}/messages:send`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
    });
    if (res.ok) return { ok: true, status: res.status };
    let reason;
    try {
      const j = await res.json();
      reason = j.error?.details?.find((d) => d.errorCode)?.errorCode || j.error?.status || j.error?.message;
    } catch { /* no body */ }
    return { ok: false, status: res.status, reason };
  } catch (err) {
    return { ok: false, status: 0, reason: err.message };
  }
}

/** Whether FCM says this token will never work again (delete it). */
export function isDeadFcmToken(result) {
  return result.status === 404 || result.reason === 'UNREGISTERED';
}
