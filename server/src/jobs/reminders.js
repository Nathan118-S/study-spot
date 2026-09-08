import { pool } from '../db.js';
import { sendEmail } from '../lib/mailer.js';
import { displayName } from '../lib/twofa.js';

const FETCH_HORIZON_HOURS = 72; // wide enough to cover the max lead-time option (48h)
const REMINDER_TZ = 'America/New_York';

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

function inDndWindow(now, start, end) {
  const s = toMinutes(start);
  const e = toMinutes(end);
  if (s === null || e === null || s === e) return false;
  const cur = nowMinutesInTZ(now, REMINDER_TZ);
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

// Mirrors base44/functions/sendAssignmentReminders — emails users about
// assignments due soon, per-lead-time, honoring do-not-disturb windows.
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

  let reminded = 0;
  let skipped = 0;
  for (const a of assignments) {
    if (!a.due_date || !a.created_by_id) { skipped++; continue; }
    const owner = await getOwner(a.created_by_id);
    if (!owner || !owner.email) { skipped++; continue; }

    const settings = owner.data || {};
    if (settings.reminders_enabled === false) { skipped++; continue; }

    const leads = normalizeLeads(settings.reminder_lead_hours);
    const sentHours = Array.isArray(a.reminder_sent_hours) ? a.reminder_sent_hours.map(Number) : [];
    if (a.reminder_sent === true && sentHours.length === 0) { skipped++; continue; }

    const due = new Date(a.due_date);
    const hoursUntilDue = (due.getTime() - now.getTime()) / (60 * 60 * 1000);

    const pendingLeads = leads.filter((l) => hoursUntilDue <= l && !sentHours.includes(l)).sort((x, y) => y - x);
    if (pendingLeads.length === 0) { skipped++; continue; }

    if (settings.dnd_enabled !== false && settings.dnd_start && settings.dnd_end) {
      if (inDndWindow(now, settings.dnd_start, settings.dnd_end)) { skipped++; continue; }
    }

    const lead = pendingLeads[0];
    const classPart = a.class_name ? ` for ${a.class_name}` : '';
    const subject = `Reminder: "${a.title}" is due soon`;
    const body = [
      `Hi ${displayName(owner)},`,
      '',
      `This is a reminder that "${a.title}"${classPart} is due ${due.toLocaleString()}.`,
      '',
      'Open Study Spot to view the details.',
      '',
      '— Study Spot',
    ].join('\n');

    try {
      await sendEmail({ to: owner.email, subject, body });
      const newSent = [...sentHours, lead];
      const allSent = leads.every((l) => newSent.includes(l));
      await pool.query(
        'UPDATE assignments SET reminder_sent_hours = $1, reminder_sent = $2, updated_at = now() WHERE id = $3',
        [newSent, allSent ? true : a.reminder_sent || false, a.id]
      );
      reminded++;
    } catch {
      skipped++;
    }
  }

  return { reminded, skipped, checked: assignments.length };
}
