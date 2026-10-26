import { useEffect, useState, useMemo, useRef } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  FileText,
  Plus,
  Search,
  Eye,
  Copy,
  CheckCircle2,
  Sparkles,
  Code,
  RefreshCw,
  X,
  Layers,
  UploadCloud,
  Download,
  FileCode,
  Trash2,
  PauseCircle,
  PlayCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';

const CONTROL =
  'mt-1.5 h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:opacity-60';
const FIELD_LABEL = 'text-sm font-semibold text-on-surface';

const TIPOS_CONTRATO = ['INICIAL', 'RENOVACION', 'PERMANENCIA', 'COMERCIAL', 'OTRO'];

const ESTADO_VARIANT = {
  ACTIVA: 'success',
  BORRADOR: 'warning',
  HISTORICA: 'secondary',
  SUSPENDIDA: 'destructive',
  INACTIVA: 'destructive',
};

const DEFAULT_VARIABLES = [
  'propiedad.nombre',
  'propiedad.direccion',
  'propiedad.ciudad',
  'apartamento.numero',
  'apartamento.bloque',
  'inquilino.nombre_completo',
  'inquilino.tipo_documento',
  'inquilino.numero_documento',
  'inquilino.telefono',
  'inquilino.email',
  'contrato.canon_mensual',
  'contrato.fecha_inicio',
  'contrato.fecha_fin',
  'contrato.tipo',
  'fecha_actual',
];

const DEFAULT_HTML_TEMPLATE = `<h2>CONTRATO DE ARRENDAMIENTO DE VIVIENDA URBANA</h2>
<p>Entre los suscritos a saber, <strong>\${propiedad.nombre}</strong> (en adelante EL ARRENDADOR), ubicada en \${propiedad.direccion}, \${propiedad.ciudad}, y por la otra parte <strong>\${inquilino.nombre_completo}</strong>, identificado con \${inquilino.tipo_documento} No. \${inquilino.numero_documento} (en adelante EL ARRENDATARIO), se ha celebrado el presente contrato sobre el inmueble:</p>
<ul>
  <li><strong>Unidad:</strong> Apartamento \${apartamento.numero} \${apartamento.bloque}</li>
  <li><strong>Canon Mensual:</strong> $\${contrato.canon_mensual} COP</li>
  <li><strong>Fecha de Inicio:</strong> \${contrato.fecha_inicio}</li>
  <li><strong>Fecha de Terminación:</strong> \${contrato.fecha_fin}</li>
</ul>
<p>El arrendatario se compromete al cumplimiento cabal de las normas de convivencia de la copropiedad y al pago oportuno en los primeros cinco (5) días de cada mes calendario.</p>
<p>En constancia se firma en la fecha: \${fecha_actual}.</p>`;

export default function OrgPlantillasContratosPage() {
  const [plantillas, setPlantillas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  // Modales
  const [modalFormOpen, setModalFormOpen] = useState(false);
  const [isVersionMode, setIsVersionMode] = useState(false);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewHtml, setPreviewHtml] = useState('');
  const [previewTitle, setPreviewTitle] = useState('');
  const [previewLoading, setPreviewLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [plantillaAEliminar, setPlantillaAEliminar] = useState(null);

  // Soporte de carga de archivos HTML
  const fileInputRef = useRef(null);
  const [uploadedFileName, setUploadedFileName] = useState('');
  const [modalTab, setModalTab] = useState('editor'); // 'editor' | 'preview'

  // Formulario
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState({
    codigo: '',
    nombre: '',
    tipoContrato: 'INICIAL',
    descripcion: '',
    contenidoHtml: DEFAULT_HTML_TEMPLATE,
    variablesDisponibles: DEFAULT_VARIABLES,
    camposRequeridos: ['propiedad.nombre', 'inquilino.nombre_completo', 'apartamento.numero', 'contrato.canon_mensual'],
    estado: 'ACTIVA',
    vigenciaDesde: new Date().toISOString().split('T')[0],
    vigenciaHasta: '',
  });

  async function loadPlantillas() {
    try {
      setLoading(true);
      setError(null);
      const url = filtroEstado
        ? `/org/contratos/plantillas?estado=${filtroEstado}`
        : '/org/contratos/plantillas';
      const res = await api.get(url);
      const list = res?.data || (Array.isArray(res) ? res : []);
      setPlantillas(list);
    } catch (err) {
      console.error('Error al cargar plantillas:', err);
      setError(err.message || 'Error al cargar el catálogo de plantillas.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadPlantillas();
  }, [filtroEstado]);

  const plantillasFiltradas = useMemo(() => {
    return plantillas.filter((p) => {
      const matchSearch =
        !searchTerm ||
        p.nombre?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.codigo?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.tipoContrato?.toLowerCase().includes(searchTerm.toLowerCase());
      return matchSearch;
    });
  }, [plantillas, searchTerm]);

  function abrirNuevo() {
    setEditingId(null);
    setIsVersionMode(false);
    setUploadedFileName('');
    setModalTab('editor');
    setForm({
      codigo: `CONTRATO_ARRIENDO_${Date.now().toString().slice(-4)}`,
      nombre: '',
      tipoContrato: 'INICIAL',
      descripcion: '',
      contenidoHtml: DEFAULT_HTML_TEMPLATE,
      variablesDisponibles: DEFAULT_VARIABLES,
      camposRequeridos: ['propiedad.nombre', 'inquilino.nombre_completo', 'apartamento.numero', 'contrato.canon_mensual'],
      estado: 'ACTIVA',
      vigenciaDesde: new Date().toISOString().split('T')[0],
      vigenciaHasta: '',
    });
    setModalFormOpen(true);
  }

  function abrirEditar(plantilla) {
    setEditingId(plantilla.idPlantilla);
    setIsVersionMode(false);
    setUploadedFileName('');
    setModalTab('editor');
    setForm({
      codigo: plantilla.codigo,
      nombre: plantilla.nombre,
      tipoContrato: plantilla.tipoContrato,
      descripcion: plantilla.descripcion || '',
      contenidoHtml: plantilla.contenidoHtml,
      variablesDisponibles: plantilla.variablesDisponibles || DEFAULT_VARIABLES,
      camposRequeridos: plantilla.camposRequeridos || [],
      estado: plantilla.estado,
      vigenciaDesde: plantilla.vigenciaDesde ? plantilla.vigenciaDesde.split('T')[0] : '',
      vigenciaHasta: plantilla.vigenciaHasta ? plantilla.vigenciaHasta.split('T')[0] : '',
    });
    setModalFormOpen(true);
  }

  function abrirNuevaVersion(plantilla) {
    setEditingId(plantilla.idPlantilla);
    setIsVersionMode(true);
    setUploadedFileName('');
    setModalTab('editor');
    setForm({
      codigo: plantilla.codigo,
      nombre: `${plantilla.nombre} (v${(plantilla.version || 1) + 1})`,
      tipoContrato: plantilla.tipoContrato,
      descripcion: plantilla.descripcion || '',
      contenidoHtml: plantilla.contenidoHtml,
      variablesDisponibles: plantilla.variablesDisponibles || DEFAULT_VARIABLES,
      camposRequeridos: plantilla.camposRequeridos || [],
      estado: 'ACTIVA',
      vigenciaDesde: new Date().toISOString().split('T')[0],
      vigenciaHasta: '',
    });
    setModalFormOpen(true);
  }

  function handleFileUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    const isHtml = file.name.match(/\.(html|htm)$/i) || file.type === 'text/html';
    if (!isHtml) {
      toast.error('Por favor seleccione un archivo en formato HTML (.html o .htm)');
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result;
      if (typeof content === 'string') {
        const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
        const codeGuess = 'CONTRATO_' + file.name.replace(/\.[^/.]+$/, '').toUpperCase().replace(/[^A-Z0-9]/g, '_').slice(0, 30);
        setForm((prev) => ({
          ...prev,
          contenidoHtml: content,
          nombre: prev.nombre?.trim() ? prev.nombre : cleanName,
          codigo: prev.codigo?.trim() && !prev.codigo.startsWith('CONTRATO_ARRIENDO_') ? prev.codigo : codeGuess,
        }));
        setUploadedFileName(file.name);
        toast.success(`Archivo HTML '${file.name}' cargado con éxito (${(file.size / 1024).toFixed(1)} KB)`);
      }
    };
    reader.onerror = () => {
      toast.error('Error al leer el archivo HTML.');
    };
    reader.readAsText(file);
  }

  function handleDownloadHtml(plantilla) {
    const blob = new Blob([plantilla.contenidoHtml || ''], { type: 'text/html;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${plantilla.codigo || 'plantilla_contrato'}_v${plantilla.version || 1}.html`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    toast.success(`Descargando archivo HTML: ${plantilla.codigo}.html`);
  }

  function renderLiveHtml(html) {
    if (!html || !html.trim()) {
      return '<p style="color: #888; font-style: italic; padding: 1rem;">No hay contenido HTML cargado aún. Escriba o suba un archivo HTML para previsualizar.</p>';
    }
    let rendered = html;
    const sampleData = {
      'propiedad.nombre': 'Condominio Campestre Torres del Parque',
      'propiedad.direccion': 'Carrera 45 # 12-80',
      'propiedad.ciudad': 'Medellín',
      'apartamento.numero': '302',
      'apartamento.bloque': 'Torre B',
      'inquilino.nombre_completo': 'Juan David Restrepo Gómez',
      'inquilino.tipo_documento': 'CC',
      'inquilino.numero_documento': '1020456789',
      'inquilino.telefono': '310 987 6543',
      'inquilino.email': 'juan.restrepo@correo.com',
      'contrato.canon_mensual': '1.850.000',
      'contrato.fecha_inicio': '01/10/2026',
      'contrato.fecha_fin': '30/09/2027',
      'contrato.tipo': form.tipoContrato || 'INICIAL',
      'fecha_actual': new Date().toLocaleDateString('es-CO'),
    };
    Object.entries(sampleData).forEach(([k, v]) => {
      rendered = rendered.split(`\${${k}}`).join(v);
    });
    return rendered;
  }

  async function handleGuardar(e) {
    e.preventDefault();
    if (!form.codigo.trim() || !form.nombre.trim() || !form.contenidoHtml.trim()) {
      toast.error('Complete los campos obligatorios: Código, Nombre y Contenido HTML.');
      return;
    }

    try {
      setSaving(true);
      if (isVersionMode && editingId) {
        await api.post(`/org/contratos/plantillas/${editingId}/version`, form);
        toast.success('Nueva versión creada con éxito. La versión anterior se archivó como histórica.');
      } else if (editingId) {
        await api.put(`/org/contratos/plantillas/${editingId}`, form);
        toast.success('Plantilla actualizada con éxito.');
      } else {
        await api.post('/org/contratos/plantillas', form);
        toast.success('Plantilla de contrato creada con éxito.');
      }
      setModalFormOpen(false);
      loadPlantillas();
    } catch (err) {
      toast.error(err.message || 'Error al guardar la plantilla.');
    } finally {
      setSaving(false);
    }
  }

  async function cambiarEstado(id, nuevoEstado) {
    try {
      await api.patch(`/org/contratos/plantillas/${id}/estado`, { estado: nuevoEstado });
      toast.success(`Estado cambiado a ${nuevoEstado}`);
      loadPlantillas();
    } catch (err) {
      toast.error(err.message || 'Error al cambiar estado.');
    }
  }

  async function handleEliminarConfirmado() {
    if (!plantillaAEliminar) return;
    try {
      await api.delete(`/org/contratos/plantillas/${plantillaAEliminar.idPlantilla}`);
      toast.success(`Plantilla '${plantillaAEliminar.nombre}' eliminada con éxito.`);
      setPlantillaAEliminar(null);
      loadPlantillas();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar la plantilla.');
    }
  }

  async function previsualizar(plantilla) {
    try {
      setPreviewTitle(`${plantilla.nombre} (v${plantilla.version})`);
      setPreviewLoading(true);
      setPreviewModalOpen(true);
      const res = await api.post(`/org/contratos/plantillas/${plantilla.idPlantilla}/preview`, {
        'propiedad.nombre': 'Condominio Campestre Torres del Parque',
        'propiedad.direccion': 'Carrera 45 # 12-80',
        'propiedad.ciudad': 'Medellín',
        'apartamento.numero': '302',
        'apartamento.bloque': 'Torre B',
        'inquilino.nombre_completo': 'Juan David Restrepo Gómez',
        'inquilino.tipo_documento': 'CC',
        'inquilino.numero_documento': '1020456789',
        'inquilino.telefono': '310 987 6543',
        'inquilino.email': 'juan.restrepo@correo.com',
        'contrato.canon_mensual': '1.850.000',
        'contrato.fecha_inicio': '01/10/2026',
        'contrato.fecha_fin': '30/09/2027',
        'contrato.tipo': plantilla.tipoContrato,
        'fecha_actual': new Date().toLocaleDateString('es-CO'),
      });
      setPreviewHtml(res?.data || res || '');
    } catch (err) {
      toast.error(err.message || 'Error al generar vista previa.');
    } finally {
      setPreviewLoading(false);
    }
  }

  function insertarVariable(variable) {
    const placeholder = `\${${variable}}`;
    setForm((prev) => ({
      ...prev,
      contenidoHtml: prev.contenidoHtml + ' ' + placeholder,
    }));
    toast.info(`Variable ${placeholder} agregada al final del contenido`);
  }

  return (
    <PageContainer>
      <PageHeader
        title="Plantillas de contratos"
        subtitle="Contratos en formato HTML para su organización, con control de versiones y variables dinámicas."
        badge={<Badge variant="info">Formato HTML</Badge>}
        action={
          <>
            <Button variant="outline" size="sm" onClick={loadPlantillas} disabled={loading} className="min-h-10 sm:min-h-9">
              <RefreshCw className={loading ? 'animate-spin' : ''} aria-hidden="true" />
              Actualizar
            </Button>
            <Button variant="primary" size="sm" onClick={abrirNuevo} className="min-h-10 sm:min-h-9">
              <UploadCloud aria-hidden="true" />
              Cargar o crear plantilla
            </Button>
          </>
        }
      />

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="relative w-full sm:w-80">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="text"
            aria-label="Buscar plantillas"
            placeholder="Buscar por nombre, código o tipo..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className={`${CONTROL} mt-0 pl-9 pr-10`}
          />
          {searchTerm && (
            <button
              type="button"
              onClick={() => setSearchTerm('')}
              aria-label="Limpiar búsqueda"
              className="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded text-text-muted hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <label htmlFor="plantillas-estado" className="whitespace-nowrap text-xs font-semibold text-text-secondary">Estado</label>
          <select
            id="plantillas-estado"
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className={`${CONTROL} mt-0 sm:w-auto`}
          >
            <option value="">Todos los estados</option>
            <option value="ACTIVA">Activas</option>
            <option value="BORRADOR">Borradores</option>
            <option value="SUSPENDIDA">Suspendidas</option>
            <option value="HISTORICA">Históricas</option>
          </select>
        </div>
      </div>

      {/* Listado de Plantillas */}
      {loading ? (
        <div role="status" aria-busy="true" aria-label="Cargando plantillas" className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="p-4 space-y-3">
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-24 w-full" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <ErrorState title="No se pudo cargar el catálogo de plantillas" message={error} onRetry={loadPlantillas} />
      ) : plantillasFiltradas.length === 0 ? (
        <div className="table-container">
          <EmptyState
            icon={FileText}
            title="No se encontraron plantillas"
            subtitle={
              searchTerm || filtroEstado
                ? 'No hay plantillas que coincidan con los filtros aplicados.'
                : 'Aún no hay plantillas de contrato para esta organización.'
            }
          >
            {!searchTerm && !filtroEstado ? (
              <Button variant="primary" size="sm" onClick={abrirNuevo} className="mt-3">
                <Plus aria-hidden="true" />
                Crear primera plantilla
              </Button>
            ) : (
              <Button
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={() => {
                  setSearchTerm('');
                  setFiltroEstado('');
                }}
              >
                Limpiar filtros
              </Button>
            )}
          </EmptyState>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {plantillasFiltradas.map((p) => (
            <Card key={p.idPlantilla} className="flex flex-col justify-between hover:border-primary/40 transition-colors">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-1">
                    <span className="font-mono text-xs text-muted-foreground bg-muted/60 px-1.5 py-0.5 rounded">
                      {p.codigo}
                    </span>
                    <CardTitle className="text-base font-bold leading-tight line-clamp-1">
                      {p.nombre}
                    </CardTitle>
                  </div>
                  <Badge variant={ESTADO_VARIANT[p.estado] || 'secondary'}>
                    {p.estado}
                  </Badge>
                </div>
                <div className="flex items-center gap-2 pt-1">
                  <Badge variant="secondary" className="text-xs font-medium">
                    {p.tipoContrato}
                  </Badge>
                  <Badge variant="outline" className="text-xs bg-tint-blue-bg text-tint-blue-fg border-tint-blue-fg/25 font-mono">
                    HTML
                  </Badge>
                  <span className="text-xs text-muted-foreground flex items-center gap-1">
                    <Layers className="h-3 w-3" /> v{p.version}
                  </span>
                </div>
              </CardHeader>

              <CardContent className="space-y-4 pt-0">
                <p className="text-xs text-muted-foreground line-clamp-2 min-h-[32px]">
                  {p.descripcion || 'Sin descripción detallada registrada para esta plantilla.'}
                </p>

                <div className="text-xs text-muted-foreground space-y-1 border-t border-border/50 pt-3">
                  <div className="flex items-center justify-between">
                    <span>Vigencia:</span>
                    <span className="font-medium text-foreground">
                      {p.vigenciaDesde ? new Date(p.vigenciaDesde).toLocaleDateString('es-CO') : 'Inmediata'}
                      {p.vigenciaHasta ? ` al ${new Date(p.vigenciaHasta).toLocaleDateString('es-CO')}` : ' (Indefinida)'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span>Variables:</span>
                    <span className="font-mono text-foreground font-semibold">
                      {p.variablesDisponibles ? p.variablesDisponibles.length : 0} dinámicas
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between gap-1 pt-2 border-t border-border/50">
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => previsualizar(p)}
                      title="Previsualizar contrato renderizado"
                      className="h-10 px-2 text-xs sm:h-8"
                    >
                      <Eye className="h-3.5 w-3.5 mr-1" />
                      Ver
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDownloadHtml(p)}
                      title="Descargar archivo HTML de la plantilla"
                      className="h-8 px-2 text-xs text-tint-blue-fg"
                    >
                      <Download className="h-3.5 w-3.5 mr-1" />
                      HTML
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => abrirEditar(p)}
                      title="Editar plantilla"
                      className="h-10 px-2 text-xs sm:h-8"
                    >
                      Editar
                    </Button>
                  </div>

                  <div className="flex items-center gap-1 flex-wrap justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => abrirNuevaVersion(p)}
                      title="Crear nueva versión incrementando número"
                      className="h-10 px-2 text-xs sm:h-8"
                    >
                      <Copy className="h-3.5 w-3.5 mr-1" />
                      v{p.version + 1}
                    </Button>

                    {p.estado === 'BORRADOR' && (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => cambiarEstado(p.idPlantilla, 'ACTIVA')}
                        title="Activar plantilla"
                        className="h-10 px-2 text-xs sm:h-8"
                      >
                        Activar
                      </Button>
                    )}

                    {p.estado === 'ACTIVA' && (
                      <>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => cambiarEstado(p.idPlantilla, 'SUSPENDIDA')}
                          title="Suspender plantilla temporalmente"
                          className="h-8 px-2 text-xs text-destructive hover:bg-tint-red-bg"
                        >
                          <PauseCircle className="h-3.5 w-3.5 mr-1" />
                          Suspender
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => cambiarEstado(p.idPlantilla, 'HISTORICA')}
                          title="Archivar como histórica"
                          className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                        >
                          Archivar
                        </Button>
                      </>
                    )}

                    {(p.estado === 'SUSPENDIDA' || p.estado === 'HISTORICA') && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => cambiarEstado(p.idPlantilla, 'ACTIVA')}
                        title="Reactivar plantilla para su uso"
                        className="h-8 px-2 text-xs text-tint-green-fg border-tint-green-fg/25 hover:bg-tint-green-bg"
                      >
                        <PlayCircle className="h-3.5 w-3.5 mr-1" />
                        Reactivar
                      </Button>
                    )}

                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setPlantillaAEliminar(p)}
                      title="Eliminar plantilla"
                      aria-label={`Eliminar plantilla ${p.nombre}`}
                      className="h-10 w-10 p-0 text-text-muted hover:bg-tint-red-bg hover:text-destructive sm:h-8 sm:w-8"
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Modal de Crear / Editar / Versionar */}
      <Dialog open={modalFormOpen} onOpenChange={(o) => !saving && setModalFormOpen(o)}>
        <DialogContent className="max-h-[90dvh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {isVersionMode
                ? 'Crear nueva versión de plantilla'
                : editingId
                  ? 'Editar plantilla de contrato'
                  : 'Nueva plantilla de contrato'}
            </DialogTitle>
            <DialogDescription>Los campos marcados con * son obligatorios.</DialogDescription>
          </DialogHeader>
            <form onSubmit={handleGuardar} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="pl-codigo" className={FIELD_LABEL}>Código único *</label>
                  <input
                    id="pl-codigo"
                    type="text"
                    required
                    disabled={!!editingId && !isVersionMode}
                    value={form.codigo}
                    onChange={(e) => setForm({ ...form, codigo: e.target.value.toUpperCase().replace(/\s+/g, '_') })}
                    placeholder="Ej. CONTRATO_ARRIENDO_V1"
                    className={`${CONTROL} font-mono`}
                  />
                </div>
                <div className="sm:col-span-2">
                  <label htmlFor="pl-nombre" className={FIELD_LABEL}>Nombre de la plantilla *</label>
                  <input
                    id="pl-nombre"
                    type="text"
                    required
                    value={form.nombre}
                    onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                    placeholder="Ej. Contrato de Arrendamiento Residencial Estándar"
                    className={CONTROL}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="pl-tipo" className={FIELD_LABEL}>Tipo de contrato</label>
                  <select
                    id="pl-tipo"
                    value={form.tipoContrato}
                    onChange={(e) => setForm({ ...form, tipoContrato: e.target.value })}
                    className={CONTROL}
                  >
                    {TIPOS_CONTRATO.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label htmlFor="pl-estado-inicial" className={FIELD_LABEL}>Estado inicial</label>
                  <select
                    id="pl-estado-inicial"
                    value={form.estado}
                    onChange={(e) => setForm({ ...form, estado: e.target.value })}
                    className={CONTROL}
                  >
                    <option value="ACTIVA">ACTIVA</option>
                    <option value="BORRADOR">BORRADOR</option>
                    <option value="HISTORICA">HISTORICA</option>
                  </select>
                </div>
                <div>
                  <label htmlFor="pl-vigencia" className={FIELD_LABEL}>Vigencia desde</label>
                  <input
                    id="pl-vigencia"
                    type="date"
                    value={form.vigenciaDesde}
                    onChange={(e) => setForm({ ...form, vigenciaDesde: e.target.value })}
                    className={CONTROL}
                  />
                </div>
              </div>

              <div>
                <label htmlFor="pl-descripcion" className={FIELD_LABEL}>Descripción operativa</label>
                <input
                  id="pl-descripcion"
                  type="text"
                  value={form.descripcion}
                  onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                  placeholder="Instrucciones para los administradores de propiedad al aplicar esta plantilla..."
                  className={CONTROL}
                />
              </div>

              {/* Zona de Carga de Archivo HTML */}
              <div className="p-4 border-2 border-dashed border-primary/30 rounded-xl bg-primary/5 hover:bg-primary/10 transition-colors">
                <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="p-2.5 bg-primary/10 rounded-lg text-primary">
                      <UploadCloud className="h-6 w-6" />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-foreground">
                        Cargar Contrato en Formato HTML
                      </h4>
                      <p className="text-xs text-muted-foreground">
                        Seleccione o arrastre un archivo .html o .htm para importar el contenido automáticamente
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="file"
                      ref={fileInputRef}
                      accept=".html,.htm,text/html"
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => fileInputRef.current?.click()}
                      className="text-xs font-semibold gap-1.5 shadow-sm bg-background"
                    >
                      <FileCode className="h-3.5 w-3.5 text-primary" />
                      Subir Archivo .HTML
                    </Button>
                  </div>
                </div>
                {uploadedFileName && (
                  <div className="mt-3 pt-3 border-t border-primary/20 flex items-center justify-between text-xs text-primary font-medium">
                    <span className="flex items-center gap-1.5">
                      <CheckCircle2 className="h-3.5 w-3.5 text-tint-green-fg" />
                      Archivo cargado: <strong>{uploadedFileName}</strong>
                    </span>
                    <button
                      type="button"
                      onClick={() => {
                        setUploadedFileName('');
                        if (fileInputRef.current) fileInputRef.current.value = '';
                      }}
                      className="text-muted-foreground hover:text-destructive text-xs"
                    >
                      Quitar archivo
                    </button>
                  </div>
                )}
              </div>

              {/* Pestañas de Editor HTML / Vista Previa en Vivo */}
              <div className="border border-border rounded-xl overflow-hidden bg-background">
                <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-muted/30">
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      aria-pressed={modalTab === 'editor'}
                      onClick={() => setModalTab('editor')}
                      className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center gap-1.5 ${
                        modalTab === 'editor'
                          ? 'bg-background text-primary shadow-sm border border-border'
                          : 'text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <Code className="h-3.5 w-3.5" />
                      Código HTML del Contrato
                    </button>
                    <button
                      type="button"
                      aria-pressed={modalTab === 'preview'}
                      onClick={() => setModalTab('preview')}
                      className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center gap-1.5 ${
                        modalTab === 'preview'
                          ? 'bg-background text-primary shadow-sm border border-border'
                          : 'text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <Eye className="h-3.5 w-3.5" />
                      Vista Previa en Vivo
                    </button>
                  </div>
                  <span className="text-xs font-mono text-muted-foreground">
                    {form.contenidoHtml ? `${(new Blob([form.contenidoHtml]).size / 1024).toFixed(1)} KB` : '0 KB'}
                  </span>
                </div>

                {modalTab === 'editor' ? (
                  <div className="p-4 space-y-3">
                    {/* Inserción de variables dinámicas */}
                    <div className="space-y-2 p-3 bg-muted/40 border border-border/70 rounded-lg">
                      <div className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
                        <Sparkles className="h-3.5 w-3.5 text-tint-amber-fg" />
                        <span>Insertar Variable Dinámica (clic para añadir al HTML):</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {DEFAULT_VARIABLES.map((v) => (
                          <button
                            type="button"
                            key={v}
                            onClick={() => insertarVariable(v)}
                            className="text-xs font-mono px-2 py-1 bg-background hover:bg-primary/10 hover:text-primary hover:border-primary/40 border border-border rounded transition-colors text-muted-foreground"
                          >
                            + {`\${${v}}`}
                          </button>
                        ))}
                      </div>
                    </div>

                    <div>
                      <textarea
                        required
                        aria-label="Código HTML del contrato"
                        rows={12}
                        value={form.contenidoHtml}
                        onChange={(e) => setForm({ ...form, contenidoHtml: e.target.value })}
                        placeholder="Pegue o escriba aquí el código HTML de su contrato..."
                        className="w-full rounded-md border border-input bg-surface px-3 py-2 font-mono text-xs leading-relaxed text-on-surface focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                      />
                      <p className="text-xs text-muted-foreground mt-1">
                        Soporta etiquetas HTML estándar (&lt;h1&gt;, &lt;p&gt;, &lt;table&gt;, &lt;ul&gt;, etc.) y estilos CSS inline.
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="min-h-[250px] max-h-[400px] overflow-y-auto bg-surface p-4">
                    <div className="mb-3 flex items-center justify-between border-b border-border pb-2 text-xs text-text-muted">
                      <span className="font-semibold text-on-surface">Vista con datos de ejemplo</span>
                      <span>Se actualiza al escribir</span>
                    </div>
                    {/* F5-H06: template HTML is rendered in a sandboxed frame (no scripts, no same-origin access). */}
                    <iframe
                      title="Vista previa de la plantilla"
                      sandbox=""
                      srcDoc={renderLiveHtml(form.contenidoHtml)}
                      className="w-full min-h-[320px] bg-white rounded border-0"
                    />
                  </div>
                )}
              </div>

              <DialogFooter className="gap-2 border-t border-border pt-4">
                <Button type="button" variant="outline" onClick={() => setModalFormOpen(false)} disabled={saving}>
                  Cancelar
                </Button>
                <Button type="submit" variant="primary" disabled={saving}>
                  {saving ? 'Guardando...' : isVersionMode ? 'Publicar nueva versión' : 'Guardar plantilla'}
                </Button>
              </DialogFooter>
            </form>
        </DialogContent>
      </Dialog>

      {/* Modal de Vista Previa Renderizada */}
      <Dialog open={previewModalOpen} onOpenChange={setPreviewModalOpen}>
        <DialogContent className="flex max-h-[90dvh] max-w-3xl flex-col overflow-hidden">
          <DialogHeader>
            <DialogTitle className="pr-8">Vista previa: {previewTitle}</DialogTitle>
            <DialogDescription>Contrato con las variables sustituidas por datos de ejemplo.</DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 overflow-y-auto rounded-md border border-border">
            {previewLoading ? (
              <div className="space-y-3 p-6" role="status" aria-busy="true">
                <Skeleton className="h-6 w-1/2" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-11/12" />
                <Skeleton className="h-40 w-full" />
                <p className="text-xs text-text-muted">Sustituyendo variables y generando el contrato...</p>
              </div>
            ) : (
              <iframe
                title="Vista previa del contrato"
                sandbox=""
                srcDoc={previewHtml || ''}
                className="min-h-[60vh] w-full border-0 bg-white"
              />
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setPreviewModalOpen(false)}>
              Cerrar vista previa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Diálogo de Confirmación para Eliminar */}
      <ConfirmDialog
        open={!!plantillaAEliminar}
        onClose={() => setPlantillaAEliminar(null)}
        onConfirm={handleEliminarConfirmado}
        title="Eliminar plantilla de contrato"
        message={`¿Está seguro de que desea eliminar la plantilla "${plantillaAEliminar?.nombre}" (${plantillaAEliminar?.codigo})? Esta acción no se puede deshacer.`}
        confirmLabel="Eliminar definitivamente"
        danger
      />
    </PageContainer>
  );
}
