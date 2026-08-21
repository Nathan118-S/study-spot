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

    // The platform enforces email verification at login and does not expose a
    // way for an admin to mark another user's email as verified (the is_verified
    // field is system-managed and read-only via entity updates). The most
    // useful action an admin can take is to trigger a fresh verification email
    // so the user can verify themselves and sign in.
    const target = await base44.asServiceRole.entities.User.get(userId);
    if (!target) return Response.json({ error: 'User not found' }, { status: 404 });

    await base44.auth.resendOtp(target.email);
    return Response.json({ ok: true, email: target.email });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}