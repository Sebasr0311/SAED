import { useState, useMemo } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent } from '../components/ui/card.tsx';
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
import LocationSelector from '../components/ui/LocationSelector';
import { findDepartamentoByCiudad } from '../lib/colombiaData';

/**
 * PropiedadesPage 2.0 — Propiedades globales y por organización.
 * Consume /api/v1/properties (GET/POST/PUT) con useTenantApi + catálogos.
 */
const ESTADO_BADGE = {
  ACTIVA: 'default',
  SUSPENDIDA: 'warning',
  INACTIVA: 'secondary',
};

const emptyForm = {
  idOrganizacion: '',
  idTipoPropiedad: '',
  nombre: '',
  direccion: '',
  departamento: 'Bogotá D.C.',
  ciudad: 'Bogotá',
  pais: 'Colombia',
  tipoOcupacionPredominante: 'MIXTA',
};

export default function PropiedadesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/properties'),
    [tenant.activeAssignmentId]
  );
  const { data: orgsData } = useFetch(
    () => tenantApi.get('/organizations'),
    [tenant.activeAssignmentId]
  );
  const { data: tiposData } = useFetch(
    () => tenantApi.get('/tipos-propiedad'),
    [tenant.activeAssignmentId]
  );

  // Normalización resiliente de catálogos y entidades
  const propiedades = useMemo(() => {
    const raw = Array.isArray(data?.items) ? data.items : (Array.isArray(data) ? data : (data?.data || []));
    return raw.map((p) => ({
      ...p,
      id: p.id ?? p.idPropiedad ?? p.ID_PROPIEDAD,
      idOrganizacion: p.idOrganizacion ?? p.ID_ORGANIZACION,
      idTipoPropiedad: p.idTipoPropiedad ?? p.ID_TIPO_PROPIEDAD,
      nombre: p.nombre ?? p.NOMBRE ?? '',
      direccion: p.direccion ?? p.DIRECCION ?? '',
      ciudad: p.ciudad ?? p.CIUDAD ?? '',
      estado: p.estado ?? p.ESTADO ?? 'ACTIVA',
      tipoPropiedadNombre: p.tipoPropiedadNombre ?? p.TIPO_PROPIEDAD_NOMBRE,
      tipoPropiedadCodigo: p.tipoPropiedadCodigo ?? p.TIPO_PROPIEDAD_CODIGO,
      organizacionNombre: p.organizacionNombre ?? p.ORGANIZACION_NOMBRE,
      tipoOcupacionPredominante: p.tipoOcupacionPredominante ?? p.TIPO_OCUPACION_PREDOMINANTE ?? 'MIXTA',
    }));
  }, [data]);

  const organizaciones = useMemo(() => {
    const raw = Array.isArray(orgsData?.items) ? orgsData.items : (Array.isArray(orgsData) ? orgsData : (orgsData?.data || []));
    return raw.map((o) => ({
      ...o,
      id: o.id ?? o.idOrganizacion ?? o.ID_ORGANIZACION,
      nombre: o.nombre ?? o.NOMBRE ?? `Organización ${o.id ?? o.idOrganizacion}`,
    }));
  }, [orgsData]);

  const tipos = useMemo(() => {
    const raw = Array.isArray(tiposData?.items) ? tiposData.items : (Array.isArray(tiposData) ? tiposData : (tiposData?.data || []));
    return raw.map((t) => ({
      ...t,
      idTipoPropiedad: t.idTipoPropiedad ?? t.ID_TIPO_PROPIEDAD ?? t.id,
      codigo: t.codigo ?? t.CODIGO ?? '',
      nombre: t.nombre ?? t.NOMBRE ?? t.codigo ?? 'Tipo de Propiedad',
    }));
  }, [tiposData]);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Asegurar que la organización y el tipo de la propiedad en edición siempre existan en las opciones del selector
  const availableOrgs = useMemo(() => {
    const list = [...organizaciones];
    if (editing && editing.idOrganizacion && !list.some((o) => String(o.id) === String(editing.idOrganizacion))) {
      list.push({
        id: editing.idOrganizacion,
        nombre: editing.organizacionNombre || `Organización #${editing.idOrganizacion}`,
      });
    }
    if (list.length === 0 && tenant.activeOrgId) {
      list.push({
        id: tenant.activeOrgId,
        nombre: `Organización activa #${tenant.activeOrgId}`,
      });
    }
    return list;
  }, [organizaciones, editing, tenant.activeOrgId]);

  const availableTipos = useMemo(() => {
    const list = [...tipos];
    if (editing && editing.idTipoPropiedad && !list.some((t) => String(t.idTipoPropiedad) === String(editing.idTipoPropiedad))) {
      list.push({
        idTipoPropiedad: editing.idTipoPropiedad,
        codigo: editing.tipoPropiedadCodigo || 'TIPO',
        nombre: editing.tipoPropiedadNombre || `Tipo #${editing.idTipoPropiedad}`,
      });
    }
    return list;
  }, [tipos, editing]);

  function handleOpenCreate() {
    setEditing(null);
    const defaultOrgId = tenant.activeOrgId != null ? String(tenant.activeOrgId) : (organizaciones[0]?.id != null ? String(organizaciones[0].id) : '');
    const defaultTipoId = tipos[0]?.idTipoPropiedad != null ? String(tipos[0].idTipoPropiedad) : '';
    setForm({
      ...emptyForm,
      idOrganizacion: defaultOrgId,
      idTipoPropiedad: defaultTipoId,
    });
    setDialogOpen(true);
  }

  function handleOpenEdit(prop) {
    setEditing(prop);
    const inferredDept = findDepartamentoByCiudad(prop.ciudad);
    setForm({
      idOrganizacion: prop.idOrganizacion != null ? String(prop.idOrganizacion) : '',
      idTipoPropiedad: prop.idTipoPropiedad != null ? String(prop.idTipoPropiedad) : '',
      nombre: prop.nombre || '',
      direccion: prop.direccion || '',
      departamento: inferredDept,
      ciudad: prop.ciudad || 'Bogotá',
      pais: prop.pais || 'Colombia',
      tipoOcupacionPredominante: prop.tipoOcupacionPredominante || 'MIXTA',
    });
    setDialogOpen(true);
  }

  async function guardar() {
    if (!form.nombre.trim()) {
      toast.error('El nombre de la propiedad es obligatorio');
      return;
    }
    if (!form.direccion.trim()) {
      toast.error('La dirección de la propiedad es obligatoria');
      return;
    }
    if (!form.ciudad.trim()) {
      toast.error('La ciudad de la propiedad es obligatoria');
      return;
    }
    if (!form.idOrganizacion) {
      toast.error('Seleccione una organización para la propiedad');
      return;
    }
    if (!form.idTipoPropiedad) {
      toast.error('Seleccione un tipo de propiedad válido');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        idOrganizacion: Number(form.idOrganizacion),
        idTipoPropiedad: Number(form.idTipoPropiedad),
        nombre: form.nombre.trim(),
        direccion: form.direccion.trim(),
        ciudad: form.ciudad.trim(),
        tipoOcupacionPredominante: form.tipoOcupacionPredominante || 'MIXTA',
      };

      const propId = editing ? (editing.id ?? editing.idPropiedad ?? editing.ID_PROPIEDAD) : null;
      if (editing && propId) {
        await tenantApi.put(`/properties/${propId}`, payload);
        toast.success('Propiedad actualizada exitosamente');
      } else {
        await tenantApi.post('/properties', payload);
        toast.success('Propiedad creada exitosamente');
      }
      setDialogOpen(false);
      refetch();
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.mensaje ||
        err?.message ||
        'No se pudo guardar la propiedad';
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  }

  // Filtrado de propiedades por búsqueda y estado
  const filteredPropiedades = useMemo(() => {
    return propiedades.filter((p) => {
      const term = searchTerm.trim().toLowerCase();
      const matchesSearch =
        !term ||
        p.nombre.toLowerCase().includes(term) ||
        p.ciudad.toLowerCase().includes(term) ||
        p.direccion.toLowerCase().includes(term) ||
        (p.organizacionNombre && p.organizacionNombre.toLowerCase().includes(term));
      const matchesStatus = statusFilter === 'ALL' || p.estado === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [propiedades, searchTerm, statusFilter]);


  return (
    <div className="propiedades-page space-y-6 animate-fadeIn">
      <PageHeader
        title="Propiedades Globales"
        subtitle="Gestión y supervisión centralizada de copropiedades, edificios y conjuntos residenciales"
      >
        <Button onClick={handleOpenCreate} className="gap-1.5 shadow-sm">
          <span className="material-symbols-outlined text-base">add</span>
          <span>Nueva Propiedad</span>
        </Button>
      </PageHeader>

      {/* Tarjetas resumen KPI */}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card className="border-border/60 shadow-sm transition-all hover:shadow-md">
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-xl bg-primary/10 p-3 text-primary">
              <span className="material-symbols-outlined text-2xl">apartment</span>
            </div>
            <div>
              <p className="text-2xl font-bold tracking-tight text-foreground">{propiedades.length}</p>
              <p className="text-xs font-medium text-muted-foreground uppercase">Propiedades Totales</p>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border/60 shadow-sm transition-all hover:shadow-md">
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-xl bg-emerald-500/10 p-3 text-emerald-600 dark:text-emerald-400">
              <span className="material-symbols-outlined text-2xl">domain</span>
            </div>
            <div>
              <p className="text-2xl font-bold tracking-tight text-foreground">{organizaciones.length}</p>
              <p className="text-xs font-medium text-muted-foreground uppercase">Organizaciones</p>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border/60 shadow-sm transition-all hover:shadow-md">
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-xl bg-blue-500/10 p-3 text-blue-600 dark:text-blue-400">
              <span className="material-symbols-outlined text-2xl">category</span>
            </div>
            <div>
              <p className="text-2xl font-bold tracking-tight text-foreground">{tipos.length}</p>
              <p className="text-xs font-medium text-muted-foreground uppercase">Tipos de Inmueble</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Contenedor principal con filtros y tabla */}
      <Card className="border-border/70 shadow-sm">
        <CardContent className="p-6 space-y-4">
          {/* Barra de búsqueda y filtros */}
          <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
            <div className="relative flex-1 max-w-md">
              <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-lg">
                search
              </span>
              <Input
                type="text"
                placeholder="Buscar por nombre, ciudad o dirección..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9 h-9 text-sm"
              />
            </div>
            <div className="flex items-center gap-2">
              <Label className="text-xs text-muted-foreground whitespace-nowrap">Estado:</Label>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="h-9 w-36 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">Todos los estados</SelectItem>
                  <SelectItem value="ACTIVA">Activas</SelectItem>
                  <SelectItem value="SUSPENDIDA">Suspendidas</SelectItem>
                  <SelectItem value="INACTIVA">Inactivas</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Tabla de propiedades */}
          {loading ? (
            <div className="space-y-2 py-4">
              <Skeleton className="h-10 w-full rounded-md" />
              <Skeleton className="h-12 w-full rounded-md" />
              <Skeleton className="h-12 w-full rounded-md" />
              <Skeleton className="h-12 w-full rounded-md" />
            </div>
          ) : filteredPropiedades.length === 0 ? (
            <div className="py-12 text-center space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">
                apartment
              </span>
              <p className="text-sm font-medium text-foreground">
                {searchTerm || statusFilter !== 'ALL'
                  ? 'No se encontraron propiedades que coincidan con los filtros aplicados.'
                  : 'No hay propiedades registradas en el sistema.'}
              </p>
              {searchTerm && (
                <Button variant="outline" size="sm" onClick={() => setSearchTerm('')} className="mt-2 text-xs">
                  Limpiar búsqueda
                </Button>
              )}
            </div>
          ) : (
            <div className="rounded-lg border border-border/60 overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-muted/40 hover:bg-muted/40">
                    <TableHead className="font-semibold">Propiedad</TableHead>
                    <TableHead className="font-semibold">Tipo</TableHead>
                    <TableHead className="font-semibold">Organización</TableHead>
                    <TableHead className="font-semibold">Ubicación</TableHead>
                    <TableHead className="font-semibold">Ocupación</TableHead>
                    <TableHead className="font-semibold">Estado</TableHead>
                    <TableHead className="text-right font-semibold">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredPropiedades.map((p) => (
                    <TableRow key={p.id} className="transition-colors hover:bg-muted/30">
                      <TableCell>
                        <div className="font-semibold text-foreground">{p.nombre}</div>
                        <div className="text-xs text-muted-foreground">{p.direccion || 'Sin dirección registrada'}</div>
                      </TableCell>
                      <TableCell>
                        <span className="text-sm text-foreground">
                          {p.tipoPropiedadNombre || p.tipoPropiedadCodigo || '—'}
                        </span>
                      </TableCell>
                      <TableCell>
                        <span className="text-sm text-foreground">
                          {p.organizacionNombre || `Org #${p.idOrganizacion}`}
                        </span>
                      </TableCell>
                      <TableCell>
                        <span className="text-sm text-foreground">{p.ciudad || '—'}</span>
                      </TableCell>
                      <TableCell>
                        <span className="text-xs font-medium text-muted-foreground capitalize">
                          {(p.tipoOcupacionPredominante || 'MIXTA').toLowerCase()}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[p.estado] || 'default'} className="text-xs">
                          {p.estado}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleOpenEdit(p)}
                          className="h-8 w-8 p-0 text-muted-foreground hover:text-primary transition-colors"
                          aria-label={`Editar propiedad ${p.nombre}`}
                          title={`Editar propiedad ${p.nombre}`}
                        >
                          <span className="material-symbols-outlined text-base">edit</span>
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

      {/* Modal de Creación / Edición */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold text-foreground">
              {editing ? 'Editar Propiedad' : 'Nueva Propiedad'}
            </DialogTitle>
            <DialogDescription className="text-xs text-muted-foreground">
              {editing
                ? `Modifique los datos de ${editing.nombre || 'la propiedad'}. Todos los campos con * son obligatorios.`
                : 'Complete la información para registrar una nueva propiedad en la organización.'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="prop-org" className="text-xs font-semibold">Organización *</Label>
                <Select
                  value={form.idOrganizacion ? String(form.idOrganizacion) : undefined}
                  onValueChange={(v) => setForm((f) => ({ ...f, idOrganizacion: v }))}
                >
                  <SelectTrigger id="prop-org" className="h-9 text-sm">
                    <SelectValue placeholder="Seleccione organización" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableOrgs.map((o) => (
                      <SelectItem key={o.id} value={String(o.id)}>
                        {o.nombre}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="prop-tipo" className="text-xs font-semibold">Tipo de Propiedad *</Label>
                <Select
                  value={form.idTipoPropiedad ? String(form.idTipoPropiedad) : undefined}
                  onValueChange={(v) => setForm((f) => ({ ...f, idTipoPropiedad: v }))}
                >
                  <SelectTrigger id="prop-tipo" className="h-9 text-sm">
                    <SelectValue placeholder="Seleccione tipo" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableTipos.map((t) => (
                      <SelectItem key={t.idTipoPropiedad} value={String(t.idTipoPropiedad)}>
                        {t.nombre} {t.codigo && t.nombre !== t.codigo ? `(${t.codigo})` : ''}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="prop-nombre" className="text-xs font-semibold">Nombre de la Propiedad *</Label>
              <Input
                id="prop-nombre"
                value={form.nombre}
                onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))}
                placeholder="Ej: Torre Norte, Conjunto Residencial Los Pinos"
                className="h-9 text-sm"
              />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="prop-dir" className="text-xs font-semibold">Dirección *</Label>
              <Input
                id="prop-dir"
                value={form.direccion}
                onChange={(e) => setForm((f) => ({ ...f, direccion: e.target.value }))}
                placeholder="Ej: Carrera 15 # 85-20"
                className="h-9 text-sm"
              />
            </div>

            {/* Selector de País -> Departamento -> Ciudad */}
            <LocationSelector
              idPrefix="prop-modal"
              pais={form.pais}
              departamento={form.departamento}
              ciudad={form.ciudad}
              required
              onChange={({ pais, departamento, ciudad }) =>
                setForm((f) => ({ ...f, pais, departamento, ciudad }))
              }
            />

            <div className="grid gap-1.5">
              <Label htmlFor="prop-ocupacion" className="text-xs font-semibold">Tipo de Ocupación</Label>
              <Select
                value={form.tipoOcupacionPredominante || 'MIXTA'}
                onValueChange={(v) => setForm((f) => ({ ...f, tipoOcupacionPredominante: v }))}
              >
                <SelectTrigger id="prop-ocupacion" className="h-9 text-sm">
                  <SelectValue placeholder="Seleccione ocupación" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RESIDENCIAL">Residencial</SelectItem>
                  <SelectItem value="COMERCIAL">Comercial</SelectItem>
                  <SelectItem value="MIXTA">Mixta</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0 pt-2">
            <Button
              variant="outline"
              onClick={() => setDialogOpen(false)}
              disabled={saving}
              className="text-xs h-9"
            >
              Cancelar
            </Button>
            <Button
              onClick={guardar}
              disabled={saving}
              className="text-xs h-9 gap-1.5"
            >
              {saving ? (
                <>
                  <span className="material-symbols-outlined animate-spin text-sm">progress_activity</span>
                  <span>Guardando...</span>
                </>
              ) : editing ? (
                'Guardar cambios'
              ) : (
                'Crear propiedad'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}