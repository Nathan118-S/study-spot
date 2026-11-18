import React from 'react';
import { Link } from 'react-router-dom';
import { GraduationCap, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { SUPPORT_EMAIL } from '@/lib/support';

export default function PrivacyPolicy() {
  const updated = 'November 18, 2026';
  return (
    <div className="min-h-screen text-foreground">
      <header className="sticky top-0 z-30 backdrop-blur bg-background/80 border-b border-border">
        <div className="max-w-3xl mx-auto px-4 h-16 flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link to="/" aria-label="Back to home">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <div className="flex items-center gap-2">
            <GraduationCap className="h-6 w-6 text-primary" />
            <span className="font-heading font-bold text-lg">Study Spot</span>
          </div>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-10 space-y-8">
        <div>
          <h1 className="font-heading text-3xl md:text-4xl font-bold">Privacy Policy</h1>
          <p className="mt-2 text-sm text-muted-foreground">Last updated: {updated}</p>
        </div>

        <p className="text-muted-foreground">
          Study Spot (&ldquo;we&rdquo;, &ldquo;us&rdquo;, or &ldquo;the app&rdquo;) is a task manager that helps students
          organize assignments by syncing with Google Calendar, Google Classroom, and Blackboard. This policy explains
          what we collect, how we use it, and the choices you have.
        </p>

        <Section title="Information we collect">
          <ul className="list-disc pl-5 space-y-2 text-muted-foreground">
            <li><strong className="text-foreground">Account information:</strong> your name, username, and password (stored only as a secure hash), used to create and sign in to your account. We do not collect email addresses.</li>
            <li><strong className="text-foreground">Assignment and class data:</strong> the assignments, classes, scores, notes, and study sessions you create or import from connected services.</li>
            <li><strong className="text-foreground">Connected service data:</strong> when you link Google Calendar, Google Classroom, or Blackboard, we retrieve course work, due dates, and attachments you have access to, on your behalf.</li>
            <li><strong className="text-foreground">Device information:</strong> if you allow notifications in the iPhone or Android app, a push token for that device, used only to deliver your reminders and class countdowns.</li>
            <li><strong className="text-foreground">No tracking or analytics:</strong> we don&rsquo;t use advertising, analytics or tracking tools, and we don&rsquo;t follow you across other apps or websites.</li>
          </ul>
        </Section>

        <Section title="How we use your information">
          <ul className="list-disc pl-5 space-y-2 text-muted-foreground">
            <li>To display and organize your assignments, classes, and progress.</li>
            <li>To send you reminders about upcoming due dates in the app and as push notifications on your phone, based on your reminder settings.</li>
            <li>To sync completion status back to Google Classroom when you enable that option.</li>
            <li>To provide analytics, streaks, and grading features.</li>
            <li>To maintain, secure, and improve the app.</li>
          </ul>
        </Section>

        <Section title="Connected services">
          <p className="text-muted-foreground">
            Connecting Google or Blackboard is optional. We use your authorization only to import the data you request
            (such as coursework and due dates) and to take actions you initiate (such as marking work done on Classroom).
            We do not post to your accounts without your action, and you can disconnect any service at any time from
            Settings, which stops further syncing.
          </p>
        </Section>

        <Section title="Data storage and security">
          <p className="text-muted-foreground">
            Your data is stored on secure servers and is isolated to your account — other users cannot see your
            assignments or classes. We offer optional two-factor authentication (authenticator app or passkeys) and you can
            sign out of all devices from Settings. While we work to protect your data, no system is perfectly secure.
          </p>
        </Section>

        <Section title="Sharing of your information">
          <p className="text-muted-foreground">
            We do not sell your data. We share information only as needed to operate the app (for example, with our
            hosting provider, and Apple or Google to deliver push notifications), to comply with legal obligations, or to protect our rights. Admin
            users in your workspace may access limited account information needed to manage the organization.
          </p>
        </Section>

        <Section title="Your choices">
          <ul className="list-disc pl-5 space-y-2 text-muted-foreground">
            <li>Connect or disconnect Google and Blackboard integrations anytime in Settings.</li>
            <li>Customize or turn off reminders and phone notifications, and set a do-not-disturb window.</li>
            <li>Enable or disable two-factor authentication.</li>
            <li>Export your data from Settings.</li>
            <li>Delete your account from Settings &rarr; Danger Zone. This permanently deletes your account and everything in it, including assignments, classes, study history, connections and devices.</li>
          </ul>
        </Section>

        <Section title="Children's privacy">
          <p className="text-muted-foreground">
            Study Spot is intended for students managing their own coursework. We do not knowingly collect personal
            information from children below the age permitted by applicable law. If you believe a minor has provided
            data, please contact us so we can remove it.
          </p>
        </Section>

        <Section title="Changes to this policy">
          <p className="text-muted-foreground">
            We may update this policy as the app evolves. We will reflect the date of the latest revision above. Continued
            use after a change means you accept the updated policy.
          </p>
        </Section>

        <Section title="Contact">
          <p className="text-muted-foreground">
            Questions about this policy or your data? Email{' '}
            <a className="text-primary font-medium hover:underline" href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>{' '}
            or see the <Link className="text-primary font-medium hover:underline" to="/support">Support page</Link>.
          </p>
        </Section>

        <div className="pt-4 border-t border-border">
          <Button asChild variant="outline">
            <Link to="/">
              <ArrowLeft className="h-4 w-4 mr-2" /> Back to home
            </Link>
          </Button>
        </div>
      </main>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section className="space-y-3">
      <h2 className="font-heading text-xl font-semibold">{title}</h2>
      {children}
    </section>
  );
}