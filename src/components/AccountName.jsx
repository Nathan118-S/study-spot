import React, { useState } from 'react';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useToast } from '@/components/ui/use-toast';
import { Loader2, Check } from 'lucide-react';

export default function AccountName() {
  const { user, checkUserAuth } = useAuth();
  const { toast } = useToast();
  const current = user?.name || user?.full_name || '';
  const [name, setName] = useState(current);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    setSaving(true);
    try {
      await base44.auth.updateMe({ name: name.trim() });
      await checkUserAuth();
      toast({ title: 'Name saved' });
    } catch (e) {
      toast({ title: 'Failed to save', description: e.message, variant: 'destructive' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-lg border bg-card p-4 space-y-3">
      <div>
        <p className="text-sm font-medium">Name</p>
        <p className="text-xs text-muted-foreground">Used to personalize the emails we send you.</p>
      </div>
      <div className="flex gap-2">
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Your name"
          className="flex-1"
          maxLength={80}
        />
        <Button onClick={save} disabled={saving || name.trim() === current.trim()}>
          {saving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Check className="h-4 w-4 mr-1" />}
          Save
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{user?.email}</p>
    </div>
  );
}