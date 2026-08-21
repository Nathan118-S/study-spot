import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { base44 } from '@/api/base44Client';

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6', '#f97316', '#0ea5e9'];

export default function ClassForm({ open, onOpenChange, cls, onSaved }) {
  const [form, setForm] = useState({ name: '', color: COLORS[0], teacher_name: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (cls) {
      setForm({ name: cls.name || '', color: cls.color || COLORS[0], teacher_name: cls.teacher_name || '' });
    } else {
      setForm({ name: '', color: COLORS[0], teacher_name: '' });
    }
  }, [cls, open]);

  const save = async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      const payload = { name: form.name.trim(), color: form.color, teacher_name: form.teacher_name.trim() };
      if (cls) {
        await base44.entities.Class.update(cls.id, payload);
      } else {
        await base44.entities.Class.create({ ...payload, source: 'manual' });
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