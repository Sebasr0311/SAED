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
  const [assignmentsRaw, setAssignments] = useState([]);
  const [activeAssignmentId, setActiveAssignmentIdState] = useState(() => {
    const saved = typeof window !== 'undefined' ? sessionStorage.getItem('saed_active_assignment_id') : null;
    return saved ? Number(saved) : null;
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const setActiveAssignmentId = useCallback((id) => {
    setActiveAssignmentIdState(id);
    if (typeof window !== 'undefined') {
      if (id != null) {
        sessionStorage.setItem('saed_active_assignment_id', String(id));
      } else {
        sessionStorage.removeItem('saed_active_assignment_id');
      }
    }
  }, []);

  // Derived: empty when not authenticated (avoids setState inside useEffect)
  const assignments = useMemo(
    () => (!isAuthenticated || !user?.idUsuario ? [] : assignmentsRaw),
    [isAuthenticated, user?.idUsuario, assignmentsRaw]
  );

  // Cargar asignaciones cuando el usuario se autentica
  useEffect(() => {
    if (!isAuthenticated || !user?.idUsuario) {
      if (!isAuthenticated && typeof window !== 'undefined') {
        sessionStorage.removeItem('saed_active_assignment_id');
        setActiveAssignmentIdState(null);
      }
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
        // 1) Si ya hay uno guardado y es valido, mantenerlo
        // 2) Si hay una sola asignacion, seleccionarla
        // 3) GLOBAL/SUPERADMIN, 4) la primera
        const currentSaved = typeof window !== 'undefined' ? sessionStorage.getItem('saed_active_assignment_id') : null;
        if (currentSaved && list.some((a) => a.idAsignacion === Number(currentSaved))) {
          setActiveAssignmentId(Number(currentSaved));
        } else if (list.length === 1) {
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
  }, [isAuthenticated, user?.idUsuario, setActiveAssignmentId]);

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
    return assignments.length > 1;
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