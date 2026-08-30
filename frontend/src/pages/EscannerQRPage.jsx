import { useState, useRef, useEffect } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { VideoCamara } from '../components/ui/VideoCamara.jsx';
import { toast } from 'sonner';
import { valPlaca } from '../lib/validation.js';
import api from '../lib/api.js';
import { useFetch } from '../lib/hooks.js';
import { formatDate } from '../lib/utils.js';

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  ACTIVA: 'badge-activo',
  FINALIZADA: 'badge-finalizada',
  CANCELADA: 'badge-cancelado',
};

function PanelValidar({ onResultado, onNotificarVisita, onRegistrarEntrada, idVisitaActual, onLimpiar, codigoInicial }) {
  const [codigoManual, setCodigoManual] = useState(codigoInicial || '');
  const [validando, setValidando] = useState(false);
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');

  // When codigoInicial prop changes, sync to local state
  useEffect(() => {
    if (codigoInicial) setCodigoManual(codigoInicial);
  }, [codigoInicial]);
  const [medioTransporte, setMedioTransporte] = useState('CARRO');
  const [placa, setPlaca] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [esperando, setEsperando] = useState(false);
  const [idMensaje, setIdMensaje] = useState(null);
  const [confirmado, setConfirmado] = useState(null);
  const [registrando, setRegistrando] = useState(false);
  const [parqueaderoAsignado, setParqueaderoAsignado] = useState(null);

  async function validar(codigo) {
    if (!codigo || codigo.length < 5) {
      setError('Ingrese un código QR válido');
      return;
    }
    setValidando(true);
    setError('');
    try {
      const data = await api.post('/porteria/qr/validar', { codigoQr: codigo });
      setDatos(data);
      onResultado?.(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setValidando(false);
    }
  }

  // Polling para ver si el residente confirma la visita
  useEffect(() => {
    if (!esperando || !idVisitaActual) return;
    const interval = setInterval(async () => {
      try {
        const res = await api.get(`/buzon/resultado-notificar?idVisita=${idVisitaActual}`);
        if (res.confirmado === 1) {
          setConfirmado(true);
          setEsperando(false);
          clearInterval(interval);
        } else if (res.confirmado === 0) {
          setConfirmado(false);
          setEsperando(false);
          clearInterval(interval);
        }
      } catch (err) {
        // seguir pollando
      }
    }, 2000);
    return () => clearInterval(interval);
  }, [esperando, idVisitaActual]);

  async function notificarVisita(foto) {
    if (!datos?.codigoQr) return;
    setEsperando(true);
    setConfirmado(null);
    try {
      const res = await api.post('/porteria/qr/notificar', {
        codigoQr: datos.codigoQr,
        fotoCaptura: foto,
      });
      setIdMensaje(res.idMensaje);
      onNotificarVisita?.(res);
    } catch (err) {
      setError(err.message);
      setEsperando(false);
    }
  }

  async function registrarEntrada() {
    if (!datos?.codigoQr) return;
    if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') {
      const rPlaca = valPlaca(placa, medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO');
      if (!rPlaca.ok) { setError(rPlaca.mensaje); return; }
    }
    if (medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') {
      if (!descripcion.trim()) {
        setError(medioTransporte === 'BICICLETA' ? 'La descripción de la bicicleta es requerida' : 'La descripción es requerida');
        return;
      }
    }
    setRegistrando(true);
    try {
      const payload = { codigoQr: datos.codigoQr, medioTransporte };
      if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') payload.placa = placa;
      if (medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') payload.descripcion = descripcion;
      const res = await api.post('/porteria/qr/entrada', payload);
      setParqueaderoAsignado(res.parqueadero);
      onRegistrarEntrada?.(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setRegistrando(false);
    }
  }

  function limpiar() {
    setDatos(null);
    setError('');
    setCodigoManual('');
    setPlaca('');
    setDescripcion('');
    setConfirmado(null);
    setEsperando(false);
    setIdMensaje(null);
    setParqueaderoAsignado(null);
    onLimpiar?.();
  }

  return (
    <div>
      <div className="form-group">
        <Input
          id="qr-manual"
          label="Código QR (manual o escaneado)"
          value={codigoManual}
          onChange={(e) => setCodigoManual(e.target.value)}
          placeholder="Escanea con la cámara o pega el código"
        />
        <div style={{ marginTop: '8px' }}>
          <Button onClick={() => validar(codigoManual)} disabled={!codigoManual || validando}>
            {validando ? 'Validando...' : 'Validar QR'}
          </Button>
        </div>
      </div>

      {error && <div className="login-error-msg" style={{ marginBottom: '12px' }}>{error}</div>}

      {datos && (
        <div className="card" style={{ marginTop: '12px' }}>
          <h3 className="card-title">Datos del QR</h3>
          <div className="detail-row"><span>Visitante</span><span>{datos.nombreVisitante || '—'}</span></div>
          <div className="detail-row"><span>Documento</span><span>{datos.documentoVisitante || '—'}</span></div>
          <div className="detail-row"><span>Residente</span><span>{datos.nombreResidente || '—'}</span></div>
          <div className="detail-row"><span>Apartamento</span><span>{datos.numeroApartamento || '—'}</span></div>
          <div className="detail-row"><span>Expira</span><span>{formatDate(datos.fechaExpiracion) || '—'}</span></div>
          {datos.notas && (
            <div className="detail-row"><span>Notas</span><span>{datos.notas}</span></div>
          )}

          {!esperando && confirmado === null && (
            <>
              <div style={{ marginTop: '16px' }}>
                <h4 className="mb-2 text-[13px] font-bold">Foto del visitante (requerida)</h4>
                <VideoCamara onCapture={notificarVisita} buttonLabel="Capturar y Notificar" buttonClass="btn-primary" />
              </div>
            </>
          )}

          {esperando && confirmado === null && (
            <div style={{ marginTop: '16px', textAlign: 'center' }}>
              <p>Esperando confirmación del residente...</p>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>(polling cada 2s)</p>
            </div>
          )}

          {confirmado === true && (
            <>
              <div className="login-error-msg" style={{ background: 'var(--accent-green-bg)', color: 'var(--success-strong)', borderColor: 'var(--success-strong)', marginTop: '12px' }}>
                El residente confirmó la visita. Proceda a registrar la entrada.
              </div>
              <div style={{ marginTop: '12px' }}>
                <h4 className="mb-2 text-[13px] font-bold">¿En qué viene?</h4>
                <Select id="medio" value={medioTransporte} onChange={(e) => setMedioTransporte(e.target.value)}>
                  <option value="CARRO">Carro</option>
                  <option value="MOTO">Moto</option>
                  <option value="BICICLETA">Bicicleta</option>
                  <option value="A_PIE">A pie</option>
                  <option value="OTRO">Otro</option>
                </Select>
                {(medioTransporte === 'CARRO' || medioTransporte === 'MOTO') && (
                  <div style={{ marginTop: '8px' }}>
                    <Input id="placa" label="Placa" value={placa} onChange={(e) => setPlaca(e.target.value.toUpperCase())} />
                  </div>
                )}
                {medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO' ? (
                  <div style={{ marginTop: '8px' }}>
                    <Textarea id="desc" label={medioTransporte === 'BICICLETA' ? 'Descripción de la bicicleta' : 'Descripción'} rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
                  </div>
                ) : null}
                <div style={{ marginTop: '12px' }}>
                  <Button onClick={registrarEntrada} disabled={registrando}>
                    {registrando ? 'Registrando...' : 'Registrar Entrada'}
                  </Button>
                </div>
              </div>
            </>
          )}

          {confirmado === false && (
            <div className="login-error-msg" style={{ marginTop: '12px' }}>
              El residente rechazó la visita.
            </div>
          )}

          {parqueaderoAsignado && (
            <div
              className="card"
              style={{
                marginTop: '16px',
                background: 'var(--primary)',
                color: 'white',
                textAlign: 'center',
                padding: '24px',
              }}
            >
              <div style={{ fontSize: '14px' }}>Parqueadero asignado</div>
              <div style={{ fontSize: '36px', fontWeight: 800, marginTop: '8px' }}>{parqueaderoAsignado}</div>
              <div style={{ fontSize: '12px', opacity: 0.7, marginTop: '8px' }}>Se cierra en 10s</div>
            </div>
          )}

          <div style={{ marginTop: '12px' }}>
            <Button variant="outline" onClick={limpiar}>Limpiar</Button>
          </div>
        </div>
      )}
    </div>
  );
}

function TabValidar({ onToast }) {
  const [codigoEscaneado, setCodigoEscaneado] = useState('');
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState('');
  const [codigoInput, setCodigoInput] = useState('');
  const [jsqrListo, setJsqrListo] = useState(!!window.jsQR);
  const scanRef = useRef(null);

  useEffect(() => () => detener(), []);

  // Carga bajo demanda de jsQR (solo en esta pagina, no en el bundle global).
  useEffect(() => {
    if (window.jsQR) {
      setJsqrListo(true);
      return undefined;
    }
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js';
    script.onload = () => setJsqrListo(true);
    script.onerror = () => setJsqrListo(false);
    document.head.appendChild(script);
    return () => {
      script.onload = null;
      script.onerror = null;
    };
  }, []);

  async function iniciar() {
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      videoRef.current.srcObject = s;
      await videoRef.current.play();
      setStreaming(true);
      scanRef.current = requestAnimationFrame(escanear);
    } catch (e) {
      setError('No se pudo acceder a la cámara: ' + e.message);
    }
  }
  function detener() {
    const s = videoRef.current?.srcObject;
    if (s) {
      s.getTracks().forEach((t) => t.stop());
      videoRef.current.srcObject = null;
    }
    if (scanRef.current) cancelAnimationFrame(scanRef.current);
    setStreaming(false);
  }
  function escanear() {
    if (!streaming) return;
    const v = videoRef.current;
    const c = canvasRef.current;
    if (!v || !c) {
      scanRef.current = requestAnimationFrame(escanear);
      return;
    }
    if (v.videoWidth > 0) {
      c.width = v.videoWidth;
      c.height = v.videoHeight;
      const ctx = c.getContext('2d');
      ctx.drawImage(v, 0, 0, c.width, c.height);
      const data = ctx.getImageData(0, 0, c.width, c.height);
      if (jsqrListo) {
        const code = window.jsQR(data.data, data.width, data.height, { inversionAttempts: 'dontInvert' });
        if (code && code.data && code.data.length >= 5) {
          setCodigoEscaneado(code.data);
          setCodigoInput(code.data);
          detener();
          return;
        }
      }
    }
    scanRef.current = requestAnimationFrame(escanear);
  }

  return (
    <div>
      <h3 className="mb-3 text-[14px] font-bold">Escanear con cámara</h3>
      {error && <p className="field-error">{error}</p>}
      {!jsqrListo && (
        <p className="field-error" style={{ marginBottom: '8px' }}>
          La librería de escaneo no pudo cargarse. Verifique la conexión; mientras
          tanto puede validar manualmente abajo.
        </p>
      )}
      <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
        {streaming ? (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            style={{ width: '100%', maxWidth: '320px', maxHeight: '240px', borderRadius: '8px', background: 'var(--preview-bg)' }}
          />
        ) : (
          <div
            style={{
              width: '100%',
              maxWidth: '320px',
              height: '240px',
              borderRadius: '8px',
              background: 'var(--surface-dim)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-muted)',
            }}
          >
            Cámara inactiva
          </div>
        )}
        <canvas ref={canvasRef} style={{ display: 'none' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {!streaming && <Button onClick={iniciar}>Activar Cámara</Button>}
          {streaming && <Button variant="outline" onClick={detener}>Detener</Button>}
        </div>
      </div>

      <div style={{ marginTop: '16px' }}>
        <PanelValidar codigoInicial={codigoInput} onToast={(t) => { onToast?.(t); }} />
      </div>
    </div>
  );
}

function TabRegistrarSalida({ onToast }) {
  const { data: visitas, loading, refetch } = useFetch(() => api.get('/porteria/visitas-resumen'), []);
  const [registrando, setRegistrando] = useState(null);

  const activas = (visitas?.items || visitas || []).filter(
    (v) => v.estado === 'ACTIVA' || v.estado === 'PENDIENTE'
  );

  async function registrarSalida(idVisita) {
    setRegistrando(idVisita);
    try {
      await api.put(`/porteria/visitas/${idVisita}/salida`);
      toast.success('Salida registrada');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setRegistrando(null);
    }
  }

  return (
    <div>
      <h3 className="mb-3 text-[14px] font-bold">Visitas activas</h3>
      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Visitante</th>
              <th>Apartamento</th>
              <th>Ingreso</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center' }}>
                  Cargando...
                </td>
              </tr>
            )}
            {!loading && activas.length === 0 && (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                  No hay visitas activas
                </td>
              </tr>
            )}
            {activas.map((v) => (
              <tr key={v.idVisita}>
                <td>{v.idVisita}</td>
                <td>{v.nombreVisitante || '—'}</td>
                <td>{v.numeroApartamento || '—'}</td>
                <td>{formatDate(v.fechaVisita) || '—'}</td>
                <td>
                  <Button
                    onClick={() => registrarSalida(v.idVisita)}
                    disabled={registrando === v.idVisita}
                  >
                    {registrando === v.idVisita ? 'Registrando...' : 'Registrar Salida'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TabParqueaderos({ onToast }) {
  const { data: parqueaderos, loading, refetch } = useFetch(
    () => api.get('/parqueaderos'),
    []
  );

  const grouped = (parqueaderos?.items || parqueaderos || []).reduce((acc, p) => {
    const key = p.esVisitante ? 'Visitantes' : 'Residentes';
    if (!acc[key]) acc[key] = [];
    acc[key].push(p);
    return acc;
  }, {});

  return (
    <div>
      <h3 className="mb-3 text-[14px] font-bold">Estado en tiempo real</h3>
      <Button onClick={refetch} style={{ marginBottom: '12px' }}>
        Actualizar
      </Button>
      {loading && <p>Cargando...</p>}
      {Object.entries(grouped).map(([tipo, lista]) => (
        <div key={tipo} style={{ marginBottom: '16px' }}>
          <h4 className="mb-2 text-[13px] font-bold">{tipo}</h4>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Tipo</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {lista.map((p) => (
                  <tr key={p.idParqueadero}>
                    <td>{p.codigo}</td>
                    <td>{p.tipo}</td>
                    <td>
                      <span
                        className={`badge ${
                          p.estado === 'DISPONIBLE'
                            ? 'badge-activo'
                            : p.estado === 'OCUPADO'
                              ? 'badge-ocupado'
                              : 'badge-en-mantenimiento'
                        }`}
                      >
                        {p.estado}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function EscannerQRPage() {
  const [tab, setTab] = useState('validar');

  return (
    <div>
      <PageHeader title="Escáner QR" subtitle="Validar entrada y registrar salida de visitantes" />

      <div className="tabs" role="tablist" aria-label="Escáner QR">
        <button className={`tab ${tab === 'validar' ? 'active' : ''}`} role="tab" aria-selected={tab === 'validar'} aria-controls="panel-validar" id="tab-validar" onClick={() => setTab('validar')}>
          Validar Entrada
        </button>
        <button className={`tab ${tab === 'salida' ? 'active' : ''}`} role="tab" aria-selected={tab === 'salida'} aria-controls="panel-salida" id="tab-salida" onClick={() => setTab('salida')}>
          Registrar Salida
        </button>
        <button className={`tab ${tab === 'parqueaderos' ? 'active' : ''}`} role="tab" aria-selected={tab === 'parqueaderos'} aria-controls="panel-parqueaderos" id="tab-parqueaderos" onClick={() => setTab('parqueaderos')}>
          Parqueaderos
        </button>
      </div>

      <div className={`tab-content ${tab === 'validar' ? 'active' : ''}`} role="tabpanel" id="panel-validar" aria-labelledby="tab-validar">
        <TabValidar />
      </div>
      <div className={`tab-content ${tab === 'salida' ? 'active' : ''}`} role="tabpanel" id="panel-salida" aria-labelledby="tab-salida">
        <TabRegistrarSalida />
      </div>
      <div className={`tab-content ${tab === 'parqueaderos' ? 'active' : ''}`} role="tabpanel" id="panel-parqueaderos" aria-labelledby="tab-parqueaderos">
        <TabParqueaderos />
      </div>
    </div>
  );
}




