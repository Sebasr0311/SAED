import { useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';

export default function EscannerQRPage() {
  const [toast, setToast] = useState(null);
  return (
    <div>
      <PageHeader title="Escáner QR" subtitle="Validar entrada / registrar salida" />
      <div className="card text-center">
        <p className="text-sm text-on-surface-variant">
          La funcionalidad del escáner QR requiere acceso a la cámara del dispositivo.
          Próximamente disponible.
        </p>
      </div>
      <Toast toast={toast} />
    </div>
  );
}
