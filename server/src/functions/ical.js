// Google Calendar links ("Secret address in iCal format") for people who
// can't connect their Google account, e.g. because their school blocks
// third-party apps. Each link becomes a class; its assignment-like events
// become assignments, kept up to date on every sync.
import { pool } from '../db.js';
import { HttpError } from '../lib/httpError.js';
import { decryptValue, encryptValue } from '../lib/secrets.js';
import { eventDue, feedKind, fetchIcal, icalUrlHint, LMS_NAMES, normalizeIcalUrl, parseIcal } from '../lib/ical.js';
import { guessPriority, guessType, isAssignmentEvent } from '../lib/sync.js';
import { userTimeZone } from '../jobs/classActivities.js';
import { readZip } from '../lib/zip.js';

const MAX_FEEDS = 25;
const COLORS = ['#3b82f6', '#6366f1', '#ec4899', '#f59e0b', '#10b981', '#06b6d4', '#8b5cf6', '#ef4444', '#14b8a6'];
const PAST_DAYS = 14;
const FUTURE_DAYS = 180;

// The kind is recorded at the end of the non-secret hint, e.g. "school.edu (Canvas)".
function kindOfRow(row) {
  const m = /\((Blackboard|Canvas|Schoology|Brightspace)\)$/.exec(String(row.url_hint || ''));
  return m ? m[1].toLowerCase() : 'google';
}

/** Assignment/class source for each kind of link. */
const SOURCE = { google: 'google_calendar', blackboard: 'blackboard', canvas: 'canvas', schoology: 'schoology', brightspace: 'brightspace' };
/** School systems whose calendars are mostly coursework: import everything by default. */
const ALL_BY_DEFAULT = new Set(['blackboard', 'schoology']);

function publicFeed(row) {
  const kind = kindOfRow(row);
  return {
    id: row.id,
    label: row.label || row.calendar_name || (kind === 'google' ? 'Google Calendar' : LMS_NAMES[kind]),
    kind,
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

/**
 * The course an event belongs to. Blackboard's calendar link has no course
 * field; titles are usually "Course name: Item name", so split on the first
 * ": " when the prefix looks like a course (other feeds may use CATEGORIES).
 */
export function splitCourse(ev, kind) {
  if (ev.category) return { course: ev.category, title: ev.summary };
  const summary = ev.summary || '';
  // Canvas: "Essay 1 [ENGL 101]".
  if (kind === 'canvas') {
    const m = /^(.*\S)\s*\[([^\]]{1,120})\]\s*$/.exec(summary);
    if (m) return { course: m[2].trim(), title: m[1].trim() };
  }
  // Brightspace: "Quiz 1 - Due", with the course as the location.
  if (kind === 'brightspace') {
    return { course: ev.location || undefined, title: summary.replace(/\s+-\s+Due$/i, '').trim() || summary };
  }
  if (kind === 'blackboard') {
    const i = summary.indexOf(': ');
    if (i > 1 && i <= 80) {
      const title = summary.slice(i + 2).trim();
      if (title) return { course: summary.slice(0, i).trim(), title };
    }
  }
  return { course: undefined, title: summary };
}

/** Whether an event looks like schoolwork (mode 'auto'), or always (mode 'all'). */
export function isImportable(ev, mode, kind = 'google') {
  if (ev.cancelled) return false;
  if (mode === 'all') return true;
  // Canvas marks assignments in the UID ("event-assignment-123"); Brightspace
  // titles due dates "… - Due" (other events are availability windows).
  if (kind === 'canvas' && /assignment/i.test(ev.uid || '')) return true;
  if (kind === 'brightspace') return /\s-\sDue$/i.test(ev.summary || '');
  // Google Classroom's class calendars link every event to Classroom.
  if (/classroom\.google\.com/i.test(`${ev.description || ''} ${ev.url || ''}`)) return true;
  return isAssignmentEvent({ summary: ev.summary, description: ev.description });
}

async function classForFeed(user, feed, calendarName, category, kind = 'google') {
  // Events that name their course (e.g. Blackboard) get a class per course.
  const ext = category ? `ical:${feed.id}:cat:${category.slice(0, 120)}` : `ical:${feed.id}`;
  const name = (category || feed.label || calendarName || (kind === 'google' ? 'Google Calendar' : LMS_NAMES[kind])).slice(0, 120);
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
    `INSERT INTO classes (created_by_id, name, color, source, external_id) VALUES ($1, $2, $3, $5, $4) RETURNING *`,
    [user.id, name, COLORS[count % COLORS.length], ext, SOURCE[kind] || 'google_calendar']
  );
  return ins.rows[0];
}

/** Imports one feed. Returns { imported, updated, reviewed }. */
async function syncFeed(user, feed) {
  const tz = userTimeZone(user.data);
  try {
    const url = feedUrl(feed);
    const kind = feedKind(url);
    const text = await fetchIcal(url);
    const { calendarName, events } = parseIcal(text, tz);
    const now = Date.now();
    const from = now - PAST_DAYS * 86400e3;
    const to = now + FUTURE_DAYS * 86400e3;
    const wanted = [];
    for (const ev of events) {
      if (!ev.uid || !isImportable(ev, feed.mode, kind)) continue;
      const due = eventDue(ev.start, tz);
      if (!due || due.getTime() < from || due.getTime() > to) continue;
      const { course, title: itemTitle } = splitCourse(ev, kind);
      wanted.push({ ...ev, course, summary: itemTitle, due, externalId: `ical:${feed.id}:${ev.uid}${ev.recurrenceId ? `:${ev.recurrenceId}` : ''}` });
    }

    const classCache = new Map();
    const classFor = async (category) => {
      const key = category || '';
      if (!classCache.has(key)) classCache.set(key, await classForFeed(user, feed, calendarName, category, kind));
      return classCache.get(key);
    };
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
      const cls = await classFor(ev.course);
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed)
         VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, $10, $9, false)`,
        [user.id, title, cls.id, cls.name, ev.due.toISOString(), guessPriority(title), guessType(title),
          (ev.description || '').slice(0, 5000), ev.externalId, SOURCE[kind] || 'google_calendar']
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
  // Blackboard's calendar is mostly course due dates, so import all of it by default.
  const mode = body?.mode === 'all' || body?.mode === 'auto' ? body.mode : ALL_BY_DEFAULT.has(feedKind(url)) ? 'all' : 'auto';
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
  // Switching to "every event" should bring in the events skipped before.
  if (mode !== feed.mode) {
    try { await syncFeed(user, upd.rows[0]); } catch { /* the error is saved on the feed */ }
  }
  if (label && label !== feed.label) {
    await pool.query('UPDATE classes SET name = $1, updated_at = now() WHERE created_by_id = $2 AND external_id = $3', [label, user.id, `ical:${feed.id}`]);
  }
  const fresh = (await pool.query('SELECT * FROM ical_feeds WHERE id = $1', [feed.id])).rows[0];
  return { ok: true, feed: publicFeed(fresh) };
}

/** Body: { id, removeAssignments?: boolean } — by default imported work is kept. */
export async function icalRemoveFeed(user, body) {
  const res = await pool.query('DELETE FROM ical_feeds WHERE id = $1 AND user_id = $2 RETURNING id', [body?.id, user.id]);
  if (!res.rows[0]) throw new HttpError(404, 'Calendar not found');
  let removed = 0;
  if (body.removeAssignments) {
    removed = (await pool.query('DELETE FROM assignments WHERE created_by_id = $1 AND external_id LIKE $2', [user.id, `ical:${body.id}:%`])).rowCount;
    await pool.query('DELETE FROM classes WHERE created_by_id = $1 AND (external_id = $2 OR external_id LIKE $3)', [user.id, `ical:${body.id}`, `ical:${body.id}:cat:%`]);
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

// --- One-time import of a calendar file -------------------------------------
// For when calendar links are turned off too: Google Calendar → Settings →
// Import & export → Export gives a .zip of .ics files (one per calendar);
// other calendars can export a single .ics.

const MAX_UPLOAD = 10 * 1024 * 1024;

/**
 * Body: { filename, data (base64 of a .ics or .zip), mode?: 'auto'|'all' }
 * Imports upcoming events once; re-importing the same file doesn't duplicate.
 */
export async function icalImportFile(user, body) {
  if (user.role === 'demo') throw new HttpError(400, "The demo account can't import calendars");
  const filename = String(body?.filename || 'calendar.ics');
  let buf;
  try {
    buf = Buffer.from(String(body?.data || ''), 'base64');
  } catch {
    throw new HttpError(400, 'That file couldn’t be read');
  }
  if (!buf.length) throw new HttpError(400, 'Choose a calendar file (.ics or .zip)');
  if (buf.length > MAX_UPLOAD) throw new HttpError(400, 'That file is too large (10 MB max)');

  let files;
  if (buf.readUInt32LE(0) === 0x04034b50) {
    try {
      files = readZip(buf).filter((f) => /\.ics$/i.test(f.name));
    } catch (e) {
      throw new HttpError(400, e.message);
    }
    if (!files.length) throw new HttpError(400, 'That .zip has no calendar (.ics) files in it');
  } else {
    files = [{ name: filename, data: buf }];
  }

  const mode = body?.mode === 'all' ? 'all' : 'auto';
  const tz = userTimeZone(user.data);
  const now = Date.now();
  const from = now - PAST_DAYS * 86400e3;
  const to = now + 365 * 86400e3;
  const calendars = [];
  let imported = 0;
  for (const f of files) {
    const text = f.data.toString('utf8');
    if (!/BEGIN:VCALENDAR/.test(text)) continue;
    const { calendarName, events } = parseIcal(text, tz);
    const baseName = (calendarName || f.name.split('/').pop().replace(/\.ics$/i, '').replace(/_[^_]*@[^_]*$/, '') || 'Imported calendar').slice(0, 120);
    const existing = await pool.query(
      'SELECT external_id FROM assignments WHERE created_by_id = $1 AND external_id LIKE $2',
      [user.id, 'icsfile:%']
    );
    const seen = new Set(existing.rows.map((r) => r.external_id));
    let cls = null;
    let count = 0;
    for (const ev of events) {
      if (!ev.uid || !isImportable(ev, mode)) continue;
      const due = eventDue(ev.start, tz);
      if (!due || due.getTime() < from || due.getTime() > to) continue;
      const ext = `icsfile:${ev.uid}${ev.recurrenceId ? `:${ev.recurrenceId}` : ''}`.slice(0, 500);
      if (seen.has(ext)) continue;
      if (!cls) {
        const clsExt = `icsfile:${baseName}`;
        const found = await pool.query('SELECT * FROM classes WHERE created_by_id = $1 AND external_id = $2 LIMIT 1', [user.id, clsExt]);
        cls = found.rows[0];
        if (!cls) {
          const n = (await pool.query('SELECT count(*)::int n FROM classes WHERE created_by_id = $1', [user.id])).rows[0].n;
          cls = (await pool.query(
            `INSERT INTO classes (created_by_id, name, color, source, external_id) VALUES ($1, $2, $3, 'manual', $4) RETURNING *`,
            [user.id, baseName, COLORS[n % COLORS.length], clsExt]
          )).rows[0];
        }
      }
      const title = (ev.summary || 'Untitled event').slice(0, 300);
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed)
         VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, 'manual', $9, false)`,
        [user.id, title, cls.id, cls.name, due.toISOString(), guessPriority(title), guessType(title), (ev.description || '').slice(0, 5000), ext]
      );
      seen.add(ext);
      count++;
    }
    calendars.push({ name: baseName, imported: count, events: events.length });
    imported += count;
  }
  if (!calendars.length) throw new HttpError(400, 'That file isn’t a calendar. Choose the .ics or .zip file you exported.');
  return { ok: true, imported, calendars };
}

// --- Import from the phone's calendar --------------------------------------
// Zero school setup: if the student's school Google account is on their phone,
// Google Classroom due dates sync to the device calendar. The app filters
// on-device and uploads only the assignments the student confirms (never the
// raw calendar). Each phone calendar becomes a class.

/**
 * Body: { platform: 'ios'|'android', items: [{ calendarKey, calendarTitle,
 *   eventKey, title, due (ISO), description? }] }. Replaces this device's
 *   imported assignments for the calendars present in `items` (so events the
 *   student unticks or that are removed go away), then adds/updates the rest.
 */
export async function deviceCalendarImport(user, body) {
  if (user.role === 'demo') throw new HttpError(400, "The demo account can't import calendars");
  const platform = body?.platform === 'android' ? 'android' : 'ios';
  const items = Array.isArray(body?.items) ? body.items.slice(0, 2000) : null;
  if (!items) throw new HttpError(400, 'Nothing to import');

  const prefix = `device:${platform}:`;
  // Group by calendar; only calendars present in this payload are reconciled.
  const byCalendar = new Map();
  for (const it of items) {
    const calKey = String(it?.calendarKey || '').slice(0, 200);
    const eventKey = String(it?.eventKey || '').slice(0, 200);
    const due = it?.due ? new Date(it.due) : null;
    if (!calKey || !eventKey || !due || isNaN(due.getTime())) continue;
    if (!byCalendar.has(calKey)) byCalendar.set(calKey, { title: String(it?.calendarTitle || 'Calendar').slice(0, 120), events: new Map() });
    byCalendar.get(calKey).events.set(eventKey, {
      title: String(it?.title || 'Untitled').slice(0, 300),
      due,
      description: String(it?.description || '').slice(0, 5000),
    });
  }

  const existing = await pool.query(
    'SELECT id, external_id, title, due_date FROM assignments WHERE created_by_id = $1 AND source = $2 AND external_id LIKE $3',
    [user.id, 'device_calendar', `${prefix}%`]
  );
  const byExt = new Map(existing.rows.map((a) => [a.external_id, a]));
  const wantedExt = new Set();
  let imported = 0;
  let updated = 0;

  for (const [calKey, cal] of byCalendar) {
    const classExt = `${prefix}${calKey}`;
    let cls = (await pool.query('SELECT * FROM classes WHERE created_by_id = $1 AND external_id = $2 LIMIT 1', [user.id, classExt])).rows[0];
    if (!cls) {
      const n = (await pool.query('SELECT count(*)::int n FROM classes WHERE created_by_id = $1', [user.id])).rows[0].n;
      cls = (await pool.query(
        `INSERT INTO classes (created_by_id, name, color, source, external_id) VALUES ($1, $2, $3, 'device_calendar', $4) RETURNING *`,
        [user.id, cal.title, COLORS[n % COLORS.length], classExt]
      )).rows[0];
    }
    for (const [eventKey, ev] of cal.events) {
      const ext = `${prefix}${calKey}:${eventKey}`.slice(0, 500);
      wantedExt.add(ext);
      const prev = byExt.get(ext);
      if (prev) {
        if (prev.title !== ev.title || new Date(prev.due_date).getTime() !== ev.due.getTime()) {
          await pool.query('UPDATE assignments SET title = $1, due_date = $2, updated_at = now() WHERE id = $3', [ev.title, ev.due.toISOString(), prev.id]);
          updated++;
        }
        continue;
      }
      await pool.query(
        `INSERT INTO assignments (created_by_id, title, class_id, class_name, due_date, priority, type, notes, description, source, external_id, completed)
         VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, 'device_calendar', $9, false)`,
        [user.id, ev.title, cls.id, cls.name, ev.due.toISOString(), guessPriority(ev.title), guessType(ev.title), ev.description, ext]
      );
      imported++;
    }
  }

  // Remove this device's imported items for the included calendars that are no
  // longer present (unticked, or deleted in the calendar). Keep completed ones.
  let removed = 0;
  for (const [ext, a] of byExt) {
    const calKey = ext.slice(prefix.length, ext.lastIndexOf(':'));
    if (byCalendar.has(calKey) && !wantedExt.has(ext)) {
      const del = await pool.query('DELETE FROM assignments WHERE id = $1 AND NOT completed', [a.id]);
      removed += del.rowCount;
    }
  }
  // Drop empty device classes.
  await pool.query(
    `DELETE FROM classes c WHERE c.created_by_id = $1 AND c.source = 'device_calendar'
       AND NOT EXISTS (SELECT 1 FROM assignments a WHERE a.class_id = c.id)`,
    [user.id]
  );
  return { ok: true, imported, updated, removed };
}
