import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import AssignmentForm from '@/components/AssignmentForm';
import AssignmentDetail from '@/components/AssignmentDetail';
import { Button } from '@/components/ui/button';
import SheetSelect from '@/components/SheetSelect';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { Plus, RefreshCw, Trash2, Pencil, AlertTriangle, Loader2, Inbox, CheckSquare, Timer } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion } from 'framer-motion';
import { format, parseISO, isToday, isPast, isThisWeek } from 'date-fns';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { isGraded, DEFAULT_GRADING_SCALE } from '@/lib/grading';
import GradedList from '@/components/GradedList';
import PullToRefresh from '@/components/PullToRefresh';
import StudyTimer from '@/components/StudyTimer';
import { isDemoUser, getDemoAssignments, getDemoClasses, demoConnections } from '@/lib/demoData';

const PRIORITY_BAR = { high: 'bg-red-500', medium: 'bg-amber-500', low: 'bg-emerald-500' };
const PRIORITY_RANK = { high: 3, medium: 2, low: 1 };
const PRIORITY_LABEL = { high: 'High', medium: 'Medium', low: 'Low' };
const TYPE_LABEL = {
  homework: 'Homework', project: 'Project', quiz: 'Quiz', test: 'Test', reading: 'Reading', other: 'Other',
};
const SOURCE_LABEL = { manual: 'Manual', google_calendar: 'Calendar', google_classroom: 'Classroom', blackboard: 'Blackboard' };

// Greet once per full page load (login, refresh, or opening already logged in).
let greetedThisLoad = false;

export default function Dashboard() {
  const { user } = useAuth();
  const [assignments, setAssignments] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncMsg, setSyncMsg] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [detail, setDetail] = useState(null);
  const [filters, setFilters] = useState({ class: 'all', due: 'all', priority: 'all', type: 'all' });
  const [sort, setSort] = useState('soonest');
  const [view, setView] = useState('unfinished');
  const [gradingScale, setGradingScale] = useState(DEFAULT_GRADING_SCALE);
  const [selectMode, setSelectMode] = useState(false);
  const [selected, setSelected] = useState({});
  const [showTimer, setShowTimer] = useState(false);
  const [connections, setConnections] = useState({ calendar: false, classroom: false, blackboard: false });
  const [showWelcome, setShowWelcome] = useState(false);

  const load = useCallback(async () => {
    if (isDemoUser(user)) {
      setAssignments(getDemoAssignments());
      setClasses(getDemoClasses());
      return;
    }
    const [a, c] = await Promise.all([
      base44.entities.Assignment.list('-due_date', 500),
      base44.entities.Class.list(),
    ]);
    setAssignments(a);
    setClasses(c);
    try {
      const me = await base44.auth.me();
      const gs = me?.data?.grading_scale;
      if (gs && gs.length) setGradingScale(gs);
    } catch {}
  }, [user]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  useEffect(() => {
    if (isDemoUser(user)) {
      setConnections(demoConnections);
      return;
    }
    base44.functions
      .invoke('checkGoogleConnections', {})
      .then((res) => setConnections(res.data || {}))
      .catch(() => {});
  }, [user]);

  const runSync = useCallback(
    async (silent = false) => {
      setSyncing(true);
      if (!silent) setSyncMsg('Syncing...');
      try {
        const [calRes, clsRes, bbRes] = await Promise.allSettled([
          base44.functions.invoke('syncGoogleCalendar', {}),
          base44.functions.invoke('syncGoogleClassroom', {}),
          base44.functions.invoke('syncBlackboard', {}),
        ]);
        const parts = [];
        if (calRes.status === 'fulfilled') parts.push(`Calendar: ${calRes.value.data?.imported ?? 0} new`);
        else parts.push('Calendar: not connected');
        if (clsRes.status === 'fulfilled') parts.push(`Classroom: ${clsRes.value.data?.imported ?? 0} new`);
        else parts.push('Classroom: not connected');
        if (bbRes.status === 'fulfilled') parts.push(`Blackboard: ${bbRes.value.data?.imported ?? 0} new`);
        else parts.push('Blackboard: not connected');
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
    if (!user || isDemoUser(user)) return;
    const last = user.data?.last_sync ? new Date(user.data.last_sync) : null;
    if (!last || Date.now() - last.getTime() > 6 * 60 * 60 * 1000) {
      runSync(true);
    }
  }, [user, runSync]);

  // Welcome box once per full page load.
  useEffect(() => {
    if (!user || greetedThisLoad) return;
    greetedThisLoad = true;
    setShowWelcome(true);
    const t = setTimeout(() => setShowWelcome(false), 2800);
    return () => clearTimeout(t);
  }, [user]);

  const toggleComplete = async (a) => {
    const prev = assignments;
    const next = !a.completed;
    setAssignments((p) => p.map((x) => (x.id === a.id ? { ...x, completed: next } : x)));
    if (isDemoUser(user)) return;
    try {
      await base44.entities.Assignment.update(a.id, { completed: next });
      if (next && a.source === 'google_classroom' && a.external_id && user?.data?.sync_completion_to_classroom !== false) {
        base44.functions.invoke('syncCompletionToClassroom', { assignment_id: a.id }).catch(() => {});
      }
    } catch {
      setAssignments(prev);
    }
  };

  const remove = async (a) => {
    const prev = assignments;
    setAssignments((p) => p.filter((x) => x.id !== a.id));
    setDetail(null);
    if (isDemoUser(user)) return;
    try {
      await base44.entities.Assignment.delete(a.id);
    } catch {
      setAssignments(prev);
    }
  };

  const applyUpdate = (updated) => {
    setAssignments((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
    setDetail((d) => (d && d.id === updated.id ? updated : d));
  };

  const toggleSelect = (id) =>
    setSelected((prev) => {
      const next = { ...prev };
      if (next[id]) delete next[id];
      else next[id] = true;
      return next;
    });

  const bulkApply = async (patch) => {
    const ids = Object.keys(selected);
    if (!ids.length) return;
    const prev = assignments;
    setAssignments((p) => p.map((a) => (selected[a.id] ? { ...a, ...patch } : a)));
    setSelected({});
    if (isDemoUser(user)) return;
    try {
      await base44.entities.Assignment.bulkUpdate(ids.map((id) => ({ id, ...patch })));
    } catch {
      setAssignments(prev);
    }
  };

  const openEditFromDetail = (a) => {
    setDetail(null);
    setEditing(a);
    setShowForm(true);
  };

  const filtered = useMemo(() => {
    let list = [...assignments];
    if (view === 'unfinished') list = list.filter((a) => !a.completed && !isGraded(a));
    else if (view === 'completed') list = list.filter((a) => a.completed && !isGraded(a));
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
  }, [assignments, filters, sort, view]);

  const stats = useMemo(() => {
    const now = new Date();
    const overdue = assignments.filter((a) => a.due_date && isPast(parseISO(a.due_date)) && !a.completed).length;
    const today = assignments.filter((a) => a.due_date && isToday(parseISO(a.due_date))).length;
    const completed = assignments.filter((a) => a.completed).length;
    return { total: assignments.length, overdue, today, completed };
  }, [assignments]);

  const classColor = (id) => classes.find((c) => c.id === id)?.color || '#94a3b8';

  const selectedCount = Object.keys(selected).length;
  const allSelected = selectMode && filtered.length > 0 && filtered.every((a) => selected[a.id]);
  const toggleSelectAll = () => {
    if (allSelected) setSelected({});
    else {
      const next = {};
      filtered.forEach((a) => { next[a.id] = true; });
      setSelected(next);
    }
  };

  return (
    <div className="space-y-6">
      {showWelcome && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-card md:bg-black/30 md:backdrop-blur-sm p-0 md:px-4"
          onClick={() => setShowWelcome(false)}
        >
          <motion.div
            initial={{ scale: 0.8, opacity: 0, y: 10 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            transition={{ type: 'spring', stiffness: 260, damping: 20 }}
            className="rounded-none md:rounded-3xl bg-card border-0 md:border border-border shadow-none md:shadow-2xl px-6 md:px-10 py-16 md:py-14 text-center w-full max-w-none md:max-w-md min-h-screen md:min-h-0 flex flex-col items-center justify-center"
          >
            <motion.div
              initial={{ scale: 0, rotate: -20 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ delay: 0.15, type: 'spring', stiffness: 300, damping: 12 }}
              className="text-5xl mb-3"
            >
              👋
            </motion.div>
            <p className="text-3xl md:text-4xl font-heading font-bold tracking-tight">
              <span className="bg-gradient-to-r from-primary to-amber-500 bg-clip-text text-transparent">Hello</span>
              {(() => {
                const name = user?.full_name || (user?.email ? user.email.split('@')[0] : '');
                return name ? `, ${name}!` : '!';
              })()}
            </p>
            <p className="mt-2 text-sm text-muted-foreground">Welcome back to Study Spot</p>
          </motion.div>
        </div>
      )}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground text-sm">All your assignments, synced and manual, in one place.</p>
        </div>
        <div className="flex gap-2">
          <Button
            variant={showTimer ? 'default' : 'outline'}
            size="icon"
            className="h-11 w-11 md:h-9 md:w-9"
            onClick={() => setShowTimer((s) => !s)}
            title="Study timer"
          >
            <Timer className="h-4 w-4" />
          </Button>
          {(connections.calendar || connections.classroom || connections.blackboard) && (
            <Button variant="outline" className="h-11 md:h-9" onClick={() => runSync(false)} disabled={syncing}>
              {syncing ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <RefreshCw className="h-4 w-4 mr-2" />}
              Sync Now
            </Button>
          )}
          <Button
            className="h-11 md:h-9"
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

      {showTimer && <StudyTimer assignments={assignments} classes={classes} />}

      <Tabs value={view} onValueChange={(v) => { setView(v); setSelected({}); }} className="w-full">
        <TabsList>
          <TabsTrigger value="unfinished">Unfinished</TabsTrigger>
          <TabsTrigger value="completed">Completed</TabsTrigger>
          <TabsTrigger value="graded">Graded</TabsTrigger>
        </TabsList>
      </Tabs>

      {view === 'graded' ? (
        <GradedList assignments={assignments} gradingScale={gradingScale} onSelect={setDetail} />
      ) : (
      <div className="space-y-4 mt-4">
      <div className="hidden md:flex flex-wrap gap-2 items-center">
        <SheetSelect
          value={filters.class}
          onValueChange={(v) => setFilters({ ...filters, class: v })}
          placeholder="Class"
          triggerClassName="w-[150px]"
          options={[{ value: 'all', label: 'All classes' }, ...classes.map((c) => ({ value: c.id, label: c.name }))]}
        />
        <SheetSelect
          value={filters.due}
          onValueChange={(v) => setFilters({ ...filters, due: v })}
          placeholder="Due"
          triggerClassName="w-[150px]"
          options={[
            { value: 'all', label: 'All dates' },
            { value: 'overdue', label: 'Overdue' },
            { value: 'today', label: 'Due today' },
            { value: 'week', label: 'This week' },
          ]}
        />
        <SheetSelect
          value={filters.priority}
          onValueChange={(v) => setFilters({ ...filters, priority: v })}
          placeholder="Priority"
          triggerClassName="w-[140px]"
          options={[
            { value: 'all', label: 'All priorities' },
            { value: 'high', label: 'High' },
            { value: 'medium', label: 'Medium' },
            { value: 'low', label: 'Low' },
          ]}
        />
        <SheetSelect
          value={filters.type}
          onValueChange={(v) => setFilters({ ...filters, type: v })}
          placeholder="Type"
          triggerClassName="w-[150px]"
          options={[{ value: 'all', label: 'All types' }, ...Object.keys(TYPE_LABEL).map((t) => ({ value: t, label: TYPE_LABEL[t] }))]}
        />
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant={selectMode ? 'default' : 'outline'}
            size="sm"
            onClick={() => { setSelectMode((m) => !m); setSelected({}); }}
          >
            <CheckSquare className="h-4 w-4 mr-1" /> Select
          </Button>
          <span className="text-xs text-muted-foreground">Sort</span>
          <SheetSelect
            value={sort}
            onValueChange={setSort}
            triggerClassName="w-[160px]"
            options={[
              { value: 'soonest', label: 'Soonest due' },
              { value: 'priority', label: 'Highest priority' },
              { value: 'class', label: 'Class name' },
            ]}
          />
        </div>
      </div>

      {selectMode && selectedCount > 0 && (
        <div className="sticky top-2 z-10 flex flex-wrap items-center gap-2 rounded-lg border bg-card p-3 shadow-sm">
          <span className="text-sm font-medium">{selectedCount} selected</span>
          <SheetSelect
            onValueChange={(v) => bulkApply({ priority: v })}
            placeholder="Set priority"
            triggerClassName="w-[150px]"
            options={[
              { value: 'high', label: 'High' },
              { value: 'medium', label: 'Medium' },
              { value: 'low', label: 'Low' },
            ]}
          />
          <Button size="sm" onClick={() => bulkApply({ completed: true })}>Mark complete</Button>
          <Button size="sm" variant="outline" onClick={() => bulkApply({ completed: false })}>Mark incomplete</Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected({})}>Clear</Button>
        </div>
      )}

      {selectMode && filtered.length > 0 && !loading && (
        <div className="flex items-center gap-3 px-3 py-2 rounded-lg border bg-muted/40">
          <Checkbox checked={allSelected} onCheckedChange={toggleSelectAll} />
          <span className="text-sm text-muted-foreground">
            {allSelected ? 'All selected' : 'Select all on this tab'}
          </span>
        </div>
      )}

      <PullToRefresh onRefresh={() => runSync(false)}>
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
                {selectMode && (
                  <div className="flex items-center justify-center w-11 h-11 shrink-0">
                    <Checkbox checked={!!selected[a.id]} onCheckedChange={() => toggleSelect(a.id)} />
                  </div>
                )}
                <div className={cn('w-1.5 shrink-0', PRIORITY_BAR[a.priority])} />
                <div className="flex items-center justify-center w-11 h-11 shrink-0">
                  <Checkbox checked={!!a.completed} onCheckedChange={() => toggleComplete(a)} />
                </div>
                <button
                  type="button"
                  onClick={() => setDetail(a)}
                  className="flex-1 py-3 pr-3 min-w-0 text-left"
                >
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
                    {typeof a.progress === 'number' && a.progress > 0 && (
                      <span className="ml-auto text-xs font-medium text-muted-foreground">
                        {a.progress >= 100 ? '✓' : `${a.progress}%`}
                      </span>
                    )}
                  </div>
                </button>
                <div className="flex items-center pr-2 gap-1">
                  <Button variant="ghost" size="icon" className="h-11 w-11" onClick={() => { setEditing(a); setShowForm(true); }} title="Edit">
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" className="h-11 w-11" onClick={() => remove(a)} title="Delete">
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}
      </PullToRefresh>
      </div>
      )}

      <AssignmentForm
        open={showForm}
        onOpenChange={setShowForm}
        assignment={editing}
        classes={classes}
        onSaved={load}
      />

      <AssignmentDetail
        open={!!detail}
        onOpenChange={(o) => !o && setDetail(null)}
        assignment={detail}
        classes={classes}
        gradingScale={gradingScale}
        onUpdated={applyUpdate}
        onEdit={openEditFromDetail}
        onDelete={remove}
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