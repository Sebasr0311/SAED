import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
  const ayer = new Date(); ayer.setDate(hoy.getDate() - 1);
  const mismoDia = (a, b) => a.getDate() === b.getDate() && a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear();
  if (mismoDia(d, hoy)) return d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  if (mismoDia(d, ayer)) return 'Ayer';
  return d.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit' });
}

function iconoTipo(tipo) {
  if (tipo === 'PAGO') return 'payments';
  if (tipo === 'QUEJA') return 'support_agent';
  return 'notifications';
}

export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  // Timestamp del ultimo "visto" (admin); 0 = nunca visto -> todas cuentan.
  const [visto, setVisto] = useState(() => {
    try { return Number(localStorage.getItem(VISTO_KEY)) || 0; } catch { return 0; }
  });
  const vistoRef = useRef(visto);

  const esAdmin = user?.rol === 'ADMINISTRADOR';
  const verMasRuta = esAdmin ? '/quejas-admin' : '/res-buzon';

  const cargar = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    try {
      const res = esAdmin
        ? await api.get('/notificaciones')
        : await api.get('/buzon');
      const lista = res?.items || res || [];
      setItems(Array.isArray(lista) ? lista : []);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [user, esAdmin]);

  useEffect(() => {
    cargar();
    const t = setInterval(cargar, 45000);
    return () => clearInterval(t);
  }, [cargar]);

  // Contador de no leidas
  const noLeidas = esAdmin
    ? items.filter((it) => {
        const f = new Date(it.fecha);
        return !Number.isNaN(f.getTime()) && f.getTime() > vistoRef.current;
      }).length
    : items.filter((it) => !it.leido).length;

  function alAbrir() {
    setOpen(true);
    if (esAdmin) {
      const ahora = Date.now();
      vistoRef.current = ahora;
      setVisto(ahora);
      try { localStorage.setItem(VISTO_KEY, String(ahora)); } catch { /* noop */ }
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
          className="topbar-icon-btn"
          aria-label={`Notificaciones${noLeidas > 0 ? ` (${noLeidas} sin leer)` : ''}`}
          onClick={alAbrir}
        >
          <span className="material-symbols-outlined" aria-hidden="true">notifications</span>
          {noLeidas > 0 && (
            <span className="notif-badge" aria-hidden="true">
              {noLeidas > 99 ? '99+' : noLeidas}
            </span>
          )}
        </button>
      </SheetTrigger>
      <SheetContent side="right" className="w-full sm:max-w-sm p-0">
        <SheetHeader className="border-b px-4 py-3">
          <SheetTitle className="text-base">Notificaciones</SheetTitle>
        </SheetHeader>
        <div className="notif-list">
          {loading && items.length === 0 && (
            <p className="notif-empty">Cargando...</p>
          )}
          {!loading && items.length === 0 && (
            <p className="notif-empty">No tienes notificaciones</p>
          )}
          {visibles.map((it, i) => (
            <button
              key={`${it.tipo}-${it.id}-${i}`}
              type="button"
              className="notif-item"
              onClick={() => irA(it)}
            >
              <span className={`notif-icon notif-icon-${String(it.tipo || '').toLowerCase()}`} aria-hidden="true">
                <span className="material-symbols-outlined">{iconoTipo(it.tipo)}</span>
              </span>
              <span className="notif-body">
                <span className="notif-title">{it.titulo}</span>
                {it.cuerpo && <span className="notif-cuerpo">{it.cuerpo}</span>}
                <span className="notif-fecha">{fechaCorta(it.fecha)}</span>
              </span>
              {!esAdmin && !it.leido && <span className="notif-punto" aria-hidden="true" />}
            </button>
          ))}
        </div>
        <div className="border-t px-4 py-3">
          <Button variant="outline" className="w-full" onClick={() => { setOpen(false); navigate(verMasRuta); }}>
            Ver más
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
