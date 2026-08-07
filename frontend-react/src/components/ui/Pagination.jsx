import { Button } from './Button.jsx';

export function Pagination({ page, totalPages, onPageChange, totalItems, pageSize }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between px-4 py-3">
      <div className="text-xs text-on-surface-variant">
        {totalItems != null && (
          <span>
            {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalItems)} de {totalItems}
          </span>
        )}
      </div>
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          icon="chevron_left"
        >
          Anterior
        </Button>
        <span className="px-3 text-sm text-on-surface-variant">
          Página {page + 1} / {totalPages}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          icon="chevron_right"
        >
          Siguiente
        </Button>
      </div>
    </div>
  );
}
