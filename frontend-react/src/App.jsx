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
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="residentes" element={<ResidentesPage />} />
          <Route path="apartamentos" element={<ApartamentosPage />} />
          <Route path="contratos" element={<ContratosPage />} />
          <Route path="usuarios" element={<UsuariosPage />} />
          <Route path="visitas" element={<VisitasPage />} />
          <Route path="parqueaderos" element={<ParqueaderosPage />} />
          <Route path="pagos" element={<PagosPage />} />
          <Route path="multas" element={<MultasPage />} />
          <Route path="alertas" element={<AlertasPage />} />
          <Route path="avisos" element={<AvisosPage />} />
          <Route path="quejas-admin" element={<QuejasAdminPage />} />
          <Route path="ganancias" element={<GananciasPage />} />
          <Route path="historial-visitas" element={<HistorialVisitasPage />} />
          <Route path="paquetes-admin" element={<PaquetesAdminPage />} />
          <Route path="escanner-qr" element={<EscannerQRPage />} />

          {/* Residente */}
          <Route path="residente-dashboard" element={<ResidenteDashboardPage />} />
          <Route path="res-perfil" element={<ResPerfilPage />} />
          <Route path="res-apartamento" element={<ResApartamentoPage />} />
          <Route path="res-cuotas" element={<ResCuotasPage />} />
          <Route path="res-frecuentes" element={<ResFrecuentesPage />} />
          <Route path="res-buzon" element={<ResBuzonPage />} />
          <Route path="res-visita" element={<ResVisitaPage />} />
          <Route path="res-quejas" element={<ResQuejasPage />} />

          {/* Portero */}
          <Route path="portero-dashboard" element={<PorteroDashboardPage />} />
          <Route path="paquetes" element={<PaquetesPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
