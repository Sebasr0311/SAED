import { useState, useRef, useEffect } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
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

function VideoCamara({ onCapture, buttonLabel = 'Capturar', buttonClass = 'btn-primary' }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [stream, setStream] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => () => detener(), []);

  async function iniciar(facingMode = 'environment') {
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
      setError('No se pudo acceder a la cÃ¡mara: ' + e.message);
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
          style={{ width: '100%', maxHeight: '320px', borderRadius: '8px', background: 'var(--preview-bg)', objectFit: 'contain' }}
        />
      )}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
        {!stream && <Button onClick={() => iniciar()}>Activar CÃ¡mara</Button>}
        {stream && <Button onClick={capturar} className={buttonClass}>{buttonLabel}</Button>}
        {stream && <Button variant="outline" onClick={detener}>Cancelar</Button>}
      </div>
    </div>
  );
}

function PanelValidar({ onResultado, onNotificarVisita, onRegistrarEntrada, idVisitaActual, onLimpiar, codigoInicial }) {
  const [codigoManual, setCodigoManual] = useState('');
  const [validando, setValidando] = useState(false);
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');

  // Sincroniza el codigo escaneado por camara hacia el campo manual.
  useEffect(() => {
    if (codigoInicial) {
      setCodigoManual(codigoInicial);
    }
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
      setError('Ingrese un cÃ³digo QR vÃ¡lido');
      return;
    }
    setValidando(true);
    setError('');
    try {
      const data = await api.post('/qr/validar', { codigoQr: codigo });
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
      const res = await api.post('/qr/notificar', {
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
        setError(medioTransporte === 'BICICLETA' ? 'La descripciÃ³n de la bicicleta es requerida' : 'La descripciÃ³n es requerida');
        return;
      }
    }
    setRegistrando(true);
    try {
      const payload = { codigoQr: datos.codigoQr, medioTransporte };
      if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') payload.placa = placa;
      if (medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') payload.descripcion = descripcion;
      const res = await api.post('/qr/entrada', payload);
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
          label="CÃ³digo QR (manual o escaneado)"
          value={codigoManual}
          onChange={(e) => setCodigoManual(e.target.value)}
          placeholder="Escanea con la cÃ¡mara o pega el cÃ³digo"
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
          <div className="detail-row"><span>Visitante</span><span>{datos.nombreVisitante || 'â€”'}</span></div>
          <div className="detail-row"><span>Documento</span><span>{datos.documentoVisitante || 'â€”'}</span></div>
          <div className="detail-row"><span>Residente</span><span>{datos.nombreResidente || 'â€”'}</span></div>
          <div className="detail-row"><span>Apartamento</span><span>{datos.numeroApartamento || 'â€”'}</span></div>
          <div className="detail-row"><span>Expira</span><span>{formatDate(datos.fechaExpiracion) || 'â€”'}</span></div>
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
              <p>Esperando confirmaciÃ³n del residente...</p>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>(polling cada 2s)</p>
            </div>
          )}

          {confirmado === true && (
            <>
              <div className="login-error-msg" style={{ background: 'var(--accent-green-bg)', color: 'var(--success-strong)', borderColor: 'var(--success-strong)', marginTop: '12px' }}>
                El residente confirmÃ³ la visita. Proceda a registrar la entrada.
              </div>
              <div style={{ marginTop: '12px' }}>
                <h4 className="mb-2 text-[13px] font-bold">Â¿En quÃ© viene?</h4>
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
                    <Textarea id="desc" label={medioTransporte === 'BICICLETA' ? 'DescripciÃ³n de la bicicleta' : 'DescripciÃ³n'} rows={2} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
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
              El residente rechazÃ³ la visita.
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
  const [toast, setToast] = useState(null);
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
      setError('No se pudo acceder a la cÃ¡mara: ' + e.message);
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
      <h3 className="mb-3 text-[14px] font-bold">Escanear con cÃ¡mara</h3>
      {error && <p className="field-error">{error}</p>}
      {!jsqrListo && (
        <p className="field-error" style={{ marginBottom: '8px' }}>
          La librerÃ­a de escaneo no pudo cargarse. Verifique la conexiÃ³n; mientras
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
            CÃ¡mara inactiva
          </div>
        )}
        <canvas ref={canvasRef} style={{ display: 'none' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {!streaming && <Button onClick={iniciar}>Activar CÃ¡mara</Button>}
          {streaming && <Button variant="outline" onClick={detener}>Detener</Button>}
        </div>
      </div>

      <div style={{ marginTop: '16px' }}>
        <PanelValidar codigoInicial={codigoInput} onToast={(t) => { setToast(t); onToast?.(t); }} />
      </div>
      <Toast toast={toast} />
    </div>
  );
}

function TabRegistrarSalida({ onToast }) {
  const { data: visitas, loading, refetch } = useFetch(() => api.get('/visitas'), []);
  const [registrando, setRegistrando] = useState(null);
  const [toast, setToast] = useState(null);

  const activas = (visitas?.items || visitas || []).filter(
    (v) => v.estado === 'ACTIVA' || v.estado === 'PENDIENTE'
  );

  async function registrarSalida(idVisita) {
    setRegistrando(idVisita);
    try {
      await api.put(`/visitas/${idVisita}/salida`);
      setToast({ message: 'Salida registrada', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
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
                <td>{v.nombreVisitante || 'â€”'}</td>
                <td>{v.numeroApartamento || 'â€”'}</td>
                <td>{formatDate(v.fechaVisita) || 'â€”'}</td>
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
      <Toast toast={toast} />
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
                  <th>CÃ³digo</th>
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
  const [toast, setToast] = useState(null);

  return (
    <div>
      <PageHeader title="EscÃ¡ner QR" subtitle="Validar entrada y registrar salida de visitantes" />

      <div className="tabs">
        <button className={`tab ${tab === 'validar' ? 'active' : ''}`} onClick={() => setTab('validar')}>
          Validar Entrada
        </button>
        <button className={`tab ${tab === 'salida' ? 'active' : ''}`} onClick={() => setTab('salida')}>
          Registrar Salida
        </button>
        <button className={`tab ${tab === 'parqueaderos' ? 'active' : ''}`} onClick={() => setTab('parqueaderos')}>
          Parqueaderos
        </button>
      </div>

      <div className={`tab-content ${tab === 'validar' ? 'active' : ''}`}>
        <TabValidar onToast={setToast} />
      </div>
      <div className={`tab-content ${tab === 'salida' ? 'active' : ''}`}>
        <TabRegistrarSalida onToast={setToast} />
      </div>
      <div className={`tab-content ${tab === 'parqueaderos' ? 'active' : ''}`}>
        <TabParqueaderos onToast={setToast} />
      </div>

      <Toast toast={toast} />
    </div>
  );
}
