import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/AuthContext.jsx';
import { ROLE_HOME, normalizeRole } from '../lib/access.js';

export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, loading, user } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center text-on-surface-variant" role="status" aria-live="polite">
        Cargando...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const userRole = normalizeRole(user?.rol);

  if (roles && roles.length > 0) {
    const normalizedRoles = roles.map(normalizeRole);
    if (!normalizedRoles.includes(userRole)) {
      const home = ROLE_HOME[userRole] || '/login';
      return <Navigate to={home} replace />;
    }
  }

  return children;
}
