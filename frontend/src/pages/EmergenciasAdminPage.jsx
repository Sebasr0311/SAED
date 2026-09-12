import React, { useState, useMemo } from 'react';
import {
  AlertTriangle,
  Flame,
  Siren,
  HeartPulse,
  Zap,
  Droplets,
  Shield,
  Phone,
  Plus,
  Search,
  FileText,
  MapPin,
  Calendar,
  Clock,
  Edit2,
  Trash2,
  ExternalLink,
  CheckCircle2,
  AlertCircle,
  Building2,
  LifeBuoy,
  Wrench,
  Compass,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';

const TIPOS_SERVICIO = [
  { id: 'BOMBEROS', label: 'Bomberos', icon: Flame, color: 'text-red-500 bg-red-500/10 border-red-500/30' },
  { id: 'POLICIA', label: 'Policía Nacional', icon: Siren, color: 'text-blue-500 bg-blue-500/10 border-blue-500/30' },
  { id: 'AMBULANCIA', label: 'Ambulancia / Médica', icon: HeartPulse, color: 'text-rose-500 bg-rose-500/10 border-rose-500/30' },
  { id: 'DEFENSA_CIVIL', label: 'Defensa Civil', icon: LifeBuoy, color: 'text-orange-500 bg-orange-500/10 border-orange-500/30' },
  { id: 'GAS_NATURAL', label: 'Gas Natural', icon: AlertTriangle, color: 'text-amber-500 bg-amber-500/10 border-amber-500/30' },
  { id: 'ACUEDUCTO', label: 'Acueducto y Alcantarillado', icon: Droplets, color: 'text-cyan-500 bg-cyan-500/10 border-cyan-500/30' },
  { id: 'ENERGIA', label: 'Energía Eléctrica', icon: Zap, color: 'text-yellow-500 bg-yellow-500/10 border-yellow-500/30' },
  { id: 'ASCENSORES', label: 'Mantenimiento Ascensores', icon: Wrench, color: 'text-indigo-500 bg-indigo-500/10 border-indigo-500/30' },
  { id: 'SEGURIDAD_PRIVADA', label: 'Seguridad Privada', icon: Shield, color: 'text-emerald-500 bg-emerald-500/10 border-emerald-500/30' },
  { id: 'OTRO', label: 'Otro Servicio', icon: Phone, color: 'text-neutral-500 bg-neutral-500/10 border-neutral-500/30' },
];

const TIPOS_CONTINGENCIA = [
  'INCENDIO',
  'INUNDACION',
  'TERREMOTO',
  'FALLA_ELECTRICA',
  'ACCIDENTE',
  'EVACUACION',
  'SEGURIDAD',
  'OTRO',
];

const ESTADOS_PLAN = {
  ACTIVO: { label: 'Activo / Vigente', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  EN_REVISION: { label: 'En Revisión', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
  OBSOLETO: { label: 'Obsoleto', color: 'bg-neutral/20 text-neutral-content' },
};

export default function EmergenciasAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [tabActiva, setTabActiva] = useState('contactos');
  const [filtroServicio, setFiltroServicio] = useState('TODOS');
  const [busqueda, setBusqueda] = useState('');

  // Modales
  const [modalContactoOpen, setModalContactoOpen] = useState(false);
  const [modalPlanOpen, setModalPlanOpen] = useState(false);
  const [modalDeleteOpen, setModalDeleteOpen] = useState(false);
  const [itemEliminar, setItemEliminar] = useState(null); // { tipo: 'contacto'|'plan', data: obj }
  const [contactoEditar, setContactoEditar] = useState(null);
  const [planEditar, setPlanEditar] = useState(null);
  const [saving, setSaving] = useState(false);

  // Forms
  const initialContactoForm = {
    entidad: '',
    tipoServicio: TIPOS_SERVICIO[0].id,
    telefonoPrincipal: '',
    telefonoAlterno: '',
    direccion: '',
    esPrioritarioMinuta: 'S',
    ordenVisualizacion: 1,
  };
  const [contactoForm, setContactoForm] = useState(initialContactoForm);

  const initialPlanForm = {
    titulo: '',
    tipoContingencia: TIPOS_CONTINGENCIA[0],
    puntosEncuentro: '',
    rutasEvacuacionDesc: '',
    mapaEvacuacionUrl: '',
    documentoPlanUrl: '',
    fechaUltimaRevision: new Date().toISOString().split('T')[0],
    estado: 'ACTIVO',
  };
  const [planForm, setPlanForm] = useState(initialPlanForm);

  // Queries
  const {
    data: contactosRaw,
    loading: loadingContactos,
    refetch: refetchContactos,
  } = useFetch(() => tenantApi.get('/emergencias/contactos'), [tenant.activeAssignmentId]);

  const {
    data: planesRaw,
    loading: loadingPlanes,
    refetch: refetchPlanes,
  } = useFetch(() => tenantApi.get('/emergencias/planes'), [tenant.activeAssignmentId]);

  const {
    data: resumenRaw,
    loading: loadingResumen,
    refetch: refetchResumen,
  } = useFetch(() => tenantApi.get('/emergencias/resumen'), [tenant.activeAssignmentId]);

  const contactos = useMemo(() => {
    if (!contactosRaw) return [];
    if (Array.isArray(contactosRaw)) return contactosRaw;
    if (Array.isArray(contactosRaw.items)) return contactosRaw.items;
    return contactosRaw.data || [];
  }, [contactosRaw]);

  const planes = useMemo(() => {
    if (!planesRaw) return [];
    if (Array.isArray(planesRaw)) return planesRaw;
    if (Array.isArray(planesRaw.items)) return planesRaw.items;
    return planesRaw.data || [];
  }, [planesRaw]);

  const resumen = resumenRaw?.data || resumenRaw || {
    totalPlanes: 0,
    planesActivos: 0,
    totalContactos: 0,
    contactosPrioritarios: 0,
  };

  const contactosFiltrados = useMemo(() => {
    return contactos.filter((c) => {
      const matchServicio = filtroServicio === 'TODOS' || c.tipoServicio === filtroServicio;
      const matchBusqueda =
        !busqueda ||
        c.entidad?.toLowerCase().includes(busqueda.toLowerCase()) ||
        c.telefonoPrincipal?.includes(busqueda) ||
        c.direccion?.toLowerCase().includes(busqueda.toLowerCase());
      return matchServicio && matchBusqueda;
    });
  }, [contactos, filtroServicio, busqueda]);

  const planesFiltrados = useMemo(() => {
    return planes.filter((p) => {
      return (
        !busqueda ||
        p.titulo?.toLowerCase().includes(busqueda.toLowerCase()) ||
        p.tipoContingencia?.toLowerCase().includes(busqueda.toLowerCase()) ||
        p.puntosEncuentro?.toLowerCase().includes(busqueda.toLowerCase())
      );
    });
  }, [planes, busqueda]);

  // Handlers Contacto
  const handleOpenCrearContacto = () => {
    setContactoEditar(null);
    setContactoForm(initialContactoForm);
    setModalContactoOpen(true);
  };

  const handleOpenEditarContacto = (c) => {
    setContactoEditar(c);
    setContactoForm({
      entidad: c.entidad || '',
      tipoServicio: c.tipoServicio || TIPOS_SERVICIO[0].id,
      telefonoPrincipal: c.telefonoPrincipal || '',
      telefonoAlterno: c.telefonoAlterno || '',
      direccion: c.direccion || '',
      esPrioritarioMinuta: c.esPrioritarioMinuta || 'S',
      ordenVisualizacion: c.ordenVisualizacion || 1,
    });
    setModalContactoOpen(true);
  };

  const handleSaveContacto = async (e) => {
    e.preventDefault();
    if (!contactoForm.entidad.trim() || !contactoForm.telefonoPrincipal.trim()) {
      toast.error('Entidad y teléfono principal son obligatorios');
      return;
    }

    try {
      setSaving(true);
      const payload = {
        ...contactoForm,
        ordenVisualizacion: Number(contactoForm.ordenVisualizacion) || 1,
      };

      if (contactoEditar) {
        await tenantApi.put(`/emergencias/contactos/${contactoEditar.idContactoEmergencia}`, payload);
        toast.success('Contacto de emergencia actualizado');
      } else {
        await tenantApi.post('/emergencias/contactos', payload);
        toast.success('Contacto de emergencia registrado');
      }

      setModalContactoOpen(false);
      refetchContactos();
      refetchResumen();
    } catch (err) {
      toast.error(err.message || 'Error al guardar contacto');
    } finally {
      setSaving(false);
    }
  };

  // Handlers Plan
  const handleOpenCrearPlan = () => {
    setPlanEditar(null);
    setPlanForm(initialPlanForm);
    setModalPlanOpen(true);
  };

  const handleOpenEditarPlan = (p) => {
    setPlanEditar(p);
    setPlanForm({
      titulo: p.titulo || '',
      tipoContingencia: p.tipoContingencia || TIPOS_CONTINGENCIA[0],
      puntosEncuentro: p.puntosEncuentro || '',
      rutasEvacuacionDesc: p.rutasEvacuacionDesc || '',
      mapaEvacuacionUrl: p.mapaEvacuacionUrl || '',
      documentoPlanUrl: p.documentoPlanUrl || '',
      fechaUltimaRevision: p.fechaUltimaRevision || new Date().toISOString().split('T')[0],
      estado: p.estado || 'ACTIVO',
    });
    setModalPlanOpen(true);
  };

  const handleSavePlan = async (e) => {
    e.preventDefault();
    if (!planForm.titulo.trim() || !planForm.puntosEncuentro.trim() || !planForm.rutasEvacuacionDesc.trim()) {
      toast.error('Título, puntos de encuentro y rutas de evacuación son obligatorios');
      return;
    }

    try {
      setSaving(true);
      if (planEditar) {
        await tenantApi.put(`/emergencias/planes/${planEditar.idPlanEmergencia}`, planForm);
        toast.success('Plan de emergencia actualizado');
      } else {
        await tenantApi.post('/emergencias/planes', planForm);
        toast.success('Plan de emergencia creado exitosamente');
      }

      setModalPlanOpen(false);
      refetchPlanes();
      refetchResumen();
    } catch (err) {
      toast.error(err.message || 'Error al guardar plan de emergencia');
    } finally {
      setSaving(false);
    }
  };

  // Handlers Delete
  const handleDelete = async () => {
    if (!itemEliminar) return;
    try {
      setSaving(true);
      if (itemEliminar.tipo === 'contacto') {
        await tenantApi.del(`/emergencias/contactos/${itemEliminar.data.idContactoEmergencia}`);
        toast.success('Contacto eliminado con éxito');
        refetchContactos();
      } else {
        await tenantApi.del(`/emergencias/planes/${itemEliminar.data.idPlanEmergencia}`);
        toast.success('Plan de contingencia eliminado');
        refetchPlanes();
      }
      setModalDeleteOpen(false);
      setItemEliminar(null);
      refetchResumen();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar el registro');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Directorio y Planes de Emergencia"
        description="Líneas de emergencia para minuta de portería, planes de contingencia y protocolos de evacuación"
        action={
          <div className="flex items-center gap-2">
            <button
              onClick={handleOpenCrearContacto}
              className="flex items-center gap-2 px-3 py-2 bg-card border border-border text-foreground rounded-lg font-medium shadow-xs hover:bg-muted/70 transition-colors text-xs sm:text-sm"
            >
              <Plus className="w-4 h-4 text-primary" />
              Nuevo Contacto
            </button>
            <button
              onClick={handleOpenCrearPlan}
              className="flex items-center gap-2 px-3 py-2 bg-primary text-primary-content rounded-lg font-medium shadow-sm hover:bg-primary/90 transition-colors text-xs sm:text-sm"
            >
              <Plus className="w-4 h-4" />
              Nuevo Plan
            </button>
          </div>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Directorio Telefónico</p>
              <h3 className="text-2xl font-bold mt-1 text-foreground">{resumen.totalContactos}</h3>
            </div>
            <div className="p-3 bg-blue-500/10 text-blue-600 dark:text-blue-400 rounded-xl">
              <Phone className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            <Building2 className="w-3.5 h-3.5" /> Organismos y soporte técnico
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Prioritarios Minuta</p>
              <h3 className="text-2xl font-bold mt-1 text-red-600 dark:text-red-400">{resumen.contactosPrioritarios}</h3>
            </div>
            <div className="p-3 bg-red-500/10 text-red-600 dark:text-red-400 rounded-xl">
              <Siren className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-red-600 dark:text-red-400 mt-3 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5" /> Visibles en portería y accesos
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Planes de Contingencia</p>
              <h3 className="text-2xl font-bold mt-1 text-foreground">{resumen.totalPlanes}</h3>
            </div>
            <div className="p-3 bg-amber-500/10 text-amber-600 dark:text-amber-400 rounded-xl">
              <Compass className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            <MapPin className="w-3.5 h-3.5" /> Rutas y puntos de encuentro
          </p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Planes Activos</p>
              <h3 className="text-2xl font-bold mt-1 text-emerald-600 dark:text-emerald-400">{resumen.planesActivos}</h3>
            </div>
            <div className="p-3 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 rounded-xl">
              <Shield className="w-6 h-6" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-3 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Protocolos vigentes
          </p>
        </div>
      </div>

      {/* Tabs & Search Bar */}
      <div className="bg-card border border-border rounded-xl p-4 flex flex-col sm:flex-row gap-4 items-center justify-between">
        <div className="flex items-center gap-2 border-b sm:border-b-0 pb-2 sm:pb-0 w-full sm:w-auto">
          <button
            onClick={() => {
              setTabActiva('contactos');
              setBusqueda('');
            }}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors flex items-center gap-2 ${
              tabActiva === 'contactos'
                ? 'bg-primary text-primary-content shadow-xs'
                : 'bg-muted/40 text-muted-foreground hover:bg-muted/70'
            }`}
          >
            <Phone className="w-4 h-4" />
            Directorio Telefónico ({contactos.length})
          </button>
          <button
            onClick={() => {
              setTabActiva('planes');
              setBusqueda('');
            }}
            className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors flex items-center gap-2 ${
              tabActiva === 'planes'
                ? 'bg-primary text-primary-content shadow-xs'
                : 'bg-muted/40 text-muted-foreground hover:bg-muted/70'
            }`}
          >
            <Compass className="w-4 h-4" />
            Planes de Evacuación ({planes.length})
          </button>
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder={tabActiva === 'contactos' ? 'Buscar entidad, teléfono...' : 'Buscar plan, contingencia...'}
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
          />
        </div>
      </div>

      {/* PESTAÑA: DIRECTORIO TELEFÓNICO */}
      {tabActiva === 'contactos' && (
        <div className="space-y-4">
          {/* Subfiltro de servicios */}
          <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
            <button
              onClick={() => setFiltroServicio('TODOS')}
              className={`px-3 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors ${
                filtroServicio === 'TODOS'
                  ? 'bg-foreground text-background font-semibold'
                  : 'bg-muted/50 text-muted-foreground hover:bg-muted'
              }`}
            >
              Todos los Servicios
            </button>
            {TIPOS_SERVICIO.map((ts) => (
              <button
                key={ts.id}
                onClick={() => setFiltroServicio(ts.id)}
                className={`px-3 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors flex items-center gap-1.5 ${
                  filtroServicio === ts.id
                    ? 'bg-primary text-primary-content font-semibold'
                    : 'bg-muted/50 text-muted-foreground hover:bg-muted'
                }`}
              >
                <ts.icon className="w-3.5 h-3.5" />
                {ts.label}
              </button>
            ))}
          </div>

          {loadingContactos ? (
            <div className="p-12 text-center text-muted-foreground bg-card border border-border rounded-xl">
              <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full mx-auto mb-3" />
              <p className="text-sm">Cargando directorio de emergencias...</p>
            </div>
          ) : contactosFiltrados.length === 0 ? (
            <div className="p-12 text-center bg-card border border-border rounded-xl">
              <Phone className="w-12 h-12 text-muted-foreground/40 mx-auto mb-3" />
              <h3 className="text-lg font-semibold text-foreground">No hay contactos registrados</h3>
              <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
                No se encontraron contactos para el filtro seleccionado.
              </p>
              <button
                onClick={handleOpenCrearContacto}
                className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-content rounded-lg text-sm font-medium shadow-sm hover:bg-primary/90 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Agregar Contacto
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {contactosFiltrados.map((c) => {
                const srvCfg = TIPOS_SERVICIO.find((s) => s.id === c.tipoServicio) || TIPOS_SERVICIO[TIPOS_SERVICIO.length - 1];
                const IconComponent = srvCfg.icon;

                return (
                  <div
                    key={c.idContactoEmergencia}
                    className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-3">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${srvCfg.color}`}>
                          <IconComponent className="w-3.5 h-3.5" />
                          {srvCfg.label}
                        </span>
                        {c.esPrioritarioMinuta === 'S' && (
                          <span className="bg-red-500/15 text-red-700 dark:text-red-400 border border-red-500/30 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider flex items-center gap-1">
                            <Siren className="w-3 h-3" /> Minuta
                          </span>
                        )}
                      </div>

                      <h4 className="font-bold text-base text-foreground mb-1">{c.entidad}</h4>

                      <div className="mt-3 space-y-2 text-xs">
                        <div className="flex items-center justify-between p-2 rounded-lg bg-muted/40 border border-border/40">
                          <span className="text-muted-foreground font-medium">Línea Principal:</span>
                          <a
                            href={`tel:${c.telefonoPrincipal}`}
                            className="font-bold text-base text-primary hover:underline flex items-center gap-1"
                          >
                            <Phone className="w-4 h-4" />
                            {c.telefonoPrincipal}
                          </a>
                        </div>

                        {c.telefonoAlterno && (
                          <div className="flex items-center justify-between px-2 text-muted-foreground">
                            <span>Alterno:</span>
                            <a href={`tel:${c.telefonoAlterno}`} className="font-medium hover:underline text-foreground">
                              {c.telefonoAlterno}
                            </a>
                          </div>
                        )}

                        {c.direccion && (
                          <div className="flex items-start gap-1.5 px-2 text-muted-foreground">
                            <MapPin className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                            <span className="text-foreground">{c.direccion}</span>
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center justify-between border-t border-border/60 pt-3 mt-4 text-xs text-muted-foreground">
                      <span>Prioridad visual: #{c.ordenVisualizacion || 1}</span>
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleOpenEditarContacto(c)}
                          className="p-1.5 text-muted-foreground hover:text-primary hover:bg-primary/10 rounded-md transition-colors"
                          title="Editar Contacto"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => {
                            setItemEliminar({ tipo: 'contacto', data: c });
                            setModalDeleteOpen(true);
                          }}
                          className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-md transition-colors"
                          title="Eliminar Contacto"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* PESTAÑA: PLANES DE CONTINGENCIA Y EVACUACIÓN */}
      {tabActiva === 'planes' && (
        <div className="space-y-4">
          {loadingPlanes ? (
            <div className="p-12 text-center text-muted-foreground bg-card border border-border rounded-xl">
              <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full mx-auto mb-3" />
              <p className="text-sm">Cargando planes de evacuación...</p>
            </div>
          ) : planesFiltrados.length === 0 ? (
            <div className="p-12 text-center bg-card border border-border rounded-xl">
              <Compass className="w-12 h-12 text-muted-foreground/40 mx-auto mb-3" />
              <h3 className="text-lg font-semibold text-foreground">No hay planes de emergencia registrados</h3>
              <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
                Registre protocolos de contingencia ante incendios, sismos o evacuaciones.
              </p>
              <button
                onClick={handleOpenCrearPlan}
                className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-content rounded-lg text-sm font-medium shadow-sm hover:bg-primary/90 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Crear Primer Plan
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              {planesFiltrados.map((p) => {
                const estCfg = ESTADOS_PLAN[p.estado] || { label: p.estado, color: 'bg-muted text-muted-foreground' };
                return (
                  <div
                    key={p.idPlanEmergencia}
                    className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <span className="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider bg-primary/10 text-primary border border-primary/20">
                          {p.tipoContingencia}
                        </span>
                        <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${estCfg.color}`}>
                          {estCfg.label}
                        </span>
                      </div>

                      <h4 className="font-bold text-lg text-foreground mb-3">{p.titulo}</h4>

                      <div className="space-y-3 text-xs">
                        <div className="p-3 bg-muted/40 border border-border/40 rounded-lg">
                          <p className="font-semibold text-primary flex items-center gap-1.5 mb-1">
                            <MapPin className="w-3.5 h-3.5" /> Puntos de Encuentro:
                          </p>
                          <p className="text-foreground leading-relaxed">{p.puntosEncuentro}</p>
                        </div>

                        <div>
                          <p className="font-semibold text-muted-foreground flex items-center gap-1.5 mb-1">
                            <Compass className="w-3.5 h-3.5" /> Rutas de Evacuación:
                          </p>
                          <p className="text-foreground leading-relaxed bg-background/60 p-2.5 rounded-lg border border-border/40 whitespace-pre-line">
                            {p.rutasEvacuacionDesc}
                          </p>
                        </div>

                        <div className="flex items-center gap-4 text-muted-foreground pt-1">
                          <span className="flex items-center gap-1">
                            <Calendar className="w-3.5 h-3.5" /> Última Revisión: {p.fechaUltimaRevision}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between border-t border-border/60 pt-3 mt-4">
                      <div className="flex items-center gap-3">
                        {p.mapaEvacuacionUrl && (
                          <a
                            href={p.mapaEvacuacionUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 text-xs text-primary hover:underline font-medium"
                          >
                            <MapPin className="w-3.5 h-3.5" /> Ver Mapa
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        )}
                        {p.documentoPlanUrl && (
                          <a
                            href={p.documentoPlanUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1 text-xs text-primary hover:underline font-medium"
                          >
                            <FileText className="w-3.5 h-3.5" /> Documento Completo
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        )}
                        {!p.mapaEvacuacionUrl && !p.documentoPlanUrl && (
                          <span className="text-xs text-muted-foreground italic">Sin archivos adjuntos</span>
                        )}
                      </div>

                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleOpenEditarPlan(p)}
                          className="p-1.5 text-muted-foreground hover:text-primary hover:bg-primary/10 rounded-md transition-colors"
                          title="Editar Plan"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => {
                            setItemEliminar({ tipo: 'plan', data: p });
                            setModalDeleteOpen(true);
                          }}
                          className="p-1.5 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-md transition-colors"
                          title="Eliminar Plan"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Modal Contacto (Crear / Editar) */}
      <Modal
        open={modalContactoOpen}
        onOpenChange={setModalContactoOpen}
        title={contactoEditar ? 'Editar Contacto de Emergencia' : 'Registrar Contacto de Emergencia'}
        description="Agregue teléfonos de bomberos, cuadrante policial, ambulancias o cuadrillas de servicios públicos"
      >
        <form onSubmit={handleSaveContacto} className="space-y-4 pt-2">
          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Entidad u Organismo *
            </label>
            <input
              type="text"
              required
              value={contactoForm.entidad}
              onChange={(e) => setContactoForm({ ...contactoForm, entidad: e.target.value })}
              placeholder="Ej. Bomberos Estación Norte, CAI Chicó"
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Tipo de Servicio *
              </label>
              <select
                value={contactoForm.tipoServicio}
                onChange={(e) => setContactoForm({ ...contactoForm, tipoServicio: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              >
                {TIPOS_SERVICIO.map((ts) => (
                  <option key={ts.id} value={ts.id}>
                    {ts.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Prioritario en Minuta (Portería) *
              </label>
              <select
                value={contactoForm.esPrioritarioMinuta}
                onChange={(e) => setContactoForm({ ...contactoForm, esPrioritarioMinuta: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              >
                <option value="S">Sí (Visible en pantalla principal de portero)</option>
                <option value="N">No (Directorio secundario)</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Teléfono Principal *
              </label>
              <input
                type="tel"
                required
                value={contactoForm.telefonoPrincipal}
                onChange={(e) => setContactoForm({ ...contactoForm, telefonoPrincipal: e.target.value })}
                placeholder="Ej. 119 o 6013456789"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Teléfono Alterno / Celular
              </label>
              <input
                type="tel"
                value={contactoForm.telefonoAlterno}
                onChange={(e) => setContactoForm({ ...contactoForm, telefonoAlterno: e.target.value })}
                placeholder="Ej. 3001234567"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Dirección / Sede
              </label>
              <input
                type="text"
                value={contactoForm.direccion}
                onChange={(e) => setContactoForm({ ...contactoForm, direccion: e.target.value })}
                placeholder="Ej. Av. Carrera 15 # 85-30"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Orden de Visualización
              </label>
              <input
                type="number"
                min="1"
                max="99"
                value={contactoForm.ordenVisualizacion}
                onChange={(e) => setContactoForm({ ...contactoForm, ordenVisualizacion: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <button
              type="button"
              onClick={() => setModalContactoOpen(false)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm font-medium bg-primary text-primary-content rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {saving ? 'Guardando...' : contactoEditar ? 'Actualizar Contacto' : 'Guardar Contacto'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal Plan (Crear / Editar) */}
      <Modal
        open={modalPlanOpen}
        onOpenChange={setModalPlanOpen}
        title={planEditar ? 'Editar Plan de Emergencia' : 'Registrar Plan de Contingencia'}
        description="Detalle el protocolo de evacuación, puntos de encuentro y rutas de escape"
      >
        <form onSubmit={handleSavePlan} className="space-y-4 pt-2">
          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Título del Plan *
            </label>
            <input
              type="text"
              required
              value={planForm.titulo}
              onChange={(e) => setPlanForm({ ...planForm, titulo: e.target.value })}
              placeholder="Ej. Plan de Contingencia ante Incendio Torre 1"
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Tipo de Contingencia *
              </label>
              <select
                value={planForm.tipoContingencia}
                onChange={(e) => setPlanForm({ ...planForm, tipoContingencia: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              >
                {TIPOS_CONTINGENCIA.map((tc) => (
                  <option key={tc} value={tc}>
                    {tc}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                Estado del Plan *
              </label>
              <select
                value={planForm.estado}
                onChange={(e) => setPlanForm({ ...planForm, estado: e.target.value })}
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              >
                <option value="ACTIVO">Activo / Vigente</option>
                <option value="EN_REVISION">En Revisión</option>
                <option value="OBSOLETO">Obsoleto</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Puntos de Encuentro Seguros *
            </label>
            <input
              type="text"
              required
              value={planForm.puntosEncuentro}
              onChange={(e) => setPlanForm({ ...planForm, puntosEncuentro: e.target.value })}
              placeholder="Ej. Parqueadero exterior de visitantes, Parque central contiguo"
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Rutas y Protocolo de Evacuación *
            </label>
            <textarea
              required
              rows={4}
              value={planForm.rutasEvacuacionDesc}
              onChange={(e) => setPlanForm({ ...planForm, rutasEvacuacionDesc: e.target.value })}
              placeholder="Detalle los pasos de evacuación por escaleras de emergencia, señalización y prohibición de uso de ascensores..."
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                URL Mapa de Evacuación
              </label>
              <input
                type="url"
                value={planForm.mapaEvacuacionUrl}
                onChange={(e) => setPlanForm({ ...planForm, mapaEvacuacionUrl: e.target.value })}
                placeholder="https://.../mapa-evacuacion.pdf"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-muted-foreground mb-1">
                URL Documento del Plan
              </label>
              <input
                type="url"
                value={planForm.documentoPlanUrl}
                onChange={(e) => setPlanForm({ ...planForm, documentoPlanUrl: e.target.value })}
                placeholder="https://.../plan-contingencia.pdf"
                className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-muted-foreground mb-1">
              Fecha Última Revisión
            </label>
            <input
              type="date"
              value={planForm.fechaUltimaRevision}
              onChange={(e) => setPlanForm({ ...planForm, fechaUltimaRevision: e.target.value })}
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4 border-t border-border">
            <button
              type="button"
              onClick={() => setModalPlanOpen(false)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm font-medium bg-primary text-primary-content rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {saving ? 'Guardando...' : planEditar ? 'Actualizar Plan' : 'Guardar Plan'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal Confirmar Eliminación */}
      <Modal
        open={modalDeleteOpen}
        onOpenChange={setModalDeleteOpen}
        title="Confirmar Eliminación"
        description="¿Está seguro de eliminar este registro? Esta acción no se puede deshacer."
      >
        <div className="pt-2 space-y-4">
          {itemEliminar && (
            <div className="p-3 bg-destructive/10 text-destructive rounded-lg text-sm">
              <p className="font-semibold">
                {itemEliminar.tipo === 'contacto'
                  ? itemEliminar.data.entidad
                  : itemEliminar.data.titulo}
              </p>
              <p className="text-xs text-muted-foreground">
                {itemEliminar.tipo === 'contacto'
                  ? `Teléfono: ${itemEliminar.data.telefonoPrincipal}`
                  : `Contingencia: ${itemEliminar.data.tipoContingencia}`}
              </p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2 border-t border-border">
            <button
              type="button"
              onClick={() => setModalDeleteOpen(false)}
              className="px-4 py-2 text-sm font-medium text-muted-foreground hover:bg-muted rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button
              type="button"
              disabled={saving}
              onClick={handleDelete}
              className="px-4 py-2 text-sm font-medium bg-destructive text-destructive-content rounded-lg hover:bg-destructive/90 transition-colors disabled:opacity-50"
            >
              {saving ? 'Eliminando...' : 'Sí, Eliminar'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
