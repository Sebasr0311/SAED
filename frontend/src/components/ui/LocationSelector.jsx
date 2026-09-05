import React, { useMemo, useEffect } from 'react';
import { Label } from './label.tsx';
import {
  PAISES,
  DEPARTAMENTOS_COLOMBIA,
  COLOMBIA_LOCATIONS,
  findDepartamentoByCiudad,
} from '../../lib/colombiaData';

/**
 * Reusable cascading location selector for Colombia:
 * Country (País) -> Department (Departamento) -> City (Ciudad)
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
}) {
  // Determine effective department
  const effectiveDept = useMemo(() => {
    if (departamento && COLOMBIA_LOCATIONS[departamento]) {
      return departamento;
    }
    return findDepartamentoByCiudad(ciudad);
  }, [departamento, ciudad]);

  // Available cities for the active department
  const availableCities = useMemo(() => {
    return COLOMBIA_LOCATIONS[effectiveDept] || ['Bogotá'];
  }, [effectiveDept]);

  // Ensure ciudad matches one of the available cities or fallback
  const effectiveCity = useMemo(() => {
    if (ciudad && availableCities.includes(ciudad)) {
      return ciudad;
    }
    return availableCities[0] || '';
  }, [ciudad, availableCities]);

  // Handle department change
  const handleDeptChange = (e) => {
    const newDept = e.target.value;
    const citiesForDept = COLOMBIA_LOCATIONS[newDept] || [];
    const newCity = citiesForDept[0] || '';
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

  const selectClasses =
    'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-sm ring-offset-background transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:cursor-not-allowed disabled:opacity-50 text-foreground cursor-pointer';

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
          {DEPARTAMENTOS_COLOMBIA.map((dept) => (
            <option key={dept} value={dept}>
              {dept}
            </option>
          ))}
        </select>
      </div>

      {/* Ciudad */}
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-ciudad`} className="text-xs font-semibold uppercase text-muted-foreground">
          Ciudad {required && '*'}
        </Label>
        <select
          id={`${idPrefix}-ciudad`}
          value={effectiveCity}
          onChange={handleCityChange}
          disabled={disabled}
          className={selectClasses}
        >
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
