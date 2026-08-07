import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function PaquetesPage() {
  const [selectedApto, setSelectedApto] = useState('');
  const [toast, setToast] = useState(null);

  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=200'), []);
  const { data: paquetes, loading } = useFetch(
    () => (selectedApto ? api.get(`/paquetes?apartamento=${selectedApto}`) : Promise.resolve([])),
    [selectedApto]
  );

  const columns = [
    { key: 'idPaquete', label: 'ID', width: 60 },
    { key: 'descripcion', label: 'Descripción' },
    { key: 'fechaRecepcion', label: 'Recibido', render: (r) => formatDate(r.fechaRecepcion) },
    { key: 'fechaEntrega', label: 'Entregado', render: (r) => formatDate(r.fechaEntrega) },
    { key: 'estado', label: 'Estado' },
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
            onChange={(e) => setSelectedApto(e.target.value)}
          >
            <option value="">— Seleccione apartamento —</option>
            {(apartamentos?.items || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
          </Select>
        </div>
        <Button disabled={!selectedApto}>Notificar Llegada</Button>
      </div>
      <DataTable
        columns={columns}
        rows={paquetes || []}
        loading={loading}
        empty="Seleccione un apartamento para ver paquetes"
        keyField="idPaquete"
      />
      <Toast toast={toast} />
    </div>
  );
}
