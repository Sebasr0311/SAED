import { classNames } from '../../lib/utils.js';

export function Input({ label, error, id, className = '', ...props }) {
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
        className={classNames(
          'w-full rounded-lg border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-error/20'
            : 'border-outline-variant focus:border-primary focus:ring-primary/20',
          className
        )}
      />
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  );
}

export function Select({ label, error, id, children, className = '', ...props }) {
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
        className={classNames(
          'w-full rounded-lg border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-error/20'
            : 'border-outline-variant focus:border-primary focus:ring-primary/20',
          className
        )}
      >
        {children}
      </select>
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  );
}

export function Textarea({ label, error, id, className = '', ...props }) {
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
        className={classNames(
          'w-full rounded-lg border bg-surface px-3 py-2 text-sm transition-colors',
          'focus:outline-none focus:ring-2',
          error
            ? 'border-error focus:border-error focus:ring-error/20'
            : 'border-outline-variant focus:border-primary focus:ring-primary/20',
          className
        )}
      />
      {error && <p className="text-xs text-error">{error}</p>}
    </div>
  );
}
