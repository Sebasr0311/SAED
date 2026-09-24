import { useRef, useState, useMemo } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
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
  BORRADOR: 'badge-neutral',
  PENDIENTE_FIRMA: 'badge-pendiente-firma',
  ACTIVO: 'badge-activo',
  VENCIDO: 'badge-danger',
  TERMINADO_ANTICIPADO: 'badge-warn',
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

  const { data: contratosRaw, loading, refetch } = useFetch(() => api.get('/contratos'), []);
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
          <button
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row.idContrato);
            }}
            className="p-1 rounded-md text-muted-foreground hover:bg-muted transition-colors"
            title="Ver Participantes y Detalle"
            aria-label="Ver detalle del contrato"
          >
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
              visibility
            </span>
          </button>
          {(row.estado === 'ACTIVO' || row.estado === 'PENDIENTE_FIRMA') && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                descargarPDF(row.idContrato);
              }}
              className="p-1 rounded-md text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
              title="Descargar PDF"
              aria-label="Descargar PDF"
              disabled={descargando === row.idContrato}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>
                {descargando === row.idContrato ? 'hourglass_empty' : 'download'}
              </span>
            </button>
          )}
          {row.estado === 'PENDIENTE_FIRMA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                activar(row.idContrato);
              }}
              className="p-1 rounded-md text-muted-foreground hover:bg-muted transition-colors"
              title="Activar"
              aria-label="Activar contrato"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px', color: 'var(--success-strong)' }}>
                check_circle
              </span>
            </button>
          )}
          {row.estado === 'ACTIVO' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setConfirmCancelar(row);
              }}
              className="p-1 rounded-md text-muted-foreground hover:bg-muted transition-colors"
              title="Cancelar"
              aria-label="Cancelar contrato"
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
        <div className="form-group" style={{ marginBottom: '12px' }}>
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
          <div style={{ padding: '24px', textAlign: 'center' }}>Cargando participantes...</div>
        ) : detalleContrato ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Resumen Contrato */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '12px', background: 'var(--muted, #f8fafc)', padding: '12px', borderRadius: '8px' }}>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--muted-foreground)' }}>Apartamento</span>
                <div style={{ fontWeight: '600' }}>Apto {detalleContrato.numeroApartamento || detalleContrato.idUnidad}</div>
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--muted-foreground)' }}>Arrendatario Principal</span>
                <div style={{ fontWeight: '600' }}>{detalleContrato.nombreCompletoResidente || detalleContrato.nombresResidente}</div>
                <div style={{ fontSize: '12px', color: 'var(--muted-foreground)' }}>Doc: {detalleContrato.numeroDocumentoResidente || '-'}</div>
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--muted-foreground)' }}>Canon Mensual</span>
                <div style={{ fontWeight: '600', color: 'var(--primary)' }}>{formatCurrency(detalleContrato.valorCanon)}</div>
              </div>
              <div>
                <span style={{ fontSize: '11px', color: 'var(--muted-foreground)' }}>Vigencia</span>
                <div style={{ fontWeight: '500' }}>{formatDate(detalleContrato.fechaInicio)} - {detalleContrato.fechaFin ? formatDate(detalleContrato.fechaFin) : 'Indefinido'}</div>
              </div>
            </div>

            {/* Tutor Legal */}
            {(detalleContrato.idTutor || detalleContrato.nombreTutor) && (
              <div style={{ border: '1px solid var(--border)', borderRadius: '8px', padding: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--primary)' }}>shield_person</span>
                  <strong style={{ fontSize: '14px' }}>Tutor Legal / Representante</strong>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', fontSize: '13px' }}>
                  <div><span style={{ color: 'var(--muted-foreground)' }}>Nombre:</span> {detalleContrato.nombreTutor || 'No registrado'}</div>
                  <div><span style={{ color: 'var(--muted-foreground)' }}>Cédula/Doc:</span> {detalleContrato.cedulaTutor || '-'}</div>
                  <div><span style={{ color: 'var(--muted-foreground)' }}>Parentesco:</span> {detalleContrato.parentescoTutor || detalleContrato.relacionTutor || 'Tutor Legal'}</div>
                  <div><span style={{ color: 'var(--muted-foreground)' }}>Teléfono:</span> {detalleContrato.telefonoTutor || '-'}</div>
                </div>
              </div>
            )}

            {/* Coarrendatarios */}
            <div style={{ border: '1px solid var(--border)', borderRadius: '8px', padding: '12px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--primary)' }}>group</span>
                  <strong style={{ fontSize: '14px' }}>Coarrendatarios y Ocupantes Autorizados ({detalleContrato.coarrendatarios?.length || 0})</strong>
                </div>
              </div>

              {(!detalleContrato.coarrendatarios || detalleContrato.coarrendatarios.length === 0) ? (
                <div style={{ fontSize: '13px', color: 'var(--muted-foreground)', padding: '8px 0' }}>
                  No hay coarrendatarios vinculados a este contrato.
                </div>
              ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border)', textAlign: 'left', color: 'var(--muted-foreground)', fontSize: '11px' }}>
                      <th style={{ padding: '6px' }}>Nombre</th>
                      <th style={{ padding: '6px' }}>Documento</th>
                      <th style={{ padding: '6px' }}>Vínculo</th>
                      <th style={{ padding: '6px' }}>Responsable Pago</th>
                      <th style={{ padding: '6px' }}>Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detalleContrato.coarrendatarios.map((c) => (
                      <tr key={c.idContratoResidente || c.idPersona} style={{ borderBottom: '1px solid var(--border, #eee)' }}>
                        <td style={{ padding: '6px' }}>{c.nombrePersona || `Persona #${c.idPersona}`}</td>
                        <td style={{ padding: '6px' }}>{c.numeroDocumento || '-'}</td>
                        <td style={{ padding: '6px' }}><span className="badge badge-neutral">{c.tipoVinculo}</span></td>
                        <td style={{ padding: '6px' }}>{c.esResponsablePago === 'S' ? 'Sí' : 'No'}</td>
                        <td style={{ padding: '6px' }}><span className={`badge ${c.estado === 'ACTIVO' ? 'badge-activo' : 'badge-neutral'}`}>{c.estado}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
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
    </div>
  );
}
