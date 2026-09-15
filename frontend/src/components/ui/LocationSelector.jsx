import { useMemo, useEffect, useState } from 'react';
import { Label } from './label.tsx';
import {
  PAISES,
  DEPARTAMENTOS_COLOMBIA,
  COLOMBIA_LOCATIONS,
  findDepartamentoByCiudad,
  fetchDepartamentos,
  fetchCiudadesPorDepartamento,
} from '../../lib/colombiaData';

/**
 * Reusable cascading location selector for Colombia:
 * Country (País) -> Department (Departamento) -> City (Ciudad)
 * Consumes public Colombia API (https://api-colombia.com/api/v1) with offline fallback.
 */
export default function LocationSelector({
  pais = 'Colombia',
  departamento = '',
  ciudad = '',
  onChange,
  idPrefix = 'loc',
  showPais = true,
  disabled = false,
  className = '',
  gridCols = 'sm:grid-cols-3',
  required = false,
  selectClassName = '',
}) {
  const [departmentsList, setDepartmentsList] = useState(DEPARTAMENTOS_COLOMBIA);
  const [citiesList, setCitiesList] = useState([]);
  const [loadingCities, setLoadingCities] = useState(false);

  // Load official departments from API on mount
  useEffect(() => {
    let isMounted = true;
    fetchDepartamentos().then((depts) => {
      if (isMounted && Array.isArray(depts) && depts.length > 0) {
        setDepartmentsList(depts.map((d) => d.name));
      }
    }).catch(() => {
      // Fallback already in place
    });
    return () => {
      isMounted = false;
    };
  }, []);

  const normalize = (s) =>
    s ? s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase() : '';

  // Determine effective department based on input or inference
  const effectiveDept = useMemo(() => {
    if (departamento) {
      // Match against departmentsList
      const depNorm = normalize(departamento);
      const match = departmentsList.find((d) => {
        const dNorm = normalize(d);
        return dNorm === depNorm || (depNorm.includes('bogota') && dNorm.includes('bogota'));
      });
      if (match) return match;
      if (COLOMBIA_LOCATIONS[departamento]) return departamento;
    }
    if (ciudad) {
      return findDepartamentoByCiudad(ciudad);
    }
    return '';
  }, [departamento, ciudad, departmentsList]);

  // Load cities dynamically when effectiveDept changes
  useEffect(() => {
    if (!effectiveDept) {
      setCitiesList([]);
      return;
    }

    let isMounted = true;
    setLoadingCities(true);

    // Immediate fallback while API fetches
    const offlineCities = COLOMBIA_LOCATIONS[effectiveDept] || [];
    if (offlineCities.length > 0) {
      setCitiesList(offlineCities);
    }

    fetchCiudadesPorDepartamento(effectiveDept)
      .then((apiCities) => {
        if (isMounted) {
          if (Array.isArray(apiCities) && apiCities.length > 0) {
            setCitiesList(apiCities);
          }
          setLoadingCities(false);
        }
      })
      .catch(() => {
        if (isMounted) setLoadingCities(false);
      });

    return () => {
      isMounted = false;
    };
  }, [effectiveDept]);

  // Available cities for the active department
  const availableCities = useMemo(() => {
    if (citiesList.length > 0) return citiesList;
    return effectiveDept ? (COLOMBIA_LOCATIONS[effectiveDept] || []) : [];
  }, [citiesList, effectiveDept]);

  // Ensure ciudad matches one of the available cities or fallback
  const effectiveCity = useMemo(() => {
    if (ciudad && availableCities.length > 0) {
      if (availableCities.includes(ciudad)) return ciudad;
      const stripped = normalize(ciudad);
      const match = availableCities.find((c) => normalize(c) === stripped);
      if (match) return match;
    }
    return ciudad || '';
  }, [ciudad, availableCities]);

  // Handle department change
  const handleDeptChange = async (e) => {
    const newDept = e.target.value;
    let newCity = '';

    if (newDept) {
      const fallback = COLOMBIA_LOCATIONS[newDept] || [];
      newCity = fallback[0] || '';
      try {
        const fetched = await fetchCiudadesPorDepartamento(newDept);
        if (fetched && fetched.length > 0) {
          newCity = fetched[0];
        }
      } catch {
        // use fallback city
      }
    }

    if (onChange) {
      onChange({
        pais: pais || 'Colombia',
        departamento: newDept,
        ciudad: newCity,
      });
    }
  };

  // Handle city change
  const handleCityChange = (e) => {
    const newCity = e.target.value;
    if (onChange) {
      onChange({
        pais: pais || 'Colombia',
        departamento: effectiveDept,
        ciudad: newCity,
      });
    }
  };

  // Handle country change
  const handleCountryChange = (e) => {
    const newPais = e.target.value;
    if (onChange) {
      onChange({
        pais: newPais,
        departamento: effectiveDept,
        ciudad: effectiveCity,
      });
    }
  };

  const defaultSelectClasses =
    'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-sm ring-offset-background transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:cursor-not-allowed disabled:opacity-50 text-foreground cursor-pointer';

  const selectClasses = selectClassName || defaultSelectClasses;

  return (
    <div className={`grid grid-cols-1 ${showPais ? gridCols : 'sm:grid-cols-2'} gap-3 ${className}`}>
      {/* País */}
      {showPais && (
        <div className="space-y-1.5">
          <Label htmlFor={`${idPrefix}-pais`} className="text-xs font-semibold uppercase text-muted-foreground">
            País {required && '*'}
          </Label>
          <select
            id={`${idPrefix}-pais`}
            value={pais || 'Colombia'}
            onChange={handleCountryChange}
            disabled={disabled}
            className={selectClasses}
          >
            {PAISES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Departamento */}
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-depto`} className="text-xs font-semibold uppercase text-muted-foreground">
          Departamento {required && '*'}
        </Label>
        <select
          id={`${idPrefix}-depto`}
          value={effectiveDept}
          onChange={handleDeptChange}
          disabled={disabled}
          className={selectClasses}
        >
          <option value="">-- Seleccionar departamento --</option>
          {departmentsList.map((dept) => (
            <option key={dept} value={dept}>
              {dept}
            </option>
          ))}
        </select>
      </div>

      {/* Ciudad */}
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-ciudad`} className="text-xs font-semibold uppercase text-muted-foreground">
          Ciudad / Municipio {required && '*'}
        </Label>
        <select
          id={`${idPrefix}-ciudad`}
          value={effectiveCity}
          onChange={handleCityChange}
          disabled={disabled || !effectiveDept}
          className={selectClasses}
        >
          <option value="">
            {effectiveDept
              ? loadingCities && availableCities.length === 0
                ? '-- Cargando municipios... --'
                : '-- Seleccionar ciudad / municipio --'
              : '-- Primero seleccione departamento --'}
          </option>
          {availableCities.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
