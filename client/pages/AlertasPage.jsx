import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function AlertasPage() {
  const [page] = useState(0);
  const [toast, setToast] = useState(null);
  const [soloNoLeidas, setSoloNoLeidas] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState(null);

  const qs = new URLSearchParams({ page, size: 20, ...(soloNoLeidas ? { soloNoLeidas: 'true' } : {}) });
  const { data, loading, refetch } = useFetch(() => api.get(`/alertas?${qs}`), [page, soloNoLeidas]);

  const items = (data?.items || []).filter((a) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [a.tipo, a.numeroApartamento, a.nombreResidente, a.estadoCuota]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  });

  async function marcarLeida() {
    if (!selectedId) {
      setToast({ message: 'Seleccione una alerta de la tabla', type: 'error' });
      return;
    }
    try {
      await api.put(`/alertas/${selectedId}/leer`);
      setToast({ message: 'Alerta marcada como leída', type: 'success' });
      setSelectedId(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  const columns = [
    { key: 'idAlerta', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Residente' },
    { key: 'periodo', label: 'Periodo' },
    { key: 'estadoCuota', label: 'Estado Cuota' },
    { key: 'canal', label: 'Canal' },
    {
      key: 'leida',
      label: 'Leída',
      render: (r) => (
        <span className={`badge ${r.leida ? 'badge-activo' : 'badge-pendiente-firma'}`}>{r.leida ? 'Sí' : 'No'}</span>
      ),
    },
    { key: 'fechaEnvio', label: 'Enviada', render: (r) => formatDate(r.fechaEnvio) },
  ];

  return (
    <div>
      <PageHeader
        title="Alertas"
        subtitle="Notificaciones enviadas a residentes"
        action={
          <>
            <label className="checkbox-label">
              <input type="checkbox" checked={soloNoLeidas} onChange={(e) => setSoloNoLeidas(e.target.checked)} />
              <span>Solo no leídas</span>
            </label>
            <Input id="search" placeholder="Buscar..." value={search} onChange={(e) => setSearch(e.target.value)} />
            <Button onClick={marcarLeida}>Marcar Leída</Button>
          </>
        }
      />
      <DataTable
        columns={columns}
        rows={items}
        loading={loading}
        empty="No hay alertas"
        keyField="idAlerta"
        selectedKey={selectedId}
        onRowClick={(row) => setSelectedId(row.idAlerta === selectedId ? null : row.idAlerta)}
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={() => {}}
      />
      <Toast toast={toast} />
    </div>
  );
}
