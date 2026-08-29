/**
 * ActionButtons — shared edit/delete button pair for data tables.
 *
 * @param {Function} onEdit   — click handler for edit button
 * @param {Function} onDelete — click handler for delete button
 */
export function ActionButtons({ onEdit, onDelete }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      <button onClick={onEdit} className="btn btn-ghost btn-sm" aria-label="Editar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>edit</span>
      </button>
      <button onClick={onDelete} className="btn btn-ghost btn-sm" aria-label="Eliminar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--error)' }}>
          delete
        </span>
      </button>
    </div>
  );
}
