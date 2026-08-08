import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, todayStr } from '../lib/utils.js';

function exportarExcel(visitas, fechaInicio, fechaFin) {
  let xls =
    '<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">' +
    '<head><meta charset="UTF-8"><style>table{width:100%;border-collapse:collapse}th{background:#0F2044;color:#fff}</style></head><body><table>' +
    '<thead><tr><th>Fecha</th><th>Visitante</th><th>Documento</th><th>Apartamento</th><th>Residente</th><th>Entrada</th><th>Salida</th><th>Tipo Vehiculo</th><th>Placa</th><th>Parqueadero</th><th>Estado</th></tr></thead><tbody>';
  visitas.forEach((v) => {
    xls +=
      '<tr>' +
      `<td>${v.fechaVisita || v.fechaIngreso || ''}</td>` +
      `<td>${v.nombreVisitante || ''} ${v.apellidoVisitante || ''}</td>` +
      `<td>${v.documentoVisitante || ''}</td>` +
      `<td>${v.numeroApartamento || ''}</td>` +
      `<td>${v.nombreResidente || ''}</td>` +
      `<td>${v.fechaVisita || v.fechaIngreso || ''}</td>` +
      `<td>${v.fechaSalida || ''}</td>` +
      `<td>${v.tipoVehiculo || ''}</td>` +
      `<td>${v.placaVehiculo || ''}</td>` +
      `<td>${v.codigoParqueadero || ''}</td>` +
      `<td>${v.estado || ''}</td>` +
      '</tr>';
  });
  xls += '</tbody></table></body></html>';
  const blob = new Blob([xls], { type: 'application/vnd.ms-excel' });
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

  const { data: visitasRaw, loading } = useFetch(
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
    activas: filtradas.filter((v) => v.estado === 'EN_CURSO' || v.estado === 'PENDIENTE').length,
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
            id="search"
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
                  src={`data:image/jpeg;base64,${detalle.fotoCaptura}`}
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
