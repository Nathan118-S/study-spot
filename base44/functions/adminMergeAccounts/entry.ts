import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

async function reassign(entity, sourceId, targetId) {
  for (let i = 0; i < 20; i++) {
    const res = await entity.updateMany(
      { created_by_id: sourceId },
      { $set: { created_by_id: targetId } }
    );
    if (!res || !res.has_more) break;
  }
}

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const caller = await base44.auth.me();
    if (!caller) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    if (caller.role !== 'admin') return Response.json({ error: 'Forbidden' }, { status: 403 });

    const body = await req.json();
    const { sourceId, targetId } = body || {};
    if (!sourceId || !targetId) {
      return Response.json({ error: 'sourceId and targetId are required' }, { status: 400 });
    }
    if (sourceId === targetId) {
      return Response.json({ error: 'Cannot merge an account with itself' }, { status: 400 });
    }
    if (sourceId === caller.id) {
      return Response.json({ error: 'Cannot merge your own account away' }, { status: 400 });
    }

    const assignments = await base44.asServiceRole.entities.Assignment.filter(
      { created_by_id: sourceId },
      '-created_date',
      1000
    );
    const classes = await base44.asServiceRole.entities.Class.filter(
      { created_by_id: sourceId },
      '-created_date',
      1000
    );

    if (assignments.length) {
      await reassign(base44.asServiceRole.entities.Assignment, sourceId, targetId);
    }
    if (classes.length) {
      await reassign(base44.asServiceRole.entities.Class, sourceId, targetId);
    }

    await base44.asServiceRole.entities.User.delete(sourceId);

    return Response.json({
      ok: true,
      movedAssignments: assignments.length,
      movedClasses: classes.length,
    });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}