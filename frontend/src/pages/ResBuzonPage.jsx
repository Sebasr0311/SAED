import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { formatDate, formatMiles, imageSrc } from '../lib/utils.js';

export default function ResBuzonPage() {
  const { user } = useAuth();
  const [confirmVaciar, setConfirmVaciar] = useState(false);
  const [confirmVaciarSel, setConfirmVaciarSel] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);
  const [seleccionados, setSeleccionados] = useState([]);
  const [vaciandoSel, setVaciandoSel] = useState(false);
  const vaciandoSelRef = useRef(false); // patr�n anti doble-submit generalizado

  const { data, loading, error, refetch } = useFetch(
    () => api.get(`/buzon`),
    [user]
  );

  const items = data?.items || data || [];

  // Mensajes marcados como leidos localmente mientras llega el refetch
  const [leidosLocalmente, setLeidosLocalmente] = useState([]);

  async function marcarLeido(idMensaje) {
    try {
      await api.put(`/buzon/${idMensaje}/leido`);
      setLeidosLocalmente((prev) => (prev.includes(idMensaje) ? prev : [...prev, idMensaje]));
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  async function vaciar() {
    try {
      await api.put('/buzon/vaciar');
      toast.success('Buz�n vaciado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmVaciar(false);
    }
  }

  function toggleSeleccion(idMensaje) {
    setSeleccionados((prev) =>
      prev.includes(idMensaje) ? prev.filter((x) => x !== idMensaje) : [...prev, idMensaje]
    );
  }

  async function vaciarSeleccionados() {
    if (vaciandoSelRef.current) return; // doble submit
    setConfirmVaciarSel(false);
    vaciandoSelRef.current = true;
    setVaciandoSel(true);
    try {
      await api.put('/buzon/vaciar-multi', { ids: seleccionados });
      toast.success('Mensajes eliminados');
      setSeleccionados([]);
      refetch();
    } catch (err) {
      // 403 posible: algun ID ya no pertenece al apartamento (todo-o-nada en backend).
      // Nunca asumir exito parcial: sincronizar la lista real y limpiar la seleccion.
      toast.error(err.message);
      setSeleccionados([]);
      refetch();
    } finally {
      vaciandoSelRef.current = false;
      setVaciandoSel(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Buz�n"
        subtitle="Notificaciones del administrador"
        action={
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
            {seleccionados.length > 0 && (
              <>
                <Button variant="outline" size="sm" onClick={() => setSeleccionados([])}>
                  Limpiar ({seleccionados.length})
                </Button>
                <Button variant="danger" size="sm" onClick={() => setConfirmVaciarSel(true)} disabled={vaciandoSel}>
                  Vaciar seleccionados ({seleccionados.length})
                </Button>
              </>
            )}
            {items.length > 0 && (
              <Button variant="danger" onClick={() => setConfirmVaciar(true)}>
                Vaciar buz�n
              </Button>
            )}
          </div>
        }
      />
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {loading && <div className="card empty-state">Cargando...</div>}
        {!loading && error && (
          <div className="card empty-state" style={{ color: 'var(--error)' }}>Error al cargar el buz�n: {error?.message}</div>
        )}
        {!loading && !error && items.length === 0 && (
          <div className="card empty-state">Buz�n vac�o</div>
        )}
        {items.map((it) => {
          const leido = it.leido || leidosLocalmente.includes(it.idMensaje);
          return (
            <div
              key={it.idMensaje}
              className="card"
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: '10px',
                borderLeft: leido ? 'none' : '4px solid var(--primary)',
                opacity: leido ? 0.7 : 1,
                cursor: leido ? 'default' : 'pointer',
              }}
              onClick={() => {
                if (!leido) marcarLeido(it.idMensaje);
              }}
            >
              <input
                type="checkbox"
                checked={seleccionados.includes(it.idMensaje)}
                onChange={() => toggleSeleccion(it.idMensaje)}
                onClick={(e) => e.stopPropagation()}
                aria-label={`Seleccionar: ${it.titulo}`}
                style={{ marginTop: '4px', flexShrink: 0 }}
              />
              <div style={{ flex: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--on-surface)' }}>{it.titulo}</div>
                  {it.cuerpo && (
                    <div style={{ marginTop: '4px', fontSize: '13px', color: 'var(--text-secondary)' }}>{it.cuerpo}</div>
                  )}
                  <div style={{ marginTop: '8px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <span className="badge badge-info" style={{ fontSize: '10px' }}>
                      {it.tipo}
                    </span>
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{formatDate(it.fechaCreacion)}</span>
                    {!leido && (
                      <span className="badge badge-warn" style={{ fontSize: '10px' }}>
                        Nuevo
                      </span>
                    )}
                  </div>
                </div>
                {it.fotoCaptura && (
                  <img
                  src={imageSrc(it.fotoCaptura)}
                  alt="Foto"
                  loading="lazy"
                    style={{ width: '80px', height: '80px', objectFit: 'cover', borderRadius: '8px', cursor: 'zoom-in', flexShrink: 0 }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setFotoGrande(imageSrc(it.fotoCaptura));
                    }}
                  />
                )}
              </div>
            </div>
          );
        })}
      </div>

      <Modal
        open={confirmVaciar}
        onClose={() => setConfirmVaciar(false)}
        title="Vaciar buz�n"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmVaciar(false)}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={vaciar}>
              Vaciar
            </Button>
          </>
        }
      >
        <p>�Marcar todos los mensajes como le�dos y entregados? Esta acci�n no se puede deshacer.</p>
      </Modal>

      <Modal
        open={confirmVaciarSel}
        onClose={() => setConfirmVaciarSel(false)}
        title="Vaciar seleccionados"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmVaciarSel(false)}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={vaciarSeleccionados} disabled={vaciandoSel}>
              {vaciandoSel ? 'Vaciando...' : 'Vaciar seleccionados'}
            </Button>
          </>
        }
      >
        <p>�Marcar los {seleccionados.length} mensajes seleccionados como le�dos y entregados? Esta acci�n no se puede deshacer.</p>
      </Modal>

      {fotoGrande && (
        <div
          onClick={() => setFotoGrande(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'zoom-out',
          }}
        >
          <img
            src={fotoGrande}
            alt="Foto"
            style={{ maxWidth: '90%', maxHeight: '90%', borderRadius: '8px' }}
          />
        </div>
      )}
    </div>
  );
}

