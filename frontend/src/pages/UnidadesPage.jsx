import { useMemo, useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/button.tsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  CornerDownRight,
  Layers,
  Network,
  Pencil,
  Plus,
  PlusCircle,
  SlidersHorizontal,
  ToggleLeft,
  ToggleRight,
  Trash2,
} from 'lucide-react';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../components/ui/card.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';
import { formatMiles } from '../lib/utils.js';
import PropertyConfigModal from '../components/PropertyConfigModal.jsx';

/**
 * UnidadesPage 2.0 — Jerarquía Propiedad -> Bloque -> Unidad (GAP-CFG-06).
 * Incluye pestañas para:
 * 1. Gestión de Unidades (CRUD, tipo de unidad, asignación a bloques, área, coeficiente).
 * 2. Gestión de Estructura de Copropiedad (Bloques, Torres, Etapas, Manzanas, Pisos, Sectores).
 * 3. Acceso directo a Configuración Operativa de la Propiedad (GAP-CFG-07).
 */
const ESTADO_BADGE = {
  ACTIVA: 'success',
  ACTIVO: 'success',
  INACTIVA: 'secondary',
  INACTIVO: 'secondary',
  EN_CONSTRUCCION: 'outline',
  SUSPENDIDA: 'destructive',
};

const ESTADO_LABEL = {
  ACTIVA: 'Activa',
  ACTIVO: 'Activo',
  INACTIVA: 'Inactiva',
  INACTIVO: 'Inactivo',
  EN_CONSTRUCCION: 'En construcción',
  SUSPENDIDA: 'Suspendida',
};

const BLOCK_TIPO_BADGE = {
  TORRE: 'info',
  BLOQUE: 'secondary',
  ETAPA: 'outline',
  MANZANA: 'lavender',
  PISO: 'secondary',
  SECTOR: 'outline',
};

const emptyForm = {
  identificador: '',
  idBloque: 'NONE',
  idTipoUnidad: '',
  piso: '',
  areaM2: '',
  coeficienteCopropiedad: '',
};

const emptyBlockForm = {
  tipo: 'TORRE',
  codigo: '',
  nombre: '',
  idBloquePadre: '',
  orden: 1,
};

export default function UnidadesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const activePropertyId = tenant.activePropertyId;

  // Unidades data
  const { data, loading, error: errorUnidades, refetch: refetchUnidades } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );
  const { data: tiposUnidad } = useFetch(
    () => tenantApi.get('/tipos-unidad'),
    [tenant.activeAssignmentId]
  );

  // Bloques data (intentar endpoint específico de propiedad y fallback a /bloques)
  const { data: bloquesData, loading: loadingBloques, error: errorBloques, refetch: refetchBloques } = useFetch(
    () => activePropertyId
      ? tenantApi.get(`/properties/${activePropertyId}/blocks`).catch(() => tenantApi.get('/bloques'))
      : tenantApi.get('/bloques'),
    [tenant.activeAssignmentId, activePropertyId]
  );

  // DEC-04: coefficient census of the property; an incomplete sum is shown as a warning.
  const { data: coefSummary, refetch: refetchCoef } = useFetch(
    () => (activePropertyId
      ? tenantApi.get(`/units/coefficient-summary?idPropiedad=${activePropertyId}`)
      : Promise.resolve(null)),
    [tenant.activeAssignmentId, activePropertyId]
  );

  const unidades = Array.isArray(data) ? data : (data?.items || []);
  const tipos = (tiposUnidad?.items || (Array.isArray(tiposUnidad) ? tiposUnidad : [])).map((t) => ({
    idTipoUnidad: t.idTipoUnidad ?? t.ID_TIPO_UNIDAD ?? t.id,
    nombre: t.nombre ?? t.NOMBRE ?? '',
    codigo: t.codigo ?? t.CODIGO ?? '',
  }));

  const rawBloques = Array.isArray(bloquesData) ? bloquesData : (bloquesData?.items || []);
  const bloquesList = rawBloques.map((b) => ({
    idBloque: b.idBloque ?? b.ID_BLOQUE ?? b.id,
    idPropiedad: b.idPropiedad ?? b.ID_PROPIEDAD,
    idBloquePadre: b.idBloquePadre ?? b.ID_BLOQUE_PADRE,
    nombreBloquePadre: b.nombreBloquePadre ?? b.bloquePadreNombre ?? b.BLOQUE_PADRE_NOMBRE,
    tipo: b.tipo ?? b.TIPO ?? 'BLOQUE',
    codigo: b.codigo ?? b.CODIGO ?? '',
    nombre: b.nombre ?? b.NOMBRE ?? '',
    orden: b.orden ?? b.ORDEN ?? 0,
    estado: b.estado ?? b.ESTADO ?? 'ACTIVO',
  }));

  // Map for fast block name lookup
  const blockMap = useMemo(() => {
    const m = new Map();
    bloquesList.forEach((b) => m.set(Number(b.idBloque), b));
    return m;
  }, [bloquesList]);

  // Unit Dialog states
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  // Block Dialog states
  const [blockDialogOpen, setBlockDialogOpen] = useState(false);
  const [editingBlock, setEditingBlock] = useState(null);
  const [blockForm, setBlockForm] = useState(emptyBlockForm);
  const [savingBlock, setSavingBlock] = useState(false);
  const [deleteBlockTarget, setDeleteBlockTarget] = useState(null);

  // Property Config Modal state (GAP-CFG-07)
  const [configModalOpen, setConfigModalOpen] = useState(false);

  // Guardar unidad (Creación / Edición)
  async function guardarUnidad() {
    if (!form.identificador.trim()) {
      toast.error('El identificador es obligatorio');
      return;
    }
    if (!form.idTipoUnidad) {
      toast.error('Seleccione el tipo de unidad');
      return;
    }
    let areaVal = null;
    if (form.areaM2 !== '' && form.areaM2 != null) {
      areaVal = Number(form.areaM2);
      if (Number.isNaN(areaVal) || !Number.isFinite(areaVal) || areaVal <= 0) {
        toast.error('El área debe ser un número positivo mayor a 0');
        return;
      }
    }
    let coefVal = null;
    if (form.coeficienteCopropiedad !== '' && form.coeficienteCopropiedad != null) {
      coefVal = Number(form.coeficienteCopropiedad);
      if (Number.isNaN(coefVal) || !Number.isFinite(coefVal) || coefVal <= 0 || coefVal > 1) {
        toast.error('El coeficiente debe ser un número entre 0 y 1 (ej: 0.0035)');
        return;
      }
    }
    setSaving(true);
    try {
      const payload = {
        idPropiedad: activePropertyId,
        idTipoUnidad: Number(form.idTipoUnidad),
        identificador: form.identificador.trim(),
        idBloque: form.idBloque && form.idBloque !== 'NONE' ? Number(form.idBloque) : null,
        piso: form.piso && form.piso.trim() ? form.piso.trim() : null,
        areaM2: areaVal,
        coeficienteCopropiedad: coefVal,
      };
      if (editing) {
        await tenantApi.put(`/units/${editing.id}`, payload);
        toast.success('Unidad actualizada');
      } else {
        await tenantApi.post('/units', payload);
        toast.success('Unidad creada');
      }
      setDialogOpen(false);
      refetchUnidades();
      // The census banner is advisory; its own fetch error state is kept by useFetch.
      refetchCoef().catch(() => undefined);
    } catch (err) {
      toast.error(err.message || 'No se pudo guardar la unidad');
    } finally {
      setSaving(false);
    }
  }

  // Guardar Bloque (Creación / Edición)
  async function guardarBloque() {
    if (!blockForm.codigo.trim()) {
      toast.error('El código del bloque/torre es obligatorio (ej. T1, P2)');
      return;
    }
    if (!blockForm.nombre.trim()) {
      toast.error('El nombre del bloque/torre es obligatorio');
      return;
    }
    if (!activePropertyId) {
      toast.error('No hay una propiedad activa seleccionada');
      return;
    }

    setSavingBlock(true);
    try {
      const payload = {
        tipo: blockForm.tipo,
        codigo: blockForm.codigo.trim().toUpperCase(),
        nombre: blockForm.nombre.trim(),
        idBloquePadre: blockForm.idBloquePadre && blockForm.idBloquePadre !== 'NONE' ? Number(blockForm.idBloquePadre) : null,
        orden: Number(blockForm.orden) || 1,
      };

      if (editingBlock) {
        await tenantApi.put(`/properties/${activePropertyId}/blocks/${editingBlock.idBloque}`, payload);
        toast.success(`Estructura "${payload.nombre}" actualizada`);
      } else {
        await tenantApi.post(`/properties/${activePropertyId}/blocks`, payload);
        toast.success(`Estructura "${payload.nombre}" creada exitosamente`);
      }
      setBlockDialogOpen(false);
      refetchBloques();
    } catch (err) {
      console.error('Error saving block:', err);
      toast.error(err?.response?.data?.message || err?.message || 'No se pudo guardar la estructura');
    } finally {
      setSavingBlock(false);
    }
  }

  // Alternar estado de bloque
  async function toggleBlockStatus(b) {
    if (!activePropertyId) return;
    const nuevoEstado = b.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await tenantApi.patch(`/properties/${activePropertyId}/blocks/${b.idBloque}/status`, {
        estado: nuevoEstado,
      });
      toast.success(`Bloque ${b.nombre} ahora está ${nuevoEstado}`);
      refetchBloques();
    } catch (err) {
      toast.error('Error al cambiar estado del bloque');
    }
  }

  // Eliminar bloque
  async function confirmDeleteBlock() {
    if (!deleteBlockTarget || !activePropertyId) return;
    try {
      await tenantApi.delete(`/properties/${activePropertyId}/blocks/${deleteBlockTarget.idBloque}`);
      toast.success(`Bloque "${deleteBlockTarget.nombre}" eliminado`);
      setDeleteBlockTarget(null);
      refetchBloques();
    } catch (err) {
      toast.error(err?.response?.data?.message || err?.message || 'No se pudo eliminar el bloque (puede tener unidades asociadas)');
    }
  }

  const total = unidades.length;
  const activas = unidades.filter((u) => u.estado === 'ACTIVA').length;

  const stats = useMemo(
    () => [
      { label: 'Unidades', value: total, icon: Building2, variant: 'primary' },
      { label: 'Activas', value: activas, icon: CheckCircle2, variant: 'success' },
      { label: 'Tipos', value: tipos.length, icon: Layers, variant: 'secondary' },
      { label: 'Bloques / Torres', value: bloquesList.length, icon: Network, variant: 'info' },
    ],
    [total, activas, tipos.length, bloquesList.length]
  );

  return (
    <PageContainer className="unidades-page">
      <PageHeader
        title="Unidades y Estructura"
        subtitle="Estructura de la copropiedad: bloques, torres y unidades habitacionales."
        action={
          <>
            {activePropertyId && (
              <Button
                variant="outline"
                onClick={() => setConfigModalOpen(true)}
                className="min-h-10"
                title="Ajustar parámetros y límites de la copropiedad"
              >
                <SlidersHorizontal aria-hidden="true" />
                Configuración
              </Button>
            )}
            <Button
              className="min-h-10"
              onClick={() => { setEditing(null); setForm(emptyForm); refetchBloques(); setDialogOpen(true); }}
            >
              <Plus aria-hidden="true" />
              Nueva Unidad
            </Button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((s) => (
          <MetricCard
            key={s.label}
            label={s.label}
            value={!data ? '—' : s.value}
            icon={s.icon}
            variant={s.variant}
          />
        ))}
      </div>

      {coefSummary?.advertencia && (
        <div role="status" className="rounded-md border border-tint-amber-fg/25 bg-tint-amber-bg px-4 py-3 text-sm text-tint-amber-fg">
          {coefSummary.advertencia}
        </div>
      )}

      <Tabs defaultValue="unidades" className="w-full space-y-4">
        <TabsList className="grid w-full max-w-md grid-cols-2">
          <TabsTrigger value="unidades" className="flex items-center gap-2">
            <Building2 className="h-4 w-4" aria-hidden="true" />
            <span>Unidades <span className="tabular-nums">({total})</span></span>
          </TabsTrigger>
          <TabsTrigger value="estructura" className="flex items-center gap-2">
            <Network className="h-4 w-4" aria-hidden="true" />
            <span>Estructura / Bloques <span className="tabular-nums">({bloquesList.length})</span></span>
          </TabsTrigger>
        </TabsList>

        {/* TAB 1: LISTADO DE UNIDADES */}
        <TabsContent value="unidades">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base">Listado de Unidades</CardTitle>
                <CardDescription className="text-xs">
                  Apartamentos, casas, locales, oficinas, parqueaderos y depósitos registrados
                </CardDescription>
              </div>
            </CardHeader>
            <CardContent>
              {loading && !data ? (
                <div className="space-y-2" role="status" aria-label="Cargando unidades">
                  <Skeleton className="h-8 w-full" />
                  <Skeleton className="h-8 w-full" />
                  <Skeleton className="h-8 w-full" />
                </div>
              ) : errorUnidades && !data ? (
                <ErrorState
                  title="No se pudieron cargar las unidades"
                  message={errorUnidades?.message || 'Verifique su conexión e intente nuevamente.'}
                  onRetry={() => refetchUnidades().catch(() => {})}
                />
              ) : unidades.length === 0 ? (
                <EmptyState
                  icon={Building2}
                  title="No hay unidades registradas"
                  subtitle="Registre la primera unidad habitacional de esta copropiedad."
                >
                  <Button
                    className="mt-4 min-h-10"
                    onClick={() => { setEditing(null); setForm(emptyForm); refetchBloques(); setDialogOpen(true); }}
                  >
                    <Plus aria-hidden="true" />
                    Nueva Unidad
                  </Button>
                </EmptyState>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Identificador</TableHead>
                        <TableHead>Tipo</TableHead>
                        <TableHead>Bloque / Torre</TableHead>
                        <TableHead>Área (m²)</TableHead>
                        <TableHead>Coeficiente</TableHead>
                        <TableHead>Estado</TableHead>
                        <TableHead className="text-right">Acciones</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {unidades.map((u) => (
                        <TableRow key={u.id}>
                          <TableCell className="font-medium">{u.identificador}</TableCell>
                          <TableCell>
                            <span className="text-xs px-2 py-0.5 rounded bg-muted font-medium text-foreground">
                              {u.tipoUnidadNombre || u.tipoUnidadCodigo || '—'}
                            </span>
                          </TableCell>
                          <TableCell>
                            {u.bloqueNombre || u.bloqueCodigo ? (
                              <span className="text-xs font-semibold text-foreground">
                                {u.bloqueNombre || u.bloqueCodigo}
                              </span>
                            ) : (
                              <span className="text-xs text-muted-foreground italic">Sin bloque</span>
                            )}
                          </TableCell>
                          <TableCell className="tabular-nums">
                            {u.areaM2 != null ? `${formatMiles(u.areaM2)} m²` : '—'}
                          </TableCell>
                          <TableCell className="tabular-nums">
                            {u.coeficienteCopropiedad != null ? `${u.coeficienteCopropiedad}` : '—'}
                          </TableCell>
                          <TableCell>
                            <Badge variant={ESTADO_BADGE[u.estado || 'ACTIVA'] || 'secondary'}>
                              {ESTADO_LABEL[u.estado || 'ACTIVA'] || u.estado}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setEditing(u);
                                setForm({
                                  identificador: u.identificador || u.IDENTIFICADOR || '',
                                  idBloque: (u.idBloque != null && u.idBloque !== '') ? String(u.idBloque) : ((u.ID_BLOQUE != null && u.ID_BLOQUE !== '') ? String(u.ID_BLOQUE) : 'NONE'),
                                  idTipoUnidad: u.idTipoUnidad != null ? String(u.idTipoUnidad) : (u.ID_TIPO_UNIDAD != null ? String(u.ID_TIPO_UNIDAD) : ''),
                                  piso: u.piso || '',
                                  areaM2: u.areaM2 != null ? String(u.areaM2) : (u.AREA_M2 != null ? String(u.AREA_M2) : ''),
                                  coeficienteCopropiedad: u.coeficienteCopropiedad != null ? String(u.coeficienteCopropiedad) : (u.COEFICIENTE_COPROPIEDAD != null ? String(u.COEFICIENTE_COPROPIEDAD) : ''),
                                });
                                refetchBloques();
                                setDialogOpen(true);
                              }}
                              aria-label={`Editar ${u.identificador}`}
                            >
                              <Pencil aria-hidden="true" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* TAB 2: GESTIÓN DE ESTRUCTURA Y BLOQUES */}
        <TabsContent value="estructura">
          <Card>
            <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <CardTitle className="text-base flex items-center gap-2">
                  <Network className="h-5 w-5 text-primary-text" aria-hidden="true" />
                  Estructura Arquitectónica de la Copropiedad
                </CardTitle>
                <CardDescription className="text-xs">
                  Organice su propiedad mediante Torres, Bloques, Etapas, Manzanas, Pisos o Sectores
                </CardDescription>
              </div>
              <Button
                size="sm"
                onClick={() => {
                  setEditingBlock(null);
                  setBlockForm(emptyBlockForm);
                  setBlockDialogOpen(true);
                }}
                className="min-h-10"
              >
                <Plus aria-hidden="true" />
                Nuevo Bloque / Torre
              </Button>
            </CardHeader>
            <CardContent>
              {loadingBloques && !bloquesData ? (
                <div className="space-y-2" role="status" aria-label="Cargando estructura">
                  <Skeleton className="h-8 w-full" />
                  <Skeleton className="h-8 w-full" />
                </div>
              ) : errorBloques && !bloquesData ? (
                <ErrorState
                  title="No se pudo cargar la estructura"
                  message={errorBloques?.message || 'Verifique su conexión e intente nuevamente.'}
                  onRetry={() => refetchBloques().catch(() => {})}
                />
              ) : bloquesList.length === 0 ? (
                <EmptyState
                  icon={Network}
                  title="No hay bloques ni torres configurados"
                  subtitle="Defina la estructura (Torre A, Torre B, Manzana 1, Piso 1, etc.) para organizar y asignar sus unidades."
                >
                  <Button
                    variant="outline"
                    onClick={() => {
                      setEditingBlock(null);
                      setBlockForm(emptyBlockForm);
                      setBlockDialogOpen(true);
                    }}
                    className="mt-4 min-h-10"
                  >
                    Crear primera estructura
                  </Button>
                </EmptyState>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Tipo</TableHead>
                        <TableHead>Código</TableHead>
                        <TableHead>Nombre</TableHead>
                        <TableHead>Jerarquía / Padre</TableHead>
                        <TableHead>Orden</TableHead>
                        <TableHead>Estado</TableHead>
                        <TableHead className="text-right">Acciones</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {bloquesList.map((b) => {
                        const padre = b.idBloquePadre ? blockMap.get(Number(b.idBloquePadre)) : null;
                        return (
                          <TableRow key={b.idBloque}>
                            <TableCell>
                              <Badge variant={BLOCK_TIPO_BADGE[b.tipo] || 'outline'} className="text-[11.5px]">
                                {b.tipo}
                              </Badge>
                            </TableCell>
                            <TableCell className="font-mono font-semibold text-xs">
                              {b.codigo}
                            </TableCell>
                            <TableCell className="font-medium text-foreground">
                              {b.nombre}
                            </TableCell>
                            <TableCell className="text-xs text-muted-foreground">
                              {padre ? (
                                <span className="flex items-center gap-1">
                                  <CornerDownRight className="h-3 w-3" aria-hidden="true" />
                                  {padre.nombre} ({padre.codigo})
                                </span>
                              ) : (
                                <span className="italic">Nivel principal</span>
                              )}
                            </TableCell>
                            <TableCell className="text-xs font-mono tabular-nums">
                              {b.orden}
                            </TableCell>
                            <TableCell>
                              <Badge variant={ESTADO_BADGE[b.estado] || 'secondary'}>
                                {ESTADO_LABEL[b.estado] || b.estado}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-right">
                              <div className="flex items-center justify-end gap-1">
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => {
                                    setEditing(null);
                                    setForm({ ...emptyForm, idBloque: String(b.idBloque) });
                                    refetchBloques();
                                    setDialogOpen(true);
                                  }}
                                  className="h-9 w-9 p-0 hover:text-primary-text"
                                  title={`Registrar unidad en ${b.nombre}`}
                                  aria-label={`Registrar unidad en ${b.nombre}`}
                                >
                                  <PlusCircle aria-hidden="true" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => toggleBlockStatus(b)}
                                  className="h-9 w-9 p-0"
                                  title={b.estado === 'ACTIVO' ? 'Desactivar bloque' : 'Activar bloque'}
                                  aria-label={b.estado === 'ACTIVO' ? `Desactivar ${b.nombre}` : `Activar ${b.nombre}`}
                                >
                                  {b.estado === 'ACTIVO' ? <ToggleRight aria-hidden="true" /> : <ToggleLeft aria-hidden="true" />}
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => {
                                    setEditingBlock(b);
                                    setBlockForm({
                                      tipo: b.tipo,
                                      codigo: b.codigo,
                                      nombre: b.nombre,
                                      idBloquePadre: b.idBloquePadre ? String(b.idBloquePadre) : '',
                                      orden: b.orden,
                                    });
                                    setBlockDialogOpen(true);
                                  }}
                                  className="h-9 w-9 p-0 hover:text-primary-text"
                                  title={`Editar ${b.nombre}`}
                                  aria-label={`Editar ${b.nombre}`}
                                >
                                  <Pencil aria-hidden="true" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => setDeleteBlockTarget(b)}
                                  className="h-9 w-9 p-0 hover:text-destructive"
                                  title={`Eliminar ${b.nombre}`}
                                  aria-label={`Eliminar ${b.nombre}`}
                                >
                                  <Trash2 aria-hidden="true" />
                                </Button>
                              </div>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* DIALOG DE UNIDAD */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar Unidad' : 'Nueva Unidad'}</DialogTitle>
            <DialogDescription>
              {editing
                ? `Actualice los datos de la unidad ${editing.identificador}.`
                : 'Registre una nueva unidad en la propiedad activa.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label htmlFor="identificador">Identificador *</Label>
              <Input
                id="identificador"
                value={form.identificador}
                onChange={(e) => setForm((f) => ({ ...f, identificador: e.target.value }))}
                placeholder="Ej: 101, 201, Casa 15"
              />
            </div>
            <div className="grid gap-2">
              <Label>Tipo de unidad *</Label>
              <Select
                value={form.idTipoUnidad}
                onValueChange={(v) => setForm((f) => ({ ...f, idTipoUnidad: v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Seleccione el tipo" />
                </SelectTrigger>
                <SelectContent>
                  {tipos.map((t) => (
                    <SelectItem key={t.idTipoUnidad} value={String(t.idTipoUnidad)}>
                      {t.nombre || t.codigo || 'Tipo de Unidad'}{t.codigo && t.nombre ? ` (${t.codigo})` : ''}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <Label>Bloque / Torre / Manzana (Estructura)</Label>
                {loadingBloques && (
                  <span className="text-xs text-muted-foreground animate-pulse">Actualizando estructuras…</span>
                )}
              </div>
              <Select
                value={form.idBloque || 'NONE'}
                onValueChange={(v) => setForm((f) => ({ ...f, idBloque: v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Sin bloque asignado" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NONE">Sin bloque (Inmueble independiente / sin subdivisión)</SelectItem>
                  {bloquesList
                    .filter((b) => b.estado === 'ACTIVO' || (editing && String(editing.idBloque) === String(b.idBloque)))
                    .map((b) => (
                      <SelectItem key={b.idBloque} value={String(b.idBloque)}>
                        {b.tipo}: {b.nombre} {b.codigo ? `(${b.codigo})` : ''}
                        {b.nombreBloquePadre ? ` · [Padre: ${b.nombreBloquePadre}]` : ''}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
              <p className="text-[11.5px] text-muted-foreground">
                Opcional para casas independientes o inmuebles unibloque. Recomendado para apartamentos en torres, etapas o manzanas.
              </p>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="piso">Piso</Label>
              <Input
                id="piso"
                maxLength={10}
                value={form.piso}
                onChange={(e) => setForm((f) => ({ ...f, piso: e.target.value }))}
                placeholder="Ej: 12, PH, S1"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="areaM2">Área (m²)</Label>
                <Input
                  id="areaM2"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.areaM2}
                  onChange={(e) => setForm((f) => ({ ...f, areaM2: e.target.value }))}
                  placeholder="Ej: 70"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="coef">Coef. copropiedad</Label>
                <Input
                  id="coef"
                  type="number"
                  min="0"
                  step="0.0001"
                  value={form.coeficienteCopropiedad}
                  onChange={(e) => setForm((f) => ({ ...f, coeficienteCopropiedad: e.target.value }))}
                  placeholder="Ej: 0.0035"
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={guardarUnidad} disabled={saving}>
              {saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear unidad'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* DIALOG DE BLOQUE / ESTRUCTURA */}
      <Dialog open={blockDialogOpen} onOpenChange={setBlockDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editingBlock ? 'Editar Estructura' : 'Nueva Estructura / Bloque'}
            </DialogTitle>
            <DialogDescription>
              {editingBlock
                ? `Modifique los datos de ${editingBlock.nombre}.`
                : 'Defina un nuevo bloque, torre, etapa, manzana o piso para la copropiedad.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs font-semibold">Tipo de Estructura *</Label>
                <Select
                  value={blockForm.tipo}
                  onValueChange={(val) => setBlockForm((f) => ({ ...f, tipo: val }))}
                >
                  <SelectTrigger className="h-9 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="TORRE">Torre</SelectItem>
                    <SelectItem value="BLOQUE">Bloque</SelectItem>
                    <SelectItem value="ETAPA">Etapa</SelectItem>
                    <SelectItem value="MANZANA">Manzana</SelectItem>
                    <SelectItem value="PISO">Piso</SelectItem>
                    <SelectItem value="SECTOR">Sector</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="block-code" className="text-xs font-semibold">Código *</Label>
                <Input
                  id="block-code"
                  placeholder="Ej: T1, B-A, MZ-1"
                  value={blockForm.codigo}
                  onChange={(e) => setBlockForm((f) => ({ ...f, codigo: e.target.value.toUpperCase() }))}
                  className="h-9 text-sm"
                  required
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="block-name" className="text-xs font-semibold">Nombre descriptivo *</Label>
              <Input
                id="block-name"
                placeholder="Ej: Torre 1, Bloque Norte, Manzana A"
                value={blockForm.nombre}
                onChange={(e) => setBlockForm((f) => ({ ...f, nombre: e.target.value }))}
                className="h-9 text-sm"
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs font-semibold">Estructura Padre (Opcional)</Label>
              <Select
                value={blockForm.idBloquePadre}
                onValueChange={(val) => setBlockForm((f) => ({ ...f, idBloquePadre: val }))}
              >
                <SelectTrigger className="h-9 text-sm">
                  <SelectValue placeholder="Ninguna (Nivel superior)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NONE">Ninguna (Nivel superior)</SelectItem>
                  {bloquesList
                    .filter((b) => !editingBlock || b.idBloque !== editingBlock.idBloque)
                    .map((b) => (
                      <SelectItem key={b.idBloque} value={String(b.idBloque)}>
                        {b.tipo}: {b.nombre} ({b.codigo})
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
              <p className="text-[11.5px] text-muted-foreground">
                Útil para jerarquías como Torres conteniendo Pisos, o Etapas conteniendo Manzanas.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="block-order" className="text-xs font-semibold">Orden de presentación</Label>
              <Input
                id="block-order"
                type="number"
                min="1"
                value={blockForm.orden}
                onChange={(e) => setBlockForm((f) => ({ ...f, orden: e.target.value }))}
                className="h-9 text-sm"
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setBlockDialogOpen(false)} disabled={savingBlock}>
              Cancelar
            </Button>
            <Button onClick={guardarBloque} disabled={savingBlock}>
              {savingBlock ? 'Guardando…' : editingBlock ? 'Guardar cambios' : 'Crear estructura'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* CONFIRMACIÓN DE ELIMINACIÓN DE BLOQUE */}
      {deleteBlockTarget && (
        <Dialog open={Boolean(deleteBlockTarget)} onOpenChange={(open) => { if (!open) setDeleteBlockTarget(null); }}>
          <DialogContent className="sm:max-w-sm">
            <DialogHeader>
              <DialogTitle className="text-base font-bold text-destructive flex items-center gap-2">
                <AlertTriangle className="h-5 w-5" aria-hidden="true" />
                Eliminar Estructura
              </DialogTitle>
              <DialogDescription className="text-xs">
                ¿Está seguro de eliminar la estructura{' '}
                <strong className="text-foreground">{deleteBlockTarget.nombre}</strong> ({deleteBlockTarget.codigo})?
                <br /><br />
                Esta acción solo se completará si no tiene sub-estructuras ni unidades asociadas.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter className="gap-2">
              <Button variant="outline" size="sm" onClick={() => setDeleteBlockTarget(null)}>
                Cancelar
              </Button>
              <Button variant="destructive" size="sm" onClick={confirmDeleteBlock}>
                Eliminar
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {/* MODAL DE CONFIGURACIÓN OPERATIVA DE PROPIEDAD (GAP-CFG-07) */}
      {configModalOpen && activePropertyId && (
        <PropertyConfigModal
          propertyId={activePropertyId}
          propertyName={tenant.activePropertyName || `Propiedad #${activePropertyId}`}
          isOpen={configModalOpen}
          onClose={() => setConfigModalOpen(false)}
        />
      )}
    </PageContainer>
  );
}