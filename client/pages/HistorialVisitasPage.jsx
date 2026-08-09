import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, todayStr, imageSrc } from '../lib/utils.js';

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function exportarExcel(visitas, fechaInicio, fechaFin) {
  const total = visitas.length;
  const canceladas = visitas.filter((v) => (v.estado || '').toUpperCase() === 'CANCELADA').length;
  const expiradas = visitas.filter((v) => (v.estado || '').toUpperCase() === 'EXPIRADA').length;
  const aptos = new Set(visitas.map((v) => v.numeroApartamento).filter(Boolean)).size;

  // Resumen por estado
  const porEstado = {};
  visitas.forEach((v) => {
    const e = (v.estado || 'SIN ESTADO').toUpperCase();
    porEstado[e] = (porEstado[e] || 0) + 1;
  });
  const estadosOrdenados = Object.keys(porEstado).sort();

  // Resumen por apartamento
  const porApto = {};
  visitas.forEach((v) => {
    const apto = v.numeroApartamento || 'Sin apto';
    if (!porApto[apto]) porApto[apto] = { residente: v.nombreResidente || '', n: 0 };
    porApto[apto].n += 1;
  });
  const aptosOrdenados = Object.keys(porApto).sort((a, b) => Number(a) - Number(b) || String(a).localeCompare(String(b)));

  // Badge de estado: EXPIRADA ambar, CANCELADA rojo, resto neutro azul
  const badgeEstado = (estado) => {
    const e = (estado || '').toUpperCase();
    if (e === 'EXPIRADA') return 'background:#FDF3DE;color:#B7791F';
    if (e === 'CANCELADA') return 'background:#FBEAE8;color:#C0392B';
    return 'background:#E8EEF9;color:#2855A0';
  };

  const celdaVacia = 'style="padding:8px 12px;border-bottom:1px solid #E5E9F0;text-align:center;color:#B7C0D1;font-style:italic;"';

  const filaDetalle = (v, fondo) => {
    const vacio = (val) => !val || String(val).trim() === '';
    const fecha = v.fechaVisita || v.fechaIngreso;
    return `
<tr style="background:${fondo};">
${vacio(fecha) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(formatDate(fecha))}</td>`}
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;font-weight:600;">${esc(v.nombreVisitante || '')} ${esc(v.apellidoVisitante || '')}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(v.documentoVisitante || '')}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;text-align:center;font-weight:600;">${esc(v.numeroApartamento || '')}</td>
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(v.nombreResidente || '')}</td>
${vacio(fecha) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(formatDate(fecha))}</td>`}
${vacio(v.fechaSalida) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(formatDate(v.fechaSalida))}</td>`}
${vacio(v.tipoVehiculo) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(v.tipoVehiculo)}</td>`}
${vacio(v.placaVehiculo) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(v.placaVehiculo)}</td>`}
${vacio(v.codigoParqueadero) ? `<td ${celdaVacia}>—</td>` : `<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;">${esc(v.codigoParqueadero)}</td>`}
<td style="padding:8px 12px;border-bottom:1px solid #E5E9F0;"><span style="${badgeEstado(v.estado)};padding:3px 10px;border-radius:10px;font-size:12px;font-weight:700;">${esc(v.estado || '')}</span></td>
</tr>`;
  };

  const filasDetalle = visitas
    .map((v, i) => filaDetalle(v, i % 2 === 0 ? '#FFFFFF' : '#F7F9FC'))
    .join('');

  const filasEstado = estadosOrdenados
    .map((e) => `
<tr>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;"><span style="${badgeEstado(e)};padding:3px 10px;border-radius:10px;font-size:12px;font-weight:700;">${esc(e)}</span></td>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;font-weight:700;">${porEstado[e]}</td>
</tr>`
    )
    .join('');

  const filasApto = aptosOrdenados
    .map((a) => `
<tr>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;font-weight:600;">${esc(a)}</td>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;">${esc(porApto[a].residente)}</td>
<td style="padding:8px 14px;border-bottom:1px solid #E5E9F0;text-align:center;">${porApto[a].n}</td>
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
<x:Name>Historial Visitas</x:Name>
<x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions>
</x:ExcelWorksheet>
</x:ExcelWorksheets>
</x:ExcelWorkbook>
</xml>
<![endif]-->
<style>
  body { font-family: Calibri, Arial, sans-serif; color:#1A2233; margin:0; padding:24px; background:#EEF1F6; }
  .sheet { max-width: 1150px; margin: 0 auto; background:#fff; }
  h1 { font-size: 22px; margin:0 0 4px 0; color:#0F2044; }
  .subtitle { color:#5B6B85; font-size:13px; margin-bottom:16px; }
  table { border-collapse: collapse; width:100%; }
  .kpi-table td { padding:16px 20px; }
  .kpi-label { font-size:11px; text-transform:uppercase; letter-spacing:.04em; color:#8592A8; }
  .kpi-value { font-size:20px; font-weight:700; color:#0F2044; }
  .section-title { font-size:15px; font-weight:700; color:#0F2044; margin:28px 0 10px 0; padding-top:10px; border-top:2px solid #EEF1F6;}
  thead th { background:#0F2044; color:#fff; text-align:left; padding:10px 12px; font-size:12px; text-transform:uppercase; letter-spacing:.03em; }
  .mini thead th { background:#2855A0; }
  .footer-note { color:#8592A8; font-size:11px; margin-top:24px; }
</style>
</head>
<body>
<div class="sheet">
  <h1>Historial de Visitas — Edificio Residencial</h1>
  <div class="subtitle">Periodo: ${d1} — ${d2} &nbsp;|&nbsp; Generado por SAED &nbsp;|&nbsp; ${total} registros</div>

  <table class="kpi-table" style="border:1px solid #E5E9F0;">
    <tr>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Total Registros</div><div class="kpi-value">${total}</div></td>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Canceladas</div><div class="kpi-value" style="color:#C0392B;">${canceladas}</div></td>
      <td style="border-right:1px solid #E5E9F0;"><div class="kpi-label">Expiradas</div><div class="kpi-value" style="color:#B7791F;">${expiradas}</div></td>
      <td><div class="kpi-label">Apartamentos Involucrados</div><div class="kpi-value">${aptos}</div></td>
    </tr>
  </table>

  <div class="section-title">Detalle de registros</div>
  <table>
    <thead>
      <tr><th>Fecha</th><th>Visitante</th><th>Documento</th><th>Apto</th><th>Residente</th><th>Entrada</th><th>Salida</th><th>Vehículo</th><th>Placa</th><th>Parqueadero</th><th>Estado</th></tr>
    </thead>
    <tbody>
      ${filasDetalle}
    </tbody>
  </table>

  <div style="display:flex; gap:24px; margin-top:10px;">
    <div style="flex:1;">
      <div class="section-title">Resumen por estado</div>
      <table class="mini">
        <thead><tr><th>Estado</th><th style="text-align:center;">Cantidad</th></tr></thead>
        <tbody>
          ${filasEstado}
        </tbody>
      </table>
    </div>
    <div style="flex:1;">
      <div class="section-title">Resumen por apartamento</div>
      <table class="mini">
        <thead><tr><th style="text-align:center;">Apto</th><th>Residente</th><th style="text-align:center;">Registros</th></tr></thead>
        <tbody>
          ${filasApto}
        </tbody>
      </table>
    </div>
  </div>
</div>
</body>
</html>`;

  // BOM UTF-8: sin él, Excel interpreta el .xls como Windows-1252 y corrompe los
  // acentos (archivo "dañado" o texto ilegible). El BOM fuerza detección UTF-8.
  const blob = new Blob(['\uFEFF' + xls], { type: 'application/vnd.ms-excel' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = `visitas_${fechaInicio}_${fechaFin}.xls`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

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

function haceDias(dias) {
  const d = new Date();
  d.setDate(d.getDate() - dias);
  return d.toISOString().slice(0, 10);
}

export default function HistorialVisitasPage() {
  const [toast, setToast] = useState(null);
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
      setToast({ message: err.message, type: 'error' });
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
        <Stat icon="today" value={stats.total} label="Total" color="primary" />
        <Stat icon="how_to_reg" value={stats.activas} label="Activas" color="cyan" />
        <Stat icon="check_circle" value={stats.finalizadas} label="Finalizadas" color="green" />
      </div>

      <DataTable
        columns={columns}
        rows={filtradas}
        loading={loading}
        empty="No hay visitas en el rango seleccionado"
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
                  {detalle.tipoVehiculo} â€” {detalle.placaVehiculo}
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
                  style={{ maxWidth: '100%', borderRadius: '8px', cursor: 'zoom-in' }}
                  onClick={(e) => window.open(e.target.src, '_blank')}
                />
              </div>
            )}
          </div>
        )}
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
