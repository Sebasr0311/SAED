import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { formatDate } from '../lib/utils.js';

export default function ResBuzonPage() {
  const { user } = useAuth();
  const { data, loading } = useFetch(() => api.get(`/buzon/residente/${user?.idResidente}`), [user]);
  const items = data || [];

  return (
    <div>
      <PageHeader title="Buzón" subtitle="Notificaciones del administrador" />
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {loading && <div className="card empty-state">Cargando...</div>}
        {!loading && items.length === 0 && (
          <div className="card empty-state">Buzón vacío</div>
        )}
        {items.map((it) => (
          <div key={it.id} className="card">
            <div style={{ fontSize: '14px', fontWeight: 700 }}>{it.asunto}</div>
            <div style={{ marginTop: '4px', fontSize: '13px', color: '#475569' }}>{it.cuerpo}</div>
            <div style={{ marginTop: '8px', fontSize: '11px', color: '#94a3b8' }}>{formatDate(it.fecha)}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
