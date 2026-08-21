import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { b64uEncode } from '../../shared/webauthn.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const challenge = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
    const userHandle = b64uEncode(new TextEncoder().encode(user.id));
    await base44.auth.updateMe({ webauthn_challenge: challenge });

    const exclude = [];
    if (user.data?.passkey_cred_id) {
      exclude.push({ type: 'public-key', id: user.data.passkey_cred_id });
    }

    return Response.json({
      rp: { name: 'Study Spot' },
      user: { id: userHandle, name: user.email, displayName: user.full_name || user.email },
      challenge,
      pubKeyCredParams: [
        { type: 'public-key', alg: -7 },
        { type: 'public-key', alg: -257 },
      ],
      timeout: 60000,
      attestation: 'none',
      authenticatorSelection: { userVerification: 'preferred', residentKey: 'preferred', requireResidentKey: false },
      excludeCredentials: exclude,
    });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}