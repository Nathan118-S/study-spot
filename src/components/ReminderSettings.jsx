import React, { useState } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select';
import { Loader2, Bell } from 'lucide-react';

const LEAD_OPTIONS = [
  { value: 1, label: '1 hour before' },
  { value: 6, label: '6 hours before' },
  { value: 12, label: '12 hours before' },
  { value: 24, label: '24 hours before' },
  { value: 48, label: '2 days before' },
];

export default function ReminderSettings() {
  const { user } = useAuth();
  const [enabled, setEnabled] = useState(user?.data?.reminders_enabled !== false);
  const [leadHours, setLeadHours] = useState(user?.data?.reminder_lead_hours ?? 24);
  const [saving, setSaving] = useState(false);

  const persist = async (changes) => {
    setSaving(true);
    try {
      await base44.auth.updateMe(changes);
    } catch {
      // revert handled by caller via local state only on success
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (v) => {
    setEnabled(v);
    await persist({ reminders_enabled: v });
  };

  const changeLead = async (v) => {
    const n = Number(v);
    setLeadHours(n);
    await persist({ reminder_lead_hours: n });
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-4">
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
      <div className={`flex items-center justify-between gap-4 ${enabled ? '' : 'opacity-50 pointer-events-none'}`}>
        <div>
          <Label className="text-sm font-medium">Remind me</Label>
          <p className="text-xs text-muted-foreground">How early to send each reminder.</p>
        </div>
        <Select value={String(leadHours)} onValueChange={changeLead} disabled={!enabled}>
          <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            {LEAD_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={String(o.value)}>{o.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}