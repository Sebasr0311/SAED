import React, { useState, useMemo } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import api from '../lib/api';
import { formatDate } from '../lib/utils';
import { toast } from 'sonner';
import {
  BookOpen,
  Plus,
  FileText,
  CheckCircle2,
  AlertCircle,
  Clock,
  Download,
  Edit3,
  Power,
  RefreshCw,
  Hash,
  ShieldCheck,
  History,
} from 'lucide-react';

export const TIPOS_NORMATIVA = [
  { value: 'REGLAMENTO_INTERNO', label: 'Reglamento Interno', color: 'border-blue-500/30 text-blue-400 bg-blue-500/10' },
  { value: 'MANUAL_CONVIVENCIA', label: 'Manual de Convivencia', color: 'border-emerald-500/30 text-emerald-400 bg-emerald-500/10' },
  { value: 'MANUAL_ZONAS_COMUNES', label: 'Manual de Zonas Comunes', color: 'border-purple-500/30 text-purple-400 bg-purple-500/10' },
  { value: 'MANUAL_POLITICA_MASCOTAS', label: 'Política de Mascotas', color: 'border-amber-500/30 text-amber-400 bg-amber-500/10' },
  { value: 'ESTATUTO_COPROPIEDAD', label: 'Estatuto de Copropiedad', color: 'border-indigo-500/30 text-indigo-400 bg-indigo-500/10' },
  { value: 'OTRO', label: 'Otro Documento Normativo', color: 'border-slate-500/30 text-slate-400 bg-slate-500/10' },
];

export const TIPOS_MAP = Object.fromEntries(TIPOS_NORMATIVA.map((t) => [t.value, t.label]));

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

export default function ReglamentosAdminPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/reglamentos/admin'));
  const { data: docsData } = useFetch(() => api.get('/documentos/admin'));

  const availableDocs = useMemo(() => {
    return docsData?.items || docsData || [];
  }, [docsData]);

  // Filters
  const [tipoFiltro, setTipoFiltro] = useState('TODOS');
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS');
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [publishModalOpen, setPublishModalOpen] = useState(false);
  const [selectedReg, setSelectedReg] = useState(null);

  // States
  const [submitting, setSubmitting] = useState(false);
  const [downloadingId, setDownloadingId] = useState(null);

  // Create Form
  const [createForm, setCreateForm] = useState({
    tipoNormativa: 'REGLAMENTO_INTERNO',
    titulo: '',
    descripcion: '',
    idDocumento: '',
  });

  // Edit Form
  const [editForm, setEditForm] = useState({
    tipoNormativa: '',
    titulo: '',
    descripcion: '',
    idDocumento: '',
  });

  // Publish Form
  const [publishForm, setPublishForm] = useState({
    fechaEntradaEnVigor: new Date().toISOString().split('T')[0],
  });

  const reglamentos = useMemo(() => {
    return data?.items || data || [];
  }, [data]);

  // Filtered
  const filteredReglamentos = useMemo(() => {
    return reglamentos.filter((item) => {
      const matchTipo = tipoFiltro === 'TODOS' || item.tipoNormativa === tipoFiltro;
      const matchEstado = estadoFiltro === 'TODOS' || item.estado === estadoFiltro;
      const term = searchTerm.toLowerCase();
      const matchSearch =
        !searchTerm ||
        (item.titulo && item.titulo.toLowerCase().includes(term)) ||
        (item.descripcion && item.descripcion.toLowerCase().includes(term)) ||
        (item.archivoNombreOrig && item.archivoNombreOrig.toLowerCase().includes(term));
      return matchTipo && matchEstado && matchSearch;
    });
  }, [reglamentos, tipoFiltro, estadoFiltro, searchTerm]);

  // KPIs
  const kpis = useMemo(() => {
    const vigentes = reglamentos.filter((r) => r.estado === 'PUBLICADO').length;
    const borradores = reglamentos.filter((r) => r.estado === 'BORRADOR').length;
    const reemplazados = reglamentos.filter((r) => r.estado === 'REEMPLAZADO').length;
    const inactivos = reglamentos.filter((r) => r.estado === 'INACTIVO').length;
    return { total: reglamentos.length, vigentes, borradores, reemplazados, inactivos };
  }, [reglamentos]);

  // Download
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
      toast.success('Descarga iniciada exitosamente');
    } catch (err) {
      toast.error('Error al descargar el archivo: ' + (err.response?.data?.message || err.message));
    } finally {
      setDownloadingId(null);
    }
  };

  // Create Draft
  const handleCreate = async (e) => {
    e.preventDefault();
    if (!createForm.titulo.trim()) {
      toast.error('El título es requerido');
      return;
    }
    if (!createForm.idDocumento) {
      toast.error('Debe seleccionar un documento base para la normativa');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/reglamentos', {
        tipoNormativa: createForm.tipoNormativa,
        titulo: createForm.titulo.trim(),
        descripcion: createForm.descripcion?.trim() || null,
        idDocumento: Number(createForm.idDocumento),
      });
      toast.success('Borrador de reglamento creado exitosamente');
      setCreateModalOpen(false);
      setCreateForm({
        tipoNormativa: 'REGLAMENTO_INTERNO',
        titulo: '',
        descripcion: '',
        idDocumento: '',
      });
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Error al crear el borrador');
    } finally {
      setSubmitting(false);
    }
  };

  // Open Edit
  const handleOpenEdit = (reg) => {
    setSelectedReg(reg);
    setEditForm({
      tipoNormativa: reg.tipoNormativa,
      titulo: reg.titulo,
      descripcion: reg.descripcion || '',
      idDocumento: String(reg.idDocumento),
    });
    setEditModalOpen(true);
  };

  // Submit Edit
  const handleEdit = async (e) => {
    e.preventDefault();
    if (!editForm.titulo.trim()) {
      toast.error('El título es requerido');
      return;
    }
    if (!editForm.idDocumento) {
      toast.error('Debe seleccionar un documento');
      return;
    }
    try {
      setSubmitting(true);
      await api.put(`/reglamentos/${selectedReg.idReglamento}`, {
        tipoNormativa: editForm.tipoNormativa,
        titulo: editForm.titulo.trim(),
        descripcion: editForm.descripcion?.trim() || null,
        idDocumento: Number(editForm.idDocumento),
      });
      toast.success('Borrador actualizado exitosamente');
      setEditModalOpen(false);
      setSelectedReg(null);
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Error al actualizar el borrador');
    } finally {
      setSubmitting(false);
    }
  };

  // Open Publish
  const handleOpenPublish = (reg) => {
    setSelectedReg(reg);
    setPublishForm({
      fechaEntradaEnVigor: new Date().toISOString().split('T')[0],
    });
    setPublishModalOpen(true);
  };

  // Submit Publish
  const handlePublish = async (e) => {
    e.preventDefault();
    try {
      setSubmitting(true);
      await api.post(`/reglamentos/${selectedReg.idReglamento}/publicar`, {
        fechaEntradaEnVigor: publishForm.fechaEntradaEnVigor || null,
      });
      toast.success('Reglamento publicado y puesto en vigor exitosamente');
      setPublishModalOpen(false);
      setSelectedReg(null);
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Error al publicar el reglamento');
    } finally {
      setSubmitting(false);
    }
  };

  // Inactivate
  const handleInactivate = async (reg) => {
    if (!window.confirm(`¿Está seguro de inactivar "${reg.titulo}"? Esta acción retirará su vigencia.`)) {
      return;
    }
    try {
      await api.post(`/reglamentos/${reg.idReglamento}/inactivar`);
      toast.success('Reglamento inactivado exitosamente');
      refetch();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Error al inactivar el reglamento');
    }
  };

  const columns = [
    {
      header: 'Normativa',
      accessorKey: 'titulo',
      cell: (info) => {
        const item = info.row.original;
        const tipoLabel = TIPOS_MAP[item.tipoNormativa] || item.tipoNormativa;
        const tipoObj = TIPOS_NORMATIVA.find((t) => t.value === item.tipoNormativa);
        return (
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${
                  tipoObj?.color || 'border-slate-500/30 text-slate-400 bg-slate-500/10'
                }`}
              >
                {tipoLabel}
              </span>
            </div>
            <div className="font-semibold text-slate-100 flex items-center gap-1.5">
              <BookOpen className="w-4 h-4 text-emerald-400 flex-shrink-0" />
              <span>{item.titulo}</span>
            </div>
            {item.descripcion && (
              <p className="text-xs text-slate-400 line-clamp-2 max-w-md">{item.descripcion}</p>
            )}
          </div>
        );
      },
    },
    {
      header: 'Documento Base (F10-01)',
      accessorKey: 'archivoNombreOrig',
      cell: (info) => {
        const item = info.row.original;
        return (
          <div className="space-y-1 text-xs">
            <div className="flex items-center gap-1.5 text-slate-300 font-medium truncate max-w-xs">
              <FileText className="w-3.5 h-3.5 text-blue-400 flex-shrink-0" />
              <span className="truncate" title={item.archivoNombreOrig}>
                {item.archivoNombreOrig || 'Documento sin versión'}
              </span>
            </div>
            <div className="flex items-center gap-3 text-slate-400">
              <span className="inline-flex items-center gap-1">
                <span className="text-slate-500">v{item.numeroVersion || 1}</span>
              </span>
              <span>{formatFileSize(item.archivoTamanoBytes)}</span>
            </div>
            {item.archivoSha256 && (
              <div className="flex items-center gap-1 text-[10px] text-slate-500 font-mono" title={item.archivoSha256}>
                <Hash className="w-3 h-3 text-slate-500" />
                <span>{item.archivoSha256.substring(0, 12)}...</span>
              </div>
            )}
          </div>
        );
      },
    },
    {
      header: 'Estado',
      accessorKey: 'estado',
      cell: (info) => {
        const estado = info.getValue();
        let badgeColor = 'bg-slate-500/20 text-slate-300 border-slate-500/30';
        let label = estado;
        let Icon = Clock;

        if (estado === 'PUBLICADO') {
          badgeColor = 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40';
          label = 'Vigente';
          Icon = CheckCircle2;
        } else if (estado === 'BORRADOR') {
          badgeColor = 'bg-amber-500/20 text-amber-400 border-amber-500/40';
          label = 'Borrador';
          Icon = Clock;
        } else if (estado === 'REEMPLAZADO') {
          badgeColor = 'bg-slate-500/20 text-slate-400 border-slate-500/40';
          label = 'Reemplazado';
          Icon = History;
        } else if (estado === 'INACTIVO') {
          badgeColor = 'bg-rose-500/20 text-rose-400 border-rose-500/40';
          label = 'Inactivo';
          Icon = AlertCircle;
        }

        return (
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${badgeColor}`}
          >
            <Icon className="w-3.5 h-3.5" />
            {label}
          </span>
        );
      },
    },
    {
      header: 'Fechas',
      accessorKey: 'fechaPublicacion',
      cell: (info) => {
        const item = info.row.original;
        return (
          <div className="space-y-1 text-xs text-slate-400">
            {item.fechaEntradaEnVigor && (
              <div>
                <span className="text-slate-500">En vigor:</span>{' '}
                <span className="text-slate-200 font-medium">{formatDate(item.fechaEntradaEnVigor)}</span>
              </div>
            )}
            {item.fechaPublicacion && (
              <div>
                <span className="text-slate-500">Publicado:</span>{' '}
                <span className="text-slate-300">{formatDate(item.fechaPublicacion)}</span>
              </div>
            )}
            <div>
              <span className="text-slate-500">Creado:</span>{' '}
              <span className="text-slate-400">{formatDate(item.fechaCreacion)}</span>
            </div>
          </div>
        );
      },
    },
    {
      header: 'Acciones',
      id: 'acciones',
      cell: (info) => {
        const reg = info.row.original;
        const isDownloading = downloadingId === reg.idReglamento;
        return (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleDownload(reg)}
              disabled={isDownloading}
              title="Descargar documento (PDF)"
              className="text-xs flex items-center gap-1 text-blue-400 hover:text-blue-300 border-blue-500/30"
            >
              <Download className="w-3.5 h-3.5" />
              {isDownloading ? 'Descargando...' : 'Descargar'}
            </Button>

            {reg.estado === 'BORRADOR' && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleOpenEdit(reg)}
                  title="Editar borrador"
                  className="text-xs flex items-center gap-1 text-amber-400 hover:text-amber-300 border-amber-500/30"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                  Editar
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => handleOpenPublish(reg)}
                  title="Publicar y hacer vigente"
                  className="text-xs flex items-center gap-1 bg-emerald-600 hover:bg-emerald-500 text-white"
                >
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Publicar
                </Button>
              </>
            )}

            {(reg.estado === 'PUBLICADO' || reg.estado === 'BORRADOR') && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleInactivate(reg)}
                title="Inactivar normativa"
                className="text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-500/10"
              >
                <Power className="w-3.5 h-3.5" />
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reglamentos y Normativa (F10-02)"
        description="Gestión oficial de estatutos, reglamentos internos y manuales de convivencia de la copropiedad."
      >
        <div className="flex items-center gap-3">
          <Button variant="outline" size="sm" onClick={() => refetch()} className="flex items-center gap-2">
            <RefreshCw className="w-4 h-4" />
            Actualizar
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setCreateModalOpen(true)}
            className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white"
          >
            <Plus className="w-4 h-4" />
            Nuevo Reglamento
          </Button>
        </div>
      </PageHeader>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl border border-slate-700/60 bg-slate-800/40 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Normativas Vigentes</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold text-slate-100">{kpis.vigentes}</div>
          <div className="text-[11px] text-emerald-400/80">Publicadas para residentes</div>
        </div>

        <div className="p-4 rounded-xl border border-slate-700/60 bg-slate-800/40 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Borradores en Revisión</span>
            <Clock className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-bold text-slate-100">{kpis.borradores}</div>
          <div className="text-[11px] text-amber-400/80">Pendientes de publicación</div>
        </div>

        <div className="p-4 rounded-xl border border-slate-700/60 bg-slate-800/40 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Histórico Reemplazados</span>
            <History className="w-4 h-4 text-slate-400" />
          </div>
          <div className="text-2xl font-bold text-slate-100">{kpis.reemplazados}</div>
          <div className="text-[11px] text-slate-400/80">Versiones normativas previas</div>
        </div>

        <div className="p-4 rounded-xl border border-slate-700/60 bg-slate-800/40 space-y-1">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Total Registros</span>
            <BookOpen className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-bold text-slate-100">{kpis.total}</div>
          <div className="text-[11px] text-blue-400/80">En el repositorio</div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 p-4 rounded-xl border border-slate-700/60 bg-slate-800/20">
        <div className="flex-1 min-w-[200px]">
          <input
            type="text"
            placeholder="Buscar por título, descripción o archivo..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/60 border border-slate-700 text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          />
        </div>

        <div className="w-48">
          <select
            value={tipoFiltro}
            onChange={(e) => setTipoFiltro(e.target.value)}
            className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/60 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option value="TODOS">Todos los Tipos</option>
            {TIPOS_NORMATIVA.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </div>

        <div className="w-44">
          <select
            value={estadoFiltro}
            onChange={(e) => setEstadoFiltro(e.target.value)}
            className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/60 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option value="TODOS">Todos los Estados</option>
            <option value="PUBLICADO">Vigente (Publicado)</option>
            <option value="BORRADOR">Borrador</option>
            <option value="REEMPLAZADO">Reemplazado</option>
            <option value="INACTIVO">Inactivo</option>
          </select>
        </div>
      </div>

      {/* Main Table */}
      <DataTable
        columns={columns}
        data={filteredReglamentos}
        loading={loading}
        error={error}
        emptyMessage="No se encontraron reglamentos registrados para la copropiedad."
      />

      {/* MODAL 1: Create Draft */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Nuevo Borrador de Reglamento / Normativa"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Tipo de Normativa *</label>
            <select
              value={createForm.tipoNormativa}
              onChange={(e) => setCreateForm({ ...createForm, tipoNormativa: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            >
              {TIPOS_NORMATIVA.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Título del Reglamento *</label>
            <input
              type="text"
              placeholder="Ej: Reglamento Interno de Propiedad Horizontal 2026"
              value={createForm.titulo}
              onChange={(e) => setCreateForm({ ...createForm, titulo: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
              maxLength={255}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Descripción o Alcance</label>
            <textarea
              placeholder="Breve resumen de normas, modificaciones clave o alcance de la norma..."
              value={createForm.descripcion}
              onChange={(e) => setCreateForm({ ...createForm, descripcion: e.target.value })}
              rows={3}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              maxLength={1000}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Documento Oficial Vinculado (F10-01) *</label>
            <select
              value={createForm.idDocumento}
              onChange={(e) => setCreateForm({ ...createForm, idDocumento: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            >
              <option value="">Seleccione un documento del repositorio...</option>
              {availableDocs.map((doc) => (
                <option key={doc.idDocumento} value={doc.idDocumento}>
                  {doc.titulo} ({doc.archivoNombreOrig || 'v1'})
                </option>
              ))}
            </select>
            <p className="text-[11px] text-slate-400">
              El reglamento reutiliza la infraestructura de almacenamiento y hash SHA-256 de F10-01.
            </p>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700">
            <Button variant="outline" type="button" onClick={() => setCreateModalOpen(false)}>
              Cancelar
            </Button>
            <Button variant="primary" type="submit" disabled={submitting} className="bg-emerald-600 hover:bg-emerald-500 text-white">
              {submitting ? 'Creando...' : 'Crear Borrador'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 2: Edit Draft */}
      <Modal
        isOpen={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Editar Borrador de Reglamento"
      >
        <form onSubmit={handleEdit} className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Tipo de Normativa *</label>
            <select
              value={editForm.tipoNormativa}
              onChange={(e) => setEditForm({ ...editForm, tipoNormativa: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            >
              {TIPOS_NORMATIVA.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Título *</label>
            <input
              type="text"
              value={editForm.titulo}
              onChange={(e) => setEditForm({ ...editForm, titulo: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
              maxLength={255}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Descripción</label>
            <textarea
              value={editForm.descripcion}
              onChange={(e) => setEditForm({ ...editForm, descripcion: e.target.value })}
              rows={3}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              maxLength={1000}
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Documento Base Vinculado *</label>
            <select
              value={editForm.idDocumento}
              onChange={(e) => setEditForm({ ...editForm, idDocumento: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            >
              {availableDocs.map((doc) => (
                <option key={doc.idDocumento} value={doc.idDocumento}>
                  {doc.titulo} ({doc.archivoNombreOrig || 'v1'})
                </option>
              ))}
            </select>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700">
            <Button variant="outline" type="button" onClick={() => setEditModalOpen(false)}>
              Cancelar
            </Button>
            <Button variant="primary" type="submit" disabled={submitting} className="bg-emerald-600 hover:bg-emerald-500 text-white">
              {submitting ? 'Guardando...' : 'Guardar Cambios'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* MODAL 3: Publish and Put In Force */}
      <Modal
        isOpen={publishModalOpen}
        onClose={() => setPublishModalOpen(false)}
        title="Publicar y Poner en Vigor"
      >
        <form onSubmit={handlePublish} className="space-y-4">
          <div className="p-3 rounded-lg border border-amber-500/30 bg-amber-500/10 text-amber-200 text-xs space-y-1.5">
            <div className="font-semibold flex items-center gap-1.5 text-amber-300">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>Regla de Unicidad Normativa Vigente</span>
            </div>
            <p>
              Al publicar este reglamento ({TIPOS_MAP[selectedReg?.tipoNormativa]}), cualquier otro reglamento vigente de este mismo tipo en la copropiedad pasará automáticamente a estado <strong>REEMPLAZADO</strong>.
            </p>
            <p>
              El documento oficial vinculado será habilitado automáticamente para descarga por los residentes.
            </p>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-slate-300">Fecha de Entrada en Vigor *</label>
            <input
              type="date"
              value={publishForm.fechaEntradaEnVigor}
              onChange={(e) => setPublishForm({ ...publishForm, fechaEntradaEnVigor: e.target.value })}
              className="w-full px-3 py-2 text-sm rounded-lg bg-slate-900/80 border border-slate-700 text-slate-100 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              required
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700">
            <Button variant="outline" type="button" onClick={() => setPublishModalOpen(false)}>
              Cancelar
            </Button>
            <Button variant="primary" type="submit" disabled={submitting} className="bg-emerald-600 hover:bg-emerald-500 text-white">
              {submitting ? 'Publicando...' : 'Confirmar y Publicar'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
