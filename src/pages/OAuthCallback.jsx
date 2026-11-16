import React, { useEffect } from 'react';
import { api } from '@/api/client';
import { safePath, safeReturnTo } from '@/lib/authReturnTo';
import { completeIosHandoff } from '@/lib/iosHandoff';

// Landing point for the "Continue with Google" sign-in redirect. The backend
// issues a session token and sends the browser here with it (and the original
// returnTo) in the URL fragment, which never reaches the server — the token
// is picked up here and stored client-side.
export default function OAuthCallback() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const token = params.get('token');
    const returnTo = params.get('returnTo');
    if (completeIosHandoff(token)) return;
    if (token) api.auth.setToken(token);
    // returnTo round-trips through the server, so validate it like ?returnTo=.
    window.location.href = returnTo ? safePath(returnTo) : safeReturnTo();
  }, []);

  return (
    <div className="fixed inset-0 flex items-center justify-center">
      <div className="w-8 h-8 border-4 border-slate-200 border-t-slate-800 rounded-full animate-spin"></div>
    </div>
  );
}
