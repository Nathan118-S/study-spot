import nodemailer from 'nodemailer';
import { resolveSecret } from './secrets.js';

// Sends an email if SMTP is configured (via the admin secrets vault or
// server/.env); otherwise logs to the console so local development and
// self-hosted setups without email still surface the content (e.g. OTP
// codes, password reset links) instead of failing silently.
export async function sendEmail({ to, subject, body }) {
  const host = await resolveSecret('SMTP_HOST');
  if (!host) {
    console.log(`[mailer] SMTP not configured — would send to ${to}:\nSubject: ${subject}\n\n${body}`);
    return;
  }

  const [port, user, pass, from] = await Promise.all([
    resolveSecret('SMTP_PORT'),
    resolveSecret('SMTP_USER'),
    resolveSecret('SMTP_PASS'),
    resolveSecret('SMTP_FROM'),
  ]);

  const transporter = nodemailer.createTransport({
    host,
    port: Number(port || 587),
    secure: Number(port) === 465,
    auth: user ? { user, pass } : undefined,
  });

  await transporter.sendMail({
    from: from || 'Study Spot <no-reply@example.com>',
    to,
    subject,
    text: body,
  });
}
