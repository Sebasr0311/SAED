import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS = ['', 'EN_CURSO', 'FINALIZADA', 'CANCELADA'];

export default function VisitasPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [toast, setToast] = useState(null);

  const qs = new URLSearchParams({
    page,
    size: 20,
    ...(filtroEstado ? { estado: filtroEstado } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/visitas?${qs}`), [page, filtroEstado]);

  const columns = [
    { key: 'idVisita', label: 'ID', width: 60 },
    { key: 'visitante', label: 'Visitante' },
    { key: 'documento', label: 'Documento' },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'fechaIngreso', label: 'Ingreso', render: (r) => formatDate(r.fechaIngreso) },
    { key: 'fechaSalida', label: 'Salida', render: (r) => formatDate(r.fechaSalida) },
    { key: 'estado', label: 'Estado' },
  ];

  return (
    <div>
      <PageHeader
        title="Visitas"
        subtitle="Registro de visitas al edificio"
        action={
          <div className="flex gap-2">
            <Select
              id="f-estado"
              value={filtroEstado}
              onChange={(e) => {
                setFiltroEstado(e.target.value);
                setPage(0);
              }}
              className="w-auto"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e || 'Todos'}
                </option>
              ))}
            </Select>
            <Button icon="add">Nueva Visita</Button>
          </div>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay visitas"
        keyField="idVisita"
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={setPage}
      />
      <Toast toast={toast} />
    </div>
  );
}
