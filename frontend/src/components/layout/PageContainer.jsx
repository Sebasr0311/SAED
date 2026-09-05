import { memo } from 'react';
import { cn } from '../../lib/utils.js';

/**
 * PageContainer — Contenedor estructural estándar para vistas internas de SAED 2.0.
 * Provee ancho máximo unificado, padding responsivo (mobile a desktop) y espaciado coherente.
 */
export const PageContainer = memo(function PageContainer({
  children,
  className = '',
  maxWidth = 'max-w-7xl',
}) {
  return (
    <div className={cn('w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6', maxWidth, className)}>
      {children}
    </div>
  );
});

export default PageContainer;
