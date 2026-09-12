import React, { useState, useMemo, useCallback } from 'react';
import {
  Droplets,
  Zap,
  Flame,
  DollarSign,
  AlertTriangle,
  CheckCircle2,
  TrendingUp,
  Plus,
  Search,
  Calendar,
  Filter,
  Camera,
  FileText,
  Edit2,
  Trash2,
  Building2,
  Gauge,
  ArrowUpRight,
  BarChart3,
  RefreshCw,
  Info,
  ExternalLink,
  X,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { MetricCard } from '../components/ui/MetricCard';
import { Modal } from '../components/ui/Modal';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';

const SERVICIOS = [
  { id: 'AGUA', label: 'Agua Potable', icon: Droplets, unit: 'm³', color: 'text-cyan-500 bg-cyan-500/10 border-cyan-500/30', badgeColor: 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 border-cyan-500/20' },
  { id: 'ENERGIA', label: 'Energía Eléctrica', icon: Zap, unit: 'kWh', color: 'text-amber-500 bg-amber-500/10 border-amber-500/30', badgeColor: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20' },
  { id: 'GAS', label: 'Gas Natural', icon: Flame, unit: 'm³', color: 'text-orange-500 bg-orange-500/10 border-orange-500/30', badgeColor: 'bg-orange-500/10 text-orange-600 dark:text-orange-400 border-orange-500/20' },
];

const SERVICIOS_MAP = SERVICIOS.reduce((acc, s) => {
  acc[s.id] = s;
  return acc;
}, {});

function formatCurrency(val) {
  if (val == null || isNaN(val)) return '$ 0';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

function formatNumber(val, decimals = 2) {
  if (val == null || isNaN(val)) return '0.00';
  return Number(val).toLocaleString('es-CO', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

const getCurrentPeriod = () => {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  return `${d.getFullYear()}-${month}`;
};

export default function ConsumosAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [periodoFiltro, setPeriodoFiltro] = useState(getCurrentPeriod());
  const [servicioFiltro, setServicioFiltro] = useState('TODOS');
  const [unidadFiltro, setUnidadFiltro] = useState('TODOS');
  const [busqueda, setBusqueda] = useState('');
  const [mostrarTendencias, setMostrarTendencias] = useState(false);

  // Modales
  const [modalOpen, setModalOpen] = useState(false);
  const [modalDeleteOpen, setModalDeleteOpen] = useState(false);
  const [modalFotoOpen, setModalFotoOpen] = useState(false);
  const [fotoVisualizar, setFotoVisualizar] = useState(null);

  // Estados de formulario
  const [isEditing, setIsEditing] = useState(false);
  const [currentId, setCurrentId] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [medicionAEliminar, setMedicionAEliminar] = useState(null);

  const initialFormState = {
    tipoServicio: 'AGUA',
    idUnidad: '',
    numeroMedidor: '',
    periodo: periodoFiltro,
    lecturaAnterior: '',
    lecturaActual: '',
    unidadMedida: 'M3',
    tarifaUnitaria: '',
    fotoMedidorUrl: '',
    observacionAnomalia: '',
    fechaTomaLectura: new Date().toISOString().split('T')[0],
  };

  const [form, setForm] = useState(initialFormState);

  // Carga de Unidades
  const { data: rawUnits } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );
  const unidades = useMemo(() => {
    if (!rawUnits) return [];
    if (Array.isArray(rawUnits)) return rawUnits;
    if (Array.isArray(rawUnits.items)) return rawUnits.items;
    return [];
  }, [rawUnits]);

  // Carga de Mediciones del período
  const {
    data: rawMediciones,
    loading: loadingMediciones,
    refresh: refreshMediciones,
  } = useFetch(
    () =>
      tenantApi.get(
        `/consumos?periodo=${periodoFiltro}${servicioFiltro !== 'TODOS' ? `&tipoServicio=${servicioFiltro}` : ''}${unidadFiltro !== 'TODOS' ? (unidadFiltro === 'ZONAS_COMUNES' ? '&idUnidad=' : `&idUnidad=${unidadFiltro}`) : ''}`
      ),
    [periodoFiltro, servicioFiltro, unidadFiltro, tenant.activeAssignmentId]
  );

  const mediciones = useMemo(() => {
    if (!rawMediciones) return [];
    if (Array.isArray(rawMediciones)) return rawMediciones;
    if (Array.isArray(rawMediciones.items)) return rawMediciones.items;
    return [];
  }, [rawMediciones]);

  // Carga de Resumen KPIs
  const {
    data: resumen,
    loading: loadingResumen,
    refresh: refreshResumen,
  } = useFetch(
    () => tenantApi.get(`/consumos/resumen?periodo=${periodoFiltro}`),
    [periodoFiltro, tenant.activeAssignmentId]
  );

  // Carga de Tendencias Históricas
  const {
    data: tendenciasRaw,
    loading: loadingTendencias,
    refresh: refreshTendencias,
  } = useFetch(
    () => tenantApi.get(`/consumos/tendencias?meses=12`),
    [tenant.activeAssignmentId]
  );

  const tendencias = useMemo(() => {
    if (!tendenciasRaw) return [];
    if (Array.isArray(tendenciasRaw)) return tendenciasRaw;
    if (Array.isArray(tendenciasRaw.items)) return tendenciasRaw.items;
    return [];
  }, [tendenciasRaw]);

  // Mediciones filtradas por búsqueda local
  const medicionesFiltradas = useMemo(() => {
    if (!busqueda.trim()) return mediciones;
    const q = busqueda.toLowerCase();
    return mediciones.filter((m) => {
      const medidor = (m.numeroMedidor || '').toLowerCase();
      const servicio = (m.tipoServicio || '').toLowerCase();
      const unidad = (m.identificadorUnidad || 'zonas comunes').toLowerCase();
      const leido = (m.leidoPor || '').toLowerCase();
      return (
        medidor.includes(q) ||
        servicio.includes(q) ||
        unidad.includes(q) ||
        leido.includes(q)
      );
    });
  }, [mediciones, busqueda]);

  // Refrescar todo
  const refreshAll = useCallback(() => {
    refreshMediciones();
    refreshResumen();
    refreshTendencias();
  }, [refreshMediciones, refreshResumen, refreshTendencias]);

  // Abrir modal de creación
  const handleOpenCreate = () => {
    setIsEditing(false);
    setCurrentId(null);
    setForm({
      ...initialFormState,
      periodo: periodoFiltro,
    });
    setModalOpen(true);
  };

  // Abrir modal de edición
  const handleOpenEdit = (medicion) => {
    setIsEditing(true);
    setCurrentId(medicion.idMedicion);
    setForm({
      tipoServicio: medicion.tipoServicio,
      idUnidad: medicion.idUnidad != null ? String(medicion.idUnidad) : '',
      numeroMedidor: medicion.numeroMedidor || '',
      periodo: medicion.periodo || periodoFiltro,
      lecturaAnterior: medicion.lecturaAnterior != null ? String(medicion.lecturaAnterior) : '',
      lecturaActual: medicion.lecturaActual != null ? String(medicion.lecturaActual) : '',
      unidadMedida: medicion.unidadMedida || (medicion.tipoServicio === 'ENERGIA' ? 'KWH' : 'M3'),
      tarifaUnitaria: medicion.tarifaUnitaria != null ? String(medicion.tarifaUnitaria) : '',
      fotoMedidorUrl: medicion.fotoMedidorUrl || '',
      observacionAnomalia: medicion.observacionAnomalia || '',
      fechaTomaLectura: medicion.fechaTomaLectura || new Date().toISOString().split('T')[0],
    });
    setModalOpen(true);
  };

  // Autocompletar última lectura desde backend
  const handleConsultarUltimaLectura = async (servicio, unidadId, medidor) => {
    try {
      let url = `/consumos/ultima-lectura?tipoServicio=${servicio}`;
      if (unidadId) url += `&idUnidad=${unidadId}`;
      if (medidor) url += `&numeroMedidor=${encodeURIComponent(medidor)}`;

      const res = await tenantApi.get(url);
      if (res && res.lecturaActual != null) {
        setForm((prev) => ({
          ...prev,
          lecturaAnterior: String(res.lecturaActual),
          numeroMedidor: prev.numeroMedidor || res.numeroMedidor || '',
          unidadMedida: res.unidadMedida || prev.unidadMedida,
          tarifaUnitaria: prev.tarifaUnitaria || (res.tarifaUnitaria ? String(res.tarifaUnitaria) : ''),
        }));
        toast.info(`Lectura anterior cargada: ${res.lecturaActual} ${res.unidadMedida} (Período: ${res.periodo})`);
      } else {
        toast.info('No se encontraron lecturas previas para este medidor/unidad.');
      }
    } catch {
      // Sin lecturas previas
    }
  };

  // Guardar formulario
  const handleSave = async (e) => {
    e.preventDefault();

    if (!form.tipoServicio) {
      toast.error('Seleccione el tipo de servicio.');
      return;
    }
    if (!form.numeroMedidor.trim()) {
      toast.error('Ingrese el número o identificador del medidor.');
      return;
    }
    if (!form.periodo) {
      toast.error('Especifique el período en formato YYYY-MM.');
      return;
    }
    if (form.lecturaAnterior === '' || form.lecturaActual === '') {
      toast.error('Ingrese tanto la lectura anterior como la actual.');
      return;
    }

    const anterior = parseFloat(form.lecturaAnterior);
    const actual = parseFloat(form.lecturaActual);

    if (isNaN(anterior) || isNaN(actual)) {
      toast.error('Las lecturas deben ser valores numéricos válidos.');
      return;
    }

    if (actual < anterior) {
      toast.error('La lectura actual no puede ser menor a la lectura anterior.');
      return;
    }

    const payload = {
      tipoServicio: form.tipoServicio,
      idUnidad: form.idUnidad ? parseInt(form.idUnidad, 10) : null,
      numeroMedidor: form.numeroMedidor.trim(),
      periodo: form.periodo.trim(),
      lecturaAnterior: anterior,
      lecturaActual: actual,
      unidadMedida: form.unidadMedida || (form.tipoServicio === 'ENERGIA' ? 'KWH' : 'M3'),
      tarifaUnitaria: form.tarifaUnitaria ? parseFloat(form.tarifaUnitaria) : null,
      fotoMedidorUrl: form.fotoMedidorUrl.trim() || null,
      observacionAnomalia: form.observacionAnomalia.trim() || null,
      fechaTomaLectura: form.fechaTomaLectura || null,
    };

    setIsSubmitting(true);
    try {
      if (isEditing) {
        await tenantApi.put(`/consumos/${currentId}`, payload);
        toast.success('Medición de consumo actualizada correctamente.');
      } else {
        await tenantApi.post('/consumos', payload);
        toast.success('Medición registrada y consumo calculado con éxito.');
      }
      setModalOpen(false);
      refreshAll();
    } catch (err) {
      const msg = err.response?.data?.message || 'Error al guardar la medición.';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Confirmar eliminación
  const handleDeleteConfirm = async () => {
    if (!medicionAEliminar) return;
    setIsSubmitting(true);
    try {
      await tenantApi.delete(`/consumos/${medicionAEliminar.idMedicion}`);
      toast.success('Medición eliminada exitosamente.');
      setModalDeleteOpen(false);
      setMedicionAEliminar(null);
      refreshAll();
    } catch (err) {
      const msg = err.response?.data?.message || 'Error al eliminar la medición.';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Cálculo en tiempo real en formulario
  const consumoEstimado = useMemo(() => {
    const ant = parseFloat(form.lecturaAnterior);
    const act = parseFloat(form.lecturaActual);
    if (!isNaN(ant) && !isNaN(act) && act >= ant) {
      return (act - ant).toFixed(2);
    }
    return '0.00';
  }, [form.lecturaAnterior, form.lecturaActual]);

  const costoEstimado = useMemo(() => {
    const consumo = parseFloat(consumoEstimado);
    const tarifa = parseFloat(form.tarifaUnitaria);
    if (!isNaN(consumo) && !isNaN(tarifa) && consumo > 0 && tarifa > 0) {
      return consumo * tarifa;
    }
    return 0;
  }, [consumoEstimado, form.tarifaUnitaria]);

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <PageHeader
        title="Consumos y Sostenibilidad"
        subtitle="Monitoreo y facturación de servicios públicos (Agua, Energía, Gas), detección de fugas y huella ecológica"
      >
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setMostrarTendencias(!mostrarTendencias)}
            className="flex items-center gap-1.5"
          >
            <BarChart3 className="w-4 h-4 text-primary" />
            <span>{mostrarTendencias ? 'Ocultar Tendencias' : 'Ver Tendencias'}</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={refreshAll}
            className="flex items-center gap-1.5"
          >
            <RefreshCw className="w-4 h-4" />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>
          <Button
            onClick={handleOpenCreate}
            size="sm"
            className="flex items-center gap-1.5 bg-primary text-primary-foreground hover:bg-primary/90"
          >
            <Plus className="w-4 h-4" />
            <span>Nueva Medición</span>
          </Button>
        </div>
      </PageHeader>

      {/* Alerta de Anomalías Global del Período */}
      {resumen?.anomaliasDetectadas > 0 && (
        <div className="flex items-center gap-3 p-4 rounded-xl border border-amber-500/30 bg-amber-500/10 text-amber-900 dark:text-amber-200 animate-in fade-in duration-300">
          <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 flex-shrink-0" />
          <div className="flex-1 text-sm">
            <span className="font-semibold">¡Atención requerida! </span>
            Se detectaron{' '}
            <strong className="underline decoration-amber-500 underline-offset-2">
              {resumen.anomaliasDetectadas} medición(es) con variación anómala
            </strong>{' '}
            superior al +50% respecto al histórico en el período {periodoFiltro}. Verifique posibles fugas, cortocircuitos o medidores descalibrados.
          </div>
        </div>
      )}

      {/* KPIs Principales */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Consumo Agua"
          value={`${formatNumber(resumen?.consumoTotalAgua || 0, 1)} m³`}
          subtitle={`Período ${periodoFiltro}`}
          icon={Droplets}
          variant="info"
        />
        <MetricCard
          title="Consumo Energía"
          value={`${formatNumber(resumen?.consumoTotalEnergia || 0, 1)} kWh`}
          subtitle={`Período ${periodoFiltro}`}
          icon={Zap}
          variant="warning"
        />
        <MetricCard
          title="Consumo Gas"
          value={`${formatNumber(resumen?.consumoTotalGas || 0, 1)} m³`}
          subtitle={`Período ${periodoFiltro}`}
          icon={Flame}
          variant="primary"
        />
        <MetricCard
          title="Costo Consolidado"
          value={formatCurrency(resumen?.costoTotalPeriodo || 0)}
          subtitle={`${resumen?.totalMediciones || 0} lecturas tomadas`}
          icon={DollarSign}
          variant="success"
        />
      </div>

      {/* Panel de Tendencias Históricas (Expandible) */}
      {mostrarTendencias && (
        <div className="rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
          <div className="flex items-center justify-between border-b border-border pb-4">
            <div className="flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-primary" />
              <h3 className="text-base font-semibold text-foreground">
                Tendencia de Consumo Histórico (Últimos 12 Meses)
              </h3>
            </div>
            <span className="text-xs text-muted-foreground">
              Agrupado por servicio y mes calendario
            </span>
          </div>

          {loadingTendencias ? (
            <div className="h-40 flex items-center justify-center text-muted-foreground text-sm">
              Cargando tendencias históricas...
            </div>
          ) : tendencias.length === 0 ? (
            <div className="h-32 flex items-center justify-center text-muted-foreground text-sm">
              No hay suficientes registros históricos para calcular tendencias.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-2">
              {['AGUA', 'ENERGIA', 'GAS'].map((srv) => {
                const srvInfo = SERVICIOS_MAP[srv];
                const dataSrv = tendencias.filter((t) => t.tipoServicio === srv);
                const maxConsumo = Math.max(...dataSrv.map((d) => Number(d.consumoTotal || 0)), 1);

                return (
                  <div
                    key={srv}
                    className="rounded-lg border border-border/60 bg-muted/20 p-4 space-y-3"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {React.createElement(srvInfo.icon, {
                          className: `w-4 h-4 ${srvInfo.color.split(' ')[0]}`,
                        })}
                        <span className="text-sm font-medium text-foreground">
                          {srvInfo.label}
                        </span>
                      </div>
                      <Badge variant="outline" className="text-xs">
                        {srvInfo.unit}
                      </Badge>
                    </div>

                    <div className="space-y-2">
                      {dataSrv.slice(-6).map((item) => {
                        const pct = Math.min(
                          100,
                          Math.max(10, ((Number(item.consumoTotal) || 0) / maxConsumo) * 100)
                        );
                        return (
                          <div key={item.periodo} className="space-y-1">
                            <div className="flex justify-between text-xs text-muted-foreground">
                              <span>{item.periodo}</span>
                              <span className="font-semibold text-foreground">
                                {formatNumber(item.consumoTotal, 1)} {srvInfo.unit} ({formatCurrency(item.costoTotal)})
                              </span>
                            </div>
                            <div className="w-full h-2 rounded-full bg-muted overflow-hidden">
                              <div
                                className={`h-full rounded-full transition-all duration-500 ${
                                  srv === 'AGUA'
                                    ? 'bg-cyan-500'
                                    : srv === 'ENERGIA'
                                    ? 'bg-amber-500'
                                    : 'bg-orange-500'
                                }`}
                                style={{ width: `${pct}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Barra de Filtros y Búsqueda */}
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
          {/* Selector de Período */}
          <div className="flex items-center gap-1.5">
            <Label htmlFor="periodo-select" className="text-xs text-muted-foreground flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5" /> Período:
            </Label>
            <Input
              id="periodo-select"
              type="month"
              value={periodoFiltro}
              onChange={(e) => setPeriodoFiltro(e.target.value)}
              className="h-9 w-36 text-xs"
            />
          </div>

          {/* Filtro por Servicio */}
          <div className="flex items-center gap-1">
            <Button
              variant={servicioFiltro === 'TODOS' ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => setServicioFiltro('TODOS')}
              className="text-xs h-8"
            >
              Todos
            </Button>
            {SERVICIOS.map((srv) => {
              const IconComp = srv.icon;
              return (
                <Button
                  key={srv.id}
                  variant={servicioFiltro === srv.id ? 'secondary' : 'ghost'}
                  size="sm"
                  onClick={() => setServicioFiltro(srv.id)}
                  className={`text-xs h-8 flex items-center gap-1 ${
                    servicioFiltro === srv.id ? 'font-semibold text-foreground' : 'text-muted-foreground'
                  }`}
                >
                  <IconComp className="w-3.5 h-3.5" />
                  {srv.label.split(' ')[0]}
                </Button>
              );
            })}
          </div>

          {/* Filtro por Unidad */}
          <div className="flex items-center gap-1.5">
            <select
              value={unidadFiltro}
              onChange={(e) => setUnidadFiltro(e.target.value)}
              aria-label="Filtrar por ubicación o unidad"
              className="h-9 px-2 text-xs rounded-md border border-input bg-background text-foreground"
            >
              <option value="TODOS">Todas las Unidades / Z.C.</option>
              <option value="ZONAS_COMUNES">Solo Zonas Comunes</option>
              {unidades.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.identificador || `Unidad #${u.id}`}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Buscador Rápido */}
        <div className="relative w-full md:w-64">
          <Search className="w-4 h-4 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar por medidor o unidad..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="pl-8 h-9 text-xs"
          />
          {busqueda && (
            <button
              onClick={() => setBusqueda('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Tabla de Mediciones */}
      <div className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
        {loadingMediciones ? (
          <div className="p-8 text-center text-muted-foreground text-sm">
            Cargando mediciones del período {periodoFiltro}...
          </div>
        ) : medicionesFiltradas.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <Gauge className="w-12 h-12 mx-auto text-muted-foreground/40" />
            <div className="text-base font-semibold text-foreground">
              No hay mediciones registradas
            </div>
            <p className="text-xs text-muted-foreground max-w-sm mx-auto">
              No se han encontrado tomas de lectura para los filtros seleccionados en el período{' '}
              <strong className="text-foreground">{periodoFiltro}</strong>.
            </p>
            <Button
              onClick={handleOpenCreate}
              size="sm"
              variant="outline"
              className="mt-2 text-xs"
            >
              <Plus className="w-3.5 h-3.5 mr-1" /> Registrar Primera Medición
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-muted/50 text-muted-foreground border-b border-border font-medium">
                <tr>
                  <th className="py-3 px-4">Servicio</th>
                  <th className="py-3 px-4">Ubicación / Unidad</th>
                  <th className="py-3 px-4">Medidor</th>
                  <th className="py-3 px-4 text-right">Lectura Anterior</th>
                  <th className="py-3 px-4 text-right">Lectura Actual</th>
                  <th className="py-3 px-4 text-right">Consumo</th>
                  <th className="py-3 px-4 text-right">Tarifa Unit.</th>
                  <th className="py-3 px-4 text-right">Costo Total</th>
                  <th className="py-3 px-4 text-center">Estado / Anomalía</th>
                  <th className="py-3 px-4 text-center">Evidencia</th>
                  <th className="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {medicionesFiltradas.map((m) => {
                  const srvInfo = SERVICIOS_MAP[m.tipoServicio] || {
                    label: m.tipoServicio,
                    icon: Gauge,
                    color: '',
                    badgeColor: 'bg-muted text-muted-foreground',
                    unit: m.unidadMedida,
                  };
                  const IconComp = srvInfo.icon;

                  return (
                    <tr
                      key={m.idMedicion}
                      className="hover:bg-muted/30 transition-colors"
                    >
                      {/* Servicio */}
                      <td className="py-3 px-4 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium border ${srvInfo.badgeColor}`}
                        >
                          <IconComp className="w-3.5 h-3.5" />
                          {srvInfo.label}
                        </span>
                      </td>

                      {/* Ubicación / Unidad */}
                      <td className="py-3 px-4 whitespace-nowrap font-medium text-foreground">
                        {m.idUnidad ? (
                          <span className="inline-flex items-center gap-1 text-foreground">
                            <Building2 className="w-3.5 h-3.5 text-muted-foreground" />
                            {m.identificadorUnidad || `Unidad #${m.idUnidad}`}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-primary font-medium">
                            <Building2 className="w-3.5 h-3.5" />
                            Zonas Comunes
                          </span>
                        )}
                      </td>

                      {/* Medidor */}
                      <td className="py-3 px-4 whitespace-nowrap text-muted-foreground font-mono">
                        {m.numeroMedidor}
                      </td>

                      {/* Lectura Anterior */}
                      <td className="py-3 px-4 text-right whitespace-nowrap text-muted-foreground font-mono">
                        {formatNumber(m.lecturaAnterior, 2)}
                      </td>

                      {/* Lectura Actual */}
                      <td className="py-3 px-4 text-right whitespace-nowrap font-semibold text-foreground font-mono">
                        {formatNumber(m.lecturaActual, 2)}
                      </td>

                      {/* Consumo Calculado */}
                      <td className="py-3 px-4 text-right whitespace-nowrap font-bold text-foreground">
                        {formatNumber(m.consumoCalculado, 2)}{' '}
                        <span className="text-[10px] text-muted-foreground font-normal">
                          {m.unidadMedida}
                        </span>
                      </td>

                      {/* Tarifa Unitaria */}
                      <td className="py-3 px-4 text-right whitespace-nowrap text-muted-foreground">
                        {m.tarifaUnitaria ? formatCurrency(m.tarifaUnitaria) : '-'}
                      </td>

                      {/* Costo Total */}
                      <td className="py-3 px-4 text-right whitespace-nowrap font-semibold text-emerald-600 dark:text-emerald-400">
                        {formatCurrency(m.costoTotal)}
                      </td>

                      {/* Estado / Anomalía */}
                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        {m.anomaliaDetectada === 'S' ? (
                          <span
                            title={m.observacionAnomalia || 'Variación superior al +50% sobre el promedio'}
                            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30 cursor-help"
                          >
                            <AlertTriangle className="w-3 h-3 text-amber-500" />
                            Anomalía (+50%)
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-500/10 text-emerald-700 dark:text-emerald-400">
                            <CheckCircle2 className="w-3 h-3 text-emerald-500" />
                            Normal
                          </span>
                        )}
                      </td>

                      {/* Evidencia Foto */}
                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        {m.fotoMedidorUrl ? (
                          <button
                            onClick={() => {
                              setFotoVisualizar(m.fotoMedidorUrl);
                              setModalFotoOpen(true);
                            }}
                            className="inline-flex items-center gap-1 text-primary hover:underline text-[11px]"
                          >
                            <Camera className="w-3.5 h-3.5" />
                            Ver Foto
                          </button>
                        ) : (
                          <span className="text-muted-foreground text-[10px]">—</span>
                        )}
                      </td>

                      {/* Acciones */}
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-muted-foreground hover:text-foreground"
                            onClick={() => handleOpenEdit(m)}
                            title="Editar medición"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-destructive hover:bg-destructive/10"
                            onClick={() => {
                              setMedicionAEliminar(m);
                              setModalDeleteOpen(true);
                            }}
                            title="Eliminar medición"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Crear / Editar Medición */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={isEditing ? 'Editar Medición de Consumo' : 'Registrar Nueva Medición de Consumo'}
        size="lg"
      >
        <form onSubmit={handleSave} className="space-y-4 pt-2">
          {/* Fila 1: Servicio y Unidad */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Tipo de Servicio *</Label>
              <select
                value={form.tipoServicio}
                onChange={(e) => {
                  const srv = e.target.value;
                  const uMed = srv === 'ENERGIA' ? 'KWH' : 'M3';
                  setForm((prev) => ({
                    ...prev,
                    tipoServicio: srv,
                    unidadMedida: uMed,
                  }));
                }}
                className="w-full h-9 px-3 text-xs rounded-md border border-input bg-background text-foreground"
              >
                <option value="AGUA">Agua Potable (m³)</option>
                <option value="ENERGIA">Energía Eléctrica (kWh)</option>
                <option value="GAS">Gas Natural (m³)</option>
              </select>
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Ubicación / Unidad</Label>
              <select
                value={form.idUnidad}
                onChange={(e) => {
                  const uId = e.target.value;
                  setForm((prev) => ({ ...prev, idUnidad: uId }));
                  if (uId) {
                    handleConsultarUltimaLectura(form.tipoServicio, uId, form.numeroMedidor);
                  }
                }}
                className="w-full h-9 px-3 text-xs rounded-md border border-input bg-background text-foreground"
              >
                <option value="">Zonas Comunes (Medidor Principal)</option>
                {unidades.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.identificador || `Unidad #${u.id}`}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Fila 2: Número de Medidor y Período */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="space-y-1 sm:col-span-2">
              <div className="flex items-center justify-between">
                <Label className="text-xs font-semibold">Número de Medidor *</Label>
                <button
                  type="button"
                  onClick={() =>
                    handleConsultarUltimaLectura(
                      form.tipoServicio,
                      form.idUnidad,
                      form.numeroMedidor
                    )
                  }
                  className="text-[10px] text-primary hover:underline flex items-center gap-1"
                >
                  <RefreshCw className="w-2.5 h-2.5" /> Traer última lectura
                </button>
              </div>
              <Input
                placeholder="Ej: MED-AGUA-GEN-01"
                value={form.numeroMedidor}
                onChange={(e) => setForm({ ...form, numeroMedidor: e.target.value.toUpperCase() })}
                className="h-9 text-xs uppercase"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Período (YYYY-MM) *</Label>
              <Input
                type="month"
                value={form.periodo}
                onChange={(e) => setForm({ ...form, periodo: e.target.value })}
                className="h-9 text-xs"
              />
            </div>
          </div>

          {/* Fila 3: Lectura Anterior, Lectura Actual y Unidad de Medida */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Lectura Anterior *</Label>
              <Input
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                value={form.lecturaAnterior}
                onChange={(e) => setForm({ ...form, lecturaAnterior: e.target.value })}
                className="h-9 text-xs font-mono"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Lectura Actual *</Label>
              <Input
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                value={form.lecturaActual}
                onChange={(e) => setForm({ ...form, lecturaActual: e.target.value })}
                className="h-9 text-xs font-mono"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Unidad Medida</Label>
              <Input
                value={form.unidadMedida}
                onChange={(e) => setForm({ ...form, unidadMedida: e.target.value.toUpperCase() })}
                className="h-9 text-xs uppercase font-mono"
              />
            </div>
          </div>

          {/* Tarjeta de Cálculo en Vivo */}
          <div className="rounded-lg border border-primary/20 bg-primary/5 p-3 flex items-center justify-between">
            <div className="space-y-0.5">
              <span className="text-[11px] text-muted-foreground uppercase tracking-wider font-semibold">
                Consumo Resultante
              </span>
              <div className="text-base font-bold text-foreground">
                {consumoEstimado} {form.unidadMedida}
              </div>
            </div>
            <div className="text-right space-y-0.5">
              <span className="text-[11px] text-muted-foreground uppercase tracking-wider font-semibold">
                Costo Proyectado
              </span>
              <div className="text-base font-bold text-emerald-600 dark:text-emerald-400">
                {formatCurrency(costoEstimado)}
              </div>
            </div>
          </div>

          {/* Fila 4: Tarifa Unitaria y Fecha de Toma */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-semibold">Tarifa Unitaria ($ COP)</Label>
              <Input
                type="number"
                step="0.01"
                min="0"
                placeholder="Ej: 6500"
                value={form.tarifaUnitaria}
                onChange={(e) => setForm({ ...form, tarifaUnitaria: e.target.value })}
                className="h-9 text-xs"
              />
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Fecha de Toma de Lectura</Label>
              <Input
                type="date"
                value={form.fechaTomaLectura}
                onChange={(e) => setForm({ ...form, fechaTomaLectura: e.target.value })}
                className="h-9 text-xs"
              />
            </div>
          </div>

          {/* Fila 5: URL de Foto Comprobante */}
          <div className="space-y-1">
            <Label className="text-xs font-semibold">URL de Foto o Evidencia del Medidor</Label>
            <Input
              placeholder="https://..."
              value={form.fotoMedidorUrl}
              onChange={(e) => setForm({ ...form, fotoMedidorUrl: e.target.value })}
              className="h-9 text-xs"
            />
          </div>

          {/* Fila 6: Observaciones de Anomalía */}
          <div className="space-y-1">
            <Label className="text-xs font-semibold">Observaciones / Notas Adicionales</Label>
            <Input
              placeholder="Ej: Medidor con precinto intacto. Toma regular."
              value={form.observacionAnomalia}
              onChange={(e) => setForm({ ...form, observacionAnomalia: e.target.value })}
              className="h-9 text-xs"
            />
          </div>

          {/* Botones de Acción */}
          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isSubmitting}
              className="bg-primary text-primary-foreground hover:bg-primary/90 min-w-[120px]"
            >
              {isSubmitting ? 'Guardando...' : isEditing ? 'Guardar Cambios' : 'Registrar Medición'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Confirmación de Eliminación */}
      <Modal
        open={modalDeleteOpen}
        onClose={() => setModalDeleteOpen(false)}
        title="Confirmar Eliminación"
        size="sm"
      >
        <div className="space-y-4 pt-2">
          <p className="text-xs text-muted-foreground">
            ¿Está seguro de que desea eliminar la medición del medidor{' '}
            <strong className="text-foreground">{medicionAEliminar?.numeroMedidor}</strong> correspondiente
            al período <strong className="text-foreground">{medicionAEliminar?.periodo}</strong>?
          </p>
          <div className="p-3 rounded-md bg-destructive/10 text-destructive text-xs">
            Esta acción recalculará los consolidados de consumo y costos del período.
          </div>
          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setModalDeleteOpen(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={handleDeleteConfirm}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Eliminando...' : 'Eliminar'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal Visualizador de Foto */}
      <Modal
        open={modalFotoOpen}
        onClose={() => setModalFotoOpen(false)}
        title="Evidencia Fotográfica del Medidor"
        size="md"
      >
        <div className="space-y-4 pt-2 text-center">
          {fotoVisualizar ? (
            <div className="rounded-lg overflow-hidden border border-border bg-black/5">
              <img
                src={fotoVisualizar}
                alt="Foto del Medidor"
                className="w-full max-h-[60vh] object-contain mx-auto"
                onError={(e) => {
                  e.target.onerror = null;
                  e.target.src = 'https://placehold.co/600x400?text=Foto+No+Disponible';
                }}
              />
            </div>
          ) : (
            <div className="py-8 text-muted-foreground text-xs">No hay foto disponible.</div>
          )}
          <div className="flex justify-end">
            <Button size="sm" variant="outline" onClick={() => setModalFotoOpen(false)}>
              Cerrar
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
