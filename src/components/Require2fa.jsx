import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import { useAuth } from '@/lib/AuthContext';

export default function Require2fa() {
  const { user } = useAuth();
  const verified = sessionStorage.getItem('cf-2fa-verified') === '1';
  if (user?.twofa_enabled && !verified) {
    return <Navigate to="/verify-2fa" replace />;
  }
  return <Outlet />;
}