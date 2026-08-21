import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Loader2, CheckCircle2, XCircle } from 'lucide-react';

export default function BlackboardCallback() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    if (!code) {
      setStatus('error');
      setError('No authorization code was returned.');
      return;
    }
    (async () => {
      try {
        await base44.functions.invoke('blackboardCallback', { code, state });
        setStatus('success');
        setTimeout(() => navigate('/settings'), 1500);
      } catch (e) {
        setStatus('error');
        setError(e.message || 'Authorization failed.');
      }
    })();
  }, [navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <div className="max-w-md w-full rounded-lg border bg-card p-8 text-center space-y-4">
        {status === 'loading' && (
          <>
            <Loader2 className="h-10 w-10 animate-spin text-primary mx-auto" />
            <div>
              <p className="font-medium">Connecting Blackboard…</p>
              <p className="text-sm text-muted-foreground">Finishing authorization.</p>
            </div>
          </>
        )}
        {status === 'success' && (
          <>
            <CheckCircle2 className="h-10 w-10 text-emerald-500 mx-auto" />
            <div>
              <p className="font-medium">Blackboard connected</p>
              <p className="text-sm text-muted-foreground">Redirecting to settings…</p>
            </div>
          </>
        )}
        {status === 'error' && (
          <>
            <XCircle className="h-10 w-10 text-destructive mx-auto" />
            <div>
              <p className="font-medium">Connection failed</p>
              <p className="text-sm text-destructive">{error}</p>
            </div>
            <Button variant="outline" onClick={() => navigate('/settings')}>
              Back to Settings
            </Button>
          </>
        )}
      </div>
    </div>
  );
}