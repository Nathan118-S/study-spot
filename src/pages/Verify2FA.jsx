import React, { useState, useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2, ShieldCheck, LogOut, Fingerprint } from 'lucide-react';
import { b64uEncode, b64uDecode } from '@/lib/webauthn';

export default function Verify2FA() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const method = user?.twofa_method || (user?.twofa_enabled ? 'totp' : '');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  const finish = () => {
    sessionStorage.setItem('cf-2fa-verified', '1');
    navigate('/', { replace: true });
  };

  const sendEmail = async () => {
    setError('');
    setLoading(true);
    try {
      await base44.functions.invoke('email2faSend', {});
      setEmailSent(true);
    } catch (e) {
      setError(e.response?.data?.error || e.message || 'Failed to send code');
    } finally {
      setLoading(false);
    }
  };

  const submitTotp = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await base44.functions.invoke('verify2fa', { code });
      finish();
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Invalid code');
    } finally {
      setLoading(false);
    }
  };

  const submitEmail = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await base44.functions.invoke('email2faVerify', { code });
      finish();
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Invalid code');
    } finally {
      setLoading(false);
    }
  };

  const submitPasskey = async () => {
    setError('');
    setLoading(true);
    try {
      const startRes = await base44.functions.invoke('passkeyLoginStart', {});
      const opts = startRes.data;
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge: b64uDecode(opts.challenge),
          rpId: window.location.hostname,
          allowCredentials: (opts.allowCredentials || []).map((c) => ({ ...c, id: b64uDecode(c.id) })),
          userVerification: 'preferred',
          timeout: 60000,
        },
      });
      await base44.functions.invoke('passkeyLoginFinish', {
        credentialId: b64uEncode(assertion.rawId),
        authenticatorData: b64uEncode(assertion.response.authenticatorData),
        clientDataJSON: b64uEncode(assertion.response.clientDataJSON),
        signature: b64uEncode(assertion.response.signature),
      });
      finish();
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Passkey verification failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (method === 'email' && !emailSent) sendEmail();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [method]);

  if (user && !user?.twofa_enabled) return <Navigate to="/" replace />;

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-background">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center space-y-3">
          <div className="mx-auto h-14 w-14 rounded-full bg-primary/10 flex items-center justify-center">
            <ShieldCheck className="h-7 w-7 text-primary" />
          </div>
          <h1 className="font-heading text-2xl font-bold">Two-factor authentication</h1>
          <p className="text-sm text-muted-foreground">
            {method === 'email' && 'Enter the 6-digit code sent to your email to continue.'}
            {method === 'passkey' && 'Use your passkey to continue.'}
            {(!method || method === 'totp') && 'Enter the 6-digit code from your authenticator app to continue.'}
          </p>
        </div>

        {method === 'passkey' ? (
          <div className="space-y-4">
            {error && <p className="text-sm text-destructive text-center">{error}</p>}
            <Button className="w-full h-12" onClick={submitPasskey} disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Fingerprint className="h-4 w-4 mr-2" />}
              Verify with passkey
            </Button>
          </div>
        ) : (
          <form onSubmit={method === 'email' ? submitEmail : submitTotp} className="space-y-4">
            <Input
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              placeholder="123456"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              className="text-center text-2xl tracking-[0.4em] h-14 font-mono"
              autoFocus
              required
            />
            {error && <p className="text-sm text-destructive text-center">{error}</p>}
            <Button type="submit" className="w-full h-12" disabled={loading || code.length !== 6}>
              {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : null}
              Verify
            </Button>
            {method === 'email' && (
              <Button type="button" variant="ghost" className="w-full" onClick={sendEmail} disabled={loading}>
                Resend code
              </Button>
            )}
          </form>
        )}

        <Button variant="ghost" className="w-full" onClick={() => logout()}>
          <LogOut className="h-4 w-4 mr-2" /> Sign out
        </Button>
      </div>
    </div>
  );
}