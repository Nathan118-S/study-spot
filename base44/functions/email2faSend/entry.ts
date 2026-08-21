import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const code = String(Math.floor(100000 + Math.random() * 900000));
    const expires = Date.now() + 10 * 60 * 1000;
    await base44.auth.updateMe({ email_2fa_code: code, email_2fa_expires: expires });

    await base44.asServiceRole.integrations.Core.SendEmail({
      to: user.email,
      subject: 'Your Study Spot verification code',
      body: `Your verification code is ${code}. It expires in 10 minutes. If you didn't request this, you can ignore this email.`,
    });

    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}