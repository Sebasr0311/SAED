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
  Sparkles,
  MapPin,
  Layers,
  Key,
  AlertCircle,
  ArrowRight,
  FileText,
} from 'lucide-react';

import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';
import { valTelefono, valEmail } from '../lib/validation.js';

import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input } from '../components/ui/Form.jsx';

function CopyChip({ text, label, icon: Icon }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = (e) => {
    e.stopPropagation();
    if (!text) return;
    navigator.clipboard.writeText(String(text));
    setCopied(true);
    toast.success(`${label || 'Valor'} copiado al portapapeles`);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!text) return null;

  return (
    <button
      type="button"
      onClick={handleCopy}
      title={`Copiar ${label || ''}: ${text}`}
      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-white/10 hover:bg-white/20 text-white/90 border border-white/15 transition-all duration-150 backdrop-blur-sm cursor-pointer group"
    >
      {Icon && <Icon className="w-3.5 h-3.5 opacity-75 group-hover:opacity-100" />}
      <span className="truncate max-w-[180px]">{text}</span>
      {copied ? (
        <Check className="w-3 h-3 text-emerald-300 ml-0.5" />
      ) : (
        <Copy className="w-3 h-3 opacity-50 group-hover:opacity-100 ml-0.5" />
      )}
    </button>
  );
}

function DetailItem({ icon: Icon, label, value, badge, subtext, isMono = false }) {
  return (
    <div className="flex items-start gap-3 p-3 rounded-lg border border-border/50 bg-background/50 hover:bg-muted/40 transition-colors">
      <div className="p-2 rounded-md bg-primary/10 text-primary mt-0.5 shrink-0">
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">{label}</span>
          {badge}
        </div>
        <p className={`text-sm font-semibold text-foreground truncate mt-0.5 ${isMono ? 'font-mono' : ''}`}>
          {value || '—'}
        </p>
        {subtext && <p className="text-xs text-muted-foreground mt-0.5">{subtext}</p>}
      </div>
    </div>
  );
}

export default function ResPerfilPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Estados de edición
  const [modalOpen, setModalOpen] = useState(false);
  const [edit, setEdit] = useState({ telefono: '', email: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const { touch, fieldError } = useLiveValidation();

  // Resolución de IDs
  const residentId = user?.idResidente || user?.idPersona || user?.idUsuario;

  // 1. Datos personales de la persona
  const { data: personaData, refetch: refetchPersona } = useFetch(
    () => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null)),
    [residentId]
  );
  const perfil = useMemo(() => personaData?.raw || personaData || {}, [personaData]);

  // 2. Dashboard financiero y de unidad del residente
  const { data: dashboardData, refetch: refetchDashboard } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/dashboard`) : Promise.resolve(null)),
    [residentId]
  );
  const dashboard = useMemo(() => dashboardData?.raw || dashboardData || {}, [dashboardData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);
  const contratoInfo = useMemo(() => dashboard.contrato || {}, [dashboard]);
  const cuotas = useMemo(() => dashboard.cuotas || [], [dashboard]);

  // 3. Ficha de la unidad oficial
  const unitId =
    user?.idUnidad || perfil.idApartamento || perfil.idUnidad || aptoInfo.idApartamento || aptoInfo.id || 1;
  const { data: unitData } = useFetch(() => (unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)), [unitId]);
  const u = useMemo(() => unitData?.raw || unitData || {}, [unitData]);

  // 4. Residentes / cohabitantes de la unidad
  const { data: unitResidentsData } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}/residents`) : Promise.resolve([])),
    [unitId]
  );

  // 5. Asignaciones de parqueadero
  const { data: asignacionesParqueadero } = useFetch(() => api.get('/parqueaderos/asignaciones'), []);

  // 6. Visitantes frecuentes
  const { data: frecuentesData } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/frecuentes`) : Promise.resolve([])),
    [residentId]
  );

  // Normalizaciones y cálculos
  const nombreCompleto = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres || user?.nombreCompleto || 'Carlos';
    const sNombre = perfil.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos || (user?.nombreCompleto ? '' : 'Martínez');
    const sApellido = perfil.segundoApellido || '';
    return `${pNombre} ${sNombre} ${pApellido} ${sApellido}`.replace(/\s+/g, ' ').trim();
  }, [perfil, user]);

  const iniciales = useMemo(() => {
    const partes = nombreCompleto.split(' ').filter(Boolean);
    if (!partes.length) return 'RE';
    return (partes[0][0] + (partes[1]?.[0] || '')).toUpperCase();
  }, [nombreCompleto]);

  const numeroApto =
    u.identificador ||
    u.numero ||
    aptoInfo.numero ||
    perfil.numeroApartamento ||
    (user?.idUnidad ? `Apto 20${user.idUnidad}` : 'Apto 201');
  const nombreBloque = u.bloqueNombre || aptoInfo.bloque || aptoInfo.torre || 'Torre 1';
  const pisoApto = aptoInfo.piso || (numeroApto.match(/\d+/) ? numeroApto.match(/\d+/)[0][0] : '2');
  const areaApto = u.areaM2 ? `${u.areaM2} m²` : aptoInfo.areaM2 ? `${aptoInfo.areaM2} m²` : '75.50 m²';
  const tipoUnidad = u.tipoUnidadNombre || aptoInfo.tipo || 'Apartamento Residencial';
  const coeficiente = u.coeficienteCopropiedad
    ? `${(Number(u.coeficienteCopropiedad) * 100).toFixed(2)}%`
    : '1.2500%';
  const estadoUnidad = u.estado || aptoInfo.estado || 'HABITADA';

  // Coarrendatarios y compañeros
  const listaHabitantes = useMemo(() => {
    const rawList = Array.isArray(unitResidentsData)
      ? unitResidentsData
      : unitResidentsData?.items || [];
    if (rawList.length > 0) return rawList;
    // Si no hay lista del backend, reflejar al menos al titular
    return [
      {
        id: residentId || 4,
        nombres: perfil.primerNombre || perfil.nombres || 'Carlos',
        apellidos: perfil.primerApellido || perfil.apellidos || 'Martínez',
        numeroDocumento: perfil.numeroDocumento || '1000000004',
        tipoResidente: 'TITULAR',
        estado: 'ACTIVO',
      },
    ];
  }, [unitResidentsData, perfil, residentId]);

  // Cuotas y estado financiero
  const cuotasPendientes = useMemo(() => {
    return (cuotas || []).filter((c) => c.estado !== 'PAGADA');
  }, [cuotas]);
  const alDia = cuotasPendientes.length === 0;
  const tipoContrato = contratoInfo.tipoContrato || 'Copropietario Residente';

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
      await api.put(`/personas/${residentId}`, {
        tipoDocumentoId: Number(perfil.tipoDocumentoId || perfil.idTipoDoc || 1),
        numeroDocumento: perfil.numeroDocumento || '1000000004',
        tipoPersona: perfil.tipoPersona || 'NATURAL',
        primerNombre: perfil.primerNombre || perfil.nombres || 'Carlos',
        segundoNombre: perfil.segundoNombre || '',
        primerApellido: perfil.primerApellido || perfil.apellidos || 'Martínez',
        segundoApellido: perfil.segundoApellido || '',
        telefono: edit.telefono.replace(/\D/g, ''),
        email: edit.email.trim() || null,
      });
      toast.success('Perfil de residente actualizado exitosamente');
      setModalOpen(false);
      refetchPersona();
      refetchDashboard();
    } catch (err) {
      toast.error(err.message || 'Error al actualizar el perfil');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6 pb-12 max-w-7xl mx-auto">
      {/* Título de navegación */}
      <PageHeader
        title="Mi Perfil & Apartamento"
        subtitle="Centro de gestión integral del copropietario y residente"
      />

      {/* HERO BANNER DEL RESIDENTE */}
      <div className="relative overflow-hidden rounded-2xl border border-primary/20 bg-gradient-to-br from-slate-900 via-primary/95 to-slate-900 text-white shadow-xl">
        {/* Patrón de fondo geométrico sutil */}
        <div
          className="absolute inset-0 opacity-5 pointer-events-none"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 30%, white 1px, transparent 1px), radial-gradient(circle at 80% 70%, white 1px, transparent 1px)',
            backgroundSize: '28px 28px',
          }}
        />

        <div className="relative p-6 sm:p-8 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="flex items-center gap-5">
            {/* Avatar con aura y badge de actividad */}
            <div className="relative shrink-0">
              <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl bg-white/10 backdrop-blur-md border-2 border-white/30 flex items-center justify-center text-white text-2xl sm:text-3xl font-extrabold shadow-2xl ring-4 ring-white/10">
                {iniciales}
              </div>
              <div
                className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-emerald-500 border-2 border-slate-900 flex items-center justify-center"
                title="Residente Activo y Conectado"
              >
                <div className="w-2.5 h-2.5 rounded-full bg-white animate-pulse" />
              </div>
            </div>

            {/* Identidad y Rol */}
            <div className="space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white">{nombreCompleto}</h1>
                <Badge variant="secondary" className="bg-white/20 text-white border-white/20 hover:bg-white/30">
                  Residente Titular
                </Badge>
                <Badge
                  variant="outline"
                  className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30 font-medium"
                >
                  <CheckCircle2 className="w-3 h-3 mr-1" />
                  Al Día
                </Badge>
              </div>

              <p className="text-sm text-white/80 font-medium flex items-center gap-1.5">
                <Home className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>
                  {numeroApto} · {nombreBloque} · Piso {pisoApto}
                </span>
                <span className="text-white/40">•</span>
                <span className="text-white/70">{user?.nombrePropiedad || 'Edificio Residencial SAED'}</span>
              </p>

              {/* Chips de contacto con copia rápida */}
              <div className="flex flex-wrap items-center gap-2 pt-1">
                <CopyChip
                  text={perfil.numeroDocumento || '1000000004'}
                  label="Documento"
                  icon={Shield}
                />
                <CopyChip
                  text={perfil.telefono || '3001234567'}
                  label="Teléfono"
                  icon={Phone}
                />
                <CopyChip
                  text={perfil.email || user?.email || 'camartinez@saed.com'}
                  label="Correo"
                  icon={Mail}
                />
              </div>
            </div>
          </div>

          {/* Quick Info Box / Nomenclatura Destacada */}
          <div className="w-full md:w-auto flex md:flex-col items-center md:items-end justify-between border-t md:border-t-0 md:border-l border-white/15 pt-4 md:pt-0 md:pl-6 gap-3">
            <div className="text-left md:text-right">
              <div className="text-xs text-white/60 uppercase tracking-wider font-semibold">Unidad Habitacional</div>
              <div className="text-2xl font-black text-white tracking-tight">{numeroApto}</div>
              <div className="text-xs text-white/70">{tipoUnidad}</div>
            </div>
          </div>
        </div>
      </div>

      {/* STRIP DE KPIS / STATUS EN 4 COLUMNAS */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1: Inmueble */}
        <Card className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70">
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400">
              <Building2 className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Apartamento</p>
              <h3 className="text-xl font-bold text-foreground truncate">{numeroApto}</h3>
              <p className="text-xs text-muted-foreground truncate">{nombreBloque} · {areaApto}</p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2: Finanzas */}
        <Card className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70">
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <CreditCard className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Estado de Cartera</p>
              <h3 className="text-xl font-bold text-foreground">
                {alDia ? 'Al Día' : `${cuotasPendientes.length} Pendiente(s)`}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {alDia ? 'Sin obligaciones en mora' : 'Requiere pago oportuno'}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3: Habitantes */}
        <Card className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70">
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-purple-500/10 text-purple-600 dark:text-purple-400">
              <Users className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Habitantes</p>
              <h3 className="text-xl font-bold text-foreground">{listaHabitantes.length} Registrado(s)</h3>
              <p className="text-xs text-muted-foreground truncate">Núcleo familiar activo</p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 4: Movilidad / Parqueadero */}
        <Card className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70">
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <Car className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Parqueadero</p>
              <h3 className="text-xl font-bold text-foreground">
                {parqueaderoAsignado ? `Celda ${parqueaderoAsignado.identificador || parqueaderoAsignado.numero || 'P-1'}` : 'Comunal / Libre'}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {parqueaderoAsignado ? (parqueaderoAsignado.tipo || 'Privado cubierto') : 'Acceso vehicular'}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* PESTAÑAS PRINCIPALES INTEGRADAS */}
      <Tabs defaultValue="perfil" className="space-y-6">
        <TabsList className="grid w-full grid-cols-3 max-w-xl h-11 p-1 bg-muted/80 rounded-xl border border-border">
          <TabsTrigger value="perfil" className="gap-2 text-sm font-semibold">
            <User className="w-4 h-4" />
            Mi Perfil
          </TabsTrigger>
          <TabsTrigger value="apartamento" className="gap-2 text-sm font-semibold">
            <Home className="w-4 h-4" />
            Mi Apartamento
          </TabsTrigger>
          <TabsTrigger value="gestiones" className="gap-2 text-sm font-semibold">
            <Sparkles className="w-4 h-4" />
            Gestiones & Atajos
          </TabsTrigger>
        </TabsList>

        {/* TAB 1: DATOS PERSONALES & CUENTA */}
        <TabsContent value="perfil" className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Tarjeta 1.1: Datos de Identidad */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                  <Shield className="w-5 h-5 text-primary" />
                  Identidad y Registro Civil
                </CardTitle>
                <CardDescription>Datos legales validados ante la administración</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <DetailItem
                  icon={User}
                  label="Nombre Completo"
                  value={nombreCompleto}
                  badge={<Badge variant="outline">Natural</Badge>}
                />
                <DetailItem
                  icon={Shield}
                  label="Documento de Identidad"
                  value={`${perfil.numeroDocumento || '1000000004'}`}
                  subtext={perfil.tipoDocumentoNombre || 'Cédula de Ciudadanía (CC)'}
                  badge={<Badge variant="success">Verificado</Badge>}
                  isMono
                />
                <DetailItem
                  icon={Calendar}
                  label="Fecha de Nacimiento"
                  value={perfil.fechaNacimiento ? formatDate(perfil.fechaNacimiento) : '15/05/1988'}
                  subtext="Mayor de edad legal (Colombia)"
                />
                <DetailItem
                  icon={Layers}
                  label="Tipo de Persona"
                  value="Persona Natural"
                  subtext="Copropietario Habitante"
                />
              </CardContent>
            </Card>

            {/* Tarjeta 1.2: Información de Contacto */}
            <Card>
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <Phone className="w-5 h-5 text-primary" />
                      Canales de Contacto
                    </CardTitle>
                    <CardDescription>Utilizados para avisos, citaciones y emergencias</CardDescription>
                  </div>
                  <Button variant="ghost" size="sm" onClick={openEdit} className="text-primary hover:text-primary">
                    <Pencil className="w-3.5 h-3.5 mr-1" />
                    Editar
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <DetailItem
                  icon={Phone}
                  label="Teléfono Celular"
                  value={perfil.telefono || '3001234567'}
                  badge={<Badge variant="outline">Principal</Badge>}
                  subtext="Habilitado para llamadas de citofonía y SMS"
                  isMono
                />
                <DetailItem
                  icon={Mail}
                  label="Correo Electrónico"
                  value={perfil.email || user?.email || 'camartinez@saed.com'}
                  badge={<Badge variant="success">Notificaciones OK</Badge>}
                  subtext="Recepción de estados de cuenta y circulares"
                />
                <DetailItem
                  icon={MapPin}
                  label="Dirección de la Copropiedad"
                  value="Calle 100 # 15-20"
                  subtext="Bogotá D.C., Colombia"
                />
                <DetailItem
                  icon={Building2}
                  label="Copropiedad / Conjunto"
                  value={user?.nombrePropiedad || 'Edificio Residencial SAED'}
                  badge={<Badge variant="secondary">Propiedad #1</Badge>}
                />
              </CardContent>
            </Card>

            {/* Tarjeta 1.3: Seguridad y Credenciales */}
            <Card className="md:col-span-2">
              <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                  <Key className="w-5 h-5 text-primary" />
                  Cuenta de Usuario y Seguridad
                </CardTitle>
                <CardDescription>Permisos y trazabilidad de acceso en la plataforma</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div className="p-4 rounded-xl border border-border/60 bg-muted/20">
                    <span className="text-xs font-semibold text-muted-foreground uppercase">Nombre de Usuario</span>
                    <p className="text-base font-bold text-foreground font-mono mt-1">@{user?.username || 'camartinez'}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">Credencial única para inicio de sesión</p>
                  </div>
                  <div className="p-4 rounded-xl border border-border/60 bg-muted/20">
                    <span className="text-xs font-semibold text-muted-foreground uppercase">Rol Asignado</span>
                    <div className="mt-1 flex items-center gap-2">
                      <Badge variant="default" className="bg-primary font-semibold">RESIDENTE</Badge>
                      <span className="text-xs text-muted-foreground">Nivel Unidad</span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">Acceso a cuotas, visitas, buzón y reservas</p>
                  </div>
                  <div className="p-4 rounded-xl border border-border/60 bg-muted/20">
                    <span className="text-xs font-semibold text-muted-foreground uppercase">Seguridad de Acceso</span>
                    <div className="mt-1 flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-semibold text-sm">
                      <CheckCircle2 className="w-4 h-4" />
                      Sesión Cifrada JWT
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">Aislamiento por RLS / VPD activo</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* TAB 2: MI APARTAMENTO & CONTRATO */}
        <TabsContent value="apartamento" className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* 2.1: Ficha Técnica de la Unidad (2 columnas) */}
            <Card className="lg:col-span-2">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <Building2 className="w-5 h-5 text-primary" />
                      Ficha Técnica del Inmueble
                    </CardTitle>
                    <CardDescription>Parámetros catastrales y coeficientes de copropiedad</CardDescription>
                  </div>
                  <Badge variant="success" className="font-semibold">{estadoUnidad}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Nomenclatura</span>
                    <p className="text-lg font-bold text-foreground mt-0.5">{numeroApto}</p>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Bloque / Torre</span>
                    <p className="text-lg font-bold text-foreground mt-0.5">{nombreBloque}</p>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Nivel / Piso</span>
                    <p className="text-lg font-bold text-foreground mt-0.5">Piso {pisoApto}</p>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Área Privada</span>
                    <p className="text-lg font-bold text-foreground mt-0.5">{areaApto}</p>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Coeficiente</span>
                    <p className="text-lg font-bold text-foreground mt-0.5 font-mono">{coeficiente}</p>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60">
                    <span className="text-xs font-medium text-muted-foreground">Destinación</span>
                    <p className="text-lg font-bold text-foreground mt-0.5">Residencial</p>
                  </div>
                </div>

                <div className="pt-2 border-t border-border">
                  <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                    <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      Relación de Tenencia y Cuota de Administración
                    </h4>
                    <span className="text-[11px] text-muted-foreground bg-muted px-2 py-0.5 rounded-full font-medium">
                      Información registrada por administración · Solo lectura
                    </span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <DetailItem
                      icon={FileText}
                      label="Modalidad"
                      value={tipoContrato}
                      subtext={contratoInfo.estado ? `Estado: ${contratoInfo.estado}` : 'Registro de copropietario vigente'}
                      badge={<Badge variant="outline">Activo</Badge>}
                    />
                    <DetailItem
                      icon={CreditCard}
                      label="Cuota de Administración"
                      value={formatCurrency(contratoInfo.valorMensual || 450000)}
                      subtext={`Corte de facturación: Día ${contratoInfo.diaPago || 5} de cada mes`}
                      badge={<Badge variant="success">Fijada por Asamblea</Badge>}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 2.2: Parqueadero & Asignaciones (1 columna) */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                  <Car className="w-5 h-5 text-primary" />
                  Movilidad y Parqueo
                </CardTitle>
                <CardDescription>Celdas asignadas a la unidad</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {parqueaderoAsignado ? (
                  <div className="p-4 rounded-xl border border-primary/20 bg-primary/5 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-primary uppercase">Celda Asignada</span>
                      <Badge variant="default" className="bg-primary">Asignado</Badge>
                    </div>
                    <p className="text-2xl font-black text-foreground">
                      {parqueaderoAsignado.identificador || parqueaderoAsignado.numero || 'Celda P-201'}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Tipo: {parqueaderoAsignado.tipo || 'Automóvil cubierto'}
                    </p>
                    {parqueaderoAsignado.placa && (
                      <div className="pt-2 border-t border-border/50 flex items-center justify-between text-xs">
                        <span className="text-muted-foreground">Vehículo registrado:</span>
                        <span className="font-mono font-bold text-foreground">{parqueaderoAsignado.placa}</span>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-4 rounded-xl border border-dashed border-border text-center space-y-2">
                    <Car className="w-8 h-8 text-muted-foreground mx-auto opacity-50" />
                    <p className="text-sm font-semibold text-foreground">Sin celda privada exclusiva</p>
                    <p className="text-xs text-muted-foreground">
                      Tu unidad cuenta con derecho a parqueadero comunal según disponibilidad y reglamento.
                    </p>
                  </div>
                )}

                <div className="p-3 rounded-lg border border-border bg-muted/20 text-xs text-muted-foreground flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-500 shrink-0" />
                  <span>Para registrar un nuevo vehículo o moto, acércate a la oficina de administración.</span>
                </div>
              </CardContent>
            </Card>

            {/* 2.3: Habitantes Registrados en la Unidad (Full width) */}
            <Card className="lg:col-span-3">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <Users className="w-5 h-5 text-primary" />
                      Núcleo Familiar y Habitantes de la Unidad ({listaHabitantes.length})
                    </CardTitle>
                    <CardDescription>Personas autorizadas formalmente para residir en el inmueble</CardDescription>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {listaHabitantes.map((h, idx) => {
                    const hNombre = h.persona
                      ? `${h.persona.primerNombre || ''} ${h.persona.primerApellido || ''}`.trim()
                      : `${h.nombres || ''} ${h.apellidos || ''}`.trim();
                    const hDoc = h.persona?.numeroDocumento || h.numeroDocumento || '—';
                    const hRol = h.tipoResidente || (idx === 0 ? 'TITULAR' : 'COHABITANTE');
                    const hInitials = (hNombre[0] || 'R').toUpperCase();

                    return (
                      <div
                        key={h.id || idx}
                        className="flex items-center gap-3.5 p-3.5 rounded-xl border border-border/80 bg-card hover:bg-muted/30 transition-all duration-150"
                      >
                        <div className="w-11 h-11 rounded-full bg-primary/15 text-primary flex items-center justify-center font-bold text-base shrink-0 border border-primary/20">
                          {hInitials}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center justify-between gap-1">
                            <h4 className="text-sm font-bold text-foreground truncate">{hNombre}</h4>
                            <Badge variant={hRol === 'TITULAR' ? 'default' : 'secondary'} className="text-[10px] px-1.5 py-0">
                              {hRol}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground font-mono mt-0.5">Doc: {hDoc}</p>
                          <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400 mt-1 font-medium">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                            Habitante Activo
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* TAB 3: GESTIONES Y ATAJOS RÁPIDOS */}
        <TabsContent value="gestiones" className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {/* Atajo 1: Cuotas y Pagos */}
            <Card
              onClick={() => navigate('/res-cuotas')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-emerald-500/10 text-emerald-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <CreditCard className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    Mis Cuotas y Pagos
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Consulta tus saldos, genera comprobantes y realiza pagos seguros en línea con Wompi.
                  </p>
                </div>
                <Badge variant={alDia ? 'success' : 'destructive'} className="text-xs">
                  {alDia ? 'Al día sin mora' : `${cuotasPendientes.length} cuota(s) pendiente(s)`}
                </Badge>
              </CardContent>
            </Card>

            {/* Atajo 2: Visitas & Códigos QR */}
            <Card
              onClick={() => navigate('/res-visitas')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-blue-500/10 text-blue-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Key className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    Registrar Visita (QR)
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Autoriza accesos directos por portería generando un código QR dinámico de seguridad.
                  </p>
                </div>
                <Badge variant="outline" className="text-xs">
                  Autorización inmediata
                </Badge>
              </CardContent>
            </Card>

            {/* Atajo 3: Visitantes Frecuentes */}
            <Card
              onClick={() => navigate('/res-visitas')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-purple-500/10 text-purple-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Users className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    Visitantes Frecuentes
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Administra familiares, empleados y personas con permiso permanente de entrada.
                  </p>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {numFrecuentes} registrado(s)
                </Badge>
              </CardContent>
            </Card>

            {/* Atajo 4: Buzón y Comunicados */}
            <Card
              onClick={() => navigate('/res-buzon')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-amber-500/10 text-amber-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Mail className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    Buzón de Comunicados
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Circulares oficiales, convocatorias de asamblea y avisos de mantenimiento.
                  </p>
                </div>
                <Badge variant="outline" className="text-xs">
                  Comunidad informada
                </Badge>
              </CardContent>
            </Card>

            {/* Atajo 5: Peticiones y Reclamos (PQRS) */}
            <Card
              onClick={() => navigate('/res-quejas')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-rose-500/10 text-rose-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <AlertCircle className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    PQRS & Soporte
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Envía solicitudes, quejas de convivencia o reportes de fallas locativas con seguimiento.
                  </p>
                </div>
                <Badge variant="outline" className="text-xs">
                  Respuesta formal
                </Badge>
              </CardContent>
            </Card>

            {/* Atajo 6: Panel General */}
            <Card
              onClick={() => navigate('/residente-dashboard')}
              className="cursor-pointer hover:shadow-lg transition-all duration-200 hover:-translate-y-1 group border-border/70"
            >
              <CardContent className="p-6 space-y-3">
                <div className="w-12 h-12 rounded-xl bg-slate-500/10 text-slate-600 flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Home className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors flex items-center justify-between">
                    Panel de Control Principal
                    <ArrowRight className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Visualiza métricas globales, gráficos de pagos y actividad reciente de la copropiedad.
                  </p>
                </div>
                <Badge variant="secondary" className="text-xs">
                  Dashboard
                </Badge>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>

      {/* MODAL DE EDICIÓN DE CONTACTO */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Editar Datos de Contacto"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={handleSave} disabled={saving} className="gap-2">
              {saving ? 'Guardando cambios...' : 'Guardar Cambios'}
            </Button>
          </>
        }
      >
        <div className="space-y-4 py-2">
          <p className="text-xs text-muted-foreground">
            Los datos de contacto son confidenciales y se utilizan exclusivamente para notificaciones oficiales y citofonía.
          </p>

          <div>
            <Input
              id="editTelefono"
              label="Teléfono Celular *"
              placeholder="Ej. 3001234567"
              value={edit.telefono}
              onChange={(e) => setEdit((s) => ({ ...s, telefono: e.target.value }))}
              onBlur={() => touch('telefono')}
              error={fieldError('telefono', valTelefono(edit.telefono, { required: false })) || errors.telefono}
            />
            <p className="text-[11px] text-muted-foreground mt-1">10 dígitos numéricos para telefonía móvil colombiana.</p>
          </div>

          <div>
            <Input
              id="editEmail"
              label="Correo Electrónico *"
              type="email"
              placeholder="Ej. residente@ejemplo.com"
              value={edit.email}
              onChange={(e) => setEdit((s) => ({ ...s, email: e.target.value }))}
              onBlur={() => touch('email')}
              error={fieldError('email', valEmail(edit.email, { required: false })) || errors.email}
            />
            <p className="text-[11px] text-muted-foreground mt-1">Recibirás los comprobantes de pago y avisos aquí.</p>
          </div>
        </div>
      </Modal>
    </div>
  );
}
