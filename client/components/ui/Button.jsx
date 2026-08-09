import { classNames } from '../../lib/utils.js';

export function Button({
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
  const variants = {
    primary: 'bg-primary text-on-primary hover:bg-primary-hover',
    accent: 'bg-btn-accent text-on-primary hover:bg-btn-accent-hover',
    outline: 'border border-outline-variant bg-transparent text-on-surface hover:bg-surface-container',
    danger: 'bg-btn-danger text-on-primary hover:bg-btn-danger-hover',
    ghost: 'bg-transparent text-on-surface-variant hover:bg-surface-container',
  };
  const sizes = {
    sm: 'px-3 py-1.5 text-xs',
    md: 'px-4 py-2.5 text-sm',
    lg: 'px-6 py-3 text-base',
  };
  return (
    <button
      type={type}
      disabled={disabled || loading}
      onClick={onClick}
      className={classNames(
        'inline-flex items-center justify-center gap-2 rounded font-semibold transition-colors',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        variants[variant],
        sizes[size],
        className
      )}
    >
      {loading ? (
        <span className="material-symbols-outlined animate-spin text-base">progress_activity</span>
      ) : icon ? (
        <span className="material-symbols-outlined text-lg">{icon}</span>
      ) : null}
      {children}
    </button>
  );
}
