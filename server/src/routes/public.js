// Public, non-secret information for the "For school IT" page (/schools):
// the IDs a school admin needs to approve Study Spot, and what it accesses.
import { Router } from 'express';
import { resolveSecret } from '../lib/secrets.js';
import { CONNECTOR_SCOPES } from './googleAuth.js';

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

export default router;
