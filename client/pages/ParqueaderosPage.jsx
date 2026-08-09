import { useState, useEffect, useMemo, useRef } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';

const ESTADOS = ['', 'DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];
const TIPOS = ['', 'VEHICULO', 'MOTO', 'BICICLETA'];

const ESTADO_BADGE = {
  DISPONIBLE: 'badge-activo',
  OCUPADO: 'badge-ocupado',
  EN_MANTENIMIENTO: 'badge-en-mantenimiento',
};

function prefijoParq(tipo, esVisitante) {
  if (tipo === 'MOTO') return 'M';
  if (tipo === 'BICICLETA') return 'B';
  return esVisitante ? 'V' : 'P';
}

const emptyForm = { tipo: 'VEHICULO', esVisitante: true, idApartamento: '', numero: '', estado: 'DISPONIBLE' };
const PAGE_SIZE = 15;

export default function ParqueaderosPage() {
  const { isPortero } = useAuth();
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [toast, setToast] = useState(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);
  const { touch, fieldError } = useLiveValidation();

  const qs = new URLSearchParams({
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/parqueaderos?${qs}`), [filtroEstado, filtroTipo]);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos'), []);

  // Auto-refresh cada 10s si la pestaña está visible
  useEffect(() => {
    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') refetch();
    }, 10000);
    return () => clearInterval(interval);
  }, [refetch]);

  const items = data?.items || data || [];
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const codigoGenerado = useMemo(() => {
    const prefijo = prefijoParq(form.tipo, form.esVisitante);
    const existentes = items.filter((p) => p.codigo?.startsWith(prefijo));
    const siguiente = existentes.length + 1;
    return `${prefijo}${String(siguiente).padStart(3, '0')}`;
  }, [form.tipo, form.esVisitante, items]);

  const apartamentosDisponibles = useMemo(() => {
    const asignados = new Set(items.filter((p) => !p.esVisitante).map((p) => p.idApartamento));
    return (apartamentos?.items || []).filter((a) => !asignados.has(a.idApartamento) || a.idApartamento === editing?.idApartamento);
  }, [apartamentos, items, editing]);

  const columns = [
    { key: 'idParqueadero', label: 'ID', width: 60 },
    { key: 'codigo', label: 'Código' },
    { key: 'tipo', label: 'Tipo' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'esVisitante',
      label: 'Visitante',
      render: (r) => (r.esVisitante ? 'Visitante' : 'Residente'),
    },
    {
      key: 'numeroApartamento',
      label: 'Apartamento',
      render: (r) => r.numeroApartamento || '-',
    },
    {
      key: 'nombrePropietario',
      label: 'Propietario',
      render: (r) => r.nombrePropietario || '-',
    },
    ...(isPortero
      ? []
      : [
          {
            key: 'actions',
            label: 'Acciones',
            width: 100,
            render: (row) => (
              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setEditing(row);
                    setForm({
                      tipo: row.tipo,
                      esVisitante: row.esVisitante,
                      idApartamento: row.idApartamento || '',
                      numero: row.codigo,
                      estado: row.estado,
                    });
                    setErrors({});
                    setModalOpen(true);
                  }}
                  className="btn btn-ghost btn-sm"
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>edit</span>
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setConfirmDel(row);
                  }}
                  className="btn btn-ghost btn-sm"
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--error)' }}>delete</span>
                </button>
              </div>
            ),
          },
        ]),
  ];

  function update(k, v) {
    setErrors((prev) => (prev[k] ? { ...prev, [k]: undefined } : prev));
    setForm((f) => ({ ...f, [k]: v }));
  }
  async function save() {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    const errs = {};
    if (!form.esVisitante && !form.idApartamento) errs.idApartamento = 'Requerido para parqueadero de residente';
    if (!form.tipo) errs.tipo = 'Seleccione el tipo de parqueadero';
    if (!editing) {
      if (!form.numero) errs.numero = 'Requerido';
      else if (!/^\d{1,3}$/.test(String(form.numero).trim()))
        errs.numero = 'El número debe tener máximo 3 dígitos';
    }
    setErrors(errs);
    if (Object.keys(errs).length > 0) { savingRef.current = false; setSaving(false); return; }
    try {
      const payload = {
        tipo: form.tipo,
        esVisitante: form.esVisitante,
        idApartamento: form.esVisitante ? null : Number(form.idApartamento),
        codigo: editing ? form.numero : codigoGenerado,
        estado: form.estado,
      };
      if (editing) {
        await api.put(`/parqueaderos/${editing.idParqueadero}`, payload);
        setToast({ message: 'Parqueadero actualizado', type: 'success' });
      } else {
        await api.post('/parqueaderos', payload);
        setToast({ message: 'Parqueadero creado', type: 'success' });
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }
  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/parqueaderos/${confirmDel.idParqueadero}`);
      setToast({ message: 'Parqueadero eliminado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setConfirmDel(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Parqueaderos"
        subtitle="Gestión de parqueaderos de visitantes"
        action={
          <div className="filters">
            <Select
              id="f-estado"
              value={filtroEstado}
              onChange={(e) => {
                setFiltroEstado(e.target.value);
                setPage(0);
              }}
              className="filter-select"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e || 'Todos'}
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
              {TIPOS.map((t) => (
                <option key={t || 'all'} value={t}>
                  {t || 'Todos'}
                </option>
              ))}
            </Select>
            {!isPortero && (
              <Button
                onClick={() => {
                  setEditing(null);
                  setForm(emptyForm);
                  setErrors({});
                  setModalOpen(true);
                }}
              >
                + Nuevo
              </Button>
            )}
          </div>
        }
      />
      <DataTable columns={columns} rows={rows} loading={loading} empty={{ icon: 'local_parking', title: 'No hay parqueaderos', subtitle: 'Registra el primer parqueadero desde el botón "Nuevo Parqueadero".' }} keyField="idParqueadero" />
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
        title={editing ? 'Editar Parqueadero' : 'Nuevo Parqueadero'}
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>{saving ? 'Guardando...' : 'Guardar'}</Button>
          </>
        }
      >
        <div className="form-group">
          <Input id="codigo" label="Código (auto-generado)" value={editing ? form.numero : codigoGenerado} disabled />
        </div>
        <div className="form-row">
          <Select
            id="tipo"
            label="Tipo"
            value={form.tipo}
            onChange={(e) => update('tipo', e.target.value)}
            onBlur={() => touch('tipo')}
            error={fieldError('tipo', form.tipo ? { ok: true } : { ok: false, mensaje: 'Seleccione el tipo de parqueadero' }) || errors.tipo}
          >
            <option value="VEHICULO">Vehículo</option>
            <option value="MOTO">Moto</option>
            <option value="BICICLETA">Bicicleta</option>
          </Select>
          <Select
            id="estado"
            label="Estado"
            value={form.estado}
            onChange={(e) => update('estado', e.target.value)}
          >
            <option value="DISPONIBLE">Disponible</option>
            <option value="OCUPADO">Ocupado</option>
            <option value="EN_MANTENIMIENTO">Mantenimiento</option>
          </Select>
        </div>
        <div className="form-group">
          <label className="checkbox-label">
            <input
              type="radio"
              checked={form.esVisitante}
              onChange={() => update('esVisitante', true)}
            />
            <span>Visitante</span>
          </label>
          <label className="checkbox-label" style={{ marginLeft: '16px' }}>
            <input
              type="radio"
              checked={!form.esVisitante}
              onChange={() => update('esVisitante', false)}
            />
            <span>Residente</span>
          </label>
        </div>
        {!form.esVisitante && (
          <div className="form-group">
            <Select
              id="idApartamento"
              label="Apartamento"
              value={form.idApartamento}
              onChange={(e) => update('idApartamento', e.target.value)}
              onBlur={() => touch('idApartamento')}
              error={
                fieldError(
                  'idApartamento',
                  !form.esVisitante && !form.idApartamento
                    ? { ok: false, mensaje: 'Requerido para parqueadero de residente' }
                    : { ok: true }
                ) || errors.idApartamento
              }
            >
              <option value="">— Seleccionar —</option>
              {apartamentosDisponibles.map((a) => (
                <option key={a.idApartamento} value={a.idApartamento}>
                  Apto {a.numero}
                </option>
              ))}
            </Select>
          </div>
        )}
      </Modal>

      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar parqueadero"
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
        <p>¿Eliminar parqueadero {confirmDel?.codigo}?</p>
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar el parqueadero ${confirmDel?.codigo}`}
      />
      <Toast toast={toast} />
    </div>
  );
}
