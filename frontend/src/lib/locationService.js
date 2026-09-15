/**
 * Service for fetching Colombian administrative divisions (Departments and Municipalities)
 * via the public API https://api-colombia.com/api/v1 with multi-tier caching and offline fallback.
 */

import {
  DEPARTAMENTOS_COLOMBIA as FALLBACK_DEPARTAMENTOS,
  COLOMBIA_LOCATIONS as FALLBACK_LOCATIONS,
  findDepartamentoByCiudad as fallbackFindDept,
} from './colombiaData.js';
import { useState, useEffect } from 'react';

const API_BASE_URL = 'https://api-colombia.com/api/v1';

// In-memory runtime cache
const memoryCache = {
  departments: null,
  citiesByDeptKey: new Map(),
};

const normalizeStr = (str) =>
  str ? str.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase() : '';

/**
 * Fetch all Colombian departments.
 * Returns array of objects: [{ id, name }]
 */
export async function fetchDepartamentos() {
  // 1. In-memory cache
  if (memoryCache.departments && memoryCache.departments.length > 0) {
    return memoryCache.departments;
  }

  // 2. SessionStorage cache
  if (typeof window !== 'undefined' && window.sessionStorage) {
    try {
      const cached = window.sessionStorage.getItem('saed_colombia_deptos_v1');
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Array.isArray(parsed) && parsed.length > 0) {
          memoryCache.departments = parsed;
          return parsed;
        }
      }
    } catch {
      // Ignore storage errors
    }
  }

  // 3. API call
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const response = await fetch(`${API_BASE_URL}/Department`, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    clearTimeout(timeoutId);

    if (response.ok) {
      const data = await response.json();
      if (Array.isArray(data) && data.length > 0) {
        const depts = data
          .map((d) => {
            const rawName = (d.name || '').trim();
            // Standardize Bogotá to Bogotá D.C. for platform consistency
            const name = normalizeStr(rawName) === 'bogota' ? 'Bogotá D.C.' : rawName;
            return {
              id: d.id,
              name,
            };
          })
          .sort((a, b) => a.name.localeCompare(b.name, 'es'));

        memoryCache.departments = depts;

        if (typeof window !== 'undefined' && window.sessionStorage) {
          try {
            window.sessionStorage.setItem('saed_colombia_deptos_v1', JSON.stringify(depts));
          } catch {
            // Ignore quota errors
          }
        }
        return depts;
      }
    }
  } catch (err) {
    console.warn('[locationService] API unavailable, using offline fallback departments:', err?.message || err);
  }

  // 4. Fallback to offline dataset
  const fallback = FALLBACK_DEPARTAMENTOS.map((name, idx) => ({
    id: idx + 2000,
    name,
  }));
  memoryCache.departments = fallback;
  return fallback;
}

/**
 * Fetch municipalities for a given department (by name or ID).
 * Returns array of string names sorted alphabetically.
 */
export async function fetchCiudadesPorDepartamento(departamentoNameOrId) {
  if (!departamentoNameOrId) return [];

  const depts = await fetchDepartamentos();
  let dept = null;

  if (typeof departamentoNameOrId === 'number' || (!isNaN(Number(departamentoNameOrId)) && String(departamentoNameOrId).trim() !== '')) {
    dept = depts.find((d) => d.id === Number(departamentoNameOrId));
  }

  if (!dept && typeof departamentoNameOrId === 'string') {
    const searchNorm = normalizeStr(departamentoNameOrId);
    dept = depts.find((d) => {
      const dNorm = normalizeStr(d.name);
      return dNorm === searchNorm ||
        (searchNorm.includes('bogota') && dNorm.includes('bogota'));
    });
  }

  const deptKey = dept ? String(dept.id) : normalizeStr(String(departamentoNameOrId));

  // 1. In-memory cache
  if (memoryCache.citiesByDeptKey.has(deptKey)) {
    return memoryCache.citiesByDeptKey.get(deptKey);
  }

  // 2. SessionStorage cache
  if (typeof window !== 'undefined' && window.sessionStorage && dept) {
    try {
      const cached = window.sessionStorage.getItem(`saed_colombia_cities_${dept.id}_v1`);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Array.isArray(parsed) && parsed.length > 0) {
          memoryCache.citiesByDeptKey.set(deptKey, parsed);
          return parsed;
        }
      }
    } catch {
      // Ignore storage errors
    }
  }

  // 3. API call if department ID is from the API (< 2000)
  if (dept && dept.id < 2000) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 6000);

      const response = await fetch(`${API_BASE_URL}/Department/${dept.id}/cities`, {
        signal: controller.signal,
        headers: { Accept: 'application/json' },
      });
      clearTimeout(timeoutId);

      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          let cities = data
            .map((c) => (c.name || '').trim())
            .filter(Boolean)
            .sort((a, b) => a.localeCompare(b, 'es'));

          // Remove duplicates
          cities = Array.from(new Set(cities));

          // If Bogotá, ensure both "Bogotá" and "Bogotá D.C." are present for compatibility
          if (normalizeStr(dept.name).includes('bogota')) {
            if (!cities.includes('Bogotá')) cities.unshift('Bogotá');
            if (!cities.includes('Bogotá D.C.')) cities.unshift('Bogotá D.C.');
          }

          memoryCache.citiesByDeptKey.set(deptKey, cities);

          if (typeof window !== 'undefined' && window.sessionStorage) {
            try {
              window.sessionStorage.setItem(`saed_colombia_cities_${dept.id}_v1`, JSON.stringify(cities));
            } catch {
              // Ignore quota errors
            }
          }
          return cities;
        }
      }
    } catch (err) {
      console.warn(`[locationService] API unavailable for cities of ${dept.name}, using fallback:`, err?.message || err);
    }
  }

  // 4. Fallback to offline dataset
  let fallbackCities = [];
  const targetNorm = normalizeStr(String(departamentoNameOrId));
  for (const [dName, cList] of Object.entries(FALLBACK_LOCATIONS)) {
    const dNorm = normalizeStr(dName);
    if (dNorm === targetNorm || (dNorm.includes('bogota') && targetNorm.includes('bogota'))) {
      fallbackCities = [...cList];
      break;
    }
  }

  if (fallbackCities.length === 0 && targetNorm.includes('bogota')) {
    fallbackCities = ['Bogotá', 'Bogotá D.C.'];
  }

  memoryCache.citiesByDeptKey.set(deptKey, fallbackCities);
  return fallbackCities;
}

/**
 * Reverse-lookup department by city name.
 * Uses local fast index for immediate synchronous execution.
 */
export function findDepartamentoByCiudad(ciudad) {
  return fallbackFindDept(ciudad);
}

/**
 * Custom React hook for location selector components.
 */
export function useColombiaLocations(selectedDept = '') {
  const [departments, setDepartments] = useState(FALLBACK_DEPARTAMENTOS);
  const [cities, setCities] = useState([]);
  const [loadingDepts, setLoadingDepts] = useState(true);
  const [loadingCities, setLoadingCities] = useState(false);

  // Load departments once
  useEffect(() => {
    let isMounted = true;
    fetchDepartamentos().then((deptsList) => {
      if (isMounted) {
        setDepartments(deptsList.map((d) => d.name));
        setLoadingDepts(false);
      }
    }).catch(() => {
      if (isMounted) setLoadingDepts(false);
    });
    return () => {
      isMounted = false;
    };
  }, []);

  // Load cities whenever department changes
  useEffect(() => {
    if (!selectedDept) {
      setCities([]);
      return;
    }

    let isMounted = true;
    setLoadingCities(true);

    // Set immediate fallback so UI is not empty while waiting for API
    const immediateFallback = FALLBACK_LOCATIONS[selectedDept] || [];
    if (immediateFallback.length > 0) {
      setCities(immediateFallback);
    }

    fetchCiudadesPorDepartamento(selectedDept)
      .then((citiesList) => {
        if (isMounted) {
          setCities(citiesList);
          setLoadingCities(false);
        }
      })
      .catch(() => {
        if (isMounted) setLoadingCities(false);
      });

    return () => {
      isMounted = false;
    };
  }, [selectedDept]);

  return {
    departments,
    cities,
    loadingDepts,
    loadingCities,
  };
}
