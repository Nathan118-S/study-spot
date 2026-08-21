import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { secrets } from 'base44:runtime';
import { buildAuthUrl } from '../../shared/blackboard.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const body = await req.json().catch(() => ({}));
    const instanceUrl = (body?.instanceUrl || '').trim().replace(/\/$/, '');
    if (!instanceUrl) return Response.json({ error: 'instanceUrl is required' }, { status: 400 });
    if (!/^https:\/\//i.test(instanceUrl)) {
      return Response.json({ error: 'Instance URL must start with https://' }, { status: 400 });
    }

    const clientId = secrets.get('BLACKBOARD_CLIENT_ID');
    const redirectUri = secrets.get('BLACKBOARD_REDIRECT_URI');
    if (!clientId || !redirectUri) {
      return Response.json(
        { error: 'Blackboard is not configured. Set BLACKBOARD_CLIENT_ID and BLACKBOARD_REDIRECT_URI.' },
        { status: 500 }
      );
    }

    const state = crypto.randomUUID();
    await base44.auth.updateMe({
      blackboard_state: state,
      blackboard_instance_url: instanceUrl,
    });

    return Response.json({ authUrl: buildAuthUrl(instanceUrl, clientId, redirectUri, state) });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}