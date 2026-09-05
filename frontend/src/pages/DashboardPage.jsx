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
import { PageContainer } from '../components/layout/PageContainer.jsx';

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
    refetchUnidades();
    refetchPersonas();
    refetchCuotas();
    refetchCartera();
    refetchMultas();
    refetchPaquetes();
    refetchVisitas();
  }, [
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
    if (carteraResumen.TOTAL_CARTERA != null) {
      return Number(carteraResumen.TOTAL_CARTERA);
    }
    return cuotasPendientes.reduce(
      (acc, c) => acc + Number(c.saldoPendiente || c.valorTotal || c.valorBase || 0),
      0
    );
  }, [carteraResumen, cuotasPendientes]);

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

  const contextoLabel = useMemo(
    () =>
      [
        tenant.activeOrgId ? `Organización ${tenant.activeOrgId}` : null,
        tenant.activePropertyId ? `Propiedad ${tenant.activePropertyId}` : null,
        tenant.activeUnitId ? `Unidad ${tenant.activeUnitId}` : null,
      ]
        .filter(Boolean)
        .join(' · '),
    [tenant.activeOrgId, tenant.activePropertyId, tenant.activeUnitId]
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
            className="text-xs min-h-[44px] sm:min-h-9"
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
            className="text-xs min-h-[44px] sm:min-h-9"
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
          {/* 3. Grid de KPIs con MetricCard */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="Cartera Pendiente"
              value={formatCurrency(totalCartera)}
              subtitle={`${cuotasPendientes.length} cuotas por recaudar`}
              icon={Wallet}
              variant="primary"
              onClick={() => navigate('/cartera')}
              className="border-primary/20"
            />
            <MetricCard
              label="Unidades Habitacionales"
              value={unidadesList.length}
              subtitle="Copropiedad activa"
              icon={Building}
              variant="info"
              onClick={() => navigate('/unidades')}
            />
            <MetricCard
              label="Residentes Registrados"
              value={personasList.length}
              subtitle="Población censada"
              icon={Users}
              variant="success"
              onClick={() => navigate('/residentes')}
            />
            <MetricCard
              label="Paquetería en Custodia"
              value={paquetesPendientesList.length}
              subtitle={
                paquetesPendientesList.length > 0
                  ? 'Pendientes por entrega'
                  : 'Sin paquetes pendientes'
              }
              icon={Package}
              variant={paquetesPendientesList.length > 0 ? 'warning' : 'secondary'}
              onClick={() => navigate('/paquetes-admin')}
            />
          </div>

          {/* 4. Sección Principal: Cartera y Operación en Tiempo Real */}
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
                    {cuotasPendientes.slice(0, 6).map((c) => {
                      const id = c.id || c.idCuota;
                      const monto = Number(c.saldoPendiente != null ? c.saldoPendiente : c.valorTotal || c.valorBase || 0);

                      return (
                        <div
                          key={id}
                          className="flex items-center justify-between py-3 hover:bg-muted/40 px-2 rounded-lg transition-colors"
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
                            <p className="text-xs font-bold text-foreground">
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

            {/* Columna Derecha: Operación en Portería y Multas (5 cols) */}
            <div className="lg:col-span-5 space-y-6">
              {/* Card Operación: Paquetería */}
              <Card className="border-border/80 shadow-xs">
                <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/50">
                  <div className="space-y-0.5">
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <Package className="h-4 w-4 text-amber-500" aria-hidden="true" />
                      Paquetería en Portería
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Entregas en custodia de portería
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate('/paquetes-admin')}
                    className="text-xs text-primary hover:text-primary font-medium"
                  >
                    Ver
                    <ArrowUpRight className="h-3.5 w-3.5 ml-1" aria-hidden="true" />
                  </Button>
                </CardHeader>

                <CardContent className="pt-3">
                  {loadingPaquetes ? (
                    <LoadingState message="Cargando paquetes..." size="sm" />
                  ) : paquetesPendientesList.length === 0 ? (
                    <p className="py-5 text-center text-xs text-muted-foreground">
                      No hay paquetes pendientes por entregar en portería.
                    </p>
                  ) : (
                    <div className="divide-y divide-border/60">
                      {paquetesPendientesList.slice(0, 3).map((p) => (
                        <div key={p.idPaquete || p.id} className="py-2.5 flex items-center justify-between">
                          <div className="min-w-0 pr-2">
                            <p className="text-xs font-semibold text-foreground truncate">
                              {p.empresaTransporte || 'Encomienda'}
                              {p.codigoPin ? ` · PIN ${p.codigoPin}` : ''}
                            </p>
                            <p className="text-[11px] text-muted-foreground">
                              Destino: {p.numeroUnidad ? `Unidad ${p.numeroUnidad}` : p.destinatario || 'Residente'}
                            </p>
                          </div>
                          <Badge variant="warning" className="text-[10px] uppercase font-bold flex-shrink-0">
                            En espera
                          </Badge>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Card Control: Multas y Sanciones */}
              <Card className="border-border/80 shadow-xs">
                <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/50">
                  <div className="space-y-0.5">
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <Gavel className="h-4 w-4 text-rose-500" aria-hidden="true" />
                      Sanciones y Multas
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Infracciones pendientes de recaudo
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate('/sanciones-admin')}
                    className="text-xs text-primary hover:text-primary font-medium"
                  >
                    Ver
                    <ArrowUpRight className="h-3.5 w-3.5 ml-1" aria-hidden="true" />
                  </Button>
                </CardHeader>

                <CardContent className="pt-3">
                  {loadingMultas ? (
                    <LoadingState message="Cargando multas..." size="sm" />
                  ) : multasPendientesList.length === 0 ? (
                    <p className="py-5 text-center text-xs text-muted-foreground">
                      No hay multas ni sanciones pendientes de pago.
                    </p>
                  ) : (
                    <div className="divide-y divide-border/60">
                      {multasPendientesList.slice(0, 3).map((m) => (
                        <div key={m.idMulta || m.id} className="py-2.5 flex items-center justify-between">
                          <div className="min-w-0 pr-2">
                            <p className="text-xs font-semibold text-foreground truncate">
                              {m.tipo || m.motivo || 'Infracción Convivencia'}
                            </p>
                            <p className="text-[11px] text-muted-foreground">
                              {m.numeroApartamento ? `Apto ${m.numeroApartamento}` : 'Unidad'}
                              {m.nombreResidente ? ` · ${m.nombreResidente}` : ''}
                            </p>
                          </div>
                          <p className="text-xs font-bold text-rose-600 dark:text-rose-400 flex-shrink-0">
                            {formatCurrency(m.monto)}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>

          {/* 5. Accesos Rápidos Operativos */}
          <div className="space-y-3 pt-2">
            <h2 className="text-sm font-semibold tracking-wider text-muted-foreground uppercase">
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