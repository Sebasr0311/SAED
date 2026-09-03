import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Users, UserPlus, Search, Shield, Building, Mail, Phone, AlertCircle, CheckCircle2, Power } from 'lucide-react';

export default function OrgAdminsPage() {
  const [admins, setAdmins] = useState([]);
  const [properties, setProperties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');

  // Create modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [newAdmin, setNewAdmin] = useState({
    primerNombre: '',
    primerApellido: '',
    numeroDocumento: '',
    telefono: '',
    email: '',
    nombreUsuario: '',
    password: '',
    idRol: 3, // 3 = ADMIN_PROPIEDAD
    idPropiedad: '',
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
      if (propsList.length > 0 && !newAdmin.idPropiedad) {
        setNewAdmin((prev) => ({ ...prev, idPropiedad: propsList[0].id }));
      }
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
      await api.post('/org/admins', {
        ...newAdmin,
        idPropiedad: newAdmin.idRol === 3 ? Number(newAdmin.idPropiedad) : null,
      });
      setSuccessMsg('Administrador registrado y asignado exitosamente.');
      setIsModalOpen(false);
      setNewAdmin({
        primerNombre: '',
        primerApellido: '',
        numeroDocumento: '',
        telefono: '',
        email: '',
        nombreUsuario: '',
        password: '',
        idRol: 3,
        idPropiedad: properties.length > 0 ? properties[0].id : '',
      });
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error creating admin:', err);
      setCreateError(err?.response?.data?.message || 'Error al registrar el administrador.');
    } finally {
      setCreating(false);
    }
  }

  async function toggleStatus(admin) {
    const nextStatus = admin.asignacionEstado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.patch(`/org/admins/${admin.idAsignacion}/status`, { estado: nextStatus });
      setSuccessMsg(`Asignación de ${admin.primerNombre} ${admin.primerApellido} actualizada a ${nextStatus}.`);
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error updating assignment status:', err);
      setError('No se pudo actualizar el estado de la asignación.');
    }
  }

  const filtered = admins.filter((a) => {
    const term = searchTerm.toLowerCase();
    return (
      (a.nombreUsuario || '').toLowerCase().includes(term) ||
      (a.primerNombre || '').toLowerCase().includes(term) ||
      (a.primerApellido || '').toLowerCase().includes(term) ||
      (a.email || '').toLowerCase().includes(term) ||
      (a.propiedadNombre || '').toLowerCase().includes(term)
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
              {admins.length} administradores
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Gestión de usuarios y asignaciones operativas a las diferentes propiedades de su organización.
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

      {/* Search */}
      <div className="flex justify-between items-center">
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
                  <th className="px-6 py-3 font-semibold">Propiedad Asignada</th>
                  <th className="px-6 py-3 font-semibold">Contacto</th>
                  <th className="px-6 py-3 font-semibold text-center">Estado</th>
                  <th className="px-6 py-3 font-semibold text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-muted-foreground">
                      No se encontraron administradores registrados.
                    </td>
                  </tr>
                ) : (
                  filtered.map((a) => (
                    <tr key={a.idAsignacion} className="hover:bg-muted/20 transition-colors">
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
                        {a.propiedadNombre ? (
                          <div className="flex items-center gap-1.5 text-foreground font-medium">
                            <Building className="w-3.5 h-3.5 text-muted-foreground" />
                            <span>{a.propiedadNombre}</span>
                          </div>
                        ) : (
                          <span className="text-muted-foreground italic">Toda la Organización</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-xs text-muted-foreground">
                        {a.telefono || 'Sin teléfono'}
                      </td>
                      <td className="px-6 py-4 text-center">
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
                      </td>
                      <td className="px-6 py-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => toggleStatus(a)}
                          className="text-xs gap-1"
                        >
                          <Power className="w-3.5 h-3.5" />
                          <span>{a.asignacionEstado === 'ACTIVA' ? 'Suspender' : 'Activar'}</span>
                        </Button>
                      </td>
                    </tr>
                  ))
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
                      Número de Cédula / Doc *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej. 1020304050"
                      value={newAdmin.numeroDocumento}
                      onChange={(e) => setNewAdmin({ ...newAdmin, numeroDocumento: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
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
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Contraseña *
                    </label>
                    <input
                      type="password"
                      required
                      placeholder="Mínimo 6 caracteres"
                      value={newAdmin.password}
                      onChange={(e) => setNewAdmin({ ...newAdmin, password: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Rol Asignado
                    </label>
                    <select
                      value={newAdmin.idRol}
                      onChange={(e) => setNewAdmin({ ...newAdmin, idRol: Number(e.target.value) })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value={3}>Admin Propiedad (Copropiedad)</option>
                      <option value={2}>Admin Organización</option>
                    </select>
                  </div>
                  {newAdmin.idRol === 3 && (
                    <div>
                      <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                        Propiedad Asignada *
                      </label>
                      <select
                        required
                        value={newAdmin.idPropiedad}
                        onChange={(e) => setNewAdmin({ ...newAdmin, idPropiedad: e.target.value })}
                        className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                      >
                        {properties.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.nombre}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                </div>

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
    </div>
  );
}
