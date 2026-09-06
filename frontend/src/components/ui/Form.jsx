import { cn } from '../../lib/utils.js';
import { Input as ShadcnInput } from './input.tsx';
import { Textarea as ShadcnTextarea } from './textarea.tsx';
import { Label } from './label.tsx';

/**
 * Wrappers de compatibilidad sobre shadcn/ui.
 * Mantienen la API del kit anterior (label|error|id|className) para que las
 * paginas existentes mejoren visualmente sin cambios.
 */

function FieldShell({ label, id, required, error, children, className }) {
  return (
    <div className={cn('grid gap-1.5', className)}>
      {label && (
        <Label
          htmlFor={id}
          className={cn(
            'text-sm font-medium transition-colors',
            error ? 'text-destructive font-semibold' : 'text-foreground'
          )}
        >
          {label}
          {required && <span className="ml-1 text-destructive font-bold" aria-hidden="true">*</span>}
        </Label>
      )}
      {children}
      {error && (
        <p id={id ? `${id}-error` : undefined} role="alert" className="text-xs text-destructive font-medium flex items-center gap-1.5 mt-1 animate-fadeIn">
          <span className="inline-block w-1.5 h-1.5 rounded-full bg-destructive shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}

export function Input({ label, error, id, className = '', ...props }) {
  return (
    <FieldShell label={label} id={id} required={props.required} error={error} className={props.className}>
      <ShadcnInput
        id={id}
        {...props}
        aria-invalid={Boolean(error)}
        aria-describedby={error && id ? `${id}-error` : undefined}
        className={cn(
          error
            ? '!border-destructive focus-visible:!ring-destructive ring-1 !ring-destructive/30 bg-destructive/5 text-foreground'
            : '',
          className
        )}
      />
    </FieldShell>
  );
}

export function Select({ label, error, id, children, className = '', ...props }) {
  return (
    <FieldShell label={label} id={id} required={props.required} error={error} className={props.className}>
      <div className="relative">
        <select
          id={id}
          {...props}
          aria-invalid={Boolean(error)}
          aria-describedby={error && id ? `${id}-error` : undefined}
          className={cn(
            'flex h-9 w-full appearance-none rounded-md border border-input bg-background px-3 py-1 pr-8 text-sm shadow-sm transition-colors text-foreground',
            'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
            'disabled:cursor-not-allowed disabled:opacity-50',
            error
              ? '!border-destructive focus-visible:!ring-destructive ring-1 !ring-destructive/30 bg-destructive/5'
              : '',
            className
          )}
        >
          {children}
        </select>
        <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-base text-muted-foreground">
          expand_more
        </span>
      </div>
    </FieldShell>
  );
}

export function Textarea({ label, error, id, className = '', ...props }) {
  return (
    <FieldShell label={label} id={id} required={props.required} error={error} className={props.className}>
      <ShadcnTextarea
        id={id}
        {...props}
        aria-invalid={Boolean(error)}
        aria-describedby={error && id ? `${id}-error` : undefined}
        className={cn(
          error
            ? '!border-destructive focus-visible:!ring-destructive ring-1 !ring-destructive/30 bg-destructive/5 text-foreground'
            : '',
          className
        )}
      />
    </FieldShell>
  );
}
