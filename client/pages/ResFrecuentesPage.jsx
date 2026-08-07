import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';

export default function ResFrecuentesPage() {
  const { user } = useAuth();
  const { data, loading } = useFetch(
    () => api.get(`/visitantes-frecuentes/residente/${user?.idResidente}`),
    [user]
  );

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'nombre', label: 'Nombre' },
    { key: 'documento', label: 'Documento' },
    { key: 'placa', label: 'Placa' },
    { key: 'ultimoIngreso', label: 'Último Ingreso' },
  ];

  return (
    <div>
      <PageHeader title="Visitantes Frecuentes" subtitle="Personas que te visitan regularmente" />
      <div className="frecuentes-grid">
        {data && data.length > 0 ? (
          data.map((f) => (
            <div key={f.id} className="frecuente-card">
              <div className="name">{f.nombre}</div>
              <div className="meta">Doc: {f.documento}</div>
              {f.placa && <div className="meta">Placa: {f.placa}</div>}
            </div>
          ))
        ) : (
          <div className="card empty-state">
            {loading ? 'Cargando...' : 'No tienes visitantes frecuentes'}
          </div>
        )}
      </div>
    </div>
  );
}
