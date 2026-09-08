import React, { useState, useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2, ShieldCheck, LogOut, Fingerprint, Smartphone, Mail } from 'lucide-react';
import { b64uEncode, b64uDecode } from '@/lib/webauthn';
import { getMethods, METHOD_LABEL } from '@/lib/twofa';
import { cn } from '@/lib/utils';

const METHOD_ICON = { totp: Smartphone, email: Mail, passkey: Fingerprint };

export default function Verify2FA() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const methods = getMethods(user);
  const [selected, setSelected] = useState(() => {
    const pref = user?.twofa_method;
    return methods.includes(pref) ? pref : methods[0] || 'totp';
  });
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  const method = methods.length > 1 ? selected : methods[0];

  const finish = () => {
    sessionStorage.setItem('cf-2fa-verified', '1');
    navigate('/', { replace: true });
  };

  const sendEmail = async () => {
    setError('');
    setLoading(true);
    try {
      await api.functions.invoke('email2faSend', {});
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
      await api.functions.invoke('verify2fa', { code });
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
      await api.functions.invoke('email2faVerify', { code });
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
      const startRes = await api.functions.invoke('passkeyLoginStart', {});
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
      await api.functions.invoke('passkeyLoginFinish', {
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
    setCode('');
    setError('');
    setEmailSent(false);
    if (method === 'email' && !emailSent) sendEmail();
     
  }, [method]);

  if (user && methods.length === 0) return <Navigate to="/" replace />;

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
            {method === 'totp' && 'Enter the 6-digit code from your authenticator app to continue.'}
          </p>
        </div>

        {methods.length > 1 && (
          <div className="flex gap-2 justify-center">
            {methods.map((m) => {
              const Icon = METHOD_ICON[m];
              return (
                <button
                  key={m}
                  type="button"
                  onClick={() => setSelected(m)}
                  className={cn(
                    'flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition-colors',
                    method === m
                      ? 'border-primary bg-primary/10 text-primary'
                      : 'text-muted-foreground hover:bg-accent'
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {METHOD_LABEL[m]}
                </button>
              );
            })}
          </div>
        )}

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