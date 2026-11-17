import { pool } from '../db.js';
import { signSession, updateUser } from '../lib/users.js';
import { originAllowed, verifyPasskeyAssertion } from '../lib/passkeys.js';
import { verifyTotp, generateSecret } from '../lib/totp.js';
import { getMethods, withMethod, withoutMethod, displayName } from '../lib/twofa.js';
import { sendEmail } from '../lib/mailer.js';
import { HttpError } from '../lib/httpError.js';
import {
  b64uDecode, b64uEncode, concatBytes, bufsEqual, decodeCbor, parseAuthData, coseToJwk, crypto,
} from '../lib/webauthn.js';

export async function setup2fa(user) {
  const secret = generateSecret();
  const label = encodeURIComponent(user.email || 'user');
  const issuer = encodeURIComponent('Study Spot');
  const otpauth_url = `otpauth://totp/StudySpot:${label}?secret=${secret}&issuer=${issuer}&algorithm=SHA1&digits=6&period=30`;
  return { secret, otpauth_url };
}

export async function enable2fa(user, body) {
  const { code, secret } = body || {};
  if (!code || !secret) throw new HttpError(400, 'Code and secret required');
  const ok = await verifyTotp(secret, code);
  if (!ok) throw new HttpError(400, 'Invalid code');
  const methods = withMethod(getMethods(user), 'totp');
  await updateUser(user.id, { totp_secret: secret, twofa_enabled: true, twofa_method: 'totp', twofa_methods: methods });
  return { ok: true };
}

export async function disable2fa(user, body) {
  const { code } = body || {};
  if (!code) throw new HttpError(400, 'Code required');
  const secret = user.totp_secret;
  if (!secret) throw new HttpError(400, '2FA not configured');
  const ok = await verifyTotp(secret, code);
  if (!ok) throw new HttpError(400, 'Invalid code');
  return { ok: true };
}

export async function verify2fa(user, body) {
  const { code } = body || {};
  if (!code) throw new HttpError(400, 'Code required');
  const secret = user.totp_secret;
  if (!secret) throw new HttpError(400, '2FA not configured');
  const ok = await verifyTotp(secret, code);
  if (!ok) throw new HttpError(400, 'Invalid code');
  // Second factor passed: upgrade a pending sign-in to a full session.
  return { ok: true, access_token: signSession(user.id) };
}

export async function email2faSend(user) {
  const code = String(Math.floor(100000 + Math.random() * 900000));
  const expires = Date.now() + 10 * 60 * 1000;
  await updateUser(user.id, { email_2fa_code: code, email_2fa_expires: expires });
  await sendEmail({
    to: user.email,
    subject: 'Your Study Spot verification code',
    body: `Hi ${displayName(user)},\n\nYour verification code is ${code}. It expires in 10 minutes. If you didn't request this, you can ignore this email.\n\n— Study Spot`,
  });
  return { ok: true };
}

export async function email2faVerify(user, body) {
  const { code, enable } = body || {};
  if (!code) throw new HttpError(400, 'Code required');
  const d = user.data || {};
  if (!d.email_2fa_code || !d.email_2fa_expires) throw new HttpError(400, 'No code sent. Request a new one.');
  if (Date.now() > Number(d.email_2fa_expires)) throw new HttpError(400, 'Code expired. Request a new one.');
  if (String(code) !== String(d.email_2fa_code)) throw new HttpError(400, 'Invalid code');

  const update = { email_2fa_code: '', email_2fa_expires: 0 };
  if (enable) {
    const methods = withMethod(getMethods(user), 'email');
    update.twofa_method = 'email';
    update.twofa_enabled = true;
    update.twofa_methods = methods;
  }
  await updateUser(user.id, update);
  return { ok: true, access_token: signSession(user.id) };
}

export async function remove2faMethod(user, body) {
  const { method } = body || {};
  if (!['totp', 'email', 'passkey'].includes(method)) throw new HttpError(400, 'Invalid method');
  const current = getMethods(user);
  if (!current.includes(method)) throw new HttpError(400, 'Method not enabled');
  const methods = withoutMethod(current, method);
  const update = { twofa_methods: methods };
  if (method === 'totp') update.totp_secret = null;
  if (method === 'passkey') {
    update.passkey_cred_id = null;
    update.passkey_pub_key = null;
    update.passkey_alg = null;
    update.passkey_counter = 0;
  }
  if (methods.length === 0) {
    update.twofa_enabled = false;
    update.twofa_method = null;
  } else {
    update.twofa_enabled = true;
    update.twofa_method = methods.includes(user.twofa_method) ? user.twofa_method : methods[0];
  }
  await updateUser(user.id, update);
  return { ok: true, methods };
}

export async function passkeyRegisterStart(user) {
  const challenge = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
  const userHandle = b64uEncode(new TextEncoder().encode(user.id));
  await updateUser(user.id, { webauthn_challenge: challenge });

  const exclude = [];
  if (user.passkey_cred_id) exclude.push({ type: 'public-key', id: user.passkey_cred_id });

  return {
    rp: { name: 'Study Spot' },
    user: { id: userHandle, name: user.email, displayName: user.full_name || user.email },
    challenge,
    pubKeyCredParams: [
      { type: 'public-key', alg: -7 },
      { type: 'public-key', alg: -257 },
    ],
    timeout: 60000,
    attestation: 'none',
    // Discoverable + user-verifying, so the passkey also works for passwordless sign-in.
    authenticatorSelection: { userVerification: 'required', residentKey: 'required', requireResidentKey: true },
    excludeCredentials: exclude,
  };
}

export async function passkeyRegisterFinish(user, body) {
  const { credentialId, attestationObject, clientDataJSON } = body || {};
  if (!credentialId || !attestationObject || !clientDataJSON) throw new HttpError(400, 'Missing credential data');
  if (!user.webauthn_challenge) throw new HttpError(400, 'No active registration challenge');

  const clientData = JSON.parse(new TextDecoder().decode(b64uDecode(clientDataJSON)));
  if (clientData.type !== 'webauthn.create') throw new HttpError(400, 'Wrong client data type');
  if (clientData.challenge !== user.webauthn_challenge) throw new HttpError(400, 'Challenge mismatch');
  if (!originAllowed(clientData.origin || '')) throw new HttpError(400, 'Invalid origin');

  const attObj = decodeCbor(b64uDecode(attestationObject));
  const authData = attObj.get('authData');
  const parsed = parseAuthData(authData);
  if (!parsed.attested) throw new HttpError(400, 'No attested credential in response');
  if (!(parsed.flags & 0x01)) throw new HttpError(400, 'User presence required');

  const cose = decodeCbor(parsed.attested.credPubKey);
  const { jwk, alg, importAlg } = coseToJwk(cose);
  await crypto.subtle.importKey('jwk', jwk, importAlg, false, ['verify']);

  const methods = withMethod(getMethods(user), 'passkey');
  await updateUser(user.id, {
    passkey_cred_id: credentialId,
    passkey_pub_key: jwk,
    passkey_alg: alg,
    passkey_counter: parsed.signCount,
    webauthn_challenge: '',
    twofa_method: 'passkey',
    twofa_enabled: true,
    twofa_methods: methods,
  });
  return { ok: true };
}

export async function passkeyLoginStart(user) {
  if (!user.passkey_cred_id) throw new HttpError(400, 'No passkey registered');
  const challenge = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
  await updateUser(user.id, { webauthn_challenge: challenge });
  return { challenge, allowCredentials: [{ type: 'public-key', id: user.passkey_cred_id }] };
}

export async function passkeyLoginFinish(user, body) {
  const signCount = await verifyPasskeyAssertion(user, body, user.webauthn_challenge);
  await updateUser(user.id, { webauthn_challenge: '', passkey_counter: signCount });
  return { ok: true, access_token: signSession(user.id) };
}
