import { useState, useEffect } from 'react';
import { Button } from './Button.jsx';

export function Pagination({ page, totalPages, onPageChange, totalItems, pageSize }) {
  const [jumpInput, setJumpInput] = useState(String(page + 1));

  useEffect(() => {
    setJumpInput(String(page + 1));
  }, [page]);

  if (totalPages <= 1) return null;

  function handleJumpSubmit(e) {
    e.preventDefault();
    const num = parseInt(jumpInput, 10);
    if (!isNaN(num) && num >= 1 && num <= totalPages) {
      onPageChange(num - 1);
    } else {
      setJumpInput(String(page + 1));
    }
  }

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-border/50 text-xs">
      <div className="text-muted-foreground">
        {totalItems != null && (
          <span>
            {page * pageSize + 1}–{Math.min((page + 1) * pageSize, totalItems)} de {totalItems} registros
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          icon="chevron_left"
        >
          Anterior
        </Button>

        <span className="px-2 text-sm text-foreground font-medium">
          Página {page + 1} de {totalPages}
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

        {totalPages > 2 && (
          <form onSubmit={handleJumpSubmit} className="flex items-center gap-1.5 ml-2">
            <label htmlFor="jump-page-input" className="text-muted-foreground text-xs whitespace-nowrap">
              Ir a:
            </label>
            <input
              id="jump-page-input"
              type="number"
              min={1}
              max={totalPages}
              value={jumpInput}
              onChange={(e) => setJumpInput(e.target.value)}
              onBlur={handleJumpSubmit}
              className="w-14 h-8 px-2 text-center text-xs font-medium rounded-md border border-input bg-background text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              aria-label="Ir a número de página"
            />
          </form>
        )}
      </div>
    </div>
  );
}
