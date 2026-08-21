import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

const FETCH_HORIZON_HOURS = 72; // wide enough to cover the max lead-time option (48h)

export default async function(req: Request): Promise<Response> {
  try {
    const base44 = createClientFromRequest(req);

    const now = new Date();
    const fetchHorizon = new Date(now.getTime() + FETCH_HORIZON_HOURS * 60 * 60 * 1000);

    // Upcoming, incomplete, not-yet-reminded assignments.
    const assignments = await base44.asServiceRole.entities.Assignment.filter({
      completed: false,
      reminder_sent: { $ne: true },
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
      const leadHours = typeof settings.reminder_lead_hours === 'number'
        ? settings.reminder_lead_hours
        : 24;

      const due = new Date(a.due_date);
      const hoursUntilDue = (due.getTime() - now.getTime()) / (60 * 60 * 1000);
      if (hoursUntilDue > leadHours) { skipped++; continue; } // not yet in the reminder window

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
        await base44.asServiceRole.entities.Assignment.update(a.id, { reminder_sent: true });
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