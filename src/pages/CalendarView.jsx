import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  startOfMonth, endOfMonth, startOfWeek, endOfWeek, eachDayOfInterval,
  format, isSameMonth, isSameDay, parseISO, isToday,
} from 'date-fns';

const PRIORITY_DOT = { high: 'bg-red-500', medium: 'bg-amber-500', low: 'bg-emerald-500' };

export default function CalendarView() {
  const [assignments, setAssignments] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [cursor, setCursor] = useState(new Date());
  const [dragId, setDragId] = useState(null);
  const [overKey, setOverKey] = useState(null);

  const load = useCallback(async () => {
    const [a, c] = await Promise.all([
      base44.entities.Assignment.list('-due_date', 500),
      base44.entities.Class.list(),
    ]);
    setAssignments(a);
    setClasses(c);
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const byDay = useMemo(() => {
    const map = {};
    for (const a of assignments) {
      if (!a.due_date) continue;
      const key = format(parseISO(a.due_date), 'yyyy-MM-dd');
      if (!map[key]) map[key] = [];
      map[key].push(a);
    }
    return map;
  }, [assignments]);

  const classColor = (id) => classes.find((c) => c.id === id)?.color || '#94a3b8';

  const reschedule = async (id, day) => {
    const a = assignments.find((x) => x.id === id);
    if (!a || !a.due_date) return;
    const old = parseISO(a.due_date);
    const next = new Date(day);
    next.setHours(old.getHours(), old.getMinutes(), old.getSeconds(), 0);
    const due_date = next.toISOString();
    setAssignments((prev) => prev.map((x) => (x.id === id ? { ...x, due_date } : x)));
    await base44.entities.Assignment.update(id, { due_date });
  };

  const days = useMemo(() => {
    const start = startOfWeek(startOfMonth(cursor), { weekStartsOn: 0 });
    const end = endOfWeek(endOfMonth(cursor), { weekStartsOn: 0 });
    return eachDayOfInterval({ start, end });
  }, [cursor]);

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Calendar</h1>
          <p className="text-muted-foreground text-sm">Assignments plotted on their due dates — drag any item to another day to reschedule it.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => setCursor(new Date())} title="Today">
            <span className="text-xs font-medium">Today</span>
          </Button>
          <Button variant="outline" size="icon" onClick={() => setCursor((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="font-semibold w-36 text-center">{format(cursor, 'MMMM yyyy')}</span>
          <Button variant="outline" size="icon" onClick={() => setCursor((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-7 gap-px bg-border rounded-lg overflow-hidden border border-border">
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((d) => (
          <div key={d} className="bg-muted py-2 text-center text-xs font-medium text-muted-foreground">
            {d}
          </div>
        ))}
        {days.map((day) => {
          const key = format(day, 'yyyy-MM-dd');
          const items = byDay[key] || [];
          const inMonth = isSameMonth(day, cursor);
          return (
            <div
              key={key}
              onDragOver={(e) => { e.preventDefault(); setOverKey(key); }}
              onDragLeave={() => setOverKey((k) => (k === key ? null : k))}
              onDrop={(e) => {
                e.preventDefault();
                setOverKey(null);
                const id = e.dataTransfer.getData('text/plain');
                if (id) reschedule(id, day);
              }}
              className={cn(
                'bg-card min-h-[96px] p-1.5 flex flex-col gap-1 transition-colors',
                !inMonth && 'opacity-40',
                isToday(day) && 'ring-2 ring-primary ring-inset',
                overKey === key && 'bg-primary/10 ring-2 ring-primary ring-inset'
              )}
            >
              <span className={cn('text-xs', isToday(day) ? 'font-bold text-primary' : 'text-muted-foreground')}>
                {format(day, 'd')}
              </span>
              <div className="space-y-1 overflow-hidden">
                {items.slice(0, 3).map((a) => (
                  <div
                    key={a.id}
                    draggable
                    onDragStart={(e) => { e.dataTransfer.setData('text/plain', a.id); setDragId(a.id); }}
                    onDragEnd={() => setDragId(null)}
                    className={cn(
                      'flex items-center gap-1 text-xs px-1 py-0.5 rounded truncate cursor-grab active:cursor-grabbing',
                      dragId === a.id && 'opacity-50'
                    )}
                    style={{ background: classColor(a.class_id) + '22' }}
                    title={a.title}
                  >
                    <span className={cn('w-1.5 h-1.5 rounded-full shrink-0', PRIORITY_DOT[a.priority])} />
                    <span className="truncate">{a.title}</span>
                  </div>
                ))}
                {items.length > 3 && (
                  <p className="text-xs text-muted-foreground px-1">+{items.length - 3} more</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}