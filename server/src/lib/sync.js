// Shared helpers used by the Google Calendar and Google Classroom sync routes.

const ASSIGNMENT_KEYWORDS =
  /(assignment|homework|\bhw\b|due|quiz|test|exam|midterm|final|project|essay|reading|submit|lab|worksheet|problem set)/i;

// A calendar event is treated as an assignment when its title/description
// mentions an assignment-related keyword (Google Calendar has no native
// "assignment" label, so keyword matching is the pragmatic signal).
export function isAssignmentEvent(ev) {
  const text = `${ev.summary || ''} ${ev.description || ''}`;
  return ASSIGNMENT_KEYWORDS.test(text);
}

export function guessPriority(title) {
  const t = (title || '').toLowerCase();
  if (/(test|exam|final|midterm)\b/.test(t)) return 'high';
  if (/(quiz|project|essay|presentation|lab)\b/.test(t)) return 'medium';
  return 'medium';
}

export function guessType(title) {
  const t = (title || '').toLowerCase();
  if (/(test|exam|final|midterm)\b/.test(t)) return 'test';
  if (/quiz\b/.test(t)) return 'quiz';
  if (/project\b/.test(t)) return 'project';
  if (/(read|reading|chapter)\b/.test(t)) return 'reading';
  if (/(homework|\bhw\b|assignment|worksheet|problem set)\b/.test(t)) return 'homework';
  return 'other';
}
