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

function toLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export default function AssignmentForm({ open, onOpenChange, assignment, classes, onSaved }) {
  const [form, setForm] = useState({
    title: '',
    class_id: '',
    due_date: '',
    priority: 'medium',
    type: 'homework',
    points: '',
    notes: '',
  });
  const [saving, setSaving] = useState(false);
  const [templates, setTemplates] = useState([]);

  useEffect(() => {
    base44.entities.AssignmentTemplate.list('-updated_date', 100).then(setTemplates).catch(() => {});
  }, []);

  const applyTemplate = (id) => {
    const t = templates.find((x) => x.id === id);
    if (!t) return;
    setForm((f) => ({
      ...f,
      title: t.title || f.title,
      type: t.type || f.type,
      priority: t.priority || f.priority,
      points: t.points ?? f.points,
      notes: t.notes ?? f.notes,
    }));
  };

  useEffect(() => {
    if (assignment) {
      setForm({
        title: assignment.title || '',
        class_id: assignment.class_id || '',
        due_date: toLocalInput(assignment.due_date),
        priority: assignment.priority || 'medium',
        type: assignment.type || 'homework',
        points: assignment.points ?? '',
        notes: assignment.notes || '',
      });
    } else {
      setForm({
        title: '',
        class_id: classes[0]?.id || '',
        due_date: '',
        priority: 'medium',
        type: 'homework',
        points: '',
        notes: '',
      });
    }
  }, [assignment, open, classes]);

  const save = async () => {
    if (!form.title.trim()) return;
    setSaving(true);
    try {
      const cls = classes.find((c) => c.id === form.class_id);
      const payload = {
        title: form.title.trim(),
        class_id: form.class_id || '',
        class_name: cls?.name || 'Uncategorized',
        due_date: form.due_date ? new Date(form.due_date).toISOString() : null,
        priority: form.priority,
        type: form.type,
        points: form.points === '' ? 0 : Number(form.points),
        notes: form.notes,
      };
      if (assignment) {
        await base44.entities.Assignment.update(assignment.id, payload);
      } else {
        await base44.entities.Assignment.create({ ...payload, source: 'manual', completed: false });
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
          <DialogTitle>{assignment ? 'Edit Assignment' : 'New Assignment'}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          {!assignment && templates.length > 0 && (
            <div className="space-y-1.5">
              <Label>Use a template</Label>
              <SheetSelect
                value=""
                onValueChange={(v) => v && applyTemplate(v)}
                placeholder="Apply a template..."
                options={templates.map((t) => ({ value: t.id, label: t.name }))}
              />
            </div>
          )}
          <div className="space-y-1.5">
            <Label>Assignment Title</Label>
            <Input
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="e.g. Chapter 5 homework"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Class / Course</Label>
              <SheetSelect
                value={form.class_id}
                onValueChange={(v) => setForm({ ...form, class_id: v })}
                placeholder="Select class"
                options={classes.map((c) => ({ value: c.id, label: c.name }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Due Date</Label>
              <Input
                type="datetime-local"
                value={form.due_date}
                onChange={(e) => setForm({ ...form, due_date: e.target.value })}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Priority</Label>
              <SheetSelect
                value={form.priority}
                onValueChange={(v) => setForm({ ...form, priority: v })}
                options={PRIORITIES.map((p) => ({ value: p, label: p[0].toUpperCase() + p.slice(1) }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Type</Label>
              <SheetSelect
                value={form.type}
                onValueChange={(v) => setForm({ ...form, type: v })}
                options={TYPES.map((t) => ({ value: t, label: t[0].toUpperCase() + t.slice(1) }))}
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
              placeholder="Additional notes..."
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" className="h-11 md:h-9" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button className="h-11 md:h-9" onClick={save} disabled={saving || !form.title.trim()}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}