import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck, Flame, TestTube, Info } from 'lucide-react';
import { base44 } from '@/api/base44Client';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

function timeAgo(date) {
  const diff = Date.now() - new Date(date).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return `${d}d ago`;
}

const TYPE_ICON = { streak: Flame, test: TestTube, reminder: Bell, info: Info };
const TYPE_TINT = {
  streak: 'bg-orange-500/10 text-orange-500',
  test: 'bg-blue-500/10 text-blue-500',
  reminder: 'bg-primary/10 text-primary',
  info: 'bg-muted text-muted-foreground',
};

export default function NotificationCenter({ fullWidth = false }) {
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    try {
      const list = await base44.entities.Notification.list('-created_date', 30);
      setItems(list);
    } catch {}
  }, []);

  useEffect(() => {
    load();
    const unsub = base44.entities.Notification.subscribe(() => load());
    return unsub;
  }, [load]);

  const unread = items.filter((n) => !n.read).length;

  const markAllRead = async () => {
    const ids = items.filter((n) => !n.read).map((n) => n.id);
    if (!ids.length) return;
    setItems((prev) => prev.map((n) => ({ ...n, read: true })));
    try {
      await base44.entities.Notification.bulkUpdate(ids.map((id) => ({ id, read: true })));
    } catch {
      load();
    }
  };

  const openItem = async (n) => {
    if (!n.read) {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      try {
        await base44.entities.Notification.update(n.id, { read: true });
      } catch {
        load();
      }
    }
    setOpen(false);
    if (n.action_url) navigate(n.action_url);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size={fullWidth ? 'default' : 'icon'}
          className={cn('relative', fullWidth && 'w-full justify-start gap-3 px-3 py-2')}
          aria-label="Notifications"
        >
          <Bell className="h-5 w-5" />
          {fullWidth && <span>Notifications</span>}
          {unread > 0 && (
            <span
              className={cn(
                'rounded-full bg-primary text-primary-foreground text-[10px] font-bold flex items-center justify-center',
                fullWidth ? 'ml-auto min-w-5 h-5 px-1.5' : 'absolute top-1 right-1 min-w-4 h-4 px-1'
              )}
            >
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="end">
        <div className="flex items-center justify-between px-3 py-2 border-b">
          <p className="text-sm font-semibold">Notifications</p>
          {unread > 0 && (
            <Button variant="ghost" size="sm" onClick={markAllRead} className="h-7 text-xs">
              <CheckCheck className="h-3.5 w-3.5 mr-1" /> Mark all read
            </Button>
          )}
        </div>
        <div className="max-h-80 overflow-y-auto">
          {items.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-8">No notifications yet</p>
          ) : (
            items.map((n) => {
              const Icon = TYPE_ICON[n.type] || Info;
              return (
                <button
                  key={n.id}
                  onClick={() => openItem(n)}
                  className={cn(
                    'w-full text-left flex gap-3 px-3 py-3 border-b last:border-0 hover:bg-accent transition-colors',
                    !n.read && 'bg-primary/5'
                  )}
                >
                  <div className={cn('mt-0.5 h-8 w-8 rounded-full flex items-center justify-center shrink-0', TYPE_TINT[n.type] || TYPE_TINT.info)}>
                    <Icon className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium truncate">{n.title}</p>
                    {n.content && <p className="text-xs text-muted-foreground line-clamp-2">{n.content}</p>}
                    <p className="text-[11px] text-muted-foreground mt-1">{timeAgo(n.created_date)}</p>
                  </div>
                  {!n.read && <span className="mt-1.5 h-2 w-2 rounded-full bg-primary shrink-0" />}
                </button>
              );
            })
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}