import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input } from '../components/ui/Form.jsx';
import Toast from '../components/ui/Toast.jsx';
import { formatDate } from '../lib/utils.js';

const TIPOS_DOC = [
  { value: 1, nombre: 'C.C.' },
  { value: 2, nombre: 'C.E.' },
  { value: 4, nombre: 'Pasaporte' },
];

export default function ResFrecuentesPage() {
  const { user } = useAuth();
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
  const [search, setSearch] = useState('');
  const [fotoGrande, setFotoGrande] = useState(null);

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
    return [f.nombre, f.documento, f.placa]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(term));
  });

  function validate() {
    const e = {};
    if (!form.nombres.trim()) e.nombres = 'Requerido';
    if (!form.apellidos.trim()) e.apellidos = 'Requerido';
    if (!form.numeroDocumento.trim()) e.numeroDocumento = 'Requerido';
    if (form.telefono && !/^\d{10}$/.test(form.telefono.replace(/\D/g, '')))
      e.telefono = 'Debe ser un teléfono válido de 10 dígitos';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function save() {
    if (!validate()) return;
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
      setSaving(false);
    }
  }

  const columns = [
    {
      key: 'foto',
      label: 'Foto',
      width: 60,
      render: (row) =>
        row.fotoCaptura ? (
          <img
            src={`data:image/jpeg;base64,${row.fotoCaptura}`}
            alt={row.nombre}
            style={{ width: '40px', height: '40px', borderRadius: '50%', objectFit: 'cover', cursor: 'zoom-in' }}
            onClick={() => setFotoGrande(`data:image/jpeg;base64,${row.fotoCaptura}`)}
          />
        ) : (
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              background: '#e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#94a3b8',
            }}
          >
            <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>person</span>
          </div>
        ),
    },
    { key: 'nombre', label: 'Nombre' },
    { key: 'documento', label: 'Documento' },
    { key: 'placa', label: 'Placa' },
    { key: 'ultimoIngreso', label: 'Último Ingreso', render: (r) => formatDate(r.ultimoIngreso) },
  ];

  return (
    <div>
      <PageHeader
        title="Visitantes Frecuentes"
        subtitle="Personas que te visitan regularmente"
        action={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Input
              id="search"
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
            key={f.idFrecuente || f.id || f.documento}
            className="frecuente-card"
            style={{ flexDirection: 'row', alignItems: 'center', gap: '12px' }}
          >
            {f.fotoCaptura ? (
              <img
                src={`data:image/jpeg;base64,${f.fotoCaptura}`}
                alt={f.nombre}
                style={{ width: '48px', height: '48px', borderRadius: '50%', objectFit: 'cover', cursor: 'zoom-in' }}
                onClick={() => setFotoGrande(`data:image/jpeg;base64,${f.fotoCaptura}`)}
              />
            ) : (
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
                }}
              >
                <span className="material-symbols-outlined">person</span>
              </div>
            )}
            <div style={{ flex: 1 }}>
              <div className="name">{f.nombre || '—'}</div>
              <div className="meta">Doc: {f.documento || '—'}</div>
              {f.placa && <div className="meta">Placa: {f.placa}</div>}
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
            {TIPOS_DOC.map((t) => (
              <option key={t.value} value={t.value}>
                {t.nombre}
              </option>
            ))}
          </Select>
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
            error={errors.telefono}
          />
          <Input
            id="email"
            label="Email (opcional)"
            type="email"
            value={form.email}
            onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
          />
        </div>
      </Modal>

      {fotoGrande && (
        <div
          onClick={() => setFotoGrande(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'zoom-out',
          }}
        >
          <img
            src={fotoGrande}
            alt="Foto"
            style={{ maxWidth: '90%', maxHeight: '90%', borderRadius: '8px' }}
          />
        </div>
      )}

      <Toast toast={toast} />
    </div>
  );
}
