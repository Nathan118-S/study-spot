import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import MergeDialog from '@/components/admin/MergeDialog';
import UserManageDialog from '@/components/admin/UserManageDialog';
import { Loader2, RefreshCw, ChevronRight, ArrowLeft, CheckCircle2, Circle } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminPeople() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [mergeSource, setMergeSource] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const { toast } = useToast();

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.functions.invoke('adminListUsers', {});
      setUsers(res.data.users);
    } catch (e) {
      toast({ title: 'Failed to load users', description: e.message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user?.role === 'admin') load();
  }, [user]);

  if (isLoadingAuth) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }
  if (!user || user.role !== 'admin') return <Navigate to="/" replace />;

  const setRole = async (u, role) => {
    if (role === u.role) return;
    setBusy(u.id);
    try {
      await api.functions.invoke('adminUpdateUserRole', { userId: u.id, role });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, role } : x)));
      toast({ title: 'Role updated', description: `${u.email} is now ${role}` });
    } catch (e) {
      toast({ title: 'Update failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const disableUser = async (u, reason) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminUpdateUserRole', { userId: u.id, role: 'disabled', reason });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, role: 'disabled', disabled_reason: reason || null } : x)));
      toast({ title: 'Account disabled', description: `${u.email} can no longer log in.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const enableUser = async (u) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminUpdateUserRole', { userId: u.id, role: 'user' });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, role: 'user', disabled_reason: null } : x)));
      toast({ title: 'Account re-enabled', description: `${u.email} can log in again.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const resetPassword = async (u) => {
    setBusy(u.id);
    try {
      await api.auth.resetPasswordRequest(u.email);
      toast({ title: 'Reset email sent', description: u.email });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const restoreStreak = async (u) => {
    setBusy(u.id);
    try {
      const res = await api.functions.invoke('adminRestoreStreak', { userId: u.id });
      toast({ title: 'Streak restored', description: `${res.data.restored} overdue assignment(s) marked complete.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const resetOnboarding = async (u) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminResetOnboarding', { userId: u.id });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, onboarding_completed: false } : x)));
      toast({ title: 'Onboarding reset', description: `${u.email} will be asked to set up again.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const reset2fa = async (u) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminReset2fa', { userId: u.id });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, twofa_enabled: false } : x)));
      toast({ title: '2FA reset', description: `${u.email} can sign in without a code.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const verifyUser = async (u) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminVerifyUser', { userId: u.id });
      toast({ title: 'Verification email sent', description: u.email });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const deleteUser = async (u) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminDeleteUser', { userId: u.id });
      setUsers((prev) => prev.filter((x) => x.id !== u.id));
      toast({ title: 'User deleted', description: u.email });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const sendTestNotification = async (u, type) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminSendTestNotification', { userId: u.id, type });
      toast({
        title: type === 'push' ? 'Test push sent' : 'Test email sent',
        description: type === 'push' ? `Push notification sent to ${u.email}.` : `Email sent to ${u.email}.`,
      });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const mergeAccount = async (sourceId, targetId) => {
    setBusy(sourceId);
    try {
      const res = await api.functions.invoke('adminMergeAccounts', { sourceId, targetId });
      setUsers((prev) => prev.filter((x) => x.id !== sourceId));
      toast({ title: 'Accounts merged', description: `Moved ${res.data.movedAssignments} assignment(s) and ${res.data.movedClasses} class(es).` });
    } catch (e) {
      toast({ title: 'Merge failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const saveName = async (u, name) => {
    setBusy(u.id);
    try {
      await api.functions.invoke('adminUpdateUser', { userId: u.id, name });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, name: name.trim() } : x)));
      toast({ title: 'Name updated', description: u.email });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const selected = users.find((u) => u.id === selectedId) || null;

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate('/admin')} title="Back to Admin">
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="font-heading text-2xl md:text-3xl font-bold">People</h1>
            <p className="text-muted-foreground text-sm">Tap a user to manage roles, passwords, streaks, 2FA, and accounts.</p>
          </div>
        </div>
        <Button variant="outline" size="icon" onClick={load} title="Refresh" disabled={loading}>
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
        </Button>
      </div>

      <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-200">
        Email addresses cannot be changed. Use <span className="font-medium">Reset password</span> to send a password-reset link instead.
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : users.length === 0 ? (
        <p className="text-muted-foreground">No users found.</p>
      ) : (
        <div className="space-y-2">
          {users.map((u) => (
            <button
              key={u.id}
              onClick={() => setSelectedId(u.id)}
              className="w-full text-left rounded-lg border bg-card p-4 hover:bg-accent transition-colors flex items-center justify-between gap-3"
            >
              <div className="min-w-0">
                <p className="font-medium truncate">{u.name || u.full_name || u.email}</p>
                <p className="text-sm text-muted-foreground truncate">{u.email}</p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <span
                  className="flex items-center gap-1 text-xs text-muted-foreground"
                  title={u.onboarding_completed ? 'Onboarding complete' : 'Onboarding pending'}
                >
                  {u.onboarding_completed ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                  ) : (
                    <Circle className="h-4 w-4 text-amber-500" />
                  )}
                  <span className="hidden sm:inline">{u.onboarding_completed ? 'Onboarded' : 'Pending'}</span>
                </span>
                <Badge
                  variant={u.role === 'admin' ? 'default' : 'secondary'}
                  className={cn(
                    'capitalize',
                    u.role === 'demo' && 'border-transparent bg-emerald-500 text-white hover:bg-emerald-500/90',
                    u.role === 'disabled' && 'border-transparent bg-red-500 text-white hover:bg-red-500/90'
                  )}
                >
                  {u.role}
                </Badge>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </div>
            </button>
          ))}
        </div>
      )}

      <UserManageDialog
        open={!!selected}
        onOpenChange={(o) => !o && setSelectedId(null)}
        user={selected}
        currentUser={user}
        busy={busy}
        onSetRole={setRole}
        onResetPassword={resetPassword}
        onRestoreStreak={restoreStreak}
        onVerifyUser={verifyUser}
        onReset2fa={reset2fa}
        onResetOnboarding={resetOnboarding}
        onDeleteUser={deleteUser}
        onMerge={(u) => { setSelectedId(null); setMergeSource(u); }}
        onSendTestPush={(u) => sendTestNotification(u, 'push')}
        onSendTestEmail={(u) => sendTestNotification(u, 'email')}
        onSaveName={saveName}
        onDisable={disableUser}
        onEnable={enableUser}
      />

      <MergeDialog
        open={!!mergeSource}
        onOpenChange={(o) => !o && setMergeSource(null)}
        source={mergeSource}
        users={users}
        onMerge={(targetId) => {
          const s = mergeSource;
          setMergeSource(null);
          if (s) mergeAccount(s.id, targetId);
        }}
      />
    </div>
  );
}