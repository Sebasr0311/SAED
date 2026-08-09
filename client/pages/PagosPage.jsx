import { useMemo, useRef, useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, todayStr, formatMiles, parseMiles, periodoLabel } from '../lib/utils.js';

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

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
  const [toast, setToast] = useState(null);
  const [search, setSearch] = useState('');
  const [detalle, setDetalle] = useState(null);
  const [pagoModal, setPagoModal] = useState(null); // { tipo: 'cuota'|'multa', item }
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

  const loading = loadingCuotas || loadingMultas;

  const fetchError = errorCuotas || errorMultas;

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
    setPagoModal({ tipo: 'cuota', item: cuota });
    const saldo = Number(cuota.saldoPendiente ?? cuota.valorTotal ?? 0);
    setPagoForm({
      fecha: todayStr(),
      valor: saldo > 0 ? formatMiles(saldo) : '',
      metodo: 'EFECTIVO',
      referencia: '',
      notas: '',
    });
  }
  function abrirPagoMulta(multa) {
    setPagoModal({ tipo: 'multa', item: multa });
    setPagoForm({ fecha: todayStr(), valor: '', metodo: 'EFECTIVO', referencia: '', notas: '' });
  }

  async function confirmarPago() {
    if (savingRef.current) return; // doble submit
    if (!pagoModal) return;
    const valor = parseMiles(pagoForm.valor);
    if (valor <= 0) {
      setToast({ message: 'El valor pagado debe ser mayor que 0', type: 'error' });
      return;
    }
    if (pagoModal.tipo === 'cuota') {
      const saldo = Number(pagoModal.item.saldoPendiente ?? pagoModal.item.valorTotal ?? 0);
      if (valor > saldo) {
        setToast({ message: `El valor pagado no puede superar el saldo pendiente (${formatCurrency(saldo)})`, type: 'error' });
        return;
      }
    }
    if (pagoModal.tipo === 'cuota' && pagoForm.metodo === 'TRANSFERENCIA') {
      const ref = pagoForm.referencia.trim();
      if (!/^[A-Za-z0-9-]{4,50}$/.test(ref)) {
        setToast({ message: 'La referencia debe tener entre 4 y 50 caracteres (letras, números o guiones)', type: 'error' });
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
      } else {
        await api.put(`/multas/${pagoModal.item.idMulta}/pagar`, { metodoPago: pagoForm.metodo });
      }
      setToast({ message: 'Pago registrado', type: 'success' });
      setPagoModal(null);
      refetchCuotas();
      refetchMultas();
      if (detalle) {
        setDetalle(null);
      }
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Pagos"
        subtitle="Cuotas de arriendo y multas"
        action={
          <Input
            id="search" aria-label="Buscar"
            placeholder="Buscar apto o residente..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />
      <div className="card-grid-3" style={{ marginBottom: '20px' }}>
        <Stat icon="receipt_long" value={formatCurrency(kpis.cuotasPendientes)} label="Cuotas pendientes" color="primary" />
        <Stat icon="gavel" value={formatCurrency(kpis.multasPendientes)} label="Multas pendientes" color="amber" />
        <Stat icon="apartment" value={kpis.aptosConSaldo} label="Aptos con saldo" color="blue" />
      </div>
      <DataTable
        columns={columns}
        rows={residentes}
        loading={loading}
        empty="No hay pagos pendientes"
        error={fetchError?.message}
        keyField="numeroApartamento"
        onRowClick={setDetalle}
      />

      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title={`Apto ${detalle?.numeroApartamento || ''} â€” ${detalle?.nombreResidente || ''}`}
        size="lg"
      >
        {detalle && (
          <>
                <h4 className="mb-2 text-[13px] font-bold">Cuotas</h4>
            <div className="table-container" style={{ marginBottom: '16px' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Periodo</th>
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
        onClose={() => setPagoModal(null)}
        title={pagoModal?.tipo === 'cuota' ? 'Registrar Pago de Cuota' : 'Registrar Pago de Multa'}
        footer={
          <>
            <Button variant="outline" onClick={() => setPagoModal(null)} disabled={saving}>
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
              />
              <Input
                id="valor"
                label="Valor pagado"
                value={pagoForm.valor}
                onChange={(e) => setPagoForm((f) => ({ ...f, valor: formatMiles(e.target.value) }))}
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
              label="Referencia"
              value={pagoForm.referencia}
              onChange={(e) => setPagoForm((f) => ({ ...f, referencia: e.target.value }))}
            />
          </div>
        )}
        {pagoModal?.tipo === 'cuota' && (
          <div className="form-group">
            <Input
              id="notas"
              label="Notas (opcional)"
              value={pagoForm.notas}
              onChange={(e) => setPagoForm((f) => ({ ...f, notas: e.target.value }))}
            />
          </div>
        )}
      </Modal>

      <Toast toast={toast} />
    </div>
  );
}
