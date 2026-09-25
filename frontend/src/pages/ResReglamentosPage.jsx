import React, { useState, useMemo } from 'react';
import { PageContainer } from '../components/layout/PageContainer';
import { PageHeader } from '../components/ui/PageHeader';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import EmptyState from '../components/ui/EmptyState';
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
  Info,
  Layers,
  Sparkles,
  ExternalLink,
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

  if (loading) {
    return (
      <PageContainer>
        <LoadingState message="Cargando reglamentos y normativa vigente..." />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className="space-y-6">
        <PageHeader
          title="Reglamentos y Normativa de la Copropiedad"
          description="Consulte los reglamentos internos, manuales de convivencia y estatutos oficiales legalmente vigentes."
        />

        {/* Hero Cards for Key Regulations */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Main Reglamento Interno */}
          <div className="relative overflow-hidden rounded-2xl border border-blue-500/20 bg-gradient-to-br from-blue-50/60 via-card to-blue-50/30 dark:from-slate-900 dark:via-slate-800 dark:to-blue-950/40 p-6 shadow-sm">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-2">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/30">
                  <ShieldCheck className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
                  Norma Principal
                </span>
                <h3 className="text-xl font-bold text-foreground">
                  {mainReglamento ? mainReglamento.titulo : 'Reglamento Interno de Copropiedad'}
                </h3>
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {mainReglamento?.descripcion ||
                    'Régimen legal de propiedad horizontal, derechos, deberes y coexistencia condominal.'}
                </p>
              </div>
              <div className="p-3 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-600 dark:text-blue-400">
                <BookOpen className="w-7 h-7" />
              </div>
            </div>

            <div className="mt-5 pt-4 border-t border-border flex flex-wrap items-center justify-between gap-3 text-xs">
              {mainReglamento ? (
                <>
                  <div className="flex items-center gap-4 text-muted-foreground">
                    <span className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-medium">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Vigente
                    </span>
                    {mainReglamento.fechaEntradaEnVigor && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                        Desde {formatDate(mainReglamento.fechaEntradaEnVigor)}
                      </span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    onClick={() => handleDownload(mainReglamento)}
                    disabled={downloadingId === mainReglamento.idReglamento}
                    className="bg-blue-600 hover:bg-blue-500 text-white flex items-center gap-2 shadow-sm"
                  >
                    <Download className="w-4 h-4" />
                    {downloadingId === mainReglamento.idReglamento ? 'Descargando...' : 'Descargar PDF'}
                  </Button>
                </>
              ) : (
                <div className="text-muted-foreground text-xs italic">
                  Aún no se ha publicado el reglamento interno oficial.
                </div>
              )}
            </div>
          </div>

          {/* Main Manual de Convivencia */}
          <div className="relative overflow-hidden rounded-2xl border border-emerald-500/20 bg-gradient-to-br from-emerald-50/60 via-card to-emerald-50/30 dark:from-slate-900 dark:via-slate-800 dark:to-emerald-950/40 p-6 shadow-sm">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-2">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
                  <Sparkles className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
                  Manual Esencial
                </span>
                <h3 className="text-xl font-bold text-foreground">
                  {mainManualConvivencia ? mainManualConvivencia.titulo : 'Manual de Convivencia y Vecindad'}
                </h3>
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {mainManualConvivencia?.descripcion ||
                    'Pautas para una convivencia armónica: ruidos, mascotas, mudanzas y zonas comunes.'}
                </p>
              </div>
              <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400">
                <FileText className="w-7 h-7" />
              </div>
            </div>

            <div className="mt-5 pt-4 border-t border-border flex flex-wrap items-center justify-between gap-3 text-xs">
              {mainManualConvivencia ? (
                <>
                  <div className="flex items-center gap-4 text-muted-foreground">
                    <span className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-medium">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Vigente
                    </span>
                    {mainManualConvivencia.fechaEntradaEnVigor && (
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                        Desde {formatDate(mainManualConvivencia.fechaEntradaEnVigor)}
                      </span>
                    )}
                  </div>
                  <Button
                    size="sm"
                    onClick={() => handleDownload(mainManualConvivencia)}
                    disabled={downloadingId === mainManualConvivencia.idReglamento}
                    className="bg-emerald-600 hover:bg-emerald-500 text-white flex items-center gap-2 shadow-sm"
                  >
                    <Download className="w-4 h-4" />
                    {downloadingId === mainManualConvivencia.idReglamento ? 'Descargando...' : 'Descargar PDF'}
                  </Button>
                </>
              ) : (
                <div className="text-muted-foreground text-xs italic">
                  Aún no se ha publicado el manual de convivencia oficial.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Filter bar */}
        <div className="flex items-center justify-between gap-4 pt-2">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Buscar normativas o reglamentos por palabra clave..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-sm rounded-xl bg-background border border-input text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-emerald-500"
            />
          </div>
          <div className="text-xs text-muted-foreground font-medium">
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
                className="flex flex-col justify-between p-5 rounded-xl border border-border bg-card text-card-foreground hover:shadow-md transition-all duration-200"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
                      {TIPOS_LABELS[reg.tipoNormativa] || reg.tipoNormativa}
                    </span>
                    <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400 font-medium">
                      <CheckCircle2 className="w-3 h-3" />
                      Vigente
                    </span>
                  </div>

                  <div>
                    <h4 className="font-semibold text-foreground text-base line-clamp-2">{reg.titulo}</h4>
                    {reg.descripcion && (
                      <p className="mt-1 text-xs text-muted-foreground line-clamp-3">{reg.descripcion}</p>
                    )}
                  </div>

                  <div className="space-y-1.5 pt-2 border-t border-border text-xs text-muted-foreground">
                    {reg.fechaEntradaEnVigor && (
                      <div className="flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                        <span>En vigor desde: {formatDate(reg.fechaEntradaEnVigor)}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-1.5 text-foreground font-medium">
                      <FileText className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
                      <span className="truncate max-w-[220px]" title={reg.archivoNombreOrig}>
                        {reg.archivoNombreOrig || 'Documento oficial'}
                      </span>
                      {reg.archivoTamanoBytes > 0 && (
                        <span className="text-[11px] text-muted-foreground">
                          ({formatFileSize(reg.archivoTamanoBytes)})
                        </span>
                      )}
                    </div>

                    {reg.archivoSha256 && (
                      <div className="flex items-center justify-between text-[10px] text-muted-foreground font-mono bg-muted/50 p-1.5 rounded border border-border">
                        <div className="flex items-center gap-1 truncate" title={reg.archivoSha256}>
                          <Hash className="w-3 h-3 text-muted-foreground flex-shrink-0" />
                          <span className="truncate">SHA: {reg.archivoSha256.substring(0, 16)}...</span>
                        </div>
                        <button
                          type="button"
                          onClick={() => copySha256(reg.archivoSha256)}
                          className="hover:text-foreground text-muted-foreground p-0.5 rounded transition-colors"
                          title="Copiar huella digital"
                        >
                          <Copy className="w-3 h-3" />
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
                    className="w-full flex items-center justify-center gap-2 text-xs border-emerald-500/40 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-500/10 hover:text-emerald-800 dark:hover:text-emerald-200"
                  >
                    <Download className="w-3.5 h-3.5" />
                    {downloadingId === reg.idReglamento ? 'Descargando...' : 'Descargar Documento Oficial (PDF)'}
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
