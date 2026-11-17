import { Router } from 'express';
import { isElevated } from '../lib/adminElevation.js';
import * as adminUsers from '../functions/adminUsers.js';
import { requireAuth, requireAdmin } from '../middleware/auth.js';
import { HttpError } from '../lib/httpError.js';
import * as twofa from '../functions/twofa.js';
import * as admin from '../functions/admin.js';
import * as blackboard from '../functions/blackboard.js';
import * as google from '../functions/google.js';
import { getLeaderboard } from '../functions/leaderboard.js';
import * as secrets from '../functions/secrets.js';
import * as deploy from '../functions/deploy.js';

const router = Router();
router.use(requireAuth);

// name -> { handler(user, body), admin? }
const HANDLERS = {
  setup2fa: { handler: twofa.setup2fa },
  enable2fa: { handler: twofa.enable2fa },
  disable2fa: { handler: twofa.disable2fa },
  verify2fa: { handler: twofa.verify2fa },
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
  adminElevate: { handler: adminUsers.adminElevate, admin: true },
  adminElevateStart: { handler: adminUsers.adminElevateStart, admin: true },
  adminSendNotification: { handler: admin.adminSendNotification, admin: true },

  // Per-account management: all need the 2FA step-up.
  adminGetUser: { handler: adminUsers.adminGetUser, admin: true, elevated: true },
  adminUpdateUser: { handler: adminUsers.adminUpdateUser, admin: true, elevated: true },
  adminUpdateUserSettings: { handler: adminUsers.adminUpdateUserSettings, admin: true, elevated: true },
  adminUpdateUserRole: { handler: admin.adminUpdateUserRole, admin: true, elevated: true },
  adminDeleteUser: { handler: admin.adminDeleteUser, admin: true, elevated: true },
  adminResetOnboarding: { handler: admin.adminResetOnboarding, admin: true, elevated: true },
  adminReset2fa: { handler: admin.adminReset2fa, admin: true, elevated: true },
  adminRemove2faMethod: { handler: adminUsers.adminRemove2faMethod, admin: true, elevated: true },
  adminRestoreStreak: { handler: admin.adminRestoreStreak, admin: true, elevated: true },
  adminVerifyUser: { handler: admin.adminVerifyUser, admin: true, elevated: true },
  adminResetPassword: { handler: admin.adminResetPassword, admin: true, elevated: true },
  adminSetPassword: { handler: adminUsers.adminSetPassword, admin: true, elevated: true },
  adminSignOutUser: { handler: adminUsers.adminSignOutUser, admin: true, elevated: true },
  adminDisconnectUser: { handler: adminUsers.adminDisconnectUser, admin: true, elevated: true },
  adminRemoveDevice: { handler: adminUsers.adminRemoveDevice, admin: true, elevated: true },
  adminListRecords: { handler: adminUsers.adminListRecords, admin: true, elevated: true },
  adminUpdateRecord: { handler: adminUsers.adminUpdateRecord, admin: true, elevated: true },
  adminDeleteRecord: { handler: adminUsers.adminDeleteRecord, admin: true, elevated: true },
  adminClearRecords: { handler: adminUsers.adminClearRecords, admin: true, elevated: true },
  adminSendTestNotification: { handler: admin.adminSendTestNotification, admin: true, elevated: true },
  adminMergeAccounts: { handler: admin.adminMergeAccounts, admin: true, elevated: true },

  adminListSecrets: { handler: secrets.adminListSecrets, admin: true },
  adminSetSecrets: { handler: secrets.adminSetSecrets, admin: true },
  adminClearSecret: { handler: secrets.adminClearSecret, admin: true },

  adminDeployLatest: { handler: deploy.adminDeployLatest, admin: true },
  adminDeployStatus: { handler: deploy.adminDeployStatus, admin: true },
};

router.post('/:name', async (req, res) => {
  const entry = HANDLERS[req.params.name];
  if (!entry) return res.status(404).json({ error: 'Unknown function' });
  if (entry.admin && req.user.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
  // Opening or changing anyone's account needs a recent 2FA confirmation.
  if (entry.elevated && !isElevated(req)) {
    return res.status(403).json({ error: 'Confirm it\'s you with two-factor authentication', elevationRequired: true });
  }
  try {
    const result = await entry.handler(req.user, req.body || {}, req);
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
