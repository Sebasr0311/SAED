import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import Toast from '../components/ui/Toast.jsx';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';

const CATS = ['LIMPIEZA', 'SEGURIDAD', 'MANTENIMIENTO', 'CONVIVENCIA', 'ZONAS_COMUNES', 'OTRO'];

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTA: 'badge-activo',
  CERRADA: 'badge-neutral',
};

export default function ResQuejasPage() {
  const { user } = useAuth();
  const [form, setForm] = useState({
    tipo: 'QUEJA',
    categoria: 'LIMPIEZA',
    titulo: '',
    descripcion: '',
  });
  const [sending, setSending] = useState(false);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(
    () => api.get(`/quejas/residente/${user?.idResidente}`),
    [user]
  );

  function update(k, v) {
    setForm((f) => ({ ...f, [k]: v }));
  }
  async function send() {
    if (!form.titulo || !form.descripcion) {
      setToast({ message: 'Título y descripción son obligatorios', type: 'error' });
      return;
    }
    setSending(true);
    try {
      await api.post('/quejas', { ...form, idResidente: user?.idResidente });
      setToast({ message: 'Solicitud enviada', type: 'success' });
      setForm({ tipo: 'QUEJA', categoria: 'LIMPIEZA', titulo: '', descripcion: '' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSending(false);
    }
  }

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'titulo', label: 'Título' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    { key: 'fechaCreacion', label: 'Fecha' },
  ];

  return (
    <div>
      <PageHeader title="Mis Solicitudes" subtitle="Quejas, sugerencias y apelaciones" />
      <div className="card" style={{ maxWidth: '720px', marginBottom: '24px' }}>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)}>
            <option value="QUEJA">Queja</option>
            <option value="SUGERENCIA">Sugerencia</option>
            <option value="APELACION">Apelación</option>
          </Select>
          <Select
            id="categoria"
            label="Categoría"
            value={form.categoria}
            onChange={(e) => update('categoria', e.target.value)}
          >
            {CATS.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </Select>
        </div>
        <div className="form-group">
          <Input
            id="titulo"
            label="Título"
            value={form.titulo}
            onChange={(e) => update('titulo', e.target.value)}
          />
        </div>
        <div className="form-group">
          <Textarea
            id="descripcion"
            label="Descripción"
            rows={4}
            value={form.descripcion}
            onChange={(e) => update('descripcion', e.target.value)}
          />
        </div>
        <Button onClick={send} disabled={sending}>{sending ? 'Enviando...' : 'Enviar Solicitud'}</Button>
      </div>

      <h3 style={{ marginBottom: '12px', fontSize: '15px', fontWeight: 600 }}>Historial</h3>
      <DataTable
        columns={columns}
        rows={data || []}
        loading={loading}
        empty="No has enviado solicitudes"
        keyField="id"
      />
      <Toast toast={toast} />
    </div>
  );
}
