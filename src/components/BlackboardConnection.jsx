import React, { useState } from 'react';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { School, Link2, Unlink, Loader2 } from 'lucide-react';

export default function BlackboardConnection({ connected, instanceUrl, onChanged }) {
  const [url, setUrl] = useState(instanceUrl || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const connect = async () => {
    setError('');
    const trimmed = url.trim().replace(/\/$/, '');
    if (!trimmed) {
      setError('Enter your Blackboard Learn URL.');
      return;
    }
    if (!/^https:\/\//i.test(trimmed)) {
      setError('URL must start with https://');
      return;
    }
    setBusy(true);
    try {
      const res = await api.functions.invoke('blackboardAuthUrl', { instanceUrl: trimmed });
      window.location.href = res.data.authUrl;
    } catch (e) {
      setError(e.message || 'Failed to start connection');
      setBusy(false);
    }
  };

  const disconnect = async () => {
    setBusy(true);
    try {
      await api.functions.invoke('disconnectBlackboard', {});
      await onChanged?.();
    } catch (e) {
      setError(e.message || 'Failed to disconnect');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-3">
      <div className="flex items-start gap-3 min-w-0">
        <School className="h-5 w-5 text-primary shrink-0 mt-0.5" />
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">Blackboard Learn</p>
            {connected ? (
              <Badge className="bg-emerald-500 hover:bg-emerald-500">Connected</Badge>
            ) : (
              <Badge variant="secondary">Not connected</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Import courses and due-dated gradebook items from your school's Blackboard instance.
          </p>
        </div>
      </div>

      {connected ? (
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-muted-foreground truncate">{instanceUrl || 'Connected'}</p>
          <Button variant="outline" size="sm" onClick={disconnect} disabled={busy} className="shrink-0">
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Unlink className="h-4 w-4 mr-1" />}
            Disconnect
          </Button>
        </div>
      ) : (
        <div className="space-y-2">
          <div className="flex flex-col sm:flex-row gap-2">
            <Input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://your-school.blackboard.com"
              className="h-11 md:h-9"
            />
            <Button onClick={connect} disabled={busy} className="h-11 md:h-9 shrink-0">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Link2 className="h-4 w-4 mr-1" />}
              Connect
            </Button>
          </div>
          {error && <p className="text-xs text-destructive">{error}</p>}
        </div>
      )}
    </div>
  );
}