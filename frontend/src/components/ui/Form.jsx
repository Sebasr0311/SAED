import { ChevronDown } from 'lucide-react';
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
            'text-sm font-semibold transition-colors',
            error ? 'text-destructive' : 'text-on-surface'
          )}
        >
          {label}
          {required && <span className="ml-1 text-destructive font-bold" aria-hidden="true">*</span>}
        </Label>
      )}
      {children}
      {error && (
        <p id={id ? `${id}-error` : undefined} role="alert" className="text-xs text-destructive font-semibold flex items-center gap-1.5 mt-0.5">
          <span className="inline-block w-1.5 h-1.5 rounded-[1px] bg-destructive shrink-0" aria-hidden="true" />
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
            'flex h-9 w-full appearance-none rounded-md border border-input bg-surface px-3 py-1 pr-8 text-sm shadow-xs text-on-surface transition-[border-color,box-shadow] duration-150',
            'hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25',
            'disabled:cursor-not-allowed disabled:opacity-50',
            error
              ? '!border-destructive focus-visible:!ring-destructive ring-1 !ring-destructive/30 bg-destructive/5'
              : '',
            className
          )}
        >
          {children}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary"
          aria-hidden="true"
        />
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
