import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { api } from '@/api/client';
import { Plus, X } from 'lucide-react';

export const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const EMPTY = { name: '', color: '#6366f1', teacher_name: '', subject: '', room: '', schedule: [] };

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6', '#f97316', '#0ea5e9'];

export default function ClassForm({ open, onOpenChange, cls, onSaved }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (cls) {
      setForm({
        name: cls.name || '',
        color: cls.color || COLORS[0],
        teacher_name: cls.teacher_name || '',
        subject: cls.subject || '',
        room: cls.room || '',
        schedule: Array.isArray(cls.schedule) ? cls.schedule : [],
      });
    } else {
      setForm(EMPTY);
    }
  }, [cls, open]);

  const save = async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      const schedule = form.schedule
        .filter((m) => /^\d{2}:\d{2}$/.test(m.start) && /^\d{2}:\d{2}$/.test(m.end))
        .map((m) => ({ day: Number(m.day), start: m.start, end: m.end }))
        .sort((a, b) => a.day - b.day || a.start.localeCompare(b.start));
      const payload = {
        name: form.name.trim(),
        color: form.color,
        teacher_name: form.teacher_name.trim(),
        subject: form.subject.trim(),
        room: form.room.trim(),
        schedule,
      };
      if (cls) {
        await api.entities.Class.update(cls.id, payload);
      } else {
        await api.entities.Class.create({ ...payload, source: 'manual' });
      }
      onSaved?.();
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{cls ? 'Edit Class' : 'Add Class'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label>Class Name</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. AP Calculus" />
          </div>
          <div className="space-y-1.5">
            <Label>Color Tag</Label>
            <div className="flex flex-wrap gap-2">
              {COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setForm({ ...form, color: c })}
                  className="w-8 h-8 rounded-full border-2 transition"
                  style={{
                    background: c,
                    borderColor: form.color === c ? 'hsl(var(--foreground))' : 'transparent',
                  }}
                  aria-label={`Select color ${c}`}
                />
              ))}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Teacher Name (optional)</Label>
            <Input
              value={form.teacher_name}
              onChange={(e) => setForm({ ...form, teacher_name: e.target.value })}
              placeholder="e.g. Mr. Smith"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Subject (optional)</Label>
              <Input value={form.subject} onChange={(e) => setForm({ ...form, subject: e.target.value })} placeholder="e.g. Math" />
            </div>
            <div className="space-y-1.5">
              <Label>Room (optional)</Label>
              <Input value={form.room} onChange={(e) => setForm({ ...form, room: e.target.value })} placeholder="e.g. 204" />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Meeting times</Label>
            {form.schedule.map((m, i) => (
              <div key={i} className="flex flex-wrap items-center gap-2 sm:flex-nowrap">
                <select
                  value={m.day}
                  onChange={(e) => {
                    const schedule = form.schedule.map((x, j) => (j === i ? { ...x, day: Number(e.target.value) } : x));
                    setForm({ ...form, schedule });
                  }}
                  className="h-11 basis-full sm:basis-auto sm:flex-1 min-w-0 rounded-[10px] border border-input bg-card px-2 text-base md:text-sm"
                  aria-label="Day"
                >
                  {WEEKDAYS.map((d, n) => <option key={d} value={n}>{d}</option>)}
                </select>
                {['start', 'end'].map((key) => (
                  <Input
                    key={key}
                    type="time"
                    value={m[key]}
                    onChange={(e) => {
                      const schedule = form.schedule.map((x, j) => (j === i ? { ...x, [key]: e.target.value } : x));
                      setForm({ ...form, schedule });
                    }}
                    className="flex-1 min-w-0 sm:w-[7.5rem] sm:flex-none"
                    aria-label={key === 'start' ? 'Starts' : 'Ends'}
                  />
                ))}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => setForm({ ...form, schedule: form.schedule.filter((_, j) => j !== i) })}
                  aria-label="Remove meeting time"
                >
                  <X />
                </Button>
              </div>
            ))}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                const last = form.schedule[form.schedule.length - 1];
                const next = last ? { ...last, day: (Number(last.day) + 1) % 7 } : { day: 1, start: '09:00', end: '09:50' };
                setForm({ ...form, schedule: [...form.schedule, next] });
              }}
            >
              <Plus /> Add meeting time
            </Button>
            <p className="text-xs text-muted-foreground">
              The iOS app shows a Live Activity with a countdown 10 minutes before each class.
            </p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} disabled={saving || !form.name.trim()}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}