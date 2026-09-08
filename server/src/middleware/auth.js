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
    const userId = verifySession(token);
    if (!userId) return next();
    const user = await getUserById(userId);
    if (user) req.user = user;
    next();
  } catch (err) {
    next(err);
  }
}

export function requireAuth(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Unauthorized' });
  next();
}

export function requireAdmin(req, res, next) {
  if (!req.user) return res.status(401).json({ error: 'Unauthorized' });
  if (req.user.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
  next();
}

export function publicUser(req) {
  return toPublicUser(req.user);
}
