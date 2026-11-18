import React, { useState } from 'react';
import { ArrowLeft, ArrowRight, CalendarPlus, Check, ClipboardPaste, Copy, ExternalLink, Loader2, Lock, Mail, MoreVertical, TriangleAlert } from 'lucide-react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { schoolItEmailUrl } from '@/lib/schoolItEmail';
import { AskTeacherButton } from '@/components/AskTeacher';
import { cn } from '@/lib/utils';

const GOOGLE_SETTINGS_URL = 'https://calendar.google.com/calendar/r/settings';
const STEPS = 5;
const DONE = STEPS + 1;

// Step-by-step pop-up for people who can't connect their Google account: make
// the calendar public, copy its "Public URL to this calendar" and paste it
// here, so the server can import assignments from it
// (server/src/functions/ical.js; it also accepts iCal and share links).
export default function CalendarLinkGuide({ open, onOpenChange, onAdded }) {
  const { user } = useAuth();
  const [step, setStep] = useState(1); // 1–STEPS, then DONE
  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [mode, setMode] = useState('auto');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const reset = (toStep = 1) => {
    setStep(toStep);
    setUrl('');
    setLabel('');
    setMode('auto');
    setError('');
    setResult(null);
  };

  const close = (next) => {
    onOpenChange(next);
    if (!next) setTimeout(() => reset(1), 200);
  };

  const paste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) setUrl(text.trim());
    } catch {
      setError('Your browser blocked pasting. Click the box and press Ctrl+V (⌘V on a Mac).');
    }
  };

  const add = async (e) => {
    e?.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.functions.invoke('icalAddFeed', { url, label: label.trim() || undefined, mode });
      setResult(res.data);
      setStep(DONE);
      onAdded?.(res.data);
      // Lets any list of links on the page refresh (CalendarLinks).
      window.dispatchEvent(new Event('studyspot:ical-changed'));
    } catch (err) {
      setError(err.message || "Couldn't add this calendar");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{step === DONE ? 'You’re all set' : 'Add your Google Calendar with a link'}</DialogTitle>
          {step <= STEPS && (
            <DialogDescription>
              Step {step} of {STEPS}
            </DialogDescription>
          )}
        </DialogHeader>

        {step <= STEPS && (
          <div className="flex gap-1.5" aria-hidden>
            {Array.from({ length: STEPS }, (_, i) => (
              <div key={i} className={cn('h-1.5 flex-1 rounded-full', i < step ? 'bg-primary' : 'bg-secondary')} />
            ))}
          </div>
        )}

        <div className="space-y-4 text-[15px] leading-relaxed">
          {step === 1 && (
            <>
              <p className="text-muted-foreground">
                If your school won’t let Study Spot connect to your Google account, you can paste your calendar’s
                public link instead. Study Spot reads your calendar and imports your assignments. It takes about a
                minute.
              </p>
              <StepTitle n={1}>Open Google Calendar on a computer</StepTitle>
              <p>
                Go to <strong>calendar.google.com</strong> in a web browser and sign in with your school account. These
                settings aren’t in the Google Calendar phone app. On a phone, open the page in your browser and choose
                “Desktop site”.
              </p>
              <Button asChild variant="outline" className="rounded-full">
                <a href={GOOGLE_SETTINGS_URL} target="_blank" rel="noreferrer">
                  <ExternalLink className="h-4 w-4 mr-2" /> Open Google Calendar
                </a>
              </Button>
            </>
          )}

          {step === 2 && (
            <>
              <StepTitle n={2}>Open the calendar’s settings</StepTitle>
              <p>
                In the list on the left under <strong>My calendars</strong>, point to the calendar that has your
                assignments, click the three dots (<MoreVertical className="inline h-4 w-4 -mt-0.5" />), then choose{' '}
                <strong>Settings and sharing</strong>.
              </p>
              <SidebarIllustration />
              <Tip>
                This works for calendars you own, like your own calendar. Google Classroom class calendars belong to
                your teacher, so only your teacher can share them. Use <strong>Ask your teacher</strong> for those.
              </Tip>
              <AskTeacherButton />
            </>
          )}

          {step === 3 && (
            <>
              <StepTitle n={3}>Make the calendar public</StepTitle>
              <p>
                Under <strong>Access permissions for events</strong>, turn on <strong>Make available to public</strong>{' '}
                and choose <strong>See all event details</strong>. If Google shows a warning, click <strong>OK</strong>.
              </p>
              <PublicIllustration />
              <Tip>
                Anyone with the link can see this calendar’s events. Prefer to keep it private? Copy the{' '}
                <strong>Secret address in iCal format</strong> (under Integrate calendar) instead. Study Spot accepts
                that too.
              </Tip>
              <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 text-sm space-y-2">
                <p className="flex gap-2">
                  <TriangleAlert className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
                  <span>
                    <strong>Don’t see this option, or it’s greyed out?</strong> Your school has turned off public
                    calendars. Email your school’s IT team, or add assignments yourself.
                  </span>
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="ml-6 bg-card"
                  onClick={() => { window.location.href = schoolItEmailUrl(user, 'google'); }}
                >
                  <Mail className="h-4 w-4 mr-1.5" /> Email my school’s IT team
                </Button>
                <div className="ml-6"><AskTeacherButton /></div>
              </div>
            </>
          )}

          {step === 4 && (
            <>
              <StepTitle n={4}>Copy the public URL</StepTitle>
              <p>
                Scroll down to <strong>Integrate calendar</strong> and copy <strong>Public URL to this calendar</strong>.
              </p>
              <PublicUrlIllustration />
              <p className="text-sm text-muted-foreground">
                It starts with <code className="text-foreground">https://calendar.google.com/calendar/embed?src=</code>
              </p>
              <PrivacyNote />
            </>
          )}

          {step === 5 && (
            <form id="ical-add" onSubmit={add} className="space-y-4">
              <StepTitle n={5}>Paste it here</StepTitle>
              <div className="space-y-1.5">
                <Label htmlFor="ical-url">Calendar link</Label>
                <div className="flex gap-2">
                  <Input
                    id="ical-url"
                    value={url}
                    onChange={(e) => { setUrl(e.target.value); setError(''); }}
                    placeholder="https://calendar.google.com/calendar/embed?src=…"
                    autoComplete="off"
                    spellCheck={false}
                    className="h-11"
                    autoFocus
                  />
                  <Button type="button" variant="outline" className="h-11 shrink-0" onClick={paste}>
                    <ClipboardPaste className="h-4 w-4 mr-1.5" /> Paste
                  </Button>
                </div>
                {error && <p className="text-sm text-destructive">{error}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ical-label">Name (optional)</Label>
                <Input
                  id="ical-label"
                  value={label}
                  onChange={(e) => setLabel(e.target.value)}
                  placeholder="Uses the calendar’s name if left empty"
                  className="h-11"
                />
              </div>
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium mb-1">What to import</legend>
                <ModeOption
                  checked={mode === 'auto'}
                  onSelect={() => setMode('auto')}
                  title="Only events that look like assignments"
                  desc="Homework, quizzes, tests, projects and everything from Google Classroom."
                />
                <ModeOption
                  checked={mode === 'all'}
                  onSelect={() => setMode('all')}
                  title="Every event in this calendar"
                />
              </fieldset>
              <PrivacyNote />
            </form>
          )}

          {step === DONE && result && (
            <div className="space-y-3">
              <div className="mx-auto h-14 w-14 rounded-full bg-primary/15 flex items-center justify-center">
                <Check className="h-7 w-7 text-primary" />
              </div>
              <p className="text-center font-medium">
                {result.imported > 0
                  ? `Imported ${result.imported} assignment${result.imported === 1 ? '' : 's'} from ${result.feed?.label}.`
                  : `No assignments found in ${result.feed?.label} yet. New ones will appear automatically.`}
              </p>
              <p className="text-center text-muted-foreground text-sm">
                Study Spot checks this calendar for changes every couple of hours and whenever you sync. Add each class
                calendar the same way.
              </p>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between gap-3 pt-2">
          {step === DONE ? (
            <>
              <Button variant="outline" className="rounded-full" onClick={() => reset(2)}>
                <CalendarPlus className="h-4 w-4 mr-1.5" /> Add another calendar
              </Button>
              <Button className="rounded-full" onClick={() => close(false)}>Done</Button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                className="rounded-full"
                onClick={() => (step === 1 ? close(false) : setStep(step - 1))}
                disabled={busy}
              >
                {step === 1 ? 'Cancel' : (<><ArrowLeft className="h-4 w-4 mr-1" /> Back</>)}
              </Button>
              {step < STEPS ? (
                <Button className="rounded-full" onClick={() => setStep(step + 1)}>
                  Next <ArrowRight className="h-4 w-4 ml-1" />
                </Button>
              ) : (
                <Button type="submit" form="ical-add" className="rounded-full" disabled={busy || !url.trim()}>
                  {busy ? (<><Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> Importing…</>) : 'Add calendar'}
                </Button>
              )}
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function StepTitle({ n, children }) {
  return (
    <h3 className="flex items-center gap-2.5 text-lg font-semibold text-[hsl(var(--navy))]">
      <span className="h-7 w-7 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">
        {n}
      </span>
      {children}
    </h3>
  );
}

function Tip({ children }) {
  return <p className="rounded-xl bg-secondary/70 text-secondary-foreground p-3 text-sm">{children}</p>;
}

function PrivacyNote() {
  return (
    <p className="flex gap-2 text-xs text-muted-foreground">
      <Lock className="h-3.5 w-3.5 shrink-0 mt-0.5" />
      Study Spot only reads this calendar. If you turn off public access later, Study Spot stops getting updates
      from it.
    </p>
  );
}

function ModeOption({ checked, onSelect, title, desc }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'w-full text-left rounded-xl border p-3 flex gap-3 transition-colors',
        checked ? 'border-primary bg-primary/10' : 'border-border hover:bg-accent'
      )}
      aria-pressed={checked}
    >
      <span className={cn('mt-0.5 h-4 w-4 rounded-full border-2 shrink-0', checked ? 'border-primary bg-primary shadow-[inset_0_0_0_3px_hsl(var(--card))]' : 'border-muted-foreground/50')} />
      <span>
        <span className="block text-sm font-medium">{title}</span>
        {desc && <span className="block text-xs text-muted-foreground mt-0.5">{desc}</span>}
      </span>
    </button>
  );
}

// Simple drawings of Google Calendar's screens (no logos), to show where to click.
function SidebarIllustration() {
  return (
    <div className="rounded-xl border bg-background p-3 text-sm select-none" aria-hidden>
      <p className="text-xs font-semibold text-muted-foreground mb-2">My calendars</p>
      {[['Your name', '#4f86f7'], ['AP Biology', '#10b981'], ['English 11', '#ec4899']].map(([name, color], i) => (
        <div key={name} className={cn('relative flex items-center gap-2 rounded-md px-2 py-1.5', i === 1 && 'bg-secondary')}>
          <span className="h-3.5 w-3.5 rounded-sm" style={{ background: color }} />
          <span className="flex-1">{name}</span>
          {i === 1 && (
            <>
              <span className="rounded-full ring-2 ring-primary p-0.5"><MoreVertical className="h-4 w-4" /></span>
              <div className="absolute right-0 top-8 z-10 w-48 rounded-lg border bg-card shadow-lg py-1 text-[13px]">
                <div className="px-3 py-1.5 text-muted-foreground">Display this only</div>
                <div className="px-3 py-1.5 bg-primary/15 font-semibold text-foreground">Settings and sharing</div>
              </div>
            </>
          )}
        </div>
      ))}
      <div className="h-14" />
    </div>
  );
}

function PublicIllustration() {
  return (
    <div className="rounded-xl border bg-background p-3 text-sm select-none space-y-2" aria-hidden>
      <p className="font-semibold">Access permissions for events</p>
      <div className="flex items-center gap-3 rounded-md ring-2 ring-primary px-2 py-1.5">
        <span className="h-4 w-4 rounded-sm bg-[#1a73e8] flex items-center justify-center">
          <Check className="h-3 w-3 text-white" />
        </span>
        <span className="flex-1">Make available to public</span>
        <span className="rounded-md border bg-card px-2 py-0.5 text-xs">See all event details ▾</span>
      </div>
      <div className="flex items-center gap-3 px-2 py-1.5 text-muted-foreground">
        <span className="h-4 w-4 rounded-sm border" />
        <span>Make available for your school</span>
      </div>
    </div>
  );
}

function PublicUrlIllustration() {
  return (
    <div className="rounded-xl border bg-background p-3 text-sm select-none space-y-2" aria-hidden>
      <p className="font-semibold">Integrate calendar</p>
      <p className="text-xs text-muted-foreground">Calendar ID</p>
      <div className="rounded-md border bg-card px-2 py-1.5 text-xs font-mono text-muted-foreground truncate">c_1a2b3c…@group.calendar.google.com</div>
      <p className="text-xs font-semibold text-foreground">Public URL to this calendar</p>
      <div className="flex items-center gap-2">
        <div className="min-w-0 flex-1 truncate rounded-md ring-2 ring-primary bg-primary/10 px-2 py-1.5 text-xs font-mono">
          https://calendar.google.com/calendar/embed?src=…
        </div>
        <span className="rounded-full ring-2 ring-primary p-1"><Copy className="h-4 w-4" /></span>
      </div>
    </div>
  );
}
