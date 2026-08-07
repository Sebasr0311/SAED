import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';

const ESTADOS = ['', 'DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];
const TIPOS = ['', 'VEHICULO', 'MOTO', 'BICICLETA'];

const ESTADO_BADGE = {
  DISPONIBLE: 'badge-activo',
  OCUPADO: 'badge-ocupado',
  EN_MANTENIMIENTO: 'badge-en-mantenimiento',
};

export default function ParqueaderosPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [toast, setToast] = useState(null);

  const qs = new URLSearchParams({
    page,
    size: 20,
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/parqueaderos?${qs}`), [page, filtroEstado, filtroTipo]);

  const columns = [
    { key: 'idParqueadero', label: 'ID', width: 60 },
    { key: 'codigo', label: 'Código' },
    { key: 'tipo', label: 'Tipo' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    { key: 'visitante', label: 'Visitante' },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'propietario', label: 'Propietario' },
  ];

  return (
    <div>
      <PageHeader
        title="Parqueaderos"
        subtitle="Gestión de parqueaderos de visitantes"
        action={
          <div className="filters">
            <Select
              id="f-estado"
              value={filtroEstado}
              onChange={(e) => {
                setFiltroEstado(e.target.value);
                setPage(0);
              }}
              className="filter-select"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e || 'Todos'}
                </option>
              ))}
            </Select>
            <Select
              id="f-tipo"
              value={filtroTipo}
              onChange={(e) => {
                setFiltroTipo(e.target.value);
                setPage(0);
              }}
              className="filter-select"
            >
              {TIPOS.map((t) => (
                <option key={t || 'all'} value={t}>
                  {t || 'Todos'}
                </option>
              ))}
            </Select>
            <Button>+ Nuevo</Button>
          </div>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay parqueaderos"
        keyField="idParqueadero"
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
