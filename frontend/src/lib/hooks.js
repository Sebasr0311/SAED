import { useCallback, useEffect, useRef, useState } from 'react';
import api from './api.js';

/**
 * Normaliza la respuesta del backend a un contrato estándar de datos:
 * - Desenvuelve transparentemente ApiResponse<T> ({ status: 'success', data: ... } o { success: true, data: ... })
 * - Listas: { items: Array, totalItems: Number, totalPages: Number, raw: Array, data: Array }
 * - Objetos/Resúmenes: { ...Payload, items: null, totalItems: 0, totalPages: 1, raw: Payload, data: Payload }
 * - Paginados: { ...Payload, items: Array, totalItems: Number, totalPages: Number, raw: Payload, data: Payload }
 * - Vacíos/204: { items: [], totalItems: 0, totalPages: 1, raw: null, data: null }
 */
export function normalize(response) {
  if (response == null || response === '') {
    return { items: [], totalItems: 0, totalPages: 1, raw: null, data: null };
  }

  // Desempaquetar ApiResponse<T> si viene envuelto
  let payload = response;
  let isEnvelope = false;

  if (
    typeof response === 'object' &&
    !Array.isArray(response) &&
    response !== null &&
    ('data' in response || 'status' in response || 'success' in response) &&
    response.data !== undefined
  ) {
    payload = response.data;
    isEnvelope = true;
  }

  if (payload == null) {
    return { items: [], totalItems: 0, totalPages: 1, raw: response, data: null };
  }

  // Caso 1: Array directo o lista desenvuelta
  if (Array.isArray(payload)) {
    return {
      items: payload,
      totalItems: payload.length,
      totalPages: 1,
      raw: payload,
      data: payload,
      _envelope: isEnvelope ? response : null,
    };
  }

  // Caso 2 & 3: Objeto (Paginado o DTO / Mapa / Resumen)
  if (typeof payload === 'object') {
    const list = Array.isArray(payload.content)
      ? payload.content
      : Array.isArray(payload.items)
      ? payload.items
      : null;

    if (list !== null) {
      const total =
        payload.totalElements ??
        payload.totalItems ??
        payload.total ??
        list.length;
      const pages =
        payload.totalPages ??
        payload.pages ??
        (payload.size ? Math.ceil(total / payload.size) : 1);

      return {
        ...payload,
        items: list,
        totalItems: total,
        totalPages: pages,
        raw: payload,
        data: payload,
        _envelope: isEnvelope ? response : null,
      };
    }

    // Objeto único, mapa de resumen, métricas o DTO
    return {
      ...payload,
      items: null,
      totalItems: 0,
      totalPages: 1,
      raw: payload,
      data: payload,
      _envelope: isEnvelope ? response : null,
    };
  }

  // Caso 4: Primitivo (String, Number, Boolean)
  return {
    items: [],
    totalItems: 0,
    totalPages: 1,
    raw: payload,
    data: payload,
    value: payload,
    _envelope: isEnvelope ? response : null,
  };
}

export function useFetch(fetcher, deps = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetcher()
      .then((d) => {
        if (!cancelled) {
          setData(normalize(d));
          setError(null);
        }
      })
      .catch((e) => {
        if (!cancelled) setError(e);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, deps);

  const refetch = useCallback(() => fetcher().then((d) => setData(normalize(d))).catch(setError), [fetcher]);

  return { data, loading, error, refetch };
}

export function useTiposDocumento() {
  const { data, loading, error } = useFetch(() => api.get('/tipos-documento'), []);
  return { tiposDoc: data?.items || [], loading, error };
}

/**
 * Feedback de validación en tiempo real (portado de validarTelefonoTiempoReal /
 * validarEmailTiempoReal del legacy a React).
 *
 * Un campo "tocado" (blur o ya interactuado) muestra su error en vivo mientras
 * se escribe; un campo intacto y vacío no muestra error agresivo.
 *
 * Uso:
 *   const { touch, fieldError } = useLiveValidation();
 *   <Input
 *     value={form.telefono}
 *     onBlur={() => touch('telefono')}
 *     error={fieldError('telefono', valTelefono(form.telefono, { required: false }))}
 *   />
 */
export function useLiveValidation() {
  const [touched, setTouched] = useState({});

  function touch(name) {
    setTouched((t) => (t[name] ? t : { ...t, [name]: true }));
  }

  /** Devuelve el mensaje de error solo si el campo fue tocado y la validación falló. */
  function fieldError(name, result) {
    return touched[name] && result && !result.ok ? result.mensaje : undefined;
  }

  return { touched, touch, fieldError };
}
