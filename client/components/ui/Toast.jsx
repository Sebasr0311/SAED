import { useEffect, useState } from 'react';
import { Toaster, toast as sonner } from 'sonner';

/**
 * Wrapper de compatibilidad sobre sonner (toasts).
 * Mantiene la API del kit anterior: <Toast toast={{ message, type }} />
 * donde type es success|error|info|warning.
 */
export default function Toast({ toast }) {
  // El Toaster escucha el data-theme del documento (light/dark del app).
  const [theme, setTheme] = useState(() =>
    typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'dark'
      ? 'dark'
      : 'light'
  );

  useEffect(() => {
    if (!toast) return;
    const { message, type } = toast;
    const fn = {
      success: sonner.success,
      error: sonner.error,
      info: sonner.info,
      warning: sonner.warning,
    }[type] || sonner.info;
    fn(String(message));
  }, [toast]);

  useEffect(() => {
    const el = document.documentElement;
    const obs = new MutationObserver(() => {
      setTheme(el.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
    });
    obs.observe(el, { attributes: true, attributeFilter: ['data-theme'] });
    return () => obs.disconnect();
  }, []);

  return (
    <Toaster
      theme={theme}
      position="bottom-right"
      richColors
      toastOptions={{
        style: { borderRadius: '12px', fontSize: '14px' },
      }}
    />
  );
}
