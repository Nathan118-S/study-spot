import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { getMethods, withoutMethod } from '../../shared/twofa.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { method } = body || {};
    if (!['totp', 'email', 'passkey'].includes(method)) {
      return Response.json({ error: 'Invalid method' }, { status: 400 });
    }
    const current = getMethods(user);
    if (!current.includes(method)) {
      return Response.json({ error: 'Method not enabled' }, { status: 400 });
    }
    const methods = withoutMethod(current, method);
    const update = { twofa_methods: methods };
    if (method === 'totp') update.totp_secret = null;
    if (method === 'passkey') {
      update.passkey_cred_id = null;
      update.passkey_pub_key = null;
      update.passkey_alg = null;
      update.passkey_counter = 0;
    }
    if (methods.length === 0) {
      update.twofa_enabled = false;
      update.twofa_method = null;
    } else {
      update.twofa_enabled = true;
      const cur = user.twofa_method;
      update.twofa_method = methods.includes(cur) ? cur : methods[0];
    }
    await base44.auth.updateMe(update);
    return Response.json({ ok: true, methods });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}