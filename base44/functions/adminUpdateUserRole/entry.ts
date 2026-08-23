import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId, role, reason } = body || {};
    if (!userId || !['admin', 'user', 'demo', 'disabled'].includes(role)) {
      return Response.json({ error: 'Invalid input' }, { status: 400 });
    }
    const update = { role };
    if (role === 'disabled') {
      update.disabled_reason = (reason || '').toString().slice(0, 500) || null;
    } else {
      update.disabled_reason = null;
    }
    await base44.asServiceRole.entities.User.update(userId, update);
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}