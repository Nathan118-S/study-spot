import React, { useState, useEffect } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import { ElevationStatus } from '@/components/admin/AdminElevation';
import { Loader2, RefreshCw, ChevronRight, ArrowLeft, CheckCircle2, Circle } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminPeople() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
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

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => navigate('/admin')} title="Back to Admin">
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="font-heading text-2xl md:text-3xl font-bold">People</h1>
            <p className="text-muted-foreground text-sm">Open an account to manage everything about it.</p>
          </div>
        </div>
        <Button variant="outline" size="icon" onClick={load} title="Refresh" disabled={loading}>
          <RefreshCw className={cn('h-4 w-4', loading && 'animate-spin')} />
        </Button>
      </div>

      <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-200 space-y-2">
        <p>
          Opening someone's account asks you to confirm with your own two-factor authentication. If someone forgets
          their password, open their account and give them a temporary one.
        </p>
        <ElevationStatus />
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
              onClick={() => navigate(`/admin/people/${u.id}`)}
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
    </div>
  );
}