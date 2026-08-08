import { useEffect, useRef, useState } from 'react';
import { mapEntityId } from './mappers.js';
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
      items: data.map(mapEntityId),
      totalItems: data.length,
      totalPages: 1,
      raw: data,
    };
  }
  if (data.items) {
    return {
      ...data,
      items: data.items.map(mapEntityId),
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

  return { data, loading, error, refetch: () => fetcher().then((d) => setData(normalize(d))).catch(setError) };
}

/**
 * Devuelve solo el array de items (atajo para no hacer data?.items || [] en cada pagina).
 * Si la respuesta falla, devuelve [].
 */
export function useArray(fetcher, deps = []) {
  const { data, loading, error } = useFetch(fetcher, deps);
  return {
    data: data?.items ?? [],
    loading,
    error,
    pagination: data ? { totalItems: data.totalItems, totalPages: data.totalPages, raw: data.raw } : null,
  };
}

// Catalogo de tipos de documento: usa /tipos-documento y cae al seed Oracle
// (CC=1, TI=2, CE=3, PP=4, PEP=5, RC=6, NIT=7) si el endpoint falla.
const TIPOS_DOC_FALLBACK = [
  { idTipoDoc: 1, descripcion: 'Cédula de Ciudadanía' },
  { idTipoDoc: 2, descripcion: 'Tarjeta de Identidad' },
  { idTipoDoc: 3, descripcion: 'Cédula de Extranjería' },
  { idTipoDoc: 4, descripcion: 'Pasaporte' },
  { idTipoDoc: 5, descripcion: 'Permiso Especial de Permanencia' },
  { idTipoDoc: 6, descripcion: 'Registro Civil' },
  { idTipoDoc: 7, descripcion: 'NIT' },
];

export function useTiposDocumento() {
  const { data, loading, error } = useFetch(() => api.get('/tipos-documento'), []);
  const items = data?.items?.length ? data.items : TIPOS_DOC_FALLBACK;
  return { tiposDoc: items, loading, error };
}

export function useToast() {
  const [toast, setToast] = useState(null);
  const timerRef = useRef(null);

  function show(message, type = 'info') {
    setToast({ message, type });
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setToast(null), 3500);
  }

  useEffect(() => () => timerRef.current && clearTimeout(timerRef.current), []);

  return { toast, show };
}
