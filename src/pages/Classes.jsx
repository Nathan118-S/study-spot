import React, { useState, useEffect, useCallback } from 'react';
import { base44 } from '@/api/base44Client';
import ClassForm from '@/components/ClassForm';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Plus, Pencil, Trash2, Loader2, BookOpen } from 'lucide-react';

export default function Classes() {
  const [classes, setClasses] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);

  const load = useCallback(async () => {
    const [c, a] = await Promise.all([
      base44.entities.Class.list(),
      base44.entities.Assignment.list('-due_date', 500),
    ]);
    setClasses(c);
    setAssignments(a);
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const remove = async (c) => {
    await base44.entities.Class.delete(c.id);
    setClasses((prev) => prev.filter((x) => x.id !== c.id));
  };

  const countFor = (id) => assignments.filter((a) => a.class_id === id).length;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Classes</h1>
          <p className="text-muted-foreground text-sm">Manage your courses. Google Classroom classes are imported automatically.</p>
        </div>
        <Button onClick={() => { setEditing(null); setShowForm(true); }}>
          <Plus className="h-4 w-4 mr-2" /> Add Class
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>
      ) : classes.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <BookOpen className="h-10 w-10 text-muted-foreground mb-2" />
          <p className="text-muted-foreground">No classes yet. Add one or connect Google Classroom in Settings.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {classes.map((c) => (
            <div key={c.id} className="rounded-lg border bg-card p-4 flex flex-col">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full" style={{ background: c.color }} />
                  <h3 className="font-semibold">{c.name}</h3>
                </div>
                {c.source !== 'manual' && <Badge variant="secondary" className="text-xs">Google</Badge>}
              </div>
              {c.teacher_name && <p className="text-sm text-muted-foreground mt-1">Teacher: {c.teacher_name}</p>}
              <p className="text-sm text-muted-foreground mt-2">{countFor(c.id)} assignment(s)</p>
              <div className="flex gap-1 mt-3 justify-end">
                <Button variant="ghost" size="icon" onClick={() => { setEditing(c); setShowForm(true); }} title="Edit">
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="icon" onClick={() => remove(c)} title="Delete">
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <ClassForm open={showForm} onOpenChange={setShowForm} cls={editing} onSaved={load} />
    </div>
  );
}