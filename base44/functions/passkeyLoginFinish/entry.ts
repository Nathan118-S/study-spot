import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { b64uDecode, bufsEqual, concatBytes, parseAuthData } from '../../shared/webauthn.ts';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const body = await req.json();
    const { authenticatorData, clientDataJSON, signature } = body || {};
    if (!authenticatorData || !clientDataJSON || !signature) {
      return Response.json({ error: 'Missing assertion data' }, { status: 400 });
    }

    const d = user.data || {};
    if (!d.passkey_pub_key) {
      return Response.json({ error: 'No passkey registered' }, { status: 400 });
    }
    if (!d.webauthn_challenge) {
      return Response.json({ error: 'No active login challenge' }, { status: 400 });
    }

    const clientData = JSON.parse(new TextDecoder().decode(b64uDecode(clientDataJSON)));
    if (clientData.type !== 'webauthn.get') {
      return Response.json({ error: 'Wrong client data type' }, { status: 400 });
    }
    if (clientData.challenge !== d.webauthn_challenge) {
      return Response.json({ error: 'Challenge mismatch' }, { status: 400 });
    }
    if (!/^https:\/\//i.test(clientData.origin || '')) {
      return Response.json({ error: 'Invalid origin' }, { status: 400 });
    }

    const rpId = new URL(clientData.origin).hostname;
    const authData = b64uDecode(authenticatorData);
    const parsed = parseAuthData(authData);
    if (!(parsed.flags & 0x01)) {
      return Response.json({ error: 'User presence required' }, { status: 400 });
    }
    const expectedRpIdHash = new Uint8Array(
      await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rpId))
    );
    if (!bufsEqual(parsed.rpIdHash, expectedRpIdHash)) {
      return Response.json({ error: 'RP ID mismatch' }, { status: 400 });
    }

    const alg = d.passkey_alg;
    let importAlg, verifyAlg;
    if (alg === -7) {
      importAlg = { name: 'ECDSA', namedCurve: 'P-256' };
      verifyAlg = { name: 'ECDSA', hash: 'SHA-256' };
    } else if (alg === -257) {
      importAlg = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };
      verifyAlg = { name: 'RSASSA-PKCS1-v1_5' };
    } else {
      return Response.json({ error: 'Unsupported passkey algorithm' }, { status: 400 });
    }

    const cryptoKey = await crypto.subtle.importKey('jwk', d.passkey_pub_key, importAlg, false, ['verify']);
    const clientDataHash = await crypto.subtle.digest('SHA-256', b64uDecode(clientDataJSON));
    const signedData = concatBytes(authData, new Uint8Array(clientDataHash));
    const ok = await crypto.subtle.verify(verifyAlg, cryptoKey, b64uDecode(signature), signedData);
    if (!ok) {
      return Response.json({ error: 'Signature verification failed' }, { status: 400 });
    }

    await base44.auth.updateMe({ webauthn_challenge: '', passkey_counter: parsed.signCount });
    return Response.json({ ok: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}