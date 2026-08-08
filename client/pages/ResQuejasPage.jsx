import { useState, useRef, useEffect } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import Toast from '../components/ui/Toast.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDate } from '../lib/utils.js';

const CATS = [
  { value: 'LIMPIEZA', label: 'Limpieza' },
  { value: 'SEGURIDAD', label: 'Seguridad' },
  { value: 'MANTENIMIENTO', label: 'Mantenimiento' },
  { value: 'CONVIVENCIA', label: 'Convivencia' },
  { value: 'ZONAS_COMUNES', label: 'Zonas Comunes' },
  { value: 'OTRO', label: 'Otro' },
];

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTA: 'badge-activo',
  CERRADA: 'badge-neutral',
};

function CamaraCaptura({ onCapture, onCancel }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [stream, setStream] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => () => detener(), []);

  async function iniciar() {
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
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
    const v = videoRef.current;
    const c = canvasRef.current;
    if (!v || !c) return;
    c.width = v.videoWidth || 640;
    c.height = v.videoHeight || 480;
    c.getContext('2d').drawImage(v, 0, 0, c.width, c.height);
    const dataUrl = c.toDataURL('image/jpeg', 0.8);
    onCapture(dataUrl.split(',')[1]);
    detener();
    onCancel();
  }

  return (
    <div>
      {error && <p className="field-error">{error}</p>}
      {stream ? (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          style={{ width: '100%', maxHeight: '320px', borderRadius: '8px', background: '#000' }}
        />
      ) : (
        <Button onClick={iniciar}>Activar Cámara</Button>
      )}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
      {stream && (
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <Button onClick={capturar}>Capturar</Button>
          <Button variant="outline" onClick={() => { detener(); onCancel(); }}>
            Cancelar
          </Button>
        </div>
      )}
    </div>
  );
}

export default function ResQuejasPage() {
  const { user } = useAuth();
  const [toast, setToast] = useState(null);
  const [form, setForm] = useState({
    tipo: 'QUEJA',
    categoria: 'LIMPIEZA',
    titulo: '',
    descripcion: '',
    idMulta: '',
  });
  const [sending, setSending] = useState(false);
  const [foto, setFoto] = useState(null);
  const [showCamara, setShowCamara] = useState(false);
  const [detalle, setDetalle] = useState(null);

  const { data, loading, refetch } = useFetch(
    () => api.get(`/quejas`),
    [user]
  );

  const { data: multasData } = useFetch(
    () =>
      form.tipo === 'APELACION' && user?.idResidente
        ? api.get(`/residentes/${user.idResidente}/dashboard`).catch(() => null)
        : Promise.resolve(null),
    [user, form.tipo]
  );
  const multas = ((multasData?.raw || multasData)?.multas || []).filter(
    (m) => m.estado !== 'ANULADA'
  );

  function update(k, v) {
    setForm((f) => ({ ...f, [k]: v }));
  }

  async function send() {
    if (!form.titulo || !form.descripcion) {
      setToast({ message: 'Título y descripción son obligatorios', type: 'error' });
      return;
    }
    if (form.tipo === 'APELACION' && !form.idMulta) {
      setToast({ message: 'Seleccione la multa a apelar', type: 'error' });
      return;
    }
    setSending(true);
    try {
      const payload = { ...form, idResidente: user.idResidente };
      if (form.tipo === 'APELACION' && form.idMulta) payload.idMulta = Number(form.idMulta);
      if (foto) payload.fotoEvidencia = foto;
      await api.post('/quejas', payload);
      setToast({ message: 'Solicitud enviada', type: 'success' });
      setForm({ tipo: 'QUEJA', categoria: 'LIMPIEZA', titulo: '', descripcion: '', idMulta: '' });
      setFoto(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSending(false);
    }
  }

  const columns = [
    { key: 'idQueja', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'titulo', label: 'Título' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.fechaCreacion) },
  ];

  return (
    <div>
      <PageHeader title="Mis Solicitudes" subtitle="Quejas, sugerencias y apelaciones" />
      <div className="card" style={{ marginBottom: '24px', maxWidth: '720px' }}>
        <h3 className="card-title">Nueva solicitud</h3>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)}>
            <option value="QUEJA">Queja</option>
            <option value="SUGERENCIA">Sugerencia</option>
            <option value="APELACION">Apelación</option>
          </Select>
          <Select
            id="categoria"
            label="Categoría"
            value={form.categoria}
            onChange={(e) => update('categoria', e.target.value)}
          >
            {CATS.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </Select>
        </div>
        {form.tipo === 'APELACION' && (
          <div className="form-group">
            <Select
              id="idMulta"
              label="Multa a apelar"
              value={form.idMulta}
              onChange={(e) => update('idMulta', e.target.value)}
            >
              <option value="">— Seleccionar multa —</option>
              {multas.map((m) => (
                <option key={m.idMulta} value={m.idMulta}>
                  Multa #{m.idMulta} — {m.tipo || 'Multa'}
                  {m.monto ? ` — $${m.monto}` : ''}
                </option>
              ))}
            </Select>
            {multas.length === 0 && (
              <p className="field-error" style={{ marginTop: '4px' }}>
                No tienes multas para apelar
              </p>
            )}
          </div>
        )}
        <div className="form-group">
          <Input
            id="titulo"
            label="Título"
            value={form.titulo}
            onChange={(e) => update('titulo', e.target.value)}
          />
        </div>
        <div className="form-group">
          <Textarea
            id="descripcion"
            label="Descripción"
            rows={4}
            value={form.descripcion}
            onChange={(e) => update('descripcion', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label style={{ fontSize: '12px', fontWeight: 600, color: '#475569', display: 'block', marginBottom: '6px' }}>
            Foto de evidencia (opcional)
          </label>
          {foto && (
            <div style={{ marginBottom: '8px' }}>
              <img
                src={`data:image/jpeg;base64,${foto}`}
                alt="Evidencia"
                style={{ maxWidth: '200px', borderRadius: '8px' }}
              />
            </div>
          )}
          {!showCamara && (
            <Button variant="outline" onClick={() => setShowCamara(true)}>
              {foto ? 'Retomar Foto' : 'Tomar Foto'}
            </Button>
          )}
          {showCamara && (
            <CamaraCaptura
              onCapture={(b) => setFoto(b)}
              onCancel={() => setShowCamara(false)}
            />
          )}
        </div>
        <Button onClick={send} disabled={sending}>
          {sending ? 'Enviando...' : 'Enviar Solicitud'}
        </Button>
      </div>

      <h3 style={{ marginBottom: '12px', fontSize: '15px', fontWeight: 600 }}>Historial</h3>
      <DataTable
        columns={columns}
        rows={data?.items || data || []}
        loading={loading}
        empty="No has enviado solicitudes"
        keyField="idQueja"
        onRowClick={setDetalle}
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de Solicitud" size="md">
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="detail-row">
              <span>Tipo</span>
              <span>{detalle.tipo}</span>
            </div>
            <div className="detail-row">
              <span>Categoría</span>
              <span>{detalle.categoria}</span>
            </div>
            <div className="detail-row">
              <span>Estado</span>
              <span>
                <span className={`badge ${ESTADO_BADGE[detalle.estado] || 'badge-neutral'}`}>
                  {detalle.estado}
                </span>
              </span>
            </div>
            <div className="detail-row">
              <span>Fecha</span>
              <span>{formatDate(detalle.fechaCreacion)}</span>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Título</div>
              <div style={{ fontSize: '13px' }}>{detalle.titulo}</div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Descripción</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap' }}>{detalle.descripcion}</div>
            </div>
            {detalle.respuestaAdmin && (
              <div
                style={{
                  background: '#d1fae5',
                  padding: '12px',
                  borderRadius: '8px',
                  border: '1px solid #10b981',
                }}
              >
                <div style={{ fontSize: '11px', color: '#065f46', fontWeight: 600 }}>Respuesta del administrador</div>
                <div style={{ fontSize: '13px', marginTop: '4px' }}>{detalle.respuestaAdmin}</div>
              </div>
            )}
          </div>
        )}
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
