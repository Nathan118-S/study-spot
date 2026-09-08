import { Router } from 'express';
import { requireAuth, requireAdmin } from '../middleware/auth.js';
import { HttpError } from '../lib/httpError.js';
import * as twofa from '../functions/twofa.js';
import * as admin from '../functions/admin.js';
import * as blackboard from '../functions/blackboard.js';
import * as google from '../functions/google.js';
import { getLeaderboard } from '../functions/leaderboard.js';
import { sendOtpEmailFor } from './authHelpers.js';

const router = Router();
router.use(requireAuth);

// name -> { handler(user, body), admin? }
const HANDLERS = {
  setup2fa: { handler: twofa.setup2fa },
  enable2fa: { handler: twofa.enable2fa },
  disable2fa: { handler: twofa.disable2fa },
  verify2fa: { handler: twofa.verify2fa },
  email2faSend: { handler: twofa.email2faSend },
  email2faVerify: { handler: twofa.email2faVerify },
  remove2faMethod: { handler: twofa.remove2faMethod },
  passkeyRegisterStart: { handler: twofa.passkeyRegisterStart },
  passkeyRegisterFinish: { handler: twofa.passkeyRegisterFinish },
  passkeyLoginStart: { handler: twofa.passkeyLoginStart },
  passkeyLoginFinish: { handler: twofa.passkeyLoginFinish },

  getLeaderboard: { handler: getLeaderboard },

  blackboardAuthUrl: { handler: blackboard.blackboardAuthUrl },
  blackboardCallback: { handler: blackboard.blackboardCallback },
  disconnectBlackboard: { handler: blackboard.disconnectBlackboard },
  syncBlackboard: { handler: blackboard.syncBlackboard },

  checkGoogleConnections: { handler: google.checkGoogleConnections },
  syncGoogleCalendar: { handler: google.syncGoogleCalendar },
  syncGoogleClassroom: { handler: google.syncGoogleClassroom },
  getClassroomAttachments: { handler: google.getClassroomAttachments },
  syncCompletionToClassroom: { handler: google.syncCompletionToClassroom },

  adminListUsers: { handler: admin.adminListUsers, admin: true },
  adminUpdateUser: { handler: admin.adminUpdateUser, admin: true },
  adminUpdateUserRole: { handler: admin.adminUpdateUserRole, admin: true },
  adminDeleteUser: { handler: admin.adminDeleteUser, admin: true },
  adminResetOnboarding: { handler: admin.adminResetOnboarding, admin: true },
  adminReset2fa: { handler: admin.adminReset2fa, admin: true },
  adminRestoreStreak: { handler: admin.adminRestoreStreak, admin: true },
  adminVerifyUser: { handler: (user, body) => admin.adminVerifyUser(user, body, sendOtpEmailFor), admin: true },
  adminSendNotification: { handler: admin.adminSendNotification, admin: true },
  adminSendTestNotification: { handler: admin.adminSendTestNotification, admin: true },
  adminMergeAccounts: { handler: admin.adminMergeAccounts, admin: true },
};

router.post('/:name', async (req, res) => {
  const entry = HANDLERS[req.params.name];
  if (!entry) return res.status(404).json({ error: 'Unknown function' });
  if (entry.admin && req.user.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
  try {
    const result = await entry.handler(req.user, req.body || {});
    res.json(result);
  } catch (error) {
    if (error instanceof HttpError) {
      const payload = { error: error.message };
      if (error.notConnected) payload.notConnected = true;
      return res.status(error.status).json(payload);
    }
    res.status(500).json({ error: error.message });
  }
});

export default router;
