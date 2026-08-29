import { useState } from 'react';
import { toast } from 'sonner';
import { unzipSync, zipSync, strFromU8, strToU8 } from 'fflate';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, todayStr, imageSrc } from '../lib/utils.js';

// Export a .xlsx REAL con SheetJS: Excel abre sin warning de formato y con
// asociacion directa (formato nativo moderno). Estilos basicos aplicados por celda.
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
// Recibe XLSX como parametro: la libreria se carga bajo demanda al exportar.
function autoFitCols(XLSX, ws) {
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

// SheetJS (pesado, ~600KB) se carga SOLO al hacer clic en Exportar.
async function exportarExcel(visitas, fechaInicio, fechaFin) {
  const XLSX = await import('xlsx-js-style');
  const total = visitas.length;
  const canceladas = visitas.filter((v) => (v.estado || '').toUpperCase() === 'CANCELADA').length;
  const expiradas = visitas.filter((v) => (v.estado || '').toUpperCase() === 'EXPIRADA').length;
  const aptos = new Set(visitas.map((v) => v.numeroApartamento).filter(Boolean)).size;

  // Resumen por estado (sort cantidad DESC)
  const porEstado = {};
  visitas.forEach((v) => {
    const e = (v.estado || 'SIN ESTADO').toUpperCase();
    porEstado[e] = (porEstado[e] || 0) + 1;
  });
  const estadosOrdenados = Object.keys(porEstado).sort((a, b) => porEstado[b] - porEstado[a]);

  // Resumen por apartamento (sort registros DESC)
  const porApto = {};
  visitas.forEach((v) => {
    const apto = v.numeroApartamento || 'Sin apto';
    if (!porApto[apto]) porApto[apto] = { residente: v.nombreResidente || '', n: 0 };
    porApto[apto].n += 1;
  });
  const aptosOrdenados = Object.keys(porApto).sort((a, b) => porApto[b].n - porApto[a].n);

  // Helpers
  const serieFecha = (str) => {
    if (!str) return null;
    const m = String(str).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/);
    if (!m) return null;
    let serial = (Date.UTC(+m[1], +m[2] - 1, +m[3]) - Date.UTC(1899, 11, 30)) / 86400000;
    if (m[4] !== undefined) serial += (+m[4] * 3600 + +m[5] * 60) / 86400;
    return serial;
  };
  const tieneHora = (str) => /[T ]\d{2}:\d{2}/.test(String(str || ''));
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
  const celdaFecha = (val, st, hhmm) => {
    const serial = serieFecha(val);
    if (serial === null) return S('—', st);
    return { t: 'n', v: serial, s: { ...st, numFmt: hhmm && tieneHora(val) ? 'hh:mm' : 'dd/mm/yyyy' } };
  };
  const vacio = (val) => !val || String(val).trim() === '';
  const dash = (val, st) => (vacio(val) ? S('—', st) : S(String(val), st));
  const fechaDe = (v) => v.fechaVisita || v.fechaIngreso;

  // Estilos segun spec
  const estilos = {
    titulo: { font: { bold: true, sz: 16, color: { rgb: '0F2044' }, name: 'Calibri' } },
    subtitulo: { font: { italic: true, sz: 10, color: { rgb: '5B6B85' }, name: 'Calibri' } },
    seccion: { font: { bold: true, sz: 13, color: { rgb: '0F2044' } } },
    kpiLabel: { font: { sz: 9, color: { rgb: '64748B' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiValue: (color) => ({ font: { bold: true, sz: 14, color: { rgb: color } }, fill: { fgColor: { rgb: 'F4F6FA' } }, numFmt: '0' }),
    headerTabla: { font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '0F2044' } } },
    headerSummary: { font: { bold: true, sz: 9, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '2855A0' } } },
    zebraOdd: { fill: { fgColor: { rgb: 'F7F9FC' } } },
    zebraEven: { fill: { fgColor: { rgb: 'FFFFFF' } } },
    bd: { border: { bottom: { style: 'thin', color: { rgb: 'E5E9F0' } } } },
    muted: { font: { color: { rgb: '5B6B85' } } },
    visitante: { font: { bold: true } },
    apto: { alignment: { horizontal: 'center' }, font: { bold: true } },
    entero: { numFmt: '0', alignment: { horizontal: 'center' } },
    fecha: { numFmt: 'dd/mm/yyyy' },
    badgeEstado: {
      CANCELADA: { font: { bold: true, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'FBEAE8' } } },
      EXPIRADA: { font: { bold: true, color: { rgb: 'B7791F' } }, fill: { fgColor: { rgb: 'FDF3DE' } } },
      COMPLETADA: { font: { bold: true, color: { rgb: '0F8A5F' } }, fill: { fgColor: { rgb: 'E7F7EF' } } },
      ACTIVA: { font: { bold: true, color: { rgb: '2855A0' } }, fill: { fgColor: { rgb: 'E9F0FB' } } },
      default: { font: { bold: true, color: { rgb: '2855A0' } }, fill: { fgColor: { rgb: 'E9F0FB' } } },
    },
  };
  const badgeDe = (estado) => estilos.badgeEstado[String(estado || '').toUpperCase()] || estilos.badgeEstado.default;

  const aoa = [];
  const merges = [];
  let r = 0;

  // Titulo y subtitulo
  aoa[r] = [S('Historial de Visitas — Edificio Residencial', estilos.titulo)];
  aoa[r + 1] = [S(`Periodo: ${formatDate(fechaInicio)} — ${formatDate(fechaFin)}  ·  Generado por SAED  ·  ${total} registros`, estilos.subtitulo)];
  merges.push({ s: { r: 0, c: 0 }, e: { r: 0, c: 10 } }, { s: { r: 1, c: 0 }, e: { r: 1, c: 10 } });
  r += 3;

  // KPIs: una fila con 4 bloques (label + valor en columnas contiguas: A:B, C:D, E:F, G:H).
  // Sin merge: un merge ocultaria el valor en Excel (solo se muestra la celda superior-izquierda).
  const kpiCelda = (label, valor, color, c0) => {
    aoa[r] = aoa[r] || [];
    aoa[r][c0] = S(label, estilos.kpiLabel);
    aoa[r][c0 + 1] = N(valor, estilos.kpiValue(color));
  };
  kpiCelda('Total Registros', total, '0F2044', 0);
  kpiCelda('Canceladas', canceladas, 'C0392B', 2);
  kpiCelda('Expiradas', expiradas, 'B7791F', 4);
  kpiCelda('Apartamentos Involucrados', aptos, '1A2233', 6);
  r += 2;

  // Seccion detalle
  aoa[r] = [S('Detalle de registros', estilos.seccion)];
  merges.push({ s: { r, c: 0 }, e: { r, c: 10 } });
  r++;
  const rHeaderTabla = r;
  aoa[r] = [
    S('Fecha', estilos.headerTabla), S('Visitante', estilos.headerTabla), S('Documento', estilos.headerTabla), S('Apto', estilos.headerTabla),
    S('Residente', estilos.headerTabla), S('Entrada', estilos.headerTabla), S('Salida', estilos.headerTabla), S('Vehículo', estilos.headerTabla),
    S('Placa', estilos.headerTabla), S('Parqueadero', estilos.headerTabla), S('Estado', estilos.headerTabla),
  ];
  r++;
  visitas.forEach((v, i) => {
    const zebra = i % 2 === 0 ? estilos.zebraOdd : estilos.zebraEven;
    const base = { ...zebra, ...estilos.bd };
    const f = fechaDe(v);
    aoa[r] = [
      celdaFecha(f, base, false),
      S(`${v.nombreVisitante || ''} ${v.apellidoVisitante || ''}`, { ...estilos.visitante, ...base }),
      S(v.documentoVisitante || '', base),
      celdaApto(v.numeroApartamento, { ...estilos.apto, ...base }),
      S(v.nombreResidente || '', base),
      celdaFecha(f, base, true),
      celdaFecha(v.fechaSalida, base, true),
      dash(v.tipoVehiculo, { ...estilos.muted, ...base }),
      dash(v.placaVehiculo, { ...estilos.muted, ...base }),
      dash(v.codigoParqueadero, { ...estilos.muted, ...base }),
      S(v.estado || '', { ...base, ...badgeDe(v.estado) }),
    ];
    r++;
  });
  r++;

  // Resumen por estado + por apartamento lado a lado (gap 1 col: bloque 2 en D)
  const rSeccionResumenes = r;
  aoa[r] = [S('Resumen por estado', estilos.seccion)];
  aoa[r][3] = S('Resumen por apartamento', estilos.seccion);
  merges.push(
    { s: { r, c: 0 }, e: { r, c: 1 } },
    { s: { r, c: 3 }, e: { r, c: 5 } },
  );
  r++;
  aoa[r] = [
    S('Estado', estilos.headerSummary), S('Cantidad', estilos.headerSummary), null,
    S('Apto', estilos.headerSummary), S('Residente', estilos.headerSummary), S('Registros', estilos.headerSummary),
  ];
  r++;
  const filas = Math.max(estadosOrdenados.length, aptosOrdenados.length);
  for (let i = 0; i < filas; i++) {
    aoa[r] = [];
    if (estadosOrdenados[i]) {
      aoa[r][0] = S(estadosOrdenados[i], { ...badgeDe(estadosOrdenados[i]), ...estilos.bd });
      aoa[r][1] = N(porEstado[estadosOrdenados[i]], { ...estilos.entero, ...estilos.bd });
    }
    if (aptosOrdenados[i]) {
      aoa[r][3] = celdaApto(aptosOrdenados[i], { ...estilos.apto, ...estilos.bd });
      aoa[r][4] = S(porApto[aptosOrdenados[i]].residente || '', estilos.bd);
      aoa[r][5] = N(porApto[aptosOrdenados[i]].n, { ...estilos.entero, ...estilos.bd });
    }
    r++;
  }

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [{ wch: 12 }, { wch: 20 }, { wch: 14 }, { wch: 10 }, { wch: 26 }, { wch: 10 }, { wch: 10 }, { wch: 14 }, { wch: 10 }, { wch: 14 }, { wch: 12 }];
  ws['!merges'] = merges;
  autoFitCols(XLSX, ws);
  ws['!freeze'] = { xSplit: 0, ySplit: rHeaderTabla + 1 };

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Historial Visitas');
  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array', cellStyles: true });
  const blob = new Blob([aplicarFreeze(out, rHeaderTabla + 1)], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `visitas_${fechaInicio}_${fechaFin}.xlsx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}


function haceDias(dias) {
  const d = new Date();
  d.setDate(d.getDate() - dias);
  return d.toISOString().slice(0, 10);
}

export default function HistorialVisitasPage() {
  const [search, setSearch] = useState('');
  const [fechaInicio, setFechaInicio] = useState(haceDias(7));
  const [fechaFin, setFechaFin] = useState(todayStr());
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);

  const { data: visitasRaw, loading, error } = useFetch(
    () => api.get(`/visitas/historial?fechaInicio=${fechaInicio}&fechaFin=${fechaFin}`),
    [fechaInicio, fechaFin]
  );

  const filtradas = (visitasRaw?.items || visitasRaw || []).filter((v) => {
    if (!search) return true;
    const term = search.toLowerCase();
    return [v.nombreVisitante, v.documentoVisitante, v.numeroApartamento, v.nombreResidente]
      .filter(Boolean)
      .some((x) => String(x).toLowerCase().includes(term));
  });

  const stats = {
    total: filtradas.length,
    activas: filtradas.filter((v) => v.estado === 'ACTIVA' || v.estado === 'PENDIENTE').length,
    finalizadas: filtradas.filter((v) => v.estado === 'FINALIZADA').length,
  };

  async function verDetalle(row) {
    setLoadingDetalle(true);
    try {
      const d = await api.get(`/visitas/${row.idVisita}/detalle`);
      setDetalle(d);
    } catch (err) {
      toast.error(err.message);
    } finally {
      setLoadingDetalle(false);
    }
  }

  const columns = [
    { key: 'idVisita', label: 'ID', width: 60 },
    { key: 'nombreVisitante', label: 'Visitante' },
    { key: 'documentoVisitante', label: 'Documento' },
    { key: 'numeroApartamento', label: 'Apto' },
    { key: 'fechaVisita', label: 'Ingreso', render: (r) => formatDate(r.fechaVisita) },
    { key: 'fechaSalida', label: 'Salida', render: (r) => formatDate(r.fechaSalida) },
    { key: 'estado', label: 'Estado' },
  ];

  return (
    <div>
      <PageHeader
        title="Historial de Visitas"
        subtitle="Registro histórico de visitas"
        action={
          <Button
            variant="outline"
            onClick={() => exportarExcel(filtradas, fechaInicio, fechaFin)}
            disabled={filtradas.length === 0}
          >
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
            min={`${new Date().getFullYear()}-01-01`}
            max={fechaFin}
          />
          <Input
            id="fechaFin"
            label="Hasta"
            type="date"
            value={fechaFin}
            onChange={(e) => setFechaFin(e.target.value)}
            min={fechaInicio}
            max={todayStr()}
          />
          <Input
            id="search" aria-label="Buscar"
            label="Búsqueda rápida"
            placeholder="Visitante, documento, apto..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="card-grid-3" style={{ marginBottom: '20px' }}>
        <StatCard icon="today" value={stats.total} label="Total" color="primary" />
        <StatCard icon="how_to_reg" value={stats.activas} label="Activas" color="cyan" />
        <StatCard icon="check_circle" value={stats.finalizadas} label="Finalizadas" color="green" />
      </div>

      <DataTable
        columns={columns}
        rows={filtradas}
        loading={loading}
                empty={{ icon: 'history', title: 'No hay visitas en el rango seleccionado', subtitle: 'Prueba ampliando el rango de fechas.' }}
        error={error?.message}
        keyField="idVisita"
        onRowClick={verDetalle}
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de Visita" size="md">
        {loadingDetalle && <p>Cargando...</p>}
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="detail-row">
              <span>Visitante</span>
              <span>
                {detalle.nombreVisitante} {detalle.apellidoVisitante}
              </span>
            </div>
            <div className="detail-row">
              <span>Documento</span>
              <span>{detalle.documentoVisitante}</span>
            </div>
            <div className="detail-row">
              <span>Residente anfitrión</span>
              <span>{detalle.nombreResidente}</span>
            </div>
            <div className="detail-row">
              <span>Apartamento</span>
              <span>{detalle.numeroApartamento}</span>
            </div>
            <div className="detail-row">
              <span>Ingreso</span>
              <span>{formatDate(detalle.fechaVisita)}</span>
            </div>
            <div className="detail-row">
              <span>Salida</span>
              <span>{formatDate(detalle.fechaSalida) || 'Aún dentro'}</span>
            </div>
            {detalle.placaVehiculo && (
              <div className="detail-row">
                <span>Vehículo</span>
                <span>
                  {detalle.tipoVehiculo} — {detalle.placaVehiculo}
                </span>
              </div>
            )}
            {detalle.codigoParqueadero && (
              <div className="detail-row">
                <span>Parqueadero</span>
                <span>{detalle.codigoParqueadero}</span>
              </div>
            )}
            {detalle.fotoCaptura && (
              <div style={{ marginTop: '12px' }}>
                <img
                  src={imageSrc(detalle.fotoCaptura)}
                  alt="Foto de captura"
                  loading="lazy"
                  width="400"
                  height="300"
                  style={{ maxWidth: '100%', borderRadius: '8px', cursor: 'zoom-in' }}
                  onClick={(e) => window.open(e.target.src, '_blank')}
                />
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
