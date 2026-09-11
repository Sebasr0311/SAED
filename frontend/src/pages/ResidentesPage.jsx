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
  Upload,
  Download,
  FileSpreadsheet,
  AlertCircle,
  CheckCircle2,
  FileUp,
  AlertTriangle,
  UserMinus,
  Info,
} from 'lucide-react';
import {
  valNombre,
  valApellido,
  valDocumento,
  valFechaNacimiento,
  valTelefono,
  valEmail,
  valSelect,
  getDocPlaceholder,
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
  tipoRelacion: 'ARRENDATARIO',
  crearContrato: false,
  idPlantilla: '',
  contratoCanon: '',
  contratoFechaInicio: '',
  contratoFechaFin: '',
  contratoTipo: 'INICIAL',
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

function getRelacionBadge(tipoRelacion) {
  switch (tipoRelacion) {
    case 'PROPIETARIO_RESIDENTE':
      return (
        <Badge
          variant="outline"
          className="bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800 text-[10px] font-semibold"
        >
          Propietario Residente
        </Badge>
      );
    case 'PROPIETARIO_NO_RESIDENTE':
      return (
        <Badge
          variant="outline"
          className="bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-800 text-[10px] font-semibold"
        >
          Propietario No Residente
        </Badge>
      );
    case 'ARRENDATARIO':
      return (
        <Badge
          variant="outline"
          className="bg-indigo-50 text-indigo-700 border-indigo-200 dark:bg-indigo-950/40 dark:text-indigo-300 dark:border-indigo-800 text-[10px] font-semibold"
        >
          Arrendatario
        </Badge>
      );
    case 'CONVIVIENTE':
    case 'FAMILIAR':
      return (
        <Badge
          variant="outline"
          className="bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700 text-[10px] font-medium"
        >
          Conviviente
        </Badge>
      );
    default:
      return (
        <Badge
          variant="outline"
          className="bg-muted text-muted-foreground text-[10px]"
        >
          Residente
        </Badge>
      );
  }
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
  const [filterRelacion, setFilterRelacion] = useState('TODOS');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [tutorForm, setTutorForm] = useState(emptyTutorForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);

  // Estados para Carga Masiva (Requisito #16)
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [parsedRows, setParsedRows] = useState([]);
  const [importResult, setImportResult] = useState(null);
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef(null);

  // 1. Censo de Personas/Residentes
  const {
    data,
    loading,
    error: errorPersonas,
    refetch,
  } = useFetch(() => tenantApi.get('/personas?page=0&size=200'), [tenant.activeAssignmentId]);

  // 2. Unidades Habitacionales de la propiedad activa
  const { data: apartamentos } = useFetch(
    () => tenantApi.get('/units'),
    [tenant.activeAssignmentId]
  );

  // 2.1. Plantillas de Contratos activas de la organización
  const { data: plantillasRaw } = useFetch(
    () => tenantApi.get('/contratos/plantillas/activas'),
    [tenant.activeAssignmentId]
  );
  const plantillas = useMemo(() => plantillasRaw?.data || (Array.isArray(plantillasRaw) ? plantillasRaw : []), [plantillasRaw]);

  // 3. Catálogo de Tipos de Documento
  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  // Validación menor de edad / tutor
  const edad = calcularEdad(form.fechaNacimiento);
  const requiereTutor = edad !== null && edad >= 16 && edad < 18;

  const activeCodigoDoc = useMemo(() => {
    return tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || 'CC';
  }, [tiposDoc, form.idTipoDoc]);

  const maxBirthDate = useMemo(() => new Date().toISOString().split('T')[0], []);
  const minBirthDate = useMemo(() => {
    const d = new Date();
    d.setFullYear(d.getFullYear() - 115);
    return d.toISOString().split('T')[0];
  }, []);

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

  // Lista normalizada y filtrada (Requisitos #11 y #12)
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
        tipoRelacion: r.tipoRelacion || 'RESIDENTE',
      }))
      .filter((r) => {
        if (filterRelacion !== 'TODOS') {
          if (filterRelacion === 'PROPIETARIO_RESIDENTE' && r.tipoRelacion !== 'PROPIETARIO_RESIDENTE') return false;
          if (filterRelacion === 'PROPIETARIO_NO_RESIDENTE' && r.tipoRelacion !== 'PROPIETARIO_NO_RESIDENTE') return false;
          if (filterRelacion === 'ARRENDATARIOS' && r.tipoRelacion !== 'ARRENDATARIO') return false;
          if (filterRelacion === 'CONVIVIENTES' && r.tipoRelacion !== 'CONVIVIENTE' && r.tipoRelacion !== 'FAMILIAR') return false;
        }
        if (!search) return true;
        const term = search.toLowerCase();
        return [r.nombres, r.apellidos, r.numeroDocumento]
          .filter(Boolean)
          .some((v) => String(v).toLowerCase().includes(term));
      });
  }, [data, search, filterRelacion]);

  // KPIs calculados (Requisitos #11 y #12)
  const kpis = useMemo(() => {
    const raw = Array.isArray(data) ? data : data?.items || [];
    const total = raw.length;
    const propResidentes = raw.filter((r) => r.tipoRelacion === 'PROPIETARIO_RESIDENTE').length;
    const propNoResidentes = raw.filter((r) => r.tipoRelacion === 'PROPIETARIO_NO_RESIDENTE').length;
    const arrendatarios = raw.filter((r) => r.tipoRelacion === 'ARRENDATARIO').length;
    const convivientes = raw.filter((r) => r.tipoRelacion === 'CONVIVIENTE' || r.tipoRelacion === 'FAMILIAR').length;

    return { total, propResidentes, propNoResidentes, arrendatarios, convivientes };
  }, [data]);

  // Paginación
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // Apertura de Modales
  const openCreate = useCallback(() => {
    setEditing(null);
    const ccId = tiposDoc.find((t) => t.codigo === 'CC')?.idTipoDoc || tiposDoc[0]?.idTipoDoc || 1;
    setForm({ ...emptyForm, idTipoDoc: ccId });
    setTutorForm(emptyTutorForm);
    setErrors({});
    resetTouched();
    setModalOpen(true);
  }, [tiposDoc, resetTouched]);

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
        tipoRelacion: row.tipoRelacion || 'ARRENDATARIO',
        crearContrato: false,
        idPlantilla: '',
        contratoCanon: '',
        contratoFechaInicio: '',
        contratoFechaFin: '',
        contratoTipo: 'INICIAL',
      });
      setTutorForm(emptyTutorForm);
      setErrors({});
      resetTouched();
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
    [tenantApi, resetTouched]
  );

  const update = useCallback((k, v) => {
    setForm((f) => ({ ...f, [k]: v }));
  }, []);

  const updateTutor = useCallback((k, v) => {
    setTutorForm((f) => ({ ...f, [k]: v }));
  }, []);

  // Validación
  const validate = useCallback(() => {
    touchAll(['numeroDocumento', 'nombres', 'apellidos', 'fechaNacimiento', 'telefono', 'email']);
    if (requiereTutor) {
      touchAll([
        'tutor.numeroDocumento',
        'tutor.nombres',
        'tutor.apellidos',
        'tutor.telefono',
        'tutor.email',
        'tutor.parentesco',
      ]);
    }
    const e = {};
    const codigoDoc =
      tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || 'CC';
    const rNombre = valNombre(form.nombres, 'Los nombres');
    if (!rNombre.ok) e.nombres = rNombre.mensaje;
    const rApellido = valApellido(form.apellidos, 'Los apellidos');
    if (!rApellido.ok) e.apellidos = rApellido.mensaje;
    const rDoc = valDocumento(form.numeroDocumento, codigoDoc, 'El número de documento');
    if (!rDoc.ok) e.numeroDocumento = rDoc.mensaje;
    const rFecha = valFechaNacimiento(form.fechaNacimiento, { edadMin: 0, edadMax: 115 });
    if (!rFecha.ok) e.fechaNacimiento = rFecha.mensaje;
    const rTel = valTelefono(form.telefono, { required: false });
    if (!rTel.ok) e.telefono = rTel.mensaje;
    const rEmail = valEmail(form.email, { required: false });
    if (!rEmail.ok) e.email = rEmail.mensaje;

    if (requiereTutor) {
      const tCodigo =
        tiposDoc.find((t) => Number(t.idTipoDoc) === Number(tutorForm.idTipoDoc))?.codigo || 'CC';
      const rTN = valNombre(tutorForm.nombres, 'Los nombres del tutor');
      if (!rTN.ok) e['tutor.nombres'] = rTN.mensaje;
      const rTA = valApellido(tutorForm.apellidos, 'Los apellidos del tutor');
      if (!rTA.ok) e['tutor.apellidos'] = rTA.mensaje;
      const rTDoc = valDocumento(tutorForm.numeroDocumento, tCodigo, 'El documento del tutor');
      if (!rTDoc.ok) e['tutor.numeroDocumento'] = rTDoc.mensaje;
      const rTTel = valTelefono(tutorForm.telefono);
      if (!rTTel.ok) e['tutor.telefono'] = rTTel.mensaje;
      const rTEmail = valEmail(tutorForm.email, { required: false });
      if (!rTEmail.ok) e['tutor.email'] = rTEmail.mensaje;
      const rParent = valSelect(tutorForm.parentesco, 'Selecciona el parentesco');
      if (!rParent.ok) e['tutor.parentesco'] = rParent.mensaje;
      if (tutorForm.parentesco === 'OTRO' && !tutorForm.otroParentesco.trim()) {
        e['tutor.otroParentesco'] = 'Especifica el parentesco del tutor';
      }
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }, [form, requiereTutor, tiposDoc, tutorForm, touchAll]);

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
        idResidente = res?.id || (typeof res === 'number' ? res : res?.data?.id || res?.data);
        toast.success('Residente registrado con éxito');
      }

      const aptSeleccionado = form.idApartamento !== '';
      const asignacionCambia =
        aptSeleccionado &&
        (!editing ||
          Number(editing.idApartamento) !== Number(form.idApartamento) ||
          editing.tipoRelacion !== form.tipoRelacion);
      if (asignacionCambia) {
        try {
          await tenantApi.post(`/residentes/${idResidente}/asignar-apartamento`, {
            idApartamento: Number(form.idApartamento),
            tipoRelacion: form.tipoRelacion,
            rolEnContrato: form.tipoRelacion?.startsWith('PROPIETARIO') ? 'PROPIETARIO' : 'RESIDENTE',
          });
        } catch (err) {
          toast.error(
            `Residente guardado, pero la asignación al apartamento falló: ${err.message}`
          );
        }
      }

      if (form.crearContrato && form.idApartamento && !editing) {
        try {
          await tenantApi.post('/contratos', {
            idApartamento: Number(form.idApartamento),
            idResidente,
            fechaInicio: form.contratoFechaInicio || new Date().toISOString().split('T')[0],
            fechaFin: form.contratoFechaFin || null,
            tipoContrato: form.contratoTipo || 'INICIAL',
            canonMensual: Number(form.contratoCanon || 0),
            idPlantilla: form.idPlantilla ? Number(form.idPlantilla) : null,
          });
          toast.success('Contrato de arrendamiento vinculado y generado exitosamente.');
        } catch (errContrato) {
          toast.error(`Residente creado, pero falló la generación del contrato: ${errContrato.message}`);
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

  // Desvinculación Protegida (Requisitos #14 y #15)
  const handleDelete = useCallback(async () => {
    if (!confirmDel) return;
    try {
      await tenantApi.del(`/personas/${confirmDel.id}`);
      toast.success('Habitante desvinculado con éxito. Su historial y registros de auditoría fueron preservados.');
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo desvincular al habitante');
    } finally {
      setConfirmDel(null);
    }
  }, [confirmDel, refetch, tenantApi]);

  // Descarga de Plantilla Oficial Excel para Censo (Requisito #16)
  const handleDownloadTemplate = useCallback(async () => {
    try {
      const XLSX = await import('xlsx-js-style');
      const headers = [
        'TIPO_DOCUMENTO',
        'NUMERO_DOCUMENTO',
        'NOMBRES',
        'APELLIDOS',
        'EMAIL',
        'TELEFONO',
        'NUMERO_UNIDAD',
        'TIPO_RELACION',
      ];

      const rows = [
        ['CC', '1020304050', 'Carlos Andrés', 'Pérez Gómez', 'carlos.perez@ejemplo.com', '3001234567', '101', 'PROPIETARIO_RESIDENTE'],
        ['CC', '1030405060', 'María Elena', 'Rodríguez López', 'maria.rodriguez@ejemplo.com', '3109876543', '102', 'ARRENDATARIO'],
        ['CE', '90807060', 'John David', 'Smith', 'john.smith@ejemplo.com', '3205551234', '201', 'PROPIETARIO_NO_RESIDENTE'],
        ['TI', '1122334455', 'Sofía', 'Pérez Morales', '', '3001234567', '101', 'CONVIVIENTE'],
      ];

      const headerStyle = {
        font: { bold: true, color: { rgb: 'FFFFFF' }, sz: 11 },
        fill: { fgColor: { rgb: '1E293B' } },
        alignment: { horizontal: 'center', vertical: 'center' },
      };

      const dataStyle = {
        font: { sz: 10 },
        alignment: { vertical: 'center' },
      };

      const wsData = [
        headers.map((h) => ({ t: 's', v: h, s: headerStyle })),
        ...rows.map((row) => row.map((val) => ({ t: 's', v: val, s: dataStyle }))),
      ];

      const ws = XLSX.utils.aoa_to_sheet(wsData);
      ws['!cols'] = [
        { wch: 18 },
        { wch: 20 },
        { wch: 22 },
        { wch: 22 },
        { wch: 30 },
        { wch: 16 },
        { wch: 18 },
        { wch: 28 },
      ];

      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Censo_Residentes');
      XLSX.writeFile(wb, 'Plantilla_Censo_Residentes_SAED.xlsx');
      toast.success('Plantilla descargada correctamente');
    } catch (err) {
      toast.error('No se pudo generar la plantilla: ' + (err.message || 'error desconocido'));
    }
  }, []);

  // Procesamiento y pre-validación de archivo Excel / CSV (Requisito #16)
  const processSpreadsheet = useCallback(async (file) => {
    if (!file) return;
    try {
      const XLSX = await import('xlsx-js-style');
      const buffer = await file.arrayBuffer();
      const wb = XLSX.read(buffer, { type: 'array' });
      const wsName = wb.SheetNames[0];
      if (!wsName) {
        toast.error('El archivo no contiene hojas de cálculo');
        return;
      }
      const ws = wb.Sheets[wsName];
      const rawRows = XLSX.utils.sheet_to_json(ws, { defval: '' });

      if (!rawRows || rawRows.length === 0) {
        toast.error('El archivo está vacío o no tiene filas legibles');
        return;
      }

      const normalized = rawRows.map((r, index) => {
        const getVal = (...possibleKeys) => {
          for (const key of Object.keys(r)) {
            const cleanKey = key.trim().toUpperCase().replace(/[\s_]+/g, '');
            for (const pk of possibleKeys) {
              const cleanPk = pk.trim().toUpperCase().replace(/[\s_]+/g, '');
              if (cleanKey === cleanPk) return String(r[key]).trim();
            }
          }
          return '';
        };

        const tipoDoc = (getVal('TIPO_DOCUMENTO', 'TIPODOCUMENTO', 'TIPODOC', 'TIPO') || 'CC').toUpperCase();
        const numeroDocumento = getVal('NUMERO_DOCUMENTO', 'NUMERODOCUMENTO', 'DOCUMENTO', 'CEDULA', 'IDENTIFICACION', 'NUMERO');
        const nombres = getVal('NOMBRES', 'NOMBRE', 'PRIMERNOMBRE');
        const apellidos = getVal('APELLIDOS', 'APELLIDO', 'PRIMERAPELLIDO');
        const email = getVal('EMAIL', 'CORREO', 'CORREOELECTRONICO');
        const telefono = getVal('TELEFONO', 'TEL', 'CELULAR', 'MOVIL');
        const numeroUnidad = getVal('NUMERO_UNIDAD', 'NUMEROUNIDAD', 'UNIDAD', 'APARTAMENTO', 'APTO', 'INMUEBLE');
        let tipoRelacion = (getVal('TIPO_RELACION', 'TIPORELACION', 'RELACION', 'CONDICION', 'ROL') || '').toUpperCase();

        if (tipoRelacion.includes('NO_RESIDENTE') || tipoRelacion.includes('NO RESIDENTE')) {
          tipoRelacion = 'PROPIETARIO_NO_RESIDENTE';
        } else if (tipoRelacion.includes('PROPIETARIO')) {
          tipoRelacion = 'PROPIETARIO_RESIDENTE';
        } else if (tipoRelacion.includes('ARRENDATARIO') || tipoRelacion.includes('INQUILINO')) {
          tipoRelacion = 'ARRENDATARIO';
        } else if (tipoRelacion.includes('CONVIVIENTE') || tipoRelacion.includes('FAMILIAR') || tipoRelacion.includes('HIJ')) {
          tipoRelacion = 'CONVIVIENTE';
        } else {
          tipoRelacion = 'ARRENDATARIO';
        }

        const errorsList = [];
        if (!numeroDocumento) errorsList.push('Falta número de documento');
        if (!nombres) errorsList.push('Falta nombre');
        if (!numeroUnidad) errorsList.push('Sin unidad habitacional (se creará habitante sin asignar)');

        const status = errorsList.some((e) => e.startsWith('Falta')) ? 'INVALID' : errorsList.length > 0 ? 'WARNING' : 'VALID';

        return {
          rowNumber: index + 2,
          tipoDocumento: tipoDoc,
          numeroDocumento,
          nombres,
          apellidos,
          email,
          telefono,
          numeroUnidad,
          tipoRelacion,
          status,
          validationMsg: errorsList.join(' · '),
        };
      });

      setImportFile(file);
      setParsedRows(normalized);
      setImportResult(null);
    } catch (err) {
      toast.error('Error al leer el archivo Excel: ' + (err.message || 'formato inválido'));
    }
  }, []);

  // Enviar lote validado al backend (Requisito #16)
  const handleExecuteImport = useCallback(async () => {
    const validItems = parsedRows.filter((r) => r.status !== 'INVALID');
    if (validItems.length === 0) {
      toast.error('No hay filas válidas para procesar');
      return;
    }

    setImporting(true);
    try {
      const payload = validItems.map((r) => ({
        tipoDocumento: r.tipoDocumento,
        numeroDocumento: r.numeroDocumento,
        nombres: r.nombres,
        apellidos: r.apellidos,
        email: r.email,
        telefono: r.telefono,
        numeroUnidad: r.numeroUnidad,
        tipoRelacion: r.tipoRelacion,
      }));

      const res = await tenantApi.post('/personas/importar', payload);
      setImportResult(res);
      toast.success(`Censo procesado: ${res?.totalExitosos || 0} exitosos de ${res?.totalProcesados || payload.length}`);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error durante la importación masiva');
    } finally {
      setImporting(false);
    }
  }, [parsedRows, refetch, tenantApi]);

  const resetImportModal = useCallback(() => {
    setImportModalOpen(false);
    setImportFile(null);
    setParsedRows([]);
    setImportResult(null);
    setDragActive(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, []);

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
            variant="outline"
            size="sm"
            onClick={() => {
              setImportResult(null);
              setImportFile(null);
              setParsedRows([]);
              setImportModalOpen(true);
            }}
            className="text-xs min-h-[44px] sm:min-h-9 border-primary/30 text-primary hover:bg-primary/5"
          >
            <Upload className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            Carga Masiva (Excel)
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

      {/* 2. Grid de KPIs Operativos (Requisitos #11 y #12) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Total en Censo"
          value={kpis.total}
          subtitle="Censo general de personas"
          icon={Users}
          variant="primary"
        />
        <MetricCard
          label="Propietarios Residentes"
          value={kpis.propResidentes}
          subtitle="Habitan y son titulares de dominio"
          icon={ShieldCheck}
          variant="success"
        />
        <MetricCard
          label="Propietarios No Residentes"
          value={kpis.propNoResidentes}
          subtitle="Inversionistas (patrimonial / asambleas)"
          icon={ShieldAlert}
          variant="info"
        />
        <MetricCard
          label="Arrendatarios y Convivientes"
          value={kpis.arrendatarios + kpis.convivientes}
          subtitle={`${kpis.arrendatarios} arrendatarios · ${kpis.convivientes} convivientes`}
          icon={Building}
          variant="secondary"
        />
      </div>

      {/* 3. Card Principal: Búsqueda, Filtro y Listado */}
      <Card className="border-border/80 shadow-xs overflow-hidden">
        {/* Barra de Búsqueda, Filtros y Herramientas */}
        <CardHeader className="p-4 sm:p-5 border-b border-border/50 bg-card space-y-3">
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

          {/* Filtro por Condición de Dominio / Residencia (Requisito #11) */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 pt-2 sm:pb-0 text-xs scrollbar-none border-t border-border/40">
            {[
              { id: 'TODOS', label: 'Todos', count: kpis.total },
              { id: 'PROPIETARIO_RESIDENTE', label: 'Propietarios Residentes', count: kpis.propResidentes },
              { id: 'PROPIETARIO_NO_RESIDENTE', label: 'Propietarios No Residentes', count: kpis.propNoResidentes },
              { id: 'ARRENDATARIOS', label: 'Arrendatarios', count: kpis.arrendatarios },
              { id: 'CONVIVIENTES', label: 'Convivientes', count: kpis.convivientes },
            ].map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => {
                  setFilterRelacion(f.id);
                  setPage(0);
                }}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors shrink-0 flex items-center gap-1.5 ${
                  filterRelacion === f.id
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                <span>{f.label}</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                    filterRelacion === f.id
                      ? 'bg-primary-foreground/20 text-primary-foreground'
                      : 'bg-background text-muted-foreground'
                  }`}
                >
                  {f.count}
                </span>
              </button>
            ))}
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
                      <th className="py-3 px-4 w-12 text-center">#</th>
                      <th className="py-3 px-4">Residente</th>
                      <th className="py-3 px-4">Identificación</th>
                      <th className="py-3 px-4">Unidad / Apto</th>
                      <th className="py-3 px-4">Contacto</th>
                      <th className="py-3 px-4 text-right w-24">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/50 text-xs">
                    {rows.map((r, idx) => {
                      const initial = (r.nombres?.[0] || 'R').toUpperCase();
                      const tipoDocLabel = tipoDocMap.get(Number(r.idTipoDoc)) || 'DOC';
                      const unidadDesc =
                        r.numeroApartamento
                          ? (/^apto/i.test(r.numeroApartamento.trim()) ? r.numeroApartamento.trim() : `Apto ${r.numeroApartamento.trim()}`)
                          : unitMap.get(Number(r.idApartamento));
                      const rowNum = safePage * PAGE_SIZE + idx + 1;

                      return (
                        <tr
                          key={r.id}
                          className="hover:bg-muted/40 transition-colors group"
                        >
                          <td className="py-3.5 px-4 font-mono text-[11px] text-muted-foreground text-center">
                            {rowNum}
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
                                <div className="flex items-center gap-2">
                                  <p className="font-semibold text-foreground group-hover:text-primary transition-colors truncate">
                                    {r.nombres} {r.apellidos}
                                  </p>
                                  <span className="text-[10px] font-mono text-muted-foreground/80 bg-muted px-1.5 py-0.5 rounded border border-border/40">
                                    ID #{r.id}
                                  </span>
                                </div>
                                <div className="text-[11px] text-muted-foreground flex items-center gap-1.5 mt-1 flex-wrap">
                                  {getRelacionBadge(r.tipoRelacion)}
                                  {r.tipoPersona && (
                                    <span className="capitalize">{r.tipoPersona.toLowerCase()}</span>
                                  )}
                                  {r.esMenorEdad && (
                                    <Badge
                                      variant="warning"
                                      className="text-[9px] px-1 py-0 uppercase font-bold"
                                    >
                                      Menor
                                    </Badge>
                                  )}
                                </div>
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
                      ? (/^apto/i.test(r.numeroApartamento.trim()) ? r.numeroApartamento.trim() : `Apto ${r.numeroApartamento.trim()}`)
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
                            <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                              {getRelacionBadge(r.tipoRelacion)}
                              <span className="text-[11px] text-muted-foreground font-mono">
                                {tipoDocLabel} {r.numeroDocumento || '—'}
                              </span>
                            </div>
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
              label="Tipo de Documento *"
              value={form.idTipoDoc}
              onChange={(e) => update('idTipoDoc', Number(e.target.value))}
            >
              {tiposDoc.map((t) => (
                <option
                  key={t.idTipoDoc ?? t.id ?? t.value}
                  value={t.idTipoDoc ?? t.id ?? t.value}
                >
                  {t.nombre} ({t.codigo})
                </option>
              ))}
            </Select>
            <Input
              id="numeroDocumento"
              label="Número de Documento *"
              value={form.numeroDocumento}
              onChange={(e) => update('numeroDocumento', e.target.value)}
              onBlur={() => touch('numeroDocumento')}
              error={
                fieldError(
                  'numeroDocumento',
                  valDocumento(form.numeroDocumento, activeCodigoDoc, 'El número de documento')
                ) || errors.numeroDocumento
              }
              placeholder={getDocPlaceholder(activeCodigoDoc)}
            />
          </div>

          {errorTiposDoc && !tiposDoc.length && (
            <p className="text-xs text-danger-600 font-medium">
              Error al consultar el catálogo de tipos de documento.
            </p>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="nombres"
              label="Nombres *"
              value={form.nombres}
              onChange={(e) => update('nombres', e.target.value)}
              onBlur={() => touch('nombres')}
              error={fieldError('nombres', valNombre(form.nombres, 'Los nombres')) || errors.nombres}
              placeholder="Ej. Carlos Alberto"
            />
            <Input
              id="apellidos"
              label="Apellidos *"
              value={form.apellidos}
              onChange={(e) => update('apellidos', e.target.value)}
              onBlur={() => touch('apellidos')}
              error={fieldError('apellidos', valApellido(form.apellidos, 'Los apellidos')) || errors.apellidos}
              placeholder="Ej. Martínez Gómez"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="fechaNacimiento"
              label="Fecha de Nacimiento *"
              type="date"
              max={maxBirthDate}
              min={minBirthDate}
              value={form.fechaNacimiento}
              onChange={(e) => update('fechaNacimiento', e.target.value)}
              onBlur={() => touch('fechaNacimiento')}
              error={
                fieldError('fechaNacimiento', valFechaNacimiento(form.fechaNacimiento)) ||
                errors.fechaNacimiento
              }
            />
            <Input
              id="telefono"
              label="Teléfono Celular"
              type="tel"
              inputMode="numeric"
              maxLength={10}
              value={form.telefono}
              onChange={(e) => update('telefono', e.target.value)}
              onBlur={() => touch('telefono')}
              error={
                fieldError('telefono', valTelefono(form.telefono, { required: false })) ||
                errors.telefono
              }
              placeholder="Ej. 300 123 4567"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="email"
              label="Correo Electrónico"
              type="email"
              inputMode="email"
              autoComplete="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              onBlur={() => touch('email')}
              error={
                fieldError('email', valEmail(form.email, { required: false })) || errors.email
              }
              placeholder="Ej. residente@correo.com"
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

          {form.idApartamento && (
            <div className="space-y-3 pt-1">
              <Select
                id="tipoRelacion"
                label="Condición y Alcance en la Unidad *"
                value={form.tipoRelacion || 'ARRENDATARIO'}
                onChange={(e) => update('tipoRelacion', e.target.value)}
              >
                <option value="PROPIETARIO_RESIDENTE">Propietario Residente (Habita la unidad y es titular de dominio)</option>
                <option value="PROPIETARIO_NO_RESIDENTE">Propietario No Residente (Inversionista / Arrendador sin residencia física)</option>
                <option value="ARRENDATARIO">Arrendatario / Inquilino (Habitante físico principal con contrato)</option>
                <option value="CONVIVIENTE">Conviviente / Familiar (Habitante secundario sin titularidad ni contrato)</option>
              </Select>

              {form.tipoRelacion === 'PROPIETARIO_NO_RESIDENTE' && (
                <div className="text-[11px] bg-blue-500/10 border border-blue-500/20 text-blue-700 dark:text-blue-300 p-3 rounded-lg flex items-start gap-2.5">
                  <ShieldAlert className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400 mt-0.5" aria-hidden="true" />
                  <div>
                    <strong className="font-semibold block mb-0.5">Aislamiento Zero-Trust Patrimonial:</strong>
                    El propietario no residente tiene facultades patrimoniales (asambleas, reglamentos, actas y pólizas), pero <strong>no</strong> tiene acceso a la vida privada del inquilino (visitas, encomiendas, reservas ni llaves de acceso).
                  </div>
                </div>
              )}

              {form.tipoRelacion === 'PROPIETARIO_RESIDENTE' && (
                <div className="text-[11px] bg-emerald-500/10 border border-emerald-500/20 text-emerald-700 dark:text-emerald-300 p-2.5 rounded-lg flex items-start gap-2">
                  <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400 mt-0.5" aria-hidden="true" />
                  <span>
                    <strong>Titular Residente:</strong> Goza de plenas facultades como copropietario patrimonial (asambleas y cartera) y habitante residente en el portal.
                  </span>
                </div>
              )}

              {form.tipoRelacion === 'ARRENDATARIO' && !editing && (
                <div className="rounded-xl border border-primary/20 bg-primary/5 p-3.5 space-y-3 mt-2">
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={form.crearContrato}
                      onChange={(e) => update('crearContrato', e.target.checked)}
                      className="rounded border-border text-primary focus:ring-primary/30 h-4 w-4"
                    />
                    <span className="text-xs font-semibold text-foreground">
                      Vincular Contrato de Arrendamiento con Plantilla Organizacional (Requisito #10)
                    </span>
                  </label>

                  {form.crearContrato && (
                    <div className="space-y-3 pt-2 border-t border-primary/15">
                      <div>
                        <label className="text-xs font-medium text-foreground block mb-1">
                          Plantilla de Contrato
                        </label>
                        <select
                          value={form.idPlantilla}
                          onChange={(e) => {
                            const val = e.target.value;
                            update('idPlantilla', val);
                            if (val) {
                              const p = plantillas.find((tpl) => String(tpl.idPlantilla) === String(val));
                              if (p?.tipoContrato) update('contratoTipo', p.tipoContrato);
                            }
                          }}
                          className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
                        >
                          <option value="">— Plantilla Estándar del Sistema —</option>
                          {plantillas.map((p) => (
                            <option key={p.idPlantilla} value={p.idPlantilla}>
                              {p.nombre} (v{p.version} · {p.tipoContrato})
                            </option>
                          ))}
                        </select>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                        <div>
                          <label className="text-xs font-medium text-foreground block mb-1">
                            Canon Mensual (COP) *
                          </label>
                          <input
                            type="number"
                            placeholder="Ej. 1500000"
                            value={form.contratoCanon}
                            onChange={(e) => update('contratoCanon', e.target.value)}
                            className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-foreground block mb-1">
                            Fecha Inicio *
                          </label>
                          <input
                            type="date"
                            value={form.contratoFechaInicio}
                            onChange={(e) => update('contratoFechaInicio', e.target.value)}
                            className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-foreground block mb-1">
                            Fecha Fin
                          </label>
                          <input
                            type="date"
                            value={form.contratoFechaFin}
                            onChange={(e) => update('contratoFechaFin', e.target.value)}
                            className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/30"
                          />
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

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
                  label="Tipo Documento Tutor *"
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
                      {t.nombre} ({t.codigo})
                    </option>
                  ))}
                </Select>
                <Input
                  id="tutor-numeroDocumento"
                  label="Documento Tutor *"
                  value={tutorForm.numeroDocumento}
                  onChange={(e) => updateTutor('numeroDocumento', e.target.value)}
                  onBlur={() => touch('tutor.numeroDocumento')}
                  error={
                    fieldError(
                      'tutor.numeroDocumento',
                      valDocumento(
                        tutorForm.numeroDocumento,
                        tiposDoc.find((t) => Number(t.idTipoDoc) === Number(tutorForm.idTipoDoc))?.codigo || 'CC',
                        'El documento del tutor'
                      )
                    ) || errors['tutor.numeroDocumento']
                  }
                  placeholder={getDocPlaceholder(tiposDoc.find((t) => Number(t.idTipoDoc) === Number(tutorForm.idTipoDoc))?.codigo || 'CC')}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input
                  id="tutor-nombres"
                  label="Nombres Tutor *"
                  value={tutorForm.nombres}
                  onChange={(e) => updateTutor('nombres', e.target.value)}
                  onBlur={() => touch('tutor.nombres')}
                  error={
                    fieldError('tutor.nombres', valNombre(tutorForm.nombres, 'Los nombres del tutor')) ||
                    errors['tutor.nombres']
                  }
                  placeholder="Ej. María Elena"
                />
                <Input
                  id="tutor-apellidos"
                  label="Apellidos Tutor *"
                  value={tutorForm.apellidos}
                  onChange={(e) => updateTutor('apellidos', e.target.value)}
                  onBlur={() => touch('tutor.apellidos')}
                  error={
                    fieldError('tutor.apellidos', valApellido(tutorForm.apellidos, 'Los apellidos del tutor')) ||
                    errors['tutor.apellidos']
                  }
                  placeholder="Ej. Gómez Silva"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <Input
                  id="tutor-telefono"
                  label="Teléfono Celular Tutor *"
                  type="tel"
                  inputMode="numeric"
                  maxLength={10}
                  value={tutorForm.telefono}
                  onChange={(e) => updateTutor('telefono', e.target.value)}
                  onBlur={() => touch('tutor.telefono')}
                  error={
                    fieldError('tutor.telefono', valTelefono(tutorForm.telefono)) ||
                    errors['tutor.telefono']
                  }
                  placeholder="Ej. 300 123 4567"
                />
                <Input
                  id="tutor-email"
                  label="Correo Electrónico Tutor"
                  type="email"
                  inputMode="email"
                  autoComplete="email"
                  value={tutorForm.email}
                  onChange={(e) => updateTutor('email', e.target.value)}
                  onBlur={() => touch('tutor.email')}
                  error={
                    fieldError('tutor.email', valEmail(tutorForm.email, { required: false })) ||
                    errors['tutor.email']
                  }
                  placeholder="Ej. tutor@correo.com"
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

      {/* 5. Modal Confirmación de Desvinculación Protegida (Requisitos #14 y #15) */}
      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Desvinculación protegida del habitante"
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
              <UserMinus className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
              Continuar a Desvinculación
            </Button>
          </>
        }
      >
        <div className="space-y-3 py-2 text-xs sm:text-sm">
          <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-900 dark:text-amber-200 flex items-start gap-2.5">
            <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" aria-hidden="true" />
            <div className="space-y-1">
              <p className="font-semibold">Baja administrativa del censo activo</p>
              <p className="text-xs opacity-90">
                ¿Está seguro de que desea desvincular a{' '}
                <strong className="font-semibold">
                  {confirmDel?.nombres} {confirmDel?.apellidos}
                </strong>
                ? Esta acción revocará sus credenciales de usuario y liberará su asignación en la unidad habitacional.
              </p>
            </div>
          </div>

          <div className="p-3 rounded-lg bg-muted/50 border border-border/60 text-muted-foreground space-y-1.5 text-xs">
            <div className="flex items-center gap-2 font-medium text-foreground">
              <ShieldCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              <span>Garantía de Auditoría Histórica (Zero Data Loss)</span>
            </div>
            <p>
              Conforme a los Requisitos #14 y #15, <strong>no se borrará físicamente ningún registro</strong>. Todo el historial de visitas, recepción de paquetes, pagos, estado de cuenta y solicitudes PQRS se mantendrá permanentemente íntegro.
            </p>
          </div>

          <p className="text-xs text-muted-foreground pt-1">
            Por política de seguridad Zero-Trust, deberá ingresar su contraseña de administrador en el siguiente paso para confirmar la operación.
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
        descripcion={`desvincular a ${confirmDel?.nombres} ${confirmDel?.apellidos}`}
      />

      {/* 7. Modal Carga Masiva de Residentes (Requisito #16) */}
      <Modal
        open={importModalOpen}
        onClose={resetImportModal}
        title="Carga Masiva de Residentes (Censo Excel)"
        className="max-w-4xl"
        footer={
          importResult ? (
            <Button
              variant="primary"
              onClick={resetImportModal}
              className="text-xs min-h-[44px] sm:min-h-9"
            >
              Finalizar y Ver Residentes
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                onClick={resetImportModal}
                disabled={importing}
                className="text-xs min-h-[44px] sm:min-h-9"
              >
                Cancelar
              </Button>
              {importFile && (
                <Button
                  variant="primary"
                  onClick={handleExecuteImport}
                  disabled={importing || parsedRows.filter((r) => r.status !== 'INVALID').length === 0}
                  className="text-xs min-h-[44px] sm:min-h-9 shadow-xs"
                >
                  {importing ? (
                    <>
                      <RefreshCw className="h-3.5 w-3.5 mr-1.5 animate-spin" aria-hidden="true" />
                      Procesando lote...
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
                      Confirmar e Importar ({parsedRows.filter((r) => r.status !== 'INVALID').length} registros)
                    </>
                  )}
                </Button>
              )}
            </>
          )
        }
      >
        <div className="space-y-4 py-1 text-xs sm:text-sm">
          {/* Instrucciones y Descarga de Plantilla */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3.5 rounded-xl bg-muted/40 border border-border/60">
            <div className="space-y-1">
              <p className="font-semibold text-foreground flex items-center gap-1.5">
                <FileSpreadsheet className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                Plantilla Oficial de Ingesta Masiva
              </p>
              <p className="text-xs text-muted-foreground">
                Descargue el formato con las columnas estandarizadas y ejemplos de las 4 condiciones de habitantes.
              </p>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={handleDownloadTemplate}
              className="text-xs font-semibold shrink-0 border-emerald-500/30 text-emerald-700 hover:bg-emerald-50 dark:text-emerald-300 dark:hover:bg-emerald-950/40"
            >
              <Download className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
              Descargar Plantilla (.xlsx)
            </Button>
          </div>

          {/* Resultado de Importación (si ya terminó) */}
          {importResult ? (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="p-3 rounded-lg border border-border/70 bg-card text-center">
                  <p className="text-xs text-muted-foreground">Total Filas Procesadas</p>
                  <p className="text-2xl font-bold font-mono text-foreground mt-1">
                    {importResult.totalProcesados}
                  </p>
                </div>
                <div className="p-3 rounded-lg border border-emerald-200 bg-emerald-50/50 dark:bg-emerald-950/20 dark:border-emerald-800 text-center">
                  <p className="text-xs text-emerald-700 dark:text-emerald-300">Exitosos</p>
                  <p className="text-2xl font-bold font-mono text-emerald-700 dark:text-emerald-300 mt-1">
                    {importResult.totalExitosos}
                  </p>
                </div>
                <div className="p-3 rounded-lg border border-destructive/30 bg-destructive/5 text-center">
                  <p className="text-xs text-destructive">Fallidos / Errores</p>
                  <p className="text-2xl font-bold font-mono text-destructive mt-1">
                    {importResult.totalFallidos}
                  </p>
                </div>
              </div>

              {importResult.errores?.length > 0 && (
                <div className="border border-destructive/20 rounded-xl overflow-hidden">
                  <div className="bg-destructive/10 px-4 py-2 font-semibold text-xs text-destructive flex items-center gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0" />
                    Detalle de Filas con Error
                  </div>
                  <div className="max-h-48 overflow-y-auto divide-y divide-border/50 text-xs">
                    {importResult.errores.map((err, i) => (
                      <div key={i} className="p-2.5 flex items-start justify-between gap-3 hover:bg-muted/30">
                        <div className="font-mono text-muted-foreground shrink-0">
                          Fila #{err.fila || (i + 2)}
                        </div>
                        <div className="font-medium text-foreground truncate max-w-xs">
                          {err.identificador || 'Sin identificador'}
                        </div>
                        <div className="text-destructive text-right flex-1">
                          {err.error}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <>
              {/* Selector / Drag and drop */}
              <input
                type="file"
                ref={fileInputRef}
                accept=".xlsx,.xls,.csv"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) processSpreadsheet(file);
                }}
                className="hidden"
                id="file-upload-excel"
              />

              {!importFile ? (
                <div
                  onDragOver={(e) => {
                    e.preventDefault();
                    setDragActive(true);
                  }}
                  onDragLeave={() => setDragActive(false)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragActive(false);
                    const file = e.dataTransfer.files?.[0];
                    if (file) processSpreadsheet(file);
                  }}
                  onClick={() => fileInputRef.current?.click()}
                  className={`p-8 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center text-center cursor-pointer transition-colors ${
                    dragActive
                      ? 'border-primary bg-primary/5'
                      : 'border-border/80 hover:border-primary/50 hover:bg-muted/30'
                  }`}
                >
                  <div className="p-3 rounded-full bg-primary/10 text-primary mb-3">
                    <FileUp className="h-7 w-7" aria-hidden="true" />
                  </div>
                  <p className="font-semibold text-foreground text-sm">
                    Haga clic o arrastre el archivo aquí
                  </p>
                  <p className="text-xs text-muted-foreground max-w-xs mt-1">
                    Soporta hojas de cálculo en formato Excel (.xlsx, .xls) o texto (.csv)
                  </p>
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Resumen del Archivo y Métricas de Validación Previa */}
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 rounded-xl bg-card border border-border">
                    <div className="flex items-center gap-3">
                      <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600">
                        <FileSpreadsheet className="h-5 w-5" />
                      </div>
                      <div>
                        <p className="font-semibold text-foreground text-xs sm:text-sm truncate max-w-[280px]">
                          {importFile.name}
                        </p>
                        <p className="text-[11px] text-muted-foreground">
                          {(importFile.size / 1024).toFixed(1)} KB · {parsedRows.length} filas detectadas
                        </p>
                      </div>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        setImportFile(null);
                        setParsedRows([]);
                        if (fileInputRef.current) fileInputRef.current.value = '';
                      }}
                      className="text-xs"
                    >
                      Cambiar Archivo
                    </Button>
                  </div>

                  {/* Badges de Validación Previa */}
                  <div className="flex items-center gap-2 flex-wrap text-xs">
                    <span className="px-2.5 py-1 rounded-full bg-muted font-medium text-foreground">
                      Total: <strong>{parsedRows.length}</strong>
                    </span>
                    <span className="px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 font-medium">
                      Válidas para importar: <strong>{parsedRows.filter((r) => r.status === 'VALID').length}</strong>
                    </span>
                    {parsedRows.filter((r) => r.status === 'WARNING').length > 0 && (
                      <span className="px-2.5 py-1 rounded-full bg-amber-500/10 text-amber-700 dark:text-amber-300 font-medium">
                        Con advertencia: <strong>{parsedRows.filter((r) => r.status === 'WARNING').length}</strong>
                      </span>
                    )}
                    {parsedRows.filter((r) => r.status === 'INVALID').length > 0 && (
                      <span className="px-2.5 py-1 rounded-full bg-destructive/10 text-destructive font-medium">
                        Inválidas (se omitirán): <strong>{parsedRows.filter((r) => r.status === 'INVALID').length}</strong>
                      </span>
                    )}
                  </div>

                  {/* Previsualización de Filas Parseadas */}
                  <div className="border border-border/70 rounded-xl overflow-hidden">
                    <div className="max-h-60 overflow-y-auto">
                      <table className="w-full text-left border-collapse text-xs">
                        <thead>
                          <tr className="border-b border-border/70 bg-muted/40 text-[11px] font-semibold text-muted-foreground uppercase">
                            <th className="py-2 px-3 w-10 text-center">#</th>
                            <th className="py-2 px-3">Documento</th>
                            <th className="py-2 px-3">Habitante</th>
                            <th className="py-2 px-3">Unidad</th>
                            <th className="py-2 px-3">Condición</th>
                            <th className="py-2 px-3">Estado</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border/40">
                          {parsedRows.map((r, i) => (
                            <tr key={i} className="hover:bg-muted/20">
                              <td className="py-2 px-3 text-center font-mono text-muted-foreground">
                                {r.rowNumber}
                              </td>
                              <td className="py-2 px-3 font-mono">
                                {r.tipoDocumento} {r.numeroDocumento || '—'}
                              </td>
                              <td className="py-2 px-3 font-medium text-foreground">
                                {r.nombres} {r.apellidos}
                              </td>
                              <td className="py-2 px-3">
                                {r.numeroUnidad ? (
                                  <Badge variant="outline" className="text-[10px]">
                                    Apto {r.numeroUnidad}
                                  </Badge>
                                ) : (
                                  <span className="text-muted-foreground text-[10px]">Sin asignar</span>
                                )}
                              </td>
                              <td className="py-2 px-3">
                                {getRelacionBadge(r.tipoRelacion)}
                              </td>
                              <td className="py-2 px-3">
                                {r.status === 'VALID' ? (
                                  <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px]">
                                    Válida
                                  </Badge>
                                ) : r.status === 'WARNING' ? (
                                  <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200 text-[10px]" title={r.validationMsg}>
                                    Aviso
                                  </Badge>
                                ) : (
                                  <Badge variant="outline" className="bg-destructive/10 text-destructive border-destructive/20 text-[10px]" title={r.validationMsg}>
                                    {r.validationMsg || 'Inválida'}
                                  </Badge>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </Modal>
    </PageContainer>
  );
}
