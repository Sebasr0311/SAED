import React, { useState, useMemo } from 'react';
import { PageContainer } from '../components/layout/PageContainer';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/button';
import EmptyState from '../components/ui/EmptyState';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState';
import { useFetch } from '../lib/hooks';
import api from '../lib/api';
import { formatDate } from '../lib/utils';
import { toast } from 'sonner';
import {
  BookOpen,
  Download,
  Calendar,
  ShieldCheck,
  Hash,
  Search,
  CheckCircle2,
  FileText,
  Copy,
} from 'lucide-react';

const TIPOS_LABELS = {
  REGLAMENTO_INTERNO: 'Reglamento Interno',
  MANUAL_CONVIVENCIA: 'Manual de Convivencia',
  MANUAL_ZONAS_COMUNES: 'Zonas Comunes',
  MANUAL_POLITICA_MASCOTAS: 'Política de Mascotas',
  ESTATUTO_COPROPIEDAD: 'Estatuto de Copropiedad',
  OTRO: 'Normativa General',
};

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function ResReglamentosPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/reglamentos/residente'));
  const [searchTerm, setSearchTerm] = useState('');
  const [downloadingId, setDownloadingId] = useState(null);

  const reglamentos = useMemo(() => {
    return data?.items || data || [];
  }, [data]);

  // Highlighted main bylaws
  const mainReglamento = useMemo(() => {
    return reglamentos.find((r) => r.tipoNormativa === 'REGLAMENTO_INTERNO');
  }, [reglamentos]);

  const mainManualConvivencia = useMemo(() => {
    return reglamentos.find((r) => r.tipoNormativa === 'MANUAL_CONVIVENCIA');
  }, [reglamentos]);

  // Filtered
  const filteredReglamentos = useMemo(() => {
    if (!searchTerm) return reglamentos;
    const term = searchTerm.toLowerCase();
    return reglamentos.filter(
      (r) =>
        r.titulo?.toLowerCase().includes(term) ||
        r.descripcion?.toLowerCase().includes(term) ||
        TIPOS_LABELS[r.tipoNormativa]?.toLowerCase().includes(term)
    );
  }, [reglamentos, searchTerm]);

  const handleDownload = async (reg) => {
    try {
      setDownloadingId(reg.idReglamento);
      const response = await api.get(`/reglamentos/${reg.idReglamento}/descargar`, {
        responseType: 'blob',
      });
      const blob = new Blob([response.data], {
        type: reg.archivoMimeType || 'application/pdf',
      });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', reg.archivoNombreOrig || `${reg.titulo}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      window.URL.revokeObjectURL(url);
      toast.success('Descarga completada');
    } catch (err) {
      toast.error('Error al descargar el reglamento: ' + (err.response?.data?.message || err.message));
    } finally {
      setDownloadingId(null);
    }
  };

  const copySha256 = (sha) => {
    if (!sha) return;
    navigator.clipboard.writeText(sha);
    toast.success('Huella digital SHA-256 copiada al portapapeles');
  };

  if (!loading && error) {
    return (
      <PageContainer>
        <PageHeader
          title="Reglamentos y normativa de la copropiedad"
          description="Consulta los reglamentos internos, manuales de convivencia y estatutos vigentes."
        />
        <ErrorState
          title="No se pudieron cargar los reglamentos"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
      </PageContainer>
    );
  }

  if (loading) {
    return (
      <PageContainer>
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando reglamentos y normativa vigente…" />
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="space-y-6">
        <PageHeader
          title="Reglamentos y normativa de la copropiedad"
          description="Consulta los reglamentos internos, manuales de convivencia y estatutos vigentes."
        />

        {/* Hero Cards for Key Regulations */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Main Reglamento Interno - Deep Ink #1D2942 & Indigo #5267DA Gradient */}
          <div className="rounded-lg border border-border bg-surface p-5 shadow-sm sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-2">
                <span className="inline-flex items-center gap-1.5 rounded-[3px] bg-tint-purple-bg px-2 py-0.5 text-xs font-bold text-tint-purple-fg">
                  <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />
                  Norma principal (Ley 675)
                </span>
                <h3 className="app-display text-lg leading-snug text-on-surface">
                  {mainReglamento ? mainReglamento.titulo : 'Reglamento de Propiedad Horizontal'}
                </h3>
                <p className="line-clamp-2 text-[13px] leading-relaxed text-text-secondary">
                  {mainReglamento?.descripcion ||
                    'Régimen legal de propiedad horizontal, derechos, deberes y coexistencia condominal.'}
                </p>
              </div>
              <div className="shrink-0 rounded-md bg-tint-purple-bg p-2.5 text-tint-purple-fg">
                <BookOpen className="h-5 w-5" aria-hidden="true" />
              </div>
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4 text-xs">
              {mainReglamento ? (
                <>
                  <div className="flex items-center gap-3">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-tint-green-bg text-tint-green-fg font-semibold">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Vigente
                    </span>
                    {mainReglamento.fechaEntradaEnVigor && (
                      <span className="flex items-center gap-1 text-text-muted">
                        <Calendar className="w-3.5 h-3.5" />
                        Desde {formatDate(mainReglamento.fechaEntradaEnVigor)}
                      </span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    onClick={() => handleDownload(mainReglamento)}
                    disabled={downloadingId === mainReglamento.idReglamento}
                    className="min-h-10 sm:min-h-8"
                  >
                    <Download className="h-4 w-4" aria-hidden="true" />
                    {downloadingId === mainReglamento.idReglamento ? 'Descargando…' : 'Descargar PDF'}
                  </Button>
                </>
              ) : (
                <div className="text-xs text-text-muted">
                  Aún no se ha publicado el reglamento interno oficial.
                </div>
              )}
            </div>
          </div>

          {/* Main Manual de Convivencia - Lavender #EEE8FF & Pastel Blue #DFEBFF Gradient */}
          <div className="rounded-lg border border-border bg-surface p-5 shadow-sm sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-2">
                <span className="inline-flex items-center gap-1.5 rounded-[3px] border border-tint-green-fg/25 bg-tint-green-bg px-2 py-0.5 text-xs font-bold text-tint-green-fg">
                  <BookOpen className="h-3.5 w-3.5" aria-hidden="true" />
                  Manual esencial
                </span>
                <h3 className="app-display text-lg leading-snug text-on-surface">
                  {mainManualConvivencia ? mainManualConvivencia.titulo : 'Manual de Convivencia y Vecindad'}
                </h3>
                <p className="text-xs text-text-secondary line-clamp-2 leading-relaxed">
                  {mainManualConvivencia?.descripcion ||
                    'Pautas para una convivencia armónica: ruidos, mascotas, mudanzas y zonas comunes.'}
                </p>
              </div>
              <div className="shrink-0 rounded-md bg-tint-green-bg p-2.5 text-tint-green-fg">
                <FileText className="h-5 w-5" aria-hidden="true" />
              </div>
            </div>

            <div className="mt-5 pt-4 border-t border-border flex flex-wrap items-center justify-between gap-3 text-xs">
              {mainManualConvivencia ? (
                <>
                  <div className="flex items-center gap-3 text-text-secondary">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-tint-green-bg text-tint-green-fg font-semibold">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Vigente
                    </span>
                    {mainManualConvivencia.fechaEntradaEnVigor && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-primary-text" />
                        Desde {formatDate(mainManualConvivencia.fechaEntradaEnVigor)}
                      </span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={() => handleDownload(mainManualConvivencia)}
                    disabled={downloadingId === mainManualConvivencia.idReglamento}
                    className="flex items-center gap-2 shadow-sm"
                  >
                    <Download className="w-4 h-4" />
                    {downloadingId === mainManualConvivencia.idReglamento ? 'Descargando…' : 'Descargar PDF'}
                  </Button>
                </>
              ) : (
                <div className="text-text-muted text-xs italic">
                  Aún no se ha publicado el manual de convivencia oficial.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Filter bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-3 rounded-xl bg-surface-container border border-border">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-text-muted" />
            <input
              type="text"
              placeholder="Buscar normativas o reglamentos por palabra clave..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm rounded-xl bg-surface border border-border text-on-surface placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-ring focus:border-primary"
            />
          </div>
          <div className="text-xs text-on-surface font-semibold px-2">
            {filteredReglamentos.length} documento{filteredReglamentos.length !== 1 ? 's' : ''} disponible{filteredReglamentos.length !== 1 ? 's' : ''}
          </div>
        </div>

        {/* Regulations Grid */}
        {filteredReglamentos.length === 0 ? (
          <EmptyState
            icon={BookOpen}
            title="Sin normativas publicadas"
            description={
              searchTerm
                ? 'No se encontraron reglamentos que coincidan con la búsqueda.'
                : 'La administración de la copropiedad aún no ha publicado reglamentos en este módulo.'
            }
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filteredReglamentos.map((reg) => (
              <div
                key={reg.idReglamento}
                className="flex flex-col justify-between p-5 rounded-xl border border-border bg-surface hover:border-primary/50 hover:shadow-md transition-all duration-200"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-tint-purple-bg text-primary-text border border-border">
                      {TIPOS_LABELS[reg.tipoNormativa] || reg.tipoNormativa}
                    </span>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] bg-tint-green-bg text-tint-green-fg font-semibold">
                      <CheckCircle2 className="w-3 h-3" />
                      Vigente
                    </span>
                  </div>

                  <div>
                    <h4 className="font-bold text-on-surface text-base line-clamp-2">{reg.titulo}</h4>
                    {reg.descripcion && (
                      <p className="mt-1 text-xs text-text-muted line-clamp-3 leading-relaxed">
                        {reg.descripcion}
                      </p>
                    )}
                  </div>

                  <div className="space-y-2 pt-2.5 border-t border-border text-xs text-text-muted">
                    {reg.fechaEntradaEnVigor && (
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-primary-text" />
                        <span>En vigor desde: {formatDate(reg.fechaEntradaEnVigor)}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-1.5 text-on-surface font-medium">
                      <FileText className="w-3.5 h-3.5 text-primary-text" />
                      <span className="truncate max-w-[220px]" title={reg.archivoNombreOrig}>
                        {reg.archivoNombreOrig || 'Documento oficial'}
                      </span>
                      {reg.archivoTamanoBytes > 0 && (
                        <span className="text-[11px] text-text-muted">
                          ({formatFileSize(reg.archivoTamanoBytes)})
                        </span>
                      )}
                    </div>

                    {reg.archivoSha256 && (
                      <div className="flex items-center justify-between text-[10px] font-mono bg-tint-purple-bg text-on-surface p-2 rounded-lg border border-border">
                        <div className="flex items-center gap-1.5 truncate" title={reg.archivoSha256}>
                          <Hash className="w-3.5 h-3.5 text-primary-text flex-shrink-0" />
                          <span className="truncate font-semibold">SHA-256: {reg.archivoSha256.substring(0, 14)}...</span>
                        </div>
                        <button
                          type="button"
                          onClick={() => copySha256(reg.archivoSha256)}
                          className="hover:text-primary-text text-text-muted p-0.5 rounded transition-colors"
                          title="Copiar huella digital SHA-256"
                        >
                          <Copy className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                <div className="pt-4 mt-4 border-t border-border">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleDownload(reg)}
                    disabled={downloadingId === reg.idReglamento}
                    className="w-full flex items-center justify-center gap-2 text-xs font-semibold border-border text-primary-text hover:bg-surface-selected hover:border-primary/50"
                  >
                    <Download className="w-3.5 h-3.5" />
                    {downloadingId === reg.idReglamento ? 'Descargando…' : 'Descargar Documento Oficial (PDF)'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </PageContainer>
  );
}
