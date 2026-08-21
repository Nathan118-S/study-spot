export const DEFAULT_GRADING_SCALE = [
  { letter: 'A', min: 90 },
  { letter: 'B', min: 80 },
  { letter: 'C', min: 70 },
  { letter: 'D', min: 60 },
  { letter: 'F', min: 0 },
];

export function normalizeScale(scale) {
  return (Array.isArray(scale) && scale.length ? scale : DEFAULT_GRADING_SCALE)
    .slice()
    .sort((a, b) => b.min - a.min);
}

export function letterGrade(score, points, scale) {
  if (typeof score !== 'number' || !points || points <= 0) return null;
  const pct = (score / points) * 100;
  const sorted = normalizeScale(scale);
  for (const entry of sorted) {
    if (pct >= entry.min) return entry.letter;
  }
  return sorted[sorted.length - 1]?.letter || null;
}

export function isGraded(a) {
  return typeof a?.score === 'number' && a?.points > 0;
}