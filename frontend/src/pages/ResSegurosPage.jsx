import React, { useState, useMemo } from 'react';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Card } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  FileText,
  Calendar,
  DollarSign,
  ExternalLink,
  Phone,
  User,
  Search,
  RefreshCw,
  Building2,
  CheckCircle2,
  Clock,
  Info,
} from 'lucide-react';

const ESTADOS_POLIZA = {
  VIGENTE: { label: 'Vigente', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  POR_VENCER: { label: 'Por Vencer', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
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
        title="Pólizas de Seguro de la Copropiedad"
        description="Consulta de coberturas activas, carátulas y pólizas obligatorias de bienes comunes (Ley 675 de 2001)"
        action={
          <button
            onClick={() => refetch()}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground bg-muted hover:bg-muted/80 rounded-lg transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Actualizar
          </button>
        }
      />

      {/* Banner Informativo Ley 675 */}
      <div className="bg-primary/5 border border-primary/20 rounded-xl p-4 flex items-start gap-3 text-xs text-muted-foreground">
        <Info className="w-5 h-5 text-primary shrink-0 mt-0.5" />
        <div className="space-y-1">
          <p className="font-semibold text-foreground">
            Transparencia y Protección de Bienes Comunes
          </p>
          <p>
            De conformidad con el Artículo 15 de la Ley 675 de 2001, toda copropiedad está legalmente obligada a contratar
            pólizas de seguro que amparen contra los riesgos de incendio y terremoto los bienes comunes, garantizando la reconstrucción total de la propiedad horizontal.
          </p>
        </div>
      </div>

      {/* Buscador */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="relative w-full sm:w-96">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por aseguradora, póliza o ramo..."
            className="w-full pl-9 pr-3 py-2 text-sm bg-background border border-border rounded-lg focus:ring-2 focus:ring-primary/20 focus:border-primary"
          />
        </div>
        <p className="text-xs text-muted-foreground">
          {polizasFiltradas.length} {polizasFiltradas.length === 1 ? 'póliza vigente encontrada' : 'pólizas vigentes encontradas'}
        </p>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <LoadingState message="Consultando pólizas vigentes de la copropiedad..." />
      ) : error ? (
        <div className="bg-destructive/10 border border-destructive/20 text-destructive text-sm p-4 rounded-xl flex items-center gap-2">
          <ShieldAlert className="w-4 h-4" />
          <span>No fue posible cargar las pólizas de seguro: {error.message || 'Error de conexión'}</span>
        </div>
      ) : polizasFiltradas.length === 0 ? (
        <EmptyState
          icon={Shield}
          title={searchTerm ? 'No se encontraron resultados' : 'Sin pólizas vigentes registradas'}
          description={
            searchTerm
              ? 'Intente con otros términos de búsqueda.'
              : 'La administración de la copropiedad aún no ha registrado pólizas vigentes de áreas comunes en la plataforma.'
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {polizasFiltradas.map((p) => {
            const estadoCfg = ESTADOS_POLIZA[p.estado] || { label: p.estado, color: 'bg-muted text-muted-foreground' };
            return (
              <div
                key={p.idPoliza}
                className="bg-card border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${estadoCfg.color}`}>
                      {estadoCfg.label}
                    </span>
                    <span className="text-xs font-medium text-muted-foreground bg-muted/60 px-2 py-0.5 rounded">
                      Ref #{p.idPoliza}
                    </span>
                  </div>

                  <h4 className="font-bold text-base text-foreground mb-1 leading-snug">
                    {p.ramoCobertura}
                  </h4>
                  <p className="text-xs font-semibold text-primary mb-3">
                    {p.companiaAseguradora} &bull; Póliza N° {p.numeroPoliza}
                  </p>

                  <div className="space-y-2 border-t border-border/60 pt-3 text-xs">
                    <div className="flex justify-between items-center text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5" /> Vigencia:
                      </span>
                      <span className="font-medium text-foreground">
                        {p.fechaInicio} &rarr; {p.fechaFin}
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Shield className="w-3.5 h-3.5" /> Suma Asegurada:
                      </span>
                      <span className="font-semibold text-foreground">
                        {formatCurrency(p.valorAsegurado)}
                      </span>
                    </div>

                    {p.deducible && (
                      <div className="flex justify-between items-center text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Deducible:
                        </span>
                        <span className="font-medium text-foreground text-right max-w-[60%]">
                          {p.deducible}
                        </span>
                      </div>
                    )}

                    {p.nombreCorredorAgente && (
                      <div className="flex justify-between items-center text-muted-foreground pt-1">
                        <span className="flex items-center gap-1.5">
                          <User className="w-3.5 h-3.5" /> Corredor / Asesor:
                        </span>
                        <span className="font-medium text-foreground">
                          {p.nombreCorredorAgente}
                        </span>
                      </div>
                    )}

                    {p.telefonoContactoAgente && (
                      <div className="flex justify-between items-center text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                          <Phone className="w-3.5 h-3.5" /> Teléfono Asesor:
                        </span>
                        <a
                          href={`tel:${p.telefonoContactoAgente}`}
                          className="font-medium text-primary hover:underline flex items-center gap-1"
                        >
                          {p.telefonoContactoAgente}
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                <div className="border-t border-border/60 pt-3 mt-4 flex items-center justify-between">
                  {p.idDocumento ? (
                    <a
                      href={`/api/v1/documentos/${p.idDocumento}/descargar`}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline font-semibold bg-primary/10 px-3 py-1.5 rounded-lg"
                    >
                      <FileText className="w-4 h-4 text-primary" /> Descargar Certificado Oficial F10-01
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  ) : p.documentoCaratulaUrl ? (
                    <a
                      href={p.documentoCaratulaUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline font-medium bg-muted px-2.5 py-1 rounded"
                    >
                      <FileText className="w-3.5 h-3.5" /> Ver Carátula Digital
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  ) : (
                    <span className="text-xs text-muted-foreground italic">Documento físico en administración</span>
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
