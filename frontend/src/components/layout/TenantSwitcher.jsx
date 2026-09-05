import { useEffect, useRef, useState } from 'react';
import {
  Building,
  Building2,
  ChevronDown,
  ChevronUp,
  Globe,
  Home,
  RefreshCw,
} from 'lucide-react';
import { useTenant } from '../../lib/TenantContext.jsx';
import { cn } from '../../lib/utils.js';

/**
 * TenantSwitcher — selector de contexto multi-tenant SAED 2.0.
 *
 * Muestra la organización/propiedad/unidad activa con estándar Enterprise SaaS.
 * Si el usuario tiene múltiples asignaciones (ej. SUPERADMIN / ADMIN_ORGANIZACION),
 * permite cambiar entre ellas con interacción accesible.
 */
export default function TenantSwitcher({ compact = false, className = '' }) {
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
    function onKeyDown(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onClickOutside);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, []);

  if (loading) {
    return (
      <div
        className={cn(
          'inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-border/60 bg-muted/40 text-muted-foreground text-xs font-medium',
          className
        )}
        title="Cargando contexto"
      >
        <RefreshCw className="h-3.5 w-3.5 animate-spin text-primary" aria-hidden="true" />
        {!compact && <span>Cargando contexto...</span>}
      </div>
    );
  }

  if (!activeAssignment) {
    return (
      <div
        className={cn(
          'inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-dashed border-border text-muted-foreground text-xs font-medium',
          className
        )}
        title="Sin contexto asignado"
      >
        <Building2 className="h-3.5 w-3.5 opacity-50" aria-hidden="true" />
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

  const getScopeIcon = (scope) => {
    switch (scope) {
      case 'GLOBAL':
        return <Globe className="h-4 w-4 text-primary" aria-hidden="true" />;
      case 'ORGANIZACION':
        return <Building2 className="h-4 w-4 text-blue-600 dark:text-blue-400" aria-hidden="true" />;
      case 'PROPIEDAD':
        return <Building className="h-4 w-4 text-emerald-600 dark:text-emerald-400" aria-hidden="true" />;
      default:
        return <Home className="h-4 w-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />;
    }
  };

  return (
    <div className={cn('relative inline-flex items-center', className)} ref={ref}>
      <button
        type="button"
        className={cn(
          'inline-flex items-center gap-2 px-3 py-1.5 min-h-[38px] rounded-lg border border-border/80 bg-background hover:bg-muted/60 text-foreground text-xs font-medium transition-all shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 max-w-[280px] truncate',
          open && 'ring-2 ring-primary/20 border-primary',
          canSwitchTenant ? 'cursor-pointer' : 'cursor-default'
        )}
        onClick={() => canSwitchTenant && setOpen((o) => !o)}
        title={label}
        aria-haspopup={canSwitchTenant ? 'listbox' : undefined}
        aria-expanded={canSwitchTenant ? open : undefined}
      >
        {getScopeIcon(activeAssignment.scope)}
        {!compact && <span className="truncate font-semibold text-foreground/90">{label}</span>}
        {canSwitchTenant && (
          <span className="text-muted-foreground ml-0.5">
            {open ? (
              <ChevronUp className="h-3.5 w-3.5" aria-hidden="true" />
            ) : (
              <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
            )}
          </span>
        )}
      </button>

      {open && canSwitchTenant && (
        <ul
          className="absolute top-[calc(100%+6px)] right-0 z-50 min-w-[260px] max-w-[340px] p-1.5 bg-popover text-popover-foreground border border-border rounded-xl shadow-xl space-y-0.5 animate-in fade-in-0 zoom-in-95"
          role="listbox"
        >
          <li className="px-2 py-1 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase border-b border-border/50 mb-1">
            Asignaciones de Acceso
          </li>
          {assignments.map((a) => {
            const itemLabel = [
              a.roleCode,
              a.idOrganizacion ? `Org ${a.idOrganizacion}` : null,
              a.idPropiedad ? `Prop ${a.idPropiedad}` : null,
              a.idUnidad ? `Unidad ${a.idUnidad}` : null,
            ]
              .filter(Boolean)
              .join(' · ');
            const isSelected = a.idAsignacion === activeAssignment.idAsignacion;
            return (
              <li key={a.idAsignacion}>
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={cn(
                    'w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-xs text-left transition-colors font-medium',
                    isSelected
                      ? 'bg-primary/10 text-primary font-semibold'
                      : 'text-foreground/80 hover:bg-muted hover:text-foreground'
                  )}
                  onClick={() => {
                    selectAssignment(a.idAsignacion);
                    setOpen(false);
                  }}
                >
                  {getScopeIcon(a.scope)}
                  <span className="truncate flex-1">{itemLabel}</span>
                  {isSelected && (
                    <span className="h-1.5 w-1.5 rounded-full bg-primary flex-shrink-0" />
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}