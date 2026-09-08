import React, { useState } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Loader2, Bell, Moon, X, Plus, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';

const PRESETS = [1, 6, 12, 24, 48, 72];

function readLeads(raw) {
  if (Array.isArray(raw)) {
    const arr = raw.map(Number).filter((n) => Number.isFinite(n) && n > 0);
    return Array.from(new Set(arr)).sort((a, b) => a - b);
  }
  if (typeof raw === 'number' && raw > 0) return [raw];
  return [24];
}

function leadLabel(h) {
  if (h < 24) return `${h}h before`;
  const d = h / 24;
  return Number.isInteger(d) ? `${d}d before` : `${h}h before`;
}

export default function ReminderSettings() {
  const { user } = useAuth();
  const [enabled, setEnabled] = useState(user?.data?.reminders_enabled !== false);
  const [leads, setLeads] = useState(readLeads(user?.data?.reminder_lead_hours));
  const [dndEnabled, setDndEnabled] = useState(user?.data?.dnd_enabled === true);
  const [dndStart, setDndStart] = useState(user?.data?.dnd_start || '22:00');
  const [dndEnd, setDndEnd] = useState(user?.data?.dnd_end || '07:00');
  const [newLead, setNewLead] = useState('');
  const [saving, setSaving] = useState(false);

  const persist = async (changes) => {
    setSaving(true);
    try {
      await api.auth.updateMe(changes);
    } catch {
      // local state already updated; best-effort persistence
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (v) => {
    setEnabled(v);
    await persist({ reminders_enabled: v });
  };

  const addLead = async (value) => {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) return;
    if (leads.includes(n)) return;
    const next = [...leads, n].sort((a, b) => a - b);
    setLeads(next);
    await persist({ reminder_lead_hours: next });
  };

  const removeLead = async (n) => {
    const next = leads.filter((x) => x !== n);
    setLeads(next);
    await persist({ reminder_lead_hours: next });
  };

  const toggleDnd = async (v) => {
    setDndEnabled(v);
    await persist({ dnd_enabled: v });
  };

  const changeDndStart = async (v) => {
    setDndStart(v);
    await persist({ dnd_start: v });
  };

  const changeDndEnd = async (v) => {
    setDndEnd(v);
    await persist({ dnd_end: v });
  };

  const disabled = !enabled;

  return (
    <div className="rounded-lg border bg-card p-4 space-y-5">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-start gap-3 min-w-0">
          <div className="mt-0.5"><Bell className="h-5 w-5 text-primary" /></div>
          <div className="min-w-0">
            <p className="text-sm font-medium">Email reminders</p>
            <p className="text-xs text-muted-foreground">Get an email before assignments are due.</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {saving && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
          <Switch checked={enabled} onCheckedChange={toggleEnabled} aria-label="Toggle email reminders" />
        </div>
      </div>

      <div className={cn('space-y-3', disabled && 'opacity-50 pointer-events-none')}>
        <div>
          <Label className="text-sm font-medium">Remind me</Label>
          <p className="text-xs text-muted-foreground">Add the exact times you want a reminder before each due date.</p>
        </div>

        {leads.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {leads.map((h) => (
              <span
                key={h}
                className="inline-flex items-center gap-1.5 rounded-full border bg-muted/50 pl-3 pr-1.5 py-1 text-xs font-medium"
              >
                <Clock className="h-3 w-3 text-muted-foreground" />
                {leadLabel(h)}
                <button
                  type="button"
                  onClick={() => removeLead(h)}
                  className="rounded-full p-0.5 hover:bg-muted text-muted-foreground hover:text-foreground"
                  aria-label={`Remove ${leadLabel(h)}`}
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
        )}

        <div className="flex flex-wrap gap-1.5">
          {PRESETS.filter((p) => !leads.includes(p)).map((p) => (
            <Button
              key={p}
              type="button"
              variant="outline"
              size="sm"
              className="h-7 px-2.5 text-xs"
              onClick={() => addLead(p)}
            >
              + {leadLabel(p)}
            </Button>
          ))}
        </div>

        <div className="flex gap-2">
          <Input
            type="number"
            min={1}
            value={newLead}
            onChange={(e) => setNewLead(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                addLead(newLead);
                setNewLead('');
              }
            }}
            placeholder="Custom hours before"
            className="flex-1"
          />
          <Button
            type="button"
            variant="secondary"
            onClick={() => { addLead(newLead); setNewLead(''); }}
            disabled={!newLead || Number(newLead) <= 0}
          >
            <Plus className="h-4 w-4 mr-1" /> Add
          </Button>
        </div>
      </div>

      <div className={cn('space-y-3 border-t pt-4', disabled && 'opacity-50 pointer-events-none')}>
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-start gap-3 min-w-0">
            <div className="mt-0.5"><Moon className="h-5 w-5 text-primary" /></div>
            <div className="min-w-0">
              <p className="text-sm font-medium">Do not disturb</p>
              <p className="text-xs text-muted-foreground">Pause reminders during a daily quiet window (your local time).</p>
            </div>
          </div>
          <Switch checked={dndEnabled} onCheckedChange={toggleDnd} disabled={disabled} aria-label="Toggle do not disturb" />
        </div>
        <div className={cn('flex flex-wrap items-end gap-4', (!dndEnabled || disabled) && 'opacity-50 pointer-events-none')}>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">Start</Label>
            <Input
              type="time"
              value={dndStart}
              onChange={(e) => changeDndStart(e.target.value)}
              className="w-32"
            />
          </div>
          <div className="text-muted-foreground pb-2">to</div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">End</Label>
            <Input
              type="time"
              value={dndEnd}
              onChange={(e) => changeDndEnd(e.target.value)}
              className="w-32"
            />
          </div>
        </div>
      </div>
    </div>
  );
}