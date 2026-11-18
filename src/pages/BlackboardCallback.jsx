import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Loader2, CheckCircle2, XCircle, Mail } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/lib/AuthContext';
import { schoolItEmailUrl } from '@/lib/schoolItEmail';
import BlackboardLinkGuide from '@/components/BlackboardLinkGuide';

// Blackboard says this when the school hasn't added Study Spot under REST API Integrations.
const NOT_APPROVED = /not registered|unregistered|invalid application|application.*not.*(found|authori)/i;
export const BLACKBOARD_NOT_APPROVED = 'Your school hasn’t approved Study Spot in Blackboard yet. A Blackboard admin at your school needs to add Study Spot under REST API Integrations. Ask your school’s IT team, or add your assignments yourself until then.';

export default function BlackboardCallback() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');
  const { user } = useAuth();
  const notApproved = error === BLACKBOARD_NOT_APPROVED;
  const [guideOpen, setGuideOpen] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    if (!code) {
      setStatus('error');
      const denied = params.get('error');
      const description = params.get('error_description') || '';
      setError(
        NOT_APPROVED.test(description) ? BLACKBOARD_NOT_APPROVED
          : description
          || (denied === 'access_denied' ? 'You cancelled the Blackboard sign-in.' : denied ? `Blackboard returned an error: ${denied}` : 'No authorization code was returned.')
      );
      return;
    }
    (async () => {
      try {
        await api.functions.invoke('blackboardCallback', { code, state });
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
              <p className="font-medium">{notApproved ? 'Your school needs to approve Study Spot' : 'Connection failed'}</p>
              <p className={`text-sm ${notApproved ? 'text-muted-foreground' : 'text-destructive'}`}>{error}</p>
            </div>
            {notApproved && (
              <div className="flex flex-col gap-2">
                <Button className="rounded-full" onClick={() => { window.location.href = schoolItEmailUrl(user, 'blackboard'); }}>
                  <Mail className="h-4 w-4 mr-1.5" /> Email my school’s IT team
                </Button>
                <Button variant="outline" className="rounded-full" onClick={() => setGuideOpen(true)}>
                  Use a calendar link instead
                </Button>
                <Button asChild variant="ghost" className="rounded-full">
                  <Link to="/schools">Instructions for your school</Link>
                </Button>
                <BlackboardLinkGuide open={guideOpen} onOpenChange={setGuideOpen} />
              </div>
            )}
            <Button variant="outline" onClick={() => navigate('/settings')}>
              Back to Settings
            </Button>
          </>
        )}
      </div>
    </div>
  );
}