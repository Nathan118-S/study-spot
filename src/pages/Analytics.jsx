import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Loader2, Flame, AlertTriangle, ShieldAlert, Trophy, GraduationCap } from 'lucide-react';
import { buildStreaks, streakSummary } from '@/lib/streaks';
import StreakCard from '@/components/StreakCard';

export default function Analytics() {
  const [classes, setClasses] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const [c, a] = await Promise.all([
      base44.entities.Class.list(),
      base44.entities.Assignment.list('-due_date', 500),
    ]);
    setClasses(c);
    setAssignments(a);
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const streaks = useMemo(() => buildStreaks(classes, assignments), [classes, assignments]);
  const summary = useMemo(() => streakSummary(streaks), [streaks]);
  const hasClassroom = classes.some((c) => c.source === 'google_classroom');

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

      {!hasClassroom ? (
        <div className="rounded-lg border bg-card p-8 flex flex-col items-center text-center gap-3">
          <GraduationCap className="h-10 w-10 text-muted-foreground" />
          <div>
            <p className="font-medium">No Google Classroom classes yet</p>
            <p className="text-sm text-muted-foreground">
              Connect Google Classroom and sync to start tracking streaks.
            </p>
          </div>
          <Button asChild>
            <Link to="/settings">Connect Google Classroom</Link>
          </Button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <StatCard icon={<Flame className="h-4 w-4 text-orange-500" />} label="Active streaks" value={summary.active} />
            <StatCard icon={<ShieldAlert className="h-4 w-4 text-amber-500" />} label="At risk" value={summary.atRisk} />
            <StatCard icon={<AlertTriangle className="h-4 w-4 text-red-500" />} label="Broken" value={summary.broken} />
            <StatCard icon={<Trophy className="h-4 w-4 text-yellow-500" />} label="Best streak" value={summary.best} />
          </div>

          {streaks.length === 0 ? (
            <div className="rounded-lg border bg-card p-8 flex flex-col items-center text-center gap-2">
              <p className="text-muted-foreground">No streaks to show yet.</p>
            </div>
          ) : (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {streaks.map((s) => (
                <StreakCard key={s.class.id} streak={s} />
              ))}
            </div>
          )}
        </>
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