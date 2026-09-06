import { useState, useEffect, useCallback, useMemo } from 'react';
import { toast } from 'sonner';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ActionButtons } from '../components/ui/ActionButtons.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';

const emptyForm = {
  nombre: '',
  ubicacion: '',
  telefonoContacto: '',
};

export default function PorteriasPage() {
  const [porterias, setPorterias] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [busqueda, setBusqueda] = useState('');

  const cargar = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get('/porteria');
      const lista = res?.data || res?.items || res || [];
      setPorterias(Array.isArray(lista) ? lista : []);
    } catch (err) {
      toast.error(err.message || 'No se pudieron cargar los puntos de portería');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const items = useMemo(() => {
    return porterias.map((p) => ({
      idPorteria: p.idPorteria ?? p.ID_PORTERIA,
      nombre: p.nombre ?? p.NOMBRE,
      ubicacion: p.ubicacion ?? p.UBICACION,
      telefonoContacto: p.telefonoContacto ?? p.TELEFONO_CONTACTO,
      estado: p.estado ?? p.ESTADO ?? 'ACTIVA',
    }));
  }, [porterias]);

  const filteredItems = useMemo(() => {
    if (!busqueda.trim()) return items;
    const q = busqueda.toLowerCase().trim();
    return items.filter(
      (p) =>
        (p.nombre || '').toLowerCase().includes(q) ||
        (p.ubicacion || '').toLowerCase().includes(q) ||
        (p.telefonoContacto || '').toLowerCase().includes(q)
    );
  }, [items, busqueda]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setModalOpen(true);
  };

  const openEdit = (p) => {
    setEditing(p);
    setForm({
      nombre: p.nombre || '',
      ubicacion: p.ubicacion || '',
      telefonoContacto: p.telefonoContacto || '',
    });
    setModalOpen(true);
  };

  const save = async () => {
    if (!form.nombre.trim()) {
      toast.error('El nombre del punto de portería es obligatorio');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        nombre: form.nombre.trim(),
        ubicacion: form.ubicacion.trim(),
        telefonoContacto: form.telefonoContacto.trim(),
      };
      if (editing) {
        await api.put(`/porteria/${editing.idPorteria}`, payload);
        toast.success('Punto de portería actualizado');
      } else {
        await api.post('/porteria', payload);
        toast.success('Punto de portería registrado');
      }
      setModalOpen(false);
      setEditing(null);
      cargar();
    } catch (e) {
      toast.error('Error al guardar: ' + (e.message || 'Error desconocido'));
    } finally {
      setSaving(false);
    }
  };

  const eliminar = async () => {
    if (!deleteTarget) return;
    try {
      await api.del(`/porteria/${deleteTarget.idPorteria}`);
      toast.success('Punto de portería eliminado');
      setDeleteTarget(null);
      cargar();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar portería');
    }
  };

  const columns = [
    { key: 'idPorteria', label: 'ID', width: 60 },
    {
      key: 'nombre',
      label: 'Nombre de la Garita / Punto',
      render: (r) => (
        <div>
          <div className="font-semibold text-gray-900">🚪 {r.nombre}</div>
          {r.ubicacion && <div className="text-xs text-gray-500">📍 {r.ubicacion}</div>}
        </div>
      ),
    },
    {
      key: 'telefonoContacto',
      label: 'Teléfono / Radio',
      render: (r) => (
        <span className="text-sm text-gray-700">
          {r.telefonoContacto ? `📞 ${r.telefonoContacto}` : '—'}
        </span>
      ),
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => (
        <span
          className={`badge ${
            r.estado === 'ACTIVA' || r.estado === 'ACTIVO' ? 'badge-activo' : 'badge-neutral'
          }`}
        >
          {r.estado === 'ACTIVA' || r.estado === 'ACTIVO' ? 'Operativa' : 'Inactiva'}
        </span>
      ),
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 100,
      render: (row) => (
        <ActionButtons
          onEdit={(e) => {
            e.stopPropagation();
            openEdit(row);
          }}
          onDelete={(e) => {
            e.stopPropagation();
            setDeleteTarget(row);
          }}
        />
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Puntos de Portería"
        subtitle="Administración de garitas, accesos peatonales y vehiculares de la propiedad"
        action={<Button onClick={openCreate}>+ Nueva Portería</Button>}
      />

      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between mb-4">
        <div className="text-xs text-gray-500 font-medium">
          Total de garitas registradas: <strong>{items.length}</strong>
        </div>
        <div className="w-full sm:w-72">
          <input
            type="text"
            placeholder="Buscar portería o ubicación..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="w-full border rounded-lg px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={filteredItems}
        loading={loading}
        empty={{
          icon: 'door_sliding',
          title: 'No hay porterías registradas',
          subtitle: 'Registra el primer punto de control de acceso vehicular o peatonal.',
        }}
        keyField="idPorteria"
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? `Editar Portería: ${editing.nombre}` : 'Nueva Garita de Portería'}
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div className="form-group">
            <Input
              id="nombrePorteria"
              label="Nombre del Punto de Portería *"
              value={form.nombre}
              onChange={(e) => setForm({ ...form, nombre: e.target.value })}
              placeholder="Ej. Portería Principal, Acceso Norte, Garita 2"
            />
          </div>
          <div className="form-group">
            <Input
              id="ubicacionPorteria"
              label="Ubicación Física / Sector"
              value={form.ubicacion}
              onChange={(e) => setForm({ ...form, ubicacion: e.target.value })}
              placeholder="Ej. Entrada vehicular carrera 15"
            />
          </div>
          <div className="form-group">
            <Input
              id="telefonoPorteria"
              label="Teléfono Directo o Extensión"
              value={form.telefonoContacto}
              onChange={(e) => setForm({ ...form, telefonoContacto: e.target.value })}
              placeholder="Ej. Ext 101 / 3001234567"
            />
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="Eliminar punto de portería"
        message={`¿Está seguro de eliminar "${deleteTarget?.nombre}"? Esta acción no se puede deshacer.`}
        onConfirm={eliminar}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
