import { useEffect, useState, useMemo } from 'react';
import { Search, ShieldCheck, UserPlus, UserX } from 'lucide-react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input as SearchInput } from '../components/ui/input.tsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from '../components/ui/dialog.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { toast } from 'sonner';

const EMPTY_FORM = {
  nombreUsuario: '',
  email: '',
  password: '',
  primerNombre: '',
  primerApellido: '',
  nivel: 'SOPORTE',
};

export default function SuperAdminAdminsPage() {
  const [admins, setAdmins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formErrors, setFormErrors] = useState({});
  const [confirmTarget, setConfirmTarget] = useState(null);

  const [form, setForm] = useState(EMPTY_FORM);

  async function loadData() {
    try {
      setLoading(true);
      setLoadError(null);
      const res = await api.get('/platform/admins');
      const list = res?.data || res || [];
      setAdmins(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar operadores de plataforma');
      setLoadError(err?.message || 'No fue posible consultar los operadores de plataforma.');
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

  function setField(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
    if (formErrors[field]) setFormErrors((e) => ({ ...e, [field]: undefined }));
  }

  async function handleCreate(e) {
    e.preventDefault();
    const errors = {};
    if (!form.nombreUsuario.trim()) errors.nombreUsuario = 'El nombre de usuario es obligatorio.';
    if (!form.email.trim()) errors.email = 'El correo es obligatorio.';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Ingresa un correo válido.';
    if (!form.password.trim()) errors.password = 'La contraseña inicial es obligatoria.';
    if (Object.keys(errors).length) {
      setFormErrors(errors);
      toast.error('Nombre de usuario, email y contraseña son obligatorios');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/platform/admins', form);
      toast.success('Operador SUPERADMIN registrado exitosamente');
      setShowModal(false);
      setForm(EMPTY_FORM);
      setFormErrors({});
      loadData();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || 'Error al crear operador de plataforma');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(idUsuario, currentStatus) {
    if (Number(idUsuario) === 1) {
      toast.error('El Super Administrador principal (Root) está protegido y no puede ser desactivado.');
      return;
    }
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
    <PageContainer>
      <PageHeader
        title="Operadores de plataforma SAED"
        subtitle="Usuarios con credenciales SUPERADMIN y alcance global sobre la plataforma."
        action={
          <Button onClick={() => setShowModal(true)} className="min-h-10">
            <UserPlus aria-hidden="true" />
            Nuevo operador
          </Button>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:max-w-sm">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <SearchInput
            id="admin-search-input"
            type="search"
            aria-label="Buscar operadores"
            placeholder="Buscar por usuario, email o nombre…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 pl-9 text-sm"
          />
        </div>
        <div className="text-xs font-semibold text-text-secondary sm:ml-auto">
          <span className="app-numeral">{filteredAdmins.length}</span>{' '}
          {filteredAdmins.length === 1 ? 'operador' : 'operadores'}
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-[15px]">Administradores globales</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2" role="status" aria-busy="true" aria-label="Cargando operadores">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-11 w-full" />
              ))}
            </div>
          ) : loadError ? (
            <ErrorState title="No se pudo cargar la lista" message={loadError} onRetry={loadData} />
          ) : filteredAdmins.length === 0 ? (
            <EmptyState
              icon={UserX}
              title={search ? 'Sin coincidencias' : 'No hay operadores registrados'}
              subtitle={
                search
                  ? 'No se encontraron operadores con ese criterio.'
                  : 'Registra un operador para delegar soporte o auditoría de la plataforma.'
              }
            >
              {search ? (
                <Button variant="outline" size="sm" className="mt-3" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              ) : (
                <Button size="sm" className="mt-3" onClick={() => setShowModal(true)}>
                  <UserPlus aria-hidden="true" />
                  Nuevo operador
                </Button>
              )}
            </EmptyState>
          ) : (
            <div className="table-container max-h-[70vh] overflow-y-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Usuario</th>
                    <th scope="col">Nombre completo</th>
                    <th scope="col">Email</th>
                    <th scope="col">Nivel</th>
                    <th scope="col" className="text-center">Estado</th>
                    <th scope="col" className="text-right">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAdmins.map((adm) => {
                    const id = adm.idUsuario || adm.ID_USUARIO;
                    const usuario = adm.nombreUsuario || adm.NOMBRE_USUARIO;
                    const estado = adm.estado || adm.ESTADO;
                    const protegido = Number(id) === 1 || usuario === 'admin_global';
                    return (
                      <tr key={id}>
                        <td className="font-mono text-xs text-text-secondary tabular-nums">#{id}</td>
                        <td className="font-mono text-xs font-semibold text-on-surface">{usuario}</td>
                        <td className="text-xs text-on-surface">
                          {adm.primerNombre || adm.PRIMER_NOMBRE} {adm.primerApellido || adm.PRIMER_APELLIDO}
                        </td>
                        <td className="font-mono text-xs text-text-secondary">{adm.email || adm.EMAIL}</td>
                        <td>
                          <Badge variant="outline" className="font-mono">
                            {adm.nivel || 'GLOBAL'}
                          </Badge>
                        </td>
                        <td className="text-center">
                          <Badge variant={(estado || 'ACTIVO') === 'ACTIVO' ? 'success' : 'secondary'}>
                            {estado || 'ACTIVO'}
                          </Badge>
                        </td>
                        <td className="text-right">
                          {protegido ? (
                            <Badge variant="warning">
                              <ShieldCheck className="h-3 w-3" aria-hidden="true" />
                              Principal protegido
                            </Badge>
                          ) : (
                            <Button
                              variant={estado === 'ACTIVO' ? 'ghost' : 'outline'}
                              size="sm"
                              className="h-10 px-2.5 text-xs sm:h-8"
                              onClick={() =>
                                estado === 'ACTIVO'
                                  ? setConfirmTarget({ id, usuario, estado })
                                  : handleToggleStatus(id, estado)
                              }
                            >
                              {estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                            </Button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={!!confirmTarget}
        onClose={() => setConfirmTarget(null)}
        onConfirm={() => handleToggleStatus(confirmTarget.id, confirmTarget.estado)}
        title="Desactivar operador"
        message={`${confirmTarget?.usuario || 'El operador'} perderá el acceso a la consola de plataforma hasta que se reactive.`}
        confirmLabel="Desactivar"
        danger
      />

      <Dialog
        open={showModal}
        onOpenChange={(o) => {
          setShowModal(o);
          if (!o) setFormErrors({});
        }}
      >
        <DialogContent className="max-h-[90dvh] max-w-md overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Registrar operador SUPERADMIN</DialogTitle>
            <DialogDescription>Los campos marcados con * son obligatorios.</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-1" noValidate>
            <Input
              id="adm-usuario"
              label="Nombre de usuario"
              required
              value={form.nombreUsuario}
              onChange={(e) => setField('nombreUsuario', e.target.value)}
              placeholder="Ej. operador_soporte1"
              autoComplete="off"
              error={formErrors.nombreUsuario}
              className="font-mono"
            />

            <Input
              id="adm-email"
              label="Correo electrónico"
              type="email"
              required
              value={form.email}
              onChange={(e) => setField('email', e.target.value)}
              placeholder="operador@saed.com"
              autoComplete="off"
              error={formErrors.email}
            />

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input
                id="adm-pnombre"
                label="Primer nombre"
                value={form.primerNombre}
                onChange={(e) => setField('primerNombre', e.target.value)}
              />
              <Input
                id="adm-papellido"
                label="Primer apellido"
                value={form.primerApellido}
                onChange={(e) => setField('primerApellido', e.target.value)}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Select
                id="adm-nivel"
                label="Nivel de operación"
                value={form.nivel}
                onChange={(e) => setField('nivel', e.target.value)}
              >
                <option value="SOPORTE">SOPORTE</option>
                <option value="AUDITOR">AUDITOR</option>
                <option value="ADMIN_GLOBAL">ADMIN_GLOBAL</option>
              </Select>

              <Input
                id="adm-pass"
                label="Contraseña inicial"
                type="password"
                required
                value={form.password}
                onChange={(e) => setField('password', e.target.value)}
                autoComplete="new-password"
                error={formErrors.password}
              />
            </div>

            <DialogFooter className="gap-2 pt-3">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Guardando…' : 'Crear operador'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
