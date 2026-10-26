import { useState, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  User,
  Mail,
  Phone,
  Home,
  Building2,
  Shield,
  CreditCard,
  Calendar,
  Copy,
  Check,
  Pencil,
  Users,
  Car,
  CheckCircle2,
  MapPin,
  Layers,
  Key,
  AlertCircle,
  ArrowRight,
  FileText,
  ChevronRight,
} from 'lucide-react';

import { useAuth } from '../lib/AuthContext.jsx';
import { useTenant } from '../lib/TenantContext.jsx';
import { useFetch, useLiveValidation, useTiposDocumento } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, cn } from '../lib/utils.js';
import { valTelefono, valEmail } from '../lib/validation.js';

import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input } from '../components/ui/Form.jsx';
import ChangePasswordModal from '../components/ui/ChangePasswordModal.jsx';
import ConvivientesSection from '../components/residents/ConvivientesSection.jsx';
import ResidentVehiclesSection from '../components/residents/ResidentVehiclesSection.jsx';
import ResidentPetsSection from '../components/residents/ResidentPetsSection.jsx';

function CopyChip({ text, label, icon: Icon }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = (e) => {
    e.stopPropagation();
    if (!text) return;
    navigator.clipboard?.writeText(String(text)).catch(() => {});
    setCopied(true);
    toast.success(`${label || 'Valor'} copiado al portapapeles`);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!text || text === '—') return null;

  return (
    <button
      type="button"
      onClick={handleCopy}
      title={`Copiar ${label || ''}: ${text}`}
      aria-label={`Copiar ${label || 'valor'}: ${text}`}
      className="group inline-flex min-h-8 max-w-full items-center gap-1.5 rounded-md border border-border bg-surface px-2.5 py-1 text-xs font-medium text-text-secondary transition-colors duration-150 hover:border-primary/50 hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {Icon && <Icon className="h-3.5 w-3.5 shrink-0 text-text-muted" aria-hidden="true" />}
      <span className="max-w-[180px] truncate tabular-nums">{text}</span>
      {copied ? (
        <Check className="h-3 w-3 shrink-0 text-tint-green-fg" aria-hidden="true" />
      ) : (
        <Copy className="h-3 w-3 shrink-0 text-text-muted group-hover:text-on-surface" aria-hidden="true" />
      )}
    </button>
  );
}

function DetailItem({ icon: Icon, label, value, badge, subtext, isMono = false }) {
  return (
    <div className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-text-muted" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <dt className="text-xs font-semibold text-text-muted">{label}</dt>
          {badge}
        </div>
        <dd className={cn('mt-0.5 break-words text-sm font-bold text-on-surface', isMono && 'tabular-nums')}>
          {value || <span className="font-medium text-text-muted">Sin registrar</span>}
        </dd>
        {subtext && <dd className="mt-0.5 text-xs text-text-muted">{subtext}</dd>}
      </div>
    </div>
  );
}

export default function ResPerfilPage() {
  const { user } = useAuth();
  const tenant = useTenant();
  const navigate = useNavigate();
  const isConviviente =
    user?.rol === 'RESIDENTE_CONVIVENCIA' ||
    user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user?.tipoResidente === 'CONVIVIENTE' ||
    user?.tipoRelacion === 'CONVIVIENTE';

  // Estados de edición
  const [modalOpen, setModalOpen] = useState(false);
  const [edit, setEdit] = useState({ telefono: '', email: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const { touch, fieldError } = useLiveValidation();

  // Modal de cambio de contraseña
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);

  // Tipos de documento
  const { tiposDoc } = useTiposDocumento();

  // Resolución de IDs
  const residentId = user?.idPersona || user?.idResidente || user?.idUsuario;

  // 1. Datos personales de la persona
  const { data: personaData, loading: personaLoading, error: personaError, refetch: refetchPersona } = useFetch(
    () => api.get('/personas/me').catch(() => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null))),
    [residentId]
  );
  const perfil = useMemo(() => personaData?.raw || personaData || {}, [personaData]);
  const resolvedPersonaId = perfil.id || perfil.idPersona || user?.idPersona || user?.idResidente || residentId;

  // 2. Dashboard financiero y de unidad del residente (Solo residente titular)
  const { data: dashboardData, loading: dashboardLoading, error: dashboardError, refetch: refetchDashboard } = useFetch(
    () => (!isConviviente && resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/dashboard`) : Promise.resolve(null)),
    [resolvedPersonaId, isConviviente]
  );
  const dashboard = useMemo(() => dashboardData?.raw || dashboardData || {}, [dashboardData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);
  const contratoInfo = useMemo(() => dashboard.contrato || {}, [dashboard]);
  const cuotas = useMemo(() => dashboard.cuotas || [], [dashboard]);

  // 3. Ficha de la unidad oficial
  const unitId =
    user?.idUnidad ||
    user?.idApartamento ||
    perfil.idApartamento ||
    perfil.idUnidad ||
    user?.asignaciones?.[0]?.idUnidad ||
    aptoInfo.idApartamento ||
    aptoInfo.id ||
    null;
  const { data: unitData, loading: unitLoading } = useFetch(() => (unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)), [unitId]);
  const u = useMemo(() => unitData?.raw || unitData || {}, [unitData]);

  // 4. Residentes / cohabitantes de la unidad (household members without titular privileges do not call these endpoints)
  const { data: unitResidentsData, loading: residentsLoading, refetch: refetchResidents } = useFetch(
    () => (unitId && !isConviviente ? api.get(`/units/${unitId}/residents`) : Promise.resolve([])),
    [unitId, isConviviente]
  );

  // 4b. Cupo parametrizado de convivientes de la unidad (P2-01)
  const {
    data: quotaData,
    loading: quotaLoading,
    refetch: refetchQuota,
  } = useFetch(
    () => (unitId && !isConviviente ? api.get(`/units/${unitId}/residents/quota`) : Promise.resolve(null)),
    [unitId, isConviviente]
  );

  const handleRefreshHabitantes = () => {
    if (refetchResidents) refetchResidents().catch(() => {});
    if (refetchQuota) refetchQuota().catch(() => {});
  };

  // 5. Asignaciones de parqueadero
  const { data: asignacionesParqueadero } = useFetch(
    () => (isConviviente ? Promise.resolve([]) : api.get('/parqueaderos/asignaciones')),
    [isConviviente]
  );

  // 6. Visitantes frecuentes
  const { data: frecuentesData } = useFetch(
    () => (resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/frecuentes`) : Promise.resolve([])),
    [resolvedPersonaId]
  );

  // Normalizaciones y cálculos
  const nombreCompleto = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres || user?.primerNombre || user?.nombreCompleto || user?.username || 'Residente';
    const sNombre = perfil.segundoNombre || user?.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos || user?.primerApellido || '';
    const sApellido = perfil.segundoApellido || user?.segundoApellido || '';
    const fullName = `${pNombre} ${sNombre} ${pApellido} ${sApellido}`.replace(/\s+/g, ' ').trim();
    return fullName || user?.nombreCompleto || user?.username || 'Residente';
  }, [perfil, user]);

  const iniciales = useMemo(() => {
    const partes = nombreCompleto.split(' ').filter(Boolean);
    if (!partes.length) return 'RE';
    return (partes[0][0] + (partes[1]?.[0] || '')).toUpperCase();
  }, [nombreCompleto]);

  const isUnitDataLoading = personaLoading || dashboardLoading || (unitId && unitLoading);

  const rawNumeroApto =
    dashboard.identificadorUnidad ||
    u.identificador ||
    u.numero ||
    aptoInfo.numero ||
    aptoInfo.identificador ||
    perfil.numeroApartamento ||
    user?.identificadorUnidad ||
    tenant?.activeUnitNumber;

  const numeroApto = rawNumeroApto || (isUnitDataLoading ? '…' : 'Sin asignar');
  const nombreBloque = u.bloqueNombre || aptoInfo.bloque || aptoInfo.torre || (isUnitDataLoading ? '…' : '—');
  const pisoApto = aptoInfo.piso || u.piso || (rawNumeroApto && rawNumeroApto.match(/\d+/) ? rawNumeroApto.match(/\d+/)[0][0] : '—');
  const areaApto = u.areaM2 ? `${u.areaM2} m²` : aptoInfo.areaM2 ? `${aptoInfo.areaM2} m²` : (isUnitDataLoading ? '…' : '—');
  const tipoUnidad = u.tipoUnidadNombre || aptoInfo.tipo || 'Apartamento';
  const coeficiente = u.coeficienteCopropiedad
    ? `${(Number(u.coeficienteCopropiedad) * 100).toFixed(2)}%`
    : '—';
  const estadoUnidad = u.estado || aptoInfo.estado || 'HABITADA';

  // Coarrendatarios y compañeros
  const listaHabitantes = useMemo(() => {
    const rawList = Array.isArray(unitResidentsData)
      ? unitResidentsData
      : unitResidentsData?.items || [];
    if (rawList.length > 0) return rawList;
    if (perfil.primerNombre || user?.nombreCompleto || user?.username) {
      return [
        {
          id: resolvedPersonaId || residentId || 0,
          nombres: perfil.primerNombre || perfil.nombres || user?.nombreCompleto || user?.username || 'Titular',
          apellidos: perfil.primerApellido || perfil.apellidos || '',
          numeroDocumento: perfil.numeroDocumento || '—',
          tipoResidente: 'TITULAR',
          estado: 'ACTIVO',
        },
      ];
    }
    return [];
  }, [unitResidentsData, perfil, residentId, resolvedPersonaId, user]);

  // Cuotas y estado financiero
  const cuotasPendientes = useMemo(() => {
    return (cuotas || []).filter((c) => c.estado !== 'PAGADA');
  }, [cuotas]);
  const alDia = cuotasPendientes.length === 0;
  const tipoContrato = contratoInfo.tipoContrato || (isConviviente ? 'Residente conviviente' : 'Residente titular');

  // Parqueadero asignado
  const parqueaderoAsignado = useMemo(() => {
    const list = Array.isArray(asignacionesParqueadero) ? asignacionesParqueadero : [];
    return list.find(
      (p) =>
        Number(p.idUnidad) === Number(unitId) ||
        Number(p.idPersona) === Number(residentId) ||
        String(p.numeroApartamento || '').includes(String(numeroApto))
    );
  }, [asignacionesParqueadero, unitId, residentId, numeroApto]);

  const numFrecuentes = useMemo(() => {
    const f = Array.isArray(frecuentesData) ? frecuentesData : frecuentesData?.items || [];
    return f.length;
  }, [frecuentesData]);

  // Funciones de edición
  function openEdit() {
    setEdit({
      telefono: perfil.telefono || '',
      email: perfil.email || user?.email || '',
    });
    setErrors({});
    setModalOpen(true);
  }

  function validate() {
    const e = {};
    const rTel = valTelefono(edit.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(edit.email, { required: false });
    if (!rEmail.ok) e.email = rEmail.mensaje;
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSave() {
    if (savingRef.current) return;
    if (!validate()) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const targetPersonaId = resolvedPersonaId || residentId;
      await api.put(`/personas/${targetPersonaId}`, {
        tipoDocumentoId: Number(perfil.tipoDocumentoId || perfil.idTipoDoc || 1),
        numeroDocumento: perfil.numeroDocumento || user?.numeroDocumento || '',
        tipoPersona: perfil.tipoPersona || 'NATURAL',
        primerNombre: perfil.primerNombre || perfil.nombres || user?.primerNombre || user?.nombreCompleto || '',
        segundoNombre: perfil.segundoNombre || user?.segundoNombre || '',
        primerApellido: perfil.primerApellido || perfil.apellidos || user?.primerApellido || '',
        segundoApellido: perfil.segundoApellido || user?.segundoApellido || '',
        telefono: edit.telefono ? edit.telefono.replace(/\D/g, '') : null,
        email: edit.email?.trim() || null,
      });
      toast.success('Perfil de residente actualizado exitosamente');
      setModalOpen(false);
      refetchPersona().catch(() => {});
      refetchDashboard().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al actualizar el perfil');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <PageContainer className="pb-12">
      <PageHeader
        title="Mi perfil y apartamento"
        subtitle="Tus datos de contacto, la ficha de tu unidad y los accesos a tus gestiones."
      />

      {personaError && !personaLoading && (
        <ErrorState
          compact
          title="No se pudieron cargar tus datos personales"
          message="Algunos campos pueden aparecer vacíos. Intenta de nuevo en unos segundos."
          onRetry={() => refetchPersona().catch(() => {})}
        />
      )}

      {/* Identity summary */}
      <section
        aria-labelledby="perfil-identidad"
        className="relative overflow-hidden rounded-lg border border-border bg-surface shadow-sm"
      >
        <div
          className="app-studs pointer-events-none absolute inset-y-0 right-0 hidden w-1/2 sm:block [mask-image:linear-gradient(to_left,black,transparent)]"
          aria-hidden="true"
        />
        <div className="relative flex flex-col gap-5 p-5 sm:p-6 md:flex-row md:items-center md:justify-between">
          <div className="flex min-w-0 items-start gap-4">
            <div
              className="app-numeral flex h-14 w-14 shrink-0 items-center justify-center rounded-md bg-tint-purple-bg text-xl text-on-surface ring-1 ring-inset ring-keyline/15"
              aria-hidden="true"
            >
              {personaLoading ? '' : iniciales}
            </div>
            <div className="min-w-0 space-y-2">
              {personaLoading ? (
                <div className="space-y-2" aria-hidden="true">
                  <div className="app-skeleton h-5 w-48" />
                  <div className="app-skeleton h-3 w-32" />
                </div>
              ) : (
                <h2 id="perfil-identidad" className="app-display break-words text-xl leading-tight text-on-surface sm:text-2xl">
                  {nombreCompleto}
                </h2>
              )}
              <p className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-text-secondary">
                <span className="inline-flex items-center gap-1.5 font-semibold text-on-surface">
                  <Home className="h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
                  <span className="tabular-nums">{numeroApto}</span>
                </span>
                {nombreBloque && nombreBloque !== '—' && (
                  <>
                    <span className="text-text-muted" aria-hidden="true">·</span>
                    <span>{nombreBloque}</span>
                  </>
                )}
                {user?.nombrePropiedad && (
                  <>
                    <span className="text-text-muted" aria-hidden="true">·</span>
                    <span className="min-w-0 truncate">{user.nombrePropiedad}</span>
                  </>
                )}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="lavender">{isConviviente ? 'Residente conviviente' : 'Residente titular'}</Badge>
                {isConviviente ? (
                  <Badge variant="success">
                    <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                    Conviviente autorizado
                  </Badge>
                ) : dashboardLoading || dashboardError ? null : alDia ? (
                  <Badge variant="success">
                    <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                    Al día
                  </Badge>
                ) : (
                  <Badge variant="warning">
                    <AlertCircle className="h-3 w-3" aria-hidden="true" />
                    Saldo pendiente
                  </Badge>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <CopyChip text={perfil.numeroDocumento || user?.numeroDocumento} label="Documento" icon={Shield} />
                <CopyChip
                  text={perfil.telefono || perfil.celular || perfil.telefonoContacto || user?.telefono}
                  label="Teléfono"
                  icon={Phone}
                />
                <CopyChip text={perfil.email || user?.email} label="Correo" icon={Mail} />
              </div>
            </div>
          </div>
          <div className="flex shrink-0 items-center justify-between gap-3 border-t border-border pt-4 md:flex-col md:items-end md:border-l md:border-t-0 md:pl-6 md:pt-0">
            <div className="text-left md:text-right">
              <p className="text-xs font-semibold text-text-muted">Unidad</p>
              <p className="app-numeral text-[1.625rem] text-on-surface">{numeroApto}</p>
              <p className="text-xs text-text-muted">{tipoUnidad}</p>
            </div>
          </div>
        </div>
      </section>

      {/* Status strip */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Apartamento"
          value={numeroApto}
          context={[nombreBloque, areaApto].filter((v) => v && v !== '—' && v !== '…').join(' · ') || 'Datos de la unidad'}
          icon={Building2}
          variant="primary"
        />
        {isConviviente ? (
          <MetricCard
            label="Zonas comunes"
            value="Habilitado"
            context="Puedes reservar áreas sociales"
            icon={Calendar}
            variant="info"
          />
        ) : (
          <MetricCard
            label="Estado de cartera"
            value={
              dashboardLoading ? '…' : dashboardError ? '—' : alDia ? 'Al día' : `${cuotasPendientes.length} pendiente(s)`
            }
            context={dashboardError ? 'No se pudo consultar' : alDia ? 'Sin obligaciones en mora' : 'Requiere pago oportuno'}
            icon={CreditCard}
            variant={dashboardError ? 'secondary' : alDia ? 'success' : 'warning'}
          />
        )}
        <MetricCard
          label="Habitantes"
          value={listaHabitantes.length}
          context="Registrados en la unidad"
          icon={Users}
          variant="info"
        />
        <MetricCard
          label="Parqueadero"
          value={
            parqueaderoAsignado
              ? parqueaderoAsignado.identificador || parqueaderoAsignado.numero || 'Asignado'
              : 'Sin celda'
          }
          context={parqueaderoAsignado ? parqueaderoAsignado.tipo || 'Celda asignada' : 'Sin celda privada asignada'}
          icon={Car}
          variant="secondary"
        />
      </div>

      <Tabs defaultValue="perfil" className="space-y-6">
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
          <TabsList className="w-max">
            <TabsTrigger value="perfil" className="gap-2">
              <User className="h-4 w-4" aria-hidden="true" />
              Mi perfil
            </TabsTrigger>
            <TabsTrigger value="apartamento" className="gap-2">
              <Home className="h-4 w-4" aria-hidden="true" />
              Mi apartamento
            </TabsTrigger>
            <TabsTrigger value="gestiones" className="gap-2">
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
              Gestiones
            </TabsTrigger>
          </TabsList>
        </div>

        {/* Tab 1: personal data and account */}
        <TabsContent value="perfil" className="space-y-6">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            <Panel icon={Shield} title="Identidad" description="Datos registrados ante la administración">
              {personaLoading ? (
                <LoadingState size="sm" message="Cargando tus datos…" className="p-0" />
              ) : (
                <dl className="divide-y divide-border-subtle">
                  <DetailItem icon={User} label="Nombre completo" value={nombreCompleto} />
                  <DetailItem
                    icon={Shield}
                    label="Documento de identidad"
                    value={perfil.numeroDocumento || user?.numeroDocumento}
                    subtext={perfil.tipoDocumentoNombre}
                    isMono
                  />
                  <DetailItem
                    icon={Calendar}
                    label="Fecha de nacimiento"
                    value={perfil.fechaNacimiento ? formatDate(perfil.fechaNacimiento) : null}
                  />
                  <DetailItem
                    icon={Layers}
                    label="Tipo de persona"
                    value={perfil.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : 'Persona natural'}
                    subtext={isConviviente ? 'Residente conviviente' : 'Residente titular'}
                  />
                </dl>
              )}
            </Panel>

            <Panel
              icon={Phone}
              title="Contacto"
              description="Se usa para avisos, citaciones y emergencias"
              action={
                <Button variant="outline" size="sm" onClick={openEdit} className="min-h-10 sm:min-h-8">
                  <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                  Editar
                </Button>
              }
            >
              {personaLoading ? (
                <LoadingState size="sm" message="Cargando tus datos de contacto…" className="p-0" />
              ) : (
                <dl className="divide-y divide-border-subtle">
                  <DetailItem
                    icon={Phone}
                    label="Teléfono celular"
                    value={perfil.telefono || perfil.celular || perfil.telefonoContacto || user?.telefono}
                    isMono
                  />
                  <DetailItem icon={Mail} label="Correo electrónico" value={perfil.email || user?.email} />
                  <DetailItem
                    icon={MapPin}
                    label="Dirección de la copropiedad"
                    value={user?.direccionPropiedad}
                    subtext={user?.ciudadPropiedad || ''}
                  />
                  <DetailItem icon={Building2} label="Copropiedad" value={user?.nombrePropiedad} />
                </dl>
              )}
            </Panel>

            <Panel
              icon={Key}
              title="Cuenta de usuario"
              description="Tu acceso a la plataforma"
              className="md:col-span-2"
              action={
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setChangePasswordOpen(true)}
                  className="min-h-10 sm:min-h-8"
                >
                  <Key className="h-3.5 w-3.5" aria-hidden="true" />
                  Cambiar contraseña
                </Button>
              }
            >
              <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
                <div>
                  <dt className="text-xs font-semibold text-text-muted">Nombre de usuario</dt>
                  <dd className="mt-1 break-all font-mono text-sm font-bold text-on-surface">
                    @{user?.nombreUsuario || user?.username || perfil.nombreUsuario || 'residente'}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold text-text-muted">Rol asignado</dt>
                  <dd className="mt-1 space-y-1">
                    <Badge variant="lavender">{isConviviente ? 'Residente conviviente' : 'Residente titular'}</Badge>
                    <p className="text-xs text-text-muted">
                      {isConviviente
                        ? 'Acceso a visitas, buzón, PQRS, zonas comunes, vehículos y mascotas.'
                        : 'Acceso a cuotas, visitas, buzón, reservas y gestión del núcleo familiar.'}
                    </p>
                  </dd>
                </div>
              </dl>
            </Panel>
          </div>
        </TabsContent>

        {/* Tab 2: unit */}
        <TabsContent value="apartamento" className="space-y-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <Panel
              icon={Building2}
              title="Ficha del inmueble"
              description="Datos de la unidad y coeficiente de copropiedad"
              className="lg:col-span-2"
              action={<Badge variant="success">{estadoUnidad}</Badge>}
            >
              {isUnitDataLoading ? (
                <LoadingState size="sm" message="Cargando la ficha de tu unidad…" className="p-0" />
              ) : (
                <div className="space-y-5">
                  <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-md border border-border bg-border sm:grid-cols-3">
                    {[
                      ['Nomenclatura', numeroApto],
                      ['Bloque / torre', nombreBloque],
                      ['Piso', pisoApto],
                      ['Área privada', areaApto],
                      ['Coeficiente', coeficiente],
                      ['Destinación', 'Residencial'],
                    ].map(([label, value]) => (
                      <div key={label} className="bg-surface p-3">
                        <dt className="text-xs font-semibold text-text-muted">{label}</dt>
                        <dd className="mt-0.5 text-[15px] font-bold tabular-nums text-on-surface">{value}</dd>
                      </div>
                    ))}
                  </dl>

                  <div>
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                      <h4 className="text-[13px] font-bold text-on-surface">Tenencia y cuota de administración</h4>
                      <span className="text-xs text-text-muted">Registrado por la administración · solo lectura</span>
                    </div>
                    <dl className="divide-y divide-border-subtle rounded-md border border-border px-3">
                      <DetailItem
                        icon={FileText}
                        label="Modalidad"
                        value={tipoContrato}
                        subtext={contratoInfo.estado ? `Estado: ${contratoInfo.estado}` : undefined}
                      />
                      <DetailItem
                        icon={CreditCard}
                        label="Cuota de administración"
                        value={
                          isConviviente
                            ? 'Gestionada por el titular'
                            : contratoInfo.valorMensual != null
                              ? formatCurrency(contratoInfo.valorMensual)
                              : null
                        }
                        subtext={
                          isConviviente
                            ? 'La atiende el propietario o el residente titular'
                            : contratoInfo.diaPago
                              ? `Corte de facturación: día ${contratoInfo.diaPago} de cada mes`
                              : undefined
                        }
                        isMono={!isConviviente}
                      />
                    </dl>
                  </div>
                </div>
              )}
            </Panel>

            <Panel icon={Car} title="Parqueadero" description="Celdas asignadas a la unidad">
              <div className="space-y-3">
                {parqueaderoAsignado ? (
                  <div className="space-y-1.5 rounded-md border border-border bg-surface-container p-4">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-text-muted">Celda asignada</span>
                      <Badge variant="info">Asignado</Badge>
                    </div>
                    <p className="app-numeral text-[1.625rem] text-on-surface">
                      {parqueaderoAsignado.identificador || parqueaderoAsignado.numero || '—'}
                    </p>
                    {parqueaderoAsignado.tipo && (
                      <p className="text-xs text-text-muted">Tipo: {parqueaderoAsignado.tipo}</p>
                    )}
                    {parqueaderoAsignado.placa && (
                      <div className="flex items-center justify-between border-t border-border pt-2 text-xs">
                        <span className="text-text-muted">Vehículo registrado</span>
                        <span className="font-mono font-bold text-on-surface">{parqueaderoAsignado.placa}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <EmptyState
                    icon={Car}
                    title="Sin celda privada asignada"
                    subtitle="El uso de parqueadero comunal depende de la disponibilidad y del reglamento."
                    className="py-6"
                  />
                )}
                <p className="flex items-start gap-2 text-xs text-text-secondary">
                  <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-text-muted" aria-hidden="true" />
                  Gestiona los vehículos de tu hogar en la sección de vehículos más abajo.
                </p>
              </div>
            </Panel>

            {isConviviente && (
              <div className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4 shadow-sm lg:col-span-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-tint-purple-bg text-tint-purple-fg">
                  <Shield className="h-[18px] w-[18px]" aria-hidden="true" />
                </div>
                <div className="space-y-1">
                  <h3 className="text-sm font-bold text-on-surface">Perfil de residente conviviente</h3>
                  <p className="max-w-[80ch] text-[13px] leading-relaxed text-text-secondary">
                    Como integrante autorizado del hogar en <strong className="text-on-surface">{numeroApto}</strong>,
                    puedes registrar vehículos y mascotas de la unidad, reservar zonas comunes, emitir pases QR para
                    visitantes, consultar encomiendas en el buzón y radicar PQRS. La administración de convivientes y la
                    facturación corresponden al residente titular.
                  </p>
                </div>
              </div>
            )}

            {!isConviviente && (
              <ConvivientesSection
                unitId={unitId}
                residentId={residentId}
                tiposDoc={tiposDoc}
                titularFallback={{
                  id: residentId,
                  nombres:
                    perfil.primerNombre ||
                    perfil.nombres ||
                    user?.nombreCompleto?.split(' ')[0] ||
                    user?.nombreUsuario ||
                    'Titular',
                  apellidos:
                    perfil.primerApellido ||
                    perfil.apellidos ||
                    (user?.nombreCompleto ? user.nombreCompleto.split(' ').slice(1).join(' ') : ''),
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
            )}

            <div className="lg:col-span-3">
              <ResidentVehiclesSection unitId={unitId} defaultPersonaId={residentId} />
            </div>

            <div className="lg:col-span-3">
              <ResidentPetsSection unitId={unitId} defaultPersonaId={residentId} />
            </div>
          </div>
        </TabsContent>

        {/* Tab 3: shortcuts */}
        <TabsContent value="gestiones">
          <section className="rounded-lg border border-border bg-surface shadow-sm" aria-label="Accesos a tus gestiones">
            <ul className="divide-y divide-border-subtle">
              {!isConviviente && (
                <ShortcutRow
                  icon={CreditCard}
                  title="Mis cuotas y pagos"
                  description="Consulta saldos, descarga comprobantes y paga en línea con PSE o tarjeta a través de Wompi."
                  meta={
                    dashboardLoading || dashboardError ? null : (
                      <Badge variant={alDia ? 'success' : 'warning'}>
                        {alDia ? 'Al día' : `${cuotasPendientes.length} pendiente(s)`}
                      </Badge>
                    )
                  }
                  onClick={() => navigate('/res-cuotas')}
                />
              )}
              <ShortcutRow
                icon={Key}
                title="Registrar visita con QR"
                description="Autoriza el ingreso por portería con un código QR."
                onClick={() => navigate('/res-visitas')}
              />
              <ShortcutRow
                icon={Users}
                title="Visitantes frecuentes"
                description="Familiares, empleados y personas con ingreso recurrente."
                meta={
                  <Badge variant="outline" className="tabular-nums">
                    {numFrecuentes} registrado(s)
                  </Badge>
                }
                onClick={() => navigate('/res-visitas')}
              />
              <ShortcutRow
                icon={Mail}
                title="Buzón y comunicados"
                description="Circulares, encomiendas con PIN de retiro y avisos de mantenimiento."
                onClick={() => navigate('/res-buzon')}
              />
              <ShortcutRow
                icon={AlertCircle}
                title="PQRS y soporte"
                description="Solicitudes, quejas de convivencia o reportes de fallas con seguimiento."
                onClick={() => navigate('/res-quejas')}
              />
              <ShortcutRow
                icon={Home}
                title="Mi panel"
                description="Lo que requiere tu atención y la actividad reciente de tu unidad."
                onClick={() => navigate('/residente-dashboard')}
              />
            </ul>
          </section>
        </TabsContent>
      </Tabs>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Editar datos de contacto"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? 'Guardando cambios…' : 'Guardar cambios'}
            </Button>
          </>
        }
      >
        <div className="space-y-4 py-2">
          <p className="text-xs text-text-muted">
            Tus datos de contacto son confidenciales y se usan solo para notificaciones oficiales y citofonía.
          </p>

          <div>
            <Input
              id="editTelefono"
              label="Teléfono celular"
              placeholder="Ej. 3001234567"
              inputMode="numeric"
              value={edit.telefono}
              onChange={(e) => setEdit((s) => ({ ...s, telefono: e.target.value }))}
              onBlur={() => touch('telefono')}
              error={fieldError('telefono', valTelefono(edit.telefono, { required: false })) || errors.telefono}
            />
            <p className="mt-1 text-xs text-text-muted">10 dígitos, número móvil colombiano.</p>
          </div>

          <div>
            <Input
              id="editEmail"
              label="Correo electrónico"
              type="email"
              placeholder="Ej. residente@ejemplo.com"
              value={edit.email}
              onChange={(e) => setEdit((s) => ({ ...s, email: e.target.value }))}
              onBlur={() => touch('email')}
              error={fieldError('email', valEmail(edit.email, { required: false })) || errors.email}
            />
            <p className="mt-1 text-xs text-text-muted">Aquí recibirás comprobantes de pago y avisos.</p>
          </div>
        </div>
      </Modal>

      <ChangePasswordModal open={changePasswordOpen} onOpenChange={setChangePasswordOpen} />
    </PageContainer>
  );
}

/** Section card with a title row; the body owns its loading, error and empty states. */
function Panel({ icon: Icon, title, description, action, className, children }) {
  return (
    <section className={cn('rounded-lg border border-border bg-surface shadow-sm', className)}>
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-2 px-4 pt-4 sm:px-5 sm:pt-5">
        <div className="min-w-0">
          <h3 className="flex items-center gap-2 text-[15px] font-bold text-on-surface">
            {Icon && <Icon className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />}
            {title}
          </h3>
          {description && <p className="mt-0.5 text-xs text-text-muted">{description}</p>}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      <div className="p-4 sm:p-5">{children}</div>
    </section>
  );
}

function ShortcutRow({ icon: Icon, title, description, meta, onClick }) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className="group flex w-full min-w-0 items-center gap-3 px-4 py-3.5 text-left transition-colors duration-150 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:gap-4 sm:px-5"
      >
        <Icon className="h-5 w-5 shrink-0 text-text-secondary" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-on-surface">{title}</span>
          <span className="mt-0.5 block text-xs text-text-muted">{description}</span>
        </span>
        {meta && <span className="hidden shrink-0 sm:block">{meta}</span>}
        <ChevronRight
          className="h-4 w-4 shrink-0 text-text-muted transition-transform duration-150 ease-out group-hover:translate-x-0.5"
          aria-hidden="true"
        />
      </button>
    </li>
  );
}
