import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { downloadReportFile, exportToExcel } from '../lib/exportUtils.js';
import { toast } from 'sonner';
import {
  Ban,
  BarChart3,
  Copy,
  CreditCard,
  FileDown,
  FileSpreadsheet,
  FileText,
  History,
  Plus,
  Receipt,
  RefreshCw,
  ScrollText,
  SlidersHorizontal,
  Wallet,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Button as IconButton } from '../components/ui/button.tsx';
import { Input as BareInput } from '../components/ui/input.tsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';

const ACCION_VARIANT = {
  INSERT: 'success',
  UPDATE: 'info',
  DELETE: 'destructive',
  DENIED: 'warning',
};

const SELECT_CLASS =
  'h-10 rounded-md border border-input bg-surface px-3 text-sm text-on-surface shadow-xs transition-[border-color,box-shadow] duration-150 hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 sm:h-9';

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

  const { data: auditData, loading: auditLoading, error: auditError, refetch: refetchAudit } = useFetch(
    () => api.get(`/audit?${params.toString()}`),
    [filtro]
  );
  const {
    data: statsData,
    loading: statsLoading,
    error: statsError,
    refetch: refetchStats,
  } = useFetch(() => api.get('/audit/stats'), []);

  // --- Business reports ---
  const [reporteFiltro, setReporteFiltro] = useState({ fechaInicio: '', fechaFin: '' });
  const reportParams = new URLSearchParams();
  if (reporteFiltro.fechaInicio) reportParams.set('fechaInicio', reporteFiltro.fechaInicio);
  if (reporteFiltro.fechaFin) reportParams.set('fechaFin', reporteFiltro.fechaFin);
  const reportQuery = reportParams.toString() ? `?${reportParams.toString()}` : '';

  const { data: morosaData, loading: morosaLoading, error: morosaError, refetch: refetchMorosa } = useFetch(
    () => api.get(`/reportes/cartera-morosa${reportQuery}`),
    [tabActiva === 'morosa', reporteFiltro.fechaInicio, reporteFiltro.fechaFin]
  );
  const { data: cuotasData, loading: cuotasLoading, error: cuotasError, refetch: refetchCuotasRep } = useFetch(
    () => api.get(`/reportes/ejecucion-cuotas${reportQuery}`),
    [tabActiva === 'cuotas', reporteFiltro.fechaInicio, reporteFiltro.fechaFin]
  );
  const { data: pagosData, loading: pagosLoading, error: pagosError, refetch: refetchPagosRep } = useFetch(
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
  const [desactivarTarget, setDesactivarTarget] = useState(null);

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
      const ext = { PDF: 'pdf', CSV: 'csv', XLSX: 'xlsx' }[formato.toUpperCase()] || 'pdf';
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
    if (!id) return;
    try {
      await api.delete(`/reportes/configurados/${id}`);
      toast.success('Configuración desactivada exitosamente');
      setRefreshConfigTrigger((v) => v + 1);
      setDesactivarTarget(null);
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
    <div className="flex flex-wrap items-center gap-1.5 sm:border-l sm:border-border sm:pl-3">
      <Button
        variant="outline"
        size="sm"
        disabled={exportando[`${tipo}-pdf`] || !hasData}
        onClick={() => handleExportPdf(tipo)}
        icon={<FileDown className="h-3.5 w-3.5" />}
      >
        {exportando[`${tipo}-pdf`] ? 'Generando…' : 'PDF'}
      </Button>
      <Button
        variant="outline"
        size="sm"
        disabled={exportando[`${tipo}-csv`] || !hasData}
        onClick={() => handleExportCsv(tipo)}
        icon={<FileText className="h-3.5 w-3.5" />}
      >
        {exportando[`${tipo}-csv`] ? 'Generando…' : 'CSV'}
      </Button>
      <Button
        variant="outline"
        size="sm"
        disabled={exportando[`${tipo}-excel`] || !hasData}
        onClick={() => handleExportExcel(tipo)}
        icon={<FileSpreadsheet className="h-3.5 w-3.5" />}
      >
        {exportando[`${tipo}-excel`] ? 'Generando…' : 'Excel'}
      </Button>
    </div>
  );

  const renderDateFilter = (prefix, desdeLabel) => (
    <div className="flex flex-wrap items-end gap-2">
      <div className="grid gap-1">
        <label htmlFor={`${prefix}-desde`} className="text-xs font-semibold text-text-secondary">
          {desdeLabel}
        </label>
        <BareInput
          id={`${prefix}-desde`}
          type="date"
          className="h-9 w-40 tabular-nums"
          value={reporteFiltro.fechaInicio}
          onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaInicio: e.target.value }))}
        />
      </div>
      <div className="grid gap-1">
        <label htmlFor={`${prefix}-hasta`} className="text-xs font-semibold text-text-secondary">
          Hasta
        </label>
        <BareInput
          id={`${prefix}-hasta`}
          type="date"
          className="h-9 w-40 tabular-nums"
          value={reporteFiltro.fechaFin}
          onChange={(e) => setReporteFiltro((prev) => ({ ...prev, fechaFin: e.target.value }))}
        />
      </div>
      {(reporteFiltro.fechaInicio || reporteFiltro.fechaFin) && (
        <Button variant="ghost" size="sm" onClick={() => setReporteFiltro({ fechaInicio: '', fechaFin: '' })}>
          Limpiar
        </Button>
      )}
    </div>
  );

  const retry = (fn) => () => fn().catch(() => {});

  const TABS = [
    { id: 'audit', label: 'Registro de auditoría', icon: History },
    { id: 'stats', label: 'Estadísticas', icon: BarChart3 },
    { id: 'morosa', label: 'Cartera morosa', icon: Wallet },
    { id: 'cuotas', label: 'Ejecución de cuotas', icon: Receipt },
    { id: 'pagos', label: 'Pagos recientes', icon: CreditCard },
    { id: 'configurados', label: 'Reportes guardados', icon: SlidersHorizontal },
    { id: 'historial', label: 'Historial de reportes', icon: ScrollText },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reportes y auditoría"
        subtitle="Actividad del sistema, reportes financieros exportables y plantillas guardadas"
      />

      <Tabs value={tabActiva} onValueChange={setTabActiva}>
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
          <TabsList>
            {TABS.map(({ id, label, icon: Icon }) => (
              <TabsTrigger key={id} value={id} className="gap-1.5">
                <Icon className="h-4 w-4" aria-hidden="true" />
                {label}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>

        {/* Audit log */}
        <TabsContent value="audit" className="mt-4 space-y-4">
          <div className="flex flex-wrap gap-2">
            <select
              aria-label="Filtrar por tabla"
              className={SELECT_CLASS}
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
              aria-label="Filtrar por acción"
              className={SELECT_CLASS}
              value={filtro.accion}
              onChange={(e) => setFiltro((f) => ({ ...f, accion: e.target.value }))}
            >
              <option value="">Todas las acciones</option>
              <option value="INSERT">Creación (INSERT)</option>
              <option value="UPDATE">Actualización (UPDATE)</option>
              <option value="DELETE">Eliminación (DELETE)</option>
              <option value="DENIED">Acceso denegado (DENIED)</option>
            </select>
            <select
              aria-label="Cantidad de registros"
              className={SELECT_CLASS}
              value={filtro.limite}
              onChange={(e) => setFiltro((f) => ({ ...f, limite: Number(e.target.value) }))}
            >
              <option value={25}>25 registros</option>
              <option value={50}>50 registros</option>
              <option value={100}>100 registros</option>
              <option value={200}>200 registros</option>
            </select>
          </div>

          <section className="table-container" aria-label="Registro de auditoría">
            {auditLoading ? (
              <SkeletonRows />
            ) : auditError ? (
              <div className="p-4">
                <ErrorState compact title="No se pudo cargar el registro de auditoría" onRetry={retry(refetchAudit)} />
              </div>
            ) : registros.length === 0 ? (
              <EmptyState icon={History} title="No hay registros de auditoría" subtitle="Ajuste los filtros para ampliar la búsqueda." />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Fecha</th>
                      <th scope="col">Tabla</th>
                      <th scope="col">Acción</th>
                      <th scope="col">ID registro</th>
                      <th scope="col">Usuario</th>
                      <th scope="col">Estado anterior</th>
                      <th scope="col">Estado nuevo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {registros.map((r, i) => (
                      <tr key={r.ID || r.id || i}>
                        <td className="whitespace-nowrap tabular-nums text-text-secondary">{r.FECHA_ACCION || r.fechaAccion || '—'}</td>
                        <td className="font-mono text-xs">{r.TABLA || r.tabla}</td>
                        <td><AccionBadge accion={r.ACCION || r.accion} /></td>
                        <td className="tabular-nums">{r.ID_REGISTRO || r.idRegistro}</td>
                        <td>{r.USUARIO || r.usuario}</td>
                        <td className="max-w-[140px] truncate text-text-secondary">{parseEstado(r.ESTADO_ANTERIOR || r.estadoAnterior)}</td>
                        <td className="max-w-[140px] truncate text-text-secondary">{parseEstado(r.ESTADO_NUEVO || r.estadoNuevo)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </TabsContent>

        {/* Stats */}
        <TabsContent value="stats" className="mt-4">
          <ReportPanel title="Resumen de actividad">
            {statsLoading ? (
              <SkeletonRows />
            ) : statsError ? (
              <div className="p-4">
                <ErrorState compact title="No se pudieron cargar las estadísticas" onRetry={retry(refetchStats)} />
              </div>
            ) : stats.length === 0 ? (
              <EmptyState icon={BarChart3} title="Sin estadísticas disponibles" subtitle="Aparecen cuando el sistema registra actividad." />
            ) : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Tabla</th>
                      <th scope="col">Acción</th>
                      <th scope="col" className="text-right">Total</th>
                      <th scope="col">Primera</th>
                      <th scope="col">Última</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.map((s, i) => (
                      <tr key={i}>
                        <td className="font-mono text-xs">{s.TABLA || s.tabla}</td>
                        <td><AccionBadge accion={s.ACCION || s.accion} /></td>
                        <td className="text-right font-bold tabular-nums">{Number(s.TOTAL || s.total).toLocaleString('es-CO')}</td>
                        <td className="whitespace-nowrap tabular-nums text-text-secondary">{s.PRIMERA || s.primera || '—'}</td>
                        <td className="whitespace-nowrap tabular-nums text-text-secondary">{s.ULTIMA || s.ultima || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>

        {/* Overdue receivables */}
        <TabsContent value="morosa" className="mt-4">
          <ReportPanel
            title="Cartera morosa"
            description="Unidades con saldo vencido"
            toolbar={
              <>
                {renderDateFilter('morosa', 'Vencimiento desde')}
                {renderExportButtons('morosa', morosa.length > 0)}
              </>
            }
          >
            {morosaError ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudo cargar el reporte de cartera morosa"
                  message={morosaError?.message}
                  onRetry={retry(refetchMorosa)}
                />
              </div>
            ) : morosaLoading ? (
              <SkeletonRows />
            ) : morosa.length === 0 ? (
              <EmptyState icon={Wallet} title="No hay cuotas vencidas" subtitle="Ninguna unidad registra saldo vencido para los filtros seleccionados." />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Unidad</th>
                      <th scope="col">Propiedad</th>
                      <th scope="col" className="text-right">Cuotas pendientes</th>
                      <th scope="col" className="text-right">Deuda total</th>
                      <th scope="col">Primer vencimiento</th>
                      <th scope="col">Último vencimiento</th>
                      <th scope="col" className="text-right">Días de mora</th>
                    </tr>
                  </thead>
                  <tbody>
                    {morosa.map((m, i) => (
                      <tr key={i}>
                        <td className="font-semibold">{m.unidad || m.UNIDAD}</td>
                        <td>{m.propiedad || m.PROPIEDAD}</td>
                        <td className="text-right tabular-nums">{Number(m.cuotasPendientes ?? m.CUOTAS_PENDIENTES ?? 0).toLocaleString('es-CO')}</td>
                        <td className="whitespace-nowrap text-right font-bold tabular-nums">{fmtCOP.format(Number(m.deudaTotal ?? m.DEUDA_TOTAL ?? 0))}</td>
                        <td className="whitespace-nowrap tabular-nums text-text-secondary">{m.primerVencimiento || m.PRIMER_VENCIMIENTO || '—'}</td>
                        <td className="whitespace-nowrap tabular-nums text-text-secondary">{m.ultimoVencimiento || m.ULTIMO_VENCIMIENTO || '—'}</td>
                        <td className="whitespace-nowrap text-right font-semibold tabular-nums">
                          {(m.diasMora ?? m.DIAS_MORA) != null ? `${m.diasMora ?? m.DIAS_MORA} días` : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t border-border bg-surface-container text-sm font-bold text-on-surface">
                      <th scope="row" colSpan={2} className="px-4 py-3 text-left">Total</th>
                      <td className="px-4 py-3 text-right tabular-nums">
                        {morosa.reduce((acc, m) => acc + Number(m.cuotasPendientes ?? m.CUOTAS_PENDIENTES ?? 0), 0).toLocaleString('es-CO')}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                        {fmtCOP.format(morosa.reduce((acc, m) => acc + Number(m.deudaTotal ?? m.DEUDA_TOTAL ?? 0), 0))}
                      </td>
                      <td colSpan={3} />
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>

        {/* Fee execution */}
        <TabsContent value="cuotas" className="mt-4">
          <ReportPanel
            title="Ejecución de cuotas por periodo"
            description="Facturado frente a recaudado"
            toolbar={
              <>
                {renderDateFilter('cuotas', 'Desde')}
                {renderExportButtons('cuotas', cuotas.length > 0)}
              </>
            }
          >
            {cuotasError ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudo cargar el reporte de ejecución de cuotas"
                  message={cuotasError?.message}
                  onRetry={retry(refetchCuotasRep)}
                />
              </div>
            ) : cuotasLoading ? (
              <SkeletonRows />
            ) : cuotas.length === 0 ? (
              <EmptyState icon={Receipt} title="Sin datos de ejecución" subtitle="No hay cuotas para el periodo seleccionado." />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Periodo</th>
                      <th scope="col" className="text-right">Cuotas</th>
                      <th scope="col" className="text-right">Pagadas</th>
                      <th scope="col" className="text-right">Pendientes</th>
                      <th scope="col" className="text-right">Facturado</th>
                      <th scope="col" className="text-right">Recaudado</th>
                      <th scope="col" className="w-44">Recaudo</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cuotas.map((c, i) => {
                      const totalFacturado = Number(c.totalFacturado ?? c.TOTAL_FACTURADO ?? 0);
                      const totalRecaudado = Number(c.totalRecaudado ?? c.TOTAL_RECAUDADO ?? 0);
                      const pct = (c.porcentajeRecaudado != null)
                        ? Number(c.porcentajeRecaudado)
                        : (totalFacturado > 0 ? (totalRecaudado / totalFacturado) * 100 : 0);
                      return (
                        <tr key={i}>
                          <td className="whitespace-nowrap font-semibold tabular-nums">{c.periodo || c.PERIODO}</td>
                          <td className="text-right tabular-nums">{Number(c.totalCuotas ?? c.TOTAL_CUOTAS ?? 0).toLocaleString('es-CO')}</td>
                          <td className="text-right tabular-nums">{Number(c.pagadas ?? c.PAGADAS ?? 0).toLocaleString('es-CO')}</td>
                          <td className="text-right tabular-nums">{Number(c.pendientes ?? c.PENDIENTES ?? 0).toLocaleString('es-CO')}</td>
                          <td className="whitespace-nowrap text-right tabular-nums">{fmtCOP.format(totalFacturado)}</td>
                          <td className="whitespace-nowrap text-right font-semibold tabular-nums">{fmtCOP.format(totalRecaudado)}</td>
                          <td>
                            <div className="flex items-center gap-2">
                              <div
                                className="h-2 flex-1 overflow-hidden rounded-[3px] bg-surface-dim"
                                role="img"
                                aria-label={`${pct.toFixed(1)} % recaudado`}
                              >
                                <div
                                  className="h-full rounded-[3px] bg-on-surface/70 transition-[width] duration-300 ease-out"
                                  style={{ width: `${Math.min(pct, 100)}%` }}
                                />
                              </div>
                              <span className="whitespace-nowrap text-xs font-semibold tabular-nums">{pct.toFixed(1)} %</span>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>

        {/* Recent payments */}
        <TabsContent value="pagos" className="mt-4">
          <ReportPanel
            title="Pagos registrados"
            toolbar={
              <>
                {renderDateFilter('pagos', 'Pago desde')}
                {renderExportButtons('pagos', pagos.length > 0)}
              </>
            }
          >
            {pagosError ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudo cargar el reporte de pagos"
                  message={pagosError?.message}
                  onRetry={retry(refetchPagosRep)}
                />
              </div>
            ) : pagosLoading ? (
              <SkeletonRows />
            ) : pagos.length === 0 ? (
              <EmptyState icon={CreditCard} title="No hay pagos registrados" subtitle="Ningún pago coincide con los filtros seleccionados." />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">ID</th>
                      <th scope="col">Unidad</th>
                      <th scope="col" className="text-right">Monto</th>
                      <th scope="col">Método</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Fecha</th>
                      <th scope="col">Referencia</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagos.map((p) => {
                      const est = p.estado || p.ESTADO;
                      return (
                        <tr key={p.idPago ?? p.ID_PAGO}>
                          <td className="tabular-nums text-text-muted">{p.idPago ?? p.ID_PAGO}</td>
                          <td className="font-semibold">{p.unidad || p.UNIDAD}</td>
                          <td className="whitespace-nowrap text-right font-bold tabular-nums">{fmtCOP.format(Number(p.montoTotal ?? p.MONTO_TOTAL ?? 0))}</td>
                          <td>{p.metodoPago || p.METODO_PAGO}</td>
                          <td>
                            <Badge
                              variant={
                                est === 'CONFIRMADO' || est === 'APROBADO'
                                  ? 'success'
                                  : est === 'PENDIENTE'
                                  ? 'warning'
                                  : 'secondary'
                              }
                            >
                              {est}
                            </Badge>
                          </td>
                          <td className="whitespace-nowrap tabular-nums text-text-secondary">
                            {(p.fechaPago || p.FECHA_PAGO || '-').toString().replace('T', ' ').substring(0, 19)}
                          </td>
                          <td className="max-w-[140px] truncate tabular-nums">{p.referenciaComprobante || p.REFERENCIA_COMPROBANTE || '—'}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>

        {/* Saved report templates */}
        <TabsContent value="configurados" className="mt-4">
          <ReportPanel
            title="Plantillas de reportes guardados"
            description="Reportes configurados sobre fuentes de datos autorizadas."
            toolbar={
              <Button size="sm" onClick={() => setModalNuevaConfig(true)} icon={<Plus className="h-3.5 w-3.5" />}>
                Nueva configuración
              </Button>
            }
          >
            {configsError ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudieron cargar los reportes configurados"
                  message={configsError?.message}
                  onRetry={() => setRefreshConfigTrigger((v) => v + 1)}
                />
              </div>
            ) : configsLoading ? (
              <SkeletonRows />
            ) : configurados.length === 0 ? (
              <EmptyState icon={SlidersHorizontal} title="No hay reportes configurados" subtitle="Cree una plantilla para generar el mismo reporte cuando lo necesite.">
                <div className="mt-3">
                  <Button size="sm" onClick={() => setModalNuevaConfig(true)}>Nueva configuración</Button>
                </div>
              </EmptyState>
            ) : (
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Nombre y código</th>
                      <th scope="col">Fuente</th>
                      <th scope="col">Formato</th>
                      <th scope="col">Ámbito</th>
                      <th scope="col">Estado</th>
                      <th scope="col" className="text-right">Generar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {configurados.map((c) => {
                      const isActivo = (c.estado || 'ACTIVO').toUpperCase() === 'ACTIVO';
                      const isGlobal = !c.idOrganizacion;
                      return (
                        <tr key={c.idReporteConfig}>
                          <td className="min-w-[12rem]">
                            <div className="font-semibold">{c.nombre}</div>
                            <div className="font-mono text-xs text-text-muted">{c.codigo}</div>
                            {c.descripcion && <div className="max-w-xs truncate text-xs text-text-muted">{c.descripcion}</div>}
                          </td>
                          <td>
                            <Badge variant="outline" className="font-mono">{c.consultaOrigenClave}</Badge>
                          </td>
                          <td className="font-mono text-xs font-semibold">{c.formatoSalidaDefecto || 'PDF'}</td>
                          <td>
                            <Badge variant={isGlobal ? 'info' : 'secondary'}>{isGlobal ? 'Estándar del sistema' : 'Organización'}</Badge>
                          </td>
                          <td>
                            <Badge variant={isActivo ? 'success' : 'secondary'}>{c.estado || 'ACTIVO'}</Badge>
                          </td>
                          <td className="text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={!isActivo || exportando[`gen-${c.idReporteConfig}-PDF`]}
                                onClick={() => handleGenerarConfigurado(c, 'PDF')}
                              >
                                {exportando[`gen-${c.idReporteConfig}-PDF`] ? 'Generando…' : 'PDF'}
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={!isActivo || exportando[`gen-${c.idReporteConfig}-CSV`]}
                                onClick={() => handleGenerarConfigurado(c, 'CSV')}
                              >
                                {exportando[`gen-${c.idReporteConfig}-CSV`] ? 'Generando…' : 'CSV'}
                              </Button>
                              {!isGlobal && isActivo && (
                                <IconButton
                                  variant="ghost"
                                  size="icon"
                                  className="h-10 w-10 text-destructive hover:text-destructive"
                                  onClick={() => setDesactivarTarget(c)}
                                  aria-label={`Desactivar plantilla ${c.nombre}`}
                                  title="Desactivar plantilla"
                                >
                                  <Ban aria-hidden="true" />
                                </IconButton>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>

        {/* Generated report history */}
        <TabsContent value="historial" className="mt-4">
          <ReportPanel
            title="Historial de reportes generados"
            description="Cada ejecución queda registrada de forma inmutable con su huella SHA-256."
            toolbar={
              <Button
                variant="outline"
                size="sm"
                onClick={() => setRefreshHistTrigger((v) => v + 1)}
                icon={<RefreshCw className="h-3.5 w-3.5" />}
              >
                Actualizar
              </Button>
            }
          >
            {histError ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudo cargar el historial de reportes"
                  message={histError?.message}
                  onRetry={() => setRefreshHistTrigger((v) => v + 1)}
                />
              </div>
            ) : histLoading ? (
              <SkeletonRows />
            ) : historial.length === 0 ? (
              <EmptyState icon={ScrollText} title="Sin reportes generados" subtitle="Los reportes que genere desde una plantilla aparecerán aquí." />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">ID</th>
                      <th scope="col">Fecha de ejecución</th>
                      <th scope="col">Formato</th>
                      <th scope="col">Archivo generado</th>
                      <th scope="col" className="text-right">Registros</th>
                      <th scope="col" className="text-right">Tiempo</th>
                      <th scope="col">Integridad SHA-256</th>
                    </tr>
                  </thead>
                  <tbody>
                    {historial.map((h) => {
                      const sha = h.archivoSha256 || '';
                      return (
                        <tr key={h.idHistorialReporte}>
                          <td className="tabular-nums text-text-muted">{h.idHistorialReporte}</td>
                          <td className="whitespace-nowrap tabular-nums">
                            {(h.fechaEjecucion || '-').toString().replace('T', ' ').substring(0, 19)}
                          </td>
                          <td>
                            <Badge variant="outline" className="font-mono">{h.formatoGenerado || 'PDF'}</Badge>
                          </td>
                          <td className="max-w-xs truncate font-mono text-xs" title={h.archivoGeneradoUrl}>
                            {h.archivoGeneradoUrl || '—'}
                          </td>
                          <td className="text-right tabular-nums">{Number(h.registrosProcesados ?? 0).toLocaleString('es-CO')}</td>
                          <td className="whitespace-nowrap text-right tabular-nums text-text-secondary">
                            {h.tiempoGeneracionMs ? `${h.tiempoGeneracionMs} ms` : '—'}
                          </td>
                          <td>
                            {sha ? (
                              <div className="flex items-center gap-1.5">
                                <span className="rounded-[3px] bg-surface-dim px-1.5 py-0.5 font-mono text-xs text-on-surface" title={sha}>
                                  {sha.substring(0, 10)}…{sha.substring(sha.length - 6)}
                                </span>
                                <IconButton
                                  variant="ghost"
                                  size="icon"
                                  className="h-9 w-9"
                                  onClick={() => handleCopiarHash(sha)}
                                  aria-label="Copiar SHA-256 completo"
                                  title="Copiar SHA-256"
                                >
                                  <Copy aria-hidden="true" />
                                </IconButton>
                                <Badge variant="secondary">Inmutable</Badge>
                              </div>
                            ) : (
                              <span className="text-text-muted">—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </ReportPanel>
        </TabsContent>
      </Tabs>

      <Modal open={modalNuevaConfig} onClose={() => setModalNuevaConfig(false)} title="Nueva configuración de reporte">
        <form onSubmit={handleCrearConfiguracion} className="space-y-4">
          <Input
            id="rep-nombre"
            label="Nombre de la configuración"
            required
            placeholder="Ej. Cartera morosa trimestral"
            value={nuevaConfigForm.nombre}
            onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, nombre: e.target.value })}
          />
          <Input
            id="rep-codigo"
            label="Código (opcional)"
            placeholder="Ej. REP_CARTERA_Q3"
            className="font-mono uppercase"
            value={nuevaConfigForm.codigo}
            onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, codigo: e.target.value.toUpperCase() })}
          />
          <div className="space-y-1">
            <Select
              id="rep-origen"
              label="Fuente de datos"
              required
              value={nuevaConfigForm.consultaOrigenClave}
              onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, consultaOrigenClave: e.target.value })}
            >
              <option value="CARTERA_MOROSA">Cartera morosa (CARTERA_MOROSA)</option>
              <option value="EJECUCION_CUOTAS">Ejecución de cuotas (EJECUCION_CUOTAS)</option>
              <option value="PAGOS_RECIENTES">Pagos recientes (PAGOS_RECIENTES)</option>
              <option value="EJECUCION_PRESUPUESTAL">Ejecución presupuestal (EJECUCION_PRESUPUESTAL)</option>
            </Select>
            <p className="text-xs text-text-muted">Solo se permiten fuentes autorizadas por el sistema.</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              id="rep-formato"
              label="Formato por defecto"
              value={nuevaConfigForm.formatoSalidaDefecto}
              onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, formatoSalidaDefecto: e.target.value })}
            >
              <option value="PDF">PDF</option>
              <option value="CSV">CSV</option>
              <option value="XLSX">Excel (XLSX)</option>
            </Select>
            <Input
              id="rep-filtros"
              label="Filtros JSON (opcional)"
              placeholder='{"size": 100}'
              className="font-mono"
              value={nuevaConfigForm.parametrosFiltroJson}
              onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, parametrosFiltroJson: e.target.value })}
            />
          </div>
          <Textarea
            id="rep-descripcion"
            label="Descripción (opcional)"
            rows={2}
            placeholder="Propósito de este reporte"
            value={nuevaConfigForm.descripcion}
            onChange={(e) => setNuevaConfigForm({ ...nuevaConfigForm, descripcion: e.target.value })}
          />
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button variant="outline" type="button" onClick={() => setModalNuevaConfig(false)} disabled={guardandoConfig}>
              Cancelar
            </Button>
            <Button type="submit" disabled={guardandoConfig} loading={guardandoConfig}>
              {guardandoConfig ? 'Guardando…' : 'Crear plantilla'}
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!desactivarTarget}
        onClose={() => setDesactivarTarget(null)}
        onConfirm={() => handleDesactivarConfigurado(desactivarTarget?.idReporteConfig)}
        title="Desactivar plantilla"
        message={`¿Está seguro de desactivar la plantilla "${desactivarTarget?.nombre || ''}"?`}
        confirmLabel="Desactivar"
        danger
      />
    </div>
  );
}

function SkeletonRows() {
  return (
    <div className="space-y-3 p-4" role="status" aria-label="Cargando">
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
    </div>
  );
}

function ReportPanel({ title, description, toolbar, children }) {
  return (
    <section className="table-container" aria-label={title}>
      <div className="flex flex-col gap-3 border-b border-border px-4 py-3.5 sm:px-5 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0">
          <h2 className="text-base font-bold text-on-surface">{title}</h2>
          {description && <p className="mt-0.5 text-xs text-text-muted">{description}</p>}
        </div>
        {toolbar && <div className="flex flex-wrap items-end gap-3">{toolbar}</div>}
      </div>
      {children}
    </section>
  );
}

function AccionBadge({ accion }) {
  const variant = ACCION_VARIANT[accion] || 'secondary';
  return <Badge variant={variant} className="font-mono">{accion}</Badge>;
}
