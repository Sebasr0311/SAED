// SAED 2.0 Identity & Access Control Layer
// Separation of Platform SaaS (SUPERADMIN) from Property Operations (ADMIN_PROPIEDAD, PORTERO, RESIDENTE)

export function normalizeRole(rol) {
  if (!rol) return rol;
  const r = String(rol).toUpperCase();
  if (r === 'SUPERADMIN') {
    return 'SUPERADMIN';
  }
  if (r === 'ADMIN_ORGANIZACION') {
    return 'ADMIN_ORGANIZACION';
  }
  if (r === 'ADMIN_PROPIEDAD' || r === 'ADMIN' || r === 'ADMINISTRADOR') {
    return 'ADMIN_PROPIEDAD';
  }
  if (r === 'PORTERO' || r === 'VIGILANTE') {
    return 'PORTERO';
  }
  if (r === 'RESIDENTE' || r === 'PROPIETARIO' || r === 'PROPIETARIO_UNIDAD') {
    return 'RESIDENTE';
  }
  return rol;
}

export const ROLE_HOME = {
  SUPERADMIN: '/superadmin/dashboard',
  ADMIN_ORGANIZACION: '/org/dashboard',
  ADMIN_PROPIEDAD: '/dashboard',
  PORTERO: '/portero-dashboard',
  RESIDENTE: '/residente-dashboard',
};

const ACCESS_BY_ROLE = {
  SUPERADMIN: [
    '/superadmin/dashboard',
    '/superadmin/organizaciones',
    '/superadmin/propiedades',
    '/superadmin/planes',
    '/superadmin/membresias',
    '/superadmin/administradores',
    '/superadmin/auditoria',
    '/superadmin/metricas',
    '/superadmin/configuracion',
  ],
  ADMIN_ORGANIZACION: [
    '/org/dashboard',
    '/org/organizacion',
    '/org/propiedades',
    '/org/admins',
    '/org/plan',
    '/org/auditoria',
  ],
  ADMIN_PROPIEDAD: [
    '/dashboard',
    '/personas',
    '/residentes',
    '/unidades',
    '/apartamentos',
    '/contratos',
    '/contratos-proveedor',
    '/propiedades',
    '/roles-asignaciones',
    '/reportes',
    '/usuarios',
    '/visitas',
    '/historial-visitas',
    '/paquetes-admin',
    '/parqueaderos',
    '/escanner-qr',
    '/pagos',
    '/cartera',
    '/presupuestos',
    '/gastos',
    '/conciliaciones',
    '/paz-y-salvos',
    '/flujo-caja',
    '/ganancias',
    '/multas',
    '/sanciones-admin',
    '/obras-admin',
    '/mantenimiento-admin',
    '/mantenimientos',
    '/asambleas-admin',
    '/asambleas',
    '/polizas-admin',
    '/polizas',
    '/emergencias-admin',
    '/emergencias',
    '/incidentes-admin',
    '/alertas',
    '/avisos',
    '/quejas-admin',
    '/reservas-admin',
    '/porterias-admin',
    '/coarrendatarios',
  ],
  PORTERO: [
    '/portero-dashboard',
    '/paquetes',
    '/visitas',
    '/parqueaderos',
    '/escanner-qr',
    '/incidentes-admin',
  ],
  RESIDENTE: [
    '/residente-dashboard',
    '/res-perfil',
    '/res-apartamento',
    '/res-cuotas',
    '/res-frecuentes',
    '/res-buzon',
    '/res-visita',
    '/res-quejas',
    '/res-reservas',
    '/res-sanciones',
    '/res-obras',
    '/res-incidentes',
  ],
};

export function roleCanAccess(pathname, rol) {
  if (!pathname || !rol) return false;
  const norm = normalizeRole(rol);
  const allowed = ACCESS_BY_ROLE[norm];
  if (!allowed) return false;
  return allowed.some((path) => pathname === path || pathname.startsWith(path + '/'));
}