import { memo, useEffect, useState } from 'react';
import { classNames } from '../../lib/utils.js';
import EmptyState from './EmptyState.jsx';
import { Button } from './Button.jsx';

export const DataTable = memo(function DataTable({ columns, rows, loading, empty, onRowClick, keyField = 'id', selectedKey, error, onRetry, pageSize }) {
  const [page, setPage] = useState(1);

  // Si cambian los datos (refetch, filtro), volver a la primera pagina.
  const totalRows = rows?.length || 0;
  useEffect(() => {
    setPage(1);
  }, [totalRows]);

  const totalPaginas = pageSize && pageSize > 0 ? Math.max(1, Math.ceil(totalRows / pageSize)) : 1;
  const paginaSegura = Math.min(page, totalPaginas);
  const filasVisibles = pageSize && pageSize > 0 ? rows.slice((paginaSegura - 1) * pageSize, paginaSegura * pageSize) : rows;

  if (loading) {
    return (
      <div className="table-container p-8 text-center text-on-surface-variant">Cargando...</div>
    );
  }
  if (error) {
    return (
      <div className="table-container p-8 text-center">
        <p className="text-error" style={{ marginBottom: '8px' }}>{error?.message || error}</p>
        {onRetry && (
          <button type="button" className="btn btn-outline btn-sm" onClick={onRetry}>
            Reintentar
          </button>
        )}
      </div>
    );
  }
  if (!rows || rows.length === 0) {
    // `empty` acepta string (titulo) u objeto { icon, title, subtitle }.
    const emptyTitle = typeof empty === 'string' ? empty : empty?.title;
    const emptyObj = typeof empty === 'string' ? { title: empty } : empty || {};
    return (
      <div className="table-container">
        <EmptyState
          icon={emptyObj.icon}
          title={emptyTitle || 'Sin datos'}
          subtitle={emptyObj.subtitle}
        />
      </div>
    );
  }
  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} scope="col" style={col.width ? { width: col.width } : undefined}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filasVisibles.map((row, idx) => (
            <tr
              key={row[keyField] ?? idx}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={
                onRowClick
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
              tabIndex={onRowClick ? 0 : undefined}
              role={onRowClick ? 'button' : undefined}
              aria-label={onRowClick ? 'Ver detalles' : undefined}
              className={classNames(
                onRowClick && 'row-clickable',
                selectedKey != null && row[keyField] === selectedKey && 'selected'
              )}
              style={selectedKey != null && row[keyField] === selectedKey ? { background: 'var(--surface-selected)' } : undefined}
            >
              {columns.map((col) => (
                <td key={col.key} className={classNames(col.cellClassName)}>
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {pageSize && pageSize > 0 && totalPaginas > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', paddingTop: '14px' }}>
          <Button
            variant="outline"
            size="sm"
            disabled={paginaSegura <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            ← Anterior
          </Button>
          <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            Página {paginaSegura} de {totalPaginas}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={paginaSegura >= totalPaginas}
            onClick={() => setPage((p) => Math.min(totalPaginas, p + 1))}
          >
            Siguiente →
          </Button>
        </div>
      )}
    </div>
  );
});
