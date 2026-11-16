// Native iOS app sign-in. The app opens /login?client=ios (or /register) in a
// private in-app browser session. The flag is kept in sessionStorage for that
// tab so it survives the Google redirect round-trip. Once any sign-in method
// produces a session token, it is handed to the app through its URL scheme
// instead of signing in on the web.
import { api } from '@/api/client';

const KEY = 'ss-ios-handoff';
const APP_CALLBACK = 'studyspot://oauth/callback';

export function captureIosHandoff() {
  try {
    if (new URLSearchParams(window.location.search).get('client') === 'ios') {
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

// Returns true if the token was handed to the iOS app (caller should stop).
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
