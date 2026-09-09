import { Toaster } from "@/components/ui/toaster"
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClientInstance } from '@/lib/query-client'
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom';
import PageNotFound from './lib/PageNotFound';
import { AuthProvider, useAuth } from '@/lib/AuthContext';
import UserNotRegisteredError from '@/components/UserNotRegisteredError';
import ScrollToTop from './components/ScrollToTop';
import ProtectedRoute from '@/components/ProtectedRoute';
import Require2fa from '@/components/Require2fa';
import Layout from '@/components/Layout';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import ForgotPassword from '@/pages/ForgotPassword';
import ResetPassword from '@/pages/ResetPassword';
import OAuthCallback from '@/pages/OAuthCallback';
import { lazy, Suspense } from 'react';
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Classes = lazy(() => import('@/pages/Classes'));
const CalendarView = lazy(() => import('@/pages/CalendarView'));
const Analytics = lazy(() => import('@/pages/Analytics'));
const Insights = lazy(() => import('@/pages/Insights'));
const Settings = lazy(() => import('@/pages/Settings'));
const Admin = lazy(() => import('@/pages/Admin'));
const AdminPeople = lazy(() => import('@/pages/AdminPeople'));
const AdminSecrets = lazy(() => import('@/pages/AdminSecrets'));
const AdminDeploy = lazy(() => import('@/pages/AdminDeploy'));
const Verify2FA = lazy(() => import('@/pages/Verify2FA'));
const BlackboardCallback = lazy(() => import('@/pages/BlackboardCallback'));
import Landing from '@/pages/Landing';
import PrivacyPolicy from '@/pages/PrivacyPolicy';

function PageLoader() {
  return (
    <div className="flex items-center justify-center py-24">
      <div className="w-8 h-8 border-4 border-slate-200 border-t-slate-800 rounded-full animate-spin"></div>
    </div>
  );
}

const AuthenticatedApp = () => {
  const { isLoadingAuth, isLoadingPublicSettings, authError, navigateToLogin } = useAuth();

  // Show loading spinner while checking app public settings or auth
  if (isLoadingPublicSettings || isLoadingAuth) {
    return (
      <div className="fixed inset-0 flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-slate-200 border-t-slate-800 rounded-full animate-spin"></div>
      </div>
    );
  }

  // Handle authentication errors
  if (authError) {
    if (authError.type === 'user_not_registered') {
      return <UserNotRegisteredError />;
    } else if (authError.type === 'auth_required') {
      // Redirect to login automatically
      navigateToLogin();
      return null;
    }
  }

  // Render the main app
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/oauth/callback" element={<OAuthCallback />} />
      <Route path="/privacy" element={<PrivacyPolicy />} />
      <Route element={<ProtectedRoute unauthenticatedElement={<Landing />} />}>
        <Route path="/verify-2fa" element={<Suspense fallback={<PageLoader />}><Verify2FA /></Suspense>} />
        <Route path="/blackboard/callback" element={<Suspense fallback={<PageLoader />}><BlackboardCallback /></Suspense>} />
        <Route element={<Require2fa />}>
          <Route element={<Layout />}>
            <Route path="/" element={<Suspense fallback={<PageLoader />}><Dashboard /></Suspense>} />
            <Route path="/classes" element={<Suspense fallback={<PageLoader />}><Classes /></Suspense>} />
            <Route path="/calendar" element={<Suspense fallback={<PageLoader />}><CalendarView /></Suspense>} />
            <Route path="/analytics" element={<Suspense fallback={<PageLoader />}><Analytics /></Suspense>} />
            <Route path="/insights" element={<Suspense fallback={<PageLoader />}><Insights /></Suspense>} />
            <Route path="/settings" element={<Suspense fallback={<PageLoader />}><Settings /></Suspense>} />
            <Route path="/admin" element={<Suspense fallback={<PageLoader />}><Admin /></Suspense>} />
            <Route path="/admin/people" element={<Suspense fallback={<PageLoader />}><AdminPeople /></Suspense>} />
            <Route path="/admin/secrets" element={<Suspense fallback={<PageLoader />}><AdminSecrets /></Suspense>} />
            <Route path="/admin/deploy" element={<Suspense fallback={<PageLoader />}><AdminDeploy /></Suspense>} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<PageNotFound />} />
    </Routes>
  );
};


function App() {

  return (
    <AuthProvider>
      <QueryClientProvider client={queryClientInstance}>
        <Router>
          <ScrollToTop />
          <AuthenticatedApp />
        </Router>
        <Toaster />
      </QueryClientProvider>
    </AuthProvider>
  )
}

export default App