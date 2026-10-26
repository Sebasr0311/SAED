import { memo } from 'react';
import { cn } from '../../lib/utils.js';

const SIZE_MAP = {
  sm: { container: 'p-3 gap-2', rows: ['w-3/4', 'w-1/2'], text: 'text-xs' },
  md: { container: 'p-6 gap-2.5', rows: ['w-2/3', 'w-full', 'w-5/6'], text: 'text-xs' },
  lg: { container: 'p-8 gap-3', rows: ['w-1/2', 'w-full', 'w-11/12', 'w-4/5'], text: 'text-sm' },
};

/**
 * LoadingState — skeleton placeholder for views and sections (no spinner in the content).
 * WCAG: role="status" + aria-live="polite"; the message stays readable as a caption.
 * API kept stable: message | description | size | className.
 */
export const LoadingState = memo(function LoadingState({
  message = 'Cargando...',
  description,
  size = 'md',
  className = '',
}) {
  const current = SIZE_MAP[size] || SIZE_MAP.md;

  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      className={cn('flex w-full flex-col', current.container, className)}
    >
      <div className="flex flex-col gap-2" aria-hidden="true">
        {current.rows.map((width, idx) => (
          <div key={idx} className={cn('app-skeleton h-3', idx === 0 ? 'h-3.5' : '', width)} />
        ))}
      </div>
      <div className="pt-1">
        <p className={cn('font-semibold text-text-secondary', current.text)}>{message}</p>
        {description && <p className="mt-0.5 text-xs text-text-muted">{description}</p>}
      </div>
    </div>
  );
});

export default LoadingState;
