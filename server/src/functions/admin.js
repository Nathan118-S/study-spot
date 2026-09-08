import { pool } from '../db.js';
import { updateUser, toPublicUser } from '../lib/users.js';
import { displayName } from '../lib/twofa.js';
import { sendEmail } from '../lib/mailer.js';
import { HttpError } from '../lib/httpError.js';

export async function adminListUsers() {
  const res = await pool.query('SELECT * FROM users ORDER BY created_at DESC LIMIT 500');
  return {
    users: res.rows.map((u) => ({
      id: u.id,
      email: u.email,
      full_name: u.full_name,
      role: u.role,
      name: u.name || null,
      disabled_reason: u.disabled_reason || null,
      twofa_enabled: !!u.twofa_enabled,
      twofa_method: u.twofa_method || (u.twofa_enabled ? 'totp' : null),
      is_verified: !!u.is_verified,
      onboarding_completed: !!u.data?.onboarding_completed,
      created_date: u.created_at,
    })),
  };
}

export async function adminUpdateUser(caller, body) {
  const { userId, name } = body || {};
  if (!userId) throw new HttpError(400, 'userId is required');
  const cleanName = typeof name === 'string' ? name.trim().slice(0, 80) : '';
  await updateUser(userId, { name: cleanName });
  return { ok: true, name: cleanName };
}

export async function adminUpdateUserRole(caller, body) {
  const { userId, role, reason } = body || {};
  if (!userId || !['admin', 'user', 'demo', 'disabled'].includes(role)) throw new HttpError(400, 'Invalid input');
  const update = { role };
  update.disabled_reason = role === 'disabled' ? (reason || '').toString().slice(0, 500) || null : null;
  await updateUser(userId, update);
  return { ok: true };
}

export async function adminDeleteUser(caller, body) {
  const { userId } = body || {};
  if (!userId) throw new HttpError(400, 'Invalid input');
  if (userId === caller.id) throw new HttpError(400, 'Cannot delete your own account');
  await pool.query('DELETE FROM users WHERE id = $1', [userId]);
  return { ok: true };
}

export async function adminResetOnboarding(caller, body) {
  const { userId } = body || {};
  if (!userId) throw new HttpError(400, 'userId is required');
  await updateUser(userId, { onboarding_completed: false });
  return { ok: true };
}

export async function adminReset2fa(caller, body) {
  const { userId } = body || {};
  if (!userId) throw new HttpError(400, 'Invalid input');
  await updateUser(userId, {
    totp_secret: null,
    twofa_enabled: false,
    twofa_method: null,
    twofa_methods: [],
    passkey_cred_id: null,
    passkey_pub_key: null,
    passkey_alg: null,
    passkey_counter: 0,
  });
  return { ok: true };
}

export async function adminRestoreStreak(caller, body) {
  const { userId } = body || {};
  if (!userId) throw new HttpError(400, 'Invalid input');
  const res = await pool.query(
    `SELECT * FROM assignments WHERE created_by_id = $1 AND completed = false
     AND due_date IS NOT NULL AND due_date < now()`,
    [userId]
  );
  if (!res.rows.length) return { restored: 0 };
  await pool.query(
    `UPDATE assignments SET completed = true, updated_at = now() WHERE id = ANY($1::uuid[])`,
    [res.rows.map((a) => a.id)]
  );
  return { restored: res.rows.length };
}

export async function adminVerifyUser(caller, body, sendOtp) {
  const { userId } = body || {};
  if (!userId) throw new HttpError(400, 'userId is required');
  const res = await pool.query('SELECT * FROM users WHERE id = $1', [userId]);
  const target = res.rows[0];
  if (!target) throw new HttpError(404, 'User not found');
  await sendOtp(target.email);
  return { ok: true, email: target.email };
}

export async function adminSendNotification(caller, body) {
  const { userId, title, content, type } = body || {};
  if (!title || !title.trim()) throw new HttpError(400, 'Title is required');
  if (!['info', 'reminder', 'streak', 'test'].includes(type || 'info')) throw new HttpError(400, 'Invalid type');

  const recipients = [];
  if (userId === 'all') {
    const res = await pool.query('SELECT id FROM users ORDER BY created_at DESC LIMIT 500');
    recipients.push(...res.rows.map((u) => u.id));
  } else {
    if (!userId) throw new HttpError(400, 'User id required');
    const res = await pool.query('SELECT id FROM users WHERE id = $1', [userId]);
    if (!res.rows[0]) throw new HttpError(404, 'User not found');
    recipients.push(userId);
  }

  let created = 0;
  for (const rid of recipients) {
    try {
      await pool.query(
        'INSERT INTO notifications (user_id, title, content, type, read) VALUES ($1, $2, $3, $4, false)',
        [rid, title.trim(), content || '', type || 'info']
      );
      created++;
    } catch { /* skip this recipient */ }
  }
  return { ok: true, sent: created, to: userId === 'all' ? 'all' : 'one' };
}

export async function adminSendTestNotification(caller, body) {
  const { userId, type } = body || {};
  if (!userId) throw new HttpError(400, 'User id required');
  if (!['push', 'email'].includes(type)) throw new HttpError(400, 'Invalid type');

  const res = await pool.query('SELECT * FROM users WHERE id = $1', [userId]);
  const target = res.rows[0];
  if (!target) throw new HttpError(404, 'User not found');

  if (type === 'push') {
    try {
      await pool.query(
        'INSERT INTO notifications (user_id, title, content, type, read, action_label, action_url) VALUES ($1, $2, $3, $4, false, $5, $6)',
        [userId, 'Test notification', 'This is a test notification from Study Spot admin.', 'test', 'Open Study Spot', '/']
      );
    } catch { /* in-app create failed; still report push attempted */ }
    // No native mobile push infrastructure in the self-hosted build — the
    // in-app notification above is the delivered channel.
    return { ok: true, sent: 'push', push_delivered: false };
  }

  await sendEmail({
    to: target.email,
    subject: 'Test notification from Study Spot',
    body: `Hi ${displayName(target)},\n\nThis is a test email sent from the Study Spot admin dashboard to confirm email delivery is working.\n\n— Study Spot`,
  });
  return { ok: true, sent: 'email' };
}

async function reassignOwner(table, sourceId, targetId) {
  await pool.query(`UPDATE ${table} SET created_by_id = $1 WHERE created_by_id = $2`, [targetId, sourceId]);
}

export async function adminMergeAccounts(caller, body) {
  const { sourceId, targetId } = body || {};
  if (!sourceId || !targetId) throw new HttpError(400, 'sourceId and targetId are required');
  if (sourceId === targetId) throw new HttpError(400, 'Cannot merge an account with itself');
  if (sourceId === caller.id) throw new HttpError(400, 'Cannot merge your own account away');

  const [assignments, classes] = await Promise.all([
    pool.query('SELECT id FROM assignments WHERE created_by_id = $1', [sourceId]),
    pool.query('SELECT id FROM classes WHERE created_by_id = $1', [sourceId]),
  ]);

  if (assignments.rows.length) await reassignOwner('assignments', sourceId, targetId);
  if (classes.rows.length) await reassignOwner('classes', sourceId, targetId);
  await pool.query('DELETE FROM users WHERE id = $1', [sourceId]);

  return { ok: true, movedAssignments: assignments.rows.length, movedClasses: classes.rows.length };
}
