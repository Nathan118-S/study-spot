import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { code, enable } = body || {};
    if (!code) return Response.json({ error: 'Code required' }, { status: 400 });

    const d = user.data || {};
    if (!d.email_2fa_code || !d.email_2fa_expires) {
      return Response.json({ error: 'No code sent. Request a new one.' }, { status: 400 });
    }
    if (Date.now() > Number(d.email_2fa_expires)) {
      return Response.json({ error: 'Code expired. Request a new one.' }, { status: 400 });
    }
    if (String(code) !== String(d.email_2fa_code)) {
      return Response.json({ error: 'Invalid code' }, { status: 400 });
    }

    const update = { email_2fa_code: '', email_2fa_expires: 0 };
    if (enable) {
      update.twofa_method = 'email';
      update.twofa_enabled = true;
    }
    await base44.auth.updateMe(update);
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}