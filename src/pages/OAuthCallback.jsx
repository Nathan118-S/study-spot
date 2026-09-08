import React, { useEffect } from 'react';
import { api } from '@/api/client';
import { safeReturnTo } from '@/lib/authReturnTo';

// Landing point for the "Continue with Google" sign-in redirect. The backend
// issues a session token and sends the browser here with it (and the original
// returnTo) in the URL fragment, which never reaches the server — the token
// is picked up here and stored client-side.
export default function OAuthCallback() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const token = params.get('token');
    const returnTo = params.get('returnTo');
    if (token) api.auth.setToken(token);
    window.location.href = returnTo ? decodeURIComponent(returnTo) : safeReturnTo();
  }, []);

  return (
    <div className="fixed inset-0 flex items-center justify-center">
      <div className="w-8 h-8 border-4 border-slate-200 border-t-slate-800 rounded-full animate-spin"></div>
    </div>
  );
}
