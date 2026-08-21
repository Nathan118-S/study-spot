import React from 'react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Flame, BadgeCheck, ShieldAlert } from 'lucide-react';

const STATUS = {
  active: {
    label: 'Active',
    className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300',
    flame: 'text-orange-500',
  },
  atrisk: {
    label: 'At risk',
    className: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300',
    flame: 'text-amber-500',
  },
  broken: {
    label: 'Broken',
    className: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
    flame: 'text-muted-foreground',
  },
  empty: {
    label: 'No assignments',
    className: 'bg-muted text-muted-foreground',
    flame: 'text-muted-foreground',
  },
};

export default function StreakCard({ streak }) {
  const { class: cls, streak: count, status, total, completed, verified } = streak;
  const meta = STATUS[status] || STATUS.empty;
  const color = cls.color || '#94a3b8';
  const pct = total ? (completed / total) * 100 : 0;

  return (
    <div className="rounded-lg border bg-card p-4 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: color }} />
          <span className="font-medium truncate">{cls.name}</span>
          {verified ? (
            <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-4 gap-0.5 border-emerald-500/40 text-emerald-700 dark:text-emerald-300 shrink-0">
              <BadgeCheck className="h-3 w-3" /> Verified
            </Badge>
          ) : (
            <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-4 gap-0.5 text-muted-foreground shrink-0">
              <ShieldAlert className="h-3 w-3" /> Unverified
            </Badge>
          )}
        </div>
        <Badge variant="outline" className={cn('text-xs shrink-0', meta.className)}>
          {meta.label}
        </Badge>
      </div>

      <div className="flex items-center gap-3">
        <Flame className={cn('h-10 w-10', meta.flame)} />
        <div>
          <p className="text-3xl font-bold leading-none">{count}</p>
          <p className="text-xs text-muted-foreground">streak</p>
        </div>
        <div className="ml-auto text-right text-xs text-muted-foreground">
          {completed}/{total} done
        </div>
      </div>

      {total > 0 && (
        <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
          <div className="h-full bg-primary transition-all" style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  );
}