import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { b64uEncode } from '../../shared/webauthn.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const d = user.data || {};
    if (!d.passkey_cred_id) {
      return Response.json({ error: 'No passkey registered' }, { status: 400 });
    }

    const challenge = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
    await base44.auth.updateMe({ webauthn_challenge: challenge });

    return Response.json({
      challenge,
      allowCredentials: [{ type: 'public-key', id: d.passkey_cred_id }],
    });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}