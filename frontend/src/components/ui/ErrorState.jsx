import { memo } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { cn } from '../../lib/utils.js';
import { Button } from './Button.jsx';

/**
 * ErrorState — Componente accesible para mostrar errores de carga o ejecución en vistas.
 * Cumple WCAG con role="alert" y soporte de reintento.
 */
export const ErrorState = memo(function ErrorState({
  title = 'Ha ocurrido un error',
  message,
  onRetry,
  retryLabel = 'Reintentar',
  icon = 'error',
  className = '',
}) {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center p-8 text-center rounded-xl border border-destructive/20 bg-destructive/5',
        className
      )}
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10 text-destructive mb-3">
        {icon === 'error' ? (
          <AlertTriangle className="h-6 w-6 text-destructive" aria-hidden="true" />
        ) : typeof icon === 'string' ? (
          <span className="material-symbols-outlined text-2xl leading-none" aria-hidden="true">
            {icon}
          </span>
        ) : (
          icon
        )}
      </div>
      <h3 className="text-base font-semibold text-foreground">{title}</h3>
      {message && (
        <p className="mt-1 text-sm text-muted-foreground max-w-md">{message}</p>
      )}
      {onRetry && (
        <div className="mt-4">
          <Button variant="outline" size="sm" onClick={onRetry}>
            <RotateCcw className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            {retryLabel}
          </Button>
        </div>
      )}
    </div>
  );
});

export default ErrorState;
