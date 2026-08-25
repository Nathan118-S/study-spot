import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';
import { base44 } from '@/api/base44Client';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import { Plus, Loader2, Inbox } from 'lucide-react';
import { cn } from '@/lib/utils';
import AssignmentForm from '@/components/AssignmentForm';
import AssignmentDetail from '@/components/AssignmentDetail';
import BoardCard from '@/components/BoardCard';
import { isGraded, DEFAULT_GRADING_SCALE } from '@/lib/grading';
import { isDemoUser, getDemoAssignments, getDemoClasses } from '@/lib/demoData';

const COLUMNS = [
  { id: 'todo', title: 'To Do' },
  { id: 'inprogress', title: 'In Progress' },
  { id: 'completed', title: 'Completed' },
];

const columnFor = (a) => {
  if (a.completed) return 'completed';
  if (typeof a.progress === 'number' && a.progress > 0 && a.progress < 100) return 'inprogress';
  return 'todo';
};

export default function Board() {
  const { user } = useAuth();
  const [assignments, setAssignments] = useState([]);
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [detail, setDetail] = useState(null);
  const [gradingScale, setGradingScale] = useState(DEFAULT_GRADING_SCALE);

  const load = useCallback(async () => {
    if (isDemoUser(user)) {
      setAssignments(getDemoAssignments());
      setClasses(getDemoClasses());
      return;
    }
    const [a, c] = await Promise.all([
      base44.entities.Assignment.list('-due_date', 500),
      base44.entities.Class.list(),
    ]);
    setAssignments(a.filter((x) => !isGraded(x)));
    setClasses(c);
    try {
      const me = await base44.auth.me();
      const gs = me?.data?.grading_scale;
      if (gs && gs.length) setGradingScale(gs);
    } catch {}
  }, [user]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  const classColor = (id) => classes.find((c) => c.id === id)?.color || '#94a3b8';

  const grouped = useMemo(() => {
    const g = { todo: [], inprogress: [], completed: [] };
    for (const a of assignments) g[columnFor(a)].push(a);
    return g;
  }, [assignments]);

  const moveTo = async (assignment, columnId) => {
    const patch =
      columnId === 'todo'
        ? { completed: false, progress: 0 }
        : columnId === 'inprogress'
        ? {
            completed: false,
            progress:
              assignment.progress > 0 && assignment.progress < 100 ? assignment.progress : 50,
          }
        : { completed: true, progress: 100 };
    setAssignments((prev) => prev.map((a) => (a.id === assignment.id ? { ...a, ...patch } : a)));
    if (isDemoUser(user)) return;
    try {
      await base44.entities.Assignment.update(assignment.id, patch);
      if (
        patch.completed &&
        assignment.source === 'google_classroom' &&
        assignment.external_id &&
        user?.data?.sync_completion_to_classroom !== false
      ) {
        base44.functions.invoke('syncCompletionToClassroom', { assignment_id: assignment.id }).catch(() => {});
      }
    } catch {
      load();
    }
  };

  const onDragEnd = (result) => {
    const { source, destination } = result;
    if (!destination || source.droppableId === destination.droppableId) return;
    const a = grouped[source.droppableId]?.[source.index];
    if (!a) return;
    moveTo(a, destination.droppableId);
  };

  const applyUpdate = (updated) => {
    setAssignments((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
    setDetail((d) => (d && d.id === updated.id ? updated : d));
  };

  const remove = async (a) => {
    setAssignments((prev) => prev.filter((x) => x.id !== a.id));
    setDetail(null);
    if (isDemoUser(user)) return;
    try {
      await base44.entities.Assignment.delete(a.id);
    } catch {
      load();
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl md:text-3xl font-bold">Board</h1>
          <p className="text-muted-foreground text-sm">
            Drag assignments between columns to update their status.
          </p>
        </div>
        <Button
          onClick={() => {
            setEditing(null);
            setShowForm(true);
          }}
        >
          <Plus className="h-4 w-4 mr-2" /> Add Assignment
        </Button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : assignments.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Inbox className="h-10 w-10 text-muted-foreground mb-2" />
          <p className="text-muted-foreground">No assignments yet. Add one to get started.</p>
        </div>
      ) : (
        <DragDropContext onDragEnd={onDragEnd}>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {COLUMNS.map((col) => (
              <Droppable droppableId={col.id} key={col.id}>
                {(provided, snapshot) => (
                  <div
                    ref={provided.innerRef}
                    {...provided.droppableProps}
                    className={cn(
                      'flex flex-col rounded-lg border bg-muted/30 min-h-[200px] transition-colors',
                      snapshot.isDraggingOver && 'bg-primary/5 border-primary/40'
                    )}
                  >
                    <div className="flex items-center justify-between px-3 py-2 border-b">
                      <span className="font-medium text-sm">{col.title}</span>
                      <span className="text-xs text-muted-foreground bg-card border rounded-full px-2 py-0.5">
                        {grouped[col.id].length}
                      </span>
                    </div>
                    <div className="flex-1 p-2 space-y-2 overflow-y-auto max-h-[70vh]">
                      {grouped[col.id].length === 0 ? (
                        <p className="text-xs text-muted-foreground text-center py-6">Drop here</p>
                      ) : (
                        grouped[col.id].map((a, index) => (
                          <Draggable draggableId={a.id} index={index} key={a.id}>
                            {(p, s) => (
                              <div
                                ref={p.innerRef}
                                {...p.draggableProps}
                                {...p.dragHandleProps}
                                className={cn(s.isDragging && 'shadow-lg ring-2 ring-primary/40 rounded-lg')}
                              >
                                <BoardCard
                                  assignment={a}
                                  classColor={classColor(a.class_id)}
                                  onClick={() => setDetail(a)}
                                />
                              </div>
                            )}
                          </Draggable>
                        ))
                      )}
                      {provided.placeholder}
                    </div>
                  </div>
                )}
              </Droppable>
            ))}
          </div>
        </DragDropContext>
      )}

      <AssignmentForm
        open={showForm}
        onOpenChange={setShowForm}
        assignment={editing}
        classes={classes}
        onSaved={load}
      />

      <AssignmentDetail
        open={!!detail}
        onOpenChange={(o) => !o && setDetail(null)}
        assignment={detail}
        classes={classes}
        gradingScale={gradingScale}
        onUpdated={applyUpdate}
        onEdit={(a) => {
          setDetail(null);
          setEditing(a);
          setShowForm(true);
        }}
        onDelete={remove}
      />
    </div>
  );
}