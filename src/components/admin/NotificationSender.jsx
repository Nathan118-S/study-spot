import React, { useState } from 'react';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { useToast } from '@/components/ui/use-toast';
import SheetSelect from '@/components/SheetSelect';
import { Send, Loader2 } from 'lucide-react';

export default function NotificationSender({ users }) {
  const [recipient, setRecipient] = useState('all');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [type, setType] = useState('info');
  const [sending, setSending] = useState(false);
  const { toast } = useToast();

  const send = async () => {
    if (!title.trim()) {
      toast({ title: 'Title is required', variant: 'destructive' });
      return;
    }
    setSending(true);
    try {
      const res = await base44.functions.invoke('adminSendNotification', {
        userId: recipient,
        title,
        content,
        type,
      });
      toast({
        title: 'Notification sent',
        description:
          recipient === 'all'
            ? `Delivered to ${res.data.sent} user(s).`
            : 'Notification delivered.',
      });
      setTitle('');
      setContent('');
    } catch (e) {
      toast({ title: 'Failed to send', description: e.message, variant: 'destructive' });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-4">
      <div className="space-y-2">
        <Label className="text-xs text-muted-foreground">Recipient</Label>
        <SheetSelect
          value={recipient}
          onValueChange={setRecipient}
          triggerClassName="w-full"
          options={[
            { value: 'all', label: 'All users' },
            ...users.map((u) => ({
              value: u.id,
              label: u.name || u.full_name || u.email,
            })),
          ]}
        />
      </div>
      <div className="space-y-2">
        <Label className="text-xs text-muted-foreground">Type</Label>
        <SheetSelect
          value={type}
          onValueChange={setType}
          triggerClassName="w-full"
          options={[
            { value: 'info', label: 'Info' },
            { value: 'reminder', label: 'Reminder' },
            { value: 'streak', label: 'Streak' },
            { value: 'test', label: 'Test' },
          ]}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="notif-title" className="text-xs text-muted-foreground">Title</Label>
        <Input
          id="notif-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Notification title"
          maxLength={120}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="notif-content" className="text-xs text-muted-foreground">Message (optional)</Label>
        <Textarea
          id="notif-content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Write your message..."
          rows={3}
        />
      </div>
      <Button onClick={send} disabled={sending || !title.trim()}>
        {sending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Send className="h-4 w-4 mr-2" />}
        Send notification
      </Button>
    </div>
  );
}