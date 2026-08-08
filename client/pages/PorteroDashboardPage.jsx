import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import Toast from '../components/ui/Toast.jsx';
import { formatDate, formatCurrency, imageSrc } from '../lib/utils.js';

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

function VideoCamara({ onCapture, label = 'Capturar Foto' }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [stream, setStream] = useState(null);
  const [error, setError] = useState('');

  async function iniciar(facingMode) {
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { facingMode, width: { ideal: 640 }, height: { ideal: 480 } },
        audio: false,
      });
      setStream(s);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = s;
          videoRef.current.play();
        }
      }, 50);
    } catch (e) {
      setError('No se pudo acceder a la cámara: ' + e.message);
    }
  }
  function detener() {
    if (stream) {
      stream.getTracks().forEach((t) => t.stop());
      setStream(null);
    }
  }
  function capturar() {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
    onCapture(dataUrl.split(',')[1]);
    detener();
  }

  return (
    <div>
      {error && <p className="field-error">{error}</p>}
      {stream && (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          style={{ width: '100%', maxHeight: '280px', borderRadius: '8px', background: '#000' }}
        />
      )}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
        {!stream && (
          <>
            <Button onClick={() => iniciar('environment')}>Cámara Trasera</Button>
            <Button variant="outline" onClick={() => iniciar('user')}>
              Cámara Frontal
            </Button>
          </>
        )}
        {stream && <Button onClick={capturar}>{label}</Button>}
        {stream && (
          <Button variant="outline" onClick={detener}>
            Cancelar
          </Button>
        )}
      </div>
    </div>
  );
}

const QUICK = [
  { label: 'Registrar Visita', icon: 'edit_note', path: '/visitas' },
  { label: 'Registrar Paquete', icon: 'inventory_2', path: '/paquetes' },
  { label: 'Gestionar Parqueaderos', icon: 'local_parking', path: '/parqueaderos' },
  { label: 'Escáner QR', icon: 'qr_code_scanner', path: '/escanner-qr' },
];

function ModalAvisoRuido({ open, onClose, onConfirm, apartamentos }) {
  const [idApartamento, setIdApartamento] = useState('');
  const [cuerpo, setCuerpo] = useState('Ruido excesivo en zona común. Por favor moderar el volumen.');
  const [sending, setSending] = useState(false);

  async function send() {
    if (!idApartamento) return;
    setSending(true);
    try {
      await api.post('/buzon/aviso-ruido', {
        idApartamento: Number(idApartamento),
        cuerpo,
      });
      onConfirm();
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Aviso de Ruido"
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={sending}>
            Cancelar
          </Button>
          <Button onClick={send} disabled={sending || !idApartamento}>
            {sending ? 'Enviando...' : 'Enviar Aviso'}
          </Button>
        </>
      }
    >
      <div className="form-group">
        <Select
          id="avisoApto"
          label="Apartamento"
          value={idApartamento}
          onChange={(e) => setIdApartamento(e.target.value)}
        >
          <option value="">— Seleccionar —</option>
          {(apartamentos?.items || apartamentos || [])
            .filter((a) => a.estado === 'OCUPADO')
            .map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
        </Select>
      </div>
      <div className="form-group">
        <Textarea
          id="avisoCuerpo"
          label="Mensaje"
          rows={3}
          value={cuerpo}
          onChange={(e) => setCuerpo(e.target.value)}
        />
      </div>
    </Modal>
  );
}

function ModalGenerarMulta({ open, onClose, onConfirm, apartamentos, quejasRuido, tipoInicial }) {
  const [tipo, setTipo] = useState(tipoInicial || 'RUIDO');
  const [idApartamento, setIdApartamento] = useState('');
  const [idMensaje, setIdMensaje] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [foto, setFoto] = useState(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  async function send() {
    if (!idApartamento) {
      setError('Seleccione un apartamento');
      return;
    }
    if (tipo === 'PARQUEADERO' && !foto) {
      setError('La foto de evidencia es obligatoria para multa de parqueadero');
      return;
    }
    setSending(true);
    setError('');
    try {
      const payload = { tipo, idApartamento: Number(idApartamento), descripcion };
      if (idMensaje) payload.idMensaje = Number(idMensaje);
      if (foto) payload.fotoEvidencia = foto;
      await api.post('/multas/generar', payload);
      onConfirm();
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Generar Multa (${tipo})`}
      size="md"
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={sending}>
            Cancelar
          </Button>
          <Button onClick={send} disabled={sending}>
            {sending ? 'Generando...' : 'Generar Multa'}
          </Button>
        </>
      }
    >
      {error && <div className="login-error-msg" style={{ marginBottom: '12px' }}>{error}</div>}

      <div className="form-group">
        <Select id="multaTipo" label="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value)}>
          <option value="RUIDO">Ruido</option>
          <option value="PARQUEADERO">Parqueadero</option>
        </Select>
      </div>

      {tipo === 'RUIDO' && quejasRuido && quejasRuido.length > 0 && (
        <div className="form-group">
          <Select id="idMensaje" label="Generar desde aviso de ruido previo (opcional)" value={idMensaje} onChange={(e) => setIdMensaje(e.target.value)}>
            <option value="">— Generar multa directa —</option>
            {quejasRuido.map((q) => (
              <option key={q.idMensaje} value={q.idMensaje}>
                {formatDate(q.fechaCreacion)} — Apto {q.numeroApartamento} — {q.titulo}
              </option>
            ))}
          </Select>
          <p className="field-error" style={{ marginTop: '4px' }}>
            Deben haber pasado al menos 20 minutos desde el aviso (validado por el backend).
          </p>
        </div>
      )}

      <div className="form-group">
        <Select
          id="multaApto"
          label="Apartamento"
          value={idApartamento}
          onChange={(e) => setIdApartamento(e.target.value)}
        >
          <option value="">— Seleccionar —</option>
          {(apartamentos?.items || apartamentos || [])
            .filter((a) => a.estado === 'OCUPADO')
            .map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
        </Select>
      </div>

      <div className="form-group">
        <Textarea
          id="multaDesc"
          label="Descripción"
          rows={2}
          value={descripcion}
          onChange={(e) => setDescripcion(e.target.value)}
          placeholder={tipo === 'RUIDO' ? 'Multa por ruido excesivo' : 'Vehículo mal estacionado'}
        />
      </div>

      {tipo === 'PARQUEADERO' && (
        <div className="form-group">
          <label style={{ fontSize: '12px', fontWeight: 600, color: '#475569' }}>
            Foto de evidencia (obligatoria)
          </label>
          <VideoCamara onCapture={setFoto} label="Capturar Evidencia" />
          {foto && (
            <img
              src={imageSrc(foto)}
              alt="Evidencia"
              style={{ maxWidth: '200px', borderRadius: '8px', marginTop: '8px' }}
            />
          )}
        </div>
      )}
    </Modal>
  );
}

function ModalPaquetes({ open, onClose, onConfirm }) {
  const { data: paquetes, loading, refetch } = useFetch(
    () => open ? api.get('/buzon/paquetes') : Promise.resolve([]),
    [open]
  );
  const [detalle, setDetalle] = useState(null);
  const [marcaId, setMarcaId] = useState(null);

  async function marcarEntregado(idMensaje) {
    setMarcaId(idMensaje);
    try {
      await api.put(`/buzon/${idMensaje}/entregado`);
      onConfirm();
      refetch();
    } catch (err) {
      showToast({ message: err.message || 'No se pudo marcar como entregado', type: 'error' });
    } finally {
      setMarcaId(null);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Paquetes Pendientes"
      size="lg"
      footer={
        <Button variant="outline" onClick={onClose}>
          Cerrar
        </Button>
      }
    >
      {loading && <p>Cargando...</p>}
      <DataTable
        columns={[
          { key: 'idMensaje', label: 'ID', width: 60 },
          { key: 'numeroApartamento', label: 'Apto' },
          { key: 'nombreResidente', label: 'Residente' },
          { key: 'fechaCreacion', label: 'Recibido', render: (r) => formatDate(r.fechaCreacion) },
          {
            key: 'foto',
            label: '',
            render: (row) => (
              <Button variant="outline" onClick={() => setDetalle(row)} style={{ padding: '2px 8px', fontSize: '11px' }}>
                Ver
              </Button>
            ),
          },
          {
            key: 'actions',
            label: '',
            render: (row) => (
              <Button
                onClick={() => marcarEntregado(row.idMensaje)}
                disabled={marcaId === row.idMensaje}
                style={{ padding: '4px 10px', fontSize: '11px' }}
              >
                {marcaId === row.idMensaje ? '...' : 'Entregado'}
              </Button>
            ),
          },
        ]}
        rows={paquetes?.items || paquetes || []}
        empty="No hay paquetes pendientes"
        keyField="idMensaje"
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle del Paquete" size="md">
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="detail-row">
              <span>Apartamento</span>
              <span>{detalle.numeroApartamento}</span>
            </div>
            <div className="detail-row">
              <span>Residente</span>
              <span>{detalle.nombreResidente}</span>
            </div>
            <div className="detail-row">
              <span>Recibido</span>
              <span>{formatDate(detalle.fechaCreacion)}</span>
            </div>
            {detalle.fotoCaptura && (
              <img
                src={imageSrc(detalle.fotoCaptura)}
                alt="Foto"
                style={{ maxWidth: '100%', borderRadius: '8px' }}
              />
            )}
          </div>
        )}
      </Modal>
    </Modal>
  );
}

export default function PorteroDashboardPage() {
  const navigate = useNavigate();
  const [toast, setToast] = useState(null);
  const [modalAviso, setModalAviso] = useState(false);
  const [modalMulta, setModalMulta] = useState(null); // 'RUIDO' | 'PARQUEADERO' | null
  const [modalPaquetes, setModalPaquetes] = useState(false);

  const { data: visitasHoy } = useFetch(() => api.get('/visitas/hoy'), []);
  const { data: parqueaderos } = useFetch(() => api.get('/parqueaderos?estado=DISPONIBLE'), []);
  const { data: paquetes } = useFetch(() => api.get('/buzon/paquetes-pendientes'), []);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos'), []);
  const { data: quejasRuido } = useFetch(
    () => (modalMulta === 'RUIDO' ? api.get('/buzon/quejas-ruido-pendientes') : Promise.resolve([])),
    [modalMulta]
  );

  const visitasHoyCount = visitasHoy?.items?.length ?? visitasHoy?.length ?? '—';
  const visitasActivas = (visitasHoy?.items || visitasHoy || []).filter((v) => v.estado === 'ACTIVA' || v.estado === 'PENDIENTE').length;
  const parqDisponibles = (parqueaderos?.items || parqueaderos || []).filter((p) => p.esVisitante).length;
  const paquetesCount = paquetes?.count ?? '—';

  function showToast(t) {
    setToast(t);
  }

  return (
    <div>
      <PageHeader title="Panel de Portería" />
      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <Stat icon="today" value={visitasHoyCount} label="Visitas Hoy" color="amber" />
        <Stat icon="how_to_reg" value={visitasActivas} label="Visitas Activas" color="cyan" />
        <Stat icon="local_parking" value={parqDisponibles} label="Parqueaderos Visitantes" color="green" />
        <Stat icon="inventory_2" value={paquetesCount} label="Paquetes Pendientes" color="orange" />
      </div>

      <div className="card">
        <h3 className="card-title">
          <span className="material-symbols-outlined">dashboard</span>
          Acciones Rápidas
        </h3>
        <div className="card-grid-4" style={{ marginTop: '12px' }}>
          <button onClick={() => setModalAviso(true)} className="action-card" style={{ border: 'none', cursor: 'pointer' }}>
            <span className="action-card-icon">
              <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>volume_up</span>
            </span>
            <span className="action-card-label">Aviso de Ruido</span>
          </button>
          <button onClick={() => setModalMulta('RUIDO')} className="action-card" style={{ border: 'none', cursor: 'pointer' }}>
            <span className="action-card-icon">
              <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>gavel</span>
            </span>
            <span className="action-card-label">Multa por Ruido</span>
          </button>
          <button onClick={() => setModalMulta('PARQUEADERO')} className="action-card" style={{ border: 'none', cursor: 'pointer' }}>
            <span className="action-card-icon">
              <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>directions_car</span>
            </span>
            <span className="action-card-label">Multa Parqueadero</span>
          </button>
          <button onClick={() => setModalPaquetes(true)} className="action-card" style={{ border: 'none', cursor: 'pointer' }}>
            <span className="action-card-icon">
              <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>inventory_2</span>
            </span>
            <span className="action-card-label">Paquetes Pendientes</span>
          </button>
        </div>
      </div>

      <div className="card" style={{ marginTop: '12px' }}>
        <h3 className="card-title">Otras acciones</h3>
        <div className="card-grid-4" style={{ marginTop: '12px' }}>
          {QUICK.map((q) => (
            <button
              key={q.path}
              type="button"
              onClick={() => navigate(q.path)}
              className="action-card"
              style={{ border: 'none', cursor: 'pointer' }}
            >
              <span className="action-card-icon">
                <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>
                  {q.icon}
                </span>
              </span>
              <span className="action-card-label">{q.label}</span>
            </button>
          ))}
        </div>
      </div>

      <ModalAvisoRuido
        open={modalAviso}
        onClose={() => setModalAviso(false)}
        onConfirm={() => {
          setModalAviso(false);
          showToast({ message: 'Aviso de ruido enviado', type: 'success' });
        }}
        apartamentos={apartamentos?.items || []}
      />

      <ModalGenerarMulta
        open={!!modalMulta}
        onClose={() => setModalMulta(null)}
        onConfirm={() => {
          setModalMulta(null);
          showToast({ message: 'Multa generada', type: 'success' });
        }}
        apartamentos={apartamentos?.items || []}
        quejasRuido={quejasRuido?.items || quejasRuido || []}
        tipoInicial={modalMulta}
      />

      <ModalPaquetes
        open={modalPaquetes}
        onClose={() => setModalPaquetes(false)}
        onConfirm={() => showToast({ message: 'Paquete marcado como entregado', type: 'success' })}
      />
      <Toast toast={toast} />
    </div>
  );
}
