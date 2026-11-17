// Full account management for admins. Every function here except the two
// adminElevate* ones requires the 2FA step-up (see lib/adminElevation.js and
// the `elevated` flag in routes/functions.js).
import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { pool } from '../db.js';
import { getUserById, getUserByUsername, normalizeUsername, toPublicUser, updateUser } from '../lib/users.js';
import { getMethods, withoutMethod } from '../lib/twofa.js';
import { verifyTotp } from '../lib/totp.js';
import { verifyPasskeyAssertion } from '../lib/passkeys.js';
import { bearerOf, ELEVATION_MINUTES, signElevation } from '../lib/adminElevation.js';
import { b64uEncode } from '../lib/webauthn.js';
import { HttpError } from '../lib/httpError.js';

// --- Step-up -----------------------------------------------------------------

/** Which second factors the admin can confirm with (and a passkey challenge). */
export async function adminElevateStart(caller) {
  const methods = getMethods(caller);
  if (!methods.length) {
    throw new HttpError(400, 'Turn on two-factor authentication for your own account (Settings → Security) to manage other accounts.');
  }
  const result = { methods, minutes: ELEVATION_MINUTES };
  if (methods.includes('passkey') && caller.passkey_cred_id) {
    const challenge = b64uEncode(crypto.randomBytes(32));
    await updateUser(caller.id, { webauthn_challenge: challenge });
    result.passkey = { challenge, allowCredentials: [{ type: 'public-key', id: caller.passkey_cred_id }] };
  }
  return result;
}

/** Confirms a code or passkey and returns an elevation token (15 minutes). */
export async function adminElevate(caller, body, req) {
  const methods = getMethods(caller);
  if (!methods.length) {
    throw new HttpError(400, 'Turn on two-factor authentication for your own account (Settings → Security) to manage other accounts.');
  }
  if (body?.code) {
    if (!methods.includes('totp') || !caller.totp_secret) throw new HttpError(400, 'No authenticator app is set up');
    if (!(await verifyTotp(caller.totp_secret, String(body.code)))) throw new HttpError(400, 'Invalid code');
  } else if (body?.clientDataJSON) {
    if (!methods.includes('passkey')) throw new HttpError(400, 'No passkey is set up');
    const signCount = await verifyPasskeyAssertion(caller, body, caller.webauthn_challenge, { requireUserVerification: true });
    await updateUser(caller.id, { webauthn_challenge: '', passkey_counter: signCount });
  } else {
    throw new HttpError(400, 'Enter a code from your authenticator app or use your passkey');
  }
  return {
    elevation: signElevation(caller.id, bearerOf(req)),
    expires_at: new Date(Date.now() + ELEVATION_MINUTES * 60 * 1000).toISOString(),
  };
}

// --- Account -----------------------------------------------------------------

async function targetUser(userId) {
  if (!userId) throw new HttpError(400, 'userId is required');
  const user = await getUserById(userId);
  if (!user) throw new HttpError(404, 'User not found');
  return user;
}

const count = async (sql, id) => Number((await pool.query(sql, [id])).rows[0].n);

/** Everything about one account. */
export async function adminGetUser(caller, body) {
  const u = await targetUser(body?.userId);
  const id = u.id;
  const [google, devices] = await Promise.all([
    pool.query('SELECT connector, scope, expires_at, created_at, updated_at FROM google_connections WHERE user_id = $1', [id]),
    pool.query('SELECT token, kind, environment, bundle_id, created_at, updated_at FROM device_push_tokens WHERE user_id = $1 ORDER BY updated_at DESC', [id]),
  ]);
  const stats = {
    assignments: await count('SELECT count(*) n FROM assignments WHERE created_by_id = $1', id),
    completed: await count('SELECT count(*) n FROM assignments WHERE created_by_id = $1 AND completed', id),
    overdue: await count('SELECT count(*) n FROM assignments WHERE created_by_id = $1 AND NOT completed AND due_date < now()', id),
    classes: await count('SELECT count(*) n FROM classes WHERE created_by_id = $1', id),
    templates: await count('SELECT count(*) n FROM assignment_templates WHERE created_by_id = $1', id),
    study_sessions: await count('SELECT count(*) n FROM study_sessions WHERE created_by_id = $1', id),
    study_minutes: Number((await pool.query(
      "SELECT coalesce(sum(duration_minutes), 0) n FROM study_sessions WHERE created_by_id = $1 AND session_type = 'focus'", [id]
    )).rows[0].n),
    notifications: await count('SELECT count(*) n FROM notifications WHERE user_id = $1', id),
    unread_notifications: await count('SELECT count(*) n FROM notifications WHERE user_id = $1 AND NOT read', id),
  };
  const d = u.data || {};
  return {
    user: {
      ...toPublicUser(u),
      has_password: !!u.password_hash,
      twofa_methods: getMethods(u),
    },
    stats,
    connections: {
      google: google.rows,
      blackboard: d.blackboard_access_token
        ? { connected: true, domain: d.blackboard_domain || null, expires_at: d.blackboard_expires_at || null }
        : { connected: false },
    },
    devices: devices.rows.map((r) => ({ ...r, token: `…${r.token.slice(-8)}`, id: r.token.slice(-16) })),
  };
}

/** Username, display name and full name. */
export async function adminUpdateUser(caller, body) {
  const u = await targetUser(body?.userId);
  const patch = {};
  if (body.name !== undefined) patch.name = String(body.name || '').trim().slice(0, 80) || null;
  if (body.full_name !== undefined) patch.full_name = String(body.full_name || '').trim().slice(0, 80) || null;
  if (body.username !== undefined) {
    let username;
    try { username = normalizeUsername(body.username); } catch (e) { throw new HttpError(400, e.message); }
    if (username !== String(u.username).toLowerCase()) {
      const taken = await getUserByUsername(username);
      if (taken && taken.id !== u.id) throw new HttpError(400, 'That username is taken');
      patch.username = username;
    }
  }
  if (!Object.keys(patch).length) return { ok: true, user: toPublicUser(u) };
  try {
    return { ok: true, user: toPublicUser(await updateUser(u.id, patch)) };
  } catch (e) {
    if (e.code === '23505') throw new HttpError(400, 'That username is taken');
    throw e;
  }
}

// Settings an admin may change for someone (the same ones people change
// themselves, plus the streak/onboarding flags). Server-managed secrets are
// never writable here.
const ADMIN_SETTING_KEYS = new Set([
  'onboarding_completed', 'welcome_style', 'leaderboard_enabled',
  'reminders_enabled', 'reminder_lead_hours', 'push_reminders', 'dnd_enabled', 'dnd_start', 'dnd_end',
  'sync_completion_to_classroom', 'grading_scale', 'timezone', 'class_live_activities',
]);

export async function adminUpdateUserSettings(caller, body) {
  const u = await targetUser(body?.userId);
  const patch = {};
  for (const [k, v] of Object.entries(body?.settings || {})) {
    if (!ADMIN_SETTING_KEYS.has(k)) throw new HttpError(400, `Unknown setting: ${k}`);
    patch[k] = v;
  }
  if (!Object.keys(patch).length) throw new HttpError(400, 'Nothing to change');
  return { ok: true, user: toPublicUser(await updateUser(u.id, patch)) };
}

export async function adminSetPassword(caller, body) {
  const u = await targetUser(body?.userId);
  const pw = String(body?.password || '');
  if (pw.length < 8) throw new HttpError(400, 'Passwords must be at least 8 characters');
  await updateUser(u.id, {
    password_hash: await bcrypt.hash(pw, 10),
    sessions_invalidated_at: body?.signOut === false ? u.data?.sessions_invalidated_at ?? null : new Date().toISOString(),
  });
  return { ok: true };
}

export async function adminSignOutUser(caller, body) {
  const u = await targetUser(body?.userId);
  await updateUser(u.id, { sessions_invalidated_at: new Date().toISOString() });
  return { ok: true };
}

export async function adminRemove2faMethod(caller, body) {
  const u = await targetUser(body?.userId);
  const method = body?.method;
  if (!['totp', 'passkey'].includes(method)) throw new HttpError(400, 'Invalid method');
  const methods = withoutMethod(getMethods(u), method);
  const update = { twofa_methods: methods };
  if (method === 'totp') update.totp_secret = null;
  if (method === 'passkey') Object.assign(update, { passkey_cred_id: null, passkey_pub_key: null, passkey_alg: null, passkey_counter: 0 });
  Object.assign(update, methods.length
    ? { twofa_enabled: true, twofa_method: methods.includes(u.twofa_method) ? u.twofa_method : methods[0] }
    : { twofa_enabled: false, twofa_method: null });
  await updateUser(u.id, update);
  return { ok: true, methods };
}

export async function adminDisconnectUser(caller, body) {
  const u = await targetUser(body?.userId);
  const connector = body?.connector;
  if (connector === 'google_calendar' || connector === 'google_classroom') {
    await pool.query('DELETE FROM google_connections WHERE user_id = $1 AND connector = $2', [u.id, connector]);
  } else if (connector === 'blackboard') {
    await pool.query(
      `UPDATE users SET data = data - 'blackboard_access_token' - 'blackboard_refresh_token' - 'blackboard_expires_at'
         - 'blackboard_user_id' - 'blackboard_state', updated_at = now() WHERE id = $1`,
      [u.id]
    );
  } else {
    throw new HttpError(400, 'Unknown connection');
  }
  return { ok: true };
}

export async function adminRemoveDevice(caller, body) {
  const u = await targetUser(body?.userId);
  const id = String(body?.deviceId || '');
  if (!id) throw new HttpError(400, 'deviceId is required');
  await pool.query('DELETE FROM device_push_tokens WHERE user_id = $1 AND right(token, 16) = $2', [u.id, id]);
  return { ok: true };
}

// --- The account's data ------------------------------------------------------

// Tables an admin can browse/edit for a user, the owner column, a sort, and
// the columns that may be changed.
const RECORDS = {
  assignments: {
    owner: 'created_by_id', order: 'due_date ASC NULLS LAST',
    editable: ['title', 'class_id', 'class_name', 'due_date', 'priority', 'type', 'notes', 'description', 'completed',
      'progress', 'subtasks', 'points', 'score'],
  },
  classes: { owner: 'created_by_id', order: 'name ASC', editable: ['name', 'color', 'teacher_name', 'best_streak'] },
  assignment_templates: { owner: 'created_by_id', order: 'name ASC', editable: ['name', 'title', 'type', 'priority', 'points', 'notes'] },
  study_sessions: { owner: 'created_by_id', order: 'created_at DESC', editable: ['duration_minutes', 'session_type', 'note'] },
  notifications: { owner: 'user_id', order: 'created_at DESC', editable: ['title', 'content', 'read'] },
};

function table(name) {
  const t = RECORDS[name];
  if (!t) throw new HttpError(400, 'Unknown record type');
  return t;
}

export async function adminListRecords(caller, body) {
  const u = await targetUser(body?.userId);
  const t = table(body?.type);
  const limit = Math.min(Math.max(Number(body?.limit) || 500, 1), 2000);
  const res = await pool.query(`SELECT * FROM ${body.type} WHERE ${t.owner} = $1 ORDER BY ${t.order} LIMIT ${limit}`, [u.id]);
  return { records: res.rows };
}

export async function adminUpdateRecord(caller, body) {
  const u = await targetUser(body?.userId);
  const t = table(body?.type);
  if (!body?.id) throw new HttpError(400, 'id is required');
  const sets = [];
  const values = [];
  for (const [k, v] of Object.entries(body.changes || {})) {
    if (!t.editable.includes(k)) throw new HttpError(400, `${k} can't be changed`);
    values.push(k === 'subtasks' ? JSON.stringify(v ?? []) : v === '' ? null : v);
    sets.push(`${k} = $${values.length}`);
  }
  if (!sets.length) throw new HttpError(400, 'Nothing to change');
  values.push(body.id, u.id);
  const res = await pool.query(
    `UPDATE ${body.type} SET ${sets.join(', ')}, updated_at = now()
     WHERE id = $${values.length - 1} AND ${t.owner} = $${values.length} RETURNING *`,
    values
  );
  if (!res.rows[0]) throw new HttpError(404, 'Record not found');
  return { ok: true, record: res.rows[0] };
}

export async function adminDeleteRecord(caller, body) {
  const u = await targetUser(body?.userId);
  const t = table(body?.type);
  if (!body?.id) throw new HttpError(400, 'id is required');
  const res = await pool.query(`DELETE FROM ${body.type} WHERE id = $1 AND ${t.owner} = $2`, [body.id, u.id]);
  return { ok: true, deleted: res.rowCount };
}

/** Deletes all of one kind of record for the user (e.g. clear notifications). */
export async function adminClearRecords(caller, body) {
  const u = await targetUser(body?.userId);
  const t = table(body?.type);
  const res = await pool.query(`DELETE FROM ${body.type} WHERE ${t.owner} = $1`, [u.id]);
  return { ok: true, deleted: res.rowCount };
}
