import { useState, useMemo } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, todayStr } from '../lib/utils.js';

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

function exportarExcel(pagos, fechaInicio, fechaFin) {
  const total = pagos.reduce((s, p) => s + Number(p.valor || 0), 0);
  let xls =
    '<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">' +
    '<head><meta charset="UTF-8"><style>table{width:100%;border-collapse:collapse}th{background:#0F2044;color:#fff}</style></head><body><table>' +
    '<thead><tr><th>#</th><th>Fecha</th><th>Tipo</th><th>Apartamento</th><th>Residente</th><th>Método</th><th>Valor</th><th>Descripción</th></tr></thead><tbody>';
  pagos.forEach((p) => {
    xls += `<tr><td>${p.id}</td><td>${p.fecha || ''}</td><td>${p.tipoPago || 'Cuota'}</td><td>${p.apartamento || ''}</td><td>${p.residente || ''}</td><td>${p.metodo || ''}</td><td>${p.valor ?? 0}</td><td>${p.descripcion || ''}</td></tr>`;
  });
  xls += `</tbody><tfoot><tr><td colspan="7">Total</td><td>$${total.toLocaleString('es-CO')}</td></tr></tfoot></table></body></html>`;
  const blob = new Blob([xls], { type: 'application/vnd.ms-excel' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `ganancias_${fechaInicio}_${fechaFin}.xls`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export default function GananciasPage() {
  const [toast, setToast] = useState(null);
  const [search, setSearch] = useState('');
  const [fechaInicio, setFechaInicio] = useState(`${new Date().getFullYear()}-01-01`);
  const [fechaFin, setFechaFin] = useState(todayStr());

  const { data: pagos, loading } = useFetch(() => api.get('/pagos/registrados'), []);
  const all = pagos?.items || pagos || [];

  const filtrados = useMemo(() => {
    return all.filter((p) => {
      const fecha = (p.fecha || '').slice(0, 10);
      if (fecha && (fecha < fechaInicio || fecha > fechaFin)) return false;
      if (!search) return true;
      const term = search.toLowerCase();
      return [p.apartamento, p.residente, p.metodo, p.tipoPago]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(term));
    });
  }, [all, search, fechaInicio, fechaFin]);

  const stats = useMemo(() => {
    const totalPagos = filtrados.length;
    const totalIngresos = filtrados.reduce((s, p) => s + Number(p.valor || 0), 0);
    const cuotas = filtrados.filter((p) => (p.tipoPago || 'CUOTA').toUpperCase().includes('CUOTA')).length;
    const multas = filtrados.filter((p) => (p.tipoPago || '').toUpperCase().includes('MULTA')).length;
    return { totalPagos, totalIngresos, cuotas, multas };
  }, [filtrados]);

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'fecha', label: 'Fecha', render: (r) => formatDate(r.fecha) },
    { key: 'tipoPago', label: 'Tipo', render: (r) => r.tipoPago || 'Cuota' },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'residente', label: 'Residente' },
    { key: 'metodo', label: 'Método' },
    { key: 'valor', label: 'Valor', render: (r) => formatCurrency(r.valor) },
    { key: 'descripcion', label: 'Descripción' },
  ];

  return (
    <div>
      <PageHeader
        title="Ganancias"
        subtitle="Ingresos del edificio"
        action={
          <Button variant="outline" onClick={() => exportarExcel(filtrados, fechaInicio, fechaFin)}>
            Exportar Excel
          </Button>
        }
      />

      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'flex-end' }}>
          <Input
            id="fechaInicio"
            label="Desde"
            type="date"
            value={fechaInicio}
            onChange={(e) => setFechaInicio(e.target.value)}
          />
          <Input
            id="fechaFin"
            label="Hasta"
            type="date"
            value={fechaFin}
            onChange={(e) => setFechaFin(e.target.value)}
          />
          <Input
            id="search"
            label="Búsqueda rápida"
            placeholder="Apto, residente, método..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <Stat icon="receipt_long" value={stats.totalPagos} label="Total Pagos" color="primary" />
        <Stat icon="payments" value={formatCurrency(stats.totalIngresos)} label="Ingresos Totales" color="green" />
        <Stat icon="description" value={stats.cuotas} label="Cuotas" color="blue" />
        <Stat icon="gavel" value={stats.multas} label="Multas" color="amber" />
      </div>

      <DataTable
        columns={columns}
        rows={filtrados}
        loading={loading}
        empty="No hay ganancias en el rango seleccionado"
        keyField="id"
      />
      <Toast toast={toast} />
    </div>
  );
}
