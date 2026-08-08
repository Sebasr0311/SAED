import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatMiles, imageSrc } from '../lib/utils.js';

const ESTADOS = ['', 'ACTIVA', 'FINALIZADA', 'CANCELADA'];
const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  ACTIVA: 'badge-activo',
  FINALIZADA: 'badge-finalizada',
  CANCELADA: 'badge-cancelado',
};

export default function VisitasPage() {
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroFecha, setFiltroFecha] = useState('');
  const [toast, setToast] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);

  const { data: dataRaw, loading, refetch } = useFetch(() => api.get('/visitas'), []);

  const filtradas = (dataRaw?.items || dataRaw || []).filter((v) => {
    if (filtroEstado && (!v.estado || v.estado !== filtroEstado)) return false;
    if (!filtroFecha) return true;
    const fecha = (v.fechaVisita || '').slice(0, 10);
    return fecha === filtroFecha;
  });

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

  async function registrarSalida(row) {
    try {
      await api.put(`/visitas/${row.idVisita}/salida`);
      setToast({ message: 'Salida registrada', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function cancelarVisita(row) {
    if (!window.confirm(`¿Cancelar la visita #${row.idVisita}?`)) return;
    try {
      await api.del(`/visitas/${row.idVisita}`);
      setToast({ message: 'Visita cancelada', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  const columns = [
    { key: 'idVisita', label: 'ID', width: 60 },
    { key: 'nombreVisitante', label: 'Visitante' },
    { key: 'documentoVisitante', label: 'Documento' },
    { key: 'numeroApartamento', label: 'Apartamento' },
    {
      key: 'fechaIngreso',
      label: 'Ingreso',
      render: (r) => formatDate(r.fechaIngreso || r.fechaVisita),
    },
    { key: 'fechaSalida', label: 'Salida', render: (r) => formatDate(r.fechaSalida) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 160,
      render: (row) => (
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row);
            }}
            className="btn btn-ghost btn-sm"
            aria-label="Ver detalle"
            title="Ver detalle"
          >
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>visibility</span>
          </button>
          {(row.estado === 'ACTIVA' || row.estado === 'PENDIENTE') && (
            <Button
              onClick={(e) => {
                e.stopPropagation();
                registrarSalida(row);
              }}
              style={{ padding: '4px 10px', fontSize: '11px' }}
            >
              Salida
            </Button>
          )}
          {row.estado !== 'FINALIZADA' && row.estado !== 'CANCELADA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                cancelarVisita(row);
              }}
              className="btn btn-ghost btn-sm"
              aria-label="Cancelar"
              title="Cancelar"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px', color: '#e11d48' }}>cancel</span>
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Visitas"
        subtitle="Registro de visitas al edificio"
        action={
          <div className="filters">
            <Select
              id="f-estado"
              value={filtroEstado}
              onChange={(e) => setFiltroEstado(e.target.value)}
              className="filter-select"
            >
              {ESTADOS.map((e) => (
                <option key={e || 'all'} value={e}>
                  {e || 'Todos'}
                </option>
              ))}
            </Select>
            <input
              id="f-fecha"
              type="date"
              value={filtroFecha}
              onChange={(e) => setFiltroFecha(e.target.value)}
              className="form-control"
              style={{ width: '160px' }}
            />
          </div>
        }
      />
      <DataTable
        columns={columns}
        rows={filtradas}
        loading={loading}
        empty="No hay visitas"
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
              <span>Residente</span>
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
            {detalle.esFrecuente && (
              <span className="badge badge-info" style={{ alignSelf: 'flex-start' }}>
                Visitante Frecuente
              </span>
            )}
            {detalle.fotoCaptura && (
              <img
                src={imageSrc(detalle.fotoCaptura)}
                alt="Foto"
                style={{ maxWidth: '100%', borderRadius: '8px', marginTop: '8px', cursor: 'zoom-in' }}
                onClick={() => setFotoGrande(imageSrc(detalle.fotoCaptura))}
              />
            )}
          </div>
        )}
      </Modal>

      {fotoGrande && (
        <div
          onClick={() => setFotoGrande(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'zoom-out',
          }}
        >
          <img
            src={fotoGrande}
            alt="Foto"
            style={{ maxWidth: '90%', maxHeight: '90%', borderRadius: '8px' }}
          />
        </div>
      )}

      <Toast toast={toast} />
    </div>
  );
}
