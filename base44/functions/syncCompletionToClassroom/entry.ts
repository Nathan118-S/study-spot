import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

const CLASSROOM_CONNECTOR_ID = '6a87a2e5f3be615b69035dcd';

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
      return Response.json({ error: 'Assignment is not from Google Classroom' }, { status: 400 });
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
    if (!courseId) return Response.json({ error: 'Linked class has no Google course id' }, { status: 400 });

    const courseWorkId = assignment.external_id;

    const subRes = await fetch(
      `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${courseWorkId}/studentSubmissions?userId=me`,
      { headers: authHeaders }
    );
    if (!subRes.ok) {
      return Response.json({ error: 'Classroom submissions lookup failed', details: await subRes.text() }, { status: 502 });
    }
    const submissions = (await subRes.json()).studentSubmissions || [];
    const submission = submissions[0];
    if (!submission) return Response.json({ error: 'No student submission found' }, { status: 404 });

    if (submission.state === 'TURNED_IN') {
      return Response.json({ synced: true, alreadyDone: true });
    }

    const turnInRes = await fetch(
      `https://classroom.googleapis.com/v1/courses/${courseId}/courseWork/${courseWorkId}/studentSubmissions/${submission.id}:turnIn`,
      {
        method: 'POST',
        headers: { ...authHeaders, 'Content-Type': 'application/json' },
        body: '{}',
      }
    );
    if (!turnInRes.ok) {
      const details = await turnInRes.text();
      if (turnInRes.status === 410 || details.includes('already')) {
        return Response.json({ synced: true, alreadyDone: true });
      }
      return Response.json({ error: 'Turn in failed', details }, { status: 502 });
    }
    return Response.json({ synced: true });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}