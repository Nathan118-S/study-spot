// Admin step-up: before an admin can open or change anyone's account, they
// confirm a second factor (authenticator code or passkey). That issues a
// short-lived elevation token tied to the admin and to their current session;
// clients send it back in the X-Admin-Elevation header.
import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';

export const ELEVATION_MINUTES = 15;
const PURPOSE = 'admin-elevation';

const sessionFingerprint = (bearer) =>
  crypto.createHash('sha256').update(String(bearer || '')).digest('base64url').slice(0, 22);

export function bearerOf(req) {
  const h = req.headers.authorization || '';
  return h.startsWith('Bearer ') ? h.slice(7) : '';
}

export function signElevation(userId, bearer) {
  return jwt.sign({ sub: userId, purpose: PURPOSE, sid: sessionFingerprint(bearer) }, process.env.SESSION_SECRET, {
    expiresIn: `${ELEVATION_MINUTES}m`,
  });
}

/** True if the request carries a valid elevation for this admin and session. */
export function isElevated(req) {
  const token = req.headers['x-admin-elevation'];
  if (!token || !req.user) return false;
  try {
    const p = jwt.verify(String(token), process.env.SESSION_SECRET);
    return p.purpose === PURPOSE && p.sub === req.user.id && p.sid === sessionFingerprint(bearerOf(req));
  } catch {
    return false;
  }
}
