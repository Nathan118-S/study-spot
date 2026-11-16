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
  score = toNumber(score);
  points = toNumber(points);
  if (score === null || !points || points <= 0) return null;
  const pct = (score / points) * 100;
  const sorted = normalizeScale(scale);
  for (const entry of sorted) {
    if (pct >= entry.min) return entry.letter;
  }
  return sorted[sorted.length - 1]?.letter || null;
}

// A score of null/undefined/'' means "not graded yet"; 0 is a real grade.
export function toNumber(v) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export function isGraded(a) {
  return toNumber(a?.score) !== null && (toNumber(a?.points) || 0) > 0;
}