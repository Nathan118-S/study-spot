import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Loader2, Flame, AlertTriangle, ShieldAlert, ShieldCheck, Trophy, GraduationCap, X } from 'lucide-react';
import { buildStreaks, streakSummary } from '@/lib/streaks';
import StreakCard from '@/components/StreakCard';
import Leaderboard from '@/components/Leaderboard';
import { useAuth } from '@/lib/AuthContext';
import { isDemoUser, getDemoClasses, getDemoAssignments, getDemoStreaks, demoStreakSummary, demoConnections } from '@/lib/demoData';

export default function Analytics() {
  const { user } = useAuth();
  const [classes, setClasses] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [connections, setConnections] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (isDemoUser(user)) {
      setClasses(getDemoClasses());
      setAssignments(getDemoAssignments());
      setConnections(demoConnections);
      return;
    }
    const [c, a, conn] = await Promise.all([
      api.entities.Class.list(),
      api.entities.Assignment.list('-due_date', 500),
      api.functions.invoke('checkGoogleConnections', {}).catch(() => null),
    ]);
    setClasses(c);
    setAssignments(a);
    setConnections(conn?.data ?? { classroom: false, blackboard: false });
  }, [user]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const classroomOn = connections?.classroom === true;
  const blackboardOn = connections?.blackboard === true;
  const streaks = useMemo(
    () => isDemoUser(user)
      ? getDemoStreaks()
      : buildStreaks(classes, assignments, { classroom: classroomOn, blackboard: blackboardOn }),
    [classes, assignments, classroomOn, blackboardOn, user]
  );
  const summary = useMemo(
    () => isDemoUser(user) ? demoStreakSummary : streakSummary(streaks),
    [streaks, user]
  );

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
          breaks it. Streaks from synced Google Classroom and Blackboard classes are verified;
          manual classes are unverified.
        </p>
      </div>

      <StatusBox classroom={classroomOn} blackboard={blackboardOn} />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatCard icon={<Flame className="h-4 w-4 text-orange-500" />} label="Active streaks" value={summary.active} index={0} />
        <StatCard icon={<ShieldAlert className="h-4 w-4 text-amber-500" />} label="At risk" value={summary.atRisk} index={1} />
        <StatCard icon={<AlertTriangle className="h-4 w-4 text-red-500" />} label="Broken" value={summary.broken} index={2} />
        <StatCard icon={<Trophy className="h-4 w-4 text-yellow-500" />} label="Best streak" value={summary.best} index={3} />
      </div>

      {streaks.length === 0 ? (
        <div className="rounded-lg border bg-card p-8 flex flex-col items-center text-center gap-3">
          <GraduationCap className="h-10 w-10 text-muted-foreground" />
          <p className="text-muted-foreground">
            No classes yet. Add a class to start a streak.
          </p>
          <Button asChild>
            <Link to="/classes">Add a class</Link>
          </Button>
        </div>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {streaks.map((s, index) => (
            <div key={s.class.id} className="animate-fade-up" style={{ animationDelay: `${Math.min(index, 10) * 40}ms` }}>
              <StreakCard streak={s} />
            </div>
          ))}
        </div>
      )}

      {(user?.leaderboard_enabled ?? user?.data?.leaderboard_enabled) === true && <Leaderboard />}
    </div>
  );
}

function StatCard({ icon, label, value, index = 0 }) {
  return (
    <div
      className="rounded-lg border bg-card p-4 transition-transform duration-200 hover:-translate-y-0.5 animate-fade-up"
      style={{ animationDelay: `${index * 70}ms` }}
    >
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        {icon}
        {label}
      </div>
      <p className="text-2xl font-bold mt-1">{value}</p>
    </div>
  );
}

function StatusBox({ classroom, blackboard }) {
  const [dismissed, setDismissed] = useState(() => localStorage.getItem('cf-streaks-unverified-dismissed') === 'true');

  if (classroom || blackboard) {
    const sources = [classroom && 'Google Classroom', blackboard && 'Blackboard'].filter(Boolean).join(' and ');
    return (
      <div className="flex items-center gap-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4">
        <ShieldCheck className="h-5 w-5 text-emerald-600 dark:text-emerald-400 shrink-0" />
        <div>
          <p className="font-medium text-emerald-700 dark:text-emerald-300">Verified</p>
          <p className="text-sm text-muted-foreground">
            {sources} {classroom && blackboard ? 'are' : 'is'} connected. Streaks from synced classes are verified.
          </p>
        </div>
      </div>
    );
  }
  if (dismissed) return null;
  return (
    <div className="flex items-start gap-3 rounded-lg border border-amber-500/40 bg-amber-500/10 p-4">
      <ShieldAlert className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
      <div className="flex-1">
        <p className="font-medium text-amber-700 dark:text-amber-300">Unverified</p>
        <p className="text-sm text-muted-foreground">
          Neither Google Classroom nor Blackboard is connected. You can continue with streaks based
          on your manual classes, but your streaks can not be verified.
        </p>
      </div>
      <Button asChild variant="outline" size="sm" className="shrink-0">
        <Link to="/settings">Connect</Link>
      </Button>
      <Button
        variant="ghost"
        size="icon"
        className="shrink-0 h-8 w-8 text-muted-foreground hover:text-foreground"
        onClick={() => {
          localStorage.setItem('cf-streaks-unverified-dismissed', 'true');
          setDismissed(true);
        }}
        aria-label="Dismiss"
        title="Dismiss"
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  );
}