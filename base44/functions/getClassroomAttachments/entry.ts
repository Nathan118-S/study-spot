import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

const CLASSROOM_CONNECTOR_ID = '6a87a2e5f3be615b69035dcd';

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

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const body = await req.json().catch(() => ({}));
    const assignmentId = body.assignment_id;
    if (!assignmentId) return Response.json({ error: 'assignment_id required' }, { status: 400 });

    const assignment = await base44.entities.Assignment.get(assignmentId);
    if (!assignment) return Response.json({ error: 'Assignment not found' }, { status: 404 });
    if (assignment.source !== 'google_classroom' || !assignment.external_id) {
      return Response.json({ attachments: [] });
    }

    let accessToken;
    try {
      ({ accessToken } = await base44.asServiceRole.connectors.getCurrentAppUserConnection(CLASSROOM_CONNECTOR_ID));
    } catch (e) {
      return Response.json({ error: 'Google Classroom not connected', notConnected: true }, { status: 400 });
    }
    const authHeaders = { Authorization: `Bearer ${accessToken}` };

    const cls = assignment.class_id ? await base44.entities.Class.get(assignment.class_id) : null;
    const courseId = cls?.external_id;
    if (!courseId) return Response.json({ attachments: [] });

    const cwRes = await fetch(
      `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${assignment.external_id}`,
      { headers: authHeaders }
    );
    if (!cwRes.ok) {
      return Response.json({ error: 'Classroom coursework lookup failed', details: await cwRes.text() }, { status: 502 });
    }
    const cw = await cwRes.json();
    return Response.json({ attachments: extractMaterials(cw.materials) });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}