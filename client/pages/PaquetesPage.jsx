import { useState, useRef, useEffect } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, imageSrc } from '../lib/utils.js';

export default function PaquetesPage() {
  const [selectedApto, setSelectedApto] = useState('');
  const [toast, setToast] = useState(null);
  const [camaraActiva, setCamaraActiva] = useState(false);
  const [foto, setFoto] = useState(null);
  const [registrando, setRegistrando] = useState(false);
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);

  const { data: apartamentos } = useFetch(() => api.get('/apartamentos'), []);
  const {
    data: paquetesRaw,
    loading,
    error,
    refetch,
  } = useFetch(
    () => (selectedApto ? api.get(`/buzon?idApartamento=${selectedApto}`) : Promise.resolve([])),
    [selectedApto]
  );
  const paquetes = (paquetesRaw?.items || paquetesRaw || []).filter((p) => p.tipo === 'PAQUETE');

  useEffect(() => {
    return () => detenerCamara();
  }, []);

  async function abrirCamara() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } },
        audio: false,
      });
      streamRef.current = stream;
      setCamaraActiva(true);
      setFoto(null);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }
      }, 50);
    } catch (err) {
      setToast({ message: 'No se pudo acceder a la cámara: ' + err.message, type: 'error' });
    }
  }

  function detenerCamara() {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    setCamaraActiva(false);
  }

  function capturar() {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
    setFoto(dataUrl.split(',')[1]);
    detenerCamara();
  }

  function retomar() {
    setFoto(null);
    abrirCamara();
  }

  async function registrar() {
    if (!selectedApto) {
      setToast({ message: 'Seleccione un apartamento', type: 'error' });
      return;
    }
    if (!foto) {
      setToast({ message: 'Debe tomar una foto del paquete', type: 'error' });
      return;
    }
    setRegistrando(true);
    try {
      await api.post('/buzon/paquete', {
        idApartamento: Number(selectedApto),
        titulo: 'Paquete/Domicilio recibido',
        fotoCaptura: foto,
      });
      setToast({ message: 'Paquete registrado', type: 'success' });
      setFoto(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setRegistrando(false);
    }
  }

  const columns = [
    { key: 'idMensaje', label: 'ID', width: 60 },
    { key: 'titulo', label: 'Descripción' },
    { key: 'fechaCreacion', label: 'Recibido', render: (r) => formatDate(r.fechaCreacion) },
    {
      key: 'entregado',
      label: 'Estado',
      render: (r) => (
        <span className={`badge ${r.entregado ? 'badge-activo' : 'badge-pendiente-firma'}`}>
          {r.entregado ? 'Entregado' : 'Pendiente'}
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="Notificar Paquete o Domicilio" />
      <div className="card" style={{ maxWidth: '480px', marginBottom: '20px' }}>
        <div className="form-group">
          <Select
            id="apartamento"
            label="Apartamento"
            value={selectedApto}
            onChange={(e) => {
              setSelectedApto(e.target.value);
              setFoto(null);
              detenerCamara();
            }}
          >
            <option value="">— Seleccione apartamento —</option>
            {(apartamentos?.items || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
          </Select>
        </div>

        <div style={{ marginBottom: '12px' }}>
          {camaraActiva && (
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              style={{ width: '100%', maxHeight: '240px', borderRadius: '8px', background: '#000', objectFit: 'contain' }}
            />
          )}
          <canvas ref={canvasRef} style={{ display: 'none' }} />
          {foto && !camaraActiva && (
            <img
              src={imageSrc(foto)}
              alt="Foto del paquete"
              style={{ maxWidth: '240px', maxHeight: '180px', borderRadius: '8px', border: '1px solid #e2e8f0' }}
            />
          )}
        </div>

        <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', marginBottom: '16px' }}>
          {!camaraActiva && !foto && (
            <Button variant="outline" onClick={abrirCamara} disabled={!selectedApto}>
              Abrir Cámara
            </Button>
          )}
          {camaraActiva && <Button onClick={capturar}>Capturar Foto</Button>}
          {foto && !camaraActiva && (
            <Button variant="outline" onClick={retomar}>
              Repetir
            </Button>
          )}
        </div>

        <Button onClick={registrar} disabled={!foto || registrando}>
          {registrando ? 'Notificando...' : 'Notificar Llegada'}
        </Button>
      </div>
      <DataTable
        columns={columns}
        rows={paquetes}
        loading={loading}
        error={error?.message}
        onRetry={refetch}
        empty="Seleccione un apartamento para ver paquetes"
        keyField="idMensaje"
      />
      <Toast toast={toast} />
    </div>
  );
}
