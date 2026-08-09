import { useRef, useState } from 'react';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { valNombre, valApellido, valDocumento, valTelefono, valEmail } from '../lib/validation.js';
import Toast from '../components/ui/Toast.jsx';
import { formatDate } from '../lib/utils.js';

export default function ResFrecuentesPage() {
  const { user } = useAuth();
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState({
    idTipoDoc: 1,
    numeroDocumento: '',
    nombres: '',
    apellidos: '',
    telefono: '',
    email: '',
    placa: '',
  });
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  // Guard anti doble-submit: mismo patron que VisitasPage (FASE 4.2-P2).
  const savingRef = useRef(false);
  const [search, setSearch] = useState('');

  const { data, loading, refetch } = useFetch(
    () => api.get(`/residentes/${user?.idResidente}/frecuentes`),
    [user]
  );

  // Carga paralela de las ultimas QR del residente para mostrar placas
  const { data: qrs } = useFetch(
    () => api.get(`/residentes/${user?.idResidente}/qr-activos`).catch(() => []),
    [user]
  );

  const filtrados = (data?.items || data || []).filter((f) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [f.nombreVisitante, f.documento, f.ultimaPlaca]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  });

  function validate() {
    const e = {};
    const rN = valNombre(form.nombres, 'El nombre');
    if (!rN.ok) e.nombres = rN.mensaje;
    const rA = valApellido(form.apellidos, 'El apellido');
    if (!rA.ok) e.apellidos = rA.mensaje;
    const codigoDoc = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || '';
    const rD = valDocumento(form.numeroDocumento, codigoDoc, 'El documento');
    if (!rD.ok) e.numeroDocumento = rD.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function save() {
    if (savingRef.current) return; // doble submit
    if (!validate()) return;
    savingRef.current = true;
    setSaving(true);
    try {
      // POST /api/visitantes crea el visitante; la vinculacion como frecuente
      // se hace via flujo de QR/libera visita
      await api.post('/visitantes', {
        idTipoDoc: Number(form.idTipoDoc),
        numeroDocumento: form.numeroDocumento.trim(),
        nombres: form.nombres.trim(),
        apellidos: form.apellidos.trim(),
        telefono: form.telefono.replace(/\D/g, '') || null,
        email: form.email.trim() || null,
        activo: true,
      });
      setToast({ message: 'Visitante creado. Para marcarlo como frecuente, genere un QR de "Visita Rápida" en /res-visita', type: 'success' });
      setForm({
        idTipoDoc: 1,
        numeroDocumento: '',
        nombres: '',
        apellidos: '',
        telefono: '',
        email: '',
        placa: '',
      });
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  const columns = [
    { key: 'nombreVisitante', label: 'Nombre' },
    { key: 'documento', label: 'Documento' },
    { key: 'ultimaPlaca', label: 'Placa' },
    { key: 'ultimaVisita', label: 'Ãšltimo Ingreso', render: (r) => formatDate(r.ultimaVisita) },
  ];

  return (
    <div>
      <PageHeader
        title="Visitantes Frecuentes"
        subtitle="Personas que te visitan regularmente"
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Input
              id="search" aria-label="Buscar"
              placeholder="Buscar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <Button onClick={() => setModalOpen(true)}>+ Nuevo Visitante</Button>
          </div>
        }
      />
      <div className="frecuentes-grid">
        {loading && <div className="card empty-state">Cargando...</div>}
        {!loading && filtrados.length === 0 && (
          <div className="card empty-state">No tienes visitantes frecuentes</div>
        )}
        {filtrados.map((f) => (
          <div
            key={f.idFrecuente}
            className="frecuente-card"
            style={{ flexDirection: 'row', alignItems: 'center', gap: '12px' }}
          >
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '50%',
                background: '#e2e8f0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#94a3b8',
                flexShrink: 0,
              }}
            >
              <span className="material-symbols-outlined">person</span>
            </div>
            <div style={{ flex: 1 }}>
              <div className="name">{f.nombreVisitante || 'â€”'}</div>
              <div className="meta">Doc: {f.documento || 'â€”'}</div>
              {f.ultimaPlaca && <div className="meta">Placa: {f.ultimaPlaca}</div>}
              {f.ultimaVisita && <div className="meta">Ãšltima visita: {formatDate(f.ultimaVisita)}</div>}
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Nuevo Visitante"
        size="md"
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
        <div className="form-row">
          <Select
            id="idTipoDoc"
            label="Tipo Documento"
            value={form.idTipoDoc}
            onChange={(e) => setForm((f) => ({ ...f, idTipoDoc: Number(e.target.value) }))}
          >
            {tiposDoc.map((t) => (
              <option key={t.idTipoDoc ?? t.value} value={t.idTipoDoc ?? t.value}>
                {t.descripcion || t.nombre}
              </option>
            ))}
          </Select>
          {errorTiposDoc && !tiposDoc.length && (
            <p style={{ color: '#e11d48', fontSize: '12px' }}>Error al cargar los tipos de documento</p>
          )}
          <Input
            id="numeroDocumento"
            label="Número Documento"
            value={form.numeroDocumento}
            onChange={(e) => setForm((f) => ({ ...f, numeroDocumento: e.target.value }))}
            error={errors.numeroDocumento}
          />
        </div>
        <div className="form-row">
          <Input
            id="nombres"
            label="Nombres"
            value={form.nombres}
            onChange={(e) => setForm((f) => ({ ...f, nombres: e.target.value }))}
            error={errors.nombres}
          />
          <Input
            id="apellidos"
            label="Apellidos"
            value={form.apellidos}
            onChange={(e) => setForm((f) => ({ ...f, apellidos: e.target.value }))}
            error={errors.apellidos}
          />
        </div>
        <div className="form-row">
          <Input
            id="telefono"
            label="Teléfono (opcional)"
            value={form.telefono}
            onChange={(e) => setForm((f) => ({ ...f, telefono: e.target.value }))}
            onBlur={() => touch('telefono')}
            error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
          />
          <Input
            id="email"
            label="Email (opcional)"
            type="email"
            value={form.email}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
            onBlur={() => touch('email')}
            error={fieldError('email', valEmail(form.email, { required: false })) || errors.email}
          />
        </div>
      </Modal>

      <Toast toast={toast} />
    </div>
  );
}
