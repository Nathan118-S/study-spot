import React, { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
import { api } from '@/api/client';
import TemplateForm from '@/components/TemplateForm';
import {
  AlertDialog,
  AlertDialogTrigger,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from '@/components/ui/alert-dialog';
import { Plus, Pencil, Trash2, LayoutTemplate } from 'lucide-react';

const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);

export default function TemplateManager({ open, onOpenChange, templates, onChanged }) {
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(null);
  const { toast } = useToast();

  const openNew = () => { setEditing(null); setShowForm(true); };
  const openEdit = (t) => { setEditing(t); setShowForm(true); };

  const remove = async (t) => {
    setBusy(t.id);
    try {
      await api.entities.AssignmentTemplate.delete(t.id);
      onChanged?.();
      toast({ title: 'Template deleted' });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <Dialog open={open && !showForm} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <LayoutTemplate className="h-5 w-5 text-primary" /> Templates
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-2 max-h-[55vh] overflow-y-auto">
            {templates.length === 0 ? (
              <p className="text-sm text-muted-foreground py-6 text-center">
                No templates yet. Create one to prefill new assignments.
              </p>
            ) : templates.map((t) => (
              <div key={t.id} className="flex items-center justify-between gap-2 rounded-md border p-3">
                <div className="min-w-0">
                  <p className="font-medium truncate">{t.name}</p>
                  {t.title && <p className="text-xs text-muted-foreground truncate">Title: {t.title}</p>}
                  <div className="flex gap-1.5 mt-1">
                    <Badge variant="secondary" className="capitalize">{cap(t.type)}</Badge>
                    <Badge variant="outline" className="capitalize">{t.priority}</Badge>
                    {t.points > 0 && <Badge variant="outline">{t.points} pts</Badge>}
                  </div>
                </div>
                <div className="flex gap-1 shrink-0">
                  <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(t)} disabled={busy === t.id}>
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button size="icon" variant="ghost" className="h-8 w-8 text-destructive" disabled={busy === t.id}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Delete "{t.name}"?</AlertDialogTitle>
                        <AlertDialogDescription>This template will be removed. Existing assignments are unaffected.</AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                          onClick={() => remove(t)}
                        >
                          Delete
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </div>
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>Done</Button>
            <Button onClick={openNew}><Plus className="h-4 w-4 mr-1" /> New template</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <TemplateForm
        open={showForm}
        onOpenChange={setShowForm}
        template={editing}
        onSaved={onChanged}
      />
    </>
  );
}