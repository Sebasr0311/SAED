import { useCallback, useEffect, useRef, useState } from 'react';
import api from './api.js';

/**
 * Normaliza la respuesta del backend a un objeto { items, totalItems, totalPages, raw }.
 * Acepta: array directo, { items, ... }, o null.
 */
function normalize(data) {
  if (data == null) {
    return { items: [], totalItems: 0, totalPages: 1, raw: null };
  }
  if (Array.isArray(data)) {
    return {
      items: data,
      totalItems: data.length,
      totalPages: 1,
      raw: data,
    };
  }
  if (data.items) {
    return {
      ...data,
      items: data.items,
      raw: data,
    };
  }
  return { items: [], totalItems: 0, totalPages: 1, raw: data };
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

  const refetch = useCallback(() => fetcher().then((d) => setData(normalize(d))).catch(setError), deps);

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
