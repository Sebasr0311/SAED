import { useRef, useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api, { BASE_URL } from '../lib/api.js';
import { formatDate, formatCurrency, formatMiles, parseMiles } from '../lib/utils.js';
import { valNumero } from '../lib/validation.js';

const ESTADOS = ['', 'ACTIVO', 'SUSPENDIDO', 'VENCIDO', 'PENDIENTE_FIRMA', 'CANCELADO'];
const TIPOS = ['INICIAL', 'RENOVACION', 'PERMANENCIA'];
const VALOR_POR_TIPO = {
  ESTUDIO: 800000,
  '1HAB': 1200000,
  '2HAB': 1600000,
  '3HAB': 2200000,
  PENTHOUSE: 3000000,
  OTRO: 1000000,
};
const MESES_POR_TIPO = { INICIAL: 3, RENOVACION: 6, PERMANENCIA: null };
const PAGE_SIZE = 15;

const emptyForm = {
  idApartamento: '',
  idResidente: '',
  fechaInicio: '',
  fechaFin: '',
  tipoContrato: 'INICIAL',
  valorMensual: '',
  notas: '',
  enviarCorreo: true,
};

const ESTADO_BADGE = {
  ACTIVO: 'badge-activo',
  SUSPENDIDO: 'badge-warn',
  VENCIDO: 'badge-danger',
  PENDIENTE_FIRMA: 'badge-pendiente-firma',
  CANCELADO: 'badge-cancelado',
};
const TIPO_BADGE = {
  INICIAL: 'badge-info',
  RENOVACION: 'badge-success',
  PERMANENCIA: 'badge-navy',
};

function calcularFechaFin(fechaInicio, tipo) {
  const meses = MESES_POR_TIPO[tipo];
  if (!fechaInicio || meses == null) return '';
  const d = new Date(fechaInicio);
  d.setMonth(d.getMonth() + meses);
  return d.toISOString().slice(0, 10);
}

export default function ContratosPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [renovarModal, setRenovarModal] = useState(null);
  const [renovarForm, setRenovarForm] = useState({ fechaInicio: '', fechaFin: '', valorMensual: '', notas: '' });
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [confirmCancelar, setConfirmCancelar] = useState(null);
  const [saving, setSaving] = useState(false);
  const { touch, fieldError } = useLiveValidation();
  // Guard anti doble-submit (compartido por crear/confirmarRenovar): mismo patron que
  // VisitasPage (FASE 4.2-P2). disabled={state} NO bloquea clicks sincronicos — el ref es
  // la barrera real. Ambos flujos son mutuamente excluyentes desde la misma pantalla.
  const savingRef = useRef(false);
  const [descargando, setDescargando] = useState(null);

  const { data: contratosRaw, loading, refetch } = useFetch(() => api.get('/contratos'), []);
  const { data: apartamentos } = useFetch(() => api.get('/units'), []);
  const { data: residentes } = useFetch(() => api.get('/personas'), []);

  const contratos = (contratosRaw?.items || []).filter((c) => !filtroEstado || c.estado === filtroEstado);
  const totalPages = Math.max(1, Math.ceil(contratos.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = contratos.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  async function descargarPDF(idContrato) {
    setDescargando(idContrato);
    try {
      const token = sessionStorage.getItem('auth_token');
      const res = await fetch(`${BASE_URL}/contratos/${idContrato}/pdf`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: `Error ${res.status}` }));
        throw new Error(err.error || 'Error al descargar PDF');
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contrato_${idContrato}.pdf`;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }, 100);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setDescargando(null);
    }
  }

  async function reenviarCorreo(idContrato) {
    if (!window.confirm(`¿Desea reenviar el correo de notificación del contrato #${idContrato} al residente?`)) return;
    try {
      await api.post(`/contratos/${idContrato}/reenviar-correo`);
      setToast({ message: 'Correo reenviado exitosamente', type: 'success' });
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function activar(idContrato) {
    try {
      await api.post(`/contratos/${idContrato}/activar`);
      setToast({ message: 'Contrato activado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function cancelar() {
    if (!confirmCancelar) return;
    try {
      await api.post(`/contratos/${confirmCancelar.idContrato}/cancelar`);
      setToast({ message: 'Contrato cancelado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  function abrirRenovar(contrato) {
    const fechaInicio = new Date().toISOString().slice(0, 10);
    const fechaFin = calcularFechaFin(fechaInicio, 'RENOVACION');
    setRenovarModal(contrato);
    setRenovarForm({
      fechaInicio,
      fechaFin,
      valorMensual: formatMiles(contrato.valorMensual),
      notas: '',
    });
  }

  async function confirmarRenovar() {
    if (savingRef.current) return; // doble submit
    if (!renovarModal) return;
    if (!renovarForm.fechaInicio) {
      setToast({ message: 'La fecha de inicio es obligatoria', type: 'error' });
      return;
    }
    if (!parseMiles(renovarForm.valorMensual) || parseMiles(renovarForm.valorMensual) <= 0) {
      setToast({ message: 'El valor mensual debe ser mayor que 0', type: 'error' });
      return;
    }
    savingRef.current = true;
    setSaving(true);
    try {
      const res = await api.post(`/contratos/${renovarModal.idContrato}/renovar`, {
        fechaInicio: renovarForm.fechaInicio,
        fechaFin: renovarForm.fechaFin || null,
        valorMensual: parseMiles(renovarForm.valorMensual),
        notas: renovarForm.notas,
      });
      setToast({ message: 'Contrato renovado', type: 'success' });
      handleEmailStatus(res);
      setRenovarModal(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  function handleEmailStatus(res) {
    if (res.emailStatus === 'enviado') {
      setToast({ message: 'Correo de notificación enviado al residente', type: 'success' });
    } else if (res.emailStatus === 'sin_email') {
      setToast({ message: 'El residente no tiene correo electrónico registrado', type: 'warning' });
    } else if (res.emailStatus === 'error') {
      setToast({
        message: `Contrato creado. No se pudo enviar el correo: ${res.emailMensaje || ''}. Puede reenviarlo desde la tabla.`,
        type: 'warning',
      });
    }
  }

  async function sugerirTipo(idApartamento) {
    if (!idApartamento) return;
    try {
      const res = await api.get(`/contratos/sugerir-tipo/${idApartamento}`);
      if (res?.tipoSugerido) {
        update('tipoContrato', res.tipoSugerido);
        if (form.fechaInicio) {
          update('fechaFin', calcularFechaFin(form.fechaInicio, res.tipoSugerido));
        }
      }
    } catch (err) {
      setToast({ message: err.message || 'No se pudo sugerir el tipo de contrato', type: 'error' });
    }
  }

  function autoFillValor(idApartamento) {
    const apto = (apartamentos || []).find((a) => String(a.id) === String(idApartamento));
    if (!apto) return;
    const valorSugerido = apto.administracion != null ? Number(apto.administracion) : VALOR_POR_TIPO[apto.tipo] || null;
    if (valorSugerido) update('valorMensual', formatMiles(valorSugerido));
  }

  function update(k, v) {
    setErrors((prev) => (prev[k] ? { ...prev, [k]: undefined } : prev));
    setForm((f) => ({ ...f, [k]: v }));
  }
  function onApartamentoChange(idApartamento) {
    update('idApartamento', idApartamento);
    sugerirTipo(idApartamento);
    autoFillValor(idApartamento);
  }
  function onTipoChange(tipo) {
    update('tipoContrato', tipo);
    if (form.fechaInicio) update('fechaFin', calcularFechaFin(form.fechaInicio, tipo));
  }
  function onFechaInicioChange(fecha) {
    update('fechaInicio', fecha);
    update('fechaFin', calcularFechaFin(fecha, form.tipoContrato));
  }

  function validate() {
    const e = {};
    if (!form.idApartamento) e.idApartamento = 'Requerido';
    if (!form.idResidente) e.idResidente = 'Requerido';
    const rValor = valNumero(parseMiles(form.valorMensual), { positivo: true });
    if (!rValor.ok) e.valorMensual = rValor.mensaje;
    const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
    const hoyStr = hoy.toISOString().slice(0, 10);
    if (!form.fechaInicio) e.fechaInicio = 'Requerido';
    else if (form.fechaInicio < hoyStr) e.fechaInicio = 'La fecha de inicio no puede ser anterior a hoy';
    if (form.tipoContrato !== 'PERMANENCIA') {
      if (form.fechaInicio && form.fechaFin && form.fechaFin <= form.fechaInicio)
        e.fechaFin = 'La fecha de fin debe ser posterior a la de inicio';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function crear() {
    if (savingRef.current) return; // doble submit
    if (!validate()) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const payload = {
        idApartamento: Number(form.idApartamento),
        idResidente: Number(form.idResidente),
        fechaInicio: form.fechaInicio,
        fechaFin: form.fechaFin || null,
        tipoContrato: form.tipoContrato,
        valorMensual: parseMiles(form.valorMensual),
        notas: form.notas,
        enviarCorreo: form.enviarCorreo,
      };
      const res = await api.post('/contratos', payload);
      setToast({ message: 'Contrato creado', type: 'success' });
      handleEmailStatus(res);
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
    { key: 'idContrato', label: 'ID', width: 60 },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Arrendatario' },
    { key: 'fechaInicio', label: 'Inicio', render: (r) => formatDate(r.fechaInicio) },
    { key: 'fechaFin', label: 'Fin', render: (r) => (r.fechaFin ? formatDate(r.fechaFin) : 'Indefinido') },
    {
      key: 'tipoContrato',
      label: 'Tipo',
      render: (r) => <span className={`badge ${TIPO_BADGE[r.tipoContrato] || 'badge-neutral'}`}>{r.tipoContrato}</span>,
    },
    { key: 'valorMensual', label: 'Valor Mensual', render: (r) => formatCurrency(r.valorMensual) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 220,
      render: (row) => (
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {(row.estado === 'ACTIVO' || row.estado === 'PENDIENTE_FIRMA') && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                descargarPDF(row.idContrato);
              }}
              className="btn btn-ghost btn-xs"
              title="Descargar PDF"
              disabled={descargando === row.idContrato}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
                {descargando === row.idContrato ? 'hourglass_empty' : 'download'}
              </span>
            </button>
          )}
          {(row.estado === 'ACTIVO' || row.estado === 'PENDIENTE_FIRMA') && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                reenviarCorreo(row.idContrato);
              }}
              className="btn btn-ghost btn-xs"
              title="Reenviar correo"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>mail</span>
            </button>
          )}
          {row.estado === 'PENDIENTE_FIRMA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                activar(row.idContrato);
              }}
              className="btn btn-ghost btn-xs"
              title="Activar"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px', color: 'var(--success-strong)' }}>
                check_circle
              </span>
            </button>
          )}
          {row.estado === 'VENCIDO' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                abrirRenovar(row);
              }}
              className="btn btn-ghost btn-xs"
              title="Renovar"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px', color: 'var(--info)' }}>
                autorenew
              </span>
            </button>
          )}
          {row.estado === 'ACTIVO' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setConfirmCancelar(row);
              }}
              className="btn btn-ghost btn-xs"
              title="Cancelar"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px', color: 'var(--error)' }}>
                cancel
              </span>
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="Contratos" subtitle="Contratos de arrendamiento" />
      <div className="table-toolbar" style={{ marginBottom: '12px' }}>
        <div className="filters">
          <Select id="filtroEstado" aria-label="Filtrar por estado" value={filtroEstado} onChange={(e) => setFiltroEstado(e.target.value)} className="filter-select">
            {ESTADOS.map((e) => (
              <option key={e || 'all'} value={e}>
                {e || 'Todos los estados'}
              </option>
            ))}
          </Select>
        </div>
        <div className="actions">
          <Button
            onClick={() => {
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            + Nuevo Contrato
          </Button>
        </div>
      </div>
      <DataTable columns={columns} rows={rows} loading={loading} empty={{ icon: 'description', title: 'No hay contratos', subtitle: 'Crea el primer contrato desde el botón "Nuevo Contrato".' }} keyField="idContrato" />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={contratos.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Nuevo Contrato"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={crear} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        <div className="form-row">
          <Select
            id="idApartamento"
            label="Apartamento"
            value={form.idApartamento}
            onChange={(e) => onApartamentoChange(e.target.value)}
            error={errors.idApartamento}
          >
            <option value="">— Seleccionar —</option>
            {(apartamentos || []).map((a) => (
              <option key={a.id} value={a.id}>
                Apto {a.numero}
              </option>
            ))}
          </Select>
          <Select
            id="idResidente"
            label="Residente (arrendatario)"
            value={form.idResidente}
            onChange={(e) => update('idResidente', e.target.value)}
            error={errors.idResidente}
          >
            <option value="">— Seleccionar —</option>
            {(residentes?.content || []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombres} {r.apellidos}
              </option>
            ))}
          </Select>
        </div>
        <div className="form-row">
          <Input
            id="fechaInicio"
            label="Fecha Inicio"
            type="date"
            value={form.fechaInicio}
            onChange={(e) => onFechaInicioChange(e.target.value)}
            onBlur={() => touch('fechaInicio')}
            error={
              fieldError(
                'fechaInicio',
                !form.fechaInicio
                  ? { ok: false, mensaje: 'Requerido' }
                  : form.fechaInicio < new Date(new Date().setHours(0, 0, 0, 0)).toISOString().slice(0, 10)
                    ? { ok: false, mensaje: 'La fecha de inicio no puede ser anterior a hoy' }
                    : { ok: true }
              ) || errors.fechaInicio
            }
          />
          <Select id="tipoContrato" label="Tipo" value={form.tipoContrato} onChange={(e) => onTipoChange(e.target.value)}>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
        </div>
        <div className="form-row">
          <Input
            id="fechaFin"
            label="Fecha Fin (auto-calculada)"
            type="date"
            value={form.fechaFin}
            onChange={(e) => update('fechaFin', e.target.value)}
            onBlur={() => touch('fechaFin')}
            disabled={form.tipoContrato === 'PERMANENCIA'}
            error={
              fieldError(
                'fechaFin',
                form.tipoContrato === 'PERMANENCIA' || !form.fechaFin || !form.fechaInicio
                  ? { ok: true }
                  : form.fechaFin <= form.fechaInicio
                    ? { ok: false, mensaje: 'La fecha de fin debe ser posterior a la de inicio' }
                    : { ok: true }
              ) || errors.fechaFin
            }
          />
          <Input
            id="valorMensual"
            label="Valor Mensual"
            value={form.valorMensual}
            onChange={(e) => update('valorMensual', formatMiles(e.target.value))}
            onBlur={() => touch('valorMensual')}
            error={fieldError('valorMensual', valNumero(parseMiles(form.valorMensual), { positivo: true })) || errors.valorMensual}
          />
        </div>
        <div className="form-group">
          <Input id="notas" label="Notas (opcional)" value={form.notas} onChange={(e) => update('notas', e.target.value)} />
        </div>
        <div className="form-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.enviarCorreo}
              onChange={(e) => update('enviarCorreo', e.target.checked)}
            />
            <span>Enviar correo de notificación al residente</span>
          </label>
        </div>
      </Modal>

      <Modal
        open={!!renovarModal}
        onClose={() => setRenovarModal(null)}
        title={`Renovar Contrato #${renovarModal?.idContrato || ''}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRenovarModal(null)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={confirmarRenovar} disabled={saving}>
              {saving ? 'Renovando...' : 'Renovar'}
            </Button>
          </>
        }
      >
        <div className="form-row">
          <Input
            id="renFechaInicio"
            label="Nueva Fecha Inicio"
            type="date"
            value={renovarForm.fechaInicio}
            onChange={(e) => setRenovarForm((f) => ({ ...f, fechaInicio: e.target.value }))}
          />
          <Input
            id="renFechaFin"
            label="Nueva Fecha Fin"
            type="date"
            value={renovarForm.fechaFin}
            onChange={(e) => setRenovarForm((f) => ({ ...f, fechaFin: e.target.value }))}
          />
        </div>
        <div className="form-group">
          <Input
            id="renValor"
            label="Valor Mensual"
            value={renovarForm.valorMensual}
            onChange={(e) => setRenovarForm((f) => ({ ...f, valorMensual: formatMiles(e.target.value) }))}
          />
        </div>
        <div className="form-group">
          <Input
            id="renNotas"
            label="Notas (opcional)"
            value={renovarForm.notas}
            onChange={(e) => setRenovarForm((f) => ({ ...f, notas: e.target.value }))}
          />
        </div>
      </Modal>

      <ConfirmDialog
        open={!!confirmCancelar}
        onClose={() => setConfirmCancelar(null)}
        onConfirm={cancelar}
        title="Cancelar contrato"
        message={`¿Cancelar el contrato #${confirmCancelar?.idContrato}? El apartamento quedará disponible.`}
        confirmLabel="Cancelar contrato"
        danger
      />
      <Toast toast={toast} />
    </div>
  );
}
