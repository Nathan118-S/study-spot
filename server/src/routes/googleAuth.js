import { Router } from 'express';
import crypto from 'node:crypto';
import { pool } from '../db.js';
import { getUserByEmail, signSession } from '../lib/users.js';
import { requireAuth } from '../middleware/auth.js';

const router = Router();

const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';

// "Sign in with Google" — issues an app session, no Calendar/Classroom scopes.
router.get('/auth/google/start', async (req, res) => {
  const state = crypto.randomUUID();
  const returnTo = typeof req.query.returnTo === 'string' ? req.query.returnTo : '/';
  await pool.query(
    'INSERT INTO oauth_states (state, purpose, return_to) VALUES ($1, $2, $3)',
    [state, 'login', returnTo]
  );
  const params = new URLSearchParams({
    client_id: process.env.GOOGLE_CLIENT_ID,
    redirect_uri: process.env.GOOGLE_LOGIN_REDIRECT_URI,
    response_type: 'code',
    scope: 'openid email profile',
    state,
    prompt: 'select_account',
  });
  res.redirect(`${GOOGLE_AUTH_URL}?${params.toString()}`);
});

router.get('/auth/google/callback', async (req, res) => {
  try {
    const { code, state } = req.query;
    const stateRes = await pool.query('SELECT * FROM oauth_states WHERE state = $1 AND purpose = $2', [state, 'login']);
    const stateRow = stateRes.rows[0];
    if (!stateRow) return res.status(400).send('Invalid or expired login link.');
    await pool.query('DELETE FROM oauth_states WHERE state = $1', [state]);

    const tokenRes = await fetch(GOOGLE_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        code,
        client_id: process.env.GOOGLE_CLIENT_ID,
        client_secret: process.env.GOOGLE_CLIENT_SECRET,
        redirect_uri: process.env.GOOGLE_LOGIN_REDIRECT_URI,
        grant_type: 'authorization_code',
      }),
    });
    if (!tokenRes.ok) return res.status(502).send('Google sign-in failed.');
    const tokens = await tokenRes.json();

    const profileRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
      headers: { Authorization: `Bearer ${tokens.access_token}` },
    });
    if (!profileRes.ok) return res.status(502).send('Google sign-in failed.');
    const profile = await profileRes.json();
    if (!profile.email) return res.status(400).send('Google account has no email.');

    let user = await getUserByEmail(profile.email);
    if (!user) {
      const insertRes = await pool.query(
        'INSERT INTO users (email, name, full_name, role, is_verified) VALUES ($1, $2, $3, $4, true) RETURNING *',
        [profile.email.toLowerCase(), profile.given_name || profile.name || null, profile.name || null, 'user']
      );
      user = insertRes.rows[0];
    } else if (user.role === 'disabled') {
      return res.status(403).send('This account has been disabled.');
    } else if (!user.is_verified) {
      await pool.query('UPDATE users SET is_verified = true WHERE id = $1', [user.id]);
    }

    const token = signSession(user.id);
    const returnTo = encodeURIComponent(stateRow.return_to || '/');
    res.redirect(`${process.env.FRONTEND_URL}/oauth/callback#token=${token}&returnTo=${returnTo}`);
  } catch (error) {
    res.status(500).send('Google sign-in failed: ' + error.message);
  }
});

// Google Calendar / Classroom connectors — separate OAuth grant with
// read-only scopes, stored per user so sync routes can use them.
const CONNECTOR_SCOPES = {
  google_calendar: 'https://www.googleapis.com/auth/calendar.readonly',
  google_classroom:
    'https://www.googleapis.com/auth/classroom.courses.readonly https://www.googleapis.com/auth/classroom.coursework.me https://www.googleapis.com/auth/classroom.coursework.students',
};

router.get('/connectors/google/start', requireAuth, async (req, res) => {
  const connector = req.query.connector;
  if (!CONNECTOR_SCOPES[connector]) return res.status(400).json({ error: 'Unknown connector' });
  const state = crypto.randomUUID();
  await pool.query(
    'INSERT INTO oauth_states (state, purpose, user_id, return_to) VALUES ($1, $2, $3, $4)',
    [state, connector, req.user.id, null]
  );
  const params = new URLSearchParams({
    client_id: process.env.GOOGLE_CLIENT_ID,
    redirect_uri: process.env.GOOGLE_CONNECT_REDIRECT_URI,
    response_type: 'code',
    access_type: 'offline',
    prompt: 'consent',
    scope: CONNECTOR_SCOPES[connector],
    state,
  });
  res.json({ url: `${GOOGLE_AUTH_URL}?${params.toString()}` });
});

router.get('/connectors/google/callback', async (req, res) => {
  try {
    const { code, state } = req.query;
    const stateRes = await pool.query('SELECT * FROM oauth_states WHERE state = $1', [state]);
    const stateRow = stateRes.rows[0];
    if (!stateRow || !CONNECTOR_SCOPES[stateRow.purpose]) {
      return res.status(400).send('Invalid or expired connection link.');
    }
    await pool.query('DELETE FROM oauth_states WHERE state = $1', [state]);

    const tokenRes = await fetch(GOOGLE_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        code,
        client_id: process.env.GOOGLE_CLIENT_ID,
        client_secret: process.env.GOOGLE_CLIENT_SECRET,
        redirect_uri: process.env.GOOGLE_CONNECT_REDIRECT_URI,
        grant_type: 'authorization_code',
      }),
    });
    if (!tokenRes.ok) return res.status(502).send('Connecting to Google failed.');
    const tokens = await tokenRes.json();
    const expiresAt = new Date(Date.now() + (tokens.expires_in || 3600) * 1000);

    await pool.query(
      `INSERT INTO google_connections (user_id, connector, access_token, refresh_token, expires_at, scope)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (user_id, connector) DO UPDATE SET
         access_token = EXCLUDED.access_token,
         refresh_token = COALESCE(EXCLUDED.refresh_token, google_connections.refresh_token),
         expires_at = EXCLUDED.expires_at,
         scope = EXCLUDED.scope,
         updated_at = now()`,
      [stateRow.user_id, stateRow.purpose, tokens.access_token, tokens.refresh_token || null, expiresAt, tokens.scope || null]
    );

    res.send(
      '<html><body><script>window.opener && window.opener.postMessage("google-connected", "*"); window.close();</script>Connected. You can close this window.</body></html>'
    );
  } catch (error) {
    res.status(500).send('Connecting to Google failed: ' + error.message);
  }
});

router.delete('/connectors/google/:connector', requireAuth, async (req, res) => {
  const connector = req.params.connector;
  if (!CONNECTOR_SCOPES[connector]) return res.status(400).json({ error: 'Unknown connector' });
  await pool.query('DELETE FROM google_connections WHERE user_id = $1 AND connector = $2', [req.user.id, connector]);
  res.json({ ok: true });
});

// Returns a valid (refreshed if needed) access token for a user's connector.
export async function getValidGoogleToken(userId, connector) {
  const res = await pool.query(
    'SELECT * FROM google_connections WHERE user_id = $1 AND connector = $2',
    [userId, connector]
  );
  const row = res.rows[0];
  if (!row) return null;

  const expiresAt = row.expires_at ? new Date(row.expires_at).getTime() : 0;
  if (expiresAt - Date.now() > 60000 || !row.refresh_token) return row.access_token;

  const tokenRes = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      refresh_token: row.refresh_token,
      client_id: process.env.GOOGLE_CLIENT_ID,
      client_secret: process.env.GOOGLE_CLIENT_SECRET,
      grant_type: 'refresh_token',
    }),
  });
  if (!tokenRes.ok) return row.access_token;
  const tokens = await tokenRes.json();
  const newExpiresAt = new Date(Date.now() + (tokens.expires_in || 3600) * 1000);
  await pool.query(
    'UPDATE google_connections SET access_token = $1, expires_at = $2, updated_at = now() WHERE user_id = $3 AND connector = $4',
    [tokens.access_token, newExpiresAt, userId, connector]
  );
  return tokens.access_token;
}

export default router;
