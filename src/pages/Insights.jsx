import React, { useEffect, useMemo, useState } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Loader2, TrendingUp, CheckCircle2, CalendarCheck, Award, Activity } from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  Cell,
} from 'recharts';
import { isDemoUser, getDemoAssignments } from '@/lib/demoData';

const WEEKS = 8;

function startOfWeek(d) {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1); // Monday as start
  date.setDate(diff);
  date.setHours(0, 0, 0, 0);
  return date;
}

function weekLabel(date) {
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export default function Insights() {
  const { user } = useAuth();
  const [assignments, setAssignments] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      try {
        if (isDemoUser(user)) {
          setAssignments(getDemoAssignments());
        } else {
          const items = await base44.entities.Assignment.list('-updated_date', 500);
          setAssignments(items || []);
        }
      } catch {
        setAssignments([]);
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, [user]);

  const weekly = useMemo(() => {
    if (!assignments) return [];
    const now = new Date();
    const thisWeekStart = startOfWeek(now);
    const buckets = [];
    for (let i = WEEKS - 1; i >= 0; i--) {
      const start = new Date(thisWeekStart);
      start.setDate(start.getDate() - i * 7);
      const end = new Date(start);
      end.setDate(end.getDate() + 7);
      buckets.push({ start, end, label: weekLabel(start), completed: 0, progressPoints: 0 });
    }
    for (const a of assignments) {
      const completedAt = a.completed ? new Date(a.updated_date || a.created_date || a.due_date) : null;
      if (completedAt && !isNaN(completedAt)) {
        for (const b of buckets) {
          if (completedAt >= b.start && completedAt < b.end) {
            b.completed += 1;
            break;
          }
        }
      }
      const progress = Number(a.progress || 0);
      if (progress > 0) {
        const ref = new Date(a.updated_date || a.created_date || a.due_date);
        if (isNaN(ref)) continue;
        for (const b of buckets) {
          if (ref >= b.start && ref < b.end) {
            b.progressPoints += progress;
            break;
          }
        }
      }
    }
    let cumulative = 0;
    return buckets.map((b) => {
      cumulative += b.completed;
      return { ...b, cumulative };
    });
  }, [assignments]);

  const stats = useMemo(() => {
    if (!weekly.length) return { total: 0, thisWeek: 0, avg: 0, best: 0 };
    const total = weekly[weekly.length - 1].cumulative;
    const thisWeek = weekly[weekly.length - 1].completed;
    const avg = (weekly.reduce((s, b) => s + b.completed, 0) / weekly.length).toFixed(1);
    const best = Math.max(...weekly.map((b) => b.completed));
    return { total, thisWeek, avg, best };
  }, [weekly]);

  const maxCompleted = Math.max(1, ...weekly.map((b) => b.completed));
  const lastIdx = weekly.length - 1;

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade-up">
      <div>
        <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
          <TrendingUp className="h-7 w-7 text-primary" />
          Insights
        </h1>
        <p className="text-muted-foreground text-sm">Track your assignment completion progress over time.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard icon={<CheckCircle2 className="h-5 w-5 text-emerald-500" />} label="Total completed" value={stats.total} />
        <StatCard icon={<CalendarCheck className="h-5 w-5 text-primary" />} label="This week" value={stats.thisWeek} />
        <StatCard icon={<Activity className="h-5 w-5 text-blue-500" />} label="Avg per week" value={stats.avg} />
        <StatCard icon={<Award className="h-5 w-5 text-amber-500" />} label="Best week" value={stats.best} />
      </div>

      <div className="rounded-xl border bg-card p-4 md:p-6">
        <div className="mb-4">
          <h2 className="font-heading text-lg font-semibold">Weekly completion trend</h2>
          <p className="text-sm text-muted-foreground">Assignments marked complete each week (last {WEEKS} weeks).</p>
        </div>
        <div className="h-72 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={weekly} margin={{ top: 10, right: 12, left: -12, bottom: 0 }}>
              <defs>
                <linearGradient id="completionGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} stroke="hsl(var(--muted-foreground))" tickLine={false} axisLine={false} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} stroke="hsl(var(--muted-foreground))" tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: '0.5rem',
                  fontSize: '0.8rem',
                }}
                labelStyle={{ color: 'hsl(var(--foreground))' }}
              />
              <Area
                type="monotone"
                dataKey="completed"
                name="Completed"
                stroke="hsl(var(--primary))"
                strokeWidth={2}
                fill="url(#completionGradient)"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="rounded-xl border bg-card p-4 md:p-6">
        <div className="mb-4">
          <h2 className="font-heading text-lg font-semibold">Completions per week</h2>
          <p className="text-sm text-muted-foreground">Bar view of the same trend, highlighting the current week.</p>
        </div>
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={weekly} margin={{ top: 10, right: 12, left: -12, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} stroke="hsl(var(--muted-foreground))" tickLine={false} axisLine={false} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} stroke="hsl(var(--muted-foreground))" tickLine={false} axisLine={false} />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: '0.5rem',
                  fontSize: '0.8rem',
                }}
                cursor={{ fill: 'hsl(var(--accent))', opacity: 0.4 }}
              />
              <Bar dataKey="completed" name="Completed" radius={[4, 4, 0, 0]}>
                {weekly.map((entry, idx) => (
                  <Cell
                    key={idx}
                    fill={idx === lastIdx ? 'hsl(var(--primary))' : 'hsl(var(--primary) / 0.5)'}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon, label, value }) {
  return (
    <div className="rounded-xl border bg-card p-4 flex items-center gap-3">
      <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center shrink-0">{icon}</div>
      <div className="min-w-0">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="text-xl font-bold">{value}</p>
      </div>
    </div>
  );
}