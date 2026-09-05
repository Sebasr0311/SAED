import { useState, useRef, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  Building,
  ChevronLeft,
  ChevronRight,
  Mail,
  Pencil,
  Phone,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import {
  valNombre,
  valApellido,
  valDocumento,
  valFechaNacimiento,
  valTelefono,
  valEmail,
  valSelect,
} from '../lib/validation.js';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardContent, CardHeader } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';

const emptyForm = {
  idTipoDoc: 1,
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  fechaNacimiento: '',
  telefono: '',
  email: '',
  idApartamento: '',
};

const emptyTutorForm = {
  idTipoDoc: '',
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  email: '',
  parentesco: '',
  otroParentesco: '',
};

function calcularEdad(fechaNacimiento) {
  if (!fechaNacimiento) return null;
  const hoy = new Date();
  const nac = new Date(fechaNacimiento);
  let edad = hoy.getFullYear() - nac.getFullYear();
  const m = hoy.getMonth() - nac.getMonth();
  if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
  return edad;
}

const PAGE_SIZE = 15;

/**
 * ResidentesPage 2.0 — Censo y Gestión Integral de Residentes.
 * Modern Enterprise SaaS / PropTech Premium.
 *
 * Mantiene estrictamente los contratos REST, validaciones y lógica CRUD
 * aislando el contexto multi-tenant con useTenantApi().
 */
export default function ResidentesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [tutorForm, setTutorForm] = useState(emptyTutorForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);

  // 1. Censo de Personas/Residentes
  const {
    data,
    loading,
    error: errorPersonas,
    refetch,
  } = useFetch(() => tenantApi.get('/personas'), [tenant.activeAssignmentId]);

  // 2. Unidades Habitacionales de la propiedad activa
  const { data: apartamentos } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );

  // 3. Catálogo de Tipos de Documento
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, fieldError } = useLiveValidation();

  // Validación menor de edad / tutor
  const edad = calcularEdad(form.fechaNacimiento);
  const requiereTutor = edad !== null && edad >= 16 && edad < 18;

  // Mapa rápido de unidades
  const unitMap = useMemo(() => {
    const map = new Map();
    const list = apartamentos?.items || (Array.isArray(apartamentos) ? apartamentos : []);
    list.forEach((u) => {
      const id = u.idApartamento || u.id || u.idUnidad;
      if (id) {
        const desc = u.numero
          ? `Apto ${u.numero}${u.bloque ? ` · ${u.bloque}` : ''}`
          : `Unidad ${id}`;
        map.set(Number(id), desc);
      }
    });
    return map;
  }, [apartamentos]);

  // Mapa rápido de tipos de documento
  const tipoDocMap = useMemo(() => {
    const map = new Map();
    tiposDoc.forEach((t) => {
      const id = t.idTipoDoc ?? t.id ?? t.value;
      if (id) map.set(Number(id), t.codigo || t.nombre || t.descripcion);
    });
    return map;
  }, [tiposDoc]);

  // Lista normalizada y filtrada
  const items = useMemo(() => {
    const raw = Array.isArray(data) ? data : data?.items || [];
    return raw
      .map((r) => ({
        ...r,
        nombres:
          (r.primerNombre
            ? (r.primerNombre + ' ' + (r.segundoNombre || '')).trim()
            : r.nombres) || '',
        apellidos:
          (r.primerApellido
            ? (r.primerApellido + ' ' + (r.segundoApellido || '')).trim()
            : r.apellidos) || '',
        idTipoDoc: r.tipoDocumentoId || r.idTipoDoc,
      }))
      .filter((r) => {
        if (!search) return true;
        const term = search.toLowerCase();
        return [r.nombres, r.apellidos, r.numeroDocumento]
          .filter(Boolean)
          .some((v) => String(v).toLowerCase().includes(term));
      });
  }, [data, search]);

  // KPIs calculados
  const kpis = useMemo(() => {
    const raw = Array.isArray(data) ? data : data?.items || [];
    const total = raw.length;
    const conUnidad = raw.filter((r) => r.idApartamento || r.numeroApartamento).length;
    const conEmail = raw.filter((r) => r.email || r.correoElectronico).length;
    const menores = raw.filter(
      (r) => r.esMenorEdad || (r.fechaNacimiento && calcularEdad(r.fechaNacimiento) < 18)
    ).length;

    return { total, conUnidad, conEmail, menores };
  }, [data]);

  // Paginación
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // Apertura de Modales
  const openCreate = useCallback(() => {
    setEditing(null);
    setForm(emptyForm);
    setTutorForm(emptyTutorForm);
    setErrors({});
    setModalOpen(true);
  }, []);

  const openEdit = useCallback(
    (row) => {
      setEditing(row);
      setForm({
        idTipoDoc: row.idTipoDoc || 1,
        numeroDocumento: row.numeroDocumento || '',
        nombres: row.nombres || '',
        apellidos: row.apellidos || '',
        fechaNacimiento: row.fechaNacimiento || '',
        telefono: row.telefono || '',
        email: row.email || '',
        idApartamento: row.idApartamento || '',
      });
      setTutorForm(emptyTutorForm);
      setErrors({});
      setModalOpen(true);

      if (row.esMenorEdad) {
        tenantApi
          .get(`/residentes/${row.id}`)
          .then((r) => {
            const t = r?.tutor;
            if (t) {
              setTutorForm({
                idTipoDoc: t.idTipoDoc || '',
                numeroDocumento: t.numeroDocumento || '',
                nombres: t.nombres || '',
                apellidos: t.apellidos || '',
                telefono: t.telefono || '',
                email: t.email || '',
                parentesco: t.parentesco || '',
                otroParentesco: t.otroParentesco || '',
              });
            }
          })
          .catch(() => {
            /* Tutor no disponible — no bloqueante */
          });
      }
    },
    [tenantApi]
  );

  const update = useCallback((k, v) => {
    setForm((f) => ({ ...f, [k]: v }));
  }, []);

  const updateTutor = useCallback((k, v) => {
    setTutorForm((f) => ({ ...f, [k]: v }));
  }, []);

  // Validación
  const validate = useCallback(() => {
    const e = {};
    const codigoDoc =
      tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || '';
    const rNombre = valNombre(form.nombres, 'El nombre');
    if (!rNombre.ok) e.nombres = rNombre.mensaje;
    const rApellido = valApellido(form.apellidos, 'El apellido');
    if (!rApellido.ok) e.apellidos = rApellido.mensaje;
    const rDoc = valDocumento(form.numeroDocumento, codigoDoc, 'El documento');
    if (!rDoc.ok) e.numeroDocumento = rDoc.mensaje;
    const rFecha = valFechaNacimiento(form.fechaNacimiento, { edadMin: 0, edadMax: 115 });
    if (!rFecha.ok) e.fechaNacimiento = rFecha.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(form.email);
    if (!rEmail.ok) e.email = rEmail.mensaje;

    if (requiereTutor) {
      const tCodigo =
        tiposDoc.find((t) => Number(t.idTipoDoc) === Number(tutorForm.idTipoDoc))?.codigo || '';
      const rTN = valNombre(tutorForm.nombres, 'El nombre del tutor');
      if (!rTN.ok) e['tutor.nombres'] = rTN.mensaje;
      const rTA = valApellido(tutorForm.apellidos, 'El apellido del tutor');
      if (!rTA.ok) e['tutor.apellidos'] = rTA.mensaje;
      const rTDoc = valDocumento(tutorForm.numeroDocumento, tCodigo, 'El documento del tutor');
      if (!rTDoc.ok) e['tutor.numeroDocumento'] = rTDoc.mensaje;
      const rTTel = valTelefono(tutorForm.telefono);
      if (!rTTel.ok) e['tutor.telefono'] = rTTel.mensaje;
      const rTEmail = valEmail(tutorForm.email);
      if (!rTEmail.ok) e['tutor.email'] = rTEmail.mensaje;
      const rParent = valSelect(tutorForm.parentesco, 'Seleccione el parentesco');
      if (!rParent.ok) e['tutor.parentesco'] = rParent.mensaje;
      if (tutorForm.parentesco === 'OTRO' && !tutorForm.otroParentesco.trim()) {
        e['tutor.otroParentesco'] = 'Especifique el parentesco';
      }
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }, [form, requiereTutor, tiposDoc, tutorForm]);

  // Guardado CRUD
  const save = useCallback(async () => {
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);

    const _nombres = form.nombres.trim().split(' ');
    const _apellidos = form.apellidos.trim().split(' ');
    const payload = {
      tipoDocumentoId: Number(form.idTipoDoc),
      numeroDocumento: form.numeroDocumento,
      tipoPersona: 'NATURAL',
      primerNombre: _nombres[0] || '',
      segundoNombre: _nombres.slice(1).join(' ') || '',
      primerApellido: _apellidos[0] || '',
      segundoApellido: _apellidos.slice(1).join(' ') || '',
      email: form.email,
      telefono: form.telefono,
    };

    try {
      let idResidente;
      if (editing) {
        await tenantApi.put(`/personas/${editing.id}`, payload);
        idResidente = editing.id;
        toast.success('Residente actualizado con éxito');
      } else {
        const res = await tenantApi.post('/personas', payload);
        idResidente = res.id;
        toast.success('Residente registrado con éxito');
      }

      const aptSeleccionado = form.idApartamento !== '';
      const asignacionCambia =
        aptSeleccionado &&
        (!editing || Number(editing.idApartamento) !== Number(form.idApartamento));
      if (asignacionCambia) {
        try {
          await tenantApi.post(`/residentes/${idResidente}/asignar-apartamento`, {
            idApartamento: Number(form.idApartamento),
            rolEnContrato: 'OTRO',
          });
          const verif = await tenantApi.get(`/personas/${idResidente}`);
          if (Number(verif?.idApartamento) !== Number(form.idApartamento)) {
            throw new Error('La asignación no se pudo confirmar en el servidor');
          }
        } catch (err) {
          toast.error(
            `Residente guardado, pero la asignación al apartamento falló: ${err.message}`
          );
        }
      }

      setModalOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al procesar el residente');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }, [editing, form, refetch, tenantApi, validate]);

  // Eliminación CRUD
  const handleDelete = useCallback(async () => {
    if (!confirmDel) return;
    try {
      await tenantApi.del(`/personas/${confirmDel.id}`);
      toast.success('Residente eliminado correctamente');
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo eliminar el residente');
    } finally {
      setConfirmDel(null);
    }
  }, [confirmDel, refetch, tenantApi]);

  return (
    <PageContainer className="space-y-6">
      {/* 1. Header Contextual Enterprise */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between border-b border-border/70 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Residentes
            </h1>
            <Badge
              variant="outline"
              className="bg-primary/5 text-primary border-primary/20 text-xs font-semibold"
            >
              Censo Poblacional
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground">
            Gestión, registro y censo de habitantes de la copropiedad
          </p>
        </div>

        {/* Acciones del Header */}
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
          <Button
            variant="outline"
            size="sm"
            onClick={refetch}
            disabled={loading}
            className="text-xs min-h-[44px] sm:min-h-9"
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1.5 ${loading ? 'animate-spin text-primary' : ''}`}
              aria-hidden="true"
            />
            Actualizar
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={openCreate}
            className="text-xs min-h-[44px] sm:min-h-9 shadow-xs"
          >
            <UserPlus className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            Nuevo Residente
          </Button>
        </div>
      </div>

      {/* 2. Grid de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total Residentes"
          value={kpis.total}
          subtitle="Censo de la copropiedad"
          icon={Users}
          variant="primary"
        />
        <MetricCard
          label="Con Unidad Asignada"
          value={kpis.conUnidad}
          subtitle="Habitantes vinculados"
          icon={Building}
          variant="info"
        />
        <MetricCard
          label="Canal Digital Activo"
          value={kpis.conEmail}
          subtitle="Correos electrónicos registrados"
          icon={Mail}
          variant="success"
        />
        <MetricCard
          label="Menores en Censo"
          value={kpis.menores}
          subtitle="Con tutor legal requerido"
          icon={ShieldCheck}
          variant="secondary"
        />
      </div>

      {/* 3. Card Principal: Búsqueda, Filtro y Listado */}
      <Card className="border-border/80 shadow-xs overflow-hidden">
        {/* Barra de Búsqueda y Herramientas */}
        <CardHeader className="p-4 sm:p-5 border-b border-border/50 bg-card">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div className="relative flex-1 max-w-md">
              <Search
                className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none"
                aria-hidden="true"
              />
              <input
                id="search-residentes"
                type="text"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
                placeholder="Buscar por nombre, apellido o documento..."
                className="w-full pl-9 pr-9 py-2 text-xs sm:text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all text-foreground placeholder:text-muted-foreground"
                aria-label="Buscar residentes"
              />
              {search && (
                <button
                  type="button"
                  onClick={() => {
                    setSearch('');
                    setPage(0);
                  }}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-muted-foreground hover:text-foreground rounded-full"
                  aria-label="Limpiar búsqueda"
                >
                  <X className="h-3.5 w-3.5" aria-hidden="true" />
                </button>
              )}
            </div>

            <div className="flex items-center gap-2 text-xs text-muted-foreground self-end sm:self-center">
              <span>
                Mostrando <strong className="text-foreground">{rows.length}</strong> de{' '}
                <strong className="text-foreground">{items.length}</strong> residentes
              </span>
            </div>
          </div>
        </CardHeader>

        {/* Contenido Principal: Estados y Datos */}
        <CardContent className="p-0">
          {loading && !data ? (
            <div className="p-8">
              <LoadingState
                message="Cargando residentes..."
                description="Consultando censo poblacional con aislamiento de copropiedad"
              />
            </div>
          ) : errorPersonas && !data ? (
            <div className="p-8">
              <ErrorState
                title="Error al consultar residentes"
                message={
                  errorPersonas?.message ||
                  'No se pudo sincronizar la lista de habitantes. Verifique su conexión o intente nuevamente.'
                }
                onRetry={refetch}
              />
            </div>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-center">
              <div className="p-3.5 rounded-2xl bg-primary/10 text-primary mb-3">
                <Users className="h-8 w-8" aria-hidden="true" />
              </div>
              <h3 className="text-base font-semibold text-foreground">
                {search ? 'Sin resultados encontrados' : 'No hay residentes registrados'}
              </h3>
              <p className="text-xs sm:text-sm text-muted-foreground max-w-sm mt-1 mb-4">
                {search
                  ? `No se encontró ningún residente que coincida con "${search}". Intente con otro criterio de búsqueda.`
                  : 'Comience registrando al primer habitante de la copropiedad con el botón "Nuevo Residente".'}
              </p>
              {search ? (
                <Button variant="outline" size="sm" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              ) : (
                <Button variant="primary" size="sm" onClick={openCreate}>
                  <UserPlus className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
                  Registrar Residente
                </Button>
              )}
            </div>
          ) : (
            <>
              {/* Tabla Desktop & Tablet (md+) */}
              <div className="hidden md:block overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-border/70 bg-muted/30 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                      <th className="py-3 px-4 w-12">ID</th>
                      <th className="py-3 px-4">Residente</th>
                      <th className="py-3 px-4">Identificación</th>
                      <th className="py-3 px-4">Unidad / Apto</th>
                      <th className="py-3 px-4">Contacto</th>
                      <th className="py-3 px-4 text-right w-24">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/50 text-xs">
                    {rows.map((r) => {
                      const initial = (r.nombres?.[0] || 'R').toUpperCase();
                      const tipoDocLabel = tipoDocMap.get(Number(r.idTipoDoc)) || 'DOC';
                      const unidadDesc =
                        r.numeroApartamento
                          ? `Apto ${r.numeroApartamento}`
                          : unitMap.get(Number(r.idApartamento));

                      return (
                        <tr
                          key={r.id}
                          className="hover:bg-muted/40 transition-colors group"
                        >
                          <td className="py-3.5 px-4 font-mono text-[11px] text-muted-foreground">
                            #{r.id}
                          </td>
                          <td className="py-3.5 px-4">
                            <div className="flex items-center gap-3">
                              <div
                                className="h-8 w-8 rounded-full bg-primary/10 text-primary font-bold flex items-center justify-center text-xs shrink-0"
                                aria-hidden="true"
                              >
                                {initial}
                              </div>
                              <div className="min-w-0">
                                <p className="font-semibold text-foreground group-hover:text-primary transition-colors truncate">
                                  {r.nombres} {r.apellidos}
                                </p>
                                <p className="text-[11px] text-muted-foreground flex items-center gap-1.5 mt-0.5">
                                  {r.tipoPersona && (
                                    <span className="capitalize">{r.tipoPersona.toLowerCase()}</span>
                                  )}
                                  {r.tipo && <span>· {r.tipo}</span>}
                                  {r.esMenorEdad && (
                                    <Badge
                                      variant="warning"
                                      className="text-[9px] px-1 py-0 uppercase font-bold"
                                    >
                                      Menor
                                    </Badge>
                                  )}
                                </p>
                              </div>
                            </div>
                          </td>
                          <td className="py-3.5 px-4">
                            <div className="space-y-0.5">
                              <span className="inline-block px-1.5 py-0.5 text-[10px] font-semibold rounded bg-muted text-muted-foreground border border-border">
                                {tipoDocLabel}
                              </span>
                              <p className="font-mono text-foreground text-xs">
                                {r.numeroDocumento || '—'}
                              </p>
                            </div>
                          </td>
                          <td className="py-3.5 px-4">
                            {unidadDesc ? (
                              <Badge
                                variant="outline"
                                className="bg-info-50 text-info-700 border-info-200 dark:bg-info-950/40 dark:text-info-400 dark:border-info-800 text-[11px] font-medium"
                              >
                                <Building className="h-3 w-3 mr-1" aria-hidden="true" />
                                {unidadDesc}
                              </Badge>
                            ) : (
                              <span className="text-muted-foreground text-[11px]">
                                — Sin asignar —
                              </span>
                            )}
                          </td>
                          <td className="py-3.5 px-4">
                            <div className="space-y-0.5">
                              {r.email && (
                                <p className="text-muted-foreground flex items-center gap-1.5 truncate max-w-[200px]">
                                  <Mail className="h-3 w-3 shrink-0 text-muted-foreground/70" aria-hidden="true" />
                                  <span className="truncate">{r.email}</span>
                                </p>
                              )}
                              {r.telefono && (
                                <p className="text-muted-foreground flex items-center gap-1.5">
                                  <Phone className="h-3 w-3 shrink-0 text-muted-foreground/70" aria-hidden="true" />
                                  <span>{r.telefono}</span>
                                </p>
                              )}
                              {!r.email && !r.telefono && (
                                <span className="text-muted-foreground text-[11px]">Sin contacto</span>
                              )}
                            </div>
                          </td>
                          <td className="py-3.5 px-4 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  openEdit(r);
                                }}
                                className="p-1.5 rounded-lg text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                                aria-label={`Editar a ${r.nombres}`}
                              >
                                <Pencil className="h-4 w-4" aria-hidden="true" />
                              </button>
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setConfirmDel(r);
                                }}
                                className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                                aria-label={`Eliminar a ${r.nombres}`}
                              >
                                <Trash2 className="h-4 w-4" aria-hidden="true" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Vista Móvil Adaptativa (sm/mobile) */}
              <div className="md:hidden divide-y divide-border/60">
                {rows.map((r) => {
                  const initial = (r.nombres?.[0] || 'R').toUpperCase();
                  const tipoDocLabel = tipoDocMap.get(Number(r.idTipoDoc)) || 'DOC';
                  const unidadDesc =
                    r.numeroApartamento
                      ? `Apto ${r.numeroApartamento}`
                      : unitMap.get(Number(r.idApartamento));

                  return (
                    <div key={r.id} className="p-4 space-y-3 hover:bg-muted/30 transition-colors">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div
                            className="h-9 w-9 rounded-full bg-primary/10 text-primary font-bold flex items-center justify-center text-xs shrink-0"
                            aria-hidden="true"
                          >
                            {initial}
                          </div>
                          <div className="min-w-0">
                            <p className="text-sm font-semibold text-foreground truncate">
                              {r.nombres} {r.apellidos}
                            </p>
                            <p className="text-[11px] text-muted-foreground font-mono">
                              {tipoDocLabel} {r.numeroDocumento || '—'}
                            </p>
                          </div>
                        </div>
                        {unidadDesc && (
                          <Badge
                            variant="outline"
                            className="bg-info-50 text-info-700 border-info-200 dark:bg-info-950/40 dark:text-info-400 dark:border-info-800 text-[10px] shrink-0"
                          >
                            {unidadDesc}
                          </Badge>
                        )}
                      </div>

                      <div className="grid grid-cols-1 gap-1 text-xs text-muted-foreground pt-1">
                        {r.email && (
                          <p className="flex items-center gap-1.5 truncate">
                            <Mail className="h-3.5 w-3.5 shrink-0 text-muted-foreground/80" aria-hidden="true" />
                            <span className="truncate">{r.email}</span>
                          </p>
                        )}
                        {r.telefono && (
                          <p className="flex items-center gap-1.5">
                            <Phone className="h-3.5 w-3.5 shrink-0 text-muted-foreground/80" aria-hidden="true" />
                            <span>{r.telefono}</span>
                          </p>
                        )}
                      </div>

                      <div className="flex items-center justify-end gap-2 pt-1 border-t border-border/40">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => openEdit(r)}
                          className="text-xs h-9 min-h-[44px] flex-1"
                        >
                          <Pencil className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
                          Editar
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => setConfirmDel(r)}
                          className="text-xs h-9 min-h-[44px] flex-1"
                        >
                          <Trash2 className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
                          Eliminar
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Paginación Enterprise */}
              {totalPages > 1 && (
                <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 border-t border-border/60 bg-card">
                  <div className="text-xs text-muted-foreground">
                    Página <strong className="text-foreground">{safePage + 1}</strong> de{' '}
                    <strong className="text-foreground">{totalPages}</strong> ({items.length}{' '}
                    residentes)
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={safePage === 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      className="text-xs min-h-[44px] sm:min-h-8"
                    >
                      <ChevronLeft className="h-3.5 w-3.5 mr-1" aria-hidden="true" />
                      Anterior
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={safePage >= totalPages - 1}
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      className="text-xs min-h-[44px] sm:min-h-8"
                    >
                      Siguiente
                      <ChevronRight className="h-3.5 w-3.5 ml-1" aria-hidden="true" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      {/* 4. Modal Crear / Editar Residente */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar Residente' : 'Nuevo Residente'}
        size="lg"
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => setModalOpen(false)}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              Cancelar
            </Button>
            <Button
              variant="primary"
              onClick={save}
              disabled={saving}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              {saving ? 'Guardando...' : editing ? 'Guardar Cambios' : 'Registrar Residente'}
            </Button>
          </>
        }
      >
        <div className="space-y-4 pt-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              id="idTipoDoc"
              label="Tipo de Documento"
              value={form.idTipoDoc}
              onChange={(e) => update('idTipoDoc', Number(e.target.value))}
            >
              {tiposDoc.map((t) => (
                <option
                  key={t.idTipoDoc ?? t.id ?? t.value}
                  value={t.idTipoDoc ?? t.id ?? t.value}
                >
                  {t.descripcion || t.nombre}
                </option>
              ))}
            </Select>
            <Input
              id="numeroDocumento"
              label="Número de Documento"
              value={form.numeroDocumento}
              onChange={(e) => update('numeroDocumento', e.target.value)}
              error={errors.numeroDocumento}
              placeholder="Ej. 1020304050"
            />
          </div>

          {errorTiposDoc && !tiposDoc.length && (
            <p className="text-xs text-destructive">
              Error al consultar el catálogo de tipos de documento.
            </p>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="nombres"
              label="Nombres"
              value={form.nombres}
              onChange={(e) => update('nombres', e.target.value)}
              error={errors.nombres}
              placeholder="Ej. Carlos Alberto"
            />
            <Input
              id="apellidos"
              label="Apellidos"
              value={form.apellidos}
              onChange={(e) => update('apellidos', e.target.value)}
              error={errors.apellidos}
              placeholder="Ej. Martínez Gómez"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="fechaNacimiento"
              label="Fecha de Nacimiento"
              type="date"
              value={form.fechaNacimiento}
              onChange={(e) => update('fechaNacimiento', e.target.value)}
              error={errors.fechaNacimiento}
            />
            <Input
              id="telefono"
              label="Teléfono Móvil"
              value={form.telefono}
              onChange={(e) => update('telefono', e.target.value)}
              onBlur={() => touch('telefono')}
              error={
                fieldError('telefono', valTelefono(form.telefono, { required: false })) ||
                errors.telefono
              }
              placeholder="Ej. 3001234567"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="email"
              label="Correo Electrónico"
              type="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              onBlur={() => touch('email')}
              error={
                fieldError('email', valEmail(form.email, { required: false })) || errors.email
              }
              placeholder="Ej. residente@dominio.com"
            />
            <Select
              id="idApartamento"
              label="Apartamento Asignado"
              value={form.idApartamento}
              onChange={(e) => update('idApartamento', e.target.value)}
            >
              <option value="">— Sin asignar —</option>
              {(apartamentos?.items || (Array.isArray(apartamentos) ? apartamentos : [])).map(
                (a) => {
                  const id = a.idApartamento || a.id || a.idUnidad;
                  return (
                    <option key={id} value={id}>
                      Apto {a.numero}
                      {a.bloque ? ` - ${a.bloque}` : ''}
                      {a.piso ? ` (Piso ${a.piso})` : ''}
                    </option>
                  );
                }
              )}
            </Select>
          </div>

          {/* Sección Condicional: Tutor Legal */}
          {requiereTutor && (
            <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 p-4 space-y-3 mt-4">
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-4 w-4 text-amber-600 dark:text-amber-400" aria-hidden="true" />
                <h4 className="text-xs font-bold uppercase tracking-wider text-amber-700 dark:text-amber-300">
                  Datos del Tutor Legal Requerido
                </h4>
              </div>
              <p className="text-[11px] text-muted-foreground leading-relaxed">
                El residente registrado tiene entre 16 y 17 años. Conforme a la normativa de
                propiedad horizontal, puede residir independientemente pero requiere un tutor legal
                registrado.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                <Select
                  id="tutor-idTipoDoc"
                  label="Tipo Documento Tutor"
                  value={tutorForm.idTipoDoc}
                  onChange={(e) => updateTutor('idTipoDoc', Number(e.target.value))}
                  error={errors['tutor.idTipoDoc']}
                >
                  <option value="">Seleccione...</option>
                  {tiposDoc.map((t) => (
                    <option
                      key={t.idTipoDoc ?? t.id ?? t.value}
                      value={t.idTipoDoc ?? t.id ?? t.value}
                    >
                      {t.descripcion || t.nombre}
                    </option>
                  ))}
                </Select>
                <Input
                  id="tutor-numeroDocumento"
                  label="Documento Tutor"
                  value={tutorForm.numeroDocumento}
                  onChange={(e) => updateTutor('numeroDocumento', e.target.value)}
                  error={errors['tutor.numeroDocumento']}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input
                  id="tutor-nombres"
                  label="Nombres Tutor"
                  value={tutorForm.nombres}
                  onChange={(e) => updateTutor('nombres', e.target.value)}
                  error={errors['tutor.nombres']}
                />
                <Input
                  id="tutor-apellidos"
                  label="Apellidos Tutor"
                  value={tutorForm.apellidos}
                  onChange={(e) => updateTutor('apellidos', e.target.value)}
                  error={errors['tutor.apellidos']}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input
                  id="tutor-telefono"
                  label="Teléfono Tutor"
                  value={tutorForm.telefono}
                  onChange={(e) => updateTutor('telefono', e.target.value)}
                  error={errors['tutor.telefono']}
                />
                <Input
                  id="tutor-email"
                  label="Email Tutor"
                  type="email"
                  value={tutorForm.email}
                  onChange={(e) => updateTutor('email', e.target.value)}
                  error={errors['tutor.email']}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Select
                  id="tutor-parentesco"
                  label="Parentesco"
                  value={tutorForm.parentesco}
                  onChange={(e) => {
                    updateTutor('parentesco', e.target.value);
                    if (e.target.value !== 'OTRO') updateTutor('otroParentesco', '');
                  }}
                  error={errors['tutor.parentesco']}
                >
                  <option value="">Seleccione parentesco...</option>
                  <option value="PADRE">Padre</option>
                  <option value="MADRE">Madre</option>
                  <option value="ABUELO">Abuelo</option>
                  <option value="ABUELA">Abuela</option>
                  <option value="TIO">Tío</option>
                  <option value="TIA">Tía</option>
                  <option value="HERMANO">Hermano</option>
                  <option value="HERMANA">Hermana</option>
                  <option value="TUTOR_LEGAL">Tutor Legal</option>
                  <option value="OTRO">Otro</option>
                </Select>
                {tutorForm.parentesco === 'OTRO' && (
                  <Input
                    id="tutor-parentesco-otro"
                    label="Especifique Parentesco"
                    value={tutorForm.otroParentesco}
                    onChange={(e) => updateTutor('otroParentesco', e.target.value)}
                    error={errors['tutor.otroParentesco']}
                  />
                )}
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* 5. Modal Confirmación de Eliminación */}
      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar residente"
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => setConfirmDel(null)}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={() => setPwdConfirmOpen(true)}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              Continuar a Confirmación
            </Button>
          </>
        }
      >
        <div className="space-y-2 py-2">
          <p className="text-sm text-foreground">
            ¿Está seguro de que desea eliminar del censo a{' '}
            <strong className="font-semibold">
              {confirmDel?.nombres} {confirmDel?.apellidos}
            </strong>
            ?
          </p>
          <p className="text-xs text-muted-foreground">
            Esta acción revocará su vinculación a la copropiedad y sus permisos de acceso. Por
            política de seguridad RLS, deberá ingresar su contraseña de administrador en el siguiente
            paso.
          </p>
        </div>
      </Modal>

      {/* 6. Diálogo de Verificación de Contraseña */}
      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar a ${confirmDel?.nombres} ${confirmDel?.apellidos}`}
      />
    </PageContainer>
  );
}
