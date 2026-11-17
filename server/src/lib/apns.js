// Minimal Apple Push Notification service client: ActivityKit pushes (Live
// Activity push-to-start / update / end) and regular alert notifications. Token-based auth with a .p8
// key; no dependencies (node:http2 + node:crypto).
//
// Configure in Admin -> Integration Secrets (or server env):
//   APNS_KEY     contents of the AuthKey_XXXXXXXXXX.p8 file
//   APNS_KEY_ID  the key's 10-character ID
//   APNS_TEAM_ID your Apple Developer Team ID
import crypto from 'node:crypto';
import http2 from 'node:http2';
import { resolveSecret } from './secrets.js';

const HOSTS = {
  production: 'https://api.push.apple.com',
  development: 'https://api.sandbox.push.apple.com',
};

// The admin panel's field is single-line, so accept the key with or without
// its line breaks and rebuild a proper PEM.
function normalizePem(raw) {
  const body = String(raw)
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  const lines = body.match(/.{1,64}/g) || [];
  return `-----BEGIN PRIVATE KEY-----\n${lines.join('\n')}\n-----END PRIVATE KEY-----\n`;
}

let jwtCache = null; // { token, issuedAt, keyId }

async function config() {
  const [key, keyId, teamId] = await Promise.all([
    resolveSecret('APNS_KEY'),
    resolveSecret('APNS_KEY_ID'),
    resolveSecret('APNS_TEAM_ID'),
  ]);
  if (!key || !keyId || !teamId) return null;
  return { key: normalizePem(key), keyId: keyId.trim(), teamId: teamId.trim() };
}

export async function apnsConfigured() {
  return !!(await config());
}

async function providerToken(cfg) {
  // Apple accepts a token for up to an hour; refresh every 45 minutes.
  const now = Math.floor(Date.now() / 1000);
  if (jwtCache && jwtCache.keyId === cfg.keyId && now - jwtCache.issuedAt < 45 * 60) return jwtCache.token;
  const b64u = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const unsigned = `${b64u({ alg: 'ES256', kid: cfg.keyId })}.${b64u({ iss: cfg.teamId, iat: now })}`;
  const signature = crypto.sign('sha256', Buffer.from(unsigned), { key: cfg.key, dsaEncoding: 'ieee-p1363' });
  jwtCache = { token: `${unsigned}.${signature.toString('base64url')}`, issuedAt: now, keyId: cfg.keyId };
  return jwtCache.token;
}

/**
 * Sends one ActivityKit push.
 * @returns {Promise<{ ok: boolean, status: number, reason?: string }>}
 */
export function sendLiveActivityPush({ token, environment = 'production', bundleId, payload }) {
  return sendPush({ token, environment, topic: `${bundleId}.push-type.liveactivity`, pushType: 'liveactivity', payload });
}

/**
 * Sends a regular notification (banner, sound) to an app's device token.
 * @returns {Promise<{ ok: boolean, status: number, reason?: string }>}
 */
export function sendAlertPush({ token, environment = 'production', bundleId, payload, collapseId }) {
  return sendPush({ token, environment, topic: bundleId, pushType: 'alert', payload, collapseId });
}

/** Whether APNs says this token will never work again (delete it). */
export function isDeadToken(result) {
  return result.status === 410 || result.reason === 'BadDeviceToken' || result.reason === 'Unregistered'
    || result.reason === 'DeviceTokenNotForTopic';
}

async function sendPush({ token, environment, topic, pushType, payload, collapseId }) {
  const cfg = await config();
  if (!cfg) return { ok: false, status: 0, reason: 'APNs not configured' };
  // APNS_HOST overrides the Apple endpoint (testing against a local server).
  const host = process.env.APNS_HOST || HOSTS[environment] || HOSTS.production;
  const jwt = await providerToken(cfg);
  const body = JSON.stringify(payload);

  return new Promise((resolve) => {
    const client = http2.connect(host);
    client.on('error', (err) => resolve({ ok: false, status: 0, reason: err.message }));
    const req = client.request({
      ':method': 'POST',
      ':path': `/3/device/${token}`,
      authorization: `bearer ${jwt}`,
      'apns-topic': topic,
      'apns-push-type': pushType,
      'apns-priority': '10',
      'content-type': 'application/json',
      ...(collapseId ? { 'apns-collapse-id': String(collapseId).slice(0, 64) } : {}),
    });
    let status = 0;
    let data = '';
    req.setTimeout(15000, () => req.close(http2.constants.NGHTTP2_CANCEL));
    req.on('response', (headers) => { status = headers[':status']; });
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => {
      client.close();
      let reason;
      try { reason = data ? JSON.parse(data).reason : undefined; } catch { reason = data; }
      resolve({ ok: status === 200, status, reason });
    });
    req.on('error', (err) => { client.close(); resolve({ ok: false, status: 0, reason: err.message }); });
    req.end(body);
  });
}
