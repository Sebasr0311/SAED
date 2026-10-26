import { useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  Building,
  ChevronRight,
  FileText,
  Gavel,
  Package,
  Receipt,
  RefreshCw,
  UserCheck,
  Users,
  Wallet,
} from 'lucide-react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { cn, formatCurrency } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PropertyAnaliticaSection } from '../components/PropertyAnaliticaSection.jsx';
import { KpiSummaryPanel } from '../components/KpiSummaryPanel.jsx';

// refetch() from useFetch rethrows; the section already renders the error state.
const quiet = (fn) => () => fn().catch(() => {});

/**
 * DashboardPage — operational home of ADMIN_PROPIEDAD.
 * Reads only existing endpoints; every count shown comes from them (no derived or invented metrics).
 */
export default function DashboardPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const navigate = useNavigate();

  const { data: kpisRaw, refetch: refetchKpis } = useFetch(
    () => tenantApi.get('/dashboard/propiedad'),
    [tenant.activeAssignmentId]
  );
  const kpis = useMemo(() => kpisRaw?.data || kpisRaw || null, [kpisRaw]);

  const {
    data: unidades,
    loading: loadingUnidades,
    error: errorUnidades,
    refetch: refetchUnidades,
  } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);

  const {
    data: personas,
    loading: loadingPersonas,
    error: errorPersonas,
    refetch: refetchPersonas,
  } = useFetch(() => tenantApi.get('/personas'), [tenant.activeAssignmentId]);

  const {
    data: cuotasRaw,
    loading: loadingCuotas,
    error: errorCuotas,
    refetch: refetchCuotas,
  } = useFetch(() => tenantApi.get('/cuotas'), [tenant.activeAssignmentId]);

  const {
    data: carteraResumenRaw,
    loading: loadingCartera,
    error: errorCartera,
    refetch: refetchCartera,
  } = useFetch(() => tenantApi.get('/cartera/resumen'), [tenant.activeAssignmentId]);

  const {
    data: multasRaw,
    loading: loadingMultas,
    error: errorMultas,
    refetch: refetchMultas,
  } = useFetch(() => tenantApi.get('/multas/todas'), [tenant.activeAssignmentId]);

  const {
    data: paquetesRaw,
    loading: loadingPaquetes,
    error: errorPaquetes,
    refetch: refetchPaquetes,
  } = useFetch(() => tenantApi.get('/paquetes'), [tenant.activeAssignmentId]);

  const {
    data: visitasRaw,
    loading: loadingVisitas,
    error: errorVisitas,
    refetch: refetchVisitas,
  } = useFetch(() => tenantApi.get('/porteria/visitas-resumen'), [tenant.activeAssignmentId]);

  const refetchAll = useCallback(() => {
    [
      refetchKpis,
      refetchUnidades,
      refetchPersonas,
      refetchCuotas,
      refetchCartera,
      refetchMultas,
      refetchPaquetes,
      refetchVisitas,
    ].forEach((fn) => fn().catch(() => {}));
  }, [
    refetchKpis,
    refetchUnidades,
    refetchPersonas,
    refetchCuotas,
    refetchCartera,
    refetchMultas,
    refetchPaquetes,
    refetchVisitas,
  ]);

  const unidadesList = useMemo(() => unidades?.items || [], [unidades]);
  const personasList = useMemo(() => personas?.items || [], [personas]);
  const cuotasList = useMemo(
    () => cuotasRaw?.items || cuotasRaw?.data || (Array.isArray(cuotasRaw) ? cuotasRaw : []),
    [cuotasRaw]
  );
  const multasList = useMemo(() => multasRaw?.items || [], [multasRaw]);
  const paquetesList = useMemo(() => paquetesRaw?.items || [], [paquetesRaw]);
  const visitasList = useMemo(
    () => visitasRaw?.items || visitasRaw?.data || (Array.isArray(visitasRaw) ? visitasRaw : []),
    [visitasRaw]
  );

  const cuotasPendientes = useMemo(
    () => cuotasList.filter((c) => c.estado === 'PENDIENTE' || Number(c.saldoPendiente || 0) > 0),
    [cuotasList]
  );

  const carteraResumen = useMemo(
    () => carteraResumenRaw?.data || carteraResumenRaw?.raw || carteraResumenRaw || {},
    [carteraResumenRaw]
  );

  const totalCartera = useMemo(() => {
    if (kpis?.carteraTotal != null) return Number(kpis.carteraTotal);
    if (carteraResumen.TOTAL_CARTERA != null) return Number(carteraResumen.TOTAL_CARTERA);
    return cuotasPendientes.reduce(
      (acc, c) => acc + Number(c.saldoPendiente || c.valorTotal || c.valorBase || 0),
      0
    );
  }, [kpis, carteraResumen, cuotasPendientes]);

  const totalUnidadesCount = useMemo(
    () => (kpis?.totalUnidades != null ? Number(kpis.totalUnidades) : unidadesList.length),
    [kpis, unidadesList]
  );
  const totalPersonasCount = useMemo(
    () => (kpis?.totalPersonas != null ? Number(kpis.totalPersonas) : personasList.length),
    [kpis, personasList]
  );

  const multasPendientesList = useMemo(
    () => multasList.filter((m) => m.estado === 'PENDIENTE'),
    [multasList]
  );
  const totalMultasPendientes = useMemo(
    () => multasPendientesList.reduce((acc, m) => acc + Number(m.monto || 0), 0),
    [multasPendientesList]
  );

  const paquetesPendientesList = useMemo(
    () => paquetesList.filter((p) => p.estado === 'RECIBIDO' || p.estado === 'PENDIENTE'),
    [paquetesList]
  );

  const visitasActivasList = useMemo(
    () =>
      visitasList.filter(
        (v) => v.estado === 'ACTIVA' || v.estado === 'EN_CURSO' || v.estado === 'INGRESADO'
      ),
    [visitasList]
  );

  const paquetesPendientesCount = useMemo(
    () => (kpis?.paquetesPendientes != null ? Number(kpis.paquetesPendientes) : paquetesPendientesList.length),
    [kpis, paquetesPendientesList]
  );

  const cuotasPendientesCount = useMemo(
    () => (kpis?.cuotasPendientesCount != null ? Number(kpis.cuotasPendientesCount) : cuotasPendientes.length),
    [kpis, cuotasPendientes]
  );

  const contextoLabel = useMemo(
    () =>
      [
        tenant.activePropertyName || (tenant.activePropertyId ? `Propiedad #${tenant.activePropertyId}` : null),
        tenant.activeOrgName || (tenant.activeOrgId ? `Organización #${tenant.activeOrgId}` : null),
        tenant.activeUnitNumber
          ? `Unidad ${tenant.activeUnitNumber}`
          : tenant.activeUnitId
          ? `Unidad #${tenant.activeUnitId}`
          : null,
      ]
        .filter(Boolean)
        .join(' · '),
    [
      tenant.activePropertyName,
      tenant.activePropertyId,
      tenant.activeOrgName,
      tenant.activeOrgId,
      tenant.activeUnitNumber,
      tenant.activeUnitId,
    ]
  );

  const fechaHoy = useMemo(() => {
    const raw = new Intl.DateTimeFormat('es-CO', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date());
    return raw.charAt(0).toUpperCase() + raw.slice(1);
  }, []);

  const refreshing = loadingUnidades || loadingCuotas || loadingCartera;
  const hayErrorCritico = errorUnidades && errorPersonas && errorCuotas && errorCartera;

  const header = (
    <PageHeader
      title="Panel de control"
      subtitle={contextoLabel ? `${fechaHoy} · ${contextoLabel}` : fechaHoy}
      action={
        <>
          <Button variant="outline" onClick={refetchAll} disabled={refreshing} className="min-h-10">
            <RefreshCw className={cn(refreshing && 'animate-spin')} aria-hidden="true" />
            Actualizar
          </Button>
          <Button onClick={() => navigate('/cartera')} className="min-h-10">
            <Wallet aria-hidden="true" />
            Gestionar cartera
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
          title="No se pudieron cargar los indicadores"
          message="Verifique su conexión y la propiedad activa, e intente de nuevo."
          onRetry={refetchAll}
        />
      </PageContainer>
    );
  }

  const attentionRows = [
    {
      id: 'cuotas',
      icon: Receipt,
      title: 'Cuotas por recaudar',
      count: cuotasPendientesCount,
      detail:
        cuotasPendientesCount > 0
          ? `${formatCurrency(totalCartera)} en cartera pendiente`
          : 'Sin cuotas pendientes de recaudo',
      loading: loadingCuotas,
      error: errorCuotas,
      onRetry: quiet(refetchCuotas),
      to: '/cartera',
      actionLabel: 'Abrir cartera',
    },
    {
      id: 'multas',
      icon: Gavel,
      title: 'Multas pendientes de pago',
      count: multasPendientesList.length,
      detail:
        multasPendientesList.length > 0
          ? `${formatCurrency(totalMultasPendientes)} por recaudar`
          : 'Sin multas pendientes',
      loading: loadingMultas,
      error: errorMultas,
      onRetry: quiet(refetchMultas),
      to: '/multas',
      actionLabel: 'Abrir multas',
    },
    {
      id: 'paquetes',
      icon: Package,
      title: 'Paquetes en custodia',
      count: paquetesPendientesCount,
      detail: paquetesPendientesCount > 0 ? 'Pendientes de entrega en portería' : 'Sin paquetes pendientes',
      loading: loadingPaquetes,
      error: errorPaquetes,
      onRetry: quiet(refetchPaquetes),
      to: '/paquetes-admin',
      actionLabel: 'Abrir paquetería',
    },
    {
      id: 'visitas',
      icon: UserCheck,
      title: 'Visitas activas',
      count: visitasActivasList.length,
      detail: visitasActivasList.length > 0 ? 'Visitantes dentro de la copropiedad' : 'Sin visitas en curso',
      loading: loadingVisitas,
      error: errorVisitas,
      onRetry: quiet(refetchVisitas),
      to: '/visitas',
      actionLabel: 'Abrir visitas',
    },
  ];
  const attentionLoading = attentionRows.some((r) => r.loading);
  const attentionError = attentionRows.some((r) => r.error);
  const pendingTotal = attentionRows.reduce((acc, r) => acc + (!r.loading && !r.error ? r.count : 0), 0);

  const accesos = [
    { label: 'Cartera', path: '/cartera', icon: Wallet },
    { label: 'Pagos', path: '/pagos', icon: Receipt },
    { label: 'Residentes', path: '/residentes', icon: Users },
    { label: 'Unidades', path: '/unidades', icon: Building },
    { label: 'Sanciones', path: '/sanciones-admin', icon: Gavel },
    { label: 'Reportes', path: '/reportes', icon: FileText },
  ];

  return (
    <PageContainer className="space-y-6">
      {header}

      <KpiSummaryPanel deps={[tenant.activeAssignmentId]} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section
          aria-labelledby="admin-attention-title"
          className="rounded-lg border border-border bg-surface shadow-sm lg:col-span-2"
        >
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3.5 sm:px-5">
            <h2 id="admin-attention-title" className="text-base font-bold text-on-surface">
              Requiere atención
            </h2>
            <span className="text-xs font-semibold text-text-muted" aria-live="polite">
              {attentionLoading
                ? 'Consultando…'
                : attentionError
                ? 'Algunos datos no se pudieron cargar'
                : pendingTotal > 0
                ? `${pendingTotal} pendiente${pendingTotal === 1 ? '' : 's'}`
                : 'Todo al día'}
            </span>
          </div>
          <ul className="divide-y divide-border-subtle">
            {attentionRows.map((row) => (
              <AttentionRow key={row.id} row={row} onOpen={() => navigate(row.to)} />
            ))}
          </ul>
        </section>

        <section
          aria-labelledby="admin-property-title"
          className="flex flex-col rounded-lg border border-border bg-surface shadow-sm"
        >
          <div className="border-b border-border px-4 py-3.5 sm:px-5">
            <h2 id="admin-property-title" className="text-base font-bold text-on-surface">
              Copropiedad
            </h2>
          </div>
          <dl className="grid grid-cols-2 divide-x divide-border-subtle border-b border-border-subtle">
            <CountFact
              label="Unidades"
              value={totalUnidadesCount}
              loading={loadingUnidades && kpis?.totalUnidades == null}
              error={errorUnidades && kpis?.totalUnidades == null}
            />
            <CountFact
              label="Residentes"
              value={totalPersonasCount}
              loading={loadingPersonas && kpis?.totalPersonas == null}
              error={errorPersonas && kpis?.totalPersonas == null}
            />
          </dl>
          <div className="p-4 sm:p-5">
            <h3 className="mb-2.5 text-[13px] font-semibold text-text-secondary">Accesos rápidos</h3>
            <div className="grid grid-cols-2 gap-2">
              {accesos.map(({ label, path, icon: Icon }) => (
                <Button
                  key={path}
                  variant="outline"
                  onClick={() => navigate(path)}
                  className="h-auto min-h-11 justify-start gap-2 px-3 py-2 text-[13px]"
                >
                  <Icon className="text-primary-text" aria-hidden="true" />
                  {label}
                </Button>
              ))}
            </div>
          </div>
        </section>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <section
          aria-labelledby="admin-cobros-title"
          className="flex flex-col rounded-lg border border-border bg-surface shadow-sm lg:col-span-7"
        >
          <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3.5 sm:px-5">
            <div className="min-w-0">
              <h2 id="admin-cobros-title" className="text-base font-bold text-on-surface">
                Cobros de administración pendientes
              </h2>
              <p className="mt-0.5 text-xs text-text-muted">Cuotas generadas a la espera de recaudo</p>
            </div>
            <Button variant="link" size="sm" className="h-auto shrink-0 px-0" onClick={() => navigate('/cartera')}>
              Ver todo
              <ArrowRight aria-hidden="true" />
            </Button>
          </div>
          <div className="flex-1">
            {loadingCuotas ? (
              <LoadingState size="sm" message="Cargando cuotas…" className="px-5" />
            ) : errorCuotas ? (
              <div className="p-4 sm:p-5">
                <ErrorState
                  compact
                  title="No se pudieron cargar las cuotas"
                  message="Revise su conexión e intente de nuevo."
                  onRetry={quiet(refetchCuotas)}
                />
              </div>
            ) : cuotasPendientes.length === 0 ? (
              <EmptyState
                icon={Receipt}
                title="La cartera está al día"
                subtitle="No hay cuotas pendientes de cobro en esta copropiedad."
              />
            ) : (
              <ul className="divide-y divide-border-subtle">
                {cuotasPendientes.slice(0, 5).map((c) => {
                  const id = c.id || c.idCuota;
                  const monto = Number(
                    c.saldoPendiente != null ? c.saldoPendiente : c.valorTotal || c.valorBase || 0
                  );
                  return (
                    <li key={id} className="flex items-center justify-between gap-3 px-4 py-3 sm:px-5">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-on-surface">
                          {c.concepto || `Cuota de administración #${id}`}
                        </p>
                        <p className="truncate text-xs text-text-muted">
                          <span className="font-medium text-text-secondary">
                            {c.numeroApartamento ? `Apto ${c.numeroApartamento}` : 'Unidad'}
                          </span>
                          {c.nombreResidente && ` · ${c.nombreResidente}`}
                          {c.periodo && ` · Periodo ${c.periodo}`}
                        </p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-1">
                        <span className="text-sm font-bold tabular-nums text-on-surface">
                          {formatCurrency(monto)}
                        </span>
                        <Badge variant="warning">Pendiente</Badge>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </section>

        <section
          aria-labelledby="admin-porteria-title"
          className="flex flex-col rounded-lg border border-border bg-surface shadow-sm lg:col-span-5"
        >
          <div className="border-b border-border px-4 py-3.5 sm:px-5">
            <h2 id="admin-porteria-title" className="text-base font-bold text-on-surface">
              Portería y convivencia
            </h2>
            <p className="mt-0.5 text-xs text-text-muted">Registros en espera de gestión</p>
          </div>
          <Tabs defaultValue="paquetes" className="flex flex-1 flex-col p-4 sm:p-5">
            <TabsList className="w-full sm:w-auto sm:self-start">
              <TabsTrigger value="paquetes" className="flex-1 gap-1.5 sm:flex-none">
                <Package className="h-3.5 w-3.5" aria-hidden="true" />
                Paquetes <span className="tabular-nums">({paquetesPendientesList.length})</span>
              </TabsTrigger>
              <TabsTrigger value="sanciones" className="flex-1 gap-1.5 sm:flex-none">
                <Gavel className="h-3.5 w-3.5" aria-hidden="true" />
                Multas <span className="tabular-nums">({multasPendientesList.length})</span>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="paquetes" className="mt-3 flex flex-1 flex-col">
              <div className="flex-1">
                {loadingPaquetes ? (
                  <LoadingState size="sm" message="Cargando paquetes…" className="px-0" />
                ) : errorPaquetes ? (
                  <ErrorState
                    compact
                    title="No se pudieron cargar los paquetes"
                    onRetry={quiet(refetchPaquetes)}
                  />
                ) : paquetesPendientesList.length === 0 ? (
                  <p className="py-6 text-center text-sm text-text-muted">
                    No hay paquetes pendientes en portería.
                  </p>
                ) : (
                  <ul className="divide-y divide-border-subtle">
                    {paquetesPendientesList.slice(0, 4).map((p) => (
                      <li key={p.idPaquete || p.id} className="flex items-center justify-between gap-2 py-2.5">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-on-surface">
                            {p.empresaTransporte || 'Encomienda'}
                            {p.codigoPin ? ` · PIN ${p.codigoPin}` : ''}
                          </p>
                          <p className="truncate text-xs text-text-muted">
                            Destino:{' '}
                            {p.numeroUnidad ? `Unidad ${p.numeroUnidad}` : p.destinatario || 'Residente'}
                          </p>
                        </div>
                        <Badge variant="warning" className="shrink-0">
                          En espera
                        </Badge>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <Button
                variant="ghost"
                onClick={() => navigate('/paquetes-admin')}
                className="mt-3 w-full justify-center border-t border-border-subtle pt-3"
              >
                Gestionar paquetería
                <ChevronRight aria-hidden="true" />
              </Button>
            </TabsContent>

            <TabsContent value="sanciones" className="mt-3 flex flex-1 flex-col">
              <div className="flex-1">
                {loadingMultas ? (
                  <LoadingState size="sm" message="Cargando multas…" className="px-0" />
                ) : errorMultas ? (
                  <ErrorState compact title="No se pudieron cargar las multas" onRetry={quiet(refetchMultas)} />
                ) : multasPendientesList.length === 0 ? (
                  <p className="py-6 text-center text-sm text-text-muted">No hay multas pendientes de recaudo.</p>
                ) : (
                  <ul className="divide-y divide-border-subtle">
                    {multasPendientesList.slice(0, 4).map((m) => (
                      <li key={m.idMulta || m.id} className="flex items-center justify-between gap-2 py-2.5">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-on-surface">
                            {m.tipo || m.motivo || 'Infracción de convivencia'}
                          </p>
                          <p className="truncate text-xs text-text-muted">
                            {m.numeroApartamento ? `Apto ${m.numeroApartamento}` : 'Unidad'}
                            {m.nombreResidente ? ` · ${m.nombreResidente}` : ''}
                          </p>
                        </div>
                        <span className="shrink-0 text-sm font-bold tabular-nums text-on-surface">
                          {formatCurrency(m.monto)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <Button
                variant="ghost"
                onClick={() => navigate('/sanciones-admin')}
                className="mt-3 w-full justify-center border-t border-border-subtle pt-3"
              >
                Ver registro de sanciones
                <ChevronRight aria-hidden="true" />
              </Button>
            </TabsContent>
          </Tabs>
        </section>
      </div>

      <PropertyAnaliticaSection />
    </PageContainer>
  );
}

function CountFact({ label, value, loading, error }) {
  return (
    <div className="px-4 py-3.5 sm:px-5">
      <dt className="text-[13px] font-semibold text-text-secondary">{label}</dt>
      <dd className="mt-1.5">
        {loading ? (
          <span className="app-skeleton block h-6 w-12" aria-label="Cargando" />
        ) : error ? (
          <span className="text-sm font-semibold text-tint-red-fg">Sin datos</span>
        ) : (
          <span className="app-numeral text-[1.625rem] text-on-surface">{value}</span>
        )}
      </dd>
    </div>
  );
}

/**
 * One line of "Requiere atención": a line with pending items is an Índigo piece with an Índigo count;
 * settled lines stay as a ghosted dashed piece (same vocabulary as the resident home).
 */
function AttentionRow({ row, onOpen }) {
  const { icon: Icon, title, count, detail, loading, error, onRetry, actionLabel } = row;
  const needsAction = !loading && !error && count > 0;

  return (
    <li className="flex items-stretch">
      <button
        type="button"
        onClick={onOpen}
        aria-label={`${title}: ${loading ? 'cargando' : error ? 'sin datos' : count}. ${actionLabel}`}
        className="group flex min-w-0 flex-1 items-center gap-3 px-4 py-3.5 text-left transition-colors duration-150 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:gap-4 sm:px-5"
      >
        <span
          className={cn(
            'h-3 w-3 shrink-0 rounded-[3px]',
            needsAction
              ? 'bg-btn-primary ring-1 ring-primary-text'
              : 'border-[1.5px] border-dashed border-keyline/45'
          )}
          aria-hidden="true"
        />
        <Icon className="hidden h-5 w-5 shrink-0 text-text-secondary sm:block" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold leading-snug text-on-surface">{title}</span>
          {loading ? (
            <span className="app-skeleton mt-1.5 block h-2.5 w-2/3" aria-hidden="true" />
          ) : (
            <span
              className={cn(
                'mt-0.5 line-clamp-2 text-xs sm:line-clamp-1',
                error ? 'font-semibold text-tint-red-fg' : 'text-text-muted'
              )}
            >
              {error ? 'No se pudo cargar esta información.' : detail}
            </span>
          )}
        </span>
        <span
          className={cn(
            'app-numeral min-w-8 shrink-0 text-right text-[1.625rem]',
            needsAction ? 'text-primary-text' : 'text-text-muted'
          )}
          aria-hidden="true"
        >
          {loading ? '' : error ? '—' : count}
        </span>
        <ChevronRight
          className="h-4 w-4 shrink-0 text-text-muted transition-transform duration-150 ease-out group-hover:translate-x-0.5"
          aria-hidden="true"
        />
      </button>
      {error && onRetry && (
        <div className="flex items-center pr-3 sm:pr-4">
          <Button variant="outline" size="sm" onClick={onRetry} aria-label={`Reintentar: ${title}`}>
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            <span className="hidden sm:inline">Reintentar</span>
          </Button>
        </div>
      )}
    </li>
  );
}
