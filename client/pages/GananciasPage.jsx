import { useState, useMemo, useEffect } from 'react';
import * as XLSX from 'xlsx-js-style';
import { unzipSync, zipSync, strFromU8, strToU8 } from 'fflate';
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

// SheetJS CE lee freeze panes pero no los escribe: se inyectan en el XML de la hoja
// dentro del zip del .xlsx (fflate). ySplit = cantidad de filas congeladas.
function aplicarFreeze(buffer, ySplit) {
  if (!ySplit) return buffer;
  try {
    const zip = unzipSync(new Uint8Array(buffer));
    const ruta = zip['xl/worksheets/sheet1.xml'];
    if (!ruta) return buffer;
    const xml = strFromU8(ruta);
    const celda = 'A' + (ySplit + 1);
    const pane = `<pane xSplit="0" ySplit="${ySplit}" topLeftCell="${celda}" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="${celda}" sqref="${celda}"/>`;
    const limpio = xml.replace(/<selection[^>]*\/>/g, '');
    const nuevo = limpio.replace(/<sheetView([^>]*)>/, `<sheetView$1>${pane}`);
    zip['xl/worksheets/sheet1.xml'] = strToU8(nuevo);
    return zipSync(zip);
  } catch {
    return buffer;
  }
}

// Ajusta el ancho de cada columna al contenido real formateado (evita que Excel
// muestre ##### cuando el valor no cabe). Las celdas dentro de merges no cuentan
// (su texto se reparte en el rango). Usa el ancho del spec como minimo.
function autoFitCols(ws) {
  if (!ws['!ref']) return;
  try {
    const range = XLSX.utils.decode_range(ws['!ref']);
    const merged = new Set();
    (ws['!merges'] || []).forEach((m) => {
      for (let r = m.s.r; r <= m.e.r; r++) {
        for (let c = m.s.c; c <= m.e.c; c++) merged.add(r + ':' + c);
      }
    });
    const cols = (ws['!cols'] || []).map((c) => (c && c.wch) || 10);
    for (let R = range.s.r; R <= range.e.r; R++) {
      for (let C = range.s.c; C <= range.e.c; C++) {
        if (merged.has(R + ':' + C)) continue;
        const cell = ws[XLSX.utils.encode_cell({ r: R, c: C })];
        if (!cell || cell.v === undefined || cell.v === null) continue;
        let texto;
        const fmt = cell.z || (cell.s && cell.s.numFmt);
        if (cell.t === 'n' && fmt) {
          try { texto = XLSX.SSF.format(fmt, cell.v); } catch { texto = String(cell.v); }
        } else {
          texto = String(cell.v);
        }
        // Ancho real de Excel: chars * (fontSize/11) + padding (~3). Los valores KPI
        // usan font sz 14, que ocupa ~27% mas ancho que el Calibri 11 de referencia.
        const sz = (cell.s && cell.s.font && cell.s.font.sz) || 11;
        const wchNeeded = Math.ceil(texto.length * (sz / 11)) + 3;
        if (cols[C] < wchNeeded) cols[C] = wchNeeded;
      }
    }
    ws['!cols'] = cols.map((wch) => ({ wch }));
  } catch { /* mantiene !cols original ante cualquier error */ }
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

  // Resumen mensual (cronologico)
  const porMes = {};
  pagos.forEach((p) => {
    const mes = (p.fecha || '').slice(0, 7);
    if (!mes) return;
    porMes[mes] = porMes[mes] || { total: 0, n: 0 };
    porMes[mes].total += Number(p.valor || 0);
    porMes[mes].n += 1;
  });
  const mesesOrdenados = Object.keys(porMes).sort();

  // Resumen por apartamento (sort por total DESC segun spec)
  const porApto = {};
  pagos.forEach((p) => {
    const apto = p.apartamento || 'Sin apto';
    porApto[apto] = porApto[apto] || { total: 0, n: 0 };
    porApto[apto].total += Number(p.valor || 0);
    porApto[apto].n += 1;
  });
  const aptosOrdenados = Object.keys(porApto).sort((a, b) => porApto[b].total - porApto[a].total);

  const MESES = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];

  // Helpers
  const serieFecha = (str) => {
    if (!str) return null;
    const m = String(str).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/);
    if (!m) return null;
    let serial = (Date.UTC(+m[1], +m[2] - 1, +m[3]) - Date.UTC(1899, 11, 30)) / 86400000;
    if (m[4] !== undefined) serial += (+m[4] * 3600 + +m[5] * 60) / 86400;
    return serial;
  };
  const numOstr = (val) => {
    const s = String(val == null ? '' : val).trim();
    if (s === '') return '';
    const n = Number(s);
    return Number.isNaN(n) ? s : n;
  };
  // IMPORTANTE: cada celda recibe un CLON del estilo. El writer de xlsx-js-style
  // normaliza/muta los objetos de estilo; si se comparte el mismo objeto entre
  // celdas, los estilos quedan cruzados (label con font de valor, etc.).
  const clonarEstilo = (st) => (st ? JSON.parse(JSON.stringify(st)) : undefined);
  const S = (v, st) => ({ t: 's', v, s: clonarEstilo(st) });
  const N = (v, st) => ({ t: 'n', v: Number(v || 0), s: clonarEstilo(st) });
  const celdaApto = (val, st) => {
    const n = numOstr(val);
    return typeof n === 'number' ? { t: 'n', v: n, s: { ...st, numFmt: '0' } } : { t: 's', v: String(val || ''), s: st };
  };

  // Estilos segun spec
  const estilos = {
    titulo: { font: { bold: true, sz: 16, color: { rgb: '0F2044' }, name: 'Calibri' } },
    subtitulo: { font: { italic: true, sz: 10, color: { rgb: '5B6B85' }, name: 'Calibri' } },
    seccion: { font: { bold: true, sz: 13, color: { rgb: '0F2044' } } },
    kpiLabel: { font: { sz: 9, color: { rgb: '8592A8' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiFill: { fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiValue: (color, sz) => ({ font: { bold: true, sz, color: { rgb: color } }, fill: { fgColor: { rgb: 'F4F6FA' } }, numFmt: '$#,##0' }),
    headerTabla: { font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '0F2044' } } },
    headerSummary: { font: { bold: true, sz: 9, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '2855A0' } } },
    zebraOdd: { fill: { fgColor: { rgb: 'F7F9FC' } } },
    zebraEven: { fill: { fgColor: { rgb: 'FFFFFF' } } },
    bd: { border: { bottom: { style: 'thin', color: { rgb: 'E5E9F0' } } } },
    totalRow: { font: { bold: true, sz: 10, color: { rgb: '0F2044' } }, fill: { fgColor: { rgb: 'EEF1F6' } } },
    badgeCuota: { font: { bold: true, color: { rgb: '0F8A5F' } }, fill: { fgColor: { rgb: 'E7F7EF' } } },
    badgeMulta: { font: { bold: true, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'FBEAE8' } } },
    apto: { alignment: { horizontal: 'center' }, font: { bold: true } },
    fecha: { numFmt: 'dd/mm/yyyy' },
    moneda: { numFmt: '$#,##0', alignment: { horizontal: 'right' } },
    monedaBold: { numFmt: '$#,##0', alignment: { horizontal: 'right' }, font: { bold: true } },
    entero: { numFmt: '0', alignment: { horizontal: 'center' } },
  };

  const aoa = [];
  const merges = [];
  let r = 0;

  // Titulo y subtitulo
  aoa[r] = [S('Reporte de Ganancias — Edificio Residencial', estilos.titulo)];
  aoa[r + 1] = [S(`Periodo: ${formatDate(fechaInicio)} — ${formatDate(fechaFin)}  ·  Generado por SAED  ·  ${pagos.length} transacciones`, estilos.subtitulo)];
  merges.push({ s: { r: 0, c: 0 }, e: { r: 0, c: 7 } }, { s: { r: 1, c: 0 }, e: { r: 1, c: 7 } });
  r += 3;

  // KPIs grilla: fila 1 = Total General / Cuotas / Multas; fila 2 = Efectivo / Transferencia
  // El spec mergeaba A:B (label + valor en el rango) — un merge en Excel oculta el valor de
  // la 2da celda, así que label y valor van en columnas contiguas SIN merge (mismos col_start).
  const kpiCelda = (label, valor, color, sz, c0) => {
    aoa[r] = aoa[r] || [];
    aoa[r][c0] = S(label, estilos.kpiLabel);
    aoa[r][c0 + 1] = N(valor, estilos.kpiValue(color, sz));
  };
  kpiCelda('Total General', total, '0F2044', 14, 0);
  kpiCelda('Cuotas', totalCuotas, '0F8A5F', 14, 2);
  kpiCelda('Multas', totalMultas, 'C0392B', 14, 4);
  r++;
  kpiCelda('Efectivo', totalEfectivo, '1A2233', 13, 0);
  kpiCelda('Transferencia', totalTransferencia, '1A2233', 13, 2);
  r += 2;

  // Seccion detalle
  aoa[r] = [S('Detalle de transacciones', estilos.seccion)];
  merges.push({ s: { r, c: 0 }, e: { r, c: 7 } });
  r++;
  const rHeaderTabla = r;
  aoa[r] = [
    S('#', estilos.headerTabla), S('Fecha', estilos.headerTabla), S('Tipo', estilos.headerTabla), S('Apto', estilos.headerTabla),
    S('Residente', estilos.headerTabla), S('Método', estilos.headerTabla), S('Valor', estilos.headerTabla), S('Descripción', estilos.headerTabla),
  ];
  r++;
  pagos.forEach((p, i) => {
    const zebra = i % 2 === 0 ? estilos.zebraOdd : estilos.zebraEven;
    const base = { ...zebra, ...estilos.bd };
    const esMulta = (p.tipoPago || '').toUpperCase().includes('MULTA');
    aoa[r] = [
      { t: 'n', v: Number(p.id || 0), s: { ...estilos.entero, ...base } },
      { t: 'n', v: serieFecha(p.fecha), s: { ...estilos.fecha, ...base } },
      S(p.tipoPago || 'CUOTA', { ...base, ...(esMulta ? estilos.badgeMulta : estilos.badgeCuota) }),
      celdaApto(p.apartamento, { ...estilos.apto, ...base }),
      S(p.residente || '', base),
      S(p.metodo || '', base),
      N(p.valor, { ...estilos.moneda, ...base }),
      S(p.descripcion || '', base),
    ];
    r++;
  });
  // Total
  aoa[r] = [S('TOTAL', estilos.totalRow)];
  aoa[r][6] = N(total, { ...estilos.totalRow, ...estilos.monedaBold });
  merges.push({ s: { r, c: 0 }, e: { r, c: 5 } });
  r += 2;

  // Resumen mensual + por apartamento lado a lado
  const rSeccionResumenes = r;
  aoa[r] = [S('Resumen mensual', estilos.seccion)];
  aoa[r][4] = S('Resumen por apartamento', estilos.seccion);
  merges.push(
    { s: { r, c: 0 }, e: { r, c: 2 } },
    { s: { r, c: 4 }, e: { r, c: 6 } },
  );
  r++;
  aoa[r] = [
    S('Mes', estilos.headerSummary), S('Transacciones', estilos.headerSummary), S('Total', estilos.headerSummary), null,
    S('Apto', estilos.headerSummary), S('Transacciones', estilos.headerSummary), S('Total', estilos.headerSummary),
  ];
  r++;
  const filas = Math.max(mesesOrdenados.length, aptosOrdenados.length);
  for (let i = 0; i < filas; i++) {
    aoa[r] = [];
    if (mesesOrdenados[i]) {
      const [y, mm] = mesesOrdenados[i].split('-');
      const nombreMes = MESES[Number(mm) - 1] || mm;
      aoa[r][0] = S(`${nombreMes} ${y}`, estilos.bd);
      aoa[r][1] = N(porMes[mesesOrdenados[i]].n, { ...estilos.entero, ...estilos.bd });
      aoa[r][2] = N(porMes[mesesOrdenados[i]].total, { ...estilos.monedaBold, ...estilos.bd });
    }
    if (aptosOrdenados[i]) {
      aoa[r][4] = celdaApto(aptosOrdenados[i], { ...estilos.apto, ...estilos.bd });
      aoa[r][5] = N(porApto[aptosOrdenados[i]].n, { ...estilos.entero, ...estilos.bd });
      aoa[r][6] = N(porApto[aptosOrdenados[i]].total, { ...estilos.monedaBold, ...estilos.bd });
    }
    r++;
  }

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [{ wch: 8 }, { wch: 12 }, { wch: 10 }, { wch: 12 }, { wch: 26 }, { wch: 16 }, { wch: 14 }, { wch: 22 }];
  ws['!merges'] = merges;
  autoFitCols(ws);
  ws['!freeze'] = { xSplit: 0, ySplit: rHeaderTabla + 1 };

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Ganancias');
  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array', cellStyles: true });
  const blob = new Blob([aplicarFreeze(out, rHeaderTabla + 1)], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
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
            <p style={{ color: 'var(--error)', fontSize: '12px', width: '100%' }}>{fechaError}</p>
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
