// Self-hosted API client. Talks to the Express/Postgres backend in /server.
// Exposes a small, consistent surface (auth.*, entities.*, functions.invoke,
// connectors.*) that the rest of the app calls into.

import { b64uDecode, b64uEncode } from '@/lib/webauthn';

const API_BASE = import.meta.env.VITE_API_URL || '/api';
const TOKEN_KEY = 'ss_access_token';

function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function setToken(token) {
  // An admin elevation is tied to the session it was issued for.
  clearElevation();
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage may be unavailable (private browsing); the session just won't persist.
  }
}

// --- Admin step-up (2FA) ----------------------------------------------------
// Opening or changing someone else's account needs a recent second-factor
// confirmation. The resulting token lives in memory only (never storage), is
// sent as X-Admin-Elevation on function calls, and expires after 15 minutes.
let elevation = null; // { token, expiresAt (ms) }
const elevationListeners = new Set();
let elevationHandler = null; // () => Promise<void>; shows the 2FA prompt
let pendingElevation = null;
let elevationTimer = null;

// reason: 'set' | 'lock' (admin pressed Lock / signed out) | 'expired' | 'retry'
// (the server rejected it and a fresh prompt is about to show).
function notifyElevation(reason) {
  for (const fn of elevationListeners) fn(getElevation(), reason);
}

function getElevation() {
  if (elevation && elevation.expiresAt <= Date.now()) elevation = null;
  return elevation;
}

function setElevation(token, expiresAt) {
  clearTimeout(elevationTimer);
  elevation = token ? { token, expiresAt: new Date(expiresAt).getTime() } : null;
  if (elevation) {
    elevationTimer = setTimeout(() => clearElevation('expired'), Math.max(0, elevation.expiresAt - Date.now()));
  }
  notifyElevation('set');
}

function clearElevation(reason = 'lock') {
  clearTimeout(elevationTimer);
  if (!elevation) return;
  elevation = null;
  notifyElevation(reason);
}

// Runs the registered prompt once, even if several calls need it at once.
function requestElevation() {
  if (!elevationHandler) {
    return Promise.reject(Object.assign(new Error('Two-factor confirmation is required'), { elevationCancelled: true }));
  }
  if (!pendingElevation) {
    pendingElevation = elevationHandler().finally(() => {
      pendingElevation = null;
    });
  }
  return pendingElevation;
}

async function request(path, { method = 'GET', body, headers } = {}) {
  const token = getToken();
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  let data = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!res.ok) {
    const err = new Error((data && data.error) || `Request failed (${res.status})`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

// PostgreSQL NUMERIC columns arrive as strings ("45"); convert them so the UI
// can rely on typeof === 'number'. A null score stays null (= not graded).
const NUMERIC_FIELDS = ['progress', 'points', 'score', 'duration_minutes', 'best_streak'];

function normalizeRow(row) {
  if (!row || typeof row !== 'object') return row;
  const out = { ...row };
  for (const f of NUMERIC_FIELDS) {
    if (typeof out[f] === 'string' && out[f].trim() !== '' && Number.isFinite(Number(out[f]))) {
      out[f] = Number(out[f]);
    }
  }
  return out;
}

const normalize = (data) => (Array.isArray(data) ? data.map(normalizeRow) : normalizeRow(data));

function entity(name) {
  return {
    list: (sort, limit) => {
      const params = new URLSearchParams();
      if (sort) params.set('sort', sort);
      if (limit) params.set('limit', String(limit));
      const qs = params.toString();
      return request(`/entities/${name}${qs ? `?${qs}` : ''}`).then(normalize);
    },
    create: (data) => request(`/entities/${name}`, { method: 'POST', body: data }).then(normalize),
    update: (id, data) => request(`/entities/${name}/${id}`, { method: 'PATCH', body: data }).then(normalize),
    delete: (id) => request(`/entities/${name}/${id}`, { method: 'DELETE' }),

    // Applies [{ id, ...patch }] updates. The API has no bulk endpoint, so this
    // sends them in parallel and fails if any one fails.
    async bulkUpdate(updates) {
      return Promise.all(
        (updates || []).map(({ id, ...patch }) =>
          request(`/entities/${name}/${id}`, { method: 'PATCH', body: patch })
        )
      );
    },

    // Deletes every record of this entity the current user owns. Only an
    // empty filter ({}) is supported.
    async deleteMany(filter = {}) {
      if (filter && Object.keys(filter).length) throw new Error('deleteMany only supports an empty filter');
      const items = await request(`/entities/${name}?limit=5000`);
      await Promise.all(items.map((it) => request(`/entities/${name}/${it.id}`, { method: 'DELETE' })));
      return { deleted: items.length };
    },

    // Lightweight change feed: polls every 30s and whenever the tab regains
    // focus. Returns an unsubscribe function.
    subscribe(callback) {
      const tick = () => {
        if (document.visibilityState === 'visible') callback();
      };
      const timer = setInterval(tick, 30000);
      window.addEventListener('focus', tick);
      document.addEventListener('visibilitychange', tick);
      return () => {
        clearInterval(timer);
        window.removeEventListener('focus', tick);
        document.removeEventListener('visibilitychange', tick);
      };
    },
  };
}

const CONNECTOR_ROUTES = {
  google_calendar: 'google_calendar',
  google_classroom: 'google_classroom',
};

export const api = {
  auth: {
    me: () => request('/auth/me'),

    // Accounts are username-based (no email). Registration signs straight in.
    async register({ username, password }) {
      const result = await request('/auth/register', { method: 'POST', body: { username, password } });
      if (result?.access_token) setToken(result.access_token);
      return result;
    },

    setToken,

    hasToken: () => !!getToken(),

    async updateMe(patch) {
      return request('/auth/me', { method: 'PATCH', body: patch });
    },

    async loginWithPassword(username, password) {
      const result = await request('/auth/login', { method: 'POST', body: { username, password } });
      if (result?.access_token) setToken(result.access_token);
      return result;
    },

    // Passwordless sign-in. With a username, only that account's passkey is
    // offered; without one, the device shows its own list of passkeys.
    passkeySupported: () => typeof window !== 'undefined' && !!window.PublicKeyCredential,

    async loginWithPasskey(username) {
      const opts = await request('/auth/passkey/start', { method: 'POST', body: { username: username || undefined } });
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge: b64uDecode(opts.challenge),
          rpId: window.location.hostname,
          allowCredentials: (opts.allowCredentials || []).map((c) => ({ ...c, id: b64uDecode(c.id) })),
          userVerification: 'required',
          timeout: opts.timeout || 60000,
        },
      });
      const r = assertion.response;
      const result = await request('/auth/passkey/finish', {
        method: 'POST',
        body: {
          credentialId: b64uEncode(assertion.rawId),
          authenticatorData: b64uEncode(r.authenticatorData),
          clientDataJSON: b64uEncode(r.clientDataJSON),
          signature: b64uEncode(r.signature),
          userHandle: r.userHandle ? b64uEncode(r.userHandle) : null,
        },
      });
      if (result?.access_token) setToken(result.access_token);
      return result;
    },

    changePassword: ({ currentPassword, newPassword }) =>
      request('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),

    /** Permanently deletes the signed-in account and all of its data. */
    deleteAccount: (password) => request('/auth/me', { method: 'DELETE', body: { password } }),

    logout(redirectUrl) {
      setToken(null);
      if (redirectUrl) window.location.href = '/login';
    },

    redirectToLogin(returnUrl) {
      const path = '/login' + (returnUrl && returnUrl !== '/' ? `?returnTo=${encodeURIComponent(returnUrl)}` : '');
      window.location.href = path;
    },
  },

  entities: {
    Assignment: entity('Assignment'),
    Class: entity('Class'),
    AssignmentTemplate: entity('AssignmentTemplate'),
    StudySession: entity('StudySession'),
    Notification: entity('Notification'),
  },

  functions: {
    async invoke(name, payload) {
      const call = () => {
        const el = getElevation();
        return request(`/functions/${name}`, {
          method: 'POST',
          body: payload || {},
          headers: el ? { 'X-Admin-Elevation': el.token } : undefined,
        });
      };
      try {
        return { data: await call() };
      } catch (err) {
        if (!err?.data?.elevationRequired) throw err;
        // Missing or expired step-up: confirm 2FA, then retry once.
        clearElevation('retry');
        await requestElevation();
        return { data: await call() };
      }
    },
  },

  // Admin step-up state, for the 2FA prompt and the "Unlocked" indicator.
  elevation: {
    get: getElevation,
    set: setElevation,
    clear: () => clearElevation('lock'),
    subscribe(fn) {
      elevationListeners.add(fn);
      return () => elevationListeners.delete(fn);
    },
    // The app registers a function that shows the prompt and resolves once
    // elevated (or rejects with err.elevationCancelled).
    setHandler(fn) {
      elevationHandler = fn;
      return () => {
        if (elevationHandler === fn) elevationHandler = null;
      };
    },
  },

  connectors: {
    async connectAppUser(id) {
      const connector = CONNECTOR_ROUTES[id] || id;
      const result = await request(`/connectors/google/start?connector=${connector}`);
      return result.url;
    },
    async disconnectAppUser(id) {
      const connector = CONNECTOR_ROUTES[id] || id;
      return request(`/connectors/google/${connector}`, { method: 'DELETE' });
    },
  },
};
