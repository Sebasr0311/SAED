import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { useTiposDocumento } from '../lib/hooks.js';
import { valDocumento, getDocPlaceholder } from '../lib/validation.js';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  Users,
  UserPlus,
  Search,
  Shield,
  Building,
  Mail,
  Phone,
  AlertCircle,
  CheckCircle2,
  Power,
  Trash2,
  Lock,
  Pencil,
  X,
  Building2,
} from 'lucide-react';
import { toast } from 'sonner';

/**
 * Subcomponente selector de múltiples propiedades con búsqueda y selección masiva
 */
function PropertyMultiSelect({ properties, selectedIds, onChange }) {
  const [query, setQuery] = useState('');

  const filteredProps = properties.filter((p) => {
    const q = query.toLowerCase();
    return (p.nombre || '').toLowerCase().includes(q) || (p.ciudad || '').toLowerCase().includes(q);
  });

  const toggle = (id) => {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((x) => x !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  };

  const selectAll = () => {
    onChange(properties.map((p) => p.id));
  };

  const clearAll = () => {
    onChange([]);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <label className="block text-xs font-semibold text-muted-foreground uppercase">
          Propiedades Asignadas ({selectedIds.length} seleccionada{selectedIds.length === 1 ? '' : 's'})
        </label>
        <div className="flex items-center gap-2 text-xs">
          <button
            type="button"
            onClick={selectAll}
            className="text-[11px] text-primary hover:underline font-medium"
          >
            Seleccionar todas
          </button>
          <span className="text-muted-foreground">·</span>
          <button
            type="button"
            onClick={clearAll}
            className="text-[11px] text-muted-foreground hover:text-foreground"
          >
            Deseleccionar todas
          </button>
        </div>
      </div>

      <div className="relative">
        <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          placeholder="Buscar edificio o conjunto..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full pl-8 pr-3 py-1.5 border border-input rounded-md bg-background text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
        />
      </div>

      <div className="max-h-44 overflow-y-auto border border-input rounded-lg divide-y divide-border bg-background/50">
        {filteredProps.length === 0 ? (
          <div className="p-3 text-xs text-center text-muted-foreground">
            No se encontraron propiedades disponibles.
          </div>
        ) : (
          filteredProps.map((p) => {
            const isChecked = selectedIds.includes(p.id);
            return (
              <label
                key={p.id}
                className={`flex items-center gap-2.5 p-2 text-xs cursor-pointer hover:bg-muted/40 transition-colors ${
                  isChecked ? 'bg-primary/5' : ''
                }`}
              >
                <input
                  type="checkbox"
                  checked={isChecked}
                  onChange={() => toggle(p.id)}
                  className="rounded border-input text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                />
                <Building className={`w-3.5 h-3.5 ${isChecked ? 'text-primary' : 'text-muted-foreground'}`} />
                <div className="flex-1 min-w-0">
                  <span className={`font-medium ${isChecked ? 'text-foreground font-semibold' : 'text-muted-foreground'}`}>
                    {p.nombre}
                  </span>
                  {p.ciudad && (
                    <span className="text-[10px] text-muted-foreground ml-1.5">
                      ({p.ciudad})
                    </span>
                  )}
                </div>
              </label>
            );
          })
        )}
      </div>

      {selectedIds.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 pt-1">
          {selectedIds.map((id) => {
            const prop = properties.find((p) => p.id === id);
            if (!prop) return null;
            return (
              <Badge
                key={id}
                variant="secondary"
                className="text-[11px] py-0.5 px-2 flex items-center gap-1.5 bg-primary/10 text-primary border border-primary/20"
              >
                <Building className="w-3 h-3 text-primary/70" />
                <span>{prop.nombre}</span>
                <button
                  type="button"
                  onClick={() => toggle(id)}
                  className="hover:text-destructive hover:bg-destructive/10 rounded transition-colors ml-0.5 p-0.5"
                  title="Quitar selección"
                >
                  <X className="w-3 h-3" />
                </button>
              </Badge>
            );
          })}
        </div>
      ) : (
        <div className="p-2 rounded-md bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-400 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>Sin propiedades: el perfil quedará en estado <strong>Inactivo</strong> sin acceso a copropiedades.</span>
        </div>
      )}
    </div>
  );
}

export default function OrgAdminsPage() {
  const [admins, setAdmins] = useState([]);
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('TODOS'); // 'TODOS' | 'ACTIVOS' | 'INACTIVOS'

  // Estados de eliminación con doble autorización
  const [adminAEliminar, setAdminAEliminar] = useState(null);
  const [authPassword, setAuthPassword] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  // Estados de edición y asignación de múltiples propiedades
  const [adminAEditar, setAdminAEditar] = useState(null);
  const [editForm, setEditForm] = useState({
    primerNombre: '',
    primerApellido: '',
    email: '',
    telefono: '',
    idPropiedades: [],
    estado: 'ACTIVA',
    password: '',
  });
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState(null);

  function openEditModal(admin) {
    setAdminAEditar(admin);
    setEditError(null);
    const assignedIds = Array.isArray(admin.idPropiedades) && admin.idPropiedades.length > 0
      ? admin.idPropiedades
      : admin.idPropiedad ? [admin.idPropiedad] : [];
    setEditForm({
      primerNombre: admin.primerNombre || '',
      primerApellido: admin.primerApellido || '',
      email: admin.email || '',
      telefono: admin.telefono || '',
      idPropiedades: assignedIds,
      estado: admin.asignacionEstado || 'ACTIVA',
      password: '',
    });
  }

  async function handleUpdate(e) {
    e.preventDefault();
    if (!adminAEditar) return;
    try {
      setEditLoading(true);
      setEditError(null);

      if (!editForm.primerNombre?.trim() || !editForm.primerApellido?.trim()) {
        setEditError('El primer nombre y el primer apellido son obligatorios.');
        setEditLoading(false);
        return;
      }

      const payload = {
        primerNombre: editForm.primerNombre.trim(),
        primerApellido: editForm.primerApellido.trim(),
        email: editForm.email?.trim() || null,
        telefono: editForm.telefono?.trim() || null,
        idPropiedades: editForm.idPropiedades.map(Number),
        estado: editForm.idPropiedades.length === 0 ? 'INACTIVA' : editForm.estado,
      };

      if (editForm.password?.trim()) {
        payload.password = editForm.password.trim();
      }

      const res = await api.put(`/org/admins/${adminAEditar.idAsignacion}`, payload);
      toast.success(res?.message || 'Administrador actualizado exitosamente');
      setAdminAEditar(null);
      await loadData();
    } catch (err) {
      console.error('Error updating admin:', err);
      const msg = err.response?.data?.message || err.message || 'No se pudo actualizar el administrador.';
      setEditError(msg);
      toast.error(msg);
    } finally {
      setEditLoading(false);
    }
  }

  const { tiposDoc } = useTiposDocumento();

  // Create modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [newAdmin, setNewAdmin] = useState({
    primerNombre: '',
    primerApellido: '',
    idTipoDocumento: 1,
    numeroDocumento: '',
    telefono: '',
    email: '',
    nombreUsuario: '',
    password: '',
    idRol: 3, // 3 = ADMIN_PROPIEDAD
    idPropiedades: [],
  });

  async function loadData() {
    try {
      setLoading(true);
      setError(null);
      const [adminsRes, propsRes] = await Promise.all([
        api.get('/org/admins'),
        api.get('/properties'),
      ]);
      setAdmins(Array.isArray(adminsRes?.data) ? adminsRes.data : Array.isArray(adminsRes) ? adminsRes : []);
      const propsList = Array.isArray(propsRes?.data) ? propsRes.data : Array.isArray(propsRes) ? propsRes : [];
      setProperties(propsList);
    } catch (err) {
      console.error('Error loading org admins:', err);
      setError('No se pudieron cargar los administradores de la organización.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    try {
      setCreating(true);
      setCreateError(null);

      // Validar documento según norma colombiana
      const selectedDoc = tiposDoc.find((t) => Number(t.idTipoDoc) === Number(newAdmin.idTipoDocumento));
      const cod = selectedDoc?.codigo || 'CC';
      const docResult = valDocumento(newAdmin.numeroDocumento, cod, 'El número de documento');
      if (!docResult.ok) {
        setCreateError(docResult.mensaje);
        setCreating(false);
        return;
      }

      if (!newAdmin.primerNombre?.trim() || !newAdmin.primerApellido?.trim()) {
        setCreateError('El primer nombre y el primer apellido son obligatorios.');
        setCreating(false);
        return;
      }

      if (!newAdmin.email?.trim()) {
        setCreateError('El correo electrónico es obligatorio.');
        setCreating(false);
        return;
      }

      if (!newAdmin.nombreUsuario?.trim()) {
        setCreateError('El usuario de ingreso es obligatorio.');
        setCreating(false);
        return;
      }

      const effectivePassword = newAdmin.password?.trim() || ('Adm' + Math.random().toString(36).slice(-6) + '!9');

      await api.post('/org/admins', {
        ...newAdmin,
        password: effectivePassword,
        idRol: 3,
        idPropiedades: newAdmin.idPropiedades.map(Number),
      });

      setSuccessMsg(
        newAdmin.idPropiedades.length > 0
          ? 'Administrador de propiedad registrado y asignado exitosamente.'
          : 'Administrador registrado exitosamente (inactivo, sin propiedades a cargo).'
      );
      setIsModalOpen(false);
      setNewAdmin({
        primerNombre: '',
        primerApellido: '',
        idTipoDocumento: 1,
        numeroDocumento: '',
        telefono: '',
        email: '',
        nombreUsuario: '',
        password: '',
        idRol: 3,
        idPropiedades: [],
      });
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error creating admin:', err);
      const msg = err?.response?.data?.message || err?.response?.data?.error || 'Error al registrar el administrador.';
      setCreateError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    } finally {
      setCreating(false);
    }
  }

  async function toggleStatus(admin) {
    if (admin.rolCodigo === 'ADMIN_ORGANIZACION') {
      toast.error('No se puede suspender cuentas de nivel organizacional.');
      return;
    }
    const nextStatus = admin.asignacionEstado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.patch(`/org/admins/${admin.idAsignacion}/status`, { estado: nextStatus });
      setSuccessMsg(`Asignación de ${admin.primerNombre} ${admin.primerApellido} actualizada a ${nextStatus}.`);
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error updating assignment status:', err);
      setError(err?.response?.data?.message || 'No se pudo actualizar el estado de la asignación.');
    }
  }

  async function handleUnassignProperty(admin, propId, propNombre) {
    if (!window.confirm(`¿Desea desvincular a ${admin.primerNombre} ${admin.primerApellido} de la administración de "${propNombre}"?`)) {
      return;
    }
    try {
      const res = await api.delete(`/org/admins/${admin.idUsuario}/propiedades/${propId}`);
      const msg = res?.message || `Administrador desvinculado de "${propNombre}"`;
      toast.success(msg);
      await loadData();
    } catch (err) {
      console.error('Error desasignando propiedad:', err);
      const msg = err.response?.data?.message || err.message || 'No se pudo desvincular la propiedad.';
      toast.error(msg);
    }
  }

  async function handleEliminarConfirmado(e) {
    e.preventDefault();
    if (!authPassword.trim()) {
      setDeleteError('Debe ingresar su contraseña de administrador para autorizar la eliminación.');
      return;
    }
    try {
      setDeleteLoading(true);
      setDeleteError(null);
      await api.delete(`/org/admins/${adminAEliminar.idAsignacion}`, {
        data: { password: authPassword.trim() },
      });
      toast.success(`Administrador '${adminAEliminar.primerNombre} ${adminAEliminar.primerApellido}' eliminado exitosamente.`);
      setAdminAEliminar(null);
      setAuthPassword('');
      await loadData();
    } catch (err) {
      console.error('Error eliminando administrador:', err);
      setDeleteError(err?.response?.data?.message || err?.message || 'Error al eliminar el administrador.');
    } finally {
      setDeleteLoading(false);
    }
  }

  const activeCount = admins.filter(
    (a) => a.asignacionEstado === 'ACTIVA' && (a.rolCodigo === 'ADMIN_ORGANIZACION' || (Array.isArray(a.idPropiedades) && a.idPropiedades.length > 0))
  ).length;

  const inactiveCount = admins.filter(
    (a) => a.rolCodigo !== 'ADMIN_ORGANIZACION' && (a.asignacionEstado === 'INACTIVA' || !a.idPropiedades || a.idPropiedades.length === 0)
  ).length;

  const filtered = admins.filter((a) => {
    const hasProps = a.rolCodigo === 'ADMIN_ORGANIZACION' || (Array.isArray(a.idPropiedades) && a.idPropiedades.length > 0);
    const isActive = a.asignacionEstado === 'ACTIVA' && hasProps;

    if (statusFilter === 'ACTIVOS' && !isActive) return false;
    if (statusFilter === 'INACTIVOS' && isActive) return false;

    const term = searchTerm.toLowerCase();
    const propsMatch = (a.propiedadesNombres || []).some((pn) => (pn || '').toLowerCase().includes(term));
    return (
      (a.nombreUsuario || '').toLowerCase().includes(term) ||
      (a.primerNombre || '').toLowerCase().includes(term) ||
      (a.primerApellido || '').toLowerCase().includes(term) ||
      (a.email || '').toLowerCase().includes(term) ||
      (a.propiedadNombre || '').toLowerCase().includes(term) ||
      propsMatch
    );
  });

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Administradores de la Organización
            </h1>
            <Badge variant="outline" className="text-xs px-2.5 py-0.5 font-medium">
              {admins.length} {admins.length === 1 ? 'administrador' : 'administradores'}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Gestión de usuarios y asignaciones operativas multi-propiedad a las diferentes copropiedades de su organización.
          </p>
        </div>
        <div>
          <Button onClick={() => setIsModalOpen(true)} className="flex items-center gap-2">
            <UserPlus className="w-4 h-4" />
            <span>Nuevo Administrador</span>
          </Button>
        </div>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="bg-emerald-500/15 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* Filter Tabs & Search */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="flex items-center gap-1.5 p-1 bg-muted/40 rounded-lg border border-border">
          <button
            type="button"
            onClick={() => setStatusFilter('TODOS')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-colors ${
              statusFilter === 'TODOS'
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            Todos ({admins.length})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('ACTIVOS')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-colors ${
              statusFilter === 'ACTIVOS'
                ? 'bg-background text-emerald-600 dark:text-emerald-400 shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            Activos ({activeCount})
          </button>
          <button
            type="button"
            onClick={() => setStatusFilter('INACTIVOS')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-colors ${
              statusFilter === 'INACTIVOS'
                ? 'bg-background text-amber-600 dark:text-amber-400 shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            Inactivos / Sin propiedades ({inactiveCount})
          </button>
        </div>

        <div className="relative w-full max-w-sm">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por nombre, usuario, email o propiedad..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
      </div>

      {/* Table */}
      <Card className="border border-border/80 shadow-sm">
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-muted-foreground bg-muted/30 border-b border-border">
                <tr>
                  <th className="px-6 py-3 font-semibold">Administrador</th>
                  <th className="px-6 py-3 font-semibold">Usuario / Acceso</th>
                  <th className="px-6 py-3 font-semibold">Rol</th>
                  <th className="px-6 py-3 font-semibold">Propiedad(es) Asignada(s)</th>
                  <th className="px-6 py-3 font-semibold">Contacto</th>
                  <th className="px-6 py-3 font-semibold text-center">Estado</th>
                  <th className="px-6 py-3 font-semibold text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-muted-foreground">
                      No se encontraron administradores con los filtros aplicados.
                    </td>
                  </tr>
                ) : (
                  filtered.map((a) => {
                    const isInactiveNoProps =
                      a.rolCodigo !== 'ADMIN_ORGANIZACION' &&
                      (a.asignacionEstado === 'INACTIVA' || !a.idPropiedades || a.idPropiedades.length === 0);

                    return (
                      <tr key={a.idAsignacion || a.idUsuario} className="hover:bg-muted/20 transition-colors">
                        <td className="px-6 py-4">
                          <div className="font-medium text-foreground">
                            {a.primerNombre} {a.primerApellido}
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <span className="font-mono text-xs font-semibold text-foreground">
                            @{a.nombreUsuario}
                          </span>
                          <div className="text-xs text-muted-foreground">{a.email}</div>
                        </td>
                        <td className="px-6 py-4 text-xs">
                          <Badge variant="outline" className="text-[11px] font-medium">
                            {a.rolNombre || a.rolCodigo}
                          </Badge>
                        </td>
                        <td className="px-6 py-4 text-xs">
                          {(() => {
                            if (a.rolCodigo === 'ADMIN_ORGANIZACION') {
                              return <span className="text-muted-foreground italic">Toda la Organización</span>;
                            }

                            const assignedProps = (a.propiedades && a.propiedades.length > 0)
                              ? a.propiedades
                              : (Array.isArray(a.idPropiedades) && a.idPropiedades.length > 0)
                                ? a.idPropiedades.map((pId) => {
                                    const found = properties.find((p) => p.id === pId);
                                    return {
                                      idPropiedad: pId,
                                      propiedadNombre: found ? found.nombre : `Propiedad #${pId}`,
                                    };
                                  })
                                : [];

                            if (assignedProps.length === 0) {
                              return (
                                <Badge variant="outline" className="text-amber-600 dark:text-amber-400 bg-amber-500/10 border-amber-500/20 text-[11px] font-medium">
                                  Sin propiedades asignadas
                                </Badge>
                              );
                            }

                            return (
                              <div className="flex flex-wrap gap-1.5 max-w-sm">
                                {assignedProps.map((p) => (
                                  <span
                                    key={p.idPropiedad}
                                    className="inline-flex items-center gap-1.5 text-xs bg-muted/60 text-foreground px-2 py-1 rounded-md border border-border/60"
                                  >
                                    <Building className="w-3.5 h-3.5 text-muted-foreground" />
                                    <span className="font-medium">{p.propiedadNombre}</span>
                                    <button
                                      type="button"
                                      onClick={() => handleUnassignProperty(a, p.idPropiedad, p.propiedadNombre)}
                                      className="text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded p-0.5 transition-colors ml-0.5"
                                      title={`Desvincular de "${p.propiedadNombre}"`}
                                    >
                                      <X className="w-3 h-3" />
                                    </button>
                                  </span>
                                ))}
                              </div>
                            );
                          })()}
                        </td>
                        <td className="px-6 py-4 text-xs text-muted-foreground">
                          {a.telefono || 'Sin teléfono'}
                        </td>
                        <td className="px-6 py-4 text-center">
                          {isInactiveNoProps ? (
                            <Badge
                              variant="secondary"
                              className="bg-amber-500/15 text-amber-700 dark:text-amber-400 border-amber-500/20 text-[11px] font-semibold"
                            >
                              INACTIVO (Sin propiedades)
                            </Badge>
                          ) : (
                            <Badge
                              variant={a.asignacionEstado === 'ACTIVA' ? 'default' : 'secondary'}
                              className={
                                a.asignacionEstado === 'ACTIVA'
                                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs'
                                  : 'text-xs'
                              }
                            >
                              {a.asignacionEstado || 'ACTIVA'}
                            </Badge>
                          )}
                        </td>
                        <td className="px-6 py-4 text-right">
                          {a.rolCodigo === 'ADMIN_ORGANIZACION' ? (
                            <Badge variant="outline" className="text-[11px] font-medium bg-primary/5 text-primary border-primary/20">
                              Cuenta Titular
                            </Badge>
                          ) : (
                            <div className="flex items-center justify-end gap-1">
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openEditModal(a)}
                                className="text-xs text-primary hover:bg-primary/10 hover:text-primary h-8 w-8 p-0"
                                title="Editar datos y asignar propiedades"
                              >
                                <Pencil className="w-3.5 h-3.5" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => toggleStatus(a)}
                                className="text-xs gap-1"
                                title={a.asignacionEstado === 'ACTIVA' ? 'Suspender cuenta' : 'Activar cuenta'}
                              >
                                <Power className="w-3.5 h-3.5" />
                                <span>{a.asignacionEstado === 'ACTIVA' ? 'Suspender' : 'Activar'}</span>
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => {
                                  setAdminAEliminar(a);
                                  setAuthPassword('');
                                  setDeleteError(null);
                                }}
                                className="text-xs text-destructive hover:bg-destructive/10 hover:text-destructive h-8 w-8 p-0"
                                title="Eliminar cuenta de administrador"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </Button>
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Create Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-lg bg-background border-border shadow-xl">
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="text-lg font-bold text-foreground">
                Registrar Nuevo Administrador
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-6">
              {createError && (
                <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2 rounded-lg text-xs flex items-center gap-2 mb-4">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{createError}</span>
                </div>
              )}
              <form onSubmit={handleCreate} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Primer Nombre *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. Juan"
                      value={newAdmin.primerNombre}
                      onChange={(e) => setNewAdmin({ ...newAdmin, primerNombre: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Primer Apellido *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. Pérez"
                      value={newAdmin.primerApellido}
                      onChange={(e) => setNewAdmin({ ...newAdmin, primerApellido: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Tipo de Documento *
                    </label>
                    <select
                      value={newAdmin.idTipoDocumento}
                      onChange={(e) => setNewAdmin({ ...newAdmin, idTipoDocumento: Number(e.target.value) })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      {tiposDoc.map((t) => (
                        <option key={t.idTipoDoc} value={t.idTipoDoc}>
                          {t.codigo} - {t.nombre}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Número de Documento *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder={getDocPlaceholder(tiposDoc.find((t) => Number(t.idTipoDoc) === Number(newAdmin.idTipoDocumento))?.codigo || 'CC')}
                      value={newAdmin.numeroDocumento}
                      onChange={(e) => setNewAdmin({ ...newAdmin, numeroDocumento: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Teléfono
                    </label>
                    <input
                      type="text"
                      placeholder="Ej. 3001234567"
                      value={newAdmin.telefono}
                      onChange={(e) => setNewAdmin({ ...newAdmin, telefono: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Correo Electrónico *
                    </label>
                    <input
                      type="email"
                      required
                      placeholder="admin@ejemplo.com"
                      value={newAdmin.email}
                      onChange={(e) => setNewAdmin({ ...newAdmin, email: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Usuario de Ingreso *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. jperez"
                      value={newAdmin.nombreUsuario}
                      onChange={(e) => setNewAdmin({ ...newAdmin, nombreUsuario: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-xs font-semibold text-muted-foreground uppercase">
                        Contraseña
                      </label>
                      <span className="text-[11px] text-muted-foreground">Opcional</span>
                    </div>
                    <input
                      type="password"
                      placeholder="Dejar vacía para auto-generar"
                      value={newAdmin.password}
                      onChange={(e) => setNewAdmin({ ...newAdmin, password: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                    <p className="text-[11px] text-muted-foreground mt-1">
                      Si se deja vacía, el sistema generará una clave aleatoria y la enviará al correo.
                    </p>
                  </div>
                </div>

                {/* Rol asignado */}
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Rol Asignado
                  </label>
                  <div className="w-full px-3 py-2 border border-input rounded-lg bg-muted/40 text-sm font-medium text-foreground flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <Shield className="w-3.5 h-3.5 text-primary" />
                      Admin Propiedad
                    </span>
                    <Badge variant="outline" className="text-[10px] bg-primary/10 text-primary border-primary/20">
                      Multi-Copropiedad
                    </Badge>
                  </div>
                  <p className="text-[11px] text-muted-foreground mt-1">
                    Gestión operativa para una o varias copropiedades de la organización.
                  </p>
                </div>

                {/* Selector múltiple de propiedades */}
                <PropertyMultiSelect
                  properties={properties}
                  selectedIds={newAdmin.idPropiedades}
                  onChange={(ids) => setNewAdmin({ ...newAdmin, idPropiedades: ids })}
                />

                <div className="flex justify-end gap-3 pt-4 border-t border-border">
                  <Button type="button" variant="outline" onClick={() => setIsModalOpen(false)}>
                    Cancelar
                  </Button>
                  <Button type="submit" disabled={creating}>
                    {creating ? 'Guardando...' : 'Crear Administrador'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Modal de Eliminación con Doble Autorización */}
      {adminAEliminar && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-md bg-background border-border shadow-2xl">
            <CardHeader className="border-b border-border pb-4">
              <div className="flex items-center gap-2 text-destructive">
                <AlertCircle className="w-5 h-5" />
                <CardTitle className="text-lg font-bold text-foreground">
                  Doble Autorización Requerida
                </CardTitle>
              </div>
              <CardDescription className="text-xs text-muted-foreground mt-1">
                Está a punto de eliminar la cuenta de administrador de propiedad de{' '}
                <strong className="text-foreground">{adminAEliminar.primerNombre} {adminAEliminar.primerApellido}</strong> (@{adminAEliminar.nombreUsuario}).
              </CardDescription>
            </CardHeader>
            <form onSubmit={handleEliminarConfirmado}>
              <CardContent className="pt-5 space-y-4">
                {deleteError && (
                  <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2 rounded-lg text-xs flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{deleteError}</span>
                  </div>
                )}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                    <Lock className="w-3.5 h-3.5 text-primary" />
                    Ingrese su contraseña de Administrador de Organización:
                  </label>
                  <input
                    type="password"
                    autoFocus
                    required
                    placeholder="Contraseña actual de su cuenta"
                    value={authPassword}
                    onChange={(e) => setAuthPassword(e.target.value)}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-destructive/30"
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Esta acción de seguridad protege las copropiedades y no se puede deshacer.
                  </p>
                </div>
                <div className="flex items-center justify-end gap-2 pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={deleteLoading}
                    onClick={() => {
                      setAdminAEliminar(null);
                      setAuthPassword('');
                      setDeleteError(null);
                    }}
                  >
                    Cancelar
                  </Button>
                  <Button
                    type="submit"
                    variant="destructive"
                    size="sm"
                    disabled={deleteLoading || !authPassword.trim()}
                    className="bg-destructive hover:bg-destructive/90 text-white font-medium"
                  >
                    {deleteLoading ? 'Verificando...' : 'Confirmar y Eliminar'}
                  </Button>
                </div>
              </CardContent>
            </form>
          </Card>
        </div>
      )}

      {/* Modal de Edición y Asignación Multi-Propiedad */}
      {adminAEditar && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-lg bg-background border-border shadow-xl max-h-[90vh] overflow-y-auto">
            <CardHeader className="border-b border-border pb-4">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg font-bold text-foreground">
                    Editar / Reasignar Administrador
                  </CardTitle>
                  <CardDescription className="text-xs text-muted-foreground mt-0.5">
                    Modifique los datos de contacto o asigne/desvincule edificios y conjuntos.
                  </CardDescription>
                </div>
                <Badge variant="outline" className="text-xs bg-primary/10 text-primary border-primary/20">
                  {adminAEditar.rolNombre || 'Admin Propiedad'}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="pt-6">
              {editError && (
                <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2 rounded-lg text-xs flex items-center gap-2 mb-4">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{editError}</span>
                </div>
              )}
              <form onSubmit={handleUpdate} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Primer Nombre *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. Juan"
                      value={editForm.primerNombre}
                      onChange={(e) => setEditForm({ ...editForm, primerNombre: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Primer Apellido *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. Pérez"
                      value={editForm.primerApellido}
                      onChange={(e) => setEditForm({ ...editForm, primerApellido: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Correo Electrónico
                    </label>
                    <input
                      type="email"
                      placeholder="correo@ejemplo.com"
                      value={editForm.email}
                      onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Teléfono / Móvil
                    </label>
                    <input
                      type="tel"
                      placeholder="Ej. 3001234567"
                      value={editForm.telefono}
                      onChange={(e) => setEditForm({ ...editForm, telefono: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                {/* Selector Multi-propiedad */}
                <PropertyMultiSelect
                  properties={properties}
                  selectedIds={editForm.idPropiedades}
                  onChange={(ids) => setEditForm({ ...editForm, idPropiedades: ids })}
                />

                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Estado de la Asignación
                  </label>
                  <select
                    value={editForm.estado}
                    onChange={(e) => setEditForm({ ...editForm, estado: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  >
                    <option value="ACTIVA">ACTIVA (Acceso Habilitado)</option>
                    <option value="SUSPENDIDA">SUSPENDIDA (Acceso Bloqueado)</option>
                    <option value="INACTIVA">INACTIVA (Sin Propiedades)</option>
                  </select>
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-semibold text-muted-foreground uppercase">
                      Nueva Contraseña
                    </label>
                    <span className="text-[11px] text-muted-foreground">Opcional</span>
                  </div>
                  <input
                    type="password"
                    placeholder="Dejar en blanco para mantener la actual"
                    value={editForm.password}
                    onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                  <p className="text-[11px] text-muted-foreground mt-1">
                    Solo ingrese un valor si desea restablecer la clave de acceso del administrador.
                  </p>
                </div>

                <div className="flex justify-end gap-3 pt-4 border-t border-border">
                  <Button type="button" variant="outline" onClick={() => setAdminAEditar(null)} disabled={editLoading}>
                    Cancelar
                  </Button>
                  <Button type="submit" disabled={editLoading}>
                    {editLoading ? 'Guardando cambios...' : 'Guardar Cambios'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
