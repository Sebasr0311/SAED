import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import {
  Bike,
  Calendar,
  Car,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Copy,
  Eye,
  Footprints,
  History,
  Mail,
  Phone,
  Plus,
  QrCode,
  RefreshCw,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  User,
  UserCheck,
  UserPlus,
  Users,
} from 'lucide-react';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import {
  valNombre,
  valApellido,
  valDocumento,
  valTelefono,
  valEmail,
  valPlaca,
  getDocPlaceholder,
} from '../lib/validation.js';
import { formatDate, formatDateTime, cn } from '../lib/utils.js';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { LocalQRCode } from '../components/ui/LocalQRCode.jsx';

const emptyVisitante = {
  idTipoDoc: '',
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  email: '',
};

const emptyForm = {
  visitante: { ...emptyVisitante },
  motivo: '',
  tiempoValidezMin: 30,
  cantidadPersonas: 1,
  medioTransporte: 'A_PIE',
  placa: '',
  descripcion: '',
  guardarFrecuente: false,
};

function calcularFechaExpiracion(minutos) {
  return new Date(Date.now() + Number(minutos || 30) * 60000);
}

/**
 * ResVisitasPage — Módulo Unificado de Visitas para el Residente
 * Integra la administración de Visitantes Frecuentes y el Registro de Nuevas Visitas
 * con generación de código QR dinámico y validaciones en tiempo real.
 */
export default function ResVisitasPage() {
  const { user } = useAuth();
  const residentId = user?.idResidente || user?.idPersona || user?.idUsuario;
  const isConviviente =
    user?.rol === 'RESIDENTE_CONVIVENCIA' ||
    user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user?.rol === 'CONVIVIENTE' ||
    (Array.isArray(user?.roles) && user.roles.includes('RESIDENTE_CONVIVENCIA'));

  const { tiposDoc, error: errorTiposDoc } = useTiposDocumento();
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  // ==== 1. Carga de Visitantes Frecuentes ====
  const {
    data: frecuentesRaw,
    loading: loadingFrecuentes,
    refetch: refetchFrecuentes,
  } = useFetch(
    () =>
      residentId
        ? api.get(`/residentes/${residentId}/frecuentes`)
        : Promise.resolve([]),
    [residentId]
  );
  const frecuentes = useMemo(() => {
    return Array.isArray(frecuentesRaw)
      ? frecuentesRaw
      : frecuentesRaw?.items || [];
  }, [frecuentesRaw]);

  // ==== 2. Carga de Códigos QR Activos ====
  const {
    data: qrsRaw,
    loading: loadingQrs,
    refetch: refetchQrs,
  } = useFetch(
    () =>
      residentId
        ? api.get(`/residentes/${residentId}/qr-activos`)
        : Promise.resolve([]),
    [residentId]
  );
  const qrActivos = useMemo(() => {
    return Array.isArray(qrsRaw) ? qrsRaw : qrsRaw?.items || [];
  }, [qrsRaw]);

  // ==== 2.5 Carga de Historial de Visitas (Solo Residente Titular) ====
  const [pageHistorial, setPageHistorial] = useState(0);
  const [searchHistorial, setSearchHistorial] = useState('');
  const [searchHistorialDebounced, setSearchHistorialDebounced] = useState('');
  const [detalleVisita, setDetalleVisita] = useState(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setSearchHistorialDebounced(searchHistorial);
      setPageHistorial(0);
    }, 350);
    return () => clearTimeout(t);
  }, [searchHistorial]);

  const {
    data: historialRaw,
    loading: loadingHistorial,
    refetch: refetchHistorial,
  } = useFetch(
    () => {
      if (isConviviente || !residentId) {
        return Promise.resolve({ items: [], total: 0, totalPages: 0, page: 0, size: 10 });
      }
      const params = new URLSearchParams();
      params.append('page', String(pageHistorial));
      params.append('size', '10');
      if (searchHistorialDebounced.trim()) {
        params.append('search', searchHistorialDebounced.trim());
      }
      return api.get(`/residentes/${residentId}/visitas-historial?${params.toString()}`);
    },
    [residentId, isConviviente, pageHistorial, searchHistorialDebounced]
  );

  const historialData = useMemo(() => {
    const payload = historialRaw?.data !== undefined ? historialRaw.data : historialRaw;
    return {
      items: Array.isArray(payload?.items) ? payload.items : [],
      total: typeof payload?.total === 'number' ? payload.total : 0,
      page: typeof payload?.page === 'number' ? payload.page : pageHistorial,
      size: typeof payload?.size === 'number' ? payload.size : 10,
      totalPages: typeof payload?.totalPages === 'number' ? payload.totalPages : 0,
    };
  }, [historialRaw, pageHistorial]);

  const historialItems = historialData.items;
  const totalHistorial = historialData.total;
  const totalPagesHistorial = historialData.totalPages;

  // Refresco consolidado
  const [refreshing, setRefreshing] = useState(false);
  const refetchAll = useCallback(() => {
    setRefreshing(true);
    const promises = [refetchFrecuentes(), refetchQrs()];
    if (!isConviviente && refetchHistorial) {
      promises.push(refetchHistorial());
    }
    Promise.allSettled(promises).finally(() => {
      setTimeout(() => setRefreshing(false), 400);
      toast.success('Listados de visitas actualizados');
    });
  }, [refetchFrecuentes, refetchQrs, isConviviente, refetchHistorial]);

  // Filtro de búsqueda en frecuentes
  const [search, setSearch] = useState('');
  const frecuentesFiltrados = useMemo(() => {
    if (!search.trim()) return frecuentes;
    const term = search.toLowerCase();
    return frecuentes.filter((f) =>
      [f.nombreVisitante, f.documento, f.ultimaPlaca, f.empresa]
        .filter(Boolean)
        .some((val) => String(val).toLowerCase().includes(term))
    );
  }, [frecuentes, search]);

  // ==== 3. Modal de Registrar Nueva Visita ====
  const [modalNuevaVisita, setModalNuevaVisita] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [sending, setSending] = useState(false);
  const sendingRef = useRef(false);

  // Debounced search para autocompletar visitante si ya existe en portería
  const [buscarDoc, setBuscarDoc] = useState('');
  const [buscarDocDebounced, setBuscarDocDebounced] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setBuscarDocDebounced(buscarDoc), 400);
    return () => clearTimeout(t);
  }, [buscarDoc]);

  const { data: visitanteExistente } = useFetch(
    () =>
      buscarDocDebounced.length >= 4
        ? api
            .get(
              `/porteria/visitas/buscar?documento=${encodeURIComponent(
                buscarDocDebounced
              )}`
            )
            .catch(() => null)
        : Promise.resolve(null),
    [buscarDocDebounced]
  );
  const visitanteEncontrado =
    visitanteExistente?.raw || visitanteExistente || null;

  // Autocompletar cuando se encuentra un visitante en DB
  useEffect(() => {
    if (
      visitanteEncontrado &&
      visitanteEncontrado.nombres &&
      form.visitante.numeroDocumento === visitanteEncontrado.numeroDocumento &&
      !form.visitante.nombres
    ) {
      setForm((f) => ({
        ...f,
        visitante: {
          idTipoDoc:
            visitanteEncontrado.idTipoDoc || f.visitante.idTipoDoc || '',
          numeroDocumento: visitanteEncontrado.numeroDocumento,
          nombres: visitanteEncontrado.nombres || '',
          apellidos: visitanteEncontrado.apellidos || '',
          telefono: visitanteEncontrado.telefono || '',
          email: visitanteEncontrado.email || '',
        },
      }));
    }
  }, [visitanteEncontrado, form.visitante.numeroDocumento, form.visitante.nombres]);

  function updateForm(path, value) {
    setForm((f) => {
      const keys = path.split('.');
      const next = { ...f };
      let cursor = next;
      for (let i = 0; i < keys.length - 1; i++) {
        cursor[keys[i]] = { ...cursor[keys[i]] };
        cursor = cursor[keys[i]];
      }
      cursor[keys[keys.length - 1]] = value;
      return next;
    });
  }

  function handleDocumentoChange(doc) {
    updateForm('visitante.numeroDocumento', doc);
    setBuscarDoc(doc);
  }

  const codigoDocSeleccionado = useMemo(() => {
    return (
      tiposDoc.find(
        (t) => Number(t.idTipoDoc) === Number(form.visitante.idTipoDoc)
      )?.codigo || ''
    );
  }, [tiposDoc, form.visitante.idTipoDoc]);

  function validateNuevaVisita() {
    const e = {};
    const rN = valNombre(form.visitante.nombres, 'El nombre del visitante');
    if (!rN.ok) e['visitante.nombres'] = rN.mensaje;

    const rA = valApellido(form.visitante.apellidos, 'El apellido del visitante');
    if (!rA.ok) e['visitante.apellidos'] = rA.mensaje;

    const rD = valDocumento(
      form.visitante.numeroDocumento,
      codigoDocSeleccionado,
      'El documento del visitante'
    );
    if (!rD.ok) e['visitante.numeroDocumento'] = rD.mensaje;

    if (!form.visitante.idTipoDoc && !visitanteEncontrado?.nombres) {
      e['visitante.idTipoDoc'] = 'Seleccione el tipo de documento';
    }

    const rTel = valTelefono(form.visitante.telefono, { required: false });
    if (!rTel.ok) e['visitante.telefono'] = rTel.mensaje;

    const rEmail = valEmail(form.visitante.email, { required: false });
    if (!rEmail.ok) e['visitante.email'] = rEmail.mensaje;

    if (
      (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') &&
      form.placa?.trim()
    ) {
      const rPlaca = valPlaca(
        form.placa,
        form.medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO'
      );
      if (!rPlaca.ok) e.placa = rPlaca.mensaje;
    }

    if (
      form.medioTransporte === 'BICICLETA' ||
      form.medioTransporte === 'OTRO'
    ) {
      if (!form.descripcion.trim()) {
        e.descripcion =
          'La descripción es requerida para ' +
          (form.medioTransporte === 'BICICLETA' ? 'bicicleta' : 'otro medio');
      }
    }

    const validez = Number(form.tiempoValidezMin);
    if (
      !form.tiempoValidezMin ||
      Number.isNaN(validez) ||
      validez < 5 ||
      validez > 60
    ) {
      e.tiempoValidezMin = 'La validez debe ser entre 5 y 60 minutos';
    }

    const personas = Number(form.cantidadPersonas);
    if (
      !form.cantidadPersonas ||
      Number.isNaN(personas) ||
      personas < 1 ||
      personas > 99
    ) {
      e.cantidadPersonas = 'Debe ser entre 1 y 99 personas';
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function registrarVisita() {
    if (sendingRef.current) return;
    const fields = [
      'visitante.idTipoDoc',
      'visitante.numeroDocumento',
      'visitante.nombres',
      'visitante.apellidos',
      'visitante.telefono',
      'visitante.email',
      'tiempoValidezMin',
      'cantidadPersonas',
      ...(form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO'
        ? ['placa']
        : []),
      ...(form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO'
        ? ['descripcion']
        : []),
    ];
    touchAll(fields);

    if (!validateNuevaVisita()) return;

    sendingRef.current = true;
    setSending(true);

    try {
      const payload = {
        unidadId: user?.idUnidad || user?.unidadId || undefined,
        metodoIngreso: 'CODIGO_QR',
        visitante: {
          idTipoDoc: form.visitante.idTipoDoc ? Number(form.visitante.idTipoDoc) : undefined,
          numeroDocumento: form.visitante.numeroDocumento?.trim(),
          nombres: form.visitante.nombres?.trim(),
          apellidos: form.visitante.apellidos?.trim(),
          telefono: form.visitante.telefono?.trim() || null,
          email: form.visitante.email?.trim() || null,
        },
        idResidente: residentId,
        tiempoValidezMin: Number(form.tiempoValidezMin),
        cantidadPersonas: Number(form.cantidadPersonas),
        notas: form.motivo?.trim() || null,
        guardarFrecuente: Boolean(form.guardarFrecuente),
      };

      if (!payload.visitante.idTipoDoc) delete payload.visitante.idTipoDoc;

      if (
        (form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') &&
        form.placa?.trim()
      ) {
        payload.vehiculo = {
          placa: form.placa.trim().toUpperCase(),
          tipo:
            form.medioTransporte === 'CARRO' ? 'VEHICULO' : form.medioTransporte,
        };
      } else if (
        (form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') &&
        form.descripcion?.trim()
      ) {
        payload.vehiculo = {
          tipo: form.medioTransporte,
          descripcion: form.descripcion.trim(),
        };
      }

      const res = await api.post('/porteria/visitas', payload);

      toast.success('¡Visita registrada y código QR generado con éxito!');
      setModalNuevaVisita(false);
      setForm(emptyForm);
      resetTouched();
      refetchAll();

      // Abrir modal de éxito con el QR generado
      setQrExito({
        codigoQr: res.codigoQr,
        nombreVisitante: `${form.visitante.nombres} ${form.visitante.apellidos}`.trim(),
        tiempoValidezMin: form.tiempoValidezMin,
        fechaExpiracion: calcularFechaExpiracion(form.tiempoValidezMin),
      });
    } catch (err) {
      toast.error(err.message || 'Error al registrar la visita');
      const apiErrors = err.errors || err.response?.data?.errors;
      if (apiErrors && typeof apiErrors === 'object') {
        setErrors((prev) => ({ ...prev, ...apiErrors }));
        touchAll(Object.keys(apiErrors));
      }
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  // ==== 4. Modal de Pase Rápido para Visitante Frecuente ====
  const [frecuenteSeleccionado, setFrecuenteSeleccionado] = useState(null);
  const [rapidoForm, setRapidoForm] = useState({
    medioTransporte: 'A_PIE',
    placa: '',
    descripcion: '',
    cantidadPersonas: '1',
    tiempoValidezMin: '30',
    notas: '',
  });
  const [rapidoErrors, setRapidoErrors] = useState({});
  const [generandoRapido, setGenerandoRapido] = useState(false);
  const rapidoRef = useRef(false);

  function abrirPaseRapido(f) {
    const medio =
      f.ultimoTipoVehiculo === 'VEHICULO'
        ? 'CARRO'
        : f.ultimoTipoVehiculo || 'A_PIE';
    setRapidoForm({
      medioTransporte: medio,
      placa: f.ultimaPlaca || '',
      descripcion: f.ultimaDescripcionTipo || '',
      cantidadPersonas: '1',
      tiempoValidezMin: '30',
      notas: '',
    });
    setRapidoErrors({});
    setFrecuenteSeleccionado(f);
  }

  function validateRapido() {
    const e = {};
    const personas = Number(rapidoForm.cantidadPersonas);
    if (
      !rapidoForm.cantidadPersonas ||
      Number.isNaN(personas) ||
      personas < 1 ||
      personas > 99
    ) {
      e.cantidadPersonas = 'Debe ser entre 1 y 99 personas';
    }
    const validez = Number(rapidoForm.tiempoValidezMin);
    if (
      !rapidoForm.tiempoValidezMin ||
      Number.isNaN(validez) ||
      validez < 5 ||
      validez > 60
    ) {
      e.tiempoValidezMin = 'La validez debe ser entre 5 y 60 minutos';
    }
    if (
      rapidoForm.medioTransporte === 'CARRO' ||
      rapidoForm.medioTransporte === 'MOTO'
    ) {
      const rPlaca = valPlaca(
        rapidoForm.placa,
        rapidoForm.medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO'
      );
      if (!rPlaca.ok) e.placa = rPlaca.mensaje;
    }
    if (
      rapidoForm.medioTransporte === 'BICICLETA' ||
      rapidoForm.medioTransporte === 'OTRO'
    ) {
      if (!rapidoForm.descripcion.trim()) {
        e.descripcion = 'La descripción es requerida';
      }
    }
    setRapidoErrors(e);
    return Object.keys(e).length === 0;
  }

  async function generarPaseRapido() {
    if (rapidoRef.current) return;
    if (!validateRapido()) return;

    rapidoRef.current = true;
    setGenerandoRapido(true);

    try {
      const tipoVehiculo =
        rapidoForm.medioTransporte === 'A_PIE'
          ? null
          : rapidoForm.medioTransporte === 'CARRO'
          ? 'VEHICULO'
          : rapidoForm.medioTransporte;

      const res = await api.post('/porteria/visitas/rapida', {
        idFrecuente: frecuenteSeleccionado.idFrecuente,
        idVisitante: frecuenteSeleccionado.idVisitante,
        cantidadPersonas: Number(rapidoForm.cantidadPersonas),
        tiempoValidezMin: Number(rapidoForm.tiempoValidezMin),
        tipoVehiculo,
        placa:
          rapidoForm.medioTransporte === 'CARRO' ||
          rapidoForm.medioTransporte === 'MOTO'
            ? rapidoForm.placa.toUpperCase()
            : null,
        descripcionTipo:
          rapidoForm.medioTransporte === 'BICICLETA' ||
          rapidoForm.medioTransporte === 'OTRO'
            ? rapidoForm.descripcion.trim()
            : null,
        notas: rapidoForm.notas.trim() || null,
      });

      toast.success(
        `Pase QR emitido para ${frecuenteSeleccionado.nombreVisitante}`
      );
      setFrecuenteSeleccionado(null);
      refetchAll();

      // Abrir modal de éxito
      setQrExito({
        codigoQr: res.codigoQr,
        nombreVisitante: frecuenteSeleccionado.nombreVisitante,
        tiempoValidezMin: rapidoForm.tiempoValidezMin,
        fechaExpiracion: calcularFechaExpiracion(rapidoForm.tiempoValidezMin),
      });
    } catch (err) {
      toast.error(err.message || 'Error al generar el pase de visita rápida');
      const apiErrors = err.errors || err.response?.data?.errors;
      if (apiErrors && typeof apiErrors === 'object') {
        setRapidoErrors((prev) => ({ ...prev, ...apiErrors }));
      }
    } finally {
      rapidoRef.current = false;
      setGenerandoRapido(false);
    }
  }

  // ==== 5. Quitar Visitante Frecuente ====
  const [confirmQuitar, setConfirmQuitar] = useState(null);

  async function confirmarQuitarFrecuente() {
    if (!confirmQuitar) return;
    try {
      if (residentId && confirmQuitar.idFrecuente) {
        await api.del(
          `/residentes/${residentId}/frecuentes/${confirmQuitar.idFrecuente}`
        );
      }
      toast.success('Visitante retirado de tu lista de frecuentes');
      refetchFrecuentes();
    } catch (err) {
      toast.error(err.message || 'No se pudo retirar al visitante');
    } finally {
      setConfirmQuitar(null);
    }
  }

  // ==== 6. Modales de Visualización y Compartición de QR ====
  const [qrExito, setQrExito] = useState(null);
  const [qrZoom, setQrZoom] = useState(null);

  function compartirTelegram(codigoQr, nombre) {
    const text = encodeURIComponent(
      `Código de acceso QR para ${nombre || 'tu visita'}: ${codigoQr}\n\nPresenta este código al ingresar a la copropiedad.`
    );
    window.open(
      `https://t.me/share/url?url=${encodeURIComponent(window.location.origin)}&text=${text}`,
      '_blank'
    );
  }

  function compartirSMS(codigoQr, telefono) {
    const body = encodeURIComponent(
      `Tu código de acceso QR en portería es: ${codigoQr}`
    );
    window.open(telefono ? `sms:${telefono}?body=${body}` : `sms:?body=${body}`);
  }

  function compartirCorreo(codigoQr, nombre, email) {
    const subject = encodeURIComponent('Pase de Acceso con Código QR — SAED');
    const body = encodeURIComponent(
      `Hola,\n\nHas recibido un pase de acceso rápido con código QR${
        nombre ? ` para ${nombre}` : ''
      }.\n\n` +
        `Código de Acceso: ${codigoQr}\n\nPresenta este código al ingresar en portería.\n\n` +
        `Conjunto / Edificio: ${user?.nombrePropiedad || 'Copropiedad'}`
    );
    window.open(
      email
        ? `mailto:${email}?subject=${subject}&body=${body}`
        : `mailto:?subject=${subject}&body=${body}`
    );
  }

  async function copiarQR(codigoQr) {
    try {
      await navigator.clipboard.writeText(codigoQr);
      toast.success('Código QR copiado al portapapeles');
    } catch {
      toast.error('No se pudo copiar el código');
    }
  }

  return (
    <PageContainer>
      {/* 1. CABECERA PRINCIPAL CON BOTÓN DE REGISTRAR NUEVA VISITA */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2 border-b border-border/70">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              Visitas
            </h1>
            <Badge variant="outline" className="text-xs font-semibold">
              Control Peatonal y Vehicular
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            Gestiona tus visitantes frecuentes, autoriza nuevas visitas y comparte pases QR dinámicos para portería.
          </p>
        </div>

        <div className="flex items-center gap-2.5 self-stretch sm:self-auto">
          <Button
            variant="outline"
            size="sm"
            onClick={refetchAll}
            disabled={refreshing}
            className="gap-1.5 shadow-sm text-xs"
          >
            <RefreshCw className={cn('w-3.5 h-3.5', refreshing && 'animate-spin')} />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>

          <Button
            onClick={() => {
              setForm(emptyForm);
              setBuscarDoc('');
              setBuscarDocDebounced('');
              setErrors({});
              resetTouched();
              setModalNuevaVisita(true);
            }}
            className="gap-2 font-semibold shadow-sm text-xs sm:text-sm bg-primary text-primary-foreground hover:bg-primary/90"
          >
            <UserPlus className="w-4 h-4" />
            Registrar Nueva Visita
          </Button>
        </div>
      </div>

      {/* 2. STRIP DE 3 KPIS DE GESTIÓN DE VISITAS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* KPI 1: Frecuentes */}
        <Card className="border-border/70 bg-card">
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-purple-500/10 text-purple-600 dark:text-purple-400 shrink-0">
              <Users className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Visitantes Frecuentes
              </p>
              <h3 className="text-xl font-bold text-foreground truncate">
                {frecuentes.length} Registrado(s)
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                Familiares, amigos y servicios de confianza
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2: QR Activos */}
        <Card className="border-border/70 bg-card">
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 shrink-0">
              <QrCode className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Pases QR Vigentes
              </p>
              <h3 className="text-xl font-bold text-foreground truncate">
                {qrActivos.length} Activo(s)
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {qrActivos.length > 0
                  ? 'Listos para validación en portería'
                  : 'Sin invitaciones en tránsito'}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3: Seguridad y Protocolo */}
        <Card className="border-border/70 bg-card">
          <CardContent className="p-4 sm:p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 shrink-0">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Portería & Seguridad
              </p>
              <h3 className="text-xl font-bold text-foreground truncate">
                Control 24/7
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                Lector digital y registro fotográfico
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. TABS: FRECUENTES, PASES ACTIVOS & HISTORIAL (SOLO RESIDENTE TITULAR) */}
      <Tabs defaultValue="frecuentes" className="space-y-6">
        <TabsList
          className={cn(
            'grid w-full h-11 p-1 bg-muted/80 rounded-xl border border-border',
            isConviviente ? 'grid-cols-2 max-w-md' : 'grid-cols-3 max-w-xl'
          )}
        >
          <TabsTrigger value="frecuentes" className="gap-2 text-xs sm:text-sm font-semibold">
            <Users className="w-4 h-4" />
            Visitantes Frecuentes ({frecuentes.length})
          </TabsTrigger>
          <TabsTrigger value="activos" className="gap-2 text-xs sm:text-sm font-semibold">
            <QrCode className="w-4 h-4" />
            Pases QR Activos ({qrActivos.length})
          </TabsTrigger>
          {!isConviviente && (
            <TabsTrigger value="historial" className="gap-2 text-xs sm:text-sm font-semibold">
              <History className="w-4 h-4" />
              Historial ({totalHistorial})
            </TabsTrigger>
          )}
        </TabsList>

        {/* TAB 1: VISITANTES FRECUENTES */}
        <TabsContent value="frecuentes" className="space-y-4">
          <Card className="border-border/80">
            <CardHeader className="pb-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <UserCheck className="w-5 h-5 text-primary" />
                    Mis Visitantes Frecuentes
                  </CardTitle>
                  <CardDescription>
                    Genera pases de acceso rápido en un solo clic con los datos habituales de tus invitados
                  </CardDescription>
                </div>

                {/* Buscador en tiempo real */}
                <div className="w-full sm:w-72 relative">
                  <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <input
                    type="text"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Buscar por nombre o cédula..."
                    className="w-full pl-9 pr-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                  {search && (
                    <button
                      type="button"
                      onClick={() => setSearch('')}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground text-xs"
                    >
                      ×
                    </button>
                  )}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {loadingFrecuentes ? (
                <div className="p-8 text-center text-muted-foreground text-xs">
                  Cargando visitantes frecuentes...
                </div>
              ) : frecuentesFiltrados.length === 0 ? (
                <EmptyState
                  icon="groups"
                  title={search ? 'No se encontraron coincidencias' : 'Aún no tienes visitantes frecuentes'}
                  subtitle={
                    search
                      ? 'Prueba con otro término de búsqueda o limpia el filtro.'
                      : 'Registra a tus personas de confianza para autorizar su ingreso a portería en un clic.'
                  }
                >
                  <div className="mt-4">
                    <Button
                      onClick={() => {
                        setForm(emptyForm);
                        setBuscarDoc('');
                        setBuscarDocDebounced('');
                        setErrors({});
                        resetTouched();
                        setModalNuevaVisita(true);
                      }}
                      className="gap-2 text-xs font-semibold"
                    >
                      <UserPlus className="w-4 h-4" />
                      Registrar Nueva Visita
                    </Button>
                  </div>
                </EmptyState>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {frecuentesFiltrados.map((f) => {
                    const iniciales = (f.nombreVisitante || 'Visitante')
                      .split(' ')
                      .filter(Boolean)
                      .slice(0, 2)
                      .map((p) => p[0])
                      .join('')
                      .toUpperCase();

                    return (
                      <div
                        key={f.idFrecuente || f.idVisitante}
                        className="p-5 rounded-xl border border-border bg-card hover:shadow-sm transition-all space-y-4 flex flex-col justify-between"
                      >
                        <div className="space-y-3">
                          <div className="flex items-start gap-3.5">
                            <div className="w-12 h-12 rounded-xl bg-primary/10 border border-primary/20 text-primary font-black text-sm flex items-center justify-center shrink-0">
                              {iniciales || 'VI'}
                            </div>
                            <div className="min-w-0 flex-1">
                              <h4 className="text-sm font-bold text-foreground truncate">
                                {f.nombreVisitante || 'Visitante Frecuente'}
                              </h4>
                              <p className="text-xs text-muted-foreground font-mono mt-0.5">
                                Doc: {f.documento || '—'}
                              </p>
                              {f.empresa && (
                                <Badge variant="secondary" className="text-[10px] mt-1">
                                  {f.empresa}
                                </Badge>
                              )}
                            </div>
                          </div>

                          <div className="p-3 rounded-lg bg-muted/30 border border-border/50 text-xs space-y-1.5">
                            {f.ultimaPlaca ? (
                              <div className="flex items-center justify-between">
                                <span className="text-muted-foreground flex items-center gap-1">
                                  <Car className="w-3.5 h-3.5" /> Placa habitual:
                                </span>
                                <span className="font-mono font-bold text-foreground bg-card px-1.5 py-0.5 rounded border border-border">
                                  {f.ultimaPlaca}
                                </span>
                              </div>
                            ) : (
                              <div className="flex items-center justify-between text-muted-foreground">
                                <span>Medio habitual:</span>
                                <span className="font-medium text-foreground">Peatonal (A pie)</span>
                              </div>
                            )}

                            {f.ultimaVisita && (
                              <div className="flex items-center justify-between">
                                <span className="text-muted-foreground flex items-center gap-1">
                                  <Clock className="w-3.5 h-3.5" /> Último ingreso:
                                </span>
                                <span className="text-foreground">
                                  {formatDate(f.ultimaVisita)}
                                </span>
                              </div>
                            )}
                          </div>
                        </div>

                        <div className="pt-2 border-t border-border flex items-center justify-between gap-2">
                          <Button
                            variant="default"
                            size="sm"
                            onClick={() => abrirPaseRapido(f)}
                            className="flex-1 gap-1.5 text-xs font-semibold bg-primary text-primary-foreground hover:bg-primary/90"
                          >
                            <QrCode className="w-3.5 h-3.5" />
                            Pase Rápido
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setConfirmQuitar(f)}
                            className="px-2.5 text-muted-foreground hover:text-rose-600 hover:border-rose-300 dark:hover:border-rose-800"
                            title="Quitar de frecuentes"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* TAB 2: PASES QR ACTIVOS */}
        <TabsContent value="activos" className="space-y-4">
          <Card className="border-border/80">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <QrCode className="w-5 h-5 text-primary" />
                    Pases de Acceso QR Vigentes
                  </CardTitle>
                  <CardDescription>
                    Códigos QR activos que tus invitados pueden presentar al guardia en portería
                  </CardDescription>
                </div>
                <Button
                  size="sm"
                  onClick={() => {
                    setForm(emptyForm);
                    setBuscarDoc('');
                    setBuscarDocDebounced('');
                    setErrors({});
                    resetTouched();
                    setModalNuevaVisita(true);
                  }}
                  className="gap-1.5 text-xs"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Nuevo Pase
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {loadingQrs ? (
                <div className="p-8 text-center text-muted-foreground text-xs">
                  Consultando pases en portería...
                </div>
              ) : qrActivos.length === 0 ? (
                <EmptyState
                  icon="qr_code_2"
                  title="No tienes pases QR vigentes"
                  subtitle="Genera una nueva invitación para que tus visitantes o entregas ingresen con validación rápida."
                >
                  <div className="mt-4">
                    <Button
                      onClick={() => {
                        setForm(emptyForm);
                        setBuscarDoc('');
                        setBuscarDocDebounced('');
                        setErrors({});
                        resetTouched();
                        setModalNuevaVisita(true);
                      }}
                      className="gap-2 text-xs font-semibold"
                    >
                      <Plus className="w-4 h-4" />
                      Generar Pase de Acceso
                    </Button>
                  </div>
                </EmptyState>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {qrActivos.map((qr) => (
                    <div
                      key={qr.idQr}
                      className="p-5 rounded-xl border border-border bg-card space-y-4 hover:shadow-sm transition-all flex flex-col justify-between"
                    >
                      <div className="flex items-start gap-4">
                        <LocalQRCode
                          value={qr.codigoQr}
                          size={72}
                          alt={`QR ${qr.nombreVisitante || 'Visita'}`}
                          title={`Ver código QR de ${qr.nombreVisitante || 'Visita'}`}
                          onClick={() => setQrZoom(qr)}
                          className="w-18 h-18 rounded-xl border border-border cursor-zoom-in bg-white p-1 shrink-0 shadow-xs"
                        />
                        <div className="min-w-0 flex-1">
                          <Badge variant="outline" className="text-[10px] mb-1">
                            Pase Dinámico
                          </Badge>
                          <h4 className="text-sm font-bold text-foreground truncate">
                            {qr.nombreVisitante || 'Visitante Autorizado'}
                          </h4>
                          {qr.documentoVisitante && (
                            <p className="text-xs text-muted-foreground font-mono mt-0.5">
                              Doc: {qr.documentoVisitante}
                            </p>
                          )}
                          <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                            <Users className="w-3 h-3" />
                            {qr.cantidadPersonas || 1} Persona(s)
                          </p>
                          {qr.fechaCreacion && (
                            <p className="text-[11px] text-muted-foreground flex items-center gap-1 mt-0.5">
                              <Calendar className="w-3 h-3" />
                              Generado: {formatDateTime(qr.fechaCreacion)}
                            </p>
                          )}
                          <p className="text-[11px] text-amber-600 dark:text-amber-400 font-semibold flex items-center gap-1 mt-0.5">
                            <Clock className="w-3 h-3" />
                            Expira: {formatDateTime(qr.fechaExpiracion)}
                          </p>
                        </div>
                      </div>

                      <div className="p-2.5 rounded-lg bg-muted/40 font-mono text-[11px] flex justify-between items-center">
                        <span className="text-muted-foreground">Código:</span>
                        <span className="font-bold text-foreground">
                          #{String(qr.codigoQr).slice(0, 14)}...
                        </span>
                      </div>

                      <div className="grid grid-cols-4 gap-1.5 pt-1 border-t border-border">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => compartirTelegram(qr.codigoQr, qr.nombreVisitante)}
                          className="h-8 p-0 text-blue-500"
                          title="Enviar por Telegram"
                        >
                          <Send className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => compartirSMS(qr.codigoQr, '')}
                          className="h-8 p-0 text-emerald-500"
                          title="Enviar por SMS"
                        >
                          <Phone className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => compartirCorreo(qr.codigoQr, qr.nombreVisitante, '')}
                          className="h-8 p-0 text-purple-500"
                          title="Enviar por Correo"
                        >
                          <Mail className="w-3.5 h-3.5" />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => copiarQR(qr.codigoQr)}
                          className="h-8 p-0 text-slate-500"
                          title="Copiar código"
                        >
                          <Copy className="w-3.5 h-3.5" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* TAB 3: HISTORIAL DE VISITAS (SOLO RESIDENTE TITULAR) */}
        {!isConviviente && (
          <TabsContent value="historial" className="space-y-4">
            <Card className="border-border/80">
              <CardHeader className="pb-3">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <CardTitle className="text-lg flex items-center gap-2">
                      <History className="w-5 h-5 text-primary" />
                      Historial de Visitas Emitidas
                    </CardTitle>
                    <CardDescription>
                      Registro histórico de todas las visitas generadas para tu unidad residencial, medios de transporte y estado de ingreso.
                    </CardDescription>
                  </div>

                  {/* Buscador en tiempo real de historial */}
                  <div className="w-full sm:w-72 relative">
                    <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
                    <input
                      type="text"
                      value={searchHistorial}
                      onChange={(e) => setSearchHistorial(e.target.value)}
                      placeholder="Buscar por visitante, doc, placa..."
                      className="w-full pl-9 pr-8 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
                    />
                    {searchHistorial && (
                      <button
                        type="button"
                        onClick={() => setSearchHistorial('')}
                        className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground text-xs"
                      >
                        ×
                      </button>
                    )}
                  </div>
                </div>
              </CardHeader>

              <CardContent>
                {loadingHistorial ? (
                  <div className="p-12 text-center text-muted-foreground text-xs flex flex-col items-center gap-2">
                    <RefreshCw className="w-5 h-5 animate-spin text-primary" />
                    <span>Consultando historial de visitas...</span>
                  </div>
                ) : historialItems.length === 0 ? (
                  <EmptyState
                    icon="history"
                    title={searchHistorial ? 'No se encontraron visitas' : 'No hay visitas registradas'}
                    subtitle={
                      searchHistorial
                        ? 'No hay registros que coincidan con los criterios de búsqueda.'
                        : 'Cuando autorices visitas o pases QR, aparecerán listados aquí con todo su detalle histórico.'
                    }
                  />
                ) : (
                  <div className="space-y-4">
                    {/* Vista Desktop: Tabla completa */}
                    <div className="hidden md:block overflow-x-auto rounded-lg border border-border">
                      <table className="w-full text-left text-xs border-collapse">
                        <thead>
                          <tr className="bg-muted/50 border-b border-border text-muted-foreground font-semibold">
                            <th className="py-3 px-3.5">Fecha y Hora Generación</th>
                            <th className="py-3 px-3.5">Quién Generó</th>
                            <th className="py-3 px-3.5">Para Quién (Visitante)</th>
                            <th className="py-3 px-3.5">Medio de Transporte</th>
                            <th className="py-3 px-3.5">¿Se Efectuó la Visita?</th>
                            <th className="py-3 px-3.5 text-right">Acción</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {historialItems.map((v) => {
                            const isEfectuada = v.efectuada === 'SI';
                            const medio = (v.medioTransporte || 'PEATONAL').toUpperCase();

                            return (
                              <tr
                                key={v.idVisita}
                                className="hover:bg-muted/30 transition-colors cursor-pointer"
                                onClick={() => setDetalleVisita(v)}
                              >
                                <td className="py-3 px-3.5 whitespace-nowrap">
                                  <div className="flex items-center gap-1.5 font-medium text-foreground">
                                    <Calendar className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                                    <span>{v.fechaGeneracion ? formatDateTime(v.fechaGeneracion) : '—'}</span>
                                  </div>
                                  {v.fechaProgramada && (
                                    <div className="text-[11px] text-muted-foreground mt-0.5">
                                      Programada: {formatDate(v.fechaProgramada)}
                                    </div>
                                  )}
                                </td>

                                <td className="py-3 px-3.5">
                                  <Badge variant="outline" className="text-[11px] font-medium bg-primary/5 text-primary border-primary/20">
                                    {v.generadoPor || 'Residente'}
                                  </Badge>
                                  {v.usernameGenerador && (
                                    <div className="text-[10px] text-muted-foreground font-mono mt-0.5">
                                      @{v.usernameGenerador}
                                    </div>
                                  )}
                                </td>

                                <td className="py-3 px-3.5">
                                  <div className="font-bold text-foreground">
                                    {v.nombreVisitante || 'Visitante'}
                                  </div>
                                  <div className="text-[11px] text-muted-foreground font-mono mt-0.5">
                                    Doc: {v.documentoVisitante || '—'}
                                  </div>
                                </td>

                                <td className="py-3 px-3.5">
                                  <div className="flex items-center gap-1.5">
                                    {medio === 'CARRO' ? (
                                      <Badge variant="secondary" className="gap-1 text-[11px] font-normal">
                                        <Car className="w-3.5 h-3.5 text-blue-500" />
                                        <span>Carro</span>
                                        {v.placa && (
                                          <span className="font-mono font-bold ml-1 bg-background px-1 py-0.2 rounded border text-[10px]">
                                            {v.placa}
                                          </span>
                                        )}
                                      </Badge>
                                    ) : medio === 'MOTO' ? (
                                      <Badge variant="secondary" className="gap-1 text-[11px] font-normal">
                                        <Bike className="w-3.5 h-3.5 text-amber-500" />
                                        <span>Moto</span>
                                        {v.placa && (
                                          <span className="font-mono font-bold ml-1 bg-background px-1 py-0.2 rounded border text-[10px]">
                                            {v.placa}
                                          </span>
                                        )}
                                      </Badge>
                                    ) : medio === 'BICICLETA' ? (
                                      <Badge variant="secondary" className="gap-1 text-[11px] font-normal">
                                        <Bike className="w-3.5 h-3.5 text-emerald-500" />
                                        <span>Bicicleta</span>
                                      </Badge>
                                    ) : (
                                      <Badge variant="outline" className="gap-1 text-[11px] font-normal text-muted-foreground">
                                        <Footprints className="w-3.5 h-3.5" />
                                        <span>Peatonal</span>
                                      </Badge>
                                    )}
                                  </div>
                                </td>

                                <td className="py-3 px-3.5">
                                  {isEfectuada ? (
                                    <div>
                                      <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20 border-emerald-500/20 gap-1 text-[11px] font-semibold">
                                        <CheckCircle2 className="w-3.5 h-3.5" />
                                        Sí (Efectuada)
                                      </Badge>
                                      {v.fechaIngreso && (
                                        <div className="text-[10px] text-muted-foreground mt-0.5 flex items-center gap-1">
                                          <Clock className="w-3 h-3" />
                                          Ingreso: {formatDateTime(v.fechaIngreso)}
                                        </div>
                                      )}
                                    </div>
                                  ) : (
                                    <div>
                                      <Badge variant="outline" className="text-muted-foreground gap-1 text-[11px]">
                                        <Clock className="w-3.5 h-3.5 text-amber-500" />
                                        No
                                      </Badge>
                                      {v.estadoVisita && (
                                        <div className="text-[10px] text-muted-foreground capitalize mt-0.5">
                                          {v.estadoVisita.toLowerCase()}
                                        </div>
                                      )}
                                    </div>
                                  )}
                                </td>

                                <td className="py-3 px-3.5 text-right">
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setDetalleVisita(v);
                                    }}
                                    className="h-8 px-2 text-xs text-muted-foreground hover:text-foreground"
                                  >
                                    <Eye className="w-3.5 h-3.5 mr-1" />
                                    Detalle
                                  </Button>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>

                    {/* Vista Mobile: Cards */}
                    <div className="grid grid-cols-1 gap-3 md:hidden">
                      {historialItems.map((v) => {
                        const isEfectuada = v.efectuada === 'SI';
                        const medio = (v.medioTransporte || 'PEATONAL').toUpperCase();

                        return (
                          <div
                            key={v.idVisita}
                            onClick={() => setDetalleVisita(v)}
                            className="p-4 rounded-xl border border-border bg-card space-y-3 cursor-pointer hover:shadow-sm"
                          >
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <h4 className="text-sm font-bold text-foreground">
                                  {v.nombreVisitante || 'Visitante'}
                                </h4>
                                <p className="text-xs text-muted-foreground font-mono">
                                  Doc: {v.documentoVisitante || '—'}
                                </p>
                              </div>
                              {isEfectuada ? (
                                <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] gap-1 font-semibold">
                                  <CheckCircle2 className="w-3 h-3" />
                                  Sí (Efectuada)
                                </Badge>
                              ) : (
                                <Badge variant="outline" className="text-muted-foreground text-[10px] gap-1">
                                  <Clock className="w-3 h-3 text-amber-500" />
                                  No
                                </Badge>
                              )}
                            </div>

                            <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground pt-1 border-t border-border/60">
                              <div>
                                <span className="text-[10px] uppercase font-semibold block">Generado por</span>
                                <span className="font-medium text-foreground">{v.generadoPor || 'Residente'}</span>
                              </div>
                              <div>
                                <span className="text-[10px] uppercase font-semibold block">Transporte</span>
                                <span className="font-medium text-foreground flex items-center gap-1">
                                  {medio === 'CARRO' ? (
                                    <>
                                      <Car className="w-3 h-3 text-blue-500" /> Carro {v.placa ? `(${v.placa})` : ''}
                                    </>
                                  ) : medio === 'MOTO' ? (
                                    <>
                                      <Bike className="w-3 h-3 text-amber-500" /> Moto {v.placa ? `(${v.placa})` : ''}
                                    </>
                                  ) : (
                                    <>
                                      <Footprints className="w-3 h-3" /> Peatonal
                                    </>
                                  )}
                                </span>
                              </div>
                              <div className="col-span-2">
                                <span className="text-[10px] uppercase font-semibold block">Generación</span>
                                <span className="text-foreground">{v.fechaGeneracion ? formatDateTime(v.fechaGeneracion) : '—'}</span>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>

                    {/* Barra de Paginación: 10 visitas por página */}
                    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-3 border-t border-border text-xs text-muted-foreground">
                      <div>
                        Mostrando{' '}
                        <span className="font-semibold text-foreground">
                          {totalHistorial === 0 ? 0 : pageHistorial * 10 + 1}
                        </span>{' '}
                        a{' '}
                        <span className="font-semibold text-foreground">
                          {Math.min((pageHistorial + 1) * 10, totalHistorial)}
                        </span>{' '}
                        de <span className="font-semibold text-foreground">{totalHistorial}</span> visitas
                      </div>

                      <div className="flex items-center gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setPageHistorial((p) => Math.max(0, p - 1))}
                          disabled={pageHistorial === 0 || loadingHistorial}
                          className="h-8 gap-1 text-xs"
                        >
                          <ChevronLeft className="w-3.5 h-3.5" />
                          Anterior
                        </Button>

                        <div className="px-2 font-medium text-foreground">
                          Pág. {pageHistorial + 1} de {Math.max(1, totalPagesHistorial)}
                        </div>

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setPageHistorial((p) => Math.min(totalPagesHistorial - 1, p + 1))}
                          disabled={pageHistorial >= totalPagesHistorial - 1 || loadingHistorial}
                          className="h-8 gap-1 text-xs"
                        >
                          Siguiente
                          <ChevronRight className="w-3.5 h-3.5" />
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        )}
      </Tabs>

      {/* 4. MODAL: REGISTRAR NUEVA VISITA (FORMULARIO CON SUS VALIDACIONES) */}
      <Modal
        open={modalNuevaVisita}
        onClose={() => !sending && setModalNuevaVisita(false)}
        title="Registrar Nueva Visita"
        size="lg"
        footer={
          <div className="flex items-center justify-end gap-3 w-full">
            <Button
              variant="outline"
              onClick={() => setModalNuevaVisita(false)}
              disabled={sending}
            >
              Cancelar
            </Button>
            <Button
              onClick={registrarVisita}
              disabled={sending}
              className="gap-2 font-semibold bg-primary text-primary-foreground hover:bg-primary/90"
            >
              <QrCode className="w-4 h-4" />
              {sending ? 'Generando QR...' : 'Generar Pase QR'}
            </Button>
          </div>
        }
      >
        <div className="space-y-4 py-1">
          <p className="text-xs text-muted-foreground">
            Ingresa la información del visitante. Si ya ha ingresado antes, sus datos se autocompletarán tras ingresar el número de documento.
          </p>

          {/* Bloque: Datos del Visitante */}
          <div className="p-4 rounded-xl border border-border bg-card/50 space-y-3">
            <div className="flex items-center gap-2">
              <User className="w-4 h-4 text-primary" />
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Datos del Visitante
              </h4>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <Select
                  id="idTipoDoc"
                  label="Tipo de Documento"
                  value={form.visitante.idTipoDoc}
                  onChange={(e) => updateForm('visitante.idTipoDoc', Number(e.target.value))}
                  onBlur={() => touch('visitante.idTipoDoc')}
                  error={
                    fieldError(
                      'visitante.idTipoDoc',
                      form.visitante.idTipoDoc || visitanteEncontrado?.nombres
                        ? { ok: true }
                        : { ok: false, mensaje: 'Seleccione el tipo de documento' }
                    ) || errors['visitante.idTipoDoc']
                  }
                  required
                >
                  <option value="">Seleccione tipo...</option>
                  {tiposDoc.map((t) => (
                    <option key={t.idTipoDoc ?? t.value} value={t.idTipoDoc ?? t.value}>
                      {t.descripcion || t.nombre}
                    </option>
                  ))}
                </Select>
                {errorTiposDoc && !tiposDoc.length && (
                  <p className="text-[11px] text-destructive mt-1">
                    Error al cargar los tipos de documento
                  </p>
                )}
              </div>

              <div>
                <Input
                  id="numeroDocumento"
                  label="Número de Documento"
                  placeholder={getDocPlaceholder(codigoDocSeleccionado)}
                  value={form.visitante.numeroDocumento}
                  onChange={(e) => handleDocumentoChange(e.target.value)}
                  onBlur={() => touch('visitante.numeroDocumento')}
                  error={
                    fieldError(
                      'visitante.numeroDocumento',
                      valDocumento(
                        form.visitante.numeroDocumento,
                        codigoDocSeleccionado,
                        'El documento del visitante'
                      )
                    ) || errors['visitante.numeroDocumento']
                  }
                  required
                />
              </div>
            </div>

            {/* Aviso de visitante encontrado */}
            {visitanteEncontrado && visitanteEncontrado.nombres && (
              <div className="p-2.5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 text-xs flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
                <span className="text-foreground">
                  Visitante registrado: <strong>{visitanteEncontrado.nombres} {visitanteEncontrado.apellidos}</strong>
                </span>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                id="nombres"
                label="Nombres"
                placeholder="Ej. Juan Carlos"
                value={form.visitante.nombres}
                onChange={(e) => updateForm('visitante.nombres', e.target.value)}
                onBlur={() => touch('visitante.nombres')}
                error={
                  fieldError(
                    'visitante.nombres',
                    valNombre(form.visitante.nombres, 'El nombre del visitante')
                  ) || errors['visitante.nombres']
                }
                required
              />

              <Input
                id="apellidos"
                label="Apellidos"
                placeholder="Ej. Gómez Pérez"
                value={form.visitante.apellidos}
                onChange={(e) => updateForm('visitante.apellidos', e.target.value)}
                onBlur={() => touch('visitante.apellidos')}
                error={
                  fieldError(
                    'visitante.apellidos',
                    valApellido(form.visitante.apellidos, 'El apellido del visitante')
                  ) || errors['visitante.apellidos']
                }
                required
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                id="telefono"
                label="Teléfono Celular (opcional)"
                placeholder="Ej. 300 123 4567"
                inputMode="numeric"
                maxLength={10}
                value={form.visitante.telefono}
                onChange={(e) => updateForm('visitante.telefono', e.target.value)}
                onBlur={() => touch('visitante.telefono')}
                error={
                  fieldError(
                    'visitante.telefono',
                    valTelefono(form.visitante.telefono, { required: false })
                  ) || errors['visitante.telefono']
                }
              />

              <Input
                id="email"
                label="Correo Electrónico (opcional)"
                placeholder="Ej. visitante@correo.com"
                type="email"
                value={form.visitante.email}
                onChange={(e) => updateForm('visitante.email', e.target.value)}
                onBlur={() => touch('visitante.email')}
                error={
                  fieldError(
                    'visitante.email',
                    valEmail(form.visitante.email, { required: false })
                  ) || errors['visitante.email']
                }
              />
            </div>
          </div>

          {/* Bloque: Parámetros de la Visita */}
          <div className="p-4 rounded-xl border border-border bg-card/50 space-y-3">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-primary" />
              <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Parámetros de Entrada & Seguridad
              </h4>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                id="tiempoValidezMin"
                label="Vigencia del Pase (minutos)"
                type="number"
                min="5"
                max="60"
                placeholder="30"
                value={form.tiempoValidezMin}
                onChange={(e) => updateForm('tiempoValidezMin', e.target.value)}
                onBlur={() => touch('tiempoValidezMin')}
                error={
                  fieldError(
                    'tiempoValidezMin',
                    !form.tiempoValidezMin ||
                      Number(form.tiempoValidezMin) < 5 ||
                      Number(form.tiempoValidezMin) > 60
                      ? { ok: false, mensaje: 'La validez debe ser entre 5 y 60 minutos' }
                      : { ok: true }
                  ) || errors.tiempoValidezMin
                }
                required
              />

              <Input
                id="cantidadPersonas"
                label="Cantidad de Personas"
                type="number"
                min="1"
                max="99"
                placeholder="1"
                value={form.cantidadPersonas}
                onChange={(e) => updateForm('cantidadPersonas', e.target.value)}
                onBlur={() => touch('cantidadPersonas')}
                error={
                  fieldError(
                    'cantidadPersonas',
                    !form.cantidadPersonas ||
                      Number(form.cantidadPersonas) < 1 ||
                      Number(form.cantidadPersonas) > 99
                      ? { ok: false, mensaje: 'Debe ser entre 1 y 99 personas' }
                      : { ok: true }
                  ) || errors.cantidadPersonas
                }
                required
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Select
                id="medioTransporte"
                label="Medio de Transporte"
                value={form.medioTransporte}
                onChange={(e) => updateForm('medioTransporte', e.target.value)}
              >
                <option value="A_PIE">A pie (Peatonal)</option>
                <option value="CARRO">Carro / Automóvil</option>
                <option value="MOTO">Motocicleta</option>
                <option value="BICICLETA">Bicicleta</option>
                <option value="OTRO">Otro vehículo</option>
              </Select>

              {(form.medioTransporte === 'CARRO' || form.medioTransporte === 'MOTO') && (
                <Input
                  id="placa"
                  label={form.medioTransporte === 'MOTO' ? 'Placa (Moto - Opcional)' : 'Placa (Carro - Opcional)'}
                  placeholder={form.medioTransporte === 'MOTO' ? 'Ej. ABC12D' : 'Ej. DEM-123'}
                  maxLength="8"
                  value={form.placa}
                  onChange={(e) => updateForm('placa', e.target.value.toUpperCase())}
                  onBlur={() => touch('placa')}
                  error={
                    form.placa?.trim()
                      ? fieldError(
                          'placa',
                          valPlaca(
                            form.placa,
                            form.medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO'
                          )
                        ) || errors.placa
                      : undefined
                  }
                />
              )}

              {(form.medioTransporte === 'BICICLETA' || form.medioTransporte === 'OTRO') && (
                <Input
                  id="descripcion"
                  label={
                    form.medioTransporte === 'BICICLETA'
                      ? 'Descripción de la Bicicleta'
                      : 'Descripción del Medio'
                  }
                  placeholder={
                    form.medioTransporte === 'BICICLETA'
                      ? 'Ej. Bicicleta de montaña negra'
                      : 'Ej. Patineta eléctrica'
                  }
                  maxLength="100"
                  value={form.descripcion}
                  onChange={(e) => updateForm('descripcion', e.target.value)}
                  onBlur={() => touch('descripcion')}
                  error={
                    fieldError(
                      'descripcion',
                      form.descripcion.trim()
                        ? { ok: true }
                        : { ok: false, mensaje: 'La descripción es obligatoria' }
                    ) || errors.descripcion
                  }
                  required
                />
              )}
            </div>

            <Input
              id="motivo"
              label="Motivo o Notas de la Visita (opcional)"
              placeholder="Ej. Visita familiar / Almuerzo / Entrega de documento"
              value={form.motivo}
              onChange={(e) => updateForm('motivo', e.target.value)}
            />

            {/* Checkbox para guardar como frecuente */}
            <div className="pt-1 flex items-center gap-2">
              <input
                type="checkbox"
                id="guardarFrecuente"
                checked={form.guardarFrecuente}
                onChange={(e) => updateForm('guardarFrecuente', e.target.checked)}
                className="w-4 h-4 rounded border-border text-primary focus:ring-primary/20"
              />
              <label htmlFor="guardarFrecuente" className="text-xs text-foreground cursor-pointer font-medium">
                Guardar este visitante en mi lista de <strong>Visitantes Frecuentes</strong>
              </label>
            </div>
          </div>
        </div>
      </Modal>

      {/* 5. MODAL: PASE RÁPIDO PARA FRECUENTE */}
      <Modal
        open={!!frecuenteSeleccionado}
        onClose={() => !generandoRapido && setFrecuenteSeleccionado(null)}
        title={`Pase Rápido para ${frecuenteSeleccionado?.nombreVisitante || 'Visitante'}`}
        size="md"
        footer={
          <div className="flex items-center justify-end gap-3 w-full">
            <Button
              variant="outline"
              onClick={() => setFrecuenteSeleccionado(null)}
              disabled={generandoRapido}
            >
              Cancelar
            </Button>
            <Button
              onClick={generarPaseRapido}
              disabled={generandoRapido}
              className="gap-2 font-semibold bg-primary text-primary-foreground hover:bg-primary/90"
            >
              <QrCode className="w-4 h-4" />
              {generandoRapido ? 'Generando...' : 'Emitir Pase QR'}
            </Button>
          </div>
        }
      >
        {frecuenteSeleccionado && (
          <div className="space-y-4 py-1">
            <div className="p-3 rounded-lg border border-border bg-muted/30 text-xs flex items-center justify-between">
              <div>
                <p className="font-bold text-foreground">
                  {frecuenteSeleccionado.nombreVisitante}
                </p>
                <p className="text-muted-foreground font-mono">
                  Documento: {frecuenteSeleccionado.documento || '—'}
                </p>
              </div>
              <Badge variant="secondary" className="text-[10px]">
                Visitante de Confianza
              </Badge>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Input
                id="rapidoTiempo"
                label="Validez (minutos)"
                type="number"
                min="5"
                max="60"
                value={rapidoForm.tiempoValidezMin}
                onChange={(e) =>
                  setRapidoForm((f) => ({ ...f, tiempoValidezMin: e.target.value }))
                }
                error={rapidoErrors.tiempoValidezMin}
                required
              />
              <Input
                id="rapidoPersonas"
                label="Personas"
                type="number"
                min="1"
                max="99"
                value={rapidoForm.cantidadPersonas}
                onChange={(e) =>
                  setRapidoForm((f) => ({ ...f, cantidadPersonas: e.target.value }))
                }
                error={rapidoErrors.cantidadPersonas}
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Select
                id="rapidoMedio"
                label="Medio de Transporte"
                value={rapidoForm.medioTransporte}
                onChange={(e) =>
                  setRapidoForm((f) => ({ ...f, medioTransporte: e.target.value }))
                }
              >
                <option value="A_PIE">A pie (Peatonal)</option>
                <option value="CARRO">Carro</option>
                <option value="MOTO">Moto</option>
                <option value="BICICLETA">Bicicleta</option>
                <option value="OTRO">Otro</option>
              </Select>

              {(rapidoForm.medioTransporte === 'CARRO' ||
                rapidoForm.medioTransporte === 'MOTO') && (
                <Input
                  id="rapidoPlaca"
                  label="Placa"
                  placeholder="Ej. ABC123"
                  maxLength="8"
                  value={rapidoForm.placa}
                  onChange={(e) =>
                    setRapidoForm((f) => ({
                      ...f,
                      placa: e.target.value.toUpperCase(),
                    }))
                  }
                  error={rapidoErrors.placa}
                  required
                />
              )}
            </div>

            <Input
              id="rapidoNotas"
              label="Notas u observaciones (opcional)"
              placeholder="Ej. Almuerzo familiar"
              value={rapidoForm.notas}
              onChange={(e) =>
                setRapidoForm((f) => ({ ...f, notas: e.target.value }))
              }
            />
          </div>
        )}
      </Modal>

      {/* 6. MODAL DE ÉXITO: CÓDIGO QR RECIÉN GENERADO */}
      <Modal
        open={!!qrExito}
        onClose={() => setQrExito(null)}
        title="¡Pase QR Generado Exitosamente!"
        size="md"
        footer={
          <Button onClick={() => setQrExito(null)} className="w-full font-semibold">
            Listo, Entendido
          </Button>
        }
      >
        {qrExito && (
          <div className="flex flex-col items-center justify-center p-2 space-y-4 text-center">
            <div className="p-4 bg-white rounded-2xl border-2 border-primary/20 shadow-md flex flex-col items-center">
              <LocalQRCode
                value={qrExito.codigoQr}
                size={220}
                showDownload={true}
                downloadFileName={`saed-qr-${qrExito.codigoQr}.png`}
                alt={`Pase QR de ${qrExito.nombreVisitante || 'Visitante'}`}
              />
            </div>

            <div className="space-y-1">
              <h3 className="text-base font-bold text-foreground">
                {qrExito.nombreVisitante || 'Visitante Autorizado'}
              </h3>
              <p className="text-xs text-muted-foreground">
                Comparte este código con tu invitado para que lo presente en portería
              </p>
              <p className="text-xs font-mono font-bold text-primary bg-primary/10 py-1 px-3 rounded-full inline-block mt-1">
                #{qrExito.codigoQr}
              </p>
              <p className="text-xs text-amber-600 dark:text-amber-400 font-semibold mt-1">
                Válido durante {qrExito.tiempoValidezMin} minutos
              </p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-2 pt-2 border-t border-border w-full">
              <Button
                variant="outline"
                size="sm"
                onClick={() => compartirTelegram(qrExito.codigoQr, qrExito.nombreVisitante)}
                className="gap-1.5 text-xs text-blue-500"
              >
                <Send className="w-3.5 h-3.5" /> Telegram
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => compartirSMS(qrExito.codigoQr, '')}
                className="gap-1.5 text-xs text-emerald-500"
              >
                <Phone className="w-3.5 h-3.5" /> SMS
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => compartirCorreo(qrExito.codigoQr, qrExito.nombreVisitante, '')}
                className="gap-1.5 text-xs text-purple-500"
              >
                <Mail className="w-3.5 h-3.5" /> Correo
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => copiarQR(qrExito.codigoQr)}
                className="gap-1.5 text-xs"
              >
                <Copy className="w-3.5 h-3.5" /> Copiar
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 7. MODAL DE ZOOM DE QR DESDE LA LISTA */}
      <Modal
        open={!!qrZoom}
        onClose={() => setQrZoom(null)}
        title="Pase QR de Acceso"
        size="md"
        footer={
          <Button onClick={() => setQrZoom(null)} className="w-full">
            Cerrar
          </Button>
        }
      >
        {qrZoom && (
          <div className="flex flex-col items-center justify-center p-2 space-y-4 text-center">
            <div className="p-4 bg-white rounded-2xl border-2 border-primary/20 shadow-md flex flex-col items-center">
              <LocalQRCode
                value={qrZoom.codigoQr}
                size={220}
                showDownload={true}
                downloadFileName={`saed-qr-${qrZoom.codigoQr}.png`}
                alt={`Pase QR de ${qrZoom.nombreVisitante || 'Visitante'}`}
              />
            </div>
            <div>
              <h3 className="text-base font-bold text-foreground">
                {qrZoom.nombreVisitante || 'Visitante Autorizado'}
              </h3>
              <p className="text-xs font-mono text-muted-foreground mt-0.5">
                Código: #{qrZoom.codigoQr}
              </p>
              <p className="text-xs text-amber-600 dark:text-amber-400 font-semibold mt-1">
                Expira: {formatDateTime(qrZoom.fechaExpiracion)}
              </p>
            </div>
            <div className="flex items-center gap-2 pt-2">
              <Button
                variant="outline"
                onClick={() => copiarQR(qrZoom.codigoQr)}
                className="gap-1.5 text-xs"
              >
                <Copy className="w-3.5 h-3.5" /> Copiar Código
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 8. DIÁLOGO DE CONFIRMACIÓN PARA QUITAR FRECUENTE */}
      <ConfirmDialog
        open={!!confirmQuitar}
        onClose={() => setConfirmQuitar(null)}
        onConfirm={confirmarQuitarFrecuente}
        title="Quitar Visitante Frecuente"
        message={`¿Estás seguro de que deseas retirar a ${confirmQuitar?.nombreVisitante || 'este visitante'} de tu lista de frecuentes? Podrás volver a agregarlo en cualquier momento.`}
        confirmLabel="Sí, quitar"
        danger
      />

      {/* 9. MODAL: DETALLE COMPLETO DE VISITA (HISTORIAL) */}
      <Modal
        open={!!detalleVisita}
        onClose={() => setDetalleVisita(null)}
        title="Detalle de Visita"
        size="md"
      >
        {detalleVisita && (
          <div className="space-y-4 text-xs">
            <div className="p-3.5 rounded-xl bg-muted/40 border border-border flex items-start justify-between gap-3">
              <div>
                <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">
                  Visitante
                </p>
                <h3 className="text-base font-bold text-foreground">
                  {detalleVisita.nombreVisitante || 'Visitante Autorizado'}
                </h3>
                <p className="text-muted-foreground font-mono mt-0.5">
                  Documento: {detalleVisita.documentoVisitante || '—'}
                </p>
                {detalleVisita.telefonoVisitante && (
                  <p className="text-muted-foreground mt-0.5">
                    Tel: {detalleVisita.telefonoVisitante}
                  </p>
                )}
              </div>
              <Badge
                className={cn(
                  'text-[11px] font-semibold gap-1',
                  detalleVisita.efectuada === 'SI'
                    ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                    : 'bg-muted text-muted-foreground border-border'
                )}
              >
                {detalleVisita.efectuada === 'SI' ? (
                  <>
                    <CheckCircle2 className="w-3.5 h-3.5" /> Efectuada
                  </>
                ) : (
                  <>
                    <Clock className="w-3.5 h-3.5 text-amber-500" /> No efectuada
                  </>
                )}
              </Badge>
            </div>

            <div className="grid grid-cols-2 gap-3 p-3.5 rounded-xl border border-border bg-card">
              <div>
                <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                  Generado Por
                </span>
                <span className="font-semibold text-foreground">
                  {detalleVisita.generadoPor || 'Residente Titular'}
                </span>
                {detalleVisita.usernameGenerador && (
                  <span className="text-[10px] text-muted-foreground block font-mono">
                    @{detalleVisita.usernameGenerador}
                  </span>
                )}
              </div>

              <div>
                <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                  Unidad / Apto
                </span>
                <span className="font-semibold text-foreground">
                  {detalleVisita.identificadorUnidad ? `Apto ${detalleVisita.identificadorUnidad}` : 'Tu Unidad'}
                </span>
              </div>

              <div>
                <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                  Medio de Transporte
                </span>
                <span className="font-semibold text-foreground capitalize">
                  {detalleVisita.medioTransporte ? String(detalleVisita.medioTransporte).toLowerCase() : 'Peatonal'}
                </span>
                {detalleVisita.placa && (
                  <span className="font-mono text-primary font-bold block">
                    Placa: {detalleVisita.placa}
                  </span>
                )}
              </div>

              <div>
                <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                  Estado de la Visita
                </span>
                <span className="font-semibold text-foreground capitalize">
                  {detalleVisita.estadoVisita ? String(detalleVisita.estadoVisita).toLowerCase() : 'Registrada'}
                </span>
              </div>

              <div className="col-span-2 pt-2 border-t border-border/60">
                <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                  Fecha y Hora de Generación
                </span>
                <span className="text-foreground">
                  {detalleVisita.fechaGeneracion ? formatDateTime(detalleVisita.fechaGeneracion) : '—'}
                </span>
              </div>

              {detalleVisita.fechaIngreso && (
                <div>
                  <span className="text-[10px] uppercase font-bold text-emerald-600 dark:text-emerald-400 block">
                    Registro de Entrada
                  </span>
                  <span className="text-foreground font-medium">
                    {formatDateTime(detalleVisita.fechaIngreso)}
                  </span>
                </div>
              )}

              {detalleVisita.fechaSalida && (
                <div>
                  <span className="text-[10px] uppercase font-bold text-blue-600 dark:text-blue-400 block">
                    Registro de Salida
                  </span>
                  <span className="text-foreground font-medium">
                    {formatDateTime(detalleVisita.fechaSalida)}
                  </span>
                </div>
              )}

              {detalleVisita.motivo && (
                <div className="col-span-2 pt-2 border-t border-border/60">
                  <span className="text-[10px] uppercase font-bold text-muted-foreground block">
                    Motivo
                  </span>
                  <p className="text-muted-foreground mt-0.5 italic">
                    "{detalleVisita.motivo}"
                  </p>
                </div>
              )}
            </div>

            <div className="flex justify-end pt-2">
              <Button variant="outline" size="sm" onClick={() => setDetalleVisita(null)}>
                Cerrar
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
