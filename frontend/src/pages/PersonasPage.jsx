import { useState, useRef, useMemo } from 'react';
import { toast } from 'sonner';
import api from '../lib/api.js';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import { valEmail, valTelefono, valDocumento, valNombre, valApellido, getDocPlaceholder } from '../lib/validation.js';

import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

const PAGE_SIZE = 10;

const emptyForm = {
  idTipoDocumento: 1, // CC por defecto
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
  
  const { data, loading, error, refetch } = useFetch(() => api.get(`/personas?page=${page}&size=${PAGE_SIZE}`), [page]);
  const { items, totalItems, totalPages } = data || { items: [], totalItems: 0, totalPages: 1 };
  
  // Catálogo completo de documentos colombianos (con fallback garantizado)
  const { tiposDoc } = useTiposDocumento();

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formErrors, setFormErrors] = useState({});
  const savingRef = useRef(false);

  const { touched, touch, fieldError } = useLiveValidation();

  // Filtrar tipos de documento según sea Persona Natural o Jurídica
  const filteredTiposDoc = useMemo(() => {
    if (form.tipoPersona === 'JURIDICA') {
      const jurList = tiposDoc.filter((t) => t.codigo === 'NIT' || t.aplicaPersonaJuridica);
      return jurList.length > 0 ? jurList : tiposDoc;
    }
    const natList = tiposDoc.filter((t) => t.codigo !== 'NIT' || t.aplicaPersonaNatural);
    return natList.length > 0 ? natList : tiposDoc;
  }, [tiposDoc, form.tipoPersona]);

  // Código activo del documento para aplicar validación según normativa colombiana
  const activeCodigoDoc = useMemo(() => {
    const found = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDocumento));
    return found?.codigo || (form.tipoPersona === 'JURIDICA' ? 'NIT' : 'CC');
  }, [tiposDoc, form.idTipoDocumento, form.tipoPersona]);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    if (formErrors[field]) setFormErrors((e) => ({ ...e, [field]: null }));
  }

  function handleTipoPersonaChange(tipo) {
    if (tipo === 'JURIDICA') {
      const nitDoc = tiposDoc.find((t) => t.codigo === 'NIT') || { idTipoDoc: 2 };
      setForm((f) => ({
        ...f,
        tipoPersona: 'JURIDICA',
        idTipoDocumento: nitDoc.idTipoDoc,
        segundoNombre: '',
        primerApellido: '',
        segundoApellido: '',
      }));
    } else {
      const ccDoc = tiposDoc.find((t) => t.codigo === 'CC') || { idTipoDoc: 1 };
      setForm((f) => ({
        ...f,
        tipoPersona: 'NATURAL',
        idTipoDocumento: ccDoc.idTipoDoc,
      }));
    }
  }

  const columns = [
    { key: 'tipoPersona', label: 'Tipo' },
    { 
      key: 'documento', 
      label: 'Documento', 
      render: (r) => `${r.numeroDocumento || '—'}` 
    },
    { 
      key: 'nombres', 
      label: 'Nombres / Razón Social', 
      render: (r) => `${r.primerNombre || ''} ${r.segundoNombre || ''}`.trim() || '—' 
    },
    { 
      key: 'apellidos', 
      label: 'Apellidos', 
      render: (r) => `${r.primerApellido || ''} ${r.segundoApellido || ''}`.trim() || '—' 
    },
    { key: 'correoElectronico', label: 'Email', render: (r) => r.correoElectronico || '—' },
    { key: 'telefono', label: 'Teléfono', render: (r) => r.telefono || '—' },
  ];

  const safePage = Math.min(Math.max(0, page), Math.max(0, totalPages - 1));

  async function save() {
    if (savingRef.current) return;
    
    // 1. Validación de Documento bajo Normativa Colombiana
    const rDoc = valDocumento(form.numeroDocumento, activeCodigoDoc, 'El número de documento');
    if (!rDoc.ok) {
      toast.error(rDoc.mensaje);
      touch('numeroDocumento');
      return;
    }

    // 2. Validación de Nombre / Razón Social
    const labelNombre = form.tipoPersona === 'JURIDICA' ? 'La razón social' : 'El primer nombre';
    const rNom = valNombre(form.primerNombre, labelNombre);
    if (!rNom.ok) {
      toast.error(rNom.mensaje);
      touch('primerNombre');
      return;
    }

    // 3. Validación de Apellido (solo Natural)
    if (form.tipoPersona === 'NATURAL') {
      const rApe = valApellido(form.primerApellido, 'El primer apellido');
      if (!rApe.ok) {
        toast.error(rApe.mensaje);
        touch('primerApellido');
        return;
      }
    }

    // 4. Validaciones de Contacto
    if (form.correoElectronico) {
      const rMail = valEmail(form.correoElectronico, { required: false });
      if (!rMail.ok) {
        toast.error(rMail.mensaje);
        touch('correoElectronico');
        return;
      }
    }

    if (form.telefono) {
      const rTel = valTelefono(form.telefono, { required: false });
      if (!rTel.ok) {
        toast.error(rTel.mensaje);
        touch('telefono');
        return;
      }
    }

    savingRef.current = true;
    setSaving(true);
    
    try {
      await api.post('/personas', form);
      toast.success('Persona registrada correctamente');
      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al registrar persona');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Personas"
        subtitle="Registro centralizado de Personas (Propietarios, Residentes, Proveedores)"
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
        <div className="space-y-4 pt-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select 
              id="tipoPersona" 
              label="Tipo de Persona *" 
              value={form.tipoPersona} 
              onChange={(e) => handleTipoPersonaChange(e.target.value)}
            >
              <option value="NATURAL">Persona Natural</option>
              <option value="JURIDICA">Persona Jurídica</option>
            </Select>
            <Select 
              id="idTipoDocumento" 
              label="Tipo de Documento *" 
              value={form.idTipoDocumento} 
              onChange={(e) => update('idTipoDocumento', Number(e.target.value))}
            >
              {filteredTiposDoc.map((t) => (
                <option key={t.idTipoDoc} value={t.idTipoDoc}>{t.nombre} ({t.codigo})</option>
              ))}
            </Select>
          </div>
          <div className="grid grid-cols-1 gap-1.5">
            <Input
              id="numeroDocumento"
              label="Número de Documento *"
              placeholder={getDocPlaceholder(activeCodigoDoc)}
              value={form.numeroDocumento}
              onChange={(e) => update('numeroDocumento', e.target.value)}
              onBlur={() => touch('numeroDocumento')}
              error={fieldError('numeroDocumento', valDocumento(form.numeroDocumento, activeCodigoDoc, 'El número de documento'))}
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="primerNombre"
              label={form.tipoPersona === 'JURIDICA' ? 'Razón Social *' : 'Primer Nombre *'}
              placeholder={form.tipoPersona === 'JURIDICA' ? 'Ej. Inversiones SAED S.A.S.' : 'Ej. Carlos'}
              value={form.primerNombre}
              onChange={(e) => update('primerNombre', e.target.value)}
              onBlur={() => touch('primerNombre')}
              error={fieldError('primerNombre', valNombre(form.primerNombre, form.tipoPersona === 'JURIDICA' ? 'La razón social' : 'El primer nombre'))}
            />
            {form.tipoPersona === 'NATURAL' && (
              <Input
                id="segundoNombre"
                label="Segundo Nombre"
                placeholder="Ej. Alberto (opcional)"
                value={form.segundoNombre}
                onChange={(e) => update('segundoNombre', e.target.value)}
              />
            )}
          </div>
          {form.tipoPersona === 'NATURAL' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="primerApellido"
                label="Primer Apellido *"
                placeholder="Ej. Martínez"
                value={form.primerApellido}
                onChange={(e) => update('primerApellido', e.target.value)}
                onBlur={() => touch('primerApellido')}
                error={fieldError('primerApellido', valApellido(form.primerApellido, 'El primer apellido'))}
              />
              <Input
                id="segundoApellido"
                label="Segundo Apellido"
                placeholder="Ej. Gómez (opcional)"
                value={form.segundoApellido}
                onChange={(e) => update('segundoApellido', e.target.value)}
              />
            </div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="correoElectronico"
              label="Correo Electrónico"
              type="email"
              placeholder="ejemplo@correo.com"
              value={form.correoElectronico}
              onChange={(e) => update('correoElectronico', e.target.value)}
              onBlur={() => touch('correoElectronico')}
              error={fieldError('correoElectronico', valEmail(form.correoElectronico, { required: false }))}
            />
            <Input
              id="telefono"
              label="Teléfono / Celular"
              placeholder="Ej. 3001234567"
              value={form.telefono}
              onChange={(e) => update('telefono', e.target.value)}
              onBlur={() => touch('telefono')}
              error={fieldError('telefono', valTelefono(form.telefono, { required: false }))}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
