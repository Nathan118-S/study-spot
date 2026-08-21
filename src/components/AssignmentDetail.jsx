import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog';
import { Slider } from '@/components/ui/slider';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Pencil, Trash2, AlertTriangle, CalendarClock, CheckCircle2, Plus, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { format, parseISO, isPast } from 'date-fns';
import { letterGrade } from '@/lib/grading';
import { Input } from '@/components/ui/input';
import ClassroomAttachments from '@/components/ClassroomAttachments';

const PRIORITY_LABEL = { high: 'High', medium: 'Medium', low: 'Low' };
const PRIORITY_BADGE = {
  high: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
  medium: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  low: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
};
const TYPE_LABEL = {
  homework: 'Homework', project: 'Project', quiz: 'Quiz', test: 'Test', reading: 'Reading', other: 'Other',
};
const SOURCE_LABEL = { manual: 'Manual', google_calendar: 'Calendar', google_classroom: 'Classroom' };
const GRADE_COLOR = {
  A: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  B: 'bg-lime-100 text-lime-700 dark:bg-lime-900/40 dark:text-lime-300',
  C: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  D: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300',
  F: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
};
const gradeColor = (l) => GRADE_COLOR[(l || '')[0]] || '';

export default function AssignmentDetail({
  open, onOpenChange, assignment, classes, gradingScale, onUpdated, onEdit, onDelete,
}) {
  const [progress, setProgress] = useState(0);
  const [notes, setNotes] = useState('');
  const [score, setScore] = useState('');
  const [subtasks, setSubtasks] = useState([]);
  const [newSub, setNewSub] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setProgress(assignment?.progress ?? 0);
    setNotes(assignment?.notes ?? '');
    setScore(assignment?.score ?? '');
    setSubtasks(assignment?.subtasks ?? []);
    setNewSub('');
  }, [assignment]);

  if (!assignment) return null;

  const classColor = classes.find((c) => c.id === assignment.class_id)?.color || '#94a3b8';
  const overdue = assignment.due_date && isPast(parseISO(assignment.due_date)) && !assignment.completed;

  const persistNotes = async (value) => {
    if (value === (assignment.notes ?? '')) return;
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, { notes: value });
      onUpdated?.({ ...assignment, notes: value });
    } finally {
      setSaving(false);
    }
  };

  const persistScore = async (raw) => {
    const value = raw === '' ? undefined : Number(raw);
    if (raw !== '' && Number.isNaN(value)) return;
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, { score: value });
      onUpdated?.({ ...assignment, score: value });
    } finally {
      setSaving(false);
    }
  };

  const persistProgress = async (value) => {
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, { progress: value });
      onUpdated?.({ ...assignment, progress: value });
    } finally {
      setSaving(false);
    }
  };

  const computeProgress = (list) =>
    list.length ? Math.round((list.filter((s) => s.done).length / list.length) * 100) : 0;

  const persistSubtasks = async (list) => {
    const nextProgress = computeProgress(list);
    setProgress(nextProgress);
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, { subtasks: list, progress: nextProgress });
      onUpdated?.({ ...assignment, subtasks: list, progress: nextProgress });
    } finally {
      setSaving(false);
    }
  };

  const addSubtask = () => {
    const text = newSub.trim();
    if (!text) return;
    const list = [...subtasks, { text, done: false }];
    setSubtasks(list);
    setNewSub('');
    persistSubtasks(list);
  };

  const toggleSubtask = (i) => {
    const list = subtasks.map((s, idx) => (idx === i ? { ...s, done: !s.done } : s));
    setSubtasks(list);
    persistSubtasks(list);
  };

  const removeSubtask = (i) => {
    const list = subtasks.filter((_, idx) => idx !== i);
    setSubtasks(list);
    persistSubtasks(list);
  };

  const toggleComplete = async () => {
    const completed = !assignment.completed;
    let nextProgress;
    let nextSubtasks = subtasks;
    if (subtasks.length > 0) {
      nextSubtasks = subtasks.map((s) => ({ ...s, done: completed }));
      nextProgress = computeProgress(nextSubtasks);
      setSubtasks(nextSubtasks);
    } else {
      nextProgress = completed ? 100 : (assignment.progress >= 100 ? 0 : assignment.progress);
    }
    const next = subtasks.length > 0
      ? { completed, progress: nextProgress, subtasks: nextSubtasks }
      : { completed, progress: nextProgress };
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, next);
      onUpdated?.({ ...assignment, ...next });
      setProgress(nextProgress);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <span className="w-2.5 h-2.5 rounded-full" style={{ background: classColor }} />
            <span className="text-sm text-muted-foreground">{assignment.class_name || 'Uncategorized'}</span>
            {assignment.source !== 'manual' && (
              <Badge variant="secondary" className="text-xs">{SOURCE_LABEL[assignment.source]}</Badge>
            )}
          </div>
          <DialogTitle className={cn(assignment.completed && 'line-through text-muted-foreground')}>
            {assignment.title}
          </DialogTitle>
          {assignment.description && (
            <DialogDescription className="whitespace-pre-wrap pt-1">
              {assignment.description}
            </DialogDescription>
          )}
        </DialogHeader>

        <div className="flex flex-wrap gap-2 text-sm">
          <Badge variant="outline" className={PRIORITY_BADGE[assignment.priority]}>
            {PRIORITY_LABEL[assignment.priority]} priority
          </Badge>
          <Badge variant="outline">{TYPE_LABEL[assignment.type]}</Badge>
          {typeof assignment.points === 'number' && assignment.points > 0 && (
            <Badge variant="outline">{assignment.points} pts</Badge>
          )}
          {overdue && (
            <Badge variant="destructive" className="text-xs">
              <AlertTriangle className="h-3 w-3 mr-1" /> Overdue
            </Badge>
          )}
        </div>

        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <CalendarClock className="h-4 w-4" />
          {assignment.due_date
            ? format(parseISO(assignment.due_date), 'EEEE, MMM d · h:mm a')
            : 'No due date'}
        </div>

        <ClassroomAttachments assignment={assignment} />

        <div className="rounded-lg border bg-muted/40 p-3">
          <p className="text-xs font-semibold text-muted-foreground mb-1">Notes</p>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            onBlur={(e) => persistNotes(e.target.value)}
            placeholder="Add notes about this assignment…"
            rows={4}
            className="w-full resize-y rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>

        <div className="rounded-lg border p-4 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">Subtasks</span>
            {subtasks.length > 0 && (
              <span className="text-xs text-muted-foreground">
                {subtasks.filter((s) => s.done).length}/{subtasks.length} done
              </span>
            )}
          </div>
          <div className="flex gap-2">
            <Input
              value={newSub}
              onChange={(e) => setNewSub(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addSubtask(); } }}
              placeholder="Add a subtask…"
              disabled={saving}
            />
            <Button size="icon" onClick={addSubtask} disabled={saving || !newSub.trim()} title="Add subtask">
              <Plus className="h-4 w-4" />
            </Button>
          </div>
          {subtasks.length > 0 && (
            <ul className="space-y-1.5">
              {subtasks.map((s, i) => (
                <li key={i} className="flex items-center gap-2 rounded-md border bg-muted/40 px-2 py-1.5">
                  <Checkbox checked={!!s.done} onCheckedChange={() => toggleSubtask(i)} disabled={saving} />
                  <span className={cn('flex-1 text-sm', s.done && 'line-through text-muted-foreground')}>
                    {s.text}
                  </span>
                  <Button variant="ghost" size="icon" onClick={() => removeSubtask(i)} disabled={saving} title="Remove">
                    <X className="h-4 w-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="rounded-lg border p-4 space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">Progress</span>
            <span className={cn('text-sm font-semibold', progress >= 100 && 'text-emerald-500')}>
              {progress}%
            </span>
          </div>
          {subtasks.length > 0 ? (
            <p className="text-xs text-muted-foreground">
              Progress is calculated from completed subtasks.
            </p>
          ) : (
            <Slider
              value={[progress]}
              min={0}
              max={100}
              step={5}
              disabled={saving}
              onValueChange={([v]) => setProgress(v)}
              onValueCommit={([v]) => persistProgress(v)}
            />
          )}
          <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

        {assignment.points > 0 && (
          <div className="rounded-lg border p-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Grade</span>
              {letterGrade(assignment.score, assignment.points, gradingScale) && (
                <Badge
                  variant="outline"
                  className={cn('text-base font-bold', gradeColor(letterGrade(assignment.score, assignment.points, gradingScale)))}
                >
                  {letterGrade(assignment.score, assignment.points, gradingScale)}
                </Badge>
              )}
            </div>
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min={0}
                max={assignment.points}
                step={0.5}
                value={score}
                onChange={(e) => setScore(e.target.value)}
                onBlur={(e) => persistScore(e.target.value)}
                disabled={saving}
                className="w-24"
              />
              <span className="text-sm text-muted-foreground">
                / {assignment.points} pts
                {typeof assignment.score === 'number' && ` · ${Math.round((assignment.score / assignment.points) * 100)}%`}
              </span>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between pt-1">
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <Checkbox checked={!!assignment.completed} onCheckedChange={toggleComplete} disabled={saving} />
            Mark as complete
          </label>
          <div className="flex gap-1">
            <Button variant="ghost" size="icon" onClick={() => onEdit?.(assignment)} title="Edit">
              <Pencil className="h-4 w-4" />
            </Button>
            <Button variant="ghost" size="icon" onClick={() => onDelete?.(assignment)} title="Delete">
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}