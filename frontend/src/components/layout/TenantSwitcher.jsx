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

  const formatRoleName = (code) => {
    switch (code) {
      case 'SUPERADMIN':
        return 'Super Admin';
      case 'ADMIN_ORGANIZACION':
        return 'Admin Organización';
      case 'ADMIN_PROPIEDAD':
        return 'Admin Propiedad';
      case 'PORTERO':
        return 'Portería';
      case 'RESIDENTE':
        return 'Residente';
      case 'PROPIETARIO_UNIDAD':
        return 'Propietario';
      default:
        return code || '';
    }
  };

  const getAssignmentPrimaryLabel = (a) => {
    if (!a) return 'Sin contexto';
    if (a.scope === 'GLOBAL' || a.roleCode === 'SUPERADMIN') {
      return 'Plataforma Global';
    }
    if (a.nombrePropiedad) {
      return a.nombrePropiedad;
    }
    if (a.idPropiedad) {
      return `Propiedad #${a.idPropiedad}`;
    }
    if (a.nombreOrganizacion) {
      return a.nombreOrganizacion;
    }
    if (a.idOrganizacion) {
      return `Organización #${a.idOrganizacion}`;
    }
    return formatRoleName(a.roleCode);
  };

  const getAssignmentSubLabel = (a) => {
    if (!a) return null;
    if (a.scope === 'GLOBAL' || a.roleCode === 'SUPERADMIN') {
      return 'Administración General';
    }
    const parts = [];
    if (a.identificadorUnidad) {
      parts.push(`Unidad ${a.identificadorUnidad}`);
    } else if (a.idUnidad) {
      parts.push(`Unidad #${a.idUnidad}`);
    }
    if (a.nombreOrganizacion) {
      parts.push(a.nombreOrganizacion);
    } else if (a.idOrganizacion) {
      parts.push(`Org #${a.idOrganizacion}`);
    }
    return parts.join(' · ');
  };

  const primaryActiveLabel = getAssignmentPrimaryLabel(activeAssignment);
  const subActiveLabel = getAssignmentSubLabel(activeAssignment);
  const fullTitle = [
    primaryActiveLabel,
    subActiveLabel,
    formatRoleName(activeAssignment.roleCode),
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
          'inline-flex items-center gap-2 px-3 py-1.5 min-h-[38px] rounded-lg border border-border/80 bg-background hover:bg-muted/60 text-foreground text-xs font-medium transition-all shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 max-w-[320px] truncate',
          open && 'ring-2 ring-primary/20 border-primary',
          canSwitchTenant ? 'cursor-pointer' : 'cursor-default'
        )}
        onClick={() => canSwitchTenant && setOpen((o) => !o)}
        title={fullTitle}
        aria-haspopup={canSwitchTenant ? 'listbox' : undefined}
        aria-expanded={canSwitchTenant ? open : undefined}
      >
        <span className="shrink-0">{getScopeIcon(activeAssignment.scope)}</span>
        {!compact && (
          <div className="flex flex-col text-left truncate leading-tight">
            <span className="truncate font-semibold text-foreground/90">
              {primaryActiveLabel}
            </span>
            {subActiveLabel && (
              <span className="truncate text-[10px] text-muted-foreground font-normal">
                {subActiveLabel}
              </span>
            )}
          </div>
        )}
        {canSwitchTenant && (
          <span className="text-muted-foreground ml-auto pl-1 shrink-0">
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
          className="absolute top-[calc(100%+6px)] right-0 z-50 min-w-[280px] max-w-[360px] p-1.5 bg-popover text-popover-foreground border border-border rounded-xl shadow-xl space-y-1 animate-in fade-in-0 zoom-in-95"
          role="listbox"
        >
          <li className="px-2.5 py-1 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase border-b border-border/50 mb-1 flex items-center justify-between">
            <span>Cambiar Copropiedad / Contexto</span>
            <span className="text-[10px] lowercase font-normal">({assignments.length})</span>
          </li>
          {assignments.map((a) => {
            const isSelected = a.idAsignacion === activeAssignment.idAsignacion;
            const primary = getAssignmentPrimaryLabel(a);
            const sub = getAssignmentSubLabel(a);
            return (
              <li key={a.idAsignacion}>
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={cn(
                    'w-full flex items-start gap-2.5 px-2.5 py-2 rounded-lg text-xs text-left transition-colors font-medium',
                    isSelected
                      ? 'bg-primary/10 text-primary font-semibold'
                      : 'text-foreground/80 hover:bg-muted hover:text-foreground'
                  )}
                  onClick={() => {
                    selectAssignment(a.idAsignacion);
                    setOpen(false);
                  }}
                >
                  <span className="mt-0.5 shrink-0">{getScopeIcon(a.scope)}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-1.5">
                      <span className={cn('truncate', isSelected ? 'font-bold text-primary' : 'font-semibold text-foreground')}>
                        {primary}
                      </span>
                      <span className="text-[9px] px-1.5 py-0.5 rounded bg-muted/80 text-muted-foreground uppercase font-mono tracking-tight shrink-0">
                        {formatRoleName(a.roleCode)}
                      </span>
                    </div>
                    {sub && (
                      <p className="text-[11px] text-muted-foreground truncate mt-0.5">
                        {sub}
                      </p>
                    )}
                  </div>
                  {isSelected && (
                    <span className="h-1.5 w-1.5 rounded-full bg-primary flex-shrink-0 self-center" />
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