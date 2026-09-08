import { Router } from 'express';
import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { pool } from '../db.js';
import { getUserByEmail, signSession, toPublicUser, updateUser } from '../lib/users.js';
import { requireAuth } from '../middleware/auth.js';
import { sendEmail } from '../lib/mailer.js';
import { sendOtpEmailFor } from './authHelpers.js';

const router = Router();
const sendOtpEmail = sendOtpEmailFor;

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

router.post('/register', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) return res.status(400).json({ error: 'Email and password are required' });
    if (password.length < 8) return res.status(400).json({ error: 'Password must be at least 8 characters' });

    const existing = await getUserByEmail(email);
    if (existing && existing.is_verified) {
      return res.status(400).json({ error: 'An account with that email already exists' });
    }

    const passwordHash = await bcrypt.hash(password, 10);
    if (existing) {
      await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, existing.id]);
    } else {
      await pool.query(
        'INSERT INTO users (email, password_hash, role, is_verified) VALUES ($1, $2, $3, false)',
        [email.toLowerCase(), passwordHash, 'user']
      );
    }
    await sendOtpEmail(email.toLowerCase(), null);
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/resend-otp', async (req, res) => {
  try {
    const email = (req.body?.email || '').toLowerCase();
    if (!email) return res.status(400).json({ error: 'Email required' });
    await sendOtpEmail(email, null);
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/verify-otp', async (req, res) => {
  try {
    const email = (req.body?.email || '').toLowerCase();
    const { otpCode } = req.body || {};
    if (!email || !otpCode) return res.status(400).json({ error: 'Email and code are required' });

    const otpRes = await pool.query(
      'SELECT * FROM email_otps WHERE email = $1 AND purpose = $2 ORDER BY created_at DESC LIMIT 1',
      [email, 'register']
    );
    const otp = otpRes.rows[0];
    if (!otp) return res.status(400).json({ error: 'No code found. Request a new one.' });
    if (new Date(otp.expires_at) < new Date()) return res.status(400).json({ error: 'Code expired. Request a new one.' });
    const ok = await bcrypt.compare(String(otpCode), otp.code_hash);
    if (!ok) return res.status(400).json({ error: 'Invalid code' });

    const user = await getUserByEmail(email);
    if (!user) return res.status(400).json({ error: 'Account not found' });
    await pool.query('UPDATE users SET is_verified = true WHERE id = $1', [user.id]);
    await pool.query('DELETE FROM email_otps WHERE email = $1 AND purpose = $2', [email, 'register']);

    res.json({ access_token: signSession(user.id) });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/login', async (req, res) => {
  try {
    const email = (req.body?.email || '').toLowerCase();
    const { password } = req.body || {};
    if (!email || !password) return res.status(400).json({ error: 'Email and password are required' });

    const user = await getUserByEmail(email);
    if (!user || !user.password_hash) return res.status(401).json({ error: 'Invalid email or password' });
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return res.status(401).json({ error: 'Invalid email or password' });
    if (user.role === 'disabled') return res.status(403).json({ error: 'This account has been disabled' });
    if (!user.is_verified) return res.status(403).json({ error: 'Please verify your email before logging in' });

    res.json({ access_token: signSession(user.id) });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/forgot-password', async (req, res) => {
  try {
    const email = (req.body?.email || '').toLowerCase();
    const user = email ? await getUserByEmail(email) : null;
    if (user) {
      const token = crypto.randomBytes(32).toString('hex');
      const expiresAt = new Date(Date.now() + 60 * 60 * 1000);
      await pool.query(
        'INSERT INTO password_resets (user_id, token_hash, expires_at) VALUES ($1, $2, $3)',
        [user.id, hashToken(token), expiresAt]
      );
      const link = `${process.env.FRONTEND_URL}/reset-password?token=${token}`;
      await sendEmail({
        to: user.email,
        subject: 'Reset your Study Spot password',
        body: `Hi,\n\nUse the link below to reset your password. It expires in 1 hour.\n\n${link}\n\nIf you didn't request this, you can ignore this email.\n\n— Study Spot`,
      });
    }
    // Always respond ok — do not reveal whether the email is registered.
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/reset-password', async (req, res) => {
  try {
    const { resetToken, newPassword } = req.body || {};
    if (!resetToken || !newPassword) return res.status(400).json({ error: 'Missing token or password' });
    if (newPassword.length < 8) return res.status(400).json({ error: 'Password must be at least 8 characters' });

    const tokenHash = hashToken(resetToken);
    const rowRes = await pool.query(
      'SELECT * FROM password_resets WHERE token_hash = $1 AND used_at IS NULL',
      [tokenHash]
    );
    const row = rowRes.rows[0];
    if (!row || new Date(row.expires_at) < new Date()) {
      return res.status(400).json({ error: 'This reset link is invalid or has expired' });
    }
    const passwordHash = await bcrypt.hash(newPassword, 10);
    await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, row.user_id]);
    await pool.query('UPDATE password_resets SET used_at = now() WHERE id = $1', [row.id]);
    res.json({ ok: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.get('/me', requireAuth, (req, res) => {
  res.json(toPublicUser(req.user));
});

router.patch('/me', requireAuth, async (req, res) => {
  try {
    const updated = await updateUser(req.user.id, req.body || {});
    res.json(toPublicUser(updated));
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/logout', (req, res) => {
  // Bearer tokens are stateless; the client discards the token locally.
  res.json({ ok: true });
});

export default router;
