import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

export default function ResApartamentoPage() {
  const { user } = useAuth();
  const { data } = useFetch(() => api.get(`/apartamentos/residente/${user?.idResidente}`), [user]);
  const a = data || {};

  return (
    <div>
      <PageHeader title="Mi Apartamento" />
      <div className="card max-w-xl space-y-3">
        <div className="flex justify-between text-sm">
          <span className="text-on-surface-variant">Número</span>
          <span className="font-semibold">{a.numero || '—'}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-on-surface-variant">Piso</span>
          <span className="font-semibold">{a.piso || '—'}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-on-surface-variant">Tipo</span>
          <span className="font-semibold">{a.tipo || '—'}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-on-surface-variant">Área</span>
          <span className="font-semibold">{a.area ? `${a.area} m²` : '—'}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-on-surface-variant">Estado</span>
          <span className="font-semibold">{a.estado || '—'}</span>
        </div>
      </div>
    </div>
  );
}
