import React, { useState, useEffect, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Calendar, BookOpen, Loader2, Link2, Unlink } from 'lucide-react';
import BlackboardConnection from '@/components/BlackboardConnection';
import { isDemoUser } from '@/lib/demoData';

const CALENDAR_ID = '6a87a0a86ad979ee05f39b0c';
const CLASSROOM_ID = '6a87a2e5f3be615b69035dcd';

export default function ServicesStep() {
  const { user } = useAuth();
  const [status, setStatus] = useState({ calendar: false, classroom: false, blackboard: false });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(null);

  const check = useCallback(async () => {
    if (isDemoUser(user)) {
      setStatus({ calendar: true, classroom: true, blackboard: false });
      setLoading(false);
      return;
    }
    try {
      const res = await base44.functions.invoke('checkGoogleConnections', {});
      setStatus({
        calendar: !!res.data?.calendar,
        classroom: !!res.data?.classroom,
        blackboard: !!res.data?.blackboard,
      });
    } catch {
      setStatus({ calendar: false, classroom: false, blackboard: false });
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    check();
  }, [check]);

  const connect = async (id, label) => {
    if (isDemoUser(user)) return;
    setBusy(label);
    try {
      const url = await base44.connectors.connectAppUser(id);
      const popup = window.open(url, '_blank');
      const timer = setInterval(() => {
        if (!popup || popup.closed) {
          clearInterval(timer);
          check();
        }
      }, 500);
    } finally {
      setBusy(null);
    }
  };

  const disconnect = async (id, label) => {
    if (isDemoUser(user)) return;
    setBusy(label);
    try {
      await base44.connectors.disconnectAppUser(id);
      await check();
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">Connect your school accounts to import assignments automatically. Each user connects separately — your data stays private.</p>
      <ServiceRow
        icon={<Calendar className="h-5 w-5 text-blue-500" />}
        title="Google Calendar"
        desc="Import calendar events labeled as assignments."
        loading={loading}
        connected={status.calendar}
        busy={busy === 'calendar'}
        onConnect={() => connect(CALENDAR_ID, 'calendar')}
        onDisconnect={() => disconnect(CALENDAR_ID, 'calendar')}
      />
      <ServiceRow
        icon={<BookOpen className="h-5 w-5 text-emerald-500" />}
        title="Google Classroom"
        desc="Import courses and coursework with due dates."
        loading={loading}
        connected={status.classroom}
        busy={busy === 'classroom'}
        onConnect={() => connect(CLASSROOM_ID, 'classroom')}
        onDisconnect={() => disconnect(CLASSROOM_ID, 'classroom')}
      />
      <BlackboardConnection
        connected={status.blackboard}
        instanceUrl={user?.data?.blackboard_instance_url}
        onChanged={check}
      />
    </div>
  );
}

function ServiceRow({ icon, title, desc, loading, connected, busy, onConnect, onDisconnect }) {
  return (
    <div className="rounded-lg border bg-card p-4 flex flex-col gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5">{icon}</div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">{title}</p>
            {loading ? (
              <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
            ) : connected ? (
              <Badge className="bg-emerald-500 hover:bg-emerald-500">Connected</Badge>
            ) : (
              <Badge variant="secondary">Not connected</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">{desc}</p>
        </div>
      </div>
      <div className="shrink-0">
        {connected ? (
          <Button variant="outline" size="sm" onClick={onDisconnect} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Unlink className="h-4 w-4 mr-1" />}
            Disconnect
          </Button>
        ) : (
          <Button size="sm" onClick={onConnect} disabled={busy}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Link2 className="h-4 w-4 mr-1" />}
            Connect
          </Button>
        )}
      </div>
    </div>
  );
}