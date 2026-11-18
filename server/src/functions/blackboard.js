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
    throw new HttpError(400, 'Blackboard isn’t set up on this server yet. An admin needs to add the Blackboard Key, Secret and Redirect URI in Admin → Integration Secrets.');
  }

  // The iOS app prefixes its state so the web /blackboard/callback page knows
  // to hand the code back to the app instead of finishing in the browser.
  // The iOS and Android apps share the studyspot:// scheme and the "ios." state prefix.
  const state = (['ios', 'android'].includes(body?.client) ? 'ios.' : '') + crypto.randomUUID();
  await updateUser(user.id, { blackboard_state: state, blackboard_instance_url: instanceUrl });
  return { authUrl: buildAuthUrl(instanceUrl, clientId, redirectUri, state) };
}

export async function blackboardCallback(user, body) {
  const { code, state } = body || {};
  if (!code) throw new HttpError(400, 'missing code');
  if (!state || state !== user.data?.blackboard_state) {
    throw new HttpError(400, 'This Blackboard sign-in didn’t start from your current session. Start connecting again from Settings → Blackboard.');
  }

  const instanceUrl = user.data?.blackboard_instance_url;
  if (!instanceUrl) throw new HttpError(400, 'missing instance URL');

  const redirectUri = await resolveSecret('BLACKBOARD_REDIRECT_URI');
  let tokens;
  try {
    tokens = await exchangeCode(instanceUrl, code, redirectUri);
  } catch (e) {
    throw new HttpError(400, e.message);
  }
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

/** GET a Blackboard REST path, following `paging.nextPage`; returns all results. */
async function bbGetAll(instanceUrl, path, auth, maxPages = 10) {
  const results = [];
  let next = path;
  for (let i = 0; next && i < maxPages; i++) {
    const res = await fetch(`${instanceUrl}${next}`, auth);
    if (!res.ok) {
      const err = new Error(`Blackboard ${res.status}`);
      err.status = res.status;
      err.body = await res.text().catch(() => '');
      throw err;
    }
    const json = await res.json();
    results.push(...(json.results || []));
    next = json.paging?.nextPage || null;
  }
  return results;
}

/** The courses the student is enrolled in (with course details). */
async function myCourses(instanceUrl, auth, userId) {
  const path = (who) => `/learn/api/public/v1/users/${encodeURIComponent(who)}/courses?expand=course&limit=100`;
  let memberships;
  try {
    memberships = await bbGetAll(instanceUrl, path('me'), auth);
  } catch (e) {
    // Older Learn versions don't accept "me"; use the ID from the token.
    if (!userId || (e.status !== 400 && e.status !== 404)) throw e;
    memberships = await bbGetAll(instanceUrl, path(userId), auth);
  }
  return memberships
    .filter((m) => m.course && m.course.availability?.available !== 'No')
    .map((m) => ({ id: m.course.id || m.courseId, name: m.course.name || m.course.courseId || 'Unnamed course' }))
    .filter((c) => c.id);
}

/** Gradable items with due dates in one course (v2, then v1). Null if the student can't read them. */
async function courseColumns(instanceUrl, auth, courseId) {
  for (const v of ['v2', 'v1']) {
    try {
      return await bbGetAll(instanceUrl, `/learn/api/public/${v}/courses/${encodeURIComponent(courseId)}/gradebook/columns?limit=200`, auth, 5);
    } catch (e) {
      if (e.status !== 404 && e.status !== 403 && e.status !== 400) throw e;
    }
  }
  return null;
}

/**
 * Due dates from the student's Blackboard calendar (gradable items), which
 * students can always read. Blackboard limits each request to 16 weeks.
 */
async function calendarDueItems(instanceUrl, auth) {
  const items = [];
  const now = Date.now();
  const windows = [[now - 14 * 86400e3, now + 16 * 7 * 86400e3 - 14 * 86400e3], [now + 14 * 7 * 86400e3, now + 30 * 7 * 86400e3]];
  for (const [from, to] of windows) {
    const qs = new URLSearchParams({ type: 'GradebookColumn', since: new Date(from).toISOString(), until: new Date(to).toISOString(), limit: '200' });
    try {
      items.push(...(await bbGetAll(instanceUrl, `/learn/api/public/v1/calendars/items?${qs}`, auth, 5)));
    } catch (e) {
      if (e.status !== 404 && e.status !== 403 && e.status !== 400) throw e;
    }
  }
  return items;
}

function blackboardError(e) {
  if (e.status === 401) {
    return Object.assign(new HttpError(400, 'Your Blackboard sign-in has expired. Disconnect Blackboard in Settings and connect it again.'), { notConnected: true });
  }
  if (e.status === 403) {
    return new HttpError(502, 'Blackboard didn’t allow Study Spot to read your courses. Your school may need to approve Study Spot (see studyspot.education/schools).');
  }
  return new HttpError(502, `Blackboard sync failed (${e.status || e.message})`);
}

export async function syncBlackboard(user) {
  const d = user.data || {};
  if (!d.blackboard_access_token) throw Object.assign(new HttpError(400, 'Blackboard not connected'), { notConnected: true });
  const instanceUrl = validateInstanceUrl(d.blackboard_instance_url);
  const accessToken = await getValidToken(user);
  const auth = { headers: { Authorization: `Bearer ${accessToken}` } };

  let courses;
  try {
    courses = await myCourses(instanceUrl, auth, d.blackboard_user_id);
  } catch (e) {
    throw blackboardError(e);
  }

  const existingClassesRes = await pool.query(
    'SELECT * FROM classes WHERE created_by_id = $1 AND source = $2',
    [user.id, 'blackboard']
  );
  const classByExt = {};
  for (const c of existingClassesRes.rows) classByExt[c.external_id] = c;
  let colorIdx = existingClassesRes.rows.length;
  for (const course of courses) {
    if (classByExt[course.id]) continue;
    const inserted = await pool.query(
      `INSERT INTO classes (created_by_id, name, color, teacher_name, source, external_id)
       VALUES ($1, $2, $3, '', 'blackboard', $4) RETURNING *`,
      [user.id, course.name, COLORS[colorIdx++ % COLORS.length], course.id]
    );
    classByExt[course.id] = inserted.rows[0];
  }

  const existingAsgRes = await pool.query(
    'SELECT id, external_id, title, due_date FROM assignments WHERE created_by_id = $1 AND source = $2',
    [user.id, 'blackboard']
  );
  const byExt = new Map(existingAsgRes.rows.map((a) => [a.external_id, a]));
  let created = 0;
  let updated = 0;

  const save = async (extId, cls, title, dueRaw, description, points) => {
    const due = new Date(dueRaw);
    if (!extId || !cls || isNaN(due.getTime())) return;
    title = (title || 'Untitled item').slice(0, 300);
    const prev = byExt.get(extId);
    if (prev) {
      if (prev.title !== title || new Date(prev.due_date).getTime() !== due.getTime()) {
        await pool.query('UPDATE assignments SET title = $1, due_date = $2, updated_at = now() WHERE id = $3', [title, due.toISOString(), prev.id]);
        updated++;
      }
      return;
    }
    await pool.query(
      `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed, points)
       VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, 'blackboard', $9, false, $10)`,
      [user.id, title, cls.id, cls.name, due.toISOString(), guessPriority(title), guessType(title),
        (description || '').slice(0, 5000), extId, typeof points === 'number' ? points : 0]
    );
    byExt.set(extId, { external_id: extId, title, due_date: due.toISOString() });
    created++;
  };

  // Gradebook columns where the student may read them; the calendar for the rest.
  const blocked = [];
  for (const course of courses) {
    let columns;
    try {
      columns = await courseColumns(instanceUrl, auth, course.id);
    } catch (e) {
      throw blackboardError(e);
    }
    if (columns === null) { blocked.push(course.id); continue; }
    for (const col of columns) {
      const dueRaw = col.grading?.due || col.dueDate;
      if (!dueRaw) continue;
      await save(col.id, classByExt[course.id], col.name, dueRaw, col.description, col.score?.possible ?? col.pointsPossible);
    }
  }
  if (blocked.length) {
    let items = [];
    try {
      items = await calendarDueItems(instanceUrl, auth);
    } catch (e) {
      throw blackboardError(e);
    }
    const blockedSet = new Set(blocked);
    for (const it of items) {
      if (!blockedSet.has(it.calendarId)) continue;
      const colId = it.dynamicCalendarItemProps?.gradebookColumnId || it.id;
      await save(colId, classByExt[it.calendarId], it.title, it.end || it.start, it.description, undefined);
    }
  }

  return { synced: true, courses: courses.length, imported: created, updated };
}
