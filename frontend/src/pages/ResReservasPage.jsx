import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDate } from '../lib/utils.js';

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  APROBADA: 'badge-activo',
  RECHAZADA: 'badge-danger',
  CANCELADA: 'badge-neutral',
};

export default function ResReservasPage() {
  const { user } = useAuth();
  const [form, setForm] = useState({ idZona: '', fechaReserva: '', horaInicio: '', horaFin: '', cantidadAsistentes: 1, observaciones: '' });
  const [saving, setSaving] = useState(false);
  const [modal, setModal] = useState(null);

  const { data: zonasData } = useFetch(() => api.get('/zonas-comunes'), []);
  const zonas = Array.isArray(zonasData) ? zonasData : zonasData?.items || [];

  const { data: reservasData, loading, refetch } = useFetch(() => api.get(`/reservas/mis-reservas`), [user]);
  const rows = Array.isArray(reservasData) ? reservasData : reservasData?.items || [];

  const [errors, setErrors] = useState({});

  const validate = () => {
    const newErrors = {};
    if (!form.idZona) newErrors.idZona = 'Seleccione una zona común';
    if (!form.fechaReserva) newErrors.fechaReserva = 'Seleccione una fecha';
    if (!form.horaInicio) newErrors.horaInicio = 'Ingrese hora de inicio';
    if (!form.horaFin) newErrors.horaFin = 'Ingrese hora de fin';
    if (form.cantidadAsistentes < 1) newErrors.cantidadAsistentes = 'Mínimo 1 asistente';
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = { ...form, idZona: Number(form.idZona) };
      await api.post('/reservas', payload);
      toast.success('Reserva solicitada exitosamente');
      setForm({ idZona: '', fechaReserva: '', horaInicio: '', horaFin: '', cantidadAsistentes: 1, observaciones: '' });
      refetch();
    } catch {
      toast.error('Error al solicitar reserva');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'nombreZona', label: 'Zona Común' },
    { key: 'fechaReserva', label: 'Fecha', render: (r) => r.fechaReserva },
    { key: 'horario', label: 'Horario', render: (r) => `${r.horaInicio} - ${r.horaFin}` },
    { 
      key: 'estado', 
      label: 'Estado', 
      render: (r) => (
        <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>
          {r.estado}
        </span>
      ) 
    },
    { key: 'fechaSolicitud', label: 'Solicitado el', render: (r) => formatDate(r.fechaSolicitud) },
  ];

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto' }}>
      <PageHeader title="Mis Reservas" subtitle="Agenda las Zonas Comunes de la propiedad" />

      <div className="card" style={{ marginBottom: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>Solicitar Reserva</h3>
        <form onSubmit={onSubmit} className="card-grid-3">
          <Select
            id="idZona"
            label="Zona Común"
            value={form.idZona}
            onChange={(e) => setForm((f) => ({ ...f, idZona: e.target.value }))}
            error={errors.idZona}
          >
            <option value="">Seleccione...</option>
            {zonas.map((z) => (
              <option key={z.idZona} value={z.idZona}>
                {z.nombre} (Aforo: {z.aforoMaximo})
              </option>
            ))}
          </Select>
          
          <Input
            id="fechaReserva"
            label="Fecha"
            type="date"
            value={form.fechaReserva}
            onChange={(e) => setForm((f) => ({ ...f, fechaReserva: e.target.value }))}
            error={errors.fechaReserva}
            min={new Date().toISOString().split('T')[0]}
          />
          
          <Input
            id="cantidadAsistentes"
            label="Número de Asistentes"
            type="number"
            min="1"
            value={form.cantidadAsistentes}
            onChange={(e) => setForm((f) => ({ ...f, cantidadAsistentes: Number(e.target.value) }))}
            error={errors.cantidadAsistentes}
          />
          
          <Input
            id="horaInicio"
            label="Hora Inicio"
            type="time"
            value={form.horaInicio}
            onChange={(e) => setForm((f) => ({ ...f, horaInicio: e.target.value }))}
            error={errors.horaInicio}
          />
          
          <Input
            id="horaFin"
            label="Hora Fin"
            type="time"
            value={form.horaFin}
            onChange={(e) => setForm((f) => ({ ...f, horaFin: e.target.value }))}
            error={errors.horaFin}
          />
          
          <div style={{ gridColumn: '1 / -1' }}>
            <Textarea
              id="observaciones"
              label="Observaciones (Opcional)"
              rows={2}
              placeholder="Ej: Cumpleaños, decoraciones, etc."
              value={form.observaciones}
              onChange={(e) => setForm((f) => ({ ...f, observaciones: e.target.value }))}
            />
          </div>
          
          <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
            <Button type="submit" disabled={saving}>
              {saving ? 'Solicitando...' : 'Confirmar Reserva'}
            </Button>
          </div>
        </form>
      </div>

      <h3 style={{ marginBottom: '16px' }}>Mi Historial de Reservas</h3>
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'event_busy', title: 'Aún no tienes reservas', subtitle: 'Agrega una desde el formulario superior.' }}
        keyField="idReserva"
        onRowClick={(r) => setModal(r)}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={`Reserva de ${modal?.nombreZona}`}
        footer={
          <Button variant="outline" onClick={() => setModal(null)}>
            Cerrar
          </Button>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Día y Horario</div>
                <div style={{ fontSize: '13px' }}>{modal.fechaReserva} | {modal.horaInicio} a {modal.horaFin}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Estado Actual</div>
                <div style={{ fontSize: '13px' }}>
                  <span className={`badge ${ESTADO_BADGE[modal.estado] || 'badge-neutral'}`}>
                    {modal.estado}
                  </span>
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Asistentes Esperados</div>
                <div style={{ fontSize: '13px' }}>{modal.cantidadAsistentes} personas</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Costo Liquidado</div>
                <div style={{ fontSize: '13px' }}>${Number(modal.costoTotal || 0).toLocaleString()}</div>
              </div>
            </div>
            {modal.observaciones && (
              <div className="form-group" style={{ marginBottom: '16px' }}>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Mis Observaciones</div>
                <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                  {modal.observaciones}
                </div>
              </div>
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
