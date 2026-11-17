// Starts the iOS "class starts soon" Live Activity 5 minutes before each
// class meeting (ActivityKit push-to-start), and ends it 10 minutes after the
// class starts. Runs every minute; each class meeting is sent at most once
// (class_activity_sends). Needs APNs credentials (see lib/apns.js); without
// them it does nothing and the app falls back to a local notification.
import { pool } from '../db.js';
import { apnsConfigured, sendLiveActivityPush } from '../lib/apns.js';
import { fcmConfigured } from '../lib/fcm.js';
import { sendAndroidData } from '../lib/notify.js';

export const LEAD_MINUTES = 5;
export const END_AFTER_MINUTES = 10;
const DEFAULT_TZ = 'America/New_York';

export function userTimeZone(data) {
  const tz = data?.timezone;
  if (typeof tz !== 'string' || !tz) return DEFAULT_TZ;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return tz;
  } catch {
    return DEFAULT_TZ;
  }
}

const WEEKDAYS = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };

// Calendar parts of an instant in a time zone.
export function zonedParts(date, tz) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23', weekday: 'short',
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(date).map((p) => [p.type, p.value])
  );
  return {
    year: Number(parts.year), month: Number(parts.month), day: Number(parts.day),
    hour: Number(parts.hour), minute: Number(parts.minute), second: Number(parts.second),
    weekday: WEEKDAYS[parts.weekday],
  };
}

// The UTC instant of a wall-clock time in a time zone (DST-safe).
export function zonedTimeToUtc(year, month, day, hour, minute, tz) {
  const target = Date.UTC(year, month - 1, day, hour, minute);
  let guess = target;
  for (let i = 0; i < 3; i++) {
    const p = zonedParts(new Date(guess), tz);
    const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
    const diff = asUtc - target;
    if (diff === 0) break;
    guess -= diff;
  }
  return new Date(guess);
}

// [{ day: 0-6, start: "HH:MM", end: "HH:MM" }], ignoring malformed entries.
export function cleanSchedule(schedule) {
  if (!Array.isArray(schedule)) return [];
  return schedule
    .map((m) => {
      const day = Number(m?.day);
      const match = /^(\d{1,2}):(\d{2})$/.exec(String(m?.start || ''));
      if (!Number.isInteger(day) || day < 0 || day > 6 || !match) return null;
      const hour = Number(match[1]);
      const minute = Number(match[2]);
      if (hour > 23 || minute > 59) return null;
      return { day, hour, minute, end: typeof m.end === 'string' ? m.end : null };
    })
    .filter(Boolean);
}

/** The FCM data message for Android's class-start countdown. */
export function androidClassData(cls, state) {
  return {
    type: 'class_start',
    classId: cls.id,
    className: cls.name,
    subject: cls.subject || '',
    room: cls.room || '',
    color: cls.color || '#f19d37',
    startsAt: state.startsAt,
    endsAfterMinutes: END_AFTER_MINUTES,
    dueCount: state.dueCount,
    dueToday: state.dueToday,
  };
}

export function contentState(startsAt, due) {
  return {
    startsAt: Math.floor(startsAt.getTime() / 1000),
    dueToday: due.slice(0, 3).map((a) => ({ title: a.title, className: a.class_name || '' })),
    dueCount: due.length,
  };
}

export async function dueToday(userId, tz, now) {
  const p = zonedParts(now, tz);
  const dayStart = zonedTimeToUtc(p.year, p.month, p.day, 0, 0, tz);
  const next = new Date(Date.UTC(p.year, p.month - 1, p.day + 1));
  const dayEnd = zonedTimeToUtc(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), 0, 0, tz);
  const res = await pool.query(
    `SELECT title, class_name FROM assignments
     WHERE created_by_id = $1 AND completed = false AND due_date >= $2 AND due_date < $3
     ORDER BY due_date ASC LIMIT 20`,
    [userId, dayStart.toISOString(), dayEnd.toISOString()]
  );
  return res.rows;
}

async function dropDeadToken(result, token) {
  // 410 / BadDeviceToken: the device no longer accepts pushes for this token.
  if (result.status === 410 || result.reason === 'BadDeviceToken' || result.reason === 'Unregistered') {
    await pool.query('DELETE FROM device_push_tokens WHERE token = $1', [token]);
  }
}

export async function sendClassActivities(now = new Date()) {
  const [apns, fcm] = await Promise.all([apnsConfigured(), fcmConfigured()]);
  if (!apns && !fcm) return { skipped: 'No push service configured' };
  let started = 0;
  let ended = 0;

  const usersRes = await pool.query(
    `SELECT DISTINCT u.id, u.data FROM users u
     JOIN device_push_tokens t ON t.user_id = u.id
     WHERE u.role <> 'disabled'`
  );
  for (const user of usersRes.rows) {
    if (user.data?.class_live_activities === false) continue;
    const tz = userTimeZone(user.data);
    const today = zonedParts(now, tz);
    const classes = await pool.query(
      `SELECT id, name, subject, room, color, schedule FROM classes
       WHERE created_by_id = $1 AND jsonb_array_length(schedule) > 0`,
      [user.id]
    );
    for (const cls of classes.rows) {
      for (const m of cleanSchedule(cls.schedule)) {
        if (m.day !== today.weekday) continue;
        const startsAt = zonedTimeToUtc(today.year, today.month, today.day, m.hour, m.minute, tz);
        const lead = startsAt.getTime() - now.getTime();
        if (lead <= 0 || lead > LEAD_MINUTES * 60 * 1000 + 5000) continue;

        // Claim this class meeting so it's only ever sent once.
        const claim = await pool.query(
          `INSERT INTO class_activity_sends (user_id, class_id, starts_at, sent_at)
           VALUES ($1, $2, $3, now()) ON CONFLICT DO NOTHING RETURNING 1`,
          [user.id, cls.id, startsAt.toISOString()]
        );
        if (!claim.rows.length) continue;

        const due = await dueToday(user.id, tz, now);
        const state = contentState(startsAt, due);
        const nowSec = Math.floor(now.getTime() / 1000);
        const payload = {
          aps: {
            timestamp: nowSec,
            event: 'start',
            'content-state': state,
            'attributes-type': 'ClassStartAttributes',
            attributes: {
              classId: cls.id,
              className: cls.name,
              subject: cls.subject || '',
              room: cls.room || '',
              colorHex: cls.color || '#f19d37',
            },
            alert: {
              title: `${cls.name} starts in ${LEAD_MINUTES} minutes`,
              body: [cls.subject, cls.room && `Room ${cls.room}`, due.length && `${due.length} due today`]
                .filter(Boolean).join(' · ') || 'Get ready for class',
            },
            'stale-date': state.startsAt + END_AFTER_MINUTES * 60,
            'relevance-score': 100,
          },
        };
        // Android: a data message the app turns into an ongoing countdown notification.
        if (fcm) {
          const android = await sendAndroidData(user.id, androidClassData(cls, state), { collapseKey: `class-${cls.id}`, ttlSeconds: LEAD_MINUTES * 60 });
          started += android.filter((r) => r.ok).length;
        }
        if (!apns) continue;
        const tokens = await pool.query("SELECT * FROM device_push_tokens WHERE user_id = $1 AND kind = 'live-activity-start'", [user.id]);
        for (const t of tokens.rows) {
          const result = await sendLiveActivityPush({ token: t.token, environment: t.environment, bundleId: t.bundle_id, payload });
          if (result.ok) started++;
          else {
            console.warn(`[class-activities] start push failed (${result.status} ${result.reason || ''})`);
            await dropDeadToken(result, t.token);
          }
        }
      }
    }
  }

  // End activities 10 minutes after their class started, using the push
  // token each started activity reported back (POST /api/devices/class-activity).
  const toEnd = await pool.query(
    `SELECT s.*, c.name, c.subject, c.room FROM class_activity_sends s
     JOIN classes c ON c.id = s.class_id
     WHERE s.update_token IS NOT NULL AND s.ended_at IS NULL
       AND s.starts_at < $1::timestamptz - interval '${END_AFTER_MINUTES} minutes'`,
    [now.toISOString()]
  );
  for (const row of toEnd.rows) {
    const nowSec = Math.floor(now.getTime() / 1000);
    const result = await sendLiveActivityPush({
      token: row.update_token,
      environment: row.environment || 'production',
      bundleId: row.bundle_id,
      payload: {
        aps: {
          timestamp: nowSec,
          event: 'end',
          'content-state': { startsAt: Math.floor(new Date(row.starts_at).getTime() / 1000), dueToday: [], dueCount: 0 },
          'dismissal-date': nowSec,
        },
      },
    });
    if (result.ok) ended++;
    await pool.query(
      'UPDATE class_activity_sends SET ended_at = now() WHERE user_id = $1 AND class_id = $2 AND starts_at = $3',
      [row.user_id, row.class_id, row.starts_at]
    );
  }

  await pool.query("DELETE FROM class_activity_sends WHERE starts_at < now() - interval '2 days'");
  return { started, ended };
}
