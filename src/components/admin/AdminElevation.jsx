import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '@/components/ui/dialog';
import { Loader2, ShieldCheck, Fingerprint, Lock, LockOpen } from 'lucide-react';
import { b64uDecode, b64uEncode } from '@/lib/webauthn';

const cancelled = () => Object.assign(new Error('Two-factor confirmation cancelled'), { elevationCancelled: true });

// Shows the "confirm it's you" prompt whenever an admin call needs a step-up.
// Mounted once for the whole app; the API client calls into it and retries.
export function AdminElevationProvider() {
  const [open, setOpen] = useState(false);
  const [info, setInfo] = useState(null); // adminElevateStart result
  const [startError, setStartError] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef(null); // { resolve, reject }

  const begin = useCallback(async () => {
    setInfo(null);
    setStartError('');
    setCode('');
    setError('');
    try {
      const res = await api.functions.invoke('adminElevateStart', {});
      setInfo(res.data);
    } catch (e) {
      setStartError(e.message || 'Could not start two-factor confirmation');
    }
  }, []);

  useEffect(
    () =>
      api.elevation.setHandler(
        () =>
          new Promise((resolve, reject) => {
            pending.current = { resolve, reject };
            setOpen(true);
            begin();
          })
      ),
    [begin]
  );

  const finish = (data) => {
    api.elevation.set(data.elevation, data.expires_at);
    const p = pending.current;
    pending.current = null;
    setOpen(false);
    p?.resolve();
  };

  const close = () => {
    const p = pending.current;
    pending.current = null;
    setOpen(false);
    p?.reject(cancelled());
  };

  const submitCode = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      finish((await api.functions.invoke('adminElevate', { code })).data);
    } catch (err) {
      setError(err.message || 'Invalid code');
      setCode('');
    } finally {
      setBusy(false);
    }
  };

  const confirmWithPasskey = async () => {
    setError('');
    setBusy(true);
    try {
      let opts = info?.passkey;
      if (!opts) {
        // A challenge is single-use; fetch a fresh one for a retry.
        const res = await api.functions.invoke('adminElevateStart', {});
        setInfo(res.data);
        opts = res.data.passkey;
      }
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge: b64uDecode(opts.challenge),
          rpId: window.location.hostname,
          allowCredentials: (opts.allowCredentials || []).map((c) => ({ ...c, id: b64uDecode(c.id) })),
          userVerification: 'required',
          timeout: 60000,
        },
      });
      const r = assertion.response;
      const res = await api.functions.invoke('adminElevate', {
        credentialId: b64uEncode(assertion.rawId),
        authenticatorData: b64uEncode(r.authenticatorData),
        clientDataJSON: b64uEncode(r.clientDataJSON),
        signature: b64uEncode(r.signature),
        userHandle: r.userHandle ? b64uEncode(r.userHandle) : undefined,
      });
      finish(res.data);
    } catch (err) {
      setInfo((i) => (i ? { ...i, passkey: null } : i));
      if (err?.name !== 'NotAllowedError') setError(err.message || 'Passkey verification failed');
    } finally {
      setBusy(false);
    }
  };

  const methods = info?.methods || [];
  const hasTotp = methods.includes('totp');
  const hasPasskey = methods.includes('passkey');

  return (
    <Dialog open={open} onOpenChange={(o) => !o && close()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-primary" /> Confirm it's you
          </DialogTitle>
          <DialogDescription>
            Managing someone else's account needs two-factor authentication.
            {info?.minutes ? ` You'll stay unlocked for ${info.minutes} minutes.` : ''}
          </DialogDescription>
        </DialogHeader>

        {startError ? (
          <div className="space-y-3">
            <p className="text-sm text-destructive">{startError}</p>
            <Button asChild variant="outline" className="w-full" onClick={close}>
              <Link to="/settings">Open Settings</Link>
            </Button>
          </div>
        ) : !info ? (
          <div className="flex justify-center py-6"><Loader2 className="h-5 w-5 animate-spin text-muted-foreground" /></div>
        ) : (
          <div className="space-y-4">
            {hasTotp && (
              <form onSubmit={submitCode} className="space-y-3">
                <Input
                  inputMode="numeric"
                  pattern="[0-9]*"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="123456"
                  aria-label="Authenticator code"
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  className="text-center text-2xl tracking-[0.4em] h-14 font-mono"
                  autoFocus
                />
                <Button type="submit" className="w-full h-11" disabled={busy || code.length !== 6}>
                  {busy && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  Unlock
                </Button>
              </form>
            )}
            {hasPasskey && (
              <Button variant={hasTotp ? 'outline' : 'default'} className="w-full h-11" onClick={confirmWithPasskey} disabled={busy}>
                {busy && !hasTotp ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Fingerprint className="h-4 w-4 mr-2" />}
                Use passkey
              </Button>
            )}
            {error && <p className="text-sm text-destructive text-center">{error}</p>}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

function useElevation() {
  const [el, setEl] = useState(() => api.elevation.get());
  const [, tick] = useState(0);
  useEffect(() => api.elevation.subscribe(setEl), []);
  useEffect(() => {
    if (!el) return undefined;
    const t = setInterval(() => {
      tick((n) => n + 1);
      if (!api.elevation.get()) setEl(null);
    }, 15000);
    return () => clearInterval(t);
  }, [el]);
  return el && el.expiresAt > Date.now() ? el : null;
}

/** "Unlocked for N min" + Lock, shown in the admin area while elevated. */
export function ElevationStatus({ className = '' }) {
  const el = useElevation();
  if (!el) {
    return (
      <span className={`inline-flex items-center gap-1.5 text-[13px] text-muted-foreground ${className}`}>
        <Lock className="h-3.5 w-3.5" /> Locked
      </span>
    );
  }
  const mins = Math.max(1, Math.ceil((el.expiresAt - Date.now()) / 60000));
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <span className="inline-flex items-center gap-1.5 rounded-full bg-success/15 px-2.5 py-1 text-[13px] font-medium text-success">
        <LockOpen className="h-3.5 w-3.5" /> Unlocked for {mins} min
      </span>
      <Button variant="ghost" size="sm" className="h-7 px-2" onClick={() => api.elevation.clear()}>
        <Lock className="h-3.5 w-3.5 mr-1" /> Lock
      </Button>
    </span>
  );
}
