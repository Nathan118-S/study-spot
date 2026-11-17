import { pool } from '../db.js';
import { apnsConfigured } from '../lib/apns.js';
import { sendUserPush } from '../lib/notify.js';

// Users can pick custom lead times, so look well ahead; per-lead checks below
// decide what is actually due a reminder.
const FETCH_HORIZON_HOURS = 24 * 14;
const DEFAULT_TZ = 'America/New_York';

// The user's IANA time zone (saved by the apps), validated.
function userTimeZone(settings) {
  const tz = settings?.timezone;
  if (typeof tz !== 'string' || !tz) return DEFAULT_TZ;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return tz;
  } catch {
    return DEFAULT_TZ;
  }
}

function toMinutes(hhmm) {
  if (!hhmm || typeof hhmm !== 'string') return null;
  const [h, m] = hhmm.split(':').map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

function nowMinutesInTZ(now, tz) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: tz, hour12: false, hour: '2-digit', minute: '2-digit' }).formatToParts(now);
  const h = Number(parts.find((p) => p.type === 'hour').value) % 24;
  const m = Number(parts.find((p) => p.type === 'minute').value);
  return h * 60 + m;
}

function inDndWindow(now, start, end, tz) {
  const s = toMinutes(start);
  const e = toMinutes(end);
  if (s === null || e === null || s === e) return false;
  const cur = nowMinutesInTZ(now, tz);
  if (s < e) return cur >= s && cur < e;
  return cur >= s || cur < e;
}

function normalizeLeads(raw) {
  if (Array.isArray(raw)) {
    const arr = raw.map(Number).filter((n) => Number.isFinite(n) && n > 0);
    return arr.length ? Array.from(new Set(arr)).sort((a, b) => a - b) : [24];
  }
  if (typeof raw === 'number' && raw > 0) return [raw];
  return [24];
}

// "in 2 hours", "tomorrow at 9:00 AM", "Friday at 9:00 AM" in the user's time zone.
export function duePhrase(due, now, tz) {
  const minutes = Math.round((due.getTime() - now.getTime()) / 60000);
  if (minutes < 60) return `in ${Math.max(minutes, 1)} minute${minutes === 1 ? '' : 's'}`;
  if (minutes < 6 * 60) {
    const h = Math.round(minutes / 60);
    return `in ${h} hour${h === 1 ? '' : 's'}`;
  }
  const time = new Intl.DateTimeFormat('en-US', { timeZone: tz, hour: 'numeric', minute: '2-digit' }).format(due);
  const day = (d) => new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
  const dayDiff = Math.round((Date.parse(day(due)) - Date.parse(day(now))) / 86400000);
  if (dayDiff === 0) return `today at ${time}`;
  if (dayDiff === 1) return `tomorrow at ${time}`;
  if (dayDiff < 7) return `${new Intl.DateTimeFormat('en-US', { timeZone: tz, weekday: 'long' }).format(due)} at ${time}`;
  return new Intl.DateTimeFormat('en-US', { timeZone: tz, month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(due);
}

// Scheduled job: reminds users about assignments due soon, per-lead-time,
// honoring do-not-disturb windows, with a notification on each of the user's
// iPhones and in the in-app notification center.
export async function sendAssignmentReminders() {
  const now = new Date();
  const fetchHorizon = new Date(now.getTime() + FETCH_HORIZON_HOURS * 60 * 60 * 1000);

  const asgRes = await pool.query(
    `SELECT * FROM assignments WHERE completed = false AND due_date > $1 AND due_date <= $2`,
    [now.toISOString(), fetchHorizon.toISOString()]
  );
  const assignments = asgRes.rows;

  const ownerCache = new Map();
  const getOwner = async (id) => {
    if (ownerCache.has(id)) return ownerCache.get(id);
    const res = await pool.query('SELECT * FROM users WHERE id = $1', [id]);
    const o = res.rows[0] || null;
    ownerCache.set(id, o);
    return o;
  };

  const pushReady = await apnsConfigured();

  let reminded = 0;
  let skipped = 0;
  for (const a of assignments) {
    if (!a.due_date || !a.created_by_id) { skipped++; continue; }
    const owner = await getOwner(a.created_by_id);
    if (!owner) { skipped++; continue; }

    const settings = owner.data || {};
    if (settings.reminders_enabled === false) { skipped++; continue; }

    const leads = normalizeLeads(settings.reminder_lead_hours);
    const sentHours = Array.isArray(a.reminder_sent_hours) ? a.reminder_sent_hours.map(Number) : [];
    if (a.reminder_sent === true && sentHours.length === 0) { skipped++; continue; }

    const due = new Date(a.due_date);
    const hoursUntilDue = (due.getTime() - now.getTime()) / (60 * 60 * 1000);

    const pendingLeads = leads.filter((l) => hoursUntilDue <= l && !sentHours.includes(l)).sort((x, y) => y - x);
    if (pendingLeads.length === 0) { skipped++; continue; }

    if (settings.dnd_enabled === true && settings.dnd_start && settings.dnd_end) {
      if (inDndWindow(now, settings.dnd_start, settings.dnd_end, userTimeZone(settings))) { skipped++; continue; }
    }

    const lead = pendingLeads[0];
    const when = duePhrase(due, now, userTimeZone(settings));
    const title = a.class_name ? a.title : 'Assignment due soon';
    const body = a.class_name ? `Due ${when}` : `"${a.title}" is due ${when}`;

    let delivered = false;
    try {
      // In-app notification center (shown on the website and in the app).
      await pool.query(
        `INSERT INTO notifications (user_id, title, content, type, read, action_label, action_url)
         VALUES ($1, $2, $3, 'reminder', false, 'Open assignment', $4)`,
        [owner.id, `"${a.title}" is due ${when}`, a.class_name || '', '/']
      );
      delivered = true;
      if (pushReady && settings.push_reminders !== false) {
        await sendUserPush(owner.id, {
          title, subtitle: a.class_name || undefined, body,
          data: { assignmentId: a.id, url: `studyspot://assignment/${a.id}` },
          collapseId: `asg-${a.id}`, threadId: 'assignment-reminders',
        });
      }
    } catch (err) {
      console.warn('Reminder failed:', err.message);
    }

    if (!delivered) { skipped++; continue; }
    const newSent = [...sentHours, lead];
    const allSent = leads.every((l) => newSent.includes(l));
    await pool.query(
      'UPDATE assignments SET reminder_sent_hours = $1, reminder_sent = $2, updated_at = now() WHERE id = $3',
      [newSent, allSent ? true : a.reminder_sent || false, a.id]
    );
    reminded++;
  }

  return { reminded, skipped, checked: assignments.length };
}
