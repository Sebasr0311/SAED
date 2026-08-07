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
      <DataTable
        columns={columns}
        rows={data || []}
        loading={loading}
        empty="No tienes visitantes frecuentes"
        keyField="id"
      />
    </div>
  );
}
