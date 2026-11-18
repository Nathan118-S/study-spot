// Blackboard Learn 3-legged OAuth helpers.
import { resolveSecret } from './secrets.js';

async function basicHeader() {
  const [id, secret] = await Promise.all([
    resolveSecret('BLACKBOARD_CLIENT_ID'),
    resolveSecret('BLACKBOARD_CLIENT_SECRET'),
  ]);
  if (!id || !secret) throw new Error('Blackboard credentials not configured');
  return 'Basic ' + Buffer.from(`${id}:${secret}`).toString('base64');
}

// Validate a user-supplied Blackboard instance URL before any authenticated
// outbound request is made against it. Returns the normalized origin so a
// path or query string cannot be smuggled into credential-bearing requests.
export function validateInstanceUrl(instanceUrl) {
  let url;
  let raw = String(instanceUrl || '').trim();
  // People often type just "school.blackboard.com".
  if (raw && !/^[a-z][a-z0-9+.-]*:\/\//i.test(raw)) raw = `https://${raw}`;
  try {
    url = new URL(raw);
  } catch {
    throw new Error('Invalid Blackboard instance URL');
  }
  if (url.protocol === 'http:') url.protocol = 'https:';
  if (url.protocol !== 'https:') {
    throw new Error('Blackboard instance URL must use https');
  }
  const host = url.hostname.toLowerCase();
  if (!host) throw new Error('Blackboard instance URL missing host');
  if (url.username || url.password || url.port) {
    throw new Error('Blackboard instance URL must not include credentials or a port');
  }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(':')) {
    throw new Error('Blackboard instance URL must be a domain name, not an IP address');
  }
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal')) {
    throw new Error('Blackboard instance URL must be a public domain');
  }
  if (host === 'metadata.google.internal' || host === '169.254.169.254' || host.includes('metadata')) {
    throw new Error('Blackboard instance URL must not target a metadata service');
  }
  if (!host.includes('.')) {
    throw new Error('Blackboard instance URL must be a fully-qualified domain');
  }
  return url.origin;
}

export function buildAuthUrl(instanceUrl, clientId, redirectUri, state) {
  const params = new URLSearchParams({
    redirect_uri: redirectUri,
    response_type: 'code',
    client_id: clientId,
    scope: 'read offline',
    state,
  });
  return `${instanceUrl}/learn/api/public/v1/oauth2/authorizationcode?${params.toString()}`;
}

export async function exchangeCode(instanceUrl, code, redirectUri) {
  const base = validateInstanceUrl(instanceUrl);
  const url = `${base}/learn/api/public/v1/oauth2/token?code=${encodeURIComponent(code)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: await basicHeader(),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=authorization_code',
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    let code = '';
    try { code = JSON.parse(text).error || ''; } catch { /* not JSON */ }
    if (code === 'invalid_client' || res.status === 401) {
      throw new Error('Blackboard rejected Study Spot’s app key. An admin should check the Blackboard Client ID (Key) and Client secret in Admin → Integration Secrets, and that your school has approved Study Spot.');
    }
    if (code === 'invalid_grant' || /redirect/i.test(text)) {
      throw new Error('Blackboard couldn’t finish signing in (the sign-in link expired or the redirect URI doesn’t match). Try connecting again. Admins: the Redirect URI must be exactly https://studyspot.education/blackboard/callback in both Blackboard’s developer portal and Integration Secrets.');
    }
    throw new Error(`Blackboard sign-in failed (${res.status}). Try connecting again.`);
  }
  return await res.json();
}

export async function refreshAccessToken(instanceUrl, refreshToken, redirectUri) {
  const base = validateInstanceUrl(instanceUrl);
  const url = `${base}/learn/api/public/v1/oauth2/token?refresh_token=${encodeURIComponent(refreshToken)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: await basicHeader(),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken }).toString(),
  });
  if (!res.ok) throw new Error('Blackboard token refresh failed: ' + (await res.text()));
  return await res.json();
}
