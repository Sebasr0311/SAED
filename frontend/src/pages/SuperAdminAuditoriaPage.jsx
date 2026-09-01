import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { toast } from 'sonner';

export default function SuperAdminAuditoriaPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/audit');
        const list = res?.data || res || [];
        setLogs(Array.isArray(list) ? list : []);
      } catch (err) {
        console.error(err);
        toast.error('Error al cargar pista de auditoría');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

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

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="border-b border-border pb-6">
        <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <span className="material-symbols-outlined text-primary text-2xl">shield</span>
          Pista de Auditoría Global
        </h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Registro inmutable (append-only) de accesos, mutaciones y eventos de seguridad en Oracle ATP.
        </p>
      </div>

      {/* Barra de Filtros */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
            search
          </span>
          <Input
            id="audit-search-input"
            type="search"
            placeholder="Buscar por acción, recurso, IP o resultado…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 text-sm"
          />
        </div>
        <div className="text-xs text-muted-foreground ml-auto font-medium">
          {filteredLogs.length} {filteredLogs.length === 1 ? 'evento registrado' : 'eventos registrados'}
        </div>
      </div>

      <Card className="border-border/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">Eventos de Auditoría</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : filteredLogs.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">policy</span>
              <p className="text-sm font-medium">
                {search ? 'No hay eventos que coincidan con la búsqueda.' : 'No hay eventos de auditoría registrados.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border text-muted-foreground text-xs font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">Fecha y Hora</th>
                    <th className="py-3 px-4">Acción</th>
                    <th className="py-3 px-4">Recurso / Entidad</th>
                    <th className="py-3 px-4 text-center">Resultado</th>
                    <th className="py-3 px-4">IP Origen</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredLogs.map((log, i) => (
                    <tr key={log.ID_LOG || log.id || i} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">
                        {log.FECHA_HORA || log.fechaHora || 'Reciente'}
                      </td>
                      <td className="py-3.5 px-4 font-semibold text-foreground font-mono text-xs">
                        {log.ACCION || log.accion}
                      </td>
                      <td className="py-3.5 px-4 text-xs font-mono text-foreground">
                        {log.ENTIDAD || log.entidad || log.RECURSO || log.recurso || 'PLATAFORMA'}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <Badge
                          variant={(log.RESULTADO || log.resultado) === 'FALLIDO' ? 'destructive' : 'default'}
                          className={
                            (log.RESULTADO || log.resultado) === 'EXITOSO' || !(log.RESULTADO || log.resultado)
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {log.RESULTADO || log.resultado || 'EXITOSO'}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">
                        {log.IP_ORIGEN || log.ipOrigen || '127.0.0.1'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
