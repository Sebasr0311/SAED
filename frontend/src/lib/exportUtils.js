import { BASE_URL } from './api.js';
import { TOKEN_KEY } from './storage.js';

/**
 * Descarga un archivo binario (PDF / CSV) generado por el motor de reportes backend.
 * Respeta headers de autenticación, multi-tenancy y captura errores del backend.
 *
 * @param {string} endpoint - Ruta relativa del endpoint (ej: '/reportes/cartera-morosa?formato=PDF')
 * @param {string} defaultFilename - Nombre de archivo por defecto si no viene en Content-Disposition
 */
export async function downloadReportFile(endpoint, defaultFilename = 'reporte-saed.bin', options = {}) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const activeAssignment = typeof window !== 'undefined' ? sessionStorage.getItem('saed_active_assignment_id') : null;

  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (activeAssignment) headers['X-Assignment-Id'] = activeAssignment;
  if (options.body) headers['Content-Type'] = 'application/json';

  const res = await fetch(`${BASE_URL}${endpoint}`, {
    method: options.method || 'GET',
    headers: { ...headers, ...(options.headers || {}) },
    body: options.body ? (typeof options.body === 'string' ? options.body : JSON.stringify(options.body)) : undefined,
  });

  if (!res.ok) {
    let errorMsg = 'Error al exportar el reporte';
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.mensaje || errJson.error || errorMsg;
    } catch {
      // Si no es JSON, intentar texto
      try {
        const text = await res.text();
        if (text) errorMsg = text;
      } catch {
        /* fallback */
      }
    }
    const err = new Error(errorMsg);
    err.status = res.status;
    throw err;
  }

  // Extraer nombre de archivo de Content-Disposition si existe
  let filename = defaultFilename;
  const disposition = res.headers.get('content-disposition');
  if (disposition && disposition.includes('filename=')) {
    const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
    if (match && match[1]) {
      filename = match[1].replace(/['"]/g, '').trim();
    }
  }

  const blob = await res.blob();
  const blobUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(blobUrl);
}

/**
 * Ajusta automáticamente el ancho de columnas en una hoja de trabajo de xlsx-js-style.
 */
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
    const cols = (ws['!cols'] || []).map((c) => (c && c.wch) || 12);
    for (let R = range.s.r; R <= range.e.r; R++) {
      for (let C = range.s.c; C <= range.e.c; C++) {
        if (merged.has(R + ':' + C)) continue;
        const cell = ws[XLSX.utils.encode_cell({ r: R, c: C })];
        if (!cell || cell.v === undefined || cell.v === null) continue;
        let texto;
        const fmt = cell.z || (cell.s && cell.s.numFmt);
        if (cell.t === 'n' && fmt) {
          try {
            texto = XLSX.SSF.format(fmt, cell.v);
          } catch {
            texto = String(cell.v);
          }
        } else {
          texto = String(cell.v);
        }
        const sz = (cell.s && cell.s.font && cell.s.font.sz) || 10;
        const wchNeeded = Math.ceil(texto.length * (sz / 11)) + 4;
        if ((cols[C] || 0) < wchNeeded) cols[C] = wchNeeded;
      }
    }
    ws['!cols'] = cols.map((wch) => ({ wch }));
  } catch {
    /* preserva el ancho original */
  }
}

/**
 * Exporta un conjunto de datos a formato Excel (.xlsx) nativo con formato corporativo SAED 2.0.
 * Carga xlsx-js-style de manera diferida (lazy import) para no penalizar el bundle inicial.
 *
 * @param {Object} options
 * @param {string} options.filename - Nombre del archivo a generar (sin o con .xlsx)
 * @param {string} options.sheetName - Nombre de la pestaña en el libro
 * @param {string} options.title - Título principal del reporte
 * @param {string} [options.subtitle] - Subtítulo o rango de fechas
 * @param {Array<{key: string, label: string, type?: 'string'|'currency'|'number'|'date'|'percent'}>} options.columns
 * @param {Array<Object>} options.data - Registros a exportar
 * @param {Array<Object>} [options.summaryRows] - Filas de totales o agregados al pie de la tabla
 */
export async function exportToExcel({
  filename = 'reporte-saed.xlsx',
  sheetName = 'Reporte',
  title = 'Reporte SAED 2.0',
  subtitle = '',
  columns = [],
  data = [],
  summaryRows = [],
}) {
  const XLSX = await import('xlsx-js-style');

  const clonarEstilo = (st) => (st ? JSON.parse(JSON.stringify(st)) : undefined);
  const S = (v, st) => ({ t: 's', v: String(v ?? ''), s: clonarEstilo(st) });
  const N = (v, st) => ({ t: 'n', v: Number(v || 0), s: clonarEstilo(st) });

  const estilos = {
    titulo: { font: { bold: true, sz: 14, color: { rgb: '0F2044' }, name: 'Calibri' } },
    subtitulo: { font: { italic: true, sz: 10, color: { rgb: '5B6B85' }, name: 'Calibri' } },
    metadata: { font: { sz: 9, color: { rgb: '8A99AD' }, name: 'Calibri' } },
    header: {
      font: { bold: true, sz: 10, color: { rgb: 'FFFFFF' } },
      fill: { fgColor: { rgb: '0F2044' } },
      alignment: { horizontal: 'center', vertical: 'center' },
    },
    zebraOdd: { fill: { fgColor: { rgb: 'F8FAFC' } }, font: { sz: 10 } },
    zebraEven: { fill: { fgColor: { rgb: 'FFFFFF' } }, font: { sz: 10 } },
    summaryLabel: {
      font: { bold: true, sz: 10, color: { rgb: '0F2044' } },
      fill: { fgColor: { rgb: 'E2E8F0' } },
      alignment: { horizontal: 'right' },
    },
    summaryValue: {
      font: { bold: true, sz: 10, color: { rgb: '0F2044' } },
      fill: { fgColor: { rgb: 'E2E8F0' } },
      numFmt: '$#,##0',
      alignment: { horizontal: 'right' },
    },
    moneda: { numFmt: '$#,##0', alignment: { horizontal: 'right' } },
    porcentaje: { numFmt: '0.0%', alignment: { horizontal: 'right' } },
    numero: { numFmt: '#,##0', alignment: { horizontal: 'right' } },
    centrado: { alignment: { horizontal: 'center' } },
  };

  const aoa = [];
  const merges = [];
  let r = 0;

  // Fila 0: Título
  aoa.push([S(title, estilos.titulo)]);
  merges.push({ s: { r: 0, c: 0 }, e: { r: 0, c: Math.max(columns.length - 1, 3) } });
  r++;

  // Fila 1: Subtítulo
  if (subtitle) {
    aoa.push([S(subtitle, estilos.subtitulo)]);
    merges.push({ s: { r: 1, c: 0 }, e: { r: 1, c: Math.max(columns.length - 1, 3) } });
    r++;
  }

  // Fila Metadata: Fecha de generación
  const fechaGeneracion = `Generado el: ${new Date().toLocaleString('es-CO')}`;
  aoa.push([S(fechaGeneracion, estilos.metadata)]);
  merges.push({ s: { r, c: 0 }, e: { r, c: Math.max(columns.length - 1, 3) } });
  r++;

  // Fila en blanco
  aoa.push([]);
  r++;

  // Fila Encabezados de tabla
  const headerRow = columns.map((c) => S(c.label, estilos.header));
  aoa.push(headerRow);
  r++;

  // Filas de Datos
  data.forEach((item, idx) => {
    const baseStyle = idx % 2 === 0 ? estilos.zebraEven : estilos.zebraOdd;
    const row = columns.map((col) => {
      const val = item[col.key];
      if (col.type === 'currency') {
        return N(val, { ...baseStyle, ...estilos.moneda });
      }
      if (col.type === 'percent') {
        const numVal = (Number(val) || 0) / 100;
        return N(numVal, { ...baseStyle, ...estilos.porcentaje });
      }
      if (col.type === 'number') {
        return N(val, { ...baseStyle, ...estilos.numero });
      }
      if (col.type === 'center') {
        return S(val, { ...baseStyle, ...estilos.centrado });
      }
      return S(val, baseStyle);
    });
    aoa.push(row);
    r++;
  });

  // Filas de Resumen / Totales si existen
  summaryRows.forEach((sRow) => {
    const row = columns.map((col, cIdx) => {
      const val = sRow[col.key];
      if (val === undefined || val === null) {
        return S('', estilos.summaryLabel);
      }
      if (col.type === 'currency') {
        return N(val, estilos.summaryValue);
      }
      return S(val, estilos.summaryLabel);
    });
    aoa.push(row);
    r++;
  });

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!merges'] = merges;
  autoFitCols(XLSX, ws);

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, sheetName.substring(0, 31));

  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  const finalFilename = filename.endsWith('.xlsx') ? filename : `${filename}.xlsx`;

  const blob = new Blob([out], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
  const blobUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = finalFilename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(blobUrl);
}
