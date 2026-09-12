import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import {
  Building2,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  ArrowLeft,
  Crown,
  Layers,
  Sparkles,
  CreditCard,
  Mail,
  Phone,
  User,
  FileText,
  MapPin,
  Lock,
  Loader2,
  ExternalLink,
  RefreshCw,
  UserCheck,
  Check,
} from 'lucide-react';
import api from '../lib/api.js';
import {
  DEPARTAMENTOS_COLOMBIA,
  COLOMBIA_LOCATIONS,
} from '../lib/colombiaData.js';
import {
  valDocumento,
  valTelefono,
  valEmail,
  valNombre,
  valApellido,
  soloNumeros,
  soloLetras,
  getDocPlaceholder,
  getDocHint,
} from '../lib/validation.js';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '../components/ui/card.tsx';
import { toast } from 'sonner';

const FALLBACK_PLANES = [
  {
    idPlan: 1,
    codigo: 'FREE',
    nombre: 'Prueba Gratuita',
    descripcion: 'Prueba la plataforma sin costo por 14 días.',
    precioMensual: 0,
    limitePropiedades: 1,
    limiteUnidades: 20,
    limiteUsuarios: 2,
    limiteAlmacenamientoGb: 5,
  },
  {
    idPlan: 2,
    codigo: 'PRO',
    nombre: 'Profesional',
    descripcion: 'Ideal para copropiedades y edificios residenciales medianos.',
    precioMensual: 149000,
    limitePropiedades: 5,
    limiteUnidades: 150,
    limiteUsuarios: 10,
    limiteAlmacenamientoGb: 50,
  },
  {
    idPlan: 3,
    codigo: 'ENTERPRISE',
    nombre: 'Corporativo',
    descripcion: 'Para empresas de administración inmobiliaria multisede.',
    precioMensual: 399000,
    limitePropiedades: 50,
    limiteUnidades: 1500,
    limiteUsuarios: 50,
    limiteAlmacenamientoGb: 500,
  },
];

export default function RegistroOrganizacionPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // URL Params pre-select
  const initialPlanCode = searchParams.get('plan') || 'PRO';
  const initialCycle = searchParams.get('cycle') || 'ANUAL';

  // Wizard state
  const [step, setStep] = useState(1);
  const [planes, setPlanes] = useState(FALLBACK_PLANES);
  const [loadingPlanes, setLoadingPlanes] = useState(false);

  // Selection
  const [selectedPlanId, setSelectedPlanId] = useState(2);
  const [billingCycle, setBillingCycle] = useState(initialCycle.toUpperCase() === 'MENSUAL' ? 'MENSUAL' : 'ANUAL');

  // Tipo de Registro: 'JURIDICA' (Empresa / Persona Jurídica) | 'NATURAL' (Persona Natural / Propietario Dueño de Edificio)
  const [tipoRegistro, setTipoRegistro] = useState('JURIDICA');
  const [esMismoAdmin, setEsMismoAdmin] = useState(true);

  // Form State - Organización / Inmueble
  const [orgForm, setOrgForm] = useState({
    tipoPersona: 'JURIDICA',
    nombreOrganizacion: '',
    nit: '',
    departamento: 'Bogotá D.C.',
    ciudad: 'Bogotá',
    direccion: '',
    telefonoContacto: '',
    emailContacto: '',
    // Campos de Persona Natural (Propietario / Dueño)
    tipoDocumentoDueno: 'CC',
    numeroDocumentoDueno: '',
    primerNombreDueno: '',
    segundoNombreDueno: '',
    primerApellidoDueno: '',
    segundoApellidoDueno: '',
  });

  const [adminForm, setAdminForm] = useState({
    primerNombre: '',
    segundoNombre: '',
    primerApellido: '',
    segundoApellido: '',
    tipoDocumento: 'CC',
    numeroDocumento: '',
    email: '',
    telefono: '',
    adminUsername: '',
  });

  // Touched state para Live Validation
  const [touched, setTouched] = useState({});
  const markTouched = (field) => setTouched((prev) => ({ ...prev, [field]: true }));

  // Submission & Payment State
  const [submitting, setSubmitting] = useState(false);
  const [registroResultado, setRegistroResultado] = useState(null);
  const [pagoAprobado, setPagoAprobado] = useState(false);
  const [pollingPago, setPollingPago] = useState(false);
  const pollingRef = useRef(null);

  // 1. Fetch available plans
  useEffect(() => {
    async function loadPlanes() {
      try {
        setLoadingPlanes(true);
        const res = await api.get('/auth/onboarding/planes');
        const data = res?.data || res || [];
        const catalog = Array.isArray(data) && data.length > 0 ? data : FALLBACK_PLANES;
        setPlanes(catalog);

        // Match initial plan
        if (catalog.length > 0) {
          const matched = catalog.find(
            (p) =>
              p.codigo?.toUpperCase() === initialPlanCode.toUpperCase() ||
              p.idPlan?.toString() === initialPlanCode
          );
          if (matched) {
            setSelectedPlanId(matched.idPlan);
          } else {
            const proPlan = catalog.find((p) => p.codigo === 'PRO');
            setSelectedPlanId(proPlan ? proPlan.idPlan : catalog[0].idPlan);
          }
        }
      } catch (err) {
        console.warn('Cargando catálogo base de planes:', err);
        setPlanes(FALLBACK_PLANES);
        const proPlan = FALLBACK_PLANES.find((p) => p.codigo === 'PRO');
        setSelectedPlanId(proPlan ? proPlan.idPlan : FALLBACK_PLANES[0].idPlan);
      } finally {
        setLoadingPlanes(false);
      }
    }
    loadPlanes();
  }, [initialPlanCode]);

  // Selected Plan Object
  const currentPlan = planes.find((p) => p.idPlan === selectedPlanId);

  // Price calculations
  const calculateTotal = () => {
    if (!currentPlan) return { totalPesos: 0, centavos: 0, mensual: 0, ahorro: 0 };
    const mensual = currentPlan.precioMensual || 0;
    if (mensual === 0) return { totalPesos: 0, centavos: 0, mensual: 0, ahorro: 0 };

    if (billingCycle === 'ANUAL') {
      const fullAnnual = mensual * 12;
      const totalPesos = Math.round(fullAnnual * 0.8); // 20% discount
      const ahorro = fullAnnual - totalPesos;
      return { totalPesos, centavos: totalPesos * 100, mensual, ahorro };
    }
    return { totalPesos: mensual, centavos: mensual * 100, mensual, ahorro: 0 };
  };

  const pricing = calculateTotal();

  // Poll for payment status if awaiting approval
  useEffect(() => {
    if (!pollingPago || !registroResultado?.referencia) return;

    pollingRef.current = setInterval(async () => {
      try {
        const res = await api.get(`/auth/onboarding/estado-pago?referencia=${encodeURIComponent(registroResultado.referencia)}`);
        const data = res?.data || res;
        if (data && (data.estadoPasarela === 'APROBADO' || data.estadoOrganizacion === 'ACTIVA')) {
          setPagoAprobado(true);
          setPollingPago(false);
          clearInterval(pollingRef.current);
          toast.success('¡Pago confirmado con éxito! Tu organización ha sido activada.');
        }
      } catch (e) {
        console.warn('Error consultando estado de transacción:', e);
      }
    }, 4000);

    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [pollingPago, registroResultado]);

  // Live Validation checks for Step 2 (Organización / Inmueble)
  const getOrgErrors = () => {
    const errs = {};
    if (tipoRegistro === 'JURIDICA') {
      if (!orgForm.nombreOrganizacion?.trim()) {
        errs.nombreOrganizacion = 'El nombre o razón social de la organización es obligatorio.';
      } else if (orgForm.nombreOrganizacion.trim().length < 3) {
        errs.nombreOrganizacion = 'Mínimo 3 caracteres.';
      }

      const vDoc = valDocumento(orgForm.nit, 'NIT', 'El NIT');
      if (!vDoc.ok) errs.nit = vDoc.mensaje;

      const vMail = valEmail(orgForm.emailContacto);
      if (!vMail.ok) errs.emailContacto = vMail.mensaje;

      const vTel = valTelefono(orgForm.telefonoContacto, { label: 'El teléfono corporativo' });
      if (!vTel.ok) errs.telefonoContacto = vTel.mensaje;
    } else {
      // Persona Natural (Dueño o propietario de edificio)
      if (!orgForm.nombreOrganizacion?.trim()) {
        errs.nombreOrganizacion = 'El nombre del edificio, inmueble o copropiedad es obligatorio.';
      } else if (orgForm.nombreOrganizacion.trim().length < 3) {
        errs.nombreOrganizacion = 'Mínimo 3 caracteres.';
      }

      const vNom1 = valNombre(orgForm.primerNombreDueno, 'El primer nombre');
      if (!vNom1.ok) errs.primerNombreDueno = vNom1.mensaje;

      if (orgForm.segundoNombreDueno?.trim()) {
        const vNom2 = valNombre(orgForm.segundoNombreDueno, 'El segundo nombre', { required: false });
        if (!vNom2.ok) errs.segundoNombreDueno = vNom2.mensaje;
      }

      const vApe1 = valApellido(orgForm.primerApellidoDueno, 'El primer apellido');
      if (!vApe1.ok) errs.primerApellidoDueno = vApe1.mensaje;

      if (orgForm.segundoApellidoDueno?.trim()) {
        const vApe2 = valApellido(orgForm.segundoApellidoDueno, 'El segundo apellido', { required: false });
        if (!vApe2.ok) errs.segundoApellidoDueno = vApe2.mensaje;
      }

      const vDoc = valDocumento(orgForm.numeroDocumentoDueno, orgForm.tipoDocumentoDueno, 'El documento de identidad');
      if (!vDoc.ok) errs.numeroDocumentoDueno = vDoc.mensaje;

      const vMail = valEmail(orgForm.emailContacto);
      if (!vMail.ok) errs.emailContacto = vMail.mensaje;

      const vTel = valTelefono(orgForm.telefonoContacto, { label: 'El número celular' });
      if (!vTel.ok) errs.telefonoContacto = vTel.mensaje;
    }

    if (!orgForm.departamento) errs.departamento = 'Selecciona un departamento de Colombia.';
    if (!orgForm.ciudad) errs.ciudad = 'Selecciona una ciudad o municipio.';

    return errs;
  };

  // Live Validation checks for Step 3 (Administrador Principal)
  const getAdminErrors = () => {
    const errs = {};
    const vNom1 = valNombre(adminForm.primerNombre, 'El primer nombre');
    if (!vNom1.ok) errs.primerNombre = vNom1.mensaje;

    if (adminForm.segundoNombre?.trim()) {
      const vNom2 = valNombre(adminForm.segundoNombre, 'El segundo nombre', { required: false });
      if (!vNom2.ok) errs.segundoNombre = vNom2.mensaje;
    }

    const vApe1 = valApellido(adminForm.primerApellido, 'El primer apellido');
    if (!vApe1.ok) errs.primerApellido = vApe1.mensaje;

    if (adminForm.segundoApellido?.trim()) {
      const vApe2 = valApellido(adminForm.segundoApellido, 'El segundo apellido', { required: false });
      if (!vApe2.ok) errs.segundoApellido = vApe2.mensaje;
    }

    const vDoc = valDocumento(adminForm.numeroDocumento, adminForm.tipoDocumento, 'El número de documento');
    if (!vDoc.ok) errs.numeroDocumento = vDoc.mensaje;

    if (!adminForm.adminUsername || adminForm.adminUsername.trim().length < 3) {
      errs.adminUsername = 'El usuario debe tener al menos 3 caracteres alfanuméricos.';
    } else if (!/^[a-z0-9._-]{3,30}$/.test(adminForm.adminUsername.trim())) {
      errs.adminUsername = 'Solo minúsculas, números, punto o guion.';
    }

    const vMail = valEmail(adminForm.email);
    if (!vMail.ok) errs.email = vMail.mensaje;

    const vTel = valTelefono(adminForm.telefono, { label: 'El celular' });
    if (!vTel.ok) errs.telefono = vTel.mensaje;

    return errs;
  };

  const orgErrors = getOrgErrors();
  const adminErrors = getAdminErrors();

  const renderFieldFeedback = (touchedKey, errorMsg, hintMsg) => {
    if (touched[touchedKey] && errorMsg) {
      return (
        <p className="text-[11px] text-rose-400 flex items-center gap-1 mt-1 animate-in fade-in duration-200 font-medium">
          <AlertCircle className="w-3 h-3 shrink-0" />
          <span>{errorMsg}</span>
        </p>
      );
    }
    if (hintMsg) {
      return <p className="text-[11px] text-slate-500 mt-1">{hintMsg}</p>;
    }
    return null;
  };

  const getInputClass = (touchedKey, errorMsg) => {
    const isErr = touched[touchedKey] && !!errorMsg;
    return `bg-slate-950 text-white rounded-xl text-xs py-3 transition-colors ${
      isErr
        ? 'border-rose-500 focus:border-rose-400 focus:ring-1 focus:ring-rose-500/30'
        : 'border-slate-800 focus:border-cyan-400'
    }`;
  };

  // Handler to advance from Step 2 to Step 3
  const handleAvanzarPaso2 = () => {
    const errs = getOrgErrors();
    if (Object.keys(errs).length > 0) {
      const allTouched = {
        nombreOrganizacion: true,
        nit: true,
        departamento: true,
        ciudad: true,
        emailContacto: true,
        telefonoContacto: true,
        tipoDocumentoDueno: true,
        numeroDocumentoDueno: true,
        primerNombreDueno: true,
        segundoNombreDueno: true,
        primerApellidoDueno: true,
        segundoApellidoDueno: true,
      };
      setTouched((prev) => ({ ...prev, ...allTouched }));
      const firstError = Object.values(errs)[0];
      toast.error(firstError);
      return;
    }

    // Auto-populate Step 3 if Persona Natural and esMismoAdmin
    if (tipoRegistro === 'NATURAL' && esMismoAdmin) {
      setAdminForm((prev) => {
        const pNom = orgForm.primerNombreDueno.trim();
        const pApe = orgForm.primerApellidoDueno.trim();
        const suggestedUser = prev.adminUsername || `${pNom}.${pApe}`.toLowerCase().replace(/[^a-z0-9._-]/g, '');
        return {
          ...prev,
          primerNombre: pNom,
          segundoNombre: (orgForm.segundoNombreDueno || '').trim(),
          primerApellido: pApe,
          segundoApellido: (orgForm.segundoApellidoDueno || '').trim(),
          tipoDocumento: orgForm.tipoDocumentoDueno || 'CC',
          numeroDocumento: orgForm.numeroDocumentoDueno.trim(),
          email: orgForm.emailContacto.trim(),
          telefono: orgForm.telefonoContacto.trim(),
          adminUsername: suggestedUser,
        };
      });
    }

    setStep(3);
  };

  // Submit Handler
  const handleSubmitRegistro = async () => {
    const orgErrs = getOrgErrors();
    const adminErrs = getAdminErrors();

    if (Object.keys(orgErrs).length > 0) {
      toast.error('Por favor revisa los datos de la organización o inmueble.');
      setStep(2);
      return;
    }
    if (Object.keys(adminErrs).length > 0) {
      const allAdminTouched = {
        primerNombre: true,
        segundoNombre: true,
        primerApellido: true,
        segundoApellido: true,
        tipoDocumento: true,
        numeroDocumento: true,
        adminUsername: true,
        email: true,
        telefono: true,
      };
      setTouched((prev) => ({ ...prev, ...allAdminTouched }));
      const firstError = Object.values(adminErrs)[0];
      toast.error(firstError);
      return;
    }

    try {
      setSubmitting(true);
      const isNatural = tipoRegistro === 'NATURAL';
      const cleanNit = isNatural ? orgForm.numeroDocumentoDueno.trim() : orgForm.nit.trim();
      const payload = {
        tipoPersona: tipoRegistro,
        nombreOrganizacion: orgForm.nombreOrganizacion.trim(),
        nit: cleanNit,
        departamento: orgForm.departamento,
        ciudad: orgForm.ciudad.trim(),
        direccion: orgForm.direccion.trim() || 'No especificada',
        telefonoContacto: orgForm.telefonoContacto.trim() || adminForm.telefono.trim(),
        emailContacto: orgForm.emailContacto.trim(),
        adminPrimerNombre: adminForm.primerNombre.trim(),
        adminSegundoNombre: adminForm.segundoNombre?.trim() || null,
        adminPrimerApellido: adminForm.primerApellido.trim(),
        adminSegundoApellido: adminForm.segundoApellido?.trim() || null,
        adminTipoDocumento: adminForm.tipoDocumento,
        adminNumeroDocumento: adminForm.numeroDocumento.trim(),
        adminUsername: adminForm.adminUsername.trim().toLowerCase(),
        adminEmail: adminForm.email.trim().toLowerCase(),
        adminTelefono: adminForm.telefono.trim(),
        idPlan: selectedPlanId,
        cicloFacturacion: billingCycle,
      };

      const res = await api.post('/auth/onboarding/registro', payload);
      const data = res?.data || res;

      setRegistroResultado(data);
      setStep(4); // Pantalla de resultado/pago

      if (data.requierePago) {
        setPollingPago(true);
        // Abrir Wompi si el script está listo
        lanzarWidgetWompi(data);
      } else {
        toast.success('¡Registro exitoso! Correo de activación despachado.');
      }
    } catch (err) {
      console.error('Error registrando organización:', err);
      toast.error(err.message || 'Error al procesar el registro. Verifica los datos e intenta de nuevo.');
    } finally {
      setSubmitting(false);
    }
  };

  // Launch Wompi Checkout Widget
  const lanzarWidgetWompi = (txData) => {
    try {
      if (typeof window.WidgetCheckout !== 'undefined') {
        const checkout = new window.WidgetCheckout({
          currency: txData.moneda || 'COP',
          amountInCents: txData.montoCentavos,
          reference: txData.referencia,
          publicKey: txData.wompiPublicKey,
          signature: { integrity: txData.firmaIntegridad },
          customerData: {
            email: txData.adminEmail,
            fullName: `${adminForm.primerNombre} ${adminForm.primerApellido}`,
            phoneNumber: adminForm.telefono,
          },
        });

        checkout.open((result) => {
          const transaction = result?.transaction;
          if (transaction && transaction.status === 'APPROVED') {
            setPagoAprobado(true);
            setPollingPago(false);
            toast.success('¡Transacción aprobada!');
          }
        });
      } else {
        // Cargar dinámicamente el script de Wompi si no estaba presente
        const script = document.createElement('script');
        script.src = 'https://checkout.wompi.co/widget.js';
        script.async = true;
        script.onload = () => {
          if (typeof window.WidgetCheckout !== 'undefined') {
            lanzarWidgetWompi(txData);
          }
        };
        document.body.appendChild(script);
      }
    } catch (e) {
      console.warn('Error inicializando widget Wompi:', e);
    }
  };

  return (
    <div className="min-h-screen bg-[#070B14] text-slate-100 flex flex-col selection:bg-cyan-500/30 selection:text-cyan-200">
      {/* Header Minimalista */}
      <header className="sticky top-0 z-50 bg-[#070B14]/90 backdrop-blur-xl border-b border-slate-800/80 shadow-xl py-4">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3 group focus:outline-none">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 via-sky-400 to-blue-600 flex items-center justify-center text-slate-950 font-black shadow-lg shadow-sky-500/20">
              <Building2 className="w-5 h-5 text-slate-950" />
            </div>
            <div>
              <span className="text-lg font-extrabold tracking-tight font-['Plus_Jakarta_Sans'] text-white">
                SAED <span className="text-cyan-400 text-sm font-semibold">2.0</span>
              </span>
              <p className="text-[10px] text-slate-400 font-mono tracking-widest uppercase">
                SaaS Onboarding
              </p>
            </div>
          </Link>

          <div className="flex items-center gap-4">
            <span className="text-xs text-slate-400 hidden sm:inline">
              ¿Ya tienes cuenta activa?
            </span>
            <Link
              to="/login"
              className="text-xs font-semibold text-cyan-400 hover:text-cyan-300 transition-colors py-1.5 px-3 rounded-lg border border-cyan-500/30 hover:border-cyan-500/60"
            >
              Iniciar Sesión
            </Link>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-4 sm:px-6 py-8 sm:py-12">
        {/* Wizard Steps Progress Bar */}
        <div className="mb-10">
          <div className="flex items-center justify-between max-w-2xl mx-auto relative">
            <div className="absolute left-0 top-1/2 -translate-y-1/2 h-0.5 bg-slate-800 w-full z-0" />
            <div
              className="absolute left-0 top-1/2 -translate-y-1/2 h-0.5 bg-gradient-to-r from-cyan-500 to-blue-500 transition-all duration-300 z-0"
              style={{ width: `${((step - 1) / 3) * 100}%` }}
            />

            {[
              { num: 1, label: 'Plan & Ciclo' },
              { num: 2, label: 'Organización' },
              { num: 3, label: 'Administrador' },
              { num: 4, label: 'Activación' },
            ].map((s) => (
              <div key={s.num} className="relative z-10 flex flex-col items-center gap-1.5">
                <div
                  className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-xs transition-all ${
                    step > s.num
                      ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/30'
                      : step === s.num
                      ? 'bg-slate-900 border-2 border-cyan-400 text-cyan-400 shadow-md shadow-cyan-500/20'
                      : 'bg-slate-900 border border-slate-700 text-slate-500'
                  }`}
                >
                  {step > s.num ? <CheckCircle2 className="w-5 h-5" /> : s.num}
                </div>
                <span
                  className={`text-[11px] font-medium transition-colors ${
                    step >= s.num ? 'text-slate-200 font-semibold' : 'text-slate-500'
                  }`}
                >
                  {s.label}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* ========================================================================= */}
        {/* PASO 1: Selección de Plan y Ciclo */}
        {/* ========================================================================= */}
        {step === 1 && (
          <div className="space-y-8 animate-in fade-in duration-300">
            <div className="text-center max-w-xl mx-auto space-y-2">
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white font-['Plus_Jakarta_Sans']">
                Elige la escala para tu organización
              </h1>
              <p className="text-sm text-slate-400">
                Acceso inmediato, multi-propiedad y control total de accesos y finanzas.
              </p>
            </div>

            {/* Toggle Ciclo de Facturación */}
            <div className="flex items-center justify-center">
              <div className="p-1.5 rounded-2xl bg-slate-900 border border-slate-800 flex items-center gap-2 shadow-inner">
                <button
                  type="button"
                  onClick={() => setBillingCycle('MENSUAL')}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                    billingCycle === 'MENSUAL'
                      ? 'bg-cyan-500 text-slate-950 shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Facturación Mensual
                </button>
                <button
                  type="button"
                  onClick={() => setBillingCycle('ANUAL')}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 ${
                    billingCycle === 'ANUAL'
                      ? 'bg-gradient-to-r from-cyan-400 to-sky-500 text-slate-950 shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  <span>Facturación Anual</span>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                    -20% DCTO
                  </span>
                </button>
              </div>
            </div>

            {/* Grid de Planes */}
            {loadingPlanes ? (
              <div className="flex flex-col items-center justify-center py-16 text-slate-400 gap-3">
                <Loader2 className="w-8 h-8 animate-spin text-cyan-400" />
                <span className="text-xs">Cargando catálogo oficial de planes...</span>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {planes.map((plan) => {
                  const isSelected = selectedPlanId === plan.idPlan;
                  const isCommercial = plan.precioMensual > 0;
                  const displayRate = isCommercial
                    ? billingCycle === 'ANUAL'
                      ? Math.round(plan.precioMensual * 0.8)
                      : plan.precioMensual
                    : 0;

                  return (
                    <div
                      key={plan.idPlan}
                      onClick={() => setSelectedPlanId(plan.idPlan)}
                      className={`relative rounded-3xl p-6 transition-all cursor-pointer border flex flex-col justify-between ${
                        isSelected
                          ? 'bg-slate-900/90 border-cyan-400 ring-2 ring-cyan-400/40 shadow-2xl shadow-cyan-950/40'
                          : 'bg-slate-900/40 border-slate-800 hover:border-slate-700 hover:bg-slate-900/60'
                      }`}
                    >
                      {plan.codigo === 'PRO' && (
                        <div className="absolute -top-3 right-6 px-3 py-1 rounded-full bg-cyan-500 text-slate-950 text-[10px] font-black uppercase tracking-wider shadow-md">
                          Recomendado
                        </div>
                      )}

                      <div className="space-y-4">
                        <div className="flex items-center justify-between">
                          <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center text-cyan-400">
                            {plan.codigo === 'ENTERPRISE' ? (
                              <Crown className="w-5 h-5" />
                            ) : plan.codigo === 'PRO' ? (
                              <Layers className="w-5 h-5" />
                            ) : (
                              <Building2 className="w-5 h-5" />
                            )}
                          </div>
                          <span className="text-xs font-mono font-bold text-slate-400 uppercase">
                            {plan.codigo}
                          </span>
                        </div>

                        <div>
                          <h3 className="text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                            {plan.nombre}
                          </h3>
                          <p className="text-xs text-slate-400 mt-1 min-h-[36px]">
                            {plan.descripcion}
                          </p>
                        </div>

                        {/* Price Tag */}
                        <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-slate-800/80">
                          {isCommercial ? (
                            <div>
                              <div className="flex items-baseline gap-1">
                                <span className="text-2xl font-black font-mono text-white">
                                  ${displayRate.toLocaleString('es-CO')}
                                </span>
                                <span className="text-[11px] text-slate-400">/ mes</span>
                              </div>
                              {billingCycle === 'ANUAL' && (
                                <p className="text-[10px] text-emerald-400 font-medium mt-1">
                                  Cobro anual de ${(displayRate * 12).toLocaleString('es-CO')} COP
                                </p>
                              )}
                            </div>
                          ) : (
                            <div>
                              <span className="text-2xl font-black font-mono text-emerald-400">
                                14 Días Gratis
                              </span>
                              <p className="text-[10px] text-slate-400 mt-1">
                                Sin tarjeta de crédito requerida
                              </p>
                            </div>
                          )}
                        </div>

                        {/* Límites */}
                        <div className="space-y-2 text-xs text-slate-300 pt-2 border-t border-slate-800">
                          <div className="flex justify-between">
                            <span className="text-slate-400">Propiedades:</span>
                            <span className="font-semibold text-white font-mono">
                              Hasta {plan.limitePropiedades}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-slate-400">Unidades:</span>
                            <span className="font-semibold text-white font-mono">
                              Hasta {plan.limiteUnidades}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-slate-400">Usuarios Admin:</span>
                            <span className="font-semibold text-white font-mono">
                              Hasta {plan.limiteUsuarios}
                            </span>
                          </div>
                        </div>
                      </div>

                      <div className="mt-6 pt-4 border-t border-slate-800/60 flex items-center justify-between">
                        <span className="text-xs font-semibold text-cyan-400">
                          {isSelected ? 'Plan Seleccionado' : 'Hacer clic para elegir'}
                        </span>
                        <div
                          className={`w-5 h-5 rounded-full flex items-center justify-center border ${
                            isSelected
                              ? 'bg-cyan-400 border-cyan-400 text-slate-950'
                              : 'border-slate-700'
                          }`}
                        >
                          {isSelected && <CheckCircle2 className="w-3.5 h-3.5" />}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="flex justify-end pt-4">
              <Button
                onClick={() => setStep(2)}
                disabled={!selectedPlanId}
                className="bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold px-8 py-3 rounded-xl flex items-center gap-2 shadow-lg shadow-cyan-500/20"
              >
                <span>Continuar a Datos de la Organización</span>
                <ArrowRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* PASO 2: Datos de la Empresa o Copropiedad */}
        {/* ========================================================================= */}
        {step === 2 && (
          <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in duration-300">
            <div className="space-y-1">
              <button
                type="button"
                onClick={() => setStep(1)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5 mb-2 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Volver a Planes</span>
              </button>
              <h2 className="text-2xl font-black text-white font-['Plus_Jakarta_Sans']">
                Datos de la Empresa o Copropiedad
              </h2>
              <p className="text-xs text-slate-400">
                Selecciona la modalidad de registro e ingresa la información correspondiente según la normativa colombiana.
              </p>
            </div>

            {/* Selector Dual: Empresa (Jurídica) vs Persona Natural (Dueño / Propietario) */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => {
                  setTipoRegistro('JURIDICA');
                  setOrgForm((prev) => ({ ...prev, tipoPersona: 'JURIDICA' }));
                }}
                className={`p-4 rounded-2xl border text-left transition-all flex items-start gap-3.5 ${
                  tipoRegistro === 'JURIDICA'
                    ? 'bg-cyan-950/40 border-cyan-400 ring-1 ring-cyan-400/40 shadow-lg shadow-cyan-950/30'
                    : 'bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-400'
                }`}
              >
                <div
                  className={`p-2.5 rounded-xl ${
                    tipoRegistro === 'JURIDICA' ? 'bg-cyan-500 text-slate-950' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  <Building2 className="w-5 h-5" />
                </div>
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <span className={`text-xs font-bold ${tipoRegistro === 'JURIDICA' ? 'text-white' : 'text-slate-300'}`}>
                      Empresa / Persona Jurídica
                    </span>
                    {tipoRegistro === 'JURIDICA' && (
                      <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-snug">
                    Inmobiliaria, Constructora o Administración PH con NIT.
                  </p>
                </div>
              </button>

              <button
                type="button"
                onClick={() => {
                  setTipoRegistro('NATURAL');
                  setOrgForm((prev) => ({ ...prev, tipoPersona: 'NATURAL' }));
                }}
                className={`p-4 rounded-2xl border text-left transition-all flex items-start gap-3.5 ${
                  tipoRegistro === 'NATURAL'
                    ? 'bg-cyan-950/40 border-cyan-400 ring-1 ring-cyan-400/40 shadow-lg shadow-cyan-950/30'
                    : 'bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-400'
                }`}
              >
                <div
                  className={`p-2.5 rounded-xl ${
                    tipoRegistro === 'NATURAL' ? 'bg-cyan-500 text-slate-950' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  <User className="w-5 h-5" />
                </div>
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <span className={`text-xs font-bold ${tipoRegistro === 'NATURAL' ? 'text-white' : 'text-slate-300'}`}>
                      Persona Natural (Propietario / Dueño)
                    </span>
                    {tipoRegistro === 'NATURAL' && (
                      <span className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-snug">
                    Propietario particular o titular de edificio / copropiedad.
                  </p>
                </div>
              </button>
            </div>

            <Card className="bg-slate-900/80 border-slate-800 rounded-3xl p-6 space-y-4 shadow-xl">
              {/* Formulario Modalidad Empresa / Persona Jurídica */}
              {tipoRegistro === 'JURIDICA' ? (
                <div className="space-y-4 animate-in fade-in duration-200">
                  <div className="space-y-1.5">
                    <Label htmlFor="nombreOrg" className="text-xs text-slate-300 font-semibold">
                      Nombre de la Organización / Razón Social *
                    </Label>
                    <Input
                      id="nombreOrg"
                      placeholder="ej. Inversiones Inmobiliarias del Norte S.A.S."
                      maxLength={80}
                      value={orgForm.nombreOrganizacion}
                      onChange={(e) => {
                        setOrgForm({ ...orgForm, nombreOrganizacion: e.target.value.slice(0, 80) });
                        markTouched('nombreOrganizacion');
                      }}
                      onBlur={() => markTouched('nombreOrganizacion')}
                      className={getInputClass('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                    />
                    {renderFieldFeedback(
                      'nombreOrganizacion',
                      orgErrors.nombreOrganizacion,
                      'Razón social completa inscrita en Cámara de Comercio'
                    )}
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="nit" className="text-xs text-slate-300 font-semibold">
                        NIT / Identificación Tributaria *
                      </Label>
                      <Input
                        id="nit"
                        placeholder="ej. 901234567-8"
                        maxLength={15}
                        value={orgForm.nit}
                        onChange={(e) => {
                          const val = e.target.value.replace(/[^0-9-]/g, '').slice(0, 15);
                          setOrgForm({ ...orgForm, nit: val });
                          markTouched('nit');
                        }}
                        onBlur={() => markTouched('nit')}
                        className={getInputClass('nit', orgErrors.nit)}
                      />
                      {renderFieldFeedback('nit', orgErrors.nit, '8 a 10 dígitos, opcional guion y dígito de verificación')}
                    </div>

                    <div className="space-y-1.5">
                      <Label htmlFor="emailContacto" className="text-xs text-slate-300 font-semibold">
                        Correo Electrónico Corporativo *
                      </Label>
                      <Input
                        id="emailContacto"
                        type="email"
                        maxLength={60}
                        placeholder="admin@tuempresa.com"
                        value={orgForm.emailContacto}
                        onChange={(e) => {
                          setOrgForm({ ...orgForm, emailContacto: e.target.value.slice(0, 60) });
                          markTouched('emailContacto');
                        }}
                        onBlur={() => markTouched('emailContacto')}
                        className={getInputClass('emailContacto', orgErrors.emailContacto)}
                      />
                      {renderFieldFeedback('emailContacto', orgErrors.emailContacto, 'Correo corporativo para facturación y notificaciones')}
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="telContacto" className="text-xs text-slate-300 font-semibold">
                      Teléfono Corporativo / Celular (10 dígitos) *
                    </Label>
                    <Input
                      id="telContacto"
                      placeholder="ej. 6013004000"
                      maxLength={10}
                      value={orgForm.telefonoContacto}
                      onChange={(e) => {
                        const val = soloNumeros(e.target.value, 10);
                        setOrgForm({ ...orgForm, telefonoContacto: val });
                        markTouched('telefonoContacto');
                      }}
                      onBlur={() => markTouched('telefonoContacto')}
                      className={getInputClass('telefonoContacto', orgErrors.telefonoContacto)}
                    />
                    {renderFieldFeedback('telefonoContacto', orgErrors.telefonoContacto, 'Número colombiano de 10 dígitos')}
                  </div>
                </div>
              ) : (
                /* Formulario Modalidad Persona Natural (Dueño / Propietario) */
                <div className="space-y-4 animate-in fade-in duration-200">
                  <div className="space-y-1.5">
                    <Label htmlFor="nombreEdificio" className="text-xs text-slate-300 font-semibold">
                      Nombre del Edificio / Inmueble / Copropiedad *
                    </Label>
                    <Input
                      id="nombreEdificio"
                      placeholder="ej. Inversiones Inmobiliarias del Norte o Edificio Las Palmas"
                      maxLength={80}
                      value={orgForm.nombreOrganizacion}
                      onChange={(e) => {
                        setOrgForm({ ...orgForm, nombreOrganizacion: e.target.value.slice(0, 80) });
                        markTouched('nombreOrganizacion');
                      }}
                      onBlur={() => markTouched('nombreOrganizacion')}
                      className={getInputClass('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                    />
                    {renderFieldFeedback(
                      'nombreOrganizacion',
                      orgErrors.nombreOrganizacion,
                      'Nombre comercial o identificador de la propiedad de rentas'
                    )}
                  </div>

                  <div className="p-3.5 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
                    <span className="text-xs font-bold text-cyan-400 flex items-center gap-1.5">
                      <User className="w-3.5 h-3.5" />
                      Datos Personales del Propietario
                    </span>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="pNombreDueno" className="text-[11px] text-slate-300 font-semibold">
                          Primer Nombre *
                        </Label>
                        <Input
                          id="pNombreDueno"
                          placeholder="ej. Carlos"
                          maxLength={25}
                          value={orgForm.primerNombreDueno}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, primerNombreDueno: soloLetras(e.target.value, 25) });
                            markTouched('primerNombreDueno');
                          }}
                          onBlur={() => markTouched('primerNombreDueno')}
                          className={getInputClass('primerNombreDueno', orgErrors.primerNombreDueno)}
                        />
                        {renderFieldFeedback('primerNombreDueno', orgErrors.primerNombreDueno, 'Máx. 25 caracteres, solo letras')}
                      </div>

                      <div className="space-y-1">
                        <Label htmlFor="sNombreDueno" className="text-[11px] text-slate-300 font-semibold">
                          Segundo Nombre
                        </Label>
                        <Input
                          id="sNombreDueno"
                          placeholder="ej. Andrés"
                          maxLength={25}
                          value={orgForm.segundoNombreDueno}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, segundoNombreDueno: soloLetras(e.target.value, 25) });
                            markTouched('segundoNombreDueno');
                          }}
                          onBlur={() => markTouched('segundoNombreDueno')}
                          className={getInputClass('segundoNombreDueno', orgErrors.segundoNombreDueno)}
                        />
                        {renderFieldFeedback('segundoNombreDueno', orgErrors.segundoNombreDueno, 'Opcional, máx. 25 caracteres')}
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="pApellidoDueno" className="text-[11px] text-slate-300 font-semibold">
                          Primer Apellido *
                        </Label>
                        <Input
                          id="pApellidoDueno"
                          placeholder="ej. Rodríguez"
                          maxLength={25}
                          value={orgForm.primerApellidoDueno}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, primerApellidoDueno: soloLetras(e.target.value, 25) });
                            markTouched('primerApellidoDueno');
                          }}
                          onBlur={() => markTouched('primerApellidoDueno')}
                          className={getInputClass('primerApellidoDueno', orgErrors.primerApellidoDueno)}
                        />
                        {renderFieldFeedback('primerApellidoDueno', orgErrors.primerApellidoDueno, 'Máx. 25 caracteres, solo letras')}
                      </div>

                      <div className="space-y-1">
                        <Label htmlFor="sApellidoDueno" className="text-[11px] text-slate-300 font-semibold">
                          Segundo Apellido
                        </Label>
                        <Input
                          id="sApellidoDueno"
                          placeholder="ej. Pérez"
                          maxLength={25}
                          value={orgForm.segundoApellidoDueno}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, segundoApellidoDueno: soloLetras(e.target.value, 25) });
                            markTouched('segundoApellidoDueno');
                          }}
                          onBlur={() => markTouched('segundoApellidoDueno')}
                          className={getInputClass('segundoApellidoDueno', orgErrors.segundoApellidoDueno)}
                        />
                        {renderFieldFeedback('segundoApellidoDueno', orgErrors.segundoApellidoDueno, 'Opcional, máx. 25 caracteres')}
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="tipoDocDueno" className="text-[11px] text-slate-300 font-semibold">
                          Tipo Documento *
                        </Label>
                        <select
                          id="tipoDocDueno"
                          value={orgForm.tipoDocumentoDueno}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, tipoDocumentoDueno: e.target.value });
                            markTouched('numeroDocumentoDueno');
                          }}
                          className="w-full bg-slate-950 border border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 px-3 outline-none"
                        >
                          <option value="CC">C.C. Cédula Ciudadanía</option>
                          <option value="CE">C.E. Cédula Extranjería</option>
                          <option value="PAS">Pasaporte</option>
                          <option value="PPT">Permiso Protección Temporal</option>
                          <option value="NIT">NIT Persona Natural</option>
                        </select>
                      </div>

                      <div className="space-y-1 sm:col-span-2">
                        <Label htmlFor="numDocDueno" className="text-[11px] text-slate-300 font-semibold">
                          Número de Documento *
                        </Label>
                        <Input
                          id="numDocDueno"
                          placeholder={getDocPlaceholder(orgForm.tipoDocumentoDueno)}
                          value={orgForm.numeroDocumentoDueno}
                          onChange={(e) => {
                            let val = e.target.value;
                            if (['CC', 'TI', 'RC'].includes(orgForm.tipoDocumentoDueno)) {
                              val = soloNumeros(val, 10);
                            } else if (orgForm.tipoDocumentoDueno === 'NIT') {
                              val = val.replace(/[^0-9-]/g, '').slice(0, 15);
                            } else {
                              val = val.replace(/[^A-Za-z0-9]/g, '').slice(0, 16);
                            }
                            setOrgForm({ ...orgForm, numeroDocumentoDueno: val });
                            markTouched('numeroDocumentoDueno');
                          }}
                          onBlur={() => markTouched('numeroDocumentoDueno')}
                          className={getInputClass('numeroDocumentoDueno', orgErrors.numeroDocumentoDueno)}
                        />
                        {renderFieldFeedback(
                          'numeroDocumentoDueno',
                          orgErrors.numeroDocumentoDueno,
                          getDocHint(orgForm.tipoDocumentoDueno)
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <Label htmlFor="emailDueno" className="text-[11px] text-slate-300 font-semibold">
                          Correo Electrónico *
                        </Label>
                        <Input
                          id="emailDueno"
                          type="email"
                          maxLength={60}
                          placeholder="propietario@correo.com"
                          value={orgForm.emailContacto}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, emailContacto: e.target.value.slice(0, 60) });
                            markTouched('emailContacto');
                          }}
                          onBlur={() => markTouched('emailContacto')}
                          className={getInputClass('emailContacto', orgErrors.emailContacto)}
                        />
                        {renderFieldFeedback('emailContacto', orgErrors.emailContacto, 'Correo personal para recepción de notificaciones')}
                      </div>

                      <div className="space-y-1">
                        <Label htmlFor="telDueno" className="text-[11px] text-slate-300 font-semibold">
                          Teléfono Celular (10 dígitos) *
                        </Label>
                        <Input
                          id="telDueno"
                          placeholder="ej. 3101234567"
                          maxLength={10}
                          value={orgForm.telefonoContacto}
                          onChange={(e) => {
                            const val = soloNumeros(e.target.value, 10);
                            setOrgForm({ ...orgForm, telefonoContacto: val });
                            markTouched('telefonoContacto');
                          }}
                          onBlur={() => markTouched('telefonoContacto')}
                          className={getInputClass('telefonoContacto', orgErrors.telefonoContacto)}
                        />
                        {renderFieldFeedback('telefonoContacto', orgErrors.telefonoContacto, 'Exactamente 10 dígitos')}
                      </div>
                    </div>
                  </div>

                  {/* Checkbox "Soy el administrador principal" */}
                  <div className="p-3.5 rounded-2xl bg-cyan-950/30 border border-cyan-800/40 flex items-start gap-3 transition-colors">
                    <input
                      type="checkbox"
                      id="esMismoAdmin"
                      checked={esMismoAdmin}
                      onChange={(e) => setEsMismoAdmin(e.target.checked)}
                      className="mt-0.5 h-4 w-4 rounded border-slate-700 bg-slate-950 text-cyan-500 focus:ring-cyan-400 cursor-pointer"
                    />
                    <label htmlFor="esMismoAdmin" className="text-xs cursor-pointer select-none">
                      <span className="font-semibold text-white">Soy el administrador principal de la cuenta</span>
                      <p className="text-[11px] text-slate-400 mt-0.5">
                        Tus datos se transferirán automáticamente al siguiente paso para que solo debas definir tu usuario único de acceso.
                      </p>
                    </label>
                  </div>
                </div>
              )}

              {/* Ubicación Territorial Colombiana en Cascada (Común a ambos) */}
              <div className="pt-2 border-t border-slate-800 space-y-3">
                <span className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-cyan-400" />
                  Ubicación Territorial (Colombia)
                </span>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="departamento" className="text-xs text-slate-300 font-semibold">
                      Departamento *
                    </Label>
                    <select
                      id="departamento"
                      value={orgForm.departamento}
                      onChange={(e) => {
                        const newDep = e.target.value;
                        const availableCities = COLOMBIA_LOCATIONS[newDep] || [];
                        setOrgForm({
                          ...orgForm,
                          departamento: newDep,
                          ciudad: availableCities.length > 0 ? availableCities[0] : '',
                        });
                        markTouched('departamento');
                      }}
                      onBlur={() => markTouched('departamento')}
                      className="w-full bg-slate-950 border border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 px-3 outline-none transition-colors"
                    >
                      {DEPARTAMENTOS_COLOMBIA.map((dep) => (
                        <option key={dep} value={dep}>
                          {dep}
                        </option>
                      ))}
                    </select>
                    {renderFieldFeedback('departamento', orgErrors.departamento, '32 departamentos + Bogotá D.C.')}
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="ciudad" className="text-xs text-slate-300 font-semibold">
                      Ciudad / Municipio *
                    </Label>
                    <select
                      id="ciudad"
                      value={orgForm.ciudad}
                      onChange={(e) => {
                        setOrgForm({ ...orgForm, ciudad: e.target.value });
                        markTouched('ciudad');
                      }}
                      onBlur={() => markTouched('ciudad')}
                      className="w-full bg-slate-950 border border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 px-3 outline-none transition-colors"
                    >
                      {(COLOMBIA_LOCATIONS[orgForm.departamento] || []).map((city) => (
                        <option key={city} value={city}>
                          {city}
                        </option>
                      ))}
                    </select>
                    {renderFieldFeedback('ciudad', orgErrors.ciudad, 'Municipio oficial según departamento')}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="direccion" className="text-xs text-slate-300 font-semibold">
                    Dirección {tipoRegistro === 'JURIDICA' ? 'Comercial' : 'del Inmueble / Edificio'}
                  </Label>
                  <Input
                    id="direccion"
                    placeholder="ej. Calle 100 # 15-20 Of. 501"
                    maxLength={150}
                    value={orgForm.direccion}
                    onChange={(e) => setOrgForm({ ...orgForm, direccion: e.target.value.slice(0, 150) })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                  <p className="text-[11px] text-slate-500">Opcional para facturación física o ubicación del inmueble</p>
                </div>
              </div>
            </Card>

            <div className="flex justify-between pt-2">
              <Button
                variant="outline"
                onClick={() => setStep(1)}
                className="border-slate-800 text-slate-300 hover:bg-slate-800 rounded-xl text-xs"
              >
                Volver
              </Button>
              <Button
                onClick={handleAvanzarPaso2}
                className="bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold px-6 py-2.5 rounded-xl flex items-center gap-2 shadow-lg shadow-cyan-500/20 text-xs"
              >
                <span>Continuar a Administrador</span>
                <ArrowRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* PASO 3: Datos del Administrador Principal */}
        {/* ========================================================================= */}
        {step === 3 && (
          <div className="max-w-2xl mx-auto space-y-6 animate-in fade-in duration-300">
            <div className="space-y-1">
              <button
                type="button"
                onClick={() => setStep(2)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5 mb-2 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Volver a Organización</span>
              </button>
              <h2 className="text-2xl font-black text-white font-['Plus_Jakarta_Sans']">
                Administrador Principal de la Cuenta
              </h2>
              <p className="text-xs text-slate-400">
                Esta persona tendrá el rol de <strong className="text-cyan-400">ADMIN_ORGANIZACION</strong> y recibirá las credenciales para su acceso.
              </p>
            </div>

            {/* Banner si los datos se pre-cargaron desde Persona Natural */}
            {tipoRegistro === 'NATURAL' && esMismoAdmin && (
              <div className="p-3.5 rounded-2xl bg-emerald-950/30 border border-emerald-800/40 text-xs text-emerald-300 flex items-start gap-2.5 animate-in fade-in duration-200">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold text-emerald-200">Datos personales transferidos de tu registro de propietario</p>
                  <p className="text-emerald-300/80 text-[11px] mt-0.5">
                    Hemos precargado tus datos. Por favor define tu <strong>nombre de usuario único</strong> para iniciar sesión en SAED.
                  </p>
                </div>
              </div>
            )}

            <Card className="bg-slate-900/80 border-slate-800 rounded-3xl p-6 space-y-4 shadow-xl">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="pNombre" className="text-xs text-slate-300 font-semibold">
                    Primer Nombre *
                  </Label>
                  <Input
                    id="pNombre"
                    placeholder="ej. Carlos"
                    maxLength={25}
                    value={adminForm.primerNombre}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, primerNombre: soloLetras(e.target.value, 25) });
                      markTouched('primerNombre');
                    }}
                    onBlur={() => markTouched('primerNombre')}
                    className={getInputClass('primerNombre', adminErrors.primerNombre)}
                  />
                  {renderFieldFeedback('primerNombre', adminErrors.primerNombre, 'Máx. 25 caracteres, solo letras')}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sNombre" className="text-xs text-slate-300 font-semibold">
                    Segundo Nombre
                  </Label>
                  <Input
                    id="sNombre"
                    placeholder="ej. Andrés"
                    maxLength={25}
                    value={adminForm.segundoNombre}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, segundoNombre: soloLetras(e.target.value, 25) });
                      markTouched('segundoNombre');
                    }}
                    onBlur={() => markTouched('segundoNombre')}
                    className={getInputClass('segundoNombre', adminErrors.segundoNombre)}
                  />
                  {renderFieldFeedback('segundoNombre', adminErrors.segundoNombre, 'Opcional, máx. 25 caracteres')}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="pApellido" className="text-xs text-slate-300 font-semibold">
                    Primer Apellido *
                  </Label>
                  <Input
                    id="pApellido"
                    placeholder="ej. Rodríguez"
                    maxLength={25}
                    value={adminForm.primerApellido}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, primerApellido: soloLetras(e.target.value, 25) });
                      markTouched('primerApellido');
                    }}
                    onBlur={() => markTouched('primerApellido')}
                    className={getInputClass('primerApellido', adminErrors.primerApellido)}
                  />
                  {renderFieldFeedback('primerApellido', adminErrors.primerApellido, 'Máx. 25 caracteres, solo letras')}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="sApellido" className="text-xs text-slate-300 font-semibold">
                    Segundo Apellido
                  </Label>
                  <Input
                    id="sApellido"
                    placeholder="ej. Pérez"
                    maxLength={25}
                    value={adminForm.segundoApellido}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, segundoApellido: soloLetras(e.target.value, 25) });
                      markTouched('segundoApellido');
                    }}
                    onBlur={() => markTouched('segundoApellido')}
                    className={getInputClass('segundoApellido', adminErrors.segundoApellido)}
                  />
                  {renderFieldFeedback('segundoApellido', adminErrors.segundoApellido, 'Opcional, máx. 25 caracteres')}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="tipoDoc" className="text-xs text-slate-300 font-semibold">
                    Tipo Doc. *
                  </Label>
                  <select
                    id="tipoDoc"
                    value={adminForm.tipoDocumento}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, tipoDocumento: e.target.value });
                      markTouched('numeroDocumento');
                    }}
                    className="w-full bg-slate-950 border border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 px-3 outline-none"
                  >
                    <option value="CC">C.C. Cédula Ciudadanía</option>
                    <option value="CE">C.E. Cédula Extranjería</option>
                    <option value="PAS">Pasaporte</option>
                    <option value="PPT">Permiso Protección Temporal</option>
                    <option value="NIT">NIT</option>
                  </select>
                </div>

                <div className="space-y-1.5 sm:col-span-2">
                  <Label htmlFor="numDoc" className="text-xs text-slate-300 font-semibold">
                    Número de Documento *
                  </Label>
                  <Input
                    id="numDoc"
                    placeholder={getDocPlaceholder(adminForm.tipoDocumento)}
                    value={adminForm.numeroDocumento}
                    onChange={(e) => {
                      let val = e.target.value;
                      if (['CC', 'TI', 'RC'].includes(adminForm.tipoDocumento)) {
                        val = soloNumeros(val, 10);
                      } else if (adminForm.tipoDocumento === 'NIT') {
                        val = val.replace(/[^0-9-]/g, '').slice(0, 15);
                      } else {
                        val = val.replace(/[^A-Za-z0-9]/g, '').slice(0, 16);
                      }
                      setAdminForm({ ...adminForm, numeroDocumento: val });
                      markTouched('numeroDocumento');
                    }}
                    onBlur={() => markTouched('numeroDocumento')}
                    className={getInputClass('numeroDocumento', adminErrors.numeroDocumento)}
                  />
                  {renderFieldFeedback(
                    'numeroDocumento',
                    adminErrors.numeroDocumento,
                    getDocHint(adminForm.tipoDocumento)
                  )}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="adminUsername" className="text-xs text-slate-300 font-semibold">
                    Usuario para Iniciar Sesión *
                  </Label>
                  <Input
                    id="adminUsername"
                    placeholder="ej. carlos.admin"
                    maxLength={30}
                    value={adminForm.adminUsername}
                    onChange={(e) => {
                      const val = e.target.value.toLowerCase().replace(/[^a-z0-9._-]/g, '').slice(0, 30);
                      setAdminForm({ ...adminForm, adminUsername: val });
                      markTouched('adminUsername');
                    }}
                    onBlur={() => markTouched('adminUsername')}
                    className={getInputClass('adminUsername', adminErrors.adminUsername)}
                  />
                  {renderFieldFeedback(
                    'adminUsername',
                    adminErrors.adminUsername,
                    'Usuario único para acceder (min. 3 caracteres, letras, números, punto o guion)'
                  )}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="adminEmail" className="text-xs text-slate-300 font-semibold">
                    Correo Electrónico (Recepción de Credenciales) *
                  </Label>
                  <Input
                    id="adminEmail"
                    type="email"
                    maxLength={60}
                    placeholder="carlos@tuempresa.com"
                    value={adminForm.email}
                    onChange={(e) => {
                      setAdminForm({ ...adminForm, email: e.target.value.trim().slice(0, 60) });
                      markTouched('email');
                    }}
                    onBlur={() => markTouched('email')}
                    className={getInputClass('email', adminErrors.email)}
                  />
                  {renderFieldFeedback('email', adminErrors.email, 'Aquí despacharemos tu usuario y contraseña de acceso')}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="adminTel" className="text-xs text-slate-300 font-semibold">
                  Teléfono Celular (10 dígitos) *
                </Label>
                <Input
                  id="adminTel"
                  placeholder="ej. 3101234567"
                  maxLength={10}
                  value={adminForm.telefono}
                  onChange={(e) => {
                    const val = soloNumeros(e.target.value, 10);
                    setAdminForm({ ...adminForm, telefono: val });
                    markTouched('telefono');
                  }}
                  onBlur={() => markTouched('telefono')}
                  className={getInputClass('telefono', adminErrors.telefono)}
                />
                {renderFieldFeedback('telefono', adminErrors.telefono, 'Celular colombiano de 10 dígitos')}
              </div>

              <div className="p-3.5 rounded-2xl bg-cyan-950/40 border border-cyan-800/40 text-xs text-cyan-300 flex items-start gap-3">
                <Lock className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold text-cyan-200">Asignación automática de contraseña por seguridad</p>
                  <p className="text-cyan-300/80 mt-0.5 text-[11px] leading-relaxed">
                    La contraseña de acceso será generada automáticamente por el sistema con cifrado de alta seguridad y despachada de inmediato a tu correo electrónico. Podrás cambiarla fácilmente una vez inicies sesión.
                  </p>
                </div>
              </div>

              {/* Resumen de Orden */}
              <div className="mt-4 p-4 rounded-2xl bg-slate-950/80 border border-slate-800 flex items-center justify-between">
                <div>
                  <span className="text-[11px] text-slate-400">Plan Seleccionado:</span>
                  <div className="text-sm font-bold text-white flex items-center gap-2">
                    <span>{currentPlan?.nombre}</span>
                    <span className="text-[10px] text-cyan-400 font-mono">({billingCycle})</span>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-[11px] text-slate-400">Total Inversión:</span>
                  <div className="text-base font-black font-mono text-cyan-400">
                    {pricing.totalPesos > 0 ? `$${pricing.totalPesos.toLocaleString('es-CO')} COP` : 'GRATIS (14 días)'}
                  </div>
                </div>
              </div>
            </Card>

            <div className="flex justify-between pt-2">
              <Button
                variant="outline"
                onClick={() => setStep(2)}
                className="border-slate-800 text-slate-300 hover:bg-slate-800 rounded-xl text-xs"
              >
                Volver
              </Button>
              <Button
                onClick={handleSubmitRegistro}
                disabled={submitting}
                className="bg-gradient-to-r from-cyan-400 to-sky-500 hover:from-cyan-300 hover:to-sky-400 text-slate-950 font-extrabold px-8 py-3 rounded-xl flex items-center gap-2 shadow-lg shadow-sky-950/50 text-xs"
              >
                {submitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Creando Organización...</span>
                  </>
                ) : (
                  <>
                    <span>
                      {pricing.totalPesos > 0
                        ? 'Confirmar y Proceder al Pago Wompi'
                        : 'Crear Organización y Activar Prueba'}
                    </span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </Button>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* PASO 4: Confirmación, Pago Wompi o Activación */}
        {/* ========================================================================= */}
        {step === 4 && registroResultado && (
          <div className="max-w-2xl mx-auto space-y-6 animate-in zoom-in-95 duration-300">
            {/* Caso 1: Plan Gratuito / Prueba Activada */}
            {!registroResultado.requierePago ? (
              <Card className="bg-slate-900/90 border-slate-800 rounded-3xl p-8 text-center space-y-6 shadow-2xl">
                <div className="w-16 h-16 rounded-3xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/10">
                  <CheckCircle2 className="w-8 h-8" />
                </div>

                <div className="space-y-2">
                  <h2 className="text-2xl font-black text-white font-['Plus_Jakarta_Sans']">
                    ¡Organización Creada Exitosamente!
                  </h2>
                  <p className="text-sm text-slate-300 max-w-md mx-auto">
                    Tu período de prueba de 14 días ha sido activado para{' '}
                    <strong className="text-white">{orgForm.nombreOrganizacion}</strong>.
                  </p>
                </div>

                <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800 text-left space-y-3 max-w-md mx-auto">
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="text-slate-400">Usuario de acceso:</span>
                    <strong className="font-mono text-cyan-400 font-bold">{registroResultado.adminUsername || adminForm.adminUsername}</strong>
                  </div>
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="text-slate-400">Correo registrado:</span>
                    <strong className="text-white">{registroResultado.email || adminForm.email}</strong>
                  </div>
                  <div className="pt-2 border-t border-slate-800/80 text-[11px] text-slate-300">
                    <p className="font-semibold text-emerald-400 flex items-center gap-1.5 mb-1">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Credenciales de acceso despachadas por correo
                    </p>
                    <p className="text-slate-400 leading-relaxed">
                      Hemos enviado a tu correo tu <strong>usuario</strong> y tu <strong>contraseña temporal segura</strong> para que puedas ingresar de inmediato. Recuerda que podrás cambiar la contraseña una vez inicies sesión.
                    </p>
                  </div>
                </div>

                <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-3">
                  <Link
                    to="/login"
                    className="w-full sm:w-auto px-6 py-3 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-cyan-500/20"
                  >
                    <span>Ir al Inicio de Sesión</span>
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                </div>
              </Card>
            ) : (
              /* Caso 2: Plan Comercial con Wompi */
              <Card className="bg-slate-900/90 border-slate-800 rounded-3xl p-8 space-y-6 shadow-2xl">
                <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                  <div>
                    <h2 className="text-xl font-black text-white font-['Plus_Jakarta_Sans']">
                      Pago de Membresía SaaS
                    </h2>
                    <p className="text-xs text-slate-400">
                      Organización: <span className="text-white font-semibold">{orgForm.nombreOrganizacion}</span>
                    </p>
                  </div>
                  <div className="px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs font-mono font-bold">
                    {pagoAprobado ? 'PAGO APROBADO' : 'PENDIENTE DE PAGO'}
                  </div>
                </div>

                {!pagoAprobado ? (
                  <div className="space-y-6">
                    <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800 space-y-3">
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-400">Referencia de Pago:</span>
                        <span className="font-mono text-cyan-400 font-bold">{registroResultado.referencia}</span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-400">Plan Contratado:</span>
                        <span className="text-white font-semibold">{currentPlan?.nombre} ({billingCycle})</span>
                      </div>
                      <div className="flex justify-between text-sm pt-2 border-t border-slate-800/80">
                        <span className="text-slate-300 font-bold">Total a Pagar:</span>
                        <span className="text-lg font-black font-mono text-cyan-400">
                          ${(registroResultado.montoPesos || pricing.totalPesos).toLocaleString('es-CO')} COP
                        </span>
                      </div>
                    </div>

                    <div className="p-4 rounded-2xl bg-sky-950/20 border border-sky-800/40 text-xs text-sky-200 flex items-start gap-3">
                      <CreditCard className="w-5 h-5 text-sky-400 shrink-0 mt-0.5" />
                      <div className="space-y-1">
                        <p className="font-semibold">Transacción Segura a través de Wompi</p>
                        <p className="text-[11px] text-sky-300/80">
                          Acepta tarjetas de crédito, débito, PSE, Nequi y Bancolombia. Tras la confirmación en tiempo real, tu cuenta se activará automáticamente y recibirás el enlace de configuración de contraseña.
                        </p>
                      </div>
                    </div>

                    <div className="flex flex-col sm:flex-row items-center gap-3">
                      <Button
                        onClick={() => lanzarWidgetWompi(registroResultado)}
                        className="w-full bg-gradient-to-r from-cyan-400 to-sky-500 hover:from-cyan-300 hover:to-sky-400 text-slate-950 font-extrabold py-3.5 rounded-xl flex items-center justify-center gap-2 shadow-lg shadow-sky-950/50 text-xs"
                      >
                        <CreditCard className="w-4 h-4" />
                        <span>Abrir Pasarela de Pago Wompi</span>
                      </Button>

                      <Button
                        variant="outline"
                        onClick={async () => {
                          try {
                            const res = await api.get(`/auth/onboarding/estado-pago?referencia=${encodeURIComponent(registroResultado.referencia)}`);
                            const data = res?.data || res;
                            if (data?.estadoPasarela === 'APROBADO' || data?.estadoOrganizacion === 'ACTIVA') {
                              setPagoAprobado(true);
                              toast.success('¡Pago confirmado exitosamente!');
                            } else {
                              toast.info('La transacción aún está en proceso o pendiente.');
                            }
                          } catch (e) {
                            toast.error('Error verificando estado.');
                          }
                        }}
                        className="w-full sm:w-auto border-slate-800 text-slate-300 hover:bg-slate-800 rounded-xl text-xs py-3.5 flex items-center justify-center gap-2"
                      >
                        <RefreshCw className="w-4 h-4" />
                        <span>Verificar Estado</span>
                      </Button>
                    </div>

                    {pollingPago && (
                      <div className="flex items-center justify-center gap-2 text-xs text-slate-400">
                        <Loader2 className="w-3.5 h-3.5 animate-spin text-cyan-400" />
                        <span>Esperando confirmación automática de la pasarela...</span>
                      </div>
                    )}
                  </div>
                ) : (
                  /* Pago Aprobado */
                  <div className="text-center space-y-6">
                    <div className="w-16 h-16 rounded-3xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/10">
                      <CheckCircle2 className="w-8 h-8" />
                    </div>

                    <div className="space-y-2">
                      <h3 className="text-2xl font-black text-white font-['Plus_Jakarta_Sans']">
                        ¡Pago Aprobado y Organización Activada!
                      </h3>
                      <p className="text-xs text-slate-300 max-w-md mx-auto">
                        Hemos recibido el pago de tu membresía correctamente. Despachamos las credenciales de acceso (usuario <strong className="text-cyan-400 font-mono">{adminForm.adminUsername}</strong> y contraseña generada) a <strong className="text-white">{adminForm.email}</strong>.
                      </p>
                    </div>

                    <div className="pt-2 flex justify-center">
                      <Link
                        to="/login"
                        className="px-6 py-3 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-lg shadow-cyan-500/20"
                      >
                        <span>Ir a la Plataforma SAED</span>
                        <ArrowRight className="w-4 h-4" />
                      </Link>
                    </div>
                  </div>
                )}
              </Card>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
