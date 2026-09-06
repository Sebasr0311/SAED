import { useMemo, useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
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

/**
 * UnidadesPage 2.0 — jerarquía Propiedad -> Bloque -> Unidad.
 *
 * Consume /units (API 2.0 enriquecida: tipo, bloque, area, estado) con
 * useTenantApi (X-Assignment-Id) para que el RLS filtre por tenant.
 */
const ESTADO_BADGE = {
  ACTIVA: 'default',
  INACTIVA: 'secondary',
  EN_CONSTRUCCION: 'outline',
};

const emptyForm = {
  identificador: '',
  idBloque: '',
  idTipoUnidad: '',
  areaM2: '',
  coeficienteCopropiedad: '',
};

export default function UnidadesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );
  const { data: tiposUnidad } = useFetch(
    () => tenantApi.get('/tipos-unidad'),
    [tenant.activeAssignmentId]
  );
  const { data: bloques } = useFetch(
    () => tenantApi.get('/bloques'),
    [tenant.activeAssignmentId]
  );

  const unidades = Array.isArray(data) ? data : data?.items || [];
  const tipos = (tiposUnidad?.items || (Array.isArray(tiposUnidad) ? tiposUnidad : [])).map((t) => ({
    idTipoUnidad: t.idTipoUnidad ?? t.ID_TIPO_UNIDAD ?? t.id,
    nombre: t.nombre ?? t.NOMBRE ?? '',
    codigo: t.codigo ?? t.CODIGO ?? '',
  }));
  const bloquesList = (bloques?.items || (Array.isArray(bloques) ? bloques : [])).map((b) => ({
    idBloque: b.idBloque ?? b.ID_BLOQUE ?? b.id,
    nombre: b.nombre ?? b.NOMBRE ?? '',
    codigo: b.codigo ?? b.CODIGO ?? '',
  }));

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  async function guardar() {
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
        idPropiedad: tenant.activePropertyId,
        idTipoUnidad: Number(form.idTipoUnidad),
        identificador: form.identificador.trim(),
        idBloque: form.idBloque ? Number(form.idBloque) : null,
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
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo guardar la unidad');
    } finally {
      setSaving(false);
    }
  }

  const total = unidades.length;
  const activas = unidades.filter((u) => u.estado === 'ACTIVA').length;

  const stats = useMemo(
    () => [
      { label: 'Unidades', value: total, icon: 'apartment' },
      { label: 'Activas', value: activas, icon: 'check_circle' },
      { label: 'Tipos', value: tipos.length, icon: 'category' },
      { label: 'Bloques', value: bloquesList.length, icon: 'layers' },
    ],
    [total, activas, tipos.length, bloquesList.length]
  );

  return (
    <div className="unidades-page space-y-6">
      <PageHeader
        title="Unidades"
        subtitle="Jerarquía Propiedad → Bloque → Unidad del tenant activo"
      >
        <Button onClick={() => { setEditing(null); setForm(emptyForm); setDialogOpen(true); }}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nueva Unidad
        </Button>
      </PageHeader>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((s) => (
          <Card key={s.label}>
            <CardContent className="flex items-center gap-3 pt-6">
              <div className="rounded-lg bg-primary/10 p-2.5 text-primary">
                <span className="material-symbols-outlined">{s.icon}</span>
              </div>
              <div>
                <p className="text-2xl font-bold">{s.value}</p>
                <p className="text-sm text-muted-foreground">{s.label}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Listado de Unidades</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : unidades.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">
              No hay unidades en este tenant todavía.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Identificador</TableHead>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Bloque</TableHead>
                    <TableHead>Área (m²)</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {unidades.map((u) => (
                    <TableRow key={u.id}>
                      <TableCell className="font-medium">{u.identificador}</TableCell>
                      <TableCell>
                        {u.tipoUnidadNombre || u.tipoUnidadCodigo || '—'}
                      </TableCell>
                      <TableCell>
                        {u.bloqueNombre || u.bloqueCodigo || '—'}
                      </TableCell>
                      <TableCell>
                        {u.areaM2 != null ? formatMiles(u.areaM2) : '—'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[u.estado] || 'default'}>
                          {u.estado || '—'}
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
                              idBloque: u.idBloque != null ? String(u.idBloque) : (u.ID_BLOQUE != null ? String(u.ID_BLOQUE) : ''),
                              idTipoUnidad: u.idTipoUnidad != null ? String(u.idTipoUnidad) : (u.ID_TIPO_UNIDAD != null ? String(u.ID_TIPO_UNIDAD) : ''),
                              areaM2: u.areaM2 != null ? String(u.areaM2) : (u.AREA_M2 != null ? String(u.AREA_M2) : ''),
                              coeficienteCopropiedad: u.coeficienteCopropiedad != null ? String(u.coeficienteCopropiedad) : (u.COEFICIENTE_COPROPIEDAD != null ? String(u.COEFICIENTE_COPROPIEDAD) : ''),
                            });
                            setDialogOpen(true);
                          }}
                          aria-label={`Editar ${u.identificador}`}
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
                placeholder="Ej: 101, 201, Apto 3"
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
              <Label>Bloque</Label>
              <Select
                value={form.idBloque}
                onValueChange={(v) => setForm((f) => ({ ...f, idBloque: v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Sin bloque" />
                </SelectTrigger>
                <SelectContent>
                  {bloquesList.map((b) => (
                    <SelectItem key={b.idBloque} value={String(b.idBloque)}>
                      {b.nombre || b.codigo}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
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
            <Button onClick={guardar} disabled={saving}>
              {saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear unidad'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}