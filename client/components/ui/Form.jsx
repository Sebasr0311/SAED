import { classNames } from '../../lib/utils.js';

export function Input({ label, error, id, className = '', ...props }) {
  const errorId = error && id ? `${id}-error` : undefined;
  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={id} className="block text-sm font-medium text-on-surface">
          {label}
          {props.required && <span className="ml-1 text-error">*</span>}
        </label>
      )}
      <input
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={classNames(
          'w-full rounded border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-ring-error'
            : 'border-outline-variant focus:border-primary focus:ring-ring-primary',
          className
        )}
      />
      {error && <p id={errorId} className="text-xs text-error">{error}</p>}
    </div>
  );
}

export function Select({ label, error, id, children, className = '', ...props }) {
  const errorId = error && id ? `${id}-error` : undefined;
  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={id} className="block text-sm font-medium text-on-surface">
          {label}
          {props.required && <span className="ml-1 text-error">*</span>}
        </label>
      )}
      <select
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={classNames(
          'w-full rounded border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-ring-error'
            : 'border-outline-variant focus:border-primary focus:ring-ring-primary',
          className
        )}
      >
        {children}
      </select>
      {error && <p id={errorId} className="text-xs text-error">{error}</p>}
    </div>
  );
}

export function Textarea({ label, error, id, className = '', ...props }) {
  const errorId = error && id ? `${id}-error` : undefined;
  return (
    <div className="space-y-1">
      {label && (
        <label htmlFor={id} className="block text-sm font-medium text-on-surface">
          {label}
          {props.required && <span className="ml-1 text-error">*</span>}
        </label>
      )}
      <textarea
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        className={classNames(
          'w-full rounded border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-ring-error'
            : 'border-outline-variant focus:border-primary focus:ring-ring-primary',
          className
        )}
      />
      {error && <p id={errorId} className="text-xs text-error">{error}</p>}
    </div>
  );
}
