import React, { useState, useEffect, useRef, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import SheetSelect from '@/components/SheetSelect';
import { useToast } from '@/components/ui/use-toast';
import { Play, Pause, RotateCcw, Coffee, Brain, Timer as TimerIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';

const PHASES = {
  focus: { label: 'Focus', minutes: 25, icon: Brain, accent: 'text-primary', ring: 'border-primary/40', bg: 'bg-primary/5' },
  short: { label: 'Short Break', minutes: 5, icon: Coffee, accent: 'text-emerald-500', ring: 'border-emerald-500/40', bg: 'bg-emerald-500/5' },
  long: { label: 'Long Break', minutes: 15, icon: Coffee, accent: 'text-sky-500', ring: 'border-sky-500/40', bg: 'bg-sky-500/5' },
};

const fmt = (s) => {
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
};

export default function StudyTimer({ assignments = [], classes = [] }) {
  const { toast } = useToast();
  const [phase, setPhase] = useState('focus');
  const [focusMinutes, setFocusMinutes] = useState(25);
  const [running, setRunning] = useState(false);
  const [endsAt, setEndsAt] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(25 * 60);
  const [cycles, setCycles] = useState(0);
  const [assignmentId, setAssignmentId] = useState('none');
  const [logging, setLogging] = useState(false);
  const [customOpen, setCustomOpen] = useState(false);
  const [customMin, setCustomMin] = useState('');
  const tickRef = useRef(null);
  const PRESETS = [15, 25, 45, 50];
  const isPreset = PRESETS.includes(focusMinutes);

  const phaseSeconds = useCallback(() => {
    const mins = phase === 'focus' ? focusMinutes : PHASES[phase].minutes;
    return mins * 60;
  }, [phase, focusMinutes]);

  // Tick loop driven by wall clock so it survives backgrounding.
  useEffect(() => {
    if (!running || !endsAt) return;
    const update = () => {
      const left = Math.max(0, (endsAt - Date.now()) / 1000);
      setSecondsLeft(left);
      if (left <= 0) {
        setRunning(false);
        setEndsAt(null);
        completePhase();
      }
    };
    update();
    tickRef.current = setInterval(update, 250);
    return () => clearInterval(tickRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, endsAt]);

  const completePhase = async () => {
    const finishedPhase = phase;
    const minutes = finishedPhase === 'focus' ? focusMinutes : PHASES[finishedPhase].minutes;
    const next = finishedPhase === 'focus' ? (cycles % 4 === 3 ? 'long' : 'short') : 'focus';

    if (finishedPhase === 'focus') {
      const a = assignments.find((x) => x.id === assignmentId);
      setLogging(true);
      try {
        await base44.entities.StudySession.create({
          assignment_id: a?.id || null,
          assignment_title: a?.title || null,
          class_id: a?.class_id || null,
          class_name: a?.class_name || null,
          duration_minutes: minutes,
          session_type: 'focus',
          started_at: new Date(Date.now() - minutes * 60 * 1000).toISOString(),
          completed_at: new Date().toISOString(),
        });
        toast({ title: 'Focus session logged', description: `${minutes} min${a ? ' · ' + a.title : ''}` });
      } catch (e) {
        toast({ title: 'Could not log session', description: e.message, variant: 'destructive' });
      } finally {
        setLogging(false);
      }
      setCycles((c) => c + 1);
    } else {
      toast({ title: 'Break over', description: 'Ready for the next focus session?' });
    }

    setPhase(next);
    const nextMins = next === 'focus' ? focusMinutes : PHASES[next].minutes;
    setSecondsLeft(nextMins * 60);
  };

  const start = () => {
    if (running) {
      // pause: freeze remaining time
      setRunning(false);
      setEndsAt(null);
    } else {
      setEndsAt(Date.now() + secondsLeft * 1000);
      setRunning(true);
    }
  };

  const reset = () => {
    setRunning(false);
    setEndsAt(null);
    setSecondsLeft(phaseSeconds());
  };

  const switchPhase = (p) => {
    setRunning(false);
    setEndsAt(null);
    setPhase(p);
    const mins = p === 'focus' ? focusMinutes : PHASES[p].minutes;
    setSecondsLeft(mins * 60);
  };

  const changeFocus = (m) => {
    setFocusMinutes(m);
    if (phase === 'focus' && !running) setSecondsLeft(m * 60);
  };

  const PhaseIcon = PHASES[phase].icon;
  const total = phaseSeconds();
  const progress = total > 0 ? (1 - secondsLeft / total) : 0;

  return (
    <div className={cn('rounded-lg border bg-card p-4', PHASES[phase].ring)}>
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <TimerIcon className="h-5 w-5 text-primary" />
          <h2 className="font-semibold">Study Timer</h2>
          <Badge variant="secondary" className="capitalize">{PHASES[phase].label}</Badge>
        </div>
        <div className="flex items-center gap-1 text-xs text-muted-foreground">
          <span>{cycles} session{cycles === 1 ? '' : 's'} today</span>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row gap-4 items-center">
        <div className="relative flex items-center justify-center w-28 h-28 shrink-0">
          <svg className="absolute inset-0 -rotate-90" viewBox="0 0 100 100">
            <circle cx="50" cy="50" r="44" fill="none" stroke="hsl(var(--muted))" strokeWidth="6" />
            <circle
              cx="50" cy="50" r="44" fill="none"
              stroke="currentColor"
              className={PHASES[phase].accent}
              strokeWidth="6" strokeLinecap="round"
              strokeDasharray={2 * Math.PI * 44}
              strokeDashoffset={2 * Math.PI * 44 * (1 - progress)}
            />
          </svg>
          <div className="text-center">
            <PhaseIcon className={cn('h-4 w-4 mx-auto mb-0.5', PHASES[phase].accent)} />
            <p className="text-2xl font-bold tabular-nums">{fmt(secondsLeft)}</p>
          </div>
        </div>

        <div className="flex-1 w-full space-y-3">
          <div className="flex gap-1.5">
            {['focus', 'short', 'long'].map((p) => (
              <Button
                key={p}
                size="sm"
                variant={phase === p ? 'default' : 'outline'}
                className="flex-1"
                onClick={() => switchPhase(p)}
              >
                {PHASES[p].label}
              </Button>
            ))}
          </div>

          {phase === 'focus' && (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground shrink-0">Length</span>
                <div className="flex gap-1 flex-wrap">
                  {PRESETS.map((m) => (
                    <Button
                      key={m}
                      size="sm"
                      variant={focusMinutes === m ? 'secondary' : 'ghost'}
                      className="h-7 px-2 text-xs"
                      onClick={() => { changeFocus(m); setCustomOpen(false); }}
                    >
                      {m}m
                    </Button>
                  ))}
                  <Button
                    size="sm"
                    variant={!isPreset ? 'secondary' : 'ghost'}
                    className="h-7 px-2 text-xs"
                    onClick={() => setCustomOpen((v) => !v)}
                  >
                    Other
                  </Button>
                </div>
              </div>
              {customOpen && (
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min={1}
                    max={180}
                    value={customMin}
                    onChange={(e) => setCustomMin(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        const m = Math.max(1, Math.min(180, parseInt(customMin) || 25));
                        changeFocus(m);
                        setCustomOpen(false);
                      }
                    }}
                    className="h-7 w-20"
                    placeholder="min"
                    autoFocus
                  />
                  <Button
                    size="sm"
                    className="h-7"
                    onClick={() => {
                      const m = Math.max(1, Math.min(180, parseInt(customMin) || 25));
                      changeFocus(m);
                      setCustomOpen(false);
                    }}
                  >
                    Set
                  </Button>
                </div>
              )}
              {!isPreset && !customOpen && (
                <p className="text-xs text-muted-foreground">Custom: {focusMinutes} min</p>
              )}
            </div>
          )}

          <SheetSelect
            value={assignmentId}
            onValueChange={setAssignmentId}
            placeholder="Studying for…"
            triggerClassName="w-full"
            options={[
              { value: 'none', label: 'General study (no assignment)' },
              ...assignments
                .filter((a) => !a.completed)
                .slice(0, 100)
                .map((a) => ({ value: a.id, label: a.title })),
            ]}
          />

          <div className="flex gap-2">
            <Button className="flex-1" onClick={start} disabled={logging}>
              {running ? <><Pause className="h-4 w-4 mr-1" /> Pause</> : <><Play className="h-4 w-4 mr-1" /> Start</>}
            </Button>
            <Button variant="outline" onClick={reset} disabled={running || logging}>
              <RotateCcw className="h-4 w-4 mr-1" /> Reset
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}