import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import SheetSelect from '@/components/SheetSelect';
import { base44 } from '@/api/base44Client';

const PRIORITIES = ['low', 'medium', 'high'];
const TYPES = ['homework', 'project', 'quiz', 'test', 'reading', 'other'];
const cap = (s) => s[0].toUpperCase() + s.slice(1);

export default function TemplateForm({ open, onOpenChange, template, onSaved }) {
  const [form, setForm] = useState({ name: '', title: '', priority: 'medium', type: 'homework', points: '', notes: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (template) {
      setForm({
        name: template.name || '',
        title: template.title || '',
        priority: template.priority || 'medium',
        type: template.type || 'homework',
        points: template.points ?? '',
        notes: template.notes || '',
      });
    } else {
      setForm({ name: '', title: '', priority: 'medium', type: 'homework', points: '', notes: '' });
    }
  }, [template, open]);

  const save = async () => {
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        title: form.title.trim(),
        priority: form.priority,
        type: form.type,
        points: form.points === '' ? 0 : Number(form.points),
        notes: form.notes,
      };
      if (template) {
        await base44.entities.AssignmentTemplate.update(template.id, payload);
      } else {
        await base44.entities.AssignmentTemplate.create(payload);
      }
      onSaved?.();
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{template ? 'Edit Template' : 'New Template'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label>Template Name</Label>
            <Input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="e.g. Chapter Reading"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Default Title</Label>
            <Input
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="Prefilled assignment title"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Priority</Label>
              <SheetSelect
                value={form.priority}
                onValueChange={(v) => setForm({ ...form, priority: v })}
                options={PRIORITIES.map((p) => ({ value: p, label: cap(p) }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Type</Label>
              <SheetSelect
                value={form.type}
                onValueChange={(v) => setForm({ ...form, type: v })}
                options={TYPES.map((t) => ({ value: t, label: cap(t) }))}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Points (max)</Label>
            <Input
              type="number"
              min={0}
              step={0.5}
              value={form.points}
              onChange={(e) => setForm({ ...form, points: e.target.value })}
              placeholder="0"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Notes</Label>
            <Textarea
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
              rows={3}
              placeholder="Default notes for this template..."
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" className="h-11 md:h-9" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button className="h-11 md:h-9" onClick={save} disabled={saving || !form.name.trim()}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}