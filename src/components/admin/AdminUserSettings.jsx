import React, { useEffect, useState } from 'react';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import SheetSelect from '@/components/SheetSelect';
import GradingScaleEditor from '@/components/GradingScaleEditor';
import { Section, Row } from '@/components/admin/AdminUserParts';
import { Clock, X, Plus, Check } from 'lucide-react';

// Same defaults the app applies when a setting has never been saved.
const DEFAULTS = {
  onboarding_completed: false,
  welcome_style: 'large',
  leaderboard_enabled: false,
  reminders_enabled: true,
  reminder_lead_hours: [24],
  push_reminders: true,
  dnd_enabled: false,
  dnd_start: '22:00',
  dnd_end: '07:00',
  sync_completion_to_classroom: true,
  class_live_activities: true,
  timezone: '',
};

function read(user, key) {
  const v = user?.[key] ?? user?.data?.[key];
  return v === undefined || v === null ? DEFAULTS[key] : v;
}

function leadLabel(h) {
  if (h < 24) return `${h}h before`;
  const d = h / 24;
  return Number.isInteger(d) ? `${d}d before` : `${h}h before`;
}

function leadsOf(raw) {
  const arr = Array.isArray(raw) ? raw : typeof raw === 'number' ? [raw] : [24];
  return Array.from(new Set(arr.map(Number).filter((n) => Number.isFinite(n) && n > 0))).sort((a, b) => a - b);
}

/** Every per-user setting, saved straight away through adminUpdateUserSettings. */
export default function AdminUserSettings({ user, onSave, disabled }) {
  const [newLead, setNewLead] = useState('');
  const [tz, setTz] = useState(read(user, 'timezone'));
  const [tzError, setTzError] = useState('');
  useEffect(() => setTz(read(user, 'timezone')), [user]);

  const bool = (key, label, desc) => (
    <Row label={label} desc={desc}>
      <Switch
        checked={read(user, key) === true}
        onCheckedChange={(v) => onSave({ [key]: v })}
        disabled={disabled}
        aria-label={label}
      />
    </Row>
  );

  const leads = leadsOf(read(user, 'reminder_lead_hours'));
  const addLead = (value) => {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0 || leads.includes(n)) return;
    onSave({ reminder_lead_hours: [...leads, n].sort((a, b) => a - b) });
    setNewLead('');
  };

  const saveTz = () => {
    const v = tz.trim();
    try {
      if (v) new Intl.DateTimeFormat(undefined, { timeZone: v });
    } catch {
      setTzError('Not a known time zone (e.g. America/New_York)');
      return;
    }
    setTzError('');
    onSave({ timezone: v || null });
  };

  return (
    <div className="space-y-6">
      <Section title="General">
        {bool('onboarding_completed', 'Onboarding completed', 'Off shows the setup wizard on their next visit.')}
        <Row label="Welcome greeting" desc="Shown when they open the dashboard.">
          <SheetSelect
            value={read(user, 'welcome_style')}
            onValueChange={(v) => onSave({ welcome_style: v })}
            triggerClassName="w-[120px]"
            title="Welcome greeting"
            options={[
              { value: 'large', label: 'Large' },
              { value: 'small', label: 'Small' },
              { value: 'off', label: 'Off' },
            ]}
          />
        </Row>
        {bool('leaderboard_enabled', 'Leaderboard', 'Show their streak on the leaderboard.')}
        {bool('sync_completion_to_classroom', 'Sync completion to Classroom', 'Mark Google Classroom work done when completed here.')}
        <Row label="Time zone" desc={tzError ? <span className="text-destructive">{tzError}</span> : 'Used for reminders and do-not-disturb.'} stack>
          <div className="flex gap-2">
            <Input
              value={tz || ''}
              onChange={(e) => setTz(e.target.value)}
              placeholder="e.g. America/New_York"
              className="flex-1"
              aria-label="Time zone"
            />
            <Button size="sm" className="h-9" onClick={saveTz} disabled={disabled || (tz || '') === (read(user, 'timezone') || '')}>
              <Check className="h-4 w-4 mr-1" /> Save
            </Button>
          </div>
        </Row>
      </Section>

      <Section title="Reminders">
        {bool('reminders_enabled', 'Assignment reminders', 'Notification bell and iPhone reminders before due dates.')}
        {bool('push_reminders', 'iPhone notifications', 'Also send reminders as push notifications.')}
        <Row label="Remind them" desc="Times before each due date." stack>
          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
              {leads.length === 0 && <span className="text-[13px] text-muted-foreground">No reminder times</span>}
              {leads.map((h) => (
                <span key={h} className="inline-flex items-center gap-1.5 rounded-full border bg-muted/50 pl-3 pr-1.5 py-1 text-xs font-medium">
                  <Clock className="h-3 w-3 text-muted-foreground" />
                  {leadLabel(h)}
                  <button
                    type="button"
                    onClick={() => onSave({ reminder_lead_hours: leads.filter((x) => x !== h) })}
                    className="rounded-full p-0.5 hover:bg-muted text-muted-foreground hover:text-foreground"
                    aria-label={`Remove ${leadLabel(h)}`}
                    disabled={disabled}
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
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
                  }
                }}
                placeholder="Hours before"
                className="flex-1"
                aria-label="Hours before"
              />
              <Button size="sm" variant="secondary" className="h-9" onClick={() => addLead(newLead)} disabled={disabled || !(Number(newLead) > 0)}>
                <Plus className="h-4 w-4 mr-1" /> Add
              </Button>
            </div>
          </div>
        </Row>
        {bool('dnd_enabled', 'Do not disturb', 'Pause reminders during a daily quiet window.')}
        <Row label="Quiet hours" desc="Their local time.">
          <div className="flex items-center gap-2">
            <Input
              type="time"
              value={read(user, 'dnd_start')}
              onChange={(e) => e.target.value && onSave({ dnd_start: e.target.value })}
              className="w-[110px]"
              aria-label="Do not disturb start"
            />
            <span className="text-muted-foreground text-sm">to</span>
            <Input
              type="time"
              value={read(user, 'dnd_end')}
              onChange={(e) => e.target.value && onSave({ dnd_end: e.target.value })}
              className="w-[110px]"
              aria-label="Do not disturb end"
            />
          </div>
        </Row>
        {bool('class_live_activities', 'Class Live Activities (iOS)', 'Lock Screen countdown before each class.')}
      </Section>

      <section>
        <p className="ios-section-header">Grading scale</p>
        <GradingScaleEditor
          initialScale={user?.data?.grading_scale}
          onSave={(scale) => onSave({ grading_scale: scale })}
        />
      </section>
    </div>
  );
}
