import { useEffect, useRef, useState } from 'react';
import { mapEntityId } from './mappers.js';

function normalize(data) {
  if (data == null) return { items: [], totalItems: 0, totalPages: 1 };
  if (Array.isArray(data)) {
    return { items: data.map(mapEntityId), totalItems: data.length, totalPages: 1 };
  }
  if (data.items) {
    return { ...data, items: data.items.map(mapEntityId) };
  }
  return { items: [], totalItems: 0, totalPages: 1 };
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
