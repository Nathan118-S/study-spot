import React from 'react';
import { Badge } from '@/components/ui/badge';
import { AlertTriangle } from 'lucide-react';
import { format, parseISO, isPast } from 'date-fns';
import { cn } from '@/lib/utils';

const TYPE_LABEL = {
  homework: 'Homework',
  project: 'Project',
  quiz: 'Quiz',
  test: 'Test',
  reading: 'Reading',
  other: 'Other',
};

export default function BoardCard({ assignment, classColor, onClick }) {
  const overdue =
    assignment.due_date && isPast(parseISO(assignment.due_date)) && !assignment.completed;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'w-full text-left rounded-lg border bg-card p-3 shadow-sm transition-shadow hover:shadow-md',
        overdue && 'border-red-500/60'
      )}
    >
      <div className="flex items-stretch gap-2">
        <span className="w-1.5 self-stretch rounded-full shrink-0" style={{ background: classColor }} />
        <div className="min-w-0 flex-1">
          <p
            className={cn(
              'font-medium text-sm truncate',
              assignment.completed && 'line-through text-muted-foreground'
            )}
          >
            {assignment.title}
          </p>
          <p className="text-xs text-muted-foreground mt-0.5 truncate">
            {assignment.class_name || 'Uncategorized'}
          </p>
          <div className="flex items-center gap-1.5 mt-1.5 text-xs text-muted-foreground flex-wrap">
            <span>{TYPE_LABEL[assignment.type]}</span>
            {assignment.due_date && (
              <>
                <span>·</span>
                <span className={cn(overdue && 'text-red-500 font-medium')}>
                  {format(parseISO(assignment.due_date), 'MMM d')}
                </span>
              </>
            )}
            {overdue && (
              <Badge variant="destructive" className="text-[10px] px-1 py-0 h-4">
                <AlertTriangle className="h-2.5 w-2.5 mr-0.5" /> Overdue
              </Badge>
            )}
          </div>
        </div>
      </div>
    </button>
  );
}