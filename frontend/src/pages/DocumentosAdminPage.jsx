import React, { useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { useFetch } from '../lib/hooks';
import { api } from '../lib/api';
import { toast } from 'sonner';

const CATEGORIAS = ['REGLAMENTO', 'ACTA', 'FINANZAS', 'CONTRATO', 'MANUAL', 'POLIZA', 'OTRO'];

export default function DocumentosAdminPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/api/v1/documentos/admin'));
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    titulo: '',
    categoria: 'REGLAMENTO',
    descripcion: '',
    esPublicoResidentes: 'N',
    rolMinimoAcceso: 'ADMIN_PROPIEDAD',
    archivoUrl: ''
  });

  const columns = [
    { key: 'titulo', label: 'Título' },
    { key: 'categoria', label: 'Categoría', render: (r) => <span className="badge badge-neutral">{r.categoria}</span> },
    { key: 'esPublicoResidentes', label: 'Público', render: (r) => r.esPublicoResidentes === 'S' ? 'Sí' : 'No' },
    { key: 'rolMinimoAcceso', label: 'Rol Mínimo' },
    { key: 'numeroVersion', label: 'Versión', render: (r) => r.numeroVersion ? `v${r.numeroVersion}` : 'N/A' },
    {
      key: 'actions',
      label: 'Acciones',
      render: (r) => (
        <div style={{ display: 'flex', gap: '8px' }}>
          {r.archivoUrl && (
            <Button variant="ghost" icon="download" onClick={() => window.open(r.archivoUrl, '_blank')} />
          )}
          <Button variant="ghost" icon="delete" onClick={() => handleDelete(r.idDocumento)} />
        </div>
      )
    }
  ];

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      // Simulate file upload by setting a dummy URL if one wasn't provided
      const payload = { ...form };
      if (!payload.archivoUrl) {
        payload.archivoUrl = `https://storage.saed.com/docs/${Date.now()}.pdf`;
      }
      
      await api.post('/api/v1/documentos', payload);
      toast.success('Documento subido exitosamente');
      setModalOpen(false);
      setForm({ titulo: '', categoria: 'REGLAMENTO', descripcion: '', esPublicoResidentes: 'N', rolMinimoAcceso: 'ADMIN_PROPIEDAD', archivoUrl: '' });
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    if (!confirm('¿Seguro que desea eliminar este documento?')) return;
    try {
      await api.delete(`/api/v1/documentos/${id}`);
      toast.success('Documento eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  return (
    <div>
      <PageHeader
        title="Repositorio Documental"
        subtitle="Gestión de actas, reglamentos y contratos de la copropiedad"
        action={<Button onClick={() => setModalOpen(true)} icon="upload">Subir Documento</Button>}
      />

      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        error={error?.message}
        keyField="idDocumento"
        empty={{ icon: 'folder', title: 'Sin documentos', subtitle: 'El repositorio está vacío.' }}
      />

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Subir Nuevo Documento">
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <Input label="Título del Documento" value={form.titulo} onChange={e => setForm({...form, titulo: e.target.value})} required />
          <Select label="Categoría" value={form.categoria} onChange={e => setForm({...form, categoria: e.target.value})} required>
            {CATEGORIAS.map(c => <option key={c} value={c}>{c}</option>)}
          </Select>
          <div className="form-control">
            <label className="label">Descripción</label>
            <textarea className="input" value={form.descripcion} onChange={e => setForm({...form, descripcion: e.target.value})} />
          </div>
          
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', background: 'var(--surface-hover)', padding: '16px', borderRadius: '8px' }}>
            <Select label="¿Es público para residentes?" value={form.esPublicoResidentes} onChange={e => setForm({...form, esPublicoResidentes: e.target.value})}>
              <option value="S">Sí, todos lo ven</option>
              <option value="N">No, solo administración</option>
            </Select>
            <Select label="Rol mínimo (si no es público)" value={form.rolMinimoAcceso} onChange={e => setForm({...form, rolMinimoAcceso: e.target.value})} disabled={form.esPublicoResidentes === 'S'}>
              <option value="ADMIN_PROPIEDAD">Administrador</option>
              <option value="CONSEJO">Miembro del Consejo</option>
              <option value="SUPERADMIN">Super Admin</option>
            </Select>
          </div>

          <div style={{ padding: '16px', border: '2px dashed var(--border)', borderRadius: '8px', textAlign: 'center' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '32px', color: 'var(--text-secondary)' }}>cloud_upload</span>
            <p style={{ margin: '8px 0 0 0', fontSize: '14px', color: 'var(--text-secondary)' }}>
              (Simulación) El archivo será asignado automáticamente al guardar.
            </p>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
            <Button variant="ghost" onClick={() => setModalOpen(false)} type="button">Cancelar</Button>
            <Button type="submit" loading={submitting}>Subir y Notificar</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
