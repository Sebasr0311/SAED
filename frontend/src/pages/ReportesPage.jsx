import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { downloadReportFile, exportToExcel } from '../lib/exportUtils.js';
import { toast } from 'sonner';
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

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

function parseEstado(raw) {
  if (!raw) return '-';
  try {
    const obj = JSON.parse(raw);
    if (obj && typeof obj === 'object') return obj.estado || obj.ESTADO || JSON.stringify(obj);
  } catch { /* not JSON, use as-is */ }
  return String(raw);
}

export default function ReportesPage() {
  const [filtro, setFiltro] = useState({ tabla: '', accion: '', limite: 50 });
  const [tabActiva, setTabActiva] = useState('audit');
  const [exportando, setExportando] = useState({});

  const params = new URLSearchParams();
  if (filtro.tabla) params.set('tabla', filtro.tabla);
  if (filtro.accion) params.set('accion', filtro.accion);
  params.set('limite', filtro.limite);

  const { data: auditData, loading: auditLoading } = useFetch(
    () => api.get(`/audit?${params.toString()}`),
    [filtro]
  );
  const { data: statsData } = useFetch(() => api.get('/audit/stats'), []);

  // --- Business reports ---
  const [reporteFiltro, setReporteFiltro] = useState({ fechaInicio: '', fechaFin: '' });
  const reportParams = new URLSearchParams();
  if (reporteFiltro.fechaInicio) reportParams.set('fechaInicio', reporteFiltro.fechaInicio);
  if (reporteFiltro.fechaFin) reportParams.set('fechaFin', reporteFiltro.fechaFin);
  const reportQuery = reportParams.toString() ? `?${reportParams.toString()}` : '';

  const { data: morosaData, loading: morosaLoading, error: morosaError } = useFetch(
    () => api.get(`/reportes/cartera-morosa${reportQuery}`),
    [tabActiva === 'morosa', reporteFiltro.fechaInicio, reporteFiltro.fechaFin]
  );
  const { data: cuotasData, loading: cuotasLoading, error: cuotasError } = useFetch(
    () => api.get(`/reportes/ejecucion-cuotas${reportQuery}`),
    [tabActiva === 'cuotas', reporteFiltro.fechaInicio, reporteFiltro.fechaFin]
  );
  const { data: pagosData, loading: pagosLoading, error: pagosError } = useFetch(
    () => api.get(`/reportes/pagos-recientes${reportQuery}`),
    [tabActiva === 'pagos', reporteFiltro.fechaInicio, reporteFiltro.fechaFin]
  );

  // --- Block D: Reportes Configurables e Historial ---
  const [refreshConfigTrigger, setRefreshConfigTrigger] = useState(0);
  const [refreshHistTrigger, setRefreshHistTrigger] = useState(0);
  const [modalNuevaConfig, setModalNuevaConfig] = useState(false);
  const [nuevaConfigForm, setNuevaConfigForm] = useState({
    nombre: '',
    codigo: '',
    descripcion: '',
    consultaOrigenClave: 'CARTERA_MOROSA',
    formatoSalidaDefecto: 'PDF',
    parametrosFiltroJson: '',
  });
  const [guardandoConfig, setGuardandoConfig] = useState(false);

  const { data: configsData, loading: configsLoading, error: configsError } = useFetch(
    () => api.get('/reportes/configurados'),
    [tabActiva === 'configurados', refreshConfigTrigger]
  );

  const { data: histData, loading: histLoading, error: histError } = useFetch(
    () => api.get('/reportes/historial?page=0&size=50'),
    [tabActiva === 'historial', refreshHistTrigger]
  );

  const registros = auditData?.items || auditData?.data || [];
  const stats = statsData?.items || statsData?.data || [];
  const morosa = morosaData?.items || morosaData?.data || [];
  const cuotas = cuotasData?.items || cuotasData?.data || [];
  const pagos = pagosData?.items || pagosData?.data || [];
  const configurados = configsData?.items || configsData?.data || [];
  const historial = histData?.items || histData?.data || [];

  const handleGenerarConfigurado = async (config, formatoOverride = null) => {
    const formato = formatoOverride || config.formatoSalidaDefecto || 'PDF';
    const key = `gen-${config.idReporteConfig}-${formato}`;
    setExportando((prev) => ({ ...prev, [key]: true }));
    try {
      const ext = formato.toLowerCase() === 'pdf' ? 'pdf' : 'csv';
      const defaultFilename = `${(config.codigo || 'reporte').toLowerCase()}_${new Date().toISOString().slice(0, 10)}.${ext}`;
      await downloadReportFile(
        `/reportes/configurados/${config.idReporteConfig}/generar?formato=${formato}`,
        defaultFilename,
        { method: 'POST' }
      );
      toast.success(`Reporte '${config.nombre}' generado y descargado exitosamente`);
      setRefreshHistTrigger((v) => v + 1);
    } catch (err) {
      toast.error(err.message || 'Error al generar reporte configurado');
    } finally {
      setExportando((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleDesactivarConfigurado = async (id) => {
    if (!window.confirm('¿Está seguro de desactivar esta plantilla de reporte?')) return;
    try {
      await api.delete(`/reportes/configurados/${id}`);
      toast.success('Configuración desactivada exitosamente');
      setRefreshConfigTrigger((v) => v + 1);
    } catch (err) {
      toast.error(err.message || 'Error al desactivar plantilla');
    }
  };

  const handleCrearConfiguracion = async (e) => {
    e.preventDefault();
    if (!nuevaConfigForm.nombre.trim()) {
      toast.error('El nombre de la configuración es obligatorio');
      return;
    }
    setGuardandoConfig(true);
    try {
      const payload = {
        nombre: nuevaConfigForm.nombre.trim(),
        codigo: nuevaConfigForm.codigo.trim() || undefined,
        descripcion: nuevaConfigForm.descripcion.trim() || undefined,
        consultaOrigenClave: nuevaConfigForm.consultaOrigenClave,
        formatoSalidaDefecto: nuevaConfigForm.formatoSalidaDefecto,
        parametrosFiltroJson: nuevaConfigForm.parametrosFiltroJson.trim() || undefined,
      };
      await api.post('/reportes/configurados', payload);
      toast.success('Configuración de reporte creada exitosamente');
      setModalNuevaConfig(false);
      setNuevaConfigForm({
        nombre: '',
        codigo: '',
        descripcion: '',
        consultaOrigenClave: 'CARTERA_MOROSA',
        formatoSalidaDefecto: 'PDF',
        parametrosFiltroJson: '',
      });
      setRefreshConfigTrigger((v) => v + 1);
    } catch (err) {
      toast.error(err.message || 'Error al guardar configuración');
    } finally {
      setGuardandoConfig(false);
    }
  };

  const handleCopiarHash = (hash) => {
    if (!hash) return;
    navigator.clipboard.writeText(hash);
    toast.success('Hash SHA-256 copiado al portapapeles');
  };

  const handleExportPdf = async (tipo) => {
    const key = `${tipo}-pdf`;
    setExportando((prev) => ({ ...prev, [key]: true }));
    try {
      let endpoint = '';
      let defaultFilename = '';
      if (tipo === 'morosa') {
        const q = reportQuery ? `${reportQuery}&formato=PDF` : '?formato=PDF';
        endpoint = `/reportes/cartera-morosa${q}`;
        defaultFilename = `cartera_morosa_${new Date().toISOString().slice(0, 10)}.pdf`;
      } else if (tipo === 'cuotas') {
        const q = reportQuery ? `${reportQuery}&formato=PDF` : '?formato=PDF';
        endpoint = `/reportes/ejecucion-cuotas${q}`;
        defaultFilename = `ejecucion_cuotas_${new Date().toISOString().slice(0, 10)}.pdf`;
      } else if (tipo === 'pagos') {
        const q = reportQuery ? `${reportQuery}&formato=PDF` : '?formato=PDF';
        endpoint = `/reportes/pagos-recientes${q}`;
        defaultFilename = `pagos_recientes_${new Date().toISOString().slice(0, 10)}.pdf`;
      }
      await downloadReportFile(endpoint, defaultFilename);
      toast.success('Reporte PDF descargado con éxito');
    } catch (err) {
      toast.error(err.message || 'Error al exportar PDF');
    } finally {
      setExportando((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleExportCsv = async (tipo) => {
    const key = `${tipo}-csv`;
    setExportando((prev) => ({ ...prev, [key]: true }));
    try {
      let endpoint = '';
      let defaultFilename = '';
      if (tipo === 'morosa') {
        const q = reportQuery ? `${reportQuery}&formato=CSV` : '?formato=CSV';
        endpoint = `/reportes/cartera-morosa${q}`;
        defaultFilename = `cartera_morosa_${new Date().toISOString().slice(0, 10)}.csv`;
      } else if (tipo === 'cuotas') {
        const q = reportQuery ? `${reportQuery}&formato=CSV` : '?formato=CSV';
        endpoint = `/reportes/ejecucion-cuotas${q}`;
        defaultFilename = `ejecucion_cuotas_${new Date().toISOString().slice(0, 10)}.csv`;
      } else if (tipo === 'pagos') {
        const q = reportQuery ? `${reportQuery}&formato=CSV` : '?formato=CSV';
        endpoint = `/reportes/pagos-recientes${q}`;
        defaultFilename = `pagos_recientes_${new Date().toISOString().slice(0, 10)}.csv`;
      }
      await downloadReportFile(endpoint, defaultFilename);
      toast.success('Reporte CSV descargado con éxito');
    } catch (err) {
      toast.error(err.message || 'Error al exportar CSV');
    } finally {
      setExportando((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleExportExcel = async (tipo) => {
    const key = `${tipo}-excel`;
    setExportando((prev) => ({ ...prev, [key]: true }));
    try {
      const nowStr = new Date().toISOString().slice(0, 10);
      if (tipo === 'morosa') {
        if (!morosa || morosa.length === 0) {
          toast.info('No hay registros de cartera morosa para exportar');
          return;
        }
        await exportToExcel({
          filename: `cartera_morosa_${nowStr}.xlsx`,
          sheetName: 'Cartera Morosa',
          title: 'SAED 2.0 — Reporte de Cartera Morosa',
          subtitle: `Rango: ${reporteFiltro.fechaInicio || 'Inicio'} a ${reporteFiltro.fechaFin || 'Hoy'}`,
          columns: [
            { key: 'unidad', label: 'Unidad', type: 'string' },
            { key: 'propiedad', label: 'Propiedad', type: 'string' },
            { key: 'cuotasPendientes', label: 'Cuotas Pendientes', type: 'number' },
            { key: 'deudaTotal', label: 'Deuda Total', type: 'currency' },
            { key: 'primerVencimiento', label: 'Primer Vencimiento', type: 'date' },
            { key: 'ultimoVencimiento', label: 'Último Vencimiento', type: 'date' },
            { key: 'diasMora', label: 'Días Mora', type: 'number' },
          ],
          data: morosa.map((m) => ({
            unidad: m.unidad || m.UNIDAD || '',
            propiedad: m.propiedad || m.PROPIEDAD || '',
            cuotasPendientes: Number(m.cuotasPendientes ?? m.CUOTAS_PENDIENTES ?? 0),
            deudaTotal: Number(m.deudaTotal ?? m.DEUDA_TOTAL ?? 0),
            primerVencimiento: m.primerVencimiento || m.PRIMER_VENCIMIENTO || '',
            ultimoVencimiento: m.ultimoVencimiento || m.ULTIMO_VENCIMIENTO || '',
            diasMora: Number(m.diasMora ?? m.DIAS_MORA ?? 0),
          })),
          summaryRows: [
            {
              unidad: 'TOTAL GENERAL',
              cuotasPendientes: morosa.reduce((acc, m) => acc + Number(m.cuotasPendientes ?? m.CUOTAS_PENDIENTES ?? 0), 0),
              deudaTotal: morosa.reduce((acc, m) => acc + Number(m.deudaTotal ?? m.DEUDA_TOTAL ?? 0), 0),
            },
          ],
        });
      } else if (tipo === 'cuotas') {
        if (!cuotas || cuotas.length === 0) {
          toast.info('No hay registros de cuotas para exportar');
          return;
        }
        await exportToExcel({
          filename: `ejecucion_cuotas_${nowStr}.xlsx`,
          sheetName: 'Ejecución de Cuotas',
          title: 'SAED 2.0 — Reporte de Ejecución de Cuotas',
          subtitle: `Rango: ${reporteFiltro.fechaInicio || 'Inicio'} a ${reporteFiltro.fechaFin || 'Hoy'}`,
          columns: [
            { key: 'periodo', label: 'Período', type: 'string' },
            { key: 'totalCuotas', label: 'Total Cuotas', type: 'number' },
            { key: 'pagadas', label: 'Pagadas', type: 'number' },
            { key: 'pendientes', label: 'Pendientes', type: 'number' },
            { key: 'totalFacturado', label: 'Total Facturado', type: 'currency' },
            { key: 'totalPendiente', label: 'Total Pendiente', type: 'currency' },
            { key: 'totalRecaudado', label: 'Total Recaudado', type: 'currency' },
            { key: 'porcentajeRecaudado', label: '% Recaudado', type: 'percent' },
          ],
          data: cuotas.map((c) => ({
            periodo: c.periodo || c.PERIODO || '',
            totalCuotas: Number(c.totalCuotas ?? c.TOTAL_CUOTAS ?? 0),
            pagadas: Number(c.pagadas ?? c.PAGADAS ?? 0),
            pendientes: Number(c.pendientes ?? c.PENDIENTES ?? 0),
            totalFacturado: Number(c.totalFacturado ?? c.TOTAL_FACTURADO ?? 0),
            totalPendiente: Number(c.totalPendiente ?? c.TOTAL_PENDIENTE ?? 0),
            totalRecaudado: Number(c.totalRecaudado ?? c.TOTAL_RECAUDADO ?? 0),
            porcentajeRecaudado: Number(c.porcentajeRecaudado ?? (Number(c.totalFacturado ?? c.TOTAL_FACTURADO ?? 0) > 0 ? (Number(c.totalRecaudado ?? c.TOTAL_RECAUDADO ?? 0) / Number(c.totalFacturado ?? c.TOTAL_FACTURADO ?? 1)) * 100 : 0)),
          })),
        });
      } else if (tipo === 'pagos') {
        if (!pagos || pagos.length === 0) {
          toast.info('No hay registros de pagos para exportar');
          return;
        }
        await exportToExcel({
          filename: `pagos_recientes_${nowStr}.xlsx`,
          sheetName: 'Pagos Recientes',
          title: 'SAED 2.0 — Reporte de Pagos Recientes',
          subtitle: `Rango: ${reporteFiltro.fechaInicio || 'Inicio'} a ${reporteFiltro.fechaFin || 'Hoy'}`,
          columns: [
            { key: 'idPago', label: 'ID Pago', type: 'number' },
            { key: 'unidad', label: 'Unidad', type: 'string' },
            { key: 'montoTotal', label: 'Monto Total', type: 'currency' },
            { key: 'metodoPago', label: 'Método Pago', type: 'string' },
            { key: 'estado', label: 'Estado', type: 'string' },
            { key: 'fechaPago', label: 'Fecha Pago', type: 'date' },
            { key: 'referenciaComprobante', label: 'Referencia', type: 'string' },
          ],
          data: pagos.map((p) => ({
            idPago: p.idPago ?? p.ID_PAGO ?? '',
            unidad: p.unidad || p.UNIDAD || '',
            montoTotal: Number(p.montoTotal ?? p.MONTO_TOTAL ?? 0),
            metodoPago: p.metodoPago || p.METODO_PAGO || '',
            estado: p.estado || p.ESTADO || '',
            fechaPago: (p.fechaPago || p.FECHA_PAGO || '').toString().replace('T', ' ').substring(0, 19),
            referenciaComprobante: p.referenciaComprobante || p.REFERENCIA_COMPROBANTE || '',
          })),
          summaryRows: [
            {
              idPago: 'TOTAL',
              montoTotal: pagos.reduce((acc, p) => acc + Number(p.montoTotal ?? p.MONTO_TOTAL ?? 0), 0),
            },
          ],
        });
      }
      toast.success('Reporte Excel (.xlsx) generado con éxito');
    } catch (err) {
      toast.error(err.message || 'Error al generar archivo Excel');
    } finally {
      setExportando((prev) => ({ ...prev, [key]: false }));
    }
  };

  const renderExportButtons = (tipo, hasData) => (
    <div className="flex items-center gap-1.5 border-l pl-3 ml-2">
      <button
        type="button"
        disabled={exportando[`${tipo}-pdf`] || !hasData}
        onClick={() => handleExportPdf(tipo)}
        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded bg-red-50 text-red-700 hover:bg-red-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        title="Descargar PDF oficial"
      >
        <span className="material-symbols-outlined text-sm">picture_as_pdf</span>
        {exportando[`${tipo}-pdf`] ? 'Generando...' : 'PDF'}
      </button>

      <button
        type="button"
        disabled={exportando[`${tipo}-csv`] || !hasData}
        onClick={() => handleExportCsv(tipo)}
        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        title="Descargar CSV delimitado por comas (UTF-8 con BOM)"
      >
        <span className="material-symbols-outlined text-sm">description</span>
        {exportando[`${tipo}-csv`] ? 'Generando...' : 'CSV'}
      </button>

      <button
        type="button"
        disabled={exportando[`${tipo}-excel`] || !hasData}
        onClick={() => handleExportExcel(tipo)}
        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded bg-blue-50 text-blue-700 hover:bg-blue-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        title="Exportar archivo Excel (.xlsx) estilizado"
      >
        <span className="material-symbols-outlined text-sm">table_view</span>
        {exportando[`${tipo}-excel`] ? 'Generando...' : 'Excel'}
      </button>
    </div>
  );

  return (
    <div className="space-y-6">
      <PageHeader title="Reportes y Auditoría" subtitle="Registros de actividad del sistema" />

      {/* Tabs */}
      <div className="flex gap-2 border-b pb-2">
        {[
          { id: 'audit', label: 'Registro de Auditoría', icon: 'history' },
          { id: 'stats', label: 'Estadísticas', icon: 'bar_chart' },
          { id: 'morosa', label: 'Cartera Morosa', icon: 'money_off' },
          { id: 'cuotas', label: 'Ejecución de Cuotas', icon: 'assessment' },
          { id: 'pagos', label: 'Pagos Recientes', icon: 'payments' },
          { id: 'configurados', label: 'Reportes Guardados', icon: 'tune' },
          { id: 'historial', label: 'Historial de Reportes', icon: 'receipt_long' },
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

      {/* ======= TAB: Auditoría ======= */}
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
                            {r.FECHA_ACCION || r.fechaAccion || '-'}
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
                            {parseEstado(r.ESTADO_ANTERIOR || r.estadoAnterior)}
                          </TableCell>
                          <TableCell className="text-xs max-w-[120px] truncate">
                            {parseEstado(r.ESTADO_NUEVO || r.estadoNuevo)}
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

      {/* ======= TAB: Estadísticas ======= */}
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
                        <TableCell className="text-xs">{s.PRIMERA || s.primera || '-'}</TableCell>
                        <TableCell className="text-xs">{s.ULTIMA || s.ultima || '-'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ======= TAB: Cartera Morosa ======= */}
      {tabActiva === 'morosa' && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <CardTitle className="text-base">Cartera Morosa - Unidades con Saldo Vencido</CardTitle>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <label htmlFor="morosa-desde" className="text-muted-foreground font-medium">Vencimiento Desde:</label>
              <input
                id="morosa-desde"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaInicio}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaInicio: e.target.value }))}
              />
              <label htmlFor="morosa-hasta" className="text-muted-foreground font-medium">Hasta:</label>
              <input
                id="morosa-hasta"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaFin}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaFin: e.target.value }))}
              />
              {(reporteFiltro.fechaInicio || reporteFiltro.fechaFin) && (
                <button
                  type="button"
                  onClick={() => setReporteFiltro({ fechaInicio: '', fechaFin: '' })}
                  className="text-primary hover:underline font-medium"
                >
                  Limpiar
                </button>
              )}
              {renderExportButtons('morosa', morosa.length > 0)}
            </div>
          </CardHeader>
          <CardContent>
            {morosaError ? (
              <div className="p-4 bg-destructive/10 border border-destructive/30 rounded-lg text-sm text-destructive">
                Error al cargar el reporte de cartera morosa. {morosaError?.message || ''}
              </div>
            ) : morosaLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : morosa.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay cuotas vencidas registradas para los filtros seleccionados.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Unidad</TableHead>
                      <TableHead>Propiedad</TableHead>
                      <TableHead className="text-right">Cuotas Pendientes</TableHead>
                      <TableHead className="text-right">Deuda Total</TableHead>
                      <TableHead>Primer Vencimiento</TableHead>
                      <TableHead>Último Vencimiento</TableHead>
                      <TableHead className="text-right">Días Mora</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {morosa.map((m, i) => (
                      <TableRow key={i}>
                        <TableCell className="font-mono text-sm">{m.unidad || m.UNIDAD}</TableCell>
                        <TableCell>{m.propiedad || m.PROPIEDAD}</TableCell>
                        <TableCell className="text-right font-bold">{Number(m.cuotasPendientes ?? m.CUOTAS_PENDIENTES ?? 0).toLocaleString()}</TableCell>
                        <TableCell className="text-right font-bold text-red-600">{fmtCOP.format(Number(m.deudaTotal ?? m.DEUDA_TOTAL ?? 0))}</TableCell>
                        <TableCell className="text-xs">{m.primerVencimiento || m.PRIMER_VENCIMIENTO || '-'}</TableCell>
                        <TableCell className="text-xs">{m.ultimoVencimiento || m.ULTIMO_VENCIMIENTO || '-'}</TableCell>
                        <TableCell className="text-right font-medium text-amber-600 text-xs">
                          {(m.diasMora ?? m.DIAS_MORA) != null ? `${m.diasMora ?? m.DIAS_MORA} d` : '-'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ======= TAB: Ejecución de Cuotas ======= */}
      {tabActiva === 'cuotas' && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <CardTitle className="text-base">Ejecución de Cuotas por Período</CardTitle>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <label htmlFor="cuotas-desde" className="text-muted-foreground font-medium">Desde:</label>
              <input
                id="cuotas-desde"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaInicio}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaInicio: e.target.value }))}
              />
              <label htmlFor="cuotas-hasta" className="text-muted-foreground font-medium">Hasta:</label>
              <input
                id="cuotas-hasta"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaFin}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaFin: e.target.value }))}
              />
              {(reporteFiltro.fechaInicio || reporteFiltro.fechaFin) && (
                <button
                  type="button"
                  onClick={() => setReporteFiltro({ fechaInicio: '', fechaFin: '' })}
                  className="text-primary hover:underline font-medium"
                >
                  Limpiar
                </button>
              )}
              {renderExportButtons('cuotas', cuotas.length > 0)}
            </div>
          </CardHeader>
          <CardContent>
            {cuotasError ? (
              <div className="p-4 bg-destructive/10 border border-destructive/30 rounded-lg text-sm text-destructive">
                Error al cargar el reporte de ejecución de cuotas. {cuotasError?.message || ''}
              </div>
            ) : cuotasLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : cuotas.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay datos de ejecución de cuotas para el periodo seleccionado.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Período</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="text-right">Pagadas</TableHead>
                      <TableHead className="text-right">Pendientes</TableHead>
                      <TableHead className="text-right">Total Facturado</TableHead>
                      <TableHead className="text-right">Total Recaudado</TableHead>
                      <TableHead className="w-40">% Recaudado</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {cuotas.map((c, i) => {
                      const totalFacturado = Number(c.totalFacturado ?? c.TOTAL_FACTURADO ?? 0);
                      const totalRecaudado = Number(c.totalRecaudado ?? c.TOTAL_RECAUDADO ?? 0);
                      const pct = (c.porcentajeRecaudado != null)
                        ? Number(c.porcentajeRecaudado)
                        : (totalFacturado > 0 ? (totalRecaudado / totalFacturado) * 100 : 0);
                      return (
                        <TableRow key={i}>
                          <TableCell className="font-mono text-sm">{c.periodo || c.PERIODO}</TableCell>
                          <TableCell className="text-right">{Number(c.totalCuotas ?? c.TOTAL_CUOTAS ?? 0).toLocaleString()}</TableCell>
                          <TableCell className="text-right text-green-600">{Number(c.pagadas ?? c.PAGADAS ?? 0).toLocaleString()}</TableCell>
                          <TableCell className="text-right text-amber-600">{Number(c.pendientes ?? c.PENDIENTES ?? 0).toLocaleString()}</TableCell>
                          <TableCell className="text-right">{fmtCOP.format(totalFacturado)}</TableCell>
                          <TableCell className="text-right">{fmtCOP.format(totalRecaudado)}</TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                                <div
                                  className="h-full bg-green-500 rounded-full transition-all"
                                  style={{ width: `${Math.min(pct, 100)}%` }}
                                />
                              </div>
                              <span className="text-xs font-medium whitespace-nowrap">{pct.toFixed(1)}%</span>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ======= TAB: Pagos Recientes ======= */}
      {tabActiva === 'pagos' && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <CardTitle className="text-base">Historial de Pagos Registrados</CardTitle>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <label htmlFor="pagos-desde" className="text-muted-foreground font-medium">Pago Desde:</label>
              <input
                id="pagos-desde"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaInicio}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaInicio: e.target.value }))}
              />
              <label htmlFor="pagos-hasta" className="text-muted-foreground font-medium">Hasta:</label>
              <input
                id="pagos-hasta"
                type="date"
                className="border rounded px-2 py-1 text-xs bg-background text-foreground"
                value={reporteFiltro.fechaFin}
                onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaFin: e.target.value }))}
              />
              {(reporteFiltro.fechaInicio || reporteFiltro.fechaFin) && (
                <button
                  type="button"
                  onClick={() => setReporteFiltro({ fechaInicio: '', fechaFin: '' })}
                  className="text-primary hover:underline font-medium"
                >
                  Limpiar
                </button>
              )}
              {renderExportButtons('pagos', pagos.length > 0)}
            </div>
          </CardHeader>
          <CardContent>
            {pagosError ? (
              <div className="p-4 bg-destructive/10 border border-destructive/30 rounded-lg text-sm text-destructive">
                Error al cargar el reporte de pagos recientes. {pagosError?.message || ''}
              </div>
            ) : pagosLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : pagos.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay pagos registrados para los filtros seleccionados.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>ID</TableHead>
                      <TableHead>Unidad</TableHead>
                      <TableHead className="text-right">Monto</TableHead>
                      <TableHead>Método</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead>Fecha</TableHead>
                      <TableHead>Referencia</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pagos.map((p) => {
                      const est = p.estado || p.ESTADO;
                      return (
                        <TableRow key={p.idPago ?? p.ID_PAGO}>
                          <TableCell className="font-mono text-sm">{p.idPago ?? p.ID_PAGO}</TableCell>
                          <TableCell className="font-mono text-sm">{p.unidad || p.UNIDAD}</TableCell>
                          <TableCell className="text-right font-bold">{fmtCOP.format(Number(p.montoTotal ?? p.MONTO_TOTAL ?? 0))}</TableCell>
                          <TableCell>{p.metodoPago || p.METODO_PAGO}</TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                              est === 'CONFIRMADO' || est === 'APROBADO' ? 'bg-green-100 text-green-800' :
                              est === 'PENDIENTE' ? 'bg-amber-100 text-amber-800' :
                              'bg-gray-100 text-gray-800'
                            }`}>
                              {est}
                            </span>
                          </TableCell>
                          <TableCell className="text-xs whitespace-nowrap">{(p.fechaPago || p.FECHA_PAGO || '-').toString().replace('T', ' ').substring(0, 19)}</TableCell>
                          <TableCell className="text-xs max-w-[120px] truncate">{p.referenciaComprobante || p.REFERENCIA_COMPROBANTE || '-'}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ======= TAB: Reportes Guardados / Configurables ======= */}
      {tabActiva === 'configurados' && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-base">Plantillas y Reportes Guardados</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Configuraciones de reportes con fuentes allowlistadas e inmutabilidad de ejecución.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setModalNuevaConfig(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-sm">add_circle</span>
              Nueva Configuración
            </button>
          </CardHeader>
          <CardContent>
            {configsError ? (
              <div className="p-4 bg-destructive/10 border border-destructive/30 rounded-lg text-sm text-destructive">
                Error al cargar reportes configurados. {configsError?.message || ''}
              </div>
            ) : configsLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : configurados.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay reportes configurados registrados.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre / Código</TableHead>
                      <TableHead>Clave Origen</TableHead>
                      <TableHead>Formato Defecto</TableHead>
                      <TableHead>Ámbito</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead className="text-right">Acciones de Generación</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {configurados.map((c) => {
                      const isActivo = (c.estado || 'ACTIVO').toUpperCase() === 'ACTIVO';
                      const isGlobal = !c.idOrganizacion;
                      return (
                        <TableRow key={c.idReporteConfig}>
                          <TableCell>
                            <div className="font-medium text-foreground">{c.nombre}</div>
                            <div className="font-mono text-xs text-muted-foreground">{c.codigo}</div>
                            {c.descripcion && <div className="text-xs text-muted-foreground truncate max-w-xs">{c.descripcion}</div>}
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className="font-mono text-xs bg-slate-50 text-slate-800 border-slate-200">
                              {c.consultaOrigenClave}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <span className="font-mono text-xs font-semibold">{c.formatoSalidaDefecto || 'PDF'}</span>
                          </TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                              isGlobal ? 'bg-primary/10 text-primary font-semibold' : 'bg-secondary text-secondary-foreground'
                            }`}>
                              {isGlobal ? 'Estándar Sistema' : 'Organización'}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                              isActivo ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-semibold' : 'bg-muted text-muted-foreground'
                            }`}>
                              {c.estado || 'ACTIVO'}
                            </span>
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              <button
                                type="button"
                                disabled={!isActivo || exportando[`gen-${c.idReporteConfig}-PDF`]}
                                onClick={() => handleGenerarConfigurado(c, 'PDF')}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded bg-red-50 text-red-700 hover:bg-red-100 disabled:opacity-40 transition-colors"
                                title="Generar y descargar en PDF"
                              >
                                <span className="material-symbols-outlined text-xs">picture_as_pdf</span>
                                {exportando[`gen-${c.idReporteConfig}-PDF`] ? '...' : 'PDF'}
                              </button>
                              <button
                                type="button"
                                disabled={!isActivo || exportando[`gen-${c.idReporteConfig}-CSV`]}
                                onClick={() => handleGenerarConfigurado(c, 'CSV')}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded bg-emerald-50 text-emerald-700 hover:bg-emerald-100 disabled:opacity-40 transition-colors"
                                title="Generar y descargar en CSV"
                              >
                                <span className="material-symbols-outlined text-xs">description</span>
                                {exportando[`gen-${c.idReporteConfig}-CSV`] ? '...' : 'CSV'}
                              </button>
                              {!isGlobal && isActivo && (
                                <button
                                  type="button"
                                  onClick={() => handleDesactivarConfigurado(c.idReporteConfig)}
                                  className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded bg-muted text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors ml-1"
                                  title="Desactivar plantilla"
                                >
                                  <span className="material-symbols-outlined text-xs">block</span>
                                </button>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ======= TAB: Historial de Reportes ======= */}
      {tabActiva === 'historial' && (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-base">Historial y Auditoría de Reportes Generados</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Evidencia de ejecución inmutable protegida por trigger Oracle (<span className="font-mono">TRG_HISTREP_INMUTABLE</span>) y firma SHA-256.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setRefreshHistTrigger((v) => v + 1)}
              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded border border-border text-foreground hover:bg-muted transition-colors"
            >
              <span className="material-symbols-outlined text-sm">refresh</span>
              Actualizar
            </button>
          </CardHeader>
          <CardContent>
            {histError ? (
              <div className="p-4 bg-destructive/10 border border-destructive/30 rounded-lg text-sm text-destructive">
                Error al cargar historial de reportes. {histError?.message || ''}
              </div>
            ) : histLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            ) : historial.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay registros en el historial de reportes.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>ID</TableHead>
                      <TableHead>Fecha Ejecución</TableHead>
                      <TableHead>Formato</TableHead>
                      <TableHead>Archivo Generado</TableHead>
                      <TableHead className="text-right">Registros</TableHead>
                      <TableHead className="text-right">Tiempo</TableHead>
                      <TableHead>Integridad SHA-256</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {historial.map((h) => {
                      const sha = h.archivoSha256 || '';
                      return (
                        <TableRow key={h.idHistorialReporte}>
                          <TableCell className="font-mono text-xs">{h.idHistorialReporte}</TableCell>
                          <TableCell className="text-xs whitespace-nowrap">
                            {(h.fechaEjecucion || '-').toString().replace('T', ' ').substring(0, 19)}
                          </TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-semibold ${
                              (h.formatoGenerado || '').toUpperCase() === 'PDF' ? 'bg-red-100 text-red-800' :
                              (h.formatoGenerado || '').toUpperCase() === 'CSV' ? 'bg-emerald-100 text-emerald-800' :
                              'bg-blue-100 text-blue-800'
                            }`}>
                              {h.formatoGenerado || 'PDF'}
                            </span>
                          </TableCell>
                          <TableCell className="font-mono text-xs max-w-xs truncate" title={h.archivoGeneradoUrl}>
                            {h.archivoGeneradoUrl || '-'}
                          </TableCell>
                          <TableCell className="text-right text-xs font-medium">
                            {Number(h.registrosProcesados ?? 0).toLocaleString()}
                          </TableCell>
                          <TableCell className="text-right text-xs text-muted-foreground">
                            {h.tiempoGeneracionMs ? `${h.tiempoGeneracionMs} ms` : '-'}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-1.5">
                              <span className="font-mono text-xs bg-slate-100 px-1.5 py-0.5 rounded text-slate-700" title={sha}>
                                {sha.substring(0, 10)}...{sha.substring(sha.length - 6)}
                              </span>
                              <button
                                type="button"
                                onClick={() => handleCopiarHash(sha)}
                                className="p-1 text-muted-foreground hover:text-foreground rounded hover:bg-muted"
                                title="Copiar SHA-256 completo al portapapeles"
                              >
                                <span className="material-symbols-outlined text-xs">content_copy</span>
                              </button>
                              <span className="text-[10px] bg-slate-200 text-slate-700 px-1 rounded font-mono" title="Registro inmutable ORA-20060">
                                Inmutable
                              </span>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Modal: Crear Configuración */}
      {modalNuevaConfig && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-background rounded-lg shadow-xl max-w-md w-full border border-border overflow-hidden">
            <div className="px-6 py-4 border-b border-border flex items-center justify-between">
              <h3 className="font-semibold text-base text-foreground">Nueva Configuración de Reporte</h3>
              <button
                type="button"
                onClick={() => setModalNuevaConfig(false)}
                className="text-muted-foreground hover:text-foreground"
              >
                <span className="material-symbols-outlined text-lg">close</span>
              </button>
            </div>
            <form onSubmit={handleCrearConfiguracion} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Nombre de la Configuración <span className="text-destructive">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ej: Cartera Morosa Trimestral"
                  className="w-full border rounded px-3 py-1.5 text-sm bg-background text-foreground"
                  value={nuevaConfigForm.nombre}
                  onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, nombre: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Código Mnemotécnico (opcional)
                </label>
                <input
                  type="text"
                  placeholder="Ej: REP_CARTERA_Q3"
                  className="w-full border rounded px-3 py-1.5 text-sm font-mono bg-background text-foreground uppercase"
                  value={nuevaConfigForm.codigo}
                  onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, codigo: e.target.value.toUpperCase() })}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Consulta de Origen (Registro Seguro Allowlistado) <span className="text-destructive">*</span>
                </label>
                <select
                  className="w-full border rounded px-3 py-1.5 text-sm bg-background text-foreground font-mono"
                  value={nuevaConfigForm.consultaOrigenClave}
                  onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, consultaOrigenClave: e.target.value })}
                >
                  <option value="CARTERA_MOROSA">CARTERA_MOROSA — Cartera Morosa</option>
                  <option value="EJECUCION_CUOTAS">EJECUCION_CUOTAS — Ejecución de Cuotas</option>
                  <option value="PAGOS_RECIENTES">PAGOS_RECIENTES — Pagos Recientes</option>
                  <option value="EJECUCION_PRESUPUESTAL">EJECUCION_PRESUPUESTAL — Ejecución Presupuestal</option>
                </select>
                <p className="text-[11px] text-muted-foreground mt-1">
                  Solo se permiten claves vinculadas a implementaciones certificadas del motor Java.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Formato por Defecto
                  </label>
                  <select
                    className="w-full border rounded px-3 py-1.5 text-sm bg-background text-foreground"
                    value={nuevaConfigForm.formatoSalidaDefecto}
                    onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, formatoSalidaDefecto: e.target.value })}
                  >
                    <option value="PDF">PDF</option>
                    <option value="CSV">CSV</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Filtros JSON (opcional)
                  </label>
                  <input
                    type="text"
                    placeholder='{"size": 100}'
                    className="w-full border rounded px-3 py-1.5 text-sm font-mono bg-background text-foreground"
                    value={nuevaConfigForm.parametrosFiltroJson}
                    onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, parametrosFiltroJson: e.target.value })}
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Descripción (opcional)
                </label>
                <textarea
                  rows={2}
                  placeholder="Detalles sobre el propósito de este reporte..."
                  className="w-full border rounded px-3 py-1.5 text-sm bg-background text-foreground"
                  value={nuevaConfigForm.descripcion}
                  onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, descripcion: e.target.value })}
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-border">
                <button
                  type="button"
                  onClick={() => setModalNuevaConfig(false)}
                  className="px-3 py-1.5 text-xs font-medium rounded border border-border text-foreground hover:bg-muted"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={guardandoConfig}
                  className="px-3 py-1.5 text-xs font-medium rounded bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                >
                  {guardandoConfig ? 'Guardando...' : 'Crear Plantilla'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
