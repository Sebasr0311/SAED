import { useEffect, useState, useMemo } from 'react';
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
  Clock,
  AlertCircle,
  Calendar,
  Sparkles,
  Code,
  Archive,
  RefreshCw,
  X,
  Layers,
} from 'lucide-react';
import { toast } from 'sonner';

const TIPOS_CONTRATO = ['INICIAL', 'RENOVACION', 'PERMANENCIA', 'COMERCIAL', 'OTRO'];

const ESTADO_COLORS = {
  ACTIVA: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
  BORRADOR: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
  HISTORICA: 'bg-slate-500/10 text-slate-600 dark:text-slate-400 border-slate-500/20',
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
  <li><strong>Canon Mensual:</strong> \$\${contrato.canon_mensual} COP</li>
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
    <div className="space-y-6">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border/70 pb-5">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Plantillas de Contratos
            </h1>
            <Badge variant="outline" className="bg-primary/5 text-primary border-primary/20 text-xs font-semibold">
              Multi-Tenancy Org
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1">
            Configuración centralizada de modelos contractuales, versiones y variables dinámicas para toda la cartera
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadPlantillas} disabled={loading}>
            <RefreshCw className={`h-4 w-4 mr-1.5 ${loading ? 'animate-spin' : ''}`} />
            Actualizar
          </Button>
          <Button variant="primary" size="sm" onClick={abrirNuevo}>
            <Plus className="h-4 w-4 mr-1.5" />
            Nueva Plantilla
          </Button>
        </div>
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="relative w-full sm:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por nombre, código o tipo..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-8 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {searchTerm && (
            <button
              onClick={() => setSearchTerm('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <label className="text-xs text-muted-foreground whitespace-nowrap">Estado:</label>
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className="text-xs sm:text-sm bg-background border border-border rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            <option value="">Todos los estados</option>
            <option value="ACTIVA">Activas</option>
            <option value="BORRADOR">Borradores</option>
            <option value="HISTORICA">Históricas</option>
          </select>
        </div>
      </div>

      {/* Listado de Plantillas */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="p-4 space-y-3">
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-24 w-full" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="p-6 text-center text-danger-600 space-y-2">
          <AlertCircle className="h-8 w-8 mx-auto" />
          <p className="font-semibold">{error}</p>
          <Button variant="outline" size="sm" onClick={loadPlantillas}>
            Reintentar
          </Button>
        </Card>
      ) : plantillasFiltradas.length === 0 ? (
        <Card className="p-12 text-center space-y-3">
          <FileText className="h-10 w-10 mx-auto text-muted-foreground/60" />
          <h3 className="font-semibold text-foreground">No se encontraron plantillas</h3>
          <p className="text-xs sm:text-sm text-muted-foreground max-w-md mx-auto">
            {searchTerm || filtroEstado
              ? 'No hay plantillas que coincidan con los filtros aplicados.'
              : 'Aún no has registrado plantillas de contrato para esta organización. Crea la primera con el botón "Nueva Plantilla".'}
          </p>
          {!searchTerm && !filtroEstado && (
            <Button variant="primary" size="sm" onClick={abrirNuevo}>
              <Plus className="h-4 w-4 mr-1.5" />
              Crear Primera Plantilla
            </Button>
          )}
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {plantillasFiltradas.map((p) => (
            <Card key={p.idPlantilla} className="flex flex-col justify-between hover:border-primary/40 transition-colors">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-1">
                    <span className="font-mono text-[11px] text-muted-foreground bg-muted/60 px-1.5 py-0.5 rounded">
                      {p.codigo}
                    </span>
                    <CardTitle className="text-base font-bold leading-tight line-clamp-1">
                      {p.nombre}
                    </CardTitle>
                  </div>
                  <Badge variant="outline" className={`text-xs font-semibold ${ESTADO_COLORS[p.estado] || ''}`}>
                    {p.estado}
                  </Badge>
                </div>
                <div className="flex items-center gap-2 pt-1">
                  <Badge variant="secondary" className="text-[11px] font-medium">
                    {p.tipoContrato}
                  </Badge>
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <Layers className="h-3 w-3" /> v{p.version}
                  </span>
                </div>
              </CardHeader>

              <CardContent className="space-y-4 pt-0">
                <p className="text-xs text-muted-foreground line-clamp-2 min-h-[32px]">
                  {p.descripcion || 'Sin descripción detallada registrada para esta plantilla.'}
                </p>

                <div className="text-[11px] text-muted-foreground space-y-1 border-t border-border/50 pt-3">
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
                      className="h-8 px-2 text-xs"
                    >
                      <Eye className="h-3.5 w-3.5 mr-1" />
                      Ver
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => abrirEditar(p)}
                      title="Editar plantilla"
                      className="h-8 px-2 text-xs"
                    >
                      Editar
                    </Button>
                  </div>

                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => abrirNuevaVersion(p)}
                      title="Crear nueva versión incrementando número"
                      className="h-8 px-2 text-xs"
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
                        className="h-8 px-2 text-xs bg-emerald-600 hover:bg-emerald-700"
                      >
                        Activar
                      </Button>
                    )}
                    {p.estado === 'ACTIVA' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => cambiarEstado(p.idPlantilla, 'HISTORICA')}
                        title="Archivar como histórica"
                        className="h-8 px-2 text-xs text-muted-foreground hover:text-danger-600"
                      >
                        Archivar
                      </Button>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Modal de Crear / Editar / Versionar */}
      {modalFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm overflow-y-auto">
          <div className="bg-background border border-border rounded-xl shadow-2xl w-full max-w-3xl my-8 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-border">
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-primary" />
                <h3 className="font-bold text-lg text-foreground">
                  {isVersionMode
                    ? `Crear Nueva Versión de Plantilla`
                    : editingId
                      ? 'Editar Plantilla de Contrato'
                      : 'Nueva Plantilla de Contrato'}
                </h3>
              </div>
              <button
                onClick={() => setModalFormOpen(false)}
                className="text-muted-foreground hover:text-foreground p-1 rounded-lg"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleGuardar} className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="text-xs font-semibold text-foreground">Código Único *</label>
                  <input
                    type="text"
                    required
                    disabled={!!editingId && !isVersionMode}
                    value={form.codigo}
                    onChange={(e) => setForm({ ...form, codigo: e.target.value.toUpperCase().replace(/\s+/g, '_') })}
                    placeholder="Ej. CONTRATO_ARRIENDO_V1"
                    className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg font-mono focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:opacity-60"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="text-xs font-semibold text-foreground">Nombre de la Plantilla *</label>
                  <input
                    type="text"
                    required
                    value={form.nombre}
                    onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                    placeholder="Ej. Contrato de Arrendamiento Residencial Estándar"
                    className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="text-xs font-semibold text-foreground">Tipo de Contrato</label>
                  <select
                    value={form.tipoContrato}
                    onChange={(e) => setForm({ ...form, tipoContrato: e.target.value })}
                    className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                  >
                    {TIPOS_CONTRATO.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-foreground">Estado Inicial</label>
                  <select
                    value={form.estado}
                    onChange={(e) => setForm({ ...form, estado: e.target.value })}
                    className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                  >
                    <option value="ACTIVA">ACTIVA</option>
                    <option value="BORRADOR">BORRADOR</option>
                    <option value="HISTORICA">HISTORICA</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-foreground">Vigencia Desde</label>
                  <input
                    type="date"
                    value={form.vigenciaDesde}
                    onChange={(e) => setForm({ ...form, vigenciaDesde: e.target.value })}
                    className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-foreground">Descripción Operativa</label>
                <input
                  type="text"
                  value={form.descripcion}
                  onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                  placeholder="Instrucciones para los administradores de propiedad al aplicar esta plantilla..."
                  className="w-full mt-1.5 px-3 py-2 text-xs sm:text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>

              {/* Inserción de variables dinámicas */}
              <div className="space-y-2 p-3 bg-muted/40 border border-border/70 rounded-lg">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
                  <Sparkles className="h-3.5 w-3.5 text-amber-500" />
                  <span>Variables Dinámicas Disponibles (Haz clic para insertar en el contrato):</span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {DEFAULT_VARIABLES.map((v) => (
                    <button
                      type="button"
                      key={v}
                      onClick={() => insertarVariable(v)}
                      className="text-[11px] font-mono px-2 py-1 bg-background hover:bg-primary/10 hover:text-primary hover:border-primary/40 border border-border rounded transition-colors text-muted-foreground"
                    >
                      + {`\${${v}}`}
                    </button>
                  ))}
                </div>
              </div>

              {/* Editor HTML */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <Code className="h-4 w-4 text-primary" />
                    Cuerpo del Contrato (HTML con placeholders) *
                  </label>
                  <span className="text-[11px] text-muted-foreground">Soporta HTML estándar y CSS inline</span>
                </div>
                <textarea
                  required
                  rows={10}
                  value={form.contenidoHtml}
                  onChange={(e) => setForm({ ...form, contenidoHtml: e.target.value })}
                  className="w-full px-3 py-2 text-xs sm:text-sm font-mono bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
                <Button type="button" variant="outline" onClick={() => setModalFormOpen(false)} disabled={saving}>
                  Cancelar
                </Button>
                <Button type="submit" variant="primary" disabled={saving}>
                  {saving ? 'Guardando...' : isVersionMode ? 'Publicar Nueva Versión' : 'Guardar Plantilla'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal de Vista Previa Renderizada */}
      {previewModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm overflow-y-auto">
          <div className="bg-background border border-border rounded-xl shadow-2xl w-full max-w-3xl my-8 overflow-hidden flex flex-col max-h-[85vh]">
            <div className="flex items-center justify-between px-6 py-4 border-b border-border">
              <div className="flex items-center gap-2">
                <Eye className="h-5 w-5 text-primary" />
                <h3 className="font-bold text-base text-foreground">Vista Previa: {previewTitle}</h3>
              </div>
              <button
                onClick={() => setPreviewModalOpen(false)}
                className="text-muted-foreground hover:text-foreground p-1 rounded-lg"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-1 bg-white text-slate-900 rounded-b-xl dark:bg-slate-900 dark:text-slate-100">
              {previewLoading ? (
                <div className="py-16 text-center space-y-3">
                  <RefreshCw className="h-8 w-8 animate-spin mx-auto text-primary" />
                  <p className="text-xs text-muted-foreground">Sustituyendo variables y generando contrato...</p>
                </div>
              ) : (
                <div
                  className="prose prose-sm dark:prose-invert max-w-none font-serif leading-relaxed"
                  dangerouslySetInnerHTML={{ __html: previewHtml }}
                />
              )}
            </div>

            <div className="px-6 py-3 border-t border-border bg-muted/20 flex justify-end">
              <Button variant="outline" size="sm" onClick={() => setPreviewModalOpen(false)}>
                Cerrar Previsualización
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
