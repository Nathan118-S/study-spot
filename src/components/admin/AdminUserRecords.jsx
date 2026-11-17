import React, { useCallback, useEffect, useState } from 'react';
import { api } from '@/api/client';
import { useToast } from '@/components/ui/use-toast';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog';
import SheetSelect from '@/components/SheetSelect';
import { ConfirmButton, fmtDate } from '@/components/admin/AdminUserParts';
import { Loader2, Trash2, ChevronRight, Plus, X, CheckCircle2, Circle } from 'lucide-react';

const PRIORITIES = ['low', 'medium', 'high'].map((v) => ({ value: v, label: v[0].toUpperCase() + v.slice(1) }));
const TYPES = ['homework', 'project', 'quiz', 'test', 'reading', 'other'].map((v) => ({ value: v, label: v[0].toUpperCase() + v.slice(1) }));

export const RECORD_TYPES = [
  { value: 'assignments', label: 'Assignments', stat: 'assignments', singular: 'assignment' },
  { value: 'classes', label: 'Classes', stat: 'classes', singular: 'class' },
  { value: 'assignment_templates', label: 'Templates', stat: 'templates', singular: 'template' },
  { value: 'study_sessions', label: 'Study sessions', stat: 'study_sessions', singular: 'study session' },
  { value: 'notifications', label: 'Notifications', stat: 'notifications', singular: 'notification' },
];

// Editable fields per record type (mirrors the server's allow-list).
const FIELDS = {
  assignments: [
    { key: 'title', label: 'Title', kind: 'text' },
    { key: 'class_id', label: 'Class', kind: 'class' },
    { key: 'due_date', label: 'Due', kind: 'datetime' },
    { key: 'priority', label: 'Priority', kind: 'select', options: PRIORITIES },
    { key: 'type', label: 'Type', kind: 'select', options: TYPES },
    { key: 'completed', label: 'Completed', kind: 'bool' },
    { key: 'progress', label: 'Progress (%)', kind: 'number' },
    { key: 'points', label: 'Points', kind: 'number' },
    { key: 'score', label: 'Score (blank = not graded)', kind: 'number', nullable: true },
    { key: 'description', label: 'Description', kind: 'textarea' },
    { key: 'notes', label: 'Notes', kind: 'textarea' },
    { key: 'subtasks', label: 'Subtasks', kind: 'subtasks' },
  ],
  classes: [
    { key: 'name', label: 'Name', kind: 'text' },
    { key: 'teacher_name', label: 'Teacher', kind: 'text' },
    { key: 'color', label: 'Color', kind: 'color' },
    { key: 'best_streak', label: 'Best streak', kind: 'number' },
  ],
  assignment_templates: [
    { key: 'name', label: 'Template name', kind: 'text' },
    { key: 'title', label: 'Assignment title', kind: 'text' },
    { key: 'type', label: 'Type', kind: 'select', options: TYPES },
    { key: 'priority', label: 'Priority', kind: 'select', options: PRIORITIES },
    { key: 'points', label: 'Points', kind: 'number' },
    { key: 'notes', label: 'Notes', kind: 'textarea' },
  ],
  study_sessions: [
    { key: 'duration_minutes', label: 'Minutes', kind: 'number' },
    {
      key: 'session_type', label: 'Type', kind: 'select',
      options: [{ value: 'focus', label: 'Focus' }, { value: 'break', label: 'Break' }],
    },
    { key: 'note', label: 'Note', kind: 'textarea' },
  ],
  notifications: [
    { key: 'title', label: 'Title', kind: 'text' },
    { key: 'content', label: 'Message', kind: 'textarea' },
    { key: 'read', label: 'Read', kind: 'bool' },
  ],
};

const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v));

function summary(type, r) {
  switch (type) {
    case 'assignments':
      return {
        title: r.title,
        sub: [r.class_name, r.due_date && `Due ${fmtDate(r.due_date)}`, r.score != null && `Score ${num(r.score)}/${num(r.points)}`]
          .filter(Boolean).join(' · '),
        done: r.completed,
      };
    case 'classes':
      return { title: r.name, sub: [r.teacher_name, r.source !== 'manual' && r.source].filter(Boolean).join(' · '), color: r.color };
    case 'assignment_templates':
      return { title: r.name, sub: [r.title, r.type].filter(Boolean).join(' · ') };
    case 'study_sessions':
      return {
        title: `${num(r.duration_minutes)} min ${r.session_type}`,
        sub: [r.assignment_title || r.class_name, fmtDate(r.completed_at || r.created_at)].filter(Boolean).join(' · '),
      };
    case 'notifications':
      return { title: r.title, sub: `${r.read ? 'Read' : 'Unread'} · ${fmtDate(r.created_at)}`, done: r.read };
    default:
      return { title: r.id, sub: '' };
  }
}

// <input type="datetime-local"> wants local "YYYY-MM-DDTHH:mm".
function toLocalInput(v) {
  if (!v) return '';
  const d = new Date(v);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function initialForm(type, r) {
  const f = {};
  for (const field of FIELDS[type]) {
    const v = r[field.key];
    if (field.kind === 'datetime') f[field.key] = toLocalInput(v);
    else if (field.kind === 'number') f[field.key] = v === null || v === undefined ? '' : String(num(v));
    else if (field.kind === 'subtasks') f[field.key] = Array.isArray(v) ? v : [];
    else if (field.kind === 'bool') f[field.key] = !!v;
    else f[field.key] = v ?? '';
  }
  return f;
}

function changesOf(type, record, form, classes) {
  const out = {};
  const before = initialForm(type, record);
  for (const field of FIELDS[type]) {
    const k = field.key;
    if (JSON.stringify(before[k]) === JSON.stringify(form[k])) continue;
    if (field.kind === 'datetime') out[k] = form[k] ? new Date(form[k]).toISOString() : null;
    else if (field.kind === 'number') out[k] = form[k] === '' ? (field.nullable ? null : 0) : Number(form[k]);
    else if (field.kind === 'class') {
      out.class_id = form[k] || null;
      out.class_name = classes.find((c) => c.id === form[k])?.name || null;
    } else out[k] = form[k];
  }
  return out;
}

function RecordEditor({ type, record, classes, onClose, onSave }) {
  const [form, setForm] = useState(() => (record ? initialForm(type, record) : {}));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [newSubtask, setNewSubtask] = useState('');
  useEffect(() => {
    if (record) setForm(initialForm(type, record));
    setError('');
  }, [record, type]);

  if (!record) return null;
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const meta = RECORD_TYPES.find((t) => t.value === type);

  const save = async () => {
    const changes = changesOf(type, record, form, classes);
    if (!Object.keys(changes).length) return onClose();
    setSaving(true);
    setError('');
    try {
      await onSave(changes);
      onClose();
    } catch (e) {
      if (!e?.elevationCancelled) setError(e.message || 'Could not save');
    } finally {
      setSaving(false);
    }
  };

  const field = (f) => {
    const id = `rec-${f.key}`;
    const v = form[f.key];
    let control;
    switch (f.kind) {
      case 'textarea':
        control = <Textarea id={id} value={v} onChange={(e) => set(f.key, e.target.value)} rows={3} />;
        break;
      case 'number':
        control = <Input id={id} type="number" step="any" value={v} onChange={(e) => set(f.key, e.target.value)} />;
        break;
      case 'datetime':
        control = <Input id={id} type="datetime-local" value={v} onChange={(e) => set(f.key, e.target.value)} />;
        break;
      case 'color':
        control = (
          <div className="flex items-center gap-2">
            <input
              id={id}
              type="color"
              value={/^#[0-9a-f]{6}$/i.test(v) ? v : '#6366f1'}
              onChange={(e) => set(f.key, e.target.value)}
              className="h-9 w-12 rounded-md border bg-transparent"
            />
            <Input value={v} onChange={(e) => set(f.key, e.target.value)} className="flex-1 font-mono" aria-label="Color hex" />
          </div>
        );
        break;
      case 'bool':
        return (
          <div key={f.key} className="flex items-center justify-between gap-3">
            <Label htmlFor={id}>{f.label}</Label>
            <Switch id={id} checked={!!v} onCheckedChange={(c) => set(f.key, c)} />
          </div>
        );
      case 'select':
        control = <SheetSelect value={v} onValueChange={(x) => set(f.key, x)} options={f.options} triggerClassName="w-full" title={f.label} />;
        break;
      case 'class':
        control = (
          <SheetSelect
            value={v || 'none'}
            onValueChange={(x) => set(f.key, x === 'none' ? '' : x)}
            triggerClassName="w-full"
            title="Class"
            options={[{ value: 'none', label: record.class_name && !record.class_id ? record.class_name : 'No class' },
              ...classes.map((c) => ({ value: c.id, label: c.name }))]}
          />
        );
        break;
      case 'subtasks':
        control = (
          <div className="space-y-2">
            {v.map((s, i) => (
              <div key={i} className="flex items-center gap-2">
                <Checkbox
                  checked={!!s.done}
                  onCheckedChange={(c) => set(f.key, v.map((x, j) => (j === i ? { ...x, done: !!c } : x)))}
                  aria-label="Done"
                />
                <Input
                  value={s.text || ''}
                  onChange={(e) => set(f.key, v.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))}
                  className="flex-1 h-9"
                />
                <Button variant="ghost" size="icon" onClick={() => set(f.key, v.filter((_, j) => j !== i))} aria-label="Remove subtask">
                  <X className="h-4 w-4" />
                </Button>
              </div>
            ))}
            <div className="flex gap-2">
              <Input
                value={newSubtask}
                onChange={(e) => setNewSubtask(e.target.value)}
                placeholder="Add a subtask"
                className="flex-1 h-9"
              />
              <Button
                variant="secondary"
                size="sm"
                className="h-9"
                disabled={!newSubtask.trim()}
                onClick={() => {
                  set(f.key, [...v, { text: newSubtask.trim(), done: false }]);
                  setNewSubtask('');
                }}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>
        );
        break;
      default:
        control = <Input id={id} value={v} onChange={(e) => set(f.key, e.target.value)} />;
    }
    return (
      <div key={f.key} className="space-y-1.5">
        <Label htmlFor={id} className="text-xs text-muted-foreground">{f.label}</Label>
        {control}
      </div>
    );
  };

  return (
    <Dialog open={!!record} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Edit {meta?.singular}</DialogTitle>
          <DialogDescription>Created {fmtDate(record.created_at)}</DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          {FIELDS[type].map(field)}
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter className="gap-2">
          <Button variant="outline" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={save} disabled={saving}>
            {saving && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Browse, edit and delete one account's records, one type at a time. */
export default function AdminUserRecords({ userId, stats, onChanged }) {
  const { toast } = useToast();
  const [type, setType] = useState('assignments');
  const [records, setRecords] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.functions.invoke('adminListRecords', { userId, type });
      setRecords(res.data.records || []);
      if (type === 'assignments') {
        const c = await api.functions.invoke('adminListRecords', { userId, type: 'classes' });
        setClasses(c.data.records || []);
      }
    } catch (e) {
      if (!e?.elevationCancelled) toast({ title: 'Failed to load', description: e.message, variant: 'destructive' });
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, [userId, type, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const meta = RECORD_TYPES.find((t) => t.value === type);

  const run = async (fn, success) => {
    try {
      await fn();
      if (success) toast({ title: success });
      await load();
      onChanged?.();
    } catch (e) {
      if (!e?.elevationCancelled) toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    }
  };

  return (
    <section>
      <p className="ios-section-header">Data</p>
      <div className="flex items-center gap-2 pb-2">
        <SheetSelect
          value={type}
          onValueChange={(t) => {
            setEditing(null);
            setType(t);
          }}
          triggerClassName="flex-1 sm:w-[220px] sm:flex-none bg-card"
          title="Show"
          options={RECORD_TYPES.map((t) => ({ value: t.value, label: `${t.label} (${stats?.[t.stat] ?? 0})` }))}
        />
        <ConfirmButton
          title={`Delete all ${meta.label.toLowerCase()}?`}
          description={`This permanently deletes all ${records.length} ${meta.label.toLowerCase()} on this account. This cannot be undone.`}
          confirmLabel="Delete all"
          disabled={loading || records.length === 0}
          className="h-11 md:h-9 shrink-0"
          onConfirm={() =>
            run(async () => {
              const r = await api.functions.invoke('adminClearRecords', { userId, type });
              toast({ title: `Deleted ${r.data.deleted} ${meta.label.toLowerCase()}` });
            })
          }
        >
          <Trash2 className="h-4 w-4 mr-1" /> Delete all
        </ConfirmButton>
      </div>

      <div className="ios-list">
        {loading ? (
          <div className="flex justify-center py-8"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : records.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-muted-foreground">No {meta.label.toLowerCase()}.</p>
        ) : (
          records.map((r) => {
            const s = summary(type, r);
            return (
              <div key={r.id} className="flex items-center gap-1 pr-2">
                <button
                  type="button"
                  onClick={() => setEditing(r)}
                  className="flex-1 min-w-0 text-left px-4 py-2.5 hover:bg-accent/60 transition-colors flex items-center gap-3"
                >
                  {s.color ? (
                    <span className="h-3 w-3 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
                  ) : s.done !== undefined ? (
                    s.done ? <CheckCircle2 className="h-4 w-4 text-success shrink-0" /> : <Circle className="h-4 w-4 text-muted-foreground shrink-0" />
                  ) : null}
                  <div className="min-w-0 flex-1">
                    <p className="text-[15px] font-medium truncate">{s.title || 'Untitled'}</p>
                    {s.sub && <p className="text-[13px] text-muted-foreground truncate">{s.sub}</p>}
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground/60 shrink-0" />
                </button>
                <ConfirmButton
                  title={`Delete this ${meta.singular}?`}
                  description={`“${s.title || 'Untitled'}” will be permanently deleted.`}
                  confirmLabel="Delete"
                  variant="ghost"
                  size="icon"
                  onConfirm={() => run(() => api.functions.invoke('adminDeleteRecord', { userId, type, id: r.id }), 'Deleted')}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                  <span className="sr-only">Delete</span>
                </ConfirmButton>
              </div>
            );
          })
        )}
      </div>

      <RecordEditor
        type={type}
        record={editing}
        classes={classes}
        onClose={() => setEditing(null)}
        onSave={async (changes) => {
          await api.functions.invoke('adminUpdateRecord', { userId, type, id: editing.id, changes });
          toast({ title: 'Saved' });
          await load();
          onChanged?.();
        }}
      />
    </section>
  );
}
