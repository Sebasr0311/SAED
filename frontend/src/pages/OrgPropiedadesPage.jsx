import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Home, Plus, Search, MapPin, Building, AlertCircle, CheckCircle2, Power, Eye } from 'lucide-react';

export default function OrgPropiedadesPage() {
  const [properties, setProperties] = useState([]);
  const [subscription, setSubscription] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Modal create state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [newProp, setNewProp] = useState({
    nombre: '',
    idTipoPropiedad: 1,
    direccion: '',
    ciudad: 'Bogotá',
    pais: 'Colombia',
    tipoOcupacionPredominante: 'RESIDENCIAL',
  });

  async function loadData() {
    try {
      setLoading(true);
      setError(null);
      const [propsRes, subRes] = await Promise.all([
        api.get('/properties'),
        api.get('/org/subscription').catch(() => null),
      ]);
      setProperties(Array.isArray(propsRes?.data) ? propsRes.data : Array.isArray(propsRes) ? propsRes : []);
      if (subRes) {
        setSubscription(subRes?.data || subRes || null);
      }
    } catch (err) {
      console.error('Error loading properties:', err);
      setError('No se pudieron cargar las propiedades de la organización.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    try {
      setCreating(true);
      setCreateError(null);
      await api.post('/properties', newProp);
      setSuccessMsg('Propiedad registrada exitosamente.');
      setIsModalOpen(false);
      setNewProp({
        nombre: '',
        idTipoPropiedad: 1,
        direccion: '',
        ciudad: 'Bogotá',
        pais: 'Colombia',
        tipoOcupacionPredominante: 'RESIDENCIAL',
      });
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error creating property:', err);
      if (err?.response?.status === 409 || err?.response?.data?.code === 'PLAN_LIMIT_EXCEEDED') {
        setCreateError(
          'Límite de propiedades alcanzado. Su plan actual no permite agregar más copropiedades. Actualice su suscripción para continuar.'
        );
      } else {
        setCreateError(err?.response?.data?.message || 'Error al registrar la propiedad.');
      }
    } finally {
      setCreating(false);
    }
  }

  async function toggleStatus(prop) {
    const nextStatus = prop.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.patch(`/properties/${prop.id}/status`, { estado: nextStatus });
      setSuccessMsg(`Propiedad ${prop.nombre} ahora se encuentra ${nextStatus}.`);
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error updating property status:', err);
      setError('No se pudo cambiar el estado de la propiedad.');
    }
  }

  const filtered = properties.filter((p) => {
    const matchesSearch =
      (p.nombre || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.ciudad || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.direccion || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || p.estado === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const used = subscription?.propiedadesUsadas ?? properties.filter((p) => p.estado === 'ACTIVA').length;
  const limit = subscription?.limitePropiedades ?? 0;
  const isAtLimit = limit > 0 && used >= limit;

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-44 rounded-xl" />
          ))}
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
              Propiedades de la Organización
            </h1>
            <Badge variant="outline" className="text-xs px-2.5 py-0.5 font-medium">
              {used} / {limit > 0 ? limit : '∞'} utilizadas
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Gestione y supervise todas las copropiedades, conjuntos y edificios bajo su administración.
          </p>
        </div>
        <div>
          <Button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center gap-2"
          >
            <Plus className="w-4 h-4" />
            <span>Nueva Propiedad</span>
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

      {/* Plan limit warning banner */}
      {isAtLimit && (
        <div className="bg-amber-500/15 border border-amber-500/30 text-amber-700 dark:text-amber-400 px-4 py-3 rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <div className="text-sm">
            <span className="font-semibold">Ha alcanzado el límite máximo de propiedades ({limit}).</span> Para registrar nuevas copropiedades, solicite una ampliación de su plan SaaS.
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-4 justify-between items-center">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por nombre o ciudad..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="ALL">Todos los Estados</option>
            <option value="ACTIVA">Solo Activas</option>
            <option value="INACTIVA">Solo Inactivas</option>
          </select>
        </div>
      </div>

      {/* Grid of properties */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filtered.length === 0 ? (
          <div className="col-span-full py-12 text-center text-muted-foreground">
            <Home className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p className="font-medium text-base">No se encontraron propiedades</p>
            <p className="text-xs mt-1">Cree una nueva propiedad o ajuste los filtros de búsqueda.</p>
          </div>
        ) : (
          filtered.map((prop) => (
            <Card key={prop.id} className="border border-border/80 shadow-sm hover:shadow-md transition-all flex flex-col justify-between">
              <CardHeader className="border-b border-border/40 pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-base font-bold text-foreground">
                      {prop.nombre}
                    </CardTitle>
                    <Badge variant="outline" className="text-[11px] mt-1">
                      {prop.tipoPropiedadNombre || 'Edificio Residencial'}
                    </Badge>
                  </div>
                  <Badge
                    variant={prop.estado === 'ACTIVA' ? 'default' : 'secondary'}
                    className={
                      prop.estado === 'ACTIVA'
                        ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs'
                        : 'text-xs'
                    }
                  >
                    {prop.estado || 'ACTIVA'}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="pt-4 space-y-3 flex-grow">
                <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>{prop.direccion || 'Sin dirección'}, {prop.ciudad || 'Colombia'}</span>
                </div>
                <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                  <Building className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>Ocupación: {prop.tipoOcupacionPredominante || 'RESIDENCIAL'}</span>
                </div>
              </CardContent>
              <div className="p-4 pt-0 border-t border-border/40 flex items-center justify-between mt-auto">
                <span className="text-[11px] font-mono text-muted-foreground">
                  ID: #{prop.id}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => toggleStatus(prop)}
                  className="text-xs gap-1"
                >
                  <Power className="w-3.5 h-3.5" />
                  <span>{prop.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'}</span>
                </Button>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* Create Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-lg bg-background border-border shadow-xl">
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="text-lg font-bold text-foreground">
                Registrar Nueva Propiedad
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-6">
              {createError && (
                <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2 rounded-lg text-xs flex items-center gap-2 mb-4">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{createError}</span>
                </div>
              )}
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Nombre de la Copropiedad / Edificio *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ej. Torres del Parque"
                    value={newProp.nombre}
                    onChange={(e) => setNewProp({ ...newProp, nombre: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Tipo de Propiedad
                    </label>
                    <select
                      value={newProp.idTipoPropiedad}
                      onChange={(e) => setNewProp({ ...newProp, idTipoPropiedad: Number(e.target.value) })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value={1}>Edificio Residencial</option>
                      <option value={2}>Conjunto Cerrado</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Ocupación
                    </label>
                    <select
                      value={newProp.tipoOcupacionPredominante}
                      onChange={(e) => setNewProp({ ...newProp, tipoOcupacionPredominante: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value="RESIDENCIAL">Residencial</option>
                      <option value="COMERCIAL">Comercial</option>
                      <option value="MIXTA">Mixta</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Dirección
                  </label>
                  <input
                    type="text"
                    placeholder="Ej. Calle 123 # 45-67"
                    value={newProp.direccion}
                    onChange={(e) => setNewProp({ ...newProp, direccion: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Ciudad
                    </label>
                    <input
                      type="text"
                      value={newProp.ciudad}
                      onChange={(e) => setNewProp({ ...newProp, ciudad: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      País
                    </label>
                    <input
                      type="text"
                      value={newProp.pais}
                      onChange={(e) => setNewProp({ ...newProp, pais: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-border">
                  <Button type="button" variant="outline" onClick={() => setIsModalOpen(false)}>
                    Cancelar
                  </Button>
                  <Button type="submit" disabled={creating}>
                    {creating ? 'Guardando...' : 'Crear Propiedad'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
