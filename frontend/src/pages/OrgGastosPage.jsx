import { useState, useMemo, useEffect } from 'react';
import {
  DollarSign,
  Receipt,
  FileCheck,
  AlertCircle,
  Search,
  Eye,
  Download,
  FileText,
  Building2,
  X,
  History,
  ShieldCheck,
  TrendingUp,
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
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';

const FILTER_CONTROL =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

const ESTADOS_GASTO = [
  { value: 'REGISTRADO', label: 'Registrado', badgeClass: 'bg-tint-blue-bg text-tint-blue-fg border-tint-blue-fg/25' },
  { value: 'PAGADO', label: 'Pagado', badgeClass: 'bg-tint-green-bg text-tint-green-fg border-tint-green-fg/25' },
  { value: 'ANULADO', label: 'Anulado', badgeClass: 'bg-surface-dim text-text-secondary border-border' },
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
  const { data: propData } = useFetch(() => api.get('/properties'), []);
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
  const { data: rawConsolidado, loading, error: loadError, refetch } = useFetch(
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
    <PageContainer>
      {/* 1. Header */}
      <PageHeader
        title="Auditoría de gastos consolidados"
        subtitle="Supervisión financiera, verificación de facturas de soporte y trazabilidad en todas las propiedades."
      />

      {/* 2. KPI Cards */}
      {loading && !rawConsolidado ? (
        <div role="status" aria-busy="true" aria-label="Cargando indicadores" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-lg" />
          ))}
        </div>
      ) : loadError ? (
        <ErrorState
          title="No se pudieron cargar los gastos"
          message={loadError?.message || 'Revisa la conexión e inténtalo de nuevo.'}
          onRetry={() => refetch().catch(() => {})}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            label="Egresos totales"
            value={fmtCOP.format(Number(consolidado.totalGastado || 0))}
            context="Total consolidado en el período"
            icon={DollarSign}
          />
          <MetricCard
            label="Total de movimientos"
            value={consolidado.cantidadGastos}
            context="Registros ejecutados"
            icon={Receipt}
            variant="info"
          />
          <MetricCard
            label="Con soporte"
            value={consolidado.conSoporte}
            context={consolidado.cantidadGastos > 0 ? `${porcentajeSoporte}% con factura o documento legal` : 'Sin movimientos en el período'}
            icon={FileCheck}
            variant="success"
          />
          <MetricCard
            label="Sin soporte adjunto"
            value={consolidado.sinSoporte}
            context={consolidado.sinSoporte > 0 ? 'Requieren regularización' : 'Todos los egresos tienen soporte'}
            icon={AlertCircle}
            variant={consolidado.sinSoporte > 0 ? 'warning' : 'secondary'}
          />
        </div>
      )}

      {/* 3. Executive Filter Bar */}
      <Card className="border shadow-sm">
        <CardContent className="p-4 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3">
            {/* Propiedad */}
            <div className="lg:col-span-2">
              <select
                aria-label="Filtrar por propiedad"
                value={idPropiedad}
                onChange={(e) => setIdPropiedad(e.target.value)}
                className={FILTER_CONTROL}
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
                title="Fecha desde"
                aria-label="Fecha desde"
                value={fechaInicio}
                onChange={(e) => setFechaInicio(e.target.value)}
                className={FILTER_CONTROL}
              />
            </div>

            {/* Fecha Hasta */}
            <div>
              <input
                type="date"
                title="Fecha hasta"
                aria-label="Fecha hasta"
                value={fechaFin}
                onChange={(e) => setFechaFin(e.target.value)}
                className={FILTER_CONTROL}
              />
            </div>

            {/* Estado */}
            <div>
              <select
                aria-label="Filtrar por estado"
                value={estado}
                onChange={(e) => setEstado(e.target.value)}
                className={FILTER_CONTROL}
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
                aria-label="Filtrar por soporte"
                value={conSoporte}
                onChange={(e) => setConSoporte(e.target.value)}
                className={FILTER_CONTROL}
              >
                <option value="TODOS">Soporte: Todos</option>
                <option value="CON_SOPORTE">Con factura</option>
                <option value="SIN_SOPORTE">Sin factura</option>
              </select>
            </div>
          </div>

          {/* Quick Search and Clear */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-3 pt-2 border-t">
            <div className="relative w-full sm:w-96">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
              <input
                type="search"
                aria-label="Buscar egresos"
                placeholder="Filtrar por beneficiario, NIT, concepto o propiedad..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className={`${FILTER_CONTROL} pl-9`}
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
              <Building2 className="h-4 w-4 text-text-secondary" aria-hidden="true" />
              Egresos por propiedad
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
                        <span className="font-medium text-on-surface">{p.nombrePropiedad}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-muted-foreground tabular-nums">{p.cantidadGastos} egresos</span>
                          <span className="font-bold text-on-surface tabular-nums">{fmtCOP.format(totalGasto)}</span>
                        </div>
                      </div>
                      <div className="h-2 w-full overflow-hidden rounded-[2px] bg-surface-dim" aria-hidden="true">
                        <div className="h-full bg-btn-primary transition-[width] duration-300 ease-out" style={{ width: `${pct}%` }} />
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
              <TrendingUp className="h-4 w-4 text-text-secondary" aria-hidden="true" />
              Distribución por categoría
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
                        <span className="font-medium text-on-surface">{cat}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-muted-foreground text-xs">{pct}%</span>
                          <span className="font-bold text-on-surface tabular-nums">{fmtCOP.format(montoNum)}</span>
                        </div>
                      </div>
                      <div className="h-2 w-full overflow-hidden rounded-[2px] bg-surface-dim" aria-hidden="true">
                        <div className="h-full bg-tint-green-fg transition-[width] duration-300 ease-out" style={{ width: `${pct}%` }} />
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
            <div className="p-6 space-y-3" role="status" aria-busy="true" aria-label="Cargando egresos">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : loadError ? (
            <ErrorState
              compact
              className="m-4"
              title="No se pudo cargar el detalle de egresos"
              onRetry={() => refetch().catch(() => {})}
            />
          ) : gastosFiltrados.length === 0 ? (
            <EmptyState
              icon={Receipt}
              title="No se encontraron egresos"
              subtitle="No existen gastos para los criterios y fechas seleccionados en las propiedades de la organización."
            >
              {(idPropiedad || fechaInicio || fechaFin || categoria || estado || conSoporte !== 'TODOS' || search) && (
                <Button variant="outline" size="sm" className="mt-3" onClick={limpiarFiltros}>
                  Limpiar filtros
                </Button>
              )}
            </EmptyState>
          ) : (
            <div className="max-h-[70vh] overflow-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-surface-container">
                    <TableHead className="w-32">Propiedad</TableHead>
                    <TableHead className="w-24">Fecha</TableHead>
                    <TableHead>Beneficiario y NIT</TableHead>
                    <TableHead>Categoría y justificación</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Soporte</TableHead>
                    <TableHead className="text-right">Monto</TableHead>
                    <TableHead className="text-right w-24">Expediente</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {gastosFiltrados.map((g) => {
                    const id = g.idGasto || g.ID_GASTO || g.id;
                    const tieneSop = Boolean(g.facturaSoporteUrl);
                    const estadoConfig = ESTADOS_GASTO.find((e) => e.value === g.estado) || {
                      label: g.estado,
                      badgeClass: 'bg-surface-dim text-text-secondary',
                    };

                    return (
                      <TableRow key={id} className="hover:bg-surface-container">
                        <TableCell>
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-tint-primary-bg text-primary-text border border-primary/40">
                            {g.nombrePropiedad || `Propiedad #${g.idPropiedad}`}
                          </span>
                        </TableCell>

                        <TableCell className="text-xs whitespace-nowrap tabular-nums text-text-secondary">
                          {g.fechaGasto || '-'}
                        </TableCell>

                        <TableCell>
                          <div className="font-medium text-on-surface leading-tight">
                            {g.beneficiario}
                          </div>
                          {g.proveedorNit && (
                            <div className="text-xs text-muted-foreground font-mono mt-0.5">
                              NIT: {g.proveedorNit}
                            </div>
                          )}
                        </TableCell>

                        <TableCell className="max-w-xs">
                          <div className="text-xs font-medium text-on-surface">
                            {g.categoria}
                          </div>
                          {g.justificacion ? (
                            <p className="text-xs text-muted-foreground truncate" title={g.justificacion}>
                              {g.justificacion}
                            </p>
                          ) : (
                            <span className="text-xs text-text-muted italic">Sin justificación</span>
                          )}
                        </TableCell>

                        <TableCell>
                          <span className={`inline-flex items-center rounded-[3px] border px-2 py-0.5 text-xs font-bold ${estadoConfig.badgeClass}`}>
                            {estadoConfig.label}
                          </span>
                        </TableCell>

                        <TableCell>
                          {tieneSop ? (
                            <button
                              type="button"
                              onClick={() => verSoporte(id, g.archivoNombreOrig, g.archivoMimeType)}
                              className="inline-flex min-h-9 items-center gap-1.5 rounded-md border border-tint-green-fg/25 bg-tint-green-bg px-2.5 py-1 text-xs font-semibold text-tint-green-fg transition-[filter] duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                            >
                              <FileCheck className="h-3.5 w-3.5" />
                              <span className="truncate max-w-[130px]">{g.archivoNombreOrig || 'Ver Factura'}</span>
                            </button>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25">
                              <AlertCircle className="h-3 w-3" />
                              Sin soporte
                            </span>
                          )}
                        </TableCell>

                        <TableCell className="whitespace-nowrap text-right font-bold tabular-nums text-on-surface">
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
                            <Eye className="h-4 w-4 text-text-secondary" aria-hidden="true" />
                            <span className="sr-only">Ver expediente de {g.beneficiario}</span>
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
              Expediente del gasto
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
              <div className="p-4 rounded-xl bg-surface-container border space-y-3">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="text-xs text-muted-foreground font-semibold">
                      {gastoDetalle.nombrePropiedad}
                    </span>
                    <p className="app-numeral text-[1.75rem] text-on-surface">
                      {fmtCOP.format(Number(gastoDetalle.monto || 0))}
                    </p>
                  </div>
                  <Badge variant="outline" className="text-xs">
                    {gastoDetalle.estado || 'REGISTRADO'}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-2 border-t text-xs">
                  <div>
                    <span className="text-muted-foreground">Fecha del egreso: </span>
                    <span className="font-medium">{gastoDetalle.fechaGasto || '-'}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Método de pago: </span>
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
                  <h4 className="text-xs font-semibold text-text-muted mb-1">
                    Beneficiario / Proveedor
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm space-y-1">
                    <p className="font-semibold text-on-surface">{gastoDetalle.beneficiario}</p>
                    {gastoDetalle.proveedorNit && (
                      <p className="text-xs text-muted-foreground font-mono">NIT / CC: {gastoDetalle.proveedorNit}</p>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-semibold text-text-muted mb-1">
                    Justificación Registrada
                  </h4>
                  <div className="p-3 rounded-lg border bg-background text-sm text-text-secondary italic">
                    {gastoDetalle.justificacion || 'No se ingresó justificación textual para este gasto.'}
                  </div>
                </div>
              </div>

              {/* Factura o Documento Soporte */}
              <div className="space-y-2">
                <h4 className="text-xs font-semibold text-text-muted">
                  Documento Soporte de Respaldo
                </h4>
                {gastoDetalle.facturaSoporteUrl ? (
                  <div className="p-4 rounded-xl border bg-background space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 truncate">
                        <FileText className="h-5 w-5 text-tint-green-fg flex-shrink-0" />
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
                      <div className="pt-2 border-t text-xs text-muted-foreground font-mono flex items-center gap-1 truncate">
                        <ShieldCheck className="h-3.5 w-3.5 text-tint-green-fg flex-shrink-0" />
                        <span className="truncate">SHA-256: {gastoDetalle.archivoSha256}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-4 rounded-xl border border-dashed text-center space-y-1 bg-tint-amber-bg">
                    <AlertCircle className="h-6 w-6 text-tint-amber-fg mx-auto" />
                    <p className="text-xs text-tint-amber-fg font-medium">
                      Este gasto no posee factura ni documento soporte adjunto.
                    </p>
                  </div>
                )}
              </div>

              {/* Registro de Auditoría de Usuario */}
              <div className="p-4 rounded-xl bg-surface-container border space-y-2 text-xs">
                <h4 className="font-semibold text-text-secondary flex items-center gap-1.5">
                  <History className="h-4 w-4 text-primary" />
                  Trazabilidad de Registro
                </h4>
                <div className="space-y-1 text-text-secondary">
                  <p>
                    <span className="font-medium text-on-surface">Registrado por: </span>
                    {gastoDetalle.registradoPorNombre || `Usuario #${gastoDetalle.registradoPor || '-'}`}
                  </p>
                  {gastoDetalle.fechaCreacion && (
                    <p>
                      <span className="font-medium text-on-surface">Fecha de creación: </span>
                      {gastoDetalle.fechaCreacion}
                    </p>
                  )}
                  {gastoDetalle.modificadoPorNombre && (
                    <p>
                      <span className="font-medium text-on-surface">Última modificación por: </span>
                      {gastoDetalle.modificadoPorNombre}
                      {gastoDetalle.fechaModificacion ? ` el ${gastoDetalle.fechaModificacion}` : ''}
                    </p>
                  )}
                </div>
              </div>

              {/* Historial de Soportes Reemplazados */}
              {gastoDetalle.historialSoportes && gastoDetalle.historialSoportes.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-xs font-semibold text-text-muted flex items-center gap-1.5">
                    <History className="h-4 w-4 text-primary-text" />
                    Historial de Soportes Reemplazados ({gastoDetalle.historialSoportes.length})
                  </h4>
                  <div className="space-y-2">
                    {gastoDetalle.historialSoportes.map((h) => (
                      <div key={h.idHistorial} className="p-3 rounded-lg border bg-surface-container text-xs space-y-1">
                        <div className="flex justify-between items-center">
                          <span className="font-medium text-on-surface truncate max-w-[200px]">
                            {h.archivoNombreOrig || 'Soporte archivado'}
                          </span>
                          <span className="text-xs text-muted-foreground">{h.fechaReemplazo || '-'}</span>
                        </div>
                        <p className="text-muted-foreground italic">
                          Motivo: {h.motivoReemplazo || 'Sustitución de soporte'}
                        </p>
                        <p className="text-xs text-muted-foreground">
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

          <div className="flex-1 min-h-[500px] flex items-center justify-center p-2 bg-surface-dim rounded-lg overflow-hidden">
            {previewLoading ? (
              <div className="w-full max-w-md space-y-3" role="status" aria-busy="true">
                <Skeleton className="h-6 w-2/3" />
                <Skeleton className="h-64 w-full" />
                <p className="text-xs text-muted-foreground">Cargando documento...</p>
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
                  title="Visor de factura"
                  className="w-full h-[75vh] rounded border-0"
                />
              )
            ) : (
              <p className="text-sm text-muted-foreground">No se pudo generar la vista previa</p>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
