// Native app sign-in (iOS and Android). The app opens /login?client=ios or
// /login?client=android (or /register) in an in-app browser session. The flag
// is kept in sessionStorage for that tab so it survives moving between /login
// and /register. Once any sign-in method produces a session token, it is
// handed to the app through its URL scheme instead of signing in on the web.
import { api } from '@/api/client';

const KEY = 'ss-ios-handoff';
const APP_CALLBACK = 'studyspot://oauth/callback';
// Native clients that receive the session through the app's URL scheme.
const APP_CLIENTS = ['ios', 'android'];

export function captureIosHandoff() {
  try {
    if (APP_CLIENTS.includes(new URLSearchParams(window.location.search).get('client'))) {
      sessionStorage.setItem(KEY, '1');
    }
  } catch {
    // Storage unavailable: the web sign-in simply completes on the web.
  }
}

export function isIosHandoff() {
  try {
    return sessionStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}

// Returns true if the token was handed to the native app (caller should stop).
export function completeIosHandoff(token) {
  if (!token || !isIosHandoff()) return false;
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // ignore
  }
  // The app owns this session; don't also leave it signed in on the web.
  api.auth.setToken(null);
  window.location.replace(`${APP_CALLBACK}#token=${encodeURIComponent(token)}`);
  return true;
}
