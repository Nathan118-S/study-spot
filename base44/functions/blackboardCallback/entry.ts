import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { secrets } from 'base44:runtime';
import { exchangeCode } from '../../shared/blackboard.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const body = await req.json().catch(() => ({}));
    const { code, state } = body || {};
    if (!code) return Response.json({ error: 'missing code' }, { status: 400 });
    if (!state || state !== user.data?.blackboard_state) {
      return Response.json({ error: 'invalid state' }, { status: 400 });
    }

    const instanceUrl = user.data?.blackboard_instance_url;
    if (!instanceUrl) return Response.json({ error: 'missing instance URL' }, { status: 400 });

    const redirectUri = secrets.get('BLACKBOARD_REDIRECT_URI');
    const tokens = await exchangeCode(instanceUrl, code, redirectUri);
    const expiresAt = new Date(Date.now() + (tokens.expires_in || 3600) * 1000).toISOString();

    await base44.auth.updateMe({
      blackboard_access_token: tokens.access_token,
      blackboard_refresh_token: tokens.refresh_token || '',
      blackboard_expires_at: expiresAt,
      blackboard_user_id: tokens.user_id || '',
      blackboard_state: '',
    });

    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}