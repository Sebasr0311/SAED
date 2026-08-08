import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { formatCurrency } from '../lib/utils.js';

function DetailRow({ label, value }) {
  return (
    <div className="detail-row">
      <span style={{ color: '#475569' }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value || '—'}</span>
    </div>
  );
}

export default function ResApartamentoPage() {
  const { user } = useAuth();
  const { data: info } = useFetch(() => api.get(`/residentes/${user?.idResidente}/dashboard`), [user]);
  const d = info?.raw || info || {};
  const apto = d.apartamento || {};
  const contrato = d.contrato || {};
  const { data: residentes } = useFetch(
    () => (apto?.idApartamento ? api.get(`/residentes?idApartamento=${apto.idApartamento}`) : Promise.resolve([])),
    [apto?.idApartamento]
  );

  const a = apto;
  const c = contrato;
  const rs = residentes?.items || residentes || [];

  return (
    <div>
      <PageHeader title="Mi Apartamento" />
      <div className="card-grid-2" style={{ maxWidth: '960px' }}>
        <div className="card">
          <h3 className="card-title">Información del apartamento</h3>
          <DetailRow label="Número" value={a.numero} />
          <DetailRow label="Piso" value={a.piso} />
          <DetailRow label="Tipo" value={a.tipo} />
          <DetailRow label="Área" value={a.areaM2 ? `${a.areaM2} m²` : a.area ? `${a.area} m²` : null} />
          <DetailRow label="Capacidad" value={a.capacidadMaxima} />
          <DetailRow label="Estado" value={a.estado} />
        </div>

        <div className="card">
          <h3 className="card-title">Contrato actual</h3>
          <DetailRow label="Tipo" value={c.tipoContrato} />
          <DetailRow label="Estado" value={c.estado} />
          <DetailRow label="Fecha inicio" value={c.fechaInicio} />
          <DetailRow label="Fecha fin" value={c.fechaFin || 'Indefinido'} />
          <DetailRow label="Valor mensual" value={formatCurrency(c.valorMensual)} />
          <DetailRow label="Día de pago" value={c.diaPago} />
        </div>
      </div>

      <div className="card" style={{ maxWidth: '960px', marginTop: '16px' }}>
        <h3 className="card-title">Compañeros de apartamento ({rs.length})</h3>
        <div className="frecuentes-grid">
          {rs.map((r) => (
            <div
              key={r.idResidente || r.id}
              className="frecuente-card"
              style={{ flexDirection: 'row', alignItems: 'center', gap: '12px' }}
            >
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '50%',
                  background: '#0f2044',
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '13px',
                  fontWeight: 700,
                }}
              >
                {(r.nombres?.[0] || '?').toUpperCase()}
              </div>
              <div>
                <div className="name">
                  {r.nombres} {r.apellidos}
                </div>
                <div className="meta">{r.numeroDocumento}</div>
              </div>
            </div>
          ))}
          {rs.length === 0 && <p style={{ color: '#94a3b8' }}>Sin residentes registrados</p>}
        </div>
      </div>
    </div>
  );
}
