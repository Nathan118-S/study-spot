import { Router } from 'express';
import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { pool } from '../db.js';
import {
  getUserByUsername, needsSecondFactor, normalizeUsername, pickSelfEditable, signSession, toPublicUser, updateUser,
} from '../lib/users.js';
import { requireAuth } from '../middleware/auth.js';
import { verifyPasskeyAssertion } from '../lib/passkeys.js';
import { b64uDecode, b64uEncode } from '../lib/webauthn.js';

const router = Router();

router.post('/register', async (req, res) => {
  try {
    const { password } = req.body || {};
    let username;
    try {
      username = normalizeUsername(req.body?.username);
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
    if (!password) return res.status(400).json({ error: 'Choose a password' });
    if (password.length < 8) return res.status(400).json({ error: 'Password must be at least 8 characters' });
    if (await getUserByUsername(username)) return res.status(400).json({ error: 'That username is taken' });

    const passwordHash = await bcrypt.hash(password, 10);
    let user;
    try {
      const ins = await pool.query(
        'INSERT INTO users (username, password_hash, role, is_verified) VALUES ($1, $2, $3, true) RETURNING *',
        [username, passwordHash, 'user']
      );
      user = ins.rows[0];
    } catch (e) {
      if (e.code === '23505') return res.status(400).json({ error: 'That username is taken' });
      throw e;
    }
    res.json({ access_token: signSession(user.id) });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/login', async (req, res) => {
  try {
    const username = String(req.body?.username || '').trim();
    const { password } = req.body || {};
    if (!username || !password) return res.status(400).json({ error: 'Username and password are required' });

    const user = await getUserByUsername(username);
    if (!user || !user.password_hash) return res.status(401).json({ error: 'Invalid username or password' });
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return res.status(401).json({ error: 'Invalid username or password' });
    if (user.role === 'disabled') return res.status(403).json({ error: 'This account has been disabled' });

    const mfaPending = needsSecondFactor(user);
    res.json({ access_token: signSession(user.id, { mfaPending }), mfa_required: mfaPending });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// --- Passwordless sign-in with a passkey -----------------------------------
// start issues a one-time challenge (kept in oauth_states for 5 minutes);
// finish finds the account by the passkey's credential ID, verifies the
// signature (with Face ID / Touch ID / PIN required), and signs the user in
// fully: a verified passkey is already two factors.

const PASSKEY_PURPOSE = 'passkey-login';
const PASSKEY_TTL_MS = 5 * 60 * 1000;

router.post('/passkey/start', async (req, res) => {
  try {
    await pool.query(
      "DELETE FROM oauth_states WHERE purpose = $1 AND created_at < now() - interval '10 minutes'",
      [PASSKEY_PURPOSE]
    );
    const challenge = b64uEncode(crypto.randomBytes(32));
    await pool.query('INSERT INTO oauth_states (state, purpose) VALUES ($1, $2)', [challenge, PASSKEY_PURPOSE]);

    // With a username we can name the credential (helps security keys that
    // aren't discoverable); without one the device shows its own picker.
    let allowCredentials = [];
    const username = String(req.body?.username || '').trim();
    if (username) {
      const user = await getUserByUsername(username);
      if (user?.passkey_cred_id) allowCredentials = [{ type: 'public-key', id: user.passkey_cred_id }];
    }
    res.json({ challenge, allowCredentials, timeout: 60000, userVerification: 'required' });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/passkey/finish', async (req, res) => {
  try {
    const { credentialId, clientDataJSON, userHandle } = req.body || {};
    if (!credentialId || !clientDataJSON) return res.status(400).json({ error: 'Missing passkey response' });

    let challenge;
    try {
      challenge = JSON.parse(new TextDecoder().decode(b64uDecode(clientDataJSON))).challenge;
    } catch {
      return res.status(400).json({ error: 'Invalid passkey response' });
    }
    // One-time use: delete the challenge whether or not verification succeeds.
    const stateRes = await pool.query(
      'DELETE FROM oauth_states WHERE state = $1 AND purpose = $2 RETURNING created_at',
      [challenge, PASSKEY_PURPOSE]
    );
    const issued = stateRes.rows[0];
    if (!issued || Date.now() - new Date(issued.created_at).getTime() > PASSKEY_TTL_MS) {
      return res.status(400).json({ error: 'This sign-in request expired. Please try again.' });
    }

    const userRes = await pool.query('SELECT * FROM users WHERE passkey_cred_id = $1', [credentialId]);
    const user = userRes.rows[0];
    if (!user) return res.status(400).json({ error: "This passkey isn't registered with Study Spot" });
    if (userHandle && userHandle !== b64uEncode(new TextEncoder().encode(user.id))) {
      return res.status(400).json({ error: "This passkey isn't registered with Study Spot" });
    }
    if (user.role === 'disabled') return res.status(403).json({ error: 'This account has been disabled' });

    const signCount = await verifyPasskeyAssertion(user, req.body, challenge, { requireUserVerification: true });
    await pool.query('UPDATE users SET passkey_counter = $1 WHERE id = $2', [signCount, user.id]);
    res.json({ access_token: signSession(user.id) });
  } catch (error) {
    res.status(error.status || 500).json({ error: error.message });
  }
});

// Signed in: change the password (needs the current one).
router.post('/change-password', requireAuth, async (req, res) => {
  try {
    const { currentPassword, newPassword } = req.body || {};
    if (!newPassword || newPassword.length < 8) return res.status(400).json({ error: 'New password must be at least 8 characters' });
    if (req.user.password_hash) {
      const ok = currentPassword && await bcrypt.compare(currentPassword, req.user.password_hash);
      if (!ok) return res.status(400).json({ error: 'Current password is incorrect' });
    }
    await updateUser(req.user.id, { password_hash: await bcrypt.hash(newPassword, 10) });
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.get('/me', requireAuth, (req, res) => {
  res.json({ ...toPublicUser(req.user), mfa_pending: !!req.mfaPending });
});

router.patch('/me', requireAuth, async (req, res) => {
  try {
    const patch = pickSelfEditable(req.body);
    if (req.body?.username !== undefined) {
      let username;
      try {
        username = normalizeUsername(req.body.username);
      } catch (e) {
        return res.status(400).json({ error: e.message });
      }
      if (username !== String(req.user.username).toLowerCase()) {
        const taken = await getUserByUsername(username);
        if (taken && taken.id !== req.user.id) return res.status(400).json({ error: 'That username is taken' });
        patch.username = username;
      }
    }
    if (!Object.keys(patch).length) return res.json(toPublicUser(req.user));
    const updated = await updateUser(req.user.id, patch);
    res.json(toPublicUser(updated));
  } catch (error) {
    if (error.code === '23505') return res.status(400).json({ error: 'That username is taken' });
    res.status(500).json({ error: error.message });
  }
});

router.post('/logout', (req, res) => {
  // Bearer tokens are stateless; the client discards the token locally.
  res.json({ ok: true });
});

export default router;
