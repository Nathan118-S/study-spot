import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import {
  AlertDialog,
  AlertDialogTrigger,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from '@/components/ui/alert-dialog';
import SheetSelect from '@/components/SheetSelect';
import MergeDialog from '@/components/admin/MergeDialog';
import { Loader2, KeyRound, Trash2, Flame, ShieldCheck, RefreshCw, ShieldOff, BadgeCheck, GitMerge } from 'lucide-react';

export default function Admin() {
  const { user, isLoadingAuth } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);
  const [mergeSource, setMergeSource] = useState(null);
  const { toast } = useToast();

  const load = async () => {
    setLoading(true);
    try {
      const res = await base44.functions.invoke('adminListUsers', {});
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
      await base44.functions.invoke('adminUpdateUserRole', { userId: u.id, role });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, role } : x)));
      toast({ title: 'Role updated', description: `${u.email} is now ${role}` });
    } catch (e) {
      toast({ title: 'Update failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const resetPassword = async (u) => {
    setBusy(u.id);
    try {
      await base44.auth.resetPasswordRequest(u.email);
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
      const res = await base44.functions.invoke('adminRestoreStreak', { userId: u.id });
      toast({
        title: 'Streak restored',
        description: `${res.data.restored} overdue assignment(s) marked complete.`,
      });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const reset2fa = async (u) => {
    setBusy(u.id);
    try {
      await base44.functions.invoke('adminReset2fa', { userId: u.id });
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
      await base44.functions.invoke('adminVerifyUser', { userId: u.id });
      setUsers((prev) => prev.map((x) => (x.id === u.id ? { ...x, is_verified: true } : x)));
      toast({ title: 'User verified', description: `${u.email} can now sign in.` });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const deleteUser = async (u) => {
    setBusy(u.id);
    try {
      await base44.functions.invoke('adminDeleteUser', { userId: u.id });
      setUsers((prev) => prev.filter((x) => x.id !== u.id));
      toast({ title: 'User deleted', description: u.email });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  const mergeAccount = async (sourceId, targetId) => {
    setBusy(sourceId);
    try {
      const res = await base44.functions.invoke('adminMergeAccounts', { sourceId, targetId });
      setUsers((prev) => prev.filter((x) => x.id !== sourceId));
      toast({
        title: 'Accounts merged',
        description: `Moved ${res.data.movedAssignments} assignment(s) and ${res.data.movedClasses} class(es).`,
      });
    } catch (e) {
      toast({ title: 'Merge failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
            <ShieldCheck className="h-7 w-7 text-primary" /> Admin
          </h1>
          <p className="text-muted-foreground text-sm">Manage users, roles, passwords, streaks, and accounts.</p>
        </div>
        <Button variant="outline" size="icon" onClick={load} title="Refresh" disabled={loading}>
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
        </Button>
      </div>

      <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-200">
        Email addresses cannot be changed on a Base44 account. Use <span className="font-medium">Reset password</span> to send a password-reset link instead.
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : users.length === 0 ? (
        <p className="text-muted-foreground">No users found.</p>
      ) : (
        <div className="space-y-3">
          {users.map((u) => (
            <div key={u.id} className="rounded-lg border bg-card p-4 space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-medium truncate">{u.full_name || u.email}</p>
                  <p className="text-sm text-muted-foreground truncate">{u.email}</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    Joined {u.created_date ? new Date(u.created_date).toLocaleDateString() : '—'}
                  </p>
                </div>
                <Badge variant={u.role === 'admin' ? 'default' : 'secondary'} className="capitalize">{u.role}</Badge>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <SheetSelect
                  value={u.role}
                  onValueChange={(r) => setRole(u, r)}
                  triggerClassName="w-[130px]"
                  options={[
                    { value: 'user', label: 'User' },
                    { value: 'admin', label: 'Admin' },
                  ]}
                />
                <Button size="sm" variant="outline" onClick={() => resetPassword(u)} disabled={busy === u.id} className="h-11 md:h-9">
                  <KeyRound className="h-4 w-4 mr-1" /> Reset password
                </Button>
                <Button size="sm" variant="outline" onClick={() => restoreStreak(u)} disabled={busy === u.id} className="h-11 md:h-9">
                  <Flame className="h-4 w-4 mr-1" /> Restore streak
                </Button>
                {!u.is_verified && (
                  <Button size="sm" variant="outline" onClick={() => verifyUser(u)} disabled={busy === u.id} className="h-11 md:h-9">
                    <BadgeCheck className="h-4 w-4 mr-1" /> Verify user
                  </Button>
                )}
                <Button size="sm" variant="outline" onClick={() => setMergeSource(u)} disabled={busy === u.id || u.id === user.id} className="h-11 md:h-9">
                  <GitMerge className="h-4 w-4 mr-1" /> Merge
                </Button>
                {u.twofa_enabled && (
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button size="sm" variant="outline" disabled={busy === u.id} className="h-11 md:h-9">
                        <ShieldOff className="h-4 w-4 mr-1" /> Reset 2FA
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Reset 2FA for {u.email}?</AlertDialogTitle>
                        <AlertDialogDescription>
                          This clears their authenticator setup so they can sign in without a code. They can re-enable 2FA from Settings.
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel disabled={busy === u.id}>Cancel</AlertDialogCancel>
                        <AlertDialogAction onClick={() => reset2fa(u)} disabled={busy === u.id}>Reset</AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                )}
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button size="sm" variant="destructive" disabled={busy === u.id || u.id === user.id} className="h-11 md:h-9">
                      <Trash2 className="h-4 w-4 mr-1" /> Delete
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>Delete {u.email}?</AlertDialogTitle>
                      <AlertDialogDescription>
                        This permanently removes the user account. This action cannot be undone.
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel disabled={busy === u.id}>Cancel</AlertDialogCancel>
                      <AlertDialogAction
                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        onClick={() => deleteUser(u)}
                        disabled={busy === u.id}
                      >
                        Delete
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </div>
            </div>
          ))}
        </div>
      )}

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

function cn(...args) {
  return args.filter(Boolean).join(' ');
}