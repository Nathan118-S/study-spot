import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const caller = await base44.auth.me();
    if (!caller) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (caller.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId, title, content, type } = body || {};
    if (!title || !title.trim()) return Response.json({ error: 'Title is required' }, { status: 400 });
    if (!['info', 'reminder', 'streak', 'test'].includes(type || 'info'))
      return Response.json({ error: 'Invalid type' }, { status: 400 });

    const recipients = [];
    if (userId === 'all') {
      const users = await base44.asServiceRole.entities.User.list('-created_date', 500);
      recipients.push(...users.map((u) => u.id));
    } else {
      if (!userId) return Response.json({ error: 'User id required' }, { status: 400 });
      const target = await base44.asServiceRole.entities.User.get(userId);
      if (!target) return Response.json({ error: 'User not found' }, { status: 404 });
      recipients.push(userId);
    }

    let created = 0;
    for (const rid of recipients) {
      try {
        await base44.asServiceRole.entities.Notification.create({
          user_id: rid,
          title: title.trim(),
          content: content || '',
          type: type || 'info',
          read: false,
        });
        created++;
      } catch {}
    }
    return Response.json({ ok: true, sent: created, to: userId === 'all' ? 'all' : 'one' });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}