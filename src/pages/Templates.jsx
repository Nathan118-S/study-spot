import React, { useState, useEffect } from 'react';
import { base44 } from '@/api/base44Client';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/components/ui/use-toast';
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
import TemplateForm from '@/components/TemplateForm';
import { Loader2, Plus, Pencil, Trash2, LayoutTemplate } from 'lucide-react';

const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);

export default function Templates() {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [busy, setBusy] = useState(null);
  const { toast } = useToast();

  const load = async () => {
    setLoading(true);
    try {
      const data = await base44.entities.AssignmentTemplate.list('-updated_date', 100);
      setTemplates(data);
    } catch (e) {
      toast({ title: 'Failed to load templates', description: e.message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const openNew = () => { setEditing(null); setShowForm(true); };
  const openEdit = (t) => { setEditing(t); setShowForm(true); };

  const remove = async (t) => {
    setBusy(t.id);
    try {
      await base44.entities.AssignmentTemplate.delete(t.id);
      setTemplates((prev) => prev.filter((x) => x.id !== t.id));
      toast({ title: 'Template deleted' });
    } catch (e) {
      toast({ title: 'Failed', description: e.message, variant: 'destructive' });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold flex items-center gap-2">
            <LayoutTemplate className="h-7 w-7 text-primary" /> Templates
          </h1>
          <p className="text-muted-foreground text-sm">Reusable presets to speed up adding assignments.</p>
        </div>
        <Button onClick={openNew} className="h-11 md:h-9">
          <Plus className="h-4 w-4 mr-1" /> New template
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : templates.length === 0 ? (
        <div className="rounded-lg border border-dashed p-10 text-center">
          <LayoutTemplate className="h-10 w-10 mx-auto text-muted-foreground mb-3" />
          <p className="font-medium">No templates yet</p>
          <p className="text-sm text-muted-foreground mt-1">Create a template to prefill type, priority, points, and notes.</p>
          <Button onClick={openNew} className="mt-4 h-11 md:h-9">
            <Plus className="h-4 w-4 mr-1" /> Create your first template
          </Button>
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {templates.map((t) => (
            <div key={t.id} className="rounded-lg border bg-card p-4 flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium truncate">{t.name}</p>
                <div className="flex gap-1">
                  <Button size="icon" variant="ghost" onClick={() => openEdit(t)} disabled={busy === t.id} className="h-8 w-8">
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <AlertDialog>
                    <AlertDialogTrigger asChild>
                      <Button size="icon" variant="ghost" disabled={busy === t.id} className="h-8 w-8 text-destructive">
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle>Delete "{t.name}"?</AlertDialogTitle>
                        <AlertDialogDescription>This template will be removed. Existing assignments are unaffected.</AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel disabled={busy === t.id}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                          onClick={() => remove(t)}
                          disabled={busy === t.id}
                        >
                          Delete
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                  </AlertDialog>
                </div>
              </div>
              {t.title && <p className="text-sm text-muted-foreground truncate">Title: {t.title}</p>}
              <div className="flex flex-wrap gap-1.5">
                <Badge variant="secondary" className="capitalize">{cap(t.type)}</Badge>
                <Badge variant="outline" className="capitalize">{t.priority}</Badge>
                {t.points > 0 && <Badge variant="outline">{t.points} pts</Badge>}
              </div>
              {t.notes && <p className="text-xs text-muted-foreground line-clamp-2">{t.notes}</p>}
            </div>
          ))}
        </div>
      )}

      <TemplateForm
        open={showForm}
        onOpenChange={setShowForm}
        template={editing}
        onSaved={load}
      />
    </div>
  );
}