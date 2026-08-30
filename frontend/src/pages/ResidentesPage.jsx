import { useState, useRef, useMemo } from 'react';
import { toast } from 'sonner';
import { valNombre, valApellido, valDocumento, valFechaNacimiento, valTelefono, valEmail, valSelect } from '../lib/validation.js';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { ActionButtons } from '../components/ui/ActionButtons.jsx';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';

const emptyForm = {
  idTipoDoc: 1,
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  fechaNacimiento: '',
  telefono: '',
  email: '',
  idApartamento: '',
};

const emptyTutorForm = {
  idTipoDoc: '',
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  email: '',
  parentesco: '',
  otroParentesco: '',
};

function calcularEdad(fechaNacimiento) {
  if (!fechaNacimiento) return null;
  const hoy = new Date();
  const nac = new Date(fechaNacimiento);
  let edad = hoy.getFullYear() - nac.getFullYear();
  const m = hoy.getMonth() - nac.getMonth();
  if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
  return edad;
}

const PAGE_SIZE = 15;

export default function ResidentesPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [tutorForm, setTutorForm] = useState(emptyTutorForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);

  const { data, loading, refetch } = useFetch(() => api.get('/residentes'), []);
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();
  const { data: apartamentos } = useFetch(() => api.get('/units'), []);

  const edad = calcularEdad(form.fechaNacimiento);
  const requiereTutor = edad !== null && edad >= 16 && edad < 18;

  const items = useMemo(() => (data?.items || []).filter((r) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [r.nombres, r.apellidos, r.numeroDocumento]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  }), [data, search]);
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'nombres', label: 'Nombres' },
    { key: 'apellidos', label: 'Apellidos' },
    { key: 'numeroDocumento', label: 'Documento' },
    { key: 'telefono', label: 'Teléfono' },
    { key: 'email', label: 'Email' },
    {
      key: 'actions',
      label: 'Acciones',
      width: 100,
      render: (row) => (
        <ActionButtons
          onEdit={(e) => {
            e.stopPropagation();
            openEdit(row);
          }}
          onDelete={(e) => {
            e.stopPropagation();
            setConfirmDel(row);
          }}
        />
      ),
    },
  ];

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setTutorForm(emptyTutorForm);
    setErrors({});
    setModalOpen(true);
  }
  function openEdit(row) {
    setEditing(row);
    setForm({
      idTipoDoc: row.idTipoDoc,
      numeroDocumento: row.numeroDocumento || '',
      nombres: row.nombres || '',
      apellidos: row.apellidos || '',
      fechaNacimiento: row.fechaNacimiento || '',
      telefono: row.telefono || '',
      email: row.email || '',
      idApartamento: row.idApartamento || '',
    });
    setTutorForm(emptyTutorForm);
    setErrors({});
    setModalOpen(true);
    if (row.esMenorEdad) {
      api
        .get(`/residentes/${row.id}`)
        .then((r) => {
          const t = r?.tutor;
          if (t) {
            setTutorForm({
              idTipoDoc: t.idTipoDoc || '',
              numeroDocumento: t.numeroDocumento || '',
              nombres: t.nombres || '',
              apellidos: t.apellidos || '',
              telefono: t.telefono || '',
              email: t.email || '',
              parentesco: t.parentesco || '',
              otroParentesco: t.otroParentesco || '',
            });
          }
        })
        .catch(() => { /* Tutor data not available — non-critical */ });
    }
  }
  function update(k, v) {
    setForm((f) => ({ ...f, [k]: v }));
  }
  function updateTutor(k, v) {
    setTutorForm((f) => ({ ...f, [k]: v }));
  }
  function validate() {
    const e = {};
    const codigoDoc = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || '';
    const rNombre = valNombre(form.nombres, 'El nombre');
    if (!rNombre.ok) e.nombres = rNombre.mensaje;
    const rApellido = valApellido(form.apellidos, 'El apellido');
    if (!rApellido.ok) e.apellidos = rApellido.mensaje;
    const rDoc = valDocumento(form.numeroDocumento, codigoDoc, 'El documento');
    if (!rDoc.ok) e.numeroDocumento = rDoc.mensaje;
    const rFecha = valFechaNacimiento(form.fechaNacimiento, { edadMin: 0, edadMax: 115 });
    if (!rFecha.ok) e.fechaNacimiento = rFecha.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(form.email);
    if (!rEmail.ok) e.email = rEmail.mensaje;
    if (requiereTutor) {
      const tCodigo = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(tutorForm.idTipoDoc))?.codigo || '';
      const rTN = valNombre(tutorForm.nombres, 'El nombre del tutor');
      if (!rTN.ok) e['tutor.nombres'] = rTN.mensaje;
      const rTA = valApellido(tutorForm.apellidos, 'El apellido del tutor');
      if (!rTA.ok) e['tutor.apellidos'] = rTA.mensaje;
      const rTDoc = valDocumento(tutorForm.numeroDocumento, tCodigo, 'El documento del tutor');
      if (!rTDoc.ok) e['tutor.numeroDocumento'] = rTDoc.mensaje;
      const rTTel = valTelefono(tutorForm.telefono);
      if (!rTTel.ok) e['tutor.telefono'] = rTTel.mensaje;
      const rTEmail = valEmail(tutorForm.email);
      if (!rTEmail.ok) e['tutor.email'] = rTEmail.mensaje;
      const rParent = valSelect(tutorForm.parentesco, 'Seleccione el parentesco');
      if (!rParent.ok) e['tutor.parentesco'] = rParent.mensaje;
      if (tutorForm.parentesco === 'OTRO' && !tutorForm.otroParentesco.trim()) {
        e['tutor.otroParentesco'] = 'Especifique el parentesco';
      }
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    const payload = {
      ...form,
      idApartamento: form.idApartamento === '' ? null : form.idApartamento,
      fechaNacimiento: form.fechaNacimiento === '' ? null : form.fechaNacimiento,
    };
    if (requiereTutor) {
      payload.tutor = {
        idTipoDoc: Number(tutorForm.idTipoDoc),
        numeroDocumento: tutorForm.numeroDocumento.trim(),
        nombres: tutorForm.nombres.trim(),
        apellidos: tutorForm.apellidos.trim(),
        telefono: tutorForm.telefono.trim(),
        email: tutorForm.email.trim(),
        parentesco: tutorForm.parentesco,
        otroParentesco:
          tutorForm.parentesco === 'OTRO' ? tutorForm.otroParentesco.trim() || null : null,
      };
    }
    try {
      let idResidente;
      if (editing) {
        await api.put(`/residentes/${editing.id}`, payload);
        idResidente = editing.id;
        toast.success('Residente actualizado');
      } else {
        const res = await api.post('/residentes', payload);
        idResidente = res.id;
        toast.success('Residente creado');
      }

      const aptSeleccionado = form.idApartamento !== '';
      const asignacionCambia =
        aptSeleccionado &&
        (!editing || Number(editing.idApartamento) !== Number(form.idApartamento));
      if (asignacionCambia) {
        try {
          await api.post(`/residentes/${idResidente}/asignar-apartamento`, {
            idApartamento: Number(form.idApartamento),
            rolEnContrato: 'OTRO',
          });
          const verif = await api.get(`/residentes/${idResidente}`);
          if (Number(verif?.idApartamento) !== Number(form.idApartamento)) {
            throw new Error('La asignación no se pudo confirmar en el servidor');
          }
        } catch (err) {
          toast.error(
            `Residente guardado, pero la asignación al apartamento falló: ${err.message}`,
          );
        }
      }

      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }
  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/residentes/${confirmDel.id}`);
      toast.success('Residente eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmDel(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Residentes"
        subtitle="Gestión de residentes del edificio"
        action={
          <>
            <Input
              id="search" aria-label="Buscar"
              placeholder="Buscar..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              style={{ width: '100%', maxWidth: '200px' }}
            />
            <Button onClick={openCreate}>+ Nuevo Residente</Button>
          </>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
                empty={{ icon: 'group', title: 'No hay residentes registrados', subtitle: 'Registra al primer residente con el botón "Nuevo Residente".' }}
        keyField="id"
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={items.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar Residente' : 'Nuevo Residente'}
        size="lg"
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
            id="idTipoDoc"
            label="Tipo Documento"
            value={form.idTipoDoc}
            onChange={(e) => update('idTipoDoc', Number(e.target.value))}
          >
            {tiposDoc.map((t) => (
              <option key={t.idTipoDoc ?? t.id ?? t.value} value={t.idTipoDoc ?? t.id ?? t.value}>
                {t.descripcion || t.nombre}
              </option>
            ))}
          </Select>
          {errorTiposDoc && !tiposDoc.length && (
            <p style={{ color: 'var(--error)', fontSize: '12px' }}>Error al cargar los tipos de documento</p>
          )}
          <Input
            id="numeroDocumento"
            label="Número Documento"
            value={form.numeroDocumento}
            onChange={(e) => update('numeroDocumento', e.target.value)}
            error={errors.numeroDocumento}
          />
        </div>
        <div className="form-row">
          <Input
            id="nombres"
            label="Nombres"
            value={form.nombres}
            onChange={(e) => update('nombres', e.target.value)}
            error={errors.nombres}
          />
          <Input
            id="apellidos"
            label="Apellidos"
            value={form.apellidos}
            onChange={(e) => update('apellidos', e.target.value)}
            error={errors.apellidos}
          />
        </div>
        <div className="form-row">
          <Input
            id="fechaNacimiento"
            label="Fecha de Nacimiento"
            type="date"
            value={form.fechaNacimiento}
            onChange={(e) => update('fechaNacimiento', e.target.value)}
            error={errors.fechaNacimiento}
          />
          <Input
            id="telefono"
            label="Teléfono"
            value={form.telefono}
            onChange={(e) => update('telefono', e.target.value)}
            onBlur={() => touch('telefono')}
            error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
          />
        </div>
        <div className="form-row">
          <Input
            id="email"
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => update('email', e.target.value)}
            onBlur={() => touch('email')}
            error={fieldError('email', valEmail(form.email, { required: false })) || errors.email}
          />
          <Select
            id="idApartamento"
            label="Apartamento"
            value={form.idApartamento}
            onChange={(e) => update('idApartamento', e.target.value)}
          >
            <option value="">— Sin asignar —</option>
            {(apartamentos?.items || apartamentos || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero} - Piso {a.piso}
              </option>
            ))}
          </Select>
        </div>
        {requiereTutor && (
          <div
            style={{
              marginTop: '16px',
              padding: '16px',
              background: 'var(--surface-container)',
              borderRadius: '12px',
              border: '1px solid var(--border)',
            }}
          >
                <h4 className="m-0 text-[15px] font-semibold">Datos del Tutor Legal</h4>
            <p style={{ fontSize: '12px', color: 'var(--text-muted)', margin: '4px 0 12px' }}>
              Menor de edad (16-17 años) — puede residir independientemente, pero debe tener un tutor
              legal registrado.
            </p>
            <div className="form-row">
              <Select
                id="tutor-idTipoDoc"
                label="Tipo Documento"
                value={tutorForm.idTipoDoc}
                onChange={(e) => updateTutor('idTipoDoc', Number(e.target.value))}
                error={errors['tutor.idTipoDoc']}
              >
                <option value="">Seleccione...</option>
                {tiposDoc.map((t) => (
                  <option key={t.idTipoDoc ?? t.id ?? t.value} value={t.idTipoDoc ?? t.id ?? t.value}>
                    {t.descripcion || t.nombre}
                  </option>
                ))}
              </Select>
              <Input
                id="tutor-numeroDocumento"
                label="Número Documento"
                value={tutorForm.numeroDocumento}
                onChange={(e) => updateTutor('numeroDocumento', e.target.value)}
                error={errors['tutor.numeroDocumento']}
              />
            </div>
            <div className="form-row">
              <Input
                id="tutor-nombres"
                label="Nombres"
                value={tutorForm.nombres}
                onChange={(e) => updateTutor('nombres', e.target.value)}
                error={errors['tutor.nombres']}
              />
              <Input
                id="tutor-apellidos"
                label="Apellidos"
                value={tutorForm.apellidos}
                onChange={(e) => updateTutor('apellidos', e.target.value)}
                error={errors['tutor.apellidos']}
              />
            </div>
            <div className="form-row">
              <Input
                id="tutor-telefono"
                label="Teléfono"
                value={tutorForm.telefono}
                onChange={(e) => updateTutor('telefono', e.target.value)}
                error={errors['tutor.telefono']}
              />
              <Input
                id="tutor-email"
                label="Email"
                type="email"
                value={tutorForm.email}
                onChange={(e) => updateTutor('email', e.target.value)}
                error={errors['tutor.email']}
              />
            </div>
            <div className="form-row">
              <Select
                id="tutor-parentesco"
                label="Parentesco"
                value={tutorForm.parentesco}
                onChange={(e) => {
                  updateTutor('parentesco', e.target.value);
                  if (e.target.value !== 'OTRO') updateTutor('otroParentesco', '');
                }}
                error={errors['tutor.parentesco']}
              >
                <option value="">Seleccione...</option>
                <option value="PADRE">Padre</option>
                <option value="MADRE">Madre</option>
                <option value="ABUELO">Abuelo</option>
                <option value="ABUELA">Abuela</option>
                <option value="TIO">Tío</option>
                <option value="TIA">Tía</option>
                <option value="HERMANO">Hermano</option>
                <option value="HERMANA">Hermana</option>
                <option value="TUTOR_LEGAL">Tutor Legal</option>
                <option value="OTRO">Otro</option>
              </Select>
              {tutorForm.parentesco === 'OTRO' && (
                <Input
                  id="tutor-parentesco-otro"
                  label="Especifique parentesco"
                  value={tutorForm.otroParentesco}
                  onChange={(e) => updateTutor('otroParentesco', e.target.value)}
                  error={errors['tutor.otroParentesco']}
                />
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar residente"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDel(null)}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={() => setPwdConfirmOpen(true)}>
              Eliminar
            </Button>
          </>
        }
      >
        <p>
          ¿Eliminar a {confirmDel?.nombres} {confirmDel?.apellidos}?
        </p>
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar a ${confirmDel?.nombres} ${confirmDel?.apellidos}`}
      />
    </div>
  );
}
