import { useState, useMemo } from 'react';
import {
  FileText,
  Download,
  Search,
  Calendar,
  Layers,
  BookOpen,
  FileCheck,
  ShieldCheck,
  Building,
  RefreshCw,
} from 'lucide-react';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';

import { useFetch } from '../lib/hooks.js';
import api, { BASE_URL } from '../lib/api.js';
import { formatDate } from '../lib/utils.js';
import { toast } from 'sonner';

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

const CATEGORIA_ICONS = {
  REGLAMENTO_INTERNO: BookOpen,
  RUT_MATRICULA: ShieldCheck,
  ACTA_ASAMBLEA: FileCheck,
  CONTRATO_PROVEEDOR: FileText,
  POLIZA_SEGURO: ShieldCheck,
  ESTADO_FINANCIERO: FileText,
  PLANOS: Layers,
  MANUAL_CONVIVENCIA: BookOpen,
  OTRO: FileText,
  // legacy compatibility fallbacks
  REGLAMENTO: BookOpen,
  ACTA: FileCheck,
  MANUAL: Layers,
  FINANZAS: FileText,
  CIRCULAR: Building,
};

const CATEGORIA_LABELS = {
  REGLAMENTO_INTERNO: 'Reglamento Interno',
  RUT_MATRICULA: 'RUT / Matrícula',
  ACTA_ASAMBLEA: 'Acta de Asamblea',
  CONTRATO_PROVEEDOR: 'Contrato de Proveedor',
  POLIZA_SEGURO: 'Póliza de Seguro',
  ESTADO_FINANCIERO: 'Estado Financiero',
  PLANOS: 'Planos',
  MANUAL_CONVIVENCIA: 'Manual de Convivencia',
  OTRO: 'Otro Documento',
};

export default function ResDocumentosPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/documentos/residente'), []);
  const [categoriaFiltro, setCategoriaFiltro] = useState('TODAS');
  const [search, setSearch] = useState('');
  const [downloadingId, setDownloadingId] = useState(null);

  const documentos = useMemo(() => {
    const list =
      data?.items ||
      data?.data?.items ||
      (Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []));
    return Array.isArray(list) ? list : [];
  }, [data]);

  const categorias = useMemo(() => {
    const set = new Set(documentos.map((d) => d.categoria).filter(Boolean));
    return ['TODAS', ...Array.from(set)];
  }, [documentos]);

  const stats = useMemo(() => {
    const total = documentos.length;
    const reglamentos = documentos.filter(
      (d) =>
        (d.categoria || '').toUpperCase().includes('REGLA') ||
        (d.categoria || '').toUpperCase().includes('MANUAL')
    ).length;
    const actas = documentos.filter((d) => (d.categoria || '').toUpperCase().includes('ACTA')).length;

    return { total, reglamentos, actas };
  }, [documentos]);

  const filteredDocs = useMemo(() => {
    return documentos.filter((d) => {
      if (categoriaFiltro !== 'TODAS' && d.categoria !== categoriaFiltro) return false;
      if (search.trim()) {
        const q = search.toLowerCase().trim();
        const tit = (d.titulo || '').toLowerCase();
        const desc = (d.descripcion || '').toLowerCase();
        const cat = (CATEGORIA_LABELS[d.categoria] || d.categoria || '').toLowerCase();
        return tit.includes(q) || desc.includes(q) || cat.includes(q);
      }
      return true;
    });
  }, [documentos, categoriaFiltro, search]);

  // Authenticated binary download
  async function handleDownload(doc) {
    if (!doc?.idDocumento) return;
    try {
      setDownloadingId(doc.idDocumento);
      const token = sessionStorage.getItem('saed_jwt_token');
      const activeAssignment = sessionStorage.getItem('saed_active_assignment_id');
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;
      if (activeAssignment) headers['X-Assignment-Id'] = activeAssignment;

      const res = await fetch(`${BASE_URL}/documentos/${doc.idDocumento}/descargar`, { headers });
      if (!res.ok) {
        let msg = `Error al descargar documento (${res.status})`;
        try {
          const errData = await res.json();
          msg = errData.message || errData.mensaje || errData.error || msg;
        } catch (_) {}
        throw new Error(msg);
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;

      let filename = doc.nombreArchivo || `documento_${doc.idDocumento}.pdf`;
      const disposition = res.headers.get('Content-Disposition');
      if (disposition && disposition.includes('filename=')) {
        const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        if (match && match[1]) {
          filename = match[1].replace(/['"]/g, '');
        }
      }

      a.download = filename;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }, 200);

      toast.success(`Descarga completada: ${filename}`);
    } catch (err) {
      toast.error(err.message || 'Error al descargar documento');
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <PageContainer>
      <PageHeader
        title="Biblioteca de documentos"
        subtitle="Reglamentos internos, manuales de convivencia y actas oficiales de la copropiedad."
        action={
          <Button variant="outline" onClick={() => refetch().catch(() => {})} className="min-h-10">
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Actualizar
          </Button>
        }
      />

      {/* 1. KPIs */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <MetricCard
          label="Documentos públicos"
          value={loading ? '…' : error ? '—' : String(stats.total)}
          icon={FileText}
          variant="primary"
          context="Disponibles para consulta"
        />
        <MetricCard
          label="Reglamentos y manuales"
          value={loading ? '…' : error ? '—' : String(stats.reglamentos)}
          icon={BookOpen}
          variant="success"
          context="Normas de convivencia"
        />
        <MetricCard
          label="Actas de asamblea"
          value={loading ? '…' : error ? '—' : String(stats.actas)}
          icon={FileCheck}
          variant="info"
          context="Decisiones de copropietarios"
        />
      </div>

      {/* 2. Filtros y Búsqueda */}
      <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {categorias.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setCategoriaFiltro(cat)}
              aria-pressed={categoriaFiltro === cat}
              className={`min-h-10 rounded-md px-3.5 text-xs font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-h-9 ${
                categoriaFiltro === cat
                  ? 'bg-btn-primary text-white shadow-sm'
                  : 'border border-border bg-surface text-text-secondary hover:bg-surface-container hover:text-on-surface'
              }`}
            >
              {cat === 'TODAS'
                ? `Todos (${documentos.length})`
                : `${CATEGORIA_LABELS[cat] || cat}`}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-72">
          <div className="relative">
            <Search className="w-4 h-4 text-text-muted absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Buscar por título o contenido..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Buscar documentos"
              className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Documentos */}
      {loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando documentos…" />
        </div>
      ) : error ? (
        <ErrorState
          title="No se pudieron cargar los documentos"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
      ) : filteredDocs.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={search || categoriaFiltro !== 'TODAS' ? 'Sin documentos para este filtro' : 'No hay documentos disponibles'}
          subtitle={
            search || categoriaFiltro !== 'TODAS'
              ? 'Prueba con otra categoría o término de búsqueda.'
              : 'Los reglamentos, actas y circulares que publique la administración aparecerán aquí.'
          }
        >
          {(search || categoriaFiltro !== 'TODAS') && (
            <Button
              variant="outline"
              size="sm"
              className="mt-3"
              onClick={() => {
                setSearch('');
                setCategoriaFiltro('TODAS');
              }}
            >
              Ver todos los documentos
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocs.map((doc) => {
            const CatIcon = CATEGORIA_ICONS[doc.categoria] || FileText;
            const size = formatFileSize(doc.archivoTamanoBytes);
            const isLegalOrReglamento =
              (doc.categoria || '').includes('REGLA') ||
              (doc.categoria || '').includes('MANUAL') ||
              (doc.categoria || '').includes('POLIZA');

            return (
              <div
                key={doc.idDocumento}
                className="flex flex-col justify-between rounded-lg border border-border bg-surface p-4 shadow-sm transition-colors duration-150 hover:border-primary/50 sm:p-5"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div
                      className={`shrink-0 rounded-md p-2.5 ${
                        isLegalOrReglamento ? 'bg-tint-purple-bg text-tint-purple-fg' : 'bg-tint-blue-bg text-tint-blue-fg'
                      }`}
                    >
                      <CatIcon className="w-5 h-5" />
                    </div>
                    {doc.categoria && (
                      <Badge
                        variant={isLegalOrReglamento ? 'lavender' : 'secondary'}
                        className="text-xs font-semibold"
                      >
                        {CATEGORIA_LABELS[doc.categoria] || doc.categoria}
                      </Badge>
                    )}
                  </div>

                  <h3 className="line-clamp-2 text-[15px] font-bold text-on-surface">
                    {doc.titulo}
                  </h3>
                  {doc.descripcion && (
                    <p className="text-xs text-text-muted mt-1.5 line-clamp-2 leading-relaxed">
                      {doc.descripcion}
                    </p>
                  )}
                </div>

                <div className="pt-4 mt-3 border-t border-border flex items-center justify-between text-xs text-text-muted">
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-primary-text" />
                      <span>{formatDate(doc.fechaCreacion)}</span>
                    </div>
                    {size && (
                      <span className="inline-block font-medium tabular-nums text-text-secondary">
                        {size} • v{doc.numeroVersion || 1}
                      </span>
                    )}
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleDownload(doc)}
                    disabled={downloadingId === doc.idDocumento}
                    aria-label={`Descargar ${doc.titulo}`}
                    className="min-h-10 sm:min-h-8"
                  >
                    <Download className="h-3.5 w-3.5" aria-hidden="true" />
                    {downloadingId === doc.idDocumento ? 'Descargando…' : 'Descargar'}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
