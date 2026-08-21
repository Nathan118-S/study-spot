import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { secrets } from 'base44:runtime';
import { refreshAccessToken } from '../../shared/blackboard.ts';
import { guessPriority, guessType } from '../../shared/sync.ts';

const COLORS = ['#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];

async function getValidToken(base44, d) {
  let accessToken = d.blackboard_access_token;
  const expiresAt = d.blackboard_expires_at ? new Date(d.blackboard_expires_at).getTime() : 0;
  if (expiresAt - Date.now() < 60000 && d.blackboard_refresh_token) {
    try {
      const redirectUri = secrets.get('BLACKBOARD_REDIRECT_URI');
      const tokens = await refreshAccessToken(d.blackboard_instance_url, d.blackboard_refresh_token, redirectUri);
      accessToken = tokens.access_token;
      const newExpires = new Date(Date.now() + (tokens.expires_in || 3600) * 1000).toISOString();
      await base44.auth.updateMe({
        blackboard_access_token: tokens.access_token,
        blackboard_refresh_token: tokens.refresh_token || d.blackboard_refresh_token,
        blackboard_expires_at: newExpires,
      });
    } catch (e) {
      // fall through and try the existing token
    }
  }
  return accessToken;
}

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const d = user.data || {};
    if (!d.blackboard_access_token) {
      return Response.json({ error: 'Blackboard not connected', notConnected: true }, { status: 400 });
    }
    const instanceUrl = d.blackboard_instance_url;
    const accessToken = await getValidToken(base44, d);
    const auth = { headers: { Authorization: `Bearer ${accessToken}` } };

    // 1. Fetch the user's courses (3LO token scopes results to the caller).
    const coursesRes = await fetch(`${instanceUrl}/learn/api/public/v1/courses?limit=200`, auth);
    if (!coursesRes.ok) {
      return Response.json({ error: 'Blackboard courses failed', details: await coursesRes.text() }, { status: 502 });
    }
    const courses = (await coursesRes.json()).results || [];

    // 2. Upsert classes (dedup by external_id = course.id).
    const existingClasses = await base44.entities.Class.filter({ source: 'blackboard' });
    const classByExt = {};
    for (const c of existingClasses) classByExt[c.external_id] = c;
    let colorIdx = 0;
    const classMap = {};
    for (const course of courses) {
      const extId = course.id || course.externalId;
      if (!extId) continue;
      if (classByExt[extId]) {
        classMap[extId] = classByExt[extId];
      } else {
        classMap[extId] = await base44.entities.Class.create({
          name: course.name || 'Unnamed course',
          color: COLORS[colorIdx++ % COLORS.length],
          teacher_name: '',
          source: 'blackboard',
          external_id: extId,
        });
      }
    }

    // 3. Upsert assignments from gradebook columns (dedup by external_id = column.id).
    const existingAsg = await base44.entities.Assignment.filter({ source: 'blackboard' });
    const asgByExt = {};
    for (const a of existingAsg) asgByExt[a.external_id] = a;

    let created = 0;
    for (const course of courses) {
      const extId = course.id || course.externalId;
      const cls = classMap[extId];
      if (!cls) continue;
      const colRes = await fetch(
        `${instanceUrl}/learn/api/public/v1/courses/${encodeURIComponent(course.id)}/gradebook/columns?limit=200`,
        auth
      );
      if (!colRes.ok) continue;
      const columns = (await colRes.json()).results || [];
      for (const col of columns) {
        if (!col.dueDate) continue;
        const colExt = col.id || col.externalId;
        if (!colExt || asgByExt[colExt]) continue;
        const due = new Date(col.dueDate);
        if (isNaN(due.getTime())) continue;
        await base44.entities.Assignment.create({
          title: col.name || 'Untitled item',
          class_id: cls.id,
          class_name: cls.name,
          due_date: due.toISOString(),
          priority: guessPriority(col.name),
          type: guessType(col.name),
          notes: col.description || '',
          description: col.description || '',
          source: 'blackboard',
          external_id: colExt,
          completed: false,
          points: typeof col.pointsPossible === 'number' ? col.pointsPossible : 0,
        });
        created++;
      }
    }

    return Response.json({ synced: true, courses: courses.length, imported: created });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}