import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent, CardDescription, CardFooter } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import { Textarea } from '../components/ui/textarea.tsx';
import { Switch } from '../components/ui/switch.tsx';
import { Checkbox } from '../components/ui/checkbox.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from '../components/ui/alert-dialog.tsx';
import { toast } from 'sonner';

// SAED Technical Limits (Oracle ATP & Business Architecture)
const SAED_LIMITS = {
  MAX_PROPIEDADES_CEILING: 999999,      // NUMBER(6,0)
  MAX_UNIDADES_CEILING: 99999999,        // NUMBER(8,0)
  MAX_USUARIOS_CEILING: 99999999,        // NUMBER(8,0)
  MAX_ALMACENAMIENTO_CEILING: 999999,    // NUMBER(10,2) in GB
  MAX_PRECIO_CEILING: 9999999999,        // NUMBER(12,2) COP
  MAX_NOMBRE_LENGTH: 80,                 // VARCHAR2(80 CHAR)
  MAX_CODIGO_LENGTH: 30,                 // VARCHAR2(30 CHAR)
  MAX_DESCRIPCION_LENGTH: 500            // VARCHAR2(500 CHAR)
};

const MODULE_METADATA = {
  FINANZAS: {
    icon: 'payments',
    label: 'Finanzas & Recaudo Wompi',
    desc: 'Facturación de cuotas, pasarela Wompi (PSE / Tarjetas), mora y conciliación.',
    badgeClass: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
  },
  ASAMBLEAS: {
    icon: 'how_to_vote',
    label: 'Asambleas Virtuales Ley 675',
    desc: 'Quórum automático en tiempo real con coeficientes de copropiedad y votaciones.',
    badgeClass: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20'
  },
  PAQUETES: {
    icon: 'package_2',
    label: 'Paquetería & Casillero Digital',
    desc: 'Custodia en portería y entrega blindada a residentes mediante PIN de 6 dígitos.',
    badgeClass: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20'
  },
  PARQUEADEROS: {
    icon: 'directions_car',
    label: 'Control de Parqueaderos & Bahías',
    desc: 'Bahías de visitantes, parqueaderos asignados a unidades y control de placas.',
    badgeClass: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20'
  },
  RESERVAS: {
    icon: 'event_available',
    label: 'Reservas de Zonas Comunes',
    desc: 'Salón social, zona BBQ, piscinas y canchas con aforos y depósitos.',
    badgeClass: 'bg-teal-500/10 text-teal-600 dark:text-teal-400 border-teal-500/20'
  },
  PQRS: {
    icon: 'support_agent',
    label: 'PQRS & Convivencia Integral',
    desc: 'Radicación digital de quejas, seguimiento de acuerdos y trazabilidad con SLA.',
    badgeClass: 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20'
  },
  OBRAS: {
    icon: 'construction',
    label: 'Gestión de Obras & Reformas',
    desc: 'Aprobación de reformas en unidades privadas, personal externo y contratistas.',
    badgeClass: 'bg-orange-500/10 text-orange-600 dark:text-orange-400 border-orange-500/20'
  },
  POLIZAS: {
    icon: 'verified_user',
    label: 'Pólizas de Seguro Copropiedad',
    desc: 'Vigilancia de vencimientos y coberturas contra daños en áreas comunes.',
    badgeClass: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20'
  },
  INCIDENTES: {
    icon: 'warning',
    label: 'Libro Digital de Incidentes',
    desc: 'Minuta de portería, novedades operativas, emergencias y bitácora de seguridad.',
    badgeClass: 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20'
  }
};

const DEFAULT_FORM = {
  id: null,
  codigo: '',
  nombre: '',
  descripcion: '',
  precioMensual: 150000,
  maxPropiedades: 1,
  unlimitedPropiedades: false,
  maxUnidades: 50,
  unlimitedUnidades: false,
  maxUsuarios: 100,
  unlimitedUsuarios: false,
  maxAlmacenamientoGb: 5,
  unlimitedAlmacenamiento: false,
  estado: 'ACTIVO',
  modulos: ['FINANZAS', 'PQRS', 'PAQUETES', 'PARQUEADEROS'],
  nivelSoporte: 'ESTANDAR',
  reportesAvanzados: true,
  respaldos: 'DIARIOS',
  destacado: false
};

export default function SuperAdminPlanesPage() {
  const [plans, setPlans] = useState([]);
  const [catalogModules, setCatalogModules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('TODOS');

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [activeTab, setActiveTab] = useState('general');
  const [form, setForm] = useState(DEFAULT_FORM);

  // Delete State
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  async function loadData() {
    try {
      setLoading(true);
      const [plansRes, modRes] = await Promise.all([
        api.get('/platform/plans'),
        api.get('/platform/plans/catalog-modules').catch(() => ({ data: [] }))
      ]);

      const plansList = plansRes?.data || plansRes || [];
      const modulesList = modRes?.data || modRes || [];

      setPlans(Array.isArray(plansList) ? plansList : []);

      if (Array.isArray(modulesList) && modulesList.length > 0) {
        setCatalogModules(modulesList);
      } else {
        const fallback = Object.entries(MODULE_METADATA).map(([codigo, meta], idx) => ({
          id: idx + 1,
          codigo,
          nombre: meta.label,
          descripcion: meta.desc
        }));
        setCatalogModules(fallback);
      }
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar la información de planes y módulos');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  function handleOpenCreate() {
    setForm({
      ...DEFAULT_FORM,
      modulos: catalogModules.map(m => m.codigo)
    });
    setIsEditing(false);
    setActiveTab('general');
    setShowModal(true);
  }

  function handleOpenEdit(plan) {
    let cfg = {};
    try {
      if (typeof plan.configuracionAvanzada === 'string' && plan.configuracionAvanzada.trim().startsWith('{')) {
        cfg = JSON.parse(plan.configuracionAvanzada);
      } else if (typeof plan.configuracionAvanzada === 'object' && plan.configuracionAvanzada !== null) {
        cfg = plan.configuracionAvanzada;
      }
    } catch (e) {
      cfg = {};
    }

    const hasNoPropLimit = plan.maxPropiedades === null || plan.maxPropiedades === 0;
    const hasNoUnitLimit = plan.maxUnidades === null || plan.maxUnidades === 0;
    const hasNoUsrLimit = plan.maxUsuarios === null || plan.maxUsuarios === 0;
    const hasNoStorageLimit = plan.maxAlmacenamientoGb === null || plan.maxAlmacenamientoGb === 0;

    setForm({
      id: plan.id,
      codigo: plan.codigo || '',
      nombre: plan.nombre || '',
      descripcion: plan.descripcion || '',
      precioMensual: plan.precioMensual || 0,
      maxPropiedades: hasNoPropLimit ? 1 : plan.maxPropiedades,
      unlimitedPropiedades: hasNoPropLimit,
      maxUnidades: hasNoUnitLimit ? 50 : plan.maxUnidades,
      unlimitedUnidades: hasNoUnitLimit,
      maxUsuarios: hasNoUsrLimit ? 100 : plan.maxUsuarios,
      unlimitedUsuarios: hasNoUsrLimit,
      maxAlmacenamientoGb: hasNoStorageLimit ? 5 : plan.maxAlmacenamientoGb,
      unlimitedAlmacenamiento: hasNoStorageLimit,
      estado: plan.estado || 'ACTIVO',
      modulos: Array.isArray(plan.modulos) ? [...plan.modulos] : [],
      nivelSoporte: cfg.nivelSoporte || 'ESTANDAR',
      reportesAvanzados: cfg.reportesAvanzados !== false,
      respaldos: cfg.respaldos || 'DIARIOS',
      destacado: !!cfg.destacado
    });
    setIsEditing(true);
    setActiveTab('general');
    setShowModal(true);
  }

  async function handleSave(e) {
    e.preventDefault();

    // 1. Validaciones de Identificación
    const nombre = (form.nombre || '').trim();
    if (!nombre) {
      toast.error('El nombre comercial del plan es obligatorio');
      return;
    }
    if (nombre.length > SAED_LIMITS.MAX_NOMBRE_LENGTH) {
      toast.error(`El nombre no puede exceder ${SAED_LIMITS.MAX_NOMBRE_LENGTH} caracteres`);
      return;
    }

    const codigo = (form.codigo || '').trim().toUpperCase();
    if (codigo && !/^[A-Z0-9_]{2,30}$/.test(codigo)) {
      toast.error('El código debe contener entre 2 y 30 caracteres alfanuméricos y guiones bajos (sin espacios)');
      return;
    }

    if (form.descripcion && form.descripcion.length > SAED_LIMITS.MAX_DESCRIPCION_LENGTH) {
      toast.error(`La descripción no puede exceder ${SAED_LIMITS.MAX_DESCRIPCION_LENGTH} caracteres`);
      return;
    }

    // 2. Validaciones de Tarifas
    const precio = Number(form.precioMensual);
    if (isNaN(precio) || precio < 0) {
      toast.error('El precio mensual debe ser igual o mayor a $0 COP');
      return;
    }
    if (precio > SAED_LIMITS.MAX_PRECIO_CEILING) {
      toast.error(`El precio mensual no puede exceder $${SAED_LIMITS.MAX_PRECIO_CEILING.toLocaleString('es-CO')} COP`);
      return;
    }

    // 3. Validaciones de Límites de Capacidad a límites de SAED
    let propVal = null;
    if (!form.unlimitedPropiedades) {
      propVal = Number(form.maxPropiedades);
      if (isNaN(propVal) || propVal < 1 || propVal > SAED_LIMITS.MAX_PROPIEDADES_CEILING) {
        toast.error(`El límite de propiedades debe ser un número entre 1 y ${SAED_LIMITS.MAX_PROPIEDADES_CEILING.toLocaleString('es-CO')} (o marcar Ilimitado)`);
        return;
      }
    }

    let unitVal = null;
    if (!form.unlimitedUnidades) {
      unitVal = Number(form.maxUnidades);
      if (isNaN(unitVal) || unitVal < 1 || unitVal > SAED_LIMITS.MAX_UNIDADES_CEILING) {
        toast.error(`El límite de unidades debe ser un número entre 1 y ${SAED_LIMITS.MAX_UNIDADES_CEILING.toLocaleString('es-CO')} (o marcar Ilimitado)`);
        return;
      }
    }

    let usrVal = null;
    if (!form.unlimitedUsuarios) {
      usrVal = Number(form.maxUsuarios);
      if (isNaN(usrVal) || usrVal < 1 || usrVal > SAED_LIMITS.MAX_USUARIOS_CEILING) {
        toast.error(`El límite de usuarios debe ser un número entre 1 y ${SAED_LIMITS.MAX_USUARIOS_CEILING.toLocaleString('es-CO')} (o marcar Ilimitado)`);
        return;
      }
    }

    let storageVal = null;
    if (!form.unlimitedAlmacenamiento) {
      storageVal = Number(form.maxAlmacenamientoGb);
      if (isNaN(storageVal) || storageVal < 1 || storageVal > SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING) {
        toast.error(`El almacenamiento en la nube debe ser entre 1 y ${SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING.toLocaleString('es-CO')} GB (o marcar Ilimitado)`);
        return;
      }
    }

    try {
      setSubmitting(true);
      const payload = {
        nombre,
        codigo: codigo || undefined,
        descripcion: form.descripcion ? form.descripcion.trim() : '',
        precioMensual: precio,
        maxPropiedades: propVal,
        maxUnidades: unitVal,
        maxUsuarios: usrVal,
        maxAlmacenamientoGb: storageVal,
        estado: form.estado || 'ACTIVO',
        modulos: form.modulos,
        configuracionAvanzada: JSON.stringify({
          nivelSoporte: form.nivelSoporte,
          reportesAvanzados: form.reportesAvanzados,
          respaldos: form.respaldos,
          destacado: form.destacado
        })
      };

      if (isEditing) {
        await api.put(`/platform/plans/${form.id}`, payload);
        toast.success(`Plan "${form.nombre}" actualizado exitosamente`);
      } else {
        await api.post('/platform/plans', payload);
        toast.success(`Plan "${form.nombre}" creado exitosamente`);
      }

      setShowModal(false);
      loadData();
    } catch (err) {
      console.error(err);
      const msg = err?.response?.data?.message || err?.message || 'Error al guardar el plan';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(plan) {
    const nextStatus = plan.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await api.patch(`/platform/plans/${plan.id}/status`, { estado: nextStatus });
      toast.success(`Plan ${nextStatus === 'ACTIVO' ? 'activado' : 'desactivado'} exitosamente`);
      loadData();
    } catch (err) {
      console.error(err);
      toast.error(err?.response?.data?.message || 'Error al actualizar el estado del plan');
    }
  }

  async function handleDeleteConfirm() {
    if (!deleteTarget) return;
    try {
      setDeleting(true);
      await api.delete(`/platform/plans/${deleteTarget.id}`);
      toast.success(`Plan "${deleteTarget.nombre}" eliminado exitosamente`);
      setDeleteTarget(null);
      loadData();
    } catch (err) {
      console.error(err);
      const msg = err?.response?.data?.message || 'No fue posible eliminar el plan';
      toast.error(msg);
    } finally {
      setDeleting(false);
    }
  }

  function toggleModule(codigo) {
    setForm(prev => {
      const exists = prev.modulos.includes(codigo);
      const nextModulos = exists
        ? prev.modulos.filter(c => c !== codigo)
        : [...prev.modulos, codigo];
      return { ...prev, modulos: nextModulos };
    });
  }

  function handleSelectAllModules() {
    setForm(prev => ({
      ...prev,
      modulos: catalogModules.map(m => m.codigo)
    }));
  }

  function handleClearModules() {
    setForm(prev => ({
      ...prev,
      modulos: []
    }));
  }

  function handleSelectBasicModules() {
    setForm(prev => ({
      ...prev,
      modulos: ['FINANZAS', 'PQRS', 'PAQUETES', 'PARQUEADEROS']
    }));
  }

  function formatCurrency(amount) {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(amount || 0);
  }

  // Filtered plans
  const filteredPlans = useMemo(() => {
    return plans.filter(p => {
      const matchSearch =
        (p.nombre || '').toLowerCase().includes(search.toLowerCase()) ||
        (p.codigo || '').toLowerCase().includes(search.toLowerCase()) ||
        (p.descripcion || '').toLowerCase().includes(search.toLowerCase());

      const matchStatus =
        statusFilter === 'TODOS' ||
        (statusFilter === 'ACTIVO' && p.estado === 'ACTIVO') ||
        (statusFilter === 'INACTIVO' && p.estado !== 'ACTIVO');

      return matchSearch && matchStatus;
    });
  }, [plans, search, statusFilter]);

  // Aggregate stats
  const totalActivos = useMemo(() => plans.filter(p => p.estado === 'ACTIVO').length, [plans]);
  const totalSuscripciones = useMemo(() => {
    return plans.reduce((acc, p) => acc + (Number(p.organizacionesActivas) || 0), 0);
  }, [plans]);

  return (
    <div className="p-6 space-y-6 animate-fadeIn max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-2xl">loyalty</span>
            Planes y Tarifas SaaS
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Control comercial integral, límites de infraestructura validados a la capacidad de SAED y matriz de módulos habilitados.
          </p>
        </div>
        <Button onClick={handleOpenCreate} className="gap-2 shrink-0 shadow-sm">
          <span className="material-symbols-outlined text-base">add_circle</span>
          Nuevo Plan SaaS
        </Button>
      </div>

      {/* KPI Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-border/70 shadow-xs">
          <CardContent className="p-4 flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary">
              <span className="material-symbols-outlined text-2xl">inventory_2</span>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-medium uppercase">Planes en Catálogo</div>
              <div className="text-2xl font-black text-foreground">{plans.length}</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border/70 shadow-xs">
          <CardContent className="p-4 flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <span className="material-symbols-outlined text-2xl">check_circle</span>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-medium uppercase">Planes Activos</div>
              <div className="text-2xl font-black text-foreground">{totalActivos}</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border/70 shadow-xs">
          <CardContent className="p-4 flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
              <span className="material-symbols-outlined text-2xl">corporate_fare</span>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-medium uppercase">Orgs Suscritas</div>
              <div className="text-2xl font-black text-foreground">{totalSuscripciones}</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border/70 shadow-xs">
          <CardContent className="p-4 flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
              <span className="material-symbols-outlined text-2xl">widgets</span>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-medium uppercase">Módulos Plataforma</div>
              <div className="text-2xl font-black text-foreground">{catalogModules.length}</div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filters Toolbar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-muted/40 p-3 rounded-lg border border-border/60">
        <div className="relative w-full sm:w-80">
          <span className="material-symbols-outlined absolute left-3 top-2.5 text-muted-foreground text-sm">search</span>
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por nombre, código o características..."
            className="pl-9 h-9 text-xs"
          />
        </div>

        <div className="flex items-center gap-1.5 self-end sm:self-center">
          <span className="text-xs text-muted-foreground mr-1">Estado:</span>
          {['TODOS', 'ACTIVO', 'INACTIVO'].map((st) => (
            <Button
              key={st}
              variant={statusFilter === st ? 'default' : 'ghost'}
              size="sm"
              onClick={() => setStatusFilter(st)}
              className="h-8 text-xs px-2.5"
            >
              {st === 'TODOS' ? 'Todos' : st === 'ACTIVO' ? 'Activos' : 'Inactivos'}
            </Button>
          ))}
        </div>
      </div>

      {/* Plans List Grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-[480px] w-full rounded-xl" />
          ))}
        </div>
      ) : filteredPlans.length === 0 ? (
        <div className="text-center py-16 text-muted-foreground space-y-3 bg-card rounded-xl border border-dashed border-border">
          <span className="material-symbols-outlined text-5xl text-muted-foreground/50">inventory_2</span>
          <p className="text-base font-medium">No se encontraron planes con los filtros seleccionados.</p>
          <Button variant="outline" size="sm" onClick={() => { setSearch(''); setStatusFilter('TODOS'); }}>
            Limpiar Filtros
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredPlans.map((plan) => {
            const planModules = Array.isArray(plan.modulos) ? plan.modulos : [];
            const planOrgCount = Number(plan.organizacionesActivas) || 0;
            const totalOrgs = Number(plan.totalOrganizaciones) || planOrgCount;

            let cfg = {};
            try {
              if (typeof plan.configuracionAvanzada === 'string' && plan.configuracionAvanzada.startsWith('{')) {
                cfg = JSON.parse(plan.configuracionAvanzada);
              } else if (typeof plan.configuracionAvanzada === 'object' && plan.configuracionAvanzada !== null) {
                cfg = plan.configuracionAvanzada;
              }
            } catch (e) {
              cfg = {};
            }

            const isFree = (plan.precioMensual || 0) === 0;
            const precioAnual = Math.round((plan.precioMensual || 0) * 12 * 0.80);

            return (
              <Card
                key={plan.id || plan.codigo}
                className={`flex flex-col justify-between border transition-all shadow-xs hover:shadow-md ${
                  cfg.destacado ? 'border-primary ring-1 ring-primary/30' : 'border-border/80'
                } ${plan.estado !== 'ACTIVO' ? 'opacity-80 bg-muted/20' : 'bg-card'}`}
              >
                <CardHeader className="space-y-3 pb-3">
                  <div className="flex justify-between items-start gap-2">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <Badge variant="outline" className="font-mono text-[10px] uppercase font-bold tracking-wider">
                        {plan.codigo}
                      </Badge>
                      {cfg.destacado && (
                        <Badge className="bg-primary/90 text-primary-foreground text-[10px] gap-1 px-1.5">
                          <span className="material-symbols-outlined text-[11px]">star</span>
                          Popular
                        </Badge>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <Badge
                        variant="secondary"
                        className={
                          plan.estado === 'ACTIVO'
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-[11px]'
                            : 'bg-muted text-muted-foreground text-[11px]'
                        }
                      >
                        {plan.estado}
                      </Badge>
                      <Switch
                        checked={plan.estado === 'ACTIVO'}
                        onCheckedChange={() => handleToggleStatus(plan)}
                        aria-label={`Activar o desactivar ${plan.nombre}`}
                      />
                    </div>
                  </div>

                  <div>
                    <CardTitle className="text-xl font-bold">{plan.nombre}</CardTitle>
                    <CardDescription className="text-xs mt-1 min-h-[34px] line-clamp-2">
                      {plan.descripcion || 'Plan integral de copropiedad con módulos y aislamiento multi-tenant.'}
                    </CardDescription>
                  </div>

                  {/* Pricing Box */}
                  <div className="pt-2 border-t border-border/80">
                    <div className="flex items-baseline gap-1.5">
                      <span className="text-2xl font-black text-foreground">
                        {isFree ? 'Gratis' : formatCurrency(plan.precioMensual)}
                      </span>
                      {!isFree && <span className="text-xs text-muted-foreground">/ mes</span>}
                    </div>
                    {!isFree && (
                      <div className="text-[11px] text-muted-foreground flex items-center gap-1 mt-0.5">
                        <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                          {formatCurrency(precioAnual)}/año
                        </span>
                        <span>(ahorro 20% anual)</span>
                      </div>
                    )}
                  </div>

                  {/* Usage Counter */}
                  <div className="text-xs bg-muted/40 p-2 rounded-md flex items-center justify-between border border-border/60">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-sm text-primary">groups</span>
                      Organizaciones:
                    </span>
                    <span className="font-semibold text-foreground">
                      {planOrgCount} activas {totalOrgs > planOrgCount ? `(${totalOrgs} total)` : ''}
                    </span>
                  </div>
                </CardHeader>

                <CardContent className="space-y-4 text-xs pt-1">
                  {/* Limits and Capacity */}
                  <div>
                    <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground mb-2 flex items-center gap-1">
                      <span className="material-symbols-outlined text-xs">speed</span>
                      Límites y Capacidades SAED
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div className="bg-background/80 p-2 rounded border border-border/50">
                        <div className="text-muted-foreground text-[10px] flex items-center gap-1">
                          <span className="material-symbols-outlined text-xs text-primary">apartment</span>
                          Propiedades
                        </div>
                        <div className="font-bold text-foreground mt-0.5">
                          {plan.maxPropiedades ? `Hasta ${plan.maxPropiedades.toLocaleString('es-CO')}` : 'Ilimitadas (∞)'}
                        </div>
                      </div>

                      <div className="bg-background/80 p-2 rounded border border-border/50">
                        <div className="text-muted-foreground text-[10px] flex items-center gap-1">
                          <span className="material-symbols-outlined text-xs text-primary">home</span>
                          Unidades
                        </div>
                        <div className="font-bold text-foreground mt-0.5">
                          {plan.maxUnidades ? `Hasta ${plan.maxUnidades.toLocaleString('es-CO')}` : 'Ilimitadas (∞)'}
                        </div>
                      </div>

                      <div className="bg-background/80 p-2 rounded border border-border/50">
                        <div className="text-muted-foreground text-[10px] flex items-center gap-1">
                          <span className="material-symbols-outlined text-xs text-primary">person</span>
                          Usuarios
                        </div>
                        <div className="font-bold text-foreground mt-0.5">
                          {plan.maxUsuarios ? `Hasta ${plan.maxUsuarios.toLocaleString('es-CO')}` : 'Ilimitados (∞)'}
                        </div>
                      </div>

                      <div className="bg-background/80 p-2 rounded border border-border/50">
                        <div className="text-muted-foreground text-[10px] flex items-center gap-1">
                          <span className="material-symbols-outlined text-xs text-primary">cloud</span>
                          Almacenamiento
                        </div>
                        <div className="font-bold text-foreground mt-0.5">
                          {plan.maxAlmacenamientoGb ? `${plan.maxAlmacenamientoGb} GB` : 'Ilimitado (∞)'}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Modules Entitlements Pills */}
                  <div>
                    <div className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground mb-2 flex items-center justify-between">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-xs">tune</span>
                        Módulos Incluidos
                      </span>
                      <span className="text-[10px] text-muted-foreground font-mono">
                        {planModules.length} de {catalogModules.length}
                      </span>
                    </div>

                    <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto pr-1">
                      {catalogModules.map((m) => {
                        const isEnabled = planModules.includes(m.codigo);
                        const meta = MODULE_METADATA[m.codigo] || {
                          icon: 'extension',
                          label: m.nombre,
                          badgeClass: 'bg-muted text-muted-foreground'
                        };

                        if (!isEnabled) {
                          return (
                            <span
                              key={m.codigo}
                              title={`${meta.label}: No incluido en este plan`}
                              className="text-[10px] px-1.5 py-0.5 rounded border border-border/40 text-muted-foreground/40 line-through flex items-center gap-1"
                            >
                              <span className="material-symbols-outlined text-[10px]">{meta.icon}</span>
                              {meta.label.split(' ')[0]}
                            </span>
                          );
                        }

                        return (
                          <span
                            key={m.codigo}
                            title={`${meta.label}: Habilitado`}
                            className={`text-[10px] px-2 py-0.5 rounded border font-medium flex items-center gap-1 ${meta.badgeClass}`}
                          >
                            <span className="material-symbols-outlined text-[11px]">{meta.icon}</span>
                            {meta.label.split(' ')[0]}
                          </span>
                        );
                      })}
                    </div>
                  </div>

                  {/* Support and Add-on info */}
                  {cfg.nivelSoporte && (
                    <div className="pt-2 border-t border-border/50 text-[11px] text-muted-foreground flex items-center justify-between">
                      <span>Soporte:</span>
                      <span className="font-semibold text-foreground uppercase text-[10px]">
                        {cfg.nivelSoporte.replace('_', ' ')}
                      </span>
                    </div>
                  )}
                </CardContent>

                <CardFooter className="pt-3 border-t border-border/80 flex items-center justify-between gap-2">
                  <div className="text-[11px] text-muted-foreground font-mono">
                    #{plan.id}
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenEdit(plan)}
                      className="h-8 text-xs gap-1"
                    >
                      <span className="material-symbols-outlined text-sm">edit</span>
                      Editar
                    </Button>

                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setDeleteTarget(plan)}
                      className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                      title="Eliminar Plan"
                    >
                      <span className="material-symbols-outlined text-base">delete</span>
                    </Button>
                  </div>
                </CardFooter>
              </Card>
            );
          })}
        </div>
      )}

      {/* Modal Crear / Editar Plan Integral */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-xl font-bold">
              <span className="material-symbols-outlined text-primary text-2xl">
                {isEditing ? 'edit_note' : 'add_circle'}
              </span>
              {isEditing ? `Editar Plan: ${form.nombre}` : 'Registrar Nuevo Plan SaaS'}
            </DialogTitle>
            <DialogDescription>
              Configure las tarifas comerciales, límites cuantitativos de infraestructura y la matriz de módulos habilitados de SAED.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSave} className="space-y-4 mt-2">
            <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
              <TabsList className="grid grid-cols-3 w-full">
                <TabsTrigger value="general" className="text-xs">1. General & Precio</TabsTrigger>
                <TabsTrigger value="limites" className="text-xs">2. Límites y Capacidad</TabsTrigger>
                <TabsTrigger value="modulos" className="text-xs">
                  3. Módulos ({form.modulos.length})
                </TabsTrigger>
              </TabsList>

              {/* TAB 1: General & Pricing */}
              <TabsContent value="general" className="space-y-3.5 pt-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="plan-nombre" className="text-xs font-semibold uppercase text-muted-foreground flex justify-between">
                      <span>Nombre Comercial *</span>
                      <span className="text-[10px] text-muted-foreground font-normal">Máx {SAED_LIMITS.MAX_NOMBRE_LENGTH}</span>
                    </Label>
                    <Input
                      id="plan-nombre"
                      required
                      maxLength={SAED_LIMITS.MAX_NOMBRE_LENGTH}
                      value={form.nombre}
                      onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                      placeholder="Ej. Plan Corporativo Multi-Conjunto"
                      className="text-sm"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="plan-codigo" className="text-xs font-semibold uppercase text-muted-foreground flex justify-between">
                      <span>Código Único *</span>
                      <span className="text-[10px] text-muted-foreground font-normal">Máx {SAED_LIMITS.MAX_CODIGO_LENGTH}</span>
                    </Label>
                    <Input
                      id="plan-codigo"
                      required
                      disabled={isEditing}
                      maxLength={SAED_LIMITS.MAX_CODIGO_LENGTH}
                      value={form.codigo}
                      onChange={(e) => setForm({ ...form, codigo: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_') })}
                      placeholder="Ej. CORP_MULTI"
                      className="text-sm font-mono uppercase"
                    />
                    {isEditing && (
                      <p className="text-[10px] text-muted-foreground">Inmutable para preservar integridad con membresías activas.</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="plan-precio" className="text-xs font-semibold uppercase text-muted-foreground">
                      Precio Mensual (COP) *
                    </Label>
                    <Input
                      id="plan-precio"
                      type="number"
                      min="0"
                      max={SAED_LIMITS.MAX_PRECIO_CEILING}
                      required
                      value={form.precioMensual}
                      onChange={(e) => setForm({ ...form, precioMensual: Math.max(0, Number(e.target.value)) })}
                      className="text-sm font-mono"
                    />
                    <p className="text-[11px] text-muted-foreground font-mono">
                      Equivalente anual: {formatCurrency(Math.round(form.precioMensual * 12 * 0.80))} (20% ahorro anual)
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="plan-soporte" className="text-xs font-semibold uppercase text-muted-foreground">
                      Nivel de Soporte Incluido
                    </Label>
                    <select
                      id="plan-soporte"
                      value={form.nivelSoporte}
                      onChange={(e) => setForm({ ...form, nivelSoporte: e.target.value })}
                      className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                    >
                      <option value="ESTANDAR">Estándar (Correo y Tickets)</option>
                      <option value="PRIORITARIO">Prioritario (WhatsApp & Respuesta &lt; 4h)</option>
                      <option value="DEDICADO_24_7">Dedicado 24/7 (Línea directa y ejecutivo)</option>
                    </select>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="plan-desc" className="text-xs font-semibold uppercase text-muted-foreground flex justify-between">
                    <span>Descripción Comercial</span>
                    <span className="text-[10px] text-muted-foreground font-normal">Máx {SAED_LIMITS.MAX_DESCRIPCION_LENGTH}</span>
                  </Label>
                  <Textarea
                    id="plan-desc"
                    rows={2}
                    maxLength={SAED_LIMITS.MAX_DESCRIPCION_LENGTH}
                    value={form.descripcion}
                    onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                    placeholder="Resumen de capacidades y beneficios comerciales..."
                    className="text-xs"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3 pt-2 border-t border-border">
                  <div className="flex items-center justify-between p-2.5 rounded-lg border border-border bg-muted/20">
                    <div>
                      <div className="text-xs font-semibold">Estado Activo</div>
                      <div className="text-[10px] text-muted-foreground">Habilitado para contratación</div>
                    </div>
                    <Switch
                      checked={form.estado === 'ACTIVO'}
                      onCheckedChange={(checked) => setForm({ ...form, estado: checked ? 'ACTIVO' : 'INACTIVO' })}
                    />
                  </div>

                  <div className="flex items-center justify-between p-2.5 rounded-lg border border-border bg-muted/20">
                    <div>
                      <div className="text-xs font-semibold">Destacar en Catálogo</div>
                      <div className="text-[10px] text-muted-foreground">Badge de "Más Popular"</div>
                    </div>
                    <Switch
                      checked={form.destacado}
                      onCheckedChange={(checked) => setForm({ ...form, destacado: checked })}
                    />
                  </div>
                </div>
              </TabsContent>

              {/* TAB 2: Limits & Quotas validated to SAED boundaries */}
              <TabsContent value="limites" className="space-y-4 pt-3">
                <div className="p-3 bg-muted/40 rounded-lg border border-border text-xs space-y-1">
                  <div className="font-semibold text-foreground flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-sm text-primary">verified</span>
                    Límites de Infraestructura de SAED
                  </div>
                  <p className="text-muted-foreground text-[11px]">
                    Configure los techos máximos permitidos por plan. Puede definir una cuota numérica o marcar <strong>"Sin límite (∞)"</strong> para organizaciones con acuerdos corporativos ilimitados.
                  </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Propiedades */}
                  <div className="p-3 rounded-lg border border-border/80 space-y-2 bg-card">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="max-props" className="text-xs font-bold uppercase text-foreground flex items-center gap-1">
                        <span className="material-symbols-outlined text-xs text-primary">apartment</span>
                        Propiedades
                      </Label>
                      <label className="flex items-center gap-1.5 text-xs text-muted-foreground cursor-pointer select-none">
                        <Checkbox
                          checked={form.unlimitedPropiedades}
                          onCheckedChange={(checked) => setForm({ ...form, unlimitedPropiedades: !!checked })}
                        />
                        <span>Sin límite (∞)</span>
                      </label>
                    </div>
                    <Input
                      id="max-props"
                      type="number"
                      min="1"
                      max={SAED_LIMITS.MAX_PROPIEDADES_CEILING}
                      disabled={form.unlimitedPropiedades}
                      value={form.unlimitedPropiedades ? '' : form.maxPropiedades}
                      placeholder={form.unlimitedPropiedades ? 'Ilimitadas (∞)' : `1 a ${SAED_LIMITS.MAX_PROPIEDADES_CEILING.toLocaleString('es-CO')}`}
                      onChange={(e) => setForm({ ...form, maxPropiedades: Math.min(SAED_LIMITS.MAX_PROPIEDADES_CEILING, Math.max(1, Number(e.target.value))) })}
                      className="text-sm font-mono"
                    />
                    <div className="text-[10px] text-muted-foreground">
                      {form.unlimitedPropiedades
                        ? 'La organización puede crear copropiedades ilimitadas.'
                        : `Permite entre 1 y ${SAED_LIMITS.MAX_PROPIEDADES_CEILING.toLocaleString('es-CO')} copropiedades.`}
                    </div>
                  </div>

                  {/* Unidades */}
                  <div className="p-3 rounded-lg border border-border/80 space-y-2 bg-card">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="max-unidades" className="text-xs font-bold uppercase text-foreground flex items-center gap-1">
                        <span className="material-symbols-outlined text-xs text-primary">home</span>
                        Unidades Privadas
                      </Label>
                      <label className="flex items-center gap-1.5 text-xs text-muted-foreground cursor-pointer select-none">
                        <Checkbox
                          checked={form.unlimitedUnidades}
                          onCheckedChange={(checked) => setForm({ ...form, unlimitedUnidades: !!checked })}
                        />
                        <span>Sin límite (∞)</span>
                      </label>
                    </div>
                    <Input
                      id="max-unidades"
                      type="number"
                      min="1"
                      max={SAED_LIMITS.MAX_UNIDADES_CEILING}
                      disabled={form.unlimitedUnidades}
                      value={form.unlimitedUnidades ? '' : form.maxUnidades}
                      placeholder={form.unlimitedUnidades ? 'Ilimitadas (∞)' : `1 a ${SAED_LIMITS.MAX_UNIDADES_CEILING.toLocaleString('es-CO')}`}
                      onChange={(e) => setForm({ ...form, maxUnidades: Math.min(SAED_LIMITS.MAX_UNIDADES_CEILING, Math.max(1, Number(e.target.value))) })}
                      className="text-sm font-mono"
                    />
                    <div className="text-[10px] text-muted-foreground">
                      {form.unlimitedUnidades
                        ? 'Sin restricción en la cantidad de apartamentos o casas.'
                        : `Tope máximo de unidades privadas: ${SAED_LIMITS.MAX_UNIDADES_CEILING.toLocaleString('es-CO')}.`}
                    </div>
                  </div>

                  {/* Usuarios */}
                  <div className="p-3 rounded-lg border border-border/80 space-y-2 bg-card">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="max-usuarios" className="text-xs font-bold uppercase text-foreground flex items-center gap-1">
                        <span className="material-symbols-outlined text-xs text-primary">person</span>
                        Usuarios Registrados
                      </Label>
                      <label className="flex items-center gap-1.5 text-xs text-muted-foreground cursor-pointer select-none">
                        <Checkbox
                          checked={form.unlimitedUsuarios}
                          onCheckedChange={(checked) => setForm({ ...form, unlimitedUsuarios: !!checked })}
                        />
                        <span>Sin límite (∞)</span>
                      </label>
                    </div>
                    <Input
                      id="max-usuarios"
                      type="number"
                      min="1"
                      max={SAED_LIMITS.MAX_USUARIOS_CEILING}
                      disabled={form.unlimitedUsuarios}
                      value={form.unlimitedUsuarios ? '' : form.maxUsuarios}
                      placeholder={form.unlimitedUsuarios ? 'Ilimitados (∞)' : `1 a ${SAED_LIMITS.MAX_USUARIOS_CEILING.toLocaleString('es-CO')}`}
                      onChange={(e) => setForm({ ...form, maxUsuarios: Math.min(SAED_LIMITS.MAX_USUARIOS_CEILING, Math.max(1, Number(e.target.value))) })}
                      className="text-sm font-mono"
                    />
                    <div className="text-[10px] text-muted-foreground">
                      {form.unlimitedUsuarios
                        ? 'Usuarios y residentes sin restricción de cuenta.'
                        : `Tope máximo de cuentas en el sistema: ${SAED_LIMITS.MAX_USUARIOS_CEILING.toLocaleString('es-CO')}.`}
                    </div>
                  </div>

                  {/* Almacenamiento */}
                  <div className="p-3 rounded-lg border border-border/80 space-y-2 bg-card">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="max-almacenamiento" className="text-xs font-bold uppercase text-foreground flex items-center gap-1">
                        <span className="material-symbols-outlined text-xs text-primary">cloud</span>
                        Almacenamiento (GB)
                      </Label>
                      <label className="flex items-center gap-1.5 text-xs text-muted-foreground cursor-pointer select-none">
                        <Checkbox
                          checked={form.unlimitedAlmacenamiento}
                          onCheckedChange={(checked) => setForm({ ...form, unlimitedAlmacenamiento: !!checked })}
                        />
                        <span>Sin límite (∞)</span>
                      </label>
                    </div>
                    <Input
                      id="max-almacenamiento"
                      type="number"
                      min="1"
                      max={SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING}
                      disabled={form.unlimitedAlmacenamiento}
                      value={form.unlimitedAlmacenamiento ? '' : form.maxAlmacenamientoGb}
                      placeholder={form.unlimitedAlmacenamiento ? 'Ilimitado (∞)' : `1 a ${SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING.toLocaleString('es-CO')} GB`}
                      onChange={(e) => setForm({ ...form, maxAlmacenamientoGb: Math.min(SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING, Math.max(1, Number(e.target.value))) })}
                      className="text-sm font-mono"
                    />
                    <div className="text-[10px] text-muted-foreground">
                      {form.unlimitedAlmacenamiento
                        ? 'Espacio ilimitado en la nube para documentos y archivos.'
                        : `Tope de almacenamiento seguro: ${SAED_LIMITS.MAX_ALMACENAMIENTO_CEILING.toLocaleString('es-CO')} GB.`}
                    </div>
                  </div>
                </div>
              </TabsContent>

              {/* TAB 3: Modules & Entitlements */}
              <TabsContent value="modulos" className="space-y-3.5 pt-3">
                <div className="flex items-center justify-between gap-2 border-b border-border pb-2.5">
                  <div>
                    <div className="text-xs font-semibold">Matriz de Módulos del Sistema</div>
                    <div className="text-[11px] text-muted-foreground">
                      Marque los módulos que los administradores y residentes de este plan podrán utilizar.
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Button type="button" variant="outline" size="sm" onClick={handleSelectAllModules} className="h-7 text-[11px] px-2">
                      Todos
                    </Button>
                    <Button type="button" variant="outline" size="sm" onClick={handleSelectBasicModules} className="h-7 text-[11px] px-2">
                      Básicos
                    </Button>
                    <Button type="button" variant="ghost" size="sm" onClick={handleClearModules} className="h-7 text-[11px] px-2 text-muted-foreground">
                      Limpiar
                    </Button>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 max-h-[300px] overflow-y-auto pr-1">
                  {catalogModules.map((m) => {
                    const isSelected = form.modulos.includes(m.codigo);
                    const meta = MODULE_METADATA[m.codigo] || {
                      icon: 'extension',
                      label: m.nombre,
                      desc: m.descripcion || 'Módulo funcional de SAED.'
                    };

                    return (
                      <div
                        key={m.codigo}
                        onClick={() => toggleModule(m.codigo)}
                        className={`p-3 rounded-lg border cursor-pointer transition-all flex items-start gap-2.5 ${
                          isSelected
                            ? 'border-primary bg-primary/5 shadow-xs'
                            : 'border-border/60 hover:border-border hover:bg-muted/30 opacity-70'
                        }`}
                      >
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => toggleModule(m.codigo)}
                          className="mt-0.5"
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="material-symbols-outlined text-sm text-primary">{meta.icon}</span>
                            <span className="text-xs font-bold text-foreground truncate">{meta.label}</span>
                          </div>
                          <p className="text-[10px] text-muted-foreground mt-0.5 line-clamp-2">
                            {m.descripcion || meta.desc}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </TabsContent>
            </Tabs>

            <DialogFooter className="pt-3 border-t border-border gap-2">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)} disabled={submitting}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? (
                  <>
                    <span className="material-symbols-outlined text-sm animate-spin">progress_activity</span>
                    Guardando…
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-sm">save</span>
                    {isEditing ? 'Guardar Cambios' : 'Crear Plan SaaS'}
                  </>
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Modal Confirmar Eliminación */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle className="flex items-center gap-2 text-destructive">
              <span className="material-symbols-outlined">warning</span>
              {deleteTarget?.totalOrganizaciones > 0 || deleteTarget?.organizacionesActivas > 0
                ? 'Plan en Uso Comercial'
                : '¿Eliminar Plan Permanentemente?'}
            </AlertDialogTitle>
            <AlertDialogDescription className="text-sm space-y-2">
              {deleteTarget?.totalOrganizaciones > 0 || deleteTarget?.organizacionesActivas > 0 ? (
                <>
                  <p>
                    El plan <strong>"{deleteTarget?.nombre}"</strong> no puede ser eliminado físicamente porque registra{' '}
                    <strong className="text-foreground">{deleteTarget.totalOrganizaciones} organización(es)</strong> asociadas en el sistema.
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Para retirarlo del catálogo comercial y que no aparezca en nuevas ventas o suscripciones, desactívelo en su lugar.
                  </p>
                </>
              ) : (
                <>
                  <p>
                    Está a punto de eliminar el plan <strong>"{deleteTarget?.nombre}" ({deleteTarget?.codigo})</strong>.
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Esta acción eliminará el plan y sus configuraciones de módulos asociadas. No se puede deshacer.
                  </p>
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Cancelar</AlertDialogCancel>
            {deleteTarget?.totalOrganizaciones > 0 || deleteTarget?.organizacionesActivas > 0 ? (
              <Button
                variant="default"
                onClick={() => {
                  const target = deleteTarget;
                  setDeleteTarget(null);
                  handleToggleStatus(target);
                }}
              >
                {deleteTarget.estado === 'ACTIVO' ? 'Desactivar Plan' : 'Cerrar'}
              </Button>
            ) : (
              <AlertDialogAction
                onClick={handleDeleteConfirm}
                disabled={deleting}
                className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              >
                {deleting ? 'Eliminando…' : 'Eliminar Plan'}
              </AlertDialogAction>
            )}
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
