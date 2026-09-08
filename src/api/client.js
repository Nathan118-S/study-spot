// Self-hosted API client. Talks to the Express/Postgres backend in /server.
// Exposes a small, consistent surface (auth.*, entities.*, functions.invoke,
// connectors.*) that the rest of the app calls into.

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
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage may be unavailable (private browsing); the session just won't persist.
  }
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

function entity(name) {
  return {
    list: (sort, limit) => {
      const params = new URLSearchParams();
      if (sort) params.set('sort', sort);
      if (limit) params.set('limit', String(limit));
      const qs = params.toString();
      return request(`/entities/${name}${qs ? `?${qs}` : ''}`);
    },
    create: (data) => request(`/entities/${name}`, { method: 'POST', body: data }),
    update: (id, data) => request(`/entities/${name}/${id}`, { method: 'PATCH', body: data }),
    delete: (id) => request(`/entities/${name}/${id}`, { method: 'DELETE' }),
  };
}

const CONNECTOR_ROUTES = {
  google_calendar: 'google_calendar',
  google_classroom: 'google_classroom',
};

export const api = {
  auth: {
    me: () => request('/auth/me'),

    async register({ email, password }) {
      return request('/auth/register', { method: 'POST', body: { email, password } });
    },

    async verifyOtp({ email, otpCode }) {
      const result = await request('/auth/verify-otp', { method: 'POST', body: { email, otpCode } });
      if (result?.access_token) setToken(result.access_token);
      return result;
    },

    resendOtp: (email) => request('/auth/resend-otp', { method: 'POST', body: { email } }),

    setToken,

    async updateMe(patch) {
      return request('/auth/me', { method: 'PATCH', body: patch });
    },

    async loginViaEmailPassword(email, password) {
      const result = await request('/auth/login', { method: 'POST', body: { email, password } });
      if (result?.access_token) setToken(result.access_token);
      return result;
    },

    loginWithProvider(provider, returnTo) {
      if (provider !== 'google') throw new Error(`Unsupported provider: ${provider}`);
      const url = new URL(`${API_BASE}/auth/google/start`, window.location.origin);
      url.searchParams.set('returnTo', returnTo || '/');
      window.location.href = url.toString();
    },

    resetPasswordRequest: (email) => request('/auth/forgot-password', { method: 'POST', body: { email } }),

    resetPassword: ({ resetToken, newPassword }) =>
      request('/auth/reset-password', { method: 'POST', body: { resetToken, newPassword } }),

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
      const data = await request(`/functions/${name}`, { method: 'POST', body: payload || {} });
      return { data };
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
