import jwt from 'jsonwebtoken';
import { pool } from '../db.js';

// Columns that live directly on the users table. Any other key passed to
// updateUser() is merged into the free-form `data` JSONB column instead —
// this mirrors the app's `user.field ?? user.data?.field` read pattern used
// throughout the frontend for custom/extended settings.
const KNOWN_COLUMNS = new Set([
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

export function signSession(userId) {
  return jwt.sign({ sub: userId }, process.env.SESSION_SECRET, { expiresIn: '30d' });
}

export function verifySession(token) {
  try {
    const payload = jwt.verify(token, process.env.SESSION_SECRET);
    return payload.sub;
  } catch {
    return null;
  }
}

export async function getUserById(id) {
  const res = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
  return res.rows[0] || null;
}

export async function getUserByEmail(email) {
  const res = await pool.query('SELECT * FROM users WHERE lower(email) = lower($1)', [email]);
  return res.rows[0] || null;
}

// Shapes a DB row into the object the frontend expects: known fields at the
// top level, everything else under `data`.
export function toPublicUser(row) {
  if (!row) return null;
  const { password_hash, ...rest } = row;
  return {
    ...rest,
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
