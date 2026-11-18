import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Mail } from 'lucide-react';
import { Button } from '@/components/ui/button';
import logo from '@/assets/icons/apple-touch-icon.png';
import { SUPPORT_EMAIL } from '@/lib/support';

const FAQ = [
  {
    q: 'What is Study Spot?',
    a: 'A planner for students. It brings your assignments and due dates from Google Classroom, Google Calendar and Blackboard into one place, with reminders, a study timer, streaks and grades. It works on the web, iPhone and Android with the same account.',
  },
  {
    q: 'How do I create an account?',
    a: 'Open the app or studyspot.education and choose Sign up. You only need a username and password; we don’t ask for an email address.',
  },
  {
    q: 'I forgot my password.',
    a: 'Because Study Spot doesn’t collect email addresses, there’s no reset email. Contact us (or your Study Spot admin) and we’ll give you a temporary password, which you can change in Settings → Account.',
  },
  {
    q: 'My school blocks connecting Google or Blackboard.',
    a: 'Some schools only allow approved third-party apps. In Settings → Google or Settings → Blackboard, tap “Email my school’s IT team” to open a ready-made request asking them to approve Study Spot. You can always add assignments manually.',
  },
  {
    q: 'I’m not getting reminders or class countdowns.',
    a: 'Check Settings → Reminders in Study Spot, and make sure notifications are allowed for Study Spot in your phone’s settings. On iPhone, Live Activities must also be on (Settings → Study Spot → Live Activities).',
  },
  {
    q: 'How do I keep my account secure?',
    a: 'Turn on two-factor authentication in Settings → Security, with an authenticator app or a passkey (Face ID, Touch ID or your phone’s screen lock).',
  },
  {
    q: 'How do I delete my account?',
    a: 'Go to Settings → Danger Zone → Delete account and confirm with your password. This permanently deletes your account and all of its data.',
  },
];

export default function Support() {
  return (
    <div className="min-h-screen text-foreground">
      <header className="sticky top-0 z-30 backdrop-blur bg-background/80 border-b border-border">
        <div className="max-w-3xl mx-auto px-4 h-16 flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link to="/" aria-label="Back to home">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <img src={logo} alt="" className="h-8 w-8 rounded-[22%]" />
          <span className="font-heading font-bold text-lg text-[hsl(var(--navy))]">Study Spot</span>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-10 space-y-8">
        <div>
          <h1 className="font-heading text-3xl md:text-4xl font-bold">Support</h1>
          <p className="mt-2 text-muted-foreground">Answers to common questions, and how to reach us.</p>
        </div>

        <div className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 p-6 flex flex-col sm:flex-row sm:items-center gap-4 justify-between">
          <div>
            <p className="font-semibold">Contact us</p>
            <p className="text-sm text-muted-foreground">We usually reply within two business days.</p>
          </div>
          <Button asChild className="rounded-full">
            <a href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('Study Spot support')}`}>
              <Mail className="h-4 w-4 mr-2" /> {SUPPORT_EMAIL}
            </a>
          </Button>
        </div>

        <div className="space-y-3">
          {FAQ.map(({ q, a }) => (
            <details key={q} className="group rounded-xl bg-card ring-1 ring-black/5 dark:ring-white/10 px-5 py-4">
              <summary className="cursor-pointer list-none font-medium flex justify-between gap-4">
                {q}
                <span className="text-muted-foreground transition-transform group-open:rotate-45">+</span>
              </summary>
              <p className="mt-3 text-muted-foreground leading-relaxed">{a}</p>
            </details>
          ))}
        </div>

        <p className="text-sm text-muted-foreground">
          See also our <Link to="/privacy" className="text-primary font-medium hover:underline">Privacy Policy</Link>.
        </p>
      </main>
    </div>
  );
}
