import { listSecretStatus, setSecret, clearSecret } from '../lib/secrets.js';
import { HttpError } from '../lib/httpError.js';

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
