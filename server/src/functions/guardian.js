// Parent / guardian view: a student creates private, read-only links that
// show a parent or guardian a weekly summary (what's due, what's overdue,
// what got done, streaks and study time, and grades only if the student
// turns them on). Only a SHA-256 hash of each link's token is stored.
import crypto from 'node:crypto';
import { pool } from '../db.js';
import { HttpError } from '../lib/httpError.js';
import { userTimeZone } from '../jobs/classActivities.js';

export const MAX_GUARDIAN_LINKS = 5;
const LABEL_MAX = 40;

export const hashGuardianToken = (token) => crypto.createHash('sha256').update(String(token)).digest('hex');

/** A plausible link token: 32 random bytes as base64url (43 characters). */
export const isGuardianToken = (token) => typeof token === 'string' && /^[A-Za-z0-9_-]{43}$/.test(token);

function cleanLabel(label) {
  const s = String(label ?? '').replace(/\s+/g, ' ').trim().slice(0, LABEL_MAX);
  if (!s) throw new HttpError(400, 'Give the link a name, like “Mom”.');
  return s;
}

function publicLink(row) {
  return {
    id: row.id,
    label: row.label,
    show_grades: row.show_grades,
    created_at: row.created_at,
    last_viewed_at: row.last_viewed_at,
  };
}

/** The address the website is served from, for building the parent link. */
function siteUrl(req) {
  const configured = String(process.env.FRONTEND_URL || '').split(',')[0].trim();
  if (configured) return configured.replace(/\/+$/, '');
  const host = req?.get?.('x-forwarded-host') || req?.get?.('host');
  const proto = req?.get?.('x-forwarded-proto')?.split(',')[0] || req?.protocol || 'https';
  return host ? `${proto}://${host}` : '';
}

function rejectDemo(user) {
  if (user.role === 'demo') throw new HttpError(403, 'Parent links aren’t available in the demo.');
}

export async function guardianListLinks(user) {
  const { rows } = await pool.query(
    'SELECT * FROM guardian_links WHERE user_id = $1 AND revoked_at IS NULL ORDER BY created_at',
    [user.id]
  );
  return { links: rows.map(publicLink), max: MAX_GUARDIAN_LINKS };
}

export async function guardianCreateLink(user, body, req) {
  rejectDemo(user);
  const label = cleanLabel(body.label);
  const count = (await pool.query(
    'SELECT count(*)::int n FROM guardian_links WHERE user_id = $1 AND revoked_at IS NULL',
    [user.id]
  )).rows[0].n;
  if (count >= MAX_GUARDIAN_LINKS) {
    throw new HttpError(400, `You can have up to ${MAX_GUARDIAN_LINKS} parent links. Turn one off to add another.`);
  }
  const token = crypto.randomBytes(32).toString('base64url');
  const { rows } = await pool.query(
    `INSERT INTO guardian_links (user_id, token_hash, label, show_grades)
     VALUES ($1, $2, $3, $4) RETURNING *`,
    [user.id, hashGuardianToken(token), label, body.show_grades === true]
  );
  // The token itself is never stored, so this is the only time the link is shown.
  return { link: publicLink(rows[0]), url: `${siteUrl(req)}/parent/${token}` };
}

export async function guardianUpdateLink(user, body) {
  const sets = [];
  const params = [body.id, user.id];
  if (body.label !== undefined) {
    params.push(cleanLabel(body.label));
    sets.push(`label = $${params.length}`);
  }
  if (body.show_grades !== undefined) {
    params.push(body.show_grades === true);
    sets.push(`show_grades = $${params.length}`);
  }
  if (!sets.length) throw new HttpError(400, 'Nothing to change');
  const { rows } = await pool.query(
    `UPDATE guardian_links SET ${sets.join(', ')}
     WHERE id::text = $1 AND user_id = $2 AND revoked_at IS NULL RETURNING *`,
    params
  );
  if (!rows[0]) throw new HttpError(404, 'Link not found');
  return { link: publicLink(rows[0]) };
}

export async function guardianRevokeLink(user, body) {
  const { rowCount } = await pool.query(
    'UPDATE guardian_links SET revoked_at = now() WHERE id::text = $1 AND user_id = $2 AND revoked_at IS NULL',
    [body.id, user.id]
  );
  if (!rowCount) throw new HttpError(404, 'Link not found');
  return { ok: true };
}

// --- The summary a parent sees ----------------------------------------------

// Mirrors src/lib/streaks.js computeClassStreak() (and jobs/streaks.js).
function classStreak(items, now) {
  const sorted = [...items].sort((a, b) => new Date(a.due_date || 0) - new Date(b.due_date || 0));
  if (!sorted.length) return 0;
  if (sorted.some((a) => a.due_date && !a.completed && new Date(a.due_date) < now)) return 0;
  let run = 0;
  for (let i = sorted.length - 1; i >= 0 && sorted[i].completed; i--) run++;
  return run;
}

const DEFAULT_SCALE = [
  { letter: 'A', min: 90 }, { letter: 'B', min: 80 }, { letter: 'C', min: 70 }, { letter: 'D', min: 60 }, { letter: 'F', min: 0 },
];

// Mirrors src/lib/grading.js letterGrade() for a percentage.
function letterFor(pct, scale) {
  const sorted = (Array.isArray(scale) && scale.length ? scale : DEFAULT_SCALE)
    .filter((e) => e && typeof e.letter === 'string' && Number.isFinite(Number(e.min)))
    .sort((a, b) => b.min - a.min);
  if (!sorted.length) return null;
  for (const e of sorted) if (pct >= Number(e.min)) return e.letter;
  return sorted[sorted.length - 1].letter;
}

const num = (v) => (v === null || v === undefined || v === '' ? null : Number.isFinite(Number(v)) ? Number(v) : null);

function item(a, classesById) {
  const cls = a.class_id ? classesById.get(a.class_id) : null;
  return {
    title: a.title,
    class_name: cls?.name || a.class_name || null,
    class_color: cls?.color || null,
    type: a.type,
    due_date: a.due_date,
  };
}

/**
 * The summary for a link token, or null when the link doesn't exist or was
 * turned off. Records when the link was last opened.
 */
export async function guardianSummary(token) {
  if (!isGuardianToken(token)) return null;
  const link = (await pool.query(
    `SELECT g.*, u.name, u.full_name, u.role, u.data
     FROM guardian_links g JOIN users u ON u.id = g.user_id
     WHERE g.token_hash = $1`,
    [hashGuardianToken(token)]
  )).rows[0];
  if (!link || link.revoked_at || link.role === 'disabled') return null;

  const userId = link.user_id;
  const tz = userTimeZone(link.data);
  const now = new Date();
  const [classesRes, assignmentsRes, studyRes, weekRes] = await Promise.all([
    pool.query('SELECT id, name, color FROM classes WHERE created_by_id = $1 ORDER BY name', [userId]),
    pool.query(
      `SELECT id, title, class_id, class_name, type, due_date, completed, points, score, updated_at
       FROM assignments WHERE created_by_id = $1`,
      [userId]
    ),
    // Focus time since the start of this week (Monday) in the student's time zone.
    pool.query(
      `SELECT COALESCE(SUM(duration_minutes), 0)::float AS minutes, count(*)::int AS sessions
       FROM study_sessions
       WHERE created_by_id = $1 AND session_type = 'focus'
         AND COALESCE(completed_at, started_at, created_at) >= (date_trunc('week', now() AT TIME ZONE $2) AT TIME ZONE $2)`,
      [userId, tz]
    ),
    pool.query(`SELECT (date_trunc('week', now() AT TIME ZONE $1) AT TIME ZONE $1) AS week_start`, [tz]),
  ]);
  await pool.query('UPDATE guardian_links SET last_viewed_at = now() WHERE id = $1', [link.id]);

  const classes = classesRes.rows;
  const classesById = new Map(classes.map((c) => [c.id, c]));
  const assignments = assignmentsRes.rows;
  const week = 7 * 86400e3;
  const due = (a) => (a.due_date ? new Date(a.due_date).getTime() : null);
  const byDue = (a, b) => due(a) - due(b);

  const upcoming = assignments
    .filter((a) => !a.completed && due(a) !== null && due(a) >= now.getTime() && due(a) <= now.getTime() + week)
    .sort(byDue);
  const overdue = assignments
    .filter((a) => !a.completed && due(a) !== null && due(a) < now.getTime())
    .sort((a, b) => byDue(b, a));
  // There's no completion time; the last change to a completed item stands in for it.
  const completed = assignments
    .filter((a) => a.completed && a.updated_at && new Date(a.updated_at).getTime() >= now.getTime() - week)
    .sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at));

  const streaks = classes
    .map((c) => ({ class_name: c.name, class_color: c.color, streak: classStreak(assignments.filter((a) => a.class_id === c.id), now) }))
    .filter((s) => s.streak > 0)
    .sort((a, b) => b.streak - a.streak);

  let grades = null;
  if (link.show_grades) {
    const scale = link.data?.grading_scale;
    grades = classes
      .map((c) => {
        const graded = assignments.filter((a) => a.class_id === c.id && num(a.score) !== null && (num(a.points) || 0) > 0);
        if (!graded.length) return null;
        const pct = (graded.reduce((s, a) => s + num(a.score), 0) / graded.reduce((s, a) => s + num(a.points), 0)) * 100;
        return {
          class_name: c.name,
          class_color: c.color,
          percent: Math.round(pct * 10) / 10,
          letter: letterFor(pct, scale),
          graded_count: graded.length,
        };
      })
      .filter(Boolean);
  }

  const display = String(link.name || link.full_name || '').trim();
  const LIMIT = 50;
  return {
    student: { first_name: display.split(/\s+/)[0] || null },
    label: link.label,
    generated_at: now.toISOString(),
    time_zone: tz,
    week_start: weekRes.rows[0]?.week_start || null,
    upcoming: upcoming.slice(0, LIMIT).map((a) => item(a, classesById)),
    upcoming_count: upcoming.length,
    overdue: overdue.slice(0, LIMIT).map((a) => item(a, classesById)),
    overdue_count: overdue.length,
    completed: completed.slice(0, LIMIT).map((a) => ({ ...item(a, classesById), completed_at: a.updated_at })),
    completed_count: completed.length,
    streak: { best: streaks[0]?.streak || 0, classes: streaks },
    study: { minutes_this_week: Math.round(studyRes.rows[0]?.minutes || 0), sessions_this_week: studyRes.rows[0]?.sessions || 0 },
    show_grades: link.show_grades,
    grades,
  };
}
