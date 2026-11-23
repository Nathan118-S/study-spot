import React, { createContext, useState, useContext, useEffect } from 'react';
import { api } from '@/api/client';
import { syncColorThemeFromUser } from '@/lib/colorThemes';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoadingAuth, setIsLoadingAuth] = useState(true);
  const [authError, setAuthError] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);

  useEffect(() => {
    checkUserAuth();
  }, []);

  const checkUserAuth = async () => {
    try {
      setIsLoadingAuth(true);
      setAuthError(null);
      if (!api.auth.hasToken()) {
        // Not signed in: nothing to check.
        setUser(null);
        setIsAuthenticated(false);
        setIsLoadingAuth(false);
        setAuthChecked(true);
        return;
      }
      const currentUser = await api.auth.me();
      if (currentUser?.role === 'disabled') {
        sessionStorage.setItem('cf-disabled-reason', currentUser.disabled_reason || '');
        setIsLoadingAuth(false);
        setIsAuthenticated(false);
        setAuthChecked(true);
        setAuthError({ type: 'auth_required', message: 'Authentication required' });
        // Token is cleared first, so this lands on /login (which shows the reason) once.
        api.auth.logout('/login');
        return;
      }
      setUser(currentUser);
      setIsAuthenticated(true);
      syncColorThemeFromUser(currentUser);
      // Reminders and do-not-disturb are scheduled in the user's own time zone.
      try {
        const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
        if (tz && !currentUser.mfa_pending && currentUser.data?.timezone !== tz) {
          api.auth.updateMe({ timezone: tz }).catch(() => {});
        }
      } catch {
        // Time zone unavailable; the server falls back to its default.
      }
      setIsLoadingAuth(false);
      setAuthChecked(true);
    } catch (error) {
      setIsLoadingAuth(false);
      setIsAuthenticated(false);
      setAuthChecked(true);
      if (error.status === 401 || error.status === 403) {
        api.auth.setToken(null); // expired or revoked; start signed out
        setAuthError({ type: 'auth_required', message: 'Authentication required' });
      }
    }
  };

  const logout = (shouldRedirect = true) => {
    sessionStorage.removeItem('cf-2fa-verified');
    setUser(null);
    setIsAuthenticated(false);
    api.auth.logout(shouldRedirect ? window.location.href : undefined);
  };

  const navigateToLogin = () => {
    api.auth.redirectToLogin(window.location.href);
  };

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated,
      isLoadingAuth,
      isLoadingPublicSettings: false,
      authError,
      authChecked,
      logout,
      navigateToLogin,
      checkUserAuth,
      checkAppState: checkUserAuth,
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
