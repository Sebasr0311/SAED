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

// Export a SpreadsheetML 2003 (XML nativo de Excel): con extensión .xls Excel lo
// abre SIN el warning "el formato y la extensión no coinciden" (a diferencia del
// HTML con .xls).
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
    if (e === 'EXPIRADA') return 'BadgeAmbar';
    if (e === 'CANCELADA') return 'BadgeRojo';
    return 'BadgeAzul';
  };

  const cell = (value, opts = {}) => {
    const { type = 'String', style, merge } = opts;
    const attrs = [];
    if (style) attrs.push(`ss:StyleID="${style}"`);
    if (merge) attrs.push(`ss:MergeAcross="${merge}"`);
    return `<Cell${attrs.length ? ' ' + attrs.join(' ') : ''}><Data ss:Type="${type}">${esc(value)}</Data></Cell>`;
  };

  const vacio = (val) => !val || String(val).trim() === '';
  const dash = (val) => (vacio(val) ? '—' : val);
  const fecha = (v) => v.fechaVisita || v.fechaIngreso;

  const filaDetalle = (v) => {
    const esVacioFecha = vacio(fecha(v));
    return `<Row>
      ${esVacioFecha ? cell('—', { style: 'Muted' }) : cell(esc(formatDate(fecha(v))))}
      ${cell(`${esc(v.nombreVisitante || '')} ${esc(v.apellidoVisitante || '')}`, { style: 'Visitante' })}
      ${cell(esc(v.documentoVisitante || ''))}
      ${cell(esc(v.numeroApartamento || ''), { style: 'Apto' })}
      ${cell(esc(v.nombreResidente || ''))}
      ${esVacioFecha ? cell('—', { style: 'Muted' }) : cell(esc(formatDate(fecha(v))))}
      ${vacio(v.fechaSalida) ? cell('—', { style: 'Muted' }) : cell(esc(formatDate(v.fechaSalida)))}
      ${cell(esc(dash(v.tipoVehiculo)), { style: 'Muted' })}
      ${cell(esc(dash(v.placaVehiculo)), { style: 'Muted' })}
      ${cell(esc(dash(v.codigoParqueadero)), { style: 'Muted' })}
      ${cell(esc(v.estado || ''), { style: badgeEstado(v.estado) })}
    </Row>`;
  };

  const filasDetalle = visitas.map(filaDetalle).join('');

  const filasEstado = estadosOrdenados
    .map((e) => `<Row>
      ${cell(esc(e), { style: badgeEstado(e) })}
      ${cell(String(porEstado[e]), { style: 'Apto' })}
    </Row>`)
    .join('');

  const filasApto = aptosOrdenados
    .map((a) => `<Row>
      ${cell(esc(a), { style: 'Apto' })}
      ${cell(esc(porApto[a].residente))}
      ${cell(String(porApto[a].n), { style: 'Apto' })}
    </Row>`)
    .join('');

  const d1 = formatDate(fechaInicio);
  const d2 = formatDate(fechaFin);

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
    <Style ss:ID="KpiRojo">
      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#C0392B"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="KpiAmbar">
      <Font ss:FontName="Calibri" ss:Size="14" ss:Bold="1" ss:Color="#B7791F"/>
      <Interior ss:Color="#F4F6FA" ss:Pattern="Solid"/>
    </Style>
    <Style ss:ID="Seccion">
      <Font ss:FontName="Calibri" ss:Size="13" ss:Bold="1" ss:Color="#0F2044"/>
    </Style>
    <Style ss:ID="Visitante">
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#1A2233"/>
    </Style>
    <Style ss:ID="Apto">
      <Alignment ss:Horizontal="Center"/>
      <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#1A2233"/>
    </Style>
    <Style ss:ID="Muted">
      <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#B7C0D1"/>
    </Style>
    <Style ss:ID="BadgeAmbar">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#B7791F"/>
      <Interior ss:Color="#FDF3DE" ss:Pattern="Solid"/>
      <Alignment ss:Horizontal="Center"/>
    </Style>
    <Style ss:ID="BadgeRojo">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#C0392B"/>
      <Interior ss:Color="#FBEAE8" ss:Pattern="Solid"/>
      <Alignment ss:Horizontal="Center"/>
    </Style>
    <Style ss:ID="BadgeAzul">
      <Font ss:FontName="Calibri" ss:Size="10" ss:Bold="1" ss:Color="#2855A0"/>
      <Interior ss:Color="#E8EEF9" ss:Pattern="Solid"/>
      <Alignment ss:Horizontal="Center"/>
    </Style>`;

  const kpi = (label, value, style) => `<Row>
    ${cell(esc(label), { style: 'KpiLabel', merge: 1 })}
    ${cell('')}
    ${cell(esc(value), { style })}
  </Row>`;

  const filasKpi = [
    kpi('Total Registros', String(total), 'KpiValue'),
    kpi('Canceladas', String(canceladas), 'KpiRojo'),
    kpi('Expiradas', String(expiradas), 'KpiAmbar'),
    kpi('Apartamentos Involucrados', String(aptos), 'KpiValue'),
  ].join('');

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
  <Styles>${styles}
  </Styles>
  <Worksheet ss:Name="Historial Visitas">
    <Table>
      <Column ss:Width="90"/>
      <Column ss:Width="180"/>
      <Column ss:Width="120"/>
      <Column ss:Width="70"/>
      <Column ss:Width="180"/>
      <Column ss:Width="90"/>
      <Column ss:Width="90"/>
      <Column ss:Width="100"/>
      <Column ss:Width="100"/>
      <Column ss:Width="110"/>
      <Column ss:Width="100"/>
      <Row ss:Height="30">
        ${cell('Historial de Visitas — Edificio Residencial', { style: 'Titulo', merge: 10 })}
        ${Array(10).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell(`Periodo: ${d1} — ${d2} | Generado por SAED | ${total} registros`, { style: 'Subtitulo', merge: 10 })}
        ${Array(10).fill('').map(() => cell('')).join('')}
      </Row>
      <Row ss:Height="20"/>
      ${filasKpi}
      <Row ss:Height="20"/>
      <Row>
        ${cell('Detalle de registros', { style: 'Seccion', merge: 10 })}
        ${Array(10).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell('Fecha', { style: 'Header' })}
        ${cell('Visitante', { style: 'Header' })}
        ${cell('Documento', { style: 'Header' })}
        ${cell('Apto', { style: 'Header' })}
        ${cell('Residente', { style: 'Header' })}
        ${cell('Entrada', { style: 'Header' })}
        ${cell('Salida', { style: 'Header' })}
        ${cell('Vehículo', { style: 'Header' })}
        ${cell('Placa', { style: 'Header' })}
        ${cell('Parqueadero', { style: 'Header' })}
        ${cell('Estado', { style: 'Header' })}
      </Row>
      ${filasDetalle}
      <Row ss:Height="20"/>
      <Row>
        ${cell('Resumen por estado', { style: 'Seccion', merge: 4 })}
        ${Array(4).fill('').map(() => cell('')).join('')}
        ${cell('Resumen por apartamento', { style: 'Seccion', merge: 5 })}
        ${Array(5).fill('').map(() => cell('')).join('')}
      </Row>
      <Row>
        ${cell('Estado', { style: 'HeaderMini' })}
        ${cell('Cantidad', { style: 'HeaderMini' })}
        ${Array(3).fill('').map(() => cell('')).join('')}
        ${cell('Apto', { style: 'HeaderMini' })}
        ${cell('Residente', { style: 'HeaderMini' })}
        ${cell('Registros', { style: 'HeaderMini' })}
        ${Array(3).fill('').map(() => cell('')).join('')}
      </Row>
      ${estadosOrdenados.map((e, i) => {
        const apto = aptosOrdenados[i];
        return `<Row>
          ${cell(esc(e), { style: badgeEstado(e) })}
          ${cell(String(porEstado[e]), { style: 'Apto' })}
          ${Array(3).fill('').map(() => cell('')).join('')}
          ${apto ? cell(esc(apto), { style: 'Apto' }) : cell('')}
          ${apto ? cell(esc(porApto[apto].residente)) : cell('')}
          ${apto ? cell(String(porApto[apto].n), { style: 'Apto' }) : cell('')}
          ${Array(3).fill('').map(() => cell('')).join('')}
        </Row>`;
      }).join('')}
    </Table>
  </Worksheet>
</Workbook>`;

  // BOM UTF-8: sin él, Excel interpreta el .xls como Windows-1252 y corrompe los
  // acentos (archivo "dañado" o texto ilegible). El BOM fuerza detección UTF-8.
  const blob = new Blob(['\uFEFF' + xml], { type: 'application/vnd.ms-excel' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  // Extensión .xml (SpreadsheetML 2003): Excel 2016+ muestra el warning "formato y
  // extensión no coinciden" si el XML se guarda como .xls. Con .xml + progid
  // Excel.Sheet, Windows lo abre con Excel directamente y sin advertencia.
  link.download = `visitas_${fechaInicio}_${fechaFin}.xml`;
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
