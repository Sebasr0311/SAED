import { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  Bell,
  Megaphone,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  Search,
  Filter,
  Plus,
  ArrowRight,
  Eye,
  CheckCheck,
  Building2,
  User,
  Clock,
  Send,
  Trash2,
  AlertCircle,
  MessageSquare,
  ShieldCheck,
  Calendar,
  X,
} from 'lucide-react';

import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDateTime } from '../lib/utils.js';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardContent, CardHeader } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input, Textarea, Select } from '../components/ui/Form.jsx';

const PAGE_SIZE = 12;

function getPrioridadBadge(prioridad) {
  const p = (prioridad || 'NORMAL').toUpperCase();
  switch (p) {
    case 'URGENTE':
      return (
        <Badge variant="outline" className="bg-destructive/10 text-destructive border-destructive/20 text-[10px] font-bold uppercase">
          Urgente
        </Badge>
      );
    case 'IMPORTANTE':
    case 'ALTA':
      return (
        <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800 text-[10px] font-semibold uppercase">
          Importante
        </Badge>
      );
    case 'BAJA':
      return (
        <Badge variant="outline" className="bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-400 text-[10px]">
          Baja
        </Badge>
      );
    default:
      return (
        <Badge variant="outline" className="bg-muted text-muted-foreground text-[10px]">
          Normal
        </Badge>
      );
  }
}

export default function ComunicacionesPage({ initialTab = 'todos' }) {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const { user } = useAuth();
  const navigate = useNavigate();

  // Estados de navegación y filtros
  const [activeTab, setActiveTab] = useState(initialTab); // 'todos' | 'avisos' | 'alertas' | 'notificaciones'
  const [search, setSearch] = useState('');
  const [soloPendientes, setSoloPendientes] = useState(false);
  const [soloPrioritarias, setSoloPrioritarias] = useState(false);
  const [page, setPage] = useState(0);

  // Modales
  const [modalAvisoOpen, setModalAvisoOpen] = useState(false);
  const [modalAlertaOpen, setModalAlertaOpen] = useState(false);
  const [detalleItem, setDetalleItem] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Formularios
  const [avisoForm, setAvisoForm] = useState({
    titulo: '',
    mensaje: '',
    prioridad: 'NORMAL',
    tipoSegmentacion: 'TODOS',
    enviarEmail: true,
  });

  const [alertaForm, setAlertaForm] = useState({
    tipoAlerta: 'MORA_CUOTA',
    numeroApartamento: '',
    nombreResidente: '',
    mensaje: '',
  });

  // 1. Carga de Avisos y Circulares
  const {
    data: avisosRaw,
    loading: loadingAvisos,
    error: errorAvisos,
    refetch: refetchAvisos,
  } = useFetch(() => tenantApi.get('/buzon/avisos'), [tenant.activeAssignmentId]);

  // 2. Carga de Alertas Operativas
  const {
    data: alertasRaw,
    loading: loadingAlertas,
    error: errorAlertas,
    refetch: refetchAlertas,
  } = useFetch(() => tenantApi.get('/alertas'), [tenant.activeAssignmentId]);

  // 3. Carga de Notificaciones del Buzón
  const {
    data: notifRaw,
    loading: loadingNotif,
    error: errorNotif,
    refetch: refetchNotif,
  } = useFetch(() => tenantApi.get('/buzon'), [tenant.activeAssignmentId]);

  // 4. Unidades habitacionales para asignación de alertas
  const { data: unidadesRaw } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);
  const unidades = useMemo(() => {
    return Array.isArray(unidadesRaw) ? unidadesRaw : unidadesRaw?.items || [];
  }, [unidadesRaw]);

  // Refresco unificado
  const [refreshing, setRefreshing] = useState(false);
  const handleRefetchAll = useCallback(() => {
    setRefreshing(true);
    Promise.allSettled([refetchAvisos(), refetchAlertas(), refetchNotif()]).finally(() => {
      setTimeout(() => setRefreshing(false), 400);
      toast.success('Comunicaciones sincronizadas');
    });
  }, [refetchAvisos, refetchAlertas, refetchNotif]);

  // Normalización de items
  const avisos = useMemo(() => {
    const list = Array.isArray(avisosRaw) ? avisosRaw : avisosRaw?.items || [];
    return list.map((a) => ({
      id: `aviso-${a.ID_COMUNICADO || a.idComunicado || Math.random()}`,
      rawId: a.ID_COMUNICADO || a.idComunicado,
      tipo: 'AVISO',
      titulo: a.TITULO || a.titulo || 'Aviso Oficial',
      contenido: a.CONTENIDO || a.contenido || a.mensaje || '',
      prioridad: a.PRIORIDAD || a.prioridad || 'NORMAL',
      fecha: a.FECHA_PUBLICACION || a.fechaPublicacion,
      leido: true,
      unidad: a.TIPO_SEGMENTACION === 'TODOS' ? 'Toda la copropiedad' : (a.TIPO_SEGMENTACION || 'General'),
      residente: 'Administración',
      estado: a.ESTADO || 'PUBLICADO',
    }));
  }, [avisosRaw]);

  const alertas = useMemo(() => {
    const list = Array.isArray(alertasRaw) ? alertasRaw : alertasRaw?.items || [];
    return list.map((al) => ({
      id: `alerta-${al.idAlerta || al.ID_ALERTA}`,
      rawId: al.idAlerta || al.ID_ALERTA,
      tipo: 'ALERTA',
      titulo: `Alerta: ${(al.tipoAlerta || al.TIPO_ALERTA || 'OPERATIVA').replace(/_/g, ' ')}`,
      contenido: al.mensaje || al.MENSAJE || `Situación operativa en apartamento ${al.numeroApartamento || al.NUMERO_APARTAMENTO || 'general'}`,
      prioridad: (al.tipoAlerta || '').includes('MORA') || (al.tipoAlerta || '').includes('URGENTE') ? 'URGENTE' : 'IMPORTANTE',
      fecha: al.fechaCreacion || al.FECHA_CREACION,
      leido: (al.leida || al.LEIDA) === 'S',
      unidad: al.numeroApartamento || al.NUMERO_APARTAMENTO ? `Apto ${al.numeroApartamento || al.NUMERO_APARTAMENTO}` : null,
      residente: al.nombreResidente || al.NOMBRE_RESIDENTE || null,
      estadoCuota: al.estadoCuota || al.ESTADO_CUOTA,
      enlace: (al.tipoAlerta || '').includes('MORA') ? '/cartera' : null,
    }));
  }, [alertasRaw]);

  const notificaciones = useMemo(() => {
    const list = Array.isArray(notifRaw) ? notifRaw : notifRaw?.items || [];
    return list.map((n) => ({
      id: `notif-${n.idNotificacion || n.id || Math.random()}`,
      rawId: n.idNotificacion || n.id,
      tipo: 'NOTIFICACION',
      titulo: n.titulo || 'Notificación del Sistema',
      contenido: n.mensaje || n.cuerpo || '',
      prioridad: (n.titulo || '').toLowerCase().includes('urgente') ? 'URGENTE' : 'NORMAL',
      fecha: n.fechaEnvio || n.fecha || n.creadoEn,
      leido: !!n.fechaLeido || n.leido === true,
      unidad: n.enlaceDestino || null,
      residente: null,
      enlace: n.enlaceDestino,
    }));
  }, [notifRaw]);

  // Combinación y filtrado unificado
  const itemsFiltrados = useMemo(() => {
    let combined = [];
    if (activeTab === 'todos') {
      combined = [...avisos, ...alertas, ...notificaciones];
    } else if (activeTab === 'avisos') {
      combined = avisos;
    } else if (activeTab === 'alertas') {
      combined = alertas;
    } else if (activeTab === 'notificaciones') {
      combined = notificaciones;
    }

    // Ordenar cronológicamente descendente
    combined.sort((a, b) => {
      const tA = a.fecha ? new Date(a.fecha).getTime() : 0;
      const tB = b.fecha ? new Date(b.fecha).getTime() : 0;
      return tB - tA;
    });

    return combined.filter((item) => {
      if (soloPendientes && item.leido) return false;
      if (soloPrioritarias && item.prioridad !== 'URGENTE' && item.prioridad !== 'IMPORTANTE' && item.prioridad !== 'ALTA') return false;

      if (!search.trim()) return true;
      const term = search.toLowerCase();
      return (
        item.titulo?.toLowerCase().includes(term) ||
        item.contenido?.toLowerCase().includes(term) ||
        item.unidad?.toLowerCase().includes(term) ||
        item.residente?.toLowerCase().includes(term)
      );
    });
  }, [activeTab, avisos, alertas, notificaciones, soloPendientes, soloPrioritarias, search]);

  // KPIs
  const kpis = useMemo(() => {
    const total = avisos.length + alertas.length + notificaciones.length;
    const totalAvisos = avisos.length;
    const alertasPendientes = alertas.filter((a) => !a.leido).length;
    const notificacionesNoLeidas = notificaciones.filter((n) => !n.leido).length;
    return { total, totalAvisos, alertasPendientes, notificacionesNoLeidas };
  }, [avisos, alertas, notificaciones]);

  // Paginación
  const totalPages = Math.max(1, Math.ceil(itemsFiltrados.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = itemsFiltrados.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // Acciones sobre Items
  const handleMarcarLeida = useCallback(
    async (item) => {
      try {
        if (item.tipo === 'ALERTA') {
          await tenantApi.put(`/alertas/${item.rawId}/atender`);
          toast.success('Alerta operativa marcada como atendida');
          refetchAlertas();
        } else if (item.tipo === 'NOTIFICACION') {
          await tenantApi.put(`/buzon/${item.rawId}/leido`);
          toast.success('Notificación marcada como leída');
          refetchNotif();
        }
      } catch (err) {
        toast.error(err.message || 'Error al actualizar el estado');
      }
    },
    [refetchAlertas, refetchNotif, tenantApi]
  );

  const handleMarcarTodasLeidas = useCallback(async () => {
    try {
      await Promise.allSettled([
        tenantApi.put('/alertas/marcar-todas-leidas'),
        tenantApi.put('/buzon/marcar-todas-leidas'),
      ]);
      toast.success('Todas las alertas y notificaciones fueron marcadas como leídas');
      refetchAlertas();
      refetchNotif();
    } catch (err) {
      toast.error(err.message || 'Error al procesar la acción');
    }
  }, [refetchAlertas, refetchNotif, tenantApi]);

  const handleArchivarAviso = useCallback(
    async (rawId) => {
      try {
        await tenantApi.del(`/buzon/aviso/${rawId}`);
        toast.success('Aviso archivado correctamente');
        if (detalleItem?.rawId === rawId) setDetalleItem(null);
        refetchAvisos();
      } catch (err) {
        toast.error(err.message || 'No se pudo archivar el aviso');
      }
    },
    [detalleItem, refetchAvisos, tenantApi]
  );

  // Crear Aviso
  const handleCreateAviso = useCallback(async () => {
    if (!avisoForm.titulo.trim()) {
      toast.error('Ingrese el título del aviso');
      return;
    }
    if (!avisoForm.mensaje.trim()) {
      toast.error('Ingrese el contenido del comunicado');
      return;
    }

    setSubmitting(true);
    try {
      await tenantApi.post('/buzon/aviso', {
        titulo: avisoForm.titulo.trim(),
        mensaje: avisoForm.mensaje.trim(),
        prioridad: avisoForm.prioridad,
        tipoSegmentacion: avisoForm.tipoSegmentacion,
        enviarEmail: avisoForm.enviarEmail,
      });
      toast.success('Aviso oficial publicado y notificado');
      setModalAvisoOpen(false);
      setAvisoForm({
        titulo: '',
        mensaje: '',
        prioridad: 'NORMAL',
        tipoSegmentacion: 'TODOS',
        enviarEmail: true,
      });
      refetchAvisos();
    } catch (err) {
      toast.error(err.message || 'Error al publicar el aviso');
    } finally {
      setSubmitting(false);
    }
  }, [avisoForm, refetchAvisos, tenantApi]);

  // Crear Alerta Operativa
  const handleCreateAlerta = useCallback(async () => {
    if (!alertaForm.mensaje.trim()) {
      toast.error('Ingrese el motivo o descripción de la alerta');
      return;
    }

    setSubmitting(true);
    try {
      await tenantApi.post('/alertas', {
        tipoAlerta: alertaForm.tipoAlerta,
        numeroApartamento: alertaForm.numeroApartamento || null,
        nombreResidente: alertaForm.nombreResidente || null,
        mensaje: alertaForm.mensaje.trim(),
        estadoCuota: alertaForm.tipoAlerta === 'MORA_CUOTA' ? 'EN_MORA' : 'PENDIENTE',
      });
      toast.success('Alerta operativa registrada en el sistema');
      setModalAlertaOpen(false);
      setAlertaForm({
        tipoAlerta: 'MORA_CUOTA',
        numeroApartamento: '',
        nombreResidente: '',
        mensaje: '',
      });
      refetchAlertas();
    } catch (err) {
      toast.error(err.message || 'Error al registrar la alerta');
    } finally {
      setSubmitting(false);
    }
  }, [alertaForm, refetchAlertas, tenantApi]);

  const isLoadingInitial = loadingAvisos && loadingAlertas && loadingNotif;

  return (
    <PageContainer className="space-y-6">
      {/* 1. Header Contextual Enterprise */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Centro de Comunicaciones
            </h1>
            <Badge variant="outline" className="bg-primary/5 text-primary border-primary/20 text-xs font-semibold">
              Canal Unificado
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground">
            Avisos oficiales, alertas operativas y notificaciones en tiempo real para la copropiedad
          </p>
        </div>

        {/* Acciones del Header */}
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefetchAll}
            disabled={refreshing}
            className="text-xs min-h-[44px] sm:min-h-9"
          >
            <RefreshCw className={`h-3.5 w-3.5 mr-1.5 ${refreshing ? 'animate-spin text-primary' : ''}`} aria-hidden="true" />
            Actualizar
          </Button>
          {(kpis.alertasPendientes > 0 || kpis.notificacionesNoLeidas > 0) && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleMarcarTodasLeidas}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              <CheckCheck className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
              Marcar Todo Leído
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => setModalAlertaOpen(true)}
            className="text-xs min-h-[44px] sm:min-h-9 border-amber-500/30 text-amber-700 dark:text-amber-300 hover:bg-amber-50 dark:hover:bg-amber-950/30"
          >
            <AlertTriangle className="h-3.5 w-3.5 mr-1.5 text-amber-600" aria-hidden="true" />
            Nueva Alerta
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setModalAvisoOpen(true)}
            className="text-xs min-h-[44px] sm:min-h-9 shadow-xs"
          >
            <Plus className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            Publicar Aviso
          </Button>
        </div>
      </div>

      {/* 2. Grid de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total Comunicaciones"
          value={kpis.total}
          subtitle="Avisos, alertas y notificaciones"
          icon={MessageSquare}
          variant="primary"
        />
        <MetricCard
          label="Avisos Oficiales"
          value={kpis.totalAvisos}
          subtitle="Circulares vigentes a la comunidad"
          icon={Megaphone}
          variant="info"
        />
        <MetricCard
          label="Alertas Operativas"
          value={kpis.alertasPendientes}
          subtitle={kpis.alertasPendientes > 0 ? 'Requieren revisión o atención' : 'Al día, sin incidencias'}
          icon={AlertTriangle}
          variant={kpis.alertasPendientes > 0 ? 'warning' : 'success'}
        />
        <MetricCard
          label="Notificaciones Sin Leer"
          value={kpis.notificacionesNoLeidas}
          subtitle="Eventos del sistema pendientes"
          icon={Bell}
          variant={kpis.notificacionesNoLeidas > 0 ? 'secondary' : 'default'}
        />
      </div>

      {/* 3. Card Principal con Pestañas y Filtros */}
      <Card className="border-border/80 shadow-xs overflow-hidden">
        <CardHeader className="p-4 sm:p-5 border-b border-border/50 bg-card space-y-3">
          {/* Fila de Tabs y Búsqueda */}
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
            {/* Tabs principales */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 text-xs scrollbar-none">
              {[
                { id: 'todos', label: 'Todos', icon: MessageSquare, count: kpis.total },
                { id: 'avisos', label: 'Avisos Oficiales', icon: Megaphone, count: kpis.totalAvisos },
                { id: 'alertas', label: 'Alertas Operativas', icon: AlertTriangle, count: kpis.alertasPendientes },
                { id: 'notificaciones', label: 'Notificaciones', icon: Bell, count: kpis.notificacionesNoLeidas },
              ].map((tab) => {
                const Icon = tab.icon;
                const isSelected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => {
                      setActiveTab(tab.id);
                      setPage(0);
                    }}
                    className={`px-3 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 flex items-center gap-2 ${
                      isSelected
                        ? 'bg-primary text-primary-foreground shadow-xs'
                        : 'bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>{tab.label}</span>
                    <span
                      className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                        isSelected ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-background text-muted-foreground'
                      }`}
                    >
                      {tab.count}
                    </span>
                  </button>
                );
              })}
            </div>

            {/* Buscador y Contadores */}
            <div className="flex items-center gap-3 w-full lg:w-auto">
              <div className="relative flex-1 lg:w-72">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setPage(0);
                  }}
                  placeholder="Buscar en comunicaciones..."
                  className="w-full pl-9 pr-8 py-1.5 text-xs bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary text-foreground"
                />
                {search && (
                  <button
                    type="button"
                    onClick={() => setSearch('')}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Chips de filtro rápido */}
          <div className="flex items-center gap-2 flex-wrap text-xs pt-1 border-t border-border/40">
            <span className="text-muted-foreground text-[11px] font-medium mr-1 flex items-center gap-1">
              <Filter className="h-3 w-3" /> Filtrar:
            </span>
            <button
              type="button"
              onClick={() => {
                setSoloPendientes((prev) => !prev);
                setPage(0);
              }}
              className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-colors ${
                soloPendientes
                  ? 'bg-amber-500/15 text-amber-800 dark:text-amber-300 border border-amber-500/30'
                  : 'bg-muted/60 text-muted-foreground hover:bg-muted'
              }`}
            >
              Solo Pendientes / Sin Leer
            </button>
            <button
              type="button"
              onClick={() => {
                setSoloPrioritarias((prev) => !prev);
                setPage(0);
              }}
              className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-colors ${
                soloPrioritarias
                  ? 'bg-destructive/15 text-destructive border border-destructive/30'
                  : 'bg-muted/60 text-muted-foreground hover:bg-muted'
              }`}
            >
              Alta Prioridad / Urgentes
            </button>
            <span className="ml-auto text-muted-foreground text-[11px]">
              Mostrando <strong>{rows.length}</strong> de <strong>{itemsFiltrados.length}</strong>
            </span>
          </div>
        </CardHeader>

        {/* Listado de Comunicaciones */}
        <CardContent className="p-0">
          {isLoadingInitial && !avisosRaw && !alertasRaw ? (
            <div className="p-8">
              <LoadingState message="Cargando centro de comunicaciones..." description="Consultando avisos, alertas y notificaciones con aislamiento RLS" />
            </div>
          ) : (errorAvisos || errorAlertas) && itemsFiltrados.length === 0 ? (
            <div className="p-8">
              <ErrorState title="Error de sincronización" message="No se pudieron consultar los canales de comunicación. Verifique su sesión o reintente." onRetry={handleRefetchAll} />
            </div>
          ) : itemsFiltrados.length === 0 ? (
            <div className="p-12 text-center flex flex-col items-center justify-center">
              <div className="p-3.5 rounded-2xl bg-primary/10 text-primary mb-3">
                <MessageSquare className="h-8 w-8" />
              </div>
              <h3 className="text-base font-semibold text-foreground">
                {search ? 'Sin coincidencias' : 'No hay comunicaciones en esta categoría'}
              </h3>
              <p className="text-xs sm:text-sm text-muted-foreground max-w-sm mt-1 mb-4">
                {search
                  ? `No se encontró ningún mensaje para "${search}". Intente con otro término.`
                  : 'Las novedades operativas, circulares y notificaciones registradas aparecerán aquí.'}
              </p>
              {search ? (
                <Button variant="outline" size="sm" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              ) : (
                <Button variant="primary" size="sm" onClick={() => setModalAvisoOpen(true)}>
                  <Plus className="h-3.5 w-3.5 mr-1.5" />
                  Publicar Aviso Oficial
                </Button>
              )}
            </div>
          ) : (
            <div className="divide-y divide-border/50">
              {rows.map((item) => {
                const isAviso = item.tipo === 'AVISO';
                const isAlerta = item.tipo === 'ALERTA';
                const isNotif = item.tipo === 'NOTIFICACION';

                return (
                  <div
                    key={item.id}
                    className={`p-4 sm:p-5 transition-colors flex flex-col sm:flex-row sm:items-start justify-between gap-4 hover:bg-muted/30 ${
                      !item.leido ? 'bg-primary/2 dark:bg-primary/5' : ''
                    }`}
                  >
                    <div className="flex items-start gap-3.5 min-w-0">
                      {/* Icono semántico de canal */}
                      <div
                        className={`h-9 w-9 rounded-xl flex items-center justify-center shrink-0 ${
                          isAviso
                            ? 'bg-sky-500/10 text-sky-600 dark:text-sky-400'
                            : isAlerta
                            ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                            : 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400'
                        }`}
                      >
                        {isAviso ? (
                          <Megaphone className="h-4 w-4" />
                        ) : isAlerta ? (
                          <AlertTriangle className="h-4 w-4" />
                        ) : (
                          <Bell className="h-4 w-4" />
                        )}
                      </div>

                      <div className="space-y-1 min-w-0">
                        {/* Cabecera del item: Tipo, Prioridad y Estado */}
                        <div className="flex items-center gap-2 flex-wrap">
                          <Badge
                            variant="outline"
                            className={`text-[10px] font-semibold ${
                              isAviso
                                ? 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300'
                                : isAlerta
                                ? 'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300'
                                : 'bg-indigo-50 text-indigo-700 border-indigo-200 dark:bg-indigo-950/40 dark:text-indigo-300'
                            }`}
                          >
                            {isAviso ? 'Aviso Oficial' : isAlerta ? 'Alerta Operativa' : 'Notificación Sistema'}
                          </Badge>
                          {getPrioridadBadge(item.prioridad)}
                          {!item.leido && (
                            <Badge variant="outline" className="bg-amber-500/15 text-amber-700 border-amber-300 dark:text-amber-400 text-[9px] font-bold uppercase tracking-wider animate-pulse">
                              Pendiente
                            </Badge>
                          )}
                        </div>

                        {/* Título y extracto */}
                        <h4 className="text-sm font-semibold text-foreground pt-0.5">{item.titulo}</h4>
                        <p className="text-xs text-muted-foreground line-clamp-2">{item.contenido}</p>

                        {/* Metadatos: Inmueble, Residente, Fecha */}
                        <div className="flex items-center gap-3 text-[11px] text-muted-foreground pt-1 flex-wrap">
                          {item.unidad && (
                            <span className="flex items-center gap-1">
                              <Building2 className="h-3 w-3 text-muted-foreground/70" />
                              {item.unidad}
                            </span>
                          )}
                          {item.residente && (
                            <span className="flex items-center gap-1">
                              <User className="h-3 w-3 text-muted-foreground/70" />
                              {item.residente}
                            </span>
                          )}
                          <span className="flex items-center gap-1">
                            <Clock className="h-3 w-3 text-muted-foreground/70" />
                            {item.fecha ? formatDateTime(item.fecha) : 'Fecha no registrada'}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Acciones del Item */}
                    <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setDetalleItem(item)}
                        className="text-xs h-8"
                      >
                        <Eye className="h-3.5 w-3.5 mr-1" />
                        Detalle
                      </Button>
                      {!item.leido && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleMarcarLeida(item)}
                          className="text-xs h-8 border-emerald-500/30 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-50"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                          {isAlerta ? 'Atendida' : 'Leída'}
                        </Button>
                      )}
                      {isAviso && (
                        <button
                          type="button"
                          onClick={() => handleArchivarAviso(item.rawId)}
                          className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                          title="Archivar aviso oficial"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                      {item.enlace && (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => navigate(item.enlace)}
                          className="text-xs h-8"
                        >
                          Ir al módulo
                          <ArrowRight className="h-3 w-3 ml-1" />
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Paginación */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between p-4 border-t border-border/60 bg-card text-xs text-muted-foreground">
              <span>
                Página <strong className="text-foreground">{safePage + 1}</strong> de <strong className="text-foreground">{totalPages}</strong>
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="text-xs h-8"
                >
                  Anterior
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={safePage >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  className="text-xs h-8"
                >
                  Siguiente
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 4. Modal Publicar Aviso Oficial */}
      <Modal
        open={modalAvisoOpen}
        onClose={() => setModalAvisoOpen(false)}
        title="Publicar Aviso Oficial (Circular)"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalAvisoOpen(false)} disabled={submitting}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={handleCreateAviso} disabled={submitting}>
              {submitting ? 'Publicando...' : 'Publicar Circular'}
            </Button>
          </>
        }
      >
        <div className="space-y-3.5 py-1 text-xs sm:text-sm">
          <Input
            id="aviso-titulo"
            label="Título del Comunicado *"
            placeholder="Ej. Mantenimiento Preventivo de Ascensores"
            value={avisoForm.titulo}
            onChange={(e) => setAvisoForm({ ...avisoForm, titulo: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              id="aviso-prioridad"
              label="Prioridad *"
              value={avisoForm.prioridad}
              onChange={(e) => setAvisoForm({ ...avisoForm, prioridad: e.target.value })}
            >
              <option value="NORMAL">Normal</option>
              <option value="IMPORTANTE">Importante / Alta</option>
              <option value="URGENTE">Urgente</option>
              <option value="BAJA">Baja</option>
            </Select>

            <Select
              id="aviso-segmentacion"
              label="Destinatarios / Alcance *"
              value={avisoForm.tipoSegmentacion}
              onChange={(e) => setAvisoForm({ ...avisoForm, tipoSegmentacion: e.target.value })}
            >
              <option value="TODOS">Toda la Copropiedad</option>
              <option value="PROPIETARIOS">Solo Propietarios</option>
              <option value="ARRENDATARIOS">Solo Arrendatarios</option>
            </Select>
          </div>

          <Textarea
            id="aviso-mensaje"
            label="Mensaje o Cuerpo del Comunicado *"
            rows={4}
            placeholder="Escriba aquí los detalles, horarios y recomendaciones para los residentes..."
            value={avisoForm.mensaje}
            onChange={(e) => setAvisoForm({ ...avisoForm, mensaje: e.target.value })}
          />

          <label className="flex items-center gap-2 pt-1 cursor-pointer">
            <input
              type="checkbox"
              checked={avisoForm.enviarEmail}
              onChange={(e) => setAvisoForm({ ...avisoForm, enviarEmail: e.target.checked })}
              className="rounded border-input text-primary focus:ring-primary h-4 w-4"
            />
            <span className="text-xs text-muted-foreground">
              Enviar copia automática por correo electrónico a los residentes registrados
            </span>
          </label>
        </div>
      </Modal>

      {/* 5. Modal Registrar Alerta Operativa */}
      <Modal
        open={modalAlertaOpen}
        onClose={() => setModalAlertaOpen(false)}
        title="Registrar Alerta Operativa"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalAlertaOpen(false)} disabled={submitting}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={handleCreateAlerta} disabled={submitting}>
              {submitting ? 'Guardando...' : 'Registrar Alerta'}
            </Button>
          </>
        }
      >
        <div className="space-y-3.5 py-1 text-xs sm:text-sm">
          <Select
            id="alerta-tipo"
            label="Tipo de Alerta *"
            value={alertaForm.tipoAlerta}
            onChange={(e) => setAlertaForm({ ...alertaForm, tipoAlerta: e.target.value })}
          >
            <option value="MORA_CUOTA">Mora en Cuota de Administración</option>
            <option value="RUIDO_CONVIVENCIA">Queja por Ruido / Convivencia</option>
            <option value="INCIDENTE_SEGURIDAD">Incidente de Seguridad / Portería</option>
            <option value="CONTRATO_EXPIRACION">Contrato de Arriendo por Vencer</option>
            <option value="MANTENIMIENTO_URGENTE">Falla Técnica / Mantenimiento Urgente</option>
            <option value="OPERATIVA">Otra Novedad Operativa</option>
          </Select>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              id="alerta-apto"
              label="Apartamento / Inmueble"
              value={alertaForm.numeroApartamento}
              onChange={(e) => setAlertaForm({ ...alertaForm, numeroApartamento: e.target.value })}
            >
              <option value="">— Ninguno / Copropiedad General —</option>
              {unidades.map((u) => {
                const num = u.numero || u.identificador || u.id;
                return (
                  <option key={u.id || u.idUnidad || num} value={num}>
                    Apto {num} {u.bloque ? `· ${u.bloque}` : ''}
                  </option>
                );
              })}
            </Select>

            <Input
              id="alerta-residente"
              label="Nombre de Residente o Afectado"
              placeholder="Ej. Carlos Pérez"
              value={alertaForm.nombreResidente}
              onChange={(e) => setAlertaForm({ ...alertaForm, nombreResidente: e.target.value })}
            />
          </div>

          <Textarea
            id="alerta-mensaje"
            label="Descripción o Motivo de la Alerta *"
            rows={3}
            placeholder="Describa el hecho, monto o antecedente relevante para atención administrativa..."
            value={alertaForm.mensaje}
            onChange={(e) => setAlertaForm({ ...alertaForm, mensaje: e.target.value })}
          />
        </div>
      </Modal>

      {/* 6. Modal de Detalle Completo de Comunicación */}
      <Modal
        open={!!detalleItem}
        onClose={() => setDetalleItem(null)}
        title={detalleItem?.titulo || 'Detalle de Comunicación'}
        footer={
          <>
            <Button variant="outline" onClick={() => setDetalleItem(null)}>
              Cerrar
            </Button>
            {detalleItem && !detalleItem.leido && (
              <Button
                variant="primary"
                onClick={() => {
                  handleMarcarLeida(detalleItem);
                  setDetalleItem(null);
                }}
              >
                <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                Marcar como Atendida / Leída
              </Button>
            )}
          </>
        }
      >
        {detalleItem && (
          <div className="space-y-4 py-2 text-xs sm:text-sm">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge variant="outline" className="font-semibold text-[10px]">
                {detalleItem.tipo === 'AVISO'
                  ? 'Aviso Oficial'
                  : detalleItem.tipo === 'ALERTA'
                  ? 'Alerta Operativa'
                  : 'Notificación'}
              </Badge>
              {getPrioridadBadge(detalleItem.prioridad)}
              <span className="text-muted-foreground text-[11px] ml-auto">
                {detalleItem.fecha ? formatDateTime(detalleItem.fecha) : ''}
              </span>
            </div>

            <div className="p-3.5 rounded-xl bg-muted/40 border border-border/60 whitespace-pre-wrap leading-relaxed text-foreground text-xs sm:text-sm">
              {detalleItem.contenido}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs text-muted-foreground pt-1">
              {detalleItem.unidad && (
                <div className="flex items-center gap-1.5">
                  <Building2 className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>Unidad: <strong>{detalleItem.unidad}</strong></span>
                </div>
              )}
              {detalleItem.residente && (
                <div className="flex items-center gap-1.5">
                  <User className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>Afectado/Destinatario: <strong>{detalleItem.residente}</strong></span>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
