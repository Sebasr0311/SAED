import { useEffect, useRef, useState } from 'react';
import { useTenant } from '../../lib/TenantContext.jsx';

/**
 * TenantSwitcher — selector de contexto multi-tenant.
 *
 * Muestra la organizacion/propiedad/unidad activa y, si el usuario tiene
 * multiples asignaciones (SUPERADMIN / ADMIN_ORGANIZACION), permite
 * cambiar entre ellas.
 */
export default function TenantSwitcher({ compact = false }) {
  const {
    assignments,
    activeAssignment,
    canSwitchTenant,
    selectAssignment,
    loading,
  } = useTenant();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    function onClickOutside(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  if (loading) {
    return (
      <div className="tenant-switcher tenant-switcher--loading" title="Cargando contexto">
        <span className="material-symbols-outlined">sync</span>
        {!compact && <span>Contexto…</span>}
      </div>
    );
  }

  if (!activeAssignment) {
    return (
      <div className="tenant-switcher tenant-switcher--empty" title="Sin contexto asignado">
        <span className="material-symbols-outlined">domain_disabled</span>
        {!compact && <span>Sin contexto</span>}
      </div>
    );
  }

  const label = [
    activeAssignment.roleCode,
    activeAssignment.idOrganizacion ? `Org ${activeAssignment.idOrganizacion}` : null,
    activeAssignment.idPropiedad ? `Prop ${activeAssignment.idPropiedad}` : null,
    activeAssignment.idUnidad ? `Unidad ${activeAssignment.idUnidad}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className="tenant-switcher" ref={ref}>
      <button
        type="button"
        className="tenant-switcher__trigger"
        onClick={() => setOpen((o) => !o)}
        title={label}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="material-symbols-outlined">apartment</span>
        {!compact && <span className="tenant-switcher__label">{label}</span>}
        {canSwitchTenant && (
          <span className="material-symbols-outlined tenant-switcher__caret">
            {open ? 'expand_less' : 'expand_more'}
          </span>
        )}
      </button>

      {open && canSwitchTenant && (
        <ul className="tenant-switcher__menu" role="listbox">
          {assignments.map((a) => {
            const itemLabel = [
              a.roleCode,
              a.idOrganizacion ? `Org ${a.idOrganizacion}` : null,
              a.idPropiedad ? `Prop ${a.idPropiedad}` : null,
              a.idUnidad ? `Unidad ${a.idUnidad}` : null,
            ]
              .filter(Boolean)
              .join(' · ');
            return (
              <li key={a.idAsignacion}>
                <button
                  type="button"
                  role="option"
                  aria-selected={a.idAsignacion === activeAssignment.idAsignacion}
                  className={`tenant-switcher__option ${
                    a.idAsignacion === activeAssignment.idAsignacion
                      ? 'tenant-switcher__option--active'
                      : ''
                  }`}
                  onClick={() => {
                    selectAssignment(a.idAsignacion);
                    setOpen(false);
                  }}
                >
                  <span className="material-symbols-outlined">
                    {a.scope === 'GLOBAL' ? 'public' : a.scope === 'ORGANIZACION' ? 'domain' : a.scope === 'PROPIEDAD' ? 'apartment' : 'home'}
                  </span>
                  {itemLabel}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}