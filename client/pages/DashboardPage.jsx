import { useMemo, useRef, useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, formatDateTime, imageSrc, periodoLabel } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import Toast from '../components/ui/Toast.jsx';

const MULTAS_PAGE_SIZE = 10;

function StatCard({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
      <div className="stat-badge">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [toast, setToast] = useState(null);
  const [multasPage, setMultasPage] = useState(1);
  const [notificando, setNotificando] = useState(false);
  const notificandoRef = useRef(false);
  const [confirmarNotificarTodas, setConfirmarNotificarTodas] = useState(false);
  const [detalleContrato, setDetalleContrato] = useState(null);
  const [loadingDetalleContrato, setLoadingDetalleContrato] = useState(false);
  const [detalleMulta, setDetalleMulta] = useState(null);
  const [loadingDetalleMulta, setLoadingDetalleMulta] = useState(false);
  const [estadoSistema, setEstadoSistema] = useState(null); // null = cerrado; {} = abierto cargando
  const [loadingEstadoSistema, setLoadingEstadoSistema] = useState(false);

  const { data: residentes } = useFetch(() => api.get('/residentes'), []);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos'), []);
  const {
    data: contratos,
    loading: loadingContratos,
    error: contratosError,
    refetch: refetchContratos,
  } = useFetch(() => api.get('/contratos'), []);
  const {
    data: multasRaw,
    loading: loadingMultas,
    error: multasError,
    refetch: refetchMultas,
  } = useFetch(() => api.get('/multas/todas'), []);

  const contratosActivosList = (contratos?.items || []).filter((c) => c.estado === 'ACTIVO');
  const contratosActivos = contratosActivosList.length; // conteo para la Stat Card

  // Filtro en cliente (idéntico al legacy app.js:274-275; /multas/todas devuelve array plano sin query de estado)
  const pendientes = (multasRaw?.items || multasRaw || []).filter((m) => m.estado === 'PENDIENTE');

  // Agrupación por apartamento (fiel al legacy renderMultas)
  const grupos = useMemo(() => {
    const map = {};
    pendientes.forEach((m) => {
      const key = m.numeroApartamento || m.idApartamento;
      if (!map[key]) map[key] = { apto: key, residente: m.nombreResidente || '', multas: [] };
      map[key].multas.push(m);
    });
    return Object.values(map);
  }, [pendientes]);

  const totalPaginas = Math.max(1, Math.ceil(grupos.length / MULTAS_PAGE_SIZE));
  const paginaSegura = Math.min(multasPage, totalPaginas);
  const gruposDePagina = grupos.slice((paginaSegura - 1) * MULTAS_PAGE_SIZE, paginaSegura * MULTAS_PAGE_SIZE);

  async function notificarUna(idMulta) {
    try {
      const res = await api.post(`/multas/${idMulta}/notificar`);
      setToast({ message: res.mensaje || 'Notificación enviada', type: 'success' });
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function notificarTodas() {
    if (notificandoRef.current) return; // doble submit
    setConfirmarNotificarTodas(false);
    notificandoRef.current = true;
    setNotificando(true);
    try {
      const res = await api.post('/multas/notificar-todas');
      setToast({ message: res.mensaje || 'Notificaciones enviadas', type: 'success' });
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      notificandoRef.current = false;
      setNotificando(false);
    }
  }

  // Detalle de contrato (Bloque 3): abre el modal de inmediato y carga en paralelo.
  // Asimetria deliberada de errores:
  //  - /contratos/{id} y /residentes/{id} son CRITICOS: sin ellos el modal seria
  //    enganoso (contrato sin titular ni fechas) -> toast + cierre del modal.
  //  - /cuotas?contrato= es OPCIONAL: si falla, la seccion secundaria degrada a
  //    "Sin cuotas registradas" y el modal principal sigue siendo util.
  async function verDetalleContrato(contrato) {
    setLoadingDetalleContrato(true);
    setDetalleContrato(contrato); // feedback inmediato con el header del contrato clickeado
    try {
      const [detalle, residente, cuotas] = await Promise.all([
        api.get(`/contratos/${contrato.idContrato}`),
        contrato.idResidente ? api.get(`/residentes/${contrato.idResidente}`) : Promise.resolve(null),
        api.get(`/cuotas?contrato=${contrato.idContrato}`).catch(() => []),
      ]);
      setDetalleContrato({ ...contrato, ...detalle, residente, cuotas: cuotas?.items || cuotas || [] });
    } catch (err) {
      setToast({ message: 'Error al cargar detalles: ' + err.message, type: 'error' });
      setDetalleContrato(null);
    } finally {
      setLoadingDetalleContrato(false);
    }
  }

  // Detalle de multa (Bloque 4): carga bajo demanda al clickear una fila del panel.
  async function verDetalleMulta(idMulta) {
    setLoadingDetalleMulta(true);
    try {
      const m = await api.get(`/multas/${idMulta}`);
      setDetalleMulta(m);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setLoadingDetalleMulta(false);
    }
  }

  // Hora corta HH:mm para listas del estado del sistema (fiel al legacy, que mostraba
  // solo hora con substring(11,19)). Local: unico consumidor — subir a utils si se duplica.
  function formatTime(iso) {
    if (!iso) return '-';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '-';
    return d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  }

  // Estado del sistema (Bloque 5): ambos endpoints son CRITICOS para este modal — si uno
  // falla, la imagen del estado estaria incompleta y seria enganosa -> Promise.all con
  // toast + cierre (igual al legacy, que mostraba error en ambas secciones).
  async function abrirEstadoSistema() {
    setLoadingEstadoSistema(true);
    setEstadoSistema({});
    try {
      const [activos, visitas] = await Promise.all([
        api.get('/registros-acceso/activos'),
        api.get('/visitas'),
      ]);
      setEstadoSistema({
        activos: activos?.items || activos || [],
        qrPendientes: (visitas?.items || visitas || []).filter((v) => v.estado === 'PENDIENTE'),
      });
    } catch (err) {
      setToast({ message: 'Error al cargar estado del sistema: ' + err.message, type: 'error' });
      setEstadoSistema(null);
    } finally {
      setLoadingEstadoSistema(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Resumen general"
        subtitle="Vista rápida de los indicadores clave del edificio"
        action={
          <Button variant="outline" onClick={abrirEstadoSistema}>
            Estado del Sistema
          </Button>
        }
      />

      <div className="card-grid-3" style={{ marginBottom: '20px' }}>
        <StatCard icon="groups" value={residentes?.totalItems ?? '—'} label="Residentes" color="primary" />
        <StatCard icon="domain" value={apartamentos?.totalItems ?? '—'} label="Apartamentos" color="blue" />
        <StatCard icon="description" value={contratosActivos} label="Contratos Activos" color="green" />
      </div>

      {/* ==== BLOQUE 1: Multas Pendientes ==== */}
      <div className="card" style={{ marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span className="material-symbols-outlined" style={{ color: '#D97706' }}>gavel</span>
            <div>
              <div style={{ fontWeight: 700 }}>Multas Pendientes</div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{loadingMultas ? 'Cargando...' : `${pendientes.length} pendiente(s)`}</div>
            </div>
          </div>
          <Button variant="outline" size="sm" onClick={() => setConfirmarNotificarTodas(true)} disabled={notificando || pendientes.length === 0}>
            Notificar todos
          </Button>
        </div>

        {multasError && (
          <div className="table-container p-8 text-center">
            <p className="text-error" style={{ marginBottom: '8px' }}>{multasError}</p>
            <Button variant="outline" size="sm" onClick={refetchMultas}>Reintentar</Button>
          </div>
        )}

        {!multasError && loadingMultas && (
          <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>Cargando...</p>
        )}

        {!multasError && !loadingMultas && pendientes.length === 0 && (
          <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>Sin multas pendientes</p>
        )}

        {!multasError && !loadingMultas && gruposDePagina.map((g) => (
          <div key={g.apto} style={{ borderBottom: '1px solid var(--border-subtle)', padding: '10px 0' }}>
            <div style={{ fontWeight: 700, fontSize: '13px', marginBottom: '6px' }}>
              Apto {g.apto}
              {g.residente && (
                <span style={{ fontWeight: 400, color: 'var(--text-muted)', marginLeft: '6px' }}>{g.residente}</span>
              )}
            </div>
            {g.multas.map((m) => (
              <div
                key={m.idMulta}
                role="button"
                tabIndex={0}
                onClick={() => verDetalleMulta(m.idMulta)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    verDetalleMulta(m.idMulta);
                  }
                }}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0', cursor: 'pointer' }}
              >
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '15px', color: m.tipo === 'RUIDO' ? '#D97706' : '#2855A0' }}>
                    {m.tipo === 'RUIDO' ? 'volume_up' : 'local_parking'}
                  </span>
                  {m.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero'} — {formatCurrency(m.monto)}
                </span>
                <span style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <small style={{ color: 'var(--text-muted)' }}>{formatDate(m.fechaCreacion)}</small>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    aria-label={`Notificar multa ${m.idMulta}`}
                    title="Notificar"
                    onClick={(e) => { e.stopPropagation(); notificarUna(m.idMulta); }}
                  >
                    <span className="material-symbols-outlined" style={{ fontSize: '15px' }}>notifications</span>
                  </button>
                </span>
              </div>
            ))}
          </div>
        ))}

        {/* Paginación 10/pág */}
        {!multasError && !loadingMultas && grupos.length > MULTAS_PAGE_SIZE && (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '12px' }}>
            <Button
              variant="outline"
              size="sm"
              disabled={paginaSegura <= 1}
              onClick={() => setMultasPage((p) => Math.max(1, p - 1))}
            >
              ← Anterior
            </Button>
            <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
              Página {paginaSegura} de {totalPaginas}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={paginaSegura >= totalPaginas}
              onClick={() => setMultasPage((p) => Math.min(totalPaginas, p + 1))}
            >
              Siguiente →
            </Button>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={confirmarNotificarTodas}
        onClose={() => setConfirmarNotificarTodas(false)}
        onConfirm={notificarTodas}
        title="Notificar todas las multas"
        message={`¿Enviar notificación a todos los apartamentos con multas pendientes (${pendientes.length})?`}
        confirmLabel="Notificar"
      />

      {/* ==== BLOQUE 2: Próximos Cobros ==== */}
      <div className="card" style={{ marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
          <span className="material-symbols-outlined" style={{ color: '#10B981' }}>payments</span>
          <div>
            <div style={{ fontWeight: 700 }}>Próximos Cobros</div>
            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Contratos activos</div>
          </div>
        </div>

        {contratosError && (
          <div className="table-container p-8 text-center">
            <p className="text-error" style={{ marginBottom: '8px' }}>{contratosError}</p>
            <Button variant="outline" size="sm" onClick={refetchContratos}>Reintentar</Button>
          </div>
        )}

        {!contratosError && loadingContratos && (
          <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>Cargando...</p>
        )}

        {!contratosError && !loadingContratos && contratosActivosList.length === 0 && (
          <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '24px 0' }}>Sin contratos activos</p>
        )}

        {!contratosError && !loadingContratos && contratosActivosList.map((c) => (
          <button
            type="button"
            key={c.idContrato}
            onClick={() => verDetalleContrato(c)}
            style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '10px 12px', border: '1px solid var(--border)', borderRadius: '8px',
              background: 'none', cursor: 'pointer', fontFamily: 'inherit', fontSize: '13px',
              width: '100%', textAlign: 'left', marginBottom: '6px',
            }}
          >
            <span>
              <strong>Apt {c.numeroApartamento || c.idApartamento}</strong>
              <small style={{ display: 'block', color: 'var(--text-muted)' }}>Contrato #{c.idContrato}</small>
            </span>
            <span style={{ fontWeight: 700, color: '#10B981' }}>{formatCurrency(c.valorMensual)}</span>
          </button>
        ))}
      </div>

      {/* ==== BLOQUE 3: Modal Detalle Contrato ==== */}
      <Modal
        open={!!detalleContrato}
        onClose={() => setDetalleContrato(null)}
        title={`Detalles del Contrato #${detalleContrato?.idContrato || ''}`}
        size="lg"
      >
        {loadingDetalleContrato && <p>Cargando...</p>}
        {detalleContrato && !loadingDetalleContrato && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {detalleContrato.residente && (
              <div className="card" style={{ padding: '12px', background: 'var(--navy-50)', border: '1px solid var(--navy-200)' }}>
                <div style={{ fontWeight: 700, marginBottom: '8px' }}>Titular del Contrato</div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px', fontSize: '13px' }}>
                  <div><span className="text-muted">Nombre:</span><br /><strong>
                    {detalleContrato.residente.nombres} {detalleContrato.residente.apellidos}
                  </strong></div>
                  <div><span className="text-muted">Documento:</span><br /><strong>{detalleContrato.residente.numeroDocumento || '-'}</strong></div>
                  <div><span className="text-muted">Teléfono:</span><br /><strong>{detalleContrato.residente.telefono || '-'}</strong></div>
                  <div><span className="text-muted">Email:</span><br /><strong style={{ wordBreak: 'break-all' }}>{detalleContrato.residente.email || '-'}</strong></div>
                </div>
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '12px' }}>
              <div><span className="text-muted">Fecha Inicio</span><br /><strong>{formatDate(detalleContrato.fechaInicio)}</strong></div>
              <div><span className="text-muted">Fecha Fin</span><br /><strong>{detalleContrato.fechaFin ? formatDate(detalleContrato.fechaFin) : 'Indefinido'}</strong></div>
              <div><span className="text-muted">Valor Mensual</span><br /><strong style={{ color: '#10B981' }}>{formatCurrency(detalleContrato.valorMensual)}</strong></div>
              <div><span className="text-muted">Estado</span><br />
                <span className={`badge ${detalleContrato.estado === 'ACTIVO' ? 'badge-activo' : 'badge-neutral'}`}>{detalleContrato.estado}</span>
              </div>
            </div>

            <div>
              <div style={{ fontWeight: 700, marginBottom: '8px' }}>Resumen de Pagos</div>
              {detalleContrato.cuotas.length === 0 ? (
                <p className="text-muted" style={{ fontSize: '13px' }}>Sin cuotas registradas</p>
              ) : (
                <>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '12px', marginBottom: '12px' }}>
                    <div style={{ padding: '12px', background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '8px' }}>
                      <div className="text-muted" style={{ fontSize: '12px' }}>Cuotas Pagadas</div>
                      <div style={{ fontWeight: 800, color: '#059669' }}>
                        {detalleContrato.cuotas.filter((c) => c.estado === 'PAGADA').length}
                      </div>
                      <div style={{ fontSize: '12px', color: '#059669' }}>
                        {formatCurrency(detalleContrato.cuotas.filter((c) => c.estado === 'PAGADA').reduce((s, c) => s + Number(c.valorTotal || 0), 0))}
                      </div>
                    </div>
                    <div style={{ padding: '12px', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px' }}>
                      <div className="text-muted" style={{ fontSize: '12px' }}>Cuotas Pendientes</div>
                      <div style={{ fontWeight: 800, color: '#d97706' }}>
                        {detalleContrato.cuotas.filter((c) => c.estado !== 'PAGADA').length}
                      </div>
                      <div style={{ fontSize: '12px', color: '#d97706' }}>
                        {formatCurrency(detalleContrato.cuotas.filter((c) => c.estado !== 'PAGADA').reduce((s, c) => s + Number(c.valorTotal || 0), 0))}
                      </div>
                    </div>
                  </div>
                  <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
                    <table className="data-table">
                      <thead>
                        <tr><th>Periodo</th><th>Vencimiento</th><th>Valor</th><th>Estado</th></tr>
                      </thead>
                      <tbody>
                        {detalleContrato.cuotas.map((c) => (
                          <tr key={c.idCuota || c.id}>
                            <td>{periodoLabel(c.anio, c.mes)}</td>
                            <td>{formatDate(c.fechaLimite)}</td>
                            <td>{formatCurrency(c.valorTotal)}</td>
                            <td><span className={`badge ${c.estado === 'PAGADA' ? 'badge-activo' : 'badge-pendiente-firma'}`}>{c.estado}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </Modal>

      {/* ==== BLOQUE 4: Modal Detalle Multa ==== */}
      <Modal
        open={!!detalleMulta}
        onClose={() => setDetalleMulta(null)}
        title={`Multa por ${detalleMulta?.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero'}`}
        size="md"
      >
        {loadingDetalleMulta && <p>Cargando...</p>}
        {detalleMulta && !loadingDetalleMulta && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="card" style={{ padding: '12px', background: 'var(--navy-50)', border: '1px solid var(--navy-200)' }}>
              <div style={{ fontWeight: 700, marginBottom: '8px' }}>Residente Afectado</div>
              <div style={{ fontSize: '14px' }}><strong>{detalleMulta.nombreResidente || 'Sin información'}</strong></div>
              <div style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
                Apto {detalleMulta.numeroApartamento || 'N/A'}
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '12px' }}>
              <div>
                <span className="text-muted">Monto</span><br />
                <strong style={{ fontSize: '18px', color: '#e11d48' }}>{formatCurrency(detalleMulta.monto)}</strong>
              </div>
              <div>
                <span className="text-muted">Estado</span><br />
                <span className={`badge ${detalleMulta.estado === 'PAGADA' ? 'badge-activo' : 'badge-pendiente-firma'}`}>
                  {detalleMulta.estado}
                </span>
              </div>
              <div>
                <span className="text-muted">Fecha de Generación</span><br />
                <strong>{formatDateTime(detalleMulta.fechaCreacion)}</strong>
              </div>
              <div>
                <span className="text-muted">Generada por</span><br />
                <strong>{detalleMulta.nombrePortero || 'Sistema'}</strong>
              </div>
            </div>

            {detalleMulta.descripcion && (
              <div>
                <span className="text-muted">Descripción</span><br />
                <div style={{ whiteSpace: 'pre-wrap', fontSize: '13px' }}>{detalleMulta.descripcion}</div>
              </div>
            )}

            {detalleMulta.tipo === 'RUIDO' && detalleMulta.fechaAvisoRuido && (
              <div style={{ padding: '12px', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px' }}>
                <div style={{ fontWeight: 600, marginBottom: '6px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>notifications</span>
                  Aviso Previo de Ruido
                </div>
                <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                  Fecha y hora: {formatDateTime(detalleMulta.fechaAvisoRuido)}
                </div>
              </div>
            )}

            {/* Fiel al legacy (app.js:608) y al modelado real: la foto solo existe en el flujo PARQUEADERO */}
            {detalleMulta.tipo === 'PARQUEADERO' && detalleMulta.fotoEvidencia && (
              <div>
                <span className="text-muted">Foto de Evidencia</span><br />
                <img
                  src={imageSrc(detalleMulta.fotoEvidencia)}
                  alt="Evidencia de la multa"
                  style={{ width: '100%', maxHeight: '300px', objectFit: 'contain', borderRadius: '8px', border: '1px solid var(--border)', cursor: 'zoom-in' }}
                  onClick={() => window.open(imageSrc(detalleMulta.fotoEvidencia))}
                />
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '8px' }}>
              <Button variant="outline" size="sm" onClick={() => notificarUna(detalleMulta.idMulta)}>
                Notificar al Residente
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* ==== BLOQUE 5: Modal Estado del Sistema ==== */}
      <Modal
        open={!!estadoSistema}
        onClose={() => setEstadoSistema(null)}
        title="Estado del Sistema"
        size="md"
      >
        {loadingEstadoSistema && <p>Cargando...</p>}
        {estadoSistema && !loadingEstadoSistema && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div>
              <div style={{ fontWeight: 700, marginBottom: '8px' }}>Visitantes dentro del edificio</div>
              {estadoSistema.activos.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>No hay visitantes dentro</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {estadoSistema.activos.map((v) => (
                    <div key={v.idAcceso} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                      <span>
                        {v.numeroApartamento && <small style={{ color: 'var(--text-muted)' }}>Apto {v.numeroApartamento} — </small>}
                        {v.nombreResidente || `Visitante #${v.idVisita}`}
                        {v.codigoParqueadero && <small style={{ color: 'var(--text-muted)' }}> · Parq. {v.codigoParqueadero}</small>}
                      </span>
                      <small style={{ color: 'var(--text-muted)' }}>{formatTime(v.horaEntrada)}</small>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div>
              <div style={{ fontWeight: 700, marginBottom: '8px' }}>QR generados sin escanear</div>
              {estadoSistema.qrPendientes.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '13px' }}>No hay QR pendientes</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {estadoSistema.qrPendientes.map((v) => (
                    <div key={v.idVisita} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                      <span>
                        {v.numeroApartamento && <small style={{ color: 'var(--text-muted)' }}>Apto {v.numeroApartamento} — </small>}
                        {v.nombreVisitante || `Visitante #${v.idVisita}`}
                      </span>
                      <small style={{ color: 'var(--text-muted)' }}>{formatTime(v.fechaRegistro)}</small>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>

      <Toast toast={toast} />
    </div>
  );
}
