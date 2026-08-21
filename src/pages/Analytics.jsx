import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { Loader2, BarChart3 } from 'lucide-react';
import {
  ResponsiveContainer, PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend,
} from 'recharts';
import { format, parseISO, startOfDay, endOfDay, addDays, isWithinInterval } from 'date-fns';

const TYPE_COLORS = {
  homework: '#6366f1', project: '#ec4899', quiz: '#f59e0b',
  test: '#ef4444', reading: '#10b981', other: '#94a3b8',
};
const TYPE_LABEL = {
  homework: 'Homework', project: 'Project', quiz: 'Quiz', test: 'Test', reading: 'Reading', other: 'Other',
};

export default function Analytics() {
  const [assignments, setAssignments] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [includeCompleted, setIncludeCompleted] = useState(false);

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

  const week = useMemo(() => {
    const start = startOfDay(new Date());
    const end = endOfDay(addDays(new Date(), 7));
    return assignments.filter(
      (a) =>
        a.due_date &&
        isWithinInterval(parseISO(a.due_date), { start, end }) &&
        (includeCompleted || !a.completed)
    );
  }, [assignments, includeCompleted]);

  const classColor = (id) => classes.find((c) => c.id === id)?.color || '#94a3b8';

  const byClass = useMemo(() => {
    const map = {};
    for (const a of week) {
      const name = a.class_name || 'Uncategorized';
      map[name] = (map[name] || 0) + 1;
    }
    return Object.entries(map).map(([name, value]) => ({ name, value, color: findClassColor(name) }));
    function findClassColor(name) {
      const c = classes.find((c) => c.name === name);
      return c?.color || '#94a3b8';
    }
  }, [week, classes]);

  const byType = useMemo(() => {
    const map = {};
    for (const a of week) {
      map[a.type] = (map[a.type] || 0) + 1;
    }
    return Object.entries(map).map(([type, value]) => ({
      name: TYPE_LABEL[type] || type, value, color: TYPE_COLORS[type] || '#94a3b8',
    }));
  }, [week]);

  const byDay = useMemo(() => {
    const days = [];
    for (let i = 0; i < 7; i++) {
      const day = addDays(startOfDay(new Date()), i);
      const key = format(day, 'yyyy-MM-dd');
      const items = week.filter((a) => format(parseISO(a.due_date), 'yyyy-MM-dd') === key);
      const breakdown = {};
      for (const a of items) breakdown[a.type] = (breakdown[a.type] || 0) + 1;
      days.push({ day: format(day, 'EEE d'), ...breakdown });
    }
    return days;
  }, [week]);

  const typesPresent = useMemo(() => {
    const set = new Set();
    byDay.forEach((d) => Object.keys(d).forEach((k) => k !== 'day' && set.add(k)));
    return Array.from(set);
  }, [byDay]);

  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Workload Analytics</h1>
          <p className="text-muted-foreground text-sm">
            Distribution across classes and assignment types for the next 7 days ({week.length} assignment{week.length === 1 ? '' : 's'}).
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm cursor-pointer">
          <input
            type="checkbox"
            checked={includeCompleted}
            onChange={(e) => setIncludeCompleted(e.target.checked)}
            className="rounded"
          />
          Include completed
        </label>
      </div>

      {week.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <BarChart3 className="h-10 w-10 text-muted-foreground mb-2" />
          <p className="text-muted-foreground">No assignments due in the upcoming week.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <ChartCard title="By Class">
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={byClass} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={60} outerRadius={100} paddingAngle={2}>
                  {byClass.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>

          <ChartCard title="By Type">
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={byType} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={60} outerRadius={100} paddingAngle={2}>
                  {byType.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>

          <div className="lg:col-span-2">
            <ChartCard title="Daily Breakdown (by type)">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={byDay}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                  <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Legend />
                  {typesPresent.map((t) => (
                    <Bar key={t} dataKey={t} stackId="a" fill={TYPE_COLORS[t] || '#94a3b8'} name={TYPE_LABEL[t] || t} />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>
        </div>
      )}
    </div>
  );
}

function ChartCard({ title, children }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <h3 className="font-semibold mb-3">{title}</h3>
      {children}
    </div>
  );
}