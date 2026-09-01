import { useCallback } from 'react';
import api from './api.js';
import { useTenant } from './TenantContext.jsx';

/**
 * useTenantApi — wrapper de api.js que inyecta X-Assignment-Id (y opcionalmente
 * headers de contexto) en cada request, para que el backend resuelva el
 * contexto RLS correcto por peticion (JwtAuthenticationFilter).
 *
 * Uso:
 *   const tenantApi = useTenantApi();
 *   const data = await tenantApi.get('/units');
 *   const res = await tenantApi.post('/visitas', body);
 */
export function useTenantApi() {
  const { activeAssignmentId } = useTenant();

  const headers = useCallback(
    (extra) => {
      const h = { ...(extra || {}) };
      if (activeAssignmentId != null) {
        h['X-Assignment-Id'] = String(activeAssignmentId);
      }
      return h;
    },
    [activeAssignmentId]
  );

  const get = useCallback(
    (url, opts) => api.get(url, { ...(opts || {}), headers: headers(opts?.headers) }),
    [headers]
  );

  const post = useCallback(
    (url, body, opts) =>
      api.post(url, body, { ...(opts || {}), headers: headers(opts?.headers) }),
    [headers]
  );

  const put = useCallback(
    (url, body, opts) =>
      api.put(url, body, { ...(opts || {}), headers: headers(opts?.headers) }),
    [headers]
  );

  const del = useCallback(
    (url, body, opts) =>
      api.del(url, body, { ...(opts || {}), headers: headers(opts?.headers) }),
    [headers]
  );

  const patch = useCallback(
    (url, body, opts) =>
      api.patch(url, body, { ...(opts || {}), headers: headers(opts?.headers) }),
    [headers]
  );

  return { get, post, put, del, delete: del, patch };
}

export default useTenantApi;