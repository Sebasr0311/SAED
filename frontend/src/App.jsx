import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'sonner';
import LoginPage from './pages/LoginPage.jsx';
import AppShell from './components/layout/AppShell.jsx';
import { AuthProvider } from './lib/AuthContext.jsx';
import { TenantProvider } from './lib/TenantContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import { warmUpBackend } from './lib/api.js';

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
const ResCuotasPage = lazy(() => import('./pages/ResCuotasPage.jsx'));
const ResVisitasPage = lazy(() => import('./pages/ResVisitasPage.jsx'));
const ResBuzonPage = lazy(() => import('./pages/ResBuzonPage.jsx'));
const ResQuejasPage = lazy(() => import('./pages/ResQuejasPage.jsx'));
const ResIncidentesPage = lazy(() => import('./pages/ResIncidentesPage.jsx'));
const ResDocumentosPage = lazy(() => import('./pages/ResDocumentosPage.jsx'));

const PorteriasPage = lazy(() => import('./pages/PorteriasPage.jsx'));
const PorteroDashboardPage = lazy(() => import('./pages/PorteroDashboardPage.jsx'));
const PaquetesPage = lazy(() => import('./pages/PaquetesPage.jsx'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage.jsx'));

// Fallback de las rutas lazy vive en AppShell (envuelve <Outlet />), de modo que
// el shell (sidebar/topbar) permanezca visible mientras se carga la página.

const IncidentesAdminPage = lazy(() => import('./pages/IncidentesAdminPage.jsx'));
const SuperAdminDashboardPage = lazy(() => import('./pages/SuperAdminDashboardPage.jsx'));
const SuperAdminOrganizacionesPage = lazy(() => import('./pages/SuperAdminOrganizacionesPage.jsx'));
const SuperAdminPropiedadesPage = lazy(() => import('./pages/SuperAdminPropiedadesPage.jsx'));
const SuperAdminPlanesPage = lazy(() => import('./pages/SuperAdminPlanesPage.jsx'));
const SuperAdminMembresiasPage = lazy(() => import('./pages/SuperAdminMembresiasPage.jsx'));
const SuperAdminAdminsPage = lazy(() => import('./pages/SuperAdminAdminsPage.jsx'));
const SuperAdminAuditoriaPage = lazy(() => import('./pages/SuperAdminAuditoriaPage.jsx'));

const OrgDashboardPage = lazy(() => import('./pages/OrgDashboardPage.jsx'));
const OrgOrganizacionPage = lazy(() => import('./pages/OrgOrganizacionPage.jsx'));
const OrgPropiedadesPage = lazy(() => import('./pages/OrgPropiedadesPage.jsx'));
const OrgAdminsPage = lazy(() => import('./pages/OrgAdminsPage.jsx'));
const OrgPlantillasContratosPage = lazy(() => import('./pages/OrgPlantillasContratosPage.jsx'));
const OrgPlanPage = lazy(() => import('./pages/OrgPlanPage.jsx'));
const OrgCarteraPage = lazy(() => import('./pages/OrgCarteraPage.jsx'));
const OrgGastosPage = lazy(() => import('./pages/OrgGastosPage.jsx'));
const OrgReportesPage = lazy(() => import('./pages/OrgReportesPage.jsx'));
const OrgAnaliticaPage = lazy(() => import('./pages/OrgAnaliticaPage.jsx'));
const OrgAuditoriaPage = lazy(() => import('./pages/OrgAuditoriaPage.jsx'));
const DocumentosAdminPage = lazy(() => import('./pages/DocumentosAdminPage.jsx'));
const LandingPage = lazy(() => import('./pages/LandingPage.jsx'));
const SuscripcionesPage = lazy(() => import('./pages/SuscripcionesPage.jsx'));
const ActivarCuentaPage = lazy(() => import('./pages/ActivarCuentaPage.jsx'));

import { useAuth } from './lib/AuthContext.jsx';

function RoleIndexRedirect() {
  const { user } = useAuth();
  if (user?.rol === 'SUPERADMIN') return <Navigate to="/superadmin/dashboard" replace />;
  if (user?.rol === 'ADMIN_ORGANIZACION') return <Navigate to="/org/dashboard" replace />;
  if (user?.rol === 'PORTERO') return <Navigate to="/portero-dashboard" replace />;
  if (user?.rol === 'RESIDENTE') return <Navigate to="/residente-dashboard" replace />;
  return <Navigate to="/dashboard" replace />;
}

function PropiedadesRedirect() {
  const { user } = useAuth();
  if (user?.rol === 'SUPERADMIN') return <Navigate to="/superadmin/propiedades" replace />;
  if (user?.rol === 'ADMIN_ORGANIZACION') return <Navigate to="/org/propiedades" replace />;
  return <Navigate to="/dashboard" replace />;
}

export default function App() {
  useEffect(() => {
    warmUpBackend();
  }, []);

  return (
    <AuthProvider>
      <Toaster position="top-right" richColors />
      <Routes>
        <Route
          path="/"
          element={
            <Suspense
              fallback={
                <div className="min-h-screen bg-[#0A1628] flex items-center justify-center">
                  <div className="w-8 h-8 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                </div>
              }
            >
              <LandingPage />
            </Suspense>
          }
        />
        <Route
          path="/suscripciones"
          element={
            <Suspense
              fallback={
                <div className="min-h-screen bg-[#070B14] flex items-center justify-center">
                  <div className="w-8 h-8 border-2 border-sky-400 border-t-transparent rounded-full animate-spin" />
                </div>
              }
            >
              <SuscripcionesPage />
            </Suspense>
          }
        />
        <Route path="/planes" element={<Navigate to="/suscripciones" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/activar-cuenta"
          element={
            <Suspense
              fallback={
                <div className="min-h-screen bg-[#0A1628] flex items-center justify-center">
                  <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                </div>
              }
            >
              <ActivarCuentaPage />
            </Suspense>
          }
        />
        <Route
          element={
            <ProtectedRoute>
              <TenantProvider>
                <AppShell />
              </TenantProvider>
            </ProtectedRoute>
          }
        >
          <Route path="app" element={<RoleIndexRedirect />} />

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
                <SuperAdminPropiedadesPage />
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
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/organizacion"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgOrganizacionPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/propiedades"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgPropiedadesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/admins"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgAdminsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/plantillas"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgPlantillasContratosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/plan"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgPlanPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/cartera"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgCarteraPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/gastos"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION', 'SUPERADMIN']}>
                <OrgGastosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/reportes"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgReportesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/analitica"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
                <OrgAnaliticaPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="org/auditoria"
            element={
              <ProtectedRoute roles={['ADMIN_ORGANIZACION']}>
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
            path="apartamentos"
            element={<Navigate to="/unidades" replace />}
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
            element={<Navigate to="/superadmin/organizaciones" replace />}
          />
          <Route
            path="propiedades"
            element={<PropiedadesRedirect />}
          />
          <Route
            path="roles-asignaciones"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <RolesYAsignacionesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="planes"
            element={<Navigate to="/superadmin/planes" replace />}
          />
          <Route
            path="membresias"
            element={<Navigate to="/superadmin/membresias" replace />}
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
            path="porterias"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <PorteriasPage />
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
          <Route path="mantenimiento-admin" element={<Navigate to="/mantenimientos" replace />} />
          <Route path="mantenimientos" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><MantenimientoAdminPage /></ProtectedRoute>} />
          <Route path="asambleas-admin" element={<Navigate to="/asambleas" replace />} />
          <Route path="asambleas" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><AsambleasAdminPage /></ProtectedRoute>} />
          <Route path="polizas-admin" element={<Navigate to="/polizas" replace />} />
          <Route path="polizas" element={<ProtectedRoute roles={['ADMIN_PROPIEDAD']}><PolizasAdminPage /></ProtectedRoute>} />
          <Route path="emergencias-admin" element={<Navigate to="/emergencias" replace />} />
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
            element={<Navigate to="/flujo-caja" replace />}
          />
          <Route
            path="documentos"
            element={
              <ProtectedRoute roles={['ADMIN_PROPIEDAD']}>
                <DocumentosAdminPage />
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
            element={<Navigate to="/porterias" replace />}
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
            element={<Navigate to="/res-perfil" replace />}
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
            path="res-visitas"
            element={
              <ProtectedRoute roles={['RESIDENTE']}>
                <ResVisitasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="res-frecuentes"
            element={<Navigate to="/res-visitas" replace />}
          />
          <Route
            path="res-visita"
            element={<Navigate to="/res-visitas" replace />}
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
          <Route path="res-documentos" element={<ProtectedRoute roles={['RESIDENTE']}><ResDocumentosPage /></ProtectedRoute>} />

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

