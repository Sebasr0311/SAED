import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { toast } from 'sonner';
import LocationSelector from '../components/ui/LocationSelector';

export default function SuperAdminOrganizacionesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [search, setSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    nombre: '',
    identificacionFiscal: '',
    emailContacto: '',
    telefonoContacto: '',
    direccion: '',
    departamento: 'Bogotá D.C.',
    ciudad: 'Bogotá',
    pais: 'Colombia',
  });

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/organizations');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar organizaciones');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const filteredItems = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(
      (o) =>
        (o.nombre && o.nombre.toLowerCase().includes(q)) ||
        (o.identificacionFiscal && o.identificacionFiscal.toLowerCase().includes(q)) ||
        (o.emailContacto && o.emailContacto.toLowerCase().includes(q)) ||
        (o.ciudad && o.ciudad.toLowerCase().includes(q))
    );
  }, [items, search]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.nombre.trim() || !form.identificacionFiscal.trim() || !form.emailContacto.trim()) {
      toast.error('Por favor completa los campos obligatorios (*)');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/organizations', {
        nombre: form.nombre.trim(),
        identificacionFiscal: form.identificacionFiscal.trim(),
        emailContacto: form.emailContacto.trim(),
        telefonoContacto: form.telefonoContacto?.trim() || null,
        direccion: form.direccion?.trim() || null,
        ciudad: form.ciudad || 'Bogotá',
      });
      toast.success('Organización creada exitosamente');
      setShowModal(false);
      setForm({
        nombre: '',
        identificacionFiscal: '',
        emailContacto: '',
        telefonoContacto: '',
        direccion: '',
        departamento: 'Bogotá D.C.',
        ciudad: 'Bogotá',
        pais: 'Colombia',
      });
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al crear la organización');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-2xl">domain</span>
            Organizaciones SaaS
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Empresas administradoras y constructoras de copropiedades clientes de SAED.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2 shrink-0">
          <span className="material-symbols-outlined text-sm">add_business</span>
          Nueva Organización
        </Button>
      </div>

      {/* Barra de Filtros */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
            search
          </span>
          <Input
            id="org-search-input"
            type="search"
            placeholder="Buscar por nombre, NIT, email o ciudad…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 text-sm"
          />
        </div>
        <div className="text-xs text-muted-foreground ml-auto font-medium">
          {filteredItems.length} {filteredItems.length === 1 ? 'organización' : 'organizaciones'}
        </div>
      </div>

      {/* Listado */}
      <Card className="border-border/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">Listado de Organizaciones Registradas</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">domain_disabled</span>
              <p className="text-sm font-medium">
                {search ? 'No se encontraron organizaciones con ese criterio.' : 'No hay organizaciones registradas en la plataforma.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border text-muted-foreground text-xs font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Nombre Comercial</th>
                    <th className="py-3 px-4">NIT / ID Fiscal</th>
                    <th className="py-3 px-4">Contacto</th>
                    <th className="py-3 px-4">Ubicación</th>
                    <th className="py-3 px-4 text-center">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredItems.map((org) => (
                    <tr key={org.id || org.idOrganizacion} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">#{org.id || org.idOrganizacion}</td>
                      <td className="py-3.5 px-4 font-semibold text-foreground">{org.nombre}</td>
                      <td className="py-3.5 px-4 text-muted-foreground font-mono text-xs">{org.identificacionFiscal || org.nit || '—'}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col">
                          <span className="text-foreground text-xs">{org.emailContacto || org.email || '—'}</span>
                          {org.telefonoContacto && (
                            <span className="text-muted-foreground text-[11px] font-mono">{org.telefonoContacto}</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-xs text-muted-foreground">
                        {org.ciudad ? `${org.ciudad}, ${org.pais || 'Colombia'}` : org.pais || 'Colombia'}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <Badge
                          variant={org.estado === 'ACTIVA' ? 'default' : 'secondary'}
                          className={
                            org.estado === 'ACTIVA'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {org.estado || 'ACTIVA'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Modal Accesible con Radix UI Dialog */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold">
              <span className="material-symbols-outlined text-primary">add_business</span>
              Registrar Organización SaaS
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="org-nombre" className="text-xs font-semibold uppercase text-muted-foreground">
                Nombre Comercial / Razón Social *
              </Label>
              <Input
                id="org-nombre"
                required
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej. Inversiones Residenciales S.A.S."
                className="text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="org-nit" className="text-xs font-semibold uppercase text-muted-foreground">
                NIT / Identificación Fiscal *
              </Label>
              <Input
                id="org-nit"
                required
                value={form.identificacionFiscal}
                onChange={(e) => setForm({ ...form, identificacionFiscal: e.target.value })}
                placeholder="Ej. 901234567-8"
                className="text-sm font-mono"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="org-email" className="text-xs font-semibold uppercase text-muted-foreground">
                  Email de Contacto *
                </Label>
                <Input
                  id="org-email"
                  type="email"
                  required
                  value={form.emailContacto}
                  onChange={(e) => setForm({ ...form, emailContacto: e.target.value })}
                  placeholder="admin@empresa.com"
                  className="text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="org-tel" className="text-xs font-semibold uppercase text-muted-foreground">
                  Teléfono
                </Label>
                <Input
                  id="org-tel"
                  value={form.telefonoContacto}
                  onChange={(e) => setForm({ ...form, telefonoContacto: e.target.value })}
                  placeholder="+57 300 123 4567"
                  className="text-sm"
                />
              </div>
            </div>

            <LocationSelector
              idPrefix="org"
              pais={form.pais}
              departamento={form.departamento}
              ciudad={form.ciudad}
              onChange={({ pais, departamento, ciudad }) =>
                setForm((prev) => ({ ...prev, pais, departamento, ciudad }))
              }
            />

            <div className="space-y-1.5">
              <Label htmlFor="org-dir" className="text-xs font-semibold uppercase text-muted-foreground">
                Dirección Comercial
              </Label>
              <Input
                id="org-dir"
                value={form.direccion}
                onChange={(e) => setForm({ ...form, direccion: e.target.value })}
                placeholder="Calle 100 # 15-20 Of. 401"
                className="text-sm"
              />
            </div>

            <DialogFooter className="pt-3 gap-2">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? 'Guardando…' : 'Crear Organización'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
