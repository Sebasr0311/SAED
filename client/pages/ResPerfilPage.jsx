import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Input } from '../components/ui/Form.jsx';

export default function ResPerfilPage() {
  const { user } = useAuth();
  const { data } = useFetch(() => api.get(`/residentes/${user?.idResidente}`), [user]);
  const r = data || {};

  return (
    <div>
      <PageHeader title="Mi Perfil" subtitle="Datos personales" />
      <div className="card" style={{ maxWidth: '640px' }}>
        <div className="form-row">
          <Input id="nombres" label="Nombres" value={r.nombres || ''} readOnly />
          <Input id="apellidos" label="Apellidos" value={r.apellidos || ''} readOnly />
        </div>
        <div className="form-row">
          <Input id="documento" label="Documento" value={r.numeroDocumento || ''} readOnly />
          <Input id="telefono" label="Teléfono" value={r.telefono || ''} readOnly />
        </div>
        <div className="form-group">
          <Input id="email" label="Email" value={r.email || ''} readOnly />
        </div>
      </div>
    </div>
  );
}
