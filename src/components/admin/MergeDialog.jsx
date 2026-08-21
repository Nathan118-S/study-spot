import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import SheetSelect from '@/components/SheetSelect';
import { GitMerge } from 'lucide-react';

export default function MergeDialog({ open, onOpenChange, source, users, onMerge }) {
  const [targetId, setTargetId] = useState('');

  useEffect(() => {
    if (open) setTargetId('');
  }, [open]);

  const options = users
    .filter((u) => u.id !== source?.id)
    .map((u) => ({ value: u.id, label: u.full_name ? `${u.full_name} — ${u.email}` : u.email }));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Merge account</DialogTitle>
          <DialogDescription>
            Move all assignments and classes from{' '}
            <span className="font-medium text-foreground">{source?.email}</span> into another
            account, then remove {source?.email}. This cannot be undone.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2 py-2">
          <p className="text-sm font-medium">Keep which account?</p>
          <SheetSelect
            value={targetId}
            onValueChange={setTargetId}
            placeholder="Select target account"
            options={options}
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button variant="destructive" disabled={!targetId} onClick={() => onMerge(targetId)}>
            <GitMerge className="h-4 w-4 mr-1" /> Merge &amp; delete source
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}