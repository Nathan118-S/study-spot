import crypto from 'node:crypto';
import { pool } from '../db.js';
import { updateUser } from '../lib/users.js';
import { HttpError } from '../lib/httpError.js';
import { buildAuthUrl, exchangeCode, refreshAccessToken, validateInstanceUrl } from '../lib/blackboard.js';
import { guessPriority, guessType } from '../lib/sync.js';
import { resolveSecret } from '../lib/secrets.js';

export async function blackboardAuthUrl(user, body) {
  const raw = (body?.instanceUrl || '').trim();
  if (!raw) throw new HttpError(400, 'instanceUrl is required');
  let instanceUrl;
  try {
    instanceUrl = validateInstanceUrl(raw);
  } catch (e) {
    throw new HttpError(400, e.message);
  }

  const [clientId, redirectUri] = await Promise.all([
    resolveSecret('BLACKBOARD_CLIENT_ID'),
    resolveSecret('BLACKBOARD_REDIRECT_URI'),
  ]);
  if (!clientId || !redirectUri) {
    throw new HttpError(500, 'Blackboard is not configured. Set BLACKBOARD_CLIENT_ID and BLACKBOARD_REDIRECT_URI.');
  }

  const state = crypto.randomUUID();
  await updateUser(user.id, { blackboard_state: state, blackboard_instance_url: instanceUrl });
  return { authUrl: buildAuthUrl(instanceUrl, clientId, redirectUri, state) };
}

export async function blackboardCallback(user, body) {
  const { code, state } = body || {};
  if (!code) throw new HttpError(400, 'missing code');
  if (!state || state !== user.data?.blackboard_state) throw new HttpError(400, 'invalid state');

  const instanceUrl = user.data?.blackboard_instance_url;
  if (!instanceUrl) throw new HttpError(400, 'missing instance URL');

  const redirectUri = await resolveSecret('BLACKBOARD_REDIRECT_URI');
  const tokens = await exchangeCode(instanceUrl, code, redirectUri);
  const expiresAt = new Date(Date.now() + (tokens.expires_in || 3600) * 1000).toISOString();

  await updateUser(user.id, {
    blackboard_access_token: tokens.access_token,
    blackboard_refresh_token: tokens.refresh_token || '',
    blackboard_expires_at: expiresAt,
    blackboard_user_id: tokens.user_id || '',
    blackboard_state: '',
  });
  return { ok: true };
}

export async function disconnectBlackboard(user) {
  await updateUser(user.id, {
    blackboard_access_token: '',
    blackboard_refresh_token: '',
    blackboard_expires_at: '',
    blackboard_user_id: '',
    blackboard_instance_url: '',
    blackboard_state: '',
  });
  return { ok: true };
}

async function getValidToken(user) {
  const d = user.data || {};
  let accessToken = d.blackboard_access_token;
  const expiresAt = d.blackboard_expires_at ? new Date(d.blackboard_expires_at).getTime() : 0;
  if (expiresAt - Date.now() < 60000 && d.blackboard_refresh_token) {
    try {
      const redirectUri = await resolveSecret('BLACKBOARD_REDIRECT_URI');
      const tokens = await refreshAccessToken(d.blackboard_instance_url, d.blackboard_refresh_token, redirectUri);
      accessToken = tokens.access_token;
      const newExpires = new Date(Date.now() + (tokens.expires_in || 3600) * 1000).toISOString();
      await updateUser(user.id, {
        blackboard_access_token: tokens.access_token,
        blackboard_refresh_token: tokens.refresh_token || d.blackboard_refresh_token,
        blackboard_expires_at: newExpires,
      });
    } catch {
      // Fall through and try the existing token.
    }
  }
  return accessToken;
}

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];

export async function syncBlackboard(user) {
  const d = user.data || {};
  if (!d.blackboard_access_token) throw Object.assign(new HttpError(400, 'Blackboard not connected'), { notConnected: true });
  const instanceUrl = d.blackboard_instance_url;
  const accessToken = await getValidToken(user);
  const auth = { headers: { Authorization: `Bearer ${accessToken}` } };

  const coursesRes = await fetch(`${instanceUrl}/learn/api/public/v1/courses?limit=200`, auth);
  if (!coursesRes.ok) throw new HttpError(502, 'Blackboard courses failed: ' + (await coursesRes.text()));
  const courses = (await coursesRes.json()).results || [];

  const existingClassesRes = await pool.query(
    'SELECT * FROM classes WHERE created_by_id = $1 AND source = $2',
    [user.id, 'blackboard']
  );
  const classByExt = {};
  for (const c of existingClassesRes.rows) classByExt[c.external_id] = c;
  let colorIdx = 0;
  const classMap = {};
  for (const course of courses) {
    const extId = course.id || course.externalId;
    if (!extId) continue;
    if (classByExt[extId]) {
      classMap[extId] = classByExt[extId];
    } else {
      const inserted = await pool.query(
        `INSERT INTO classes (created_by_id, name, color, teacher_name, source, external_id)
         VALUES ($1, $2, $3, '', 'blackboard', $4) RETURNING *`,
        [user.id, course.name || 'Unnamed course', COLORS[colorIdx++ % COLORS.length], extId]
      );
      classMap[extId] = inserted.rows[0];
    }
  }

  const existingAsgRes = await pool.query(
    'SELECT external_id FROM assignments WHERE created_by_id = $1 AND source = $2',
    [user.id, 'blackboard']
  );
  const seenExt = new Set(existingAsgRes.rows.map((a) => a.external_id));

  let created = 0;
  for (const course of courses) {
    const extId = course.id || course.externalId;
    const cls = classMap[extId];
    if (!cls) continue;
    const colRes = await fetch(
      `${instanceUrl}/learn/api/public/v1/courses/${encodeURIComponent(course.id)}/gradebook/columns?limit=200`,
      auth
    );
    if (!colRes.ok) continue;
    const columns = (await colRes.json()).results || [];
    for (const col of columns) {
      if (!col.dueDate) continue;
      const colExt = col.id || col.externalId;
      if (!colExt || seenExt.has(colExt)) continue;
      const due = new Date(col.dueDate);
      if (isNaN(due.getTime())) continue;
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed, points)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'blackboard', $10, false, $11)`,
        [
          user.id, col.name || 'Untitled item', cls.id, cls.name, due.toISOString(),
          guessPriority(col.name), guessType(col.name), col.description || '', col.description || '',
          colExt, typeof col.pointsPossible === 'number' ? col.pointsPossible : 0,
        ]
      );
      seenExt.add(colExt);
      created++;
    }
  }

  return { synced: true, courses: courses.length, imported: created };
}
