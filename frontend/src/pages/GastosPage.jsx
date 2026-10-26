import { useState, useMemo, useEffect } from 'react';
import {
  DollarSign,
  Receipt,
  FileCheck,
  AlertCircle,
  Plus,
  Search,
  Eye,
  Download,
  Trash2,
  Edit3,
  Paperclip,
  FileText,
  Upload,
  X,
  History,
  ShieldCheck,
  RefreshCw,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import { api, BASE_URL } from '../lib/api.js';
import { TOKEN_KEY } from '../lib/storage.js';
import { cn } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Button as IconButton } from '../components/ui/button.tsx';
import { Input as BareInput } from '../components/ui/input.tsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../components/ui/dialog.tsx';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '../components/ui/sheet.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

const CATEGORIAS_SUGERIDAS = [
  'Mantenimiento General',
  'Servicios Públicos',
  'Seguridad y Vigilancia',
  'Aseo y Limpieza',
  'Jardinería y Zonas Verdes',
  'Reparaciones Locativas',
  'Honorarios Profesionales',
  'Seguros y Pólizas',
  'Administración',
  'Insumos y Papelería',
  'Otros Gastos Operativos',
];

const METODOS_PAGO = [
  { value: 'EFECTIVO', label: 'Efectivo' },
  { value: 'TRANSFERENCIA', label: 'Transferencia Bancaria' },
  { value: 'TARJETA', label: 'Tarjeta Débito / Crédito' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'CONSIGNACION', label: 'Consignación' },
];

const METODO_LABEL = Object.fromEntries(METODOS_PAGO.map((m) => [m.value, m.label]));

const ESTADOS_GASTO = [
  { value: 'REGISTRADO', label: 'Registrado', variant: 'info' },
  { value: 'PAGADO', label: 'Pagado', variant: 'success' },
  { value: 'ANULADO', label: 'Anulado', variant: 'outline' },
];

const SELECT_CLASS =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface shadow-xs transition-[border-color,box-shadow] duration-150 hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 sm:h-9';

const EMPTY_FORM = {
  fecha: new Date().toISOString().split('T')[0],
  categoria: '',
  beneficiario: '',
  proveedorNit: '',
  justificacion: '',
  monto: '',
  metodoPago: 'TRANSFERENCIA',
  estado: 'REGISTRADO',
  presupuestoId: '',
};

export default function GastosPage() {
  const { data: gastosRaw, loading, error, refetch } = useFetch(() => api.get('/gastos'), []);
  const { data: presupData } = useFetch(() => api.get('/presupuestos'), []);

  // UI States
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [soporteFile, setSoporteFile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [intentado, setIntentado] = useState(false);

  // Soporte replacement dialog
  const [soporteModalOpen, setSoporteModalOpen] = useState(false);
  const [targetGastoSoporte, setTargetGastoSoporte] = useState(null);
  const [nuevoSoporteFile, setNuevoSoporteFile] = useState(null);
  const [motivoReemplazo, setMotivoReemplazo] = useState('');
  const [subiendoSoporte, setSubiendoSoporte] = useState(false);

  // Detail Sheet (Drawer)
  const [detalleSheetOpen, setDetalleSheetOpen] = useState(false);
  const [gastoDetalle, setGastoDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);

  // Document Preview
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewFileInfo, setPreviewFileInfo] = useState({ name: '', type: '', id: null });

  // Delete target
  const [deleteTarget, setDeleteTarget] = useState(null);

  // Filters
  const [search, setSearch] = useState('');
  const [filtroPresupuesto, setFiltroPresupuesto] = useState('');
  const [filtroCategoria, setFiltroCategoria] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroSoporte, setFiltroSoporte] = useState('TODOS');

  const gastos = useMemo(() => {
    const raw = gastosRaw?.data || gastosRaw?.items || gastosRaw || [];
    return Array.isArray(raw) ? raw : [];
  }, [gastosRaw]);

  const presupuestos = useMemo(() => {
    const raw = presupData?.data || presupData?.items || presupData || [];
    return Array.isArray(raw) ? raw : [];
  }, [presupData]);

  // KPIs Calculations
  const kpis = useMemo(() => {
    let total = 0;
    let conSop = 0;
    let sinSop = 0;

    gastos.forEach((g) => {
      const monto = Number(g.monto || g.MONTO || 0);
      if ((g.estado || g.ESTADO) !== 'ANULADO') {
        total += monto;
      }
      const tieneSop = Boolean(g.facturaSoporteUrl || g.FACTURA_SOPORTE_URL);
      if (tieneSop) conSop++;
      else sinSop++;
    });

    const porcentajeSoporte = gastos.length > 0 ? Math.round((conSop / gastos.length) * 100) : 100;
    return {
      total,
      cantidad: gastos.length,
      conSoporte: conSop,
      sinSoporte: sinSop,
      porcentajeSoporte,
    };
  }, [gastos]);

  // Filtered Gastos
  const gastosFiltrados = useMemo(() => {
    return gastos.filter((g) => {
      const cat = (g.categoria || g.CATEGORIA || '').toLowerCase();
      const ben = (g.beneficiario || g.BENEFICIARIO || '').toLowerCase();
      const nit = (g.proveedorNit || g.PROVEEDOR_NIT || '').toLowerCase();
      const just = (g.justificacion || g.JUSTIFICACION || '').toLowerCase();
      const q = search.toLowerCase().trim();

      if (q && !cat.includes(q) && !ben.includes(q) && !nit.includes(q) && !just.includes(q)) {
        return false;
      }
      if (filtroPresupuesto) {
        const pId = String(g.idPresupuesto || g.ID_PRESUPUESTO || '');
        if (pId !== filtroPresupuesto) return false;
      }
      if (filtroCategoria) {
        if (cat !== filtroCategoria.toLowerCase()) return false;
      }
      if (filtroEstado) {
        const est = (g.estado || g.ESTADO || '').toUpperCase();
        if (est !== filtroEstado) return false;
      }
      if (filtroSoporte === 'CON_SOPORTE') {
        if (!g.facturaSoporteUrl && !g.FACTURA_SOPORTE_URL) return false;
      } else if (filtroSoporte === 'SIN_SOPORTE') {
        if (g.facturaSoporteUrl || g.FACTURA_SOPORTE_URL) return false;
      }
      return true;
    });
  }, [gastos, search, filtroPresupuesto, filtroCategoria, filtroEstado, filtroSoporte]);

  function limpiarFiltros() {
    setSearch('');
    setFiltroPresupuesto('');
    setFiltroCategoria('');
    setFiltroEstado('');
    setFiltroSoporte('TODOS');
  }

  // Abrir Modal Crear
  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setSoporteFile(null);
    setIntentado(false);
    setDialogOpen(true);
  }

  // Abrir Modal Editar
  function abrirEditar(g) {
    setEditando(g);
    setForm({
      fecha: g.fechaGasto || g.FECHA_GASTO || '',
      categoria: g.categoria || g.CATEGORIA || '',
      beneficiario: g.beneficiario || g.BENEFICIARIO || '',
      proveedorNit: g.proveedorNit || g.PROVEEDOR_NIT || '',
      justificacion: g.justificacion || g.JUSTIFICACION || '',
      monto: String(g.monto || g.MONTO || ''),
      metodoPago: g.metodoPago || g.METODO_PAGO || 'EFECTIVO',
      estado: g.estado || g.ESTADO || 'REGISTRADO',
      presupuestoId: String(g.idPresupuesto || g.ID_PRESUPUESTO || ''),
    });
    setSoporteFile(null);
    setIntentado(false);
    setDialogOpen(true);
  }

  // Guardar Gasto (Crear o Actualizar)
  async function guardar() {
    setIntentado(true);
    if (!form.categoria.trim() || !form.beneficiario.trim()) {
      toast.error('La categoría y el beneficiario son obligatorios');
      return;
    }
    const montoNum = Number(form.monto);
    if (!form.monto || Number.isNaN(montoNum) || montoNum <= 0) {
      toast.error('Ingrese un monto válido mayor a $0 COP');
      return;
    }

    setSaving(true);
    try {
      if (editando) {
        // Actualización JSON
        const id = editando.idGasto || editando.ID_GASTO || editando.id;
        const payload = {
          fechaGasto: form.fecha || undefined,
          categoria: form.categoria.trim(),
          beneficiario: form.beneficiario.trim(),
          proveedorNit: form.proveedorNit.trim() || null,
          justificacion: form.justificacion.trim() || null,
          monto: montoNum,
          metodoPago: form.metodoPago,
          estado: form.estado,
          idPresupuesto: form.presupuestoId ? Number(form.presupuestoId) : null,
        };
        await api.put(`/gastos/${id}`, payload);
        toast.success('Gasto operativo actualizado');
      } else {
        // Creación con FormData si se adjuntó archivo, o JSON si no
        if (soporteFile) {
          const formData = new FormData();
          formData.append('categoria', form.categoria.trim());
          formData.append('beneficiario', form.beneficiario.trim());
          formData.append('monto', String(montoNum));
          formData.append('metodoPago', form.metodoPago);
          formData.append('estado', form.estado);
          if (form.fecha) formData.append('fechaGasto', form.fecha);
          if (form.proveedorNit.trim()) formData.append('proveedorNit', form.proveedorNit.trim());
          if (form.justificacion.trim()) formData.append('justificacion', form.justificacion.trim());
          if (form.presupuestoId) formData.append('idPresupuesto', form.presupuestoId);
          formData.append('soporte', soporteFile);

          await api.postFormData('/gastos', formData);
        } else {
          const payload = {
            fechaGasto: form.fecha || undefined,
            categoria: form.categoria.trim(),
            beneficiario: form.beneficiario.trim(),
            proveedorNit: form.proveedorNit.trim() || null,
            justificacion: form.justificacion.trim() || null,
            monto: montoNum,
            metodoPago: form.metodoPago,
            estado: form.estado,
            idPresupuesto: form.presupuestoId ? Number(form.presupuestoId) : null,
          };
          await api.post('/gastos', payload);
        }
        toast.success('Gasto operativo registrado exitosamente');
      }

      setDialogOpen(false);
      setForm(EMPTY_FORM);
      setSoporteFile(null);
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al guardar el gasto');
    } finally {
      setSaving(false);
    }
  }

  // Abrir Modal de Soporte (Adjuntar / Reemplazar)
  function abrirModalSoporte(g) {
    setTargetGastoSoporte(g);
    setNuevoSoporteFile(null);
    setMotivoReemplazo('');
    setSoporteModalOpen(true);
  }

  // Subir / Reemplazar Soporte
  async function subirSoporte() {
    if (!nuevoSoporteFile) {
      toast.error('Seleccione un archivo de soporte (PDF, JPG, PNG)');
      return;
    }
    const id = targetGastoSoporte.idGasto || targetGastoSoporte.ID_GASTO || targetGastoSoporte.id;
    const tienePrevio = Boolean(targetGastoSoporte.facturaSoporteUrl || targetGastoSoporte.FACTURA_SOPORTE_URL);

    if (tienePrevio && !motivoReemplazo.trim()) {
      toast.error('Para reemplazar el soporte debe ingresar el motivo de la sustitución');
      return;
    }

    setSubiendoSoporte(true);
    try {
      const formData = new FormData();
      formData.append('soporte', nuevoSoporteFile);
      if (motivoReemplazo.trim()) {
        formData.append('motivoReemplazo', motivoReemplazo.trim());
      }

      await api.postFormData(`/gastos/${id}/soporte`, formData);
      toast.success(tienePrevio ? 'Soporte documental reemplazado y archivado en auditoría' : 'Soporte documental adjuntado');
      setSoporteModalOpen(false);
      setTargetGastoSoporte(null);
      setNuevoSoporteFile(null);
      setMotivoReemplazo('');
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al subir el soporte documental');
    } finally {
      setSubiendoSoporte(false);
    }
  }

  // Ver Detalle Completo en Sheet
  async function abrirDetalle(id) {
    setLoadingDetalle(true);
    setDetalleSheetOpen(true);
    try {
      const res = await api.get(`/gastos/${id}`);
      setGastoDetalle(res.data || res);
    } catch (err) {
      toast.error(err.message || 'No se pudo cargar el detalle del gasto');
      setDetalleSheetOpen(false);
    } finally {
      setLoadingDetalle(false);
    }
  }

  // Visualizar Documento (Fetch authenticated blob)
  async function verSoporte(id, nombre, mime) {
    setPreviewLoading(true);
    setPreviewFileInfo({ name: nombre || 'soporte_gasto', type: mime || 'application/pdf', id });
    setPreviewModalOpen(true);

    try {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const res = await fetch(`${BASE_URL}/gastos/${id}/soporte`, {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      });

      if (!res.ok) {
        throw new Error('No se pudo cargar el documento de soporte');
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      toast.error(err.message || 'Error al abrir visor de documento');
      setPreviewModalOpen(false);
    } finally {
      setPreviewLoading(false);
    }
  }

  // Descargar Documento
  async function descargarSoporte(id, nombre) {
    try {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const res = await fetch(`${BASE_URL}/gastos/${id}/soporte?download=true`, {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      });
      if (!res.ok) throw new Error('Error al descargar archivo');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = nombre || `soporte_gasto_${id}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success('Descarga completada');
    } catch (err) {
      toast.error(err.message || 'No se pudo descargar el archivo');
    }
  }

  // Cleanup blob URL on close
  useEffect(() => {
    if (!previewModalOpen && previewBlobUrl) {
      URL.revokeObjectURL(previewBlobUrl);
      setPreviewBlobUrl(null);
    }
  }, [previewModalOpen, previewBlobUrl]);

  // Eliminar Gasto
  async function confirmarEliminar() {
    if (!deleteTarget) return;
    const id = deleteTarget.idGasto || deleteTarget.ID_GASTO || deleteTarget.id;
    try {
      await api.del(`/gastos/${id}`);
      toast.success('Gasto eliminado exitosamente');
      setDeleteTarget(null);
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al eliminar el gasto');
    }
  }

  const hayFiltros = Boolean(search || filtroPresupuesto || filtroCategoria || filtroEstado || filtroSoporte !== 'TODOS');
  const tieneSoportePrevio = Boolean(targetGastoSoporte?.facturaSoporteUrl || targetGastoSoporte?.FACTURA_SOPORTE_URL);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Gestión de gastos"
        subtitle="Egresos operativos de la copropiedad con soporte documental y trazabilidad"
        action={
          <Button onClick={abrirCrear} icon={<Plus className="h-4 w-4" />}>
            Nuevo gasto
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total gastado"
          value={loading ? '…' : error ? '—' : fmtCOP.format(kpis.total)}
          subtitle="Egresos activos (sin anulados)"
          icon={DollarSign}
          variant="primary"
        />
        <MetricCard
          label="Registros"
          value={loading ? '…' : error ? '—' : kpis.cantidad}
          subtitle="Gastos en el sistema"
          icon={Receipt}
          variant="secondary"
        />
        <MetricCard
          label="Con soporte"
          value={loading ? '…' : error ? '—' : kpis.conSoporte}
          subtitle={error ? 'Sin datos' : `${kpis.porcentajeSoporte} % con factura o comprobante`}
          icon={FileCheck}
          variant="success"
        />
        <MetricCard
          label="Sin soporte"
          value={loading ? '…' : error ? '—' : kpis.sinSoporte}
          subtitle={error ? 'Sin datos' : kpis.sinSoporte > 0 ? 'Requieren adjuntar factura' : 'Al día con los soportes'}
          icon={AlertCircle}
          variant={kpis.sinSoporte > 0 ? 'warning' : 'secondary'}
        />
      </div>

      <section className="table-container" aria-label="Gastos registrados">
        <div className="flex flex-col gap-2 border-b border-border p-3 sm:p-4 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
              aria-hidden="true"
            />
            <BareInput
              type="text"
              aria-label="Buscar gastos"
              placeholder="Buscar por beneficiario, NIT, categoría o motivo"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3 lg:flex">
            <select
              aria-label="Filtrar por presupuesto"
              value={filtroPresupuesto}
              onChange={(e) => setFiltroPresupuesto(e.target.value)}
              className={cn(SELECT_CLASS, 'lg:w-52')}
            >
              <option value="">Presupuesto: todos</option>
              {presupuestos.map((p) => (
                <option key={p.idPresupuesto || p.ID_PRESUPUESTO || p.id} value={p.idPresupuesto || p.ID_PRESUPUESTO || p.id}>
                  {p.rubro || p.RUBRO}
                </option>
              ))}
            </select>
            <select
              aria-label="Filtrar por estado"
              value={filtroEstado}
              onChange={(e) => setFiltroEstado(e.target.value)}
              className={cn(SELECT_CLASS, 'lg:w-40')}
            >
              <option value="">Estado: todos</option>
              {ESTADOS_GASTO.map((est) => (
                <option key={est.value} value={est.value}>{est.label}</option>
              ))}
            </select>
            <select
              aria-label="Filtrar por soporte"
              value={filtroSoporte}
              onChange={(e) => setFiltroSoporte(e.target.value)}
              className={cn(SELECT_CLASS, 'lg:w-44')}
            >
              <option value="TODOS">Soporte: todos</option>
              <option value="CON_SOPORTE">Con documento</option>
              <option value="SIN_SOPORTE">Sin documento</option>
            </select>
          </div>
          {hayFiltros && (
            <Button variant="ghost" size="sm" onClick={limpiarFiltros} icon={<X className="h-4 w-4" />}>
              Limpiar
            </Button>
          )}
        </div>

        {loading ? (
          <div className="space-y-3 p-4" role="status" aria-label="Cargando gastos">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : error ? (
          <div className="p-4">
            <ErrorState
              compact
              title="No se pudieron cargar los gastos"
              message={error.message || 'Revise su conexión e intente de nuevo.'}
              onRetry={() => refetch().catch(() => {})}
            />
          </div>
        ) : gastosFiltrados.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title={hayFiltros ? 'No se encontraron gastos' : 'Aún no hay gastos registrados'}
            subtitle={
              hayFiltros
                ? 'Ningún egreso coincide con los filtros aplicados.'
                : 'Registre el primer egreso operativo con su factura o comprobante.'
            }
          >
            <div className="mt-3">
              {hayFiltros ? (
                <Button variant="outline" size="sm" onClick={limpiarFiltros}>Limpiar filtros</Button>
              ) : (
                <Button size="sm" onClick={abrirCrear}>Nuevo gasto</Button>
              )}
            </div>
          </EmptyState>
        ) : (
          <div className="max-h-[70vh] overflow-auto">
            <table className="data-table">
              <caption className="sr-only">Gastos operativos, valores en pesos colombianos</caption>
              <thead>
                <tr>
                  <th scope="col" className="w-28">Fecha</th>
                  <th scope="col">Beneficiario</th>
                  <th scope="col">Categoría</th>
                  <th scope="col">Presupuesto</th>
                  <th scope="col">Método</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Soporte</th>
                  <th scope="col" className="text-right">Monto</th>
                  <th scope="col" className="text-right"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {gastosFiltrados.map((g) => {
                  const id = g.idGasto || g.ID_GASTO || g.id;
                  const fecha = g.fechaGasto || g.FECHA_GASTO || '—';
                  const ben = g.beneficiario || g.BENEFICIARIO || '—';
                  const nit = g.proveedorNit || g.PROVEEDOR_NIT;
                  const cat = g.categoria || g.CATEGORIA || '—';
                  const rubro = g.rubroPresupuesto || g.RUBRO_PRESUPUESTO;
                  const metodo = g.metodoPago || g.METODO_PAGO || 'EFECTIVO';
                  const estado = (g.estado || g.ESTADO || 'REGISTRADO').toUpperCase();
                  const monto = Number(g.monto || g.MONTO || 0);
                  const tieneSoporte = Boolean(g.facturaSoporteUrl || g.FACTURA_SOPORTE_URL);
                  const origName = g.archivoNombreOrig || g.ARCHIVO_NOMBRE_ORIG;
                  const mime = g.archivoMimeType || g.ARCHIVO_MIME_TYPE;
                  const estadoConfig = ESTADOS_GASTO.find((e) => e.value === estado) || {
                    label: estado,
                    variant: 'secondary',
                  };
                  const anulado = estado === 'ANULADO';

                  return (
                    <tr key={id}>
                      <td className="whitespace-nowrap tabular-nums text-text-secondary">{fecha}</td>
                      <td className="min-w-[11rem]">
                        <div className="font-semibold leading-tight">{ben}</div>
                        {nit && <div className="mt-0.5 text-xs tabular-nums text-text-muted">NIT {nit}</div>}
                      </td>
                      <td className="min-w-[9rem]">{cat}</td>
                      <td className="min-w-[8rem] text-text-secondary">
                        {rubro || <span className="text-text-muted">Sin asociar</span>}
                      </td>
                      <td className="whitespace-nowrap text-text-secondary">{METODO_LABEL[metodo] || metodo}</td>
                      <td>
                        <Badge variant={estadoConfig.variant}>{estadoConfig.label}</Badge>
                      </td>
                      <td>
                        {tieneSoporte ? (
                          <div className="flex items-center gap-1">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => verSoporte(id, origName, mime)}
                              icon={<FileText className="h-3.5 w-3.5" />}
                              className="max-w-[11rem]"
                            >
                              <span className="truncate">{origName || 'Ver factura'}</span>
                            </Button>
                            <IconButton
                              variant="ghost"
                              size="icon"
                              className="h-10 w-10"
                              onClick={() => abrirModalSoporte(g)}
                              aria-label={`Reemplazar soporte del gasto ${id}`}
                              title="Reemplazar soporte"
                            >
                              <RefreshCw aria-hidden="true" />
                            </IconButton>
                          </div>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => abrirModalSoporte(g)}
                            icon={<Upload className="h-3.5 w-3.5" />}
                            className="text-tint-amber-fg"
                          >
                            Adjuntar
                          </Button>
                        )}
                      </td>
                      <td
                        className={cn(
                          'whitespace-nowrap text-right font-bold tabular-nums',
                          anulado && 'text-text-muted line-through'
                        )}
                      >
                        {fmtCOP.format(monto)}
                      </td>
                      <td className="whitespace-nowrap text-right">
                        <IconButton
                          variant="ghost"
                          size="icon"
                          className="h-10 w-10"
                          onClick={() => abrirDetalle(id)}
                          aria-label={`Ver detalle del gasto ${id}`}
                          title="Ver detalle y trazabilidad"
                        >
                          <Eye aria-hidden="true" />
                        </IconButton>
                        <IconButton
                          variant="ghost"
                          size="icon"
                          className="h-10 w-10"
                          onClick={() => abrirEditar(g)}
                          aria-label={`Editar el gasto ${id}`}
                          title="Editar"
                        >
                          <Edit3 aria-hidden="true" />
                        </IconButton>
                        <IconButton
                          variant="ghost"
                          size="icon"
                          className="h-10 w-10 text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(g)}
                          aria-label={`Eliminar el gasto ${id}`}
                          title="Eliminar"
                        >
                          <Trash2 aria-hidden="true" />
                        </IconButton>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Create / edit expense */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar gasto operativo' : 'Registrar nuevo gasto'}</DialogTitle>
            <DialogDescription>
              {editando
                ? 'Actualice los datos del gasto. Los cambios quedan registrados en auditoría.'
                : 'Ingrese los datos del egreso y adjunte la factura o comprobante (PDF, JPG o PNG).'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Input
                id="gasto-fecha"
                label="Fecha del gasto"
                required
                type="date"
                value={form.fecha}
                onChange={(e) => setForm((f) => ({ ...f, fecha: e.target.value }))}
              />
              <Input
                id="gasto-monto"
                label="Monto (COP)"
                required
                type="number"
                step="1"
                min="1"
                placeholder="0"
                className="text-right font-semibold tabular-nums"
                value={form.monto}
                onChange={(e) => setForm((f) => ({ ...f, monto: e.target.value }))}
                error={intentado && (!form.monto || !(Number(form.monto) > 0)) ? 'Ingrese un monto mayor a 0' : undefined}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <Input
                  id="gasto-categoria"
                  label="Categoría del gasto"
                  required
                  list="categorias-list"
                  placeholder="Ej. Mantenimiento general"
                  value={form.categoria}
                  onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
                  error={intentado && !form.categoria.trim() ? 'La categoría es obligatoria' : undefined}
                />
                <datalist id="categorias-list">
                  {CATEGORIAS_SUGERIDAS.map((c) => (
                    <option key={c} value={c} />
                  ))}
                </datalist>
              </div>
              <Select
                id="gasto-presupuesto"
                label="Presupuesto asociado"
                value={form.presupuestoId}
                onChange={(e) => setForm((f) => ({ ...f, presupuestoId: e.target.value }))}
              >
                <option value="">Sin asociar a presupuesto</option>
                {presupuestos.map((p) => (
                  <option key={p.idPresupuesto || p.ID_PRESUPUESTO || p.id} value={p.idPresupuesto || p.ID_PRESUPUESTO || p.id}>
                    {p.rubro || p.RUBRO}
                  </option>
                ))}
              </Select>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="sm:col-span-2">
                <Input
                  id="gasto-beneficiario"
                  label="Beneficiario o proveedor"
                  required
                  placeholder="Razón social o nombre completo"
                  value={form.beneficiario}
                  onChange={(e) => setForm((f) => ({ ...f, beneficiario: e.target.value }))}
                  error={intentado && !form.beneficiario.trim() ? 'El beneficiario es obligatorio' : undefined}
                />
              </div>
              <Input
                id="gasto-nit"
                label="NIT o cédula"
                placeholder="Ej. 900.123.456-7"
                className="tabular-nums"
                value={form.proveedorNit}
                onChange={(e) => setForm((f) => ({ ...f, proveedorNit: e.target.value }))}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Select
                id="gasto-metodo"
                label="Método de pago"
                value={form.metodoPago}
                onChange={(e) => setForm((f) => ({ ...f, metodoPago: e.target.value }))}
              >
                {METODOS_PAGO.map((m) => (
                  <option key={m.value} value={m.value}>{m.label}</option>
                ))}
              </Select>
              <Select
                id="gasto-estado"
                label="Estado del gasto"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS_GASTO.map((e) => (
                  <option key={e.value} value={e.value}>{e.label}</option>
                ))}
              </Select>
            </div>

            <div className="space-y-1">
              <Textarea
                id="gasto-justificacion"
                label="Justificación o motivo del gasto"
                rows={2}
                placeholder="Ej. Mantenimiento preventivo de las bombas de agua según contrato trimestral"
                value={form.justificacion}
                maxLength={500}
                onChange={(e) => setForm((f) => ({ ...f, justificacion: e.target.value }))}
              />
              <p className="text-right text-xs tabular-nums text-text-muted">
                {form.justificacion.length}/500 caracteres
              </p>
            </div>

            {!editando && (
              <div className="space-y-2 border-t border-border-subtle pt-4">
                <p className="flex items-center gap-1.5 text-sm font-semibold text-on-surface">
                  <Paperclip className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  Factura o documento soporte (PDF, JPG o PNG, máximo 10 MB)
                </p>
                <div className="rounded-md border-[1.5px] border-dashed border-keyline/40 bg-surface-container p-4 text-center">
                  {soporteFile ? (
                    <div className="flex items-center justify-between gap-2 rounded-md border border-border bg-surface p-2 text-sm">
                      <div className="flex min-w-0 items-center gap-2">
                        <FileText className="h-4 w-4 shrink-0 text-tint-green-fg" aria-hidden="true" />
                        <span className="truncate font-medium">{soporteFile.name}</span>
                        <span className="whitespace-nowrap text-xs tabular-nums text-text-muted">
                          ({(soporteFile.size / 1024).toFixed(1)} KB)
                        </span>
                      </div>
                      <IconButton
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-10 w-10 text-destructive hover:text-destructive"
                        onClick={() => setSoporteFile(null)}
                        aria-label="Quitar archivo seleccionado"
                      >
                        <X aria-hidden="true" />
                      </IconButton>
                    </div>
                  ) : (
                    <div>
                      <input
                        type="file"
                        id="soporte-file-input"
                        accept=".pdf,.jpg,.jpeg,.png"
                        className="sr-only"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) {
                            if (file.size > 10 * 1024 * 1024) {
                              toast.error('El archivo no puede exceder 10 MB');
                              return;
                            }
                            setSoporteFile(file);
                          }
                        }}
                      />
                      <label
                        htmlFor="soporte-file-input"
                        className="inline-flex min-h-11 cursor-pointer flex-col items-center gap-1 rounded-md px-3 py-2 text-xs text-text-secondary hover:text-on-surface"
                      >
                        <Upload className="mb-1 h-6 w-6 text-text-muted" aria-hidden="true" />
                        <span className="font-semibold text-primary-text">Seleccionar archivo</span>
                        <span>PDF, JPG o PNG hasta 10 MB</span>
                      </label>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={guardar} disabled={saving} loading={saving}>
              {saving ? 'Guardando…' : editando ? 'Guardar cambios' : 'Registrar gasto'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Attach or replace supporting document */}
      <Dialog open={soporteModalOpen} onOpenChange={setSoporteModalOpen}>
        <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{tieneSoportePrevio ? 'Reemplazar soporte documental' : 'Adjuntar soporte documental'}</DialogTitle>
            <DialogDescription>
              {tieneSoportePrevio
                ? 'El archivo anterior queda archivado en el historial de auditoría.'
                : 'Suba la factura o comprobante que respalda este egreso.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <dl className="grid grid-cols-1 gap-1 rounded-md border border-border bg-surface-container p-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-text-secondary">Gasto</dt>
                <dd className="font-semibold tabular-nums">#{targetGastoSoporte?.idGasto || targetGastoSoporte?.ID_GASTO}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-text-secondary">Beneficiario</dt>
                <dd className="text-right font-semibold">{targetGastoSoporte?.beneficiario || targetGastoSoporte?.BENEFICIARIO}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-text-secondary">Monto</dt>
                <dd className="font-semibold tabular-nums">
                  {fmtCOP.format(Number(targetGastoSoporte?.monto || targetGastoSoporte?.MONTO || 0))}
                </dd>
              </div>
            </dl>

            <div className="grid gap-1.5">
              <label htmlFor="gasto-soporte-nuevo" className="text-sm font-semibold text-on-surface">
                Archivo de soporte <span className="text-destructive" aria-hidden="true">*</span>
              </label>
              <input
                id="gasto-soporte-nuevo"
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    if (file.size > 10 * 1024 * 1024) {
                      toast.error('El archivo no puede exceder 10 MB');
                      return;
                    }
                    setNuevoSoporteFile(file);
                  }
                }}
                className="w-full rounded-md border border-input bg-surface p-2 text-sm text-on-surface file:mr-3 file:rounded file:border-0 file:bg-surface-dim file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-on-surface"
              />
            </div>

            {tieneSoportePrevio && (
              <Textarea
                id="gasto-motivo-reemplazo"
                label="Motivo del reemplazo"
                required
                rows={2}
                placeholder="Ej. Factura corregida por el proveedor con nuevo número de radicación"
                value={motivoReemplazo}
                maxLength={500}
                onChange={(e) => setMotivoReemplazo(e.target.value)}
              />
            )}
          </div>

          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => setSoporteModalOpen(false)} disabled={subiendoSoporte}>
              Cancelar
            </Button>
            <Button onClick={subirSoporte} disabled={subiendoSoporte} loading={subiendoSoporte}>
              {subiendoSoporte ? 'Subiendo…' : 'Guardar soporte'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Detail and audit trail */}
      <Sheet open={detalleSheetOpen} onOpenChange={setDetalleSheetOpen}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>Detalle del gasto y auditoría</SheetTitle>
            <SheetDescription>Datos completos, soporte documental y trazabilidad de cambios.</SheetDescription>
          </SheetHeader>

          {loadingDetalle ? (
            <div className="space-y-4 py-6" role="status" aria-label="Cargando detalle">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : gastoDetalle ? (
            <div className="space-y-6 py-4">
              <div className="space-y-3 rounded-lg border border-border bg-surface-container p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <span className="text-xs font-semibold text-text-secondary">Monto pagado</span>
                    <p className="app-numeral mt-1 text-[1.625rem] text-on-surface">
                      {fmtCOP.format(Number(gastoDetalle.monto || 0))}
                    </p>
                  </div>
                  <Badge
                    variant={(ESTADOS_GASTO.find((e) => e.value === (gastoDetalle.estado || 'REGISTRADO')) || {}).variant || 'secondary'}
                  >
                    {(ESTADOS_GASTO.find((e) => e.value === (gastoDetalle.estado || 'REGISTRADO')) || {}).label ||
                      gastoDetalle.estado}
                  </Badge>
                </div>
                <dl className="grid grid-cols-2 gap-2 border-t border-border-subtle pt-3 text-xs">
                  {[
                    ['Fecha', gastoDetalle.fechaGasto || '—'],
                    ['Método', METODO_LABEL[gastoDetalle.metodoPago] || gastoDetalle.metodoPago || '—'],
                    ['Categoría', gastoDetalle.categoria || '—'],
                    ['Presupuesto', gastoDetalle.rubroPresupuesto || 'Sin asociar'],
                  ].map(([k, v]) => (
                    <div key={k}>
                      <dt className="text-text-secondary">{k}</dt>
                      <dd className="font-semibold tabular-nums text-on-surface">{v}</dd>
                    </div>
                  ))}
                </dl>
              </div>

              <div className="space-y-3">
                <div>
                  <h4 className="mb-1 text-[13px] font-bold text-on-surface">Beneficiario</h4>
                  <div className="space-y-1 rounded-md border border-border bg-surface p-3 text-sm">
                    <p className="font-semibold text-on-surface">{gastoDetalle.beneficiario}</p>
                    {gastoDetalle.proveedorNit && (
                      <p className="text-xs tabular-nums text-text-muted">NIT o identificación: {gastoDetalle.proveedorNit}</p>
                    )}
                  </div>
                </div>
                <div>
                  <h4 className="mb-1 text-[13px] font-bold text-on-surface">Motivo o justificación</h4>
                  <div className="rounded-md border border-border bg-surface p-3 text-sm text-text-secondary">
                    {gastoDetalle.justificacion || 'No se registró una justificación detallada para este egreso.'}
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <h4 className="text-[13px] font-bold text-on-surface">Documento soporte vigente</h4>
                {gastoDetalle.facturaSoporteUrl ? (
                  <div className="space-y-3 rounded-md border border-border bg-surface p-4">
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex min-w-0 items-center gap-2">
                        <FileText className="h-5 w-5 shrink-0 text-tint-green-fg" aria-hidden="true" />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold">{gastoDetalle.archivoNombreOrig || 'Soporte documental'}</p>
                          <p className="text-xs tabular-nums text-text-muted">
                            {gastoDetalle.archivoMimeType}
                            {gastoDetalle.archivoTamanoBytes ? ` · ${(gastoDetalle.archivoTamanoBytes / 1024).toFixed(1)} KB` : ''}
                          </p>
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => verSoporte(gastoDetalle.idGasto, gastoDetalle.archivoNombreOrig, gastoDetalle.archivoMimeType)}
                          icon={<Eye className="h-3.5 w-3.5" />}
                        >
                          Ver
                        </Button>
                        <IconButton
                          variant="outline"
                          size="icon"
                          className="h-10 w-10"
                          onClick={() => descargarSoporte(gastoDetalle.idGasto, gastoDetalle.archivoNombreOrig)}
                          aria-label="Descargar soporte"
                        >
                          <Download aria-hidden="true" />
                        </IconButton>
                      </div>
                    </div>
                    {gastoDetalle.archivoSha256 && (
                      <div className="flex items-center gap-1 truncate border-t border-border-subtle pt-2 font-mono text-[11.5px] text-text-muted">
                        <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-tint-green-fg" aria-hidden="true" />
                        <span className="truncate">SHA-256: {gastoDetalle.archivoSha256}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="space-y-2 rounded-md border-[1.5px] border-dashed border-tint-amber-fg/40 bg-tint-amber-bg p-4 text-center">
                    <AlertCircle className="mx-auto h-6 w-6 text-tint-amber-fg" aria-hidden="true" />
                    <p className="text-xs font-semibold text-tint-amber-fg">
                      Este egreso no tiene soporte documental adjunto.
                    </p>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        setDetalleSheetOpen(false);
                        abrirModalSoporte(gastoDetalle);
                      }}
                      icon={<Upload className="h-3.5 w-3.5" />}
                    >
                      Adjuntar soporte
                    </Button>
                  </div>
                )}
              </div>

              <div className="space-y-2 rounded-md border border-border bg-surface-container p-4 text-xs">
                <h4 className="flex items-center gap-1.5 text-[13px] font-bold text-on-surface">
                  <ShieldCheck className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  Registro de auditoría
                </h4>
                <div className="space-y-1 text-text-secondary">
                  <p>
                    <span className="font-semibold text-on-surface">Registrado por: </span>
                    {gastoDetalle.registradoPorNombre || `Usuario #${gastoDetalle.registradoPor || '-'}`}
                  </p>
                  {gastoDetalle.fechaCreacion && (
                    <p className="tabular-nums">
                      <span className="font-semibold text-on-surface">Fecha de creación: </span>
                      {gastoDetalle.fechaCreacion}
                    </p>
                  )}
                  {gastoDetalle.modificadoPorNombre && (
                    <p className="tabular-nums">
                      <span className="font-semibold text-on-surface">Última modificación por: </span>
                      {gastoDetalle.modificadoPorNombre}
                      {gastoDetalle.fechaModificacion ? ` el ${gastoDetalle.fechaModificacion}` : ''}
                    </p>
                  )}
                </div>
              </div>

              {gastoDetalle.historialSoportes && gastoDetalle.historialSoportes.length > 0 && (
                <div className="space-y-2">
                  <h4 className="flex items-center gap-1.5 text-[13px] font-bold text-on-surface">
                    <History className="h-4 w-4 text-text-secondary" aria-hidden="true" />
                    Soportes reemplazados <span className="tabular-nums">({gastoDetalle.historialSoportes.length})</span>
                  </h4>
                  <ul className="space-y-2">
                    {gastoDetalle.historialSoportes.map((h) => (
                      <li key={h.idHistorial} className="space-y-1 rounded-md border border-border bg-surface p-3 text-xs">
                        <div className="flex items-center justify-between gap-2">
                          <span className="max-w-[200px] truncate font-semibold text-on-surface">
                            {h.archivoNombreOrig || 'Soporte archivado'}
                          </span>
                          <span className="tabular-nums text-text-muted">{h.fechaReemplazo || '—'}</span>
                        </div>
                        <p className="text-text-secondary">Motivo: {h.motivoReemplazo || 'Sustitución de soporte'}</p>
                        <p className="text-text-muted">
                          Reemplazado por: {h.reemplazadoPorNombre || `Usuario #${h.reemplazadoPor || '-'}`}
                        </p>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

      {/* Supporting document preview */}
      <Dialog open={previewModalOpen} onOpenChange={setPreviewModalOpen}>
        <DialogContent className="flex max-h-[92dvh] flex-col p-4 sm:max-w-4xl">
          <DialogHeader className="border-b border-border pb-3 pr-10">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <DialogTitle className="min-w-0 truncate text-base">{previewFileInfo.name}</DialogTitle>
              <Button
                variant="outline"
                size="sm"
                onClick={() => descargarSoporte(previewFileInfo.id, previewFileInfo.name)}
                icon={<Download className="h-3.5 w-3.5" />}
              >
                Descargar
              </Button>
            </div>
          </DialogHeader>

          <div className="flex min-h-[50dvh] flex-1 items-center justify-center overflow-hidden rounded-md bg-surface-dim p-2">
            {previewLoading ? (
              <div className="w-full max-w-md space-y-3" role="status">
                <Skeleton className="h-6 w-2/3" />
                <Skeleton className="h-40 w-full" />
                <p className="text-center text-xs text-text-secondary">Cargando documento…</p>
              </div>
            ) : previewBlobUrl ? (
              previewFileInfo.type.includes('image') ? (
                <img src={previewBlobUrl} alt={previewFileInfo.name} className="max-h-[75dvh] max-w-full rounded object-contain" />
              ) : (
                <iframe src={previewBlobUrl} title="Visor de factura" className="h-[75dvh] w-full rounded border-0" />
              )
            ) : (
              <p className="text-sm text-text-secondary">No se pudo generar la vista previa.</p>
            )}
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmarEliminar}
        title="Eliminar gasto operativo"
        message={`¿Está seguro de eliminar el gasto por valor de ${fmtCOP.format(Number(deleteTarget?.monto || deleteTarget?.MONTO || 0))} a favor de "${deleteTarget?.beneficiario || deleteTarget?.BENEFICIARIO || ''}"? Esta acción revertirá el monto en presupuestos.`}
        confirmLabel="Eliminar gasto"
        danger
      />
    </div>
  );
}
