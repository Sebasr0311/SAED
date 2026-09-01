import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

export default function SuperAdminAuditoriaPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/auditoria');
        setLogs(res?.data || res || []);
      } catch (err) {
        console.error(err);
        toast.error('Error al cargar pista de auditoría');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="border-b border-border pb-6">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Pista de Auditoría Global</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Registro inmutable de accesos, mutaciones administrativas y eventos de seguridad.
        </p>
      </div>

      <Card className="border-border/80">
        <CardHeader>
          <CardTitle className="text-base font-semibold">Eventos de Auditoría</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-16 w-full rounded-lg" />
              ))}
            </div>
          ) : logs.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <span className="material-symbols-outlined text-4xl mb-2">policy</span>
              <p>No hay eventos de auditoría recientes.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-muted-foreground font-medium">
                    <th className="py-3 px-4">Fecha y Hora</th>
                    <th className="py-3 px-4">Acción</th>
                    <th className="py-3 px-4">Recurso</th>
                    <th className="py-3 px-4">Categoría</th>
                    <th className="py-3 px-4">Severidad</th>
                    <th className="py-3 px-4">IP Origen</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {logs.map((log, i) => (
                    <tr key={i} className="hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-4 font-mono text-xs text-muted-foreground">
                        {log.FECHA_HORA || log.fechaHora || 'Hoy'}
                      </td>
                      <td className="py-3 px-4 font-semibold text-foreground">
                        {log.ACCION || log.accion}
                      </td>
                      <td className="py-3 px-4 text-xs font-mono">{log.RECURSO || log.recurso}</td>
                      <td className="py-3 px-4">{log.CATEGORIA || log.categoria}</td>
                      <td className="py-3 px-4">
                        <Badge variant={log.SEVERIDAD === 'CRITICAL' ? 'destructive' : 'outline'}>
                          {log.SEVERIDAD || log.severidad || 'INFO'}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 font-mono text-xs text-muted-foreground">
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
