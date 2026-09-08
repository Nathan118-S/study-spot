import { pool } from '../db.js';

// Returns the top study-streak students across the app. A student's best
// streak is the highest Class.best_streak among their classes (maintained by
// the checkStreakRecords cron job). Disabled and demo accounts are excluded.
export async function getLeaderboard(user) {
  const [usersRes, classesRes] = await Promise.all([
    pool.query('SELECT id, email, name, full_name, role FROM users ORDER BY created_at DESC LIMIT 5000'),
    pool.query('SELECT created_by_id, best_streak FROM classes ORDER BY created_at DESC LIMIT 5000'),
  ]);

  const bestByOwner = new Map();
  for (const c of classesRes.rows) {
    if (!c.created_by_id) continue;
    const best = typeof c.best_streak === 'number' ? c.best_streak : 0;
    const cur = bestByOwner.get(c.created_by_id) || 0;
    if (best > cur) bestByOwner.set(c.created_by_id, best);
  }

  const entries = usersRes.rows
    .filter((u) => u.role !== 'disabled' && u.role !== 'demo')
    .map((u) => {
      const best = bestByOwner.get(u.id) || 0;
      const name = u.name || u.full_name || (u.email ? u.email.split('@')[0] : 'Anonymous');
      return { userId: u.id, name, bestStreak: best };
    })
    .filter((e) => e.bestStreak > 0)
    .sort((a, b) => b.bestStreak - a.bestStreak)
    .slice(0, 10);

  return { entries, currentUserId: user.id };
}
