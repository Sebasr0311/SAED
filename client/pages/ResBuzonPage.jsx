import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

export default function ResBuzonPage() {
  const { user } = useAuth();
  const { data, loading } = useFetch(() => api.get(`/buzon/residente/${user?.idResidente}`), [user]);
  const items = data || [];

  return (
    <div>
      <PageHeader title="Buzón" subtitle="Notificaciones del administrador" />
      <div className="space-y-3">
        {loading && <div className="card text-center text-on-surface-variant">Cargando...</div>}
        {!loading && items.length === 0 && (
          <div className="card text-center text-on-surface-variant">Buzón vacío</div>
        )}
        {items.map((it) => (
          <div key={it.id} className="card">
            <div className="text-sm font-semibold">{it.asunto}</div>
            <div className="mt-1 text-sm text-on-surface-variant">{it.cuerpo}</div>
            <div className="mt-2 text-xs text-on-surface-variant">{it.fecha}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
