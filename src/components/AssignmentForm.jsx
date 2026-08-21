import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
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
    notes: '',
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (assignment) {
      setForm({
        title: assignment.title || '',
        class_id: assignment.class_id || '',
        due_date: toLocalInput(assignment.due_date),
        priority: assignment.priority || 'medium',
        type: assignment.type || 'homework',
        notes: assignment.notes || '',
      });
    } else {
      setForm({
        title: '',
        class_id: classes[0]?.id || '',
        due_date: '',
        priority: 'medium',
        type: 'homework',
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
              <Select value={form.class_id} onValueChange={(v) => setForm({ ...form, class_id: v })}>
                <SelectTrigger>
                  <SelectValue placeholder="Select class" />
                </SelectTrigger>
                <SelectContent>
                  {classes.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
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
              <Select value={form.priority} onValueChange={(v) => setForm({ ...form, priority: v })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PRIORITIES.map((p) => (
                    <SelectItem key={p} value={p} className="capitalize">
                      {p}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Type</Label>
              <Select value={form.type} onValueChange={(v) => setForm({ ...form, type: v })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TYPES.map((t) => (
                    <SelectItem key={t} value={t} className="capitalize">
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
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
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={save} disabled={saving || !form.title.trim()}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}