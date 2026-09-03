import { lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';
import LoginPage from './pages/LoginPage.jsx';
import AppShell from './components/layout/AppShell.jsx';
import { AuthProvider } from './lib/AuthContext.jsx';
import { TenantProvider } from './lib/TenantContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

// Code-splitting por ruta: cada página carga solo cuando se visita, reduciendo
// el bundle inicial (~1.2MB -> fracciones). Login queda eager (entry point).
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'));
const PersonasPage = lazy(() => import('./pages/PersonasPage.jsx'));
const ResidentesPage = lazy(() => import('./pages/ResidentesPage.jsx'));
const UnidadesPage = lazy(() => import('./pages/UnidadesPage.jsx'));
const ContratosPage = lazy(() => import('./pages/ContratosPage.jsx'));
const UsuariosPage = lazy(() => import('./pages/UsuariosPage.jsx'));
const OrganizacionesPage = lazy(() => import('./pages/OrganizacionesPage.jsx'));
const PropiedadesPage = lazy(() => import('./pages/PropiedadesPage.jsx'));
const RolesYAsignacionesPage = lazy(() => import('./pages/RolesYAsignacionesPage.jsx'));
const PlanesPage = lazy(() => import('./pages/PlanesPage.jsx'));
const MembresiasPage = lazy(() => import('./pages/MembresiasPage.jsx'));
const ReportesPage = lazy(() => import('./pages/ReportesPage.jsx'));
const VisitasPage = lazy(() => import('./pages/VisitasPage.jsx'));
const ParqueaderosPage = lazy(() => import('./pages/ParqueaderosPage.jsx'));
const PagosPage = lazy(() => import('./pages/PagosPage.jsx'));
const MultasPage = lazy(() => import('./pages/MultasPage.jsx'));
const AlertasPage = lazy(() => import('./pages/AlertasPage.jsx'));
const AvisosPage = lazy(() => import('./pages/AvisosPage.jsx'));
const QuejasAdminPage = lazy(() => import('./pages/QuejasAdminPage.jsx'));
const ReservasAdminPage = lazy(() => import('./pages/ReservasAdminPage.jsx'));
const ResReservasPage = lazy(() => import('./pages/ResReservasPage.jsx'));
const SancionesAdminPage = lazy(() => import('./pages/SancionesAdminPage.jsx'));
const EmergenciasAdminPage = lazy(() => import('./pages/EmergenciasAdminPage.jsx'));
const ResSancionesPage = lazy(() => import('./pages/ResSancionesPage.jsx'));
const AsambleasAdminPage = lazy(() => import('./pages/AsambleasAdminPage.jsx'));
const PolizasAdminPage = lazy(() => import('./pages/PolizasAdminPage.jsx'));
const MantenimientoAdminPage = lazy(() => import('./pages/MantenimientoAdminPage.jsx'));
const ObrasAdminPage = lazy(() => import('./pages/ObrasAdminPage.jsx'));
const ResObrasPage = lazy(() => import('./pages/ResObrasPage.jsx'));
const GananciasPage = lazy(() => import('./pages/GananciasPage.jsx'));
const HistorialVisitasPage = lazy(() => import('./pages/HistorialVisitasPage.jsx'));
const PaquetesAdminPage = lazy(() => import('./pages/PaquetesAdminPage.jsx'));
const EscannerQRPage = lazy(() => import('./pages/EscannerQRPage.jsx'));
const CarteraPage = lazy(() => import('./pages/CarteraPage.jsx'));
const PresupuestoPage = lazy(() => import('./pages/PresupuestoPage.jsx'));
const GastosPage = lazy(() => import('./pages/GastosPage.jsx'));
const ConciliacionPage = lazy(() => import('./pages/ConciliacionPage.jsx'));
const PazYSalvoPage = lazy(() => import('./pages/PazYSalvoPage.jsx'));
const FlujoCajaPage = lazy(() => import('./pages/FlujoCajaPage.jsx'));
const CoarrendatariosPage = lazy(() => import('./pages/CoarrendatariosPage.jsx'));
const ContratosProveedorPage = lazy(() => import('./pages/ContratosProveedorPage.jsx'));

const ResidenteDashboardPage = lazy(() => import('./pages/ResidenteDashboardPage.jsx'));
const ResPerfilPage = lazy(() => import('./pages/ResPerfilPage.jsx'));
const ResApartamentoPage = lazy(() => import('./pages/ResApartamentoPage.jsx'));
const ResCuotasPage = lazy(() => import('./pages/ResCuotasPage.jsx'));
const ResFrecuentesPage = lazy(() => import('./pages/ResFrecuentesPage.jsx'));
const ResBuzonPage = lazy(() => import('./pages/ResBuzonPage.jsx'));
const ResVisitaPage = lazy(() => import('./pages/ResVisitaPage.jsx'));
const ResQuejasPage = lazy(() => import('./pages/ResQuejasPage.jsx'));
const ResIncidentesPage = lazy(() => import('./pages/ResIncidentesPage.jsx'));

const PorteriasPage = lazy(() => import('./pages/PorteriasPage.jsx'));
const PorteroDashboardPage = lazy(() => import('./pages/PorteroDashboardPage.jsx'));
const PaquetesPage = lazy(() => import('./pages/PaquetesPage.jsx'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage.jsx'));

// Fallback de las rutas lazy vive en AppShell (envuelve <Outlet />), de modo que
// el shell (sidebar/topbar) permanezca visible mientras se carga la página.

const IncidentesAdminPage = lazy(() => import('./pages/IncidentesAdminPage.jsx'));
const SuperAdminDashboardPage = lazy(() => import('./pages/SuperAdminDashboardPage.jsx'));
const SuperAdminOrganizacionesPage = lazy(() => import('./pages/SuperAdminOrganizacionesPage.jsx'));
const SuperAdminPlanesPage = lazy(() => import('./pages/SuperAdminPlanesPage.jsx'));
const SuperAdminMembresiasPage = lazy(() => import('./pages/SuperAdminMembresiasPage.jsx'));
const SuperAdminAdminsPage = lazy(() => import('./pages/SuperAdminAdminsPage.jsx'));
const SuperAdminAuditoriaPage = lazy(() => import('./pages/SuperAdminAuditoriaPage.jsx'));

const OrgDashboardPage = lazy(() => import('./pages/OrgDashboardPage.jsx'));
const OrgOrganizacionPage = lazy(() => import('./pages/OrgOrganizacionPage.jsx'));
const OrgPropiedadesPage = lazy(() => import('./pages/OrgPropiedadesPage.jsx'));
const OrgAdminsPage = lazy(() => import('./pages/OrgAdminsPage.jsx'));
const OrgPlanPage = lazy(() => import('./pages/OrgPlanPage.jsx'));
const OrgAuditoriaPage = lazy(() => import('./pages/OrgAuditoriaPage.jsx'));

import { useAuth } from './lib/AuthContext.jsx';

function RoleIndexRedirect() {
  const { user } = useAuth();
  if (user?.rol === 'SUPERADMIN') return <Navigate to="/superadmin/dashboard" replace />;
  if (user?.rol === 'ADMIN_ORGANIZACION') return <Navigate to="/org/dashboard" replace />;
  if (user?.rol === 'PORTERO') return <Navigate to="/portero-dashboard" replace />;
  if (user?.rol === 'RESIDENTE') return <Navigate to="/residente-dashboard" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <Toaster position="top-right" richColors />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <TenantProvider>
                <AppShell />
              </TenantProvider>
            </ProtectedRoute>
          }
        >
          <Route index element={<RoleIndexRedirect />} />

          {/* SuperAdmin SaaS Platform Routes */}
          <Route
            path="superadmin/dashboard"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/organizaciones"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminOrganizacionesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/propiedades"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <PropiedadesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/planes"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminPlanesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/membresias"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminMembresiasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/administradores"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminAdminsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/auditoria"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminAuditoriaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/metricas"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="superadmin/configuracion"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <SuperAdminDashboardPage />
              </ProtectedRoute>
            }
          />

          {/* Admin Organizacion Console Routes */}
          <Route
            path="org/dashboard"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/organizacion"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgOrganizacionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/propiedades"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgPropiedadesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/admins"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgAdminsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/plan"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgPlanPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/auditoria"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgAuditoriaPage />
              </ProtectedRoute>
            }
          />

          {/* Admin Propiedad Operative Routes */}
          <Route
            path="dashboard"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="personas"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PersonasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="residentes"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ResidentesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="unidades"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <UnidadesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="contratos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ContratosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="organizaciones"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <OrganizacionesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="propiedades"
            element={
              <ProtectedRoute roles={['SUPERADMIN', 'ADMIN_ORGANIZACION', 'ADMIN_PROPIEDAD']}>
                <PropiedadesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="roles-asignaciones"
            element={
              <ProtectedRoute roles={['SUPERADMIN', 'ADMIN_ORGANIZACION', 'ADMIN_PROPIEDAD']}>
                <RolesYAsignacionesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="planes"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <PlanesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="membresias"
            element={
              <ProtectedRoute roles={['SUPERADMIN']}>
                <MembresiasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="reportes"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ReportesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="usuarios"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <UsuariosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="visitas"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD', 'PORTERO']}>
                <VisitasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="parqueaderos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD', 'PORTERO']}>
                <ParqueaderosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="pagos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PagosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="multas"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <MultasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="sanciones-admin"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <SancionesAdminPage />
              </ProtectedRoute>
            }
          />
          <Route path="obras-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><ObrasAdminPage /></ProtectedRoute>} />
          <Route path="mantenimiento-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><MantenimientoAdminPage /></ProtectedRoute>} />
          <Route path="mantenimientos" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><MantenimientoAdminPage /></ProtectedRoute>} />
          <Route path="asambleas-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><AsambleasAdminPage /></ProtectedRoute>} />
          <Route path="asambleas" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><AsambleasAdminPage /></ProtectedRoute>} />
          <Route path="polizas-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><PolizasAdminPage /></ProtectedRoute>} />
          <Route path="polizas" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><PolizasAdminPage /></ProtectedRoute>} />
          <Route path="emergencias-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><EmergenciasAdminPage /></ProtectedRoute>} />
          <Route path="emergencias" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><EmergenciasAdminPage /></ProtectedRoute>} />
          <Route path="incidentes-admin" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD', 'PORTERO']}><IncidentesAdminPage /></ProtectedRoute>} />
          <Route
            path="alertas"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <AlertasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="avisos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <AvisosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="quejas-admin"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <QuejasAdminPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="reservas-admin"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ReservasAdminPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="ganancias"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <GananciasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="historial-visitas"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <HistorialVisitasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="paquetes-admin"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PaquetesAdminPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="escanner-qr"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD', 'PORTERO']}>
                <EscannerQRPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="cartera"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <CarteraPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="presupuestos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PresupuestoPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="gastos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <GastosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="conciliaciones"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ConciliacionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="paz-y-salvos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PazYSalvoPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="flujo-caja"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <FlujoCajaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="porterias-admin"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PorteriasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="coarrendatarios"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <CoarrendatariosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="contratos-proveedor"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <ContratosProveedorPage />
              </ProtectedRoute>
            }
          />

          {/* Residente */}
          <Route
            path="residente-dashboard"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResidenteDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-perfil"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResPerfilPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-apartamento"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResApartamentoPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-cuotas"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResCuotasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-frecuentes"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResFrecuentesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-buzon"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResBuzonPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-visita"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResVisitaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-quejas"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResQuejasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-reservas"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResReservasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-sanciones"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResSancionesPage />
              </ProtectedRoute>
            }
          />
          <Route path="res-obras" element={<ProtectedRoute roles={['RESIDENTE']}><ResObrasPage /></ProtectedRoute>} />
          <Route path="res-incidentes" element={<ProtectedRoute roles={['RESIDENTE']}><ResIncidentesPage /></ProtectedRoute>} />

          {/* Portero */}
          <Route
            path="portero-dashboard"
            element={
              <ProtectedRoute roles={['PORTERO']}>
                <PorteroDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="paquetes"
            element={
              <ProtectedRoute roles={['PORTERO']}>
                <PaquetesPage />
              </ProtectedRoute>
            }
          />
          {/* 404 con identidad dentro del shell (usuarios autenticados). */}
          <Route
            path="*"
            element={
              <ProtectedRoute>
                <NotFoundPage />
              </ProtectedRoute>
            }
          />
        </Route>
        {/* Ruta raiz no autenticada -> login; cualquier otra desconocida fuera del shell. */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  );
}

