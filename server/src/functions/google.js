import { pool } from '../db.js';
import { HttpError } from '../lib/httpError.js';
import { isAssignmentEvent, guessPriority, guessType } from '../lib/sync.js';
import { getValidGoogleToken } from '../routes/googleAuth.js';

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];

export async function checkGoogleConnections(user) {
  const res = await pool.query(
    'SELECT connector FROM google_connections WHERE user_id = $1',
    [user.id]
  );
  const connected = new Set(res.rows.map((r) => r.connector));
  return {
    calendar: connected.has('google_calendar'),
    classroom: connected.has('google_classroom'),
    blackboard: !!user.data?.blackboard_access_token,
  };
}

export async function syncGoogleCalendar(user) {
  const accessToken = await getValidGoogleToken(user.id, 'google_calendar');
  if (!accessToken) throw Object.assign(new HttpError(400, 'Google Calendar not connected'), { notConnected: true });

  const timeMin = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
  const timeMax = new Date(Date.now() + 120 * 24 * 60 * 60 * 1000).toISOString();
  const url =
    'https://www.googleapis.com/calendar/v3/calendars/primary/events' +
    `?singleEvents=true&orderBy=startTime&maxResults=250` +
    `&timeMin=${encodeURIComponent(timeMin)}&timeMax=${encodeURIComponent(timeMax)}`;

  const res = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!res.ok) throw new HttpError(502, 'Google Calendar API error: ' + (await res.text()));
  const data = await res.json();
  const events = (data.items || []).filter((ev) => ev.start && (ev.start.dateTime || ev.start.date) && isAssignmentEvent(ev));

  let gcClassRes = await pool.query(
    `SELECT * FROM classes WHERE created_by_id = $1 AND name = 'Google Calendar' AND source = 'google_calendar' LIMIT 1`,
    [user.id]
  );
  let gcClass = gcClassRes.rows[0];
  if (!gcClass) {
    const inserted = await pool.query(
      `INSERT INTO classes (created_by_id, name, color, source, external_id) VALUES ($1, 'Google Calendar', '#3b82f6', 'google_calendar', 'google-calendar-primary') RETURNING *`,
      [user.id]
    );
    gcClass = inserted.rows[0];
  }

  const existingRes = await pool.query(
    'SELECT external_id FROM assignments WHERE created_by_id = $1 AND source = $2',
    [user.id, 'google_calendar']
  );
  const seenIds = new Set(existingRes.rows.map((a) => a.external_id));

  let created = 0;
  for (const ev of events) {
    if (seenIds.has(ev.id)) continue;
    const start = ev.start.dateTime || ev.start.date;
    await pool.query(
      `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'google_calendar', $10, false)`,
      [
        user.id, ev.summary || 'Untitled event', gcClass.id, gcClass.name, new Date(start).toISOString(),
        guessPriority(ev.summary), guessType(ev.summary), ev.description || '', ev.description || '', ev.id,
      ]
    );
    seenIds.add(ev.id);
    created++;
  }

  return { synced: true, imported: created, reviewed: events.length };
}

function courseworkDueISO(cw) {
  if (!cw.dueDate) return null;
  const d = cw.dueDate;
  const t = cw.dueTime || { hours: 23, minutes: 59 };
  return new Date(Date.UTC(d.year, (d.month || 1) - 1, d.day || 1, t.hours ?? 23, t.minutes ?? 59)).toISOString();
}

export async function syncGoogleClassroom(user) {
  const accessToken = await getValidGoogleToken(user.id, 'google_classroom');
  if (!accessToken) throw Object.assign(new HttpError(400, 'Google Classroom not connected'), { notConnected: true });

  const auth = { headers: { Authorization: `Bearer ${accessToken}` } };
  const coursesRes = await fetch('https://classroom.googleapis.com/v1/courses?courseStates=ACTIVE', auth);
  if (!coursesRes.ok) throw new HttpError(502, 'Classroom API error: ' + (await coursesRes.text()));
  const courses = (await coursesRes.json()).courses || [];

  const existingClassesRes = await pool.query(
    'SELECT * FROM classes WHERE created_by_id = $1 AND source = $2',
    [user.id, 'google_classroom']
  );
  const classByExt = {};
  for (const c of existingClassesRes.rows) classByExt[c.external_id] = c;
  let colorIdx = 0;
  const classMap = {};
  for (const course of courses) {
    if (classByExt[course.id]) {
      classMap[course.id] = classByExt[course.id];
    } else {
      const inserted = await pool.query(
        `INSERT INTO classes (created_by_id, name, color, teacher_name, source, external_id)
         VALUES ($1, $2, $3, '', 'google_classroom', $4) RETURNING *`,
        [user.id, course.name || 'Unnamed course', COLORS[colorIdx++ % COLORS.length], course.id]
      );
      classMap[course.id] = inserted.rows[0];
    }
  }

  const existingAsgRes = await pool.query(
    'SELECT external_id FROM assignments WHERE created_by_id = $1 AND source = $2',
    [user.id, 'google_classroom']
  );
  const seenExt = new Set(existingAsgRes.rows.map((a) => a.external_id));

  let created = 0;
  for (const course of courses) {
    const cls = classMap[course.id];
    const cwRes = await fetch(
      `https://classroom.googleapis.com/v1/courses/${course.id}/courseWork?courseWorkStates=PUBLISHED,DRAFT`,
      auth
    );
    if (!cwRes.ok) continue;
    const items = (await cwRes.json()).courseWork || [];
    for (const cw of items) {
      if (seenExt.has(cw.id)) continue;
      const due = courseworkDueISO(cw);
      if (!due) continue;
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed, points)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'google_classroom', $10, false, $11)`,
        [
          user.id, cw.title || 'Untitled coursework', cls.id, cls.name, due,
          guessPriority(cw.title), guessType(cw.title), cw.description || '', cw.description || '',
          cw.id, typeof cw.maxPoints === 'number' ? cw.maxPoints : 0,
        ]
      );
      seenExt.add(cw.id);
      created++;
    }
  }

  return { synced: true, courses: courses.length, imported: created };
}

export async function getClassroomAttachments(user, body) {
  const assignmentId = body?.assignment_id;
  if (!assignmentId) throw new HttpError(400, 'assignment_id required');

  const asgRes = await pool.query('SELECT * FROM assignments WHERE id = $1 AND created_by_id = $2', [assignmentId, user.id]);
  const assignment = asgRes.rows[0];
  if (!assignment) throw new HttpError(404, 'Assignment not found');
  if (assignment.source !== 'google_classroom' || !assignment.external_id) return { attachments: [] };

  const accessToken = await getValidGoogleToken(user.id, 'google_classroom');
  if (!accessToken) throw Object.assign(new HttpError(400, 'Google Classroom not connected'), { notConnected: true });
  const authHeaders = { Authorization: `Bearer ${accessToken}` };

  const clsRes = assignment.class_id ? await pool.query('SELECT * FROM classes WHERE id = $1', [assignment.class_id]) : null;
  const courseId = clsRes?.rows[0]?.external_id;
  if (!courseId) return { attachments: [] };

  const cwRes = await fetch(
    `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${assignment.external_id}`,
    { headers: authHeaders }
  );
  if (!cwRes.ok) throw new HttpError(502, 'Classroom coursework lookup failed: ' + (await cwRes.text()));
  const cw = await cwRes.json();
  return { attachments: extractMaterials(cw.materials) };
}

function extractMaterials(materials) {
  const out = [];
  for (const m of materials || []) {
    if (m.driveFile?.driveFile) {
      const df = m.driveFile.driveFile;
      out.push({ title: df.title || 'Drive file', url: df.alternateLink, kind: 'drive' });
    } else if (m.link?.url) {
      out.push({ title: m.link.title || m.link.url, url: m.link.url, kind: 'link' });
    } else if (m.youtubeVideo?.alternateLink) {
      out.push({ title: m.youtubeVideo.title || 'YouTube video', url: m.youtubeVideo.alternateLink, kind: 'video' });
    } else if (m.form?.formUrl) {
      out.push({ title: m.form.title || 'Form', url: m.form.formUrl, kind: 'form' });
    }
  }
  return out;
}

export async function syncCompletionToClassroom(user, body) {
  const assignmentId = body?.assignment_id;
  if (!assignmentId) throw new HttpError(400, 'assignment_id required');

  const asgRes = await pool.query('SELECT * FROM assignments WHERE id = $1 AND created_by_id = $2', [assignmentId, user.id]);
  const assignment = asgRes.rows[0];
  if (!assignment) throw new HttpError(404, 'Assignment not found');
  if (assignment.source !== 'google_classroom' || !assignment.external_id) {
    throw new HttpError(400, 'Assignment is not from Google Classroom');
  }

  const accessToken = await getValidGoogleToken(user.id, 'google_classroom');
  if (!accessToken) throw Object.assign(new HttpError(400, 'Google Classroom not connected'), { notConnected: true });
  const authHeaders = { Authorization: `Bearer ${accessToken}` };

  const clsRes = assignment.class_id ? await pool.query('SELECT * FROM classes WHERE id = $1', [assignment.class_id]) : null;
  const courseId = clsRes?.rows[0]?.external_id;
  if (!courseId) throw new HttpError(400, 'Linked class has no Google course id');

  const courseWorkId = assignment.external_id;
  const subRes = await fetch(
    `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${courseWorkId}/studentSubmissions?userId=me`,
    { headers: authHeaders }
  );
  if (!subRes.ok) throw new HttpError(502, 'Classroom submissions lookup failed: ' + (await subRes.text()));
  const submissions = (await subRes.json()).studentSubmissions || [];
  const submission = submissions[0];
  if (!submission) throw new HttpError(404, 'No student submission found');

  if (submission.state === 'TURNED_IN') return { synced: true, alreadyDone: true };

  const turnInRes = await fetch(
    `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${courseWorkId}/studentSubmissions/${submission.id}:turnIn`,
    { method: 'POST', headers: { ...authHeaders, 'Content-Type': 'application/json' }, body: '{}' }
  );
  if (!turnInRes.ok) {
    const details = await turnInRes.text();
    if (turnInRes.status === 410 || details.includes('already')) return { synced: true, alreadyDone: true };
    throw new HttpError(502, 'Turn in failed: ' + details);
  }
  return { synced: true };
}
