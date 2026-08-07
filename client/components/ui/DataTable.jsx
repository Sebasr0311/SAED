import { classNames } from '../../lib/utils.js';

export function DataTable({ columns, rows, loading, empty, onRowClick, keyField = 'id', selectedKey }) {
  if (loading) {
    return (
      <div className="table-container p-8 text-center text-on-surface-variant">Cargando...</div>
    );
  }
  if (!rows || rows.length === 0) {
    return (
      <div className="table-container p-8 text-center text-on-surface-variant">
        {empty || 'Sin datos'}
      </div>
    );
  }
  return (
    <div className="table-container">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} style={col.width ? { width: col.width } : undefined}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, idx) => (
            <tr
              key={row[keyField] ?? idx}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={classNames(onRowClick && 'cursor-pointer', selectedKey != null && row[keyField] === selectedKey && 'selected')}
              style={selectedKey != null && row[keyField] === selectedKey ? { background: '#edf4fc' } : undefined}
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
    </div>
  );
}
