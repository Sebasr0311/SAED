import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { ShieldCheck, Search, Eye, AlertCircle, Clock, ChevronLeft, ChevronRight } from 'lucide-react';

export default function OrgAuditoriaPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [actionFilter, setActionFilter] = useState('ALL');
  const [selectedLog, setSelectedLog] = useState(null);
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 10;

  useEffect(() => {
    async function load() {
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
    }
    load();
  }, []);

  const filtered = logs.filter((l) => {
    const term = searchTerm.toLowerCase();
    const matchesSearch =
      (l.recursoAfectado || '').toLowerCase().includes(term) ||
      (l.accion || '').toLowerCase().includes(term) ||
      (l.ipCliente || '').toLowerCase().includes(term) ||
      String(l.idUsuario || '').includes(term);
    const matchesAction = actionFilter === 'ALL' || l.accion === actionFilter;
    return matchesSearch && matchesAction;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const paginatedLogs = useMemo(() => {
    return filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
  }, [filtered, safePage]);

  const getActionBadgeClass = (action) => {
    switch (action) {
      case 'CREATE':
        return 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20';
      case 'UPDATE':
        return 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border-blue-500/20';
      case 'DELETE':
        return 'bg-red-500/15 text-red-700 dark:text-red-400 border-red-500/20';
      case 'STATUS_CHANGE':
        return 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border-amber-500/20';
      default:
        return 'bg-muted text-muted-foreground';
    }
  };

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Pista de Auditoría
            </h1>
            <Badge variant="outline" className="text-xs px-2.5 py-0.5 font-medium">
              {logs.length} eventos registrados
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Registro inmutable de acciones, mutaciones y eventos de seguridad en su organización.
          </p>
        </div>
      </div>

      {error && (
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-4 justify-between items-center">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por recurso, usuario o IP..."
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setPage(0);
            }}
            className="w-full pl-9 pr-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
            className="px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="ALL">Todas las Acciones</option>
            <option value="CREATE">CREATE</option>
            <option value="UPDATE">UPDATE</option>
            <option value="DELETE">DELETE</option>
            <option value="STATUS_CHANGE">STATUS_CHANGE</option>
          </select>
        </div>
      </div>

      {/* Audit Table */}
      <Card className="border border-border/80 shadow-sm">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-muted-foreground bg-muted/30 border-b border-border">
                <tr>
                  <th className="px-6 py-3 font-semibold">Fecha y Hora</th>
                  <th className="px-6 py-3 font-semibold">Acción</th>
                  <th className="px-6 py-3 font-semibold">Recurso</th>
                  <th className="px-6 py-3 font-semibold">ID Registro</th>
                  <th className="px-6 py-3 font-semibold">Usuario ID</th>
                  <th className="px-6 py-3 font-semibold">IP Origen</th>
                  <th className="px-6 py-3 font-semibold text-right">Detalle</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-muted-foreground">
                      No se encontraron registros de auditoría.
                    </td>
                  </tr>
                ) : (
                  paginatedLogs.map((l) => (
                    <tr key={l.idAuditoria} className="hover:bg-muted/20 transition-colors">
                      <td className="px-6 py-4 text-xs font-mono text-muted-foreground whitespace-nowrap">
                        <div className="flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5 text-muted-foreground" />
                          <span>
                            {l.fechaHora
                              ? new Date(l.fechaHora).toLocaleString('es-CO')
                              : 'Reciente'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <Badge variant="outline" className={`text-[11px] font-semibold ${getActionBadgeClass(l.accion)}`}>
                          {l.accion}
                        </Badge>
                      </td>
                      <td className="px-6 py-4 font-medium text-foreground text-xs">
                        {l.recursoAfectado}
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-muted-foreground">
                        {l.idRegistroAfectado ? `#${l.idRegistroAfectado}` : 'N/A'}
                      </td>
                      <td className="px-6 py-4 text-xs text-muted-foreground">
                        {l.idUsuario ? `Usuario #${l.idUsuario}` : 'Sistema'}
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-muted-foreground">
                        {l.ipCliente || '127.0.0.1'}
                      </td>
                      <td className="px-6 py-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedLog(l)}
                          className="text-xs gap-1"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Ver</span>
                        </Button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls (10 items per page) */}
          {filtered.length > 0 && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-6 py-4 border-t border-border">
              <div className="text-xs text-muted-foreground">
                Mostrando{' '}
                <span className="font-semibold text-foreground">
                  {safePage * PAGE_SIZE + 1}
                </span>{' '}
                a{' '}
                <span className="font-semibold text-foreground">
                  {Math.min((safePage + 1) * PAGE_SIZE, filtered.length)}
                </span>{' '}
                de{' '}
                <span className="font-semibold text-foreground">
                  {filtered.length}
                </span>{' '}
                eventos
              </div>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="h-8 px-2.5 text-xs gap-1"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                  <span>Anterior</span>
                </Button>
                <span className="px-3 text-xs font-medium text-muted-foreground">
                  Página {safePage + 1} de {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="h-8 px-2.5 text-xs gap-1"
                >
                  <span>Siguiente</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Detail Modal */}
      {selectedLog && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-2xl bg-background border-border shadow-xl">
            <CardHeader className="border-b border-border pb-4 flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-lg font-bold text-foreground flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-primary" />
                  <span>Detalle de Evento de Auditoría #{selectedLog.idAuditoria}</span>
                </CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Registrado el {new Date(selectedLog.fechaHora).toLocaleString('es-CO')}
                </p>
              </div>
              <Badge variant="outline" className={getActionBadgeClass(selectedLog.accion)}>
                {selectedLog.accion}
              </Badge>
            </CardHeader>
            <CardContent className="pt-6 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-xs">
                <div>
                  <span className="font-semibold text-muted-foreground uppercase block">Recurso</span>
                  <span className="text-foreground font-medium">{selectedLog.recursoAfectado}</span>
                </div>
                <div>
                  <span className="font-semibold text-muted-foreground uppercase block">ID Registro Afectado</span>
                  <span className="text-foreground font-mono font-medium">#{selectedLog.idRegistroAfectado || 'N/A'}</span>
                </div>
                <div>
                  <span className="font-semibold text-muted-foreground uppercase block">Usuario Ejecutor</span>
                  <span className="text-foreground font-medium">ID #{selectedLog.idUsuario || 'Sistema'}</span>
                </div>
                <div>
                  <span className="font-semibold text-muted-foreground uppercase block">Dirección IP</span>
                  <span className="text-foreground font-mono font-medium">{selectedLog.ipCliente || '127.0.0.1'}</span>
                </div>
              </div>

              {selectedLog.detalles && (
                <div>
                  <span className="text-xs font-semibold text-muted-foreground uppercase block mb-1.5">
                    Carga Útil / Cambios (Snapshot)
                  </span>
                  <pre className="p-3 bg-muted/40 rounded-lg text-xs font-mono text-foreground overflow-x-auto border border-border">
                    {typeof selectedLog.detalles === 'object'
                      ? JSON.stringify(selectedLog.detalles, null, 2)
                      : selectedLog.detalles}
                  </pre>
                </div>
              )}

              <div className="flex justify-end pt-4 border-t border-border">
                <Button variant="outline" onClick={() => setSelectedLog(null)}>
                  Cerrar
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
