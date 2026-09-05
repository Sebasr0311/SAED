import { useState, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  AlertTriangle,
  BarChart3,
  Building,
  CheckCircle2,
  Clock,
  FileText,
  Receipt,
  RefreshCw,
  Search,
  ShieldAlert,
  Wallet,
  X,
} from 'lucide-react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { formatCurrency } from '../lib/utils.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';

const TABS = [
  { id: 'unidades', label: 'Cartera por Unidad', icon: Building },
  { id: 'cuotas', label: 'Detalle de Cuotas', icon: Receipt },
  { id: 'antiguedad', label: 'Antigüedad de Cartera', icon: BarChart3 },
];

/**
 * CarteraPage 2.0 — Centro de Gestión de Cartera y Cobros.
 * Modern Enterprise SaaS / PropTech Premium.
 *
 * Mantiene intacta la lógica financiera de Oracle ATP y los contratos REST de:
 *  - GET  /api/v1/cartera
 *  - GET  /api/v1/cartera/resumen
 *  - GET  /api/v1/cartera/antiguedad
 *  - GET  /api/v1/cuotas
 *  - POST /api/v1/cartera/recalcular
 */
export default function CarteraPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [tabActiva, setTabActiva] = useState('unidades');
  const [recalculando, setRecalculando] = useState(false);
  const [searchUnidades, setSearchUnidades] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('TODOS');
  const [searchCuotas, setSearchCuotas] = useState('');
  const [filtroEstadoCuota, setFiltroEstadoCuota] = useState('TODOS');

  // 1. Cartera consolidada por unidad
  const {
    data: carteraData,
    loading: carteraLoading,
    error: carteraError,
    refetch: refetchCartera,
  } = useFetch(() => tenantApi.get('/cartera'), [tenant.activeAssignmentId]);

  // 2. Resumen ejecutivo de cartera
  const {
    data: resumenData,
    loading: resumenLoading,
    error: resumenError,
    refetch: refetchResumen,
  } = useFetch(() => tenantApi.get('/cartera/resumen'), [tenant.activeAssignmentId]);

  // 3. Distribución por antigüedad de mora
  const {
    data: antiguedadData,
    loading: antiguedadLoading,
    error: antiguedadError,
    refetch: refetchAntiguedad,
  } = useFetch(() => tenantApi.get('/cartera/antiguedad'), [tenant.activeAssignmentId]);

  // 4. Detalle de cuotas individuales
  const {
    data: cuotasData,
    loading: cuotasLoading,
    refetch: refetchCuotas,
  } = useFetch(() => tenantApi.get('/cuotas'), [tenant.activeAssignmentId]);

  // Refresco consolidado
  const refetchAll = useCallback(() => {
    refetchCartera();
    refetchResumen();
    refetchAntiguedad();
    refetchCuotas();
  }, [refetchCartera, refetchResumen, refetchAntiguedad, refetchCuotas]);

  // Ejecución de recálculo en backend
  const recalcular = useCallback(async () => {
    setRecalculando(true);
    try {
      await tenantApi.post('/cartera/recalcular');
      toast.success('Cartera recalculada exitosamente desde cuotas y pagos');
      refetchAll();
    } catch (err) {
      toast.error(err.message || 'Error al recalcular la cartera');
    } finally {
      setRecalculando(false);
    }
  }, [refetchAll, tenantApi]);

  // Normalización de listas
  const unidades = useMemo(
    () => carteraData?.items || (Array.isArray(carteraData) ? carteraData : []),
    [carteraData]
  );

  const resumen = useMemo(
    () => resumenData?.items || resumenData?.raw || resumenData || {},
    [resumenData]
  );

  const antiguedad = useMemo(
    () => antiguedadData?.items || (Array.isArray(antiguedadData) ? antiguedadData : []),
    [antiguedadData]
  );

  const cuotas = useMemo(
    () => cuotasData?.items || (Array.isArray(cuotasData) ? cuotasData : []),
    [cuotasData]
  );

  // Cálculos de KPIs con datos reales y fallback defensivo
  const kpis = useMemo(() => {
    const totalCarteraReal =
      resumen.TOTAL_CARTERA != null
        ? Number(resumen.TOTAL_CARTERA)
        : unidades.reduce((acc, u) => acc + Number(u.SALDO_TOTAL || 0), 0);

    const totalMoraReal =
      resumen.TOTAL_MORA != null
        ? Number(resumen.TOTAL_MORA)
        : unidades.reduce(
            (acc, u) =>
              acc +
              Number(u.SALDO_MORA_30 || 0) +
              Number(u.SALDO_MORA_60 || 0) +
              Number(u.SALDO_MORA_90_MAS || 0),
            0
          );

    const unidadesAlDia =
      resumen.COUNT_AL_DIA != null
        ? Number(resumen.COUNT_AL_DIA)
        : unidades.filter((u) => (u.ESTADO_CARTERA || 'AL_DIA') === 'AL_DIA').length;

    const unidadesEnMora =
      resumen.COUNT_MORA_LEVE != null ||
      resumen.COUNT_MORA_MEDIA != null ||
      resumen.COUNT_MORA_GRAVE != null
        ? Number(resumen.COUNT_MORA_LEVE || 0) +
          Number(resumen.COUNT_MORA_MEDIA || 0) +
          Number(resumen.COUNT_MORA_GRAVE || 0)
        : unidades.filter((u) => (u.ESTADO_CARTERA || 'AL_DIA') !== 'AL_DIA').length;

    return {
      totalCartera: totalCarteraReal,
      totalMora: totalMoraReal,
      alDia: unidadesAlDia,
      enMora: unidadesEnMora,
    };
  }, [resumen, unidades]);

  // Filtrado de unidades
  const unidadesFiltradas = useMemo(() => {
    return unidades.filter((u) => {
      const aptoLabel = String(u.NUMERO_APARTAMENTO || u.ID_UNIDAD || u.id_unidad || '');
      const coincideSearch =
        !searchUnidades || aptoLabel.toLowerCase().includes(searchUnidades.toLowerCase());

      const estado = u.ESTADO_CARTERA || 'AL_DIA';
      const coincideEstado =
        filtroEstado === 'TODOS'
          ? true
          : filtroEstado === 'AL_DIA'
          ? estado === 'AL_DIA'
          : filtroEstado === 'EN_MORA'
          ? estado !== 'AL_DIA'
          : estado === filtroEstado;

      return coincideSearch && coincideEstado;
    });
  }, [unidades, searchUnidades, filtroEstado]);

  // Filtrado de cuotas
  const cuotasFiltradas = useMemo(() => {
    return cuotas.filter((c) => {
      const term = searchCuotas.toLowerCase();
      const coincideSearch =
        !searchCuotas ||
        [c.concepto, c.numeroApartamento, c.nombreResidente, c.periodo]
          .filter(Boolean)
          .some((val) => String(val).toLowerCase().includes(term));

      const estado = c.estado || 'PENDIENTE';
      const coincideEstado =
        filtroEstadoCuota === 'TODOS' ? true : estado === filtroEstadoCuota;

      return coincideSearch && coincideEstado;
    });
  }, [cuotas, searchCuotas, filtroEstadoCuota]);

  // Formateador de estado para Badge
  const getEstadoBadge = useCallback((estado) => {
    const e = estado || 'AL_DIA';
    switch (e) {
      case 'AL_DIA':
        return <Badge variant="success">Al día</Badge>;
      case 'MORA_LEVE':
        return <Badge variant="warning">Mora 30d</Badge>;
      case 'MORA_MEDIA':
        return <Badge variant="warning">Mora 60d</Badge>;
      case 'MORA_GRAVE':
        return <Badge variant="destructive">Mora 90d+</Badge>;
      default:
        return <Badge variant="secondary">{e.replace('_', ' ')}</Badge>;
    }
  }, []);

  const getCuotaBadge = useCallback((estado) => {
    const e = estado || 'PENDIENTE';
    switch (e) {
      case 'PAGADO':
      case 'PAGADA':
        return <Badge variant="success">Pagada</Badge>;
      case 'PENDIENTE':
        return <Badge variant="warning">Pendiente</Badge>;
      case 'VENCIDA':
        return <Badge variant="destructive">Vencida</Badge>;
      default:
        return <Badge variant="secondary">{e}</Badge>;
    }
  }, []);

  const hayErrorCritico = carteraError && resumenError && antiguedadError;

  if (hayErrorCritico) {
    return (
      <PageContainer>
        <ErrorState
          title="Error al consultar cartera"
          message="No se pudieron sincronizar los balances financieros de la copropiedad. Verifique su asignación activa."
          onRetry={refetchAll}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="space-y-6">
      {/* 1. Header Contextual Enterprise */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Cartera y Cobros
            </h1>
            <Badge
              variant="outline"
              className="bg-primary/5 text-primary border-primary/20 text-xs font-semibold"
            >
              ADMIN_PROPIEDAD
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground">
            Control de recaudo, saldos por unidad y antigüedad de cartera
          </p>
        </div>

        {/* Acciones Rápidas */}
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          <Button
            variant="outline"
            size="sm"
            onClick={refetchAll}
            disabled={carteraLoading || resumenLoading || antiguedadLoading || recalculando}
            className="text-xs min-h-[44px] sm:min-h-9"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1.5 ${
                carteraLoading || resumenLoading || antiguedadLoading ? 'animate-spin text-primary' : ''
              }`}
              aria-hidden="true"
            />
            Actualizar
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={recalcular}
            disabled={recalculando}
            className="text-xs min-h-[44px] sm:min-h-9 shadow-xs"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1.5 ${recalculando ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            {recalculando ? 'Recalculando...' : 'Recalcular Cartera'}
          </Button>
        </div>
      </div>

      {/* 2. Grid de KPIs Financieros Consolidados */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total Cartera"
          value={formatCurrency(kpis.totalCartera)}
          subtitle={`${unidades.length} unidades censadas`}
          icon={Wallet}
          variant="primary"
        />
        <MetricCard
          label="Total en Mora"
          value={formatCurrency(kpis.totalMora)}
          subtitle="Deuda vencida en copropiedad"
          icon={ShieldAlert}
          variant={kpis.totalMora > 0 ? 'warning' : 'secondary'}
        />
        <MetricCard
          label="Unidades al Día"
          value={kpis.alDia}
          subtitle="Cuentas de cobro saldadas"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Unidades en Mora"
          value={kpis.enMora}
          subtitle={
            kpis.enMora > 0
              ? `${kpis.enMora} requieren gestión de recaudo`
              : 'Sin morosidad registrada'
          }
          icon={AlertTriangle}
          variant={kpis.enMora > 0 ? 'destructive' : 'secondary'}
        />
      </div>

      {/* 3. Navegación por Tabs Enterprise */}
      <div role="tablist" className="flex items-center gap-2 border-b border-border/70 overflow-x-auto pb-1">
        {TABS.map((tab) => {
          const IconComp = tab.icon;
          const isActiva = tabActiva === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActiva}
              type="button"
              onClick={() => setTabActiva(tab.id)}
              className={`flex items-center gap-2 px-4 py-2.5 text-xs sm:text-sm font-semibold rounded-t-lg transition-all border-b-2 whitespace-nowrap min-h-[44px] sm:min-h-9 ${
                isActiva
                  ? 'border-primary text-primary bg-primary/5'
                  : 'border-transparent text-muted-foreground hover:text-foreground hover:bg-muted/40'
              }`}
            >
              <IconComp className={`h-4 w-4 ${isActiva ? 'text-primary' : 'text-muted-foreground'}`} />
              {tab.label}
              {tab.id === 'unidades' && (
                <span className="ml-1 px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-muted text-muted-foreground">
                  {unidades.length}
                </span>
              )}
              {tab.id === 'cuotas' && cuotas.length > 0 && (
                <span className="ml-1 px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-muted text-muted-foreground">
                  {cuotas.length}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* 4. TAB 1: Cartera por Unidad */}
      {tabActiva === 'unidades' && (
        <Card className="border-border/80 shadow-xs overflow-hidden">
          <CardHeader className="p-4 sm:p-5 border-b border-border/50 bg-card">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="flex flex-1 items-center gap-2 max-w-md">
                <div className="relative flex-1">
                  <Search
                    className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none"
                    aria-hidden="true"
                  />
                  <input
                    type="text"
                    value={searchUnidades}
                    onChange={(e) => setSearchUnidades(e.target.value)}
                    placeholder="Buscar por número de apartamento..."
                    className="w-full pl-9 pr-9 py-2 text-xs sm:text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-foreground placeholder:text-muted-foreground"
                    aria-label="Buscar unidad"
                  />
                  {searchUnidades && (
                    <button
                      type="button"
                      onClick={() => setSearchUnidades('')}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-muted-foreground hover:text-foreground rounded-full"
                      aria-label="Limpiar búsqueda"
                    >
                      <X className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  )}
                </div>

                <select
                  value={filtroEstado}
                  onChange={(e) => setFiltroEstado(e.target.value)}
                  className="py-2 px-3 text-xs sm:text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-foreground shrink-0"
                  aria-label="Filtrar por estado de cartera"
                >
                  <option value="TODOS">Todos los estados</option>
                  <option value="AL_DIA">Al día</option>
                  <option value="EN_MORA">En mora (cualquiera)</option>
                  <option value="MORA_LEVE">Mora 30d</option>
                  <option value="MORA_MEDIA">Mora 60d</option>
                  <option value="MORA_GRAVE">Mora 90d+</option>
                </select>
              </div>

              <span className="text-xs text-muted-foreground self-end sm:self-center">
                Mostrando <strong className="text-foreground">{unidadesFiltradas.length}</strong> de{' '}
                <strong className="text-foreground">{unidades.length}</strong> unidades
              </span>
            </div>
          </CardHeader>

          <CardContent className="p-0">
            {carteraLoading ? (
              <div className="p-8">
                <LoadingState
                  message="Consultando cartera por unidad..."
                  description="Obteniendo saldos de cuentas corrientes y envejecimiento de deuda"
                />
              </div>
            ) : unidadesFiltradas.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-12 text-center text-muted-foreground">
                <Building className="h-8 w-8 text-muted-foreground/60 mb-2" aria-hidden="true" />
                <p className="text-sm font-semibold text-foreground">
                  {searchUnidades || filtroEstado !== 'TODOS'
                    ? 'Sin coincidencias con los filtros aplicados'
                    : 'No hay registros de cartera'}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {searchUnidades || filtroEstado !== 'TODOS'
                    ? 'Intente cambiando el criterio de búsqueda o el filtro de estado.'
                    : 'Haga clic en "Recalcular Cartera" para consolidar los saldos desde las cuotas.'}
                </p>
              </div>
            ) : (
              <>
                {/* Desktop & Tablet Table (md+) */}
                <div className="hidden md:block overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-border/70 bg-muted/30 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                        <th className="py-3 px-4">Unidad / Apartamento</th>
                        <th className="py-3 px-4 text-right">Saldo Corriente</th>
                        <th className="py-3 px-4 text-right">Mora 30d</th>
                        <th className="py-3 px-4 text-right">Mora 60d</th>
                        <th className="py-3 px-4 text-right">Mora 90d+</th>
                        <th className="py-3 px-4 text-right font-bold">Saldo Total</th>
                        <th className="py-3 px-4 text-center">Estado</th>
                        <th className="py-3 px-4 text-right">Fecha Corte</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/50 text-xs">
                      {unidadesFiltradas.map((u, idx) => {
                        const id = u.ID_CARTERA || u.id || idx;
                        const apto = u.NUMERO_APARTAMENTO || u.ID_UNIDAD || u.id_unidad || 'Unidad';

                        return (
                          <tr key={id} className="hover:bg-muted/40 transition-colors group">
                            <td className="py-3.5 px-4 font-semibold text-foreground">
                              <Badge variant="outline" className="font-semibold bg-muted/50">
                                <Building className="h-3 w-3 mr-1 text-primary" aria-hidden="true" />
                                {apto.startsWith('Apto') ? apto : `Apto ${apto}`}
                              </Badge>
                            </td>
                            <td className="py-3.5 px-4 text-right font-mono text-muted-foreground">
                              {formatCurrency(Number(u.SALDO_CORRIENTE || 0))}
                            </td>
                            <td className="py-3.5 px-4 text-right font-mono text-amber-600 dark:text-amber-400">
                              {formatCurrency(Number(u.SALDO_MORA_30 || 0))}
                            </td>
                            <td className="py-3.5 px-4 text-right font-mono text-orange-600 dark:text-orange-400">
                              {formatCurrency(Number(u.SALDO_MORA_60 || 0))}
                            </td>
                            <td className="py-3.5 px-4 text-right font-mono text-rose-600 dark:text-rose-400">
                              {formatCurrency(Number(u.SALDO_MORA_90_MAS || 0))}
                            </td>
                            <td className="py-3.5 px-4 text-right font-mono font-bold text-foreground">
                              {formatCurrency(Number(u.SALDO_TOTAL || 0))}
                            </td>
                            <td className="py-3.5 px-4 text-center">
                              {getEstadoBadge(u.ESTADO_CARTERA)}
                            </td>
                            <td className="py-3.5 px-4 text-right text-[11px] text-muted-foreground font-mono">
                              {u.FECHA_CORTE || '—'}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Vista Móvil Adaptativa (md:hidden) */}
                <div className="md:hidden divide-y divide-border/60">
                  {unidadesFiltradas.map((u, idx) => {
                    const id = u.ID_CARTERA || u.id || idx;
                    const apto = u.NUMERO_APARTAMENTO || u.ID_UNIDAD || u.id_unidad || 'Unidad';

                    return (
                      <div key={id} className="p-4 space-y-2.5 hover:bg-muted/30 transition-colors">
                        <div className="flex items-center justify-between">
                          <Badge variant="outline" className="font-semibold text-xs bg-muted/50">
                            <Building className="h-3 w-3 mr-1 text-primary" aria-hidden="true" />
                            {apto.startsWith('Apto') ? apto : `Apto ${apto}`}
                          </Badge>
                          {getEstadoBadge(u.ESTADO_CARTERA)}
                        </div>

                        <div className="flex items-baseline justify-between pt-1">
                          <span className="text-xs text-muted-foreground">Saldo Total a Pagar:</span>
                          <span className="text-base font-bold text-foreground font-mono">
                            {formatCurrency(Number(u.SALDO_TOTAL || 0))}
                          </span>
                        </div>

                        <div className="grid grid-cols-2 gap-2 text-[11px] pt-1 border-t border-border/40 text-muted-foreground font-mono">
                          <div>
                            <span>Corriente: </span>
                            <span className="font-semibold text-foreground">
                              {formatCurrency(Number(u.SALDO_CORRIENTE || 0))}
                            </span>
                          </div>
                          <div>
                            <span>Mora 30d: </span>
                            <span className="font-semibold text-amber-600 dark:text-amber-400">
                              {formatCurrency(Number(u.SALDO_MORA_30 || 0))}
                            </span>
                          </div>
                          <div>
                            <span>Mora 60d: </span>
                            <span className="font-semibold text-orange-600 dark:text-orange-400">
                              {formatCurrency(Number(u.SALDO_MORA_60 || 0))}
                            </span>
                          </div>
                          <div>
                            <span>Mora 90d+: </span>
                            <span className="font-semibold text-rose-600 dark:text-rose-400">
                              {formatCurrency(Number(u.SALDO_MORA_90_MAS || 0))}
                            </span>
                          </div>
                        </div>

                        {u.FECHA_CORTE && (
                          <p className="text-[10px] text-muted-foreground text-right pt-1">
                            Corte: {u.FECHA_CORTE}
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}

      {/* 5. TAB 2: Detalle de Cuotas */}
      {tabActiva === 'cuotas' && (
        <Card className="border-border/80 shadow-xs overflow-hidden">
          <CardHeader className="p-4 sm:p-5 border-b border-border/50 bg-card">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div className="flex flex-1 items-center gap-2 max-w-md">
                <div className="relative flex-1">
                  <Search
                    className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none"
                    aria-hidden="true"
                  />
                  <input
                    type="text"
                    value={searchCuotas}
                    onChange={(e) => setSearchCuotas(e.target.value)}
                    placeholder="Buscar por concepto, apartamento, residente..."
                    className="w-full pl-9 pr-9 py-2 text-xs sm:text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-foreground placeholder:text-muted-foreground"
                    aria-label="Buscar cuotas"
                  />
                  {searchCuotas && (
                    <button
                      type="button"
                      onClick={() => setSearchCuotas('')}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-muted-foreground hover:text-foreground rounded-full"
                      aria-label="Limpiar búsqueda"
                    >
                      <X className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  )}
                </div>

                <select
                  value={filtroEstadoCuota}
                  onChange={(e) => setFiltroEstadoCuota(e.target.value)}
                  className="py-2 px-3 text-xs sm:text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-foreground shrink-0"
                  aria-label="Filtrar por estado de cuota"
                >
                  <option value="TODOS">Todos los estados</option>
                  <option value="PENDIENTE">Pendientes</option>
                  <option value="PAGADO">Pagadas</option>
                  <option value="VENCIDA">Vencidas</option>
                </select>
              </div>

              <span className="text-xs text-muted-foreground self-end sm:self-center">
                Mostrando <strong className="text-foreground">{cuotasFiltradas.length}</strong> de{' '}
                <strong className="text-foreground">{cuotas.length}</strong> cuotas
              </span>
            </div>
          </CardHeader>

          <CardContent className="p-0">
            {cuotasLoading ? (
              <div className="p-8">
                <LoadingState
                  message="Consultando obligaciones y cuotas..."
                  description="Sincronizando cuentas de cobro de administración"
                />
              </div>
            ) : cuotasFiltradas.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-12 text-center text-muted-foreground">
                <Receipt className="h-8 w-8 text-muted-foreground/60 mb-2" aria-hidden="true" />
                <p className="text-sm font-semibold text-foreground">
                  {searchCuotas || filtroEstadoCuota !== 'TODOS'
                    ? 'Sin cuotas que coincidan con la búsqueda'
                    : 'No hay cuotas registradas'}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Las obligaciones generadas para las unidades aparecerán reflejadas aquí.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-border/70 bg-muted/30 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                      <th className="py-3 px-4 w-14">ID</th>
                      <th className="py-3 px-4">Concepto / Obligación</th>
                      <th className="py-3 px-4">Inmueble / Titular</th>
                      <th className="py-3 px-4 text-center">Periodo</th>
                      <th className="py-3 px-4 text-right">Valor Total</th>
                      <th className="py-3 px-4 text-right">Saldo Pendiente</th>
                      <th className="py-3 px-4 text-center">Estado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/50 text-xs">
                    {cuotasFiltradas.map((c) => {
                      const id = c.id || c.idCuota;
                      const saldo = Number(c.saldoPendiente != null ? c.saldoPendiente : c.valorTotal || 0);

                      return (
                        <tr key={id} className="hover:bg-muted/40 transition-colors">
                          <td className="py-3.5 px-4 font-mono text-[11px] text-muted-foreground">
                            #{id}
                          </td>
                          <td className="py-3.5 px-4">
                            <p className="font-semibold text-foreground">
                              {c.concepto || `Cuota de Administración #${id}`}
                            </p>
                          </td>
                          <td className="py-3.5 px-4">
                            <p className="font-medium text-foreground">
                              {c.numeroApartamento ? `Apto ${c.numeroApartamento}` : 'Unidad'}
                            </p>
                            {c.nombreResidente && (
                              <p className="text-[11px] text-muted-foreground">
                                {c.nombreResidente}
                              </p>
                            )}
                          </td>
                          <td className="py-3.5 px-4 text-center font-mono text-[11px] text-muted-foreground">
                            {c.periodo || '—'}
                          </td>
                          <td className="py-3.5 px-4 text-right font-mono text-foreground">
                            {formatCurrency(Number(c.valorTotal || c.valorBase || 0))}
                          </td>
                          <td className="py-3.5 px-4 text-right font-mono font-bold">
                            <span
                              className={
                                saldo > 0
                                  ? 'text-amber-600 dark:text-amber-400'
                                  : 'text-emerald-600 dark:text-emerald-400'
                              }
                            >
                              {formatCurrency(saldo)}
                            </span>
                          </td>
                          <td className="py-3.5 px-4 text-center">
                            {getCuotaBadge(c.estado)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 6. TAB 3: Antigüedad de Cartera */}
      {tabActiva === 'antiguedad' && (
        <Card className="border-border/80 shadow-xs">
          <CardHeader className="border-b border-border/50 pb-3">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <Clock className="h-4 w-4 text-primary" aria-hidden="true" />
                  Distribución y Envejecimiento de Deuda
                </CardTitle>
                <p className="text-xs text-muted-foreground">
                  Estratificación de saldos en mora según días de vencimiento
                </p>
              </div>
              <Badge variant="outline" className="text-xs">
                Base: {formatCurrency(kpis.totalCartera)}
              </Badge>
            </div>
          </CardHeader>

          <CardContent className="pt-5 space-y-6">
            {antiguedadLoading ? (
              <div className="p-8">
                <LoadingState
                  message="Calculando bandas de antigüedad..."
                  description="Procesando rangos de vencimiento a 30, 60 y 90+ días"
                />
              </div>
            ) : antiguedad.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-12 text-center text-muted-foreground">
                <CheckCircle2 className="h-8 w-8 text-emerald-500 mb-2" aria-hidden="true" />
                <p className="text-sm font-semibold text-foreground">Sin cartera envejecida</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  No se registran obligaciones pendientes distribuidas en rangos de mora.
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                {antiguedad.map((a, idx) => {
                  const rango = a.RANGO || `Rango ${idx + 1}`;
                  const monto = Number(a.TOTAL_SALDO || 0);
                  const cantidad = Number(a.CANTIDAD_CUOTAS || 0);
                  const totalGeneral = kpis.totalCartera > 0 ? kpis.totalCartera : 1;
                  const pct = Math.min(100, Math.round((monto / totalGeneral) * 100));

                  const isVigente = rango.toUpperCase().includes('VIGENTE');
                  const isMoraGrave = rango.includes('90') || rango.includes('60');

                  return (
                    <div
                      key={rango}
                      className="p-3.5 rounded-xl border border-border/70 bg-card hover:bg-muted/30 transition-colors space-y-2"
                    >
                      <div className="flex items-center justify-between text-xs sm:text-sm">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-foreground">{rango}</span>
                          <Badge
                            variant={isVigente ? 'success' : isMoraGrave ? 'destructive' : 'warning'}
                            className="text-[10px] font-mono px-1.5 py-0"
                          >
                            {cantidad} {cantidad === 1 ? 'cuota' : 'cuotas'}
                          </Badge>
                        </div>
                        <div className="text-right">
                          <span className="font-mono font-bold text-foreground">
                            {formatCurrency(monto)}
                          </span>
                          <span className="text-xs text-muted-foreground ml-2">({pct}%)</span>
                        </div>
                      </div>

                      {/* Barra de Progreso Semántica */}
                      <div className="h-2 w-full bg-muted rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${
                            isVigente
                              ? 'bg-emerald-500'
                              : isMoraGrave
                              ? 'bg-rose-500'
                              : 'bg-amber-500'
                          }`}
                          style={{ width: `${Math.max(pct, 2)}%` }}
                        />
                      </div>
                    </div>
                  );
                })}

                <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 flex items-start gap-3 mt-4">
                  <FileText className="h-5 w-5 text-primary shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="space-y-1 text-xs">
                    <p className="font-semibold text-foreground">
                      Criterio de Cobro y Conciliación
                    </p>
                    <p className="text-muted-foreground leading-relaxed">
                      Los saldos vencidos mayores a 60 días ameritan notificación formal o proceso de
                      cobro prejurídico conforme al reglamento de propiedad horizontal. El botón
                      "Recalcular Cartera" ejecuta el procedimiento <code>MERGE</code> consolidando
                      las cuotas devengadas contra los pagos acreditados.
                    </p>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </PageContainer>
  );
}
