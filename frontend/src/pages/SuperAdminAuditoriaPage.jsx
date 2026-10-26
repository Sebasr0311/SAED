import { useCallback, useEffect, useState, useMemo } from 'react';
import { ChevronLeft, ChevronRight, Search, ShieldCheck } from 'lucide-react';
import api from '../lib/api.js';
import { formatDateTime } from '../lib/utils.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Button } from '../components/ui/button.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { toast } from 'sonner';

function resultadoVariant(resultado) {
  if (resultado === 'FALLIDO' || resultado === 'ERROR' || resultado === 'DENEGADO') return 'destructive';
  if (!resultado || resultado === 'EXITOSO') return 'success';
  return 'secondary';
}

export default function SuperAdminAuditoriaPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 10;

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError(null);
      const res = await api.get('/audit');
      const list = res?.data || res || [];
      setLogs(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar pista de auditoría');
      setLoadError(err?.message || 'No fue posible consultar los eventos de auditoría.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filteredLogs = useMemo(() => {
    if (!search.trim()) return logs;
    const q = search.toLowerCase();
    return logs.filter(
      (l) =>
        ((l.ACCION || l.accion) && (l.ACCION || l.accion).toLowerCase().includes(q)) ||
        ((l.ENTIDAD || l.entidad || l.RECURSO || l.recurso) &&
          (l.ENTIDAD || l.entidad || l.RECURSO || l.recurso).toLowerCase().includes(q)) ||
        ((l.IP_ORIGEN || l.ipOrigen) && (l.IP_ORIGEN || l.ipOrigen).toLowerCase().includes(q)) ||
        ((l.RESULTADO || l.resultado) && (l.RESULTADO || l.resultado).toLowerCase().includes(q))
    );
  }, [logs, search]);

  const totalPages = Math.max(1, Math.ceil(filteredLogs.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const paginatedLogs = useMemo(() => {
    return filteredLogs.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
  }, [filteredLogs, safePage]);

  return (
    <PageContainer>
      <PageHeader
        title="Pista de auditoría global"
        subtitle="Registro de solo anexión con accesos, cambios y eventos de seguridad de toda la plataforma."
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:max-w-sm">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <Input
            id="audit-search-input"
            type="search"
            aria-label="Buscar eventos de auditoría"
            placeholder="Buscar por acción, recurso, IP o resultado…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="h-10 pl-9 text-sm"
          />
        </div>
        <div className="text-xs font-semibold text-text-secondary sm:ml-auto" aria-live="polite">
          <span className="app-numeral">{filteredLogs.length}</span>{' '}
          {filteredLogs.length === 1 ? 'evento registrado' : 'eventos registrados'}
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-[15px]">Eventos de auditoría</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2" role="status" aria-busy="true" aria-label="Cargando eventos">
              {[1, 2, 3, 4, 5].map((i) => (
                <Skeleton key={i} className="h-11 w-full" />
              ))}
            </div>
          ) : loadError ? (
            <ErrorState title="No se pudo cargar la auditoría" message={loadError} onRetry={load} />
          ) : filteredLogs.length === 0 ? (
            <EmptyState
              icon={ShieldCheck}
              title={search ? 'Sin coincidencias' : 'No hay eventos de auditoría registrados'}
              subtitle={
                search
                  ? 'Ningún evento coincide con la búsqueda. Prueba con otra acción, recurso o IP.'
                  : 'Los accesos y cambios relevantes aparecerán aquí a medida que ocurran.'
              }
            >
              {search && (
                <Button variant="outline" size="sm" className="mt-3" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              )}
            </EmptyState>
          ) : (
            <>
              <div className="table-container max-h-[640px] overflow-y-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Fecha y hora</th>
                      <th scope="col">Acción</th>
                      <th scope="col">Recurso / entidad</th>
                      <th scope="col" className="text-center">Resultado</th>
                      <th scope="col">IP de origen</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedLogs.map((log, i) => {
                      const resultado = log.RESULTADO || log.resultado;
                      const fecha = log.FECHA_HORA || log.fechaHora;
                      return (
                        <tr key={log.ID_LOG || log.id || i}>
                          <td className="whitespace-nowrap text-xs tabular-nums text-text-secondary">
                            {fecha ? formatDateTime(fecha) : '—'}
                          </td>
                          <td className="font-mono text-xs font-semibold text-on-surface">
                            {log.ACCION || log.accion || '—'}
                          </td>
                          <td className="font-mono text-xs text-on-surface">
                            {log.ENTIDAD || log.entidad || log.RECURSO || log.recurso || 'PLATAFORMA'}
                          </td>
                          <td className="text-center">
                            <Badge variant={resultadoVariant(resultado)}>{resultado || 'EXITOSO'}</Badge>
                          </td>
                          <td className="whitespace-nowrap font-mono text-xs text-text-secondary">
                            {log.IP_ORIGEN || log.ipOrigen || '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="mt-4 flex flex-col items-center justify-between gap-3 border-t border-border pt-4 sm:flex-row">
                <div className="text-xs text-text-secondary tabular-nums">
                  Mostrando{' '}
                  <span className="font-semibold text-on-surface">{safePage * PAGE_SIZE + 1}</span> a{' '}
                  <span className="font-semibold text-on-surface">
                    {Math.min((safePage + 1) * PAGE_SIZE, filteredLogs.length)}
                  </span>{' '}
                  de <span className="font-semibold text-on-surface">{filteredLogs.length}</span> eventos
                </div>
                <div className="flex items-center gap-1.5">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={safePage === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    className="h-10 gap-1 px-3 text-xs sm:h-8"
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
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
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  );
}
