// Public, non-secret information for the "For school IT" page (/schools):
// the IDs a school admin needs to approve Study Spot, and what it accesses.
import { Router } from 'express';
import { resolveSecret } from '../lib/secrets.js';
import { CONNECTOR_SCOPES } from './googleAuth.js';
import { guardianSummary } from '../functions/guardian.js';

const router = Router();

const SCOPE_INFO = {
  'https://www.googleapis.com/auth/calendar.readonly':
    'Read the student’s Google Calendar to find assignments and due dates. Read-only.',
  'https://www.googleapis.com/auth/classroom.courses.readonly':
    'See the list of Google Classroom classes the student is in. Read-only.',
  'https://www.googleapis.com/auth/classroom.coursework.me':
    'Read the student’s own assignments and due dates. Only if the student turns on “Mark Classroom assignments done”, it turns in the student’s own work when they mark it done in Study Spot. It can’t see other students’ work or grades.',
};

router.get('/school-info', async (req, res) => {
  const [googleClientId, blackboardApplicationId] = await Promise.all([
    resolveSecret('GOOGLE_CLIENT_ID'),
    resolveSecret('BLACKBOARD_APPLICATION_ID'),
  ]);
  const scopes = [...new Set(Object.values(CONNECTOR_SCOPES).flatMap((s) => s.split(' ')))];
  res.set('Cache-Control', 'public, max-age=300');
  res.json({
    googleClientId: googleClientId || null,
    blackboardApplicationId: blackboardApplicationId || null,
    googleScopes: scopes.map((scope) => ({ scope, purpose: SCOPE_INFO[scope] || '' })),
  });
});

// A small in-memory, per-client limit on parent-view lookups. Link tokens are
// 256-bit random values, so this only keeps the database from being hammered.
const GUARDIAN_WINDOW_MS = 60_000;
const GUARDIAN_MAX_PER_WINDOW = 60;
const guardianHits = new Map();
function guardianRateLimited(req) {
  const key = String(req.get('cf-connecting-ip') || req.get('x-forwarded-for') || req.ip || '').split(',')[0].trim();
  const now = Date.now();
  if (guardianHits.size > 10_000) {
    for (const [k, v] of guardianHits) if (v.reset <= now) guardianHits.delete(k);
  }
  const entry = guardianHits.get(key);
  if (!entry || entry.reset <= now) {
    guardianHits.set(key, { count: 1, reset: now + GUARDIAN_WINDOW_MS });
    return false;
  }
  entry.count += 1;
  return entry.count > GUARDIAN_MAX_PER_WINDOW;
}

// The read-only weekly summary a parent or guardian sees at /parent/<token>.
router.get('/guardian/:token', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.set('X-Robots-Tag', 'noindex, nofollow');
  res.set('Referrer-Policy', 'no-referrer');
  if (guardianRateLimited(req)) {
    return res.status(429).json({ error: 'Too many requests. Try again in a minute.' });
  }
  try {
    const summary = await guardianSummary(req.params.token);
    if (!summary) return res.status(404).json({ error: 'This link was turned off.', off: true });
    res.json(summary);
  } catch (err) {
    console.error('guardian summary failed', err);
    res.status(500).json({ error: 'Couldn’t load this summary right now.' });
  }
});

export default router;
