import { useCallback, useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { formatDateTime } from '../lib/utils.js';
import { Card, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/input.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { ShieldCheck, Search, Eye, ChevronLeft, ChevronRight } from 'lucide-react';

const FILTER_SELECT =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 sm:w-auto';

const ACTION_VARIANT = {
  CREATE: 'success',
  INSERT: 'success',
  UPDATE: 'info',
  DELETE: 'destructive',
  STATUS_CHANGE: 'warning',
};

function Dato({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-text-muted">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium text-on-surface">{children}</dd>
    </div>
  );
}

export default function OrgAuditoriaPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [actionFilter, setActionFilter] = useState('ALL');
  const [selectedLog, setSelectedLog] = useState(null);
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 10;

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/audit');
      setLogs(Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []);
    } catch (err) {
      console.error('Error loading org audit logs:', err);
      setError('No se pudo cargar la pista de auditoría.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(
    () =>
      logs.filter((l) => {
        const term = searchTerm.toLowerCase();
        const matchesSearch =
          (l.recursoAfectado || '').toLowerCase().includes(term) ||
          (l.accion || '').toLowerCase().includes(term) ||
          (l.ipCliente || '').toLowerCase().includes(term) ||
          String(l.idUsuario || '').includes(term);
        const matchesAction = actionFilter === 'ALL' || l.accion === actionFilter;
        return matchesSearch && matchesAction;
      }),
    [logs, searchTerm, actionFilter]
  );

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const paginatedLogs = useMemo(() => {
    return filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
  }, [filtered, safePage]);

  const formatAction = (action) => {
    switch (action) {
      case 'CREATE':
      case 'INSERT':
        return 'Creación';
      case 'UPDATE':
        return 'Modificación';
      case 'DELETE':
        return 'Eliminación';
      case 'STATUS_CHANGE':
        return 'Cambio de estado';
      default:
        return action;
    }
  };

  const hayFiltros = Boolean(searchTerm || actionFilter !== 'ALL');

  return (
    <PageContainer>
      <PageHeader
        title="Pista de auditoría"
        subtitle="Registro inmutable de acciones, cambios y eventos de seguridad en su organización."
        badge={!loading && !error ? <Badge variant="outline">{logs.length} eventos registrados</Badge> : null}
      />

      <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:w-80">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <Input
            type="search"
            aria-label="Buscar por recurso, usuario o IP"
            placeholder="Buscar por recurso, usuario o IP..."
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setPage(0);
            }}
            className="h-10 pl-9 text-sm"
          />
        </div>
        <select
          aria-label="Filtrar por acción"
          value={actionFilter}
          onChange={(e) => {
            setActionFilter(e.target.value);
            setPage(0);
          }}
          className={FILTER_SELECT}
        >
          <option value="ALL">Todas las acciones</option>
          <option value="CREATE">Creación</option>
          <option value="UPDATE">Modificación</option>
          <option value="DELETE">Eliminación</option>
          <option value="STATUS_CHANGE">Cambio de estado</option>
        </select>
      </div>

      {loading ? (
        <div role="status" aria-busy="true" aria-label="Cargando auditoría" className="space-y-2">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-11 w-full" />
          ))}
        </div>
      ) : error ? (
        <ErrorState title="No se pudo cargar la auditoría" message={error} onRetry={load} />
      ) : filtered.length === 0 ? (
        <div className="table-container">
          <EmptyState
            icon={ShieldCheck}
            title={hayFiltros ? 'Sin coincidencias' : 'No hay registros de auditoría'}
            subtitle={
              hayFiltros
                ? 'Ningún evento coincide con los filtros aplicados.'
                : 'Las acciones realizadas en la organización quedarán registradas aquí.'
            }
          >
            {hayFiltros && (
              <Button
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={() => {
                  setSearchTerm('');
                  setActionFilter('ALL');
                  setPage(0);
                }}
              >
                Limpiar filtros
              </Button>
            )}
          </EmptyState>
        </div>
      ) : (
        <Card>
          <CardContent className="p-0">
            <div className="max-h-[70vh] overflow-auto rounded-t-lg">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Fecha y hora</th>
                    <th scope="col">Acción</th>
                    <th scope="col">Recurso</th>
                    <th scope="col">ID registro</th>
                    <th scope="col">Usuario</th>
                    <th scope="col">IP de origen</th>
                    <th scope="col" className="text-right">
                      <span className="sr-only">Detalle</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedLogs.map((l) => (
                    <tr key={l.idAuditoria}>
                      <td className="whitespace-nowrap text-xs tabular-nums text-text-secondary">
                        {l.fechaHora ? formatDateTime(l.fechaHora) : '—'}
                      </td>
                      <td>
                        <Badge variant={ACTION_VARIANT[l.accion] || 'secondary'}>{formatAction(l.accion)}</Badge>
                      </td>
                      <td className="text-xs font-medium text-on-surface">{l.recursoAfectado}</td>
                      <td className="font-mono text-xs text-text-secondary tabular-nums">
                        {l.idRegistroAfectado ? `#${l.idRegistroAfectado}` : 'N/A'}
                      </td>
                      <td className="whitespace-nowrap text-xs text-text-secondary tabular-nums">
                        {l.idUsuario ? `Usuario #${l.idUsuario}` : 'Sistema'}
                      </td>
                      <td className="font-mono text-xs text-text-secondary">{l.ipCliente || '—'}</td>
                      <td className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedLog(l)}
                          className="h-10 gap-1 text-xs sm:h-8"
                          aria-label={`Ver detalle del evento ${l.idAuditoria}`}
                        >
                          <Eye aria-hidden="true" />
                          Ver
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-3 border-t border-border px-4 py-4 sm:flex-row sm:px-6">
              <div className="text-xs text-text-secondary tabular-nums">
                Mostrando <span className="font-semibold text-on-surface">{safePage * PAGE_SIZE + 1}</span> a{' '}
                <span className="font-semibold text-on-surface">
                  {Math.min((safePage + 1) * PAGE_SIZE, filtered.length)}
                </span>{' '}
                de <span className="font-semibold text-on-surface">{filtered.length}</span> eventos
              </div>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="h-10 gap-1 px-3 text-xs sm:h-8"
                >
                  <ChevronLeft aria-hidden="true" />
                  Anterior
                </Button>
                <span className="px-3 text-xs font-medium text-text-secondary tabular-nums">
                  Página {safePage + 1} de {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="h-10 gap-1 px-3 text-xs sm:h-8"
                >
                  Siguiente
                  <ChevronRight aria-hidden="true" />
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <Modal
        open={!!selectedLog}
        onClose={() => setSelectedLog(null)}
        size="lg"
        title={selectedLog ? `Evento de auditoría #${selectedLog.idAuditoria}` : ''}
        footer={
          <Button variant="outline" onClick={() => setSelectedLog(null)}>
            Cerrar
          </Button>
        }
      >
        {selectedLog && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2 text-xs text-text-secondary">
              <Badge variant={ACTION_VARIANT[selectedLog.accion] || 'secondary'}>
                {formatAction(selectedLog.accion)}
              </Badge>
              <span className="tabular-nums">
                Registrado el {selectedLog.fechaHora ? formatDateTime(selectedLog.fechaHora) : '—'}
              </span>
            </div>
            <dl className="grid grid-cols-1 gap-4 rounded-lg border border-border bg-surface-container p-3 sm:grid-cols-2">
              <Dato label="Recurso">{selectedLog.recursoAfectado}</Dato>
              <Dato label="ID de registro afectado">
                <span className="font-mono tabular-nums">#{selectedLog.idRegistroAfectado || 'N/A'}</span>
              </Dato>
              <Dato label="Usuario ejecutor">
                {selectedLog.idUsuario ? `ID #${selectedLog.idUsuario}` : 'Sistema'}
              </Dato>
              <Dato label="Dirección IP">
                <span className="font-mono">{selectedLog.ipCliente || '—'}</span>
              </Dato>
            </dl>

            {selectedLog.detalles && (
              <div>
                <p className="mb-1.5 text-xs font-semibold text-text-muted">Cambios registrados</p>
                <pre className="max-h-72 overflow-auto rounded-lg border border-border bg-surface-container p-3 font-mono text-xs text-on-surface">
                  {typeof selectedLog.detalles === 'object'
                    ? JSON.stringify(selectedLog.detalles, null, 2)
                    : selectedLog.detalles}
                </pre>
              </div>
            )}
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
