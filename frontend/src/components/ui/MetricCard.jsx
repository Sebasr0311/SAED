import React, { memo } from 'react';
import { cn } from '../../lib/utils.js';

/**
 * MetricCard — Tarjeta de KPI / Métrica estándar del Design System SAED 2.0.
 *
 * Sigue el patrón:
 * - Valor principal (text-2xl font-bold)
 * - Etiqueta (text-sm text-muted-foreground)
 * - Contexto / subtítulo / tendencia
 * - Icono (admite string de Material Symbols o componente de Lucide)
 * - Estado semántico opcional (primary, success, warning, danger, info)
 */
export const MetricCard = memo(function MetricCard({
  value,
  label,
  title,
  subtitle,
  context,
  icon,
  variant = 'primary',
  color,
  trend,
  className = '',
  onClick,
}) {
  const displayLabel = label || title;
  const displayContext = context || subtitle;
  const activeVariant = color || variant;

  const variantStyles = {
    primary: {
      bg: 'bg-navy-50 text-navy-800 dark:bg-navy-900/50 dark:text-navy-300',
      border: 'border-slate-200 dark:border-slate-800',
    },
    success: {
      bg: 'bg-success-50 text-success-700 dark:bg-success-950/40 dark:text-success-400',
      border: 'border-success-200/60 dark:border-success-800/60',
    },
    warning: {
      bg: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-400',
      border: 'border-amber-200/60 dark:border-amber-800/60',
    },
    danger: {
      bg: 'bg-danger-50 text-danger-700 dark:bg-danger-950/40 dark:text-danger-400',
      border: 'border-danger-200/60 dark:border-danger-800/60',
    },
    info: {
      bg: 'bg-info-50 text-info-700 dark:bg-info-950/40 dark:text-info-400',
      border: 'border-info-200/60 dark:border-info-800/60',
    },
    secondary: {
      bg: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
      border: 'border-slate-200 dark:border-slate-700',
    },
  };

  const currentTheme = variantStyles[activeVariant] || variantStyles.primary;

  function renderIcon() {
    if (!icon) return null;
    if (typeof icon === 'string') {
      return (
        <span className="material-symbols-outlined text-xl leading-none" aria-hidden="true">
          {icon}
        </span>
      );
    }
    if (React.isValidElement(icon)) {
      return icon;
    }
    if (typeof icon === 'function' || typeof icon === 'object') {
      const IconComponent = icon;
      return <IconComponent className="h-5 w-5" aria-hidden="true" />;
    }
    return null;
  }

  return (
    <div
      role={onClick ? 'button' : 'group'}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick}
      onKeyDown={onClick ? (e) => (e.key === 'Enter' || e.key === ' ') && onClick(e) : undefined}
      aria-label={displayLabel ? `Métrica: ${displayLabel}` : undefined}
      className={cn(
        'relative flex items-center justify-between gap-4 overflow-hidden rounded-xl border bg-card p-5 text-card-foreground shadow-sm transition-all duration-200',
        'hover:shadow-md hover:-translate-y-0.5',
        onClick && 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        className
      )}
    >
      <div className="min-w-0 flex-1 space-y-1">
        {displayLabel && (
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {displayLabel}
          </p>
        )}
        <div className="text-2xl font-bold tracking-tight text-on-background">
          {value != null ? value : '—'}
        </div>
        {(displayContext || trend) && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            {trend && (
              <span
                className={cn(
                  'inline-flex items-center font-semibold',
                  trend.positive ? 'text-success-600' : 'text-danger-600'
                )}
              >
                {trend.positive ? '↑' : '↓'} {trend.value}
              </span>
            )}
            {displayContext && <span>{displayContext}</span>}
          </div>
        )}
      </div>

      {icon && (
        <div
          className={cn(
            'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl transition-colors',
            currentTheme.bg
          )}
        >
          {renderIcon()}
        </div>
      )}
    </div>
  );
});

export default MetricCard;
