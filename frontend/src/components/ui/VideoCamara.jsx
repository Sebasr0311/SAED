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
          videoRef.current.play();
        }
      }, 50);
    } catch (e) {
      setError('No se pudo acceder a la cámara: ' + e.message);
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

  return (
    <div>
      {error && <p className="field-error">{error}</p>}
      {stream && (
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          style={{ width: '100%', maxHeight, borderRadius: '8px', background: 'var(--preview-bg)', objectFit: 'contain' }}
        />
      )}
      <canvas ref={canvasRef} style={{ display: 'none' }} />
      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
        {!stream && !dualCamera && <Button onClick={() => iniciar()}>Activar Cámara</Button>}
        {!stream && dualCamera && (
          <>
            <Button onClick={() => iniciar('environment')}>Cámara Trasera</Button>
            <Button variant="outline" onClick={() => iniciar('user')}>Cámara Frontal</Button>
          </>
        )}
        {stream && <Button onClick={capturar} className={buttonClass}>{buttonLabel}</Button>}
        {stream && <Button variant="outline" onClick={detener}>Cancelar</Button>}
      </div>
    </div>
  );
}
