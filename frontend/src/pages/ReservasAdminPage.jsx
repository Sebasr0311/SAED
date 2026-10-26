import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import {
  AlertTriangle,
  Ban,
  CalendarCheck,
  CheckCircle2,
  Clock,
  PauseCircle,
  Pencil,
  PlayCircle,
  Plus,
  Search,
  Trash2,
  Trees,
  Wrench,
  XCircle,
} from 'lucide-react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS_RESERVA = ['PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'];
const PAGE_SIZE_RESERVAS = 15;
const PAGE_SIZE_ZONAS = 10;

const ESTADO_RESERVA_BADGE = {
  PENDIENTE: 'warning',
  APROBADA: 'success',
  RECHAZADA: 'destructive',
  CANCELADA: 'secondary',
};

const ESTADO_RESERVA_LABEL = {
  PENDIENTE: 'Pendiente',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
  CANCELADA: 'Cancelada',
};

const ICON_BTN =
  'inline-flex h-9 w-9 items-center justify-center rounded-md text-text-secondary transition-colors duration-150 hover:bg-surface-dim hover:text-on-surface';
const SEARCH_INPUT =
  'h-9 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted transition-[border-color,box-shadow] duration-150 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25';
const LABEL = 'text-xs font-semibold text-text-secondary';

const ESTADOS_ZONA = ['ACTIVA', 'MANTENIMIENTO', 'INACTIVA'];
const TIPOS_ZONA = ['SOCIAL', 'BBQ', 'DEPORTIVA', 'ACUATICA', 'JUEGOS', 'GIMNASIO', 'COWORKING', 'OTRO'];

const ESTADO_ZONA_BADGE = {
  ACTIVA: 'success',
  MANTENIMIENTO: 'warning',
  INACTIVA: 'destructive',
};

const formatCOP = (val) =>
  Number(val || 0).toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

const formatCurrency = (val) => {
  const num = Number(val || 0);
  if (num === 0) return 'Gratuita';
  return formatCOP(num);
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
    { key: 'nombreZona', label: 'Zona común' },
    { key: 'fechaReserva', label: 'Día reservado', cellClassName: 'tabular-nums whitespace-nowrap', render: (r) => r.fechaReserva },
    { key: 'horario', label: 'Horario', cellClassName: 'tabular-nums whitespace-nowrap', render: (r) => `${r.horaInicio} - ${r.horaFin}` },
    { key: 'cantidadAsistentes', label: 'Asistentes', cellClassName: 'tabular-nums' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => (
        <Badge variant={ESTADO_RESERVA_BADGE[r.estado] || 'secondary'}>
          {ESTADO_RESERVA_LABEL[r.estado] || r.estado}
        </Badge>
      ),
    },
    { key: 'fechaSolicitud', label: 'Solicitado el', cellClassName: 'tabular-nums whitespace-nowrap', render: (r) => formatDate(r.fechaSolicitud) },
  ];

  const columnsZonas = [
    {
      key: 'nombre',
      label: 'Nombre de la zona',
      render: (z) => (
        <div className="flex items-center gap-2">
          <Trees className="h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
          <span className="font-semibold text-on-surface">{z.nombre}</span>
        </div>
      ),
    },
    {
      key: 'tipo',
      label: 'Tipo',
      render: (z) => <Badge variant="secondary">{z.tipo}</Badge>,
    },
    {
      key: 'aforoMaximo',
      label: 'Aforo máx.',
      cellClassName: 'tabular-nums whitespace-nowrap',
      render: (z) => `${z.aforoMaximo} pers.`,
    },
    {
      key: 'requiereReserva',
      label: 'Req. reserva',
      render: (z) => (
        <Badge variant={z.requiereReserva === 'S' ? 'success' : 'secondary'}>
          {z.requiereReserva === 'S' ? 'Sí' : 'No'}
        </Badge>
      ),
    },
    {
      key: 'costoReserva',
      label: 'Costo',
      cellClassName: 'tabular-nums whitespace-nowrap',
      render: (z) => formatCurrency(z.costoReserva),
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (z) => (
        <Badge variant={ESTADO_ZONA_BADGE[z.estado] || 'secondary'}>
          {z.estado === 'ACTIVA' ? 'Activa' : z.estado === 'MANTENIMIENTO' ? 'Mantenimiento' : 'Inactiva'}
        </Badge>
      ),
    },
    {
      key: 'acciones',
      label: 'Acciones',
      render: (z) => (
        <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
          <button
            type="button"
            className={ICON_BTN}
            onClick={() => openEditZona(z)}
            title="Editar zona común"
            aria-label={`Editar ${z.nombre}`}
          >
            <Pencil className="h-4 w-4" aria-hidden="true" />
          </button>
          {z.estado === 'ACTIVA' && (
            <button
              type="button"
              className={`${ICON_BTN} text-tint-amber-fg hover:bg-tint-amber-bg hover:text-tint-amber-fg`}
              onClick={() => toggleMantenimiento(z)}
              title="Pausar por mantenimiento"
              aria-label={`Pausar ${z.nombre} por mantenimiento`}
            >
              <PauseCircle className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
          {z.estado === 'MANTENIMIENTO' && (
            <button
              type="button"
              className={`${ICON_BTN} text-tint-green-fg hover:bg-tint-green-bg hover:text-tint-green-fg`}
              onClick={() => toggleMantenimiento(z)}
              title="Reanudar disponibilidad"
              aria-label={`Reanudar disponibilidad de ${z.nombre}`}
            >
              <PlayCircle className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
          {z.estado !== 'INACTIVA' && (
            <button
              type="button"
              className={`${ICON_BTN} text-destructive hover:bg-tint-red-bg hover:text-destructive`}
              onClick={() => confirmDeleteZona(z)}
              title="Desactivar (baja lógica)"
              aria-label={`Desactivar ${z.nombre}`}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>
      ),
    },
  ];

  const TAB_CLASS = (active) =>
    `inline-flex min-h-10 shrink-0 items-center gap-2 rounded-md px-3 text-[13px] font-semibold transition-colors duration-150 ${
      active ? 'bg-surface text-primary-text shadow-sm' : 'text-text-secondary hover:text-on-surface'
    }`;

  return (
    <PageContainer>
      <PageHeader
        title="Gestión de Reservas y Zonas Comunes"
        subtitle="Zonas comunes de la copropiedad y solicitudes de reserva de los residentes."
        action={
          activeTab === 'zonas' ? (
            <Button onClick={openCreateZona}>
              <Plus className="h-4 w-4 mr-1.5" aria-hidden="true" />
              Nueva Zona Común
            </Button>
          ) : null
        }
      />

      {/* Tabs */}
      <div role="tablist" aria-label="Secciones de reservas" className="flex w-fit max-w-full gap-1 overflow-x-auto rounded-lg border border-border bg-surface-dim p-1">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'solicitudes'}
          className={TAB_CLASS(activeTab === 'solicitudes')}
          onClick={() => setActiveTab('solicitudes')}
        >
          <CalendarCheck className="h-4 w-4" aria-hidden="true" />
          <span>Solicitudes de Reserva <span className="tabular-nums">({statsReservas.pendientes} pendientes)</span></span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'zonas'}
          className={TAB_CLASS(activeTab === 'zonas')}
          onClick={() => setActiveTab('zonas')}
        >
          <Trees className="h-4 w-4" aria-hidden="true" />
          <span>Catálogo de Zonas Comunes <span className="tabular-nums">({allZonas.length})</span></span>
        </button>
      </div>

      {/* Reservation requests */}
      {activeTab === 'solicitudes' && (
        <>
          <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <MetricCard icon={CalendarCheck} value={!data ? '—' : statsReservas.total} label="Total reservas" variant="primary" />
            <MetricCard icon={Clock} value={!data ? '—' : statsReservas.pendientes} label="Pendientes" variant={statsReservas.pendientes > 0 ? 'warning' : 'secondary'} />
            <MetricCard icon={CheckCircle2} value={!data ? '—' : statsReservas.aprobadas} label="Aprobadas" variant="success" />
            <MetricCard icon={XCircle} value={!data ? '—' : statsReservas.rechazadas} label="Rechazadas" variant="danger" />
          </div>

          <div className="rounded-lg border border-border bg-card p-3 shadow-sm">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-[14rem_minmax(0,1fr)]">
              <Select
                id="f-estado"
                aria-label="Filtrar por estado"
                value={filtroEstado}
                onChange={(e) => {
                  setFiltroEstado(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">Todos los estados</option>
                {ESTADOS_RESERVA.map((e) => (
                  <option key={e} value={e}>
                    {ESTADO_RESERVA_LABEL[e] || e}
                  </option>
                ))}
              </Select>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
                <input
                  id="search"
                  aria-label="Buscar"
                  type="search"
                  placeholder="Buscar por zona..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className={SEARCH_INPUT}
                />
              </div>
            </div>
          </div>

          {error && !data ? (
            <ErrorState
              title="No se pudieron cargar las reservas"
              message={error?.message || 'Verifique su conexión e intente nuevamente.'}
              onRetry={() => refetch().catch(() => {})}
            />
          ) : (
            <DataTable
              columns={columnsReservas}
              rows={rowsReservas}
              loading={loading && !data}
              empty={
                filtroEstado || search
                  ? { icon: 'filter_alt_off', title: 'Sin resultados', subtitle: 'Ninguna reserva coincide con los filtros aplicados.' }
                  : { icon: 'event_busy', title: 'No hay reservas', subtitle: 'Las solicitudes de los residentes aparecerán aquí.' }
              }
              keyField="idReserva"
              onRowClick={openDetalle}
            />
          )}
          <Pagination
            page={safePageReservas}
            totalPages={totalPagesReservas}
            totalItems={filtradasReservas.length}
            pageSize={PAGE_SIZE_RESERVAS}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Common-area catalog (GAP-F8-05) */}
      {activeTab === 'zonas' && (
        <>
          <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <MetricCard icon={Trees} value={!zonasData ? '—' : statsZonas.total} label="Total zonas" variant="primary" />
            <MetricCard icon={CheckCircle2} value={!zonasData ? '—' : statsZonas.activas} label="Activas" variant="success" />
            <MetricCard icon={Wrench} value={!zonasData ? '—' : statsZonas.mantenimiento} label="En mantenimiento" variant="warning" />
            <MetricCard icon={Ban} value={!zonasData ? '—' : statsZonas.inactivas} label="Inactivas (baja)" variant="secondary" />
          </div>

          <div className="rounded-lg border border-border bg-card p-3 shadow-sm">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-[14rem_14rem_minmax(0,1fr)]">
              <Select
                id="f-zona-estado"
                aria-label="Filtrar zonas por estado"
                value={filtroZonaEstado}
                onChange={(e) => {
                  setFiltroZonaEstado(e.target.value);
                  setZonaPage(0);
                }}
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
                aria-label="Filtrar zonas por tipo"
                value={filtroZonaTipo}
                onChange={(e) => {
                  setFiltroZonaTipo(e.target.value);
                  setZonaPage(0);
                }}
              >
                <option value="">Todos los tipos</option>
                {TIPOS_ZONA.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </Select>

              <div className="relative sm:col-span-2 lg:col-span-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
                <input
                  id="search-zona"
                  aria-label="Buscar zona común"
                  type="search"
                  placeholder="Buscar por nombre o tipo..."
                  value={searchZona}
                  onChange={(e) => {
                    setSearchZona(e.target.value);
                    setZonaPage(0);
                  }}
                  className={SEARCH_INPUT}
                />
              </div>
            </div>
          </div>

          {errorZonas && !zonasData ? (
            <ErrorState
              title="No se pudo cargar el catálogo de zonas"
              message={errorZonas?.message || 'Verifique su conexión e intente nuevamente.'}
              onRetry={() => refetchZonas().catch(() => {})}
            />
          ) : (
            <DataTable
              columns={columnsZonas}
              rows={rowsZonas}
              loading={loadingZonas && !zonasData}
              empty={
                filtroZonaEstado || filtroZonaTipo || searchZona
                  ? { icon: 'filter_alt_off', title: 'Sin resultados', subtitle: 'Ninguna zona coincide con los filtros aplicados.' }
                  : { icon: 'deck', title: 'No hay zonas comunes', subtitle: 'Cree la primera zona común con el botón "Nueva Zona Común".' }
              }
              keyField="idZona"
            />
          )}
          <Pagination
            page={safePageZonas}
            totalPages={totalPagesZonas}
            totalItems={filtradasZonas.length}
            pageSize={PAGE_SIZE_ZONAS}
            onPageChange={setZonaPage}
          />
        </>
      )}

      {/* Reservation detail / status change */}
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
            <Button onClick={saveEstadoReserva} disabled={saving} loading={saving}>
              {saving ? 'Guardando...' : 'Actualizar Estado'}
            </Button>
          </>
        }
      >
        {modal && (
          <div className="space-y-4 text-[13px]">
            <dl className="grid grid-cols-2 gap-3">
              <div>
                <dt className={LABEL}>Fecha reservada</dt>
                <dd className="tabular-nums text-on-surface">{modal.fechaReserva}</dd>
              </div>
              <div>
                <dt className={LABEL}>Horario</dt>
                <dd className="tabular-nums text-on-surface">{modal.horaInicio} - {modal.horaFin}</dd>
              </div>
              <div>
                <dt className={LABEL}>Asistentes</dt>
                <dd className="tabular-nums text-on-surface">{modal.cantidadAsistentes}</dd>
              </div>
              <div>
                <dt className={LABEL}>Costo total</dt>
                <dd className="tabular-nums text-on-surface">{formatCOP(modal.costoTotal)}</dd>
              </div>
            </dl>

            <div>
              <div className={LABEL}>Observaciones</div>
              <div className="whitespace-pre-wrap rounded-md border border-border bg-surface-dim p-3 text-on-surface">
                {modal.observaciones || 'Sin observaciones'}
              </div>
            </div>

            <div className="border-t border-border pt-4">
              <Select
                id="estado"
                label="Estado de la Reserva"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS_RESERVA.map((e) => (
                  <option key={e} value={e}>
                    {ESTADO_RESERVA_LABEL[e] || e}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        )}
      </Modal>

      {/* Create / edit common area (GAP-F8-05) */}
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
            <Button onClick={handleSaveZona} disabled={savingZona} loading={savingZona}>
              {savingZona ? 'Guardando...' : modalZona?.isNew ? 'Crear Zona Común' : 'Guardar Cambios'}
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4">
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

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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

      {/* Soft-delete confirmation */}
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
            <Button variant="danger" onClick={handleDeleteZona} disabled={deletingZona} loading={deletingZona}>
              {deletingZona ? 'Desactivando...' : 'Desactivar Zona'}
            </Button>
          </>
        }
      >
        <div className="space-y-3 py-1 text-[13px]">
          <p className="text-sm text-on-surface">
            ¿Está seguro de que desea desactivar la zona común <strong>"{modalDeleteZona?.nombre}"</strong>?
          </p>
          <div className="flex items-start gap-2 rounded-md border border-tint-amber-fg/25 bg-tint-amber-bg p-3 text-tint-amber-fg">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p>
              La zona pasará a estado <strong>INACTIVA</strong>. Las reservas históricas existentes se conservarán para auditoría, pero los residentes ya no podrán agendar nuevas reservas en esta área.
            </p>
          </div>
        </div>
      </Modal>
    </PageContainer>
  );
}
