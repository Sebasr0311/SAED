import { useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowUpRight,
  Building,
  CheckCircle2,
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
import { formatCurrency } from '../lib/utils.js';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PropertyAnaliticaSection } from '../components/PropertyAnaliticaSection.jsx';

/**
 * DashboardPage 2.0 — Centro Operativo ADMIN_PROPIEDAD.
 * Modern Enterprise SaaS / PropTech Premium.
 *
 * Muestra métricas operativas y financieras en tiempo real consumiendo
 * exclusivamente endpoints certificados de SAED 2.0 con aislamiento multi-tenant.
 */
export default function DashboardPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const navigate = useNavigate();

  // 0. KPIs consolidados de la propiedad (Endpoint Atómico F11)
  const {
    data: kpisRaw,
    refetch: refetchKpis,
  } = useFetch(() => tenantApi.get('/dashboard/propiedad'), [tenant.activeAssignmentId]);

  const kpis = useMemo(() => kpisRaw?.data || kpisRaw || null, [kpisRaw]);

  // 1. Unidades del tenant
  const {
    data: unidades,
    loading: loadingUnidades,
    error: errorUnidades,
    refetch: refetchUnidades,
  } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);

  // 2. Personas / Residentes registrados
  const {
    data: personas,
    loading: loadingPersonas,
    error: errorPersonas,
    refetch: refetchPersonas,
  } = useFetch(() => tenantApi.get('/personas'), [tenant.activeAssignmentId]);

  // 3. Cuotas de administración
  const {
    data: cuotasRaw,
    loading: loadingCuotas,
    error: errorCuotas,
    refetch: refetchCuotas,
  } = useFetch(() => tenantApi.get('/cuotas'), [tenant.activeAssignmentId]);

  // 4. Resumen consolidado de cartera
  const {
    data: carteraResumenRaw,
    loading: loadingCartera,
    error: errorCartera,
    refetch: refetchCartera,
  } = useFetch(() => tenantApi.get('/cartera/resumen'), [tenant.activeAssignmentId]);

  // 5. Multas y sanciones
  const {
    data: multasRaw,
    loading: loadingMultas,
    refetch: refetchMultas,
  } = useFetch(() => tenantApi.get('/multas/todas'), [tenant.activeAssignmentId]);

  // 6. Paquetes en portería
  const {
    data: paquetesRaw,
    loading: loadingPaquetes,
    refetch: refetchPaquetes,
  } = useFetch(() => tenantApi.get('/paquetes'), [tenant.activeAssignmentId]);

  // 7. Visitas activas en portería
  const {
    data: visitasRaw,
    refetch: refetchVisitas,
  } = useFetch(() => tenantApi.get('/porteria/visitas-resumen'), [tenant.activeAssignmentId]);

  // Refresco unificado
  const refetchAll = useCallback(() => {
    refetchKpis();
    refetchUnidades();
    refetchPersonas();
    refetchCuotas();
    refetchCartera();
    refetchMultas();
    refetchPaquetes();
    refetchVisitas();
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

  // Extracción de listas normalizadas
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

  // Cálculos de negocio reales
  const cuotasPendientes = useMemo(
    () => cuotasList.filter((c) => c.estado === 'PENDIENTE' || Number(c.saldoPendiente || 0) > 0),
    [cuotasList]
  );

  const carteraResumen = useMemo(
    () => carteraResumenRaw?.data || carteraResumenRaw?.raw || carteraResumenRaw || {},
    [carteraResumenRaw]
  );

  const totalCartera = useMemo(() => {
    if (kpis?.carteraTotal != null) {
      return Number(kpis.carteraTotal);
    }
    if (carteraResumen.TOTAL_CARTERA != null) {
      return Number(carteraResumen.TOTAL_CARTERA);
    }
    return cuotasPendientes.reduce(
      (acc, c) => acc + Number(c.saldoPendiente || c.valorTotal || c.valorBase || 0),
      0
    );
  }, [kpis, carteraResumen, cuotasPendientes]);

  const totalUnidadesCount = useMemo(() => {
    return kpis?.totalUnidades != null ? Number(kpis.totalUnidades) : unidadesList.length;
  }, [kpis, unidadesList]);

  const totalPersonasCount = useMemo(() => {
    return kpis?.totalPersonas != null ? Number(kpis.totalPersonas) : personasList.length;
  }, [kpis, personasList]);

  const multasPendientesList = useMemo(
    () => multasList.filter((m) => m.estado === 'PENDIENTE'),
    [multasList]
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

  const paquetesPendientesCount = useMemo(() => {
    return kpis?.paquetesPendientes != null ? Number(kpis.paquetesPendientes) : paquetesPendientesList.length;
  }, [kpis, paquetesPendientesList]);

  const cuotasPendientesCount = useMemo(() => {
    return kpis?.cuotasPendientesCount != null ? Number(kpis.cuotasPendientesCount) : cuotasPendientes.length;
  }, [kpis, cuotasPendientes]);

  const contextoLabel = useMemo(
    () =>
      [
        tenant.activePropertyName || (tenant.activePropertyId ? `Propiedad #${tenant.activePropertyId}` : null),
        tenant.activeOrgName || (tenant.activeOrgId ? `Organización #${tenant.activeOrgId}` : null),
        tenant.activeUnitNumber ? `Unidad ${tenant.activeUnitNumber}` : (tenant.activeUnitId ? `Unidad #${tenant.activeUnitId}` : null),
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
    return new Intl.DateTimeFormat('es-CO', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date());
  }, []);

  const cargandoInicial =
    loadingUnidades && loadingPersonas && loadingCuotas && loadingCartera;

  const hayErrorCritico =
    errorUnidades && errorPersonas && errorCuotas && errorCartera;

  if (hayErrorCritico) {
    return (
      <PageContainer>
        <ErrorState
          title="Error de conexión con el tenant"
          message="No se pudieron sincronizar los indicadores operativos. Verifique su asignación activa o su sesión de usuario."
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
              Panel de Control
            </h1>
            <Badge variant="outline" className="hidden sm:inline-flex bg-primary/5 text-primary border-primary/20 text-xs font-semibold">
              ADMIN_PROPIEDAD
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground flex items-center gap-2">
            <span className="capitalize">{fechaHoy}</span>
            {contextoLabel && (
              <>
                <span className="text-border" aria-hidden="true">•</span>
                <span className="font-medium text-foreground/80">{contextoLabel}</span>
              </>
            )}
          </p>
        </div>

        {/* Acciones Rápidas del Header */}
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          <Button
            variant="outline"
            size="sm"
            onClick={refetchAll}
            className="text-xs min-h-[40px] sm:min-h-9"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1.5 ${
                loadingUnidades || loadingCuotas || loadingCartera ? 'animate-spin text-primary' : ''
              }`}
              aria-hidden="true"
            />
            Actualizar
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => navigate('/cartera')}
            className="text-xs min-h-[40px] sm:min-h-9"
          >
            <Wallet className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            Gestionar Cartera
          </Button>
        </div>
      </div>

      {/* 2. Loading State Inicial */}
      {cargandoInicial ? (
        <LoadingState
          message="Sincronizando Centro Operativo..."
          description="Consultando métricas en tiempo real con aislamiento de copropiedad"
        />
      ) : (
        <>
          {/* 3. Grid de KPIs Ejecutivos (MetricCards) */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Cartera Pendiente"
              value={formatCurrency(totalCartera)}
              subtitle={`${cuotasPendientesCount} cuotas por recaudar`}
              icon={Wallet}
              variant="primary"
              onClick={() => navigate('/cartera')}
              className="border-primary/20"
            />
            <MetricCard
              label="Unidades Habitacionales"
              value={totalUnidadesCount}
              subtitle="Copropiedad activa"
              icon={Building}
              variant="info"
              onClick={() => navigate('/unidades')}
            />
            <MetricCard
              label="Residentes Registrados"
              value={totalPersonasCount}
              subtitle="Población censada"
              icon={Users}
              variant="success"
              onClick={() => navigate('/residentes')}
            />
            <MetricCard
              label="Paquetería en Custodia"
              value={paquetesPendientesCount}
              subtitle={
                paquetesPendientesCount > 0
                  ? 'Pendientes por entrega'
                  : 'Sin paquetes pendientes'
              }
              icon={Package}
              variant={paquetesPendientesCount > 0 ? 'warning' : 'secondary'}
              onClick={() => navigate('/paquetes-admin')}
            />
          </div>

          {/* 4. Tendencias y Analítica con shadcn UI Charts (Evolución Financiera & Operativa) */}
          <PropertyAnaliticaSection />

          {/* 5. Centro Operativo de Gestión: Cobros Pendientes y Atención en Portería */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            {/* Columna Izquierda: Cobros y Cartera Pendiente (7 cols) */}
            <Card className="lg:col-span-7 border-border/80 shadow-xs flex flex-col">
              <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/50">
                <div className="space-y-0.5">
                  <CardTitle className="text-base font-semibold flex items-center gap-2">
                    <Receipt className="h-4 w-4 text-primary" aria-hidden="true" />
                    Cobros de Administración Pendientes
                  </CardTitle>
                  <p className="text-xs text-muted-foreground">
                    Cuotas generadas a la espera de conciliación o recaudo
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => navigate('/cartera')}
                  className="text-xs text-primary hover:text-primary font-medium"
                >
                  Ver todo
                  <ArrowUpRight className="h-3.5 w-3.5 ml-1" aria-hidden="true" />
                </Button>
              </CardHeader>

              <CardContent className="pt-4 flex-1">
                {loadingCuotas ? (
                  <LoadingState message="Cargando cuotas..." size="sm" />
                ) : cuotasPendientes.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-8 text-center text-muted-foreground">
                    <CheckCircle2 className="h-10 w-10 text-emerald-500/80 mb-2" aria-hidden="true" />
                    <p className="text-sm font-semibold text-foreground">La cartera está al día</p>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      No existen cuotas pendientes de cobro para esta copropiedad.
                    </p>
                  </div>
                ) : (
                  <div className="divide-y divide-border/60">
                    {cuotasPendientes.slice(0, 5).map((c) => {
                      const id = c.id || c.idCuota;
                      const monto = Number(c.saldoPendiente != null ? c.saldoPendiente : c.valorTotal || c.valorBase || 0);

                      return (
                        <div
                          key={id}
                          className="flex items-center justify-between py-2.5 hover:bg-muted/40 px-2 rounded-lg transition-colors"
                        >
                          <div className="space-y-0.5 min-w-0 pr-3">
                            <p className="text-xs font-semibold text-foreground truncate">
                              {c.concepto || `Cuota de Administración #${id}`}
                            </p>
                            <p className="text-[11px] text-muted-foreground truncate">
                              <span className="font-medium text-foreground/80">
                                {c.numeroApartamento ? `Apto ${c.numeroApartamento}` : 'Unidad'}
                              </span>
                              {c.nombreResidente && ` · ${c.nombreResidente}`}
                              {c.periodo && ` · Periodo ${c.periodo}`}
                            </p>
                          </div>
                          <div className="text-right flex-shrink-0">
                            <p className="text-xs font-bold font-mono text-foreground">
                              {formatCurrency(monto)}
                            </p>
                            <span className="inline-block px-1.5 py-0.2 text-[10px] font-semibold rounded bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20">
                              Pendiente
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Columna Derecha: Portería y Control de Convivencia con Tabs (5 cols) */}
            <Card className="lg:col-span-5 border-border/80 shadow-xs flex flex-col">
              <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/50">
                <div className="space-y-0.5">
                  <CardTitle className="text-base font-semibold flex items-center gap-2">
                    <Package className="h-4 w-4 text-amber-500" aria-hidden="true" />
                    Portería y Control
                  </CardTitle>
                  <p className="text-xs text-muted-foreground">
                    Atención inmediata y registros pendientes
                  </p>
                </div>
                <Badge variant="outline" className="text-[10px] font-semibold">
                  {paquetesPendientesList.length + multasPendientesList.length} en espera
                </Badge>
              </CardHeader>

              <CardContent className="pt-3 flex-1 flex flex-col">
                <Tabs defaultValue="paquetes" className="w-full flex-1 flex flex-col">
                  <div className="flex items-center justify-between border-b border-border/40 pb-2">
                    <TabsList className="h-8 bg-muted/60 p-0.5">
                      <TabsTrigger value="paquetes" className="text-xs gap-1.5 px-3">
                        <Package className="h-3 w-3 text-amber-500" />
                        Paquetes ({paquetesPendientesList.length})
                      </TabsTrigger>
                      <TabsTrigger value="sanciones" className="text-xs gap-1.5 px-3">
                        <Gavel className="h-3 w-3 text-rose-500" />
                        Sanciones ({multasPendientesList.length})
                      </TabsTrigger>
                    </TabsList>
                  </div>

                  {/* TAB PAQUETES */}
                  <TabsContent value="paquetes" className="pt-2 flex-1 flex flex-col justify-between">
                    <div>
                      {loadingPaquetes ? (
                        <LoadingState message="Cargando paquetes..." size="sm" />
                      ) : paquetesPendientesList.length === 0 ? (
                        <p className="py-8 text-center text-xs text-muted-foreground">
                          No hay paquetes pendientes en portería.
                        </p>
                      ) : (
                        <div className="divide-y divide-border/60">
                          {paquetesPendientesList.slice(0, 4).map((p) => (
                            <div key={p.idPaquete || p.id} className="py-2.5 flex items-center justify-between">
                              <div className="min-w-0 pr-2">
                                <p className="text-xs font-semibold text-foreground truncate">
                                  {p.empresaTransporte || 'Encomienda'}
                                  {p.codigoPin ? ` · PIN ${p.codigoPin}` : ''}
                                </p>
                                <p className="text-[11px] text-muted-foreground truncate">
                                  Destino: {p.numeroUnidad ? `Unidad ${p.numeroUnidad}` : p.destinatario || 'Residente'}
                                </p>
                              </div>
                              <Badge variant="warning" className="text-[10px] uppercase font-bold shrink-0">
                                En espera
                              </Badge>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="pt-3 border-t border-border/40 mt-auto">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate('/paquetes-admin')}
                        className="w-full text-xs text-muted-foreground hover:text-foreground justify-center h-8"
                      >
                        Gestionar toda la paquetería
                        <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
                      </Button>
                    </div>
                  </TabsContent>

                  {/* TAB SANCIONES */}
                  <TabsContent value="sanciones" className="pt-2 flex-1 flex flex-col justify-between">
                    <div>
                      {loadingMultas ? (
                        <LoadingState message="Cargando multas..." size="sm" />
                      ) : multasPendientesList.length === 0 ? (
                        <p className="py-8 text-center text-xs text-muted-foreground">
                          No hay multas pendientes de recaudo.
                        </p>
                      ) : (
                        <div className="divide-y divide-border/60">
                          {multasPendientesList.slice(0, 4).map((m) => (
                            <div key={m.idMulta || m.id} className="py-2.5 flex items-center justify-between">
                              <div className="min-w-0 pr-2">
                                <p className="text-xs font-semibold text-foreground truncate">
                                  {m.tipo || m.motivo || 'Infracción Convivencia'}
                                </p>
                                <p className="text-[11px] text-muted-foreground truncate">
                                  {m.numeroApartamento ? `Apto ${m.numeroApartamento}` : 'Unidad'}
                                  {m.nombreResidente ? ` · ${m.nombreResidente}` : ''}
                                </p>
                              </div>
                              <p className="text-xs font-bold font-mono text-rose-600 dark:text-rose-400 shrink-0">
                                {formatCurrency(m.monto)}
                              </p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="pt-3 border-t border-border/40 mt-auto">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate('/sanciones-admin')}
                        className="w-full text-xs text-muted-foreground hover:text-foreground justify-center h-8"
                      >
                        Ver registro completo de sanciones
                        <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
                      </Button>
                    </div>
                  </TabsContent>
                </Tabs>
              </CardContent>
            </Card>
          </div>

          {/* 6. Accesos Rápidos Operativos */}
          <div className="space-y-3 pt-2">
            <h2 className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">
              Módulos de Gestión Rápida
            </h2>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {[
                { label: 'Cartera', desc: 'Recaudo y estados', path: '/cartera', icon: Wallet },
                { label: 'Residentes', desc: 'Censo y personas', path: '/residentes', icon: Users },
                { label: 'Unidades', desc: 'Inmuebles registrados', path: '/unidades', icon: Building },
                {
                  label: 'Visitas',
                  desc: visitasActivasList.length > 0 ? `${visitasActivasList.length} activas hoy` : 'Registro y QR',
                  path: '/visitas',
                  icon: UserCheck,
                },
                { label: 'Paquetería', desc: 'Custodia portería', path: '/paquetes-admin', icon: Package },
                { label: 'Reportes', desc: 'Informes de gestión', path: '/reportes', icon: FileText },
              ].map((mod) => {
                const IconComponent = mod.icon;
                return (
                  <button
                    key={mod.path}
                    type="button"
                    onClick={() => navigate(mod.path)}
                    className="flex flex-col items-start p-3.5 rounded-xl border border-border/70 bg-card hover:bg-muted/40 hover:border-primary/30 transition-all text-left group shadow-2xs hover:shadow-xs"
                  >
                    <div className="flex w-full items-center justify-between mb-2">
                      <div className="p-2 rounded-lg bg-primary/10 text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                        <IconComponent className="h-4 w-4" aria-hidden="true" />
                      </div>
                      <ArrowUpRight className="h-3.5 w-3.5 text-muted-foreground/60 group-hover:text-primary group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-all" aria-hidden="true" />
                    </div>
                    <p className="text-xs font-semibold text-foreground group-hover:text-primary transition-colors">
                      {mod.label}
                    </p>
                    <span className="text-[10px] text-muted-foreground mt-0.5">
                      {mod.desc}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}
    </PageContainer>
  );
}