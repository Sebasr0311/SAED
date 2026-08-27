import { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import api from './api.js';
import { useAuth } from './AuthContext.jsx';

/**
 * TenantContext — contexto multi-tenant de SAED 2.0.
 *
 * Resuelve las asignaciones ACTIVAS del usuario autenticado (via /me/contexts)
 * y expone:
 *  - assignments: todas las asignaciones activas del usuario
 *  - activeAssignment: la seleccionada (idAsignacion para X-Assignment-Id)
 *  - activeOrg / activeProperty / activeUnit: el tenant seleccionado
 *  - selectAssignment(id): cambia el tenant activo (SUPERADMIN/ADMIN_*)
 *
 * El header X-Assignment-Id se agrega en useTenantApi() para que el backend
 * resuelva el contexto RLS correcto por request.
 */
const TenantContext = createContext(null);

export function TenantProvider({ children }) {
  const { user, isAuthenticated } = useAuth();
  const [assignments, setAssignments] = useState([]);
  const [activeAssignmentId, setActiveAssignmentId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Cargar asignaciones cuando el usuario se autentica
  useEffect(() => {
    if (!isAuthenticated || !user?.idUsuario) {
      setAssignments([]);
      setActiveAssignmentId(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .get('/me/contexts')
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : [];
        setAssignments(list);
        // Seleccionar por defecto:
        // 1) la unica asignacion, 2) GLOBAL/SUPERADMIN, 3) la primera
        if (list.length === 1) {
          setActiveAssignmentId(list[0].idAsignacion);
        } else {
          const global = list.find((a) => a.scope === 'GLOBAL' || a.roleCode === 'SUPERADMIN');
          setActiveAssignmentId(global ? global.idAsignacion : list[0]?.idAsignacion ?? null);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se pudieron cargar los contextos');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, user?.idUsuario]);

  const selectAssignment = useCallback((id) => {
    if (assignments.some((a) => a.idAsignacion === id)) {
      setActiveAssignmentId(id);
    }
  }, [assignments]);

  const activeAssignment = useMemo(
    () => assignments.find((a) => a.idAsignacion === activeAssignmentId) || null,
    [assignments, activeAssignmentId]
  );

  const canSwitchTenant = useMemo(() => {
    const scopes = assignments.map((a) => a.scope);
    // Solo SUPERADMIN / ADMIN_ORGANIZACION pueden cambiar de tenant
    return scopes.includes('GLOBAL') || scopes.filter((s) => s === 'ORGANIZACION').length > 1;
  }, [assignments]);

  const value = useMemo(
    () => ({
      assignments,
      activeAssignment,
      activeAssignmentId,
      activeOrgId: activeAssignment?.idOrganizacion ?? null,
      activePropertyId: activeAssignment?.idPropiedad ?? null,
      activeUnitId: activeAssignment?.idUnidad ?? null,
      activeRoleCode: activeAssignment?.roleCode ?? null,
      activeScope: activeAssignment?.scope ?? null,
      canSwitchTenant,
      selectAssignment,
      loading,
      error,
      reload: () => {
        setLoading(true);
        api.get('/me/contexts').then((data) => {
          const list = Array.isArray(data) ? data : [];
          setAssignments(list);
          if (!list.some((a) => a.idAsignacion === activeAssignmentId)) {
            setActiveAssignmentId(list[0]?.idAsignacion ?? null);
          }
        }).catch((e) => setError(e.message)).finally(() => setLoading(false));
      },
    }),
    [assignments, activeAssignment, activeAssignmentId, canSwitchTenant, selectAssignment, loading, error]
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenant() {
  const ctx = useContext(TenantContext);
  if (!ctx) throw new Error('useTenant must be used inside TenantProvider');
  return ctx;
}

export default TenantContext;