import React, { useState } from 'react';
import { ArrowLeft, ArrowRight, CalendarPlus, Check, ClipboardPaste, Copy, Loader2, Lock, Mail, Settings2, TriangleAlert } from 'lucide-react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { schoolItEmailUrl } from '@/lib/schoolItEmail';
import { cn } from '@/lib/utils';

const STEPS = 3;

// Per school system: where its personal calendar link is and what it looks like.
export const LMS_SYSTEMS = {
  blackboard: {
    name: 'Blackboard',
    defaultMode: 'all',
    intro: 'If your school hasn’t approved Study Spot in Blackboard, you can paste your Blackboard calendar’s link instead. Study Spot reads it and imports your due dates.',
    step1Title: 'Open your Blackboard calendar',
    step1: <>Sign in to Blackboard in a web browser and open <strong>Calendar</strong>. In Ultra it’s in the menu on the left; in Original, open <strong>My Blackboard</strong> and choose the calendar icon.</>,
    step2Title: 'Copy the calendar link',
    step2: <>In Ultra, select the <strong>gear</strong> (Calendar Settings), open the <strong>⋯</strong> menu, choose <strong>Share Calendar</strong> and <strong>Copy</strong> the link. In Original, choose <strong>Get External Calendar Link</strong> and copy it. The link ends in <strong>learn.ics</strong>.</>,
    menu: ['Calendar settings', 'Share Calendar'],
    example: 'https://…/webapps/calendar/calendarFeed/…/learn.ics',
    itEmail: true,
  },
  canvas: {
    name: 'Canvas',
    defaultMode: 'auto',
    autoLabel: 'Only assignments and due dates',
    intro: 'If your school uses Canvas, paste your Canvas calendar feed and Study Spot will import your due dates. It works without your school approving anything.',
    step1Title: 'Open your Canvas calendar',
    step1: <>Sign in to Canvas in a web browser and select <strong>Calendar</strong> in the menu on the left.</>,
    step2Title: 'Copy the calendar feed',
    step2: <>At the bottom of the right-hand side, select <strong>Calendar Feed</strong>, then copy the link. It ends in <strong>.ics</strong>.</>,
    menu: [null, 'Calendar Feed'],
    example: 'https://…/feeds/calendars/user_….ics',
  },
  schoology: {
    name: 'Schoology',
    defaultMode: 'all',
    intro: 'If your school uses Schoology, paste your Schoology calendar link and Study Spot will import your due dates. It works without your school approving anything.',
    step1Title: 'Open your Schoology calendar',
    step1: <>Sign in to Schoology in a web browser and open <strong>Calendar</strong> from the top menu.</>,
    step2Title: 'Copy the iCal link',
    step2: <>Select <strong>Export</strong> (or the calendar’s export/iCal option) and copy the iCal feed link.</>,
    menu: [null, 'Export'],
    example: 'https://…/calendar/feed/ical/…/ical.ics',
  },
  brightspace: {
    name: 'Brightspace',
    defaultMode: 'auto',
    autoLabel: 'Only assignments and due dates',
    intro: 'If your school uses Brightspace, paste your Brightspace calendar link and Study Spot will import your due dates. It works without your school approving anything.',
    step1Title: 'Open your Brightspace calendar',
    step1: <>Sign in to Brightspace in a web browser and open <strong>Calendar</strong> (from the homepage or a course).</>,
    step2Title: 'Copy the subscription link',
    step2: <>Select <strong>Subscribe</strong>, choose <strong>All Courses</strong>, and copy the link. It contains <strong>feed.ics?token=</strong>.</>,
    menu: [null, 'Subscribe'],
    example: 'https://…/d2l/le/calendar/feed/user/feed.ics?token=…',
  },
};
const DONE = STEPS + 1;

// Step-by-step pop-up to add a school system's personal calendar link
// (Blackboard, Canvas, Schoology, Brightspace) — see LMS_SYSTEMS above and
// server/src/functions/ical.js.
export default function BlackboardLinkGuide({ open, onOpenChange, system = 'blackboard' }) {
  const sys = LMS_SYSTEMS[system] || LMS_SYSTEMS.blackboard;
  const { user } = useAuth();
  const [step, setStep] = useState(1);
  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [mode, setMode] = useState(sys.defaultMode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const reset = (toStep = 1) => {
    setStep(toStep); setUrl(''); setLabel(''); setMode(sys.defaultMode); setError(''); setResult(null);
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
          <DialogTitle>{step === DONE ? 'You’re all set' : `Add your ${sys.name} calendar with a link`}</DialogTitle>
          {step <= STEPS && <DialogDescription>Step {step} of {STEPS}</DialogDescription>}
        </DialogHeader>
        {step <= STEPS && (
          <div className="flex gap-1.5" aria-hidden>
            {Array.from({ length: STEPS }, (_, i) => (
              <div key={i} className={cn('h-1.5 flex-1 rounded-full', i < step ? 'bg-primary' : 'bg-secondary')} />
            ))}
          </div>
        )}

        <div className="min-w-0 space-y-4 text-[15px] leading-relaxed">
          {step === 1 && (
            <>
              <p className="text-muted-foreground">{sys.intro}</p>
              <StepTitle n={1}>{sys.step1Title}</StepTitle>
              <p>{sys.step1}</p>
            </>
          )}

          {step === 2 && (
            <>
              <StepTitle n={2}>{sys.step2Title}</StepTitle>
              <p>{sys.step2}</p>
              <div className="rounded-xl border bg-background p-3 text-sm select-none space-y-2" aria-hidden>
                <div className="w-56 rounded-lg border bg-card shadow-sm py-1 text-[13px]">
                  {sys.menu[0] && <div className="px-3 py-1.5 text-muted-foreground flex items-center gap-2"><Settings2 className="h-3.5 w-3.5" /> {sys.menu[0]}</div>}
                  <div className="px-3 py-1.5 bg-primary/15 font-semibold">{sys.menu[1]}</div>
                </div>
                <div className="flex items-center gap-2">
                  <div className="min-w-0 flex-1 truncate rounded-md ring-2 ring-primary bg-primary/10 px-2 py-1.5 text-xs font-mono">{sys.example}</div>
                  <span className="rounded-full ring-2 ring-primary p-1"><Copy className="h-4 w-4" /></span>
                </div>
              </div>
              <div className="rounded-xl border border-amber-500/40 bg-amber-500/10 p-3 text-sm space-y-2">
                <p className="flex gap-2">
                  <TriangleAlert className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
                  <span><strong>Don’t see it?</strong> Your school may have turned it off. Ask your school’s IT team, or add assignments yourself.</span>
                </p>
                {sys.itEmail && (
                  <Button type="button" variant="outline" size="sm" className="ml-6 bg-card" onClick={() => { window.location.href = schoolItEmailUrl(user, 'blackboard'); }}>
                    <Mail className="h-4 w-4 mr-1.5" /> Email my school’s IT team
                  </Button>
                )}
              </div>
            </>
          )}

          {step === 3 && (
            <form id="bb-ical-add" onSubmit={add} className="space-y-4">
              <StepTitle n={3}>Paste it here</StepTitle>
              <div className="space-y-1.5">
                <Label htmlFor="bb-url">{sys.name} calendar link</Label>
                <div className="flex gap-2">
                  <Input
                    id="bb-url"
                    value={url}
                    onChange={(e) => { setUrl(e.target.value); setError(''); }}
                    placeholder={sys.example}
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
                <Label htmlFor="bb-label">Name (optional)</Label>
                <Input id="bb-label" value={label} onChange={(e) => setLabel(e.target.value)} placeholder={sys.name} className="h-11" />
              </div>
              <fieldset className="space-y-2">
                <legend className="text-sm font-medium mb-1">What to import</legend>
                {sys.defaultMode === 'all' ? (
                  <>
                    <ModeOption checked={mode === 'all'} onSelect={() => setMode('all')} title="Every event in this calendar" />
                    <ModeOption checked={mode === 'auto'} onSelect={() => setMode('auto')} title={sys.autoLabel || 'Only events that look like assignments'} />
                  </>
                ) : (
                  <>
                    <ModeOption checked={mode === 'auto'} onSelect={() => setMode('auto')} title={sys.autoLabel || 'Only events that look like assignments'} />
                    <ModeOption checked={mode === 'all'} onSelect={() => setMode('all')} title="Every event in this calendar" />
                  </>
                )}
              </fieldset>
              <p className="flex gap-2 text-xs text-muted-foreground">
                <Lock className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                Keep this link private. Anyone with it can see your {sys.name} calendar. If you reset it in {sys.name},
                add the new one here.
              </p>
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
                Study Spot checks this calendar for changes every couple of hours and whenever you sync.
              </p>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between gap-3 pt-2">
          {step === DONE ? (
            <>
              <Button variant="outline" className="rounded-full" onClick={() => reset(3)}>
                <CalendarPlus className="h-4 w-4 mr-1.5" /> Add another calendar
              </Button>
              <Button className="rounded-full" onClick={() => close(false)}>Done</Button>
            </>
          ) : (
            <>
              <Button variant="ghost" className="rounded-full" onClick={() => (step === 1 ? close(false) : setStep(step - 1))} disabled={busy}>
                {step === 1 ? 'Cancel' : (<><ArrowLeft className="h-4 w-4 mr-1" /> Back</>)}
              </Button>
              {step < STEPS ? (
                <Button className="rounded-full" onClick={() => setStep(step + 1)}>Next <ArrowRight className="h-4 w-4 ml-1" /></Button>
              ) : (
                <Button type="submit" form="bb-ical-add" className="rounded-full" disabled={busy || !url.trim()}>
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
      <span className="h-7 w-7 rounded-full bg-primary text-primary-foreground text-sm font-bold flex items-center justify-center">{n}</span>
      {children}
    </h3>
  );
}

function ModeOption({ checked, onSelect, title }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={checked}
      className={cn('w-full text-left rounded-xl border p-3 flex gap-3 transition-colors', checked ? 'border-primary bg-primary/10' : 'border-border hover:bg-accent')}
    >
      <span className={cn('mt-0.5 h-4 w-4 rounded-full border-2 shrink-0', checked ? 'border-primary bg-primary shadow-[inset_0_0_0_3px_hsl(var(--card))]' : 'border-muted-foreground/50')} />
      <span className="text-sm font-medium">{title}</span>
    </button>
  );
}
