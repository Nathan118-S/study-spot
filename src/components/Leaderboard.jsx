import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Loader2, Trophy, Flame } from 'lucide-react';
import { cn } from '@/lib/utils';
import { isDemoUser, getDemoLeaderboard } from '@/lib/demoData';

const MEDAL = ['🥇', '🥈', '🥉'];

export default function Leaderboard() {
  const { user } = useAuth();
  const [entries, setEntries] = useState([]);
  const [currentUserId, setCurrentUserId] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      if (isDemoUser(user)) {
        const demo = getDemoLeaderboard();
        setEntries(demo.entries);
        setCurrentUserId(demo.currentUserId);
        setLoading(false);
        return;
      }
      try {
        const res = await base44.functions.invoke('getLeaderboard', {});
        setEntries(res.data.entries || []);
        setCurrentUserId(res.data.currentUserId);
      } catch {
        setEntries([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [user]);

  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center gap-2 mb-3">
        <Trophy className="h-5 w-5 text-yellow-500" />
        <h2 className="font-semibold">Leaderboard</h2>
        <span className="text-xs text-muted-foreground">Top study streaks</span>
      </div>

      {loading ? (
        <div className="flex justify-center py-6">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      ) : entries.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4 text-center">
          No streaks yet. Complete assignments to climb the board!
        </p>
      ) : (
        <ol className="space-y-1">
          {entries.map((e, i) => {
            const isMe = e.userId === currentUserId;
            return (
              <li
                key={e.userId}
                className={cn(
                  'flex items-center justify-between gap-3 rounded-md px-3 py-2',
                  isMe ? 'bg-primary/10 border border-primary/30' : 'hover:bg-accent'
                )}
              >
                <span className="flex items-center gap-3 min-w-0">
                  <span className="w-6 text-center text-sm font-medium shrink-0">
                    {MEDAL[i] || i + 1}
                  </span>
                  <span className="truncate font-medium">
                    {e.name}
                    {isMe && <span className="text-xs text-primary ml-1">(you)</span>}
                  </span>
                </span>
                <span className="flex items-center gap-1 text-sm font-semibold shrink-0">
                  <Flame className="h-4 w-4 text-orange-500" />
                  {e.bestStreak}
                </span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}