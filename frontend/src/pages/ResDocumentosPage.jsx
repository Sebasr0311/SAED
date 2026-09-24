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
import { Card } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import EmptyState from '../components/ui/EmptyState.jsx';
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
        title="Biblioteca de Documentos"
        subtitle="Reglamentos internos, manuales de convivencia y actas oficiales de la copropiedad"
        action={
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="w-4 h-4 mr-1.5" />
            Actualizar
          </Button>
        }
      />

      {/* 1. KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <MetricCard
          label="Documentos Públicos"
          value={String(stats.total)}
          icon={FileText}
          variant="primary"
          context="Disponibles para consulta"
        />
        <MetricCard
          label="Reglamentos y Manuales"
          value={String(stats.reglamentos)}
          icon={BookOpen}
          variant="success"
          context="Normas de convivencia"
        />
        <MetricCard
          label="Actas de Asamblea"
          value={String(stats.actas)}
          icon={FileCheck}
          variant="primary"
          context="Decisiones de copropietarios"
        />
      </div>

      {/* 2. Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between mb-6">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {categorias.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setCategoriaFiltro(cat)}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                categoriaFiltro === cat
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-card border border-border text-muted-foreground hover:text-foreground hover:bg-muted/50'
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
            <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Buscar por título o contenido..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 text-sm rounded-lg border border-border bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Documentos */}
      {loading ? (
        <LoadingState text="Cargando biblioteca de documentos oficiales..." />
      ) : error ? (
        <Card className="p-8 text-center">
          <p className="text-sm font-medium text-destructive">{error.message || 'Error al cargar documentos'}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => refetch()}>
            Reintentar
          </Button>
        </Card>
      ) : filteredDocs.length === 0 ? (
        <EmptyState
          icon="folder_off"
          title="No hay documentos disponibles"
          subtitle="Los reglamentos, actas y circulares publicadas por la administración aparecerán aquí."
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocs.map((doc) => {
            const CatIcon = CATEGORIA_ICONS[doc.categoria] || FileText;
            const size = formatFileSize(doc.archivoTamanoBytes);

            return (
              <div
                key={doc.idDocumento}
                className="group bg-card border border-border rounded-xl p-4 sm:p-5 hover:border-primary/40 hover:shadow-md transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div className="p-3 rounded-xl bg-primary/10 text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-colors shrink-0">
                      <CatIcon className="w-5 h-5" />
                    </div>
                    {doc.categoria && (
                      <Badge variant="secondary" className="text-xs font-semibold">
                        {CATEGORIA_LABELS[doc.categoria] || doc.categoria}
                      </Badge>
                    )}
                  </div>

                  <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors line-clamp-2">
                    {doc.titulo}
                  </h3>
                  {doc.descripcion && (
                    <p className="text-xs text-muted-foreground mt-1.5 line-clamp-2 leading-relaxed">
                      {doc.descripcion}
                    </p>
                  )}
                </div>

                <div className="pt-4 mt-3 border-t border-border/60 flex items-center justify-between text-xs text-muted-foreground">
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5" />
                      <span>{formatDate(doc.fechaCreacion)}</span>
                    </div>
                    {size && <span>{size} • v{doc.numeroVersion || 1}</span>}
                  </div>

                  <button
                    type="button"
                    onClick={() => handleDownload(doc)}
                    disabled={downloadingId === doc.idDocumento}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm disabled:opacity-50"
                  >
                    <Download className="w-3.5 h-3.5" />
                    {downloadingId === doc.idDocumento ? 'Descargando...' : 'Descargar'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
