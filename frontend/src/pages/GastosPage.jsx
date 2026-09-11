import { useState, useMemo, useEffect } from 'react';
import {
  DollarSign,
  Receipt,
  FileCheck,
  AlertCircle,
  Plus,
  Search,
  Filter,
  Eye,
  Download,
  Trash2,
  Edit3,
  Paperclip,
  Calendar,
  FileText,
  Building2,
  Upload,
  X,
  History,
  ShieldCheck,
  Clock,
  ArrowUpDown,
  RefreshCw,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import { api, BASE_URL } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent } from '../components/ui/card.tsx';
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table.tsx';
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

const ESTADOS_GASTO = [
  { value: 'REGISTRADO', label: 'Registrado', badgeClass: 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300' },
  { value: 'PAGADO', label: 'Pagado', badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300' },
  { value: 'ANULADO', label: 'Anulado', badgeClass: 'bg-slate-100 text-slate-600 border-slate-300 dark:bg-slate-800 dark:text-slate-400' },
];

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
  const { data: gastosRaw, loading, refetch } = useFetch(() => api.get('/gastos'), []);
  const { data: presupData } = useFetch(() => api.get('/presupuestos'), []);

  // UI States
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [soporteFile, setSoporteFile] = useState(null);
  const [saving, setSaving] = useState(false);

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
    setDialogOpen(true);
  }

  // Guardar Gasto (Crear o Actualizar)
  async function guardar() {
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
      refetch();
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
      refetch();
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
      const token = sessionStorage.getItem('token');
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
      const token = sessionStorage.getItem('token');
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
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar el gasto');
    }
  }

  return (
    <div className="space-y-6">
      {/* 1. Header */}
      <PageHeader
        title="Gestión de Gastos"
        subtitle="Registro de egresos operativos con soporte documental y trazabilidad"
        action={
          <Button onClick={abrirCrear} className="gap-2 shadow-sm">
            <Plus className="h-4 w-4" />
            Nuevo Gasto
          </Button>
        }
      />

      {/* 2. KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border shadow-sm hover:shadow-md transition-shadow">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Total Gastado</p>
              <p className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                {fmtCOP.format(kpis.total)}
              </p>
              <p className="text-xs text-muted-foreground">Egresos activos del período</p>
            </div>
            <div className="p-3 bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 rounded-xl">
              <DollarSign className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm hover:shadow-md transition-shadow">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Total Registros</p>
              <p className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                {kpis.cantidad}
              </p>
              <p className="text-xs text-muted-foreground">Movimientos en el sistema</p>
            </div>
            <div className="p-3 bg-indigo-50 text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-400 rounded-xl">
              <Receipt className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm hover:shadow-md transition-shadow">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Con Soporte</p>
              <div className="flex items-baseline gap-2">
                <p className="text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400">
                  {kpis.conSoporte}
                </p>
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-300">
                  {kpis.porcentajeSoporte}%
                </span>
              </div>
              <p className="text-xs text-muted-foreground">Con factura o comprobante</p>
            </div>
            <div className="p-3 bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 rounded-xl">
              <FileCheck className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm hover:shadow-md transition-shadow">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Sin Soporte</p>
              <p className="text-2xl font-bold tracking-tight text-amber-600 dark:text-amber-400">
                {kpis.sinSoporte}
              </p>
              <p className="text-xs text-muted-foreground">
                {kpis.sinSoporte > 0 ? 'Requieren adjuntar factura' : 'Al día con soportes'}
              </p>
            </div>
            <div className="p-3 bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 rounded-xl">
              <AlertCircle className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. Filter Bar */}
      <Card className="border shadow-sm">
        <CardContent className="p-4 space-y-3">
          <div className="flex flex-col md:flex-row gap-3">
            {/* Search Input */}
            <div className="relative flex-1">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Buscar por beneficiario, NIT, concepto o motivo..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-4 py-2 text-sm rounded-lg border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            {/* Presupuesto Select */}
            <div className="w-full md:w-56">
              <select
                value={filtroPresupuesto}
                onChange={(e) => setFiltroPresupuesto(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Presupuesto: Todos</option>
                {presupuestos.map((p) => (
                  <option key={p.idPresupuesto || p.ID_PRESUPUESTO || p.id} value={p.idPresupuesto || p.ID_PRESUPUESTO || p.id}>
                    {p.rubro || p.RUBRO}
                  </option>
                ))}
              </select>
            </div>

            {/* Estado Select */}
            <div className="w-full md:w-44">
              <select
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Estado: Todos</option>
                {ESTADOS_GASTO.map((est) => (
                  <option key={est.value} value={est.value}>{est.label}</option>
                ))}
              </select>
            </div>

            {/* Soporte Select */}
            <div className="w-full md:w-48">
              <select
                value={filtroSoporte}
                onChange={(e) => setFiltroSoporte(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="TODOS">Soporte: Todos</option>
                <option value="CON_SOPORTE">Con Documento</option>
                <option value="SIN_SOPORTE">Sin Documento</option>
              </select>
            </div>

            {/* Reset Button */}
            {(search || filtroPresupuesto || filtroCategoria || filtroEstado || filtroSoporte !== 'TODOS') && (
              <Button variant="outline" size="sm" onClick={limpiarFiltros} className="gap-1 self-center">
                <X className="h-4 w-4" />
                Limpiar
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 4. Table Card */}
      <Card className="border shadow-sm">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : gastosFiltrados.length === 0 ? (
            <div className="p-12 text-center space-y-3">
              <div className="inline-flex p-3 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-400">
                <Receipt className="h-8 w-8" />
              </div>
              <p className="text-base font-medium text-slate-700 dark:text-slate-200">No se encontraron gastos</p>
              <p className="text-sm text-muted-foreground max-w-sm mx-auto">
                No hay egresos registrados con los filtros aplicados. Puede registrar un nuevo gasto con el botón superior.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/75 dark:bg-slate-900/50">
                    <TableHead className="w-28">Fecha</TableHead>
                    <TableHead>Beneficiario & Proveedor</TableHead>
                    <TableHead>Categoría</TableHead>
                    <TableHead>Presupuesto</TableHead>
                    <TableHead>Método</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Soporte Documental</TableHead>
                    <TableHead className="text-right">Monto</TableHead>
                    <TableHead className="text-right w-36">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {gastosFiltrados.map((g) => {
                    const id = g.idGasto || g.ID_GASTO || g.id;
                    const fecha = g.fechaGasto || g.FECHA_GASTO || '-';
                    const ben = g.beneficiario || g.BENEFICIARIO || '-';
                    const nit = g.proveedorNit || g.PROVEEDOR_NIT;
                    const cat = g.categoria || g.CATEGORIA || '-';
                    const rubro = g.rubroPresupuesto || g.RUBRO_PRESUPUESTO;
                    const metodo = g.metodoPago || g.METODO_PAGO || 'EFECTIVO';
                    const estado = (g.estado || g.ESTADO || 'REGISTRADO').toUpperCase();
                    const monto = Number(g.monto || g.MONTO || 0);
                    const tieneSoporte = Boolean(g.facturaSoporteUrl || g.FACTURA_SOPORTE_URL);
                    const origName = g.archivoNombreOrig || g.ARCHIVO_NOMBRE_ORIG;
                    const mime = g.archivoMimeType || g.ARCHIVO_MIME_TYPE;

                    const estadoConfig = ESTADOS_GASTO.find((e) => e.value === estado) || {
                      label: estado,
                      badgeClass: 'bg-slate-100 text-slate-700',
                    };

                    return (
                      <TableRow key={id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/40">
                        <TableCell className="font-mono text-xs whitespace-nowrap text-slate-600 dark:text-slate-400">
                          {fecha}
                        </TableCell>

                        <TableCell>
                          <div className="font-medium text-slate-900 dark:text-white leading-tight">
                            {ben}
                          </div>
                          {nit && (
                            <div className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                              <span>NIT:</span>
                              <span className="font-mono">{nit}</span>
                            </div>
                          )}
                        </TableCell>

                        <TableCell>
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                            {cat}
                          </span>
                        </TableCell>

                        <TableCell className="text-xs text-muted-foreground">
                          {rubro || <span className="italic text-slate-400">Sin asociar</span>}
                        </TableCell>

                        <TableCell className="text-xs text-slate-600 dark:text-slate-300">
                          {metodo}
                        </TableCell>

                        <TableCell>
                          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${estadoConfig.badgeClass}`}>
                            {estadoConfig.label}
                          </span>
                        </TableCell>

                        <TableCell>
                          {tieneSoporte ? (
                            <div className="flex items-center gap-1.5">
                              <button
                                onClick={() => verSoporte(id, origName, mime)}
                                className="inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-medium bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800 transition-colors"
                                title="Visualizar soporte documental"
                              >
                                <FileText className="h-3.5 w-3.5" />
                                <span className="truncate max-w-[120px]">{origName || 'Ver Factura'}</span>
                              </button>
                              <button
                                onClick={() => abrirModalSoporte(g)}
                                className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded"
                                title="Reemplazar soporte con justificación de auditoría"
                              >
                                <RefreshCw className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => abrirModalSoporte(g)}
                              className="inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-medium bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800 transition-colors"
                            >
                              <Upload className="h-3 w-3" />
                              Adjuntar
                            </button>
                          )}
                        </TableCell>

                        <TableCell className="text-right font-mono font-bold text-slate-900 dark:text-white">
                          {fmtCOP.format(monto)}
                        </TableCell>

                        <TableCell className="text-right space-x-1 whitespace-nowrap">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => abrirDetalle(id)}
                            title="Ver detalle completo y trazabilidad"
                          >
                            <Eye className="h-4 w-4 text-slate-600 dark:text-slate-300" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => abrirEditar(g)}
                            title="Editar datos del gasto"
                          >
                            <Edit3 className="h-4 w-4 text-slate-600 dark:text-slate-300" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setDeleteTarget(g)}
                            title="Eliminar gasto"
                          >
                            <Trash2 className="h-4 w-4 text-red-600 dark:text-red-400" />
                          </Button>
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

      {/* 5. Dialog Crear / Editar Gasto */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Receipt className="h-5 w-5 text-primary" />
              {editando ? 'Editar Gasto Operativo' : 'Registrar Nuevo Gasto'}
            </DialogTitle>
            <DialogDescription>
              {editando
                ? 'Actualice los datos del gasto. Los cambios quedarán registrados en auditoría.'
                : 'Ingrese los datos del egreso y adjunte la factura o comprobante soporte (PDF, JPG, PNG).'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-5 py-3">
            {/* Fila 1: Fecha y Monto */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Fecha del Gasto *
                </label>
                <input
                  type="date"
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.fecha}
                  onChange={(e) => setForm((f) => ({ ...f, fecha: e.target.value }))}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Monto (COP) *
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-2 text-sm text-muted-foreground">$</span>
                  <input
                    type="number"
                    step="1"
                    min="1"
                    placeholder="0"
                    className="w-full border rounded-lg pl-7 pr-3 py-2 text-sm font-mono font-bold bg-background focus:ring-2 focus:ring-primary/20"
                    value={form.monto}
                    onChange={(e) => setForm((f) => ({ ...f, monto: e.target.value }))}
                  />
                </div>
              </div>
            </div>

            {/* Fila 2: Categoría y Presupuesto */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Categoría del Gasto *
                </label>
                <input
                  list="categorias-list"
                  placeholder="Ej: Mantenimiento General"
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.categoria}
                  onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
                />
                <datalist id="categorias-list">
                  {CATEGORIAS_SUGERIDAS.map((c) => (
                    <option key={c} value={c} />
                  ))}
                </datalist>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Presupuesto Asociado
                </label>
                <select
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.presupuestoId}
                  onChange={(e) => setForm((f) => ({ ...f, presupuestoId: e.target.value }))}
                >
                  <option value="">Sin asociar a presupuesto</option>
                  {presupuestos.map((p) => (
                    <option key={p.idPresupuesto || p.ID_PRESUPUESTO || p.id} value={p.idPresupuesto || p.ID_PRESUPUESTO || p.id}>
                      {p.rubro || p.RUBRO}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Fila 3: Beneficiario y NIT */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="sm:col-span-2 space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Beneficiario / Proveedor *
                </label>
                <input
                  placeholder="Razón social o nombre completo del proveedor"
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.beneficiario}
                  onChange={(e) => setForm((f) => ({ ...f, beneficiario: e.target.value }))}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  NIT o Cédula
                </label>
                <input
                  placeholder="Ej: 900.123.456-7"
                  className="w-full border rounded-lg px-3 py-2 text-sm font-mono bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.proveedorNit}
                  onChange={(e) => setForm((f) => ({ ...f, proveedorNit: e.target.value }))}
                />
              </div>
            </div>

            {/* Fila 4: Método y Estado */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Método de Pago
                </label>
                <select
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.metodoPago}
                  onChange={(e) => setForm((f) => ({ ...f, metodoPago: e.target.value }))}
                >
                  {METODOS_PAGO.map((m) => (
                    <option key={m.value} value={m.value}>{m.label}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Estado del Gasto
                </label>
                <select
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={form.estado}
                  onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
                >
                  {ESTADOS_GASTO.map((e) => (
                    <option key={e.value} value={e.value}>{e.label}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Fila 5: Justificación */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                Justificación / Motivo del Gasto
              </label>
              <textarea
                rows={2}
                placeholder="Explique el motivo del egreso (ej: Mantenimiento preventivo de bombas de agua según contrato trimestral)..."
                className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                value={form.justificacion}
                maxLength={500}
                onChange={(e) => setForm((f) => ({ ...f, justificacion: e.target.value }))}
              />
              <p className="text-[11px] text-muted-foreground text-right">
                {form.justificacion.length}/500 caracteres
              </p>
            </div>

            {/* Fila 6: Adjunto de Soporte Documental (solo en creación) */}
            {!editando && (
              <div className="space-y-2 pt-2 border-t">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
                  <Paperclip className="h-4 w-4 text-primary" />
                  Factura o Documento Soporte (PDF, JPG, PNG &lt; 10MB)
                </label>

                <div className="border-2 border-dashed rounded-lg p-4 text-center hover:border-primary/50 transition-colors bg-slate-50/50 dark:bg-slate-900/30">
                  {soporteFile ? (
                    <div className="flex items-center justify-between p-2 bg-background rounded-md border text-sm">
                      <div className="flex items-center gap-2 truncate">
                        <FileText className="h-4 w-4 text-emerald-600 flex-shrink-0" />
                        <span className="font-medium truncate">{soporteFile.name}</span>
                        <span className="text-xs text-muted-foreground whitespace-nowrap">
                          ({(soporteFile.size / 1024).toFixed(1)} KB)
                        </span>
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setSoporteFile(null)}
                      >
                        <X className="h-4 w-4 text-red-500" />
                      </Button>
                    </div>
                  ) : (
                    <div>
                      <input
                        type="file"
                        id="soporte-file-input"
                        accept=".pdf,.jpg,.jpeg,.png"
                        className="hidden"
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
                        className="cursor-pointer inline-flex flex-col items-center gap-1 text-xs text-muted-foreground hover:text-slate-900 dark:hover:text-white"
                      >
                        <Upload className="h-6 w-6 text-slate-400 mb-1" />
                        <span className="font-medium text-primary">Haga clic para seleccionar archivo</span>
                        <span>o arrastre la factura aquí (PDF, JPG, PNG hasta 10MB)</span>
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
            <Button onClick={guardar} disabled={saving} className="gap-1.5">
              {saving ? 'Guardando...' : editando ? 'Guardar Cambios' : 'Registrar Gasto'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 6. Modal Adjuntar o Reemplazar Soporte Documental */}
      <Dialog open={soporteModalOpen} onOpenChange={setSoporteModalOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Paperclip className="h-5 w-5 text-primary" />
              {targetGastoSoporte?.facturaSoporteUrl || targetGastoSoporte?.FACTURA_SOPORTE_URL
                ? 'Reemplazar Soporte Documental'
                : 'Adjuntar Soporte Documental'}
            </DialogTitle>
            <DialogDescription>
              {targetGastoSoporte?.facturaSoporteUrl || targetGastoSoporte?.FACTURA_SOPORTE_URL
                ? 'El archivo anterior quedará archivado en el historial de auditoría inmutable.'
                : 'Suba la factura o comprobante legal que respalda este egreso.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900 border text-xs space-y-1">
              <p className="font-medium text-slate-900 dark:text-white">
                Gasto #{targetGastoSoporte?.idGasto || targetGastoSoporte?.ID_GASTO}
              </p>
              <p className="text-muted-foreground">
                Beneficiario: {targetGastoSoporte?.beneficiario || targetGastoSoporte?.BENEFICIARIO}
              </p>
              <p className="text-muted-foreground">
                Monto: {fmtCOP.format(Number(targetGastoSoporte?.monto || targetGastoSoporte?.MONTO || 0))}
              </p>
            </div>

            {/* Input de archivo */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                Archivo de Soporte *
              </label>
              <input
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
                className="w-full text-xs border rounded-lg p-2 bg-background"
              />
            </div>

            {/* Motivo de reemplazo si ya existía uno previo */}
            {(targetGastoSoporte?.facturaSoporteUrl || targetGastoSoporte?.FACTURA_SOPORTE_URL) && (
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                  Motivo del Reemplazo *
                </label>
                <textarea
                  rows={2}
                  placeholder="Justifique el motivo de cambio del archivo (ej: Factura corregida por proveedor con nuevo número de radicación)..."
                  className="w-full border rounded-lg px-3 py-2 text-sm bg-background focus:ring-2 focus:ring-primary/20"
                  value={motivoReemplazo}
                  maxLength={500}
                  onChange={(e) => setMotivoReemplazo(e.target.value)}
                />
              </div>
            )}
          </div>

          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={() => setSoporteModalOpen(false)} disabled={subiendoSoporte}>
              Cancelar
            </Button>
            <Button onClick={subirSoporte} disabled={subiendoSoporte} className="gap-1.5">
              {subiendoSoporte ? 'Subiendo...' : 'Guardar Soporte'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 7. Sheet / Drawer de Detalle y Trazabilidad */}
      <Sheet open={detalleSheetOpen} onOpenChange={setDetalleSheetOpen}>
        <SheetContent className="sm:max-w-xl w-full overflow-y-auto">
          <SheetHeader>
            <SheetTitle className="flex items-center gap-2">
              <Receipt className="h-5 w-5 text-primary" />
              Detalle del Gasto & Auditoría
            </SheetTitle>
            <SheetDescription>
              Ficha técnica completa, soporte documental y trazabilidad de cambios.
            </SheetDescription>
          </SheetHeader>

          {loadingDetalle ? (
            <div className="p-6 space-y-4">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : gastoDetalle ? (
            <div className="space-y-6 py-4">
              {/* Bloque Financiero Principal */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-900 border space-y-3">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">Monto Pagado</span>
                    <p className="text-3xl font-mono font-bold text-slate-900 dark:text-white">
                      {fmtCOP.format(Number(gastoDetalle.monto || 0))}
                    </p>
                  </div>
                  <Badge variant="outline" className="text-xs px-2.5 py-1">
                    {gastoDetalle.estado || 'REGISTRADO'}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-2 border-t text-xs">
                  <div>
                    <span className="text-muted-foreground">Fecha: </span>
                    <span className="font-medium text-slate-800 dark:text-slate-200">{gastoDetalle.fechaGasto || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Método: </span>
                    <span className="font-medium text-slate-800 dark:text-slate-200">{gastoDetalle.metodoPago || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Categoría: </span>
                    <span className="font-medium text-slate-800 dark:text-slate-200">{gastoDetalle.categoria || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Presupuesto: </span>
                    <span className="font-medium text-slate-800 dark:text-slate-200">{gastoDetalle.rubroPresupuesto || 'Sin asociar'}</span>
                  </div>
                </div>
              </div>

              {/* Beneficiario y Justificación */}
              <div className="space-y-3">
                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">
                    Beneficiario & Proveedor
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm space-y-1">
                    <p className="font-semibold text-slate-900 dark:text-white">{gastoDetalle.beneficiario}</p>
                    {gastoDetalle.proveedorNit && (
                      <p className="text-xs text-muted-foreground font-mono">NIT / Identificación: {gastoDetalle.proveedorNit}</p>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">
                    Motivo o Justificación del Egreso
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm text-slate-700 dark:text-slate-300 italic">
                    {gastoDetalle.justificacion || 'No se registró una justificación detallada para este egreso.'}
                  </div>
                </div>
              </div>

              {/* Soporte Documental Vigente */}
              <div className="space-y-2">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Documento Soporte Vigente
                </h4>
                {gastoDetalle.facturaSoporteUrl ? (
                  <div className="p-4 rounded-xl border bg-background space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 truncate">
                        <FileText className="h-5 w-5 text-emerald-600 flex-shrink-0" />
                        <div className="truncate">
                          <p className="text-sm font-medium truncate">{gastoDetalle.archivoNombreOrig || 'Soporte Documental'}</p>
                          <p className="text-xs text-muted-foreground">
                            {gastoDetalle.archivoMimeType} • {gastoDetalle.archivoTamanoBytes ? `${(gastoDetalle.archivoTamanoBytes / 1024).toFixed(1)} KB` : ''}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-1">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => verSoporte(gastoDetalle.idGasto, gastoDetalle.archivoNombreOrig, gastoDetalle.archivoMimeType)}
                          className="gap-1 text-xs"
                        >
                          <Eye className="h-3.5 w-3.5" />
                          Ver
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => descargarSoporte(gastoDetalle.idGasto, gastoDetalle.archivoNombreOrig)}
                          className="gap-1 text-xs"
                        >
                          <Download className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </div>

                    {gastoDetalle.archivoSha256 && (
                      <div className="pt-2 border-t text-[11px] text-muted-foreground font-mono flex items-center gap-1 truncate">
                        <ShieldCheck className="h-3.5 w-3.5 text-emerald-600 flex-shrink-0" />
                        <span className="truncate">SHA-256: {gastoDetalle.archivoSha256}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-4 rounded-xl border border-dashed text-center space-y-2 bg-amber-50/40 dark:bg-amber-950/20">
                    <AlertCircle className="h-6 w-6 text-amber-500 mx-auto" />
                    <p className="text-xs text-amber-700 dark:text-amber-300 font-medium">
                      Este egreso no tiene adjunto ningún soporte documental.
                    </p>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        setDetalleSheetOpen(false);
                        abrirModalSoporte(gastoDetalle);
                      }}
                      className="text-xs gap-1"
                    >
                      <Upload className="h-3.5 w-3.5" />
                      Adjuntar Soporte Ahora
                    </Button>
                  </div>
                )}
              </div>

              {/* Trazabilidad y Auditoría */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-900 border space-y-2 text-xs">
                <h4 className="font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
                  <ShieldCheck className="h-4 w-4 text-primary" />
                  Registro de Auditoría
                </h4>
                <div className="space-y-1 text-slate-600 dark:text-slate-400">
                  <p>
                    <span className="font-medium text-slate-800 dark:text-slate-200">Registrado por: </span>
                    {gastoDetalle.registradoPorNombre || `Usuario #${gastoDetalle.registradoPor || '-'}`}
                  </p>
                  {gastoDetalle.fechaCreacion && (
                    <p>
                      <span className="font-medium text-slate-800 dark:text-slate-200">Fecha de creación: </span>
                      {gastoDetalle.fechaCreacion}
                    </p>
                  )}
                  {gastoDetalle.modificadoPorNombre && (
                    <p>
                      <span className="font-medium text-slate-800 dark:text-slate-200">Última modificación por: </span>
                      {gastoDetalle.modificadoPorNombre}
                      {gastoDetalle.fechaModificacion ? ` el ${gastoDetalle.fechaModificacion}` : ''}
                    </p>
                  )}
                </div>
              </div>

              {/* Historial de Soportes Anteriores Reemplazados */}
              {gastoDetalle.historialSoportes && gastoDetalle.historialSoportes.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                    <History className="h-4 w-4 text-indigo-500" />
                    Historial de Soportes Reemplazados ({gastoDetalle.historialSoportes.length})
                  </h4>
                  <div className="space-y-2">
                    {gastoDetalle.historialSoportes.map((h) => (
                      <div key={h.idHistorial} className="p-3 rounded-lg border bg-slate-50/50 dark:bg-slate-900/50 text-xs space-y-1">
                        <div className="flex justify-between items-center">
                          <span className="font-medium text-slate-800 dark:text-slate-200 truncate max-w-[200px]">
                            {h.archivoNombreOrig || 'Soporte archivado'}
                          </span>
                          <span className="text-[11px] text-muted-foreground">{h.fechaReemplazo || '-'}</span>
                        </div>
                        <p className="text-muted-foreground italic">
                          Motivo: {h.motivoReemplazo || 'Sustitución de soporte'}
                        </p>
                        <p className="text-[11px] text-muted-foreground">
                          Reemplazado por: {h.reemplazadoPorNombre || `Usuario #${h.reemplazadoPor || '-'}`}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

      {/* 8. Visor de Documento Soporte (Preview Modal) */}
      <Dialog open={previewModalOpen} onOpenChange={setPreviewModalOpen}>
        <DialogContent className="sm:max-w-4xl max-h-[92vh] flex flex-col p-4">
          <DialogHeader className="pb-2 border-b">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 truncate">
                <FileText className="h-5 w-5 text-primary" />
                <DialogTitle className="text-base truncate">{previewFileInfo.name}</DialogTitle>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => descargarSoporte(previewFileInfo.id, previewFileInfo.name)}
                className="gap-1 text-xs"
              >
                <Download className="h-3.5 w-3.5" />
                Descargar
              </Button>
            </div>
          </DialogHeader>

          <div className="flex-1 min-h-[500px] flex items-center justify-center p-2 bg-slate-100 dark:bg-slate-950 rounded-lg overflow-hidden">
            {previewLoading ? (
              <div className="space-y-3 text-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto" />
                <p className="text-xs text-muted-foreground">Cargando documento seguro...</p>
              </div>
            ) : previewBlobUrl ? (
              previewFileInfo.type.includes('image') ? (
                <img
                  src={previewBlobUrl}
                  alt={previewFileInfo.name}
                  className="max-h-[75vh] max-w-full object-contain rounded"
                />
              ) : (
                <iframe
                  src={previewBlobUrl}
                  title="Visor de Factura"
                  className="w-full h-[75vh] rounded border-0"
                />
              )
            ) : (
              <p className="text-sm text-muted-foreground">No se pudo generar la vista previa</p>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* 9. Confirm Delete Dialog */}
      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={confirmarEliminar}
        title="Eliminar Gasto Operativo"
        message={`¿Está seguro de eliminar el gasto por valor de ${fmtCOP.format(Number(deleteTarget?.monto || deleteTarget?.MONTO || 0))} a favor de "${deleteTarget?.beneficiario || deleteTarget?.BENEFICIARIO || ''}"? Esta acción revertirá el monto en presupuestos.`}
        confirmLabel="Eliminar Gasto"
        danger
      />
    </div>
  );
}
