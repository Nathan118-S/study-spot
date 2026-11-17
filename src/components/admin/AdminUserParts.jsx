import React from 'react';
import {
  AlertDialog, AlertDialogTrigger, AlertDialogContent, AlertDialogHeader,
  AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/** iOS-style grouped section: header, one card, optional footer. */
export function Section({ title, footer, action, children, className }) {
  return (
    <section className={className}>
      <div className="flex items-end justify-between gap-2">
        <p className="ios-section-header">{title}</p>
        {action && <div className="pb-1.5 pr-1">{action}</div>}
      </div>
      <div className="ios-list">{children}</div>
      {footer && <p className="ios-section-footer">{footer}</p>}
    </section>
  );
}

/** A row in a Section: label + description on the left, control on the right. */
export function Row({ icon: Icon, label, desc, children, danger, stack, className }) {
  return (
    <div
      className={cn(
        'px-4 py-3 flex gap-3',
        stack ? 'flex-col' : 'items-center justify-between',
        className
      )}
    >
      <div className="flex items-start gap-3 min-w-0">
        {Icon && <Icon className={cn('h-5 w-5 mt-0.5 shrink-0', danger ? 'text-destructive' : 'text-primary')} />}
        <div className="min-w-0">
          <p className={cn('text-[15px] font-medium', danger && 'text-destructive')}>{label}</p>
          {desc && <div className="text-[13px] text-muted-foreground break-words">{desc}</div>}
        </div>
      </div>
      {children !== undefined && <div className={cn('shrink-0', stack && 'w-full')}>{children}</div>}
    </div>
  );
}

/** A button that asks for confirmation before running a destructive action. */
export function ConfirmButton({
  title, description, confirmLabel = 'Confirm', onConfirm, destructive = true,
  children, variant = 'outline', size = 'sm', disabled, className,
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>
        <Button variant={variant} size={size} disabled={disabled} className={className}>
          {children}
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description && <AlertDialogDescription>{description}</AlertDialogDescription>}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            className={destructive ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90' : undefined}
            onClick={onConfirm}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

export const fmtDate = (v) => (v ? new Date(v).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—');
export const fmtDay = (v) => (v ? new Date(v).toLocaleDateString(undefined, { dateStyle: 'medium' }) : '—');
