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
import { Shield, Clock, Users, Trash2, Calendar, Phone, Plus, DoorOpen, MapPin, Search } from 'lucide-react';
import { Badge } from '../components/ui/badge.tsx';

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
  const [loadError, setLoadError] = useState(null);

  // Turnos & Porteros assignment state (Ley 1920 de 2018)
  const [turnosModalOpen, setTurnosModalOpen] = useState(false);
  const [selectedPorteria, setSelectedPorteria] = useState(null);
  const [turnosList, setTurnosList] = useState([]);
  const [turnosCatalogo, setTurnosCatalogo] = useState([]);
  const [porterosDisponibles, setPorterosDisponibles] = useState([]);
  const [loadingTurnos, setLoadingTurnos] = useState(false);
  const [savingTurno, setSavingTurno] = useState(false);
  const [nuevoTurno, setNuevoTurno] = useState({
    idUsuario: '',
    codigoTurno: 'TURNO_8H_MANANA',
    diasSemana: 'Lunes a Domingo',
  });

  const cargar = useCallback(async () => {
    try {
      setLoading(true);
      const res = await api.get('/porteria');
      const lista = res?.data || res?.items || res || [];
      setPorterias(Array.isArray(lista) ? lista : []);
      setLoadError(null);
    } catch (err) {
      setLoadError(err);
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

  // Turnos Management Functions
  const openTurnos = async (porteria) => {
    setSelectedPorteria(porteria);
    setTurnosModalOpen(true);
    setLoadingTurnos(true);
    try {
      const [resTurnos, resCatalogo, resPorteros] = await Promise.all([
        api.get(`/porteria/${porteria.idPorteria}/turnos`),
        api.get('/porteria/turnos/catalogo'),
        api.get('/porteria/porteros-disponibles'),
      ]);

      setTurnosList(resTurnos?.items || (Array.isArray(resTurnos) ? resTurnos : []));
      setTurnosCatalogo(resCatalogo?.items || (Array.isArray(resCatalogo) ? resCatalogo : []));
      setPorterosDisponibles(resPorteros?.items || (Array.isArray(resPorteros) ? resPorteros : []));
    } catch (err) {
      toast.error('Error al cargar la información de turnos: ' + (err.message || ''));
    } finally {
      setLoadingTurnos(false);
    }
  };

  const handleAsignarTurno = async () => {
    if (!nuevoTurno.idUsuario) {
      toast.error('Seleccione un portero para asignar.');
      return;
    }
    if (!nuevoTurno.codigoTurno) {
      toast.error('Seleccione un turno legal.');
      return;
    }

    setSavingTurno(true);
    try {
      await api.post(`/porteria/${selectedPorteria.idPorteria}/turnos`, {
        idUsuario: Number(nuevoTurno.idUsuario),
        codigoTurno: nuevoTurno.codigoTurno,
        diasSemana: nuevoTurno.diasSemana || 'Lunes a Domingo',
      });
      toast.success('Turno de vigilancia asignado exitosamente');
      setNuevoTurno({
        idUsuario: '',
        codigoTurno: 'TURNO_8H_MANANA',
        diasSemana: 'Lunes a Domingo',
      });
      // Recargar lista de turnos
      const resTurnos = await api.get(`/porteria/${selectedPorteria.idPorteria}/turnos`);
      setTurnosList(resTurnos?.items || (Array.isArray(resTurnos) ? resTurnos : []));
    } catch (err) {
      toast.error('Error asignando turno: ' + (err.response?.data?.message || err.message));
    } finally {
      setSavingTurno(false);
    }
  };

  const handleDesasignarTurno = async (idAsignacionTurno) => {
    try {
      await api.del(`/porteria/${selectedPorteria.idPorteria}/turnos/${idAsignacionTurno}`);
      toast.success('Turno desvinculado de la garita');
      const resTurnos = await api.get(`/porteria/${selectedPorteria.idPorteria}/turnos`);
      setTurnosList(resTurnos?.items || (Array.isArray(resTurnos) ? resTurnos : []));
    } catch (err) {
      toast.error('Error desvinculando turno: ' + (err.message || ''));
    }
  };

  const columns = [
    { key: 'idPorteria', label: 'ID', width: 60, render: (r) => <span className="tabular-nums">{r.idPorteria}</span> },
    {
      key: 'nombre',
      label: 'Garita o punto',
      render: (r) => (
        <div>
          <div className="font-semibold text-foreground flex items-center gap-1.5">
            <DoorOpen className="h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
            {r.nombre}
          </div>
          {r.ubicacion && (
            <div className="mt-0.5 flex items-center gap-1 text-xs text-text-secondary">
              <MapPin className="h-3 w-3 shrink-0" aria-hidden="true" />
              {r.ubicacion}
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'telefonoContacto',
      label: 'Teléfono o radio',
      render: (r) =>
        r.telefonoContacto ? (
          <span className="inline-flex items-center gap-1 text-xs text-on-surface tabular-nums">
            <Phone className="h-3 w-3 shrink-0 text-text-muted" aria-hidden="true" />
            {r.telefonoContacto}
          </span>
        ) : (
          <span className="text-xs text-text-muted">—</span>
        ),
    },
    {
      key: 'turnos',
      label: 'Turnos',
      render: (r) => (
        <button
          onClick={(e) => {
            e.stopPropagation();
            openTurnos(r);
          }}
          type="button"
          className="inline-flex h-9 items-center gap-1.5 rounded-md border border-input/60 bg-surface px-3 text-xs font-semibold text-on-surface transition-colors hover:border-primary/70 hover:bg-surface-container"
        >
          <Clock className="h-3.5 w-3.5 text-primary-text" aria-hidden="true" />
          Asignar turnos
        </button>
      ),
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => (
        <Badge variant={r.estado === 'ACTIVA' || r.estado === 'ACTIVO' ? 'success' : 'secondary'}>
          {r.estado === 'ACTIVA' || r.estado === 'ACTIVO' ? 'Operativa' : 'Inactiva'}
        </Badge>
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
    <div className="space-y-6">
      <PageHeader
        title="Puntos de Portería"
        subtitle="Garitas de la copropiedad y turnos de vigilancia (jornadas según la Ley 1920 de 2018)."
        action={
          <Button onClick={openCreate} icon={<Plus className="h-4 w-4" />}>
            Nueva portería
          </Button>
        }
      />

      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="text-sm text-text-secondary">
          Garitas registradas: <strong className="text-on-surface tabular-nums">{items.length}</strong>
        </div>
        <div className="relative w-full sm:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="search"
            aria-label="Buscar portería o ubicación"
            placeholder="Buscar portería o ubicación"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-3"
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={filteredItems}
        loading={loading}
        error={loadError ? `No se pudieron cargar los puntos de portería: ${loadError.message || 'error de conexión'}` : null}
        onRetry={cargar}
        empty={{
          icon: DoorOpen,
          title: busqueda ? 'Ninguna portería coincide con la búsqueda' : 'No hay porterías registradas',
          subtitle: busqueda ? 'Revise el nombre o la ubicación.' : 'Registre el primer punto de control de acceso vehicular o peatonal con “Nueva portería”.',
        }}
        keyField="idPorteria"
      />

      {/* Modal: Crear / Editar Portería */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? `Editar portería: ${editing.nombre}` : 'Nueva portería'}
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </div>
        }
      >
        <div className="space-y-4 pt-2">
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

      {/* Modal: Asignación de Turnos de Vigilancia (Ley 1920 de 2018) */}
      {selectedPorteria && (
        <Modal
          open={turnosModalOpen}
          onClose={() => setTurnosModalOpen(false)}
          title={`Turnos de ${selectedPorteria.nombre}`}
          size="lg"
          footer={
            <Button variant="outline" onClick={() => setTurnosModalOpen(false)}>
              Cerrar
            </Button>
          }
        >
          <div className="space-y-5 pt-2">
            {/* Banner Informativo Legal */}
            <div className="p-3 bg-tint-blue-bg border border-tint-blue-fg/25 rounded-xl flex items-start gap-2.5 text-xs text-tint-blue-fg">
              <Shield className="w-4 h-4 text-tint-blue-fg mt-0.5 shrink-0" />
              <div>
                <p className="font-semibold">Marco Legal Colombiano (Ley 1920 de 2018 - Ley del Vigilante):</p>
                <p className="text-muted-foreground mt-0.5 leading-relaxed">
                  Cada vigilante o portero asignado a una garita cubre <strong>1 turno activo</strong>. La ley colombiana regula jornadas ordinarias de 8 horas (Mañana, Tarde o Noche) o jornadas suplementarias especiales de hasta 12 horas diarias (máximo 60h semanales).
                </p>
              </div>
            </div>

            {/* Lista de Porteros y Turnos Asignados */}
            <div>
              <span className="text-xs font-semibold text-foreground block mb-2">
                Porteros Asignados a este Punto ({turnosList.length})
              </span>

              {loadingTurnos ? (
                <div className="p-6 text-center text-xs text-muted-foreground bg-card border border-border rounded-xl">
                  <div className="animate-spin w-6 h-6 border-2 border-primary border-t-transparent rounded-full mx-auto mb-2" />
                  Cargando turnos...
                </div>
              ) : turnosList.length === 0 ? (
                <div className="p-6 text-center text-xs text-muted-foreground bg-muted/20 border border-border rounded-xl">
                  No hay porteros asignados a este punto de portería actualmente.
                </div>
              ) : (
                <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                  {turnosList.map((t) => (
                    <div
                      key={t.idAsignacionTurno}
                      className="p-3 bg-card border border-border rounded-xl flex items-center justify-between gap-3 text-xs shadow-2xs hover:border-primary/30 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-xs">
                          {t.nombreUsuario?.slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <div className="font-semibold text-foreground text-sm flex items-center gap-2">
                            {t.nombreCompleto || t.nombreUsuario}
                            <span className="text-xs text-muted-foreground font-normal">(@{t.nombreUsuario})</span>
                          </div>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25">
                              <Clock className="w-3 h-3" />
                              {t.horaInicio} - {t.horaFin} ({t.nombreTurno})
                            </span>
                            <span className="text-xs text-muted-foreground">
                              <Calendar className="h-3 w-3" aria-hidden="true" /> {t.diasSemana || 'L-D'}
                            </span>
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => handleDesasignarTurno(t.idAsignacionTurno)}
                        className="text-destructive hover:bg-destructive/10 p-1.5 rounded-lg transition-colors"
                        title="Desvincular Turno"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Formulario para Asignar Portero a un Turno */}
            <div className="border-t border-border pt-4 space-y-3">
              <span className="text-xs font-semibold text-foreground block">
                Asignar Portero a Turno
              </span>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor="port-f1" className="block text-xs font-medium text-foreground mb-1">
                    Seleccionar Portero *
                  </label>
                  <select id="port-f1"
                    value={nuevoTurno.idUsuario}
                    onChange={(e) => setNuevoTurno({ ...nuevoTurno, idUsuario: e.target.value })}
                    className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                  >
                    <option value="">-- Seleccionar Vigilante / Portero --</option>
                    {porterosDisponibles.map((p) => (
                      <option key={p.idUsuario} value={p.idUsuario}>
                        {p.nombreCompleto} (@{p.username})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="port-f2" className="block text-xs font-medium text-foreground mb-1">
                    Turno Legal (1 por vigilante) *
                  </label>
                  <select id="port-f2"
                    value={nuevoTurno.codigoTurno}
                    onChange={(e) => setNuevoTurno({ ...nuevoTurno, codigoTurno: e.target.value })}
                    className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                  >
                    <optgroup label="Jornada Ordinaria (8 Horas)">
                      <option value="TURNO_8H_MANANA">Mañana: 06:00 - 14:00 (8h Ordinaria)</option>
                      <option value="TURNO_8H_TARDE">Tarde: 14:00 - 22:00 (8h Ordinaria)</option>
                      <option value="TURNO_8H_NOCHE">Noche: 22:00 - 06:00 (8h Ordinaria)</option>
                    </optgroup>
                    <optgroup label="Jornada Especial Ley 1920 (12 Horas)">
                      <option value="TURNO_12H_DIURNO">Diurno: 06:00 - 18:00 (12h Ley 1920)</option>
                      <option value="TURNO_12H_NOCTURNO">Nocturno: 18:00 - 06:00 (12h Ley 1920)</option>
                    </optgroup>
                  </select>
                </div>
              </div>

              <div>
                <label htmlFor="port-f3" className="block text-xs font-medium text-foreground mb-1">
                  Días de Cobertura Semanal
                </label>
                <input id="port-f3"
                  type="text"
                  value={nuevoTurno.diasSemana}
                  onChange={(e) => setNuevoTurno({ ...nuevoTurno, diasSemana: e.target.value })}
                  placeholder="Ej: Lunes a Domingo / Lunes a Viernes"
                  className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                />
              </div>

              <Button
                onClick={handleAsignarTurno}
                disabled={savingTurno || !nuevoTurno.idUsuario}
                className="w-full flex items-center justify-center gap-2 mt-2"
              >
                <Plus className="w-4 h-4" />
                {savingTurno ? 'Asignando Turno...' : 'Asignar Turno a Portero'}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Confirmación Eliminar Portería */}
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
