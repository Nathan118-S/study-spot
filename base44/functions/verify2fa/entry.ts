import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { verifyTotp } from '../../shared/totp.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { code } = body || {};
    if (!code) return Response.json({ error: 'Code required' }, { status: 400 });
    const secret = user && user.totp_secret;
    if (!secret) return Response.json({ error: '2FA not configured' }, { status: 400 });
    const ok = await verifyTotp(secret, code);
    if (!ok) return Response.json({ error: 'Invalid code' }, { status: 400 });
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}