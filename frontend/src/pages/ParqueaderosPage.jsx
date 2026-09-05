import { useState, useEffect, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import {
  Car,
  Bike,
  Accessibility,
  Building,
  RefreshCw,
  Search,
  X,
  Plus,
  LayoutGrid,
  List,
  Edit3,
  Trash2,
  CheckCircle2,
  AlertCircle,
  Wrench,
  ShieldCheck,
  Hash,
} from 'lucide-react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import PageContainer from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import {
  Breadcrumb,
  BreadcrumbList,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbSeparator,
  BreadcrumbPage,
} from '../components/ui/Breadcrumb.jsx';

const TIPOS = [
  { id: '', label: 'Todos los tipos' },
  { id: 'VISITANTES', label: 'Visitantes' },
  { id: 'PRIVADO', label: 'Privado (Residente)' },
  { id: 'MOTOS', label: 'Motos' },
  { id: 'BICICLETAS', label: 'Bicicletas' },
  { id: 'DISCAPACITADOS', label: 'Accesibilidad / PMR' },
];

const ESTADOS = [
  { id: '', label: 'Todos los estados' },
  { id: 'DISPONIBLE', label: 'Disponible' },
  { id: 'OCUPADO', label: 'Ocupado' },
  { id: 'EN_MANTENIMIENTO', label: 'En Mantenimiento' },
  { id: 'ASIGNADO', label: 'Asignado' },
];

function getTipoIcon(tipo) {
  const t = String(tipo || '').toUpperCase();
  if (t === 'MOTOS' || t === 'MOTO') return Bike;
  if (t === 'BICICLETAS' || t === 'BICICLETA') return Bike;
  if (t === 'DISCAPACITADOS') return Accessibility;
  return Car;
}

function prefijoPorTipo(tipo, esVisitante) {
  if (tipo === 'MOTOS' || tipo === 'MOTO') return 'M';
  if (tipo === 'BICICLETAS' || tipo === 'BICICLETA') return 'B';
  if (tipo === 'DISCAPACITADOS') return 'D';
  return esVisitante ? 'V' : 'P';
}

const emptyForm = {
  numeroParqueadero: '',
  tipo: 'VISITANTES',
  estado: 'DISPONIBLE',
  esVisitante: true,
  idApartamento: '',
};

export default function ParqueaderosPage() {
  const api = useTenantApi();
  const { isPortero } = useAuth();

  const [viewMode, setViewMode] = useState('grid'); // 'grid' | 'table'
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [search, setSearch] = useState('');

  // Formulario de creación/edición
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);
  const [formErrors, setFormErrors] = useState({});

  // Carga de datos
  const qs = new URLSearchParams({
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });

  const {
    data: rawParqueaderos,
    loading,
    refetch,
  } = useFetch(() => api.get(`/parqueaderos?${qs}`), [filtroEstado, filtroTipo]);

  const { data: rawUnits } = useFetch(() => api.get('/units'), []);

  // Auto-refresh cada 10s cuando la pestaña esté activa
  useEffect(() => {
    const interval = setInterval(() => {
      if (document.visibilityState === 'visible') refetch();
    }, 10000);
    return () => clearInterval(interval);
  }, [refetch]);

  const items = useMemo(() => {
    const list = rawParqueaderos?.items || rawParqueaderos || [];
    return Array.isArray(list) ? list : [];
  }, [rawParqueaderos]);

  const unidades = useMemo(() => {
    const list = rawUnits?.items || rawUnits || [];
    return Array.isArray(list) ? list : [];
  }, [rawUnits]);

  // Generador de código sugerido
  const codigoSugerido = useMemo(() => {
    const prefijo = prefijoPorTipo(form.tipo, form.esVisitante);
    const existentes = items.filter((p) => {
      const c = p.codigo || p.numeroParqueadero || '';
      return c.startsWith(prefijo);
    });
    const nextNum = existentes.length + 1;
    return `${prefijo}-${String(nextNum).padStart(2, '0')}`;
  }, [form.tipo, form.esVisitante, items]);

  // Filtrado de búsqueda
  const filtrados = useMemo(() => {
    return items.filter((p) => {
      if (!search.trim()) return true;
      const term = search.toLowerCase();
      const cod = String(p.codigo || p.numeroParqueadero || '').toLowerCase();
      const tipo = String(p.tipo || '').toLowerCase();
      const est = String(p.estado || '').toLowerCase();
      const placa = String(p.placaVehiculo || '').toLowerCase();
      const apto = String(p.numeroApartamento || '').toLowerCase();
      return (
        cod.includes(term) ||
        tipo.includes(term) ||
        est.includes(term) ||
        placa.includes(term) ||
        apto.includes(term)
      );
    });
  }, [items, search]);

  // Métricas
  const stats = useMemo(() => {
    const total = items.length;
    const disponibles = items.filter((p) => p.estado === 'DISPONIBLE').length;
    const ocupados = items.filter((p) => p.estado === 'OCUPADO').length;
    const visitantesLibres = items.filter(
      (p) =>
        (p.esVisitante || p.tipo === 'VISITANTES') &&
        p.estado === 'DISPONIBLE'
    ).length;
    return { total, disponibles, ocupados, visitantesLibres };
  }, [items]);

  // Guardar (Crear o Editar)
  async function handleSave(e) {
    e?.preventDefault();
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);

    const errs = {};
    const numeroFinal = editing
      ? form.numeroParqueadero
      : (form.numeroParqueadero?.trim() || codigoSugerido);

    if (!numeroFinal) errs.numeroParqueadero = 'Código de parqueadero requerido';
    if (!form.tipo) errs.tipo = 'Tipo de parqueadero requerido';

    setFormErrors(errs);
    if (Object.keys(errs).length > 0) {
      savingRef.current = false;
      setSaving(false);
      return;
    }

    try {
      const payload = {
        numeroParqueadero: numeroFinal.trim(),
        tipo: form.tipo,
        estado: form.estado,
      };

      if (editing) {
        await api.put(`/parqueaderos/${editing.idParqueadero}`, payload);
        toast.success(`Parqueadero ${numeroFinal} actualizado`);
      } else {
        await api.post('/parqueaderos', payload);
        toast.success(`Parqueadero ${numeroFinal} creado exitosamente`);
      }

      setModalOpen(false);
      setEditing(null);
      setForm(emptyForm);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al procesar el parqueadero');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  // Eliminar
  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/parqueaderos/${confirmDel.idParqueadero}`);
      toast.success(`Parqueadero ${confirmDel.codigo || confirmDel.numeroParqueadero} eliminado`);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar parqueadero');
    } finally {
      setConfirmDel(null);
    }
  }

  return (
    <PageContainer>
      {/* 1. Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink to={isPortero ? '/portero-dashboard' : '/dashboard'}>
              {isPortero ? 'Garita Principal' : 'Panel de Control'}
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Gestión de Parqueaderos</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* 2. Cabecera Contextual Enterprise */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/60 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Control y Cupos de Parqueadero
            </h1>
            <span className="px-2 py-0.5 text-xs font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded border border-slate-200 dark:border-slate-700">
              {isPortero ? 'PORTERO' : 'ADMIN_PROPIEDAD'}
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Monitoreo en tiempo real de bahías de estacionamiento, control vehicular de visitantes y asignaciones fijas.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Toggle de Vistas: Grilla vs Tabla */}
          <div className="inline-flex rounded-lg border border-border bg-card p-0.5 shadow-sm">
            <button
              onClick={() => setViewMode('grid')}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                viewMode === 'grid'
                  ? 'bg-emerald-600 text-white shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              Bahías
            </button>
            <button
              onClick={() => setViewMode('table')}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${
                viewMode === 'table'
                  ? 'bg-emerald-600 text-white shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <List className="w-3.5 h-3.5" />
              Lista
            </button>
          </div>

          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg bg-card border border-border hover:bg-muted text-foreground transition-colors shadow-sm min-h-[44px]"
            title="Refrescar estado"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-emerald-500' : ''}`} />
            Actualizar
          </button>

          {!isPortero && (
            <button
              onClick={() => {
                setEditing(null);
                setForm(emptyForm);
                setFormErrors({});
                setModalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm min-h-[44px]"
            >
              <Plus className="w-4 h-4" />
              Nuevo Cupo
            </button>
          )}
        </div>
      </div>

      {/* 3. Strip de KPIs Métricas */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="Total Bahías"
          value={stats.total}
          context="Cupos catastrados"
          icon={Building}
          variant="neutral"
        />
        <MetricCard
          label="Disponibles"
          value={stats.disponibles}
          context="Listos para ingreso"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Ocupados"
          value={stats.ocupados}
          context="Vehículos dentro"
          icon={Car}
          variant={stats.ocupados > 0 ? 'warning' : 'primary'}
        />
        <MetricCard
          label="Visitantes Libres"
          value={stats.visitantesLibres}
          context="Bahías flotantes"
          icon={ShieldCheck}
          variant="info"
        />
      </div>

      {/* 4. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-card p-4 rounded-xl border border-border/80 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por código (ej. V-01), placa o apto..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-8 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
          />
          {search && (
            <button
              onClick={() => setSearch('')}
              className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={filtroTipo}
            onChange={(e) => setFiltroTipo(e.target.value)}
            className="px-3 py-2 text-xs font-medium rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
          >
            {TIPOS.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>

          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className="px-3 py-2 text-xs font-medium rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
          >
            {ESTADOS.map((e) => (
              <option key={e.id} value={e.id}>
                {e.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 5. VISTA 1: Cuadrícula Visual de Bahías (Estilo PropTech Premium) */}
      {viewMode === 'grid' && (
        <div className="space-y-4">
          {filtrados.length === 0 ? (
            <div className="bg-card rounded-xl border border-border/80 p-12 text-center text-muted-foreground">
              <Car className="w-12 h-12 mx-auto text-muted-foreground/30 mb-3" />
              <p className="text-base font-semibold text-foreground">No se encontraron bahías de estacionamiento</p>
              <p className="text-xs text-muted-foreground mt-1">
                Ajuste los filtros o registre un nuevo cupo con el botón superior.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
              {filtrados.map((p) => {
                const IconComponent = getTipoIcon(p.tipo);
                const isDisponible = p.estado === 'DISPONIBLE';
                const isOcupado = p.estado === 'OCUPADO';
                const isMantenimiento = p.estado === 'EN_MANTENIMIENTO' || p.estado === 'MANTENIMIENTO';
                const codigo = p.codigo || p.numeroParqueadero || `P-${p.idParqueadero}`;

                let cardBorder = 'border-border/80 hover:border-emerald-500/50';
                let cardBg = 'bg-card';
                let statusText = p.estado;
                let statusBadge = 'bg-slate-500/10 text-slate-600 border-slate-500/20';

                if (isDisponible) {
                  cardBorder = 'border-emerald-500/30 hover:border-emerald-500';
                  cardBg = 'bg-emerald-500/[0.02] dark:bg-emerald-950/[0.15]';
                  statusText = 'DISPONIBLE';
                  statusBadge = 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30';
                } else if (isOcupado) {
                  cardBorder = 'border-rose-500/40 hover:border-rose-500';
                  cardBg = 'bg-rose-500/[0.03] dark:bg-rose-950/[0.2]';
                  statusText = 'OCUPADO';
                  statusBadge = 'bg-rose-500/15 text-rose-600 dark:text-rose-400 border-rose-500/30';
                } else if (isMantenimiento) {
                  cardBorder = 'border-amber-500/40';
                  cardBg = 'bg-amber-500/[0.03] dark:bg-amber-950/[0.15]';
                  statusText = 'MANTENIMIENTO';
                  statusBadge = 'bg-amber-500/15 text-amber-600 dark:text-amber-400 border-amber-500/30';
                }

                return (
                  <div
                    key={p.idParqueadero}
                    className={`rounded-xl border p-3.5 transition-all shadow-sm flex flex-col justify-between space-y-3 ${cardBorder} ${cardBg}`}
                  >
                    {/* Cabecera del Cupo: Código + Tipo */}
                    <div className="flex items-start justify-between">
                      <div>
                        <span className="text-lg font-black font-mono tracking-tight text-foreground block">
                          {codigo}
                        </span>
                        <span className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">
                          {p.tipo}
                        </span>
                      </div>
                      <div className="p-1.5 rounded-lg bg-muted/60 text-muted-foreground">
                        <IconComponent className="w-4 h-4" />
                      </div>
                    </div>

                    {/* Estado y Datos del Vehículo */}
                    <div className="space-y-2">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-black tracking-wide border ${statusBadge}`}
                      >
                        {statusText}
                      </span>

                      {/* Información de Ocupación */}
                      {isOcupado && (
                        <div className="space-y-1 text-xs pt-1 border-t border-border/60">
                          {p.placaVehiculo ? (
                            <div className="flex items-center gap-1.5 font-mono font-bold text-foreground">
                              <Car className="w-3.5 h-3.5 text-rose-500 shrink-0" />
                              <span className="bg-slate-900 text-white px-1.5 py-0.5 rounded text-[11px]">
                                {p.placaVehiculo}
                              </span>
                            </div>
                          ) : (
                            <span className="text-muted-foreground italic text-[11px]">En uso</span>
                          )}

                          {p.numeroApartamento && (
                            <span className="text-[11px] text-muted-foreground block font-medium">
                              Apto {p.numeroApartamento}
                            </span>
                          )}
                        </div>
                      )}

                      {isDisponible && (
                        <p className="text-[11px] text-emerald-600/80 dark:text-emerald-400/80 font-medium">
                          Cupo libre para asignación
                        </p>
                      )}

                      {isMantenimiento && (
                        <div className="flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400">
                          <Wrench className="w-3 h-3" />
                          <span>No operativo</span>
                        </div>
                      )}
                    </div>

                    {/* Acciones para Administrador */}
                    {!isPortero && (
                      <div className="pt-2 border-t border-border/40 flex items-center justify-end gap-1">
                        <button
                          type="button"
                          onClick={() => {
                            setEditing(p);
                            setForm({
                              numeroParqueadero: p.codigo || p.numeroParqueadero,
                              tipo: p.tipo,
                              estado: p.estado,
                              esVisitante: p.esVisitante,
                              idApartamento: p.idApartamento || '',
                            });
                            setFormErrors({});
                            setModalOpen(true);
                          }}
                          className="p-1.5 rounded-md hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                          title="Editar cupo"
                        >
                          <Edit3 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDel(p)}
                          className="p-1.5 rounded-md hover:bg-rose-500/10 text-muted-foreground hover:text-rose-600 transition-colors"
                          title="Eliminar cupo"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* 6. VISTA 2: Tabla Analítica (Desktop) + Tarjetas (Móvil) */}
      {viewMode === 'table' && (
        <div className="space-y-4">
          {/* Desktop Table */}
          <div className="hidden md:block bg-card rounded-xl border border-border/80 shadow-sm overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/40 border-b border-border/80 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Código</th>
                  <th className="px-4 py-3">Tipo</th>
                  <th className="px-4 py-3">Régimen</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3">Vehículo / Placa</th>
                  <th className="px-4 py-3">Unidad Asignada</th>
                  {!isPortero && <th className="px-4 py-3 text-right">Acciones</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {filtrados.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-4 py-10 text-center text-muted-foreground">
                      No se encontraron parqueaderos registrados
                    </td>
                  </tr>
                ) : (
                  filtrados.map((p) => {
                    const isDisponible = p.estado === 'DISPONIBLE';
                    const isOcupado = p.estado === 'OCUPADO';
                    const codigo = p.codigo || p.numeroParqueadero;

                    return (
                      <tr key={p.idParqueadero} className="hover:bg-muted/30 transition-colors">
                        <td className="px-4 py-3 font-mono font-bold text-foreground">
                          {codigo}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          {p.tipo}
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                          {p.esVisitante || p.tipo === 'VISITANTES' ? 'Visitante' : 'Residente'}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                              isDisponible
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                                : isOcupado
                                ? 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20'
                                : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                            }`}
                          >
                            {p.estado}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono text-xs whitespace-nowrap">
                          {p.placaVehiculo ? (
                            <span className="bg-slate-900 text-white px-2 py-0.5 rounded font-bold">
                              {p.placaVehiculo}
                            </span>
                          ) : (
                            <span className="text-muted-foreground italic">—</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs text-foreground whitespace-nowrap">
                          {p.numeroApartamento ? `Apto ${p.numeroApartamento}` : '—'}
                        </td>
                        {!isPortero && (
                          <td className="px-4 py-3 text-right whitespace-nowrap">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                type="button"
                                onClick={() => {
                                  setEditing(p);
                                  setForm({
                                    numeroParqueadero: p.codigo || p.numeroParqueadero,
                                    tipo: p.tipo,
                                    estado: p.estado,
                                    esVisitante: p.esVisitante,
                                    idApartamento: p.idApartamento || '',
                                  });
                                  setFormErrors({});
                                  setModalOpen(true);
                                }}
                                className="p-1.5 rounded hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                                title="Editar"
                              >
                                <Edit3 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => setConfirmDel(p)}
                                className="p-1.5 rounded hover:bg-rose-500/10 text-muted-foreground hover:text-rose-600 transition-colors"
                                title="Eliminar"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Mobile Card List */}
          <div className="md:hidden space-y-3">
            {filtrados.length === 0 ? (
              <div className="bg-card rounded-xl border border-border/80 p-8 text-center text-muted-foreground">
                <Car className="w-10 h-10 mx-auto text-muted-foreground/40 mb-2" />
                <p className="font-semibold text-foreground">No se encontraron bahías</p>
              </div>
            ) : (
              filtrados.map((p) => {
                const isDisponible = p.estado === 'DISPONIBLE';
                const isOcupado = p.estado === 'OCUPADO';
                const codigo = p.codigo || p.numeroParqueadero;

                return (
                  <div
                    key={p.idParqueadero}
                    className="bg-card rounded-xl border border-border/80 p-4 space-y-3 shadow-sm"
                  >
                    <div className="flex items-center justify-between border-b border-border/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-lg font-black font-mono text-foreground">
                          {codigo}
                        </span>
                        <span className="text-xs text-muted-foreground">({p.tipo})</span>
                      </div>
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          isDisponible
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : isOcupado
                            ? 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20'
                            : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                        }`}
                      >
                        {p.estado}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div>
                        <span className="text-muted-foreground block">Régimen:</span>
                        <span className="font-medium text-foreground">
                          {p.esVisitante || p.tipo === 'VISITANTES' ? 'Visitante' : 'Residente'}
                        </span>
                      </div>
                      <div>
                        <span className="text-muted-foreground block">Vehículo / Placa:</span>
                        {p.placaVehiculo ? (
                          <span className="font-mono font-bold bg-slate-900 text-white px-1.5 py-0.5 rounded text-[11px]">
                            {p.placaVehiculo}
                          </span>
                        ) : (
                          <span className="text-muted-foreground italic">—</span>
                        )}
                      </div>
                    </div>

                    {p.numeroApartamento && (
                      <div className="text-xs bg-muted/40 p-2 rounded text-foreground">
                        <span className="text-muted-foreground">Unidad: </span>
                        <span className="font-semibold">Apto {p.numeroApartamento}</span>
                      </div>
                    )}

                    {!isPortero && (
                      <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
                        <button
                          type="button"
                          onClick={() => {
                            setEditing(p);
                            setForm({
                              numeroParqueadero: p.codigo || p.numeroParqueadero,
                              tipo: p.tipo,
                              estado: p.estado,
                              esVisitante: p.esVisitante,
                              idApartamento: p.idApartamento || '',
                            });
                            setFormErrors({});
                            setModalOpen(true);
                          }}
                          className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDel(p)}
                          className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-600 min-h-[44px]"
                        >
                          Eliminar
                        </button>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* 7. Modal Crear / Editar Parqueadero */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar Bahía de Parqueadero' : 'Nueva Bahía de Parqueadero'}
      >
        <form onSubmit={handleSave} className="space-y-4 p-1">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1.5">
              Código / Número de Parqueadero *
            </label>
            <div className="relative">
              <Hash className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                value={form.numeroParqueadero}
                onChange={(e) => setForm({ ...form, numeroParqueadero: e.target.value })}
                placeholder={codigoSugerido}
                className="w-full pl-9 pr-3 py-2 text-sm font-mono uppercase rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              />
            </div>
            {!editing && (
              <p className="text-[11px] text-muted-foreground mt-1">
                Sugerido: <span className="font-mono font-bold text-foreground">{codigoSugerido}</span> (déjelo en blanco para usar el sugerido).
              </p>
            )}
            {formErrors.numeroParqueadero && (
              <p className="text-xs text-rose-500 mt-1">{formErrors.numeroParqueadero}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5">
                Tipo de Parqueadero *
              </label>
              <select
                value={form.tipo}
                onChange={(e) => setForm({ ...form, tipo: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              >
                <option value="VISITANTES">Visitantes</option>
                <option value="PRIVADO">Privado (Residente)</option>
                <option value="MOTOS">Motos</option>
                <option value="BICICLETAS">Bicicletas</option>
                <option value="DISCAPACITADOS">Accesibilidad (PMR)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5">
                Estado Operativo *
              </label>
              <select
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              >
                <option value="DISPONIBLE">Disponible</option>
                <option value="OCUPADO">Ocupado</option>
                <option value="EN_MANTENIMIENTO">En Mantenimiento</option>
                <option value="ASIGNADO">Asignado</option>
              </select>
            </div>
          </div>

          {form.tipo === 'PRIVADO' && (
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5">
                Apartamento Asignado
              </label>
              <select
                value={form.idApartamento}
                onChange={(e) => setForm({ ...form, idApartamento: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              >
                <option value="">Sin asignar / Seleccionar unidad...</option>
                {unidades.map((u) => {
                  const id = u.idApartamento || u.idUnidad;
                  const num = u.numero || u.numeroApartamento || u.identificador;
                  return (
                    <option key={id} value={id}>
                      Apto {num} {u.torre ? `(Torre ${u.torre})` : ''}
                    </option>
                  );
                })}
              </select>
            </div>
          )}

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border/60">
            <button
              type="button"
              onClick={() => setModalOpen(false)}
              className="px-4 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={saving}
              className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm disabled:opacity-50 min-h-[44px]"
            >
              {saving ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  Guardando...
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  {editing ? 'Actualizar' : 'Crear Cupo'}
                </>
              )}
            </button>
          </div>
        </form>
      </Modal>

      {/* 8. Diálogo de Confirmación de Eliminación */}
      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar Bahía de Parqueadero"
      >
        {confirmDel && (
          <div className="space-y-4 p-1">
            <div className="flex items-start gap-3 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 dark:text-rose-400 text-xs">
              <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold block">¿Está seguro de eliminar este cupo?</span>
                Esta acción retirará la bahía{' '}
                <span className="font-mono font-bold">
                  {confirmDel.codigo || confirmDel.numeroParqueadero}
                </span>{' '}
                del inventario catastral de la copropiedad.
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => setConfirmDel(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() => {
                  setPwdConfirmOpen(true);
                }}
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-rose-600 hover:bg-rose-700 text-white transition-colors min-h-[44px]"
              >
                Continuar
              </button>
            </div>
          </div>
        )}
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar el parqueadero ${confirmDel?.codigo || confirmDel?.numeroParqueadero}`}
      />
    </PageContainer>
  );
}
