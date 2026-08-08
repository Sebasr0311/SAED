import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';

const ESTADOS = ['DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];
const PAGE_SIZE = 15;
const TIPOS = ['ESTUDIO', '1HAB', '2HAB', '3HAB', 'PENTHOUSE', 'OTRO'];
const AREAS_POR_TIPO = { ESTUDIO: 35, '1HAB': 50, '2HAB': 70, '3HAB': 90, PENTHOUSE: 120, OTRO: '' };
const CAPACIDADES_POR_TIPO = { ESTUDIO: 2, '1HAB': 3, '2HAB': 5, '3HAB': 7, PENTHOUSE: 8, OTRO: 2 };
const emptyForm = { numero: '', piso: 1, tipo: 'ESTUDIO', areaM2: 35, capacidadMaxima: 2, estado: 'DISPONIBLE' };

const ESTADO_BADGE = {
  DISPONIBLE: 'badge-activo',
  OCUPADO: 'badge-ocupado',
  EN_MANTENIMIENTO: 'badge-en-mantenimiento',
};

function ActionButtons({ onEdit, onDelete, onVer, mostrarVer }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      {mostrarVer && (
        <button onClick={onVer} className="btn btn-ghost btn-sm" aria-label="Ver residentes">
          <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>groups</span>
        </button>
      )}
      <button onClick={onEdit} className="btn btn-ghost btn-sm" aria-label="Editar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>edit</span>
      </button>
      <button onClick={onDelete} className="btn btn-ghost btn-sm" aria-label="Eliminar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px', color: '#e11d48' }}>delete</span>
      </button>
    </div>
  );
}

export default function ApartamentosPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);
  const [verResidentes, setVerResidentes] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get('/apartamentos'), []);
  const { data: residentesDelApto, refetch: refetchResidentesApto } = useFetch(
    () => (verResidentes ? api.get(`/residentes?idApartamento=${verResidentes.idApartamento}`) : Promise.resolve(null)),
    [verResidentes]
  );

  const items = data?.items || [];
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const columns = [
    { key: 'idApartamento', label: 'ID', width: 60 },
    { key: 'numero', label: 'Número' },
    { key: 'piso', label: 'Piso' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'areaM2', label: 'Área (m²)' },
    {
      key: 'residentes',
      label: 'Residentes',
      render: (row) => {
        const cant = row.cantidadResidentes ?? 0;
        const cap = row.capacidadMaxima ?? '?';
        const alTope = cant >= cap;
        return (
          <span style={{ color: alTope ? '#e11d48' : '#0f172a', fontWeight: alTope ? 700 : 400 }}>
            {cant} / {cap}
          </span>
        );
      },
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (row) => <span className={`badge ${ESTADO_BADGE[row.estado] || 'badge-neutral'}`}>{row.estado}</span>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 140,
      render: (row) => (
        <ActionButtons
          mostrarVer={row.estado === 'OCUPADO'}
          onVer={(e) => {
            e.stopPropagation();
            setVerResidentes(row);
          }}
          onEdit={(e) => {
            e.stopPropagation();
            setEditing(row);
            setForm({
              numero: row.numero,
              piso: row.piso,
              tipo: row.tipo,
              areaM2: row.areaM2,
              capacidadMaxima: row.capacidadMaxima,
              estado: row.estado,
            });
            setErrors({});
            setModalOpen(true);
          }}
          onDelete={(e) => {
            e.stopPropagation();
            setConfirmDel(row);
          }}
        />
      ),
    },
  ];

  function update(k, v) {
    setForm((f) => {
      const next = { ...f, [k]: v };
      if (k === 'tipo') {
        if (AREAS_POR_TIPO[v] !== '') next.areaM2 = AREAS_POR_TIPO[v];
        next.capacidadMaxima = CAPACIDADES_POR_TIPO[v];
      }
      if (k === 'piso' || k === 'aptoCascada') {
        // recalculado abajo si aplica
      }
      return next;
    });
  }
  function validate() {
    const e = {};
    if (!form.numero) e.numero = 'Requerido';
    if (!form.piso) e.piso = 'Requerido';
    const duplicado = (data?.items || []).some(
      (a) => a.numero === form.numero && (!editing || a.idApartamento !== editing.idApartamento)
    );
    if (duplicado) e.numero = 'Ya existe un apartamento con ese número';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    try {
      const payload = {
        ...form,
        piso: Number(form.piso),
        areaM2: form.areaM2 ? Number(form.areaM2) : null,
        capacidadMaxima: form.capacidadMaxima ? Number(form.capacidadMaxima) : null,
      };
      if (editing) {
        await api.put(`/apartamentos/${editing.idApartamento}`, payload);
        setToast({ message: 'Apartamento actualizado', type: 'success' });
      } else {
        await api.post('/apartamentos', payload);
        setToast({ message: 'Apartamento creado', type: 'success' });
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }
  async function handleDeleteConfirmed() {
    if (!confirmDel) return;
    try {
      await api.del(`/apartamentos/${confirmDel.idApartamento}`);
      setToast({ message: 'Apartamento eliminado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setConfirmDel(null);
    }
  }
  async function quitarResidente(idResidente) {
    if (!verResidentes) return;
    if (!window.confirm('¿Está seguro de eliminar este residente del apartamento?')) return;
    try {
      await api.del(`/apartamentos/${verResidentes.idApartamento}/residentes/${idResidente}`);
      setToast({ message: 'Residente removido del apartamento', type: 'success' });
      refetchResidentesApto();
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  return (
    <div>
      <PageHeader
        title="Apartamentos"
        subtitle="Inventario de apartamentos del edificio"
        action={
          <Button
            onClick={() => {
              setEditing(null);
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            + Nuevo Apartamento
          </Button>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty="No hay apartamentos"
        keyField="idApartamento"
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
        title={editing ? 'Editar Apartamento' : 'Nuevo Apartamento'}
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save}>Guardar</Button>
          </>
        }
      >
        <div className="form-row">
          <Input
            id="numero"
            label="Número"
            value={form.numero}
            onChange={(e) => update('numero', e.target.value)}
            error={errors.numero}
          />
          <Input
            id="piso"
            label="Piso"
            type="number"
            value={form.piso}
            onChange={(e) => update('piso', e.target.value)}
            error={errors.piso}
          />
        </div>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)}>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <Input
            id="areaM2"
            label="Área (m²)"
            type="number"
            value={form.areaM2}
            onChange={(e) => update('areaM2', e.target.value)}
          />
        </div>
        <div className="form-row">
          <Input
            id="capacidadMaxima"
            label="Capacidad Máxima"
            type="number"
            value={form.capacidadMaxima}
            onChange={(e) => update('capacidadMaxima', e.target.value)}
          />
          <Select id="estado" label="Estado" value={form.estado} onChange={(e) => update('estado', e.target.value)}>
            {ESTADOS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </Select>
        </div>
      </Modal>

      <Modal
        open={!!verResidentes}
        onClose={() => setVerResidentes(null)}
        title={`Residentes de Apto ${verResidentes?.numero || ''}`}
        size="md"
      >
        {(residentesDelApto?.items || residentesDelApto || []).length === 0 && (
          <p style={{ color: '#94a3b8', textAlign: 'center' }}>Sin residentes registrados</p>
        )}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {(residentesDelApto?.items || residentesDelApto || []).map((r) => (
            <div
              key={r.id}
              className="card"
              style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
            >
              <div>
                <div style={{ fontWeight: 700 }}>
                  {r.nombres} {r.apellidos}
                </div>
                <div style={{ fontSize: '12px', color: '#475569' }}>
                  Doc: {r.numeroDocumento} · Tel: {r.telefono || '—'} · {r.email || '—'}
                </div>
              </div>
              <Button variant="danger" onClick={() => quitarResidente(r.id)} style={{ padding: '4px 10px', fontSize: '11px' }}>
                Quitar
              </Button>
            </div>
          ))}
        </div>
      </Modal>

      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar apartamento"
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
        <p>¿Eliminar apartamento #{confirmDel?.numero}?</p>
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDeleteConfirmed();
        }}
        descripcion={`eliminar el apartamento #${confirmDel?.numero}`}
      />
      <Toast toast={toast} />
    </div>
  );
}
