import { useState, useMemo, useEffect } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { validarFechas } from '../lib/validation.js';
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

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function exportarExcel(pagos, fechaInicio, fechaFin) {
  const total = pagos.reduce((s, p) => s + Number(p.valor || 0), 0);
  const cuotas = pagos.filter((p) => (p.tipoPago || 'CUOTA').toUpperCase().includes('CUOTA'));
  const multas = pagos.filter((p) => (p.tipoPago || '').toUpperCase().includes('MULTA'));
  const totalCuotas = cuotas.reduce((s, p) => s + Number(p.valor || 0), 0);
  const totalMultas = multas.reduce((s, p) => s + Number(p.valor || 0), 0);
  const totalEfectivo = pagos.filter((p) => (p.metodo || '').toUpperCase() === 'EFECTIVO').reduce((s, p) => s + Number(p.valor || 0), 0);
  const totalTransferencia = pagos.filter((p) => (p.metodo || '').toUpperCase() === 'TRANSFERENCIA').reduce((s, p) => s + Number(p.valor || 0), 0);
  const aptos = new Set(pagos.map((p) => p.apartamento).filter(Boolean)).size;

  // Resumen mensual
  const porMes = {};
  pagos.forEach((p) => {
    const mes = (p.fecha || '').slice(0, 7); // YYYY-MM
    if (!mes) return;
    porMes[mes] = porMes[mes] || { total: 0, n: 0 };
    porMes[mes].total += Number(p.valor || 0);
    porMes[mes].n += 1;
  });
  const mesesOrdenados = Object.keys(porMes).sort();

  // Resumen por apartamento
  const porApto = {};
  pagos.forEach((p) => {
    const apto = p.apartamento || 'Sin apto';
    porApto[apto] = porApto[apto] || { total: 0, n: 0 };
    porApto[apto].total += Number(p.valor || 0);
    porApto[apto].n += 1;
  });
  const aptosOrdenados = Object.keys(porApto).sort((a, b) => Number(a) - Number(b) || String(a).localeCompare(String(b)));

  const filaDetalle = (p, fondo) => `
<tr style="background:${fondo};">
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;color:#5B6B85;">${esc(p.id)}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(formatDate(p.fecha))}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;"><span style="background:${(p.tipoPago || '').toUpperCase().includes('MULTA') ? '#FBEAE8;color:#C0392B' : '#E7F7EF;color:#0F8A5F'};padding:3px 10px;border-radius:10px;font-size:12px;font-weight:700;">${esc(p.tipoPago || 'CUOTA')}</span></td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;text-align:center;font-weight:600;">${esc(p.apartamento)}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(p.residente)}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;color:#5B6B85;">${esc(p.metodo)}</td>
<td style='mso-number-format:"\\#\\,\\#\\#0";text-align:right;padding:8px 12px;border-bottom:1px solid #E5E9F0;'>${Number(p.valor || 0)}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;color:#5B6B85;">${esc(p.descripcion)}</td>
</tr>`;

  const filasDetalle = pagos
    .map((p, i) => filaDetalle(p, i % 2 === 0 ? '#FFFFFF' : '#F7F9FC'))
    .join('');

  const MESES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
  const filasMensual = mesesOrdenados
    .map((m) => {
      const [y, mm] = m.split('-');
      const nombreMes = MESES[Number(mm) - 1] || mm;
      return `
<tr>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;">${nombreMes} ${y}</td>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;">${porMes[m].n}</td>
<td style='mso-number-format:"\\#\\,\\#\\#0";text-align:right;padding:8px 12px;border-bottom:1px solid #E5E9F0;font-weight:700;'>${porMes[m].total}</td>
</tr>`;
    })
    .join('');

  const filasApto = aptosOrdenados
    .map((a) => `
<tr>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;font-weight:600;">${esc(a)}</td>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;">${porApto[a].n}</td>
<td style='mso-number-format:"\\#\\,\\#\\#0";text-align:right;padding:8px 12px;border-bottom:1px solid #E5E9F0;font-weight:700;'>${porApto[a].total}</td>
</tr>`
    )
    .join('');

  const d1 = formatDate(fechaInicio);
  const d2 = formatDate(fechaFin);

  let xls = `<!DOCTYPE html>
<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta charset="UTF-8">
<!--[if gte mso 9]>
<xml>
<x:ExcelWorkbook>
<x:ExcelWorksheets>
<x:ExcelWorksheet>
<x:Name>Ganancias</x:Name>
<x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions>
</x:ExcelWorksheet>
</x:ExcelWorksheets>
</x:ExcelWorkbook>
</xml>
<![endif]-->
<style>
  body { font-family: Calibri, Arial, sans-serif; color:#1A2233; margin:0; padding:24px; background:#EEF1F6; }
  .sheet { max-width: 1100px; margin: 0 auto; background:#fff; }
  h1 { font-size: 22px; margin:0 0 4px 0; color:#0F2044; }
  .subtitle { color:#5B6B85; font-size:13px; margin-bottom:20px; }
  table { border-collapse: collapse; width:100%; }
  .kpi-table td { padding:16px 20px; }
  .kpi-label { font-size:11px; text-transform:uppercase; letter-spacing:.04em; color:#8592A8; }
  .kpi-value { font-size:20px; font-weight:700; color:#0F2044; }
  .section-title { font-size:15px; font-weight:700; color:#0F2044; margin:28px 0 10px 0; padding-top:10px; border-top:2px solid #EEF1F6;}
  thead th { background:#0F2044; color:#fff; text-align:left; padding:10px 12px; font-size:12px; text-transform:uppercase; letter-spacing:.03em; }
  tfoot td { background:#0F2044; color:#fff; font-weight:700; padding:10px 12px; }
  .mini thead th { background:#2855A0; }
  .footer-note { color:#8592A8; font-size:11px; margin-top:24px; }
</style>
</head>
<body>
<div class="sheet">
  <h1>Reporte de Ganancias — Edificio Residencial</h1>
  <div class="subtitle">Periodo: ${d1} — ${d2} &nbsp;|&nbsp; Generado por SAED &nbsp;|&nbsp; ${pagos.length} transacciones · ${aptos} apartamentos</div>

  <table class="kpi-table" style="border:1px solid #E5E9F0;">
    <tr>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Total General</div><div class="kpi-value" style="color:#0F2044;">${formatCurrency(total)}</div></td>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Cuotas</div><div class="kpi-value" style="color:#0F8A5F;">${formatCurrency(totalCuotas)}</div></td>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Multas</div><div class="kpi-value" style="color:#C0392B;">${formatCurrency(totalMultas)}</div></td>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Efectivo</div><div class="kpi-value" style="color:#1A2233;">${formatCurrency(totalEfectivo)}</div></td>
      <td><div class="kpi-label">Transferencia</div><div class="kpi-value" style="color:#1A2233;">${formatCurrency(totalTransferencia)}</div></td>
    </tr>
  </table>

  <div class="section-title">Detalle de transacciones</div>
  <table>
    <thead>
      <tr><th>#</th><th>Fecha</th><th>Tipo</th><th>Apto</th><th>Residente</th><th>Método</th><th style="text-align:right;">Valor</th><th>Descripción</th></tr>
    </thead>
    <tbody>
      ${filasDetalle}
    </tbody>
    <tfoot>
      <tr><td colspan="6">Total</td><td style='mso-number-format:"\\#\\,\\#\\#0";text-align:right;'>${total}</td><td></td></tr>
    </tfoot>
  </table>

  <div style="display:flex;gap:24px;">
    <div style="flex:1;">
      <div class="section-title">Resumen mensual</div>
      <table class="mini">
        <thead><tr><th>Mes</th><th style="text-align:center;">Transacciones</th><th style="text-align:right;">Total</th></tr></thead>
        <tbody>
          ${filasMensual}
        </tbody>
      </table>
    </div>
    <div style="flex:1;">
      <div class="section-title">Resumen por apartamento</div>
      <table class="mini">
        <thead><tr><th style="text-align:center;">Apto</th><th style="text-align:center;">Transacciones</th><th style="text-align:right;">Total</th></tr></thead>
        <tbody>
          ${filasApto}
        </tbody>
      </table>
    </div>
  </div>

  <div class="footer-note">Los valores están escritos como números editables (formato Excel) — puedes sumar, filtrar o dar formato adicional directamente en la hoja.</div>
</div>
</body>
</html>`;

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
  const [fechaError, setFechaError] = useState('');

  useEffect(() => {
    const r = validarFechas({ fechaInicio, fechaFin });
    setFechaError(r.ok ? '' : r.mensaje);
  }, [fechaInicio, fechaFin]);

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
    { key: 'metodo', label: 'MÃ©todo' },
    { key: 'valor', label: 'Valor', render: (r) => formatCurrency(r.valor) },
    { key: 'descripcion', label: 'DescripciÃ³n' },
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
          {fechaError && (
            <p style={{ color: '#e11d48', fontSize: '12px', width: '100%' }}>{fechaError}</p>
          )}
          <Input
            id="search" aria-label="Buscar"
            label="BÃºsqueda rÃ¡pida"
            placeholder="Apto, residente, mÃ©todo..."
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
