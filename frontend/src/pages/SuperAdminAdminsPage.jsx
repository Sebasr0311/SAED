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

export default function SuperAdminAdminsPage() {
  const [admins, setAdmins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    nombreUsuario: '',
    email: '',
    password: '',
    primerNombre: '',
    primerApellido: '',
    nivel: 'SOPORTE',
  });

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/platform/admins');
      const list = res?.data || res || [];
      setAdmins(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar operadores de plataforma');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const filteredAdmins = useMemo(() => {
    if (!search.trim()) return admins;
    const q = search.toLowerCase();
    return admins.filter(
      (a) =>
        (a.nombreUsuario && a.nombreUsuario.toLowerCase().includes(q)) ||
        (a.email && a.email.toLowerCase().includes(q)) ||
        (a.primerNombre && a.primerNombre.toLowerCase().includes(q)) ||
        (a.primerApellido && a.primerApellido.toLowerCase().includes(q))
    );
  }, [admins, search]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.nombreUsuario.trim() || !form.email.trim() || !form.password.trim()) {
      toast.error('Nombre de usuario, email y contraseña son obligatorios');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/platform/admins', form);
      toast.success('Operador SUPERADMIN registrado exitosamente');
      setShowModal(false);
      setForm({
        nombreUsuario: '',
        email: '',
        password: '',
        primerNombre: '',
        primerApellido: '',
        nivel: 'SOPORTE',
      });
      loadData();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || 'Error al crear operador de plataforma');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(idUsuario, currentStatus) {
    const nextStatus = currentStatus === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await api.put(`/platform/admins/${idUsuario}/estado`, { estado: nextStatus });
      toast.success(`Operador ${nextStatus === 'ACTIVO' ? 'activado' : 'desactivado'} exitosamente`);
      loadData();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || 'Error al cambiar el estado del operador');
    }
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-2xl">admin_panel_settings</span>
            Operadores de Plataforma SAED
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Usuarios con credenciales de nivel SUPERADMIN y alcance GLOBAL sobre la plataforma.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2 shrink-0">
          <span className="material-symbols-outlined text-sm">person_add</span>
          Nuevo Operador
        </Button>
      </div>

      {/* Barra de Filtro */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
            search
          </span>
          <Input
            id="admin-search-input"
            type="search"
            placeholder="Buscar por usuario, email o nombre…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 text-sm"
          />
        </div>
        <div className="text-xs text-muted-foreground ml-auto font-medium">
          {filteredAdmins.length} {filteredAdmins.length === 1 ? 'operador' : 'operadores'}
        </div>
      </div>

      {/* Tabla */}
      <Card className="border-border/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">Administradores Globales Activos</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : filteredAdmins.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">person_off</span>
              <p className="text-sm font-medium">
                {search ? 'No se encontraron operadores con ese criterio.' : 'No hay operadores registrados.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border text-muted-foreground text-xs font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Usuario</th>
                    <th className="py-3 px-4">Nombre Completo</th>
                    <th className="py-3 px-4">Email</th>
                    <th className="py-3 px-4">Nivel</th>
                    <th className="py-3 px-4 text-center">Estado</th>
                    <th className="py-3 px-4 text-right">Acción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredAdmins.map((adm) => (
                    <tr key={adm.idUsuario || adm.ID_USUARIO} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">
                        #{adm.idUsuario || adm.ID_USUARIO}
                      </td>
                      <td className="py-3.5 px-4 font-semibold text-foreground font-mono text-xs">
                        {adm.nombreUsuario || adm.NOMBRE_USUARIO}
                      </td>
                      <td className="py-3.5 px-4 text-foreground text-xs">
                        {adm.primerNombre || adm.PRIMER_NOMBRE} {adm.primerApellido || adm.PRIMER_APELLIDO}
                      </td>
                      <td className="py-3.5 px-4 text-muted-foreground text-xs font-mono">
                        {adm.email || adm.EMAIL}
                      </td>
                      <td className="py-3.5 px-4">
                        <Badge variant="outline" className="font-mono text-xs">
                          {adm.nivel || 'GLOBAL'}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <Badge
                          variant={(adm.estado || adm.ESTADO) === 'ACTIVO' ? 'default' : 'secondary'}
                          className={
                            (adm.estado || adm.ESTADO) === 'ACTIVO'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {adm.estado || adm.ESTADO || 'ACTIVO'}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs h-8 px-2"
                          onClick={() => handleToggleStatus(adm.idUsuario || adm.ID_USUARIO, adm.estado || adm.ESTADO)}
                        >
                          {(adm.estado || adm.ESTADO) === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Modal Crear Operador */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold">
              <span className="material-symbols-outlined text-primary">person_add</span>
              Registrar Operador SUPERADMIN
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="adm-usuario" className="text-xs font-semibold uppercase text-muted-foreground">
                Nombre de Usuario *
              </Label>
              <Input
                id="adm-usuario"
                required
                value={form.nombreUsuario}
                onChange={(e) => setForm({ ...form, nombreUsuario: e.target.value })}
                placeholder="Ej. operador_soporte1"
                className="text-sm font-mono"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="adm-email" className="text-xs font-semibold uppercase text-muted-foreground">
                Correo Electrónico *
              </Label>
              <Input
                id="adm-email"
                type="email"
                required
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                placeholder="operador@saed.com"
                className="text-sm font-mono"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="adm-pnombre" className="text-xs font-semibold uppercase text-muted-foreground">
                  Primer Nombre
                </Label>
                <Input
                  id="adm-pnombre"
                  value={form.primerNombre}
                  onChange={(e) => setForm({ ...form, primerNombre: e.target.value })}
                  placeholder="Carlos"
                  className="text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="adm-papellido" className="text-xs font-semibold uppercase text-muted-foreground">
                  Primer Apellido
                </Label>
                <Input
                  id="adm-papellido"
                  value={form.primerApellido}
                  onChange={(e) => setForm({ ...form, primerApellido: e.target.value })}
                  placeholder="Méndez"
                  className="text-sm"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="adm-nivel" className="text-xs font-semibold uppercase text-muted-foreground">
                  Nivel de Operación
                </Label>
                <select
                  id="adm-nivel"
                  value={form.nivel}
                  onChange={(e) => setForm({ ...form, nivel: e.target.value })}
                  className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="SOPORTE">SOPORTE</option>
                  <option value="AUDITOR">AUDITOR</option>
                  <option value="ADMIN_GLOBAL">ADMIN_GLOBAL</option>
                </select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="adm-pass" className="text-xs font-semibold uppercase text-muted-foreground">
                  Contraseña Inicial *
                </Label>
                <Input
                  id="adm-pass"
                  type="password"
                  required
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  placeholder="••••••••"
                  className="text-sm"
                />
              </div>
            </div>

            <DialogFooter className="pt-3 gap-2">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? 'Guardando…' : 'Crear Operador'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
