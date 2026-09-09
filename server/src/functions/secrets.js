import { listSecretStatus, setSecret, clearSecret } from '../lib/secrets.js';
import { HttpError } from '../lib/httpError.js';
import { sendEmail } from '../lib/mailer.js';

// Status only (key, group, label, isSet, setAt) — never a value.
export async function adminListSecrets() {
  return { secrets: await listSecretStatus() };
}

// Body: { values: { KEY: "pasted value", ... } }. Each key is independent:
// a key that's already set is reported as a per-key failure rather than
// failing the whole batch, so pasting a full block of secrets still saves
// whichever ones are new.
export async function adminSetSecrets(caller, body) {
  const values = body?.values;
  if (!values || typeof values !== 'object') throw new HttpError(400, 'values is required');

  const results = {};
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined || value === null || String(value).trim() === '') continue;
    try {
      await setSecret(key, value);
      results[key] = { ok: true };
    } catch (e) {
      results[key] = { ok: false, error: e.message };
    }
  }
  return { results, secrets: await listSecretStatus() };
}

export async function adminClearSecret(caller, body) {
  const { key } = body || {};
  if (!key) throw new HttpError(400, 'key is required');
  await clearSecret(key);
  return { ok: true, secrets: await listSecretStatus() };
}

// Sends a real test email through whatever SMTP config is currently active
// (vault or server/.env) so an admin can confirm delivery — including the
// OTP/verification path new users go through at signup — actually works
// before relying on it.
export async function adminSendTestEmail(caller, body) {
  const to = (body?.to || '').trim();
  if (!to) throw new HttpError(400, 'A recipient email is required');
  await sendEmail({
    to,
    subject: 'Study Spot test email',
    body: [
      'This is a test email from the Study Spot admin panel.',
      '',
      'If you received this, outbound email (used for sign-up verification codes,',
      'password resets, assignment reminders, and admin notifications) is working.',
      '',
      '— Study Spot',
    ].join('\n'),
  });
  return { ok: true };
}
