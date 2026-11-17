import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import MergeDialog from '@/components/admin/MergeDialog';
import UserManageDialog from '@/components/admin/UserManageDialog';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog';
import { Loader2, RefreshCw, ChevronRight, ArrowLeft, CheckCircle2, Circle, Copy, Check } from 'lucide-react';
import { cn } from '@/lib/utils';

const handle = (u) => (u?.username ? `@${u.username}` : 'This user');

export default function AdminPeople() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [mergeSource, setMergeSource] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [tempPassword, setTempPassword] = useState(null);
  const [copied, setCopied] = useState(false);
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
      toast({ title: 'Role updated', description: `${handle(u)} is now ${role}` });
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
      toast({ title: 'Account disabled', description: `${handle(u)} can no longer log in.` });
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
      toast({ title: 'Account re-enabled', description: `${handle(u)} can log in again.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const resetPassword = async (u) => {
    setBusy(u.id);
    try {
      const res = await api.functions.invoke('adminResetPassword', { userId: u.id });
      setCopied(false);
      setTempPassword({ user: u, password: res.data.password });
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
      toast({ title: 'Onboarding reset', description: `${handle(u)} will be asked to set up again.` });
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
      toast({ title: '2FA reset', description: `${handle(u)} can sign in without a code.` });
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
      toast({ title: 'User deleted', description: handle(u) });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const sendTestNotification = async (u) => {
    setBusy(u.id);
    try {
      const res = await api.functions.invoke('adminSendTestNotification', { userId: u.id });
      const devices = res.data?.devices || 0;
      toast({
        title: 'Test notification sent',
        description: res.data?.push_delivered
          ? `Delivered to ${handle(u)}'s notification bell and ${devices} iPhone${devices === 1 ? '' : 's'}.`
          : `Delivered to ${handle(u)}'s notification bell. No iPhone received a push.`,
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
      toast({ title: 'Name updated', description: handle(u) });
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
        People sign in with their username. If someone forgets their password, use <span className="font-medium">Reset password</span> to
        create a temporary one and give it to them.
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : users.length === 0 ? (
        <p className="text-muted-foreground">No users found.</p>
      ) : (
        <div className="ios-list">
          {users.map((u) => (
            <button
              key={u.id}
              onClick={() => setSelectedId(u.id)}
              className="w-full text-left px-4 py-3 hover:bg-accent/60 transition-colors flex items-center justify-between gap-3"
            >
              <div className="min-w-0">
                <p className="font-medium truncate">{u.name || u.full_name || u.username}</p>
                <p className="text-sm text-muted-foreground truncate">@{u.username}</p>
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
        onReset2fa={reset2fa}
        onResetOnboarding={resetOnboarding}
        onDeleteUser={deleteUser}
        onMerge={(u) => { setSelectedId(null); setMergeSource(u); }}
        onSendTestPush={sendTestNotification}
        onSaveName={saveName}
        onDisable={disableUser}
        onEnable={enableUser}
      />

      <Dialog open={!!tempPassword} onOpenChange={(o) => !o && setTempPassword(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Temporary password</DialogTitle>
            <DialogDescription>
              Give this to {handle(tempPassword?.user)}. They've been signed out everywhere and can change it in
              Settings → Account after logging in. It won't be shown again.
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center gap-2">
            <code className="flex-1 rounded-lg border bg-muted/50 px-3 py-2.5 font-mono text-base tracking-wide select-all break-all">
              {tempPassword?.password}
            </code>
            <Button
              variant="outline"
              size="icon"
              aria-label="Copy password"
              title="Copy password"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(tempPassword?.password || '');
                  setCopied(true);
                } catch {
                  toast({ title: 'Copy failed', description: 'Select the password and copy it manually.', variant: 'destructive' });
                }
              }}
            >
              {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
            </Button>
          </div>
          <DialogFooter>
            <Button onClick={() => setTempPassword(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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