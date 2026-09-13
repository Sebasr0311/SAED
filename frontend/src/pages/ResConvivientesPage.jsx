import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Users, Building2, Shield, ArrowLeft, Home } from 'lucide-react';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useTiposDocumento } from '../lib/hooks.js';
import api from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Card, CardContent } from '../components/ui/card.tsx';
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
    () => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null)),
    [residentId]
  );
  const perfil = useMemo(() => personaData?.raw || personaData || {}, [personaData]);

  // 2. Dashboard de unidad
  const { data: dashboardData } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/dashboard`) : Promise.resolve(null)),
    [residentId]
  );
  const dashboard = useMemo(() => dashboardData?.raw || dashboardData || {}, [dashboardData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);

  // 3. Resolucion de unidad
  const unitId =
    user?.idUnidad ||
    user?.idApartamento ||
    user?.asignaciones?.[0]?.idUnidad ||
    perfil.idApartamento ||
    perfil.idUnidad ||
    aptoInfo.idApartamento ||
    aptoInfo.id ||
    1;

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
    if (refetchResidents) refetchResidents();
    if (refetchQuota) refetchQuota();
  };

  const nombreCompleto = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres || user?.nombreCompleto || 'Carlos';
    const sNombre = perfil.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos || (user?.nombreCompleto ? '' : 'Martínez');
    const sApellido = perfil.segundoApellido || '';
    return `${pNombre} ${sNombre} ${pApellido} ${sApellido}`.replace(/\s+/g, ' ').trim();
  }, [perfil, user]);

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Boton de regreso y encabezado */}
      <div className="flex items-center justify-between gap-4">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/residente-dashboard')}
          className="gap-2 text-muted-foreground hover:text-foreground -ml-2"
        >
          <ArrowLeft className="w-4 h-4" />
          Volver a Mi Panel
        </Button>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="gap-1.5 py-1 px-3 border-primary/30 text-primary bg-primary/5">
            <Home className="w-3.5 h-3.5" />
            Unidad: <strong className="font-semibold text-foreground ml-0.5">{u.identificador || aptoInfo.numeroApartamento || aptoInfo.identificador || 'Apto'}</strong>
          </Badge>
          <Badge variant="secondary" className="gap-1.5 py-1 px-3">
            <Building2 className="w-3.5 h-3.5 text-muted-foreground" />
            {aptoInfo.torre ? `Torre ${aptoInfo.torre}` : u.tipo || 'Residencial'}
          </Badge>
        </div>
      </div>

      <PageHeader
        title="Convivientes y Habitantes de la Unidad"
        description="Como Residente Principal, podés registrar a los integrantes de tu hogar, administrar su estado y otorgarles acceso independiente al portal de SAED."
      />

      {/* Banner Informativo de Reglas de Convivencia */}
      <Card className="border-primary/20 bg-gradient-to-r from-primary/5 via-background to-background">
        <CardContent className="p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center gap-4">
          <div className="p-3 rounded-xl bg-primary/10 text-primary shrink-0">
            <Shield className="w-6 h-6" />
          </div>
          <div className="space-y-1 flex-1">
            <h4 className="text-sm font-semibold text-foreground flex items-center gap-2">
              Autonomía y Seguridad para tu Hogar
              <Badge variant="outline" className="text-[10px] uppercase font-bold text-emerald-600 border-emerald-500/30 bg-emerald-500/10">
                Rol Conviviente
              </Badge>
            </h4>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Cada habitante que registres recibe automáticamente su propia cuenta de usuario (<code className="text-xs font-mono font-bold text-primary">RESIDENTE_CONVIVENCIA</code>). 
              Podrá gestionar sus propias visitas, generar códigos QR de acceso, recibir correspondencia y consultar comunicados sin depender de tus credenciales personales.
            </p>
          </div>
        </CardContent>
      </Card>

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
    </div>
  );
}
