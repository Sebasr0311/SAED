import { classNames } from '../../lib/utils.js';

export function Modal({ open, onClose, title, children, footer, size = 'md' }) {
  if (!open) return null;
  const sizes = {
    sm: 'max-w-md',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl',
  };
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        className={classNames(
          'w-full rounded-2xl bg-surface shadow-2xl',
          sizes[size]
        )}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className="flex items-center justify-between border-b border-outline-variant px-6 py-4">
            <h3 className="text-lg font-semibold text-on-surface">{title}</h3>
            <button
              onClick={onClose}
              className="text-on-surface-variant hover:text-on-surface"
              aria-label="Cerrar"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
          </div>
        )}
        <div className="p-6">{children}</div>
        {footer && (
          <div className="flex justify-end gap-2 border-t border-outline-variant px-6 py-4">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
