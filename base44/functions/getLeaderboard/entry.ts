import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

// Returns the top study-streak students across the app. A student's best
// streak is the highest Class.best_streak among their classes (maintained by
// the checkStreakRecords function). Disabled and demo accounts are excluded.
export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const [users, classes] = await Promise.all([
      base44.asServiceRole.entities.User.list('-created_date', 5000),
      base44.asServiceRole.entities.Class.list('-created_date', 5000),
    ]);

    const bestByOwner = new Map();
    for (const c of classes) {
      if (!c.created_by_id) continue;
      const best = typeof c.best_streak === 'number' ? c.best_streak : 0;
      const cur = bestByOwner.get(c.created_by_id) || 0;
      if (best > cur) bestByOwner.set(c.created_by_id, best);
    }

    const entries = users
      .filter((u) => u.role !== 'disabled' && u.role !== 'demo')
      .map((u) => {
        const best = bestByOwner.get(u.id) || 0;
        const name = u.name || u.full_name || (u.email ? u.email.split('@')[0] : 'Anonymous');
        return { userId: u.id, name, bestStreak: best };
      })
      .filter((e) => e.bestStreak > 0)
      .sort((a, b) => b.bestStreak - a.bestStreak)
      .slice(0, 10);

    return Response.json({ entries, currentUserId: user.id });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}