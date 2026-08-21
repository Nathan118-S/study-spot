import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Loader2, Flame, AlertTriangle, ShieldAlert, ShieldCheck, Trophy, GraduationCap } from 'lucide-react';
import { buildStreaks, streakSummary } from '@/lib/streaks';
import StreakCard from '@/components/StreakCard';

export default function Analytics() {
  const [classes, setClasses] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [connections, setConnections] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const [c, a, conn] = await Promise.all([
      base44.entities.Class.list(),
      base44.entities.Assignment.list('-due_date', 500),
      base44.functions.invoke('checkGoogleConnections', {}).catch(() => null),
    ]);
    setClasses(c);
    setAssignments(a);
    setConnections(conn?.data ?? { classroom: false });
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const verified = connections?.classroom === true;
  const streaks = useMemo(
    () => buildStreaks(classes, assignments, { onlyClassroom: verified }),
    [classes, assignments, verified]
  );
  const summary = useMemo(() => streakSummary(streaks), [streaks]);

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
          <Flame className="h-7 w-7 text-orange-500" /> Streaks
        </h1>
        <p className="text-muted-foreground text-sm">
          Complete every assignment in a class to keep its streak alive. One overdue assignment
          breaks it. Streaks are tracked for your Google Classroom classes.
        </p>
      </div>

      <StatusBox verified={verified} />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatCard icon={<Flame className="h-4 w-4 text-orange-500" />} label="Active streaks" value={summary.active} />
        <StatCard icon={<ShieldAlert className="h-4 w-4 text-amber-500" />} label="At risk" value={summary.atRisk} />
        <StatCard icon={<AlertTriangle className="h-4 w-4 text-red-500" />} label="Broken" value={summary.broken} />
        <StatCard icon={<Trophy className="h-4 w-4 text-yellow-500" />} label="Best streak" value={summary.best} />
      </div>

      {streaks.length === 0 ? (
        <div className="rounded-lg border bg-card p-8 flex flex-col items-center text-center gap-3">
          <GraduationCap className="h-10 w-10 text-muted-foreground" />
          <p className="text-muted-foreground">
            {verified ? 'No Google Classroom classes yet.' : 'No classes yet. Add a class to start a streak.'}
          </p>
          {!verified && (
            <Button asChild>
              <Link to="/classes">Add a class</Link>
            </Button>
          )}
        </div>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {streaks.map((s) => (
            <StreakCard key={s.class.id} streak={s} />
          ))}
        </div>
      )}
    </div>
  );
}

function StatCard({ icon, label, value }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        {icon}
        {label}
      </div>
      <p className="text-2xl font-bold mt-1">{value}</p>
    </div>
  );
}

function StatusBox({ verified }) {
  if (verified) {
    return (
      <div className="flex items-center gap-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4">
        <ShieldCheck className="h-5 w-5 text-emerald-600 dark:text-emerald-400 shrink-0" />
        <div>
          <p className="font-medium text-emerald-700 dark:text-emerald-300">Verified</p>
          <p className="text-sm text-muted-foreground">
            Google Classroom is connected. Streaks are tracked from your synced coursework.
          </p>
        </div>
      </div>
    );
  }
  return (
    <div className="flex items-start gap-3 rounded-lg border border-amber-500/40 bg-amber-500/10 p-4">
      <ShieldAlert className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
      <div className="flex-1">
        <p className="font-medium text-amber-700 dark:text-amber-300">Unverified</p>
        <p className="text-sm text-muted-foreground">
          Google Classroom is not connected. You can continue with streaks based on your manual
          classes, but coursework won't sync automatically.
        </p>
      </div>
      <Button asChild variant="outline" size="sm" className="shrink-0">
        <Link to="/settings">Connect</Link>
      </Button>
    </div>
  );
}