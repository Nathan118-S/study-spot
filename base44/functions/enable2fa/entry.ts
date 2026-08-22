import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { verifyTotp } from '../../shared/totp.ts';
import { getMethods, withMethod } from '../../shared/twofa.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { code, secret } = body || {};
    if (!code || !secret) return Response.json({ error: 'Code and secret required' }, { status: 400 });
    const ok = await verifyTotp(secret, code);
    if (!ok) return Response.json({ error: 'Invalid code' }, { status: 400 });
    const methods = withMethod(getMethods(user), 'totp');
    await base44.auth.updateMe({
      totp_secret: secret,
      twofa_enabled: true,
      twofa_method: 'totp',
      twofa_methods: methods,
    });
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}