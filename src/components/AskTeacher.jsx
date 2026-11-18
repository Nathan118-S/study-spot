import React, { useState } from 'react';
import { GraduationCap } from 'lucide-react';
import { useAuth } from '@/lib/AuthContext';
import { Button } from '@/components/ui/button';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { TEACHER_PLACEHOLDER, teacherEmailUrl } from '@/lib/schoolItEmail';

// Opens a drafted email asking a teacher to make the class's Google Calendar
// public (steps for teachers: /teachers), after a short heads-up.
export function AskTeacherButton({ size = 'sm', variant = 'outline', className = 'bg-card' }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type="button" variant={variant} size={size} className={className} onClick={() => setOpen(true)}>
        <GraduationCap className="h-4 w-4 mr-1.5" /> Ask your teacher to share the class calendar
      </Button>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Before you send it</AlertDialogTitle>
            <AlertDialogDescription>
              We’ll open a drafted email to your teacher. Add your teacher’s email address and their name where it says
              “{TEACHER_PLACEHOLDER}”.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => { window.location.href = teacherEmailUrl(user); }}>Open email</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
