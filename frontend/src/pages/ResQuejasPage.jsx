import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import { formatDate } from '../lib/utils.js';

const CATS = [
  { value: 'ADMINISTRACION', label: 'Administración' },
  { value: 'MANTENIMIENTO', label: 'Mantenimiento' },
  { value: 'CONVIVENCIA', label: 'Convivencia' },
  { value: 'SEGURIDAD', label: 'Seguridad' },
  { value: 'ZONAS_COMUNES', label: 'Zonas Comunes' },
  { value: 'OTRO', label: 'Otro' },
];

const TIPOS = [
  { value: 'PETICION', label: 'Petición' },
  { value: 'QUEJA', label: 'Queja' },
  { value: 'RECLAMO', label: 'Reclamo' },
  { value: 'SUGERENCIA', label: 'Sugerencia' },
];

const PRIORIDADES = [
  { value: 'BAJA', label: 'Baja' },
  { value: 'MEDIA', label: 'Media' },
  { value: 'ALTA', label: 'Alta' },
];

const ESTADO_BADGE = {
  RADICADO: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTO: 'badge-activo',
  CERRADO: 'badge-neutral',
};

export default function ResQuejasPage() {
  const { user } = useAuth();
  const [form, setForm] = useState({ tipo: 'PETICION', categoria: 'ADMINISTRACION', prioridad: 'MEDIA', asunto: '', descripcion: '' });
  const [saving, setSaving] = useState(false);
  const [modal, setModal] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/pqrs/mis-tickets`), [user]);
  const rows = Array.isArray(data) ? data : data?.items || [];

  const [errors, setErrors] = useState({});

  const validate = () => {
    const newErrors = {};
    if (!form.asunto?.trim()) newErrors.asunto = 'El asunto es obligatorio';
    if (!form.descripcion?.trim()) newErrors.descripcion = 'La descripción es obligatoria';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const onSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = { ...form };
      await api.post('/pqrs', payload);
      toast.success('PQRS radicada exitosamente');
      setForm({ tipo: 'PETICION', categoria: 'ADMINISTRACION', prioridad: 'MEDIA', asunto: '', descripcion: '' });
      refetch();
    } catch (err) {
      toast.error('Error al enviar la PQRS');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'numeroRadicado', label: 'Radicado' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'asunto', label: 'Asunto' },
    { 
      key: 'estado', 
      label: 'Estado', 
      render: (r) => (
        <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>
          {r.estado}
        </span>
      ) 
    },
    { key: 'fechaRadicacion', label: 'Radicado El', render: (r) => formatDate(r.fechaRadicacion) },
  ];

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto' }}>
      <PageHeader title="Mis PQRS" subtitle="Radica tus Peticiones, Quejas, Reclamos y Sugerencias" />

      <div className="card" style={{ marginBottom: '24px' }}>
        <h3 style={{ marginBottom: '16px' }}>Radicar nueva PQRS</h3>
        <form onSubmit={onSubmit} className="card-grid-2">
          <Select
            id="tipo"
            label="Tipo de Solicitud"
            value={form.tipo}
            onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value }))}
          >
            {TIPOS.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </Select>
          <Select
            id="categoria"
            label="Categoría"
            value={form.categoria}
            onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
          >
            {CATS.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </Select>
          <Select
            id="prioridad"
            label="Prioridad"
            value={form.prioridad}
            onChange={(e) => setForm((f) => ({ ...f, prioridad: e.target.value }))}
          >
            {PRIORIDADES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </Select>
          <Input
            id="asunto"
            label="Asunto"
            placeholder="Breve título descriptivo..."
            value={form.asunto}
            onChange={(e) => setForm((f) => ({ ...f, asunto: e.target.value }))}
            error={errors.asunto}
          />
          <div style={{ gridColumn: '1 / -1' }}>
            <Textarea
              id="descripcion"
              label="Descripción detallada"
              rows={4}
              placeholder="Explique su solicitud con claridad..."
              value={form.descripcion}
              onChange={(e) => setForm((f) => ({ ...f, descripcion: e.target.value }))}
              error={errors.descripcion}
            />
          </div>
          
          <div style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
            <Button type="submit" disabled={saving}>
              {saving ? 'Enviando...' : 'Radicar PQRS'}
            </Button>
          </div>
        </form>
      </div>

      <h3 style={{ marginBottom: '16px' }}>Mi Historial de PQRS</h3>
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'inbox', title: 'Aún no tienes radicados', subtitle: 'Tus tickets aparecerán aquí.' }}
        keyField="idTicket"
        onRowClick={(r) => setModal(r)}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={`Detalle PQRS: ${modal?.numeroRadicado}`}
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
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Tipo y Categoría</div>
                <div style={{ fontSize: '13px' }}>{modal.tipo} - {modal.categoria}</div>
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
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Fecha de Radicación</div>
                <div style={{ fontSize: '13px' }}>{formatDate(modal.fechaRadicacion)}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Vencimiento SLA</div>
                <div style={{ fontSize: '13px', color: new Date(modal.fechaLimiteSla) < new Date() ? 'var(--danger-color)' : 'inherit' }}>
                  {formatDate(modal.fechaLimiteSla)}
                </div>
              </div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Asunto</div>
              <div style={{ fontSize: '14px', fontWeight: 500 }}>{modal.asunto}</div>
            </div>
            <div className="form-group" style={{ marginBottom: '16px' }}>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Descripción de la solicitud</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                {modal.descripcion}
              </div>
            </div>
            
            {modal.estado === 'RESUELTO' || modal.estado === 'CERRADO' ? (
              <div className="form-group">
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Observaciones de Cierre</div>
                <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-accent)', color: 'var(--accent-color)', padding: '12px', borderRadius: '6px' }}>
                  {modal.observacionCierre || 'Ticket cerrado sin observaciones adicionales.'}
                </div>
              </div>
            ) : null}
          </>
        )}
      </Modal>
    </div>
  );
}
