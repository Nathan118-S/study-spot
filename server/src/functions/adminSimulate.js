// Admin tools to simulate what someone sees on their iPhone: notifications
// (sent as real pushes to their devices) and Live Activities (started and
// ended with ActivityKit pushes). All are behind the admin 2FA step-up.
import { pool } from '../db.js';
import { getUserById } from '../lib/users.js';
import { apnsConfigured, isDeadToken, sendLiveActivityPush } from '../lib/apns.js';
import { sendUserPushDetailed } from '../lib/notify.js';
import { duePhrase } from '../jobs/reminders.js';
import { contentState, dueToday, END_AFTER_MINUTES, userTimeZone } from '../jobs/classActivities.js';
import { HttpError } from '../lib/httpError.js';

// Swift's default Date encoding (used by ActivityKit content states) counts
// seconds from 2001-01-01.
const APPLE_EPOCH_OFFSET = 978307200;

async function target(userId) {
  if (!userId) throw new HttpError(400, 'userId is required');
  const u = await getUserById(userId);
  if (!u) throw new HttpError(404, 'User not found');
  return u;
}

const clip = (v, n) => String(v ?? '').trim().slice(0, n);

async function devicesOf(userId, kind) {
  return (await pool.query('SELECT * FROM device_push_tokens WHERE user_id = $1 AND kind = $2', [userId, kind])).rows;
}

/** What can be simulated for this account (devices, classes, assignments). */
export async function adminSimulateOptions(caller, body) {
  const u = await target(body?.userId);
  const [devices, classes, assignments] = await Promise.all([
    pool.query('SELECT kind, count(*)::int n FROM device_push_tokens WHERE user_id = $1 GROUP BY kind', [u.id]),
    pool.query('SELECT id, name, subject, room, color FROM classes WHERE created_by_id = $1 ORDER BY name', [u.id]),
    pool.query(
      `SELECT id, title, class_name, due_date FROM assignments WHERE created_by_id = $1 AND NOT completed
       ORDER BY due_date ASC NULLS LAST LIMIT 100`,
      [u.id]
    ),
  ]);
  const counts = Object.fromEntries(devices.rows.map((r) => [r.kind, r.n]));
  return {
    apns: await apnsConfigured(),
    devices: {
      notifications: counts.alert || 0,
      class_activity: counts['live-activity-start'] || 0,
      timer_activity: counts['timer-activity-start'] || 0,
    },
    classes: classes.rows,
    assignments: assignments.rows,
  };
}

/**
 * Sends a notification exactly as the app would receive it.
 * kind: 'reminder' (assignmentId, or a sample), 'streak' (classId, or a
 * sample), or 'custom' (title, subtitle, body). inApp also adds it to the
 * notification bell.
 */
export async function adminSimulateNotification(caller, body) {
  const u = await target(body?.userId);
  const kind = body?.kind || 'custom';
  const now = new Date();
  let msg;

  if (kind === 'reminder') {
    let a = { id: null, title: 'Sample assignment', class_name: 'Sample class', due_date: new Date(now.getTime() + 3 * 3600e3) };
    if (body.assignmentId) {
      const res = await pool.query('SELECT * FROM assignments WHERE id = $1 AND created_by_id = $2', [body.assignmentId, u.id]);
      if (!res.rows[0]) throw new HttpError(404, 'Assignment not found');
      a = res.rows[0];
    }
    const due = a.due_date ? new Date(a.due_date) : new Date(now.getTime() + 3 * 3600e3);
    const when = duePhrase(due < now ? new Date(now.getTime() + 3600e3) : due, now, userTimeZone(u.data));
    msg = {
      title: a.class_name ? a.title : 'Assignment due soon',
      subtitle: a.class_name || undefined,
      body: a.class_name ? `Due ${when}` : `"${a.title}" is due ${when}`,
      data: a.id ? { assignmentId: a.id, url: `studyspot://assignment/${a.id}` } : {},
      threadId: 'assignment-reminders',
      inAppTitle: `"${a.title}" is due ${when}`, inAppContent: a.class_name || '', type: 'reminder',
    };
  } else if (kind === 'streak') {
    let name = 'Sample class';
    if (body.classId) {
      const res = await pool.query('SELECT name FROM classes WHERE id = $1 AND created_by_id = $2', [body.classId, u.id]);
      if (!res.rows[0]) throw new HttpError(404, 'Class not found');
      name = res.rows[0].name;
    }
    const n = Math.max(2, Math.min(Number(body.streak) || 7, 999));
    msg = {
      title: 'New streak record! 🔥',
      body: `${name}: ${n} assignments in a row — your new best.`,
      data: { url: 'studyspot://tab/streaks' },
      inAppTitle: 'New streak record! 🔥', inAppContent: `${name}: ${n} assignments in a row — your new best.`, type: 'streak',
    };
  } else if (kind === 'custom') {
    const title = clip(body.title, 120);
    if (!title) throw new HttpError(400, 'A title is required');
    msg = {
      title, subtitle: clip(body.subtitle, 120) || undefined, body: clip(body.body, 500),
      data: body.url ? { url: clip(body.url, 300) } : {},
      inAppTitle: title, inAppContent: clip(body.body, 500), type: 'info',
    };
  } else {
    throw new HttpError(400, 'Unknown notification kind');
  }

  if (body.inApp) {
    await pool.query(
      'INSERT INTO notifications (user_id, title, content, type, read) VALUES ($1, $2, $3, $4, false)',
      [u.id, msg.inAppTitle, msg.inAppContent, msg.type]
    );
  }
  const results = await sendUserPushDetailed(u.id, {
    title: msg.title, subtitle: msg.subtitle, body: msg.body, data: { simulated: true, ...msg.data }, threadId: msg.threadId,
  });
  return summary(results, { apns: await apnsConfigured(), inApp: !!body.inApp });
}

function summary(results, extra = {}) {
  return {
    ok: true,
    ...extra,
    devices: results.length,
    delivered: results.filter((r) => r.ok).length,
    results: results.map(({ device, environment, ok, status, reason }) => ({ device, environment, ok, status, reason })),
  };
}

async function pushActivity(tokens, payload) {
  const results = [];
  for (const t of tokens) {
    const result = await sendLiveActivityPush({ token: t.token, environment: t.environment, bundleId: t.bundle_id, payload });
    results.push({ device: `…${t.token.slice(-8)}`, environment: t.environment, ...result });
    if (!result.ok && isDeadToken(result)) await pool.query('DELETE FROM device_push_tokens WHERE token = $1', [t.token]);
  }
  return results;
}

/**
 * Live Activities on the person's iPhones.
 * - kind 'class_start', action 'start': the "class starts soon" activity for
 *   classId (or a sample class), starting in `minutes` (1–60, default 5),
 *   listing what's due today.
 * - kind 'class_start', action 'end': ends the class activities their
 *   iPhones reported back (real classes only).
 * - kind 'study_timer', action 'start': a running study timer
 *   (phase focus|short|long, minutes), optionally for an assignment.
 */
export async function adminSimulateLiveActivity(caller, body) {
  const u = await target(body?.userId);
  if (!(await apnsConfigured())) throw new HttpError(400, 'Apple Push isn\'t set up. Add the APNs key in Admin → Integration Secrets.');
  const kind = body?.kind;
  const action = body?.action || 'start';
  const now = new Date();
  const nowSec = Math.floor(now.getTime() / 1000);

  if (kind === 'class_start' && action === 'start') {
    const tokens = await devicesOf(u.id, 'live-activity-start');
    if (!tokens.length) throw new HttpError(400, 'No iPhone has registered for class Live Activities yet (needs iOS 17.2+ and the app opened while signed in).');
    let cls = { id: 'simulated', name: 'Sample class', subject: 'Simulation', room: '101', color: '#f19d37' };
    if (body.classId) {
      const res = await pool.query('SELECT id, name, subject, room, color FROM classes WHERE id = $1 AND created_by_id = $2', [body.classId, u.id]);
      if (!res.rows[0]) throw new HttpError(404, 'Class not found');
      cls = res.rows[0];
    }
    const minutes = Math.max(1, Math.min(Number(body.minutes) || 5, 60));
    const startsAt = new Date(now.getTime() + minutes * 60e3);
    const due = await dueToday(u.id, userTimeZone(u.data), now);
    const state = contentState(startsAt, due);
    if (cls.id !== 'simulated') {
      // Lets the regular job (or "end" below) close it once the app reports its token.
      await pool.query(
        `INSERT INTO class_activity_sends (user_id, class_id, starts_at, sent_at) VALUES ($1, $2, $3, now())
         ON CONFLICT DO NOTHING`,
        [u.id, cls.id, startsAt.toISOString()]
      );
    }
    const results = await pushActivity(tokens, {
      aps: {
        timestamp: nowSec,
        event: 'start',
        'content-state': state,
        'attributes-type': 'ClassStartAttributes',
        attributes: {
          classId: cls.id, className: cls.name, subject: cls.subject || '', room: cls.room || '', colorHex: cls.color || '#f19d37',
        },
        alert: {
          title: `${cls.name} starts in ${minutes} minute${minutes === 1 ? '' : 's'}`,
          body: [cls.subject, cls.room && `Room ${cls.room}`, due.length && `${due.length} due today`].filter(Boolean).join(' · ') || 'Get ready for class',
        },
        'stale-date': state.startsAt + END_AFTER_MINUTES * 60,
        'relevance-score': 100,
      },
    });
    return summary(results, { startsAt: startsAt.toISOString() });
  }

  if (kind === 'class_start' && action === 'end') {
    const rows = (await pool.query(
      'SELECT * FROM class_activity_sends WHERE user_id = $1 AND update_token IS NOT NULL AND ended_at IS NULL',
      [u.id]
    )).rows;
    const results = [];
    for (const row of rows) {
      const result = await sendLiveActivityPush({
        token: row.update_token, environment: row.environment || 'production', bundleId: row.bundle_id,
        payload: {
          aps: {
            timestamp: nowSec, event: 'end', 'dismissal-date': nowSec,
            'content-state': { startsAt: Math.floor(new Date(row.starts_at).getTime() / 1000), dueToday: [], dueCount: 0 },
          },
        },
      });
      results.push({ device: `…${row.update_token.slice(-8)}`, environment: row.environment, ...result });
      await pool.query(
        'UPDATE class_activity_sends SET ended_at = now() WHERE user_id = $1 AND class_id = $2 AND starts_at = $3',
        [row.user_id, row.class_id, row.starts_at]
      );
    }
    return summary(results, { ended: rows.length });
  }

  if (kind === 'study_timer' && action === 'start') {
    const tokens = await devicesOf(u.id, 'timer-activity-start');
    if (!tokens.length) throw new HttpError(400, 'No iPhone has registered for study timer Live Activities yet (needs iOS 17.2+ and the latest app opened while signed in).');
    const phase = ['focus', 'short', 'long'].includes(body.phase) ? body.phase : 'focus';
    const label = { focus: 'Focus', short: 'Short Break', long: 'Long Break' }[phase];
    const minutes = Math.max(1, Math.min(Number(body.minutes) || 25, 120));
    let assignmentTitle = null;
    let className = null;
    if (body.assignmentId) {
      const res = await pool.query('SELECT title, class_name FROM assignments WHERE id = $1 AND created_by_id = $2', [body.assignmentId, u.id]);
      if (!res.rows[0]) throw new HttpError(404, 'Assignment not found');
      ({ title: assignmentTitle, class_name: className } = res.rows[0]);
    }
    const total = minutes * 60;
    const endsAtUnix = nowSec + total;
    const results = await pushActivity(tokens, {
      aps: {
        timestamp: nowSec,
        event: 'start',
        'attributes-type': 'StudyTimerAttributes',
        attributes: { assignmentTitle, className, accentHex: '#f19d37' },
        'content-state': {
          phase, phaseLabel: label, endsAt: endsAtUnix - APPLE_EPOCH_OFFSET, remaining: total, total, finished: false,
        },
        alert: { title: `${label} started`, body: assignmentTitle ? `Studying ${assignmentTitle}` : `${minutes} minutes` },
        'stale-date': endsAtUnix + 60,
      },
    });
    return summary(results, { endsAt: new Date(endsAtUnix * 1000).toISOString() });
  }

  throw new HttpError(400, 'Unknown Live Activity simulation');
}
