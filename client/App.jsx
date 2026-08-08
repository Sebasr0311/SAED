import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import AppShell from './components/layout/AppShell.jsx';
import { AuthProvider } from './lib/AuthContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

import ResidentesPage from './pages/ResidentesPage.jsx';
import ApartamentosPage from './pages/ApartamentosPage.jsx';
import ContratosPage from './pages/ContratosPage.jsx';
import UsuariosPage from './pages/UsuariosPage.jsx';
import VisitasPage from './pages/VisitasPage.jsx';
import ParqueaderosPage from './pages/ParqueaderosPage.jsx';
import PagosPage from './pages/PagosPage.jsx';
import MultasPage from './pages/MultasPage.jsx';
import AlertasPage from './pages/AlertasPage.jsx';
import AvisosPage from './pages/AvisosPage.jsx';
import QuejasAdminPage from './pages/QuejasAdminPage.jsx';
import GananciasPage from './pages/GananciasPage.jsx';
import HistorialVisitasPage from './pages/HistorialVisitasPage.jsx';
import PaquetesAdminPage from './pages/PaquetesAdminPage.jsx';
import EscannerQRPage from './pages/EscannerQRPage.jsx';

import ResidenteDashboardPage from './pages/ResidenteDashboardPage.jsx';
import ResPerfilPage from './pages/ResPerfilPage.jsx';
import ResApartamentoPage from './pages/ResApartamentoPage.jsx';
import ResCuotasPage from './pages/ResCuotasPage.jsx';
import ResFrecuentesPage from './pages/ResFrecuentesPage.jsx';
import ResBuzonPage from './pages/ResBuzonPage.jsx';
import ResVisitaPage from './pages/ResVisitaPage.jsx';
import ResQuejasPage from './pages/ResQuejasPage.jsx';

import PorteroDashboardPage from './pages/PorteroDashboardPage.jsx';
import PaquetesPage from './pages/PaquetesPage.jsx';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AppShell />
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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
