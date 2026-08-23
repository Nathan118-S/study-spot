import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useToast } from '@/components/ui/use-toast';
import NotificationSender from '@/components/admin/NotificationSender';
import { Loader2, ShieldCheck, Users, Bell, ChevronRight } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';

export default function Admin() {
  const { user, isLoadingAuth } = useAuth();
  const navigate = useNavigate();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [notifOpen, setNotifOpen] = useState(false);
  const { toast } = useToast();

  const load = async () => {
    setLoading(true);
    try {
      const res = await base44.functions.invoke('adminListUsers', {});
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
      <div>
        <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
          <ShieldCheck className="h-7 w-7 text-primary" /> Admin
        </h1>
        <p className="text-muted-foreground text-sm">Manage users and send notifications.</p>
      </div>

      <button
        onClick={() => navigate('/admin/people')}
        className="w-full text-left rounded-lg border bg-card p-4 hover:bg-accent transition-colors flex items-center justify-between gap-3"
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-10 w-10 rounded-full bg-primary/10 text-primary flex items-center justify-center shrink-0">
            <Users className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <p className="font-medium">People</p>
            <p className="text-sm text-muted-foreground truncate">
              {loading ? 'Loading…' : `${users.length} user${users.length === 1 ? '' : 's'}`}
            </p>
          </div>
        </div>
        <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
      </button>

      <button
        onClick={() => setNotifOpen(true)}
        className="w-full text-left rounded-lg border bg-card p-4 hover:bg-accent transition-colors flex items-center justify-between gap-3"
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-10 w-10 rounded-full bg-primary/10 text-primary flex items-center justify-center shrink-0">
            <Bell className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <p className="font-medium">Send Notification</p>
            <p className="text-sm text-muted-foreground truncate">Send a custom in-app notification to a user or everyone.</p>
          </div>
        </div>
        <ChevronRight className="h-5 w-5 text-muted-foreground shrink-0" />
      </button>

      <Dialog open={notifOpen} onOpenChange={setNotifOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Send Notification</DialogTitle>
            <DialogDescription>Send a custom in-app notification to a single user or everyone at once.</DialogDescription>
          </DialogHeader>
          <NotificationSender users={users} />
        </DialogContent>
      </Dialog>
    </div>
  );
}