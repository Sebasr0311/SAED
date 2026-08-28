/**
 * StatCard — tarjeta de KPI reutilizable.
 *
 * Extraida de DashboardPage, PagosPage, QuejasAdminPage,
 * PorteroDashboardPage y ResidenteDashboardPage.
 */
export function StatCard({ icon, value, label, color = 'primary', className = '' }) {
  return (
    <div className={`stat-card ${className}`} role="group" aria-label={`KPI: ${label}`}>
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined" aria-hidden="true">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
      <div className="stat-badge" aria-hidden="true">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
    </div>
  );
}
