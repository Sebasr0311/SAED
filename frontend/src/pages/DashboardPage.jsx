import { useMemo, useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { formatCurrency } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';

/**
 * DashboardPage 2.0 Ã¢â‚¬â€ panel multi-tenant.
 *
 * KPIs por tenant (unidades, residentes, contratos activos, multas pendientes)
 * usando la asignacion activa del TenantContext (X-Assignment-Id inyectado
 * por useTenantApi). El RLS del backend filtra por organizacion/propiedad.
 */
function StatCard({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
      <div className="stat-badge">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [toast, setToast] = useState(null);
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  // Datos del tenant activo (RLS por X-Assignment-Id)
  const { data: unidades, loading: loadingUnidades } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );
  const { data: personas, loading: loadingPersonas } = useFetch(
    () => tenantApi.get('/personas'),
    [tenant.activeAssignmentId]
  );
  const { data: contratos, loading: loadingContratos } = useFetch(
    () => tenantApi.get('/contratos'),
    [tenant.activeAssignmentId]
  );
  const { data: multasRaw, loading: loadingMultas } = useFetch(
    () => tenantApi.get('/multas/todas'),
    [tenant.activeAssignmentId]
  );

  const unidadesList = unidades?.items || [];
  const personasList = personas?.items || [];
  const contratosList = contratos?.items || [];
  const multasList = multasRaw?.items || [];

  const contratosActivos = contratosList.filter((c) => c.estado === 'ACTIVO').length;
  const multasPendientes = multasList.filter((m) => m.estado === 'PENDIENTE').length;

  // Proximos cobros: multas + contratos activos (sin cuotas endpoint, se
  // muestran los contratos activos como indicador).
  const proximosCobros = useMemo(
    () => contratosList.filter((c) => c.estado === 'ACTIVO'),
    [contratosList]
  );

  const contextoLabel = [
    tenant.activeOrgId ? `Org ${tenant.activeOrgId}` : null,
    tenant.activePropertyId ? `Prop ${tenant.activePropertyId}` : null,
    tenant.activeUnitId ? `Unidad ${tenant.activeUnitId}` : null,
  ]
    .filter(Boolean)
    .join(' Ã‚Â· ');

  const cargando = loadingUnidades || loadingPersonas || loadingContratos || loadingMultas;

  return (
    <div className="dashboard-page">
      <PageHeader
        title="Dashboard"
        subtitle={contextoLabel ? `Resumen del contexto: ${contextoLabel}` : 'Vista rÃƒÂ¡pida de los indicadores del tenant'}
      />

      <div className="stat-grid">
        <StatCard icon="groups" value={personasList.length} label="Personas" color="primary" />
        <StatCard icon="domain" value={unidadesList.length} label="Unidades" color="secondary" />
        <StatCard icon="description" value={contratosActivos} label="Contratos Activos" color="success" />
        <StatCard icon="gavel" value={multasPendientes} label="Multas Pendientes" color="warning" />
      </div>

      <div className="dashboard-cards-grid">
        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h3>PrÃƒÂ³ximos Cobros</h3>
            <span className="dashboard-card-sub">Contratos activos del tenant</span>
          </div>
          {cargando ? (
            <p className="dashboard-empty">Cargando...</p>
          ) : proximosCobros.length === 0 ? (
            <p className="dashboard-empty">Sin contratos activos</p>
          ) : (
            <ul className="dashboard-list">
              {proximosCobros.slice(0, 8).map((c) => (
                <li key={c.idContrato || c.id}>
                  <div>
                    <strong>{c.numeroContrato || `Contrato #${c.idContrato || c.id}`}</strong>
                    <span className="dashboard-list-sub">
                      {c.unidad?.identificador || c.numeroApartamento || 'Unidad'}
                    </span>
                  </div>
                  <span className="dashboard-list-value">
                    {c.canonMensual != null ? formatCurrency(c.canonMensual) : 'Ã¢â‚¬â€'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h3>Multas Pendientes</h3>
            <span className="dashboard-card-sub">Por cobrar en el tenant</span>
          </div>
          {cargando ? (
            <p className="dashboard-empty">Cargando...</p>
          ) : multasPendientes === 0 ? (
            <p className="dashboard-empty">Sin multas pendientes</p>
          ) : (
            <ul className="dashboard-list">
              {multasList
                .filter((m) => m.estado === 'PENDIENTE')
                .slice(0, 8)
                .map((m) => (
                  <li key={m.idMulta || m.id}>
                    <div>
                      <strong>{m.tipo || 'Multa'}</strong>
                      <span className="dashboard-list-sub">
                        {m.numeroApartamento || 'Unidad'}
                        {m.nombreResidente ? ` Ã‚Â· ${m.nombreResidente}` : ''}
                      </span>
                    </div>
                    <span className="dashboard-list-value">{formatCurrency(m.monto)}</span>
                  </li>
                ))}
            </ul>
          )}
        </section>
      </div>

      <Toast toast={toast} />
    </div>
  );
}