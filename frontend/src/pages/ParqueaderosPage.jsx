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
  Layers,
} from 'lucide-react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import PageContainer from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';

const ESTADO_PARQ_LABEL = {
  DISPONIBLE: 'Disponible',
  OCUPADO: 'Ocupado',
  EN_MANTENIMIENTO: 'Mantenimiento',
  MANTENIMIENTO: 'Mantenimiento',
  RESERVADO: 'Reservado',
};

function estadoVariant(estado) {
  if (estado === 'DISPONIBLE') return 'success';
  if (estado === 'OCUPADO') return 'destructive';
  return 'warning';
}

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

  // Creación masiva
  const [modalMasivoOpen, setModalMasivoOpen] = useState(false);
  const [formMasivo, setFormMasivo] = useState({
    prefijo: 'P',
    cantidad: 10,
    numeroInicial: 1,
    tipo: 'PRIVADO',
    estado: 'DISPONIBLE',
  });
  const [savingMasivo, setSavingMasivo] = useState(false);
  const savingMasivoRef = useRef(false);

  const { masivoPreviewList, masivoUltimoItem } = useMemo(() => {
    const pref = (formMasivo.prefijo || '').trim();
    const cant = Math.min(500, Math.max(1, Number(formMasivo.cantidad) || 1));
    const start = Math.max(1, Number(formMasivo.numeroInicial) || 1);
    const getNum = (n) => {
      if (!pref) return String(n);
      if (pref.endsWith('-') || pref.endsWith('_') || pref.endsWith(' ')) return `${pref}${n}`;
      return `${pref}-${n}`;
    };
    const list = [];
    const limit = Math.min(cant, 8);
    for (let i = 0; i < limit; i++) {
      list.push(getNum(start + i));
    }
    const last = getNum(start + cant - 1);
    return { masivoPreviewList: list, masivoUltimoItem: last };
  }, [formMasivo.prefijo, formMasivo.cantidad, formMasivo.numeroInicial]);

  // Carga de datos
  const qs = new URLSearchParams({
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });

  const {
    data: rawParqueaderos,
    loading,
    error: loadError,
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

  // Creación Masiva
  async function handleSaveMasivo(e) {
    e?.preventDefault();
    if (savingMasivoRef.current) return;
    savingMasivoRef.current = true;
    setSavingMasivo(true);

    try {
      const payload = {
        prefijo: (formMasivo.prefijo || '').trim(),
        cantidad: Math.min(500, Math.max(1, Number(formMasivo.cantidad) || 1)),
        numeroInicial: Math.max(1, Number(formMasivo.numeroInicial) || 1),
        tipo: formMasivo.tipo,
        estado: formMasivo.estado,
      };

      await api.post('/parqueaderos/masivo', payload);
      toast.success(`Se generaron ${payload.cantidad} cupos de parqueadero exitosamente`);
      setModalMasivoOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al generar parqueaderos masivos');
    } finally {
      savingMasivoRef.current = false;
      setSavingMasivo(false);
    }
  }

  return (
    <PageContainer
      breadcrumbs={[
        { label: isPortero ? 'Panel de portería' : 'Panel de control', href: isPortero ? '/portero-dashboard' : '/dashboard' },
        { label: 'Parqueaderos', active: true },
      ]}
    >
      <PageHeader
        title="Control de parqueaderos"
        subtitle="Estado de cada bahía, vehículos de visitantes y cupos asignados a las unidades."
        action={
          <>
            <div className="inline-flex rounded-md border border-border bg-surface-container p-0.5" role="group" aria-label="Vista">
              <button
                type="button"
                onClick={() => setViewMode('grid')}
                aria-pressed={viewMode === 'grid'}
                className={`inline-flex min-h-10 items-center gap-1.5 rounded px-3 text-sm font-semibold transition-colors duration-150 ${
                  viewMode === 'grid' ? 'bg-surface text-primary-text shadow-sm' : 'text-text-secondary hover:text-on-surface'
                }`}
              >
                <LayoutGrid className="h-4 w-4" aria-hidden="true" />
                Bahías
              </button>
              <button
                type="button"
                onClick={() => setViewMode('table')}
                aria-pressed={viewMode === 'table'}
                className={`inline-flex min-h-10 items-center gap-1.5 rounded px-3 text-sm font-semibold transition-colors duration-150 ${
                  viewMode === 'table' ? 'bg-surface text-primary-text shadow-sm' : 'text-text-secondary hover:text-on-surface'
                }`}
              >
                <List className="h-4 w-4" aria-hidden="true" />
                Lista
              </button>
            </div>

            <button type="button" onClick={() => refetch()} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10" aria-label="Actualizar estado de parqueaderos">
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
              Actualizar
            </button>

            {!isPortero && (
              <>
                <button
                  type="button"
                  onClick={() => {
                    setEditing(null);
                    setForm(emptyForm);
                    setFormErrors({});
                    setModalOpen(true);
                  }}
                  className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10"
                >
                  <Plus className="h-4 w-4" aria-hidden="true" />
                  Nueva bahía
                </button>
                <button type="button" onClick={() => setModalMasivoOpen(true)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md bg-btn-primary px-4 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-btn-primary-hover disabled:opacity-50 sm:min-h-10">
                  <Layers className="h-4 w-4" aria-hidden="true" />
                  Crear en lote
                </button>
              </>
            )}
          </>
        }
      />

      {/* 3. Strip de KPIs Métricas */}
      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total Bahías"
          value={stats.total}
          context="Cupos registrados"
          icon={Building}
          variant="secondary"
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
          label="Visitantes libres"
          value={stats.visitantesLibres}
          context="Cupos de visitantes"
          icon={ShieldCheck}
          variant="info"
        />
      </div>

      {/* 4. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:p-4 md:flex-row md:items-center">
        <div className="relative flex-1 max-w-md">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="search"
            aria-label="Buscar parqueadero"
            placeholder="Código (ej. V-01), placa o apartamento"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-10"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              aria-label="Limpiar búsqueda"
              className="absolute right-0 top-0 flex h-10 w-10 items-center justify-center text-text-secondary hover:text-on-surface"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={filtroTipo}
            onChange={(e) => setFiltroTipo(e.target.value)}
            aria-label="Filtrar por tipo"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
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
            aria-label="Filtrar por estado"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
          >
            {ESTADOS.map((e) => (
              <option key={e.id} value={e.id}>
                {e.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading && !rawParqueaderos ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Consultando el estado de los parqueaderos..." />
        </div>
      ) : loadError && !rawParqueaderos ? (
        <ErrorState
          title="No se pudieron cargar los parqueaderos"
          message={loadError.message || 'Revise la conexión e intente de nuevo.'}
          onRetry={refetch}
        />
      ) : null}

      {/* 5. Vista de bahías */}
      {viewMode === 'grid' && !(loading && !rawParqueaderos) && !(loadError && !rawParqueaderos) && (
        <div className="space-y-4">
          {filtrados.length === 0 ? (
            <EmptyState
              icon={Car}
              title={search || filtroTipo || filtroEstado ? 'Ninguna bahía coincide con los filtros' : 'No hay bahías registradas'}
              subtitle={
                search || filtroTipo || filtroEstado
                  ? 'Cambie la búsqueda, el tipo o el estado.'
                  : isPortero
                  ? 'La administración aún no ha registrado parqueaderos.'
                  : 'Registre una bahía o cree varias en lote.'
              }
              className="rounded-lg border border-border bg-surface py-10"
            >
              {!isPortero && !(search || filtroTipo || filtroEstado) && (
                <button type="button" onClick={() => setModalMasivoOpen(true)} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md bg-btn-primary px-4 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-btn-primary-hover disabled:opacity-50 sm:min-h-10 mt-4">
                  <Layers className="h-4 w-4" aria-hidden="true" />
                  Crear en lote
                </button>
              )}
            </EmptyState>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6" data-testid="parking-grid">
              {filtrados.map((p) => {
                const IconComponent = getTipoIcon(p.tipo);
                const isDisponible = p.estado === 'DISPONIBLE';
                const isOcupado = p.estado === 'OCUPADO';
                const isMantenimiento = p.estado === 'EN_MANTENIMIENTO' || p.estado === 'MANTENIMIENTO';
                const codigo = p.codigo || p.numeroParqueadero || `P-${p.idParqueadero}`;

                // Free bays read as a ghosted dashed piece; taken bays as a solid keylined piece.
                let cardBorder = 'border-border';
                let cardBg = 'bg-surface';
                const statusText = ESTADO_PARQ_LABEL[p.estado] || p.estado;
                if (isDisponible) {
                  cardBorder = 'border-dashed border-tint-green-fg/50';
                  cardBg = 'bg-surface';
                } else if (isOcupado) {
                  cardBorder = 'border-keyline/60';
                  cardBg = 'bg-tint-red-bg';
                } else if (isMantenimiento) {
                  cardBorder = 'border-tint-amber-fg/40';
                  cardBg = 'bg-tint-amber-bg';
                }

                return (
                  <div
                    key={p.idParqueadero}
                    className={`flex flex-col justify-between space-y-3 rounded-md border-[1.5px] p-3.5 ${cardBorder} ${cardBg}`}
                  >
                    {/* Cabecera del Cupo: Código + Tipo */}
                    <div className="flex items-start justify-between">
                      <div>
                        <span className="app-numeral block text-lg text-on-surface">
                          {codigo}
                        </span>
                        <span className="text-xs font-semibold text-muted-foreground">
                          {p.tipo}
                        </span>
                      </div>
                      <div className="rounded-md bg-surface-container p-1.5 text-text-secondary">
                        <IconComponent className="h-4 w-4" aria-hidden="true" />
                      </div>
                    </div>

                    {/* Estado y Datos del Vehículo */}
                    <div className="space-y-2">
                      <Badge variant={estadoVariant(p.estado)}>{statusText}</Badge>

                      {/* Información de Ocupación */}
                      {isOcupado && (
                        <div className="space-y-1 text-xs pt-1 border-t border-border/60">
                          {p.placaVehiculo ? (
                            <div className="flex items-center gap-1.5 tabular-nums font-bold text-foreground">
                              <Car className="w-3.5 h-3.5 text-tint-red-fg shrink-0" />
                              <span className="inline-flex items-center rounded-[3px] border-[1.5px] border-on-surface bg-surface px-1.5 py-0.5 text-xs font-bold tracking-wider text-on-surface tabular-nums">
                                {p.placaVehiculo}
                              </span>
                            </div>
                          ) : (
                            <span className="text-muted-foreground italic text-xs">En uso</span>
                          )}

                          {p.numeroApartamento && (
                            <span className="text-xs text-muted-foreground block font-medium">
                              Apto {p.numeroApartamento}
                            </span>
                          )}
                        </div>
                      )}

                      {isDisponible && (
                        <p className="text-xs font-medium text-text-secondary">Cupo libre</p>
                      )}

                      {isMantenimiento && (
                        <div className="flex items-center gap-1 text-xs text-tint-amber-fg">
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
                          className="flex h-10 w-10 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-surface-container hover:text-on-surface"
                          title="Editar cupo"
                          aria-label={`Editar cupo ${codigo}`}
                        >
                          <Edit3 className="h-4 w-4" aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDel(p)}
                          className="flex h-10 w-10 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-tint-red-bg hover:text-destructive"
                          title="Eliminar cupo"
                          aria-label={`Eliminar cupo ${codigo}`}
                        >
                          <Trash2 className="h-4 w-4" aria-hidden="true" />
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
      {viewMode === 'table' && !(loading && !rawParqueaderos) && !(loadError && !rawParqueaderos) && (
        <div className="space-y-4">
          {/* Desktop Table */}
          <div className="hidden max-h-[70vh] overflow-auto rounded-lg border border-border bg-surface shadow-sm md:block">
            <table className="w-full text-left text-sm">
              <thead className="sticky top-0 z-10 border-b border-border bg-surface-container text-xs font-bold text-text-secondary">
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
                    <td colSpan={7} className="px-4 py-10 text-center text-sm text-text-secondary">
                      No se encontraron parqueaderos con estos filtros.
                    </td>
                  </tr>
                ) : (
                  filtrados.map((p) => {
                    const codigo = p.codigo || p.numeroParqueadero;

                    return (
                      <tr key={p.idParqueadero} className="transition-colors hover:bg-surface-container">
                        <td className="px-4 py-3 tabular-nums font-bold text-foreground">
                          {codigo}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          {p.tipo}
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                          {p.esVisitante || p.tipo === 'VISITANTES' ? 'Visitante' : 'Residente'}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <Badge variant={estadoVariant(p.estado)}>{ESTADO_PARQ_LABEL[p.estado] || p.estado}</Badge>
                        </td>
                        <td className="px-4 py-3 tabular-nums text-xs whitespace-nowrap">
                          {p.placaVehiculo ? (
                            <span className="inline-flex items-center rounded-[3px] border-[1.5px] border-on-surface bg-surface px-1.5 py-0.5 text-xs font-bold tracking-wider text-on-surface tabular-nums">
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
                                className="flex h-9 w-9 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-surface-container hover:text-on-surface"
                                title="Editar"
                                aria-label={`Editar ${codigo}`}
                              >
                                <Edit3 className="h-4 w-4" aria-hidden="true" />
                              </button>
                              <button
                                type="button"
                                onClick={() => setConfirmDel(p)}
                                className="flex h-9 w-9 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-tint-red-bg hover:text-destructive"
                                title="Eliminar"
                                aria-label={`Eliminar ${codigo}`}
                              >
                                <Trash2 className="h-4 w-4" aria-hidden="true" />
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
              <EmptyState icon={Car} title="No se encontraron bahías" className="rounded-lg border border-border bg-surface py-8" />
            ) : (
              filtrados.map((p) => {
                const codigo = p.codigo || p.numeroParqueadero;

                return (
                  <div
                    key={p.idParqueadero}
                    className="space-y-3 rounded-lg border border-border bg-surface p-4 shadow-sm"
                  >
                    <div className="flex items-center justify-between border-b border-border/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="app-numeral text-lg text-on-surface">
                          {codigo}
                        </span>
                        <span className="text-xs text-muted-foreground">({p.tipo})</span>
                      </div>
                      <Badge variant={estadoVariant(p.estado)}>{ESTADO_PARQ_LABEL[p.estado] || p.estado}</Badge>
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
                          <span className="inline-flex items-center rounded-[3px] border-[1.5px] border-on-surface bg-surface px-1.5 py-0.5 text-xs font-bold tracking-wider text-on-surface tabular-nums">
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
                          className="min-h-[44px] rounded-md border border-input/60 bg-surface px-3 text-sm font-semibold text-on-surface hover:bg-surface-container"
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDel(p)}
                          className="min-h-[44px] rounded-md border border-tint-red-fg/25 bg-tint-red-bg px-3 text-sm font-semibold text-destructive"
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
        title={editing ? 'Editar bahía de parqueadero' : 'Nueva bahía de parqueadero'}
      >
        <form onSubmit={handleSave} className="space-y-4 p-1">
          <div>
            <label htmlFor="parq-f1" className="mb-1.5 block text-sm font-semibold text-on-surface">
              Código / Número de Parqueadero *
            </label>
            <div className="relative">
              <Hash className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
              <input id="parq-f1"
                type="text"
                value={form.numeroParqueadero}
                onChange={(e) => setForm({ ...form, numeroParqueadero: e.target.value })}
                placeholder={codigoSugerido}
                className="w-full pl-9 pr-3 py-2 text-sm tabular-nums rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25 focus:border-ring"
              />
            </div>
            {!editing && (
              <p className="text-xs text-muted-foreground mt-1">
                Sugerido: <span className="tabular-nums font-bold text-foreground">{codigoSugerido}</span> (déjelo en blanco para usar el sugerido).
              </p>
            )}
            {formErrors.numeroParqueadero && (
              <p className="text-xs text-tint-red-fg mt-1">{formErrors.numeroParqueadero}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="parq-f2" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Tipo de Parqueadero *
              </label>
              <select id="parq-f2"
                value={form.tipo}
                onChange={(e) => setForm({ ...form, tipo: e.target.value })}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
              >
                <option value="VISITANTES">Visitantes</option>
                <option value="PRIVADO">Privado (Residente)</option>
                <option value="MOTOS">Motos</option>
                <option value="BICICLETAS">Bicicletas</option>
                <option value="DISCAPACITADOS">Accesibilidad (PMR)</option>
              </select>
            </div>

            <div>
              <label htmlFor="parq-f3" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Estado Operativo *
              </label>
              <select id="parq-f3"
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
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
              <label htmlFor="parq-f4" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Apartamento Asignado
              </label>
              <select id="parq-f4"
                value={form.idApartamento}
                onChange={(e) => setForm({ ...form, idApartamento: e.target.value })}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
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
              className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white transition-colors shadow-sm disabled:opacity-50 min-h-[44px]"
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

      {/* 7.1 Modal Creación Masiva de Parqueaderos */}
      <Modal
        open={modalMasivoOpen}
        onClose={() => setModalMasivoOpen(false)}
        title="Crear bahías en lote"
      >
        <form onSubmit={handleSaveMasivo} className="space-y-4 p-1">
          <p className="text-xs text-muted-foreground">
            Genere consecutivamente lotes de parqueaderos para la copropiedad especificando prefijo, cantidad y numeración inicial.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="parq-f5" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Prefijo (ej: P, V, MOTO, B)
              </label>
              <input id="parq-f5"
                type="text"
                value={formMasivo.prefijo}
                onChange={(e) => setFormMasivo({ ...formMasivo, prefijo: e.target.value.toUpperCase() })}
                placeholder="P"
                className="w-full px-3 py-2 text-sm tabular-nums rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25 focus:border-ring"
              />
              <p className="text-xs text-muted-foreground mt-1">
                Opcional. Se agregará guión automáticamente si aplica.
              </p>
            </div>

            <div>
              <label htmlFor="parq-f6" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Tipo de Bahía *
              </label>
              <select id="parq-f6"
                value={formMasivo.tipo}
                onChange={(e) => setFormMasivo({ ...formMasivo, tipo: e.target.value })}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
              >
                <option value="PRIVADO">Privado (Residente)</option>
                <option value="VISITANTES">Visitantes</option>
                <option value="MOTOS">Motos</option>
                <option value="BICICLETAS">Bicicletas</option>
                <option value="DISCAPACITADOS">Accesibilidad (PMR)</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="parq-f7" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Número Inicial *
              </label>
              <input id="parq-f7"
                type="number"
                min="1"
                value={formMasivo.numeroInicial}
                onChange={(e) => setFormMasivo({ ...formMasivo, numeroInicial: Math.max(1, parseInt(e.target.value) || 1) })}
                className="w-full px-3 py-2 text-sm tabular-nums rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25 focus:border-ring"
              />
            </div>

            <div>
              <label htmlFor="parq-f8" className="mb-1.5 block text-sm font-semibold text-on-surface">
                Cantidad a Generar *
              </label>
              <input id="parq-f8"
                type="number"
                min="1"
                max="500"
                value={formMasivo.cantidad}
                onChange={(e) => setFormMasivo({ ...formMasivo, cantidad: Math.min(500, Math.max(1, parseInt(e.target.value) || 1)) })}
                className="w-full px-3 py-2 text-sm tabular-nums rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25 focus:border-ring"
              />
            </div>
          </div>

          <div>
            <label htmlFor="parq-f9" className="mb-1.5 block text-sm font-semibold text-on-surface">
              Estado Inicial *
            </label>
            <select id="parq-f9"
              value={formMasivo.estado}
              onChange={(e) => setFormMasivo({ ...formMasivo, estado: e.target.value })}
              className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
            >
              <option value="DISPONIBLE">Disponible</option>
              <option value="MANTENIMIENTO">Mantenimiento</option>
              <option value="INACTIVO">Inactivo</option>
            </select>
          </div>

          {/* Vista previa en tiempo real */}
          <div className="p-3 bg-muted/50 rounded-lg border border-border/80">
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-medium text-foreground">Vista Previa del Lote:</span>
              <span className="text-xs tabular-nums text-primary-text font-semibold">
                {formMasivo.cantidad} cupos
              </span>
            </div>
            <div className="text-xs tabular-nums text-muted-foreground flex flex-wrap gap-1.5 max-h-24 overflow-y-auto">
              {masivoPreviewList.map((item, idx) => (
                <span key={idx} className="px-2 py-0.5 bg-background rounded border border-border text-foreground">
                  {item}
                </span>
              ))}
              {formMasivo.cantidad > 8 && (
                <span className="text-xs text-muted-foreground self-center">
                  ... hasta {masivoUltimoItem}
                </span>
              )}
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border/60">
            <button
              type="button"
              onClick={() => setModalMasivoOpen(false)}
              className="px-4 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={savingMasivo}
              className="inline-flex items-center justify-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white shadow-sm min-h-[44px] disabled:opacity-50"
            >
              {savingMasivo ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  Creando {formMasivo.cantidad} cupos...
                </>
              ) : (
                `Crear ${formMasivo.cantidad} Parqueaderos`
              )}
            </button>
          </div>
        </form>
      </Modal>

      {/* 8. Diálogo de Confirmación de Eliminación */}
      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar bahía de parqueadero"
      >
        {confirmDel && (
          <div className="space-y-4 p-1">
            <div className="flex items-start gap-3 p-3 rounded-lg bg-tint-red-bg border border-tint-red-fg/25 text-tint-red-fg text-xs">
              <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold block">¿Está seguro de eliminar este cupo?</span>
                Esta acción retirará la bahía{' '}
                <span className="tabular-nums font-bold">
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
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-btn-danger hover:opacity-90 text-white transition-colors min-h-[44px]"
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
