import { lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
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
const ApartamentosPage = lazy(() => import('./pages/ApartamentosPage.jsx'));
const ContratosPage = lazy(() => import('./pages/ContratosPage.jsx'));
const UsuariosPage = lazy(() => import('./pages/UsuariosPage.jsx'));
const VisitasPage = lazy(() => import('./pages/VisitasPage.jsx'));
const ParqueaderosPage = lazy(() => import('./pages/ParqueaderosPage.jsx'));
const PagosPage = lazy(() => import('./pages/PagosPage.jsx'));
const MultasPage = lazy(() => import('./pages/MultasPage.jsx'));
const AlertasPage = lazy(() => import('./pages/AlertasPage.jsx'));
const AvisosPage = lazy(() => import('./pages/AvisosPage.jsx'));
const QuejasAdminPage = lazy(() => import('./pages/QuejasAdminPage.jsx'));
const GananciasPage = lazy(() => import('./pages/GananciasPage.jsx'));
const HistorialVisitasPage = lazy(() => import('./pages/HistorialVisitasPage.jsx'));
const PaquetesAdminPage = lazy(() => import('./pages/PaquetesAdminPage.jsx'));
const EscannerQRPage = lazy(() => import('./pages/EscannerQRPage.jsx'));

const ResidenteDashboardPage = lazy(() => import('./pages/ResidenteDashboardPage.jsx'));
const ResPerfilPage = lazy(() => import('./pages/ResPerfilPage.jsx'));
const ResApartamentoPage = lazy(() => import('./pages/ResApartamentoPage.jsx'));
const ResCuotasPage = lazy(() => import('./pages/ResCuotasPage.jsx'));
const ResFrecuentesPage = lazy(() => import('./pages/ResFrecuentesPage.jsx'));
const ResBuzonPage = lazy(() => import('./pages/ResBuzonPage.jsx'));
const ResVisitaPage = lazy(() => import('./pages/ResVisitaPage.jsx'));
const ResQuejasPage = lazy(() => import('./pages/ResQuejasPage.jsx'));

const PorteroDashboardPage = lazy(() => import('./pages/PorteroDashboardPage.jsx'));
const PaquetesPage = lazy(() => import('./pages/PaquetesPage.jsx'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage.jsx'));

// Fallback de las rutas lazy vive en AppShell (envuelve <Outlet />), de modo que
// el shell (sidebar/topbar) permanezca visible mientras se carga la página.

export default function App() {
  return (
    <AuthProvider>
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
          <Route index element={<Navigate to="/dashboard" replace />} />

          {/* Admin */}
          <Route
            path="dashboard"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="personas"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <PersonasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="residentes"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <ResidentesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="apartamentos"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <ApartamentosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="contratos"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <ContratosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="usuarios"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <UsuariosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="visitas"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR', 'PORTERO']}>
                <VisitasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="parqueaderos"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR', 'PORTERO']}>
                <ParqueaderosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="pagos"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <PagosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="multas"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <MultasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="alertas"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <AlertasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="avisos"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <AvisosPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="quejas-admin"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <QuejasAdminPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="ganancias"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <GananciasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="historial-visitas"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <HistorialVisitasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="paquetes-admin"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR']}>
                <PaquetesAdminPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="escanner-qr"
            element={
              <ProtectedRoute roles={['ADMINISTRADOR', 'PORTERO']}>
                <EscannerQRPage />
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
