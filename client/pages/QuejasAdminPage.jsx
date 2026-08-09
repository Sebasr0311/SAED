import { useState, useEffect, useRef } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, todayStr, imageSrc } from '../lib/utils.js';

const ESTADOS = ['PENDIENTE', 'EN_REVISION', 'RESUELTA', 'CERRADA'];
const PRIORIDADES = ['ALTA', 'MEDIA', 'BAJA'];
const TIPOS = ['QUEJA', 'SUGERENCIA', 'APELACION'];
const PAGE_SIZE = 15;

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTA: 'badge-activo',
  CERRADA: 'badge-neutral',
};
const PRIORIDAD_BADGE = {
  ALTA: 'badge-danger',
  MEDIA: 'badge-warn',
  BAJA: 'badge-neutral',
};

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export default function QuejasAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [filtroPrioridad, setFiltroPrioridad] = useState('');
  const [search, setSearch] = useState('');
  const [toast, setToast] = useState(null);
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ estado: '', prioridad: '', respuesta: '' });
  const [saving, setSaving] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);
  const intervalRef = useRef(null);

  const { data, loading, error, refetch } = useFetch(() => api.get('/quejas/todas'), []);
  const all = data?.items || data || [];

  const stats = {
    total: all.length,
    pendientes: all.filter((i) => i.estado === 'PENDIENTE').length,
    revision: all.filter((i) => i.estado === 'EN_REVISION').length,
    resueltas: all.filter((i) => i.estado === 'RESUELTA' || i.estado === 'CERRADA').length,
  };

  const filtradas = (() => {
    const base = all.filter((r) => {
      if (!search) return true;
      const term = search.toLowerCase();
      return [r.titulo, r.descripcion, r.numeroApartamento, r.nombreResidente]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(term));
    });
    return base.filter((r) => {
      if (filtroEstado && r.estado !== filtroEstado) return false;
      if (filtroTipo && r.tipo !== filtroTipo) return false;
      if (filtroPrioridad && r.prioridad !== filtroPrioridad) return false;
      return true;
    });
  })();
  const totalPages = Math.max(1, Math.ceil(filtradas.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = filtradas.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // Auto-refresh cada 5s (reducido de 1.5s del vanilla para no saturar)
  useEffect(() => {
    intervalRef.current = setInterval(() => {
      if (document.visibilityState === 'visible') refetch();
    }, 5000);
    return () => clearInterval(intervalRef.current);
  }, [refetch]);

  function openDetalle(row) {
    setModal(row);
    setForm({ estado: row.estado, prioridad: row.prioridad, respuesta: row.respuestaAdmin || '' });
  }

  async function save() {
    if (!modal) return;
    setSaving(true);
    try {
      // El backend expone endpoints separados para cada accion:
      //   PUT /quejas/:id/estado     -> { estado }
      //   PUT /quejas/:id/prioridad  -> { prioridad }
      //   PUT /quejas/:id/responder  -> { respuesta } (tambien marca RESUELTA)
      // Disparamos solo los que cambiaron respecto al original, en serie cuando
      // hay que coordinar estado+respuesta (porque /responder fuerza RESUELTA y
      // si el admin quiere un estado distinto hay que sobrescribirlo despues).
      const estadoOriginal = modal.estado;
      const prioridadOriginal = modal.prioridad;
      const respuestaOriginal = modal.respuestaAdmin || '';

      const estadoCambio = form.estado && form.estado !== estadoOriginal;
      const prioridadCambio = form.prioridad && form.prioridad !== prioridadOriginal;
      const respuestaCambio = (form.respuesta || '') !== respuestaOriginal && form.respuesta.trim() !== '';

      const errors = [];
      async function call(p) {
        try { await p; } catch (e) { errors.push(e?.message || 'Error'); }
      }

      // 1) Si hay respuesta, llamar a /responder primero (siempre fuerza RESUELTA).
      if (respuestaCambio) {
        await call(api.put(`/quejas/${modal.idQueja}/responder`, { respuesta: form.respuesta }));
      }
      // 2) Despues aplicar el estado final deseado (si el admin queria otro estado
      //    distinto a RESUELTA, sobrescribimos aqui).
      if (estadoCambio) {
        await call(api.put(`/quejas/${modal.idQueja}/estado`, { estado: form.estado }));
      }
      // 3) La prioridad es independiente.
      if (prioridadCambio) {
        await call(api.put(`/quejas/${modal.idQueja}/prioridad`, { prioridad: form.prioridad }));
      }

      if (!estadoCambio && !prioridadCambio && !respuestaCambio) {
        setToast({ message: 'Sin cambios para guardar', type: 'info' });
        setModal(null);
        return;
      }

      if (errors.length === 0) {
        setToast({ message: 'Solicitud actualizada', type: 'success' });
      } else {
        setToast({ message: `Actualizacion parcial: ${errors.join('; ')}`, type: 'warning' });
      }
      setModal(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSaving(false);
    }
  }

  const columns = [
    { key: 'idQueja', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'titulo', label: 'Título' },
    { key: 'numeroApartamento', label: 'Apto' },
    { key: 'nombreResidente', label: 'Residente' },
    { key: 'categoria', label: 'Categoría' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'prioridad',
      label: 'Prioridad',
      render: (r) => (
        <span className={`badge ${PRIORIDAD_BADGE[r.prioridad] || 'badge-neutral'}`}>{r.prioridad}</span>
      ),
    },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.fechaCreacion) },
  ];

  return (
    <div>
      <PageHeader title="Solicitudes" subtitle="Quejas, sugerencias y apelaciones" />

      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <Stat icon="analytics" value={stats.total} label="Total" color="primary" />
        <Stat icon="pending" value={stats.pendientes} label="Pendientes" color="amber" />
        <Stat icon="visibility" value={stats.revision} label="En Revisión" color="blue" />
        <Stat icon="check_circle" value={stats.resueltas} label="Resueltas/Cerradas" color="green" />
      </div>

      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
          <Select
            id="f-estado"
            value={filtroEstado}
            onChange={(e) => {
              setFiltroEstado(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todos los estados</option>
            {ESTADOS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </Select>
          <Select
            id="f-tipo"
            value={filtroTipo}
            onChange={(e) => {
              setFiltroTipo(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todos los tipos</option>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <Select
            id="f-prioridad"
            value={filtroPrioridad}
            onChange={(e) => {
              setFiltroPrioridad(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todas las prioridades</option>
            {PRIORIDADES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </Select>
          <input
            id="search" aria-label="Buscar"
            type="text"
            placeholder="Buscar en titulo/descripcion/apto..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="form-control"
            style={{ flex: 1, minWidth: '200px' }}
          />
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty="No hay solicitudes"
            error={error?.message}
        keyField="idQueja"
        onRowClick={openDetalle}
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={filtradas.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title="Detalle de la Solicitud"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(null)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Tipo</div>
                <div style={{ fontSize: '13px' }}>{modal.tipo}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Categoría</div>
                <div style={{ fontSize: '13px' }}>{modal.categoria}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Apartamento</div>
                <div style={{ fontSize: '13px' }}>{modal.numeroApartamento}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Residente</div>
                <div style={{ fontSize: '13px' }}>{modal.nombreResidente}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Fecha</div>
                <div style={{ fontSize: '13px' }}>{formatDate(modal.fechaCreacion)}</div>
              </div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Título</div>
              <div style={{ fontSize: '13px' }}>{modal.titulo}</div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Descripción</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap' }}>{modal.descripcion}</div>
            </div>
            {modal.fotoEvidencia && (
              <div className="form-group">
                <img
                  src={imageSrc(modal.fotoEvidencia)}
                  alt="Evidencia"
                  style={{ maxWidth: '100%', borderRadius: '8px', cursor: 'zoom-in' }}
                  onClick={() => setFotoGrande(imageSrc(modal.fotoEvidencia))}
                />
              </div>
            )}
            <div className="form-row">
              <Select
                id="estado"
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </Select>
              <Select
                id="prioridad"
                label="Prioridad"
                value={form.prioridad}
                onChange={(e) => setForm((f) => ({ ...f, prioridad: e.target.value }))}
              >
                {PRIORIDADES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </Select>
            </div>
            <div className="form-group">
              <Textarea
                id="respuesta"
                label="Respuesta al residente (opcional)"
                rows={4}
                value={form.respuesta}
                onChange={(e) => setForm((f) => ({ ...f, respuesta: e.target.value }))}
                placeholder="Escribe tu respuesta aquí..."
              />
            </div>
          </>
        )}
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
