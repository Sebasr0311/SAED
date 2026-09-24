import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS_RESERVA = ['PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'];
const PAGE_SIZE_RESERVAS = 15;
const PAGE_SIZE_ZONAS = 10;

const ESTADO_RESERVA_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  APROBADA: 'badge-activo',
  RECHAZADA: 'badge-danger',
  CANCELADA: 'badge-neutral',
};

const ESTADOS_ZONA = ['ACTIVA', 'MANTENIMIENTO', 'INACTIVA'];
const TIPOS_ZONA = ['SOCIAL', 'BBQ', 'DEPORTIVA', 'ACUATICA', 'JUEGOS', 'GIMNASIO', 'COWORKING', 'OTRO'];

const ESTADO_ZONA_BADGE = {
  ACTIVA: 'badge-activo',
  MANTENIMIENTO: 'badge-pendiente-firma',
  INACTIVA: 'badge-danger',
};

const formatCurrency = (val) => {
  const num = Number(val || 0);
  if (num === 0) return 'Gratuita';
  return '$' + num.toLocaleString('es-CO');
};

const defaultZonaForm = {
  nombre: '',
  tipo: 'SOCIAL',
  aforoMaximo: 20,
  requiereReserva: 'S',
  costoReserva: 0,
  estado: 'ACTIVA',
};

export default function ReservasAdminPage() {
  // Pestaña activa: 'solicitudes' | 'zonas'
  const [activeTab, setActiveTab] = useState('solicitudes');

  // ---------------------------------------------------------------------------
  // Estado para Solicitudes de Reserva (Preservado)
  // ---------------------------------------------------------------------------
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ estado: '' });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, refetch } = useFetch(() => api.get('/reservas/todas'), []);
  const all = Array.isArray(data) ? data : data?.items || [];

  const statsReservas = {
    total: all.length,
    pendientes: all.filter((i) => i.estado === 'PENDIENTE').length,
    aprobadas: all.filter((i) => i.estado === 'APROBADA').length,
    rechazadas: all.filter((i) => i.estado === 'RECHAZADA').length,
  };

  const filtradasReservas = all.filter((i) => {
    if (filtroEstado && i.estado !== filtroEstado) return false;
    if (search) {
      const q = search.toLowerCase();
      if (!i.nombreZona?.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });

  const totalPagesReservas = Math.ceil(filtradasReservas.length / PAGE_SIZE_RESERVAS) || 1;
  const safePageReservas = Math.min(page, totalPagesReservas - 1);
  const rowsReservas = filtradasReservas.slice(safePageReservas * PAGE_SIZE_RESERVAS, (safePageReservas + 1) * PAGE_SIZE_RESERVAS);

  const openDetalle = (row) => {
    setForm({ estado: row.estado });
    setModal(row);
  };

  const saveEstadoReserva = async () => {
    if (!form.estado) return;
    setSaving(true);
    try {
      await api.put(`/reservas/${modal.idReserva}/estado`, { estado: form.estado });
      toast.success('Estado de reserva actualizado');
      setModal(null);
      refetch();
    } catch (err) {
      toast.error('Error al actualizar reserva');
    } finally {
      setSaving(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Estado para Catálogo de Zonas Comunes (GAP-F8-05)
  // ---------------------------------------------------------------------------
  const [zonaPage, setZonaPage] = useState(0);
  const [filtroZonaEstado, setFiltroZonaEstado] = useState('');
  const [filtroZonaTipo, setFiltroZonaTipo] = useState('');
  const [searchZona, setSearchZona] = useState('');

  const [modalZona, setModalZona] = useState(null); // null o objeto zona (con isNew flag)
  const [formZona, setFormZona] = useState(defaultZonaForm);
  const [formZonaErrors, setFormZonaErrors] = useState({});
  const [savingZona, setSavingZona] = useState(false);

  const [modalDeleteZona, setModalDeleteZona] = useState(null);
  const [deletingZona, setDeletingZona] = useState(false);

  const { data: zonasData, loading: loadingZonas, error: errorZonas, refetch: refetchZonas } = useFetch(
    () => api.get('/zonas-comunes'),
    []
  );
  const allZonas = Array.isArray(zonasData) ? zonasData : zonasData?.items || [];

  const statsZonas = {
    total: allZonas.length,
    activas: allZonas.filter((z) => z.estado === 'ACTIVA').length,
    mantenimiento: allZonas.filter((z) => z.estado === 'MANTENIMIENTO').length,
    inactivas: allZonas.filter((z) => z.estado === 'INACTIVA').length,
  };

  const filtradasZonas = allZonas.filter((z) => {
    if (filtroZonaEstado && z.estado !== filtroZonaEstado) return false;
    if (filtroZonaTipo && z.tipo !== filtroZonaTipo) return false;
    if (searchZona) {
      const q = searchZona.toLowerCase();
      const matchNombre = z.nombre?.toLowerCase().includes(q);
      const matchTipo = z.tipo?.toLowerCase().includes(q);
      if (!matchNombre && !matchTipo) return false;
    }
    return true;
  });

  const totalPagesZonas = Math.ceil(filtradasZonas.length / PAGE_SIZE_ZONAS) || 1;
  const safePageZonas = Math.min(zonaPage, totalPagesZonas - 1);
  const rowsZonas = filtradasZonas.slice(safePageZonas * PAGE_SIZE_ZONAS, (safePageZonas + 1) * PAGE_SIZE_ZONAS);

  const openCreateZona = () => {
    setFormZona(defaultZonaForm);
    setFormZonaErrors({});
    setModalZona({ isNew: true });
  };

  const openEditZona = (zona) => {
    setFormZona({
      nombre: zona.nombre || '',
      tipo: zona.tipo || 'SOCIAL',
      aforoMaximo: zona.aforoMaximo || 20,
      requiereReserva: zona.requiereReserva || 'S',
      costoReserva: zona.costoReserva != null ? zona.costoReserva : 0,
      estado: zona.estado || 'ACTIVA',
    });
    setFormZonaErrors({});
    setModalZona(zona);
  };

  const toggleMantenimiento = async (zona) => {
    const nuevoEstado = zona.estado === 'ACTIVA' ? 'MANTENIMIENTO' : 'ACTIVA';
    try {
      await api.put(`/zonas-comunes/${zona.idZona}`, {
        nombre: zona.nombre,
        tipo: zona.tipo,
        aforoMaximo: zona.aforoMaximo,
        requiereReserva: zona.requiereReserva,
        costoReserva: zona.costoReserva,
        estado: nuevoEstado,
      });
      toast.success(
        nuevoEstado === 'MANTENIMIENTO'
          ? `Zona "${zona.nombre}" puesta en mantenimiento`
          : `Zona "${zona.nombre}" reactivada para reservas`
      );
      refetchZonas();
    } catch (err) {
      toast.error('Error al cambiar estado de mantenimiento');
    }
  };

  const confirmDeleteZona = (zona) => {
    setModalDeleteZona(zona);
  };

  const handleDeleteZona = async () => {
    if (!modalDeleteZona) return;
    setDeletingZona(true);
    try {
      await api.delete(`/zonas-comunes/${modalDeleteZona.idZona}`);
      toast.success(`Zona "${modalDeleteZona.nombre}" desactivada (baja lógica) exitosamente`);
      setModalDeleteZona(null);
      refetchZonas();
    } catch (err) {
      toast.error(err.message || 'Error al desactivar zona común');
    } finally {
      setDeletingZona(false);
    }
  };

  const handleSaveZona = async () => {
    const errors = {};
    if (!formZona.nombre?.trim()) {
      errors.nombre = 'El nombre de la zona es obligatorio';
    }
    if (!formZona.aforoMaximo || Number(formZona.aforoMaximo) < 1) {
      errors.aforoMaximo = 'El aforo debe ser al menos 1';
    }
    if (formZona.costoReserva == null || Number(formZona.costoReserva) < 0) {
      errors.costoReserva = 'El costo no puede ser negativo';
    }

    if (Object.keys(errors).length > 0) {
      setFormZonaErrors(errors);
      return;
    }

    setSavingZona(true);
    try {
      const payload = {
        nombre: formZona.nombre.trim(),
        tipo: formZona.tipo,
        aforoMaximo: Number(formZona.aforoMaximo),
        requiereReserva: formZona.requiereReserva,
        costoReserva: Number(formZona.costoReserva || 0),
        estado: formZona.estado,
      };

      if (modalZona?.isNew) {
        await api.post('/zonas-comunes', payload);
        toast.success('Zona común creada exitosamente');
      } else {
        await api.put(`/zonas-comunes/${modalZona.idZona}`, payload);
        toast.success('Zona común actualizada exitosamente');
      }

      setModalZona(null);
      refetchZonas();
    } catch (err) {
      if (err.status === 409 || err.code === 'ZONA_NOMBRE_DUPLICADO' || err.message?.includes('ZONA_NOMBRE_DUPLICADO')) {
        toast.error('Ya existe una zona común con este nombre en la copropiedad');
        setFormZonaErrors({ nombre: 'Nombre duplicado en la propiedad' });
      } else {
        toast.error(err.message || 'Error al guardar la zona común');
      }
    } finally {
      setSavingZona(false);
    }
  };

  // ---------------------------------------------------------------------------
  // Columnas de Tablas
  // ---------------------------------------------------------------------------
  const columnsReservas = [
    { key: 'nombreZona', label: 'Zona Común' },
    { key: 'fechaReserva', label: 'Día Reservado', render: (r) => r.fechaReserva },
    { key: 'horario', label: 'Horario', render: (r) => `${r.horaInicio} - ${r.horaFin}` },
    { key: 'cantidadAsistentes', label: 'Asistentes' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => (
        <span className={`badge ${ESTADO_RESERVA_BADGE[r.estado] || 'badge-neutral'}`}>
          {r.estado}
        </span>
      ),
    },
    { key: 'fechaSolicitud', label: 'Solicitado el', render: (r) => formatDate(r.fechaSolicitud) },
  ];

  const columnsZonas = [
    {
      key: 'nombre',
      label: 'Nombre de la Zona',
      render: (z) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span className="material-symbols-outlined" style={{ fontSize: '20px', color: 'var(--primary, #2563eb)' }}>
            deck
          </span>
          <span style={{ fontWeight: 600 }}>{z.nombre}</span>
        </div>
      ),
    },
    {
      key: 'tipo',
      label: 'Tipo',
      render: (z) => <span className="badge badge-neutral">{z.tipo}</span>,
    },
    {
      key: 'aforoMaximo',
      label: 'Aforo Máx.',
      render: (z) => `${z.aforoMaximo} pers.`,
    },
    {
      key: 'requiereReserva',
      label: 'Req. Reserva',
      render: (z) => (
        <span className={`badge ${z.requiereReserva === 'S' ? 'badge-activo' : 'badge-neutral'}`}>
          {z.requiereReserva === 'S' ? 'Sí' : 'No'}
        </span>
      ),
    },
    {
      key: 'costoReserva',
      label: 'Costo',
      render: (z) => formatCurrency(z.costoReserva),
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (z) => (
        <span className={`badge ${ESTADO_ZONA_BADGE[z.estado] || 'badge-neutral'}`}>
          {z.estado === 'ACTIVA' ? 'Activa' : z.estado === 'MANTENIMIENTO' ? 'Mantenimiento' : 'Inactiva'}
        </span>
      ),
    },
    {
      key: 'acciones',
      label: 'Acciones',
      render: (z) => (
        <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }} onClick={(e) => e.stopPropagation()}>
          <Button
            size="sm"
            variant="outline"
            onClick={() => openEditZona(z)}
            title="Editar zona común"
          >
            <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>edit</span>
          </Button>
          {z.estado === 'ACTIVA' && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => toggleMantenimiento(z)}
              title="Pausar por mantenimiento"
              style={{ color: 'var(--warning-text, #b45309)' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>pause_circle</span>
            </Button>
          )}
          {z.estado === 'MANTENIMIENTO' && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => toggleMantenimiento(z)}
              title="Reanudar disponibilidad"
              style={{ color: 'var(--success-text, #15803d)' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>play_circle</span>
            </Button>
          )}
          {z.estado !== 'INACTIVA' && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => confirmDeleteZona(z)}
              title="Desactivar (baja lógica)"
              style={{ color: 'var(--danger-text, #b91c1c)' }}
            >
              <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>delete</span>
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Gestión de Reservas y Zonas Comunes"
        subtitle="Administra las zonas comunes de la copropiedad y las solicitudes de reserva de residentes"
      />

      {/* Tabs Navigation */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '20px', borderBottom: '1px solid var(--border-color)', paddingBottom: '12px' }}>
        <Button
          variant={activeTab === 'solicitudes' ? 'primary' : 'outline'}
          onClick={() => setActiveTab('solicitudes')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: '18px', marginRight: '6px', verticalAlign: 'middle' }}>event_available</span>
          Solicitudes de Reserva ({statsReservas.pendientes} pendientes)
        </Button>
        <Button
          variant={activeTab === 'zonas' ? 'primary' : 'outline'}
          onClick={() => setActiveTab('zonas')}
        >
          <span className="material-symbols-outlined" style={{ fontSize: '18px', marginRight: '6px', verticalAlign: 'middle' }}>deck</span>
          Catálogo de Zonas Comunes ({allZonas.length})
        </Button>
      </div>

      {/* ===================================================================== */}
      {/* VISTA 1: SOLICITUDES DE RESERVA                                      */}
      {/* ===================================================================== */}
      {activeTab === 'solicitudes' && (
        <>
          <div className="card-grid-4" style={{ marginBottom: '20px' }}>
            <StatCard icon="event_available" value={statsReservas.total} label="Total Reservas" color="primary" />
            <StatCard icon="pending_actions" value={statsReservas.pendientes} label="Pendientes" color="amber" />
            <StatCard icon="check_circle" value={statsReservas.aprobadas} label="Aprobadas" color="green" />
            <StatCard icon="cancel" value={statsReservas.rechazadas} label="Rechazadas" color="danger" />
          </div>

          <div className="card" style={{ marginBottom: '16px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
              <Select
                id="f-estado"
                value={filtroEstado}
                onChange={(e) => {
                  setFiltroEstado(e.target.value);
                  setPage(0);
                }}
                className="filter-select"
                style={{ width: '180px' }}
              >
                <option value="">Todos los estados</option>
                {ESTADOS_RESERVA.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </Select>
              <input
                id="search"
                aria-label="Buscar"
                type="text"
                placeholder="Buscar por zona..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="form-control"
                style={{ flex: 1, minWidth: '200px' }}
              />
            </div>
          </div>

          <DataTable
            columns={columnsReservas}
            rows={rowsReservas}
            loading={loading}
            empty={{ icon: 'event_busy', title: 'No hay reservas', subtitle: 'Las solicitudes de los residentes aparecerán aquí.' }}
            error={error?.message}
            keyField="idReserva"
            onRowClick={openDetalle}
          />
          <Pagination
            page={safePageReservas}
            totalPages={totalPagesReservas}
            totalItems={filtradasReservas.length}
            pageSize={PAGE_SIZE_RESERVAS}
            onPageChange={setPage}
          />
        </>
      )}

      {/* ===================================================================== */}
      {/* VISTA 2: CATÁLOGO DE ZONAS COMUNES (GAP-F8-05)                       */}
      {/* ===================================================================== */}
      {activeTab === 'zonas' && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <div>
              <h3 style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>Catálogo de Zonas Comunes</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '4px 0 0 0' }}>
                Crea, edita y administra el estado y aforo de las zonas comunes de tu propiedad.
              </p>
            </div>
            <Button onClick={openCreateZona}>
              <span className="material-symbols-outlined" style={{ fontSize: '18px', marginRight: '6px', verticalAlign: 'middle' }}>add</span>
              Nueva Zona Común
            </Button>
          </div>

          <div className="card-grid-4" style={{ marginBottom: '20px' }}>
            <StatCard icon="deck" value={statsZonas.total} label="Total Zonas" color="primary" />
            <StatCard icon="check_circle" value={statsZonas.activas} label="Activas" color="green" />
            <StatCard icon="build" value={statsZonas.mantenimiento} label="En Mantenimiento" color="amber" />
            <StatCard icon="block" value={statsZonas.inactivas} label="Inactivas (Baja)" color="danger" />
          </div>

          <div className="card" style={{ marginBottom: '16px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
              <Select
                id="f-zona-estado"
                value={filtroZonaEstado}
                onChange={(e) => {
                  setFiltroZonaEstado(e.target.value);
                  setZonaPage(0);
                }}
                className="filter-select"
                style={{ width: '180px' }}
              >
                <option value="">Todos los estados</option>
                {ESTADOS_ZONA.map((e) => (
                  <option key={e} value={e}>
                    {e === 'ACTIVA' ? 'Activa' : e === 'MANTENIMIENTO' ? 'En Mantenimiento' : 'Inactiva (Baja)'}
                  </option>
                ))}
              </Select>

              <Select
                id="f-zona-tipo"
                value={filtroZonaTipo}
                onChange={(e) => {
                  setFiltroZonaTipo(e.target.value);
                  setZonaPage(0);
                }}
                className="filter-select"
                style={{ width: '180px' }}
              >
                <option value="">Todos los tipos</option>
                {TIPOS_ZONA.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </Select>

              <input
                id="search-zona"
                aria-label="Buscar zona común"
                type="text"
                placeholder="Buscar por nombre o tipo..."
                value={searchZona}
                onChange={(e) => {
                  setSearchZona(e.target.value);
                  setZonaPage(0);
                }}
                className="form-control"
                style={{ flex: 1, minWidth: '200px' }}
              />
            </div>
          </div>

          <DataTable
            columns={columnsZonas}
            rows={rowsZonas}
            loading={loadingZonas}
            empty={{ icon: 'deck', title: 'No hay zonas comunes', subtitle: 'Crea tu primera zona común con el botón superior.' }}
            error={errorZonas?.message}
            keyField="idZona"
          />
          <Pagination
            page={safePageZonas}
            totalPages={totalPagesZonas}
            totalItems={filtradasZonas.length}
            pageSize={PAGE_SIZE_ZONAS}
            onPageChange={setZonaPage}
          />
        </>
      )}

      {/* ===================================================================== */}
      {/* MODAL: DETALLE / CAMBIO DE ESTADO DE RESERVA                         */}
      {/* ===================================================================== */}
      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={`Detalle Reserva: ${modal?.nombreZona}`}
        size="md"
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(null)} disabled={saving}>
              Cerrar
            </Button>
            <Button onClick={saveEstadoReserva} disabled={saving}>
              {saving ? 'Guardando...' : 'Actualizar Estado'}
            </Button>
          </>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Fecha Reservada</div>
                <div style={{ fontSize: '13px' }}>{modal.fechaReserva}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Horario</div>
                <div style={{ fontSize: '13px' }}>{modal.horaInicio} - {modal.horaFin}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Asistentes</div>
                <div style={{ fontSize: '13px' }}>{modal.cantidadAsistentes}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Costo Total</div>
                <div style={{ fontSize: '13px' }}>${Number(modal.costoTotal || 0).toLocaleString()}</div>
              </div>
            </div>

            <div className="form-group">
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Observaciones</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                {modal.observaciones || 'Sin observaciones'}
              </div>
            </div>

            <hr style={{ margin: '20px 0', borderColor: 'var(--border-color)' }} />

            <div className="form-group">
              <Select
                id="estado"
                label="Estado de la Reserva"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS_RESERVA.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </Select>
            </div>
          </>
        )}
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: CREAR / EDITAR ZONA COMÚN (GAP-F8-05)                          */}
      {/* ===================================================================== */}
      <Modal
        open={!!modalZona}
        onClose={() => setModalZona(null)}
        title={modalZona?.isNew ? 'Nueva Zona Común' : `Editar Zona Común: ${modalZona?.nombre}`}
        size="md"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalZona(null)} disabled={savingZona}>
              Cancelar
            </Button>
            <Button onClick={handleSaveZona} disabled={savingZona}>
              {savingZona ? 'Guardando...' : modalZona?.isNew ? 'Crear Zona Común' : 'Guardar Cambios'}
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <Input
            id="form-nombre"
            label="Nombre de la Zona Común"
            required
            maxLength={100}
            placeholder="Ej. Piscina Climatizada, Salón Social Norte"
            value={formZona.nombre}
            error={formZonaErrors.nombre}
            onChange={(e) => {
              setFormZona({ ...formZona, nombre: e.target.value });
              if (formZonaErrors.nombre) setFormZonaErrors({ ...formZonaErrors, nombre: null });
            }}
          />

          <div className="card-grid-2" style={{ gap: '16px' }}>
            <Select
              id="form-tipo"
              label="Tipo de Zona"
              value={formZona.tipo}
              onChange={(e) => setFormZona({ ...formZona, tipo: e.target.value })}
            >
              {TIPOS_ZONA.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>

            <Input
              id="form-aforo"
              label="Aforo Máximo"
              type="number"
              min={1}
              required
              value={formZona.aforoMaximo}
              error={formZonaErrors.aforoMaximo}
              onChange={(e) => {
                setFormZona({ ...formZona, aforoMaximo: e.target.value });
                if (formZonaErrors.aforoMaximo) setFormZonaErrors({ ...formZonaErrors, aforoMaximo: null });
              }}
            />
          </div>

          <div className="card-grid-2" style={{ gap: '16px' }}>
            <Select
              id="form-requiere-reserva"
              label="¿Requiere Reserva?"
              value={formZona.requiereReserva}
              onChange={(e) => setFormZona({ ...formZona, requiereReserva: e.target.value })}
            >
              <option value="S">Sí (Requiere reserva previa)</option>
              <option value="N">No (Uso libre / sin reserva)</option>
            </Select>

            <Input
              id="form-costo"
              label="Costo de Reserva (COP)"
              type="number"
              min={0}
              step={1000}
              value={formZona.costoReserva}
              error={formZonaErrors.costoReserva}
              onChange={(e) => {
                setFormZona({ ...formZona, costoReserva: e.target.value });
                if (formZonaErrors.costoReserva) setFormZonaErrors({ ...formZonaErrors, costoReserva: null });
              }}
            />
          </div>

          <Select
            id="form-estado"
            label="Estado Inicial"
            value={formZona.estado}
            onChange={(e) => setFormZona({ ...formZona, estado: e.target.value })}
          >
            {ESTADOS_ZONA.map((e) => (
              <option key={e} value={e}>
                {e === 'ACTIVA' ? 'Activa (Disponible)' : e === 'MANTENIMIENTO' ? 'En Mantenimiento' : 'Inactiva (Desactivada)'}
              </option>
            ))}
          </Select>
        </div>
      </Modal>

      {/* ===================================================================== */}
      {/* MODAL: CONFIRMACIÓN DE BAJA LÓGICA (SOFT DELETE)                      */}
      {/* ===================================================================== */}
      <Modal
        open={!!modalDeleteZona}
        onClose={() => setModalDeleteZona(null)}
        title="Desactivar Zona Común"
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={() => setModalDeleteZona(null)} disabled={deletingZona}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteZona}
              disabled={deletingZona}
              style={{ background: 'var(--danger, #dc2626)', color: '#fff' }}
            >
              {deletingZona ? 'Desactivando...' : 'Desactivar Zona'}
            </Button>
          </>
        }
      >
        <div style={{ padding: '8px 0' }}>
          <p style={{ fontSize: '14px', color: 'var(--text-primary)', marginBottom: '12px' }}>
            ¿Estás seguro de que deseas desactivar la zona común <strong>"{modalDeleteZona?.nombre}"</strong>?
          </p>
          <div style={{ background: 'var(--bg-secondary, #f8fafc)', padding: '12px', borderRadius: '6px', fontSize: '13px', color: 'var(--text-secondary)' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '18px', verticalAlign: 'middle', marginRight: '6px', color: 'var(--amber, #d97706)' }}>
              warning
            </span>
            La zona pasará a estado <strong>INACTIVA</strong>. Las reservas históricas existentes se conservarán intactas para auditoría, pero los residentes ya no podrán agendar nuevas reservas en esta área.
          </div>
        </div>
      </Modal>
    </div>
  );
}
