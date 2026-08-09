import { Component } from 'react';

/**
 * Limite de error por ruta (React): si una pagina lanza un error en runtime,
 * el shell (sidebar/topbar) sigue vivo y el contenido muestra un fallback con
 * recarga en vez de desmontar toda la app (pantalla blanca).
 *
 * Se monta alrededor del <Outlet /> en AppShell; el error se reinicia al navegar.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidUpdate(prevProps) {
    // Al cambiar de ruta se limpia el estado de error (navegacion = intento nuevo).
    if (this.state.hasError && prevProps.routeKey !== this.props.routeKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="table-container p-8">
          <div className="empty-state">
            <div className="empty-icon" aria-hidden="true">
              <span className="material-symbols-outlined">error</span>
            </div>
            <div className="empty-title">Algo salió mal al cargar esta sección</div>
            <p className="empty-subtitle">
              El error no afectó el resto del sistema. Recargá la página para reintentar.
            </p>
            <div style={{ display: 'flex', gap: '8px', marginTop: '16px', justifyContent: 'center' }}>
              <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
                Recargar página
              </button>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
