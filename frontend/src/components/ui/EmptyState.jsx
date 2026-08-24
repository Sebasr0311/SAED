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
  return (
    <div className={classNames('empty-state', className)}>
      <div className="empty-icon" aria-hidden="true">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="empty-title">{title}</div>
      {subtitle && <p className="empty-subtitle">{subtitle}</p>}
      {children}
    </div>
  );
}
