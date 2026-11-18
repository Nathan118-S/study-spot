// Google Calendar links ("Secret address in iCal format") for people who
// can't connect their Google account, e.g. because their school blocks
// third-party apps. Each link becomes a class; its assignment-like events
// become assignments, kept up to date on every sync.
import { pool } from '../db.js';
import { HttpError } from '../lib/httpError.js';
import { decryptValue, encryptValue } from '../lib/secrets.js';
import { eventDue, fetchIcal, icalUrlHint, normalizeIcalUrl, parseIcal } from '../lib/ical.js';
import { guessPriority, guessType, isAssignmentEvent } from '../lib/sync.js';
import { userTimeZone } from '../jobs/classActivities.js';

const MAX_FEEDS = 25;
const COLORS = ['#3b82f6', '#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];
const PAST_DAYS = 14;
const FUTURE_DAYS = 180;

function publicFeed(row) {
  return {
    id: row.id,
    label: row.label || row.calendar_name || 'Google Calendar',
    calendar_name: row.calendar_name,
    url_hint: row.url_hint,
    mode: row.mode,
    last_synced_at: row.last_synced_at,
    last_error: row.last_error,
    last_imported: row.last_imported,
    created_at: row.created_at,
  };
}

const feedUrl = (row) => decryptValue({ iv: row.url_iv, ciphertext: row.url_ciphertext, authTag: row.url_auth_tag });

/** Whether an event looks like schoolwork (mode 'auto'), or always (mode 'all'). */
export function isImportable(ev, mode) {
  if (ev.cancelled) return false;
  if (mode === 'all') return true;
  // Google Classroom's class calendars link every event to Classroom.
  if (/classroom\.google\.com/i.test(`${ev.description || ''} ${ev.url || ''}`)) return true;
  return isAssignmentEvent({ summary: ev.summary, description: ev.description });
}

async function classForFeed(user, feed, calendarName) {
  const ext = `ical:${feed.id}`;
  const name = (feed.label || calendarName || 'Google Calendar').slice(0, 120);
  const existing = await pool.query('SELECT * FROM classes WHERE created_by_id = $1 AND external_id = $2 LIMIT 1', [user.id, ext]);
  if (existing.rows[0]) {
    // Follow a renamed calendar unless the person renamed the class themselves.
    if (!feed.label && calendarName && existing.rows[0].name === 'Google Calendar') {
      await pool.query('UPDATE classes SET name = $1, updated_at = now() WHERE id = $2', [name, existing.rows[0].id]);
      existing.rows[0].name = name;
    }
    return existing.rows[0];
  }
  const count = (await pool.query('SELECT count(*)::int n FROM classes WHERE created_by_id = $1', [user.id])).rows[0].n;
  const ins = await pool.query(
    `INSERT INTO classes (created_by_id, name, color, source, external_id) VALUES ($1, $2, $3, 'google_calendar', $4) RETURNING *`,
    [user.id, name, COLORS[count % COLORS.length], ext]
  );
  return ins.rows[0];
}

/** Imports one feed. Returns { imported, updated, reviewed }. */
async function syncFeed(user, feed) {
  const tz = userTimeZone(user.data);
  try {
    const text = await fetchIcal(feedUrl(feed));
    const { calendarName, events } = parseIcal(text, tz);
    const now = Date.now();
    const from = now - PAST_DAYS * 86400e3;
    const to = now + FUTURE_DAYS * 86400e3;
    const wanted = [];
    for (const ev of events) {
      if (!ev.uid || !isImportable(ev, feed.mode)) continue;
      const due = eventDue(ev.start, tz);
      if (!due || due.getTime() < from || due.getTime() > to) continue;
      wanted.push({ ...ev, due, externalId: `ical:${feed.id}:${ev.uid}${ev.recurrenceId ? `:${ev.recurrenceId}` : ''}` });
    }

    const cls = await classForFeed(user, feed, calendarName);
    const existing = await pool.query(
      'SELECT id, external_id, title, due_date FROM assignments WHERE created_by_id = $1 AND external_id LIKE $2',
      [user.id, `ical:${feed.id}:%`]
    );
    const byExt = new Map(existing.rows.map((a) => [a.external_id, a]));
    let imported = 0;
    let updated = 0;
    for (const ev of wanted) {
      const title = (ev.summary || 'Untitled event').slice(0, 300);
      const prev = byExt.get(ev.externalId);
      if (prev) {
        // Teachers move due dates and rename work; follow those changes.
        if (prev.title !== title || new Date(prev.due_date).getTime() !== ev.due.getTime()) {
          await pool.query(
            'UPDATE assignments SET title = $1, due_date = $2, reminder_sent = false, reminder_sent_hours = \'{}\', updated_at = now() WHERE id = $3',
            [title, ev.due.toISOString(), prev.id]
          );
          updated++;
        }
        continue;
      }
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed)
         VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, 'google_calendar', $9, false)`,
        [user.id, title, cls.id, cls.name, ev.due.toISOString(), guessPriority(title), guessType(title),
          (ev.description || '').slice(0, 5000), ev.externalId]
      );
      imported++;
    }
    await pool.query(
      'UPDATE ical_feeds SET calendar_name = $1, last_synced_at = now(), last_error = NULL, last_imported = $2 WHERE id = $3',
      [calendarName, imported, feed.id]
    );
    return { imported, updated, reviewed: wanted.length };
  } catch (err) {
    await pool.query('UPDATE ical_feeds SET last_synced_at = now(), last_error = $1 WHERE id = $2', [String(err.message).slice(0, 300), feed.id]);
    throw err;
  }
}

export async function icalListFeeds(user) {
  const res = await pool.query('SELECT * FROM ical_feeds WHERE user_id = $1 ORDER BY created_at', [user.id]);
  return { feeds: res.rows.map(publicFeed) };
}

/** Adds a calendar link and imports it right away. Body: { url, label?, mode? } */
export async function icalAddFeed(user, body) {
  if (user.role === 'demo') throw new HttpError(400, "The demo account can't add calendars");
  let url;
  try {
    url = normalizeIcalUrl(body?.url);
  } catch (e) {
    throw new HttpError(400, e.message);
  }
  const mode = body?.mode === 'all' ? 'all' : 'auto';
  const label = String(body?.label || '').trim().slice(0, 120) || null;

  const existing = await pool.query('SELECT * FROM ical_feeds WHERE user_id = $1', [user.id]);
  if (existing.rows.length >= MAX_FEEDS) throw new HttpError(400, `You can add up to ${MAX_FEEDS} calendars`);
  if (existing.rows.some((r) => { try { return feedUrl(r) === url; } catch { return false; } })) {
    throw new HttpError(400, 'You’ve already added this calendar');
  }

  // Check the link works before saving it.
  let parsed;
  try {
    parsed = parseIcal(await fetchIcal(url), userTimeZone(user.data));
  } catch (e) {
    throw new HttpError(400, e.message);
  }

  const enc = encryptValue(url);
  const ins = await pool.query(
    `INSERT INTO ical_feeds (user_id, label, url_hint, url_iv, url_ciphertext, url_auth_tag, mode, calendar_name)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING *`,
    [user.id, label, icalUrlHint(url), enc.iv, enc.ciphertext, enc.authTag, mode, parsed.calendarName]
  );
  const feed = ins.rows[0];
  const result = await syncFeed(user, feed);
  const fresh = (await pool.query('SELECT * FROM ical_feeds WHERE id = $1', [feed.id])).rows[0];
  return { ok: true, feed: publicFeed(fresh), ...result, events: parsed.events.length };
}

/** Body: { id, label?, mode? } */
export async function icalUpdateFeed(user, body) {
  const res = await pool.query('SELECT * FROM ical_feeds WHERE id = $1 AND user_id = $2', [body?.id, user.id]);
  const feed = res.rows[0];
  if (!feed) throw new HttpError(404, 'Calendar not found');
  const label = body.label === undefined ? feed.label : String(body.label || '').trim().slice(0, 120) || null;
  const mode = body.mode === undefined ? feed.mode : body.mode === 'all' ? 'all' : 'auto';
  const upd = await pool.query('UPDATE ical_feeds SET label = $1, mode = $2 WHERE id = $3 RETURNING *', [label, mode, feed.id]);
  if (label && label !== feed.label) {
    await pool.query('UPDATE classes SET name = $1, updated_at = now() WHERE created_by_id = $2 AND external_id = $3', [label, user.id, `ical:${feed.id}`]);
  }
  return { ok: true, feed: publicFeed(upd.rows[0]) };
}

/** Body: { id, removeAssignments?: boolean } — by default imported work is kept. */
export async function icalRemoveFeed(user, body) {
  const res = await pool.query('DELETE FROM ical_feeds WHERE id = $1 AND user_id = $2 RETURNING id', [body?.id, user.id]);
  if (!res.rows[0]) throw new HttpError(404, 'Calendar not found');
  let removed = 0;
  if (body.removeAssignments) {
    removed = (await pool.query('DELETE FROM assignments WHERE created_by_id = $1 AND external_id LIKE $2', [user.id, `ical:${body.id}:%`])).rowCount;
    await pool.query('DELETE FROM classes WHERE created_by_id = $1 AND external_id = $2', [user.id, `ical:${body.id}`]);
  }
  return { ok: true, removedAssignments: removed };
}

/** Syncs all of the user's calendar links. */
export async function icalSyncFeeds(user) {
  const res = await pool.query('SELECT * FROM ical_feeds WHERE user_id = $1 ORDER BY created_at', [user.id]);
  let imported = 0;
  let updated = 0;
  const errors = [];
  for (const feed of res.rows) {
    try {
      const r = await syncFeed(user, feed);
      imported += r.imported;
      updated += r.updated;
    } catch (e) {
      errors.push({ id: feed.id, label: feed.label || feed.calendar_name || 'Google Calendar', error: e.message });
    }
  }
  return { synced: true, feeds: res.rows.length, imported, updated, errors };
}

/** Scheduled job: refresh everyone's calendar links. */
export async function syncAllIcalFeeds() {
  const users = await pool.query(
    "SELECT DISTINCT u.* FROM users u JOIN ical_feeds f ON f.user_id = u.id WHERE u.role <> 'disabled'"
  );
  let imported = 0;
  for (const user of users.rows) {
    try {
      imported += (await icalSyncFeeds(user)).imported;
    } catch (err) {
      console.warn('[ical] sync failed for a user:', err.message);
    }
  }
  return { users: users.rows.length, imported };
}
