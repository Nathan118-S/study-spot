import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import AssignmentForm from '@/components/AssignmentForm';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { Plus, RefreshCw, Trash2, Pencil, AlertTriangle, Loader2, Inbox } from 'lucide-react';
import { cn } from '@/lib/utils';
import { format, parseISO, isToday, isPast, isThisWeek } from 'date-fns';

const PRIORITY_BAR = { high: 'bg-red-500', medium: 'bg-amber-500', low: 'bg-emerald-500' };
const PRIORITY_RANK = { high: 3, medium: 2, low: 1 };
const PRIORITY_LABEL = { high: 'High', medium: 'Medium', low: 'Low' };
const TYPE_LABEL = {
  homework: 'Homework', project: 'Project', quiz: 'Quiz', test: 'Test', reading: 'Reading', other: 'Other',
};
const SOURCE_LABEL = { manual: 'Manual', google_calendar: 'Calendar', google_classroom: 'Classroom' };

export default function Dashboard() {
  const { user } = useAuth();
  const [assignments, setAssignments] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncMsg, setSyncMsg] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [filters, setFilters] = useState({ class: 'all', due: 'all', priority: 'all', type: 'all' });
  const [sort, setSort] = useState('soonest');

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

  const runSync = useCallback(
    async (silent = false) => {
      setSyncing(true);
      if (!silent) setSyncMsg('Syncing...');
      try {
        const [calRes, clsRes] = await Promise.allSettled([
          base44.functions.invoke('syncGoogleCalendar', {}),
          base44.functions.invoke('syncGoogleClassroom', {}),
        ]);
        const parts = [];
        if (calRes.status === 'fulfilled') parts.push(`Calendar: ${calRes.value.data?.imported ?? 0} new`);
        else parts.push('Calendar: not connected');
        if (clsRes.status === 'fulfilled') parts.push(`Classroom: ${clsRes.value.data?.imported ?? 0} new`);
        else parts.push('Classroom: not connected');
        if (!silent) setSyncMsg(parts.join(' · '));
        await load();
        await base44.auth.updateMe({ last_sync: new Date().toISOString() });
      } catch (e) {
        if (!silent) setSyncMsg('Sync failed: ' + (e.message || 'error'));
      } finally {
        setSyncing(false);
      }
    },
    [load]
  );

  // Auto-refresh Google data if last sync was more than 6 hours ago.
  useEffect(() => {
    if (!user) return;
    const last = user.data?.last_sync ? new Date(user.data.last_sync) : null;
    if (!last || Date.now() - last.getTime() > 6 * 60 * 60 * 1000) {
      runSync(true);
    }
  }, [user, runSync]);

  const toggleComplete = async (a) => {
    await base44.entities.Assignment.update(a.id, { completed: !a.completed });
    setAssignments((prev) => prev.map((x) => (x.id === a.id ? { ...x, completed: !x.completed } : x)));
  };

  const remove = async (a) => {
    await base44.entities.Assignment.delete(a.id);
    setAssignments((prev) => prev.filter((x) => x.id !== a.id));
  };

  const filtered = useMemo(() => {
    let list = [...assignments];
    if (filters.class !== 'all') list = list.filter((a) => a.class_id === filters.class);
    if (filters.priority !== 'all') list = list.filter((a) => a.priority === filters.priority);
    if (filters.type !== 'all') list = list.filter((a) => a.type === filters.type);
    if (filters.due === 'overdue')
      list = list.filter((a) => a.due_date && isPast(parseISO(a.due_date)) && !a.completed);
    else if (filters.due === 'today') list = list.filter((a) => a.due_date && isToday(parseISO(a.due_date)));
    else if (filters.due === 'week')
      list = list.filter((a) => a.due_date && isThisWeek(parseISO(a.due_date), { weekStartsOn: 1 }));

    list.sort((a, b) => {
      if (sort === 'soonest') {
        const da = a.due_date ? new Date(a.due_date).getTime() : Infinity;
        const db = b.due_date ? new Date(b.due_date).getTime() : Infinity;
        return da - db;
      }
      if (sort === 'priority') return PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority];
      if (sort === 'class') return (a.class_name || '').localeCompare(b.class_name || '');
      return 0;
    });
    return list;
  }, [assignments, filters, sort]);

  const stats = useMemo(() => {
    const now = new Date();
    const overdue = assignments.filter((a) => a.due_date && isPast(parseISO(a.due_date)) && !a.completed).length;
    const today = assignments.filter((a) => a.due_date && isToday(parseISO(a.due_date))).length;
    const completed = assignments.filter((a) => a.completed).length;
    return { total: assignments.length, overdue, today, completed };
  }, [assignments]);

  const classColor = (id) => classes.find((c) => c.id === id)?.color || '#94a3b8';

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground text-sm">All your assignments, synced and manual, in one place.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => runSync(false)} disabled={syncing}>
            {syncing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <RefreshCw className="h-4 w-4 mr-2" />}
            Sync Now
          </Button>
          <Button
            onClick={() => {
              setEditing(null);
              setShowForm(true);
            }}
          >
            <Plus className="h-4 w-4 mr-2" /> Add Assignment
          </Button>
        </div>
      </div>

      {syncMsg && <p className="text-sm text-muted-foreground">{syncMsg}</p>}

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatCard label="Total" value={stats.total} />
        <StatCard label="Overdue" value={stats.overdue} accent="text-red-500" />
        <StatCard label="Due Today" value={stats.today} accent="text-amber-500" />
        <StatCard label="Completed" value={stats.completed} accent="text-emerald-500" />
      </div>

      <div className="flex flex-wrap gap-2 items-center">
        <Select value={filters.class} onValueChange={(v) => setFilters({ ...filters, class: v })}>
          <SelectTrigger className="w-[150px]"><SelectValue placeholder="Class" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All classes</SelectItem>
            {classes.map((c) => (
              <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={filters.due} onValueChange={(v) => setFilters({ ...filters, due: v })}>
          <SelectTrigger className="w-[150px]"><SelectValue placeholder="Due" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All dates</SelectItem>
            <SelectItem value="overdue">Overdue</SelectItem>
            <SelectItem value="today">Due today</SelectItem>
            <SelectItem value="week">This week</SelectItem>
          </SelectContent>
        </Select>
        <Select value={filters.priority} onValueChange={(v) => setFilters({ ...filters, priority: v })}>
          <SelectTrigger className="w-[140px]"><SelectValue placeholder="Priority" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All priorities</SelectItem>
            <SelectItem value="high">High</SelectItem>
            <SelectItem value="medium">Medium</SelectItem>
            <SelectItem value="low">Low</SelectItem>
          </SelectContent>
        </Select>
        <Select value={filters.type} onValueChange={(v) => setFilters({ ...filters, type: v })}>
          <SelectTrigger className="w-[150px]"><SelectValue placeholder="Type" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All types</SelectItem>
            {Object.keys(TYPE_LABEL).map((t) => (
              <SelectItem key={t} value={t}>{TYPE_LABEL[t]}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="ml-auto flex items-center gap-2">
          <span className="text-xs text-muted-foreground">Sort</span>
          <Select value={sort} onValueChange={setSort}>
            <SelectTrigger className="w-[160px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="soonest">Soonest due</SelectItem>
              <SelectItem value="priority">Highest priority</SelectItem>
              <SelectItem value="class">Class name</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : filtered.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Inbox className="h-10 w-10 text-muted-foreground mb-2" />
          <p className="text-muted-foreground">No assignments match your filters.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((a) => {
            const overdue = a.due_date && isPast(parseISO(a.due_date)) && !a.completed;
            return (
              <div
                key={a.id}
                className={cn(
                  'flex items-stretch rounded-lg border bg-card overflow-hidden',
                  overdue && 'border-red-500/60 bg-red-500/5'
                )}
              >
                <div className={cn('w-1.5 shrink-0', PRIORITY_BAR[a.priority])} />
                <div className="flex items-center px-3">
                  <Checkbox checked={!!a.completed} onCheckedChange={() => toggleComplete(a)} />
                </div>
                <div className="flex-1 py-3 pr-3 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span
                      className={cn('font-medium', a.completed && 'line-through text-muted-foreground')}
                    >
                      {a.title}
                    </span>
                    {a.source !== 'manual' && (
                      <Badge variant="secondary" className="text-xs">{SOURCE_LABEL[a.source]}</Badge>
                    )}
                    {overdue && (
                      <Badge variant="destructive" className="text-xs">
                        <AlertTriangle className="h-3 w-3 mr-1" /> Overdue
                      </Badge>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-1 text-xs text-muted-foreground flex-wrap">
                    <span className="flex items-center gap-1">
                      <span className="w-2 h-2 rounded-full" style={{ background: classColor(a.class_id) }} />
                      {a.class_name || 'Uncategorized'}
                    </span>
                    <span>·</span>
                    <span>{TYPE_LABEL[a.type]}</span>
                    <span>·</span>
                    <span className={cn(overdue && 'text-red-500 font-medium')}>
                      {a.due_date ? format(parseISO(a.due_date), 'MMM d, h:mm a') : 'No due date'}
                    </span>
                  </div>
                </div>
                <div className="flex items-center pr-2 gap-1">
                  <Button variant="ghost" size="icon" onClick={() => { setEditing(a); setShowForm(true); }} title="Edit">
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => remove(a)} title="Delete">
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <AssignmentForm
        open={showForm}
        onOpenChange={setShowForm}
        assignment={editing}
        classes={classes}
        onSaved={load}
      />
    </div>
  );
}

function StatCard({ label, value, accent }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn('text-2xl font-bold mt-1', accent)}>{value}</p>
    </div>
  );
}