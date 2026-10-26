import { memo } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { cn } from '../../lib/utils.js';
import { Button } from './Button.jsx';

/**
 * ErrorState — accessible load/execution error for views and sections, with optional retry.
 * API kept stable: title | message | onRetry | retryLabel | icon | className.
 * `compact` (optional) renders an inline row for cards and lists.
 */
export const ErrorState = memo(function ErrorState({
  title = 'Ha ocurrido un error',
  message,
  onRetry,
  retryLabel = 'Reintentar',
  icon = 'error',
  compact = false,
  className = '',
}) {
  const iconNode =
    icon === 'error' ? (
      <AlertTriangle className={compact ? 'h-4 w-4' : 'h-5 w-5'} aria-hidden="true" />
    ) : typeof icon === 'string' ? (
      <span className="material-symbols-outlined text-xl leading-none" aria-hidden="true">
        {icon}
      </span>
    ) : (
      icon
    );

  if (compact) {
    return (
      <div
        role="alert"
        className={cn(
          'flex flex-wrap items-start gap-x-3 gap-y-2 rounded-md border border-tint-red-fg/25 bg-tint-red-bg px-3 py-2.5 text-left',
          className
        )}
      >
        <span className="mt-0.5 shrink-0 text-tint-red-fg">{iconNode}</span>
        <div className="min-w-[11rem] flex-1">
          <p className="text-[13px] font-bold text-on-surface">{title}</p>
          {message && <p className="mt-0.5 text-xs text-text-secondary">{message}</p>}
        </div>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry} className="shrink-0">
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
            {retryLabel}
          </Button>
        )}
      </div>
    );
  }

  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-tint-red-fg/25 bg-tint-red-bg/60 p-8 text-center',
        className
      )}
    >
      <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-md border border-tint-red-fg/30 bg-surface text-tint-red-fg">
        {iconNode}
      </div>
      <h3 className="text-base font-bold text-on-surface">{title}</h3>
      {message && <p className="mt-1 max-w-md text-sm text-text-secondary">{message}</p>}
      {onRetry && (
        <div className="mt-4">
          <Button variant="outline" size="sm" onClick={onRetry}>
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
            {retryLabel}
          </Button>
        </div>
      )}
    </div>
  );
});

export default ErrorState;
