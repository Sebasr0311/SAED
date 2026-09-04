import { useMemo, useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { formatCurrency } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';

/**
 * DashboardPage 2.0 — panel multi-tenant.
 *
 * KPIs por tenant (unidades, residentes, contratos activos, multas pendientes)
 * usando la asignacion activa del TenantContext (X-Assignment-Id inyectado
 * por useTenantApi). El RLS del backend filtra por organizacion/propiedad.
 */

export default function DashboardPage() {
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
  const { data: cuotasRaw, loading: loadingCuotas } = useFetch(
    () => tenantApi.get('/cuotas'),
    [tenant.activeAssignmentId]
  );
  const { data: carteraResumenRaw, loading: loadingCartera } = useFetch(
    () => tenantApi.get('/cartera/resumen'),
    [tenant.activeAssignmentId]
  );
  const { data: multasRaw, loading: loadingMultas } = useFetch(
    () => tenantApi.get('/multas/todas'),
    [tenant.activeAssignmentId]
  );

  const unidadesList = unidades?.items || [];
  const personasList = personas?.items || [];
  const cuotasList = cuotasRaw?.items || cuotasRaw?.data || (Array.isArray(cuotasRaw) ? cuotasRaw : []);
  const multasList = multasRaw?.items || [];
  const carteraResumen = carteraResumenRaw?.data || carteraResumenRaw?.raw || carteraResumenRaw || {};

  const cuotasPendientes = useMemo(
    () => cuotasList.filter((c) => c.estado === 'PENDIENTE' || Number(c.saldoPendiente || 0) > 0),
    [cuotasList]
  );
  const totalCartera = Number(
    carteraResumen.TOTAL_CARTERA != null
      ? carteraResumen.TOTAL_CARTERA
      : cuotasPendientes.reduce((acc, c) => acc + Number(c.saldoPendiente || c.valorTotal || 0), 0)
  );
  const multasPendientes = multasList.filter((m) => m.estado === 'PENDIENTE').length;

  const contextoLabel = [
    tenant.activeOrgId ? `Org ${tenant.activeOrgId}` : null,
    tenant.activePropertyId ? `Prop ${tenant.activePropertyId}` : null,
    tenant.activeUnitId ? `Unidad ${tenant.activeUnitId}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  const cargando = loadingUnidades || loadingPersonas || loadingCuotas || loadingCartera || loadingMultas;

  return (
    <div className="dashboard-page">
      <PageHeader
        title="Dashboard"
        subtitle={contextoLabel ? `Resumen del contexto: ${contextoLabel}` : 'Vista rápida de los indicadores del tenant'}
      />

      <div className="stat-grid">
        <StatCard icon="groups" value={personasList.length} label="Personas" color="primary" />
        <StatCard icon="domain" value={unidadesList.length} label="Unidades" color="secondary" />
        <StatCard icon="payments" value={formatCurrency(totalCartera)} label="Cartera Pendiente" color="success" />
        <StatCard icon="gavel" value={multasPendientes} label="Multas Pendientes" color="warning" />
      </div>

      <div className="dashboard-cards-grid">
        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h3>Cobros Pendientes</h3>
            <span className="dashboard-card-sub">Cuotas y obligaciones del tenant</span>
          </div>
          {cargando ? (
            <p className="dashboard-empty">Cargando...</p>
          ) : cuotasPendientes.length === 0 ? (
            <p className="dashboard-empty">Sin cuotas pendientes</p>
          ) : (
            <ul className="dashboard-list">
              {cuotasPendientes.slice(0, 8).map((c) => (
                <li key={c.id || c.idCuota}>
                  <div>
                    <strong>{c.concepto || `Cuota #${c.id || c.idCuota}`}</strong>
                    <span className="dashboard-list-sub">
                      {c.numeroApartamento || 'Unidad'}
                      {c.nombreResidente ? ` · ${c.nombreResidente}` : ''}
                      {c.periodo ? ` · ${c.periodo}` : ''}
                    </span>
                  </div>
                  <span className="dashboard-list-value">
                    {formatCurrency(c.saldoPendiente != null ? c.saldoPendiente : c.valorBase || 0)}
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
                        {m.nombreResidente ? ` · ${m.nombreResidente}` : ''}
                      </span>
                    </div>
                    <span className="dashboard-list-value">{formatCurrency(m.monto)}</span>
                  </li>
                ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}