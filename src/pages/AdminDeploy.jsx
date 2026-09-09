import React, { useState, useEffect, useRef, useCallback } from 'react';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Loader2, ArrowLeft, GitBranch, RefreshCw } from 'lucide-react';

const STATUS_LABEL = {
  idle: 'Idle',
  running: 'Pulling & rebuilding…',
  restarting: 'Restarting…',
  failed: 'Failed',
};

export default function AdminDeploy() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const pollRef = useRef(null);
  const logRef = useRef(null);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await api.functions.invoke('adminDeployStatus', {});
      setStatus(res.data);
      return res.data;
    } catch (e) {
      toast({ title: 'Failed to load deploy status', description: e.message, variant: 'destructive' });
      return null;
    }
  }, [toast]);

  useEffect(() => {
    if (user?.role !== 'admin') return;
    (async () => {
      const s = await fetchStatus();
      setLoading(false);
      if (s && (s.status === 'running' || s.status === 'restarting')) startPolling();
    })();
    return stopPolling;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [status?.log]);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(async () => {
      const s = await fetchStatus();
      if (!s) return;
      if (s.status === 'restarting') {
        stopPolling();
        // The server process is about to exit for the supervisor to restart
        // it — give it a few seconds, then reload to pick the app back up.
        setTimeout(() => window.location.reload(), 6000);
      } else if (s.status === 'failed' || s.status === 'idle') {
        stopPolling();
      }
    }, 1500);
  };

  if (isLoadingAuth) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }
  if (!user || user.role !== 'admin') return <Navigate to="/" replace />;

  const deploy = async () => {
    setStarting(true);
    try {
      const res = await api.functions.invoke('adminDeployLatest', {});
      setStatus(res.data);
      startPolling();
    } catch (e) {
      toast({ title: 'Failed to start deploy', description: e.message, variant: 'destructive' });
    } finally {
      setStarting(false);
    }
  };

  const busy = status?.status === 'running' || status?.status === 'restarting';

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/admin')} title="Back to Admin">
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
            <GitBranch className="h-6 w-6 text-primary" /> Deploy
          </h1>
          <p className="text-muted-foreground text-sm">Pull the latest commit from GitHub, rebuild, and restart the app.</p>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : (
        <div className="space-y-4">
          <div className="rounded-lg border bg-card p-4 flex items-center justify-between gap-3">
            <div>
              <p className="font-medium">Status</p>
              <p className="text-sm text-muted-foreground">
                {status?.startedAt ? `Last started ${new Date(status.startedAt).toLocaleString()}` : 'Never run'}
              </p>
            </div>
            <Badge
              variant={status?.status === 'failed' ? 'destructive' : 'secondary'}
              className="capitalize"
            >
              {STATUS_LABEL[status?.status] || 'Idle'}
            </Badge>
          </div>

          <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-900 dark:text-amber-200">
            This resets the working tree to the latest commit on the current branch (<code>git reset --hard</code>),
            reinstalls dependencies, rebuilds the frontend, then exits the server process so it can be restarted on
            the new code. Requires the server to run under a supervisor that restarts it automatically (systemd,
            pm2, or a Docker restart policy) — see README.md.
          </div>

          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button className="w-full h-11" disabled={busy || starting} variant="default">
                {busy || starting ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <RefreshCw className="h-4 w-4 mr-2" />}
                Pull latest from GitHub & restart
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Pull latest and restart now?</AlertDialogTitle>
                <AlertDialogDescription>
                  This discards any uncommitted changes on the server, pulls the latest commit, rebuilds, and
                  restarts the app for everyone currently using it. This cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction onClick={deploy}>Deploy</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>

          {status?.log && (
            <div>
              <p className="text-sm font-medium mb-1.5">Log</p>
              <pre
                ref={logRef}
                className="rounded-lg border bg-muted/50 p-3 text-xs font-mono overflow-auto max-h-80 whitespace-pre-wrap"
              >
                {status.log}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
