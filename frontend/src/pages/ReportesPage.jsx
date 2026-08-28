import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';

const ACCION_COLORS = {
  INSERT: 'bg-green-100 text-green-800',
  UPDATE: 'bg-blue-100 text-blue-800',
  DELETE: 'bg-red-100 text-red-800',
  DENIED: 'bg-orange-100 text-orange-800',
};

export default function ReportesPage() {
  const [filtro, setFiltro] = useState({ tabla: '', accion: '', limite: 50 });
  const [tabActiva, setTabActiva] = useState('audit');

  const params = new URLSearchParams();
  if (filtro.tabla) params.set('tabla', filtro.tabla);
  if (filtro.accion) params.set('accion', filtro.accion);
  params.set('limite', filtro.limite);

  const { data: auditData, loading: auditLoading } = useFetch(
    () => api.get(`/audit?${params.toString()}`),
    [filtro]
  );
  const { data: statsData } = useFetch(() => api.get('/audit/stats'), []);

  const registros = auditData?.items || auditData?.data || [];
  const stats = statsData?.items || statsData?.data || [];

  return (
    <div className="space-y-6">
      <PageHeader title="Reportes y Auditoría" subtitle="Registros de actividad del sistema" />

      {/* Tabs */}
      <div className="flex gap-2 border-b pb-2">
        {[
          { id: 'audit', label: 'Registro de Auditoría', icon: 'history' },
          { id: 'stats', label: 'Estadísticas', icon: 'bar_chart' },
        ].map((t) => (
          <button
            key={t.id}
            onClick={() => setTabActiva(t.id)}
            className={`flex items-center gap-1 px-3 py-1.5 text-sm rounded-t font-medium transition-colors ${
              tabActiva === t.id
                ? 'border-b-2 border-primary text-foreground'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <span className="material-symbols-outlined text-base">{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* ── TAB: Auditoría ───────────────────────────── */}
      {tabActiva === 'audit' && (
        <>
          {/* Filtros */}
          <div className="flex gap-3 flex-wrap">
            <select
              className="border rounded px-3 py-1.5 text-sm"
              value={filtro.tabla}
              onChange={(e) => setFiltro((f) => ({ ...f, tabla: e.target.value }))}
            >
              <option value="">Todas las tablas</option>
              <option value="PROPIEDADES">Propiedades</option>
              <option value="PAGOS">Pagos</option>
              <option value="ASIGNACIONES">Asignaciones</option>
              <option value="MULTAS">Multas</option>
            </select>
            <select
              className="border rounded px-3 py-1.5 text-sm"
              value={filtro.accion}
              onChange={(e) => setFiltro((f) => ({ ...f, accion: e.target.value }))}
            >
              <option value="">Todas las acciones</option>
              <option value="INSERT">INSERT</option>
              <option value="UPDATE">UPDATE</option>
              <option value="DELETE">DELETE</option>
              <option value="DENIED">DENIED (acceso denegado)</option>
            </select>
            <select
              className="border rounded px-3 py-1.5 text-sm"
              value={filtro.limite}
              onChange={(e) => setFiltro((f) => ({ ...f, limite: Number(e.target.value) }))}
            >
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
              <option value={200}>200</option>
            </select>
          </div>

          <Card>
            <CardContent className="pt-6">
              {auditLoading ? (
                <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
              ) : registros.length === 0 ? (
                <p className="py-8 text-center text-muted-foreground">No hay registros de auditoría.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Fecha</TableHead>
                        <TableHead>Tabla</TableHead>
                        <TableHead>Acción</TableHead>
                        <TableHead>ID Reg.</TableHead>
                        <TableHead>Usuario</TableHead>
                        <TableHead>Estado Anterior</TableHead>
                        <TableHead>Estado Nuevo</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {registros.map((r, i) => (
                        <TableRow key={r.ID || r.id || i}>
                          <TableCell className="text-xs whitespace-nowrap">
                            {r.FECHA_ACCION || r.fechaAccion || '—'}
                          </TableCell>
                          <TableCell className="font-mono text-xs">{r.TABLA || r.tabla}</TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${ACCION_COLORS[r.ACCION || r.accion] || 'bg-gray-100'}`}>
                              {r.ACCION || r.accion}
                            </span>
                          </TableCell>
                          <TableCell className="font-mono text-xs">{r.ID_REGISTRO || r.idRegistro}</TableCell>
                          <TableCell className="text-xs">{r.USUARIO || r.usuario}</TableCell>
                          <TableCell className="text-xs max-w-[120px] truncate">
                            {r.ESTADO_ANTERIOR || r.estadoAnterior || '—'}
                          </TableCell>
                          <TableCell className="text-xs max-w-[120px] truncate">
                            {r.ESTADO_NUEVO || r.estadoNuevo || '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}

      {/* ── TAB: Estadísticas ──────────────────────────── */}
      {tabActiva === 'stats' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Resumen de Actividad</CardTitle>
          </CardHeader>
          <CardContent>
            {stats.length === 0 ? (
              <p className="py-4 text-center text-muted-foreground">Sin estadísticas disponibles.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tabla</TableHead>
                      <TableHead>Acción</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead>Primera</TableHead>
                      <TableHead>Última</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {stats.map((s, i) => (
                      <TableRow key={i}>
                        <TableCell className="font-mono text-sm">{s.TABLA || s.tabla}</TableCell>
                        <TableCell>
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${ACCION_COLORS[s.ACCION || s.accion] || 'bg-gray-100'}`}>
                            {s.ACCION || s.accion}
                          </span>
                        </TableCell>
                        <TableCell className="text-right font-bold">{Number(s.TOTAL || s.total).toLocaleString()}</TableCell>
                        <TableCell className="text-xs">{s.PRIMERA || s.primera || '—'}</TableCell>
                        <TableCell className="text-xs">{s.ULTIMA || s.ultima || '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
