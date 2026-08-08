import { useEffect, useRef } from 'react';
import { classNames } from '../../lib/utils.js';

export function Modal({ open, onClose, title, children, footer, size = 'md' }) {
  const panelRef = useRef(null);
  const returnFocusRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    // Recordar el elemento que abrió el modal para devolverle el foco al cerrar
    returnFocusRef.current = document.activeElement;

    const panel = panelRef.current;
    if (panel) {
      // Foco inicial: primer campo editable o botón, o el panel como fallback
      const focusables = panel.querySelectorAll(
        'input, select, textarea, button, [href], [tabindex]:not([tabindex="-1"])'
      );
      const target = focusables[0] || panel;
      target.focus?.();
    }

    function onKeyDown(e) {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose();
      }
      // Enfoque atrapado razonablemente dentro del modal (Tab/Shift+Tab)
      if (e.key === 'Tab') {
        const focusables = panel.querySelectorAll(
          'input, select, textarea, button, [href], [tabindex]:not([tabindex="-1"])'
        );
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      // Retorno de foco al elemento que abrió el modal
      returnFocusRef.current?.focus?.();
    };
  }, [open, onClose]);

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
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title || undefined}
        className={classNames(
          'flex max-h-[90vh] w-full flex-col rounded-2xl bg-surface shadow-2xl',
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
        <div className="min-h-0 flex-1 overflow-y-auto p-6">{children}</div>
        {footer && (
          <div className="flex justify-end gap-2 border-t border-outline-variant px-6 py-4">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
