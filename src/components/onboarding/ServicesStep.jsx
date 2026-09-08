import React, { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { api } from '@/api/client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Calendar, BookOpen, Loader2, Link2, Unlink } from 'lucide-react';
import BlackboardConnection from '@/components/BlackboardConnection';
import { isDemoUser } from '@/lib/demoData';

const CALENDAR_ID = 'google_calendar';
const CLASSROOM_ID = 'google_classroom';

const container = { hidden: {}, show: { transition: { staggerChildren: 0.1 } } };
const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.16, 1, 0.3, 1] } },
};

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
      const res = await api.functions.invoke('checkGoogleConnections', {});
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
      const url = await api.connectors.connectAppUser(id);
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
      await api.connectors.disconnectAppUser(id);
      await check();
    } finally {
      setBusy(null);
    }
  };

  return (
    <motion.div variants={container} initial="hidden" animate="show" className="space-y-3">
      <motion.p variants={item} className="text-sm text-muted-foreground">
        Connect your school accounts to import assignments automatically. Each user connects separately — your data stays private.
      </motion.p>
      <motion.div variants={item}>
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
      </motion.div>
      <motion.div variants={item}>
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
      </motion.div>
      <motion.div variants={item}>
        <BlackboardConnection
          connected={status.blackboard}
          instanceUrl={user?.data?.blackboard_instance_url}
          onChanged={check}
        />
      </motion.div>
    </motion.div>
  );
}

function ServiceRow({ icon, title, desc, loading, connected, busy, onConnect, onDisconnect }) {
  return (
    <motion.div whileHover={{ scale: 1.01 }} className="rounded-lg border bg-card p-4 flex flex-col gap-4">
      <div className="flex items-start gap-3 min-w-0">
        <div className="mt-0.5">{icon}</div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="font-medium">{title}</p>
            {loading ? (
              <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
            ) : connected ? (
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 400, damping: 15 }}
              >
                <Badge className="bg-emerald-500 hover:bg-emerald-500">Connected</Badge>
              </motion.span>
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
    </motion.div>
  );
}