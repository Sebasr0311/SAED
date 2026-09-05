import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  CreditCard,
  Package,
  Users,
  AlertTriangle,
  Megaphone,
  Info,
  Headphones,
  CheckCheck,
  ArrowRight,
  RotateCcw,
  AlertCircle,
} from 'lucide-react';
import api from '../../lib/api.js';
import { useAuth } from '../../lib/AuthContext.jsx';

const MAX_VISIBLES = 12;
const VISTO_KEY = 'saed_notif_visto';

function formatoTiempo(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHoras = Math.floor(diffMin / 60);
  const diffDias = Math.floor(diffHoras / 24);

  if (diffMin < 1) return 'Ahora';
  if (diffMin < 60) return `Hace ${diffMin} min`;
  if (diffHoras < 24) return `Hace ${diffHoras} h`;
  if (diffDias === 1) return 'Ayer';
  if (diffDias < 7) return `Hace ${diffDias} d`;
  return d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short' });
}

function clasificarTipo(item) {
  const t = (item.tipo || '').toUpperCase();
  const tit = (item.titulo || '').toUpperCase();
  const c = (item.cuerpo || '').toUpperCase();

  if (t === 'PAGO' || tit.includes('PAGO') || tit.includes('CUOTA') || c.includes('PAGO')) return 'PAGO';
  if (t === 'PAQUETE' || tit.includes('PAQUETE') || tit.includes('ENCOMIENDA') || c.includes('PAQUETE')) return 'PAQUETE';
  if (t === 'VISITA' || tit.includes('VISITA') || tit.includes('INGRESO') || c.includes('VISITANTE')) return 'VISITA';
  if (t === 'ALERTA' || tit.includes('ALERTA') || tit.includes('MULTA') || tit.includes('SANCION') || tit.includes('URGENTE')) return 'ALERTA';
  if (t === 'QUEJA' || tit.includes('QUEJA') || tit.includes('PQRS') || tit.includes('RECLAMO')) return 'QUEJA';
  if (t === 'AVISO' || tit.includes('AVISO') || tit.includes('COMUNICADO') || c.includes('COMUNICADO')) return 'AVISO';
  return 'INFO';
}

function getIconoConfig(tipo) {
  switch (tipo) {
    case 'PAGO':
      return {
        icon: <CreditCard className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400 border-emerald-100 dark:border-emerald-900/40',
      };
    case 'PAQUETE':
      return {
        icon: <Package className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-indigo-50 text-indigo-600 dark:bg-indigo-950/40 dark:text-indigo-400 border-indigo-100 dark:border-indigo-900/40',
      };
    case 'VISITA':
      return {
        icon: <Users className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400 border-violet-100 dark:border-violet-900/40',
      };
    case 'ALERTA':
      return {
        icon: <AlertTriangle className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 border-amber-100 dark:border-amber-900/40',
      };
    case 'QUEJA':
      return {
        icon: <Headphones className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-orange-50 text-orange-600 dark:bg-orange-950/40 dark:text-orange-400 border-orange-100 dark:border-orange-900/40',
      };
    case 'AVISO':
      return {
        icon: <Megaphone className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 border-sky-100 dark:border-sky-900/40',
      };
    default:
      return {
        icon: <Info className="h-4 w-4" aria-hidden="true" />,
        color: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300 border-slate-200 dark:border-slate-700',
      };
  }
}

export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [activeTab, setActiveTab] = useState('todas'); // 'todas' | 'noLeidas'

  const containerRef = useRef(null);
  const buttonRef = useRef(null);

  const [visto, setVisto] = useState(() => {
    try {
      return Number(localStorage.getItem(VISTO_KEY)) || 0;
    } catch {
      return 0;
    }
  });

  const esAdmin =
    user?.rol === 'ADMIN_PROPIEDAD' ||
    user?.rol === 'ADMIN_ORGANIZACION' ||
    user?.rol === 'SUPERADMIN';
  const verMasRuta = esAdmin ? '/quejas-admin' : '/res-buzon';

  const cargar = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setError(false);
    try {
      const res = await api.get('/buzon/avisos');
      const lista = res?.items || res || [];
      const raw = Array.isArray(lista) ? lista : [];
      setItems(
        raw.map((it) => ({
          id: it.idComunicado ?? it.id,
          tipo: it.tipo || 'AVISO',
          titulo: it.titulo || it.tituloComunicado || 'Aviso',
          cuerpo: it.contenido || it.mensaje || '',
          fecha: it.fechaPublicacion || it.fecha_publicacion || it.fecha,
          leido: it.estado === 'LEIDO' || it.leido === true,
          ruta: '/avisos',
          idMensaje: it.idMensaje,
        }))
      );
    } catch {
      setError(true);
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    cargar();
    const t = setInterval(cargar, 45000);
    return () => clearInterval(t);
  }, [cargar]);

  // Click outside listener
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('pointerdown', handleClickOutside);
    return () => document.removeEventListener('pointerdown', handleClickOutside);
  }, [open]);

  // Escape key listener
  useEffect(() => {
    if (!open) return;
    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open]);

  const esNoLeida = useCallback(
    (it) => {
      if (it.leido) return false;
      if (esAdmin) {
        if (!visto) return true;
        const f = new Date(it.fecha);
        return !Number.isNaN(f.getTime()) && f.getTime() > visto;
      }
      return !it.leido;
    },
    [esAdmin, visto]
  );

  const noLeidasCount = useMemo(() => {
    return items.filter((it) => esNoLeida(it)).length;
  }, [items, esNoLeida]);

  const alAbrir = () => {
    setOpen((prev) => !prev);
  };

  const marcarTodasLeidas = () => {
    if (esAdmin) {
      const ahora = Date.now();
      setVisto(ahora);
      try {
        localStorage.setItem(VISTO_KEY, String(ahora));
      } catch {
        /* noop */
      }
    } else {
      items.forEach((it) => {
        if (!it.leido && it.idMensaje) {
          api.put(`/buzon/${it.idMensaje}/leido`).catch(() => {});
        }
      });
      setItems((prev) => prev.map((it) => ({ ...it, leido: true })));
    }
  };

  const irA = (it) => {
    setOpen(false);
    if (!esAdmin && !it.leido && it.idMensaje) {
      api.put(`/buzon/${it.idMensaje}/leido`).catch(() => {});
    }
    navigate(it.ruta || verMasRuta);
  };

  const filteredItems = useMemo(() => {
    if (activeTab === 'noLeidas') {
      return items.filter((it) => esNoLeida(it));
    }
    return items;
  }, [items, activeTab, esNoLeida]);

  const visibles = filteredItems.slice(0, MAX_VISIBLES);

  return (
    <div className="relative" ref={containerRef}>
      {/* Botón de Campana */}
      <button
        ref={buttonRef}
        id="notification-bell-button"
        type="button"
        className={`relative inline-flex items-center justify-center w-10 h-10 min-h-[44px] min-w-[44px] rounded-xl border transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
          open
            ? 'border-primary/50 bg-primary/10 text-primary shadow-xs'
            : 'border-border/70 bg-card hover:bg-muted/60 text-muted-foreground hover:text-foreground shadow-xs'
        }`}
        aria-label={`Notificaciones${noLeidasCount > 0 ? ` (${noLeidasCount} sin leer)` : ''}`}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-controls="notification-popover"
        onClick={alAbrir}
      >
        <Bell className={`h-5 w-5 transition-transform duration-200 ${open ? 'scale-105' : ''}`} aria-hidden="true" />
        {noLeidasCount > 0 && (
          <span
            className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white shadow-xs ring-2 ring-background animate-pulse"
            aria-hidden="true"
          >
            {noLeidasCount > 99 ? '99+' : noLeidasCount}
          </span>
        )}
      </button>

      {/* Popover de Notificaciones */}
      {open && (
        <div
          id="notification-popover"
          role="dialog"
          aria-label="Centro de notificaciones"
          aria-labelledby="notification-title"
          className="fixed sm:absolute right-3 left-3 sm:left-auto sm:right-0 top-16 sm:top-full sm:mt-2 z-50 sm:w-[410px] max-h-[85vh] sm:max-h-[580px] flex flex-col rounded-2xl bg-card border border-border/80 shadow-2xl shadow-slate-900/15 overflow-hidden animate-in fade-in-0 zoom-in-95 duration-150"
        >
          {/* Header del Popover */}
          <div className="border-b border-border/70 px-4 pt-4 pb-0 bg-card shrink-0">
            <div className="flex items-center justify-between pb-3">
              <div className="flex items-center gap-2">
                <div className="p-1.5 rounded-lg bg-primary/10 text-primary">
                  <Bell className="h-4 w-4" aria-hidden="true" />
                </div>
                <h2 id="notification-title" className="text-sm font-bold text-foreground tracking-tight">
                  Notificaciones
                </h2>
                {noLeidasCount > 0 && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400 border border-rose-200 dark:border-rose-900/50">
                    {noLeidasCount} nuevas
                  </span>
                )}
              </div>

              {noLeidasCount > 0 && (
                <button
                  type="button"
                  onClick={marcarTodasLeidas}
                  title="Marcar todas como leídas"
                  className="inline-flex items-center gap-1 text-[11px] font-medium text-muted-foreground hover:text-foreground px-2 py-1 rounded-md hover:bg-muted/70 transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
                >
                  <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" />
                  <span className="hidden xs:inline">Leídas</span>
                </button>
              )}
            </div>

            {/* Tabs de Filtro */}
            <div className="flex items-center gap-4 border-b border-border/40 text-xs" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'todas'}
                onClick={() => setActiveTab('todas')}
                className={`pb-2.5 font-medium transition-colors relative focus-visible:outline-none ${
                  activeTab === 'todas'
                    ? 'text-foreground font-semibold'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                Todas ({items.length})
                {activeTab === 'todas' && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
                )}
              </button>

              <button
                type="button"
                role="tab"
                aria-selected={activeTab === 'noLeidas'}
                onClick={() => setActiveTab('noLeidas')}
                className={`pb-2.5 font-medium transition-colors relative focus-visible:outline-none ${
                  activeTab === 'noLeidas'
                    ? 'text-foreground font-semibold'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                No leídas ({noLeidasCount})
                {activeTab === 'noLeidas' && (
                  <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
                )}
              </button>
            </div>
          </div>

          {/* Lista de Notificaciones con scroll */}
          <div className="flex-1 overflow-y-auto divide-y divide-border/50">
            {loading && items.length === 0 && (
              <div className="p-4 space-y-3">
                {[1, 2, 3].map((n) => (
                  <div key={n} className="flex items-start gap-3 p-2 animate-pulse">
                    <div className="h-9 w-9 rounded-xl bg-muted/70 shrink-0" />
                    <div className="flex-1 space-y-2 py-0.5">
                      <div className="h-3 bg-muted/80 rounded-sm w-3/4" />
                      <div className="h-2.5 bg-muted/60 rounded-sm w-5/6" />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {error && (
              <div className="p-8 text-center flex flex-col items-center justify-center">
                <div className="h-10 w-10 rounded-xl bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400 flex items-center justify-center mb-2.5">
                  <AlertCircle className="h-5 w-5" aria-hidden="true" />
                </div>
                <p className="text-xs font-semibold text-foreground">Error al cargar notificaciones</p>
                <button
                  type="button"
                  onClick={cargar}
                  className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-primary hover:text-primary/80 px-3 py-1.5 rounded-lg border border-border/80 hover:bg-muted/50 transition-colors"
                >
                  <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                  Reintentar
                </button>
              </div>
            )}

            {!loading && !error && visibles.length === 0 && (
              <div className="p-8 text-center flex flex-col items-center justify-center">
                <div className="h-12 w-12 rounded-2xl bg-muted/50 text-muted-foreground/60 flex items-center justify-center mb-3">
                  <Bell className="h-6 w-6" aria-hidden="true" />
                </div>
                <p className="text-xs font-semibold text-foreground">
                  {activeTab === 'noLeidas' ? 'Todo al día' : 'Sin notificaciones'}
                </p>
                <p className="text-[11px] text-muted-foreground mt-1 max-w-[220px]">
                  {activeTab === 'noLeidas'
                    ? 'No tienes notificaciones pendientes de revisión.'
                    : 'Te avisaremos cuando haya novedades o avisos importantes.'}
                </p>
              </div>
            )}

            {!loading &&
              visibles.map((it, i) => {
                const tipoCalculado = clasificarTipo(it);
                const { icon, color } = getIconoConfig(tipoCalculado);
                const noLeida = esNoLeida(it);

                return (
                  <button
                    key={`${it.tipo}-${it.id}-${i}`}
                    type="button"
                    className={`w-full flex items-start gap-3 p-3.5 text-left transition-colors duration-150 focus-visible:outline-none focus-visible:bg-muted/60 ${
                      noLeida
                        ? 'bg-slate-50/80 dark:bg-slate-900/40 hover:bg-slate-100/90 dark:hover:bg-slate-800/60'
                        : 'bg-transparent hover:bg-muted/40'
                    }`}
                    onClick={() => irA(it)}
                  >
                    {/* Indicador de lectura */}
                    <div className="pt-2 shrink-0">
                      <span
                        className={`block h-2 w-2 rounded-full transition-colors ${
                          noLeida ? 'bg-primary ring-2 ring-primary/20' : 'bg-transparent'
                        }`}
                        aria-hidden="true"
                      />
                    </div>

                    {/* Icono contextual */}
                    <div className={`p-2 rounded-xl border shrink-0 ${color}`}>
                      {icon}
                    </div>

                    {/* Contenido textual */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-baseline justify-between gap-2">
                        <p
                          className={`text-xs truncate ${
                            noLeida
                              ? 'font-semibold text-foreground'
                              : 'font-medium text-foreground/85'
                          }`}
                        >
                          {it.titulo}
                        </p>
                        <span className="text-[10px] text-muted-foreground/80 shrink-0 font-mono">
                          {formatoTiempo(it.fecha)}
                        </span>
                      </div>

                      {it.cuerpo && (
                        <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5 leading-relaxed">
                          {it.cuerpo}
                        </p>
                      )}
                    </div>
                  </button>
                );
              })}
          </div>

          {/* Footer del Popover */}
          <div className="border-t border-border/60 p-2.5 bg-muted/20 shrink-0">
            <button
              type="button"
              className="w-full flex items-center justify-center gap-1.5 py-2 px-3 text-xs font-semibold text-primary hover:text-primary/80 hover:bg-primary/5 rounded-lg transition-colors group focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
              onClick={() => {
                setOpen(false);
                navigate(verMasRuta);
              }}
            >
              <span>Ver todas las notificaciones</span>
              <ArrowRight className="w-3.5 h-3.5 transition-transform duration-150 group-hover:translate-x-0.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
