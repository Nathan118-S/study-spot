import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';

const CALENDAR_ID = '6a87a0a86ad979ee05f39b0c';
const CLASSROOM_ID = '6a87a2e5f3be615b69035dcd';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    const check = async (id) => {
      try {
        await base44.asServiceRole.connectors.getCurrentAppUserConnection(id);
        return true;
      } catch {
        return false;
      }
    };

    return Response.json({
      calendar: await check(CALENDAR_ID),
      classroom: await check(CLASSROOM_ID),
      blackboard: !!(user.data?.blackboard_access_token),
    });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}