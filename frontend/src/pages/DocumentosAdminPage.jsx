import React, { useState, useMemo } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Eye, Folder, Globe, Lock, Search, UploadCloud } from 'lucide-react';
import { useFetch } from '../lib/hooks';
import { api, BASE_URL } from '../lib/api';
import { formatDate } from '../lib/utils';
import { toast } from 'sonner';

export const CATEGORIAS_DOCUMENTOS = [
  { value: 'REGLAMENTO_INTERNO', label: 'Reglamento Interno' },
  { value: 'RUT_MATRICULA', label: 'RUT / Matrícula Inmobiliaria' },
  { value: 'ACTA_ASAMBLEA', label: 'Acta de Asamblea' },
  { value: 'CONTRATO_PROVEEDOR', label: 'Contrato de Proveedor' },
  { value: 'POLIZA_SEGURO', label: 'Póliza de Seguro' },
  { value: 'ESTADO_FINANCIERO', label: 'Estado Financiero' },
  { value: 'PLANOS', label: 'Planos Arquitectónicos / Redes' },
  { value: 'MANUAL_CONVIVENCIA', label: 'Manual de Convivencia' },
  { value: 'OTRO', label: 'Otro Documento' },
];

export const CATEGORIAS_MAP = Object.fromEntries(
  CATEGORIAS_DOCUMENTOS.map((c) => [c.value, c.label])
);

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function DocumentosAdminPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/documentos/admin'));

  // Filter & Search states
  const [categoriaFiltro, setCategoriaFiltro] = useState('TODAS');
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [versionModalOpen, setVersionModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState(null);

  // Form states
  const [submitting, setSubmitting] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  // Upload Form
  const [uploadForm, setUploadForm] = useState({
    titulo: '',
    categoria: 'REGLAMENTO_INTERNO',
    descripcion: '',
    esPublicoResidentes: 'N',
    rolMinimoAcceso: 'ADMIN_PROPIEDAD',
  });
  const [uploadFile, setUploadFile] = useState(null);

  // Version Form
  const [versionFile, setVersionFile] = useState(null);
  const [versionNotas, setVersionNotas] = useState('');

  // Edit Form
  const [editForm, setEditForm] = useState({
    titulo: '',
    categoria: 'REGLAMENTO_INTERNO',
    descripcion: '',
    esPublicoResidentes: 'N',
    rolMinimoAcceso: 'ADMIN_PROPIEDAD',
  });

  // Extract documents safely
  const rawDocumentos = useMemo(() => {
    const list =
      data?.items ||
      data?.data?.items ||
      (Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []));
    return Array.isArray(list) ? list : [];
  }, [data]);

  // Filtered documents
  const documentos = useMemo(() => {
    return rawDocumentos.filter((doc) => {
      if (categoriaFiltro !== 'TODAS' && doc.categoria !== categoriaFiltro) return false;
      if (searchTerm.trim()) {
        const q = searchTerm.toLowerCase().trim();
        const titulo = (doc.titulo || '').toLowerCase();
        const descripcion = (doc.descripcion || '').toLowerCase();
        const cat = (CATEGORIAS_MAP[doc.categoria] || doc.categoria || '').toLowerCase();
        const archivo = (doc.nombreArchivo || '').toLowerCase();
        return (
          titulo.includes(q) ||
          descripcion.includes(q) ||
          cat.includes(q) ||
          archivo.includes(q)
        );
      }
      return true;
    });
  }, [rawDocumentos, categoriaFiltro, searchTerm]);

  // KPIs
  const stats = useMemo(() => {
    const total = rawDocumentos.length;
    const publicos = rawDocumentos.filter((d) => d.esPublicoResidentes === 'S').length;
    const privados = total - publicos;
    return { total, publicos, privados };
  }, [rawDocumentos]);

  // =========================================================================
  // ACTIONS
  // =========================================================================

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
      toast.error(err.message || 'Error al descargar el documento');
    } finally {
      setDownloadingId(null);
    }
  }

  // Real multipart upload
  async function handleUploadSubmit(e) {
    e.preventDefault();
    if (!uploadFile) {
      toast.error('Por favor seleccione un archivo para subir');
      return;
    }
    setSubmitting(true);
    try {
      const formData = new FormData();
      formData.append('archivo', uploadFile);
      formData.append('titulo', uploadForm.titulo.trim());
      formData.append('categoria', uploadForm.categoria);
      formData.append('descripcion', uploadForm.descripcion.trim());
      formData.append('esPublicoResidentes', uploadForm.esPublicoResidentes);
      formData.append('rolMinimoAcceso', uploadForm.rolMinimoAcceso);

      await api.postFormData('/documentos/upload', formData);
      toast.success('Documento y versión inicial almacenados correctamente');
      setUploadModalOpen(false);
      setUploadFile(null);
      setUploadForm({
        titulo: '',
        categoria: 'REGLAMENTO_INTERNO',
        descripcion: '',
        esPublicoResidentes: 'N',
        rolMinimoAcceso: 'ADMIN_PROPIEDAD',
      });
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al subir el documento');
    } finally {
      setSubmitting(false);
    }
  }

  // Upload new version
  async function handleVersionSubmit(e) {
    e.preventDefault();
    if (!versionFile || !selectedDoc) {
      toast.error('Por favor seleccione el nuevo archivo de la versión');
      return;
    }
    setSubmitting(true);
    try {
      const formData = new FormData();
      formData.append('archivo', versionFile);
      formData.append('notasCambio', versionNotas.trim());

      await api.postFormData(`/documentos/${selectedDoc.idDocumento}/versiones`, formData);
      toast.success(`Nueva versión cargada para "${selectedDoc.titulo}"`);
      setVersionModalOpen(false);
      setSelectedDoc(null);
      setVersionFile(null);
      setVersionNotas('');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al actualizar versión');
    } finally {
      setSubmitting(false);
    }
  }

  // Edit metadata
  async function handleEditSubmit(e) {
    e.preventDefault();
    if (!selectedDoc) return;
    setSubmitting(true);
    try {
      const payload = {
        titulo: editForm.titulo.trim(),
        categoria: editForm.categoria,
        descripcion: editForm.descripcion.trim(),
        esPublicoResidentes: editForm.esPublicoResidentes,
        rolMinimoAcceso: editForm.rolMinimoAcceso,
      };

      await api.put(`/documentos/${selectedDoc.idDocumento}`, payload);
      toast.success('Metadatos del documento actualizados');
      setEditModalOpen(false);
      setSelectedDoc(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al actualizar metadatos');
    } finally {
      setSubmitting(false);
    }
  }

  // Soft delete
  async function handleDelete(doc) {
    if (!confirm(`¿Confirma que desea eliminar el documento "${doc.titulo}"? Esta acción desactivará el acceso a sus versiones.`)) {
      return;
    }
    try {
      await api.delete(`/documentos/${doc.idDocumento}`);
      toast.success('Documento eliminado correctamente');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar el documento');
    }
  }

  function openVersionModal(doc) {
    setSelectedDoc(doc);
    setVersionFile(null);
    setVersionNotas('');
    setVersionModalOpen(true);
  }

  function openEditModal(doc) {
    setSelectedDoc(doc);
    setEditForm({
      titulo: doc.titulo || '',
      categoria: doc.categoria || 'REGLAMENTO_INTERNO',
      descripcion: doc.descripcion || '',
      esPublicoResidentes: doc.esPublicoResidentes || 'N',
      rolMinimoAcceso: doc.rolMinimoAcceso || 'ADMIN_PROPIEDAD',
    });
    setEditModalOpen(true);
  }

  // Table columns
  const columns = [
    {
      key: 'titulo',
      label: 'Documento',
      render: (r) => (
        <div className="flex flex-col">
          <span className="font-semibold text-foreground text-sm">{r.titulo}</span>
          {r.descripcion && (
            <span className="text-xs text-muted-foreground line-clamp-1">{r.descripcion}</span>
          )}
          <span className="text-[11.5px] text-muted-foreground/80 mt-0.5">
            {r.nombreArchivo || 'archivo'} • {formatFileSize(r.archivoTamanoBytes)}
          </span>
        </div>
      ),
    },
    {
      key: 'categoria',
      label: 'Categoría',
      render: (r) => (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-foreground border border-border">
          {CATEGORIAS_MAP[r.categoria] || r.categoria}
        </span>
      ),
    },
    {
      key: 'esPublicoResidentes',
      label: 'Acceso Residentes',
      render: (r) =>
        r.esPublicoResidentes === 'S' ? (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-tint-green-fg">
            <Eye className="h-3.5 w-3.5" aria-hidden="true" />
            Público
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-tint-amber-fg">
            <Lock className="h-3.5 w-3.5" aria-hidden="true" />
            Confidencial ({r.rolMinimoAcceso || 'Admin'})
          </span>
        ),
    },
    {
      key: 'numeroVersion',
      label: 'Versión',
      render: (r) => (
        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-primary/10 text-primary">
          v{r.numeroVersion || 1}
        </span>
      ),
    },
    {
      key: 'fechaCreacion',
      label: 'Fecha',
      render: (r) => (
        <span className="text-xs text-muted-foreground">
          {formatDate(r.fechaCreacion)}
        </span>
      ),
    },
    {
      key: 'actions',
      label: 'Acciones',
      render: (r) => (
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="sm"
            icon="download"
            title="Descargar archivo original"
            loading={downloadingId === r.idDocumento}
            onClick={() => handleDownload(r)}
          />
          <Button
            variant="ghost"
            size="sm"
            icon="history"
            title="Subir nueva versión"
            onClick={() => openVersionModal(r)}
          />
          <Button
            variant="ghost"
            size="sm"
            icon="edit"
            title="Editar metadatos"
            onClick={() => openEditModal(r)}
          />
          <Button
            variant="ghost"
            size="sm"
            icon="delete"
            className="text-destructive hover:text-destructive"
            title="Eliminar documento"
            onClick={() => handleDelete(r)}
          />
        </div>
      ),
    },
  ];

  return (
    <PageContainer>
      <PageHeader
        title="Repositorio Documental Oficial"
        subtitle="Custodia, control de versiones y gobierno documental de la copropiedad"
        action={
          <Button
            onClick={() => {
              setUploadFile(null);
              setUploadModalOpen(true);
            }}
            icon="upload"
          >
            Subir Documento
          </Button>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <MetricCard label="Total documentos" value={!data ? '—' : stats.total} icon={Folder} variant="primary" />
        <MetricCard label="Públicos para residentes" value={!data ? '—' : stats.publicos} icon={Globe} variant="success" />
        <MetricCard label="Confidenciales (Admin/Consejo)" value={!data ? '—' : stats.privados} icon={Lock} variant="warning" />
      </div>

      {/* Filters and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="flex gap-1.5 items-center flex-wrap w-full sm:w-auto">
          <button
            type="button"
            onClick={() => setCategoriaFiltro('TODAS')}
            aria-pressed={categoriaFiltro === 'TODAS'}
            className={`min-h-10 px-3 py-1.5 rounded-md text-xs font-semibold transition-colors duration-150 ${
              categoriaFiltro === 'TODAS'
                ? 'bg-btn-primary text-white shadow-sm'
                : 'bg-surface-dim text-text-secondary hover:bg-surface-container hover:text-on-surface'
            }`}
          >
            Todas ({rawDocumentos.length})
          </button>
          {CATEGORIAS_DOCUMENTOS.map((c) => {
            const count = rawDocumentos.filter((d) => d.categoria === c.value).length;
            if (count === 0 && categoriaFiltro !== c.value) return null;
            return (
              <button
                key={c.value}
                type="button"
                onClick={() => setCategoriaFiltro(c.value)}
                aria-pressed={categoriaFiltro === c.value}
                className={`min-h-10 px-3 py-1.5 rounded-md text-xs font-semibold transition-colors duration-150 ${
                  categoriaFiltro === c.value
                    ? 'bg-btn-primary text-white shadow-sm'
                    : 'bg-surface-dim text-text-secondary hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                {c.label} ({count})
              </button>
            );
          })}
        </div>

        <div className="w-full sm:w-72">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
            <input
              type="search"
              aria-label="Buscar documentos"
              placeholder="Buscar por título o contenido..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25"
            />
          </div>
        </div>
      </div>

      {/* Main Table */}
      {error && !data ? (
        <ErrorState
          title="No se pudieron cargar los documentos"
          message={error?.message || 'Verifique su conexión e intente nuevamente.'}
          onRetry={() => refetch().catch(() => {})}
        />
      ) : (
      <DataTable
        columns={columns}
        rows={documentos}
        loading={loading && !data}
        keyField="idDocumento"
        empty={{
          icon: 'folder_off',
          title: 'Sin documentos',
          subtitle:
            searchTerm || categoriaFiltro !== 'TODAS'
              ? 'No hay documentos que coincidan con los filtros seleccionados.'
              : 'El repositorio documental de esta propiedad está vacío. Sube el primer documento oficial.',
        }}
      />
      )}

      {/* MODAL 1: SUBIR NUEVO DOCUMENTO (MULTIPART REAL) */}
      <Modal
        open={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
        title="Subir Nuevo Documento Oficial"
        size="md"
      >
        <form onSubmit={handleUploadSubmit} className="space-y-4 pt-2">
          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              Título del Documento *
            </label>
            <input
              type="text"
              required
              value={uploadForm.titulo}
              onChange={(e) => setUploadForm({ ...uploadForm, titulo: e.target.value })}
              placeholder="Ej: Reglamento Interno de Copropiedad 2026"
              className="w-full h-9 px-3 rounded-md border border-input bg-transparent text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              Categoría Oficial *
            </label>
            <select
              required
              value={uploadForm.categoria}
              onChange={(e) => setUploadForm({ ...uploadForm, categoria: e.target.value })}
              className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            >
              {CATEGORIAS_DOCUMENTOS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">Descripción</label>
            <textarea
              rows={2}
              value={uploadForm.descripcion}
              onChange={(e) => setUploadForm({ ...uploadForm, descripcion: e.target.value })}
              placeholder="Detalles sobre el contenido, vigencia o alcance del documento..."
              className="w-full p-2.5 rounded-md border border-input bg-transparent text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-3 rounded-lg border border-border bg-muted/30">
            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                ¿Público para Residentes?
              </label>
              <select
                value={uploadForm.esPublicoResidentes}
                onChange={(e) =>
                  setUploadForm({ ...uploadForm, esPublicoResidentes: e.target.value })
                }
                className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              >
                <option value="S">Sí, visible para copropietarios y residentes</option>
                <option value="N">No, restringido a administración</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                Rol Mínimo Requerido
              </label>
              <select
                disabled={uploadForm.esPublicoResidentes === 'S'}
                value={uploadForm.rolMinimoAcceso}
                onChange={(e) =>
                  setUploadForm({ ...uploadForm, rolMinimoAcceso: e.target.value })
                }
                className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:opacity-50"
              >
                <option value="ADMIN_PROPIEDAD">Administrador de Propiedad</option>
                <option value="ADMIN_ORGANIZACION">Administrador de Organización</option>
                <option value="SUPERADMIN">Super Admin de Plataforma</option>
              </select>
            </div>
          </div>

          {/* REAL FILE ATTACHMENT */}
          <div className="border-2 border-dashed border-border rounded-xl p-4 text-center hover:border-primary/50 transition-colors bg-muted/10">
            <input
              type="file"
              id="file-upload-input"
              required
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) setUploadFile(f);
              }}
              className="hidden"
            />
            <label
              htmlFor="file-upload-input"
              className="cursor-pointer flex flex-col items-center justify-center gap-1.5"
            >
              <UploadCloud className="h-7 w-7 text-primary-text" aria-hidden="true" />
              <span className="text-sm font-semibold text-foreground">
                {uploadFile ? uploadFile.name : 'Haz clic para seleccionar el archivo'}
              </span>
              <span className="text-xs text-muted-foreground">
                {uploadFile
                  ? `Tamaño: ${formatFileSize(uploadFile.size)} • Tipo: ${uploadFile.type || 'binario'}`
                  : 'Soporta PDF, DOCX, XLSX, imágenes y archivos hasta 50 MB'}
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <Button
              variant="ghost"
              onClick={() => setUploadModalOpen(false)}
              disabled={submitting}
              type="button"
            >
              Cancelar
            </Button>
            <Button type="submit" loading={submitting}>
              Subir y Custodiar
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 2: SUBIR NUEVA VERSIÓN */}
      <Modal
        open={versionModalOpen}
        onClose={() => setVersionModalOpen(false)}
        title={`Subir Nueva Versión: ${selectedDoc?.titulo || ''}`}
        size="md"
      >
        <form onSubmit={handleVersionSubmit} className="space-y-4 pt-2">
          <div className="p-3 rounded-lg bg-muted/40 border border-border text-xs text-muted-foreground space-y-1">
            <p>
              <strong className="text-foreground">Versión actual:</strong> v
              {selectedDoc?.numeroVersion || 1} ({selectedDoc?.nombreArchivo || 'archivo'})
            </p>
            <p>
              Subir un nuevo archivo incrementará automáticamente la versión a{' '}
              <span className="font-bold text-primary">v{(selectedDoc?.numeroVersion || 1) + 1}</span>{' '}
              y preservará el histórico para auditoría.
            </p>
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              Notas de Cambio de la Versión *
            </label>
            <textarea
              rows={2}
              required
              value={versionNotas}
              onChange={(e) => setVersionNotas(e.target.value)}
              placeholder="Ej: Aprobación de adenda en asamblea ordinaria de fecha..."
              className="w-full p-2.5 rounded-md border border-input bg-transparent text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div className="border-2 border-dashed border-border rounded-xl p-4 text-center hover:border-primary/50 transition-colors bg-muted/10">
            <input
              type="file"
              id="version-file-input"
              required
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) setVersionFile(f);
              }}
              className="hidden"
            />
            <label
              htmlFor="version-file-input"
              className="cursor-pointer flex flex-col items-center justify-center gap-1.5"
            >
              <UploadCloud className="h-7 w-7 text-primary-text" aria-hidden="true" />
              <span className="text-sm font-semibold text-foreground">
                {versionFile ? versionFile.name : 'Seleccionar nuevo archivo para la versión'}
              </span>
              <span className="text-xs text-muted-foreground">
                {versionFile
                  ? `Tamaño: ${formatFileSize(versionFile.size)} • Tipo: ${versionFile.type || 'binario'}`
                  : 'Selecciona el archivo binario actualizado'}
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <Button
              variant="ghost"
              onClick={() => setVersionModalOpen(false)}
              disabled={submitting}
              type="button"
            >
              Cancelar
            </Button>
            <Button type="submit" loading={submitting}>
              Publicar Versión
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 3: EDITAR METADATOS */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Editar Metadatos del Documento"
        size="md"
      >
        <form onSubmit={handleEditSubmit} className="space-y-4 pt-2">
          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              Título del Documento *
            </label>
            <input
              type="text"
              required
              value={editForm.titulo}
              onChange={(e) => setEditForm({ ...editForm, titulo: e.target.value })}
              className="w-full h-9 px-3 rounded-md border border-input bg-transparent text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              Categoría Oficial *
            </label>
            <select
              required
              value={editForm.categoria}
              onChange={(e) => setEditForm({ ...editForm, categoria: e.target.value })}
              className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            >
              {CATEGORIAS_DOCUMENTOS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">Descripción</label>
            <textarea
              rows={2}
              value={editForm.descripcion}
              onChange={(e) => setEditForm({ ...editForm, descripcion: e.target.value })}
              className="w-full p-2.5 rounded-md border border-input bg-transparent text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-3 rounded-lg border border-border bg-muted/30">
            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                ¿Público para Residentes?
              </label>
              <select
                value={editForm.esPublicoResidentes}
                onChange={(e) =>
                  setEditForm({ ...editForm, esPublicoResidentes: e.target.value })
                }
                className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              >
                <option value="S">Sí, visible para residentes</option>
                <option value="N">No, restringido a administración</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                Rol Mínimo Requerido
              </label>
              <select
                disabled={editForm.esPublicoResidentes === 'S'}
                value={editForm.rolMinimoAcceso}
                onChange={(e) =>
                  setEditForm({ ...editForm, rolMinimoAcceso: e.target.value })
                }
                className="w-full h-9 px-3 rounded-md border border-input bg-background text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:opacity-50"
              >
                <option value="ADMIN_PROPIEDAD">Administrador de Propiedad</option>
                <option value="ADMIN_ORGANIZACION">Administrador de Organización</option>
                <option value="SUPERADMIN">Super Admin de Plataforma</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border">
            <Button
              variant="ghost"
              onClick={() => setEditModalOpen(false)}
              disabled={submitting}
              type="button"
            >
              Cancelar
            </Button>
            <Button type="submit" loading={submitting}>
              Guardar Cambios
            </Button>
          </div>
        </form>
      </Modal>
    </PageContainer>
  );
}
