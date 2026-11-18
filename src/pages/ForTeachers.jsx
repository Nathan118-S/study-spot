import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import logo from '@/assets/icons/apple-touch-icon.png';
import { SUPPORT_EMAIL } from '@/lib/support';

// For teachers whose students asked them to share the class calendar, so the
// class can add it to Study Spot even when the school blocks app connections.
export default function ForTeachers() {
  return (
    <div className="min-h-screen text-foreground">
      <header className="sticky top-0 z-30 backdrop-blur bg-background/80 border-b border-border">
        <div className="max-w-3xl mx-auto px-4 h-16 flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link to="/" aria-label="Back to home"><ArrowLeft className="h-5 w-5" /></Link>
          </Button>
          <img src={logo} alt="" className="h-8 w-8 rounded-[22%]" />
          <span className="font-heading font-bold text-lg text-[hsl(var(--navy))]">Study Spot</span>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-10 space-y-10">
        <div>
          <p className="text-sm font-semibold tracking-wide text-primary uppercase">For teachers</p>
          <h1 className="font-heading text-3xl md:text-4xl font-bold mt-1">Share your class calendar with your students</h1>
          <p className="mt-3 text-muted-foreground leading-relaxed">
            A student asked you to share your class calendar so they can keep track of assignments in Study Spot, a
            student planner. Your school may not let students connect their Google accounts to apps, but a public class
            calendar link works for everyone in your class. It takes about a minute.
          </p>
        </div>

        <section>
          <h2 className="font-heading text-xl font-bold text-[hsl(var(--navy))] mb-3">What gets shared</h2>
          <p className="text-muted-foreground leading-relaxed">
            Google Classroom adds each assignment with a due date to the class’s Google Calendar. Making that calendar
            public shares those assignment titles, due dates and descriptions with anyone who has the link. It doesn’t
            share student names, submissions or grades. You can turn it off again at any time.
          </p>
        </section>

        <section>
          <h2 className="font-heading text-xl font-bold text-[hsl(var(--navy))] mb-3">Steps</h2>
          <ol className="space-y-3">
            {[
              <>On a computer, open <a className="text-primary hover:underline" href="https://calendar.google.com" target="_blank" rel="noreferrer">calendar.google.com</a> with your school account.</>,
              <>In the list on the left under <strong>My calendars</strong>, point to the calendar named after your class (Classroom creates one per class), click the three dots (⋮) and choose <strong>Settings and sharing</strong>.</>,
              <>Under <strong>Access permissions for events</strong>, turn on <strong>Make available to public</strong> and choose <strong>See all event details</strong>. Click <strong>OK</strong> if Google shows a warning.</>,
              <>Scroll down to <strong>Integrate calendar</strong> and copy <strong>Public URL to this calendar</strong>.</>,
              <>Post the link to your class, for example in the Classroom stream: “Add our assignments to Study Spot: Settings → Google → Add a calendar link, then paste this link.”</>,
            ].map((item, i) => (
              <li key={i} className="flex gap-3">
                <span className="h-7 w-7 shrink-0 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">{i + 1}</span>
                <span className="pt-0.5 leading-relaxed">{item}</span>
              </li>
            ))}
          </ol>
          <p className="mt-4 text-sm text-muted-foreground leading-relaxed">
            If <strong>Make available to public</strong> is greyed out, your school’s Google admin has turned off public
            calendars. Your IT team can find what they need to approve Study Spot directly on{' '}
            <Link to="/schools" className="text-primary font-medium hover:underline">our page for schools</Link>.
          </p>
        </section>

        <section>
          <h2 className="font-heading text-xl font-bold text-[hsl(var(--navy))] mb-3">Using Blackboard?</h2>
          <p className="text-muted-foreground leading-relaxed">
            Blackboard calendar links are personal, so each student copies their own from Blackboard’s Calendar (Get
            external calendar link). There’s nothing you need to do.
          </p>
        </section>

        <p className="text-sm text-muted-foreground">
          Questions? Email{' '}
          <a className="text-primary font-medium hover:underline" href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>. See also our{' '}
          <Link to="/privacy" className="text-primary font-medium hover:underline">Privacy Policy</Link>.
        </p>
      </main>
    </div>
  );
}
