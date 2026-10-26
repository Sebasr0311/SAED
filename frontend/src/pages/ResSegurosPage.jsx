import React, { useState, useMemo } from 'react';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/button.tsx';
import { safeHttpUrl } from '../lib/safeUrl.js';
import ErrorState from '../components/ui/ErrorState.jsx';
import { formatDate } from '../lib/utils.js';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import {
  Shield,
  FileText,
  Calendar,
  ExternalLink,
  Phone,
  User,
  Search,
  RefreshCw,
  CheckCircle2,
  Info,
} from 'lucide-react';

const ESTADOS_POLIZA = {
  VIGENTE: {
    label: 'Vigente',
    color: 'bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25',
  },
  POR_VENCER: {
    label: 'Por Vencer',
    color: 'bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25',
  },
};

function formatCurrency(val) {
  if (val == null) return '$ 0';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

export default function ResSegurosPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: polizasRaw, loading, error, refetch } = useFetch(() => api.get('/seguros/polizas/vigentes'));

  const polizas = useMemo(() => {
    if (!polizasRaw) return [];
    if (Array.isArray(polizasRaw)) return polizasRaw;
    if (Array.isArray(polizasRaw.items)) return polizasRaw.items;
    return polizasRaw.data || [];
  }, [polizasRaw]);

  const polizasFiltradas = useMemo(() => {
    if (!searchTerm) return polizas;
    const term = searchTerm.toLowerCase();
    return polizas.filter(
      (p) =>
        p.companiaAseguradora?.toLowerCase().includes(term) ||
        p.numeroPoliza?.toLowerCase().includes(term) ||
        p.ramoCobertura?.toLowerCase().includes(term)
    );
  }, [polizas, searchTerm]);

  return (
    <PageContainer>
      <PageHeader
        title="Pólizas de seguro de la copropiedad"
        description="Coberturas vigentes, carátulas y pólizas obligatorias de los bienes comunes (Ley 675 de 2001)."
        action={
          <Button variant="outline" onClick={() => refetch().catch(() => {})} className="min-h-10">
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Actualizar
          </Button>
        }
      />

      {/* Banner Informativo Ley 675 */}
      <div className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4 text-xs text-text-secondary shadow-sm">
        <div className="shrink-0 rounded-md bg-tint-blue-bg p-2 text-tint-blue-fg">
          <Info className="h-4 w-4" aria-hidden="true" />
        </div>
        <div className="space-y-1">
          <p className="font-bold text-sm text-on-surface">
            Transparencia y protección de bienes comunes (Ley 675 de 2001)
          </p>
          <p className="max-w-[80ch] leading-relaxed">
            De conformidad con el artículo 15 de la Ley 675 de 2001, toda copropiedad está legalmente obligada a contratar
            pólizas de seguro que amparen contra los riesgos de incendio y terremoto los bienes comunes, garantizando la reconstrucción total de la propiedad horizontal.
          </p>
        </div>
      </div>

      {/* Buscador */}
      <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center">
        <div className="relative w-full sm:w-96">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por aseguradora, póliza o ramo..."
            aria-label="Buscar pólizas"
            className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25"
          />
        </div>
        <p className="px-1 text-xs font-semibold tabular-nums text-text-secondary">
          {polizasFiltradas.length} {polizasFiltradas.length === 1 ? 'póliza vigente encontrada' : 'pólizas vigentes encontradas'}
        </p>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Consultando pólizas vigentes…" />
        </div>
      ) : error ? (
        <ErrorState
          title="No se pudieron cargar las pólizas"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
      ) : polizasFiltradas.length === 0 ? (
        <EmptyState
          icon={Shield}
          title={searchTerm ? 'No se encontraron resultados' : 'Sin pólizas vigentes registradas'}
          subtitle={
            searchTerm
              ? 'Intenta con otros términos de búsqueda.'
              : 'La administración aún no ha registrado pólizas vigentes de las áreas comunes.'
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {polizasFiltradas.map((p) => {
            const estadoCfg = ESTADOS_POLIZA[p.estado] || {
              label: p.estado,
              color: 'bg-surface-container text-on-surface border border-border',
            };
            return (
              <div
                key={p.idPoliza}
                className="flex flex-col justify-between rounded-lg border border-border bg-surface p-5 shadow-sm transition-colors duration-150 hover:border-primary/50"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <span className={`rounded-[3px] px-2 py-0.5 text-xs font-bold ${estadoCfg.color}`}>
                      {estadoCfg.label}
                    </span>
                    <span className="rounded-[3px] border border-border bg-surface-container px-2 py-0.5 text-xs font-semibold tabular-nums text-text-secondary">
                      Ref #{p.idPoliza}
                    </span>
                  </div>

                  <h4 className="font-bold text-base text-on-surface mb-1 leading-snug">
                    {p.ramoCobertura}
                  </h4>
                  <p className="text-xs font-semibold text-primary-text mb-3">
                    {p.companiaAseguradora} &bull; Póliza N° {p.numeroPoliza}
                  </p>

                  <div className="space-y-2 border-t border-border pt-3 text-xs">
                    <div className="flex justify-between items-center text-text-muted">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-primary-text" /> Vigencia:
                      </span>
                      <span className="font-semibold tabular-nums text-on-surface">
                        {formatDate(p.fechaInicio)} – {formatDate(p.fechaFin)}
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-text-muted">
                      <span className="flex items-center gap-1.5">
                        <Shield className="w-3.5 h-3.5 text-primary-text" /> Suma asegurada:
                      </span>
                      <span className="font-bold tabular-nums text-on-surface">
                        {formatCurrency(p.valorAsegurado)}
                      </span>
                    </div>

                    {p.deducible && (
                      <div className="flex justify-between items-center text-text-muted">
                        <span className="flex items-center gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-tint-green-fg" /> Deducible:
                        </span>
                        <span className="font-medium text-on-surface text-right max-w-[60%]">
                          {p.deducible}
                        </span>
                      </div>
                    )}

                    {p.nombreCorredorAgente && (
                      <div className="flex justify-between items-center text-text-muted pt-1">
                        <span className="flex items-center gap-1.5">
                          <User className="w-3.5 h-3.5 text-primary-text" /> Corredor / Asesor:
                        </span>
                        <span className="font-medium text-on-surface">
                          {p.nombreCorredorAgente}
                        </span>
                      </div>
                    )}

                    {p.telefonoContactoAgente && (
                      <div className="flex justify-between items-center text-text-muted">
                        <span className="flex items-center gap-1.5">
                          <Phone className="w-3.5 h-3.5 text-primary-text" /> Teléfono del asesor:
                        </span>
                        <a
                          href={`tel:${p.telefonoContactoAgente}`}
                          className="font-semibold text-primary-text hover:underline flex items-center gap-1"
                        >
                          {p.telefonoContactoAgente}
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                <div className="border-t border-border pt-3 mt-4 flex items-center justify-between">
                  {p.idDocumento ? (
                    <a
                      href={`/api/v1/documentos/${p.idDocumento}/descargar`}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-10 items-center gap-1.5 rounded-md border border-border bg-surface px-3 text-xs font-semibold text-primary-text transition-colors duration-150 hover:border-primary/50 sm:min-h-8"
                    >
                      <FileText className="h-4 w-4" aria-hidden="true" /> Descargar certificado
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  ) : safeHttpUrl(p.documentoCaratulaUrl) ? (
                    <a
                      href={safeHttpUrl(p.documentoCaratulaUrl)}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-10 items-center gap-1.5 rounded-md border border-border bg-surface px-3 text-xs font-semibold text-primary-text transition-colors duration-150 hover:border-primary/50 sm:min-h-8"
                    >
                      <FileText className="h-3.5 w-3.5" aria-hidden="true" /> Ver carátula digital
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  ) : (
                    <span className="text-xs text-text-muted italic">Documento físico en administración</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
