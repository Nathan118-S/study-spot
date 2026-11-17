import { getUserById, verifySession, toPublicUser } from '../lib/users.js';

function extractToken(req) {
  const header = req.headers.authorization || '';
  if (header.startsWith('Bearer ')) return header.slice(7);
  return null;
}

// Populates req.user (full DB row, includes password_hash) when a valid
// session token is present. Does not reject unauthenticated requests —
// individual routes decide whether auth is required.
export async function attachUser(req, res, next) {
  try {
    const token = extractToken(req);
    if (!token) return next();
    const session = verifySession(token);
    if (!session) return next();
    const user = await getUserById(session.userId);
    if (!user) return next();
    // "Log out of all devices" invalidates every token issued before it.
    const invalidatedAt = Date.parse(user.data?.sessions_invalidated_at || '');
    if (Number.isFinite(invalidatedAt) && session.issuedAt * 1000 < invalidatedAt) return next();
    req.user = user;
    req.mfaPending = session.mfaPending;
    next();
  } catch (err) {
    next(err);
  }
}

// Reachable with a pending (pre-2FA) session or by a disabled account: just
// enough to show who is signed in and to complete the second factor.
const PENDING_ALLOWED = new Set([
  'GET /api/auth/me',
  'POST /api/functions/verify2fa',
  'POST /api/functions/passkeyLoginStart',
  'POST /api/functions/passkeyLoginFinish',
]);

export function requireAuth(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Unauthorized' });
  const route = `${req.method} ${req.baseUrl}${req.path}`.replace(/\/$/, '');
  if (req.user.role === 'disabled' && route !== 'GET /api/auth/me') {
    return res.status(403).json({ error: 'This account has been disabled' });
  }
  if (req.mfaPending && !PENDING_ALLOWED.has(route)) {
    return res.status(401).json({ error: 'Two-factor verification required', mfaRequired: true });
  }
  next();
}

export function requireAdmin(req, res, next) {
  requireAuth(req, res, () => {
    if (req.user.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
    next();
  });
}

export function publicUser(req) {
  return toPublicUser(req.user);
}
