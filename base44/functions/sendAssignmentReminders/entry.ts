import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req: Request): Promise<Response> {
  try {
    const base44 = createClientFromRequest(req);

    const now = new Date();
    const horizon = new Date(now.getTime() + 24 * 60 * 60 * 1000); // 24h ahead

    // Assignments due within the next 24h, not completed, not yet reminded.
    const assignments = await base44.asServiceRole.entities.Assignment.filter({
      completed: false,
      reminder_sent: { $ne: true },
      due_date: { $gte: now.toISOString(), $lte: horizon.toISOString() }
    });

    let reminded = 0;
    for (const a of assignments) {
      if (!a.due_date || !a.created_by_id) continue;
      let owner;
      try {
        owner = await base44.asServiceRole.entities.User.get(a.created_by_id);
      } catch {
        continue;
      }
      if (!owner || !owner.email) continue;

      const due = new Date(a.due_date);
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
      } catch (err) {
        // skip this one; continue with the rest
      }
    }

    return Response.json({ reminded, checked: assignments.length });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}