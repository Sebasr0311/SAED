import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import {
  AlertCircle,
  Bell,
  Building2,
  Check,
  CheckCheck,
  CheckCircle2,
  Clock,
  Copy,
  Inbox,
  Maximize2,
  Megaphone,
  Package,
  RefreshCw,
  Search,
  ShieldAlert,
  Trash2,
  UserCheck,
  X,
} from 'lucide-react';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDateTime, imageSrc, cn } from '../lib/utils.js';
import {
  emitNotificationsChanged,
  subscribeNotificationsChanged,
} from '../lib/notificationsSync.js';

function extractPin(item) {
  if (!item) return null;
  if (item.codigoRetiroPin && /^[0-9]{4}$/.test(String(item.codigoRetiroPin).trim())) {
    return String(item.codigoRetiroPin).trim();
  }
  const text = `${item.cuerpo || ''} ${item.titulo || ''}`;
  const match = text.match(/(?:código(?:\s+de)?\s+retiro\s+PIN|PIN(?:\s+de\s+retiro)?)\s*[:#]?\s*([0-9]{4})/i);
  return match ? match[1] : null;
}

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';

/**
 * ResBuzonPage — Centro de Notificaciones y Correspondencia para el Residente
 * Rediseño premium con soporte para paquetería en portería, circulares administrativas,
 * confirmaciones de visitas, previsualización fotográfica y selección múltiple.
 */
export default function ResBuzonPage() {
  const { user } = useAuth();

  // 1. Carga de mensajes del buzón
  const { data, loading, error, refetch } = useFetch(
    () => api.get('/buzon'),
    [user]
  );
  const items = useMemo(() => {
    return Array.isArray(data) ? data : data?.items || [];
  }, [data]);

  // 2. Carga opcional de avisos y circulares oficiales
  const { data: avisosRaw, refetch: refetchAvisos } = useFetch(
    () => api.get('/buzon/avisos').catch(() => []),
    [user]
  );
  const avisos = useMemo(() => {
    return Array.isArray(avisosRaw) ? avisosRaw : avisosRaw?.items || [];
  }, [avisosRaw]);

  // Refresco unificado
  const [refreshing, setRefreshing] = useState(false);
  const refetchAll = useCallback(() => {
    setRefreshing(true);
    Promise.allSettled([refetch(), refetchAvisos()]).finally(() => {
      setTimeout(() => setRefreshing(false), 400);
      toast.success('Buzón de notificaciones actualizado');
    });
  }, [refetch, refetchAvisos]);

  // Suscripción reactiva a cambios de notificaciones desde la campana o el sistema
  useEffect(() => {
    return subscribeNotificationsChanged(() => {
      refetch();
      refetchAvisos();
    });
  }, [refetch, refetchAvisos]);

  // Filtros y búsqueda
  const [tabFiltro, setTabFiltro] = useState('todos'); // 'todos' | 'paquetes' | 'comunicados' | 'no_leidos'
  const [search, setSearch] = useState('');

  // Selección múltiple
  const [seleccionados, setSeleccionados] = useState([]);
  const [confirmVaciar, setConfirmVaciar] = useState(false);
  const [confirmVaciarSel, setConfirmVaciarSel] = useState(false);
  const [vaciandoSel, setVaciandoSel] = useState(false);
  const vaciandoSelRef = useRef(false);

  // Estados de lectura local optimista
  const [leidosLocalmente, setLeidosLocalmente] = useState([]);

  // Modal de detalle del mensaje y visor fotográfico
  const [mensajeDetalle, setMensajeDetalle] = useState(null);
  const [fotoGrande, setFotoGrande] = useState(null);

  // Cálculos de métricas
  const paquetesPendientes = useMemo(() => {
    return items.filter(
      (m) =>
        m.tipo === 'PAQUETE' &&
        !m.leido &&
        !leidosLocalmente.includes(m.idMensaje)
    );
  }, [items, leidosLocalmente]);

  const noLeidosCount = useMemo(() => {
    return items.filter(
      (m) => !m.leido && !leidosLocalmente.includes(m.idMensaje)
    ).length;
  }, [items, leidosLocalmente]);

  // Mensajes filtrados por tab y término de búsqueda
  const mensajesFiltrados = useMemo(() => {
    let filtrados = items;

    if (tabFiltro === 'paquetes') {
      filtrados = filtrados.filter((m) => m.tipo === 'PAQUETE');
    } else if (tabFiltro === 'comunicados') {
      filtrados = filtrados.filter(
        (m) => m.tipo === 'COMUNICADO' || m.tipo === 'AVISO' || m.tipo === 'CIRCULAR'
      );
    } else if (tabFiltro === 'no_leidos') {
      filtrados = filtrados.filter(
        (m) => !m.leido && !leidosLocalmente.includes(m.idMensaje)
      );
    }

    if (!search.trim()) return filtrados;

    const term = search.toLowerCase();
    return filtrados.filter((m) =>
      [m.titulo, m.cuerpo, m.tipo]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(term))
    );
  }, [items, tabFiltro, search, leidosLocalmente]);

  // Acciones de lectura y vaciado
  async function marcarLeido(idMensaje) {
    if (leidosLocalmente.includes(idMensaje)) return;
    try {
      await api.put(`/buzon/${idMensaje}/leido`);
      setLeidosLocalmente((prev) => [...prev, idMensaje]);
      const userKey = user?.id || user?.username || 'anon';
      const readKey = `saed_read_notifs_${userKey}`;
      try {
        const saved = JSON.parse(localStorage.getItem(readKey) || '[]');
        const set = new Set(saved);
        set.add(`msg-${idMensaje}`);
        set.add(String(idMensaje));
        localStorage.setItem(readKey, JSON.stringify(Array.from(set)));
      } catch {}
      refetch();
      emitNotificationsChanged({ action: 'mark-read', id: idMensaje });
    } catch {
      /* degradación silenciosa */
    }
  }

  async function marcarTodasLeidas() {
    try {
      await api.put('/buzon/marcar-todas-leidas');
      toast.success('Todas las notificaciones fueron marcadas como leídas');
      const userKey = user?.id || user?.username || 'anon';
      const vistoKey = `saed_notif_visto_${userKey}`;
      const readKey = `saed_read_notifs_${userKey}`;
      try {
        localStorage.setItem(vistoKey, String(Date.now()));
        const saved = JSON.parse(localStorage.getItem(readKey) || '[]');
        const set = new Set(saved);
        items.forEach((m) => {
          set.add(`msg-${m.idMensaje}`);
          set.add(String(m.idMensaje));
        });
        avisos.forEach((a) => set.add(`aviso-${a.idComunicado || a.ID_COMUNICADO || a.id}`));
        localStorage.setItem(readKey, JSON.stringify(Array.from(set)));
      } catch {}
      setLeidosLocalmente(items.map((m) => m.idMensaje));
      refetch();
      emitNotificationsChanged({ action: 'mark-all-read' });
    } catch (err) {
      toast.error('Error al marcar notificaciones como leídas');
    }
  }

  function abrirDetalle(item) {
    setMensajeDetalle(item);
    if (!item.leido && !leidosLocalmente.includes(item.idMensaje)) {
      marcarLeido(item.idMensaje);
    }
    if (item.tipo === 'PAQUETE' && (!item.fotoCaptura && !item.fotoPaqueteUrl) && item.enlaceDestino) {
      api.get(item.enlaceDestino).then((res) => {
        const pkg = res?.data || res;
        if (pkg && (pkg.fotoPaqueteUrl || pkg.fotoCaptura)) {
          setMensajeDetalle((prev) => (prev && prev.idMensaje === item.idMensaje ? {
            ...prev,
            fotoCaptura: pkg.fotoPaqueteUrl || pkg.fotoCaptura,
            fotoPaqueteUrl: pkg.fotoPaqueteUrl || pkg.fotoCaptura,
            fecha: prev.fecha || pkg.fechaRecepcion || pkg.fechaCreacion,
            fechaEnvio: prev.fechaEnvio || pkg.fechaRecepcion || pkg.fechaCreacion,
            fechaCreacion: prev.fechaCreacion || pkg.fechaRecepcion || pkg.fechaCreacion,
          } : prev));
        }
      }).catch(() => {});
    }
  }

  function toggleSeleccion(idMensaje) {
    setSeleccionados((prev) =>
      prev.includes(idMensaje)
        ? prev.filter((x) => x !== idMensaje)
        : [...prev, idMensaje]
    );
  }

  function seleccionarTodosVisibles() {
    const ids = mensajesFiltrados.map((m) => m.idMensaje);
    if (seleccionados.length === ids.length) {
      setSeleccionados([]);
    } else {
      setSeleccionados(ids);
    }
  }

  async function vaciarBuzon() {
    try {
      await api.put('/buzon/vaciar');
      toast.success('Buzón vaciado y mensajes archivados');
      const userKey = user?.id || user?.username || 'anon';
      const vistoKey = `saed_notif_visto_${userKey}`;
      const readKey = `saed_read_notifs_${userKey}`;
      try {
        localStorage.setItem(vistoKey, String(Date.now()));
        const saved = JSON.parse(localStorage.getItem(readKey) || '[]');
        const set = new Set(saved);
        items.forEach((m) => {
          set.add(`msg-${m.idMensaje}`);
          set.add(String(m.idMensaje));
        });
        avisos.forEach((a) => set.add(`aviso-${a.idComunicado || a.ID_COMUNICADO || a.id}`));
        localStorage.setItem(readKey, JSON.stringify(Array.from(set)));
      } catch {}
      setLeidosLocalmente([]);
      setSeleccionados([]);
      refetch();
      emitNotificationsChanged({ action: 'vaciar' });
    } catch (err) {
      toast.error(err.message || 'No se pudo vaciar el buzón');
    } finally {
      setConfirmVaciar(false);
    }
  }

  async function vaciarSeleccionados() {
    if (vaciandoSelRef.current) return;
    vaciandoSelRef.current = true;
    setVaciandoSel(true);
    try {
      await api.put('/buzon/vaciar-multi', { ids: seleccionados });
      toast.success(`${seleccionados.length} mensaje(s) archivado(s)`);
      const userKey = user?.id || user?.username || 'anon';
      const readKey = `saed_read_notifs_${userKey}`;
      try {
        const saved = JSON.parse(localStorage.getItem(readKey) || '[]');
        const set = new Set(saved);
        seleccionados.forEach((id) => {
          set.add(`msg-${id}`);
          set.add(String(id));
        });
        localStorage.setItem(readKey, JSON.stringify(Array.from(set)));
      } catch {}
      emitNotificationsChanged({ action: 'vaciar-multi', ids: seleccionados });
      setSeleccionados([]);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al archivar los mensajes seleccionados');
      setSeleccionados([]);
      refetch();
    } finally {
      vaciandoSelRef.current = false;
      setVaciandoSel(false);
      setConfirmVaciarSel(false);
    }
  }

  // Renderizado del icono por tipo de mensaje
  function iconoTipo(tipo) {
    switch (tipo) {
      case 'PAQUETE':
        return (
          <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400 shrink-0">
            <Package className="w-5 h-5" />
          </div>
        );
      case 'COMUNICADO':
      case 'CIRCULAR':
      case 'AVISO':
        return (
          <div className="p-2.5 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 shrink-0">
            <Megaphone className="w-5 h-5" />
          </div>
        );
      case 'VISITA':
      case 'CONFIRMACION':
        return (
          <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 shrink-0">
            <UserCheck className="w-5 h-5" />
          </div>
        );
      case 'SANCION':
      case 'MULTA':
        return (
          <div className="p-2.5 rounded-xl bg-rose-500/10 text-rose-600 dark:text-rose-400 shrink-0">
            <ShieldAlert className="w-5 h-5" />
          </div>
        );
      default:
        return (
          <div className="p-2.5 rounded-xl bg-primary/10 text-primary shrink-0">
            <Bell className="w-5 h-5" />
          </div>
        );
    }
  }

  return (
    <PageContainer>
      {/* 1. CABECERA CON ESTILO SAAS Y HERRAMIENTAS */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2 border-b border-border/70">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              Buzón & Novedades
            </h1>
            {noLeidosCount > 0 && (
              <Badge variant="destructive" className="text-xs font-semibold px-2 py-0.5">
                {noLeidosCount} nuevo(s)
              </Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground">
            Recepción de correspondencia, encomiendas en portería y avisos oficiales de la copropiedad.
          </p>
        </div>

        <div className="flex items-center gap-2.5 self-stretch sm:self-auto flex-wrap">
          <Button
            variant="outline"
            size="sm"
            onClick={refetchAll}
            disabled={refreshing}
            className="gap-1.5 shadow-sm text-xs"
          >
            <RefreshCw className={cn('w-3.5 h-3.5', refreshing && 'animate-spin')} />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>

          {noLeidosCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={marcarTodasLeidas}
              className="gap-1.5 shadow-sm text-xs text-muted-foreground hover:text-foreground"
            >
              <CheckCheck className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Marcar Leídas</span>
            </Button>
          )}

          {items.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setConfirmVaciar(true)}
              className="gap-1.5 text-xs text-muted-foreground hover:text-rose-600 hover:border-rose-300 dark:hover:border-rose-800"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Vaciar Buzón</span>
            </Button>
          )}
        </div>
      </div>

      {/* 2. STRIP DE 3 KPIS INTERACTIVOS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* KPI 1: Paquetería */}
        <Card
          onClick={() => setTabFiltro('paquetes')}
          className={cn(
            'border-border/70 cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md',
            tabFiltro === 'paquetes' && 'ring-2 ring-primary/40 bg-card/80'
          )}
        >
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div
              className={cn(
                'p-3 rounded-xl shrink-0 transition-transform group-hover:scale-105',
                paquetesPendientes.length > 0
                  ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                  : 'bg-muted text-muted-foreground'
              )}
            >
              <Package className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                  Paquetes en Portería
                </p>
                {paquetesPendientes.length > 0 && (
                  <span className="w-2 h-2 rounded-full bg-amber-500 animate-ping" />
                )}
              </div>
              <h3 className="text-xl font-bold text-foreground truncate">
                {paquetesPendientes.length > 0
                  ? `${paquetesPendientes.length} por retirar`
                  : '0 pendientes'}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {paquetesPendientes.length > 0
                  ? 'En custodia de vigilancia'
                  : 'Recepción al día'}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2: Circulares de la Administración */}
        <Card
          onClick={() => setTabFiltro('comunicados')}
          className={cn(
            'border-border/70 cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md',
            tabFiltro === 'comunicados' && 'ring-2 ring-primary/40 bg-card/80'
          )}
        >
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 shrink-0">
              <Megaphone className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Avisos & Circulares
              </p>
              <h3 className="text-xl font-bold text-foreground truncate">
                {avisos.length || items.filter((m) => m.tipo === 'COMUNICADO' || m.tipo === 'AVISO').length} Publicado(s)
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                Asambleas, obras y normativas
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3: Estado General de Lectura */}
        <Card
          onClick={() => setTabFiltro('no_leidos')}
          className={cn(
            'border-border/70 cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md',
            tabFiltro === 'no_leidos' && 'ring-2 ring-primary/40 bg-card/80'
          )}
        >
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div
              className={cn(
                'p-3 rounded-xl shrink-0',
                noLeidosCount === 0
                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                  : 'bg-primary/10 text-primary'
              )}
            >
              {noLeidosCount === 0 ? (
                <CheckCircle2 className="w-6 h-6" />
              ) : (
                <Inbox className="w-6 h-6" />
              )}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Estado del Buzón
              </p>
              <h3 className="text-xl font-bold text-foreground truncate">
                {noLeidosCount === 0 ? 'Completamente al día' : `${noLeidosCount} por leer`}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {items.length} notificaciones en total
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. BARRA DE FILTROS & BÚSQUEDA REACTIVA */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-card p-3 rounded-xl border border-border">
        {/* Pestañas de categoría */}
        <Tabs
          value={tabFiltro}
          onValueChange={setTabFiltro}
          className="w-full sm:w-auto"
        >
          <TabsList className="grid grid-cols-4 w-full sm:w-auto h-9 p-0.5 bg-muted/80 rounded-lg">
            <TabsTrigger value="todos" className="text-xs font-semibold px-3">
              Todos ({items.length})
            </TabsTrigger>
            <TabsTrigger value="paquetes" className="text-xs font-semibold px-3 gap-1">
              Paquetes
              {paquetesPendientes.length > 0 && (
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 inline-block ml-0.5" />
              )}
            </TabsTrigger>
            <TabsTrigger value="comunicados" className="text-xs font-semibold px-3">
              Avisos
            </TabsTrigger>
            <TabsTrigger value="no_leidos" className="text-xs font-semibold px-3">
              Sin leer
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* Buscador */}
        <div className="relative flex-1 max-w-sm">
          <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por asunto, contenido o tipo..."
            className="w-full pl-9 pr-8 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground text-xs"
            >
              ×
            </button>
          )}
        </div>
      </div>

      {/* 4. BARRA FLOTANTE DE ACCIONES PARA SELECCIONADOS */}
      {seleccionados.length > 0 && (
        <div className="flex items-center justify-between gap-3 p-3 rounded-xl bg-primary/5 border border-primary/20 text-xs animate-saed-fade">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={seleccionarTodosVisibles}
              className="font-semibold text-primary hover:underline"
            >
              {seleccionados.length === mensajesFiltrados.length
                ? 'Deseleccionar todos'
                : `Seleccionar todos (${mensajesFiltrados.length})`}
            </button>
            <span className="text-muted-foreground">•</span>
            <span className="font-medium text-foreground">
              {seleccionados.length} mensaje(s) seleccionado(s)
            </span>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setSeleccionados([])}
              className="h-8 text-xs"
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => setConfirmVaciarSel(true)}
              disabled={vaciandoSel}
              className="h-8 text-xs gap-1.5 font-semibold"
            >
              <Trash2 className="w-3.5 h-3.5" />
              {vaciandoSel ? 'Archivando...' : 'Archivar Seleccionados'}
            </Button>
          </div>
        </div>
      )}

      {/* 5. FEED DE MENSAJES Y NOTIFICACIONES */}
      <div className="space-y-3">
        {loading && (
          <div className="p-12 text-center text-muted-foreground text-xs rounded-2xl border border-dashed border-border">
            <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-primary" />
            Cargando notificaciones del buzón...
          </div>
        )}

        {!loading && error && (
          <div className="p-6 rounded-xl border border-destructive/20 bg-destructive/5 text-center text-xs text-destructive space-y-2">
            <AlertCircle className="w-6 h-6 mx-auto" />
            <p className="font-semibold">Error al conectar con el servidor de buzón</p>
            <p className="text-muted-foreground">{error.message}</p>
            <Button size="sm" variant="outline" onClick={refetchAll}>
              Reintentar
            </Button>
          </div>
        )}

        {!loading && !error && mensajesFiltrados.length === 0 && (
          <EmptyState
            icon="mark_email_read"
            title={search ? 'Sin coincidencias' : 'Buzón al día'}
            subtitle={
              search
                ? 'No encontramos notificaciones con ese término de búsqueda.'
                : 'No tienes mensajes pendientes en esta categoría.'
            }
          >
            {tabFiltro !== 'todos' && (
              <div className="mt-3">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setTabFiltro('todos');
                    setSearch('');
                  }}
                  className="text-xs"
                >
                  Ver todos los mensajes
                </Button>
              </div>
            )}
          </EmptyState>
        )}

        {!loading &&
          mensajesFiltrados.map((item) => {
            const estaLeido =
              item.leido || leidosLocalmente.includes(item.idMensaje);
            const estaSeleccionado = seleccionados.includes(item.idMensaje);
            const pin = extractPin(item);

            return (
              <div
                key={item.idMensaje}
                onClick={() => abrirDetalle(item)}
                className={cn(
                  'saed-card-interactive p-4 sm:p-5 rounded-2xl border transition-all duration-200 cursor-pointer flex flex-col sm:flex-row items-start gap-4 animate-saed-fade',
                  estaLeido
                    ? 'bg-card/70 border-border/70 opacity-80 hover:opacity-100'
                    : 'bg-card border-primary/30 shadow-sm hover:border-primary/60'
                )}
              >
                {/* Checkbox de selección */}
                <div
                  className="pt-1 shrink-0"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={estaSeleccionado}
                    onChange={() => toggleSeleccion(item.idMensaje)}
                    aria-label={`Seleccionar ${item.titulo}`}
                    className="w-4 h-4 rounded border-border text-primary focus:ring-primary/20 cursor-pointer"
                  />
                </div>

                {/* Icono por tipo */}
                <div className="hidden sm:block">{iconoTipo(item.tipo)}</div>

                {/* Contenido principal */}
                <div className="min-w-0 flex-1 space-y-1.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <div className="sm:hidden">{iconoTipo(item.tipo)}</div>
                      <h4
                        className={cn(
                          'text-sm truncate',
                          estaLeido ? 'font-semibold text-foreground' : 'font-bold text-foreground'
                        )}
                      >
                        {item.titulo}
                      </h4>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      {!estaLeido && (
                        <Badge
                          variant="destructive"
                          className="text-[10px] uppercase tracking-wider font-bold"
                        >
                          Nuevo
                        </Badge>
                      )}
                      <Badge variant="outline" className="text-[10px] uppercase font-mono">
                        {item.tipo}
                      </Badge>
                      <span className="text-[11px] text-muted-foreground whitespace-nowrap flex items-center gap-1 font-mono">
                        <Clock className="w-3 h-3 text-muted-foreground/70 shrink-0" />
                        {formatDateTime(item.fecha || item.fechaEnvio || item.fechaCreacion)}
                      </span>
                    </div>
                  </div>

                  {item.cuerpo && (
                    <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
                      {item.cuerpo}
                    </p>
                  )}

                  {/* Badge y acción rápida de PIN de retiro si aplica */}
                  {pin && (
                    <div className="pt-1 flex items-center gap-2">
                      <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-700 dark:text-emerald-300 text-xs">
                        <span className="font-semibold text-[11px] uppercase tracking-wider">PIN de Retiro:</span>
                        <span className="font-mono font-black tracking-widest text-sm bg-emerald-100 dark:bg-emerald-900/60 px-1.5 py-0.5 rounded text-emerald-950 dark:text-emerald-100">
                          {pin}
                        </span>
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={(e) => {
                          e.stopPropagation();
                          navigator.clipboard.writeText(pin);
                          toast.success('PIN de retiro copiado al portapapeles');
                        }}
                        className="h-7 px-2 text-xs gap-1 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-50 dark:hover:bg-emerald-950/50"
                      >
                        <Copy className="w-3 h-3" />
                        Copiar
                      </Button>
                    </div>
                  )}

                  {/* Pie de la tarjeta */}
                  <div className="pt-2 flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground border-t border-border/40">
                    <div className="flex items-center gap-2">
                      <span className="flex items-center gap-1">
                        <Building2 className="w-3.5 h-3.5 text-primary" />
                        Portería / Administración
                      </span>
                      {item.tipo === 'PAQUETE' && (
                        <span className="font-semibold text-amber-600 dark:text-amber-400">
                          • En custodia para entrega
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-3">
                      {!estaLeido && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            marcarLeido(item.idMensaje);
                          }}
                          className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
                        >
                          <Check className="w-3.5 h-3.5" />
                          Marcar leído
                        </button>
                      )}
                      <span className="text-xs text-primary font-semibold flex items-center gap-1 group-hover:translate-x-0.5 transition-transform">
                        Ver detalle →
                      </span>
                    </div>
                  </div>
                </div>

                {/* Miniatura de evidencia fotográfica si existe */}
                {(item.fotoCaptura || item.fotoPaqueteUrl) && (
                  <div
                    className="relative group/thumb shrink-0 self-center sm:self-start mt-2 sm:mt-0"
                    onClick={(e) => {
                      e.stopPropagation();
                      setFotoGrande(imageSrc(item.fotoCaptura || item.fotoPaqueteUrl));
                    }}
                  >
                    <img
                      src={imageSrc(item.fotoCaptura || item.fotoPaqueteUrl)}
                      alt="Evidencia fotográfica"
                      loading="lazy"
                      width="80"
                      height="80"
                      className="w-20 h-20 rounded-xl object-cover border border-border/80 shadow-xs group-hover/thumb:brightness-90 transition-all cursor-zoom-in"
                    />
                    <div className="absolute inset-0 bg-black/30 rounded-xl opacity-0 group-hover/thumb:opacity-100 flex items-center justify-center text-white transition-opacity">
                      <Maximize2 className="w-4 h-4" />
                    </div>
                  </div>
                )}
              </div>
            );
          })}
      </div>

      {/* 6. MODAL DE LECTURA DETALLADA DEL MENSAJE */}
      <Modal
        open={!!mensajeDetalle}
        onClose={() => setMensajeDetalle(null)}
        title={mensajeDetalle?.titulo || 'Detalle del Mensaje'}
        size="lg"
        footer={
          <div className="flex items-center justify-between w-full">
            <span className="text-xs text-muted-foreground font-mono">
              Radicado #{mensajeDetalle?.idMensaje}
            </span>
            <Button onClick={() => setMensajeDetalle(null)} className="text-xs font-semibold">
              Cerrar
            </Button>
          </div>
        }
      >
        {mensajeDetalle && (
          <div className="space-y-4 py-2">
            {/* Metadatos */}
            <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-xl bg-muted/40 border border-border text-xs">
              <div className="flex items-center gap-2">
                <Badge variant="outline" className="text-[10px] font-mono">
                  {mensajeDetalle.tipo}
                </Badge>
                <span className="text-muted-foreground flex items-center gap-1 font-medium font-mono">
                  <Clock className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400 shrink-0" />
                  {formatDateTime(mensajeDetalle.fecha || mensajeDetalle.fechaEnvio || mensajeDetalle.fechaCreacion)}
                </span>
              </div>
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Leído
              </span>
            </div>

            {/* Mensaje de aviso específico según el tipo y PIN */}
            {mensajeDetalle.tipo === 'PAQUETE' && (
              <div className="space-y-3">
                <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/10 flex items-start gap-3 text-xs">
                  <Package className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
                  <div className="space-y-1">
                    <p className="font-bold text-foreground">
                      Paquete en Custodia de Portería
                    </p>
                    <p className="text-muted-foreground">
                      Tu encomienda está registrada y guardada en recepción. Presenta tu código PIN de retiro de 4 dígitos al personal de vigilancia para retirarlo.
                    </p>
                  </div>
                </div>

                {extractPin(mensajeDetalle) && (
                  <div className="p-4 rounded-2xl border border-emerald-500/40 bg-emerald-50/70 dark:bg-emerald-950/40 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div className="space-y-1 text-center sm:text-left">
                      <div className="flex items-center justify-center sm:justify-start gap-2">
                        <span className="text-xs font-bold text-emerald-900 dark:text-emerald-200 uppercase tracking-wide">
                          Código de Retiro PIN
                        </span>
                        <Badge variant="outline" className="text-[10px] bg-emerald-100 dark:bg-emerald-900/60 text-emerald-800 dark:text-emerald-200 border-emerald-300">
                          4 dígitos
                        </Badge>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        Dicta o muestra este código en portería para validar tu identidad y reclamar la encomienda.
                      </p>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <div className="px-5 py-2 rounded-xl bg-card border-2 border-emerald-500 font-mono text-2xl font-black tracking-widest text-emerald-600 dark:text-emerald-400 shadow-sm select-all">
                        {extractPin(mensajeDetalle)}
                      </div>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          navigator.clipboard.writeText(extractPin(mensajeDetalle));
                          toast.success('PIN copiado al portapapeles');
                        }}
                        className="h-10 px-3 text-xs gap-1.5 border-emerald-300 dark:border-emerald-700 hover:bg-emerald-100/50 dark:hover:bg-emerald-900 font-semibold"
                      >
                        <Copy className="w-3.5 h-3.5" />
                        Copiar
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Cuerpo del mensaje */}
            <div className="p-4 rounded-xl border border-border bg-card space-y-2">
              <h4 className="text-sm font-bold text-foreground">
                {mensajeDetalle.titulo}
              </h4>
              <p className="text-xs text-muted-foreground leading-relaxed whitespace-pre-line">
                {mensajeDetalle.cuerpo || 'Sin información adicional suministrada.'}
              </p>
            </div>

            {/* Fotografía de evidencia si fue capturada por portería */}
            {(mensajeDetalle.fotoCaptura || mensajeDetalle.fotoPaqueteUrl) && (
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-muted-foreground">
                    Registro fotográfico tomado en portería:
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setFotoGrande(imageSrc(mensajeDetalle.fotoCaptura || mensajeDetalle.fotoPaqueteUrl))}
                    className="h-7 text-xs text-primary gap-1"
                  >
                    <Maximize2 className="w-3 h-3" />
                    Ampliar foto
                  </Button>
                </div>
                <div
                  className="rounded-2xl border border-border overflow-hidden bg-black/5 cursor-zoom-in group relative max-h-80 flex items-center justify-center"
                  onClick={() => setFotoGrande(imageSrc(mensajeDetalle.fotoCaptura || mensajeDetalle.fotoPaqueteUrl))}
                >
                  <img
                    src={imageSrc(mensajeDetalle.fotoCaptura || mensajeDetalle.fotoPaqueteUrl)}
                    alt="Evidencia fotográfica"
                    className="w-full h-full max-h-80 object-contain rounded-xl"
                  />
                  <div className="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 flex items-center justify-center text-white transition-opacity">
                    <Maximize2 className="w-6 h-6" />
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* 7. VISOR FOTOGRÁFICO LIGHTBOX DE ALTA RESOLUCIÓN */}
      {fotoGrande && (
        <div
          role="dialog"
          aria-label="Foto de evidencia ampliada"
          tabIndex={0}
          onClick={() => setFotoGrande(null)}
          onKeyDown={(e) => e.key === 'Escape' && setFotoGrande(null)}
          className="fixed inset-0 bg-black/85 backdrop-blur-sm z-50 flex flex-col items-center justify-center p-4 cursor-zoom-out animate-saed-fade"
        >
          <div className="relative max-w-4xl max-h-[90vh] flex flex-col items-center">
            <button
              type="button"
              onClick={() => setFotoGrande(null)}
              className="absolute -top-10 right-0 text-white/80 hover:text-white flex items-center gap-1 text-xs"
            >
              <X className="w-4 h-4" /> Cerrar (Esc)
            </button>
            <img
              src={fotoGrande}
              alt="Evidencia ampliada"
              className="max-w-full max-h-[85vh] rounded-2xl border border-white/20 shadow-2xl object-contain bg-black/40"
            />
          </div>
        </div>
      )}

      {/* 8. DIÁLOGOS DE CONFIRMACIÓN */}
      <ConfirmDialog
        open={confirmVaciar}
        onClose={() => setConfirmVaciar(false)}
        onConfirm={vaciarBuzon}
        title="¿Vaciar todo el buzón?"
        message="Esta acción archivará todas las notificaciones de tu buzón. Podrás consultar el histórico comunicándote con la administración."
        confirmLabel="Sí, vaciar buzón"
        danger
      />

      <ConfirmDialog
        open={confirmVaciarSel}
        onClose={() => setConfirmVaciarSel(false)}
        onConfirm={vaciarSeleccionados}
        title={`¿Archivar ${seleccionados.length} mensaje(s)?`}
        message="Los mensajes seleccionados serán retirados de tu bandeja de entrada."
        confirmLabel="Archivar seleccionados"
        danger
      />
    </PageContainer>
  );
}
