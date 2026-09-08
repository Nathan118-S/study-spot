import { pool } from '../db.js';

// Only alert on meaningful new records to avoid a push on every first completion.
const MIN_STREAK = 3;

// Mirrors src/lib/streaks.js computeClassStreak(): the run of completed
// assignments at the tail of the by-due-date list, broken by any overdue item.
function computeClassStreak(assignments, now) {
  const sorted = [...assignments].sort((a, b) => new Date(a.due_date || 0).getTime() - new Date(b.due_date || 0).getTime());
  if (sorted.length === 0) return 0;
  const hasOverdue = sorted.some((a) => a.due_date && !a.completed && new Date(a.due_date) < now);
  if (hasOverdue) return 0;
  let run = 0;
  for (let i = sorted.length - 1; i >= 0; i--) {
    if (sorted[i].completed) run++;
    else break;
  }
  return run;
}

// Mirrors base44/functions/checkStreakRecords — updates each class's
// best_streak and notifies the owner when they set a new record.
export async function checkStreakRecords() {
  const now = new Date();

  const [classesRes, assignmentsRes] = await Promise.all([
    pool.query('SELECT * FROM classes ORDER BY created_at DESC LIMIT 5000'),
    pool.query('SELECT * FROM assignments ORDER BY due_date DESC LIMIT 10000'),
  ]);

  const byOwner = new Map();
  const ensure = (oid) => {
    if (!byOwner.has(oid)) byOwner.set(oid, { classes: [], assignments: [] });
    return byOwner.get(oid);
  };
  for (const c of classesRes.rows) if (c.created_by_id) ensure(c.created_by_id).classes.push(c);
  for (const a of assignmentsRes.rows) if (a.created_by_id) ensure(a.created_by_id).assignments.push(a);

  let notified = 0;
  let checked = 0;

  for (const [oid, { classes: userClasses, assignments: userAssignments }] of byOwner) {
    for (const c of userClasses) {
      checked++;
      const items = userAssignments.filter((a) => a.class_id === c.id);
      const streak = computeClassStreak(items, now);
      const best = typeof c.best_streak === 'number' ? c.best_streak : 0;

      if (streak > best && streak >= MIN_STREAK) {
        try {
          await pool.query(
            `INSERT INTO notifications (user_id, title, content, type, read, action_label, action_url)
             VALUES ($1, $2, $3, 'streak', false, 'View streaks', '/analytics')`,
            [oid, 'New streak record! 🔥', `${c.name}: ${streak} assignments in a row — your new best.`]
          );
          notified++;
        } catch { /* in-app notification create failed; continue */ }
      }

      if (streak > best) {
        try {
          await pool.query('UPDATE classes SET best_streak = $1, updated_at = now() WHERE id = $2', [streak, c.id]);
        } catch { /* ignore */ }
      }
    }
  }

  return { checked, notified, users: byOwner.size };
}
