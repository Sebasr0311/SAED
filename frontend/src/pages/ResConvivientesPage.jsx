import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Shield, ArrowLeft, Home } from 'lucide-react';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useTiposDocumento } from '../lib/hooks.js';
import api from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import ConvivientesSection from '../components/residents/ConvivientesSection.jsx';

export default function ResConvivientesPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { tiposDoc } = useTiposDocumento();

  const residentId = user?.idPersona || user?.idResidente || user?.idUsuario;

  // 1. Datos personales del titular
  const { data: personaData } = useFetch(
    () => api.get('/personas/me').catch(() => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null))),
    [residentId]
  );
  const perfil = useMemo(() => personaData?.raw || personaData || {}, [personaData]);
  const resolvedPersonaId = perfil.id || perfil.idPersona || user?.idPersona || user?.idResidente || residentId;

  // 2. Dashboard de unidad
  const { data: dashboardData } = useFetch(
    () => (resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/dashboard`) : Promise.resolve(null)),
    [resolvedPersonaId]
  );
  const dashboard = useMemo(() => dashboardData?.raw || dashboardData || {}, [dashboardData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);

  // 3. Resolucion de unidad
  const unitId =
    user?.idUnidad ||
    user?.idApartamento ||
    perfil.idApartamento ||
    perfil.idUnidad ||
    user?.asignaciones?.[0]?.idUnidad ||
    aptoInfo.idApartamento ||
    aptoInfo.id ||
    null;

  const { data: unitData } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)),
    [unitId]
  );
  const u = useMemo(() => unitData?.raw || unitData || {}, [unitData]);

  // 4. Residentes / cohabitantes de la unidad
  const {
    data: unitResidentsData,
    loading: residentsLoading,
    refetch: refetchResidents,
  } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}/residents`) : Promise.resolve([])),
    [unitId]
  );

  // 5. Cupo parametrizado de convivientes
  const {
    data: quotaData,
    loading: quotaLoading,
    refetch: refetchQuota,
  } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}/residents/quota`) : Promise.resolve(null)),
    [unitId]
  );

  const handleRefreshHabitantes = () => {
    if (refetchResidents) refetchResidents().catch(() => {});
    if (refetchQuota) refetchQuota().catch(() => {});
  };

  const nombreCompleto = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres || user?.primerNombre || user?.nombreCompleto || user?.username || 'Residente';
    const sNombre = perfil.segundoNombre || user?.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos || user?.primerApellido || '';
    const sApellido = perfil.segundoApellido || user?.segundoApellido || '';
    const fullName = `${pNombre} ${sNombre} ${pApellido} ${sApellido}`.replace(/\s+/g, ' ').trim();
    return fullName || user?.nombreCompleto || user?.username || 'Residente';
  }, [perfil, user]);

  const unitLabel = u.identificador || aptoInfo.numeroApartamento || aptoInfo.identificador || 'Apto';
  const towerLabel = aptoInfo.torre ? `Torre ${aptoInfo.torre}` : u.tipo || 'Residencial';

  return (
    <PageContainer className="pb-12">
      <Button
        variant="ghost"
        size="sm"
        onClick={() => navigate('/residente-dashboard')}
        className="-ml-2 min-h-10 gap-2"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Volver a mi panel
      </Button>

      <PageHeader
        title="Convivientes y habitantes de la unidad"
        description="Como residente principal, puedes registrar a los integrantes de tu hogar, administrar su estado y otorgarles acceso independiente al portal de SAED."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline" className="gap-1.5 px-2.5 py-1">
              <Home className="h-3.5 w-3.5 text-text-muted" aria-hidden="true" />
              Unidad <strong className="font-bold tabular-nums">{unitLabel}</strong>
            </Badge>
            <Badge variant="secondary" className="gap-1.5 px-2.5 py-1">
              <Building2 className="h-3.5 w-3.5 text-text-muted" aria-hidden="true" />
              {towerLabel}
            </Badge>
          </div>
        }
      />

      <div className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4 shadow-sm">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-tint-primary-bg text-tint-primary-fg">
          <Shield className="h-[18px] w-[18px]" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <h2 className="text-sm font-bold text-on-surface">Cada conviviente tiene su propia cuenta</h2>
          <p className="max-w-[72ch] text-[13px] leading-relaxed text-text-secondary">
            Cada habitante que registres recibe su propia cuenta de usuario con el rol{' '}
            <span className="font-semibold text-on-surface">Residente conviviente</span>. Podrá gestionar sus visitas,
            generar códigos QR de acceso, recibir correspondencia y consultar comunicados sin depender de tus
            credenciales.
          </p>
        </div>
      </div>

      {/* Seccion Operativa de Convivientes con Quota y Formulario */}
      <ConvivientesSection
        unitId={unitId}
        residentId={residentId}
        tiposDoc={tiposDoc}
        titularFallback={{
          id: residentId,
          nombres: perfil.primerNombre || perfil.nombres || user?.nombreCompleto?.split(' ')[0] || user?.nombreUsuario || 'Titular',
          apellidos: perfil.primerApellido || perfil.apellidos || (user?.nombreCompleto ? user.nombreCompleto.split(' ').slice(1).join(' ') : ''),
          numeroDocumento: perfil.numeroDocumento || user?.numeroDocumento || '—',
          tipoResidente: 'TITULAR',
          estado: 'ACTIVO',
        }}
        quotaData={quotaData}
        quotaLoading={quotaLoading}
        residentsData={unitResidentsData}
        residentsLoading={residentsLoading}
        onRefresh={handleRefreshHabitantes}
      />
    </PageContainer>
  );
}
