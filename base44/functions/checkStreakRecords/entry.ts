import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

// Only alert on meaningful new records to avoid a push on every first completion.
const MIN_STREAK = 3;

// Mirrors src/lib/streaks.js computeClassStreak(): the run of completed
// assignments at the tail of the by-due-date list, broken by any overdue item.
function computeClassStreak(assignments, now) {
  const sorted = [...assignments].sort(
    (a, b) => new Date(a.due_date || 0).getTime() - new Date(b.due_date || 0).getTime()
  );
  if (sorted.length === 0) return 0;
  const hasOverdue = sorted.some(
    (a) => a.due_date && !a.completed && new Date(a.due_date) < now
  );
  if (hasOverdue) return 0;
  let run = 0;
  for (let i = sorted.length - 1; i >= 0; i--) {
    if (sorted[i].completed) run++;
    else break;
  }
  return run;
}

export default async function(req: Request): Promise<Response> {
  try {
    const base44 = createClientFromRequest(req);
    const now = new Date();

    const [classes, assignments] = await Promise.all([
      base44.asServiceRole.entities.Class.list('-created_date', 5000),
      base44.asServiceRole.entities.Assignment.list('-due_date', 10000),
    ]);

    // Group records by owner so each user's streaks are computed from their own data.
    const byOwner = new Map();
    const ensure = (oid) => {
      if (!byOwner.has(oid)) byOwner.set(oid, { classes: [], assignments: [] });
      return byOwner.get(oid);
    };
    for (const c of classes) if (c.created_by_id) ensure(c.created_by_id).classes.push(c);
    for (const a of assignments) if (a.created_by_id) ensure(a.created_by_id).assignments.push(a);

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
            await base44.asServiceRole.entities.Notification.create({
              user_id: oid,
              title: 'New streak record! 🔥',
              content: `${c.name}: ${streak} assignments in a row — your new best.`,
              type: 'streak',
              read: false,
              action_label: 'View streaks',
              action_url: '/analytics',
            });
            notified++;
          } catch {
            // In-app notification create failed; continue.
          }
          try {
            await base44.asServiceRole.integrations.Core.SendPushNotification({
              user_id: oid,
              title: 'New streak record! 🔥',
              content: `${c.name}: ${streak} assignments in a row — your new best.`,
              action_label: 'View streaks',
              action_url: '/analytics',
            });
          } catch {
            // Push may fail if the user has no native mobile build; the in-app
            // notification above still reaches them. Fall through and record the
            // best so we don't retry the same record.
          }
        }

        if (streak > best) {
          try {
            await base44.asServiceRole.entities.Class.update(c.id, { best_streak: streak });
          } catch {}
        }
      }
    }

    return Response.json({ checked, notified, users: byOwner.size });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}