import { useState, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  AlertTriangle,
  BarChart3,
  Building,
  CheckCircle2,
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
import { cn, formatCurrency } from '../lib/utils.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/input.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';

const quiet = (fn) => () => fn().catch(() => {});

const SELECT_CLASS =
  'h-9 rounded-md border border-input bg-surface px-3 text-sm text-on-surface shadow-xs transition-[border-color,box-shadow] duration-150 hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25';

/**
 * CarteraPage — receivables by unit, fee detail and aging.
 * REST contracts: GET /cartera, /cartera/resumen, /cartera/antiguedad, /cuotas; POST /cartera/recalcular.
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

  const {
    data: carteraData,
    loading: carteraLoading,
    error: carteraError,
    refetch: refetchCartera,
  } = useFetch(() => tenantApi.get('/cartera'), [tenant.activeAssignmentId]);

  const {
    data: resumenData,
    loading: resumenLoading,
    error: resumenError,
    refetch: refetchResumen,
  } = useFetch(() => tenantApi.get('/cartera/resumen'), [tenant.activeAssignmentId]);

  const {
    data: antiguedadData,
    loading: antiguedadLoading,
    error: antiguedadError,
    refetch: refetchAntiguedad,
  } = useFetch(() => tenantApi.get('/cartera/antiguedad'), [tenant.activeAssignmentId]);

  const {
    data: cuotasData,
    loading: cuotasLoading,
    error: cuotasError,
    refetch: refetchCuotas,
  } = useFetch(() => tenantApi.get('/cuotas'), [tenant.activeAssignmentId]);

  const refetchAll = useCallback(() => {
    [refetchCartera, refetchResumen, refetchAntiguedad, refetchCuotas].forEach((fn) =>
      fn().catch(() => {})
    );
  }, [refetchCartera, refetchResumen, refetchAntiguedad, refetchCuotas]);

  const recalcular = useCallback(async () => {
    setRecalculando(true);
    try {
      await tenantApi.post('/cartera/recalcular');
      toast.success('Cartera recalculada desde cuotas y pagos');
      refetchAll();
    } catch (err) {
      toast.error(err.message || 'Error al recalcular la cartera');
    } finally {
      setRecalculando(false);
    }
  }, [refetchAll, tenantApi]);

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
      resumen.COUNT_MORA_30 != null ||
      resumen.COUNT_MORA_60 != null ||
      resumen.COUNT_MORA_CRITICA != null ||
      resumen.COUNT_COBRO_JURIDICO != null
        ? Number(resumen.COUNT_MORA_30 || 0) +
          Number(resumen.COUNT_MORA_60 || 0) +
          Number(resumen.COUNT_MORA_CRITICA || 0) +
          Number(resumen.COUNT_COBRO_JURIDICO || 0)
        : unidades.filter((u) => (u.ESTADO_CARTERA || 'AL_DIA') !== 'AL_DIA').length;

    return {
      totalCartera: totalCarteraReal,
      totalMora: totalMoraReal,
      alDia: unidadesAlDia,
      enMora: unidadesEnMora,
    };
  }, [resumen, unidades]);

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

  const totalesUnidades = useMemo(
    () =>
      unidadesFiltradas.reduce(
        (acc, u) => ({
          corriente: acc.corriente + Number(u.SALDO_CORRIENTE || 0),
          m30: acc.m30 + Number(u.SALDO_MORA_30 || 0),
          m60: acc.m60 + Number(u.SALDO_MORA_60 || 0),
          m90: acc.m90 + Number(u.SALDO_MORA_90_MAS || 0),
          total: acc.total + Number(u.SALDO_TOTAL || 0),
        }),
        { corriente: 0, m30: 0, m60: 0, m90: 0, total: 0 }
      ),
    [unidadesFiltradas]
  );

  const cuotasFiltradas = useMemo(() => {
    return cuotas.filter((c) => {
      const term = searchCuotas.toLowerCase();
      const coincideSearch =
        !searchCuotas ||
        [c.concepto, c.numeroApartamento, c.nombreResidente, c.periodo]
          .filter(Boolean)
          .some((val) => String(val).toLowerCase().includes(term));

      const estado = c.estado || 'PENDIENTE';
      const coincideEstado = filtroEstadoCuota === 'TODOS' ? true : estado === filtroEstadoCuota;

      return coincideSearch && coincideEstado;
    });
  }, [cuotas, searchCuotas, filtroEstadoCuota]);

  const getEstadoBadge = useCallback((estado) => {
    const e = estado || 'AL_DIA';
    switch (e) {
      case 'AL_DIA':
        return <Badge variant="success">Al día</Badge>;
      case 'MORA_30':
        return <Badge variant="warning">Mora 30 días</Badge>;
      case 'MORA_60':
        return <Badge variant="warning">Mora 60 días</Badge>;
      case 'MORA_CRITICA':
        return <Badge variant="destructive">Mora crítica</Badge>;
      case 'COBRO_JURIDICO':
        return <Badge variant="destructive">Cobro jurídico</Badge>;
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
      case 'PAGADA_PARCIAL':
        return <Badge variant="warning">Pago parcial</Badge>;
      case 'VENCIDA':
        return <Badge variant="destructive">Vencida</Badge>;
      default:
        return <Badge variant="secondary">{e}</Badge>;
    }
  }, []);

  const hayErrorCritico = carteraError && resumenError && antiguedadError;
  const refreshing = carteraLoading || resumenLoading || antiguedadLoading;

  const header = (
    <PageHeader
      title="Cartera y cobros"
      subtitle="Recaudo, saldos por unidad y antigüedad de la deuda de la copropiedad"
      action={
        <>
          <Button variant="outline" onClick={refetchAll} disabled={refreshing || recalculando} className="min-h-10">
            <RefreshCw className={cn(refreshing && 'animate-spin')} aria-hidden="true" />
            Actualizar
          </Button>
          <Button onClick={recalcular} disabled={recalculando} className="min-h-10">
            <RefreshCw className={cn(recalculando && 'animate-spin')} aria-hidden="true" />
            {recalculando ? 'Recalculando…' : 'Recalcular cartera'}
          </Button>
        </>
      }
    />
  );

  if (hayErrorCritico) {
    return (
      <PageContainer>
        {header}
        <ErrorState
          title="No se pudo consultar la cartera"
          message="Los saldos de la copropiedad no están disponibles. Verifique la propiedad activa e intente de nuevo."
          onRetry={refetchAll}
        />
      </PageContainer>
    );
  }

  const kpiLoading = resumenLoading && carteraLoading;
  const kpiError = resumenError && carteraError;
  const kv = (v) => (kpiLoading ? '…' : kpiError ? '—' : v);

  return (
    <PageContainer className="space-y-6">
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total cartera"
          value={kv(formatCurrency(kpis.totalCartera))}
          subtitle={`${unidades.length} unidades con registro`}
          icon={Wallet}
          variant="primary"
        />
        <MetricCard
          label="Total en mora"
          value={kv(formatCurrency(kpis.totalMora))}
          subtitle="Saldo vencido de la copropiedad"
          icon={ShieldAlert}
          variant={kpis.totalMora > 0 ? 'warning' : 'secondary'}
        />
        <MetricCard
          label="Unidades al día"
          value={kv(kpis.alDia)}
          subtitle="Sin saldo vencido"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Unidades en mora"
          value={kv(kpis.enMora)}
          subtitle={kpis.enMora > 0 ? 'Requieren gestión de cobro' : 'Sin morosidad registrada'}
          icon={AlertTriangle}
          variant={kpis.enMora > 0 ? 'danger' : 'secondary'}
        />
      </div>

      <Tabs value={tabActiva} onValueChange={setTabActiva}>
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
          <TabsList>
            <TabsTrigger value="unidades" className="gap-1.5">
              <Building className="h-4 w-4" aria-hidden="true" />
              Cartera por unidad
              <span className="tabular-nums text-text-muted">{unidades.length}</span>
            </TabsTrigger>
            <TabsTrigger value="cuotas" className="gap-1.5">
              <Receipt className="h-4 w-4" aria-hidden="true" />
              Detalle de cuotas
              {cuotas.length > 0 && <span className="tabular-nums text-text-muted">{cuotas.length}</span>}
            </TabsTrigger>
            <TabsTrigger value="antiguedad" className="gap-1.5">
              <BarChart3 className="h-4 w-4" aria-hidden="true" />
              Antigüedad de cartera
            </TabsTrigger>
          </TabsList>
        </div>

        {/* Cartera por unidad */}
        <TabsContent value="unidades" className="mt-4">
          <section className="overflow-hidden rounded-lg border border-border bg-surface shadow-sm">
            <Toolbar
              search={searchUnidades}
              onSearch={setSearchUnidades}
              searchLabel="Buscar unidad"
              placeholder="Buscar por número de apartamento"
              filter={
                <select
                  value={filtroEstado}
                  onChange={(e) => setFiltroEstado(e.target.value)}
                  className={SELECT_CLASS}
                  aria-label="Filtrar por estado de cartera"
                >
                  <option value="TODOS">Todos los estados</option>
                  <option value="AL_DIA">Al día</option>
                  <option value="EN_MORA">En mora (cualquiera)</option>
                  <option value="MORA_30">Mora 30 días</option>
                  <option value="MORA_60">Mora 60 días</option>
                  <option value="MORA_CRITICA">Mora crítica</option>
                  <option value="COBRO_JURIDICO">Cobro jurídico</option>
                </select>
              }
              count={`${unidadesFiltradas.length} de ${unidades.length} unidades`}
            />

            {carteraLoading ? (
              <LoadingState message="Consultando cartera por unidad…" />
            ) : carteraError ? (
              <div className="p-4 sm:p-5">
                <ErrorState
                  compact
                  title="No se pudo cargar la cartera por unidad"
                  message="Revise su conexión e intente de nuevo."
                  onRetry={quiet(refetchCartera)}
                />
              </div>
            ) : unidadesFiltradas.length === 0 ? (
              searchUnidades || filtroEstado !== 'TODOS' ? (
                <EmptyState
                  icon={Search}
                  title="Sin coincidencias con los filtros aplicados"
                  subtitle="Cambie el criterio de búsqueda o el filtro de estado."
                >
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-3"
                    onClick={() => {
                      setSearchUnidades('');
                      setFiltroEstado('TODOS');
                    }}
                  >
                    Limpiar filtros
                  </Button>
                </EmptyState>
              ) : (
                <EmptyState
                  icon={Building}
                  title="No hay registros de cartera"
                  subtitle="Recalcule la cartera para consolidar los saldos desde las cuotas y los pagos."
                >
                  <Button size="sm" className="mt-3" onClick={recalcular} disabled={recalculando}>
                    Recalcular cartera
                  </Button>
                </EmptyState>
              )
            ) : (
              <>
                <div className="hidden max-h-[70vh] overflow-auto md:block">
                  <table className="data-table">
                    <caption className="sr-only">Saldos de cartera por unidad, en pesos colombianos</caption>
                    <thead>
                      <tr>
                        <th scope="col">Unidad</th>
                        <th scope="col" className="text-right">Corriente</th>
                        <th scope="col" className="text-right">Mora 30 días</th>
                        <th scope="col" className="text-right">Mora 60 días</th>
                        <th scope="col" className="text-right">Mora 90+ días</th>
                        <th scope="col" className="text-right">Saldo total</th>
                        <th scope="col">Estado</th>
                        <th scope="col" className="text-right">Fecha de corte</th>
                      </tr>
                    </thead>
                    <tbody>
                      {unidadesFiltradas.map((u, idx) => {
                        const id = u.ID_CARTERA || u.id || idx;
                        const apto = String(u.NUMERO_APARTAMENTO || u.ID_UNIDAD || u.id_unidad || 'Unidad');
                        return (
                          <tr key={id}>
                            <td className="whitespace-nowrap font-semibold">
                              {apto.startsWith('Apto') ? apto : `Apto ${apto}`}
                            </td>
                            <MoneyCell value={u.SALDO_CORRIENTE} />
                            <MoneyCell value={u.SALDO_MORA_30} />
                            <MoneyCell value={u.SALDO_MORA_60} />
                            <MoneyCell value={u.SALDO_MORA_90_MAS} />
                            <MoneyCell value={u.SALDO_TOTAL} strong />
                            <td>{getEstadoBadge(u.ESTADO_CARTERA)}</td>
                            <td className="whitespace-nowrap text-right tabular-nums text-text-secondary">
                              {u.FECHA_CORTE || '—'}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                    <tfoot>
                      <tr className="border-t border-border bg-surface-container text-sm font-bold text-on-surface">
                        <th scope="row" className="px-4 py-3 text-left">
                          Total ({unidadesFiltradas.length})
                        </th>
                        <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(totalesUnidades.corriente)}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(totalesUnidades.m30)}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(totalesUnidades.m60)}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(totalesUnidades.m90)}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{formatCurrency(totalesUnidades.total)}</td>
                        <td colSpan={2} />
                      </tr>
                    </tfoot>
                  </table>
                </div>

                <ul className="divide-y divide-border-subtle md:hidden">
                  {unidadesFiltradas.map((u, idx) => {
                    const id = u.ID_CARTERA || u.id || idx;
                    const apto = String(u.NUMERO_APARTAMENTO || u.ID_UNIDAD || u.id_unidad || 'Unidad');
                    return (
                      <li key={id} className="space-y-2.5 p-4">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-bold text-on-surface">
                            {apto.startsWith('Apto') ? apto : `Apto ${apto}`}
                          </span>
                          {getEstadoBadge(u.ESTADO_CARTERA)}
                        </div>
                        <div className="flex items-baseline justify-between gap-2">
                          <span className="text-xs text-text-secondary">Saldo total</span>
                          <span className="text-base font-bold tabular-nums text-on-surface">
                            {formatCurrency(Number(u.SALDO_TOTAL || 0))}
                          </span>
                        </div>
                        <dl className="grid grid-cols-2 gap-x-3 gap-y-1.5 border-t border-border-subtle pt-2 text-xs">
                          {[
                            ['Corriente', u.SALDO_CORRIENTE],
                            ['Mora 30 días', u.SALDO_MORA_30],
                            ['Mora 60 días', u.SALDO_MORA_60],
                            ['Mora 90+ días', u.SALDO_MORA_90_MAS],
                          ].map(([label, v]) => (
                            <div key={label} className="flex justify-between gap-2">
                              <dt className="text-text-muted">{label}</dt>
                              <dd
                                className={cn(
                                  'tabular-nums',
                                  Number(v || 0) > 0 ? 'font-semibold text-on-surface' : 'text-text-muted'
                                )}
                              >
                                {formatCurrency(Number(v || 0))}
                              </dd>
                            </div>
                          ))}
                        </dl>
                        {u.FECHA_CORTE && (
                          <p className="text-right text-[11.5px] tabular-nums text-text-muted">
                            Corte: {u.FECHA_CORTE}
                          </p>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
          </section>
        </TabsContent>

        {/* Detalle de cuotas */}
        <TabsContent value="cuotas" className="mt-4">
          <section className="overflow-hidden rounded-lg border border-border bg-surface shadow-sm">
            <Toolbar
              search={searchCuotas}
              onSearch={setSearchCuotas}
              searchLabel="Buscar cuotas"
              placeholder="Buscar por concepto, apartamento o residente"
              filter={
                <select
                  value={filtroEstadoCuota}
                  onChange={(e) => setFiltroEstadoCuota(e.target.value)}
                  className={SELECT_CLASS}
                  aria-label="Filtrar por estado de cuota"
                >
                  <option value="TODOS">Todos los estados</option>
                  <option value="PENDIENTE">Pendientes</option>
                  <option value="PAGADO">Pagadas</option>
                  <option value="VENCIDA">Vencidas</option>
                </select>
              }
              count={`${cuotasFiltradas.length} de ${cuotas.length} cuotas`}
            />

            {cuotasLoading ? (
              <LoadingState message="Consultando cuotas…" />
            ) : cuotasError ? (
              <div className="p-4 sm:p-5">
                <ErrorState
                  compact
                  title="No se pudieron cargar las cuotas"
                  message="Revise su conexión e intente de nuevo."
                  onRetry={quiet(refetchCuotas)}
                />
              </div>
            ) : cuotasFiltradas.length === 0 ? (
              <EmptyState
                icon={Receipt}
                title={
                  searchCuotas || filtroEstadoCuota !== 'TODOS'
                    ? 'Sin cuotas que coincidan con la búsqueda'
                    : 'No hay cuotas registradas'
                }
                subtitle="Las cuotas generadas para las unidades aparecerán aquí."
              >
                {(searchCuotas || filtroEstadoCuota !== 'TODOS') && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-3"
                    onClick={() => {
                      setSearchCuotas('');
                      setFiltroEstadoCuota('TODOS');
                    }}
                  >
                    Limpiar filtros
                  </Button>
                )}
              </EmptyState>
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <caption className="sr-only">Detalle de cuotas de administración</caption>
                  <thead>
                    <tr>
                      <th scope="col" className="w-16">ID</th>
                      <th scope="col">Concepto</th>
                      <th scope="col">Unidad y titular</th>
                      <th scope="col">Periodo</th>
                      <th scope="col" className="text-right">Valor total</th>
                      <th scope="col" className="text-right">Saldo pendiente</th>
                      <th scope="col">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cuotasFiltradas.map((c) => {
                      const id = c.id || c.idCuota;
                      const saldo = Number(c.saldoPendiente != null ? c.saldoPendiente : c.valorTotal || 0);
                      return (
                        <tr key={id}>
                          <td className="tabular-nums text-text-muted">#{id}</td>
                          <td className="min-w-[12rem] font-semibold">
                            {c.concepto || `Cuota de administración #${id}`}
                          </td>
                          <td className="min-w-[10rem]">
                            <p className="font-medium">
                              {c.numeroApartamento ? `Apto ${c.numeroApartamento}` : 'Unidad'}
                            </p>
                            {c.nombreResidente && (
                              <p className="text-xs text-text-muted">{c.nombreResidente}</p>
                            )}
                          </td>
                          <td className="whitespace-nowrap tabular-nums text-text-secondary">{c.periodo || '—'}</td>
                          <MoneyCell value={c.valorTotal || c.valorBase} />
                          <MoneyCell value={saldo} strong />
                          <td>{getCuotaBadge(c.estado)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </TabsContent>

        {/* Antigüedad */}
        <TabsContent value="antiguedad" className="mt-4">
          <section className="rounded-lg border border-border bg-surface shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-4 py-3.5 sm:px-5">
              <div className="min-w-0">
                <h2 className="text-base font-bold text-on-surface">Antigüedad de la deuda</h2>
                <p className="mt-0.5 text-xs text-text-muted">
                  Saldos agrupados por tramo de días de vencimiento
                </p>
              </div>
              <span className="text-xs font-semibold text-text-secondary">
                Base: <span className="tabular-nums text-on-surface">{formatCurrency(kpis.totalCartera)}</span>
              </span>
            </div>

            <div className="p-4 sm:p-5">
              {antiguedadLoading ? (
                <LoadingState message="Calculando tramos de antigüedad…" className="p-0" />
              ) : antiguedadError ? (
                <ErrorState
                  compact
                  title="No se pudo cargar la antigüedad de cartera"
                  message="Revise su conexión e intente de nuevo."
                  onRetry={quiet(refetchAntiguedad)}
                />
              ) : antiguedad.length === 0 ? (
                <EmptyState
                  icon={CheckCircle2}
                  title="Sin cartera vencida"
                  subtitle="No hay obligaciones pendientes distribuidas en tramos de mora."
                />
              ) : (
                <>
                  <ul className="space-y-4">
                    {antiguedad.map((a, idx) => {
                      const rango = a.RANGO || `Tramo ${idx + 1}`;
                      const monto = Number(a.TOTAL_SALDO || 0);
                      const cantidad = Number(a.CANTIDAD_CUOTAS || 0);
                      const totalGeneral = kpis.totalCartera > 0 ? kpis.totalCartera : 1;
                      const pct = Math.min(100, Math.round((monto / totalGeneral) * 100));
                      const isVigente = rango.toUpperCase().includes('VIGENTE');
                      const isMoraGrave = rango.includes('90') || rango.includes('60');
                      return (
                        <li key={rango} className="space-y-1.5">
                          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-semibold text-on-surface">{rango}</span>
                              <Badge variant={isVigente ? 'success' : isMoraGrave ? 'destructive' : 'warning'}>
                                <span className="tabular-nums">{cantidad}</span>{' '}
                                {cantidad === 1 ? 'cuota' : 'cuotas'}
                              </Badge>
                            </div>
                            <span className="text-sm">
                              <span className="font-bold tabular-nums text-on-surface">{formatCurrency(monto)}</span>
                              <span className="ml-2 tabular-nums text-text-muted">{pct} %</span>
                            </span>
                          </div>
                          <div
                            className="h-2.5 w-full overflow-hidden rounded-[3px] bg-surface-dim"
                            role="img"
                            aria-label={`${rango}: ${pct} % de la cartera`}
                          >
                            <div
                              className="h-full rounded-[3px] bg-on-surface/70 transition-[width] duration-300 ease-out"
                              style={{ width: `${Math.max(pct, monto > 0 ? 2 : 0)}%` }}
                            />
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                  <p className="mt-5 border-t border-border-subtle pt-4 text-xs leading-relaxed text-text-secondary">
                    Los saldos con más de 60 días de vencimiento ameritan notificación formal o cobro
                    prejurídico según el reglamento de propiedad horizontal. «Recalcular cartera» consolida
                    las cuotas causadas contra los pagos acreditados.
                  </p>
                </>
              )}
            </div>
          </section>
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function MoneyCell({ value, strong = false }) {
  const n = Number(value || 0);
  return (
    <td
      className={cn(
        'whitespace-nowrap text-right tabular-nums',
        strong ? 'font-bold text-on-surface' : n > 0 ? 'text-on-surface' : 'text-text-muted'
      )}
    >
      {formatCurrency(n)}
    </td>
  );
}

function Toolbar({ search, onSearch, searchLabel, placeholder, filter, count }) {
  return (
    <div className="flex flex-col gap-3 border-b border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
      <div className="flex flex-1 flex-col gap-2 sm:max-w-xl sm:flex-row">
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <Input
            type="text"
            value={search}
            onChange={(e) => onSearch(e.target.value)}
            placeholder={placeholder}
            aria-label={searchLabel}
            className="pl-9 pr-9"
          />
          {search && (
            <button
              type="button"
              onClick={() => onSearch('')}
              className="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded text-text-muted hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="Limpiar búsqueda"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
        </div>
        {filter}
      </div>
      <span className="text-xs tabular-nums text-text-muted" aria-live="polite">
        {count}
      </span>
    </div>
  );
}
