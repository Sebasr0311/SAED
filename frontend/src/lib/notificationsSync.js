/**
 * Sincronización en tiempo real del estado de notificaciones y buzón
 * entre componentes (NotificationBell, ResBuzonPage, ComunicacionesPage, etc.)
 */
const NOTIFICATIONS_CHANGED_EVENT = 'saed:notifications-changed';

export function emitNotificationsChanged(detail = {}) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(NOTIFICATIONS_CHANGED_EVENT, { detail }));
  }
}

export function subscribeNotificationsChanged(callback) {
  if (typeof window === 'undefined') return () => {};
  const handler = (e) => {
    try {
      callback(e.detail || {});
    } catch {
      /* ignore */
    }
  };
  window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, handler);
  return () => window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, handler);
}
