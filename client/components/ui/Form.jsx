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
        <Label htmlFor={id} className="text-sm font-medium">
          {label}
          {required && <span className="ml-1 text-error">*</span>}
        </Label>
      )}
      {children}
      {error && (
        <p id={id ? `${id}-error` : undefined} className="text-xs text-error">
          {error}
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
        aria-invalid={error ? true : undefined}
        aria-describedby={error && id ? `${id}-error` : undefined}
        className={cn(error && 'border-destructive focus-visible:ring-destructive', className)}
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
          aria-invalid={error ? true : undefined}
          aria-describedby={error && id ? `${id}-error` : undefined}
          className={cn(
            'flex h-9 w-full appearance-none rounded-md border border-input bg-transparent px-3 py-1 pr-8 text-sm shadow-sm transition-colors',
            'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
            'disabled:cursor-not-allowed disabled:opacity-50',
            error && 'border-destructive focus-visible:ring-destructive',
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
        aria-invalid={error ? true : undefined}
        aria-describedby={error && id ? `${id}-error` : undefined}
        className={cn(error && 'border-destructive focus-visible:ring-destructive', className)}
      />
    </FieldShell>
  );
}
