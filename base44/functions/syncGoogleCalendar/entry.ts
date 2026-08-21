import { createClientFromRequest } from 'npm:@base44/sdk@0.8.40';
import { isAssignmentEvent, guessPriority, guessType } from '../../shared/sync.ts';

const CALENDAR_CONNECTOR_ID = '6a87a0a86ad979ee05f39b0c';

export default async function(req) {
  try {
    const base44 = createClientFromRequest(req);
    const user = await base44.auth.me();
    if (!user) return Response.json({ error: 'Unauthorized' }, { status: 401 });

    let accessToken;
    try {
      ({ accessToken } = await base44.asServiceRole.connectors.getCurrentAppUserConnection(CALENDAR_CONNECTOR_ID));
    } catch (e) {
      return Response.json({ error: 'Google Calendar not connected', notConnected: true }, { status: 400 });
    }

    const timeMin = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const timeMax = new Date(Date.now() + 120 * 24 * 60 * 60 * 1000).toISOString();
    const url =
      'https://www.googleapis.com/calendar/v3/calendars/primary/events' +
      `?singleEvents=true&orderBy=startTime&maxResults=250` +
      `&timeMin=${encodeURIComponent(timeMin)}&timeMax=${encodeURIComponent(timeMax)}`;

    const res = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (!res.ok) {
      return Response.json({ error: 'Google Calendar API error', details: await res.text() }, { status: 502 });
    }
    const data = await res.json();
    const events = (data.items || []).filter(
      (ev) => ev.start && (ev.start.dateTime || ev.start.date) && isAssignmentEvent(ev)
    );

    // Ensure a "Google Calendar" class exists for this user.
    let gcClass = (await base44.entities.Class.filter({ name: 'Google Calendar', source: 'google_calendar' }))[0];
    if (!gcClass) {
      gcClass = await base44.entities.Class.create({
        name: 'Google Calendar',
        color: '#3b82f6',
        source: 'google_calendar',
        external_id: 'google-calendar-primary',
      });
    }

    // Dedup against existing calendar-sourced assignments.
    const existing = await base44.entities.Assignment.filter({ source: 'google_calendar' });
    const seenIds = new Set(existing.map((a) => a.external_id));

    let created = 0;
    for (const ev of events) {
      if (seenIds.has(ev.id)) continue;
      const start = ev.start.dateTime || ev.start.date;
      await base44.entities.Assignment.create({
        title: ev.summary || 'Untitled event',
        class_id: gcClass.id,
        class_name: gcClass.name,
        due_date: new Date(start).toISOString(),
        priority: guessPriority(ev.summary),
        type: guessType(ev.summary),
        notes: ev.description || '',
        description: ev.description || '',
        source: 'google_calendar',
        external_id: ev.id,
        completed: false,
      });
      created++;
    }

    return Response.json({ synced: true, imported: created, reviewed: events.length });
  } catch (error) {
    return Response.json({ error: error.message }, { status: 500 });
  }
}