import { memo } from 'react';
import { cn } from '../../lib/utils.js';

/**
 * LoadingState — Componente accesible de estado de carga para vistas y secciones.
 * Cumple WCAG con role="status" y aria-live="polite".
 */
export const LoadingState = memo(function LoadingState({
  message = 'Cargando...',
  description,
  size = 'md',
  className = '',
}) {
  const sizeMap = {
    sm: {
      container: 'p-4 gap-2',
      spinner: 'h-5 w-5 border-2',
      text: 'text-xs',
    },
    md: {
      container: 'p-8 gap-3',
      spinner: 'h-8 w-8 border-2',
      text: 'text-sm',
    },
    lg: {
      container: 'p-12 gap-4',
      spinner: 'h-10 w-10 border-3',
      text: 'text-base',
    },
  };

  const currentSize = sizeMap[size] || sizeMap.md;

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex flex-col items-center justify-center text-center text-muted-foreground',
        currentSize.container,
        className
      )}
    >
      <div
        className={cn(
          'animate-spin rounded-full border-primary border-t-transparent',
          currentSize.spinner
        )}
        aria-hidden="true"
      />
      <div className="space-y-0.5">
        <p className={cn('font-medium text-foreground', currentSize.text)}>{message}</p>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      <span className="sr-only">{message}</span>
    </div>
  );
});

export default LoadingState;
