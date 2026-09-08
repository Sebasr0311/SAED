import { useState, useRef, useEffect } from 'react';
import { Button } from './Button.jsx';

/**
 * VideoCamara — reusable camera capture component.
 *
 * @param {Function} onCapture   — called with base64 image data (no prefix)
 * @param {string}   buttonLabel — label for the capture button (default: 'Capturar')
 * @param {string}   buttonClass — CSS class for the capture button (default: 'btn-primary')
 * @param {string}   maxHeight   — max video height (default: '320px')
 * @param {boolean}  dualCamera  — show front/back camera buttons (default: false)
 */
export function VideoCamara({ onCapture, buttonLabel = 'Capturar', buttonClass = 'btn-primary', maxHeight = '320px', dualCamera = false }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const fileInputRef = useRef(null);
  const [stream, setStream] = useState(null);
  const streamRef = useRef(null); // Keep a ref for the unmount cleanup
  const [error, setError] = useState('');

  useEffect(() => () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
  }, []);

  async function iniciar(facingMode = 'environment') {
    setError('');
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setError('Su navegador o dispositivo no soporta acceso directo a cámara. Puede subir una foto desde archivo.');
      return;
    }
    try {
      const s = await navigator.mediaDevices.getUserMedia({
        video: { facingMode, width: { ideal: 640 }, height: { ideal: 480 } },
        audio: false,
      });
      setStream(s);
      streamRef.current = s;
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = s;
          videoRef.current.play().catch(() => {});
        }
      }, 50);
    } catch (e) {
      // Fallback a cualquier cámara disponible
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });
        setStream(s);
        streamRef.current = s;
        setTimeout(() => {
          if (videoRef.current) {
            videoRef.current.srcObject = s;
            videoRef.current.play().catch(() => {});
          }
        }, 50);
      } catch (err2) {
        setError('No se pudo acceder a la cámara: ' + (err2.message || e.message) + '. Puede cargar una foto como archivo.');
      }
    }
  }

  function detener() {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
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

  function handleFileChange(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (evt) => {
      const img = new Image();
      img.onload = () => {
        const canvas = canvasRef.current || document.createElement('canvas');
        let w = img.naturalWidth || img.width || 640;
        let h = img.naturalHeight || img.height || 480;
        const maxDim = 800;
        if (w > maxDim || h > maxDim) {
          if (w > h) {
            h = Math.round((h * maxDim) / w);
            w = maxDim;
          } else {
            w = Math.round((w * maxDim) / h);
            h = maxDim;
          }
        }
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, w, h);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.8);
        onCapture(dataUrl.split(',')[1]);
      };
      img.src = evt.target?.result;
    };
    reader.readAsDataURL(file);
  }

  return (
    <div>
      {error && (
        <p className="text-xs text-rose-500 font-medium mb-2">{error}</p>
      )}
      {stream && (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          style={{ width: '100%', maxHeight, borderRadius: '8px', background: 'var(--preview-bg, #000)', objectFit: 'contain' }}
        />
      )}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '8px' }}>
        {!stream && !dualCamera && <Button type="button" onClick={() => iniciar()}>Activar Cámara</Button>}
        {!stream && dualCamera && (
          <>
            <Button type="button" onClick={() => iniciar('environment')}>Cámara Trasera</Button>
            <Button type="button" variant="outline" onClick={() => iniciar('user')}>Cámara Frontal</Button>
          </>
        )}
        {!stream && (
          <Button type="button" variant="outline" onClick={() => fileInputRef.current?.click()}>
            Subir Foto / Archivo
          </Button>
        )}
        {stream && <Button type="button" onClick={capturar} className={buttonClass}>{buttonLabel}</Button>}
        {stream && <Button type="button" variant="outline" onClick={detener}>Cancelar</Button>}
      </div>
    </div>
  );
}
