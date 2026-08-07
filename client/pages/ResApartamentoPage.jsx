import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

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
  const { data } = useFetch(() => api.get(`/apartamentos/residente/${user?.idResidente}`), [user]);
  const a = data || {};

  return (
    <div>
      <PageHeader title="Mi Apartamento" />
      <div className="card" style={{ maxWidth: '480px' }}>
        <DetailRow label="Número" value={a.numero} />
        <DetailRow label="Piso" value={a.piso} />
        <DetailRow label="Tipo" value={a.tipo} />
        <DetailRow label="Área" value={a.area ? `${a.area} m²` : null} />
        <DetailRow label="Estado" value={a.estado} />
      </div>
    </div>
  );
}
