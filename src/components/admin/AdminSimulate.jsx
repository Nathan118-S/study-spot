import React, { useCallback, useEffect, useState } from 'react';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import SheetSelect from '@/components/SheetSelect';
import { Section, fmtDate } from '@/components/admin/AdminUserParts';
import { cn } from '@/lib/utils';
import { Loader2, Send, Play, Square, CheckCircle2, XCircle, AlertTriangle, Bell, Timer, GraduationCap } from 'lucide-react';

const NO_APNS = 'Set up Apple Push in Admin → Integration Secrets.';
const NO_DEVICE = "They haven't opened the latest app on an iPhone yet.";
const SAMPLE = 'sample';

const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

function Field({ label, htmlFor, children }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor} className="text-xs text-muted-foreground">{label}</Label>
      {children}
    </div>
  );
}

function Why({ text }) {
  return text ? <p className="text-[13px] text-muted-foreground">{text}</p> : null;
}

/** Compact outcome of one send: "Delivered to 1 of 2 iPhones" + per-device failures. */
function Result({ value }) {
  if (!value) return null;
  if (value.error) {
    return <p className="text-[13px] text-destructive flex items-start gap-1.5"><XCircle className="h-4 w-4 shrink-0 mt-px" />{value.error}</p>;
  }
  const r = value.data;
  const failed = (r.results || []).filter((x) => !x.ok);
  const allOk = r.devices > 0 && failed.length === 0;
  let headline;
  if (value.what === 'end') headline = r.ended ? `Ended ${plural(r.ended, 'class Live Activity')}` : 'No running class Live Activities were reported by their iPhones';
  else if (r.devices === 0) headline = 'No iPhones to send to';
  else headline = `Delivered to ${r.delivered} of ${plural(r.devices, 'iPhone')}`;
  return (
    <div className="space-y-1 text-[13px]">
      <p className={cn('flex items-start gap-1.5 font-medium', allOk || (value.what === 'end' && r.ended) ? 'text-success' : 'text-amber-600 dark:text-amber-400')}>
        {allOk ? <CheckCircle2 className="h-4 w-4 shrink-0 mt-px" /> : <AlertTriangle className="h-4 w-4 shrink-0 mt-px" />}
        <span>
          {headline}
          {r.inApp ? ' · added to their bell' : ''}
          {r.startsAt ? ` · starts ${fmtDate(r.startsAt)}` : ''}
          {r.endsAt ? ` · ends ${fmtDate(r.endsAt)}` : ''}
        </span>
      </p>
      {failed.map((f, i) => (
        <p key={`${f.device}-${i}`} className="pl-5 text-muted-foreground break-words">
          <span className="font-mono">{f.device}</span> ({f.environment || 'production'}): {f.reason || `HTTP ${f.status}`}
        </p>
      ))}
    </div>
  );
}

/** Admin tools to send real notifications and Live Activities to someone's iPhones. */
export default function AdminSimulate({ userId }) {
  const [opts, setOpts] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [busy, setBusy] = useState(null);
  const [results, setResults] = useState({});

  // Notification form
  const [kind, setKind] = useState('reminder');
  const [assignmentId, setAssignmentId] = useState(SAMPLE);
  const [classId, setClassId] = useState(SAMPLE);
  const [streak, setStreak] = useState('7');
  const [title, setTitle] = useState('');
  const [subtitle, setSubtitle] = useState('');
  const [body, setBody] = useState('');
  const [url, setUrl] = useState('');
  const [inApp, setInApp] = useState(false);

  // Live Activity forms
  const [activityClass, setActivityClass] = useState(SAMPLE);
  const [classMinutes, setClassMinutes] = useState('10');
  const [phase, setPhase] = useState('focus');
  const [timerMinutes, setTimerMinutes] = useState('25');
  const [timerAssignment, setTimerAssignment] = useState('none');

  const load = useCallback(async () => {
    setLoadError('');
    try {
      const res = await api.functions.invoke('adminSimulateOptions', { userId });
      setOpts(res.data);
    } catch (e) {
      if (!e?.elevationCancelled) setLoadError(e.message || 'Could not load simulation options');
    }
  }, [userId]);

  useEffect(() => {
    load();
  }, [load]);

  const send = async (key, name, payload, what, busyKey = key) => {
    setBusy(busyKey);
    setResults((r) => ({ ...r, [key]: null }));
    try {
      const res = await api.functions.invoke(name, { userId, ...payload });
      setResults((r) => ({ ...r, [key]: { data: res.data, what } }));
    } catch (e) {
      if (!e?.elevationCancelled) setResults((r) => ({ ...r, [key]: { error: e.message || 'Failed' } }));
    } finally {
      setBusy(null);
      load();
    }
  };

  if (!opts) {
    return (
      <Section title="Simulate on iPhone">
        <div className="px-4 py-4 text-sm text-muted-foreground flex items-center justify-between gap-3">
          {loadError ? <span className="text-destructive">{loadError}</span> : <Loader2 className="h-4 w-4 animate-spin" />}
          {loadError && <Button size="sm" variant="outline" onClick={load}>Retry</Button>}
        </div>
      </Section>
    );
  }

  const d = opts.devices || {};
  const why = (count) => (!opts.apns ? NO_APNS : count ? null : NO_DEVICE);
  const notifWhy = why(d.notifications);
  const classWhy = why(d.class_activity);
  const timerWhy = why(d.timer_activity);

  const assignmentOptions = (opts.assignments || []).map((a) => ({
    value: a.id,
    label: [a.title, a.class_name].filter(Boolean).join(' · '),
  }));
  const classOptions = [
    { value: SAMPLE, label: 'Sample class' },
    ...(opts.classes || []).map((c) => ({ value: c.id, label: c.name })),
  ];

  const notifPayload = () => {
    const p = { kind, inApp };
    if (kind === 'reminder' && assignmentId !== SAMPLE) p.assignmentId = assignmentId;
    if (kind === 'streak') {
      if (classId !== SAMPLE) p.classId = classId;
      p.streak = Number(streak) || 7;
    }
    if (kind === 'custom') Object.assign(p, { title: title.trim(), subtitle: subtitle.trim(), body: body.trim(), url: url.trim() || undefined });
    return p;
  };

  return (
    <div className="space-y-6">
      <Section
        title="Simulate on iPhone"
        footer="Sends real pushes to this person's iPhones, exactly as the app would receive them."
      >
        <div className="px-4 py-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[13px]">
          <span className={cn('inline-flex items-center gap-1.5 font-medium', opts.apns ? 'text-success' : 'text-destructive')}>
            {opts.apns ? <CheckCircle2 className="h-4 w-4" /> : <XCircle className="h-4 w-4" />}
            Apple Push {opts.apns ? 'set up' : 'not set up'}
          </span>
          <span className="inline-flex items-center gap-1.5 text-muted-foreground"><Bell className="h-3.5 w-3.5" />{plural(d.notifications || 0, 'notification device')}</span>
          <span className="inline-flex items-center gap-1.5 text-muted-foreground"><GraduationCap className="h-3.5 w-3.5" />{d.class_activity || 0} class activity</span>
          <span className="inline-flex items-center gap-1.5 text-muted-foreground"><Timer className="h-3.5 w-3.5" />{d.timer_activity || 0} timer activity</span>
        </div>
      </Section>

      <Section title="Notification">
        <div className="px-4 py-3 space-y-3">
          <Field label="Type">
            <SheetSelect
              value={kind}
              onValueChange={setKind}
              triggerClassName="w-full"
              title="Notification type"
              options={[
                { value: 'reminder', label: 'Assignment reminder' },
                { value: 'streak', label: 'Streak record' },
                { value: 'custom', label: 'Custom' },
              ]}
            />
          </Field>

          {kind === 'reminder' && (
            <Field label="Assignment">
              <SheetSelect
                value={assignmentId}
                onValueChange={setAssignmentId}
                triggerClassName="w-full"
                title="Assignment"
                options={[{ value: SAMPLE, label: 'Sample assignment' }, ...assignmentOptions]}
              />
            </Field>
          )}

          {kind === 'streak' && (
            <div className="grid grid-cols-[1fr_96px] gap-2">
              <Field label="Class">
                <SheetSelect value={classId} onValueChange={setClassId} triggerClassName="w-full" title="Class" options={classOptions} />
              </Field>
              <Field label="Streak" htmlFor="sim-streak">
                <Input id="sim-streak" type="number" min={2} max={999} value={streak} onChange={(e) => setStreak(e.target.value)} />
              </Field>
            </div>
          )}

          {kind === 'custom' && (
            <>
              <Field label="Title" htmlFor="sim-title">
                <Input id="sim-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} placeholder="Required" />
              </Field>
              <Field label="Subtitle" htmlFor="sim-subtitle">
                <Input id="sim-subtitle" value={subtitle} onChange={(e) => setSubtitle(e.target.value)} maxLength={120} placeholder="Optional" />
              </Field>
              <Field label="Body" htmlFor="sim-body">
                <Textarea id="sim-body" value={body} onChange={(e) => setBody(e.target.value)} maxLength={500} rows={2} placeholder="Optional" />
              </Field>
              <Field label="Open link" htmlFor="sim-url">
                <Input
                  id="sim-url"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  maxLength={300}
                  placeholder="e.g. studyspot://tab/streaks"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                />
              </Field>
            </>
          )}

          <div className="flex items-center justify-between gap-3">
            <Label htmlFor="sim-inapp" className="text-[15px] font-normal">Also add to their notification bell</Label>
            <Switch id="sim-inapp" checked={inApp} onCheckedChange={setInApp} />
          </div>

          <Why text={notifWhy} />
          <Button
            className="w-full sm:w-auto"
            disabled={!!busy || !!notifWhy || (kind === 'custom' && !title.trim())}
            onClick={() => send('notif', 'adminSimulateNotification', notifPayload(), 'notif')}
          >
            {busy === 'notif' ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Send className="h-4 w-4 mr-1.5" />}
            Send notification
          </Button>
          <Result value={results.notif} />
        </div>
      </Section>

      <Section title="Live Activities">
        <div className="px-4 py-3 space-y-3">
          <p className="text-[15px] font-medium flex items-center gap-2"><GraduationCap className="h-4 w-4 text-primary" /> Class starting</p>
          <div className="grid grid-cols-[1fr_96px] gap-2">
            <Field label="Class">
              <SheetSelect value={activityClass} onValueChange={setActivityClass} triggerClassName="w-full" title="Class" options={classOptions} />
            </Field>
            <Field label="Starts in (min)" htmlFor="sim-class-min">
              <Input id="sim-class-min" type="number" min={1} max={60} value={classMinutes} onChange={(e) => setClassMinutes(e.target.value)} />
            </Field>
          </div>
          <Why text={classWhy} />
          <div className="flex flex-wrap gap-2">
            <Button
              disabled={!!busy || !!classWhy}
              onClick={() =>
                send('class', 'adminSimulateLiveActivity', {
                  kind: 'class_start',
                  action: 'start',
                  classId: activityClass === SAMPLE ? undefined : activityClass,
                  minutes: Number(classMinutes) || 10,
                }, 'start')
              }
            >
              {busy === 'class' ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Play className="h-4 w-4 mr-1.5" />}
              Start
            </Button>
            <Button
              variant="outline"
              disabled={!!busy || !opts.apns}
              onClick={() => send('class', 'adminSimulateLiveActivity', { kind: 'class_start', action: 'end' }, 'end', 'class-end')}
            >
              {busy === 'class-end' ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Square className="h-4 w-4 mr-1.5" />}
              End class Live Activities
            </Button>
          </div>
          <Result value={results.class} />
        </div>

        <div className="px-4 py-3 space-y-3">
          <p className="text-[15px] font-medium flex items-center gap-2"><Timer className="h-4 w-4 text-primary" /> Study timer</p>
          <div className="grid grid-cols-[1fr_96px] gap-2">
            <Field label="Phase">
              <SheetSelect
                value={phase}
                onValueChange={setPhase}
                triggerClassName="w-full"
                title="Phase"
                options={[
                  { value: 'focus', label: 'Focus' },
                  { value: 'short', label: 'Short break' },
                  { value: 'long', label: 'Long break' },
                ]}
              />
            </Field>
            <Field label="Minutes" htmlFor="sim-timer-min">
              <Input id="sim-timer-min" type="number" min={1} max={120} value={timerMinutes} onChange={(e) => setTimerMinutes(e.target.value)} />
            </Field>
          </div>
          <Field label="Assignment (optional)">
            <SheetSelect
              value={timerAssignment}
              onValueChange={setTimerAssignment}
              triggerClassName="w-full"
              title="Assignment"
              options={[{ value: 'none', label: 'No assignment' }, ...assignmentOptions]}
            />
          </Field>
          <Why text={timerWhy} />
          <Button
            disabled={!!busy || !!timerWhy}
            onClick={() =>
              send('timer', 'adminSimulateLiveActivity', {
                kind: 'study_timer',
                action: 'start',
                phase,
                minutes: Number(timerMinutes) || 25,
                assignmentId: timerAssignment === 'none' ? undefined : timerAssignment,
              }, 'start')
            }
          >
            {busy === 'timer' ? <Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> : <Play className="h-4 w-4 mr-1.5" />}
            Start timer
          </Button>
          <Result value={results.timer} />
        </div>
      </Section>
    </div>
  );
}
