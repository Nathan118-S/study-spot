// Verifies a WebAuthn assertion (passkey sign-in) against a user's stored
// credential. Used both for passwordless sign-in and for passkey 2FA.
import { HttpError } from './httpError.js';
import { b64uDecode, bufsEqual, concatBytes, crypto, parseAuthData } from './webauthn.js';

// Passkeys need a secure context: https, or http on localhost in development.
// The Android app's passkeys report "android:apk-key-hash:<base64url SHA-256
// of the signing certificate>"; those are accepted when the hash is one of
// ANDROID_APP_FINGERPRINTS (the same fingerprints assetlinks.json serves).
export function originAllowed(origin) {
  if (String(origin).startsWith('android:apk-key-hash:')) return androidOriginAllowed(origin);
  let url;
  try {
    url = new URL(origin);
  } catch {
    return false;
  }
  if (url.protocol === 'https:') return true;
  return url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
}

/** "AB:CD:…" (keytool / signingReport) -> base64url, as Android puts in the origin. */
export function fingerprintToApkKeyHash(fingerprint) {
  const hex = String(fingerprint).replace(/[^0-9a-f]/gi, '');
  return hex.length === 64 ? Buffer.from(hex, 'hex').toString('base64url') : null;
}

function androidOriginAllowed(origin) {
  const hash = String(origin).slice('android:apk-key-hash:'.length).replace(/=+$/, '');
  return String(process.env.ANDROID_APP_FINGERPRINTS || '')
    .split(',')
    .map(fingerprintToApkKeyHash)
    .some((h) => h && h === hash);
}

/**
 * The relying-party IDs a passkey from this origin may be scoped to. For the
 * website it's the page's host. An Android app has no host in its origin, so
 * use the server's public address (PASSKEY_RP_ID, else FRONTEND_URL's host).
 */
export function expectedRpIds(origin) {
  if (!String(origin).startsWith('android:')) return [new URL(origin).hostname];
  const ids = [];
  if (process.env.PASSKEY_RP_ID) ids.push(process.env.PASSKEY_RP_ID.trim());
  try {
    if (process.env.FRONTEND_URL) ids.push(new URL(process.env.FRONTEND_URL).hostname);
  } catch { /* ignore a malformed URL */ }
  return ids;
}

// WebAuthn ES256 signatures are ASN.1 DER; WebCrypto wants raw r||s.
function derToRawEcdsa(der, size = 32) {
  let i = 0;
  if (der[i++] !== 0x30) throw new HttpError(400, 'Bad signature encoding');
  if (der[i] & 0x80) i += 1 + (der[i] & 0x7f); else i += 1;
  const readInt = () => {
    if (der[i++] !== 0x02) throw new HttpError(400, 'Bad signature encoding');
    const len = der[i++];
    let bytes = der.slice(i, i + len);
    i += len;
    while (bytes.length > size && bytes[0] === 0) bytes = bytes.slice(1);
    const out = new Uint8Array(size);
    out.set(bytes, size - bytes.length);
    return out;
  };
  return concatBytes(readInt(), readInt());
}

/**
 * @param user                stored user row (passkey_pub_key, passkey_alg)
 * @param body                { authenticatorData, clientDataJSON, signature } (base64url)
 * @param expectedChallenge   the challenge this sign-in was issued
 * @param requireUserVerification  true for passwordless sign-in (Face ID / PIN)
 * @returns the authenticator's signature counter
 */
export async function verifyPasskeyAssertion(user, body, expectedChallenge, { requireUserVerification = false } = {}) {
  const { authenticatorData, clientDataJSON, signature } = body || {};
  if (!authenticatorData || !clientDataJSON || !signature) throw new HttpError(400, 'Missing assertion data');
  if (!user?.passkey_pub_key) throw new HttpError(400, 'No passkey registered');
  if (!expectedChallenge) throw new HttpError(400, 'No active sign-in challenge');

  let clientData;
  try {
    clientData = JSON.parse(new TextDecoder().decode(b64uDecode(clientDataJSON)));
  } catch {
    throw new HttpError(400, 'Invalid client data');
  }
  if (clientData.type !== 'webauthn.get') throw new HttpError(400, 'Wrong client data type');
  if (clientData.challenge !== expectedChallenge) throw new HttpError(400, 'Challenge mismatch');
  if (!originAllowed(clientData.origin || '')) throw new HttpError(400, 'Invalid origin');

  const authData = b64uDecode(authenticatorData);
  const parsed = parseAuthData(authData);
  if (!(parsed.flags & 0x01)) throw new HttpError(400, 'User presence required');
  if (requireUserVerification && !(parsed.flags & 0x04)) {
    throw new HttpError(400, 'Your device must verify you (Face ID, Touch ID or PIN) to sign in with a passkey');
  }
  let rpOk = false;
  for (const rpId of expectedRpIds(clientData.origin)) {
    const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rpId)));
    if (bufsEqual(parsed.rpIdHash, hash)) rpOk = true;
  }
  if (!rpOk) throw new HttpError(400, 'RP ID mismatch');

  const alg = Number(user.passkey_alg);
  let importAlg, verifyAlg, sig = b64uDecode(signature);
  if (alg === -7) {
    importAlg = { name: 'ECDSA', namedCurve: 'P-256' };
    verifyAlg = { name: 'ECDSA', hash: 'SHA-256' };
    sig = derToRawEcdsa(sig);
  } else if (alg === -257) {
    importAlg = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };
    verifyAlg = { name: 'RSASSA-PKCS1-v1_5' };
  } else {
    throw new HttpError(400, 'Unsupported passkey algorithm');
  }

  const key = await crypto.subtle.importKey('jwk', user.passkey_pub_key, importAlg, false, ['verify']);
  const clientDataHash = await crypto.subtle.digest('SHA-256', b64uDecode(clientDataJSON));
  const ok = await crypto.subtle.verify(verifyAlg, key, sig, concatBytes(authData, new Uint8Array(clientDataHash)));
  if (!ok) throw new HttpError(400, 'Signature verification failed');

  // A counter that doesn't increase can indicate a cloned authenticator
  // (synced passkeys always report 0, which is allowed).
  const stored = Number(user.passkey_counter) || 0;
  if (parsed.signCount !== 0 && parsed.signCount <= stored) throw new HttpError(400, 'Passkey counter check failed');
  return parsed.signCount;
}
