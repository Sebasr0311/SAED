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
  const allowed = ACCESS_BY_ROLE[rol];
  if (!allowed) return false;
  return allowed.some((path) => pathname === path || pathname.startsWith(path + '/'));
}
