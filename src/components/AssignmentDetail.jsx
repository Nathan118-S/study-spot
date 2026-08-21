import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog';
import { Slider } from '@/components/ui/slider';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Pencil, Trash2, AlertTriangle, CalendarClock, CheckCircle2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { format, parseISO, isPast } from 'date-fns';

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

export default function AssignmentDetail({
  open, onOpenChange, assignment, classes, onUpdated, onEdit, onDelete,
}) {
  const [progress, setProgress] = useState(0);
  const [notes, setNotes] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setProgress(assignment?.progress ?? 0);
    setNotes(assignment?.notes ?? '');
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

  const persistProgress = async (value) => {
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, { progress: value });
      onUpdated?.({ ...assignment, progress: value });
    } finally {
      setSaving(false);
    }
  };

  const toggleComplete = async () => {
    const completed = !assignment.completed;
    const next = { completed, progress: completed ? 100 : (assignment.progress >= 100 ? 0 : assignment.progress) };
    setSaving(true);
    try {
      await base44.entities.Assignment.update(assignment.id, next);
      onUpdated?.({ ...assignment, ...next });
      setProgress(next.progress);
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
            <span className="text-sm font-medium">Progress</span>
            <span className={cn('text-sm font-semibold', progress >= 100 && 'text-emerald-500')}>
              {progress}%
            </span>
          </div>
          <Slider
            value={[progress]}
            min={0}
            max={100}
            step={5}
            disabled={saving}
            onValueChange={([v]) => setProgress(v)}
            onValueCommit={([v]) => persistProgress(v)}
          />
          <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>

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