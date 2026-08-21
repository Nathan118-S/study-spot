import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { guessPriority, guessType } from '../../shared/sync.ts';

const CLASSROOM_CONNECTOR_ID = '6a87a2e5f3be615b69035dcd';

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];

function courseworkDueISO(cw) {
  if (!cw.dueDate) return null;
  const d = cw.dueDate;
  const t = cw.dueTime || { hours: 23, minutes: 59 };
  return new Date(
    Date.UTC(d.year, (d.month || 1) - 1, d.day || 1, t.hours ?? 23, t.minutes ?? 59)
  ).toISOString();
}

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    let accessToken;
    try {
      ({ accessToken } = await base44.asServiceRole.connectors.getCurrentAppUserConnection(CLASSROOM_CONNECTOR_ID));
    } catch (e) {
      return Response.json({ error: 'Google Classroom not connected', notConnected: true }, { status: 400 });
    }

    const auth = { headers: { Authorization: `Bearer ${accessToken}` } };
    const coursesRes = await fetch('https://classroom.googleapis.com/v1/courses?courseStates=ACTIVE', auth);
    if (!coursesRes.ok) {
      return Response.json({ error: 'Classroom API error', details: await coursesRes.text() }, { status: 502 });
    }
    const courses = (await coursesRes.json()).courses || [];

    // Upsert classes (dedup by external_id = course.id).
    const existingClasses = await base44.entities.Class.filter({ source: 'google_classroom' });
    const classByExt = {};
    for (const c of existingClasses) classByExt[c.external_id] = c;
    let colorIdx = 0;
    const classMap = {};
    for (const course of courses) {
      if (classByExt[course.id]) {
        classMap[course.id] = classByExt[course.id];
      } else {
        classMap[course.id] = await base44.entities.Class.create({
          name: course.name || 'Unnamed course',
          color: COLORS[colorIdx++ % COLORS.length],
          teacher_name: '',
          source: 'google_classroom',
          external_id: course.id,
        });
      }
    }

    // Upsert assignments (dedup by external_id = coursework id).
    const existingAsg = await base44.entities.Assignment.filter({ source: 'google_classroom' });
    const asgByExt = {};
    for (const a of existingAsg) asgByExt[a.external_id] = a;

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
        if (asgByExt[cw.id]) continue;
        const due = courseworkDueISO(cw);
        if (!due) continue; // skip coursework with no due date
        await base44.entities.Assignment.create({
          title: cw.title || 'Untitled coursework',
          class_id: cls.id,
          class_name: cls.name,
          due_date: due,
          priority: guessPriority(cw.title),
          type: guessType(cw.title),
          notes: cw.description || '',
          description: cw.description || '',
          source: 'google_classroom',
          external_id: cw.id,
          completed: false,
        });
        created++;
      }
    }

    return Response.json({ synced: true, courses: courses.length, imported: created });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}