import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { b64uDecode, decodeCbor, parseAuthData, coseToJwk, bufsEqual } from '../../shared/webauthn.ts';
import { getMethods, withMethod } from '../../shared/twofa.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { credentialId, attestationObject, clientDataJSON } = body || {};
    if (!credentialId || !attestationObject || !clientDataJSON) {
      return Response.json({ error: 'Missing credential data' }, { status: 400 });
    }

    const d = user.data || {};
    if (!d.webauthn_challenge) {
      return Response.json({ error: 'No active registration challenge' }, { status: 400 });
    }

    const clientData = JSON.parse(new TextDecoder().decode(b64uDecode(clientDataJSON)));
    if (clientData.type !== 'webauthn.create') {
      return Response.json({ error: 'Wrong client data type' }, { status: 400 });
    }
    if (clientData.challenge !== d.webauthn_challenge) {
      return Response.json({ error: 'Challenge mismatch' }, { status: 400 });
    }
    if (!/^https:\/\//i.test(clientData.origin || '')) {
      return Response.json({ error: 'Invalid origin' }, { status: 400 });
    }

    const attObj = decodeCbor(b64uDecode(attestationObject));
    const authData = attObj.get('authData');
    const parsed = parseAuthData(authData);
    if (!parsed.attested) {
      return Response.json({ error: 'No attested credential in response' }, { status: 400 });
    }
    if (!(parsed.flags & 0x01)) {
      return Response.json({ error: 'User presence required' }, { status: 400 });
    }

    const cose = decodeCbor(parsed.attested.credPubKey);
    const { jwk, alg, importAlg } = coseToJwk(cose);

    // Validate the key is importable.
    await crypto.subtle.importKey('jwk', jwk, importAlg, false, ['verify']);

    const methods = withMethod(getMethods(user), 'passkey');
    await base44.auth.updateMe({
      passkey_cred_id: credentialId,
      passkey_pub_key: jwk,
      passkey_alg: alg,
      passkey_counter: parsed.signCount,
      webauthn_challenge: '',
      twofa_method: 'passkey',
      twofa_enabled: true,
      twofa_methods: methods,
    });

    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}