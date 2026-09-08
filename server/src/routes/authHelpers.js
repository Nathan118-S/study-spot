import bcrypt from 'bcryptjs';
import { pool } from '../db.js';
import { sendEmail } from '../lib/mailer.js';
import crypto from 'node:crypto';

function randomCode() {
  return String(crypto.randomInt(100000, 1000000));
}

export async function sendOtpEmailFor(email, name) {
  const code = randomCode();
  const codeHash = await bcrypt.hash(code, 10);
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000);
  await pool.query('DELETE FROM email_otps WHERE email = $1 AND purpose = $2', [email, 'register']);
  await pool.query(
    'INSERT INTO email_otps (email, code_hash, purpose, expires_at) VALUES ($1, $2, $3, $4)',
    [email, codeHash, 'register', expiresAt]
  );
  await sendEmail({
    to: email,
    subject: 'Your Study Spot verification code',
    body: `Hi ${name || 'there'},\n\nYour verification code is ${code}. It expires in 15 minutes. If you didn't request this, you can ignore this email.\n\n— Study Spot`,
  });
}
