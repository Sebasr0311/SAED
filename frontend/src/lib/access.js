// SAED 2.0 Identity & Access Control Layer
// Separation of Platform SaaS (SUPERADMIN) from Organization Management (ADMIN_ORGANIZACION)
// and Property Operations (ADMIN_PROPIEDAD, PORTERO, RESIDENTE)

export const ROLE_DEFINITIONS = {
  SUPERADMIN: {
    code: 'SUPERADMIN',
    name: 'Superadministrador',
    scope: 'GLOBAL',
    domain: 'PLATFORM',
    home: '/superadmin/dashboard',
    description: 'Operador y administrador de la plataforma SaaS SAED',
  },
  ADMIN_ORGANIZACION: {
    code: 'ADMIN_ORGANIZACION',
    name: 'Administrador de Organización',
    scope: 'ORGANIZACION',
    domain: 'ORGANIZATION',
    home: '/org/dashboard',
    description: 'Gestor gerencial de cartera de propiedades de la organización',
  },
  ADMIN_PROPIEDAD: {
    code: 'ADMIN_PROPIEDAD',
    name: 'Administrador de Propiedad',
    scope: 'PROPIEDAD',
    domain: 'PROPERTY',
    home: '/dashboard',
    description: 'Administrador integral y operativo de una copropiedad',
  },
  PORTERO: {
    code: 'PORTERO',
    name: 'Portero / Vigilante',
    scope: 'PORTERIA',
    domain: 'GATEWAY',
    home: '/portero-dashboard',
    description: 'Operador de control de acceso, visitantes y paquetería',
  },
  PROPIETARIO: {
    code: 'PROPIETARIO',
    name: 'Propietario No Residente',
    scope: 'UNIDAD',
    domain: 'UNIT_OWNER',
    home: '/res-perfil',
    description: 'Titular de dominio legal sobre el inmueble sin residencia física',
  },
  RESIDENTE: {
    code: 'RESIDENTE',
    name: 'Residente / Inquilino',
    scope: 'UNIDAD',
    domain: 'UNIT',
    home: '/residente-dashboard',
    description: 'Habitante o copropietario residente de una unidad residencial',
  },
};

export function normalizeRole(rol) {
  if (!rol) return rol;
  const r = String(rol).toUpperCase().trim();
  if (r === 'SUPERADMIN') {
    return 'SUPERADMIN';
  }
  if (r === 'ADMIN_ORGANIZACION' || r === 'ORG_ADMIN') {
    return 'ADMIN_ORGANIZACION';
  }
  if (r === 'ADMIN_PROPIEDAD' || r === 'ADMIN' || r === 'ADMINISTRADOR') {
    return 'ADMIN_PROPIEDAD';
  }
  if (r === 'PORTERO' || r === 'VIGILANTE') {
    return 'PORTERO';
  }
  if (r === 'PROPIETARIO' || r === 'PROPIETARIO_NO_RESIDENTE' || r === 'PROPIETARIO_UNIDAD') {
    return 'PROPIETARIO';
  }
  if (r === 'RESIDENTE' || r === 'ARRENDATARIO' || r === 'CONVIVIENTE') {
    return 'RESIDENTE';
  }
  return r;
}

export const ROLE_HOME = {
  SUPERADMIN: '/superadmin/dashboard',
  ADMIN_ORGANIZACION: '/org/dashboard',
  ADMIN_PROPIEDAD: '/dashboard',
  PORTERO: '/portero-dashboard',
  PROPIETARIO: '/res-perfil',
  RESIDENTE: '/residente-dashboard',
};

export const ACCESS_BY_ROLE = {
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
    '/org/plantillas',
    '/org/plan',
    '/org/cartera',
    '/org/gastos',
    '/org/reportes',
    '/org/analitica',
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
    '/roles-asignaciones',
    '/reportes',
    '/usuarios',
    '/porterias',
    '/porterias-admin',
    '/visitas',
    '/historial-visitas',
    '/paquetes-admin',
    '/parqueaderos',
    '/escanner-qr',
    '/pagos',
    '/cartera',
    '/presupuestos',
    '/gastos',
    '/flujo-caja',
    '/conciliaciones',
    '/paz-y-salvos',
    '/multas',
    '/sanciones-admin',
    '/obras-admin',
    '/mantenimientos',
    '/mantenimiento-admin',
    '/asambleas',
    '/asambleas-admin',
    '/polizas',
    '/polizas-admin',
    '/emergencias',
    '/emergencias-admin',
    '/incidentes-admin',
    '/alertas',
    '/avisos',
    '/quejas-admin',
    '/reservas-admin',
    '/coarrendatarios',
    '/documentos',
  ],
  PORTERO: [
    '/portero-dashboard',
    '/paquetes',
    '/visitas',
    '/parqueaderos',
    '/escanner-qr',
    '/incidentes-admin',
  ],
  PROPIETARIO: [
    '/res-perfil',
    '/res-documentos',
  ],
  RESIDENTE: [
    '/residente-dashboard',
    '/res-perfil',
    '/res-apartamento',
    '/res-cuotas',
    '/res-visitas',
    '/res-frecuentes',
    '/res-buzon',
    '/res-visita',
    '/res-quejas',
    '/res-reservas',
    '/res-sanciones',
    '/res-obras',
    '/res-incidentes',
    '/res-documentos',
  ],
};

export function roleCanAccess(pathname, rol) {
  if (!pathname || !rol) return false;
  const norm = normalizeRole(rol);
  const allowed = ACCESS_BY_ROLE[norm];
  if (!allowed) return false;
  return allowed.some((path) => pathname === path || pathname.startsWith(path + '/'));
}