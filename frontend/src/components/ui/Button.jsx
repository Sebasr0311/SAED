import { memo } from 'react';
import { cn } from '../../lib/utils.js';
import { Button as ShadcnButton } from './button.tsx';

/**
 * Wrapper de compatibilidad sobre el Button de shadcn/ui.
 * Mantiene la API del kit anterior (variant|size|loading|icon) para que las
 * paginas existentes mejoren visualmente sin cambios.
 */
export const Button = memo(function Button({
  variant = 'primary',
  size = 'md',
  type = 'button',
  disabled = false,
  loading = false,
  onClick,
  children,
  className = '',
  icon,
}) {
  const variantMap = {
    primary: 'default',
    accent: 'default',
    outline: 'outline',
    danger: 'destructive',
    ghost: 'ghost',
  };
  const sizeMap = {
    sm: 'sm',
    md: 'default',
    lg: 'lg',
  };
  const accentBg = variant === 'accent' ? '!bg-btn-accent hover:!bg-btn-accent-hover' : '';
  // WCAG 2.5.8: touch target >=44px en movil; compacto en desktop (igual que el kit anterior).
  const touchTarget = size === 'sm' ? 'min-h-11 min-w-11 sm:min-h-8' : 'min-h-11 sm:min-h-9';
  return (
    <ShadcnButton
      type={type}
      disabled={disabled || loading}
      onClick={onClick}
      variant={variantMap[variant] || 'default'}
      size={sizeMap[size] || 'default'}
      className={cn(touchTarget, accentBg, className)}
    >
      {loading ? (
        <span className="material-symbols-outlined animate-spin text-base leading-none">progress_activity</span>
      ) : icon ? (
        <span className="material-symbols-outlined text-lg leading-none">{icon}</span>
      ) : null}
      {children}
    </ShadcnButton>
  );
});
