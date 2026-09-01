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
  ADMIN_ORGANIZACION: '/superadmin/organizaciones',
  ADMIN_PROPIEDAD: '/dashboard',
  ADMINISTRADOR: '/dashboard',
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
    '/superadmin/organizaciones',
    '/superadmin/propiedades',
  ],
  ADMIN_PROPIEDAD: [
    '/dashboard',
    '/residentes',
    '/apartamentos',
    '/contratos',
    '/usuarios',
    '/historial-visitas',
    '/paquetes-admin',
    '/pagos',
    '/ganancias',
    '/multas',
    '/alertas',
    '/avisos',
    '/quejas-admin',
    '/visitas',
    '/parqueaderos',
    '/escanner-qr',
    '/mantenimiento',
    '/asambleas',
    '/polizas',
    '/emergencias',
  ],
  ADMINISTRADOR: [
    '/dashboard',
    '/residentes',
    '/apartamentos',
    '/contratos',
    '/usuarios',
    '/historial-visitas',
    '/paquetes-admin',
    '/pagos',
    '/ganancias',
    '/multas',
    '/alertas',
    '/avisos',
    '/quejas-admin',
    '/visitas',
    '/parqueaderos',
    '/escanner-qr',
    '/mantenimiento',
    '/asambleas',
    '/polizas',
    '/emergencias',
  ],
  PORTERO: [
    '/portero-dashboard',
    '/paquetes',
    '/visitas',
    '/parqueaderos',
    '/escanner-qr',
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
  ],
};

export function roleCanAccess(pathname, rol) {
  if (!pathname || !rol) return false;
  const norm = normalizeRole(rol);
  const allowed = ACCESS_BY_ROLE[norm];
  if (!allowed) return false;
  return allowed.some((path) => pathname === path || pathname.startsWith(path + '/'));
}