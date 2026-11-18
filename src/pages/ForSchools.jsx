import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Check, Copy, Mail } from 'lucide-react';
import { Button } from '@/components/ui/button';
import logo from '@/assets/icons/apple-touch-icon.png';
import { SUPPORT_EMAIL } from '@/lib/support';

const SITE = typeof window !== 'undefined' ? window.location.origin : 'https://studyspot.education';
const API_BASE = import.meta.env.VITE_API_URL || '/api';

// Everything a school or district IT team needs to approve Study Spot:
// the IDs to allow, step-by-step admin-console instructions, what the app
// accesses and why, and how student data is handled. Linked from the
// "Email my school's IT team" drafts in every app.
export default function ForSchools() {
  const [info, setInfo] = useState(null);

  useEffect(() => {
    fetch(`${API_BASE}/public/school-info`)
      .then((r) => (r.ok ? r.json() : null))
      .then(setInfo)
      .catch(() => setInfo(null));
  }, []);

  const contactLine = (
    <a className="text-primary font-medium hover:underline" href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('Study Spot for our school')}`}>
      {SUPPORT_EMAIL}
    </a>
  );

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

      <main className="max-w-3xl mx-auto px-4 py-10 space-y-10">
        <div>
          <p className="text-sm font-semibold tracking-wide text-primary uppercase">For school and district IT teams</p>
          <h1 className="font-heading text-3xl md:text-4xl font-bold mt-1">Approving Study Spot for your students</h1>
          <p className="mt-3 text-muted-foreground leading-relaxed">
            Study Spot is a planner that helps students keep track of their assignments. Students can connect their
            school Google account (Google Classroom and Google Calendar) or Blackboard so their due dates import
            automatically. If your school only allows approved third-party apps, students will see a message that the
            connection is blocked. This page has everything you need to review and approve it.
          </p>
        </div>

        <Section title="At a glance">
          <div className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 divide-y">
            <Fact label="App name" value="Study Spot" />
            <Fact label="Website" value={SITE} copy />
            <Fact
              label="Google OAuth client ID"
              value={info?.googleClientId}
              placeholder={info === null ? 'Loading…' : 'Contact us for the client ID'}
              copy
              mono
            />
            {info?.blackboardApplicationId && (
              <Fact label="Blackboard Application ID" value={info.blackboardApplicationId} copy mono />
            )}
            <Fact label="Privacy policy" value={`${SITE}/privacy`} copy />
            <Fact label="Contact" value={SUPPORT_EMAIL} copy />
          </div>
        </Section>

        <Section title="Approve in Google Workspace">
          <Steps
            items={[
              <>Sign in to the <a className="text-primary hover:underline" href="https://admin.google.com" target="_blank" rel="noreferrer">Google Admin console</a> with an administrator account.</>,
              <>Go to <strong>Security → Access and data control → API controls</strong>.</>,
              <>Under <strong>App access control</strong>, click <strong>Manage Third-Party App Access</strong>.</>,
              <>Click <strong>Add app → OAuth App Name Or Client ID</strong>, paste the Google OAuth client ID above, click <strong>Search</strong> and select <strong>Study Spot</strong>.</>,
              <>Choose who it applies to: your whole organization, or just the organizational unit with your students.</>,
              <>Set access to <strong>Trusted</strong> and click <strong>Finish</strong>. Changes usually take effect within a few minutes, but can take up to 24 hours.</>,
            ]}
          />
          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">
            Google blocks unconfigured third-party apps for users designated as under 18 by default, which is the
            most common reason students can’t connect. Marking Study Spot as Trusted for those users resolves it.
          </p>
        </Section>

        <Section title="What Study Spot can access">
          <p className="text-muted-foreground mb-3">Only the student who connects their account, and only these Google permissions:</p>
          <div className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 divide-y">
            {(info?.googleScopes || []).map((s) => (
              <div key={s.scope} className="p-4">
                <p className="font-mono text-xs break-all text-[hsl(var(--navy))]">{s.scope}</p>
                <p className="mt-1 text-sm text-muted-foreground">{s.purpose}</p>
              </div>
            ))}
            {!info?.googleScopes && <p className="p-4 text-sm text-muted-foreground">Loading…</p>}
          </div>
          <p className="mt-3 text-sm text-muted-foreground">
            Study Spot doesn’t ask for Gmail, Drive, contacts, or teacher or administrator access, and it can’t see
            other students’ work or grades.
          </p>
        </Section>

        <Section title="Approve in Blackboard Learn">
          <Steps
            items={[
              <>In Blackboard Learn, go to <strong>System Admin → Integrations → REST API Integrations</strong>.</>,
              <>Click <strong>Create Integration</strong> and enter Study Spot’s Application ID{info?.blackboardApplicationId ? ' (above)' : ` (email ${SUPPORT_EMAIL} for it)`}.</>,
              <>Choose an integration user, set <strong>End User Access</strong> to <strong>Yes</strong> so students can authorize it for their own account, and leave <strong>Authorized To Act As User</strong> at <strong>Service Default</strong>.</>,
              <>Click <strong>Submit</strong>. Students sign in with their own Blackboard account, and Study Spot only reads their courses and gradebook due dates.</>,
            ]}
          />
        </Section>

        <Section title="If you’d rather not approve the app">
          <p className="text-muted-foreground leading-relaxed">
            Students can paste a Google Calendar link instead of connecting their account. That needs calendar sharing
            to be allowed (Google Admin console → Apps → Google Workspace → Calendar → Sharing settings). Approving the
            app is the better option for privacy: it doesn’t require making any calendar public, and access can be
            reviewed and revoked from the Admin console.
          </p>
        </Section>

        <Section title="Student data and privacy">
          <ul className="list-disc pl-5 space-y-2 text-muted-foreground leading-relaxed">
            <li>Students sign up with a username and password. Study Spot doesn’t collect email addresses, and passwords are stored only as secure hashes.</li>
            <li>Study Spot stores the student’s classes, assignments, notes, grades they enter and study sessions, to show them in the app. Each account’s data is private to that student.</li>
            <li>No advertising, no analytics or tracking tools, and student data is never sold or used to build profiles.</li>
            <li>Google and Blackboard access is used only to sync that student’s own coursework. Students can disconnect at any time in Settings, and you can revoke access for everyone from your admin console.</li>
            <li>Students can permanently delete their account and all of its data from Settings → Danger Zone. To request deletion for students in your school, email {contactLine}.</li>
            <li>Two-factor authentication (authenticator app or passkey) is available to every student.</li>
          </ul>
          <p className="mt-4 text-sm text-muted-foreground">
            Full details are in our <Link to="/privacy" className="text-primary font-medium hover:underline">Privacy Policy</Link>.
          </p>
        </Section>

        <Section title="Data privacy agreements">
          <p className="text-muted-foreground leading-relaxed">
            If your district requires a student data privacy agreement or a vendor security questionnaire, email{' '}
            {contactLine} and we’ll work through it with you.
          </p>
        </Section>

        <div className="rounded-2xl bg-card ring-1 ring-black/5 dark:ring-white/10 p-6 flex flex-col sm:flex-row sm:items-center gap-4 justify-between">
          <div>
            <p className="font-semibold">Questions?</p>
            <p className="text-sm text-muted-foreground">We’re happy to help your IT team review Study Spot.</p>
          </div>
          <Button asChild className="rounded-full">
            <a href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('Study Spot for our school')}`}>
              <Mail className="h-4 w-4 mr-2" /> Contact us
            </a>
          </Button>
        </div>
      </main>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section>
      <h2 className="font-heading text-xl font-bold text-[hsl(var(--navy))] mb-3">{title}</h2>
      {children}
    </section>
  );
}

function Steps({ items }) {
  return (
    <ol className="space-y-3">
      {items.map((item, i) => (
        <li key={i} className="flex gap-3">
          <span className="h-7 w-7 shrink-0 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">
            {i + 1}
          </span>
          <span className="pt-0.5 leading-relaxed">{item}</span>
        </li>
      ))}
    </ol>
  );
}

function Fact({ label, value, placeholder, copy, mono }) {
  const [copied, setCopied] = useState(false);
  const doCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* the value is still selectable */
    }
  };
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="min-w-0 flex-1">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className={`text-sm break-all ${mono ? 'font-mono' : 'font-medium'} ${value ? '' : 'text-muted-foreground'}`}>
          {value || placeholder}
        </p>
      </div>
      {copy && value && (
        <Button variant="ghost" size="icon" onClick={doCopy} aria-label={`Copy ${label}`}>
          {copied ? <Check className="h-4 w-4 text-primary" /> : <Copy className="h-4 w-4" />}
        </Button>
      )}
    </div>
  );
}
