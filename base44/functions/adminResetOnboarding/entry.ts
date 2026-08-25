import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

// Admin-only: clears a user's onboarding_completed flag so they are
// required to run the first-time setup wizard again on their next visit.
export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId } = body || {};
    if (!userId) return Response.json({ error: 'userId is required' }, { status: 400 });
    await base44.asServiceRole.entities.User.update(userId, { onboarding_completed: false });
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}