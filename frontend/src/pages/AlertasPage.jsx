import { useState } from 'react';
import { toast } from 'sonner';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, periodoLabel } from '../lib/utils.js';

const PAGE_SIZE = 15;

export default function AlertasPage() {
  const [page, setPage] = useState(0);
  const [soloNoLeidas, setSoloNoLeidas] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState(null);

  const qs = new URLSearchParams({
    ...(soloNoLeidas ? { soloNoLeidas: 'true' } : {}),
  });
  const { data, loading, error, refetch } = useFetch(() => api.get(`/alertas?${qs}`), [soloNoLeidas]);

  const items = (Array.isArray(data) ? data : data?.items || []).filter((a) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [a.tipoAlerta, a.numeroApartamento, a.nombreResidente, a.estadoCuota]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  });
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  async function marcarLeida() {
    if (!selectedId) {
      toast.error('Seleccione una alerta de la tabla');
      return;
    }
    try {
      await api.put(`/alertas/${selectedId}/leer`);
      toast.success('Alerta marcada como leída');
      setSelectedId(null);
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  const columns = [
    { key: 'idAlerta', label: 'ID', width: 60 },
    { key: 'tipoAlerta', label: 'Tipo' },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Residente' },
    {
      key: 'periodo',
      label: 'Periodo',
      render: (r) => periodoLabel(r.anio, r.mes),
    },
    { key: 'estadoCuota', label: 'Estado Cuota' },
    { key: 'canal', label: 'Canal' },
    {
      key: 'leida',
      label: 'Leída',
      render: (r) => (
        <span className={`badge ${r.leida ? 'badge-activo' : 'badge-pendiente-firma'}`}>{r.leida ? 'Sí' : 'No'}</span>
      ),
    },
    { key: 'enviadaEn', label: 'Enviada', render: (r) => formatDate(r.enviadaEn) },
  ];

  return (
    <div>
      <PageHeader
        title="Alertas"
        subtitle="Notificaciones enviadas a residentes"
        action={
          <>
            <label className="checkbox-label">
              <input type="checkbox" checked={soloNoLeidas} onChange={(e) => { setSoloNoLeidas(e.target.checked); setPage(0); }} />
              <span>Solo no leídas</span>
            </label>
            <Input id="search" aria-label="Buscar" placeholder="Buscar..." value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
            <Button onClick={marcarLeida}>Marcar Leída</Button>
          </>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
                empty={{ icon: 'notifications', title: 'No hay alertas', subtitle: 'Cuando se registre un pago o evento importante, aparecerá aquí.' }}
            error={error?.message}
        keyField="idAlerta"
        selectedKey={selectedId}
        onRowClick={(row) => setSelectedId(row.idAlerta === selectedId ? null : row.idAlerta)}
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={items.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />
    </div>
  );
}
