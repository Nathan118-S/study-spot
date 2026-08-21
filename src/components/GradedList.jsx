import React from 'react';
import { Badge } from '@/components/ui/badge';
import { Award } from 'lucide-react';
import { format, parseISO } from 'date-fns';
import { letterGrade, isGraded } from '@/lib/grading';
import { cn } from '@/lib/utils';

const GRADE_COLOR = {
  A: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
  B: 'bg-lime-100 text-lime-700 dark:bg-lime-900/40 dark:text-lime-300',
  C: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
  D: 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-300',
  F: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
};

export default function GradedList({ assignments, gradingScale, onSelect }) {
  const graded = assignments
    .filter(isGraded)
    .sort((a, b) => b.score / b.points - a.score / a.points);

  if (!graded.length) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <Award className="h-10 w-10 text-muted-foreground mb-2" />
        <p className="text-muted-foreground max-w-sm">
          No graded assignments yet. Open an assignment with points and enter a score to see its letter grade here.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {graded.map((a) => {
        const lg = letterGrade(a.score, a.points, gradingScale);
        const pct = Math.round((a.score / a.points) * 100);
        return (
          <button
            key={a.id}
            type="button"
            onClick={() => onSelect?.(a)}
            className="w-full flex items-center gap-3 rounded-lg border bg-card p-3 text-left hover:bg-accent/40 transition-colors"
          >
            <Badge
              variant="outline"
              className={cn('text-lg font-bold w-10 justify-center', GRADE_COLOR[(lg || '')[0]])}
            >
              {lg}
            </Badge>
            <div className="flex-1 min-w-0">
              <p className={cn('font-medium truncate', a.completed && 'line-through text-muted-foreground')}>
                {a.title}
              </p>
              <p className="text-xs text-muted-foreground truncate">
                {a.class_name || 'Uncategorized'} · {a.due_date ? format(parseISO(a.due_date), 'MMM d') : 'No due date'}
              </p>
            </div>
            <div className="text-right shrink-0">
              <p className="font-semibold">{a.score}/{a.points}</p>
              <p className="text-xs text-muted-foreground">{pct}%</p>
            </div>
          </button>
        );
      })}
    </div>
  );
}