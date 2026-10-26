import { useRef, useState, useMemo } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { CheckCircle2, Download, Eye, Hourglass, Plus, ShieldCheck, Users, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api, { BASE_URL } from '../lib/api.js';
import { formatDate, formatCurrency, formatMiles, parseMiles } from '../lib/utils.js';
import { valNumero } from '../lib/validation.js';

const ESTADOS = ['', 'BORRADOR', 'PENDIENTE_FIRMA', 'ACTIVO', 'VENCIDO', 'TERMINADO_ANTICIPADO', 'CANCELADO'];
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
  idPlantilla: '',
  valorMensual: '',
  notas: '',
  enviarCorreo: true,
};

const ESTADO_BADGE = {
  BORRADOR: 'secondary',
  PENDIENTE_FIRMA: 'warning',
  ACTIVO: 'success',
  VENCIDO: 'destructive',
  TERMINADO_ANTICIPADO: 'warning',
  CANCELADO: 'secondary',
};
const ESTADO_LABEL = {
  BORRADOR: 'Borrador',
  PENDIENTE_FIRMA: 'Pendiente de firma',
  ACTIVO: 'Activo',
  VENCIDO: 'Vencido',
  TERMINADO_ANTICIPADO: 'Terminado anticipadamente',
  CANCELADO: 'Cancelado',
};
const TIPO_BADGE = {
  INICIAL: 'info',
  RENOVACION: 'success',
  PERMANENCIA: 'lavender',
};
const TIPO_LABEL = {
  INICIAL: 'Inicial',
  RENOVACION: 'Renovación',
  PERMANENCIA: 'Permanencia',
};
const ICON_BTN =
  'inline-flex h-9 w-9 items-center justify-center rounded-md text-text-secondary transition-colors duration-150 hover:bg-surface-dim hover:text-on-surface disabled:opacity-50';

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
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [confirmCancelar, setConfirmCancelar] = useState(null);
  const [saving, setSaving] = useState(false);
  const { touch, fieldError } = useLiveValidation();
  // Guard anti doble-submit: mismo patron que VisitasPage. disabled={state} NO bloquea clicks sincronicos.
  const savingRef = useRef(false);
  const [descargando, setDescargando] = useState(null);
  const [detalleModalOpen, setDetalleModalOpen] = useState(false);
  const [detalleContrato, setDetalleContrato] = useState(null);
  const [cargandoDetalle, setCargandoDetalle] = useState(false);

  const { data: contratosRaw, loading, error: errorContratos, refetch } = useFetch(() => api.get('/contratos'), []);
  const { data: apartamentos } = useFetch(() => api.get('/units'), []);
  const { data: residentes } = useFetch(() => api.get('/personas'), []);
  const { data: plantillasRaw } = useFetch(() => api.get('/contratos/plantillas/activas'), []);

  const plantillas = useMemo(() => plantillasRaw?.data || (Array.isArray(plantillasRaw) ? plantillasRaw : []), [plantillasRaw]);
  const contratos = useMemo(() => (contratosRaw?.items || (Array.isArray(contratosRaw) ? contratosRaw : [])).filter((c) => !filtroEstado || c.estado === filtroEstado), [contratosRaw, filtroEstado]);
  const totalPages = Math.max(1, Math.ceil(contratos.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = contratos.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  async function descargarPDF(idContrato) {
    setDescargando(idContrato);
    try {
      const token = sessionStorage.getItem('auth_token');
      const activeAssignment = sessionStorage.getItem('saed_active_assignment_id');
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;
      if (activeAssignment) headers['X-Assignment-Id'] = activeAssignment;

      const res = await fetch(`${BASE_URL}/contratos/${idContrato}/pdf`, { headers });
      if (!res.ok) {
        let errorMsg = `Error al descargar PDF (${res.status})`;
        try {
          const errData = await res.json();
          errorMsg = errData.message || errData.error || errorMsg;
        } catch (_) {}
        throw new Error(errorMsg);
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;

      let filename = `contrato_${idContrato}.pdf`;
      const disposition = res.headers.get('Content-Disposition');
      if (disposition && disposition.includes('filename=')) {
        const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        if (match && match[1]) {
          filename = match[1].replace(/['"]/g, '');
        }
      }

      a.download = filename;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }, 150);
      toast.success('Documento descargado exitosamente');
    } catch (err) {
      toast.error(err.message);
    } finally {
      setDescargando(null);
    }
  }

  async function verDetalle(idContrato) {
    setCargandoDetalle(true);
    setDetalleModalOpen(true);
    try {
      const res = await api.get(`/contratos/${idContrato}`);
      setDetalleContrato(res?.data || res);
    } catch (err) {
      toast.error('No se pudo cargar el detalle del contrato: ' + err.message);
      setDetalleModalOpen(false);
    } finally {
      setCargandoDetalle(false);
    }
  }

  async function activar(idContrato) {
    try {
      await api.post(`/contratos/${idContrato}/activar`);
      toast.success('Contrato activado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  async function cancelar() {
    if (!confirmCancelar) return;
    try {
      await api.post(`/contratos/${confirmCancelar.idContrato}/cancelar`);
      toast.success('Contrato cancelado');
      refetch();
    } catch (err) {
      toast.error(err.message);
    }
  }

  function handleEmailStatus(res) {
    if (res.emailStatus === 'enviado') {
      toast.success('Correo de notificación enviado al residente');
    } else if (res.emailStatus === 'sin_email') {
      toast.warning('El residente no tiene correo electrónico registrado');
    } else if (res.emailStatus === 'error') {
      toast.warning(`Contrato creado. No se pudo enviar el correo: ${res.emailMensaje || ''}`);
    }
  }

  function autoFillValor(idApartamento) {
    const apto = (apartamentos?.items || apartamentos || []).find((a) => String(a.id) === String(idApartamento));
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
        idPlantilla: form.idPlantilla ? Number(form.idPlantilla) : null,
        valorMensual: parseMiles(form.valorMensual),
        notas: form.notas,
        enviarCorreo: form.enviarCorreo,
      };
      const res = await api.post('/contratos', payload);
      toast.success('Contrato creado');
      handleEmailStatus(res);
      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  const columns = [
    { key: 'idContrato', label: 'ID', width: 60 },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Arrendatario' },
    { key: 'fechaInicio', label: 'Inicio', cellClassName: 'tabular-nums whitespace-nowrap', render: (r) => formatDate(r.fechaInicio) },
    { key: 'fechaFin', label: 'Fin', cellClassName: 'tabular-nums whitespace-nowrap', render: (r) => (r.fechaFin ? formatDate(r.fechaFin) : 'Indefinido') },
    {
      key: 'tipoContrato',
      label: 'Tipo',
      render: (r) => <Badge variant={TIPO_BADGE[r.tipoContrato] || 'secondary'}>{TIPO_LABEL[r.tipoContrato] || r.tipoContrato}</Badge>,
    },
    { key: 'valorMensual', label: 'Valor mensual', cellClassName: 'tabular-nums whitespace-nowrap text-right', render: (r) => formatCurrency(r.valorMensual) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <Badge variant={ESTADO_BADGE[r.estado] || 'secondary'}>{ESTADO_LABEL[r.estado] || r.estado}</Badge>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 220,
      render: (row) => (
        <div className="flex flex-wrap gap-1">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row.idContrato);
            }}
            className={ICON_BTN}
            title="Ver participantes y detalle"
            aria-label="Ver detalle del contrato"
          >
            <Eye className="h-4 w-4" aria-hidden="true" />
          </button>
          {(row.estado === 'ACTIVO' || row.estado === 'PENDIENTE_FIRMA') && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                descargarPDF(row.idContrato);
              }}
              type="button"
              className={ICON_BTN}
              title="Descargar PDF"
              aria-label="Descargar PDF"
              disabled={descargando === row.idContrato}
            >
              {descargando === row.idContrato ? (
                <Hourglass className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Download className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          )}
          {row.estado === 'PENDIENTE_FIRMA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                activar(row.idContrato);
              }}
              type="button"
              className={`${ICON_BTN} text-tint-green-fg hover:bg-tint-green-bg hover:text-tint-green-fg`}
              title="Activar"
              aria-label="Activar contrato"
            >
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
          {row.estado === 'ACTIVO' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setConfirmCancelar(row);
              }}
              type="button"
              className={`${ICON_BTN} text-destructive hover:bg-tint-red-bg hover:text-destructive`}
              title="Cancelar"
              aria-label="Cancelar contrato"
            >
              <XCircle className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="Contratos"
        subtitle="Contratos de arrendamiento de las unidades de la copropiedad."
        action={
          <Button
            onClick={() => {
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            <Plus className="h-4 w-4 mr-1.5" aria-hidden="true" />
            Nuevo Contrato
          </Button>
        }
      />
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="w-full sm:w-64">
          <Select id="filtroEstado" aria-label="Filtrar por estado" value={filtroEstado} onChange={(e) => { setFiltroEstado(e.target.value); setPage(0); }}>
            {ESTADOS.map((e) => (
              <option key={e || 'all'} value={e}>
                {e ? ESTADO_LABEL[e] || e : 'Todos los estados'}
              </option>
            ))}
          </Select>
        </div>
        {!loading && !errorContratos && (
          <p className="text-xs text-text-secondary">
            <span className="tabular-nums font-semibold text-on-surface">{contratos.length}</span> contrato(s)
          </p>
        )}
      </div>
      {errorContratos && !contratosRaw ? (
        <ErrorState
          title="No se pudieron cargar los contratos"
          message={errorContratos?.message || 'Verifique su conexión e intente nuevamente.'}
          onRetry={() => refetch().catch(() => {})}
        />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          loading={loading && !contratosRaw}
          empty={
            filtroEstado
              ? { icon: 'filter_alt_off', title: 'Sin contratos en este estado', subtitle: 'Cambie el filtro para ver otros contratos.' }
              : { icon: 'description', title: 'No hay contratos', subtitle: 'Cree el primer contrato desde el botón "Nuevo Contrato".' }
          }
          keyField="idContrato"
        />
      )}
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
        <div className="form-group mb-3">
          <Select
            id="idPlantilla"
            label="Plantilla de Contrato (Configurada por Organización)"
            value={form.idPlantilla}
            onChange={(e) => {
              const val = e.target.value;
              update('idPlantilla', val);
              if (val) {
                const found = plantillas.find((p) => String(p.idPlantilla) === String(val));
                if (found?.tipoContrato) {
                  onTipoChange(found.tipoContrato);
                }
              }
            }}
          >
            <option value="">— Plantilla Estándar del Sistema —</option>
            {plantillas.map((p) => (
              <option key={p.idPlantilla} value={p.idPlantilla}>
                {p.nombre} (v{p.version} · {p.tipoContrato})
              </option>
            ))}
          </Select>
        </div>
        <div className="form-row">
          <Select
            id="idApartamento"
            label="Apartamento"
            value={form.idApartamento}
            onChange={(e) => onApartamentoChange(e.target.value)}
            error={errors.idApartamento}
          >
            <option value="">— Seleccionar —</option>
            {(apartamentos?.items || apartamentos || []).map((a) => (
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
            {(residentes?.content || residentes?.items || residentes || []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.primerNombre || r.nombres} {r.primerApellido || r.apellidos}
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

      {/* Modal Detalle y Participantes */}
      <Modal
        open={detalleModalOpen}
        onClose={() => setDetalleModalOpen(false)}
        title={`Detalle Contrato ${detalleContrato?.numeroContrato || ''}`}
        size="lg"
        footer={
          <Button variant="outline" onClick={() => setDetalleModalOpen(false)}>
            Cerrar
          </Button>
        }
      >
        {cargandoDetalle ? (
          <LoadingState message="Cargando participantes..." />
        ) : detalleContrato ? (
          <div className="flex flex-col gap-4">
            {/* Contract summary */}
            <dl className="grid grid-cols-1 gap-3 rounded-lg border border-border bg-surface-dim p-3 sm:grid-cols-2">
              <div>
                <dt className="text-xs text-text-secondary">Apartamento</dt>
                <dd className="font-semibold text-on-surface">Apto {detalleContrato.numeroApartamento || detalleContrato.idUnidad}</dd>
              </div>
              <div>
                <dt className="text-xs text-text-secondary">Arrendatario principal</dt>
                <dd className="font-semibold text-on-surface">{detalleContrato.nombreCompletoResidente || detalleContrato.nombresResidente}</dd>
                <dd className="text-xs text-text-secondary tabular-nums">Doc: {detalleContrato.numeroDocumentoResidente || '-'}</dd>
              </div>
              <div>
                <dt className="text-xs text-text-secondary">Canon mensual</dt>
                <dd className="app-numeral text-lg text-primary-text">{formatCurrency(detalleContrato.valorCanon)}</dd>
              </div>
              <div>
                <dt className="text-xs text-text-secondary">Vigencia</dt>
                <dd className="font-medium text-on-surface tabular-nums">{formatDate(detalleContrato.fechaInicio)} - {detalleContrato.fechaFin ? formatDate(detalleContrato.fechaFin) : 'Indefinido'}</dd>
              </div>
            </dl>

            {/* Legal guardian */}
            {(detalleContrato.idTutor || detalleContrato.nombreTutor) && (
              <section className="rounded-lg border border-border p-3">
                <h3 className="mb-2 flex items-center gap-2 text-sm font-bold text-on-surface">
                  <ShieldCheck className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  Tutor legal / representante
                </h3>
                <div className="grid grid-cols-1 gap-2 text-[13px] sm:grid-cols-2">
                  <div><span className="text-text-secondary">Nombre:</span> {detalleContrato.nombreTutor || 'No registrado'}</div>
                  <div><span className="text-text-secondary">Cédula/Doc:</span> <span className="tabular-nums">{detalleContrato.cedulaTutor || '-'}</span></div>
                  <div><span className="text-text-secondary">Parentesco:</span> {detalleContrato.parentescoTutor || detalleContrato.relacionTutor || 'Tutor Legal'}</div>
                  <div><span className="text-text-secondary">Teléfono:</span> <span className="tabular-nums">{detalleContrato.telefonoTutor || '-'}</span></div>
                </div>
              </section>
            )}

            {/* Co-tenants */}
            <section className="rounded-lg border border-border p-3">
              <h3 className="mb-2 flex items-center gap-2 text-sm font-bold text-on-surface">
                <Users className="h-4 w-4 text-primary-text" aria-hidden="true" />
                Coarrendatarios y ocupantes autorizados (<span className="tabular-nums">{detalleContrato.coarrendatarios?.length || 0}</span>)
              </h3>

              {(!detalleContrato.coarrendatarios || detalleContrato.coarrendatarios.length === 0) ? (
                <p className="py-2 text-[13px] text-text-secondary">
                  No hay coarrendatarios vinculados a este contrato.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[520px] border-collapse text-[13px]">
                    <thead>
                      <tr className="border-b border-border text-left text-xs text-text-secondary">
                        <th className="p-1.5 font-semibold">Nombre</th>
                        <th className="p-1.5 font-semibold">Documento</th>
                        <th className="p-1.5 font-semibold">Vínculo</th>
                        <th className="p-1.5 font-semibold">Responsable de pago</th>
                        <th className="p-1.5 font-semibold">Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detalleContrato.coarrendatarios.map((c) => (
                        <tr key={c.idContratoResidente || c.idPersona} className="border-b border-border/60 last:border-0">
                          <td className="p-1.5">{c.nombrePersona || `Persona #${c.idPersona}`}</td>
                          <td className="p-1.5 tabular-nums">{c.numeroDocumento || '-'}</td>
                          <td className="p-1.5"><Badge variant="secondary">{c.tipoVinculo}</Badge></td>
                          <td className="p-1.5">{c.esResponsablePago === 'S' ? 'Sí' : 'No'}</td>
                          <td className="p-1.5"><Badge variant={c.estado === 'ACTIVO' ? 'success' : 'secondary'}>{c.estado}</Badge></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </div>
        ) : null}
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
    </PageContainer>
  );
}
