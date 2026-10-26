import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  Building2,
  Mail,
  Phone,
  MapPin,
  Calendar,
  Edit3,
  Copy,
  Check,
  Layers,
  Users,
  ExternalLink,
  ArrowRight,
  Info,
} from 'lucide-react';
import { Link } from 'react-router-dom';

import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
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

  const headerBase = {
    title: 'Perfil de la organización',
    subtitle: 'Identidad, datos fiscales y canales de contacto de la organización.',
  };

  if (loading) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <div role="status" aria-busy="true" aria-label="Cargando organización" className="space-y-6">
          <Skeleton className="h-24 rounded-lg" />
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-24 rounded-lg" />
            ))}
          </div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Skeleton className="h-96 rounded-lg" />
            <Skeleton className="h-96 rounded-lg" />
          </div>
        </div>
      </PageContainer>
    );
  }

  if (!profile) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <ErrorState
          title="No se pudo cargar la organización"
          message={error || 'No se pudieron cargar los datos de la organización.'}
          onRetry={loadData}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title={profile?.nombre || 'Organización'}
        subtitle={`NIT ${profile?.identificacionFiscal || 'no registrado'} · ${profile?.ciudad || 'Bogotá'}, ${profile?.pais || 'Colombia'} · ID #${profile?.idOrganizacion ?? '—'}`}
        badge={
          <Badge variant={(profile?.estado || 'ACTIVA') === 'ACTIVA' ? 'success' : 'secondary'}>
            {profile?.estado || 'ACTIVA'}
          </Badge>
        }
        action={
          <Button
            variant={isEditing ? 'outline' : 'default'}
            onClick={() => {
              setIsEditing((prev) => !prev);
              setError(null);
            }}
            className="min-h-10"
            aria-expanded={isEditing}
            aria-controls="org-contact-form"
          >
            <Edit3 aria-hidden="true" />
            {isEditing ? 'Cancelar edición' : 'Editar contacto'}
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Propiedades"
          value={propertiesCount}
          context={
            <Link to="/org/propiedades" className="inline-flex items-center gap-1 font-semibold text-primary-text hover:underline">
              Ver propiedades <ArrowRight className="h-3 w-3" aria-hidden="true" />
            </Link>
          }
          icon={Layers}
        />
        <MetricCard
          label="Administradores"
          value={adminsCount}
          context={
            <Link to="/org/admins" className="inline-flex items-center gap-1 font-semibold text-primary-text hover:underline">
              Gestionar equipo <ArrowRight className="h-3 w-3" aria-hidden="true" />
            </Link>
          }
          icon={Users}
          variant="info"
        />
        <MetricCard
          label="Sede principal"
          value={<span className="block truncate text-lg">{profile?.ciudad || 'Bogotá'}</span>}
          context={formData.departamento || profile?.pais || 'Colombia'}
          icon={MapPin}
          variant="secondary"
        />
        <MetricCard
          label="Alta en SAED"
          value={<span className="block truncate text-lg">{formattedDate.split(' de ')[2] || '—'}</span>}
          context={formattedDate}
          icon={Calendar}
          variant="secondary"
        />
      </div>

      {error && <ErrorState compact title="No se pudo completar la operación" message={error} />}

      {isEditing && (
        <Card id="org-contact-form" className="border-primary/40">
          <CardHeader className="border-b border-border pb-4">
            <CardTitle className="text-[15px]">Actualizar canales de contacto</CardTitle>
            <CardDescription className="text-xs text-text-muted">
              Modifique los canales oficiales de contacto y la dirección de la organización.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-6">
            <form onSubmit={handleSave} className="space-y-5">
              <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
                <div className="space-y-1">
                  <Input
                    id="org-email"
                    label="Correo"
                    type="email"
                    required
                    placeholder="contacto@organizacion.com"
                    value={formData.emailContacto}
                    onChange={(e) => setFormData({ ...formData, emailContacto: e.target.value })}
                  />
                  <p className="text-xs text-text-muted">Canal principal para notificaciones del sistema.</p>
                </div>

                <div className="space-y-1">
                  <Input
                    id="org-phone"
                    label="Teléfono de contacto"
                    type="tel"
                    placeholder="+57 300 123 4567"
                    value={formData.telefonoContacto}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        telefonoContacto: e.target.value.replace(/[^0-9+\s()-]/g, '').slice(0, 20),
                      })
                    }
                  />
                  <p className="text-xs text-text-muted">Línea de atención administrativa.</p>
                </div>

                <div className="md:col-span-2">
                  <Input
                    id="org-address"
                    label="Dirección principal"
                    type="text"
                    placeholder="Ej. Calle 100 # 15-20, Oficina 402"
                    value={formData.direccion}
                    onChange={(e) => setFormData({ ...formData, direccion: e.target.value })}
                  />
                </div>

                <div className="pt-1 md:col-span-2">
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

              <div className="flex flex-wrap items-center justify-end gap-3 border-t border-border pt-4">
                <Button type="button" variant="outline" onClick={() => setIsEditing(false)} disabled={saving}>
                  Cancelar
                </Button>
                <Button type="submit" disabled={saving} className="min-w-[140px]">
                  {saving ? 'Guardando...' : 'Guardar cambios'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card className="flex flex-col justify-between">
          <div>
            <CardHeader className="border-b border-border pb-4">
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="flex items-center gap-2 text-[15px]">
                  <Building2 className="h-4 w-4 text-text-secondary" aria-hidden="true" />
                  Identidad institucional y fiscal
                </CardTitle>
                <Badge variant="outline" className="font-mono">
                  ID #{profile?.idOrganizacion}
                </Badge>
              </div>
              <CardDescription className="text-xs text-text-muted">
                Datos corporativos registrados en SAED.
              </CardDescription>
            </CardHeader>

            <CardContent className="pt-4">
              <dl className="divide-y divide-border rounded-lg border border-border">
                <InfoRow label="Razón social">
                  <span className="text-[15px] font-bold">{profile?.nombre || 'No registrado'}</span>
                </InfoRow>
                <InfoRow
                  label="NIT"
                  actions={
                    profile?.identificacionFiscal && (
                      <CopyButton
                        copied={copiedField === 'nit'}
                        label="Copiar NIT"
                        onClick={() => handleCopy(profile.identificacionFiscal, 'nit', 'NIT')}
                      />
                    )
                  }
                >
                  <span className="font-mono font-semibold">{profile?.identificacionFiscal || 'No registrada'}</span>
                </InfoRow>
                <InfoRow label="Alcance">Organización con varias copropiedades</InfoRow>
                <InfoRow label="País de operación">{profile?.pais || 'Colombia'}</InfoRow>
                <InfoRow label="Fecha de alta en la plataforma">{formattedDate}</InfoRow>
              </dl>
            </CardContent>
          </div>

          <CardFooter className="flex items-start gap-2 border-t border-border bg-surface-container px-6 pb-4 pt-3 text-xs text-text-secondary">
            <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Para modificar la razón social o el NIT, radique un requerimiento con copia del RUT actualizado.</span>
          </CardFooter>
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="flex items-center gap-2 text-[15px]">
                <Mail className="h-4 w-4 text-text-secondary" aria-hidden="true" />
                Canales de contacto
              </CardTitle>
              <CardDescription className="text-xs text-text-muted">
                Vías de atención corporativa para residentes y copropiedades.
              </CardDescription>
            </CardHeader>

            <CardContent className="pt-4">
              <dl className="divide-y divide-border rounded-lg border border-border">
                <InfoRow
                  label="Correo"
                  actions={
                    profile?.emailContacto && (
                      <>
                        <CopyButton
                          copied={copiedField === 'email'}
                          label="Copiar correo"
                          onClick={() => handleCopy(profile.emailContacto, 'email', 'Correo')}
                        />
                        <a
                          href={`mailto:${profile.emailContacto}`}
                          className={ICON_LINK}
                          title="Escribir correo"
                          aria-label="Escribir correo"
                        >
                          <ExternalLink className="h-4 w-4" aria-hidden="true" />
                        </a>
                      </>
                    )
                  }
                >
                  <span className="block truncate">{profile?.emailContacto || 'No registrado'}</span>
                </InfoRow>
                <InfoRow
                  label="Teléfono de contacto"
                  actions={
                    profile?.telefonoContacto && (
                      <>
                        <CopyButton
                          copied={copiedField === 'tel'}
                          label="Copiar teléfono"
                          onClick={() => handleCopy(profile.telefonoContacto, 'tel', 'Teléfono')}
                        />
                        <a
                          href={`tel:${profile.telefonoContacto.replace(/\s+/g, '')}`}
                          className={ICON_LINK}
                          title="Llamar"
                          aria-label="Llamar"
                        >
                          <Phone className="h-4 w-4" aria-hidden="true" />
                        </a>
                      </>
                    )
                  }
                >
                  <span className="tabular-nums">{profile?.telefonoContacto || 'No registrado'}</span>
                </InfoRow>
                <InfoRow label="Dirección principal">{profile?.direccion || 'No registrada'}</InfoRow>
                <InfoRow label="Ubicación">
                  {profile?.ciudad || 'Bogotá'}, {formData.departamento ? `${formData.departamento}, ` : ''}
                  {profile?.pais || 'Colombia'}
                </InfoRow>
              </dl>
            </CardContent>
          </div>

          <CardFooter className="flex flex-wrap items-center justify-between gap-2 border-t border-border bg-surface-container px-6 pb-4 pt-3">
            <span className="text-xs text-text-secondary">¿Desea actualizar estos canales?</span>
            <Button variant="outline" size="sm" onClick={() => setIsEditing(true)} className="h-10 text-xs sm:h-8">
              <Edit3 aria-hidden="true" />
              Editar canales
            </Button>
          </CardFooter>
        </Card>
      </div>
    </PageContainer>
  );
}

const ICON_LINK =
  'inline-flex h-10 w-10 items-center justify-center rounded-md text-text-secondary transition-colors duration-150 hover:bg-surface-dim hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring';

function InfoRow({ label, actions, children }) {
  return (
    <div className="flex items-center justify-between gap-3 px-3.5 py-3">
      <div className="min-w-0">
        <dt className="text-xs font-semibold text-text-muted">{label}</dt>
        <dd className="mt-0.5 text-sm font-medium text-on-surface">{children}</dd>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-1">{actions}</div>}
    </div>
  );
}

function CopyButton({ copied, label, onClick }) {
  return (
    <button type="button" onClick={onClick} className={ICON_LINK} title={label} aria-label={label}>
      {copied ? (
        <Check className="h-4 w-4 text-tint-green-fg" aria-hidden="true" />
      ) : (
        <Copy className="h-4 w-4" aria-hidden="true" />
      )}
    </button>
  );
}
