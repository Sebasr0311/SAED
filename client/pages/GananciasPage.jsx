import { useState, useMemo, useEffect } from 'react';
import * as XLSX from 'xlsx';
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

// Export a .xlsx REAL con SheetJS: Excel abre sin warning de formato y con
// asociacion directa (formato nativo moderno). Estilos basicos aplicados por celda.
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
    const mes = (p.fecha || '').slice(0, 7);
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

  // Estilos reutilizables (SheetJS CE: fuentes, rellenos, alineacion, formatos)
  const estilos = {
    titulo: { font: { bold: true, sz: 16, color: { rgb: '0F2044' } } },
    subtitulo: { font: { sz: 11, color: { rgb: '5B6B85' } } },
    header: { font: { bold: true, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '0F2044' } } },
    headerMini: { font: { bold: true, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '2855A0' } } },
    seccion: { font: { bold: true, sz: 13, color: { rgb: '0F2044' } } },
    kpiLabel: { font: { sz: 10, color: { rgb: '8592A8' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiValue: { font: { bold: true, sz: 14, color: { rgb: '0F2044' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiVerde: { font: { bold: true, sz: 14, color: { rgb: '0F8A5F' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiRojo: { font: { bold: true, sz: 14, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    numero: { numFmt: '#,##0', alignment: { horizontal: 'right' } },
    numeroBold: { numFmt: '#,##0', alignment: { horizontal: 'right' }, font: { bold: true } },
    apto: { alignment: { horizontal: 'center' }, font: { bold: true } },
    muted: { font: { color: { rgb: '5B6B85' } } },
    badgeCuota: { font: { bold: true, color: { rgb: '0F8A5F' } }, fill: { fgColor: { rgb: 'E7F7EF' } }, alignment: { horizontal: 'center' } },
    badgeMulta: { font: { bold: true, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'FBEAE8' } }, alignment: { horizontal: 'center' } },
  };

  const S = (name, val) => ({ t: 's', v: val, s: estilos[name] || undefined });
  const N = (v, name) => ({ t: 'n', v: Number(v || 0), s: estilos[name] || undefined });
  const SC = (r, c, name) => ({ r, c, s: estilos[name] || undefined });

  const aoa = [];
  let r = 0;

  // Titulo y subtitulo (merge A:H)
  aoa[r] = []; aoa[r][0] = { t: 's', v: 'Reporte de Ganancias — Edificio Residencial', s: estilos.titulo };
  aoa[r + 1] = []; aoa[r + 1][0] = {
    t: 's',
    v: `Periodo: ${formatDate(fechaInicio)} — ${formatDate(fechaFin)} | Generado por SAED | ${pagos.length} transacciones · ${aptos} apartamentos`,
    s: estilos.subtitulo,
  };
  r += 3;

  // KPIs: una fila por KPI (label merge A:B, valor en C) — fiel a la plantilla
  const kpis = [
    ['Total General', total, 'kpiValue'],
    ['Cuotas', totalCuotas, 'kpiVerde'],
    ['Multas', totalMultas, 'kpiRojo'],
    ['Efectivo', totalEfectivo, 'kpiValue'],
    ['Transferencia', totalTransferencia, 'kpiValue'],
  ];
  const filasKpi = [];
  kpis.forEach(([label, valor, estilo]) => {
    aoa[r] = [];
    aoa[r][0] = { t: 's', v: label, s: estilos.kpiLabel };
    aoa[r][2] = { t: 'n', v: Number(valor || 0), s: estilos[estilo] };
    filasKpi.push(r);
    r++;
  });
  r++;

  // Seccion detalle (merge A:H como en la plantilla)
  const rSeccionDetalle = r;
  aoa[r] = [{ t: 's', v: 'Detalle de transacciones', s: estilos.seccion }];
  r++;
  aoa[r] = [
    S('header', '#'), S('header', 'Fecha'), S('header', 'Tipo'), S('header', 'Apto'),
    S('header', 'Residente'), S('header', 'Método'), S('header', 'Valor'), S('header', 'Descripción'),
  ];
  r++;
  pagos.forEach((p) => {
    const esMulta = (p.tipoPago || '').toUpperCase().includes('MULTA');
    aoa[r] = [
      N(p.id, 'muted'), { t: 's', v: formatDate(p.fecha) }, S(esMulta ? 'badgeMulta' : 'badgeCuota', p.tipoPago || 'CUOTA'),
      S('apto', String(p.apartamento || '')), { t: 's', v: p.residente || '' }, S('muted', p.metodo || ''),
      N(p.valor, 'numero'), S('muted', p.descripcion || ''),
    ];
    r++;
  });
  // Total
  aoa[r] = [S('header', 'Total'), {}, {}, {}, {}, {}, N(total, 'numeroBold'), {}];
  r += 2;

  // Resumen mensual + por apartamento lado a lado (merges A:C y E:G como la plantilla)
  const rSeccionResumenes = r;
  aoa[r] = []; aoa[r][0] = { t: 's', v: 'Resumen mensual', s: estilos.seccion };
  aoa[r][4] = { t: 's', v: 'Resumen por apartamento', s: estilos.seccion };
  r++;
  aoa[r] = [S('headerMini', 'Mes'), S('headerMini', 'Transacciones'), S('headerMini', 'Total'), {}, S('headerMini', 'Apto'), S('headerMini', 'Transacciones'), S('headerMini', 'Total')];
  r++;
  const filas = Math.max(mesesOrdenados.length, aptosOrdenados.length);
  for (let i = 0; i < filas; i++) {
    aoa[r] = [];
    if (mesesOrdenados[i]) {
      const [y, mm] = mesesOrdenados[i].split('-');
      const nombreMes = MESES[Number(mm) - 1] || mm;
      aoa[r][0] = { t: 's', v: `${nombreMes} ${y}` };
      aoa[r][1] = N(porMes[mesesOrdenados[i]].n, 'apto');
      aoa[r][2] = N(porMes[mesesOrdenados[i]].total, 'numeroBold');
    }
    if (aptosOrdenados[i]) {
      aoa[r][4] = S('apto', String(aptosOrdenados[i]));
      aoa[r][5] = N(porApto[aptosOrdenados[i]].n, 'apto');
      aoa[r][6] = N(porApto[aptosOrdenados[i]].total, 'numeroBold');
    }
    r++;
  }

  // Anchuras de columna
  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [
    { wch: 12 }, { wch: 22 }, { wch: 12 }, { wch: 12 },
    { wch: 26 }, { wch: 14 }, { wch: 14 }, { wch: 30 },
  ];
  // Merges fieles a la plantilla: titulo A1:H1, subtitulo A2:H2, labels KPI A:B,
  // seccion detalle A:H, resumen mensual A:C, resumen por apartamento E:G
  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: 7 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: 7 } },
    ...filasKpi.map((fr) => ({ s: { r: fr, c: 0 }, e: { r: fr, c: 1 } })),
    { s: { r: rSeccionDetalle, c: 0 }, e: { r: rSeccionDetalle, c: 7 } },
    { s: { r: rSeccionResumenes, c: 0 }, e: { r: rSeccionResumenes, c: 2 } },
    { s: { r: rSeccionResumenes, c: 4 }, e: { r: rSeccionResumenes, c: 6 } },
  ];

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Ganancias');
  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  const blob = new Blob([out], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `ganancias_${fechaInicio}_${fechaFin}.xlsx`;
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
