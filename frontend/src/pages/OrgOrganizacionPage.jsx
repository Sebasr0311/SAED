import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Building2, Mail, Phone, MapPin, Globe, Calendar, Edit3, CheckCircle2, AlertCircle } from 'lucide-react';

export default function OrgOrganizacionPage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [successMsg, setSuccessMsg] = useState(null);

  const [formData, setFormData] = useState({
    emailContacto: '',
    telefonoContacto: '',
    direccion: '',
    ciudad: '',
    pais: '',
  });

  async function loadProfile() {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/org/profile');
      const data = res?.data || res || {};
      setProfile(data);
      setFormData({
        emailContacto: data.emailContacto || '',
        telefonoContacto: data.telefonoContacto || '',
        direccion: data.direccion || '',
        ciudad: data.ciudad || '',
        pais: data.pais || '',
      });
    } catch (err) {
      console.error('Error loading org profile:', err);
      setError('No se pudo cargar el perfil de la organización.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadProfile();
  }, []);

  async function handleSave(e) {
    e.preventDefault();
    try {
      setSaving(true);
      setError(null);
      await api.put('/org/profile', formData);
      setSuccessMsg('Información de contacto actualizada exitosamente.');
      setIsEditing(false);
      await loadProfile();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error updating org profile:', err);
      setError('Error al actualizar los datos de la organización.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Skeleton className="h-64 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Perfil de la Organización
            </h1>
            <Badge
              variant={profile?.estado === 'ACTIVA' ? 'default' : 'secondary'}
              className={
                profile?.estado === 'ACTIVA'
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs px-2.5 py-0.5'
                  : 'text-xs px-2.5 py-0.5'
              }
            >
              {profile?.estado || 'ACTIVA'}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Datos institucionales, identificación tributaria y canales de contacto corporativos.
          </p>
        </div>
        <div>
          <Button
            onClick={() => setIsEditing(!isEditing)}
            variant={isEditing ? 'outline' : 'default'}
            className="flex items-center gap-2"
          >
            <Edit3 className="w-4 h-4" />
            <span>{isEditing ? 'Cancelar Edición' : 'Editar Contacto'}</span>
          </Button>
        </div>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="bg-emerald-500/15 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* Edit Form */}
      {isEditing && (
        <Card className="border-primary/40 shadow-md animate-fadeIn">
          <CardHeader className="border-b border-border/40 pb-4">
            <CardTitle className="text-base font-semibold text-foreground">
              Actualizar Canales de Contacto Corporativo
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-6">
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1.5">
                    Correo Electrónico de Contacto
                  </label>
                  <input
                    type="email"
                    required
                    value={formData.emailContacto}
                    onChange={(e) => setFormData({ ...formData, emailContacto: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1.5">
                    Teléfono Corporativo
                  </label>
                  <input
                    type="text"
                    value={formData.telefonoContacto}
                    onChange={(e) => setFormData({ ...formData, telefonoContacto: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1.5">
                    Dirección Principal
                  </label>
                  <input
                    type="text"
                    value={formData.direccion}
                    onChange={(e) => setFormData({ ...formData, direccion: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1.5">
                    Ciudad
                  </label>
                  <input
                    type="text"
                    value={formData.ciudad}
                    onChange={(e) => setFormData({ ...formData, ciudad: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1.5">
                    País
                  </label>
                  <input
                    type="text"
                    value={formData.pais}
                    onChange={(e) => setFormData({ ...formData, pais: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-4 border-t border-border">
                <Button type="button" variant="outline" onClick={() => setIsEditing(false)}>
                  Cancelar
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving ? 'Guardando...' : 'Guardar Cambios'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Main Info Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Identidad Legal */}
        <Card className="border border-border/80 shadow-sm">
          <CardHeader className="flex flex-row items-center justify-between border-b border-border/40 pb-4">
            <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
              <Building2 className="w-5 h-5 text-primary" />
              <span>Identidad Institucional</span>
            </CardTitle>
            <Badge variant="outline" className="text-xs">ID #{profile?.idOrganizacion}</Badge>
          </CardHeader>
          <CardContent className="pt-6 space-y-4">
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                Razón Social / Nombre Comercial
              </span>
              <p className="text-base font-medium text-foreground mt-0.5">
                {profile?.nombre || 'N/A'}
              </p>
            </div>
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                Identificación Tributaria (NIT / RUT)
              </span>
              <p className="text-base font-mono font-medium text-foreground mt-0.5">
                {profile?.identificacionFiscal || 'N/A'}
              </p>
            </div>
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                País de Operación
              </span>
              <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-1.5">
                <Globe className="w-4 h-4 text-muted-foreground" />
                {profile?.pais || 'Colombia'}
              </p>
            </div>
            {profile?.fechaCreacion && (
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase block">
                  Fecha de Registro en SAED
                </span>
                <p className="text-xs text-muted-foreground mt-0.5 flex items-center gap-1.5">
                  <Calendar className="w-4 h-4" />
                  {new Date(profile.fechaCreacion).toLocaleDateString('es-CO', { year: 'numeric', month: 'long', day: 'numeric' })}
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Contact Info */}
        <Card className="border border-border/80 shadow-sm">
          <CardHeader className="border-b border-border/40 pb-4">
            <CardTitle className="text-base font-semibold text-foreground flex items-center gap-2">
              <Mail className="w-5 h-5 text-primary" />
              <span>Canales de Contacto</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-6 space-y-4">
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                Correo Electrónico Institucional
              </span>
              <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2">
                <Mail className="w-4 h-4 text-muted-foreground" />
                {profile?.emailContacto || 'No registrado'}
              </p>
            </div>
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                Teléfono de Atención
              </span>
              <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2">
                <Phone className="w-4 h-4 text-muted-foreground" />
                {profile?.telefonoContacto || 'No registrado'}
              </p>
            </div>
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase block">
                Sede / Dirección
              </span>
              <p className="text-sm font-medium text-foreground mt-0.5 flex items-center gap-2">
                <MapPin className="w-4 h-4 text-muted-foreground" />
                {profile?.direccion || 'No registrada'}, {profile?.ciudad || ''}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
