import React from 'react';
import { classNames } from '../../lib/utils.js';

/**
 * Estado vacio consistente para tablas y listas: icono + titulo + subtitulo
 * opcional. Usa tokens semanticos (funciona en light y dark).
 *
 * Uso:
 *   <EmptyState icon="inbox" title="Sin datos" subtitle="..." />
 *   <EmptyState title="No hay apartamentos" />  // icono por defecto
 */
export default function EmptyState({ icon = 'inbox', title, subtitle, className = '', children }) {
  function renderIcon() {
    if (!icon) return null;
    if (React.isValidElement(icon)) return icon;
    if (typeof icon === 'string') {
      return <span className="material-symbols-outlined">{icon}</span>;
    }
    if (typeof icon === 'function' || typeof icon === 'object') {
      const IconComponent = icon;
      return <IconComponent className="h-8 w-8 text-muted-foreground" aria-hidden="true" />;
    }
    return null;
  }

  return (
    <div className={classNames('empty-state', className)}>
      <div className="empty-icon" aria-hidden="true">
        {renderIcon()}
      </div>
      <div className="empty-title">{title}</div>
      {subtitle && <p className="empty-subtitle">{subtitle}</p>}
      {children}
    </div>
  );
}
