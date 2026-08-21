import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    await base44.auth.updateMe({
      blackboard_access_token: '',
      blackboard_refresh_token: '',
      blackboard_expires_at: '',
      blackboard_user_id: '',
      blackboard_instance_url: '',
      blackboard_state: '',
    });

    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}