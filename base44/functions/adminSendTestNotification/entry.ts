import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { displayName } from '../../shared/user.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const caller = await base44.auth.me();
    if (!caller) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (caller.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });
    const body = await req.json();
    const { userId, type } = body || {};
    if (!userId) return Response.json({ error: 'User id required' }, { status: 400 });
    if (!['push', 'email'].includes(type)) return Response.json({ error: 'Invalid type' }, { status: 400 });

    const target = await base44.asServiceRole.entities.User.get(userId);
    if (!target) return Response.json({ error: 'User not found' }, { status: 404 });

    if (type === 'push') {
      await base44.asServiceRole.integrations.Core.SendPushNotification({
        user_id: userId,
        title: 'Test notification',
        content: 'This is a test push notification from Study Spot admin.',
        action_label: 'Open Study Spot',
        action_url: '/',
      });
      return Response.json({ ok: true, sent: 'push' });
    }

    await base44.asServiceRole.integrations.Core.SendEmail({
      to: target.email,
      subject: 'Test notification from Study Spot',
      body: `Hi ${displayName(target)},\n\nThis is a test email sent from the Study Spot admin dashboard to confirm email delivery is working.\n\n— Study Spot`,
    });
    return Response.json({ ok: true, sent: 'email' });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}