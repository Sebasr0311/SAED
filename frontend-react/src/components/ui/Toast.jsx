import { useState, useEffect } from 'react';
import { classNames } from '../../lib/utils.js';

const TYPES = {
  success: 'bg-accent-green-bg text-accent-green border-accent-green',
  error: 'bg-error-container text-error border-error',
  info: 'bg-blue-50 text-blue-800 border-blue-200',
  warning: 'bg-warn-amber-bg text-warn-amber border-warn-amber',
};

export default function Toast({ toast }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (toast) {
      setVisible(true);
      const t = setTimeout(() => setVisible(false), 3300);
      return () => clearTimeout(t);
    }
    setVisible(false);
  }, [toast]);

  if (!toast || !visible) return null;

  return (
    <div
      role="alert"
      aria-live="polite"
      className={classNames(
        'fixed bottom-6 right-6 z-50 rounded-xl border px-4 py-3 text-sm font-medium shadow-lg',
        TYPES[toast.type] || TYPES.info
      )}
    >
      {toast.message}
    </div>
  );
}
