import { useState, useRef } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { valSelect, valNumero, valEntero } from '../lib/validation.js';
import { toast } from 'sonner';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';

const ESTADOS = ['DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];
const PAGE_SIZE = 15;
const TIPOS = ['ESTUDIO', '1HAB', '2HAB', '3HAB', 'PENTHOUSE', 'OTRO'];
const AREAS_POR_TIPO = { ESTUDIO: 35, '1HAB': 50, '2HAB': 70, '3HAB': 90, PENTHOUSE: 120, OTRO: '' };
const CAPACIDADES_POR_TIPO = { ESTUDIO: 2, '1HAB': 3, '2HAB': 5, '3HAB': 7, PENTHOUSE: 8, OTRO: 2 };
const emptyForm = { numero: '', piso: 1, tipo: 'ESTUDIO', descripcionTipo: '', areaM2: 35, capacidadMaxima: 2, estado: 'DISPONIBLE' };

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
        <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--error)' }}>delete</span>
      </button>
    </div>
  );
}

export default function ApartamentosPage() {
  const [page, setPage] = useState(0);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);
  const [verResidentes, setVerResidentes] = useState(null);
  const [confirmQuitarResidente, setConfirmQuitarResidente] = useState(null);
  const { touch, fieldError } = useLiveValidation();

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
          <span style={{ color: alTope ? 'var(--error)' : 'var(--on-surface)', fontWeight: alTope ? 700 : 400 }}>
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
          mostrarVer={row.cantidadResidentes > 0 || row.estado === 'OCUPADO'}
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
              descripcionTipo: row.descripcionTipo || '',
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
    setErrors((prev) => (prev[k] ? { ...prev, [k]: undefined } : prev));
    setForm((f) => {
      const next = { ...f, [k]: v };
      if (k === 'tipo') {
        if (AREAS_POR_TIPO[v] !== '') next.areaM2 = AREAS_POR_TIPO[v];
        next.capacidadMaxima = CAPACIDADES_POR_TIPO[v];
        if (v !== 'OTRO') next.descripcionTipo = '';
      }
      if (k === 'piso' || k === 'aptoCascada') {
        // recalculado abajo si aplica
      }
      return next;
    });
  }
  function validate() {
    const e = {};
    const rNumero = valNumeroApartamento(form.numero);
    if (!rNumero.ok) e.numero = rNumero.mensaje;
    if (!form.piso) e.piso = 'Requerido';
    const rTipo = valSelect(form.tipo, 'Seleccione el tipo de apartamento');
    if (!rTipo.ok) e.tipo = rTipo.mensaje;
    const rArea = valNumero(form.areaM2, { positivo: true, max: 1000 });
    if (!rArea.ok) e.areaM2 = rArea.mensaje;
    if (form.tipo === 'OTRO' && !form.descripcionTipo?.trim()) {
      e.descripcionTipo = 'Describa el tipo de apartamento';
    }
    const capMax = CAPACIDADES_POR_TIPO[form.tipo] ?? 8;
    const rCap = valEntero(form.capacidadMaxima, { min: 1, max: Math.max(capMax, 8) });
    if (!rCap.ok) e.capacidadMaxima = rCap.mensaje;
    const duplicado = (data?.items || []).some(
      (a) => a.numero === form.numero && (!editing || a.idApartamento !== editing.idApartamento)
    );
    if (duplicado) e.numero = 'Ya existe un apartamento con ese número';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const payload = {
        ...form,
        piso: Number(form.piso),
        areaM2: form.areaM2 ? Number(form.areaM2) : null,
        capacidadMaxima: form.capacidadMaxima ? Number(form.capacidadMaxima) : null,
      };
      if (editing) {
        await api.put(`/apartamentos/${editing.idApartamento}`, payload);
        toast.success('Apartamento actualizado');
      } else {
        await api.post('/apartamentos', payload);
        toast.success('Apartamento creado');
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }
  async function handleDeleteConfirmed() {
    if (!confirmDel) return;
    try {
      await api.del(`/apartamentos/${confirmDel.idApartamento}`);
      toast.success('Apartamento eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmDel(null);
    }
  }
  async function quitarResidente(idResidente) {
    if (!confirmQuitarResidente) return;
    try {
      await api.del(`/apartamentos/${confirmQuitarResidente.idApartamento}/residentes/${idResidente}`);
      toast.success('Residente removido del apartamento');
      refetchResidentesApto();
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setConfirmQuitarResidente(null);
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
                empty={{ icon: 'apartment', title: 'No hay apartamentos', subtitle: 'Usa el botón "Nuevo Apartamento" para registrar el primero.' }}
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
            <Button onClick={save} disabled={saving}>{saving ? 'Guardando...' : 'Guardar'}</Button>
          </>
        }
      >
        <div className="form-row">
          <Input
            id="numero"
            label="Número"
            value={form.numero}
            onChange={(e) => update('numero', e.target.value)}
            onBlur={() => touch('numero')}
            error={fieldError('numero', form.numero ? { ok: true } : { ok: false, mensaje: 'Requerido' }) || errors.numero}
          />
          <Input
            id="piso"
            label="Piso"
            type="number"
            value={form.piso}
            onChange={(e) => update('piso', e.target.value)}
            onBlur={() => touch('piso')}
            error={fieldError('piso', form.piso ? { ok: true } : { ok: false, mensaje: 'Requerido' }) || errors.piso}
          />
        </div>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)} onBlur={() => touch('tipo')} error={fieldError('tipo', valSelect(form.tipo, 'Seleccione el tipo de apartamento')) || errors.tipo}>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          {form.tipo === 'OTRO' && (
            <Input
              id="descripcionTipo"
              label="Descripcion del tipo"
              placeholder="Ej: Local comercial, bodega, sotano..."
              value={form.descripcionTipo}
              onChange={(e) => update('descripcionTipo', e.target.value)}
              onBlur={() => touch('descripcionTipo')}
              error={errors.descripcionTipo}
            />
          )}
          <Input
            id="areaM2"
            label="Área (m²)"
            type="number"
            value={form.areaM2}
            onChange={(e) => update('areaM2', e.target.value)}
            onBlur={() => touch('areaM2')}
            error={fieldError('areaM2', valNumero(form.areaM2, { positivo: true, max: 1000 })) || errors.areaM2}
          />
        </div>
        <div className="form-row">
          <Input
            id="capacidadMaxima"
            label="Capacidad Máxima"
            type="number"
            value={form.capacidadMaxima}
            onChange={(e) => update('capacidadMaxima', e.target.value)}
            onBlur={() => touch('capacidadMaxima')}
            error={
              fieldError(
                'capacidadMaxima',
                valEntero(form.capacidadMaxima, { min: 1, max: Math.max(CAPACIDADES_POR_TIPO[form.tipo] ?? 8, 8) })
              ) || errors.capacidadMaxima
            }
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
          <p style={{ color: 'var(--text-muted)', textAlign: 'center' }}>Sin residentes registrados</p>
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
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                  Doc: {r.numeroDocumento} · Tel: {r.telefono || '—'} · {r.email || '—'}
                </div>
              </div>
              <Button variant="danger" onClick={() => setConfirmQuitarResidente({ idApartamento: verResidentes.idApartamento, idResidente: r.id })} style={{ padding: '4px 10px', fontSize: '11px' }}>
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
      <ConfirmDialog
        open={!!confirmQuitarResidente}
        onClose={() => setConfirmQuitarResidente(null)}
        onConfirm={() => quitarResidente(confirmQuitarResidente?.idResidente)}
        title="Quitar residente"
        message="¿Está seguro de eliminar este residente del apartamento?"
        confirmLabel="Quitar"
        danger
      />
    </div>
  );
}
