import jwt from 'jsonwebtoken';
import { pool } from '../db.js';

// Columns that live directly on the users table. Any other key passed to
// updateUser() is merged into the free-form `data` JSONB column instead —
// this mirrors the app's `user.field ?? user.data?.field` read pattern used
// throughout the frontend for custom/extended settings.
const KNOWN_COLUMNS = new Set([
  'username',
  'password_hash',
  'name',
  'full_name',
  'role',
  'disabled_reason',
  'is_verified',
  'totp_secret',
  'twofa_enabled',
  'twofa_method',
  'twofa_methods',
  'webauthn_challenge',
  'passkey_cred_id',
  'passkey_pub_key',
  'passkey_alg',
  'passkey_counter',
]);

// A session issued at password/Google sign-in for a user with 2FA enabled is
// "pending" until a second factor is verified; it can only reach the 2FA
// endpoints (see middleware/auth.js). Verification issues a full session.
export function signSession(userId, { mfaPending = false } = {}) {
  const claims = mfaPending ? { sub: userId, mfa: 'pending' } : { sub: userId };
  return jwt.sign(claims, process.env.SESSION_SECRET, { expiresIn: '30d' });
}

// Returns { userId, mfaPending, issuedAt } or null.
export function verifySession(token) {
  try {
    const payload = jwt.verify(token, process.env.SESSION_SECRET);
    return { userId: payload.sub, mfaPending: payload.mfa === 'pending', issuedAt: payload.iat };
  } catch {
    return null;
  }
}

export function needsSecondFactor(user) {
  if (!user?.twofa_enabled) return false;
  const methods = Array.isArray(user.twofa_methods) && user.twofa_methods.length
    ? user.twofa_methods
    : user.twofa_method ? [user.twofa_method] : ['totp'];
  return methods.length > 0;
}

// Server-only fields that must never be sent to the client.
const PRIVATE_COLUMNS = ['password_hash', 'totp_secret', 'webauthn_challenge', 'passkey_pub_key'];
const PRIVATE_DATA_KEYS = [
  'blackboard_access_token', 'blackboard_refresh_token', 'blackboard_state',
  'blackboard_expires_at', 'blackboard_user_id',
];

// The only fields a user may change about themselves through PATCH /auth/me.
// Everything else (role, verification, 2FA state, integration tokens) is
// changed only by the server's own flows.
const SELF_EDITABLE_COLUMNS = new Set(['name', 'full_name']);
const SELF_EDITABLE_DATA_KEYS = new Set([
  'onboarding_completed', 'welcome_style', 'leaderboard_enabled',
  'reminders_enabled', 'reminder_lead_hours', 'dnd_enabled', 'dnd_start', 'dnd_end',
  'sync_completion_to_classroom', 'grading_scale', 'last_sync', 'sessions_invalidated_at',
  'timezone', 'class_live_activities', 'push_reminders',
]);

export function pickSelfEditable(patch) {
  const out = {};
  for (const [key, value] of Object.entries(patch || {})) {
    if (SELF_EDITABLE_COLUMNS.has(key)) {
      out[key] = typeof value === 'string' ? value.trim().slice(0, 80) : null;
    } else if (SELF_EDITABLE_DATA_KEYS.has(key)) {
      out[key] = value;
    }
  }
  return out;
}

export async function getUserById(id) {
  const res = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
  return res.rows[0] || null;
}

export async function getUserByUsername(username) {
  const res = await pool.query('SELECT * FROM users WHERE lower(username) = lower($1)', [String(username || '').trim()]);
  return res.rows[0] || null;
}

// New usernames: 3–30 characters, letters, numbers, dots, dashes and
// underscores, starting with a letter or number. (Accounts moved over from
// email sign-in keep their email address as the username until they change it.)
const USERNAME_RE = /^[a-z0-9][a-z0-9._-]{2,29}$/;

/** Returns the cleaned username, or throws a message suitable for the user. */
export function normalizeUsername(raw) {
  const u = String(raw || '').trim().toLowerCase();
  if (!u) throw new Error('Choose a username');
  if (u.length < 3) throw new Error('Usernames must be at least 3 characters');
  if (u.length > 30) throw new Error('Usernames can be at most 30 characters');
  if (!USERNAME_RE.test(u)) throw new Error('Use only letters, numbers, dots, dashes and underscores');
  return u;
}

// Shapes a DB row into the object the frontend expects: known fields at the
// top level, everything else under `data`.
export function toPublicUser(row) {
  if (!row) return null;
  const rest = { ...row };
  for (const col of PRIVATE_COLUMNS) delete rest[col];
  const data = { ...(row.data || {}) };
  for (const key of PRIVATE_DATA_KEYS) delete data[key];
  return {
    ...rest,
    data,
    has_passkey: !!row.passkey_cred_id,
    created_date: row.created_at,
    updated_date: row.updated_at,
  };
}

export async function updateUser(userId, patch) {
  const columnUpdates = {};
  const dataPatch = {};
  for (const [key, value] of Object.entries(patch || {})) {
    if (KNOWN_COLUMNS.has(key)) columnUpdates[key] = value;
    else dataPatch[key] = value;
  }

  const sets = [];
  const values = [];
  let i = 1;
  for (const [col, val] of Object.entries(columnUpdates)) {
    sets.push(`${col} = $${i++}`);
    values.push(val);
  }
  if (Object.keys(dataPatch).length) {
    sets.push(`data = data || $${i++}::jsonb`);
    values.push(JSON.stringify(dataPatch));
  }
  sets.push(`updated_at = now()`);
  values.push(userId);

  const res = await pool.query(
    `UPDATE users SET ${sets.join(', ')} WHERE id = $${i} RETURNING *`,
    values
  );
  return res.rows[0] || null;
}
