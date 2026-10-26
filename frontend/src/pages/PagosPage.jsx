import { useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Building2, Gavel, Receipt, Search } from 'lucide-react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { Input as BareInput } from '../components/ui/input.tsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Tabs, TabsList, TabsTrigger } from '../components/ui/tabs.tsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';
import { cn, formatCurrency, formatDate, todayStr, formatMiles, parseMiles, periodoLabel } from '../lib/utils.js';

const quiet = (fn) => () => fn().catch(() => {});

const ESTADO_PAGO = {
  PENDIENTE_APROBACION: { variant: 'warning', label: 'Pendiente de aprobación' },
  APROBADO: { variant: 'success', label: 'Aprobado' },
  RECHAZADO: { variant: 'destructive', label: 'Rechazado' },
};

function agruparPorApartamento(cuotas, multas) {
  const safeCuotas = Array.isArray(cuotas) ? cuotas : (Array.isArray(cuotas?.items) ? cuotas.items : []);
  const safeMultas = Array.isArray(multas) ? multas : (Array.isArray(multas?.items) ? multas.items : []);
  const mapa = new Map();
  safeCuotas.forEach((c) => {
    if (!c) return;
    const key = c.numeroApartamento || `Apto #${c.idContrato || 'S/N'}`;
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
  safeMultas.forEach((m) => {
    if (!m) return;
    const key = m.numeroApartamento || `Apto #${m.idApartamento || 'S/N'}`;
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
    const cuotasList = Array.isArray(cuotas) ? cuotas : (Array.isArray(cuotas?.items) ? cuotas.items : []);
    const multasList = Array.isArray(multas) ? multas : (Array.isArray(multas?.items) ? multas.items : []);
    const agrupado = agruparPorApartamento(
      cuotasList,
      multasList.filter((m) => m && m.estado === 'PENDIENTE')
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
    const cuotasList = Array.isArray(cuotas) ? cuotas : (Array.isArray(cuotas?.items) ? cuotas.items : []);
    const multasList = Array.isArray(multas) ? multas : (Array.isArray(multas?.items) ? multas.items : []);
    const cuotasPendientes = cuotasList.reduce((s, c) => s + Number(c?.saldoPendiente ?? c?.valorTotal ?? 0), 0);
    const multasPendientes = multasList
      .filter((m) => m && m.estado === 'PENDIENTE')
      .reduce((s, m) => s + Number(m?.monto || 0), 0);
    return { cuotasPendientes, multasPendientes, aptosConSaldo: residentes.length };
  }, [cuotas, multas, residentes]);

  const columns = [
    {
      key: 'numeroApartamento',
      label: 'Apartamento',
      render: (r) => <span className="font-semibold">{r.numeroApartamento}</span>,
    },
    { key: 'nombreResidente', label: 'Residente' },
    {
      key: 'cuotas',
      label: 'Cuotas pendientes',
      render: (r) => (
        <Badge variant={r.cuotas.length > 0 ? 'warning' : 'secondary'}>
          <span className="tabular-nums">{r.cuotas.length}</span>
        </Badge>
      ),
    },
    {
      key: 'multas',
      label: 'Multas pendientes',
      render: (r) => (
        <Badge variant={r.multas.length > 0 ? 'destructive' : 'secondary'}>
          <span className="tabular-nums">{r.multas.length}</span>
        </Badge>
      ),
    },
    {
      key: 'saldo',
      label: <span className="block text-right">Saldo pendiente</span>,
      cellClassName: 'text-right tabular-nums font-semibold whitespace-nowrap',
      render: (r) =>
        formatCurrency(
          r.cuotas.reduce((s, c) => s + Number(c?.saldoPendiente ?? c?.valorTotal ?? 0), 0) +
            r.multas.reduce((s, m) => s + Number(m?.monto || 0), 0)
        ),
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

  const cobrosError = errorCuotas || errorMultas;
  const saldoDe = (item) => Number(item?.saldoPendiente ?? item?.valorTotal ?? 0);
  const filtrosPago = [
    { id: 'PENDIENTE_APROBACION', label: 'Pendientes', count: pagosPendientesCount },
    { id: 'APROBADO', label: 'Aprobados' },
    { id: 'RECHAZADO', label: 'Rechazados' },
    { id: 'TODOS', label: 'Todos', count: pagos.length },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Gestión de pagos"
        subtitle="Saldos por apartamento, registro de pagos manuales y aprobación de soportes"
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Tabs value={tabPrincipal} onValueChange={setTabPrincipal} className="-mx-1 min-w-0 overflow-x-auto px-1 pb-1">
          <TabsList>
            <TabsTrigger value="COBROS">Cobros y saldos</TabsTrigger>
            <TabsTrigger value="APROBACIONES" className="gap-2">
              Bandeja de aprobación
              {pagosPendientesCount > 0 && (
                <Badge variant="warning" className="px-1.5 py-0">
                  <span className="tabular-nums">{pagosPendientesCount}</span>
                  <span className="sr-only"> pendientes</span>
                </Badge>
              )}
            </TabsTrigger>
          </TabsList>
        </Tabs>
        <div className="relative w-full sm:w-80">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <BareInput
            id="search"
            aria-label="Buscar"
            placeholder={tabPrincipal === 'COBROS' ? 'Buscar apartamento o residente' : 'Buscar por apartamento, residente o referencia'}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {tabPrincipal === 'COBROS' ? (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <MetricCard
              icon={Receipt}
              value={loadingCuotas ? '…' : errorCuotas ? '—' : formatCurrency(kpis.cuotasPendientes)}
              label="Cuotas pendientes"
              subtitle="Saldo por recaudar"
              variant="primary"
            />
            <MetricCard
              icon={Gavel}
              value={loadingMultas ? '…' : errorMultas ? '—' : formatCurrency(kpis.multasPendientes)}
              label="Multas pendientes"
              subtitle="Multas sin pago registrado"
              variant="warning"
            />
            <MetricCard
              icon={Building2}
              value={loading ? '…' : cobrosError ? '—' : kpis.aptosConSaldo}
              label="Apartamentos con saldo"
              subtitle="Seleccione uno para registrar un pago"
              variant="secondary"
            />
          </div>
          {cobrosError ? (
            <ErrorState
              title="No se pudieron cargar los saldos"
              message={cobrosError?.message || 'Revise su conexión e intente de nuevo.'}
              onRetry={() => {
                quiet(refetchCuotas)();
                quiet(refetchMultas)();
              }}
            />
          ) : (
            <DataTable
              columns={columns}
              rows={residentes}
              loading={loading}
              empty={{
                icon: Receipt,
                title: search ? 'No hay pagos pendientes que coincidan con la búsqueda' : 'No hay pagos pendientes',
                subtitle: search
                  ? 'Pruebe con otro apartamento o nombre.'
                  : 'Todos los apartamentos están al día. Los nuevos saldos aparecerán aquí.',
              }}
              keyField="numeroApartamento"
              onRowClick={setDetalle}
            />
          )}
        </>
      ) : (
        /* Manual payment approval inbox (GAP-F6-05) */
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por estado del pago">
            {filtrosPago.map((f) => {
              const active = filtroEstadoPago === f.id;
              return (
                <button
                  key={f.id}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setFiltroEstadoPago(f.id)}
                  className={cn(
                    'inline-flex min-h-10 items-center gap-1.5 rounded-md border px-3 text-[13px] font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-h-8',
                    active
                      ? 'border-primary-text/40 bg-tint-primary-bg text-primary-text'
                      : 'border-border bg-surface text-text-secondary hover:bg-surface-container hover:text-on-surface'
                  )}
                >
                  {f.label}
                  {f.count != null && <span className="tabular-nums">({f.count})</span>}
                </button>
              );
            })}
          </div>

          {errorPagos ? (
            <ErrorState
              title="No se pudieron cargar los pagos"
              message={errorPagos?.message || 'Revise su conexión e intente de nuevo.'}
              onRetry={quiet(refetchPagos)}
            />
          ) : loadingPagos ? (
            <div className="table-container space-y-3 p-4" role="status" aria-label="Cargando pagos">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-9 w-full" />
              ))}
            </div>
          ) : filteredPagos.length === 0 ? (
            <div className="table-container">
              <EmptyState
                icon={Receipt}
                title="No hay pagos en este estado"
                subtitle={
                  filtroEstadoPago === 'PENDIENTE_APROBACION'
                    ? 'Los pagos manuales registrados quedarán aquí hasta su aprobación.'
                    : 'Cambie el filtro para ver otros pagos.'
                }
              />
            </div>
          ) : (
            <div className="table-container max-h-[70vh] overflow-auto">
              <table className="data-table">
                <caption className="sr-only">Pagos manuales registrados</caption>
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Apartamento y residente</th>
                    <th scope="col">Concepto</th>
                    <th scope="col" className="text-right">Monto</th>
                    <th scope="col">Método y referencia</th>
                    <th scope="col">Fecha de pago</th>
                    <th scope="col">Soporte</th>
                    <th scope="col">Estado</th>
                    <th scope="col" className="text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredPagos.map((p) => {
                    const esPendiente = p.estado === 'PENDIENTE_APROBACION';
                    const esAprobado = p.estado === 'APROBADO';
                    const esRechazado = p.estado === 'RECHAZADO';
                    const meta = ESTADO_PAGO[p.estado] || { variant: 'secondary', label: p.estado };
                    return (
                      <tr key={p.idPago}>
                        <td className="tabular-nums font-semibold">#{p.idPago}</td>
                        <td className="min-w-[10rem]">
                          <div className="font-semibold">Apto {p.numeroApartamento || `Unidad #${p.idUnidad}`}</div>
                          <div className="text-xs text-text-muted">{p.nombreResidente}</div>
                        </td>
                        <td className="min-w-[10rem]">
                          <div>{p.conceptoCuota || 'Cuota de administración'}</div>
                          {p.periodoCuota && (
                            <div className="text-xs tabular-nums text-text-muted">Periodo: {p.periodoCuota}</div>
                          )}
                        </td>
                        <td className="whitespace-nowrap text-right text-sm font-bold tabular-nums">
                          {formatCurrency(p.montoTotal)}
                        </td>
                        <td>
                          <div>{p.metodoPago}</div>
                          {p.referenciaComprobante && (
                            <div className="text-xs tabular-nums text-text-muted">Ref: {p.referenciaComprobante}</div>
                          )}
                        </td>
                        <td className="whitespace-nowrap tabular-nums">{formatDate(p.fechaPago)}</td>
                        <td>
                          {p.comprobanteUrl ? (
                            <Button variant="outline" size="sm" onClick={() => setComprobanteModal(p)}>
                              Ver soporte
                            </Button>
                          ) : (
                            <span className="text-xs text-text-muted">Sin soporte</span>
                          )}
                        </td>
                        <td className="min-w-[9rem]">
                          <Badge variant={meta.variant}>{meta.label}</Badge>
                          {esRechazado && p.observaciones && (
                            <div className="mt-1 text-xs text-tint-red-fg">Motivo: {p.observaciones}</div>
                          )}
                        </td>
                        <td className="text-right">
                          {esPendiente && (
                            <div className="flex justify-end gap-1.5">
                              <Button size="sm" onClick={() => handleAprobarPago(p.idPago)} disabled={accionandoPago}>
                                Aprobar
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                className="text-destructive"
                                onClick={() => {
                                  setRechazoModal(p);
                                  setMotivoRechazo('');
                                }}
                                disabled={accionandoPago}
                              >
                                Rechazar
                              </Button>
                            </div>
                          )}
                          {esAprobado && p.nombreAprobador && (
                            <div className="text-xs text-text-muted">Por: {p.nombreAprobador}</div>
                          )}
                          {esRechazado && p.nombreRechazador && (
                            <div className="text-xs text-text-muted">Por: {p.nombreRechazador}</div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Payment receipt viewer */}
      <Modal
        open={!!comprobanteModal}
        onClose={() => setComprobanteModal(null)}
        title={`Comprobante de pago #${comprobanteModal?.idPago || ''} · Apto ${comprobanteModal?.numeroApartamento || ''}`}
        size="lg"
      >
        {comprobanteModal && (
          <div className="space-y-4">
            <dl className="grid grid-cols-1 gap-2 rounded-md border border-border bg-surface-container p-3 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-xs text-text-muted">Monto</dt>
                <dd className="font-bold tabular-nums">{formatCurrency(comprobanteModal.montoTotal)}</dd>
              </div>
              <div>
                <dt className="text-xs text-text-muted">Método</dt>
                <dd className="font-semibold">{comprobanteModal.metodoPago}</dd>
              </div>
              <div>
                <dt className="text-xs text-text-muted">Referencia</dt>
                <dd className="font-semibold tabular-nums">{comprobanteModal.referenciaComprobante || 'N/A'}</dd>
              </div>
            </dl>
            <div className="flex justify-center overflow-hidden rounded-md border border-border bg-surface-dim">
              {comprobanteModal.comprobanteUrl?.toLowerCase().endsWith('.pdf') ? (
                <iframe
                  src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                  title="Comprobante PDF"
                  className="h-[480px] w-full border-0"
                />
              ) : (
                <img
                  src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                  alt="Comprobante de pago"
                  className="mx-auto max-h-[480px] max-w-full object-contain"
                />
              )}
            </div>
            <div className="flex justify-end">
              <a
                href={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-10 items-center gap-1.5 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface no-underline transition-colors hover:border-primary/70 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                Descargar comprobante original
              </a>
            </div>
          </div>
        )}
      </Modal>

      {/* Payment rejection */}
      <Modal
        open={!!rechazoModal}
        onClose={() => {
          if (!accionandoPago) setRechazoModal(null);
        }}
        title={`Rechazar pago #${rechazoModal?.idPago || ''}`}
        footer={
          <>
            <Button variant="outline" onClick={() => setRechazoModal(null)} disabled={accionandoPago}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={handleRechazarPago}
              disabled={accionandoPago || !motivoRechazo.trim()}
              loading={accionandoPago}
            >
              {accionandoPago ? 'Rechazando…' : 'Confirmar rechazo'}
            </Button>
          </>
        }
      >
        {rechazoModal && (
          <div className="space-y-3">
            <p className="text-sm text-text-secondary">
              Indique la razón del rechazo del pago de{' '}
              <strong className="tabular-nums text-on-surface">{formatCurrency(rechazoModal.montoTotal)}</strong> del
              Apto <strong className="text-on-surface">{rechazoModal.numeroApartamento}</strong>. El residente será
              notificado.
            </p>
            <Textarea
              id="motivoRechazo"
              label="Motivo del rechazo"
              required
              rows={3}
              placeholder="Ej. El número de aprobación no coincide con el extracto de la cuenta bancaria"
              value={motivoRechazo}
              onChange={(e) => setMotivoRechazo(e.target.value)}
            />
          </div>
        )}
      </Modal>

      {/* Apartment balance detail */}
      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title={`Apto ${detalle?.numeroApartamento || ''}${detalle?.nombreResidente ? ` · ${detalle.nombreResidente}` : ''}`}
        size="lg"
      >
        {detalle && (
          <div className="space-y-5">
            <section>
              <h3 className="mb-2 text-[13px] font-bold text-on-surface">Cuotas</h3>
              <div className="table-container overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Periodo</th>
                      <th scope="col" className="text-right">Saldo</th>
                      <th scope="col">Vencimiento</th>
                      <th scope="col"><span className="sr-only">Acción</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {detalle.cuotas.map((c) => (
                      <tr key={c.idCuota || c.id}>
                        <td className="tabular-nums">{periodoLabel(c.anio, c.mes)}</td>
                        <td className="whitespace-nowrap text-right font-semibold tabular-nums">
                          {formatCurrency(saldoDe(c))}
                        </td>
                        <td className="whitespace-nowrap tabular-nums">{formatDate(c.fechaLimite)}</td>
                        <td className="text-right">
                          <Button size="sm" onClick={() => abrirPagoCuota(c)}>
                            Registrar pago
                          </Button>
                        </td>
                      </tr>
                    ))}
                    {detalle.cuotas.length === 0 && (
                      <tr>
                        <td colSpan={4} className="text-center text-text-muted">
                          Sin cuotas pendientes
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
            <section>
              <h3 className="mb-2 text-[13px] font-bold text-on-surface">Multas</h3>
              <div className="table-container overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Motivo</th>
                      <th scope="col" className="text-right">Monto</th>
                      <th scope="col">Fecha</th>
                      <th scope="col"><span className="sr-only">Acción</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {detalle.multas.map((m) => (
                      <tr key={m.idMulta}>
                        <td>{m.tipo}</td>
                        <td className="whitespace-nowrap text-right font-semibold tabular-nums">
                          {formatCurrency(m.monto)}
                        </td>
                        <td className="whitespace-nowrap tabular-nums">{formatDate(m.fechaCreacion)}</td>
                        <td className="text-right">
                          <Button size="sm" onClick={() => abrirPagoMulta(m)}>
                            Registrar pago
                          </Button>
                        </td>
                      </tr>
                    ))}
                    {detalle.multas.length === 0 && (
                      <tr>
                        <td colSpan={4} className="text-center text-text-muted">
                          Sin multas pendientes
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        )}
      </Modal>

      {/* Manual payment registration */}
      <Modal
        open={!!pagoModal}
        onClose={() => {
          setPagoModal(null);
          resetTouched();
        }}
        title={pagoModal?.tipo === 'cuota' ? 'Registrar pago de cuota' : 'Registrar pago de multa'}
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
            <Button onClick={confirmarPago} disabled={saving} loading={saving}>
              {saving ? 'Guardando…' : 'Confirmar'}
            </Button>
          </>
        }
      >
        <div className="grid gap-4">
          {pagoModal && (
            <p className="rounded-md border border-border bg-surface-container px-3 py-2 text-sm text-text-secondary">
              {pagoModal.tipo === 'cuota' ? (
                <>
                  Saldo pendiente de la cuota:{' '}
                  <strong className="tabular-nums text-on-surface">{formatCurrency(saldoDe(pagoModal.item))}</strong>
                </>
              ) : (
                <>
                  Valor de la multa:{' '}
                  <strong className="tabular-nums text-on-surface">{formatCurrency(pagoModal.item?.monto)}</strong>
                </>
              )}
            </p>
          )}
          {pagoModal?.tipo === 'cuota' && (
            <div className="grid gap-4 sm:grid-cols-2">
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
                className="tabular-nums"
                value={pagoForm.valor}
                onChange={(e) => setPagoForm((f) => ({ ...f, valor: formatMiles(e.target.value) }))}
                onBlur={() => touch('valor')}
                error={
                  fieldError(
                    'valor',
                    parseMiles(pagoForm.valor) <= 0
                      ? { ok: false, mensaje: 'El valor pagado debe ser mayor que 0' }
                      : pagoModal?.tipo === 'cuota' &&
                        parseMiles(pagoForm.valor) >
                          Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0)
                      ? {
                          ok: false,
                          mensaje: `No puede superar el saldo pendiente (${formatCurrency(
                            Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0)
                          )})`,
                        }
                      : { ok: true }
                  ) || undefined
                }
                required
              />
            </div>
          )}
          <Select
            id="metodo"
            label="Método de pago"
            value={pagoForm.metodo}
            onChange={(e) => setPagoForm((f) => ({ ...f, metodo: e.target.value }))}
          >
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
          </Select>
          {pagoModal?.tipo === 'cuota' && pagoForm.metodo === 'TRANSFERENCIA' && (
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
                    : {
                        ok: false,
                        mensaje: 'La referencia debe tener entre 4 y 50 caracteres (letras, números o guiones)',
                      }
                ) || undefined
              }
              required
            />
          )}
          {pagoModal?.tipo === 'cuota' && (
            <Input
              id="notas"
              label="Notas (opcional)"
              placeholder="Ej. Pago correspondiente al mes actual"
              value={pagoForm.notas}
              onChange={(e) => setPagoForm((f) => ({ ...f, notas: e.target.value }))}
            />
          )}
        </div>
      </Modal>
    </div>
  );
}
