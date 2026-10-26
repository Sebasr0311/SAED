import React, { memo } from 'react';
import { TrendingDown, TrendingUp } from 'lucide-react';
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

  // Semantic icon tiles: tonal bg/fg pairs from index.css (AA in both modes).
  const variantStyles = {
    primary: { bg: 'bg-tint-primary-bg text-tint-primary-fg' },
    success: { bg: 'bg-tint-green-bg text-tint-green-fg' },
    warning: { bg: 'bg-tint-amber-bg text-tint-amber-fg' },
    danger: { bg: 'bg-tint-red-bg text-tint-red-fg' },
    info: { bg: 'bg-tint-purple-bg text-tint-purple-fg' },
    secondary: { bg: 'bg-tint-neutral-bg text-tint-neutral-fg' },
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
        'relative flex items-center justify-between gap-4 overflow-hidden rounded-lg border border-border bg-card p-5 text-card-foreground shadow-sm transition-[border-color,box-shadow] duration-150 ease-out',
        onClick && 'cursor-pointer hover:border-primary/60 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        className
      )}
    >
      <div className="min-w-0 flex-1 space-y-1">
        {displayLabel && (
          <p className="text-[13px] font-semibold text-text-secondary">{displayLabel}</p>
        )}
        <div className="app-numeral text-[1.625rem] text-on-surface">{value != null ? value : '—'}</div>
        {(displayContext || trend) && (
          <div className="flex items-center gap-2 text-xs text-text-muted">
            {trend && (
              <span
                className={cn(
                  'inline-flex items-center font-semibold',
                  trend.positive ? 'text-tint-green-fg' : 'text-tint-red-fg'
                )}
              >
                {trend.positive ? (
                  <TrendingUp className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                ) : (
                  <TrendingDown className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                )}
                {trend.value}
              </span>
            )}
            {displayContext && <span>{displayContext}</span>}
          </div>
        )}
      </div>

      {icon && (
        <div
          className={cn(
            'flex h-11 w-11 shrink-0 items-center justify-center rounded-md',
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
