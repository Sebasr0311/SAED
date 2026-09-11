import { useState, useMemo, useEffect } from 'react';
import {
  DollarSign,
  Receipt,
  FileCheck,
  AlertCircle,
  Search,
  Filter,
  Eye,
  Download,
  Calendar,
  FileText,
  Building2,
  X,
  History,
  ShieldCheck,
  CheckCircle2,
  TrendingUp,
  ExternalLink,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import { api, BASE_URL } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

const ESTADOS_GASTO = [
  { value: 'REGISTRADO', label: 'Registrado', badgeClass: 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300' },
  { value: 'PAGADO', label: 'Pagado', badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300' },
  { value: 'ANULADO', label: 'Anulado', badgeClass: 'bg-slate-100 text-slate-600 border-slate-300 dark:bg-slate-800 dark:text-slate-400' },
];

export default function OrgGastosPage() {
  // Filters
  const [idPropiedad, setIdPropiedad] = useState('');
  const [fechaInicio, setFechaInicio] = useState('');
  const [fechaFin, setFechaFin] = useState('');
  const [categoria, setCategoria] = useState('');
  const [estado, setEstado] = useState('');
  const [conSoporte, setConSoporte] = useState('TODOS');
  const [search, setSearch] = useState('');

  // Fetch properties for filter selector
  const { data: propData } = useFetch(() => api.get('/org/propiedades'), []);
  const propiedades = useMemo(() => {
    const raw = propData?.data || propData?.items || propData || [];
    return Array.isArray(raw) ? raw : [];
  }, [propData]);

  // Build query params
  const queryString = useMemo(() => {
    const p = new URLSearchParams();
    if (idPropiedad) p.append('idPropiedad', idPropiedad);
    if (fechaInicio) p.append('fechaInicio', fechaInicio);
    if (fechaFin) p.append('fechaFin', fechaFin);
    if (categoria) p.append('categoria', categoria);
    if (estado) p.append('estado', estado);
    if (conSoporte === 'CON_SOPORTE') p.append('conSoporte', 'true');
    if (conSoporte === 'SIN_SOPORTE') p.append('conSoporte', 'false');
    return p.toString();
  }, [idPropiedad, fechaInicio, fechaFin, categoria, estado, conSoporte]);

  // Fetch consolidated gastos
  const { data: rawConsolidado, loading, refetch } = useFetch(
    () => api.get(`/org/gastos${queryString ? `?${queryString}` : ''}`),
    [queryString]
  );

  const consolidado = useMemo(() => {
    return rawConsolidado?.data || rawConsolidado || {
      totalGastado: 0,
      cantidadGastos: 0,
      conSoporte: 0,
      sinSoporte: 0,
      desgloseCategorias: {},
      desglosePropiedades: [],
      gastos: [],
    };
  }, [rawConsolidado]);

  // Client-side text search on expenses
  const gastosFiltrados = useMemo(() => {
    const list = consolidado.gastos || [];
    if (!search.trim()) return list;
    const q = search.toLowerCase().trim();
    return list.filter((g) => {
      const ben = (g.beneficiario || '').toLowerCase();
      const nit = (g.proveedorNit || '').toLowerCase();
      const cat = (g.categoria || '').toLowerCase();
      const just = (g.justificacion || '').toLowerCase();
      const prop = (g.nombrePropiedad || '').toLowerCase();
      return ben.includes(q) || nit.includes(q) || cat.includes(q) || just.includes(q) || prop.includes(q);
    });
  }, [consolidado.gastos, search]);

  // Detail Sheet
  const [detalleSheetOpen, setDetalleSheetOpen] = useState(false);
  const [gastoDetalle, setGastoDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);

  // Document Preview
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewFileInfo, setPreviewFileInfo] = useState({ name: '', type: '', id: null });

  // Reset filters
  function limpiarFiltros() {
    setIdPropiedad('');
    setFechaInicio('');
    setFechaFin('');
    setCategoria('');
    setEstado('');
    setConSoporte('TODOS');
    setSearch('');
  }

  // Ver Detalle Completo
  async function abrirDetalle(id) {
    setLoadingDetalle(true);
    setDetalleSheetOpen(true);
    try {
      const res = await api.get(`/org/gastos/${id}`);
      setGastoDetalle(res.data || res);
    } catch (err) {
      toast.error(err.message || 'Error al cargar detalle del gasto');
      setDetalleSheetOpen(false);
    } finally {
      setLoadingDetalle(false);
    }
  }

  // Visualizar Soporte
  async function verSoporte(id, nombre, mime) {
    setPreviewLoading(true);
    setPreviewFileInfo({ name: nombre || 'soporte_gasto', type: mime || 'application/pdf', id });
    setPreviewModalOpen(true);

    try {
      const token = sessionStorage.getItem('token');
      const res = await fetch(`${BASE_URL}/org/gastos/${id}/soporte`, {
        headers: {
          Authorization: token ? `Bearer ${token}` : '',
        },
      });

      if (!res.ok) {
        throw new Error('No se pudo cargar el archivo de soporte');
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      setPreviewBlobUrl(url);
    } catch (err) {
      toast.error(err.message || 'Error al abrir visor del documento');
      setPreviewModalOpen(false);
    } finally {
      setPreviewLoading(false);
    }
  }

  // Descargar Soporte
  async function descargarSoporte(id, nombre) {
    try {
      const token = sessionStorage.getItem('token');
      const res = await fetch(`${BASE_URL}/org/gastos/${id}/soporte?download=true`, {
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

  useEffect(() => {
    if (!previewModalOpen && previewBlobUrl) {
      URL.revokeObjectURL(previewBlobUrl);
      setPreviewBlobUrl(null);
    }
  }, [previewModalOpen, previewBlobUrl]);

  const porcentajeSoporte =
    consolidado.cantidadGastos > 0
      ? Math.round((consolidado.conSoporte / consolidado.cantidadGastos) * 100)
      : 100;

  return (
    <div className="space-y-6">
      {/* 1. Header */}
      <PageHeader
        title="Auditoría de Gastos Consolidados"
        subtitle="Supervisión financiera, verificación de facturas de soporte y trazabilidad en todas las propiedades"
      />

      {/* 2. KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border shadow-sm">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Egresos Totales</p>
              <p className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                {fmtCOP.format(Number(consolidado.totalGastado || 0))}
              </p>
              <p className="text-xs text-muted-foreground">Total consolidado en el período</p>
            </div>
            <div className="p-3 bg-indigo-50 text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-400 rounded-xl">
              <DollarSign className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Total Movimientos</p>
              <p className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                {consolidado.cantidadGastos}
              </p>
              <p className="text-xs text-muted-foreground">Registros ejecutados</p>
            </div>
            <div className="p-3 bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400 rounded-xl">
              <Receipt className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Cumplimiento Soporte</p>
              <div className="flex items-baseline gap-2">
                <p className="text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400">
                  {consolidado.conSoporte}
                </p>
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-300">
                  {porcentajeSoporte}%
                </span>
              </div>
              <p className="text-xs text-muted-foreground">Con factura o documento legal</p>
            </div>
            <div className="p-3 bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 rounded-xl">
              <FileCheck className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>

        <Card className="border shadow-sm">
          <CardContent className="p-5 flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Sin Soporte Adjunto</p>
              <p className="text-2xl font-bold tracking-tight text-amber-600 dark:text-amber-400">
                {consolidado.sinSoporte}
              </p>
              <p className="text-xs text-muted-foreground">
                {consolidado.sinSoporte > 0 ? 'Requieren regularización urgente' : 'Auditoría 100% al día'}
              </p>
            </div>
            <div className="p-3 bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 rounded-xl">
              <AlertCircle className="h-6 w-6" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. Executive Filter Bar */}
      <Card className="border shadow-sm">
        <CardContent className="p-4 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3">
            {/* Propiedad */}
            <div className="lg:col-span-2">
              <select
                value={idPropiedad}
                onChange={(e) => setIdPropiedad(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Todas las propiedades</option>
                {propiedades.map((p) => (
                  <option key={p.idPropiedad || p.ID_PROPIEDAD} value={p.idPropiedad || p.ID_PROPIEDAD}>
                    {p.nombre || p.NOMBRE} ({p.ciudad || p.CIUDAD || 'Colombia'})
                  </option>
                ))}
              </select>
            </div>

            {/* Fecha Desde */}
            <div>
              <input
                type="date"
                title="Fecha Desde"
                value={fechaInicio}
                onChange={(e) => setFechaInicio(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              />
            </div>

            {/* Fecha Hasta */}
            <div>
              <input
                type="date"
                title="Fecha Hasta"
                value={fechaFin}
                onChange={(e) => setFechaFin(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              />
            </div>

            {/* Estado */}
            <div>
              <select
                value={estado}
                onChange={(e) => setEstado(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              >
                <option value="">Estado: Todos</option>
                {ESTADOS_GASTO.map((est) => (
                  <option key={est.value} value={est.value}>{est.label}</option>
                ))}
              </select>
            </div>

            {/* Soporte */}
            <div>
              <select
                value={conSoporte}
                onChange={(e) => setConSoporte(e.target.value)}
                className="w-full py-2 px-3 text-sm rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              >
                <option value="TODOS">Soporte: Todos</option>
                <option value="CON_SOPORTE">Con Factura</option>
                <option value="SIN_SOPORTE">Sin Factura</option>
              </select>
            </div>
          </div>

          {/* Quick Search and Clear */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-3 pt-2 border-t">
            <div className="relative w-full sm:w-96">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Filtrar por beneficiario, NIT, concepto o propiedad..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-4 py-1.5 text-xs rounded-lg border bg-background focus:ring-2 focus:ring-primary/20"
              />
            </div>

            {(idPropiedad || fechaInicio || fechaFin || categoria || estado || conSoporte !== 'TODOS' || search) && (
              <Button variant="ghost" size="sm" onClick={limpiarFiltros} className="gap-1 text-xs">
                <X className="h-3.5 w-3.5" />
                Limpiar filtros
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 4. Resumen por Propiedades y Categorías */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Desglose por Propiedad */}
        <Card className="border shadow-sm">
          <CardHeader className="p-4 pb-2 border-b">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Building2 className="h-4 w-4 text-primary" />
              Egresos por Propiedad
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4 space-y-3">
            {consolidado.desglosePropiedades && consolidado.desglosePropiedades.length > 0 ? (
              <div className="space-y-2">
                {consolidado.desglosePropiedades.map((p) => {
                  const totalGasto = Number(p.totalGastado || 0);
                  const totalGlobal = Number(consolidado.totalGastado || 1);
                  const pct = totalGlobal > 0 ? Math.round((totalGasto / totalGlobal) * 100) : 0;
                  return (
                    <div key={p.idPropiedad} className="space-y-1">
                      <div className="flex justify-between items-center text-xs">
                        <span className="font-medium text-slate-800 dark:text-slate-200">{p.nombrePropiedad}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-muted-foreground font-mono">{p.cantidadGastos} egresos</span>
                          <span className="font-bold text-slate-900 dark:text-white font-mono">{fmtCOP.format(totalGasto)}</span>
                        </div>
                      </div>
                      <div className="w-full bg-slate-100 dark:bg-slate-800 h-2 rounded-full overflow-hidden">
                        <div className="bg-primary h-full rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground text-center py-4">No hay datos de propiedades en el rango seleccionado</p>
            )}
          </CardContent>
        </Card>

        {/* Desglose por Categoría */}
        <Card className="border shadow-sm">
          <CardHeader className="p-4 pb-2 border-b">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-emerald-600" />
              Distribución por Categoría
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4 space-y-3">
            {Object.keys(consolidado.desgloseCategorias || {}).length > 0 ? (
              <div className="space-y-2">
                {Object.entries(consolidado.desgloseCategorias).map(([cat, monto]) => {
                  const montoNum = Number(monto || 0);
                  const totalGlobal = Number(consolidado.totalGastado || 1);
                  const pct = totalGlobal > 0 ? Math.round((montoNum / totalGlobal) * 100) : 0;
                  return (
                    <div key={cat} className="space-y-1">
                      <div className="flex justify-between items-center text-xs">
                        <span className="font-medium text-slate-800 dark:text-slate-200">{cat}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-muted-foreground text-[11px]">{pct}%</span>
                          <span className="font-bold text-slate-900 dark:text-white font-mono">{fmtCOP.format(montoNum)}</span>
                        </div>
                      </div>
                      <div className="w-full bg-slate-100 dark:bg-slate-800 h-2 rounded-full overflow-hidden">
                        <div className="bg-emerald-500 h-full rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground text-center py-4">No hay datos de categorías en el rango seleccionado</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 5. Tabla de Auditoría Consolidada */}
      <Card className="border shadow-sm">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : gastosFiltrados.length === 0 ? (
            <div className="p-12 text-center space-y-3">
              <Receipt className="h-8 w-8 text-slate-400 mx-auto" />
              <p className="text-base font-medium text-slate-700 dark:text-slate-200">No se encontraron egresos</p>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                No existen registros de gastos para los criterios y fechas seleccionados en las propiedades de la organización.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50/75 dark:bg-slate-900/50">
                    <TableHead className="w-32">Propiedad</TableHead>
                    <TableHead className="w-24">Fecha</TableHead>
                    <TableHead>Beneficiario & NIT</TableHead>
                    <TableHead>Categoría & Justificación</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Soporte de Respaldo</TableHead>
                    <TableHead className="text-right">Monto</TableHead>
                    <TableHead className="text-right w-24">Auditoría</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {gastosFiltrados.map((g) => {
                    const id = g.idGasto || g.ID_GASTO || g.id;
                    const tieneSop = Boolean(g.facturaSoporteUrl);
                    const estadoConfig = ESTADOS_GASTO.find((e) => e.value === g.estado) || {
                      label: g.estado,
                      badgeClass: 'bg-slate-100 text-slate-700',
                    };

                    return (
                      <TableRow key={id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/40">
                        <TableCell>
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200 dark:bg-indigo-950/40 dark:text-indigo-300">
                            {g.nombrePropiedad || `Propiedad #${g.idPropiedad}`}
                          </span>
                        </TableCell>

                        <TableCell className="font-mono text-xs whitespace-nowrap text-slate-600 dark:text-slate-400">
                          {g.fechaGasto || '-'}
                        </TableCell>

                        <TableCell>
                          <div className="font-medium text-slate-900 dark:text-white leading-tight">
                            {g.beneficiario}
                          </div>
                          {g.proveedorNit && (
                            <div className="text-xs text-muted-foreground font-mono mt-0.5">
                              NIT: {g.proveedorNit}
                            </div>
                          )}
                        </TableCell>

                        <TableCell className="max-w-xs">
                          <div className="text-xs font-medium text-slate-800 dark:text-slate-200">
                            {g.categoria}
                          </div>
                          {g.justificacion ? (
                            <p className="text-xs text-muted-foreground truncate" title={g.justificacion}>
                              {g.justificacion}
                            </p>
                          ) : (
                            <span className="text-[11px] text-slate-400 italic">Sin justificación</span>
                          )}
                        </TableCell>

                        <TableCell>
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${estadoConfig.badgeClass}`}>
                            {estadoConfig.label}
                          </span>
                        </TableCell>

                        <TableCell>
                          {tieneSop ? (
                            <button
                              onClick={() => verSoporte(id, g.archivoNombreOrig, g.archivoMimeType)}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-emerald-50 text-emerald-700 hover:bg-emerald-100 border border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 transition-colors"
                            >
                              <FileCheck className="h-3.5 w-3.5" />
                              <span className="truncate max-w-[130px]">{g.archivoNombreOrig || 'Ver Factura'}</span>
                            </button>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950/40 dark:text-amber-300">
                              <AlertCircle className="h-3 w-3" />
                              Sin soporte
                            </span>
                          )}
                        </TableCell>

                        <TableCell className="text-right font-mono font-bold text-slate-900 dark:text-white">
                          {fmtCOP.format(Number(g.monto || 0))}
                        </TableCell>

                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => abrirDetalle(id)}
                            className="gap-1 text-xs"
                            title="Ver expediente y auditoría"
                          >
                            <Eye className="h-4 w-4 text-slate-600 dark:text-slate-300" />
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

      {/* 6. Sheet / Drawer Detalle Ejecutivo de Auditoría */}
      <Sheet open={detalleSheetOpen} onOpenChange={setDetalleSheetOpen}>
        <SheetContent className="sm:max-w-xl w-full overflow-y-auto">
          <SheetHeader>
            <SheetTitle className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-primary" />
              Expediente de Gasto & Auditoría
            </SheetTitle>
            <SheetDescription>
              Inspección de egreso, proveedor, justificación legal y soporte documental.
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
              {/* Tarjeta de Monto y Propiedad */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-900 border space-y-3">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="text-xs uppercase tracking-wider text-muted-foreground font-semibold">
                      {gastoDetalle.nombrePropiedad}
                    </span>
                    <p className="text-3xl font-mono font-bold text-slate-900 dark:text-white">
                      {fmtCOP.format(Number(gastoDetalle.monto || 0))}
                    </p>
                  </div>
                  <Badge variant="outline" className="text-xs">
                    {gastoDetalle.estado || 'REGISTRADO'}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-2 border-t text-xs">
                  <div>
                    <span className="text-muted-foreground">Fecha Egreso: </span>
                    <span className="font-medium">{gastoDetalle.fechaGasto || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Método Pago: </span>
                    <span className="font-medium">{gastoDetalle.metodoPago || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Categoría: </span>
                    <span className="font-medium">{gastoDetalle.categoria || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Presupuesto: </span>
                    <span className="font-medium">{gastoDetalle.rubroPresupuesto || 'Sin rubro'}</span>
                  </div>
                </div>
              </div>

              {/* Proveedor y Justificación */}
              <div className="space-y-3">
                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">
                    Beneficiario / Proveedor
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm space-y-1">
                    <p className="font-semibold text-slate-900 dark:text-white">{gastoDetalle.beneficiario}</p>
                    {gastoDetalle.proveedorNit && (
                      <p className="text-xs text-muted-foreground font-mono">NIT / CC: {gastoDetalle.proveedorNit}</p>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">
                    Justificación Registrada
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm text-slate-700 dark:text-slate-300 italic">
                    {gastoDetalle.justificacion || 'No se ingresó justificación textual para este gasto.'}
                  </div>
                </div>
              </div>

              {/* Factura o Documento Soporte */}
              <div className="space-y-2">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Documento Soporte de Respaldo
                </h4>
                {gastoDetalle.facturaSoporteUrl ? (
                  <div className="p-4 rounded-xl border bg-background space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 truncate">
                        <FileText className="h-5 w-5 text-emerald-600 flex-shrink-0" />
                        <div className="truncate">
                          <p className="text-sm font-medium truncate">{gastoDetalle.archivoNombreOrig || 'Factura Soporte'}</p>
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
                  <div className="p-4 rounded-xl border border-dashed text-center space-y-1 bg-amber-50/40 dark:bg-amber-950/20">
                    <AlertCircle className="h-6 w-6 text-amber-500 mx-auto" />
                    <p className="text-xs text-amber-700 dark:text-amber-300 font-medium">
                      Este gasto no posee factura ni documento soporte adjunto.
                    </p>
                  </div>
                )}
              </div>

              {/* Registro de Auditoría de Usuario */}
              <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-900 border space-y-2 text-xs">
                <h4 className="font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
                  <History className="h-4 w-4 text-primary" />
                  Trazabilidad de Registro
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

              {/* Historial de Soportes Reemplazados */}
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

      {/* 7. Visor Modal de Soporte */}
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
    </div>
  );
}
