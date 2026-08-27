import { useState, useRef } from 'react';
import api from '../lib/api.js';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import { valEmail, valTelefono, valRequerido } from '../lib/validation.js';

import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';

const PAGE_SIZE = 10;

const emptyForm = {
  idTipoDocumento: 1, // Assume 1 is a valid Tipo Documento (e.g., CC)
  numeroDocumento: '',
  tipoPersona: 'NATURAL',
  primerNombre: '',
  segundoNombre: '',
  primerApellido: '',
  segundoApellido: '',
  correoElectronico: '',
  telefono: '',
};

export default function PersonasPage() {
  const [page, setPage] = useState(0);
  
  const { data, loading, error, refetch } = useFetch(() => api.get(`/v1/personas?page=${page}&size=${PAGE_SIZE}`), [page]);
  const { items, totalItems, totalPages } = data || { items: [], totalItems: 0, totalPages: 1 };
  
  // Also fetch tipos de documento if exists, otherwise fallback to static for now
  const { data: tiposData } = useFetch(() => api.get('/tipos-documento').catch(() => null), []);
  const tiposDoc = tiposData?.items || [
    { idTipoDoc: 1, nombre: 'Cdula de Ciudadana' },
    { idTipoDoc: 2, nombre: 'Cdula de Extranjera' },
    { idTipoDoc: 3, nombre: 'Pasaporte' },
    { idTipoDoc: 4, nombre: 'NIT' }
  ];

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [toast, setToast] = useState(null);
  const [formErrors, setFormErrors] = useState({});
  const savingRef = useRef(false);

  const { touched, touch, fieldError } = useLiveValidation();

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    if (formErrors[field]) setFormErrors((e) => ({ ...e, [field]: null }));
  }

  const columns = [
    { key: 'tipoPersona', label: 'Tipo' },
    { 
      key: 'documento', 
      label: 'Documento', 
      render: (r) => `${r.numeroDocumento}` 
    },
    { 
      key: 'nombres', 
      label: 'Nombres', 
      render: (r) => `${r.primerNombre || ''} ${r.segundoNombre || ''}`.trim() 
    },
    { 
      key: 'apellidos', 
      label: 'Apellidos', 
      render: (r) => `${r.primerApellido || ''} ${r.segundoApellido || ''}`.trim() 
    },
    { key: 'correoElectronico', label: 'Email' },
    { key: 'telefono', label: 'Telfono' },
  ];

  const safePage = Math.min(Math.max(0, page), Math.max(0, totalPages - 1));

  async function save() {
    if (savingRef.current) return;
    
    // Basic validations
    if (!form.numeroDocumento || !form.primerNombre || !form.primerApellido) {
      setToast({ message: 'Por favor complete los campos obligatorios', type: 'error' });
      return;
    }

    savingRef.current = true;
    setSaving(true);
    
    try {
      await api.post('/v1/personas', form);
      setToast({ message: 'Persona registrada correctamente', type: 'success' });
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message || 'Error al registrar persona', type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Personas"
        subtitle="Registro centralizado de Personas (Propietarios, Residentes, etc)"
        action={
          <Button
            onClick={() => {
              setForm(emptyForm);
              setFormErrors({});
              setModalOpen(true);
            }}
          >
            + Nueva Persona
          </Button>
        }
      />
      
      {error ? (
        <div style={{ color: 'var(--error)', padding: '16px' }}>Error al cargar: {error.message}</div>
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={items}
            loading={loading}
            empty={{ icon: 'person', title: 'No hay personas', subtitle: 'Registra la primera persona.' }}
            keyField="idPersona"
          />
          <Pagination
            page={safePage}
            totalPages={totalPages}
            totalItems={totalItems}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Nueva Persona"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>{saving ? 'Guardando...' : 'Guardar'}</Button>
          </>
        }
      >
        <div className="form-row">
          <Select 
            id="tipoPersona" 
            label="Tipo Persona" 
            value={form.tipoPersona} 
            onChange={(e) => update('tipoPersona', e.target.value)}
          >
            <option value="NATURAL">Natural</option>
            <option value="JURIDICA">Jurdica</option>
          </Select>
          <Select 
            id="idTipoDocumento" 
            label="Tipo Documento" 
            value={form.idTipoDocumento} 
            onChange={(e) => update('idTipoDocumento', Number(e.target.value))}
          >
            {tiposDoc.map((t) => (
              <option key={t.idTipoDoc} value={t.idTipoDoc}>{t.nombre}</option>
            ))}
          </Select>
        </div>
        <div className="form-row">
          <Input
            id="numeroDocumento"
            label="Nmero de Documento *"
            value={form.numeroDocumento}
            onChange={(e) => update('numeroDocumento', e.target.value)}
            onBlur={() => touch('numeroDocumento')}
          />
        </div>
        <div className="form-row">
          <Input
            id="primerNombre"
            label="Primer Nombre *"
            value={form.primerNombre}
            onChange={(e) => update('primerNombre', e.target.value)}
            onBlur={() => touch('primerNombre')}
          />
          <Input
            id="segundoNombre"
            label="Segundo Nombre"
            value={form.segundoNombre}
            onChange={(e) => update('segundoNombre', e.target.value)}
          />
        </div>
        <div className="form-row">
          <Input
            id="primerApellido"
            label="Primer Apellido *"
            value={form.primerApellido}
            onChange={(e) => update('primerApellido', e.target.value)}
            onBlur={() => touch('primerApellido')}
          />
          <Input
            id="segundoApellido"
            label="Segundo Apellido"
            value={form.segundoApellido}
            onChange={(e) => update('segundoApellido', e.target.value)}
          />
        </div>
        <div className="form-row">
          <Input
            id="correoElectronico"
            label="Email"
            type="email"
            value={form.correoElectronico}
            onChange={(e) => update('correoElectronico', e.target.value)}
          />
          <Input
            id="telefono"
            label="Telfono"
            value={form.telefono}
            onChange={(e) => update('telefono', e.target.value)}
          />
        </div>
      </Modal>

      <Toast toast={toast} />
    </div>
  );
}
