import { useState, useEffect } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { Modal } from '../components/ui/Modal';
import { Select, Input, Textarea } from '../components/ui/Form.jsx';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/badge.tsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';
import {
  Gavel,
  Scale,
  Home,
  User,
  CheckCircle2,
  FileText,
  Clock,
  ShieldAlert,
  Loader2,
  XCircle,
  Building,
} from 'lucide-react';
import { valTexto, valSelect, valNumero, formatoCOP } from '../lib/validation.js';

const PAGE_SIZE = 10;

const ESTADO_BADGE = {
  NOTIFICADA: { variant: 'warning', label: 'Notificada', icon: Clock },
  EN_DESCARGOS: { variant: 'info', label: 'En descargos', icon: FileText },
  ABSUELTA: { variant: 'secondary', label: 'Absuelta', icon: CheckCircle2 },
  APLICADA: { variant: 'destructive', label: 'Sanción aplicada', icon: ShieldAlert },
  ANULADA: { variant: 'outline', label: 'Anulada', icon: XCircle },
};

const GRAVEDAD_BADGE = {
  LEVE: { variant: 'secondary', label: 'Leve' },
  GRAVE: { variant: 'warning', label: 'Grave' },
  GRAVISIMA: { variant: 'destructive', label: 'Gravísima' },
};

function EstadoBadge({ estado }) {
  const meta = ESTADO_BADGE[estado] || { variant: 'secondary', label: estado };
  const Icon = meta.icon;
  return (
    <Badge variant={meta.variant}>
      {Icon && <Icon className="h-3 w-3" aria-hidden="true" />}
      {meta.label}
    </Badge>
  );
}

function GravedadBadge({ gravedad }) {
  const meta = GRAVEDAD_BADGE[gravedad] || { variant: 'secondary', label: gravedad };
  return <Badge variant={meta.variant}>{meta.label}</Badge>;
}

const TIPOS_SANCION = [
  { value: 'AMONESTACION_ESCRITA', label: 'Amonestación Escrita' },
  { value: 'AMONESTACION_VERBAL', label: 'Amonestación Verbal' },
  { value: 'MULTA_ECONOMICA', label: 'Multa Económica' },
  { value: 'SUSPENSION_ZONAS_COMUNES', label: 'Suspensión de Zonas Comunes' },
  { value: 'PUBLICACION_LISTA_INFRACTORES', label: 'Publicación en Lista de Infractores' },
];

function extractList(resp) {
  if (!resp) return [];
  if (Array.isArray(resp)) return resp;
  if (Array.isArray(resp.data)) return resp.data;
  if (Array.isArray(resp.items)) return resp.items;
  if (Array.isArray(resp.content)) return resp.content;
  return [];
}

const INITIAL_FORM_STATE = {
  idUnidad: '',
  idPersonaImputada: '',
  tipoFalta: '',
  gravedad: 'LEVE',
  tipoSancionPropuesta: 'AMONESTACION_ESCRITA',
  diasParaDescargos: 10,
  articuloReglamentoViolado: '',
  descripcionHechos: '',
};

export default function SancionesAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const tenantApi = useTenantApi();
  const { activePropertyId, activePropertyName } = useTenant();
  const { data, loading, error, refetch } = useFetch(() => tenantApi.get('/sanciones/todas'), [activePropertyId]);

  // Estados para unidades y candidatos a imputación
  const [unidades, setUnidades] = useState([]);
  const [loadingUnidades, setLoadingUnidades] = useState(false);
  const [candidatos, setCandidatos] = useState([]);
  const [loadingCandidatos, setLoadingCandidatos] = useState(false);
  const [modoManualPersona, setModoManualPersona] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState(INITIAL_FORM_STATE);

  // Live validation hook
  const { touch, touchAll, fieldError, resetTouched } = useLiveValidation();

  // Detalle y resolución
  const [detalle, setDetalle] = useState(null);
  const [anulacion, setAnulacion] = useState(null); // { idSancion, motivo }
  const [resolucionForm, setResolucionForm] = useState({
    decision: 'APLICADA',
    resolucionFinal: '',
    montoMulta: '',
    restriccionHasta: '',
  });

  // Al abrir el modal, resetear formulario y live validations
  useEffect(() => {
    if (modalOpen) {
      resetTouched();
      setModoManualPersona(false);
      setForm(INITIAL_FORM_STATE);
    }
  }, [modalOpen, resetTouched]);

  // Cargar unidades cuando se abre el modal o cambia la propiedad activa del tenant
  useEffect(() => {
    let active = true;
    async function cargarUnidades() {
      setLoadingUnidades(true);
      try {
        const url = activePropertyId ? `/units?idPropiedad=${activePropertyId}` : '/units';
        const resp = await tenantApi.get(url);
        const list = extractList(resp);
        if (active) {
          setUnidades(list);
        }
      } catch (err) {
        if (active) {
          toast.error('Error al cargar unidades: ' + (err.message || 'Error de red'));
          setUnidades([]);
        }
      } finally {
        if (active) setLoadingUnidades(false);
      }
    }

    if (modalOpen) {
      cargarUnidades();
    }
    return () => {
      active = false;
    };
  }, [modalOpen, activePropertyId, tenantApi]);

  // Cargar personas/habitantes cuando cambia la unidad seleccionada
  useEffect(() => {
    if (!form.idUnidad) {
      setCandidatos([]);
      return;
    }
    let active = true;
    async function cargarHabitantes() {
      setLoadingCandidatos(true);
      try {
        const unitId = form.idUnidad;
        const [resResidents, resOwners, resPersonas] = await Promise.allSettled([
          tenantApi.get(`/units/${unitId}/residents`),
          tenantApi.get(`/units/${unitId}/owners`),
          tenantApi.get('/personas?page=0&size=200'),
        ]);

        const personasMap = new Map();

        const addPersona = (id, nombre, documento, rol) => {
          if (!id) return;
          const key = String(id);
          if (!personasMap.has(key)) {
            personasMap.set(key, {
              id: key,
              nombreCompleto: nombre || 'Habitante / Residente',
              documento: documento || 'S/N',
              rol: rol || 'RESIDENTE',
            });
          }
        };

        // 1. Procesar residentes de la unidad
        if (resResidents.status === 'fulfilled') {
          const list = extractList(resResidents.value);
          list.forEach((r) => {
            const p = r.persona || r;
            const pId = p.id || p.idPersona || r.idPersona || r.id;
            const nombre = [p.primerNombre, p.segundoNombre, p.primerApellido, p.segundoApellido]
              .filter(Boolean)
              .join(' ')
              .trim() || p.nombreCompleto || p.nombre || 'Residente';
            const doc = p.numeroDocumento || r.numeroDocumento || 'S/N';
            addPersona(pId, nombre, doc, r.tipoResidente || 'RESIDENTE');
          });
        }

        // 2. Procesar propietarios de la unidad
        if (resOwners.status === 'fulfilled') {
          const list = extractList(resOwners.value);
          list.forEach((o) => {
            const p = o.persona || o;
            const pId = p.id || p.idPersona || o.idPersona || o.id;
            const nombre = [p.primerNombre, p.segundoNombre, p.primerApellido, p.segundoApellido]
              .filter(Boolean)
              .join(' ')
              .trim() || p.nombreCompleto || p.nombre || 'Propietario';
            const doc = p.numeroDocumento || o.numeroDocumento || 'S/N';
            addPersona(pId, nombre, doc, 'PROPIETARIO');
          });
        }

        // 3. Fallback / Complemento con personas asignadas a la unidad en censo (/personas)
        if (resPersonas.status === 'fulfilled') {
          const allPersonas = extractList(resPersonas.value);
          allPersonas.forEach((p) => {
            const aptoId = p.idApartamento || p.idUnidad;
            if (aptoId && String(aptoId) === String(unitId)) {
              const pId = p.id || p.idPersona;
              const nombre = [p.primerNombre, p.segundoNombre, p.primerApellido, p.segundoApellido]
                .filter(Boolean)
                .join(' ')
                .trim() || p.nombreCompleto || p.nombre || 'Habitante';
              const doc = p.numeroDocumento || 'S/N';
              addPersona(pId, nombre, doc, p.tipoRelacion || p.tipoPersona || 'RESIDENTE');
            }
          });
        }

        if (active) {
          const list = Array.from(personasMap.values());
          setCandidatos(list);
          if (list.length === 1) {
            setForm((prev) => ({
              ...prev,
              idPersonaImputada: prev.idPersonaImputada || String(list[0].id),
            }));
          }
        }
      } catch {
        if (active) {
          setCandidatos([]);
        }
      } finally {
        if (active) setLoadingCandidatos(false);
      }
    }
    cargarHabitantes();
    return () => {
      active = false;
    };
  }, [form.idUnidad, tenantApi]);

  // Validaciones en vivo
  const errorUnidad = fieldError('idUnidad', valSelect(form.idUnidad, 'Seleccione la unidad habitacional'));
  const errorImputado = fieldError(
    'idPersonaImputada',
    modoManualPersona
      ? valNumero(form.idPersonaImputada, { min: 1, entero: true, label: 'El ID de la persona' })
      : valSelect(form.idPersonaImputada, 'Seleccione a la persona imputada')
  );
  const errorTipoFalta = fieldError('tipoFalta', valTexto(form.tipoFalta, 'El tipo de falta', { min: 3, max: 120, required: true }));
  const errorDescripcion = fieldError(
    'descripcionHechos',
    valTexto(form.descripcionHechos, 'La descripción de los hechos', { min: 10, max: 2000, required: true })
  );
  const errorDiasDescargos = fieldError(
    'diasParaDescargos',
    valNumero(form.diasParaDescargos, { min: 1, max: 60, entero: true, label: 'Los días hábiles para descargos' })
  );

  const items = Array.isArray(data) ? data : (Array.isArray(data?.items) ? data.items : []);
  const safeItems = Array.isArray(items) ? items : [];
  const filtered = filtroEstado ? safeItems.filter((i) => i && i.estado === filtroEstado) : safeItems;

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(Math.max(0, page), totalPages - 1);
  const rows = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const columns = [
    {
      key: 'numeroExpediente',
      label: 'Expediente',
      width: 150,
      render: (r) => (
        <span className="whitespace-nowrap rounded-[3px] bg-surface-dim px-2 py-0.5 font-mono text-xs font-semibold text-on-surface">
          {r.numeroExpediente || `EXP-${r.idSancion}`}
        </span>
      ),
    },
    {
      key: 'identificadorUnidad',
      label: 'Unidad',
      render: (r) => (
        <div className="flex items-center gap-1.5 font-semibold text-on-surface">
          <Home className="h-3.5 w-3.5 shrink-0 text-text-muted" aria-hidden="true" />
          <span>{r.identificadorUnidad || (r.idUnidad ? `Unidad #${r.idUnidad}` : '-')}</span>
        </div>
      ),
    },
    {
      key: 'nombreImputado',
      label: 'Persona Imputada',
      render: (r) => (
        <div className="flex items-center gap-1.5 text-sm text-on-surface">
          <User className="h-3.5 w-3.5 shrink-0 text-text-muted" aria-hidden="true" />
          <span>{r.nombreImputado || (r.idPersonaImputada ? `Persona #${r.idPersonaImputada}` : 'No especificado')}</span>
        </div>
      ),
    },
    { key: 'tipoFalta', label: 'Falta / Infracción' },
    {
      key: 'gravedad',
      label: 'Gravedad',
      render: (r) => <GravedadBadge gravedad={r.gravedad} />,
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <EstadoBadge estado={r.estado} />,
    },
  ];

  async function handleCreate(e) {
    e.preventDefault();
    touchAll(['idUnidad', 'idPersonaImputada', 'tipoFalta', 'descripcionHechos', 'diasParaDescargos']);

    // Validaciones estrictas
    if (!form.idUnidad) {
      toast.error('Debe seleccionar la unidad habitacional a sancionar.');
      return;
    }
    if (!form.idPersonaImputada) {
      toast.error('Debe seleccionar o indicar la persona imputada.');
      return;
    }
    const valFalta = valTexto(form.tipoFalta, 'El tipo de falta', { min: 3, max: 120, required: true });
    if (!valFalta.ok) {
      toast.error(valFalta.mensaje);
      return;
    }
    const valHechos = valTexto(form.descripcionHechos, 'La descripción de los hechos', { min: 10, max: 2000, required: true });
    if (!valHechos.ok) {
      toast.error(valHechos.mensaje);
      return;
    }
    const valDias = valNumero(form.diasParaDescargos, { min: 1, max: 60, entero: true, label: 'Días para descargos' });
    if (!valDias.ok) {
      toast.error(valDias.mensaje);
      return;
    }

    setSubmitting(true);
    try {
      await tenantApi.post('/sanciones', {
        idUnidad: Number(form.idUnidad),
        idPersonaImputada: Number(form.idPersonaImputada),
        tipoFalta: form.tipoFalta.trim(),
        gravedad: form.gravedad,
        tipoSancionPropuesta: form.tipoSancionPropuesta,
        descripcionHechos: form.descripcionHechos.trim(),
        articuloReglamentoViolado: form.articuloReglamentoViolado?.trim() || null,
        diasParaDescargos: Number(form.diasParaDescargos) || 10,
      });

      toast.success('Pliego de cargos notificado y radicado exitosamente');
      setModalOpen(false);
      setForm(INITIAL_FORM_STATE);
      resetTouched();
      refetch();
    } catch (err) {
      toast.error(err.message || 'No fue posible registrar el pliego de cargos');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEmitirResolucion(e) {
    e.preventDefault();

    if (resolucionForm.decision === 'APLICADA' && detalle?.tipoSancionPropuesta === 'MULTA_ECONOMICA') {
      const monto = Number(resolucionForm.montoMulta);
      if (!resolucionForm.montoMulta || isNaN(monto) || monto <= 0) {
        toast.error('El monto de la multa es obligatorio y debe ser mayor a 0 (COP).');
        return;
      }
    }

    const aplicaSuspension =
      resolucionForm.decision === 'APLICADA' && detalle?.tipoSancionPropuesta === 'SUSPENSION_ZONAS_COMUNES';
    if (aplicaSuspension && !resolucionForm.restriccionHasta) {
      toast.error('Indique la fecha final de la suspensión de zonas comunes.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        decision: resolucionForm.decision,
        resolucionFinal: resolucionForm.resolucionFinal.trim(),
        montoMulta:
          resolucionForm.decision === 'APLICADA' && detalle?.tipoSancionPropuesta === 'MULTA_ECONOMICA'
            ? Number(resolucionForm.montoMulta)
            : null,
        restriccionHasta: aplicaSuspension ? resolucionForm.restriccionHasta : null,
      };
      await tenantApi.post(`/sanciones/${detalle.idSancion}/resolucion`, payload);
      toast.success('Resolución firmada y emitida exitosamente');
      setDetalle(null);
      setResolucionForm({ decision: 'APLICADA', resolucionFinal: '', montoMulta: '', restriccionHasta: '' });
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al emitir resolución');
    } finally {
      setSubmitting(false);
    }
  }

  function handleAnularSancion(idSancion) {
    setAnulacion({ idSancion, motivo: '' });
  }

  async function confirmarAnulacion(e) {
    e.preventDefault();
    const idSancion = anulacion?.idSancion;
    const motivo = anulacion?.motivo || '';
    if (!idSancion || !motivo.trim()) return;
    setSubmitting(true);
    try {
      await tenantApi.put(`/sanciones/${idSancion}/anular`, { motivo: motivo.trim() });
      toast.success('Expediente sancionatorio anulado');
      setAnulacion(null);
      setDetalle(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al anular sanción');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Debido proceso y sanciones"
        subtitle="Pliegos de cargos, descargos y resoluciones con garantía de debido proceso"
        action={
          <div className="flex flex-wrap items-end gap-2">
            <Select
              id="sanciones-filtro"
              aria-label="Filtrar por estado"
              value={filtroEstado}
              onChange={(e) => {
                setFiltroEstado(e.target.value);
                setPage(0);
              }}
              className="w-48 text-sm"
            >
              <option value="">Todos los estados</option>
              <option value="NOTIFICADA">Notificada</option>
              <option value="EN_DESCARGOS">En descargos</option>
              <option value="ABSUELTA">Absuelta</option>
              <option value="APLICADA">Sanción aplicada</option>
              <option value="ANULADA">Anulada</option>
            </Select>
            <Button onClick={() => setModalOpen(true)} icon={<Gavel className="h-4 w-4" />}>
              Nuevo pliego
            </Button>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title="No se pudieron cargar los procesos sancionatorios"
          message={error.message || 'Revise su conexión e intente de nuevo.'}
          onRetry={() => refetch().catch(() => {})}
        />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            loading={loading}
            empty={{
              icon: Scale,
              title: filtroEstado ? 'No hay procesos en este estado' : 'Sin sanciones registradas',
              subtitle: filtroEstado
                ? 'Cambie el filtro de estado para ver otros expedientes.'
                : 'Abra un pliego de cargos para iniciar un proceso sancionatorio.',
            }}
            keyField="idSancion"
            onRowClick={setDetalle}
          />

          <Pagination
            page={safePage}
            totalPages={totalPages}
            totalItems={filtered.length}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Modal de Creación de Nuevo Pliego de Cargos */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Abrir pliego de cargos"
        size="lg"
      >
        <form onSubmit={handleCreate} className="space-y-4 pt-1">
          <div className="flex items-start gap-2.5 rounded-md border border-border bg-surface-container p-3 text-xs text-text-secondary">
            <Scale className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
            <div>
              <span className="font-semibold text-on-surface">Garantía de debido proceso (Ley 675 y artículo 29 de la Constitución):</span>
              <p className="mt-0.5">
                La notificación del pliego otorgará días hábiles al imputado para presentar sus descargos y controvertir pruebas antes de cualquier fallo.
              </p>
            </div>
          </div>

          {/* Indicador de Copropiedad Activa */}
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-surface-container px-3.5 py-2.5 text-xs">
            <span className="flex items-center gap-1.5 font-medium text-text-secondary">
              <Building className="h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden="true" />
              Copropiedad activa
            </span>
            <span className="font-semibold text-on-surface">
              {activePropertyName || 'Copropiedad en administración'}
            </span>
          </div>

          {/* Fila 1: Selección de Unidad Habitacional */}
          <div className="relative">
            <Select
              label="Unidad habitacional"
              required
              id="sancion-unidad"
              value={form.idUnidad}
              error={errorUnidad}
              onBlur={() => touch('idUnidad')}
              onChange={(e) => {
                touch('idUnidad');
                setForm({
                  ...form,
                  idUnidad: e.target.value,
                  idPersonaImputada: '',
                });
              }}
              disabled={loadingUnidades || submitting}
            >
              <option value="">
                {loadingUnidades
                  ? 'Cargando unidades...'
                  : unidades.length === 0
                  ? 'Sin unidades registradas en la copropiedad'
                  : 'Seleccione la unidad habitacional'}
              </option>
              {unidades.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.identificador} {u.bloqueNombre ? `· ${u.bloqueNombre}` : ''}
                </option>
              ))}
            </Select>
            {loadingUnidades && (
              <span className="absolute right-8 top-8 flex items-center gap-1 text-xs text-text-muted" role="status">
                <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" /> Cargando…
              </span>
            )}
          </div>

          {/* Fila 2: Persona Imputada (Habitante de la unidad) */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <label
                htmlFor={modoManualPersona ? 'sancion-imputado-manual' : 'sancion-imputado'}
                className="text-sm font-semibold text-on-surface"
              >
                Persona imputada <span className="font-bold text-destructive" aria-hidden="true">*</span>
              </label>
              <button
                type="button"
                onClick={() => {
                  setModoManualPersona(!modoManualPersona);
                  setForm((f) => ({ ...f, idPersonaImputada: '' }));
                }}
                className="min-h-8 rounded px-1 text-xs font-semibold text-primary-text underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {modoManualPersona ? 'Volver a la lista de habitantes' : 'Ingresar el ID manualmente'}
              </button>
            </div>

            {!modoManualPersona ? (
              <div className="relative">
                <Select
                  required
                  id="sancion-imputado"
                  value={form.idPersonaImputada}
                  error={errorImputado}
                  onBlur={() => touch('idPersonaImputada')}
                  onChange={(e) => {
                    touch('idPersonaImputada');
                    setForm({ ...form, idPersonaImputada: e.target.value });
                  }}
                  disabled={!form.idUnidad || loadingCandidatos || submitting}
                >
                  <option value="">
                    {!form.idUnidad
                      ? 'Seleccione primero una unidad'
                      : loadingCandidatos
                      ? 'Cargando habitantes de la unidad…'
                      : candidatos.length === 0
                      ? 'No hay habitantes registrados (use el ID manual)'
                      : 'Seleccione el habitante o propietario'}
                  </option>
                  {candidatos.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nombreCompleto} ({c.rol}) - Doc: {c.documento}
                    </option>
                  ))}
                </Select>
                {loadingCandidatos && (
                  <span className="absolute right-8 top-2.5 flex items-center gap-1 text-xs text-text-muted">
                    <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
                  </span>
                )}
              </div>
            ) : (
              <Input
                type="number"
                placeholder="Ingrese el ID de base de datos de la persona"
                required
                id="sancion-imputado-manual"
                value={form.idPersonaImputada}
                error={errorImputado}
                onBlur={() => touch('idPersonaImputada')}
                onChange={(e) => {
                  touch('idPersonaImputada');
                  setForm({ ...form, idPersonaImputada: e.target.value });
                }}
              />
            )}
            <p className="text-xs text-text-muted">
              Seleccione al residente o propietario presuntamente responsable del hecho violatorio.
            </p>
          </div>

          {/* Fila 3: Falta, Gravedad y Sanción Propuesta */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-1">
              <Input
                label="Tipo de falta"
                required
                id="sancion-falta"
                placeholder="Ej. Ruido fuera de horario"
                value={form.tipoFalta}
                error={errorTipoFalta}
                onBlur={() => touch('tipoFalta')}
                onChange={(e) => {
                  touch('tipoFalta');
                  setForm({ ...form, tipoFalta: e.target.value });
                }}
              />
            </div>
            <div>
              <Select
                label="Gravedad"
                required
                id="sancion-gravedad"
                value={form.gravedad}
                onChange={(e) => setForm({ ...form, gravedad: e.target.value })}
              >
                <option value="LEVE">Leve</option>
                <option value="GRAVE">Grave</option>
                <option value="GRAVISIMA">Gravísima</option>
              </Select>
            </div>
            <div>
              <Select
                label="Sanción propuesta"
                required
                id="sancion-propuesta"
                value={form.tipoSancionPropuesta}
                onChange={(e) => setForm({ ...form, tipoSancionPropuesta: e.target.value })}
              >
                {TIPOS_SANCION.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {/* Fila 4: Días para descargos y Artículo violado */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Input
              label="Días hábiles para descargos"
              type="number"
              min="1"
              max="60"
              required
              id="sancion-dias"
              value={form.diasParaDescargos}
              error={errorDiasDescargos}
              onBlur={() => touch('diasParaDescargos')}
              onChange={(e) => {
                touch('diasParaDescargos');
                setForm({ ...form, diasParaDescargos: e.target.value });
              }}
            />
            <Input
              label="Artículo del reglamento infringido (opcional)"
              id="sancion-articulo"
              placeholder="Ej. Art. 45 Inciso 3 - Manual de Convivencia"
              value={form.articuloReglamentoViolado}
              onChange={(e) => setForm({ ...form, articuloReglamentoViolado: e.target.value })}
            />
          </div>

          {/* Fila 5: Hechos */}
          <Textarea
            label="Descripción circunstanciada de los hechos"
            required
            id="sancion-hechos"
            placeholder="Detalle fecha, hora, lugar, personas involucradas y la conducta observada que motiva la apertura del pliego..."
            value={form.descripcionHechos}
            error={errorDescripcion}
            onBlur={() => touch('descripcionHechos')}
            onChange={(e) => {
              touch('descripcionHechos');
              setForm({ ...form, descripcionHechos: e.target.value });
            }}
            rows={3}
          />

          <div className="flex justify-end gap-2 pt-2 border-t border-border">
            <Button variant="ghost" onClick={() => setModalOpen(false)} type="button" disabled={submitting}>
              Cancelar
            </Button>
            <Button type="submit" loading={submitting}>
              Notificar y radicar pliego
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal de Detalle de Expediente y Emisión de Resolución */}
      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title={`Expediente sancionatorio ${detalle?.numeroExpediente || (detalle?.idSancion ? `#${detalle.idSancion}` : '')}`}
        size="lg"
      >
        {detalle && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-3 rounded-md border border-border bg-surface-container p-3 sm:grid-cols-4">
              <div>
                <span className="block text-xs text-text-secondary">Estado</span>
                <span className="mt-1 block"><EstadoBadge estado={detalle.estado} /></span>
              </div>
              <div>
                <span className="block text-xs text-text-secondary">Gravedad</span>
                <span className="mt-1 block"><GravedadBadge gravedad={detalle.gravedad} /></span>
              </div>
              <div>
                <span className="block text-xs text-text-secondary">Unidad</span>
                <span className="mt-1 block font-semibold text-on-surface">
                  {detalle.identificadorUnidad || `Unidad #${detalle.idUnidad}`}
                </span>
              </div>
              <div>
                <span className="block text-xs text-text-secondary">Plazo de descargos</span>
                <span className="mt-1 block font-semibold tabular-nums text-on-surface">
                  {detalle.fechaLimiteDescargos || '10 días'}
                </span>
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                <span className="text-text-secondary">
                  Imputado: <strong className="text-on-surface">{detalle.nombreImputado || `ID #${detalle.idPersonaImputada}`}</strong>
                </span>
                <span className="text-text-secondary">
                  Falta: <strong className="text-on-surface">{detalle.tipoFalta}</strong>
                </span>
              </div>

              {detalle.articuloReglamentoViolado && (
                <div className="text-xs text-text-secondary">
                  Norma vulnerada: <span className="font-mono text-on-surface">{detalle.articuloReglamentoViolado}</span>
                </div>
              )}

              <div className="rounded-md border border-border bg-surface-container p-3">
                <strong className="mb-1 block text-xs font-semibold text-text-secondary">
                  Relación de los hechos
                </strong>
                <p className="whitespace-pre-wrap leading-relaxed text-on-surface">{detalle.descripcionHechos}</p>
              </div>
            </div>

            {/* Descargos presentados si existen */}
            {Array.isArray(detalle.descargos) && detalle.descargos.length > 0 && (
              <div className="space-y-2 pt-2 border-t border-border">
                <h4 className="flex items-center gap-1.5 text-[13px] font-bold text-on-surface">
                  <FileText className="h-3.5 w-3.5 text-primary-text" aria-hidden="true" />
                  Descargos presentados <span className="tabular-nums">({detalle.descargos.length})</span>
                </h4>
                <div className="space-y-2">
                  {detalle.descargos.map((d, idx) => (
                    <div key={d.idDescargo || idx} className="rounded-md border border-border bg-surface p-3 text-xs">
                      <div className="mb-1 flex justify-between tabular-nums text-text-muted">
                        <span>Descargo #{idx + 1}</span>
                        <span>{d.fechaPresentacion ? new Date(d.fechaPresentacion).toLocaleDateString('es-CO') : ''}</span>
                      </div>
                      <p className="whitespace-pre-wrap text-on-surface">{d.argumentos || d.textoDescargo}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Opción para anular expediente si está en trámite */}
            {(detalle.estado === 'NOTIFICADA' || detalle.estado === 'EN_DESCARGOS') && (
              <div className="flex justify-end pt-1">
                <Button
                  variant="ghost"
                  type="button"
                  onClick={() => handleAnularSancion(detalle.idSancion)}
                  className="text-destructive hover:bg-tint-red-bg hover:text-destructive"
                  icon={<XCircle className="h-3.5 w-3.5" />}
                >
                  Anular expediente
                </Button>
              </div>
            )}

            {/* Formulario de Emisión de Resolución Final */}
            {detalle.estado === 'EN_DESCARGOS' && (
              <form
                onSubmit={handleEmitirResolucion}
                className="mt-4 space-y-3 rounded-lg border border-border bg-surface p-4"
              >
                <div className="flex items-center gap-2">
                  <Scale className="h-4 w-4 text-primary-text" aria-hidden="true" />
                  <h4 className="text-base font-bold text-on-surface">Emitir y firmar resolución final</h4>
                </div>

                <Select
                  id="sancion-decision"
                  label="Decisión final"
                  value={resolucionForm.decision}
                  onChange={(e) => setResolucionForm({ ...resolucionForm, decision: e.target.value })}
                  required
                >
                  <option value="APLICADA">Aplicar Sanción</option>
                  <option value="ABSUELTA">Absolver y archivar el proceso</option>
                  <option value="ANULADA">Anular el proceso</option>
                </Select>

                {resolucionForm.decision === 'APLICADA' && detalle.tipoSancionPropuesta === 'MULTA_ECONOMICA' && (
                  <div className="space-y-1">
                    <Input
                      id="sancion-monto"
                      label="Monto de la multa (COP)"
                      type="number"
                      min="1000"
                      step="1000"
                      placeholder="Ej. 150000"
                      value={resolucionForm.montoMulta}
                      onChange={(e) => {
                        const val = e.target.value;
                        if (val === '' || Number(val) >= 0) {
                          setResolucionForm({ ...resolucionForm, montoMulta: val });
                        }
                      }}
                      required
                    />
                    {resolucionForm.montoMulta && Number(resolucionForm.montoMulta) > 0 ? (
                      <p className="text-xs text-text-secondary">
                        Valor liquidado: <strong className="tabular-nums text-on-surface">{formatoCOP(resolucionForm.montoMulta)}</strong>
                      </p>
                    ) : (
                      <p className="text-xs text-destructive">El monto debe ser un valor positivo mayor a 0.</p>
                    )}
                  </div>
                )}

                {resolucionForm.decision === 'APLICADA' && detalle.tipoSancionPropuesta === 'SUSPENSION_ZONAS_COMUNES' && (
                  <Input
                    id="sancion-restriccion"
                    label="Suspensión de zonas comunes hasta (inclusive)"
                    type="date"
                    min={new Date().toISOString().slice(0, 10)}
                    value={resolucionForm.restriccionHasta}
                    onChange={(e) => setResolucionForm({ ...resolucionForm, restriccionHasta: e.target.value })}
                    required
                  />
                )}

                <Textarea
                  id="sancion-resolucion"
                  label="Motivación y fundamento jurídico de la resolución"
                  placeholder="Explique las razones de hecho y derecho, valoración de pruebas y descargos..."
                  value={resolucionForm.resolucionFinal}
                  onChange={(e) => setResolucionForm({ ...resolucionForm, resolucionFinal: e.target.value })}
                  required
                  rows={3}
                />

                <Button
                  type="submit"
                  loading={submitting}
                  className="w-full"
                  disabled={
                    submitting ||
                    (resolucionForm.decision === 'APLICADA' &&
                      detalle.tipoSancionPropuesta === 'MULTA_ECONOMICA' &&
                      (!resolucionForm.montoMulta || Number(resolucionForm.montoMulta) <= 0))
                  }
                >
                  Firmar y notificar resolución
                </Button>
              </form>
            )}

            {/* Resolución emitida previamente */}
            {detalle.resolucionFinal && (
              <div className="mt-3 space-y-1 rounded-md border border-tint-green-fg/25 bg-tint-green-bg p-3">
                <span className="flex items-center gap-1.5 text-xs font-bold text-tint-green-fg">
                  <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" /> Resolución final emitida
                </span>
                <p className="whitespace-pre-wrap text-on-surface">{detalle.resolucionFinal}</p>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Annulment reason (replaces window.prompt) */}
      <Modal
        open={!!anulacion}
        onClose={() => {
          if (!submitting) setAnulacion(null);
        }}
        title="Anular expediente sancionatorio"
        size="sm"
      >
        <form onSubmit={confirmarAnulacion} className="space-y-4">
          <p className="text-sm text-text-secondary">
            Indique el motivo de la anulación. Queda registrado en el expediente como garantía de debido proceso.
          </p>
          <Textarea
            id="sancion-motivo-anulacion"
            label="Motivo de la anulación"
            required
            rows={3}
            value={anulacion?.motivo || ''}
            onChange={(e) => setAnulacion((a) => ({ ...a, motivo: e.target.value }))}
          />
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button variant="outline" type="button" onClick={() => setAnulacion(null)} disabled={submitting}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              type="submit"
              loading={submitting}
              disabled={submitting || !anulacion?.motivo?.trim()}
            >
              Anular expediente
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
