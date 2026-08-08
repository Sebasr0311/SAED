import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import Toast from '../components/ui/Toast.jsx';
import { valTelefono, valUsername, todayStr, dateToStr } from '../lib/utils.js';

const TIPOS_DOC = [
  { value: 1, nombre: 'C.C.' },
  { value: 2, nombre: 'C.E.' },
  { value: 3, nombre: 'NIT' },
  { value: 4, nombre: 'Pasaporte' },
  { value: 5, nombre: 'T.I.' },
];

export default function ResPerfilPage() {
  const { user } = useAuth();
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [edit, setEdit] = useState({ telefono: '', email: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const { data, refetch } = useFetch(() => api.get(`/residentes/${user?.idResidente}`), [user]);
  const perfil = data?.raw || data || {};

  function openEdit() {
    setEdit({ telefono: perfil.telefono || '', email: perfil.email || '' });
    setErrors({});
    setModalOpen(true);
  }
  function validate() {
    const e = {};
    if (!valTelefono(edit.telefono)) e.telefono = 'Debe ser un teléfono válido de 10 dígitos';
    if (edit.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(edit.email)) e.email = 'Email inválido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    setSaving(true);
    try {
      await api.put(`/residentes/${user?.idResidente}/perfil`, {
        telefono: edit.telefono.replace(/\D/g, ''),
        email: edit.email || null,
      });
      setToast({ message: 'Perfil actualizado', type: 'success' });
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Mi Perfil"
        subtitle="Datos personales"
        action={<Button onClick={openEdit}>Editar</Button>}
      />
      <div className="card" style={{ maxWidth: '640px' }}>
        <div className="form-row">
          <Input
            id="nombres"
            label="Nombres"
            value={`${perfil.nombres || ''} ${perfil.apellidos || ''}`.trim()}
            readOnly
          />
          <Input id="documento" label="Documento" value={perfil.numeroDocumento || ''} readOnly />
        </div>
        <div className="form-row">
          <Input
            id="fechaNacimiento"
            label="Fecha de Nacimiento"
            value={dateToStr(perfil.fechaNacimiento) || ''}
            readOnly
          />
          <Input id="telefono" label="Teléfono" value={perfil.telefono || ''} readOnly />
        </div>
        <div className="form-group">
          <Input id="email" label="Email" value={perfil.email || ''} readOnly />
        </div>
        {perfil.idApartamento && (
          <div className="form-group">
            <Input
              id="apartamento"
              label="Apartamento"
              value={`Apto ${perfil.numeroApartamento || ''}`}
              readOnly
            />
          </div>
        )}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Editar Perfil"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        <div className="form-group">
          <Input
            id="editTelefono"
            label="Teléfono"
            value={edit.telefono}
            onChange={(e) => setEdit((s) => ({ ...s, telefono: e.target.value }))}
            error={errors.telefono}
          />
        </div>
        <div className="form-group">
          <Input
            id="editEmail"
            label="Email"
            type="email"
            value={edit.email}
            onChange={(e) => setEdit((s) => ({ ...s, email: e.target.value }))}
            error={errors.email}
          />
        </div>
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
