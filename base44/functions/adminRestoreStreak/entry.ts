import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId } = body || {};
    if (!userId) return Response.json({ error: 'Invalid input' }, { status: 400 });
    const now = new Date().toISOString();
    const overdue = await base44.asServiceRole.entities.Assignment.filter({
      created_by_id: userId,
      completed: false,
    });
    const targets = overdue.filter((a) => a.due_date && new Date(a.due_date).toISOString() < now);
    if (!targets.length) return Response.json({ restored: 0 });
    await base44.asServiceRole.entities.Assignment.bulkUpdate(
      targets.map((a) => ({ id: a.id, completed: true }))
    );
    return Response.json({ restored: targets.length });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}