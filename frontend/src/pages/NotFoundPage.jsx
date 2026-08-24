import { useNavigate } from 'react-router-dom';
import { PageHeader } from '../components/ui/PageHeader.jsx';

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <div>
      <PageHeader title="Página no encontrada" subtitle="La dirección que buscás no existe o fue movida." />
      <div className="table-container p-8">
        <div className="empty-state">
          <div className="empty-icon" aria-hidden="true">
            <span className="material-symbols-outlined">explore_off</span>
          </div>
          <div className="empty-title">Error 404</div>
          <p className="empty-subtitle">
            Verificá el enlace o volvé al inicio del sistema.
          </p>
          <div style={{ display: 'flex', gap: '8px', marginTop: '16px', justifyContent: 'center' }}>
            <button type="button" className="btn btn-primary" onClick={() => navigate('/')}>
              Ir al inicio
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
