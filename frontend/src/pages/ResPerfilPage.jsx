import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { valTelefono, valEmail, valUsername } from '../lib/validation.js';
import { todayStr, dateToStr } from '../lib/utils.js';

export default function ResPerfilPage() {
  const { user } = useAuth();
  const [modalOpen, setModalOpen] = useState(false);
  const [edit, setEdit] = useState({ telefono: '', email: '' });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const { touch, fieldError } = useLiveValidation();

  const { data, refetch } = useFetch(() => (user?.idResidente ? api.get(`/personas/${user.idResidente}`) : Promise.resolve(null)), [user]);
  const perfil = data?.raw || data || {};

  function openEdit() {
    setEdit({ telefono: perfil.telefono || '', email: perfil.email || '' });
    setErrors({});
    setModalOpen(true);
  }
  function validate() {
    const e = {};
    const rTel = valTelefono(edit.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(edit.email, { required: false });
    if (!rEmail.ok) e.email = rEmail.mensaje;
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (savingRef.current) return; // doble submit
    if (!validate()) return;
    savingRef.current = true;
    setSaving(true);
    try {
      await api.put(`/personas/${user?.idResidente}`, {
          tipoDocumentoId: Number(perfil.tipoDocumentoId || perfil.idTipoDoc),
          numeroDocumento: perfil.numeroDocumento,
          tipoPersona: "NATURAL",
          primerNombre: perfil.primerNombre || perfil.nombres || '',
          segundoNombre: perfil.segundoNombre || '',
          primerApellido: perfil.primerApellido || perfil.apellidos || '',
          segundoApellido: perfil.segundoApellido || '',
        telefono: edit.telefono.replace(/\D/g, ''),
        email: edit.email || null,
      });
      toast.success('Perfil actualizado');
      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      savingRef.current = false;
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
            value={`${perfil.primerNombre || perfil.nombres || ''} ${perfil.segundoNombre || ''} ${perfil.primerApellido || perfil.apellidos || ''} ${perfil.segundoApellido || ''}`.trim()}
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
            onBlur={() => touch('telefono')}
            error={fieldError('telefono', valTelefono(edit.telefono, { required: false })) || errors.telefono}
          />
        </div>
        <div className="form-group">
          <Input
            id="editEmail"
            label="Email"
            type="email"
            value={edit.email}
            onChange={(e) => setEdit((s) => ({ ...s, email: e.target.value }))}
            onBlur={() => touch('email')}
            error={fieldError('email', valEmail(edit.email, { required: false })) || errors.email}
          />
        </div>
      </Modal>
    </div>
  );
}
