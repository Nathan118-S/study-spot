import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId, name } = body || {};
    if (!userId) return Response.json({ error: 'userId is required' }, { status: 400 });
    const cleanName = typeof name === 'string' ? name.trim().slice(0, 80) : '';
    await base44.asServiceRole.entities.User.update(userId, { name: cleanName });
    return Response.json({ ok: true, name: cleanName });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}