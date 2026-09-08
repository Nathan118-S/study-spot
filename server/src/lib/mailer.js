import nodemailer from 'nodemailer';

let transporter = null;
function getTransporter() {
  if (transporter) return transporter;
  if (!process.env.SMTP_HOST) return null;
  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 587),
    secure: Number(process.env.SMTP_PORT) === 465,
    auth: process.env.SMTP_USER ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS } : undefined,
  });
  return transporter;
}

// Sends an email if SMTP is configured; otherwise logs to the console so local
// development and self-hosted setups without email still surface the content
// (e.g. OTP codes, password reset links) instead of failing silently.
export async function sendEmail({ to, subject, body }) {
  const t = getTransporter();
  if (!t) {
    console.log(`[mailer] SMTP not configured — would send to ${to}:\nSubject: ${subject}\n\n${body}`);
    return;
  }
  await t.sendMail({
    from: process.env.SMTP_FROM || 'Study Spot <no-reply@example.com>',
    to,
    subject,
    text: body,
  });
}
