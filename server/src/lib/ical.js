// Minimal iCalendar (RFC 5545) reader for calendar links (Google Calendar,
// Blackboard Learn) and uploaded .ics files: enough to pull events (title,
// description, start, UID) out of a calendar. No dependencies.
import dns from 'node:dns/promises';
import net from 'node:net';
import { zonedTimeToUtc } from '../jobs/classActivities.js';

// Google's iCal links. Blackboard links live on each school's own domain, so
// those are allowed by path instead, and only after checking the host
// resolves to a public address (a pasted link can't reach internal servers).
const ALLOWED_HOSTS = new Set(['calendar.google.com']);
// School learning systems' personal calendar links, recognised by path (each
// school has its own domain):
// - Blackboard Learn:  /webapps/calendar/calendarFeed/<id>/learn.ics
// - Canvas:            /feeds/calendars/user_<token>.ics
// - Schoology:         /calendar/feed/ical/<id>/<hash>/ical.ics
// - Brightspace (D2L): /d2l/le/calendar/feed/user/feed.ics?token=…
const LMS_FEEDS = [
  { kind: 'blackboard', path: /\/webapps\/calendar\/calendarFeed\/[^/]+\/[^/]+\.ics$/i },
  { kind: 'canvas', path: /^\/feeds\/calendars\/user_[A-Za-z0-9_-]+\.ics$/i },
  { kind: 'schoology', path: /\/calendar\/feed\/ical\/.+\.ics$/i },
  { kind: 'brightspace', path: /^\/d2l\/le\/calendar\/feed\/user\/feed\.ics$/i, needsQuery: 'token' },
];
export const LMS_NAMES = { blackboard: 'Blackboard', canvas: 'Canvas', schoology: 'Schoology', brightspace: 'Brightspace' };

function lmsKind(url) {
  const hit = LMS_FEEDS.find((f) => f.path.test(url.pathname) && (!f.needsQuery || url.searchParams.get(f.needsQuery)));
  return hit?.kind || null;
}

/** 'google', 'blackboard', 'canvas', 'schoology' or 'brightspace' for a normalized calendar link. */
export function feedKind(url) {
  const u = new URL(url);
  if (ALLOWED_HOSTS.has(u.hostname.toLowerCase())) return 'google';
  return lmsKind(u) || 'blackboard';
}

function isPrivateAddress(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
      || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
  }
  const v = ip.toLowerCase();
  if (v.startsWith('::ffff:')) return isPrivateAddress(v.slice(7));
  return v === '::1' || v === '::' || v.startsWith('fc') || v.startsWith('fd') || v.startsWith('fe8') || v.startsWith('fe9')
    || v.startsWith('fea') || v.startsWith('feb') || v.startsWith('ff');
}

/** Throws unless every address the host resolves to is public. */
export async function assertPublicHost(hostname) {
  if (net.isIP(hostname)) throw new Error('Use the calendar link from Blackboard (a web address, not an IP address).');
  let addrs;
  try {
    addrs = await dns.lookup(hostname, { all: true, verbatim: true });
  } catch {
    throw new Error(`Couldn’t find ${hostname}. Check the link.`);
  }
  if (!addrs.length || addrs.some((a) => isPrivateAddress(a.address))) {
    throw new Error('That calendar link points to a private network address.');
  }
}

function checkBlackboardUrl(url) {
  if (url.protocol !== 'https:') throw new Error('The link should start with https://');
  if (url.username || url.password || url.port) throw new Error('That calendar link looks unusual. Copy it again from Blackboard.');
  const host = url.hostname.toLowerCase();
  if (net.isIP(host) || !host.includes('.') || /(^|\.)(localhost|local|internal)$/.test(host)) {
    throw new Error('Use the calendar link from Blackboard.');
  }
}

const publicFeedFor = (calendarId) =>
  `https://calendar.google.com/calendar/ical/${encodeURIComponent(calendarId)}/public/basic.ics`;

function looksLikeCalendarId(id) {
  return /^[^\s@/]+@[a-z0-9.-]+\.[a-z]+$/i.test(id) || /^[a-z0-9._%+-]+$/i.test(id);
}

/**
 * Cleans up a pasted Google Calendar link and returns the iCal address to
 * fetch, or throws an Error with a message for the user. Accepts:
 * - the "Public URL to this calendar"  …/calendar/embed?src=<id>
 * - a share link                         …/calendar/u/0?cid=<base64 id> (or /r?cid=)
 * - the "Public address in iCal format"  …/calendar/ical/<id>/public/basic.ics
 * - the "Secret address in iCal format"  …/calendar/ical/<id>/private-…/basic.ics
 * - webcal:// versions of the iCal addresses
 */
export function normalizeIcalUrl(raw) {
  let s = String(raw || '').trim().replace(/^<|>$/g, '');
  if (!s) throw new Error('Paste your calendar’s public URL');
  if (/^webcals?:\/\//i.test(s)) s = s.replace(/^webcals?:\/\//i, 'https://');
  if (/^calendar\.google\.com\//i.test(s)) s = `https://${s}`;
  let url;
  try {
    url = new URL(s);
  } catch {
    throw new Error('That doesn’t look like a link. Copy the whole “Public URL to this calendar”.');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') throw new Error('The link should start with https://');
  if (!ALLOWED_HOSTS.has(url.hostname.toLowerCase())) {
    // A school system's own calendar link (Blackboard, Canvas, Schoology, Brightspace).
    if (lmsKind(url)) {
      url.protocol = 'https:';
      checkBlackboardUrl(url);
      url.hash = '';
      return url.toString();
    }
    throw new Error('Use a calendar link from Google Calendar, Blackboard, Canvas, Schoology or Brightspace. See the guide for where to find it.');
  }

  // iCal addresses (public or secret) are used as they are.
  if (/^\/calendar\/ical\/.+\.ics$/i.test(url.pathname)) {
    url.protocol = 'https:';
    return url.toString();
  }

  // "Public URL to this calendar": …/embed?src=<id>[&src=<id2>…] (first calendar).
  const src = url.searchParams.get('src');
  if (/\/calendar\/(?:u\/\d+\/)?embed/i.test(url.pathname) && src) {
    if (!looksLikeCalendarId(src)) throw new Error('That calendar link looks incomplete. Copy it again.');
    return publicFeedFor(src);
  }

  // Share links: …?cid=<calendar id, usually base64url-encoded>.
  const cid = url.searchParams.get('cid');
  if (cid) {
    let id = cid;
    if (!cid.includes('@')) {
      try {
        const decoded = Buffer.from(cid.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8');
        if (looksLikeCalendarId(decoded)) id = decoded;
      } catch { /* keep as is */ }
    }
    if (!looksLikeCalendarId(id)) throw new Error('That calendar link looks incomplete. Copy it again.');
    return publicFeedFor(id);
  }

  throw new Error('Use the calendar’s “Public URL to this calendar” from Settings and sharing → Integrate calendar.');
}

/** Whether a normalized address is a public feed (needs the calendar shared publicly). */
export const isPublicFeed = (url) => feedKind(url) === 'google' && /\/public\/basic\.ics$/i.test(new URL(url).pathname);

/** A short, non-secret description of a link to show in lists. */
export function icalUrlHint(url) {
  const u = new URL(url);
  const kind = feedKind(url);
  if (kind !== 'google') return `${u.hostname} (${LMS_NAMES[kind]})`;
  const id = decodeURIComponent(u.pathname.split('/')[3] || '');
  if (isPublicFeed(url)) return id;
  return id.length > 32 ? `${id.slice(0, 14)}…${id.slice(-14)}` : id;
}

const MAX_BYTES = 10 * 1024 * 1024;

/** Downloads an iCal feed (following Google's redirects on allowed hosts only). */
export async function fetchIcal(url) {
  let current = url;
  const kind = feedKind(url);
  for (let hop = 0; hop < 4; hop++) {
    if (feedKind(current) !== 'google' || kind !== 'google') await assertPublicHost(new URL(current).hostname);
    const res = await fetch(current, {
      redirect: 'manual',
      headers: { Accept: 'text/calendar, */*' },
      signal: AbortSignal.timeout(20000),
    });
    if (res.status >= 300 && res.status < 400 && res.headers.get('location')) {
      const next = new URL(res.headers.get('location'), current);
      const googleOk = ALLOWED_HOSTS.has(next.hostname) || next.hostname.endsWith('.googleusercontent.com');
      // Blackboard may redirect within the school's site (e.g. to its CDN); the
      // public-address check runs again before following it.
      const blackboardOk = kind !== 'google' && !next.port && !next.username;
      if (next.protocol !== 'https:' || !(googleOk || blackboardOk)) {
        throw new Error('The calendar link redirected somewhere unexpected');
      }
      current = next.toString();
      continue;
    }
    if (kind !== 'google' && [401, 403, 404].includes(res.status)) {
      throw new Error(`${LMS_NAMES[kind]} didn’t share this calendar. The link may have been reset; copy a new one from ${LMS_NAMES[kind]}’s calendar.`);
    }
    if (res.status === 404 || res.status === 401 || res.status === 403) {
      throw new Error(isPublicFeed(url)
        ? 'This calendar isn’t public yet. In Google Calendar, open the calendar’s Settings and sharing, turn on “Make available to public” with “See all event details”, then try again. (Your school may not allow it.)'
        : 'Google didn’t share this calendar. The link may have been reset, or your school may block it.');
    }
    if (!res.ok) throw new Error(`Google Calendar returned an error (${res.status})`);
    const text = await res.text();
    if (text.length > MAX_BYTES) throw new Error('This calendar is too large to import');
    if (!/BEGIN:VCALENDAR/.test(text)) {
      throw new Error(isPublicFeed(url)
        ? 'This calendar isn’t public yet. Turn on “Make available to public” in its Settings and sharing, then try again.'
        : 'That link didn’t return a calendar. Copy the calendar’s “Public URL to this calendar”.');
    }
    return text;
  }
  throw new Error('Too many redirects');
}

function unescapeText(v) {
  return v.replace(/\\n/gi, '\n').replace(/\\([,;\\])/g, '$1');
}

/** Parses a property line "NAME;PARAM=x:VALUE" (value may contain ':'). */
function parseLine(line) {
  let i = 0;
  let inQuotes = false;
  for (; i < line.length; i++) {
    const c = line[i];
    if (c === '"') inQuotes = !inQuotes;
    else if (c === ':' && !inQuotes) break;
  }
  const head = line.slice(0, i);
  const value = line.slice(i + 1);
  const [name, ...paramParts] = head.split(';');
  const params = {};
  for (const p of paramParts) {
    const eq = p.indexOf('=');
    if (eq > 0) params[p.slice(0, eq).toUpperCase()] = p.slice(eq + 1).replace(/^"|"$/g, '');
  }
  return { name: name.toUpperCase(), params, value };
}

/**
 * Converts a DTSTART-style value to { date: Date, allDay: boolean }.
 * - 20261118T150000Z            UTC
 * - 20261118T090000 + TZID      local time in that zone
 * - 20261118T090000 (floating)  local time in `fallbackTz`
 * - 20261118 / VALUE=DATE       all-day
 */
export function parseIcalDate(value, params = {}, fallbackTz = 'UTC') {
  const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?$/.exec(String(value).trim());
  if (!m) return null;
  const [, y, mo, d, hh, mi, , z] = m;
  const year = Number(y), month = Number(mo), day = Number(d);
  if (hh === undefined || params.VALUE === 'DATE') {
    return { allDay: true, year, month, day };
  }
  if (z) return { allDay: false, date: new Date(Date.UTC(year, month - 1, day, Number(hh), Number(mi))) };
  let tz = params.TZID || fallbackTz;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
  } catch {
    tz = fallbackTz;
  }
  return { allDay: false, date: zonedTimeToUtc(year, month, day, Number(hh), Number(mi), tz) };
}

/**
 * Parses an iCal file into { calendarName, events: [{ uid, summary,
 * description, url, start, recurrenceId, cancelled, recurring }] }.
 * `start` is as returned by parseIcalDate.
 */
export function parseIcal(text, fallbackTz = 'UTC') {
  // Unfold lines (a line starting with a space or tab continues the previous one).
  const lines = String(text).replace(/\r\n/g, '\n').replace(/\n[ \t]/g, '').split('\n');
  let calendarName = null;
  const events = [];
  let ev = null;
  let depth = 0; // nested components inside a VEVENT (e.g. VALARM) are skipped
  for (const raw of lines) {
    if (!raw) continue;
    const { name, params, value } = parseLine(raw);
    if (name === 'BEGIN') {
      if (value === 'VEVENT' && !ev) ev = {};
      else if (ev) depth++;
      continue;
    }
    if (name === 'END') {
      if (ev && depth > 0) depth--;
      else if (ev && value === 'VEVENT') {
        if (ev.start) events.push(ev);
        ev = null;
      }
      continue;
    }
    if (!ev) {
      if (name === 'X-WR-CALNAME') calendarName = unescapeText(value).trim() || null;
      continue;
    }
    if (depth > 0) continue;
    switch (name) {
      case 'UID': ev.uid = value.trim(); break;
      case 'SUMMARY': ev.summary = unescapeText(value).trim(); break;
      case 'DESCRIPTION': ev.description = unescapeText(value).trim(); break;
      case 'URL': ev.url = value.trim(); break;
      case 'CATEGORIES': ev.category = unescapeText(value.split(',')[0] || '').trim() || undefined; break;
      case 'LOCATION': ev.location = unescapeText(value).trim() || undefined; break;
      case 'STATUS': ev.cancelled = value.trim().toUpperCase() === 'CANCELLED'; break;
      case 'RRULE': ev.recurring = true; break;
      case 'RECURRENCE-ID': ev.recurrenceId = value.trim(); break;
      case 'DTSTART': ev.start = parseIcalDate(value, params, fallbackTz); break;
      default: break;
    }
  }
  return { calendarName, events };
}

/** The moment an event is due: its start time, or 11:59 PM for all-day events. */
export function eventDue(start, tz) {
  if (!start) return null;
  if (!start.allDay) return start.date;
  return zonedTimeToUtc(start.year, start.month, start.day, 23, 59, tz);
}
