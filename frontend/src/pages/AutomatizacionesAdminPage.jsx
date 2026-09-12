import React, { useState, useMemo } from 'react';
import {
  Bot,
  Zap,
  Play,
  CheckCircle2,
  XCircle,
  Clock,
  Activity,
  Trash2,
  Edit,
  Plus,
  Search,
  RefreshCw,
  Copy,
  Check,
  Eye,
  Sliders,
  Bell,
  Mail,
  ShieldAlert,
  Wrench,
  QrCode,
  Globe,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { MetricCard } from '../components/ui/MetricCard';
import { Modal } from '../components/ui/Modal';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';

const ACCION_TIPOS = [
  { id: 'ENVIAR_NOTIFICACION', label: 'Enviar Notificación Push/In-App', icon: Bell, color: 'text-sky-500 bg-sky-500/10 border-sky-500/30' },
  { id: 'ENVIAR_CORREO_ADMIN', label: 'Enviar Correo al Administrador', icon: Mail, color: 'text-indigo-500 bg-indigo-500/10 border-indigo-500/30' },
  { id: 'GENERAR_MULTA', label: 'Generar Multa Automática', icon: ShieldAlert, color: 'text-amber-500 bg-amber-500/10 border-amber-500/30' },
  { id: 'CREAR_TICKET_MANTENIMIENTO', label: 'Crear Ticket de Mantenimiento', icon: Wrench, color: 'text-emerald-500 bg-emerald-500/10 border-emerald-500/30' },
  { id: 'REVOCAR_QR', label: 'Revocar Código de Acceso QR', icon: QrCode, color: 'text-rose-500 bg-rose-500/10 border-rose-500/30' },
  { id: 'GENERAR_TAREA_SLA', label: 'Generar Tarea de Alerta SLA', icon: Clock, color: 'text-purple-500 bg-purple-500/10 border-purple-500/30' },
  { id: 'WEBHOOK_EXTERNO', label: 'Disparar Webhook HTTP Externo', icon: Globe, color: 'text-cyan-500 bg-cyan-500/10 border-cyan-500/30' },
];

const ACCION_MAP = ACCION_TIPOS.reduce((acc, curr) => {
  acc[curr.id] = curr;
  return acc;
}, {});

const MODULOS_COLOR = {
  FINANZAS: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
  CONTRATOS: 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20',
  MANTENIMIENTO: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
  ACCESOS: 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20',
  PQRS: 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20',
  SEGUROS: 'bg-teal-500/10 text-teal-600 dark:text-teal-400 border-teal-500/20',
  CONSUMOS: 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 border-cyan-500/20',
  PORTERIA: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20',
};

export default function AutomatizacionesAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [activeTab, setActiveTab] = useState('reglas'); // 'reglas' | 'eventos' | 'ejecuciones'
  const [busqueda, setBusqueda] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('TODAS');
  const [filtroEvento, setFiltroEvento] = useState('TODOS');

  // Modales
  const [modalFormOpen, setModalFormOpen] = useState(false);
  const [modalSimularOpen, setModalSimularOpen] = useState(false);
  const [modalDeleteOpen, setModalDeleteOpen] = useState(false);
  const [modalLogOpen, setModalLogOpen] = useState(false);

  // Estados de trabajo
  const [reglaSeleccionada, setReglaSeleccionada] = useState(null);
  const [ejecucionSeleccionada, setEjecucionSeleccionada] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);
  const [simulacionResult, setSimulacionResult] = useState(null);
  const [copiedVar, setCopiedVar] = useState(null);

  // Formulario de Regla
  const initialFormState = {
    idRegla: null,
    idEvento: '',
    nombre: '',
    descripcion: '',
    condicionJson: '',
    estado: 'ACTIVA',
    acciones: [
      {
        tipoAccion: 'ENVIAR_NOTIFICACION',
        parametrosJson: JSON.stringify({ canal: 'PUSH', destinatario: 'RESIDENTE', mensaje: 'Notificación del sistema' }, null, 2),
        ordenEjecucion: 1,
      },
    ],
  };
  const [form, setForm] = useState(initialFormState);

  // Formulario de Simulación
  const [simulacionPayload, setSimulacionPayload] = useState('{\n  "idUnidad": 1,\n  "diasMora": 35,\n  "monto": 250000\n}');

  // Carga de datos de la API
  const { data: summary, mutate: mutateSummary } = useFetch(
    () => tenantApi.get('/automatizaciones/resumen'),
    [tenant.activeAssignmentId]
  );

  const { data: eventosRaw, isLoading: loadingEventos } = useFetch(
    () => tenantApi.get('/automatizaciones/eventos'),
    [tenant.activeAssignmentId]
  );
  const eventos = useMemo(() => (Array.isArray(eventosRaw) ? eventosRaw : []), [eventosRaw]);

  const { data: reglasRaw, isLoading: loadingReglas, mutate: mutateReglas } = useFetch(
    () => tenantApi.get('/automatizaciones/reglas'),
    [tenant.activeAssignmentId]
  );
  const reglas = useMemo(() => (Array.isArray(reglasRaw) ? reglasRaw : []), [reglasRaw]);

  const { data: ejecucionesRaw, isLoading: loadingEjecuciones, mutate: mutateEjecuciones } = useFetch(
    () => tenantApi.get('/automatizaciones/ejecuciones?limit=50'),
    [tenant.activeAssignmentId]
  );
  const ejecuciones = useMemo(() => (Array.isArray(ejecucionesRaw) ? ejecucionesRaw : []), [ejecucionesRaw]);

  // Filtrado de reglas
  const filteredReglas = useMemo(() => {
    return reglas.filter((r) => {
      const matchBusqueda =
        !busqueda ||
        r.nombre?.toLowerCase().includes(busqueda.toLowerCase()) ||
        r.descripcion?.toLowerCase().includes(busqueda.toLowerCase()) ||
        r.codigoEvento?.toLowerCase().includes(busqueda.toLowerCase());

      const matchEstado = filtroEstado === 'TODAS' || r.estado === filtroEstado;
      const matchEvento = filtroEvento === 'TODOS' || String(r.idEvento) === String(filtroEvento);

      return matchBusqueda && matchEstado && matchEvento;
    });
  }, [reglas, busqueda, filtroEstado, filtroEvento]);

  // Manejo de Acciones en Formulario
  const handleAddAccion = () => {
    setForm((prev) => ({
      ...prev,
      acciones: [
        ...prev.acciones,
        {
          tipoAccion: 'ENVIAR_CORREO_ADMIN',
          parametrosJson: JSON.stringify({ asunto: 'Alerta de automatización', destinatario: 'admin@saed.com' }, null, 2),
          ordenEjecucion: prev.acciones.length + 1,
        },
      ],
    }));
  };

  const handleRemoveAccion = (index) => {
    setForm((prev) => {
      const updated = prev.acciones.filter((_, i) => i !== index).map((acc, i) => ({
        ...acc,
        ordenEjecucion: i + 1,
      }));
      return { ...prev, acciones: updated };
    });
  };

  const handleUpdateAccion = (index, field, value) => {
    setForm((prev) => {
      const updated = [...prev.acciones];
      updated[index] = { ...updated[index], [field]: value };
      return { ...prev, acciones: updated };
    });
  };

  // Abrir Modal para Crear
  const handleOpenCreate = (eventoPreseleccionado = null) => {
    setForm({
      ...initialFormState,
      idEvento: eventoPreseleccionado ? eventoPreseleccionado.idEvento : (eventos[0]?.idEvento || ''),
    });
    setModalFormOpen(true);
  };

  // Abrir Modal para Editar
  const handleOpenEdit = (regla) => {
    setForm({
      idRegla: regla.idRegla,
      idEvento: regla.idEvento,
      nombre: regla.nombre,
      descripcion: regla.descripcion || '',
      condicionJson: regla.condicionJson || '',
      estado: regla.estado,
      acciones: (regla.acciones && regla.acciones.length > 0)
        ? regla.acciones.map((a, i) => ({
            tipoAccion: a.tipoAccion,
            parametrosJson: a.parametrosJson || '{}',
            ordenEjecucion: a.ordenEjecucion || i + 1,
          }))
        : [
            {
              tipoAccion: 'ENVIAR_NOTIFICACION',
              parametrosJson: JSON.stringify({ canal: 'PUSH', mensaje: 'Notificación del sistema' }, null, 2),
              ordenEjecucion: 1,
            },
          ],
    });
    setModalFormOpen(true);
  };

  // Guardar Regla (Crear o Actualizar)
  const handleSubmitRegla = async (e) => {
    e.preventDefault();
    if (!form.nombre.trim()) {
      toast.error('El nombre de la regla es obligatorio');
      return;
    }
    if (!form.idEvento) {
      toast.error('Debe seleccionar un evento disparador');
      return;
    }
    if (!form.acciones || form.acciones.length === 0) {
      toast.error('Debe configurar al menos una acción para la regla');
      return;
    }

    // Validar JSON de condición si se ingresó
    if (form.condicionJson && form.condicionJson.trim()) {
      try {
        JSON.parse(form.condicionJson);
      } catch (err) {
        toast.error('El JSON de condición no tiene un formato válido');
        return;
      }
    }

    // Validar JSON de cada acción
    for (let i = 0; i < form.acciones.length; i++) {
      try {
        JSON.parse(form.acciones[i].parametrosJson || '{}');
      } catch (err) {
        toast.error(`Los parámetros de la acción #${i + 1} no tienen un formato JSON válido`);
        return;
      }
    }

    setIsSubmitting(true);
    try {
      const payload = {
        idEvento: Number(form.idEvento),
        nombre: form.nombre.trim(),
        descripcion: form.descripcion.trim(),
        condicionJson: form.condicionJson.trim() || null,
        estado: form.estado,
        acciones: form.acciones.map((a, i) => ({
          tipoAccion: a.tipoAccion,
          parametrosJson: a.parametrosJson.trim(),
          ordenEjecucion: i + 1,
        })),
      };

      if (form.idRegla) {
        await tenantApi.put(`/automatizaciones/reglas/${form.idRegla}`, payload);
        toast.success('Regla de automatización actualizada exitosamente');
      } else {
        await tenantApi.post('/automatizaciones/reglas', payload);
        toast.success('Regla de automatización creada exitosamente');
      }

      setModalFormOpen(false);
      mutateReglas();
      mutateSummary();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || 'Error al guardar la regla de automatización');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Alternar Estado de Regla (Toggle ACTIVA / INACTIVA)
  const handleToggleEstado = async (regla) => {
    try {
      await tenantApi.patch(`/automatizaciones/reglas/${regla.idRegla}/toggle-estado`);
      toast.success(`Regla "${regla.nombre}" ahora está ${regla.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA'}`);
      mutateReglas();
      mutateSummary();
    } catch (err) {
      console.error(err);
      toast.error('Error al cambiar el estado de la regla');
    }
  };

  // Eliminar Regla
  const handleDeleteRegla = async () => {
    if (!reglaSeleccionada) return;
    setIsSubmitting(true);
    try {
      await tenantApi.delete(`/automatizaciones/reglas/${reglaSeleccionada.idRegla}`);
      toast.success('Regla eliminada o desactivada exitosamente para preservar trazabilidad');
      setModalDeleteOpen(false);
      setReglaSeleccionada(null);
      mutateReglas();
      mutateSummary();
    } catch (err) {
      console.error(err);
      toast.error('Error al eliminar la regla');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Abrir Modal de Simulación
  const handleOpenSimular = (regla) => {
    setReglaSeleccionada(regla);
    setSimulacionResult(null);

    // Preparar payload de ejemplo según evento
    const variables = regla.variablesPayload ? regla.variablesPayload.split(',') : [];
    const dummyObj = {};
    variables.forEach((v) => {
      const key = v.trim();
      if (key.startsWith('id')) dummyObj[key] = 101;
      else if (key.includes('monto') || key.includes('tarifa')) dummyObj[key] = 250000;
      else if (key.includes('dias') || key.includes('tiempo')) dummyObj[key] = 30;
      else dummyObj[key] = 'Valor de prueba';
    });

    setSimulacionPayload(JSON.stringify(dummyObj, null, 2));
    setModalSimularOpen(true);
  };

  // Ejecutar Simulación en Vivo
  const handleExecuteSimulacion = async () => {
    if (!reglaSeleccionada) return;
    try {
      JSON.parse(simulacionPayload);
    } catch (err) {
      toast.error('El payload de prueba debe ser un JSON válido');
      return;
    }

    setIsSimulating(true);
    try {
      const res = await tenantApi.post(`/automatizaciones/reglas/${reglaSeleccionada.idRegla}/simular`, {
        idEntidadOrigen: 101,
        tipoEntidadOrigen: 'SIMULACION_MANUAL',
        payloadJson: simulacionPayload,
      });

      setSimulacionResult(res);
      toast.success(`Simulación completada con resultado: ${res.resultado}`);
      mutateEjecuciones();
      mutateSummary();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || 'Error durante la simulación de la regla');
    } finally {
      setIsSimulating(false);
    }
  };

  // Copiar variables de payload
  const handleCopyVar = (text) => {
    navigator.clipboard.writeText(text);
    setCopiedVar(text);
    toast.success('Variables copiadas al portapapeles');
    setTimeout(() => setCopiedVar(null), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Encabezado Principal */}
      <PageHeader
        title="Motor de Automatizaciones ECA"
        description="Configure flujos reactivos inteligentes basados en arquitectura Evento -> Condición -> Acción para optimizar la operación de la copropiedad."
      >
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            onClick={() => {
              mutateSummary();
              mutateReglas();
              mutateEjecuciones();
            }}
            className="gap-2"
          >
            <RefreshCw className="w-4 h-4" />
            Actualizar
          </Button>
          <Button onClick={() => handleOpenCreate()} className="gap-2 bg-primary text-primary-foreground">
            <Plus className="w-4 h-4" />
            Nueva Regla
          </Button>
        </div>
      </PageHeader>

      {/* Tarjetas de Métricas / KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          title="Reglas Activas"
          value={summary ? `${summary.reglasActivas || 0} / ${summary.totalReglas || 0}` : '0 / 0'}
          subtitle={`${summary?.reglasInactivas || 0} en pausa`}
          icon={Bot}
          trend={summary?.totalReglas > 0 ? '+Activo' : null}
          trendDirection="up"
        />
        <MetricCard
          title="Catálogo de Eventos"
          value={summary?.totalEventos || eventos.length || 8}
          subtitle="Eventos del sistema monitoreados"
          icon={Zap}
        />
        <MetricCard
          title="Ejecuciones este Mes"
          value={summary?.totalEjecucionesMes || 0}
          subtitle="Disparos automáticos evaluados"
          icon={Activity}
        />
        <MetricCard
          title="Tasa de Efectividad"
          value={summary?.tasaExito != null ? `${summary.tasaExito.toFixed(1)}%` : '100%'}
          subtitle={`${summary?.ejecucionesExitosas || 0} exitosas, ${summary?.ejecucionesFallidas || 0} fallidas`}
          icon={CheckCircle2}
          trend={summary?.tasaExito >= 90 ? 'Excelente' : 'Revisar'}
          trendDirection={summary?.tasaExito >= 90 ? 'up' : 'down'}
        />
      </div>

      {/* Selector de Pestañas de Navegación */}
      <div className="flex border-b border-border space-x-6">
        <button
          onClick={() => setActiveTab('reglas')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 flex items-center gap-2 ${
            activeTab === 'reglas'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Sliders className="w-4 h-4" />
          Reglas de Automatización ({filteredReglas.length})
        </button>
        <button
          onClick={() => setActiveTab('eventos')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 flex items-center gap-2 ${
            activeTab === 'eventos'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Zap className="w-4 h-4" />
          Catálogo de Eventos ({eventos.length})
        </button>
        <button
          onClick={() => setActiveTab('ejecuciones')}
          className={`pb-3 font-medium text-sm transition-colors border-b-2 flex items-center gap-2 ${
            activeTab === 'ejecuciones'
              ? 'border-primary text-primary font-semibold'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Clock className="w-4 h-4" />
          Historial de Ejecuciones ({ejecuciones.length})
        </button>
      </div>

      {/* PESTAÑA 1: REGLAS DE AUTOMATIZACIÓN */}
      {activeTab === 'reglas' && (
        <div className="space-y-4">
          {/* Barra de Filtros */}
          <div className="flex flex-col sm:flex-row gap-3 items-center justify-between bg-card p-3 rounded-xl border border-border">
            <div className="relative w-full sm:w-80">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Buscar por regla, evento o acción..."
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                className="pl-9 h-9"
              />
            </div>
            <div className="flex items-center gap-3 w-full sm:w-auto">
              <div className="flex items-center gap-2">
                <Label className="text-xs text-muted-foreground whitespace-nowrap">Estado:</Label>
                <select
                  value={filtroEstado}
                  onChange={(e) => setFiltroEstado(e.target.value)}
                  className="bg-background border border-input rounded-md px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-primary"
                >
                  <option value="TODAS">Todos los Estados</option>
                  <option value="ACTIVA">Activas</option>
                  <option value="INACTIVA">Inactivas</option>
                </select>
              </div>

              <div className="flex items-center gap-2">
                <Label className="text-xs text-muted-foreground whitespace-nowrap">Evento:</Label>
                <select
                  value={filtroEvento}
                  onChange={(e) => setFiltroEvento(e.target.value)}
                  className="bg-background border border-input rounded-md px-2 py-1 text-xs focus:outline-none focus:ring-1 focus:ring-primary max-w-[180px]"
                >
                  <option value="TODOS">Todos los Eventos</option>
                  {eventos.map((ev) => (
                    <option key={ev.idEvento} value={ev.idEvento}>
                      {ev.codigo}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Listado de Reglas */}
          {loadingReglas ? (
            <div className="py-12 text-center text-muted-foreground">Cargando reglas de automatización...</div>
          ) : filteredReglas.length === 0 ? (
            <div className="p-8 text-center bg-card rounded-xl border border-dashed border-border space-y-3">
              <Bot className="w-12 h-12 text-muted-foreground mx-auto stroke-1" />
              <div className="text-base font-medium">No se encontraron reglas de automatización</div>
              <p className="text-sm text-muted-foreground max-w-md mx-auto">
                Cree su primera regla reactiva seleccionando un evento disparador, condiciones y una secuencia de acciones.
              </p>
              <Button onClick={() => handleOpenCreate()} className="gap-2">
                <Plus className="w-4 h-4" />
                Crear Regla Ahora
              </Button>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {filteredReglas.map((regla) => {
                const badgeColor = MODULOS_COLOR[regla.moduloOrigen] || 'bg-muted text-muted-foreground';
                const isActiva = regla.estado === 'ACTIVA';

                return (
                  <div
                    key={regla.idRegla}
                    className={`bg-card rounded-xl border transition-all p-5 space-y-4 ${
                      isActiva ? 'border-border shadow-sm' : 'border-border/60 opacity-75 bg-muted/20'
                    }`}
                  >
                    {/* Header de la Tarjeta */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/50 pb-3">
                      <div className="flex items-start gap-3">
                        <div
                          className={`p-2 rounded-lg mt-0.5 ${
                            isActiva ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'
                          }`}
                        >
                          <Zap className="w-5 h-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <h3 className="font-semibold text-base text-foreground">{regla.nombre}</h3>
                            <span className={`text-[11px] font-medium px-2 py-0.5 rounded-full border ${badgeColor}`}>
                              {regla.codigoEvento}
                            </span>
                            <Badge variant={isActiva ? 'default' : 'secondary'} className="text-[10px]">
                              {isActiva ? 'ACTIVA' : 'INACTIVA'}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            {regla.descripcion || 'Sin descripción adicional'}
                          </p>
                        </div>
                      </div>

                      {/* Botones de Control */}
                      <div className="flex items-center gap-2 self-end sm:self-center">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleToggleEstado(regla)}
                          className="h-8 text-xs gap-1.5"
                          title={isActiva ? 'Pausar regla' : 'Activar regla'}
                        >
                          <span
                            className={`w-2 h-2 rounded-full ${
                              isActiva ? 'bg-emerald-500' : 'bg-zinc-400'
                            }`}
                          />
                          {isActiva ? 'Desactivar' : 'Activar'}
                        </Button>

                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => handleOpenSimular(regla)}
                          className="h-8 text-xs gap-1.5"
                        >
                          <Play className="w-3.5 h-3.5 text-primary" />
                          Simular
                        </Button>

                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleOpenEdit(regla)}
                          className="h-8 w-8 p-0"
                          title="Editar regla"
                        >
                          <Edit className="w-3.5 h-3.5" />
                        </Button>

                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setReglaSeleccionada(regla);
                            setModalDeleteOpen(true);
                          }}
                          className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                          title="Eliminar regla"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </Button>
                      </div>
                    </div>

                    {/* Cadena ECA (Evento -> Condición -> Acciones) */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                      {/* 1. Disparador */}
                      <div className="bg-muted/40 p-3 rounded-lg border border-border/40 space-y-1.5">
                        <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
                          <Zap className="w-3.5 h-3.5 text-amber-500" />
                          1. Evento Disparador
                        </div>
                        <div className="font-medium text-foreground">{regla.nombreEvento || regla.codigoEvento}</div>
                        <div className="text-[11px] text-muted-foreground">
                          Módulo: <span className="font-medium">{regla.moduloOrigen || 'SISTEMA'}</span>
                        </div>
                      </div>

                      {/* 2. Condición */}
                      <div className="bg-muted/40 p-3 rounded-lg border border-border/40 space-y-1.5">
                        <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
                          <Sliders className="w-3.5 h-3.5 text-sky-500" />
                          2. Condición Lógica
                        </div>
                        {regla.condicionJson ? (
                          <pre className="font-mono text-[10px] bg-background p-1.5 rounded border border-border overflow-x-auto text-foreground">
                            {regla.condicionJson}
                          </pre>
                        ) : (
                          <div className="text-muted-foreground italic flex items-center gap-1 text-[11px]">
                            <Check className="w-3 h-3 text-emerald-500" /> Siempre se ejecuta al disparar
                          </div>
                        )}
                      </div>

                      {/* 3. Acciones */}
                      <div className="bg-muted/40 p-3 rounded-lg border border-border/40 space-y-1.5">
                        <div className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
                          <Activity className="w-3.5 h-3.5 text-emerald-500" />
                          3. Acciones Secuenciales ({regla.acciones?.length || 0})
                        </div>
                        <div className="space-y-1">
                          {regla.acciones && regla.acciones.length > 0 ? (
                            regla.acciones.map((acc, idx) => {
                              const conf = ACCION_MAP[acc.tipoAccion] || { label: acc.tipoAccion, color: 'text-zinc-500 bg-zinc-500/10' };
                              return (
                                <div key={idx} className="flex items-center gap-1.5">
                                  <span className="w-4 h-4 rounded-full bg-background border text-[10px] flex items-center justify-center font-bold">
                                    {acc.ordenEjecucion || idx + 1}
                                  </span>
                                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium border ${conf.color}`}>
                                    {conf.label}
                                  </span>
                                </div>
                              );
                            })
                          ) : (
                            <span className="text-muted-foreground italic text-[11px]">Sin acciones asociadas</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* PESTAÑA 2: CATÁLOGO DE EVENTOS */}
      {activeTab === 'eventos' && (
        <div className="space-y-4">
          <div className="bg-card p-4 rounded-xl border border-border flex items-center justify-between">
            <div className="space-y-1">
              <h3 className="font-semibold text-sm">Eventos Nativos del Sistema SAED</h3>
              <p className="text-xs text-muted-foreground">
                Estos eventos son emitidos automáticamente por los módulos operativos (Finanzas, Contratos, Mantenimiento, Accesos, PQRS, Consumos, Portería).
              </p>
            </div>
            <Badge variant="outline" className="text-xs">
              {eventos.length} Eventos Disponibles
            </Badge>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {eventos.map((ev) => {
              const badgeColor = MODULOS_COLOR[ev.moduloOrigen] || 'bg-muted text-muted-foreground';
              return (
                <div key={ev.idEvento} className="bg-card rounded-xl border border-border p-4 space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <div className="p-2 rounded-lg bg-amber-500/10 text-amber-500">
                        <Zap className="w-4 h-4" />
                      </div>
                      <div>
                        <h4 className="font-semibold text-sm text-foreground">{ev.nombre}</h4>
                        <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${badgeColor}`}>
                          {ev.codigo}
                        </span>
                      </div>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenCreate(ev)}
                      className="h-7 text-xs gap-1"
                    >
                      <Plus className="w-3 h-3" />
                      Crear Regla
                    </Button>
                  </div>

                  <p className="text-xs text-muted-foreground leading-relaxed">{ev.descripcion}</p>

                  {/* Variables disponibles en Payload */}
                  <div className="bg-muted/40 p-2.5 rounded-lg border border-border/40 space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">
                        Variables de Payload Disponibles:
                      </span>
                      <button
                        onClick={() => handleCopyVar(ev.variablesPayload)}
                        className="text-[10px] text-primary hover:underline flex items-center gap-1"
                        title="Copiar variables"
                      >
                        {copiedVar === ev.variablesPayload ? (
                          <>
                            <Check className="w-3 h-3 text-emerald-500" /> Copiado
                          </>
                        ) : (
                          <>
                            <Copy className="w-3 h-3" /> Copiar
                          </>
                        )}
                      </button>
                    </div>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {ev.variablesPayload ? (
                        ev.variablesPayload.split(',').map((v, i) => (
                          <span
                            key={i}
                            className="font-mono text-[10px] bg-background px-1.5 py-0.5 rounded border border-border text-foreground"
                          >
                            payload.{v.trim()}
                          </span>
                        ))
                      ) : (
                        <span className="text-muted-foreground text-[10px] italic">Sin variables adicionales</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* PESTAÑA 3: HISTORIAL DE EJECUCIONES (AUDIT LOG) */}
      {activeTab === 'ejecuciones' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between bg-card p-3 rounded-xl border border-border">
            <div className="space-y-0.5">
              <h3 className="font-semibold text-sm">Registro Inmutable de Ejecuciones</h3>
              <p className="text-xs text-muted-foreground">
                Trazabilidad append-only de cada regla evaluada y ejecutada en tiempo real.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => mutateEjecuciones()}
              className="gap-1.5 h-8 text-xs"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Recargar Historial
            </Button>
          </div>

          {loadingEjecuciones ? (
            <div className="py-12 text-center text-muted-foreground">Cargando historial de auditoría...</div>
          ) : ejecuciones.length === 0 ? (
            <div className="p-8 text-center bg-card rounded-xl border border-dashed border-border space-y-2">
              <Clock className="w-10 h-10 text-muted-foreground mx-auto stroke-1" />
              <div className="text-sm font-medium">Aún no hay ejecuciones registradas</div>
              <p className="text-xs text-muted-foreground">
                Dispare una simulación manual o espere a que un evento operativo del sistema active una regla.
              </p>
            </div>
          ) : (
            <div className="bg-card rounded-xl border border-border overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-border bg-muted/40 font-medium text-muted-foreground">
                      <th className="p-3">Fecha / Hora</th>
                      <th className="p-3">Regla Disparada</th>
                      <th className="p-3">Evento</th>
                      <th className="p-3">Entidad Afectada</th>
                      <th className="p-3 text-center">Resultado</th>
                      <th className="p-3 text-right">Latencia</th>
                      <th className="p-3 text-center">Acción</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {ejecuciones.map((ej) => {
                      const isExitosa = ej.resultado === 'EXITOSA';
                      return (
                        <tr key={ej.idEjecucion} className="hover:bg-muted/20 transition-colors">
                          <td className="p-3 font-mono text-[11px] whitespace-nowrap">
                            {ej.fechaEjecucion
                              ? new Date(ej.fechaEjecucion).toLocaleString('es-CO', {
                                  dateStyle: 'short',
                                  timeStyle: 'medium',
                                })
                              : '-'}
                          </td>
                          <td className="p-3 font-medium text-foreground">
                            {ej.nombreRegla || `Regla #${ej.idRegla}`}
                          </td>
                          <td className="p-3">
                            <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-muted border border-border">
                              {ej.codigoEvento || 'N/A'}
                            </span>
                          </td>
                          <td className="p-3 text-muted-foreground">
                            {ej.tipoEntidadOrigen ? `${ej.tipoEntidadOrigen} #${ej.idEntidadOrigen || ''}` : '-'}
                          </td>
                          <td className="p-3 text-center">
                            <span
                              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                                isExitosa
                                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                                  : 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20'
                              }`}
                            >
                              {isExitosa ? <CheckCircle2 className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
                              {ej.resultado}
                            </span>
                          </td>
                          <td className="p-3 text-right font-mono text-[11px]">
                            {ej.tiempoMs != null ? `${ej.tiempoMs} ms` : '-'}
                          </td>
                          <td className="p-3 text-center">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setEjecucionSeleccionada(ej);
                                setModalLogOpen(true);
                              }}
                              className="h-7 text-xs gap-1"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              Log
                            </Button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* MODAL: CREAR / EDITAR REGLA */}
      <Modal
        open={modalFormOpen}
        onClose={() => !isSubmitting && setModalFormOpen(false)}
        title={form.idRegla ? 'Editar Regla de Automatización' : 'Nueva Regla de Automatización'}
        description="Configure la cadena reactiva ECA (Evento disparador, condición lógica y acciones automáticas)."
        maxWidth="max-w-2xl"
      >
        <form onSubmit={handleSubmitRegla} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Evento */}
            <div className="space-y-1.5 sm:col-span-2">
              <Label className="text-xs font-semibold">Evento Disparador (Trigger) *</Label>
              <select
                value={form.idEvento}
                onChange={(e) => setForm({ ...form, idEvento: e.target.value })}
                className="w-full bg-background border border-input rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                required
              >
                <option value="">Seleccione un evento del sistema...</option>
                {eventos.map((ev) => (
                  <option key={ev.idEvento} value={ev.idEvento}>
                    [{ev.moduloOrigen}] {ev.codigo} — {ev.nombre}
                  </option>
                ))}
              </select>
              {form.idEvento && (
                <p className="text-[11px] text-muted-foreground">
                  {eventos.find((ev) => String(ev.idEvento) === String(form.idEvento))?.descripcion}
                </p>
              )}
            </div>

            {/* Nombre */}
            <div className="space-y-1.5 sm:col-span-2">
              <Label className="text-xs font-semibold">Nombre de la Regla *</Label>
              <Input
                placeholder="Ej. Notificar mora y generar multa a 30 días"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                required
              />
            </div>

            {/* Descripción */}
            <div className="space-y-1.5 sm:col-span-2">
              <Label className="text-xs font-semibold">Descripción</Label>
              <Input
                placeholder="Objetivo operativo de la automatización..."
                value={form.descripcion}
                onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
              />
            </div>

            {/* Estado */}
            <div className="space-y-1.5">
              <Label className="text-xs font-semibold">Estado Inicial</Label>
              <select
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                className="w-full bg-background border border-input rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="ACTIVA">ACTIVA (Monitoreando eventos)</option>
                <option value="INACTIVA">INACTIVA (Pausada)</option>
              </select>
            </div>

            {/* Preset de Condición */}
            <div className="space-y-1.5">
              <Label className="text-xs font-semibold">Plantilla de Condición</Label>
              <select
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === 'SIEMPRE') setForm({ ...form, condicionJson: '' });
                  if (val === 'MORA_30') setForm({ ...form, condicionJson: '{\n  "campo": "diasMora",\n  "operador": ">",\n  "valor": 30\n}' });
                  if (val === 'MONTO_ALTO') setForm({ ...form, condicionJson: '{\n  "campo": "monto",\n  "operador": ">",\n  "valor": 500000\n}' });
                  if (val === 'DIAS_RESTANTES_7') setForm({ ...form, condicionJson: '{\n  "campo": "diasRestantes",\n  "operador": "<=",\n  "valor": 7\n}' });
                }}
                className="w-full bg-background border border-input rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="PERSONALIZADA">Condición Personalizada</option>
                <option value="SIEMPRE">Siempre Ejecutar (Sin condición)</option>
                <option value="MORA_30">Días de mora &gt; 30</option>
                <option value="MONTO_ALTO">Monto &gt; $500.000</option>
                <option value="DIAS_RESTANTES_7">Días restantes &lt;= 7</option>
              </select>
            </div>

            {/* Editor de Condición JSON */}
            <div className="space-y-1.5 sm:col-span-2">
              <Label className="text-xs font-semibold">Condición Lógica (JSON o vacío para Siempre)</Label>
              <textarea
                rows={3}
                value={form.condicionJson}
                onChange={(e) => setForm({ ...form, condicionJson: e.target.value })}
                placeholder='Opcional: {"campo": "diasMora", "operador": ">", "valor": 30}'
                className="w-full font-mono text-xs bg-background border border-input rounded-md p-2 focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
          </div>

          {/* Constructor de Acciones */}
          <div className="space-y-3 pt-2 border-t border-border">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-bold uppercase tracking-wider text-foreground">
                Acciones Secuenciales a Ejecutar
              </Label>
              <Button type="button" variant="outline" size="sm" onClick={handleAddAccion} className="h-7 text-xs gap-1">
                <Plus className="w-3 h-3" /> Agregar Acción
              </Button>
            </div>

            <div className="space-y-3">
              {form.acciones.map((acc, index) => (
                <div key={index} className="p-3 bg-muted/40 rounded-lg border border-border/80 space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-muted-foreground flex items-center gap-1">
                      <span className="w-4 h-4 rounded-full bg-background border text-[10px] flex items-center justify-center font-bold">
                        {index + 1}
                      </span>
                      Paso {index + 1}
                    </span>
                    {form.acciones.length > 1 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveAccion(index)}
                        className="text-xs text-destructive hover:underline flex items-center gap-0.5"
                      >
                        <Trash2 className="w-3 h-3" /> Quitar
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div>
                      <Label className="text-[11px]">Tipo de Acción</Label>
                      <select
                        value={acc.tipoAccion}
                        onChange={(e) => handleUpdateAccion(index, 'tipoAccion', e.target.value)}
                        className="w-full bg-background border border-input rounded-md px-2 py-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-primary"
                      >
                        {ACCION_TIPOS.map((t) => (
                          <option key={t.id} value={t.id}>
                            {t.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <Label className="text-[11px]">Parámetros (JSON)</Label>
                      <textarea
                        rows={2}
                        value={acc.parametrosJson}
                        onChange={(e) => handleUpdateAccion(index, 'parametrosJson', e.target.value)}
                        className="w-full font-mono text-[11px] bg-background border border-input rounded-md p-1.5 focus:outline-none focus:ring-1 focus:ring-primary"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setModalFormOpen(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : form.idRegla ? 'Guardar Cambios' : 'Crear Regla'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL: SIMULACIÓN DE DISPARO */}
      <Modal
        open={modalSimularOpen}
        onClose={() => setModalSimularOpen(false)}
        title="Simulador de Regla en Vivo"
        description={`Ejecute la regla "${reglaSeleccionada?.nombre}" con un payload sintético para validar la condición y las acciones sin alterar datos reales.`}
        maxWidth="max-w-xl"
      >
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label className="text-xs font-semibold">Payload de Prueba (JSON)</Label>
            <textarea
              rows={5}
              value={simulacionPayload}
              onChange={(e) => setSimulacionPayload(e.target.value)}
              className="w-full font-mono text-xs bg-background border border-input rounded-md p-2.5 focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          <div className="flex justify-end">
            <Button
              onClick={handleExecuteSimulacion}
              disabled={isSimulating}
              className="gap-2 bg-primary text-primary-foreground"
            >
              <Play className="w-4 h-4" />
              {isSimulating ? 'Simulando ejecución...' : 'Ejecutar Simulación Ahora'}
            </Button>
          </div>

          {/* Resultado de la simulación */}
          {simulacionResult && (
            <div className="mt-4 p-4 rounded-xl border bg-muted/30 space-y-2.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-foreground flex items-center gap-1.5">
                  <Activity className="w-4 h-4 text-primary" />
                  Resultado de la Evaluación
                </span>
                <span
                  className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                    simulacionResult.resultado === 'EXITOSA'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                      : 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20'
                  }`}
                >
                  {simulacionResult.resultado}
                </span>
              </div>

              <div className="text-xs text-muted-foreground">
                Latencia de motor: <span className="font-mono font-medium text-foreground">{simulacionResult.tiempoMs} ms</span>
              </div>

              <div className="space-y-1">
                <Label className="text-[11px] font-semibold text-muted-foreground">Traza de Auditoría:</Label>
                <pre className="font-mono text-[11px] bg-background p-2.5 rounded border border-border overflow-x-auto text-foreground">
                  {simulacionResult.logDetalle}
                </pre>
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* MODAL: VER LOG DE AUDITORÍA */}
      <Modal
        open={modalLogOpen}
        onClose={() => setModalLogOpen(false)}
        title="Detalle de Ejecución de Automatización"
        description={`Registro # ${ejecucionSeleccionada?.idEjecucion} capturado en el historial inmutable.`}
        maxWidth="max-w-lg"
      >
        {ejecucionSeleccionada && (
          <div className="space-y-3 text-xs">
            <div className="grid grid-cols-2 gap-2 bg-muted/40 p-3 rounded-lg border border-border/50">
              <div>
                <span className="text-muted-foreground">Regla:</span>
                <p className="font-medium text-foreground">{ejecucionSeleccionada.nombreRegla}</p>
              </div>
              <div>
                <span className="text-muted-foreground">Evento Disparador:</span>
                <p className="font-mono text-[11px] font-medium text-foreground">{ejecucionSeleccionada.codigoEvento}</p>
              </div>
              <div>
                <span className="text-muted-foreground">Resultado:</span>
                <p className="font-bold text-foreground">{ejecucionSeleccionada.resultado}</p>
              </div>
              <div>
                <span className="text-muted-foreground">Tiempo de respuesta:</span>
                <p className="font-mono text-[11px] font-medium text-foreground">{ejecucionSeleccionada.tiempoMs} ms</p>
              </div>
            </div>

            <div className="space-y-1">
              <Label className="text-xs font-semibold">Detalle y Traza de Ejecución</Label>
              <pre className="font-mono text-[11px] bg-background p-3 rounded border border-border overflow-x-auto text-foreground whitespace-pre-wrap">
                {ejecucionSeleccionada.logDetalle}
              </pre>
            </div>

            <div className="flex justify-end pt-2">
              <Button variant="outline" onClick={() => setModalLogOpen(false)}>
                Cerrar
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* MODAL: ELIMINAR REGLA */}
      <Modal
        open={modalDeleteOpen}
        onClose={() => !isSubmitting && setModalDeleteOpen(false)}
        title="¿Eliminar Regla de Automatización?"
        description={`¿Está seguro de que desea eliminar la regla "${reglaSeleccionada?.nombre}"?`}
        maxWidth="max-w-md"
      >
        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">
            Si la regla posee ejecuciones históricas registradas, por política de auditoría inmutable no se destruirá, sino que pasará de inmediato a estado <strong>INACTIVA</strong>.
          </p>
          <div className="flex justify-end gap-3">
            <Button
              variant="outline"
              onClick={() => setModalDeleteOpen(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteRegla}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Eliminando...' : 'Confirmar Eliminación'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
