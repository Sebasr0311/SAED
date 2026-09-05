import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  CreditCard,
  Headphones,
  Megaphone,
} from 'lucide-react';
import api from '../../lib/api.js';
import { useAuth } from '../../lib/AuthContext.jsx';
import { Button } from './Button.jsx';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from './sheet.tsx';

const MAX_VISIBLES = 12;
const VISTO_KEY = 'saed_notif_visto';

function fechaCorta(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const hoy = new Date();
  const ayer = new Date();
  ayer.setDate(hoy.getDate() - 1);
  const mismoDia = (a, b) =>
    a.getDate() === b.getDate() &&
    a.getMonth() === b.getMonth() &&
    a.getFullYear() === b.getFullYear();
  if (mismoDia(d, hoy))
    return d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  if (mismoDia(d, ayer)) return 'Ayer';
  return d.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit' });
}

function getIconoTipo(tipo) {
  if (tipo === 'PAGO') return <CreditCard className="h-4 w-4 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />;
  if (tipo === 'QUEJA') return <Headphones className="h-4 w-4 text-amber-600 dark:text-amber-400" aria-hidden="true" />;
  return <Megaphone className="h-4 w-4 text-primary" aria-hidden="true" />;
}

export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
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
    try {
      const res = await api.get('/buzon/avisos');
      const lista = res?.items || res || [];
      const raw = Array.isArray(lista) ? lista : [];
      setItems(
        raw.map((it) => ({
          id: it.idComunicado ?? it.id,
          tipo: 'AVISO',
          titulo: it.titulo || it.tituloComunicado || 'Aviso',
          cuerpo: it.contenido || it.mensaje || '',
          fecha: it.fechaPublicacion || it.fecha_publicacion || it.fecha,
          leido: it.estado === 'LEIDO',
          ruta: '/avisos',
        }))
      );
    } catch {
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

  const noLeidas = esAdmin
    ? items.filter((it) => {
        const f = new Date(it.fecha);
        return !Number.isNaN(f.getTime()) && f.getTime() > visto;
      }).length
    : items.filter((it) => !it.leido).length;

  function alAbrir() {
    setOpen(true);
    if (esAdmin) {
      const ahora = Date.now();
      setVisto(ahora);
      try {
        localStorage.setItem(VISTO_KEY, String(ahora));
      } catch {
        /* noop */
      }
    }
  }

  function irA(it) {
    setOpen(false);
    if (!esAdmin && !it.leido && it.idMensaje) {
      api.put(`/buzon/${it.idMensaje}/leido`).catch(() => {});
    }
    navigate(it.ruta || verMasRuta);
  }

  const visibles = items.slice(0, MAX_VISIBLES);

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <button
          type="button"
          className="relative inline-flex items-center justify-center w-10 h-10 min-h-[44px] min-w-[44px] rounded-lg border border-border/70 bg-background hover:bg-muted/60 text-muted-foreground hover:text-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1"
          aria-label={`Notificaciones${noLeidas > 0 ? ` (${noLeidas} sin leer)` : ''}`}
          onClick={alAbrir}
        >
          <Bell className="h-5 w-5" aria-hidden="true" />
          {noLeidas > 0 && (
            <span
              className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white shadow-sm ring-2 ring-background animate-pulse"
              aria-hidden="true"
            >
              {noLeidas > 99 ? '99+' : noLeidas}
            </span>
          )}
        </button>
      </SheetTrigger>
      <SheetContent side="right" className="w-full sm:max-w-sm p-0 flex flex-col">
        <SheetHeader className="border-b px-4 py-3.5 flex-shrink-0">
          <SheetTitle className="text-base font-semibold flex items-center gap-2">
            <Bell className="h-4 w-4 text-primary" aria-hidden="true" />
            Notificaciones
            {noLeidas > 0 && (
              <span className="ml-auto text-xs font-normal text-muted-foreground">
                {noLeidas} nuevas
              </span>
            )}
          </SheetTitle>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto divide-y divide-border/60">
          {loading && items.length === 0 && (
            <p className="p-8 text-center text-xs text-muted-foreground">Cargando...</p>
          )}
          {!loading && items.length === 0 && (
            <p className="p-8 text-center text-xs text-muted-foreground">No tienes notificaciones</p>
          )}
          {visibles.map((it, i) => (
            <button
              key={`${it.tipo}-${it.id}-${i}`}
              type="button"
              className="w-full flex items-start gap-3 p-3.5 text-left transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:bg-muted/60"
              onClick={() => irA(it)}
            >
              <div className="mt-0.5 p-2 rounded-lg bg-muted/80 flex-shrink-0">
                {getIconoTipo(it.tipo)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-foreground truncate">{it.titulo}</p>
                {it.cuerpo && (
                  <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5">{it.cuerpo}</p>
                )}
                <span className="text-[11px] text-muted-foreground/80 mt-1 block">
                  {fechaCorta(it.fecha)}
                </span>
              </div>
              {!esAdmin && !it.leido && (
                <span className="h-2 w-2 rounded-full bg-primary mt-1.5 flex-shrink-0" aria-hidden="true" />
              )}
            </button>
          ))}
        </div>
        <div className="border-t p-3 flex-shrink-0 bg-muted/20">
          <Button
            variant="outline"
            className="w-full text-xs font-semibold"
            onClick={() => {
              setOpen(false);
              navigate(verMasRuta);
            }}
          >
            Ver más
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
