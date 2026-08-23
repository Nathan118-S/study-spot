import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (user.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const users = await base44.asServiceRole.entities.User.list();
    return Response.json({
      users: users.map((u) => ({
        id: u.id,
        email: u.email,
        full_name: u.full_name,
        role: u.role,
        name: u.name || null,
        disabled_reason: u.disabled_reason || null,
        twofa_enabled: !!u.twofa_enabled,
        twofa_method: u.twofa_method || (u.twofa_enabled ? 'totp' : null),
        is_verified: !!u.is_verified,
        created_date: u.created_date,
      })),
    });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}