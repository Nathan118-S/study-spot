// Encrypted, write-once vault for third-party integration credentials
// (Google OAuth, Blackboard OAuth, Apple Push). An admin pastes each value once
// through the admin panel; after that it is encrypted at rest and there is
// no API path that ever returns the plaintext again — only resolveSecret()
// (used internally by the server itself) can decrypt it. To change a value,
// an admin must clear it first and paste the new one.
import crypto from 'node:crypto';
import { pool } from '../db.js';
import { HttpError } from './httpError.js';

// key -> { group, label } — the only keys the vault will accept. Anything
// not listed here is rejected by setSecret/clearSecret.
export const KNOWN_SECRETS = [
  { key: 'GOOGLE_CLIENT_ID', group: 'Google', label: 'Client ID' },
  { key: 'GOOGLE_CLIENT_SECRET', group: 'Google', label: 'Client secret' },
  { key: 'GOOGLE_CONNECT_REDIRECT_URI', group: 'Google', label: 'Connector redirect URI' },
  { key: 'BLACKBOARD_CLIENT_ID', group: 'Blackboard Learn', label: 'Client ID' },
  { key: 'BLACKBOARD_CLIENT_SECRET', group: 'Blackboard Learn', label: 'Client secret' },
  { key: 'BLACKBOARD_REDIRECT_URI', group: 'Blackboard Learn', label: 'Redirect URI' },
  { key: 'BLACKBOARD_APPLICATION_ID', group: 'Blackboard Learn', label: 'Application ID (shown to schools on /schools)' },
  { key: 'FCM_SERVICE_ACCOUNT', group: 'Firebase (Android notifications)', label: 'Service account JSON' },
  { key: 'APNS_KEY', group: 'Apple Push (iOS notifications)', label: 'Auth key (.p8 contents)' },
  { key: 'APNS_KEY_ID', group: 'Apple Push (iOS notifications)', label: 'Key ID' },
  { key: 'APNS_TEAM_ID', group: 'Apple Push (iOS notifications)', label: 'Team ID' },
];
const KNOWN_KEYS = new Set(KNOWN_SECRETS.map((s) => s.key));

let cachedKey = null;
function encryptionKey() {
  if (cachedKey) return cachedKey;
  const material = process.env.ENCRYPTION_KEY || process.env.SESSION_SECRET;
  if (!material) {
    throw new Error('ENCRYPTION_KEY (or SESSION_SECRET) must be set to use the secrets vault');
  }
  cachedKey = crypto.scryptSync(material, 'study-spot-app-secrets-v1', 32);
  return cachedKey;
}

/** Encrypts a value with the server's key (same scheme as the secrets vault). */
export function encryptValue(value) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(String(value), 'utf8'), cipher.final()]);
  return { iv, ciphertext, authTag: cipher.getAuthTag() };
}

export function decryptValue({ iv, ciphertext, authTag }) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(), iv);
  decipher.setAuthTag(authTag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

export function listSecretMetadata() {
  return KNOWN_SECRETS;
}

// Status only — never the value. Safe to expose to the admin UI.
export async function listSecretStatus() {
  const res = await pool.query('SELECT key, set_at FROM app_secrets');
  const byKey = new Map(res.rows.map((r) => [r.key, r]));
  return KNOWN_SECRETS.map((s) => ({
    ...s,
    isSet: byKey.has(s.key),
    setAt: byKey.get(s.key)?.set_at || null,
  }));
}

// Write-once: rejects if the key is already set. Clear it first to replace it.
export async function setSecret(key, value) {
  if (!KNOWN_KEYS.has(key)) throw new HttpError(400, `Unknown secret: ${key}`);
  if (!value || !String(value).trim()) throw new HttpError(400, `${key} cannot be empty`);

  const existing = await pool.query('SELECT 1 FROM app_secrets WHERE key = $1', [key]);
  if (existing.rows.length) {
    throw new HttpError(409, `${key} is already set. Clear it first to replace it.`);
  }

  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(), iv);
  const ciphertext = Buffer.concat([cipher.update(String(value).trim(), 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();

  await pool.query(
    `INSERT INTO app_secrets (key, iv, ciphertext, auth_tag) VALUES ($1, $2, $3, $4)`,
    [key, iv, ciphertext, authTag]
  );
}

export async function clearSecret(key) {
  if (!KNOWN_KEYS.has(key)) throw new HttpError(400, `Unknown secret: ${key}`);
  await pool.query('DELETE FROM app_secrets WHERE key = $1', [key]);
}

// Internal use only (server-side integration code) — this is the one place
// a plaintext value ever exists after being set, and it is never sent back
// over the API. Falls back to the matching environment variable so an
// operator can still configure things the old way via server/.env.
export async function resolveSecret(key) {
  const res = await pool.query('SELECT iv, ciphertext, auth_tag FROM app_secrets WHERE key = $1', [key]);
  const row = res.rows[0];
  if (row) {
    try {
      const decipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(), row.iv);
      decipher.setAuthTag(row.auth_tag);
      const plain = Buffer.concat([decipher.update(row.ciphertext), decipher.final()]);
      return plain.toString('utf8');
    } catch {
      // Decryption failed (e.g. ENCRYPTION_KEY changed) — fall back to env.
    }
  }
  return process.env[key] || '';
}
