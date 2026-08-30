import React, { useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { Modal } from '../components/ui/Modal';
import { Select } from '../components/ui/select';
import { Input } from '../components/ui/input';
import { Button } from '../components/ui/Button';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

const PAGE_SIZE = 10;
const ESTADO_BADGE = {
  NOTIFICADA: 'badge-warning',
  EN_DESCARGOS: 'badge-info',
  ABSUELTA: 'badge-neutral',
  APLICADA: 'badge-error',
};

export default function SancionesAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const { data, loading, error, refetch } = useFetch(() => api.get('/sanciones/todas'));

  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({ idUnidad: '', idPersonaImputada: '', tipoFalta: '', gravedad: 'LEVE', descripcionHechos: '' });
  const [submitting, setSubmitting] = useState(false);

  const [detalle, setDetalle] = useState(null);
  const [resolucionForm, setResolucionForm] = useState({ decision: 'APLICADA', resolucionFinal: '' });

  const items = data?.items || [];
  const filtered = filtroEstado ? items.filter(i => i.estado === filtroEstado) : items;
  
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const safePage = Math.min(page, totalPages - 1);
  const rows = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const columns = [
    { key: 'numeroExpediente', label: 'Expediente', width: 140 },
    { key: 'idUnidad', label: 'Unidad' },
    { key: 'tipoFalta', label: 'Falta' },
    {
      key: 'gravedad',
      label: 'Gravedad',
      render: (r) => <span className={`badge ${r.gravedad === 'GRAVE' ? 'badge-error' : 'badge-warning'}`}>{r.gravedad}</span>,
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    }
  ];

  async function handleCreate(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/sanciones', { ...form, idUnidad: Number(form.idUnidad), idPersonaImputada: Number(form.idPersonaImputada) });
      toast.success('Sanción creada exitosamente');
      setModalOpen(false);
      setForm({ idUnidad: '', idPersonaImputada: '', tipoFalta: '', gravedad: 'LEVE', descripcionHechos: '' });
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEmitirResolucion(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post(`/api/v1/sanciones/${detalle.idSancion}/resolucion`, resolucionForm);
      toast.success('Resolución emitida exitosamente');
      setDetalle(null);
      setResolucionForm({ decision: 'APLICADA', resolucionFinal: '' });
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Debido Proceso y Sanciones"
        subtitle="Gestión de pliegos, descargos y resoluciones"
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Select value={filtroEstado} onChange={e => { setFiltroEstado(e.target.value); setPage(0); }}>
              <option value="">Todos los estados</option>
              <option value="NOTIFICADA">Notificada</option>
              <option value="EN_DESCARGOS">En Descargos</option>
              <option value="ABSUELTA">Absuelta</option>
              <option value="APLICADA">Sanción Aplicada</option>
            </Select>
            <Button onClick={() => setModalOpen(true)} icon="gavel">Nuevo Pliego</Button>
          </div>
        }
      />

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'balance', title: 'Sin sanciones', subtitle: 'No hay procesos sancionatorios activos.' }}
        error={error?.message}
        keyField="idSancion"
        onRowClick={setDetalle}
      />
      
      <Pagination page={safePage} totalPages={totalPages} totalItems={filtered.length} pageSize={PAGE_SIZE} onPageChange={setPage} />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Abrir Pliego de Cargos" size="md">
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <Input label="ID Unidad" value={form.idUnidad} onChange={e => setForm({...form, idUnidad: e.target.value})} required type="number" />
          <Input label="ID Persona Imputada" value={form.idPersonaImputada} onChange={e => setForm({...form, idPersonaImputada: e.target.value})} required type="number" />
          <Input label="Tipo de Falta" value={form.tipoFalta} onChange={e => setForm({...form, tipoFalta: e.target.value})} required placeholder="Ej. Ruido fuera de horario" />
          <Select label="Gravedad" value={form.gravedad} onChange={e => setForm({...form, gravedad: e.target.value})} required>
            <option value="LEVE">Leve</option>
            <option value="MODERADA">Moderada</option>
            <option value="GRAVE">Grave</option>
          </Select>
          <div className="form-control">
            <label className="label">Descripción de los hechos</label>
            <textarea className="input" style={{ minHeight: '80px', padding: '8px' }} value={form.descripcionHechos} onChange={e => setForm({...form, descripcionHechos: e.target.value})} required />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
            <Button variant="ghost" onClick={() => setModalOpen(false)} type="button">Cancelar</Button>
            <Button type="submit" loading={submitting}>Notificar Pliego</Button>
          </div>
        </form>
      </Modal>

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title={`Expediente ${detalle?.numeroExpediente}`} size="md">
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="detail-row"><span>Estado</span><span className={`badge ${ESTADO_BADGE[detalle.estado]}`}>{detalle.estado}</span></div>
            <div className="detail-row"><span>Gravedad</span><span>{detalle.gravedad}</span></div>
            <div className="detail-row"><span>Falta</span><span>{detalle.tipoFalta}</span></div>
            <div style={{ padding: '12px', background: 'var(--surface-hover)', borderRadius: '8px' }}>
              <strong style={{ fontSize: '12px' }}>Hechos:</strong>
              <p style={{ margin: '4px 0 0 0', fontSize: '14px' }}>{detalle.descripcionHechos}</p>
            </div>
            
            {detalle.estado === 'EN_DESCARGOS' && (
              <form onSubmit={handleEmitirResolucion} style={{ marginTop: '16px', display: 'flex', flexDirection: 'column', gap: '12px', borderTop: '1px solid var(--border)', paddingTop: '16px' }}>
                <h4 style={{ margin: 0 }}>Emitir Resolución Final</h4>
                <Select label="Decisión" value={resolucionForm.decision} onChange={e => setResolucionForm({...resolucionForm, decision: e.target.value})} required>
                  <option value="APLICADA">Aplicar Sanción</option>
                  <option value="ABSUELTA">Absolver / Archivar Proceso</option>
                </Select>
                <div className="form-control">
                  <label className="label">Justificación / Resolución</label>
                  <textarea className="input" style={{ minHeight: '80px', padding: '8px' }} value={resolucionForm.resolucionFinal} onChange={e => setResolucionForm({...resolucionForm, resolucionFinal: e.target.value})} required />
                </div>
                <Button type="submit" loading={submitting}>Firmar Resolución</Button>
              </form>
            )}

            {detalle.resolucionFinal && (
              <div style={{ padding: '12px', background: 'var(--surface-hover)', borderLeft: '4px solid var(--primary)', borderRadius: '8px', marginTop: '16px' }}>
                <strong style={{ fontSize: '12px' }}>Resolución Final:</strong>
                <p style={{ margin: '4px 0 0 0', fontSize: '14px' }}>{detalle.resolucionFinal}</p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
