// Normaliza roles de SAED 2.0 a los roles que entiende la UI (SAED 1.0).
// Backend devuelve: SUPERADMIN, ADMIN_ORGANIZACION, ADMIN_PROPIEDAD, RESIDENTE
// La UI usa: ADMINISTRADOR (admin), PORTERO (acceso parcial), RESIDENTE.
export function normalizeRole(rol) {
  if (!rol) return rol;
  const r = String(rol).toUpperCase();
  if (r === 'SUPERADMIN' || r === 'ADMIN_ORGANIZACION' || r === 'ADMIN_PROPIEDAD') {
    return 'ADMINISTRADOR';
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
  ADMINISTRADOR: '/dashboard',
  PORTERO: '/portero-dashboard',
  RESIDENTE: '/residente-dashboard',
};

const ACCESS_BY_ROLE = {
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
  const allowed = ACCESS_BY_ROLE[normalizeRole(rol)];
  if (!allowed) return false;
  return allowed.some((path) => pathname === path || pathname.startsWith(path + '/'));
}