import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  Building2,
  Mail,
  Phone,
  MapPin,
  Globe,
  Calendar,
  Edit3,
  AlertCircle,
  Copy,
  Check,
  ShieldCheck,
  Layers,
  Users,
  ExternalLink,
  Hash,
  Sparkles,
  ArrowRight,
  Info,
} from 'lucide-react';
import { Link } from 'react-router-dom';

import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import LocationSelector from '../components/ui/LocationSelector.jsx';
import { findDepartamentoByCiudad } from '../lib/colombiaData.js';

export default function OrgOrganizacionPage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [copiedField, setCopiedField] = useState(null);

  // Operational metrics
  const [propertiesCount, setPropertiesCount] = useState(0);
  const [adminsCount, setAdminsCount] = useState(0);

  const [formData, setFormData] = useState({
    emailContacto: '',
    telefonoContacto: '',
    direccion: '',
    departamento: 'Bogotá D.C.',
    ciudad: 'Bogotá',
    pais: 'Colombia',
  });

  async function loadData() {
    try {
      setLoading(true);
      setError(null);

      const [profileRes, propsRes, adminsRes] = await Promise.allSettled([
        api.get('/org/profile'),
        api.get('/properties'),
        api.get('/org/admins'),
      ]);

      if (profileRes.status === 'fulfilled') {
        const data = profileRes.value?.data || profileRes.value || {};
        setProfile(data);
        setFormData({
          emailContacto: data.emailContacto || '',
          telefonoContacto: data.telefonoContacto || '',
          direccion: data.direccion || '',
          departamento: findDepartamentoByCiudad(data.ciudad) || 'Bogotá D.C.',
          ciudad: data.ciudad || 'Bogotá',
          pais: data.pais || 'Colombia',
        });
      } else {
        throw new Error('No se pudo cargar el perfil de la organización');
      }

      if (propsRes.status === 'fulfilled') {
        const pList = Array.isArray(propsRes.value?.data)
          ? propsRes.value.data
          : Array.isArray(propsRes.value)
          ? propsRes.value
          : [];
        setPropertiesCount(pList.length);
      }

      if (adminsRes.status === 'fulfilled') {
        const aList = Array.isArray(adminsRes.value?.data)
          ? adminsRes.value.data
          : Array.isArray(adminsRes.value)
          ? adminsRes.value
          : [];
        setAdminsCount(aList.length);
      }
    } catch (err) {
      setError(err.message || 'No se pudieron cargar los datos de la organización.');
      toast.error('Error al cargar la información institucional');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  function handleCopy(text, fieldName, label) {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    toast.success(`${label} copiado al portapapeles`);
    setTimeout(() => {
      setCopiedField(null);
    }, 2000);
  }

  async function handleSave(e) {
    e.preventDefault();
    if (!formData.emailContacto?.trim()) {
      toast.error('El correo electrónico de contacto es obligatorio');
      return;
    }

    if (formData.telefonoContacto && formData.telefonoContacto.replace(/[^0-9]/g, '').length < 7) {
      toast.error('El teléfono debe contener al menos 7 dígitos');
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await api.put('/org/profile', formData);
      toast.success('Información de contacto actualizada exitosamente');
      setIsEditing(false);
      await loadData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al actualizar los datos de la organización.';
      setError(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  }

  // Derived display helpers
  const orgName = profile?.nombre ? profile.nombre.trim() : '';
  const orgWords = orgName ? orgName.split(/\s+/) : [];
  const initials = orgWords.length >= 2
    ? (orgWords[0][0] + orgWords[1][0]).toUpperCase()
    : orgName
    ? orgName.slice(0, 2).toUpperCase()
    : 'SA';

  const formattedDate = (() => {
    if (!profile?.fechaCreacion) return 'Fecha no disponible';
    try {
      return new Date(profile.fechaCreacion).toLocaleDateString('es-CO', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    } catch {
      return String(profile.fechaCreacion);
    }
  })();

  if (loading) {
    return (
      <div className="p-4 sm:p-6 md:p-8 space-y-6 max-w-7xl mx-auto animate-pulse">
        <div className="flex items-center gap-4">
          <Skeleton className="h-16 w-16 rounded-2xl" />
          <div className="space-y-2">
            <Skeleton className="h-7 w-64" />
            <Skeleton className="h-4 w-48" />
          </div>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-24 rounded-xl" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Skeleton className="h-96 rounded-xl" />
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 md:p-8 space-y-6 max-w-7xl mx-auto animate-fadeIn">
      {/* 1. Header Hero Card with Branding */}
      <Card className="border-border/60 bg-gradient-to-br from-card via-card to-muted/20 shadow-sm overflow-hidden">
        <CardContent className="p-6">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
            {/* Identity & Monogram */}
            <div className="flex items-start sm:items-center gap-4">
              <div className="h-16 w-16 sm:h-20 sm:w-20 rounded-2xl bg-primary/10 border border-primary/20 text-primary flex items-center justify-center font-bold text-2xl sm:text-3xl shadow-inner flex-shrink-0 tracking-tight">
                {initials}
              </div>
              <div className="space-y-1.5 min-w-0">
                <div className="flex flex-wrap items-center gap-2.5">
                  <h1 className="text-xl sm:text-2xl md:text-3xl font-bold tracking-tight text-foreground truncate">
                    {profile?.nombre || 'Organización'}
                  </h1>
                  <Badge
                    variant="outline"
                    className="gap-1.5 px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20"
                  >
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                    {profile?.estado || 'ACTIVA'}
                  </Badge>
                </div>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1 font-mono font-medium text-foreground/80">
                    <Hash className="w-3.5 h-3.5 text-muted-foreground" />
                    NIT: {profile?.identificacionFiscal || 'No registrado'}
                  </span>
                  <span className="text-border">·</span>
                  <span className="flex items-center gap-1">
                    <Globe className="w-3.5 h-3.5 text-muted-foreground" />
                    {profile?.ciudad || 'Bogotá'}, {profile?.pais || 'Colombia'}
                  </span>
                  <span className="text-border">·</span>
                  <span className="flex items-center gap-1">
                    <ShieldCheck className="w-3.5 h-3.5 text-primary" />
                    ID #{profile?.idOrganizacion}
                  </span>
                </div>
              </div>
            </div>

            {/* Quick Action Button */}
            <div className="flex items-center gap-2 self-start lg:self-center">
              <Button
                variant={isEditing ? 'outline' : 'default'}
                onClick={() => {
                  setIsEditing((prev) => !prev);
                  setError(null);
                }}
                className="gap-2 font-medium shadow-sm transition-all"
              >
                <Edit3 className="w-4 h-4" />
                <span>{isEditing ? 'Cancelar Edición' : 'Editar Contacto'}</span>
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 2. Operational KPIs Ribbon */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-4">
        {/* KPI 1: Propiedades */}
        <Card className="border-border/60 hover:border-primary/30 transition-colors shadow-sm">
          <CardContent className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Propiedades
              </p>
              <h3 className="text-xl sm:text-2xl font-bold text-foreground mt-1 tracking-tight">
                {propertiesCount}
              </h3>
              <Link
                to="/org/propiedades"
                className="text-[11px] font-medium text-primary hover:underline inline-flex items-center gap-1 mt-1"
              >
                Ver edificios <ArrowRight className="w-3 h-3" />
              </Link>
            </div>
            <div className="h-10 w-10 sm:h-11 sm:w-11 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 flex items-center justify-center flex-shrink-0">
              <Layers className="w-5 h-5" />
            </div>
          </CardContent>
        </Card>

        {/* KPI 2: Administradores */}
        <Card className="border-border/60 hover:border-primary/30 transition-colors shadow-sm">
          <CardContent className="p-4 sm:p-5 flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Administradores
              </p>
              <h3 className="text-xl sm:text-2xl font-bold text-foreground mt-1 tracking-tight">
                {adminsCount}
              </h3>
              <Link
                to="/org/admins"
                className="text-[11px] font-medium text-primary hover:underline inline-flex items-center gap-1 mt-1"
              >
                Gestionar equipo <ArrowRight className="w-3 h-3" />
              </Link>
            </div>
            <div className="h-10 w-10 sm:h-11 sm:w-11 rounded-xl bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 flex items-center justify-center flex-shrink-0">
              <Users className="w-5 h-5" />
            </div>
          </CardContent>
        </Card>

        {/* KPI 3: Sede Principal */}
        <Card className="border-border/60 shadow-sm">
          <CardContent className="p-4 sm:p-5 flex items-center justify-between">
            <div className="min-w-0 pr-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider truncate">
                Sede Principal
              </p>
              <h3 className="text-base sm:text-lg font-bold text-foreground mt-1 tracking-tight truncate">
                {profile?.ciudad || 'Bogotá'}
              </h3>
              <p className="text-[11px] text-muted-foreground truncate">
                {formData.departamento || profile?.pais || 'Colombia'}
              </p>
            </div>
            <div className="h-10 w-10 sm:h-11 sm:w-11 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 flex items-center justify-center flex-shrink-0">
              <MapPin className="w-5 h-5" />
            </div>
          </CardContent>
        </Card>

        {/* KPI 4: Antigüedad en SAED */}
        <Card className="border-border/60 shadow-sm">
          <CardContent className="p-4 sm:p-5 flex items-center justify-between">
            <div className="min-w-0 pr-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider truncate">
                Registro SAED
              </p>
              <h3 className="text-sm sm:text-base font-bold text-foreground mt-1 tracking-tight truncate">
                {formattedDate.split(' de ')[2] ? `Año ${formattedDate.split(' de ')[2]}` : 'Activo'}
              </h3>
              <p className="text-[11px] text-muted-foreground truncate" title={formattedDate}>
                {formattedDate}
              </p>
            </div>
            <div className="h-10 w-10 sm:h-11 sm:w-11 rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400 flex items-center justify-center flex-shrink-0">
              <Calendar className="w-5 h-5" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. Error Alert if any */}
      {error && (
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-xl flex items-center gap-3 animate-fadeIn text-sm">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="font-medium">{error}</span>
        </div>
      )}

      {/* 4. Edit Form Card (Animated when toggled) */}
      {isEditing && (
        <Card className="border-primary/40 shadow-lg animate-fadeIn">
          <CardHeader className="border-b border-border/40 pb-4">
            <div className="flex items-center gap-2 text-primary">
              <Sparkles className="w-5 h-5" />
              <CardTitle className="text-lg font-bold text-foreground">
                Actualizar Canales de Contacto
              </CardTitle>
            </div>
            <CardDescription className="text-xs text-muted-foreground">
              Modifique los canales oficiales de contacto y la dirección corporativa de la organización.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-6">
            <form onSubmit={handleSave} className="space-y-5">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                {/* Correo field */}
                <div className="space-y-1.5">
                  <Label htmlFor="org-email" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Correo *
                  </Label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <Input
                      id="org-email"
                      type="email"
                      required
                      placeholder="contacto@organizacion.com"
                      value={formData.emailContacto}
                      onChange={(e) => setFormData({ ...formData, emailContacto: e.target.value })}
                      className="pl-9 text-sm"
                    />
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    Canal principal para notificaciones del sistema y circulares corporativas.
                  </p>
                </div>

                {/* Teléfono field */}
                <div className="space-y-1.5">
                  <Label htmlFor="org-phone" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Teléfono de Contacto
                  </Label>
                  <div className="relative">
                    <Phone className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <Input
                      id="org-phone"
                      type="tel"
                      placeholder="+57 300 123 4567"
                      value={formData.telefonoContacto}
                      onChange={(e) =>
                        setFormData({
                          ...formData,
                          telefonoContacto: e.target.value.replace(/[^0-9+\s()-]/g, '').slice(0, 20),
                        })
                      }
                      className="pl-9 text-sm"
                    />
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    Línea telefónica o PBX de atención administrativa.
                  </p>
                </div>

                {/* Dirección field */}
                <div className="space-y-1.5 md:col-span-2">
                  <Label htmlFor="org-address" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    Dirección Principal
                  </Label>
                  <div className="relative">
                    <MapPin className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <Input
                      id="org-address"
                      type="text"
                      placeholder="Ej. Calle 100 # 15-20, Oficina 402"
                      value={formData.direccion}
                      onChange={(e) => setFormData({ ...formData, direccion: e.target.value })}
                      className="pl-9 text-sm"
                    />
                  </div>
                </div>

                {/* Ubicación Geográfica: Selector Cascada Colombia */}
                <div className="md:col-span-2 pt-1">
                  <LocationSelector
                    idPrefix="org-profile"
                    pais={formData.pais || 'Colombia'}
                    departamento={formData.departamento}
                    ciudad={formData.ciudad || 'Bogotá'}
                    onChange={({ pais, departamento, ciudad }) =>
                      setFormData((prev) => ({ ...prev, pais, departamento, ciudad }))
                    }
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsEditing(false)}
                  disabled={saving}
                >
                  Cancelar
                </Button>
                <Button type="submit" disabled={saving} className="min-w-[140px]">
                  {saving ? 'Guardando...' : 'Guardar Cambios'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* 5. Main Information Grid (Two Enterprise Cards) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Card 1: Identidad Legal e Institucional */}
        <Card className="border-border/60 shadow-sm flex flex-col justify-between">
          <div>
            <CardHeader className="border-b border-border/40 pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
                  <Building2 className="w-5 h-5 text-primary" />
                  <span>Identidad Institucional & Fiscal</span>
                </CardTitle>
                <Badge variant="outline" className="text-[11px] font-mono font-medium">
                  ID #{profile?.idOrganizacion}
                </Badge>
              </div>
              <CardDescription className="text-xs text-muted-foreground">
                Datos corporativos certificados y registrados en el ecosistema SAED.
              </CardDescription>
            </CardHeader>

            <CardContent className="pt-6 space-y-4 text-xs sm:text-sm">
              {/* Razón Social */}
              <div className="p-3 rounded-lg bg-muted/30 border border-border/40">
                <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                  Razón Social / Nombre Oficial
                </span>
                <p className="text-base font-bold text-foreground mt-0.5">
                  {profile?.nombre || 'No registrado'}
                </p>
              </div>

              {/* NIT / Identificación Fiscal */}
              <div className="p-3 rounded-lg bg-muted/30 border border-border/40 flex items-center justify-between">
                <div>
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    Identificación Tributaria (NIT / RUT)
                  </span>
                  <p className="text-sm font-mono font-semibold text-foreground mt-0.5">
                    {profile?.identificacionFiscal || 'No registrada'}
                  </p>
                </div>
                {profile?.identificacionFiscal && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => handleCopy(profile.identificacionFiscal, 'nit', 'NIT')}
                    className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                    title="Copiar NIT al portapapeles"
                  >
                    {copiedField === 'nit' ? (
                      <Check className="w-4 h-4 text-emerald-500" />
                    ) : (
                      <Copy className="w-4 h-4" />
                    )}
                  </Button>
                )}
              </div>

              {/* Tipo de Operación y Régimen */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="p-3 rounded-lg bg-muted/30 border border-border/40">
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    Alcance del Modelo
                  </span>
                  <p className="text-xs sm:text-sm font-medium text-foreground mt-0.5 flex items-center gap-1.5">
                    <ShieldCheck className="w-3.5 h-3.5 text-primary" />
                    Organización Multi-Tenant
                  </p>
                </div>
                <div className="p-3 rounded-lg bg-muted/30 border border-border/40">
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    País de Operación
                  </span>
                  <p className="text-xs sm:text-sm font-medium text-foreground mt-0.5 flex items-center gap-1.5">
                    <Globe className="w-3.5 h-3.5 text-muted-foreground" />
                    {profile?.pais || 'Colombia'}
                  </p>
                </div>
              </div>

              {/* Fecha de Registro */}
              <div className="p-3 rounded-lg bg-muted/30 border border-border/40">
                <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                  Fecha de Alta en la Plataforma
                </span>
                <p className="text-xs sm:text-sm font-medium text-foreground mt-0.5 flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                  {formattedDate}
                </p>
              </div>
            </CardContent>
          </div>

          <CardFooter className="pt-2 pb-4 px-6 border-t border-border/30 bg-muted/10 text-[11px] text-muted-foreground flex items-center gap-2">
            <Info className="w-4 h-4 flex-shrink-0 text-muted-foreground/70" />
            <span>
              Para modificar la Razón Social o el NIT corporativo, radique un requerimiento con copia de su RUT actualizado.
            </span>
          </CardFooter>
        </Card>

        {/* Card 2: Canales de Contacto */}
        <Card className="border-border/60 shadow-sm flex flex-col justify-between">
          <div>
            <CardHeader className="border-b border-border/40 pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
                  <Mail className="w-5 h-5 text-primary" />
                  <span>Canales de Contacto</span>
                </CardTitle>
                <Badge variant="outline" className="text-[11px] font-medium bg-primary/5 text-primary border-primary/20">
                  Oficial
                </Badge>
              </div>
              <CardDescription className="text-xs text-muted-foreground">
                Vías de atención corporativa y radicación administrativa para residentes y copropiedades.
              </CardDescription>
            </CardHeader>

            <CardContent className="pt-6 space-y-4 text-xs sm:text-sm">
              {/* Correo (labeled strictly 'Correo' as requested) */}
              <div className="p-3.5 rounded-lg bg-muted/30 border border-border/40 flex items-center justify-between hover:bg-muted/50 transition-colors">
                <div className="min-w-0 pr-2">
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    Correo
                  </span>
                  <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2 truncate">
                    <Mail className="w-4 h-4 text-primary flex-shrink-0" />
                    <span className="truncate">{profile?.emailContacto || 'No registrado'}</span>
                  </p>
                </div>
                {profile?.emailContacto && (
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => handleCopy(profile.emailContacto, 'email', 'Correo')}
                      className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                      title="Copiar correo"
                    >
                      {copiedField === 'email' ? (
                        <Check className="w-4 h-4 text-emerald-500" />
                      ) : (
                        <Copy className="w-4 h-4" />
                      )}
                    </Button>
                    <a
                      href={`mailto:${profile.emailContacto}`}
                      className="inline-flex items-center justify-center h-8 w-8 rounded-md hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                      title="Escribir correo"
                    >
                      <ExternalLink className="w-4 h-4" />
                    </a>
                  </div>
                )}
              </div>

              {/* Teléfono */}
              <div className="p-3.5 rounded-lg bg-muted/30 border border-border/40 flex items-center justify-between hover:bg-muted/50 transition-colors">
                <div className="min-w-0 pr-2">
                  <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                    Teléfono de Contacto
                  </span>
                  <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2 truncate">
                    <Phone className="w-4 h-4 text-emerald-600 dark:text-emerald-400 flex-shrink-0" />
                    <span>{profile?.telefonoContacto || 'No registrado'}</span>
                  </p>
                </div>
                {profile?.telefonoContacto && (
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => handleCopy(profile.telefonoContacto, 'tel', 'Teléfono')}
                      className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                      title="Copiar teléfono"
                    >
                      {copiedField === 'tel' ? (
                        <Check className="w-4 h-4 text-emerald-500" />
                      ) : (
                        <Copy className="w-4 h-4" />
                      )}
                    </Button>
                    <a
                      href={`tel:${profile.telefonoContacto.replace(/\s+/g, '')}`}
                      className="inline-flex items-center justify-center h-8 w-8 rounded-md hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                      title="Llamar"
                    >
                      <ExternalLink className="w-4 h-4" />
                    </a>
                  </div>
                )}
              </div>

              {/* Dirección Principal */}
              <div className="p-3.5 rounded-lg bg-muted/30 border border-border/40 hover:bg-muted/50 transition-colors">
                <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                  Dirección Principal
                </span>
                <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-indigo-500 flex-shrink-0" />
                  <span>{profile?.direccion || 'No registrada'}</span>
                </p>
              </div>

              {/* Ciudad / Departamento / País */}
              <div className="p-3.5 rounded-lg bg-muted/30 border border-border/40 hover:bg-muted/50 transition-colors">
                <span className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider block">
                  Ubicación Geográfica
                </span>
                <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2">
                  <Globe className="w-4 h-4 text-amber-500 flex-shrink-0" />
                  <span>
                    {profile?.ciudad || 'Bogotá'}, {formData.departamento ? `${formData.departamento}, ` : ''}{profile?.pais || 'Colombia'}
                  </span>
                </p>
              </div>
            </CardContent>
          </div>

          <CardFooter className="pt-2 pb-4 px-6 border-t border-border/30 bg-muted/10 flex items-center justify-between">
            <span className="text-xs text-muted-foreground">
              ¿Desea actualizar estos canales?
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsEditing(true)}
              className="text-xs gap-1.5 h-8"
            >
              <Edit3 className="w-3.5 h-3.5" />
              Editar Canales
            </Button>
          </CardFooter>
        </Card>
      </div>
    </div>
  );
}
