import { useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, todayStr, formatMiles, parseMiles, periodoLabel } from '../lib/utils.js';

function agruparPorApartamento(cuotas, multas) {
  const mapa = new Map();
  (cuotas || []).forEach((c) => {
    const key = c.numeroApartamento || `Apto #${c.idContrato}`;
    if (!mapa.has(key)) {
      mapa.set(key, {
        numeroApartamento: key,
        nombreResidente: c.nombreResidente,
        cuotas: [],
        multas: [],
      });
    }
    mapa.get(key).cuotas.push(c);
  });
  (multas || []).forEach((m) => {
    const key = m.numeroApartamento || `Apto #${m.idApartamento}`;
    if (!mapa.has(key)) {
      mapa.set(key, {
        numeroApartamento: key,
        nombreResidente: m.nombreResidente,
        cuotas: [],
        multas: [],
      });
    }
    mapa.get(key).multas.push(m);
  });
  return Array.from(mapa.values());
}

export default function PagosPage() {
  const [search, setSearch] = useState('');
  const [detalle, setDetalle] = useState(null);
  const [pagoModal, setPagoModal] = useState(null); // { tipo: 'cuota'|'multa', item }
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();
  const [pagoForm, setPagoForm] = useState({ fecha: todayStr(), valor: '', metodo: 'EFECTIVO', referencia: '', notas: '' });
  const [saving, setSaving] = useState(false);
  // Guard anti doble-submit: mismo patron que VisitasPage (FASE 4.2-P2).
  // Critico aqui: un doble POST /pagos registraria el pago de la misma cuota dos veces.
  const savingRef = useRef(false);

  const { data: cuotas, loading: loadingCuotas, error: errorCuotas, refetch: refetchCuotas } = useFetch(
    () => api.get('/cuotas?pendientes=true'),
    []
  );
  const { data: multas, loading: loadingMultas, error: errorMultas, refetch: refetchMultas } = useFetch(
    () => api.get('/multas/todas'),
    []
  );
  const { data: pagosData, loading: loadingPagos, error: errorPagos, refetch: refetchPagos } = useFetch(
    () => api.get('/pagos'),
    []
  );

  const [tabPrincipal, setTabPrincipal] = useState('COBROS'); // 'COBROS' | 'APROBACIONES'
  const [filtroEstadoPago, setFiltroEstadoPago] = useState('PENDIENTE_APROBACION');
  const [comprobanteModal, setComprobanteModal] = useState(null); // pago object
  const [rechazoModal, setRechazoModal] = useState(null); // pago object
  const [motivoRechazo, setMotivoRechazo] = useState('');
  const [accionandoPago, setAccionandoPago] = useState(false);

  const loading = loadingCuotas || loadingMultas || (tabPrincipal === 'APROBACIONES' && loadingPagos);
  const fetchError = errorCuotas || errorMultas || errorPagos;

  const pagos = useMemo(() => {
    const list = pagosData?.items || pagosData || [];
    return Array.isArray(list) ? list : [];
  }, [pagosData]);

  const pagosPendientesCount = useMemo(() => {
    return pagos.filter((p) => p.estado === 'PENDIENTE_APROBACION').length;
  }, [pagos]);

  const filteredPagos = useMemo(() => {
    return pagos.filter((p) => {
      if (filtroEstadoPago !== 'TODOS' && p.estado !== filtroEstadoPago) {
        return false;
      }
      if (search.trim()) {
        const q = search.toLowerCase().trim();
        const apto = String(p.numeroApartamento || '').toLowerCase();
        const res = String(p.nombreResidente || '').toLowerCase();
        const ref = String(p.referenciaComprobante || '').toLowerCase();
        const id = String(p.idPago || '');
        return apto.includes(q) || res.includes(q) || ref.includes(q) || id.includes(q);
      }
      return true;
    });
  }, [pagos, filtroEstadoPago, search]);

  async function handleAprobarPago(idPago) {
    if (accionandoPago) return;
    setAccionandoPago(true);
    try {
      await api.post(`/pagos/${idPago}/aprobar`);
      toast.success(`Pago #${idPago} aprobado exitosamente. Se aplicó el abono a la cuota.`);
      refetchPagos();
      refetchCuotas();
    } catch (err) {
      toast.error(err.message || 'Error al aprobar el pago');
    } finally {
      setAccionandoPago(false);
    }
  }

  async function handleRechazarPago() {
    if (!rechazoModal || accionandoPago) return;
    if (!motivoRechazo.trim()) {
      toast.error('El motivo de rechazo es obligatorio');
      return;
    }
    setAccionandoPago(true);
    try {
      await api.post(`/pagos/${rechazoModal.idPago}/rechazar`, {
        motivoRechazo: motivoRechazo.trim(),
      });
      toast.success(`Pago #${rechazoModal.idPago} rechazado`);
      setRechazoModal(null);
      setMotivoRechazo('');
      refetchPagos();
    } catch (err) {
      toast.error(err.message || 'Error al rechazar el pago');
    } finally {
      setAccionandoPago(false);
    }
  }

  const residentes = useMemo(() => {
    const agrupado = agruparPorApartamento(
      cuotas?.items || cuotas || [],
      (multas?.items || multas || []).filter((m) => m.estado === 'PENDIENTE')
    );
    if (!search) return agrupado;
    const term = search.toLowerCase();
    return agrupado.filter(
      (r) =>
        String(r.numeroApartamento || '').toLowerCase().includes(term) ||
        String(r.nombreResidente || '').toLowerCase().includes(term)
    );
  }, [cuotas, multas, search]);

  const kpis = useMemo(() => {
    const cuotasPendientes = (cuotas?.items || cuotas || []).reduce((s, c) => s + Number(c.saldoPendiente ?? c.valorTotal ?? 0), 0);
    const multasPendientes = (multas?.items || multas || [])
      .filter((m) => m.estado === 'PENDIENTE')
      .reduce((s, m) => s + Number(m.monto || 0), 0);
    return { cuotasPendientes, multasPendientes, aptosConSaldo: residentes.length };
  }, [cuotas, multas, residentes]);

  const columns = [
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Residente' },
    {
      key: 'cuotas',
      label: 'Cuotas pendientes',
      render: (r) => <span className="badge badge-pendiente-firma">{r.cuotas.length}</span>,
    },
    {
      key: 'multas',
      label: 'Multas pendientes',
      render: (r) => <span className="badge badge-danger">{r.multas.length}</span>,
    },
  ];

  function abrirPagoCuota(cuota) {
    resetTouched();
    setPagoModal({ tipo: 'cuota', item: cuota });
    const saldo = Number(cuota.saldoPendiente ?? cuota.valorTotal ?? 0);
    setPagoForm({
      fecha: todayStr(),
      valor: saldo > 0 ? formatMiles(saldo) : '',
      metodo: 'TRANSFERENCIA',
      referencia: '',
      notas: '',
    });
  }
  function abrirPagoMulta(multa) {
    resetTouched();
    setPagoModal({ tipo: 'multa', item: multa });
    setPagoForm({ fecha: todayStr(), valor: '', metodo: 'EFECTIVO', referencia: '', notas: '' });
  }

  async function confirmarPago() {
    if (savingRef.current) return; // doble submit
    if (!pagoModal) return;
    const fieldsToTouch = ['valor', ...(pagoModal.tipo === 'cuota' && pagoForm.metodo === 'TRANSFERENCIA' ? ['referencia'] : [])];
    touchAll(fieldsToTouch);
    const valor = parseMiles(pagoForm.valor);
    if (valor <= 0) {
      toast.error('El valor pagado debe ser mayor que 0');
      return;
    }
    if (pagoModal.tipo === 'cuota') {
      const saldo = Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0);
      if (valor > saldo) {
        toast.error(`El valor pagado no puede superar el saldo pendiente (${formatCurrency(saldo)})`);
        return;
      }
    }
    if (pagoModal.tipo === 'cuota' && pagoForm.metodo === 'TRANSFERENCIA') {
      const ref = pagoForm.referencia.trim();
      if (!/^[A-Za-z0-9-]{4,50}$/.test(ref)) {
        toast.error('La referencia debe tener entre 4 y 50 caracteres (letras, números o guiones)');
        return;
      }
    }
    savingRef.current = true;
    setSaving(true);
    try {
      if (pagoModal.tipo === 'cuota') {
        await api.post('/pagos', {
          idCuota: pagoModal.item.idCuota || pagoModal.item.id,
          fechaPago: pagoForm.fecha,
          valorPagado: parseMiles(pagoForm.valor),
          metodoPago: pagoForm.metodo,
          referencia: pagoForm.referencia,
          notas: pagoForm.notas,
        });
        toast.success('Pago manual registrado (Pendiente de Aprobación)');
      } else {
        await api.put(`/multas/${pagoModal.item.idMulta}/pagar`, { metodoPago: pagoForm.metodo });
        toast.success('Pago de multa registrado');
      }
      resetTouched();
      setPagoModal(null);
      refetchCuotas();
      refetchMultas();
      refetchPagos();
      if (detalle) {
        setDetalle(null);
      }
    } catch (err) {
      toast.error(err.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Gestión de Pagos"
        subtitle="Control de cartera, cobros y aprobación de pagos manuales"
        action={
          <Input
            id="search" aria-label="Buscar"
            placeholder={tabPrincipal === 'COBROS' ? "Buscar apto o residente..." : "Buscar por apto, residente, ref..."}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />

      {/* Tabs Principales: Cobros vs Bandeja de Aprobación */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        <button
          type="button"
          onClick={() => setTabPrincipal('COBROS')}
          style={{
            padding: '8px 16px',
            borderRadius: '8px',
            fontSize: '13px',
            fontWeight: 600,
            cursor: 'pointer',
            border: '1px solid var(--border-color, #e2e8f0)',
            background: tabPrincipal === 'COBROS' ? 'var(--primary, #0f172a)' : 'var(--card-bg, #fff)',
            color: tabPrincipal === 'COBROS' ? '#fff' : 'inherit',
          }}
        >
          Cobros y Saldos
        </button>
        <button
          type="button"
          onClick={() => setTabPrincipal('APROBACIONES')}
          style={{
            padding: '8px 16px',
            borderRadius: '8px',
            fontSize: '13px',
            fontWeight: 600,
            cursor: 'pointer',
            border: '1px solid var(--border-color, #e2e8f0)',
            background: tabPrincipal === 'APROBACIONES' ? 'var(--primary, #0f172a)' : 'var(--card-bg, #fff)',
            color: tabPrincipal === 'APROBACIONES' ? '#fff' : 'inherit',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <span>Bandeja de Aprobación</span>
          {pagosPendientesCount > 0 && (
            <span
              style={{
                background: '#f59e0b',
                color: '#fff',
                fontSize: '11px',
                padding: '1px 6px',
                borderRadius: '9999px',
                fontWeight: 700,
              }}
            >
              {pagosPendientesCount}
            </span>
          )}
        </button>
      </div>

      {tabPrincipal === 'COBROS' ? (
        <>
          <div className="card-grid-3" style={{ marginBottom: '20px' }}>
            <StatCard icon="receipt_long" value={formatCurrency(kpis.cuotasPendientes)} label="Cuotas pendientes" color="primary" />
            <StatCard icon="gavel" value={formatCurrency(kpis.multasPendientes)} label="Multas pendientes" color="amber" />
            <StatCard icon="apartment" value={kpis.aptosConSaldo} label="Aptos con saldo" color="blue" />
          </div>
          <DataTable
            columns={columns}
            rows={residentes}
            loading={loading}
            empty={{ icon: 'payments', title: 'No hay pagos pendientes', subtitle: 'Todos los pagos al día. Los nuevos aparecerán aquí.' }}
            error={fetchError?.message}
            keyField="numeroApartamento"
            onRowClick={setDetalle}
          />
        </>
      ) : (
        /* Bandeja de Aprobación de Pagos Manuales (GAP-F6-05) */
        <div>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
            {[
              { id: 'PENDIENTE_APROBACION', label: `Pendientes (${pagosPendientesCount})` },
              { id: 'APROBADO', label: 'Aprobados' },
              { id: 'RECHAZADO', label: 'Rechazados' },
              { id: 'TODOS', label: `Todos (${pagos.length})` },
            ].map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => setFiltroEstadoPago(f.id)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  border: filtroEstadoPago === f.id ? '1px solid var(--primary, #0f172a)' : '1px solid var(--border-color, #e2e8f0)',
                  background: filtroEstadoPago === f.id ? 'var(--muted, #f1f5f9)' : '#fff',
                  color: filtroEstadoPago === f.id ? 'var(--primary, #0f172a)' : 'var(--text-muted, #64748b)',
                }}
              >
                {f.label}
              </button>
            ))}
          </div>

          <div className="table-container" style={{ background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Apto / Residente</th>
                  <th>Concepto Cuota</th>
                  <th>Monto</th>
                  <th>Método / Ref</th>
                  <th>Fecha Pago</th>
                  <th>Comprobante</th>
                  <th>Estado</th>
                  <th style={{ textAlign: 'right' }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filteredPagos.map((p) => {
                  const esPendiente = p.estado === 'PENDIENTE_APROBACION';
                  const esAprobado = p.estado === 'APROBADO';
                  const esRechazado = p.estado === 'RECHAZADO';
                  return (
                    <tr key={p.idPago}>
                      <td style={{ fontWeight: 600 }}>#{p.idPago}</td>
                      <td>
                        <div style={{ fontWeight: 600 }}>Apto {p.numeroApartamento || `Unidad #${p.idUnidad}`}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{p.nombreResidente}</div>
                      </td>
                      <td>
                        <div>{p.conceptoCuota || 'Cuota de Administración'}</div>
                        {p.periodoCuota && <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Período: {p.periodoCuota}</div>}
                      </td>
                      <td style={{ fontWeight: 700, fontSize: '14px' }}>{formatCurrency(p.montoTotal)}</td>
                      <td>
                        <div>{p.metodoPago}</div>
                        {p.referenciaComprobante && (
                          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Ref: {p.referenciaComprobante}</div>
                        )}
                      </td>
                      <td>{formatDate(p.fechaPago)}</td>
                      <td>
                        {p.comprobanteUrl ? (
                          <button
                            type="button"
                            onClick={() => setComprobanteModal(p)}
                            style={{
                              padding: '3px 8px',
                              fontSize: '11px',
                              borderRadius: '4px',
                              border: '1px solid #cbd5e1',
                              background: '#f8fafc',
                              cursor: 'pointer',
                            }}
                          >
                            Ver Soporte
                          </button>
                        ) : (
                          <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Sin soporte</span>
                        )}
                      </td>
                      <td>
                        {esPendiente && (
                          <span style={{ background: '#fef3c7', color: '#92400e', padding: '2px 8px', borderRadius: '9999px', fontSize: '11px', fontWeight: 600 }}>
                            Pendiente Aprobación
                          </span>
                        )}
                        {esAprobado && (
                          <span style={{ background: '#d1fae5', color: '#065f46', padding: '2px 8px', borderRadius: '9999px', fontSize: '11px', fontWeight: 600 }}>
                            Aprobado
                          </span>
                        )}
                        {esRechazado && (
                          <div>
                            <span style={{ background: '#fee2e2', color: '#991b1b', padding: '2px 8px', borderRadius: '9999px', fontSize: '11px', fontWeight: 600 }}>
                              Rechazado
                            </span>
                            {p.observaciones && (
                              <div style={{ fontSize: '10px', color: '#b91c1c', marginTop: '2px' }}>
                                Motivo: {p.observaciones}
                              </div>
                            )}
                          </div>
                        )}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {esPendiente && (
                          <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                            <Button
                              onClick={() => handleAprobarPago(p.idPago)}
                              disabled={accionandoPago}
                              style={{ padding: '4px 10px', fontSize: '11px', background: '#059669', color: '#fff' }}
                            >
                              Aprobar
                            </Button>
                            <Button
                              onClick={() => {
                                setRechazoModal(p);
                                setMotivoRechazo('');
                              }}
                              disabled={accionandoPago}
                              variant="outline"
                              style={{ padding: '4px 10px', fontSize: '11px', borderColor: '#ef4444', color: '#ef4444' }}
                            >
                              Rechazar
                            </Button>
                          </div>
                        )}
                        {esAprobado && p.nombreAprobador && (
                          <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
                            Por: {p.nombreAprobador}
                          </div>
                        )}
                        {esRechazado && p.nombreRechazador && (
                          <div style={{ fontSize: '10px', color: 'var(--text-muted)' }}>
                            Por: {p.nombreRechazador}
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
                {filteredPagos.length === 0 && (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', padding: '32px', color: 'var(--text-muted)' }}>
                      No hay pagos en este estado
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Modal para Visualizar Comprobante */}
      <Modal
        open={!!comprobanteModal}
        onClose={() => setComprobanteModal(null)}
        title={`Comprobante de Pago #${comprobanteModal?.idPago || ''} — Apto ${comprobanteModal?.numeroApartamento || ''}`}
        size="lg"
      >
        {comprobanteModal && (
          <div style={{ textAlign: 'center', padding: '12px' }}>
            <div style={{ marginBottom: '12px', fontSize: '13px', color: 'var(--text-muted)' }}>
              Monto: <strong>{formatCurrency(comprobanteModal.montoTotal)}</strong> | Método: <strong>{comprobanteModal.metodoPago}</strong> | Referencia: <strong>{comprobanteModal.referenciaComprobante || 'N/A'}</strong>
            </div>
            {comprobanteModal.comprobanteUrl?.toLowerCase().endsWith('.pdf') ? (
              <iframe
                src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                title="Comprobante PDF"
                style={{ width: '100%', height: '480px', border: 'none', borderRadius: '8px' }}
              />
            ) : (
              <img
                src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                alt="Comprobante de pago"
                style={{ maxWidth: '100%', maxHeight: '480px', margin: '0 auto', borderRadius: '8px', objectFit: 'contain' }}
              />
            )}
            <div style={{ marginTop: '16px' }}>
              <a
                href={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                target="_blank"
                rel="noreferrer"
                className="btn btn-outline"
                style={{ display: 'inline-block', textDecoration: 'none', padding: '6px 14px', fontSize: '12px' }}
              >
                Descargar Comprobante Original
              </a>
            </div>
          </div>
        )}
      </Modal>

      {/* Modal de Rechazo de Pago */}
      <Modal
        open={!!rechazoModal}
        onClose={() => {
          if (!accionandoPago) setRechazoModal(null);
        }}
        title={`Rechazar Pago #${rechazoModal?.idPago || ''}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRechazoModal(null)} disabled={accionandoPago}>
              Cancelar
            </Button>
            <Button
              onClick={handleRechazarPago}
              disabled={accionandoPago || !motivoRechazo.trim()}
              style={{ background: '#ef4444', color: '#fff' }}
            >
              {accionandoPago ? 'Rechazando...' : 'Confirmar Rechazo'}
            </Button>
          </>
        }
      >
        {rechazoModal && (
          <div style={{ padding: '8px 0' }}>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '12px' }}>
              Por favor indique la razón del rechazo del pago de <strong>{formatCurrency(rechazoModal.montoTotal)}</strong> para el Apto <strong>{rechazoModal.numeroApartamento}</strong>. El residente será notificado.
            </p>
            <div className="form-group">
              <label htmlFor="motivoRechazo" style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>
                Motivo de Rechazo (Obligatorio)
              </label>
              <textarea
                id="motivoRechazo"
                rows={3}
                placeholder="Ej. El número de aprobación no coincide con los extractos de la cuenta bancaria..."
                value={motivoRechazo}
                onChange={(e) => setMotivoRechazo(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                }}
              />
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title={`Apto ${detalle?.numeroApartamento || ''} — ${detalle?.nombreResidente || ''}`}
        size="lg"
      >
        {detalle && (
          <>
                <h4 className="mb-2 text-[13px] font-bold">Cuotas</h4>
            <div className="table-container" style={{ marginBottom: '16px' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Período</th>
                    <th>Monto</th>
                    <th>Vencimiento</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {detalle.cuotas.map((c) => (
                    <tr key={c.idCuota || c.id}>
                      <td>{periodoLabel(c.anio, c.mes)}</td>
                      <td>{formatCurrency(c.saldoPendiente ?? c.valorTotal)}</td>
                      <td>{formatDate(c.fechaLimite)}</td>
                      <td>
                        <Button onClick={() => abrirPagoCuota(c)} style={{ padding: '4px 10px', fontSize: '11px' }}>
                          Pagar
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {detalle.cuotas.length === 0 && (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                        Sin cuotas pendientes
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
                <h4 className="mb-2 text-[13px] font-bold">Multas</h4>
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Motivo</th>
                    <th>Monto</th>
                    <th>Fecha</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {detalle.multas.map((m) => (
                    <tr key={m.idMulta}>
                      <td>{m.tipo}</td>
                      <td>{formatCurrency(m.monto)}</td>
                      <td>{formatDate(m.fechaCreacion)}</td>
                      <td>
                        <Button onClick={() => abrirPagoMulta(m)} style={{ padding: '4px 10px', fontSize: '11px' }}>
                          Pagar
                        </Button>
                      </td>
                    </tr>
                  ))}
                  {detalle.multas.length === 0 && (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                        Sin multas pendientes
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Modal>

      <Modal
        open={!!pagoModal}
        onClose={() => {
          setPagoModal(null);
          resetTouched();
        }}
        title={pagoModal?.tipo === 'cuota' ? 'Registrar Pago de Cuota' : 'Registrar Pago de Multa'}
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => {
                setPagoModal(null);
                resetTouched();
              }}
              disabled={saving}
            >
              Cancelar
            </Button>
            <Button onClick={confirmarPago} disabled={saving}>
              {saving ? 'Guardando...' : 'Confirmar'}
            </Button>
          </>
        }
      >
        {pagoModal?.tipo === 'cuota' && (
          <>
            <div className="form-row">
              <Input
                id="fecha"
                label="Fecha de pago"
                type="date"
                value={pagoForm.fecha}
                onChange={(e) => setPagoForm((f) => ({ ...f, fecha: e.target.value }))}
                max={todayStr()}
                required
              />
              <Input
                id="valor"
                label="Valor pagado"
                placeholder="Ej. 250.000"
                inputMode="numeric"
                value={pagoForm.valor}
                onChange={(e) => setPagoForm((f) => ({ ...f, valor: formatMiles(e.target.value) }))}
                onBlur={() => touch('valor')}
                error={
                  fieldError(
                    'valor',
                    parseMiles(pagoForm.valor) <= 0
                      ? { ok: false, mensaje: 'El valor pagado debe ser mayor que 0' }
                      : pagoModal?.tipo === 'cuota' && parseMiles(pagoForm.valor) > Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0)
                        ? { ok: false, mensaje: `No puede superar el saldo pendiente (${formatCurrency(Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0))})` }
                        : { ok: true }
                  ) || undefined
                }
                required
              />
            </div>
          </>
        )}
        <div className="form-group">
          <Select
            id="metodo"
            label="Método de pago"
            value={pagoForm.metodo}
            onChange={(e) => setPagoForm((f) => ({ ...f, metodo: e.target.value }))}
          >
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
          </Select>
        </div>
        {pagoModal?.tipo === 'cuota' && pagoForm.metodo === 'TRANSFERENCIA' && (
          <div className="form-group">
            <Input
              id="referencia"
              label="Referencia de transferencia"
              placeholder="Ej. TRANSF-98231"
              value={pagoForm.referencia}
              onChange={(e) => setPagoForm((f) => ({ ...f, referencia: e.target.value }))}
              onBlur={() => touch('referencia')}
              error={
                fieldError(
                  'referencia',
                  /^[A-Za-z0-9-]{4,50}$/.test(pagoForm.referencia.trim())
                    ? { ok: true }
                    : { ok: false, mensaje: 'La referencia debe tener entre 4 y 50 caracteres (letras, números o guiones)' }
                ) || undefined
              }
              required
            />
          </div>
        )}
        {pagoModal?.tipo === 'cuota' && (
          <div className="form-group">
            <Input
              id="notas"
              label="Notas (opcional)"
              placeholder="Ej. Pago correspondiente al mes actual"
              value={pagoForm.notas}
              onChange={(e) => setPagoForm((f) => ({ ...f, notas: e.target.value }))}
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
