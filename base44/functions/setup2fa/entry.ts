import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { generateSecret } from '../../shared/totp.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const secret = generateSecret();
    const label = encodeURIComponent(user.email || 'user');
    const issuer = encodeURIComponent('Study Spot');
    const otpauth_url = `otpauth://totp/StudySpot:${label}?secret=${secret}&issuer=${issuer}&algorithm=SHA1&digits=6&period=30`;
    return Response.json({ secret, otpauth_url });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}