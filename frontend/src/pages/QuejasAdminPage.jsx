import { useCallback, useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatDateTime } from '../lib/utils.js';

const ESTADOS = ['RADICADO', 'ASIGNADO', 'EN_GESTION', 'ESCALADO', 'RESUELTO', 'CERRADO', 'RECHAZADO'];
const TIPOS = ['PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA', 'APELACION', 'SOLICITUD'];
// CRITICA is stored by the backend as EMERGENCIA (DEC-05).
const PRIORIDADES = [
  { value: 'EMERGENCIA', label: 'Crítica' },
  { value: 'ALTA', label: 'Alta' },
  { value: 'MEDIA', label: 'Media' },
  { value: 'BAJA', label: 'Baja' },
];
const SLA_FILTROS = [
  { value: 'EN_TIEMPO', label: 'En tiempo' },
  { value: 'EN_RIESGO', label: 'En riesgo (75 % o más)' },
  { value: 'VENCIDO', label: 'Vencido' },
];
const PAGE_SIZE = 15;

// Mirrors TicketServiceImpl.ALLOWED_TRANSITIONS; the backend stays the authority and its errors are shown.
const TRANSICIONES = {
  RADICADO: ['ASIGNADO', 'EN_GESTION', 'RECHAZADO'],
  ASIGNADO: ['EN_GESTION', 'RECHAZADO'],
  EN_GESTION: ['ESCALADO', 'RESUELTO', 'RECHAZADO', 'ASIGNADO'],
  ESCALADO: ['EN_GESTION', 'RESUELTO', 'RECHAZADO'],
  RESUELTO: ['CERRADO', 'EN_GESTION'],
  CERRADO: [],
  RECHAZADO: [],
};

const ESTADO_BADGE = {
  RADICADO: 'badge-pendiente-firma',
  ASIGNADO: 'badge-info',
  EN_GESTION: 'badge-info',
  ESCALADO: 'badge-warning',
  RESUELTO: 'badge-activo',
  CERRADO: 'badge-neutral',
  RECHAZADO: 'badge-danger',
};

const SLA_BADGE = {
  EN_TIEMPO: { cls: 'badge-activo', label: 'En tiempo' },
  EN_RIESGO: { cls: 'badge-warning', label: 'En riesgo' },
  VENCIDO: { cls: 'badge-danger', label: 'Vencido' },
  CUMPLIDO: { cls: 'badge-activo', label: 'Cumplido' },
  INCUMPLIDO: { cls: 'badge-danger', label: 'Incumplido' },
  FINALIZADO: { cls: 'badge-neutral', label: 'Finalizado' },
};

const prioridadLabel = (p) => PRIORIDADES.find((x) => x.value === p)?.label || p || '—';

function SlaBadge({ ticket }) {
  const cfg = SLA_BADGE[ticket.slaEstado];
  if (!cfg) return <span>—</span>;
  const pct = ticket.slaPorcentajeConsumido;
  return (
    <span className={`badge ${cfg.cls}`}>
      {cfg.label}
      {pct != null ? ` · ${pct} %` : ''}
    </span>
  );
}

const labelStyle = { fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 };

export default function QuejasAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [filtroPrioridad, setFiltroPrioridad] = useState('');
  const [filtroSla, setFiltroSla] = useState('');
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null);
  const [traza, setTraza] = useState([]);
  const [trazaLoading, setTrazaLoading] = useState(false);
  const [trazaError, setTrazaError] = useState('');
  const [nuevoEstado, setNuevoEstado] = useState('');
  const [respuesta, setRespuesta] = useState('');
  const [responsable, setResponsable] = useState('');
  const [obsAsignacion, setObsAsignacion] = useState('');
  const [prioridad, setPrioridad] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  const { data, loading, error, refetch } = useFetch(() => api.get('/pqrs/todos'), []);
  const all = useMemo(() => (Array.isArray(data) ? data : data?.items || []), [data]);

  // Staff that can be assigned; the endpoint is granted to ADMIN_PROPIEDAD / ADMIN_ORGANIZACION.
  const { data: usuariosRaw } = useFetch(() => api.get('/usuarios'), []);
  const responsables = useMemo(() => {
    const list = Array.isArray(usuariosRaw) ? usuariosRaw : usuariosRaw?.items || [];
    const seen = new Set();
    return list
      .map((u) => ({
        id: u.ID_USUARIO ?? u.idUsuario,
        nombre: (u.NOMBRE_COMPLETO ?? u.nombreCompleto ?? u.NOMBRE_USUARIO ?? u.username ?? '').trim(),
        rol: (u.ROL_NOMBRE ?? u.rolNombre ?? u.ROL ?? u.rol ?? '').toString(),
        rolCodigo: (u.ROL ?? u.rol ?? '').toString().toUpperCase(),
        activo: ['ACTIVO', 'ACTIVA'].includes((u.ESTADO ?? u.estado ?? 'ACTIVO').toString().toUpperCase()),
      }))
      .filter((u) => u.id != null && u.activo && !u.rolCodigo.startsWith('RESIDENTE'))
      .filter((u) => (seen.has(u.id) ? false : seen.add(u.id)));
  }, [usuariosRaw]);

  const stats = {
    total: all.length,
    abiertos: all.filter((i) => !['RESUELTO', 'CERRADO', 'RECHAZADO'].includes(i.estado)).length,
    riesgo: all.filter((i) => i.slaEstado === 'EN_RIESGO').length,
    vencidos: all.filter((i) => i.slaEstado === 'VENCIDO').length,
  };

  const filtradas = all.filter((i) => {
    if (filtroEstado && i.estado !== filtroEstado) return false;
    if (filtroTipo && i.tipo !== filtroTipo) return false;
    if (filtroPrioridad && i.prioridad !== filtroPrioridad) return false;
    if (filtroSla && i.slaEstado !== filtroSla) return false;
    if (search) {
      const q = search.toLowerCase();
      if (!i.asunto?.toLowerCase().includes(q) && !i.numeroRadicado?.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });

  const totalPages = Math.ceil(filtradas.length / PAGE_SIZE) || 1;
  const safePage = Math.min(page, totalPages - 1);
  const rows = filtradas.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const idTicket = modal?.idTicket;
  const loadTraza = useCallback(async (id) => {
    setTrazaLoading(true);
    setTrazaError('');
    try {
      const res = await api.get(`/pqrs/${id}/trazabilidad`);
      setTraza(Array.isArray(res) ? res : res?.items || []);
    } catch (err) {
      setTraza([]);
      setTrazaError(err?.message || 'No se pudo cargar el historial');
    } finally {
      setTrazaLoading(false);
    }
  }, []);

  useEffect(() => {
    if (idTicket) loadTraza(idTicket);
  }, [idTicket, loadTraza]);

  const openDetalle = (row) => {
    setNuevoEstado('');
    setRespuesta('');
    setResponsable(row.responsableAsignado ? String(row.responsableAsignado) : '');
    setObsAsignacion('');
    setPrioridad(row.prioridad || '');
    setFormError('');
    setModal(row);
  };

  const closeModal = () => setModal(null);

  // Runs one action, surfaces backend validation errors inline and refreshes the ticket.
  const runAction = async (fn, okMessage) => {
    setSaving(true);
    setFormError('');
    try {
      await fn();
      toast.success(okMessage);
      const fresh = await api.get(`/pqrs/${modal.idTicket}`);
      setModal(fresh);
      setNuevoEstado('');
      setRespuesta('');
      setObsAsignacion('');
      setPrioridad(fresh.prioridad || '');
      loadTraza(modal.idTicket);
      refetch();
    } catch (err) {
      setFormError(err?.message || 'No se pudo completar la acción');
    } finally {
      setSaving(false);
    }
  };

  const cambiarEstado = (estado, observacion, okMessage) =>
    runAction(
      () => api.put(`/pqrs/${modal.idTicket}/estado`, { estado, observacion: observacion || undefined }),
      okMessage
    );

  const responder = () => {
    if (respuesta.trim().length < 3) {
      setFormError('La respuesta debe tener al menos 3 caracteres.');
      return;
    }
    return runAction(
      () =>
        api.post(`/pqrs/${modal.idTicket}/responder`, {
          respuesta: respuesta.trim(),
          nuevoEstado: nuevoEstado || undefined,
        }),
      'Respuesta registrada'
    );
  };

  const asignar = () => {
    if (!responsable) {
      setFormError('Seleccione un responsable.');
      return;
    }
    return runAction(
      () =>
        api.put(`/pqrs/${modal.idTicket}/asignar`, {
          idResponsable: Number(responsable),
          observacion: obsAsignacion.trim() || undefined,
        }),
      'Responsable asignado'
    );
  };

  const cambiarPrioridad = () =>
    runAction(() => api.put(`/pqrs/${modal.idTicket}/prioridad`, { prioridad }), 'Prioridad actualizada');

  const columns = [
    { key: 'numeroRadicado', label: 'Radicado' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'asunto', label: 'Asunto' },
    { key: 'prioridad', label: 'Prioridad', render: (r) => prioridadLabel(r.prioridad) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    { key: 'sla', label: 'SLA', render: (r) => <SlaBadge ticket={r} /> },
    { key: 'fechaLimiteSla', label: 'Vence (SLA)', render: (r) => formatDateTime(r.fechaLimiteSla) },
  ];

  const transiciones = modal ? TRANSICIONES[modal.estado] || [] : [];
  const terminal = modal && transiciones.length === 0;

  return (
    <div>
      <PageHeader title="PQRS (Tickets)" subtitle="Peticiones, Quejas, Reclamos y Sugerencias con SLA y Trazabilidad" />

      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <StatCard icon="confirmation_number" value={stats.total} label="Total PQRS" color="primary" />
        <StatCard icon="pending" value={stats.abiertos} label="Abiertos" color="blue" />
        <StatCard icon="warning" value={stats.riesgo} label="SLA en riesgo" color="amber" />
        <StatCard icon="alarm" value={stats.vencidos} label="SLA vencido" color="red" />
      </div>

      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
          <Select
            id="f-estado"
            aria-label="Filtrar por estado"
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
            id="f-prioridad"
            aria-label="Filtrar por prioridad"
            value={filtroPrioridad}
            onChange={(e) => {
              setFiltroPrioridad(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todas las prioridades</option>
            {PRIORIDADES.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </Select>
          <Select
            id="f-tipo"
            aria-label="Filtrar por tipo"
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
            id="f-sla"
            aria-label="Filtrar por SLA"
            value={filtroSla}
            onChange={(e) => {
              setFiltroSla(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todos los SLA</option>
            {SLA_FILTROS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
          <input
            id="search"
            aria-label="Buscar"
            type="text"
            placeholder="Buscar radicado o asunto..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="form-control"
            style={{ flex: 1, minWidth: '200px' }}
          />
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'support_agent', title: 'No hay PQRS', subtitle: 'Las PQRS de los residentes aparecerán aquí.' }}
        error={error?.message}
        keyField="idTicket"
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
        onClose={closeModal}
        title={`Detalle PQRS: ${modal?.numeroRadicado || ''}`}
        size="lg"
        footer={
          <Button variant="outline" onClick={closeModal} disabled={saving}>
            Cerrar
          </Button>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={labelStyle}>Estado</div>
                <span className={`badge ${ESTADO_BADGE[modal.estado] || 'badge-neutral'}`}>{modal.estado}</span>
              </div>
              <div>
                <div style={labelStyle}>SLA</div>
                <SlaBadge ticket={modal} />
              </div>
              <div>
                <div style={labelStyle}>Tipo / Categoría</div>
                <div style={{ fontSize: '13px' }}>
                  {modal.tipo} · {modal.categoria}
                </div>
              </div>
              <div>
                <div style={labelStyle}>Prioridad</div>
                <div style={{ fontSize: '13px' }}>{prioridadLabel(modal.prioridad)}</div>
              </div>
              <div>
                <div style={labelStyle}>Radicado por</div>
                <div style={{ fontSize: '13px' }}>
                  {modal.nombreRadicador || '—'}
                  {modal.identificadorUnidad ? ` · Unidad ${modal.identificadorUnidad}` : ''}
                </div>
              </div>
              <div>
                <div style={labelStyle}>Responsable</div>
                <div style={{ fontSize: '13px' }}>{modal.nombreResponsable || 'Sin asignar'}</div>
              </div>
              <div>
                <div style={labelStyle}>Fecha de radicación</div>
                <div style={{ fontSize: '13px' }}>{formatDateTime(modal.fechaRadicacion)}</div>
              </div>
              <div>
                <div style={labelStyle}>Vencimiento SLA</div>
                <div style={{ fontSize: '13px' }}>{formatDateTime(modal.fechaLimiteSla)}</div>
              </div>
            </div>
            <div className="form-group">
              <div style={labelStyle}>Asunto</div>
              <div style={{ fontSize: '14px', fontWeight: 500 }}>{modal.asunto}</div>
            </div>
            <div className="form-group">
              <div style={labelStyle}>Descripción de la solicitud</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                {modal.descripcion}
              </div>
            </div>
            {modal.observacionCierre && (
              <div className="form-group">
                <div style={labelStyle}>
                  Observación de cierre {modal.fechaCierre ? `(${formatDate(modal.fechaCierre)})` : ''}
                </div>
                <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap' }}>{modal.observacionCierre}</div>
              </div>
            )}

            <hr style={{ margin: '16px 0', borderColor: 'var(--border-color)' }} />
            <div style={{ ...labelStyle, marginBottom: '8px' }}>Historial y respuestas</div>
            {trazaLoading ? (
              <div style={{ fontSize: '13px' }}>Cargando historial...</div>
            ) : trazaError ? (
              <div role="alert" style={{ fontSize: '13px', color: 'var(--danger-color)' }}>{trazaError}</div>
            ) : traza.length === 0 ? (
              <div style={{ fontSize: '13px' }}>Sin movimientos registrados.</div>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: '8px' }}>
                {traza.map((t) => (
                  <li
                    key={t.idTrazabilidad}
                    style={{ fontSize: '13px', background: 'var(--bg-secondary)', padding: '10px', borderRadius: '6px' }}
                  >
                    <div style={{ fontWeight: 600 }}>
                      {t.tipoIntervencion}
                      {t.estadoAnterior !== t.estadoNuevo && t.estadoNuevo
                        ? ` · ${t.estadoAnterior || '—'} → ${t.estadoNuevo}`
                        : ''}
                    </div>
                    <div style={{ color: 'var(--text-secondary)', fontSize: '11px' }}>
                      {t.nombreUsuario || 'Sistema'} · {formatDateTime(t.fechaHora)}
                    </div>
                    {t.comentario && <div style={{ whiteSpace: 'pre-wrap', marginTop: '4px' }}>{t.comentario}</div>}
                  </li>
                ))}
              </ul>
            )}

            <hr style={{ margin: '16px 0', borderColor: 'var(--border-color)' }} />
            {formError && (
              <div role="alert" style={{ color: 'var(--danger-color)', fontSize: '13px', marginBottom: '12px' }}>
                {formError}
              </div>
            )}
            {terminal ? (
              <div style={{ fontSize: '13px' }}>
                Este ticket está en un estado final ({modal.estado}); no admite más acciones.
              </div>
            ) : (
              <>
                <div className="form-group">
                  <Textarea
                    id="respuesta"
                    label="Respuesta / observación"
                    placeholder="Detalle la gestión realizada o la respuesta formal al solicitante..."
                    rows={3}
                    value={respuesta}
                    onChange={(e) => setRespuesta(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <Select
                    id="nuevo-estado"
                    label="Cambiar estado (opcional)"
                    value={nuevoEstado}
                    onChange={(e) => setNuevoEstado(e.target.value)}
                  >
                    <option value="">Mantener {modal.estado}</option>
                    {transiciones.map((e) => (
                      <option key={e} value={e}>
                        {e}
                      </option>
                    ))}
                  </Select>
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '16px' }}>
                  <Button onClick={responder} disabled={saving}>
                    {nuevoEstado ? 'Responder y cambiar estado' : 'Responder'}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => cambiarEstado(nuevoEstado, respuesta.trim(), 'Estado actualizado')}
                    disabled={saving || !nuevoEstado}
                  >
                    Solo cambiar estado
                  </Button>
                  {modal.estado === 'RESUELTO' && (
                    <>
                      <Button
                        variant="outline"
                        onClick={() => cambiarEstado('CERRADO', respuesta.trim(), 'Ticket cerrado')}
                        disabled={saving}
                      >
                        Cerrar ticket
                      </Button>
                      <Button
                        variant="outline"
                        onClick={() => cambiarEstado('EN_GESTION', respuesta.trim(), 'Ticket reabierto')}
                        disabled={saving}
                      >
                        Reabrir
                      </Button>
                    </>
                  )}
                </div>

                <div className="card-grid-2">
                  <div>
                    <Select
                      id="responsable"
                      label="Asignar responsable"
                      value={responsable}
                      onChange={(e) => setResponsable(e.target.value)}
                    >
                      <option value="">Seleccione...</option>
                      {responsables.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.nombre} {u.rol ? `(${u.rol})` : ''}
                        </option>
                      ))}
                    </Select>
                    <input
                      aria-label="Observación de asignación"
                      type="text"
                      className="form-control"
                      placeholder="Observación (opcional)"
                      value={obsAsignacion}
                      onChange={(e) => setObsAsignacion(e.target.value)}
                      style={{ marginTop: '8px' }}
                    />
                    <Button variant="outline" onClick={asignar} disabled={saving} style={{ marginTop: '8px' }}>
                      Asignar
                    </Button>
                  </div>
                  <div>
                    <Select
                      id="prioridad"
                      label="Prioridad"
                      value={prioridad}
                      onChange={(e) => setPrioridad(e.target.value)}
                    >
                      {PRIORIDADES.map((p) => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </Select>
                    <Button
                      variant="outline"
                      onClick={cambiarPrioridad}
                      disabled={saving || prioridad === modal.prioridad}
                      style={{ marginTop: '8px' }}
                    >
                      Cambiar prioridad
                    </Button>
                  </div>
                </div>
              </>
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
