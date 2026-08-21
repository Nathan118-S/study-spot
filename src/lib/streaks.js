export function computeClassStreak(assignments, now = new Date()) {
  const sorted = [...assignments].sort(
    (a, b) => new Date(a.due_date || 0).getTime() - new Date(b.due_date || 0).getTime()
  );
  const total = sorted.length;
  const completed = sorted.filter((a) => a.completed).length;

  if (total === 0) {
    return { streak: 0, status: 'empty', total: 0, completed: 0, hasOverdue: false };
  }

  const hasOverdue = sorted.some(
    (a) => a.due_date && !a.completed && new Date(a.due_date) < now
  );

  if (hasOverdue) {
    return { streak: 0, status: 'broken', total, completed, hasOverdue: true };
  }

  let run = 0;
  for (let i = sorted.length - 1; i >= 0; i--) {
    if (sorted[i].completed) run++;
    else break;
  }

  const allDone = completed === total;
  return {
    streak: run,
    status: allDone ? 'active' : 'atrisk',
    total,
    completed,
    hasOverdue: false,
  };
}

export function buildStreaks(classes, assignments, now = new Date()) {
  const classroomClasses = classes.filter((c) => c.source === 'google_classroom');
  return classroomClasses.map((c) => {
    const items = assignments.filter((a) => a.class_id === c.id);
    return { class: c, ...computeClassStreak(items, now) };
  });
}

export function streakSummary(streaks) {
  const active = streaks.filter((s) => s.status === 'active').length;
  const atRisk = streaks.filter((s) => s.status === 'atrisk').length;
  const broken = streaks.filter((s) => s.status === 'broken').length;
  const best = streaks.reduce((m, s) => Math.max(m, s.streak), 0);
  return { active, atRisk, broken, best, total: streaks.length };
}