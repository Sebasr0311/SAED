import { useState } from 'react';
import * as XLSX from 'xlsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, todayStr, imageSrc } from '../lib/utils.js';

// Export a .xlsx REAL con SheetJS: Excel abre sin warning de formato y con
// asociacion directa (formato nativo moderno). Estilos basicos aplicados por celda.
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

  // Estilos reutilizables
  const estilos = {
    titulo: { font: { bold: true, sz: 16, color: { rgb: '0F2044' } } },
    subtitulo: { font: { sz: 11, color: { rgb: '5B6B85' } } },
    header: { font: { bold: true, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '0F2044' } } },
    headerMini: { font: { bold: true, color: { rgb: 'FFFFFF' } }, fill: { fgColor: { rgb: '2855A0' } } },
    seccion: { font: { bold: true, sz: 13, color: { rgb: '0F2044' } } },
    kpiLabel: { font: { sz: 10, color: { rgb: '8592A8' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiValue: { font: { bold: true, sz: 14, color: { rgb: '0F2044' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiRojo: { font: { bold: true, sz: 14, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    kpiAmbar: { font: { bold: true, sz: 14, color: { rgb: 'B7791F' } }, fill: { fgColor: { rgb: 'F4F6FA' } } },
    visitante: { font: { bold: true } },
    apto: { alignment: { horizontal: 'center' }, font: { bold: true } },
    muted: { font: { color: { rgb: 'B7C0D1' } } },
    badgeAmbar: { font: { bold: true, color: { rgb: 'B7791F' } }, fill: { fgColor: { rgb: 'FDF3DE' } }, alignment: { horizontal: 'center' } },
    badgeRojo: { font: { bold: true, color: { rgb: 'C0392B' } }, fill: { fgColor: { rgb: 'FBEAE8' } }, alignment: { horizontal: 'center' } },
    badgeAzul: { font: { bold: true, color: { rgb: '2855A0' } }, fill: { fgColor: { rgb: 'E8EEF9' } }, alignment: { horizontal: 'center' } },
  };

  const badgeEstado = (estado) => {
    const e = (estado || '').toUpperCase();
    if (e === 'EXPIRADA') return estilos.badgeAmbar;
    if (e === 'CANCELADA') return estilos.badgeRojo;
    return estilos.badgeAzul;
  };
  const vacio = (val) => !val || String(val).trim() === '';
  const dash = (val) => (vacio(val) ? '—' : val);
  const fechaDe = (v) => v.fechaVisita || v.fechaIngreso;

  const aoa = [];
  let r = 0;

  // Titulo y subtitulo
  aoa[r] = []; aoa[r][0] = { t: 's', v: 'Historial de Visitas — Edificio Residencial', s: estilos.titulo };
  aoa[r + 1] = [];
  aoa[r + 1][0] = {
    t: 's',
    v: `Periodo: ${formatDate(fechaInicio)} — ${formatDate(fechaFin)} | Generado por SAED | ${total} registros`,
    s: estilos.subtitulo,
  };
  r += 3;

  // KPIs
  const kpis = [
    ['Total Registros', String(total), 'kpiValue'],
    ['Canceladas', String(canceladas), 'kpiRojo'],
    ['Expiradas', String(expiradas), 'kpiAmbar'],
    ['Apartamentos Involucrados', String(aptos), 'kpiValue'],
  ];
  kpis.forEach(([label, valor, estilo]) => {
    aoa[r] = [];
    aoa[r][0] = { t: 's', v: label, s: estilos.kpiLabel };
    aoa[r][1] = { t: 's', v: valor, s: estilos[estilo] };
    r++;
  });
  r++;

  // Seccion detalle
  aoa[r] = []; aoa[r][0] = { t: 's', v: 'Detalle de registros', s: estilos.seccion };
  r++;
  aoa[r] = [
    { t: 's', v: 'Fecha', s: estilos.header }, { t: 's', v: 'Visitante', s: estilos.header },
    { t: 's', v: 'Documento', s: estilos.header }, { t: 's', v: 'Apto', s: estilos.header },
    { t: 's', v: 'Residente', s: estilos.header }, { t: 's', v: 'Entrada', s: estilos.header },
    { t: 's', v: 'Salida', s: estilos.header }, { t: 's', v: 'Vehículo', s: estilos.header },
    { t: 's', v: 'Placa', s: estilos.header }, { t: 's', v: 'Parqueadero', s: estilos.header },
    { t: 's', v: 'Estado', s: estilos.header },
  ];
  r++;
  visitas.forEach((v) => {
    const f = fechaDe(v);
    aoa[r] = [
      { t: 's', v: vacio(f) ? '—' : formatDate(f), s: vacio(f) ? estilos.muted : undefined },
      { t: 's', v: `${v.nombreVisitante || ''} ${v.apellidoVisitante || ''}`, s: estilos.visitante },
      { t: 's', v: v.documentoVisitante || '' },
      { t: 's', v: String(v.numeroApartamento || ''), s: estilos.apto },
      { t: 's', v: v.nombreResidente || '' },
      { t: 's', v: vacio(f) ? '—' : formatDate(f), s: vacio(f) ? estilos.muted : undefined },
      { t: 's', v: vacio(v.fechaSalida) ? '—' : formatDate(v.fechaSalida), s: vacio(v.fechaSalida) ? estilos.muted : undefined },
      { t: 's', v: String(dash(v.tipoVehiculo)), s: vacio(v.tipoVehiculo) ? estilos.muted : undefined },
      { t: 's', v: String(dash(v.placaVehiculo)), s: vacio(v.placaVehiculo) ? estilos.muted : undefined },
      { t: 's', v: String(dash(v.codigoParqueadero)), s: vacio(v.codigoParqueadero) ? estilos.muted : undefined },
      { t: 's', v: v.estado || '', s: badgeEstado(v.estado) },
    ];
    r++;
  });
  r++;

  // Resumen por estado + por apartamento lado a lado
  aoa[r] = []; aoa[r][0] = { t: 's', v: 'Resumen por estado', s: estilos.seccion };
  aoa[r][5] = { t: 's', v: 'Resumen por apartamento', s: estilos.seccion };
  r++;
  aoa[r] = [
    { t: 's', v: 'Estado', s: estilos.headerMini }, { t: 's', v: 'Cantidad', s: estilos.headerMini },
    {}, {}, {},
    { t: 's', v: 'Apto', s: estilos.headerMini }, { t: 's', v: 'Residente', s: estilos.headerMini }, { t: 's', v: 'Registros', s: estilos.headerMini },
    {}, {}, {},
  ];
  r++;
  const filas = Math.max(estadosOrdenados.length, aptosOrdenados.length);
  for (let i = 0; i < filas; i++) {
    aoa[r] = [];
    if (estadosOrdenados[i]) {
      aoa[r][0] = { t: 's', v: estadosOrdenados[i], s: badgeEstado(estadosOrdenados[i]) };
      aoa[r][1] = { t: 'n', v: porEstado[estadosOrdenados[i]], s: estilos.apto };
    }
    if (aptosOrdenados[i]) {
      aoa[r][5] = { t: 's', v: String(aptosOrdenados[i]), s: estilos.apto };
      aoa[r][6] = { t: 's', v: porApto[aptosOrdenados[i]].residente || '' };
      aoa[r][7] = { t: 'n', v: porApto[aptosOrdenados[i]].n, s: estilos.apto };
    }
    r++;
  }

  const ws = XLSX.utils.aoa_to_sheet(aoa);
  ws['!cols'] = [
    { wch: 12 }, { wch: 24 }, { wch: 14 }, { wch: 10 },
    { wch: 26 }, { wch: 12 }, { wch: 12 }, { wch: 14 },
    { wch: 14 }, { wch: 14 }, { wch: 14 },
  ];
  ws['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: 10 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: 10 } },
  ];

  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Historial Visitas');
  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  const blob = new Blob([out], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `visitas_${fechaInicio}_${fechaFin}.xlsx`;
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
