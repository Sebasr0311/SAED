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

// Escape para atributos XML (además del HTML).
function escA(s) {
  return esc(s).replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

// Export a SpreadsheetML 2003 (XML nativo de Excel): con extensión .xls Excel lo
// abre SIN el warning "el formato y la extensión no coinciden" (a diferencia del
// HTML con .xls). Los valores numéricos quedan editables y sumables.
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

  const MESES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];

  // Helpers SpreadsheetML
  const cell = (value, opts = {}) => {
    const { type = 'String', style, merge } = opts;
    const attrs = [];
    if (style) attrs.push(`ss:StyleID="${style}"`);
    if (merge) attrs.push(`ss:MergeAcross="${merge}"`);
    return `<Cell${attrs.length ? ' ' + attrs.join(' ') : ''}><Data ss:Type="${type}">${esc(value)}</Data></Cell>`;
  };
  const cellNum = (value, style) =>
    `<Cell${style ? ` ss:StyleID="${style}"` : ''}><Data ss:Type="Number">${Number(value || 0)}</Data></Cell>`;

  // Estilos
  const styles = `
    <Style ss:ID="Default" ss:Name="Normal">
      <Alignment ss:Vertical="Center"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#1A2233"/>
      <Borders>
        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E5E9F0"/>
      </Borders>
    </Style>
    <Style ss:ID="Titulo">
      <Font ss:FontName="Calibri" ss:Size="16" ss:Bold="1" ss:Color="#0F2044"/>
    </Style>
    <Style ss:ID="Subtitulo">
      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#5B6B85"/>
    </Style>
    <Style ss:ID="Header">
      <Alignment ss:Vertical="Center"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
      <Interior ss:Color="#0F2044" ss:Pattern="Solid"/>
      <Borders>
        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#0F2044"/>
      </Borders>
    </Style>
    <Style ss:ID="HeaderMini">
      <Alignment ss:Vertical="Center"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
      <Interior ss:Color="#2855A0" ss:Pattern="Solid"/>
      <Borders>
        <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#2855A0"/>
      </Borders>
    </Style>
    <Style ss:ID="KpiLabel">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Color="#8592A8"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
      <Borders>
        <Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E5E9F0"/>
        <Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E5E9F0"/>
      </Borders>
    </Style>
    <Style ss:ID="KpiValue">
      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#0F2044"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
      <Borders>
        <Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1" ss:Color="#E5E9F0"/>
      </Borders>
    </Style>
    <Style ss:ID="KpiVerde">
      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#0F8A5F"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="KpiRojo">
      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#C0392B"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="Seccion">
      <Font ss:FontName="Calibri" ss:Size="13" ss:Bold="1" ss:Color="#0F2044"/>
    </Style>
    <Style ss:ID="Numero">
      <NumberFormat ss:Format="#,##0"/>
      <Alignment ss:Horizontal="Right"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#1A2233"/>
    </Style>
    <Style ss:ID="NumeroBold">
      <NumberFormat ss:Format="#,##0"/>
      <Alignment ss:Horizontal="Right"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#0F2044"/>
    </Style>
    <Style ss:ID="BadgeCuota">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#0F8A5F"/>
      <Interior ss:Color="#E7F7EF" ss:Pattern="Solid"/>
      <Alignment ss:Horizontal="Center"/>
    </Style>
    <Style ss:ID="BadgeMulta">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#C0392B"/>
      <Interior ss:Color="#FBEAE8" ss:Pattern="Solid"/>
      <Alignment ss:Horizontal="Center"/>
    </Style>
    <Style ss:ID="Apto">
      <Alignment ss:Horizontal="Center"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#1A2233"/>
    </Style>
    <Style ss:ID="Muted">
      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#5B6B85"/>
    </Style>`;

  // Filas de detalle
  const filasDetalle = pagos
    .map((p) => {
      const esMulta = (p.tipoPago || '').toUpperCase().includes('MULTA');
      const badge = esMulta ? 'BadgeMulta' : 'BadgeCuota';
      return `<Row>
        ${cell(esc(p.id), { style: 'Muted' })}
        ${cell(esc(formatDate(p.fecha)))}
        ${cell(esc(p.tipoPago || 'CUOTA'), { style: badge })}
        ${cell(esc(p.apartamento), { style: 'Apto' })}
        ${cell(esc(p.residente))}
        ${cell(esc(p.metodo), { style: 'Muted' })}
        ${cellNum(p.valor, 'Numero')}
        ${cell(esc(p.descripcion), { style: 'Muted' })}
      </Row>`;
    })
    .join('');

  // Resumen mensual
  const filasMensual = mesesOrdenados
    .map((m) => {
      const [y, mm] = m.split('-');
      const nombreMes = MESES[Number(mm) - 1] || mm;
      return `<Row>
        ${cell(`${nombreMes} ${y}`)}
        ${cell(String(porMes[m].n), { style: 'Apto' })}
        ${cellNum(porMes[m].total, 'NumeroBold')}
      </Row>`;
    })
    .join('');

  // Resumen por apartamento
  const filasApto = aptosOrdenados
    .map((a) => `<Row>
      ${cell(esc(a), { style: 'Apto' })}
      ${cell(String(porApto[a].n), { style: 'Apto' })}
      ${cellNum(porApto[a].total, 'NumeroBold')}
    </Row>`)
    .join('');

  // KPIs
  const kpi = (label, value, style) => `<Row>
    ${cell(esc(label), { style: 'KpiLabel', merge: 1 })}
    ${cell('')}
    ${cell(esc(value), { style })}
  </Row>`;

  const filasKpi = [
    kpi('Total General', formatCurrency(total), 'KpiValue'),
    kpi('Cuotas', formatCurrency(totalCuotas), 'KpiVerde'),
    kpi('Multas', formatCurrency(totalMultas), 'KpiRojo'),
    kpi('Efectivo', formatCurrency(totalEfectivo), 'KpiValue'),
    kpi('Transferencia', formatCurrency(totalTransferencia), 'KpiValue'),
  ].join('');

  const d1 = formatDate(fechaInicio);
  const d2 = formatDate(fechaFin);

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
  <Styles>${styles}
  </Styles>
  <Worksheet ss:Name="Ganancias">
    <Table>
      <Column ss:Width="90"/>
      <Column ss:Width="180"/>
      <Column ss:Width="120"/>
      <Column ss:Width="80"/>
      <Column ss:Width="180"/>
      <Column ss:Width="110"/>
      <Column ss:Width="90"/>
      <Column ss:Width="160"/>
      <Row ss:Height="30">
        ${cell('Reporte de Ganancias — Edificio Residencial', { style: 'Titulo', merge: 7 })}
        ${Array(7).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell(`Periodo: ${d1} — ${d2} | Generado por SAED | ${pagos.length} transacciones · ${aptos} apartamentos`, { style: 'Subtitulo', merge: 7 })}
        ${Array(7).fill('').map(() => cell('')).join('')}
      </Row>
      <Row ss:Height="20"/>
      ${filasKpi}
      <Row ss:Height="20"/>
      <Row>
        ${cell('Detalle de transacciones', { style: 'Seccion', merge: 7 })}
        ${Array(7).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell('#', { style: 'Header' })}
        ${cell('Fecha', { style: 'Header' })}
        ${cell('Tipo', { style: 'Header' })}
        ${cell('Apto', { style: 'Header' })}
        ${cell('Residente', { style: 'Header' })}
        ${cell('Método', { style: 'Header' })}
        ${cell('Valor', { style: 'Header' })}
        ${cell('Descripción', { style: 'Header' })}
      </Row>
      ${filasDetalle}
      <Row>
        ${cell('Total', { style: 'Header', merge: 5 })}
        ${Array(5).fill('').map(() => cell('')).join('')}
        ${cellNum(total, 'Header')}
        ${cell('')}
      </Row>
      <Row ss:Height="20"/>
      <Row>
        ${cell('Resumen mensual', { style: 'Seccion', merge: 2 })}
        ${Array(2).fill('').map(() => cell('')).join('')}
        ${cell('Resumen por apartamento', { style: 'Seccion', merge: 2 })}
        ${Array(2).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell('Mes', { style: 'HeaderMini' })}
        ${cell('Transacciones', { style: 'HeaderMini' })}
        ${cell('Total', { style: 'HeaderMini' })}
        ${cell('Apto', { style: 'HeaderMini' })}
        ${cell('Transacciones', { style: 'HeaderMini' })}
        ${cell('Total', { style: 'HeaderMini' })}
        ${cell('')}
        ${cell('')}
      </Row>
      ${mesesOrdenados.map((m, i) => {
        const [y, mm] = m.split('-');
        const nombreMes = MESES[Number(mm) - 1] || mm;
        const apto = aptosOrdenados[i];
        const row = `<Row>
          ${cell(`${nombreMes} ${y}`)}
          ${cell(String(porMes[m].n), { style: 'Apto' })}
          ${cellNum(porMes[m].total, 'NumeroBold')}
          ${apto ? cell(esc(apto), { style: 'Apto' }) : cell('')}
          ${apto ? cell(String(porApto[apto].n), { style: 'Apto' }) : cell('')}
          ${apto ? cellNum(porApto[apto].total, 'NumeroBold') : cell('')}
          ${cell('')}
          ${cell('')}
        </Row>`;
        return row;
      }).join('')}
    </Table>
  </Worksheet>
</Workbook>`;

  // BOM UTF-8: sin él, Excel interpreta el .xls como Windows-1252 y corrompe los
  // acentos (archivo "dañado" o texto ilegible). El BOM fuerza detección UTF-8.
  const blob = new Blob(['\uFEFF' + xml], { type: 'application/vnd.ms-excel' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  // Extensión .xml (SpreadsheetML 2003): Excel 2016+ muestra el warning "formato y
  // extensión no coinciden" si el XML se guarda como .xls. Con .xml + progid
  // Excel.Sheet, Windows lo abre con Excel directamente y sin advertencia.
  link.download = `ganancias_${fechaInicio}_${fechaFin}.xml`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

const PAGE_SIZE = 10;

export default function GananciasPage() {
  const [toast, setToast] = useState(null);
  const [search, setSearch] = useState('');
  const [fechaInicio, setFechaInicio] = useState(`${new Date().getFullYear()}-01-01`);
  const [fechaFin, setFechaFin] = useState(todayStr());
  const [fechaError, setFechaError] = useState('');
  const [page, setPage] = useState(1);

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

  // Reinicia a la primera página cuando cambian los filtros (patrón de v0).
  useEffect(() => {
    setPage(1);
  }, [search, fechaInicio, fechaFin]);

  const totalPaginas = Math.max(1, Math.ceil(filtrados.length / PAGE_SIZE));
  const paginaSegura = Math.min(page, totalPaginas);
  const filasDePagina = filtrados.slice((paginaSegura - 1) * PAGE_SIZE, paginaSegura * PAGE_SIZE);

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
          {fechaError && (
            <p style={{ color: '#e11d48', fontSize: '12px', width: '100%' }}>{fechaError}</p>
          )}
          <Input
            id="search" aria-label="Buscar"
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
        rows={filasDePagina}
        loading={loading}
        empty="No hay ganancias en el rango seleccionado"
        keyField="id"
      />

      {!loading && filtrados.length > PAGE_SIZE && (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '16px' }}>
          <Button
            variant="outline"
            size="sm"
            disabled={paginaSegura <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
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
            onClick={() => setPage((p) => Math.min(totalPaginas, p + 1))}
          >
            Siguiente →
          </Button>
        </div>
      )}
      <Toast toast={toast} />
    </div>
  );
}
