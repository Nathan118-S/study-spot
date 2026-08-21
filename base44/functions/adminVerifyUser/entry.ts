import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });

    const body = await req.json().catch(() => ({}));
    const userId = body?.userId;
    if (!userId) return Response.json({ error: 'userId is required' }, { status: 400 });

    await base44.asServiceRole.entities.User.update(userId, { is_verified: true });
    return Response.json({ ok: true, userId });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}