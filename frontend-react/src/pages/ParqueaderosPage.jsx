import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';

const ESTADOS = ['', 'DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];
const TIPOS = ['', 'VEHICULO', 'MOTO', 'BICICLETA'];
const emptyForm = { codigo: '', tipo: 'VEHICULO', estado: 'DISPONIBLE', idApartamento: '' };

export default function ParqueaderosPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editing, setEditing] = useState(null);

  const qs = new URLSearchParams({
    page,
    size: 20,
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/parqueaderos?${qs}`), [page, filtroEstado, filtroTipo]);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=200'), []);

  const columns = [
    { key: 'idParqueadero', label: 'ID', width: 60 },
    { key: 'codigo', label: 'Código' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'estado', label: 'Estado' },
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
            <Select
              id="f-tipo"
              value={filtroTipo}
              onChange={(e) => {
                setFiltroTipo(e.target.value);
                setPage(0);
              }}
              className="w-auto"
            >
              {TIPOS.map((t) => (
                <option key={t || 'all'} value={t}>
                  {t || 'Todos'}
                </option>
              ))}
            </Select>
            <Button icon="add">Nuevo</Button>
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
