import { useState, useEffect } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { Modal } from '../components/ui/Modal';
import { Select, Input, Textarea } from '../components/ui/Form.jsx';
import { Button } from '../components/ui/Button';
import { useFetch, useLiveValidation } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { api } from '../lib/api';
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
} from 'lucide-react';
import { valTexto, valSelect, valNumero, formatoCOP } from '../lib/validation.js';

const PAGE_SIZE = 10;

const ESTADO_BADGE = {
  NOTIFICADA: { class: 'badge-warning', label: 'Notificada', icon: Clock },
  EN_DESCARGOS: { class: 'badge-info', label: 'En Descargos', icon: FileText },
  ABSUELTA: { class: 'badge-neutral', label: 'Absuelta', icon: CheckCircle2 },
  APLICADA: { class: 'badge-error', label: 'Sanción Aplicada', icon: ShieldAlert },
  ANULADA: { class: 'badge-neutral', label: 'Anulada', icon: XCircle },
};

const GRAVEDAD_BADGE = {
  LEVE: { class: 'badge-neutral', label: 'Leve' },
  GRAVE: { class: 'badge-warning', label: 'Grave' },
  GRAVISIMA: { class: 'badge-error', label: 'Gravísima' },
};

const TIPOS_SANCION = [
  { value: 'AMONESTACION_ESCRITA', label: 'Amonestación Escrita' },
  { value: 'AMONESTACION_VERBAL', label: 'Amonestación Verbal' },
  { value: 'MULTA_ECONOMICA', label: 'Multa Económica' },
  { value: 'SUSPENSION_ZONAS_COMUNES', label: 'Suspensión de Zonas Comunes' },
  { value: 'PUBLICACION_LISTA_INFRACTORES', label: 'Publicación en Lista de Infractores' },
];

const INITIAL_FORM_STATE = {
  idPropiedad: '',
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
  const { data, loading, error, refetch } = useFetch(() => api.get('/sanciones/todas'), []);

  const { assignments, activePropertyId } = useTenant();

  // Estados para propiedades, unidades y candidatos a imputación
  const [propiedades, setPropiedades] = useState([]);
  const [loadingPropiedades, setLoadingPropiedades] = useState(false);
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
  const [resolucionForm, setResolucionForm] = useState({
    decision: 'APLICADA',
    resolucionFinal: '',
    montoMulta: '',
  });

  // Cargar lista de propiedades del administrador
  useEffect(() => {
    let active = true;
    async function cargarPropiedades() {
      setLoadingPropiedades(true);
      try {
        const resp = await api.get('/properties');
        const raw = Array.isArray(resp) ? resp : (Array.isArray(resp?.data) ? resp.data : []);
        if (active && raw.length > 0) {
          setPropiedades(raw);
          setLoadingPropiedades(false);
          return;
        }
      } catch {
        // En caso de que falle /properties, fallback a assignments del usuario
      }

      if (active) {
        const propsFromAssignments = [];
        const seen = new Set();
        (assignments || []).forEach((a) => {
          if (a.idPropiedad && !seen.has(a.idPropiedad)) {
            seen.add(a.idPropiedad);
            propsFromAssignments.push({
              id: a.idPropiedad,
              idPropiedad: a.idPropiedad,
              nombre: a.nombrePropiedad || `Propiedad #${a.idPropiedad}`,
            });
          }
        });
        setPropiedades(propsFromAssignments);
        setLoadingPropiedades(false);
      }
    }

    cargarPropiedades();
    return () => {
      active = false;
    };
  }, [assignments]);

  // Al abrir el modal, preseleccionar la propiedad activa
  useEffect(() => {
    if (modalOpen) {
      resetTouched();
      setModoManualPersona(false);
      let propId = '';
      if (activePropertyId && propiedades.some((p) => (p.id || p.idPropiedad) === activePropertyId)) {
        propId = String(activePropertyId);
      } else if (propiedades.length > 0) {
        propId = String(propiedades[0].id || propiedades[0].idPropiedad);
      }
      setForm({
        ...INITIAL_FORM_STATE,
        idPropiedad: propId,
      });
    }
  }, [modalOpen, activePropertyId, propiedades, resetTouched]);

  // Cargar unidades cuando cambia la propiedad seleccionada
  useEffect(() => {
    if (!form.idPropiedad) {
      setUnidades([]);
      return;
    }
    let active = true;
    async function cargarUnidades() {
      setLoadingUnidades(true);
      try {
        const resp = await api.get(`/units?idPropiedad=${form.idPropiedad}`);
        const list = Array.isArray(resp) ? resp : (Array.isArray(resp?.data) ? resp.data : []);
        if (active) {
          setUnidades(list);
        }
      } catch (err) {
        if (active) {
          toast.error('Error al cargar unidades de la copropiedad: ' + (err.message || 'Error de red'));
          setUnidades([]);
        }
      } finally {
        if (active) setLoadingUnidades(false);
      }
    }
    cargarUnidades();
    return () => {
      active = false;
    };
  }, [form.idPropiedad]);

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
        const [resResidents, resOwners] = await Promise.allSettled([
          api.get(`/units/${form.idUnidad}/residents`),
          api.get(`/units/${form.idUnidad}/owners`),
        ]);

        const personasMap = new Map();

        // Procesar residentes
        if (resResidents.status === 'fulfilled' && Array.isArray(resResidents.value)) {
          resResidents.value.forEach((r) => {
            const p = r.persona;
            if (p && p.id && !personasMap.has(p.id)) {
              personasMap.set(p.id, {
                id: p.id,
                nombreCompleto: `${p.primerNombre || ''} ${p.primerApellido || ''}`.trim() || 'Residente',
                documento: p.numeroDocumento || 'S/N',
                rol: r.tipoResidente || 'RESIDENTE',
              });
            }
          });
        }

        // Procesar propietarios
        if (resOwners.status === 'fulfilled' && Array.isArray(resOwners.value)) {
          resOwners.value.forEach((o) => {
            const p = o.persona;
            if (p && p.id && !personasMap.has(p.id)) {
              personasMap.set(p.id, {
                id: p.id,
                nombreCompleto: `${p.primerNombre || ''} ${p.primerApellido || ''}`.trim() || 'Propietario',
                documento: p.numeroDocumento || 'S/N',
                rol: 'PROPIETARIO',
              });
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
  }, [form.idUnidad]);

  // Validaciones en vivo
  const errorPropiedad = fieldError('idPropiedad', valSelect(form.idPropiedad, 'Seleccione la propiedad'));
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
        <span className="font-mono font-medium text-xs text-foreground bg-muted px-2 py-0.5 rounded">
          {r.numeroExpediente || `EXP-${r.idSancion}`}
        </span>
      ),
    },
    {
      key: 'identificadorUnidad',
      label: 'Unidad',
      render: (r) => (
        <div className="flex items-center gap-1.5 font-medium text-foreground">
          <Home className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
          <span>{r.identificadorUnidad || (r.idUnidad ? `Unidad #${r.idUnidad}` : '-')}</span>
        </div>
      ),
    },
    {
      key: 'nombreImputado',
      label: 'Persona Imputada',
      render: (r) => (
        <div className="flex items-center gap-1.5 text-sm text-foreground">
          <User className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
          <span>{r.nombreImputado || (r.idPersonaImputada ? `Persona #${r.idPersonaImputada}` : 'No especificado')}</span>
        </div>
      ),
    },
    { key: 'tipoFalta', label: 'Falta / Infracción' },
    {
      key: 'gravedad',
      label: 'Gravedad',
      render: (r) => {
        const meta = GRAVEDAD_BADGE[r.gravedad] || { class: 'badge-neutral', label: r.gravedad };
        return <span className={`badge ${meta.class}`}>{meta.label}</span>;
      },
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => {
        const meta = ESTADO_BADGE[r.estado] || { class: 'badge-neutral', label: r.estado };
        return <span className={`badge ${meta.class}`}>{meta.label}</span>;
      },
    },
  ];

  async function handleCreate(e) {
    e.preventDefault();
    touchAll(['idPropiedad', 'idUnidad', 'idPersonaImputada', 'tipoFalta', 'descripcionHechos', 'diasParaDescargos']);

    // Validaciones estrictas
    if (!form.idPropiedad) {
      toast.error('Debe seleccionar una propiedad.');
      return;
    }
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
      await api.post('/sanciones', {
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

    setSubmitting(true);
    try {
      const payload = {
        decision: resolucionForm.decision,
        resolucionFinal: resolucionForm.resolucionFinal.trim(),
        montoMulta:
          resolucionForm.decision === 'APLICADA' && detalle?.tipoSancionPropuesta === 'MULTA_ECONOMICA'
            ? Number(resolucionForm.montoMulta)
            : null,
      };
      await api.post(`/sanciones/${detalle.idSancion}/resolucion`, payload);
      toast.success('Resolución firmada y emitida exitosamente');
      setDetalle(null);
      setResolucionForm({ decision: 'APLICADA', resolucionFinal: '', montoMulta: '' });
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al emitir resolución');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleAnularSancion(idSancion) {
    const motivo = window.prompt('Ingrese el motivo de anulación del expediente sancionatorio (garantía de debido proceso):');
    if (!motivo || !motivo.trim()) return;
    setSubmitting(true);
    try {
      await api.put(`/sanciones/${idSancion}/anular`, { motivo: motivo.trim() });
      toast.success('Expediente sancionatorio anulado');
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
        title="Debido Proceso y Sanciones"
        subtitle="Gestión de pliegos, descargos, garantías constitucionales y resoluciones"
        action={
          <div className="flex items-center gap-2">
            <Select
              value={filtroEstado}
              onChange={(e) => {
                setFiltroEstado(e.target.value);
                setPage(0);
              }}
              className="w-48 text-sm"
            >
              <option value="">Todos los estados</option>
              <option value="NOTIFICADA">Notificada</option>
              <option value="EN_DESCARGOS">En Descargos</option>
              <option value="ABSUELTA">Absuelta</option>
              <option value="APLICADA">Sanción Aplicada</option>
              <option value="ANULADA">Anulada</option>
            </Select>
            <Button onClick={() => setModalOpen(true)} className="flex items-center gap-2">
              <Gavel className="w-4 h-4" />
              <span>Nuevo Pliego</span>
            </Button>
          </div>
        }
      />

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{
          icon: 'balance',
          title: 'Sin sanciones registradas',
          subtitle: 'No hay procesos sancionatorios activos en este momento.',
        }}
        error={error?.message}
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

      {/* Modal de Creación de Nuevo Pliego de Cargos */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Abrir Pliego de Cargos (Debido Proceso)"
        size="lg"
      >
        <form onSubmit={handleCreate} className="space-y-4 pt-1">
          <div className="p-3 bg-muted/40 rounded-lg border border-border/60 text-xs text-muted-foreground flex items-start gap-2.5">
            <Scale className="w-4 h-4 text-primary shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold text-foreground">Garantía de Debido Proceso (Ley 675 / CP Art. 29):</span>
              <p className="mt-0.5">
                La notificación del pliego otorgará días hábiles al imputado para presentar sus descargos y controvertir pruebas antes de cualquier fallo.
              </p>
            </div>
          </div>

          {/* Fila 1: Selección de Propiedad y Unidad en cascada */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="relative">
              <Select
                label="Propiedad / Copropiedad"
                required
                id="sancion-propiedad"
                value={form.idPropiedad}
                error={errorPropiedad}
                onBlur={() => touch('idPropiedad')}
                onChange={(e) => {
                  touch('idPropiedad');
                  setForm({
                    ...form,
                    idPropiedad: e.target.value,
                    idUnidad: '',
                    idPersonaImputada: '',
                  });
                }}
                disabled={loadingPropiedades || submitting}
              >
                <option value="">-- Seleccionar Propiedad --</option>
                {propiedades.map((p) => {
                  const pId = p.id || p.idPropiedad;
                  return (
                    <option key={pId} value={pId}>
                      {p.nombre} {p.ciudad ? `(${p.ciudad})` : ''}
                    </option>
                  );
                })}
              </Select>
              {loadingPropiedades && (
                <span className="absolute right-8 top-8 text-xs text-muted-foreground flex items-center gap-1">
                  <Loader2 className="w-3 h-3 animate-spin" /> Cargando...
                </span>
              )}
            </div>

            <div className="relative">
              <Select
                label="Unidad Habitacional"
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
                disabled={!form.idPropiedad || loadingUnidades || submitting}
              >
                <option value="">
                  {!form.idPropiedad
                    ? '-- Seleccione primero una propiedad --'
                    : loadingUnidades
                    ? 'Cargando unidades...'
                    : unidades.length === 0
                    ? '-- Sin unidades registradas --'
                    : '-- Seleccionar Unidad --'}
                </option>
                {unidades.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.identificador} {u.bloqueNombre ? `· ${u.bloqueNombre}` : ''}
                  </option>
                ))}
              </Select>
              {loadingUnidades && (
                <span className="absolute right-8 top-8 text-xs text-muted-foreground flex items-center gap-1">
                  <Loader2 className="w-3 h-3 animate-spin" />
                </span>
              )}
            </div>
          </div>

          {/* Fila 2: Persona Imputada (Habitante de la unidad) */}
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-foreground">
                Persona Imputada <span className="text-destructive font-bold">*</span>
              </span>
              <button
                type="button"
                onClick={() => {
                  setModoManualPersona(!modoManualPersona);
                  setForm((f) => ({ ...f, idPersonaImputada: '' }));
                }}
                className="text-xs text-primary hover:underline focus:outline-none"
              >
                {modoManualPersona ? '← Volver a lista de habitantes' : '¿Ingresar ID manual?'}
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
                      ? '-- Seleccione primero una unidad --'
                      : loadingCandidatos
                      ? 'Cargando habitantes de la unidad...'
                      : candidatos.length === 0
                      ? '-- No hay habitantes registrados (use ID manual) --'
                      : '-- Seleccionar Habitante / Propietario --'}
                  </option>
                  {candidatos.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nombreCompleto} ({c.rol}) - Doc: {c.documento}
                    </option>
                  ))}
                </Select>
                {loadingCandidatos && (
                  <span className="absolute right-8 top-2.5 text-xs text-muted-foreground flex items-center gap-1">
                    <Loader2 className="w-3 h-3 animate-spin" />
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
            <p className="text-[11px] text-muted-foreground">
              Seleccione al residente o propietario presuntamente responsable del hecho violatorio.
            </p>
          </div>

          {/* Fila 3: Falta, Gravedad y Sanción Propuesta */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-1">
              <Input
                label="Tipo de Falta"
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
                label="Sanción Propuesta"
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
              label="Días Hábiles para Descargos"
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
              label="Artículo de Reglamento Violado (Opcional)"
              id="sancion-articulo"
              placeholder="Ej. Art. 45 Inciso 3 - Manual de Convivencia"
              value={form.articuloReglamentoViolado}
              onChange={(e) => setForm({ ...form, articuloReglamentoViolado: e.target.value })}
            />
          </div>

          {/* Fila 5: Hechos */}
          <Textarea
            label="Descripción Circunstanciada de los Hechos"
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
              Notificar y Radicar Pliego
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal de Detalle de Expediente y Emisión de Resolución */}
      <Modal
        open={!!detalle}
        onClose={() => setDetalle(null)}
        title={`Expediente Sancionatorio ${detalle?.numeroExpediente || (detalle?.idSancion ? `#${detalle.idSancion}` : '')}`}
        size="lg"
      >
        {detalle && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-muted/30 rounded-lg border border-border/60">
              <div>
                <span className="text-xs text-muted-foreground block">Estado</span>
                <span className={`badge mt-1 ${ESTADO_BADGE[detalle.estado]?.class || 'badge-neutral'}`}>
                  {ESTADO_BADGE[detalle.estado]?.label || detalle.estado}
                </span>
              </div>
              <div>
                <span className="text-xs text-muted-foreground block">Gravedad</span>
                <span className="font-semibold text-foreground mt-1 block">{detalle.gravedad}</span>
              </div>
              <div>
                <span className="text-xs text-muted-foreground block">Unidad</span>
                <span className="font-semibold text-foreground mt-1 block">
                  {detalle.identificadorUnidad || `Unidad #${detalle.idUnidad}`}
                </span>
              </div>
              <div>
                <span className="text-xs text-muted-foreground block">Plazo Descargos</span>
                <span className="font-semibold text-foreground mt-1 block">
                  {detalle.fechaLimiteDescargos || '10 días'}
                </span>
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="text-muted-foreground">
                  Imputado: <strong className="text-foreground">{detalle.nombreImputado || `ID #${detalle.idPersonaImputada}`}</strong>
                </span>
                <span className="text-muted-foreground">
                  Falta: <strong className="text-foreground">{detalle.tipoFalta}</strong>
                </span>
              </div>

              {detalle.articuloReglamentoViolado && (
                <div className="text-xs text-muted-foreground">
                  Norma vulnerada: <span className="font-mono text-foreground">{detalle.articuloReglamentoViolado}</span>
                </div>
              )}

              <div className="p-3 bg-muted/40 rounded-lg border border-border/80">
                <strong className="text-xs uppercase tracking-wider text-muted-foreground block mb-1">
                  Relación de los Hechos:
                </strong>
                <p className="text-foreground leading-relaxed whitespace-pre-wrap">{detalle.descripcionHechos}</p>
              </div>
            </div>

            {/* Descargos presentados si existen */}
            {Array.isArray(detalle.descargos) && detalle.descargos.length > 0 && (
              <div className="space-y-2 pt-2 border-t border-border">
                <h4 className="font-semibold text-xs uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                  <FileText className="w-3.5 h-3.5 text-primary" /> Descargos Presentados ({detalle.descargos.length}):
                </h4>
                <div className="space-y-2">
                  {detalle.descargos.map((d, idx) => (
                    <div key={d.idDescargo || idx} className="p-3 bg-muted/20 border border-border rounded-lg text-xs">
                      <div className="flex justify-between text-muted-foreground mb-1">
                        <span>Descargo #{idx + 1}</span>
                        <span>{d.fechaPresentacion ? new Date(d.fechaPresentacion).toLocaleDateString('es-CO') : ''}</span>
                      </div>
                      <p className="text-foreground whitespace-pre-wrap">{d.argumentos || d.textoDescargo}</p>
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
                  className="text-destructive hover:bg-destructive/10 text-xs"
                >
                  <XCircle className="w-3.5 h-3.5 mr-1" /> Anular Expediente Sancionatorio
                </Button>
              </div>
            )}

            {/* Formulario de Emisión de Resolución Final */}
            {detalle.estado === 'EN_DESCARGOS' && (
              <form
                onSubmit={handleEmitirResolucion}
                className="mt-4 p-4 rounded-xl bg-card border border-border space-y-3"
              >
                <div className="flex items-center gap-2">
                  <Scale className="w-4 h-4 text-primary" />
                  <h4 className="font-bold text-base text-foreground">Emitir y Firmar Resolución Final</h4>
                </div>

                <Select
                  label="Decisión Final"
                  value={resolucionForm.decision}
                  onChange={(e) => setResolucionForm({ ...resolucionForm, decision: e.target.value })}
                  required
                >
                  <option value="APLICADA">Aplicar Sanción</option>
                  <option value="ABSUELTA">Absolver / Archivar Proceso</option>
                  <option value="ANULADA">Anular Proceso</option>
                </Select>

                {resolucionForm.decision === 'APLICADA' && detalle.tipoSancionPropuesta === 'MULTA_ECONOMICA' && (
                  <div className="space-y-1">
                    <Input
                      label="Monto de la Multa (COP) *"
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
                      <p className="text-xs text-muted-foreground">
                        Valor liquidado: <strong className="text-foreground">{formatoCOP(resolucionForm.montoMulta)}</strong>
                      </p>
                    ) : (
                      <p className="text-xs text-destructive">El monto debe ser un valor positivo mayor a 0.</p>
                    )}
                  </div>
                )}

                <Textarea
                  label="Motivación y Fundamento Jurídico de la Resolución"
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
                  Firmar y Notificar Resolución
                </Button>
              </form>
            )}

            {/* Resolución emitida previamente */}
            {detalle.resolucionFinal && (
              <div className="p-3 bg-muted/40 border border-border rounded-lg space-y-1 mt-3">
                <span className="text-xs font-semibold text-primary block flex items-center gap-1.5">
                  <CheckCircle2 className="w-3.5 h-3.5" /> Resolución Final Emitida:
                </span>
                <p className="text-foreground whitespace-pre-wrap">{detalle.resolucionFinal}</p>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
