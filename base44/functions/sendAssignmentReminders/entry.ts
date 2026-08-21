import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

const FETCH_HORIZON_HOURS = 72; // wide enough to cover the max lead-time option (48h)
const REMINDER_TZ = 'America/New_York';

function toMinutes(hhmm) {
  if (!hhmm || typeof hhmm !== 'string') return null;
  const [h, m] = hhmm.split(':').map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

function nowMinutesInTZ(now, tz) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: tz, hour12: false, hour: '2-digit', minute: '2-digit',
  }).formatToParts(now);
  const h = Number(parts.find((p) => p.type === 'hour').value) % 24;
  const m = Number(parts.find((p) => p.type === 'minute').value);
  return h * 60 + m;
}

function inDndWindow(now, start, end) {
  const s = toMinutes(start);
  const e = toMinutes(end);
  if (s === null || e === null || s === e) return false;
  const cur = nowMinutesInTZ(now, REMINDER_TZ);
  if (s < e) return cur >= s && cur < e;       // same-day window
  return cur >= s || cur < e;                  // overnight window
}

function normalizeLeads(raw) {
  if (Array.isArray(raw)) {
    const arr = raw.map(Number).filter((n) => Number.isFinite(n) && n > 0);
    return arr.length ? Array.from(new Set(arr)).sort((a, b) => a - b) : [24];
  }
  if (typeof raw === 'number' && raw > 0) return [raw];
  return [24];
}

export default async function(req: Request): Promise<Response> {
  try {
    const base44 = createClientFromRequest(req);

    const now = new Date();
    const fetchHorizon = new Date(now.getTime() + FETCH_HORIZON_HOURS * 60 * 60 * 1000);

    // Upcoming, incomplete assignments (reminders tracked per lead-time via reminder_sent_hours).
    const assignments = await base44.asServiceRole.entities.Assignment.filter({
      completed: false,
      due_date: { $gt: now.toISOString(), $lte: fetchHorizon.toISOString() }
    });

    const ownerCache = new Map();
    const getOwner = async (id) => {
      if (ownerCache.has(id)) return ownerCache.get(id);
      try {
        const o = await base44.asServiceRole.entities.User.get(id);
        ownerCache.set(id, o);
        return o;
      } catch {
        ownerCache.set(id, null);
        return null;
      }
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
      const sentHours = Array.isArray(a.reminder_sent_hours)
        ? a.reminder_sent_hours.map(Number)
        : [];

      // Migration guard: legacy single-reminder assignments already notified.
      if (a.reminder_sent === true && sentHours.length === 0) { skipped++; continue; }

      const due = new Date(a.due_date);
      const hoursUntilDue = (due.getTime() - now.getTime()) / (60 * 60 * 1000);

      // Eligible leads: their milestone has been reached and they haven't fired yet.
      // Send only the largest (earliest) pending lead per run to avoid burst emails.
      const pendingLeads = leads
        .filter((l) => hoursUntilDue <= l && !sentHours.includes(l))
        .sort((x, y) => y - x);
      if (pendingLeads.length === 0) { skipped++; continue; }

      // Do not disturb
      if (settings.dnd_enabled !== false && settings.dnd_start && settings.dnd_end) {
        if (inDndWindow(now, settings.dnd_start, settings.dnd_end)) { skipped++; continue; }
      }

      const lead = pendingLeads[0];
      const classPart = a.class_name ? ` for ${a.class_name}` : '';
      const subject = `Reminder: "${a.title}" is due soon`;
      const body = [
        `Hi ${owner.full_name || 'there'},`,
        '',
        `This is a reminder that "${a.title}"${classPart} is due ${due.toLocaleString()}.`,
        '',
        'Open Study Spot to view the details.',
        '',
        '— Study Spot'
      ].join('\n');

      try {
        await base44.asServiceRole.integrations.Core.SendEmail({
          to: owner.email,
          subject,
          body
        });
        const newSent = [...sentHours, lead];
        const allSent = leads.every((l) => newSent.includes(l));
        await base44.asServiceRole.entities.Assignment.update(a.id, {
          reminder_sent_hours: newSent,
          reminder_sent: allSent ? true : (a.reminder_sent || false)
        });
        reminded++;
      } catch {
        skipped++;
      }
    }

    return Response.json({ reminded, skipped, checked: assignments.length });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}