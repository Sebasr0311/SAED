import React, { useState, useEffect } from 'react';
import { api } from '../lib/api.js';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

export default function PorteriasPage() {
  const [porterias, setPorterias] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dialog, setDialog] = useState({ open: false, mode: 'create', data: null });
  const [form, setForm] = useState({ nombre: '', ubicacion: '', telefonoContacto: '' });
  const [deleteTarget, setDeleteTarget] = useState(null);

  const cargar = async () => {
    try {
      setLoading(true);
      const res = await api.get('/porteria');
      setPorterias(res.data || []);
    } catch (e) {
      toast.error('No se pudieron cargar las porterías');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const openCreate = () => {
    setForm({ nombre: '', ubicacion: '', telefonoContacto: '' });
    setDialog({ open: true, mode: 'create', data: null });
  };

  const openEdit = (p) => {
    setForm({ nombre: p.nombre, ubicacion: p.ubicacion || '', telefonoContacto: p.telefonoContacto || '' });
    setDialog({ open: true, mode: 'edit', data: p });
  };

  const save = async () => {
    if (!form.nombre.trim()) {
      toast.error('El nombre es obligatorio');
      return;
    }
    try {
      if (dialog.mode === 'create') {
        await api.post('/porteria', form);
        toast.success('Portería creada');
      } else {
        await api.put(`/porteria/${dialog.data.idPorteria}`, form);
        toast.success('Portería actualizada');
      }
      setDialog({ open: false, mode: 'create', data: null });
      cargar();
    } catch (e) {
      toast.error('Error al guardar: ' + (e.message || 'Error desconocido'));
    }
  };

  const eliminar = async () => {
    if (!deleteTarget) return;
    try {
      await api.delete(`/porteria/${deleteTarget.idPorteria}`);
      toast.success('Portería eliminada');
      setDeleteTarget(null);
      cargar();
    } catch (e) {
      toast.error('Error al eliminar');
    }
  };

  const toggleEstado = async (p) => {
    const nuevoEstado = p.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.put(`/porteria/${p.idPorteria}`, {
        nombre: p.nombre,
        ubicacion: p.ubicacion,
        telefonoContacto: p.telefonoContacto,
        estado: nuevoEstado
      });
      toast.success(`Portería ${nuevoEstado.toLowerCase()}`);
      cargar();
    } catch (e) {
      toast.error('Error al cambiar estado');
    }
  };

  if (loading) return <div className="p-8 text-center">Cargando porterías...</div>;

  return (
    <div className="max-w-5xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Porterías</h1>
        <button onClick={openCreate} className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700">
          + Nueva Portería
        </button>
      </div>

      {porterias.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
          No hay porterías registradas. Crea la primera portería de esta propiedad.
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium">Nombre</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Ubicación</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Teléfono</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Estado</th>
                <th className="px-4 py-3 text-right text-sm font-medium">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {porterias.map((p) => (
                <tr key={p.idPorteria} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-medium">{p.nombre}</td>
                  <td className="px-4 py-3 text-sm">{p.ubicacion || '-'}</td>
                  <td className="px-4 py-3 text-sm">{p.telefonoContacto || '-'}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      p.estado === 'ACTIVA' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                    }`}>
                      {p.estado}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-right space-x-2">
                    <button onClick={() => openEdit(p)} className="text-blue-600 hover:underline">Editar</button>
                    <button onClick={() => toggleEstado(p)} className="text-yellow-600 hover:underline">
                      {p.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'}
                    </button>
                    <button onClick={() => setDeleteTarget(p)} className="text-red-600 hover:underline">Eliminar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Dialog Crear/Editar */}
      {dialog.open && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-lg font-semibold mb-4">
              {dialog.mode === 'create' ? 'Nueva Portería' : 'Editar Portería'}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Nombre *</label>
                <input
                  type="text"
                  value={form.nombre}
                  onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2"
                  placeholder="Ej: Portería Principal"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Ubicación</label>
                <input
                  type="text"
                  value={form.ubicacion}
                  onChange={(e) => setForm({ ...form, ubicacion: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2"
                  placeholder="Ej: Entrada lateral"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Teléfono de Contacto</label>
                <input
                  type="text"
                  value={form.telefonoContacto}
                  onChange={(e) => setForm({ ...form, telefonoContacto: e.target.value })}
                  className="w-full border rounded-lg px-3 py-2"
                  placeholder="Ej: 300 123 4567"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button onClick={() => setDialog({ open: false, mode: 'create', data: null })}
                className="px-4 py-2 border rounded-lg hover:bg-gray-50">Cancelar</button>
              <button onClick={save}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                {dialog.mode === 'create' ? 'Crear' : 'Guardar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Delete */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Eliminar portería"
        message={`¿Eliminar "${deleteTarget?.nombre}"? Esta acción no se puede deshacer.`}
        onConfirm={eliminar}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
