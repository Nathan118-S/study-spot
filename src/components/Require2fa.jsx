import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '@/lib/AuthContext';

// The server marks a sign-in as pending until the second factor is verified
// (and refuses other API calls until then), so follow its flag.
export default function Require2fa() {
  const { user } = useAuth();
  if (user?.mfa_pending) {
    return <Navigate to="/verify-2fa" replace />;
  }
  return <Outlet />;
}
