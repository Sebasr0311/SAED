import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import {
  Building2,
  Check,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  ArrowLeft,
  Crown,
  Layers,
  CreditCard,
  User,
  MapPin,
  Mail,
  Loader2,
  RefreshCw,
  Sun,
  Moon,
} from 'lucide-react';
import api from '../lib/api.js';
import {
  DEPARTAMENTOS_COLOMBIA,
  COLOMBIA_LOCATIONS,
  fetchDepartamentos,
  fetchCiudadesPorDepartamento,
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
import { toast } from 'sonner';
import { animate } from 'animejs';
import { useSaedTheme } from '../lib/theme.js';
import '../components/landing/manual.css';
import '../components/landing/manual-public.css';

const SAED_EMBLEM = '/imagenes/saed_logo_emblem_only.png';

const STEPS = [
  { num: 1, label: 'Plan y ciclo' },
  { num: 2, label: 'Organización' },
  { num: 3, label: 'Administrador' },
  { num: 4, label: 'Activación' },
];

function prefersReducedMotion() {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

function trialLabel(plan) {
  const dias = plan?.diasPrueba || plan?.DIAS_PRUEBA;
  return dias ? `Prueba de ${dias} días` : 'Prueba gratuita';
}

export default function RegistroOrganizacionPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { theme, toggleTheme } = useSaedTheme();

  // URL Params pre-select
  const initialPlanCode = searchParams.get('plan') || 'PRO';
  const initialCycle = searchParams.get('cycle') || 'ANUAL';

  // Wizard state
  const [step, setStep] = useState(1);
  const [planes, setPlanes] = useState([]);
  const [loadingPlanes, setLoadingPlanes] = useState(true);
  const [planesError, setPlanesError] = useState(null);

  // Selection
  const [selectedPlanId, setSelectedPlanId] = useState(null);
  const [billingCycle, setBillingCycle] = useState(
    initialCycle.toUpperCase() === 'MENSUAL' ? 'MENSUAL' : 'ANUAL'
  );

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

  // Dynamic Colombia API location state
  const [deptosList, setDeptosList] = useState(DEPARTAMENTOS_COLOMBIA);
  const [ciudadesList, setCiudadesList] = useState(COLOMBIA_LOCATIONS['Bogotá D.C.'] || ['Bogotá']);
  const [loadingCiudades, setLoadingCiudades] = useState(false);

  // Load official departments from API on mount
  useEffect(() => {
    let isMounted = true;
    fetchDepartamentos()
      .then((depts) => {
        if (isMounted && Array.isArray(depts) && depts.length > 0) {
          setDeptosList(depts.map((d) => d.name));
        }
      })
      .catch(() => {});
    return () => {
      isMounted = false;
    };
  }, []);

  // Load municipalities whenever selected department changes
  useEffect(() => {
    if (!orgForm.departamento) {
      setCiudadesList([]);
      return;
    }
    let isMounted = true;
    setLoadingCiudades(true);
    const fallback = COLOMBIA_LOCATIONS[orgForm.departamento] || [];
    if (fallback.length > 0) {
      setCiudadesList(fallback);
    }
    fetchCiudadesPorDepartamento(orgForm.departamento)
      .then((cities) => {
        if (isMounted && Array.isArray(cities) && cities.length > 0) {
          setCiudadesList(cities);
        }
      })
      .finally(() => {
        if (isMounted) setLoadingCiudades(false);
      });
    return () => {
      isMounted = false;
    };
  }, [orgForm.departamento]);

  // Submission & Payment State
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [registroResultado, setRegistroResultado] = useState(null);
  const [pagoAprobado, setPagoAprobado] = useState(false);
  const [pollingPago, setPollingPago] = useState(false);
  const pollingRef = useRef(null);

  // Plan and registration-type selection (feedback is the selected state itself).
  const handleSelectPlan = (planId) => {
    setSelectedPlanId(planId);
  };

  const handleSelectTipoRegistro = (tipo) => {
    setTipoRegistro(tipo);
    setOrgForm((prev) => ({ ...prev, tipoPersona: tipo }));
  };

  // 1. Fetch available plans from Oracle backend API
  const loadPlanes = async () => {
    try {
      setLoadingPlanes(true);
      setPlanesError(null);
      const res = await api.get('/auth/onboarding/planes');
      const data = res?.data || res || [];
      if (Array.isArray(data) && data.length > 0) {
        setPlanes(data);

        // Match initial plan from URL or default to PRO
        const matched = data.find(
          (p) =>
            p.codigo?.toUpperCase() === initialPlanCode.toUpperCase() ||
            p.idPlan?.toString() === initialPlanCode
        );
        if (matched) {
          setSelectedPlanId(matched.idPlan);
        } else {
          const proPlan = data.find((p) => p.codigo === 'PRO');
          setSelectedPlanId(proPlan ? proPlan.idPlan : data[0].idPlan);
        }
      } else {
        setPlanesError('No fue posible cargar los planes comerciales del catálogo.');
      }
    } catch (err) {
      console.error('Error cargando catálogo oficial de planes:', err);
      setPlanesError(
        'No se pudo conectar con el catálogo de planes en el servidor. Por favor verifica tu conexión y reintenta.'
      );
    } finally {
      setLoadingPlanes(false);
    }
  };

  useEffect(() => {
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

    let attempts = 0;
    const MAX_ATTEMPTS = 75; // 5 minutos máximo (75 * 4s)

    pollingRef.current = setInterval(async () => {
      attempts++;
      if (attempts > MAX_ATTEMPTS) {
        setPollingPago(false);
        clearInterval(pollingRef.current);
        return;
      }
      try {
        const res = await api.get(
          `/auth/onboarding/estado-pago?referencia=${encodeURIComponent(registroResultado.referencia)}`
        );
        const data = res?.data || res;
        const status = data?.estadoPasarela || data?.estado;
        if (status === 'APROBADO' || data?.estadoOrganizacion === 'ACTIVA') {
          setPagoAprobado(true);
          setPollingPago(false);
          clearInterval(pollingRef.current);
          toast.success('¡Pago confirmado con éxito! Tu organización ha sido activada.');
        }
      } catch (e) {
        if (attempts % 5 === 0) {
          console.warn('Consultando estado de transacción en Wompi...', e?.message || e);
        }
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
        const vApe2 = valApellido(orgForm.segundoApellidoDueno, 'El segundo apellido', {
          required: false,
        });
        if (!vApe2.ok) errs.segundoApellidoDueno = vApe2.mensaje;
      }

      const vDoc = valDocumento(
        orgForm.numeroDocumentoDueno,
        orgForm.tipoDocumentoDueno,
        'El documento de identidad'
      );
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

    const vDoc = valDocumento(
      adminForm.numeroDocumento,
      adminForm.tipoDocumento,
      'El número de documento'
    );
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
        <p id={`fb-${touchedKey}`} className="m-pub-error">
          <AlertCircle aria-hidden="true" />
          <span>{errorMsg}</span>
        </p>
      );
    }
    if (hintMsg) {
      return (
        <p id={`fb-${touchedKey}`} className="m-pub-hint">
          {hintMsg}
        </p>
      );
    }
    return null;
  };

  const getInputClass = (touchedKey, errorMsg) => {
    const isErr = touched[touchedKey] && !!errorMsg;
    return isErr ? 'm-pub-input field-error-shake' : 'm-pub-input';
  };

  // Ties each control to its hint/error line and exposes the invalid state.
  const fieldA11y = (touchedKey, errorMsg) => ({
    'aria-invalid': Boolean(touched[touchedKey] && errorMsg),
    'aria-describedby': `fb-${touchedKey}`,
  });

  const selectClass = 'm-pub-input';

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

      // Microanimación de sacudida (shake) en campos con error
      setTimeout(() => {
        try {
          const errInputs = document.querySelectorAll('.field-error-shake');
          if (errInputs && errInputs.length > 0 && !prefersReducedMotion()) {
            animate(errInputs, {
              translateX: [-5, 5, -4, 4, -2, 2, 0],
              duration: 340,
              ease: 'inOutQuad',
            });
          }
        } catch {
          // Animación opcional de error shake
        }
      }, 50);
      return;
    }

    // Auto-populate Step 3 if Persona Natural and esMismoAdmin
    if (tipoRegistro === 'NATURAL' && esMismoAdmin) {
      setAdminForm((prev) => {
        const pNom = orgForm.primerNombreDueno.trim();
        const pApe = orgForm.primerApellidoDueno.trim();
        const suggestedUser =
          prev.adminUsername ||
          `${pNom}.${pApe}`.toLowerCase().replace(/[^a-z0-9._-]/g, '');
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

      // Microanimación de sacudida (shake) en campos con error
      setTimeout(() => {
        try {
          const errInputs = document.querySelectorAll('.field-error-shake');
          if (errInputs && errInputs.length > 0 && !prefersReducedMotion()) {
            animate(errInputs, {
              translateX: [-5, 5, -4, 4, -2, 2, 0],
              duration: 340,
              ease: 'inOutQuad',
            });
          }
        } catch {
          // Animación opcional de error shake
        }
      }, 50);
      return;
    }

    try {
      setSubmitError(null);
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
      const errMsg =
        err.response?.data?.message ||
        err.message ||
        'Error al procesar el registro. Verifica los datos e intenta de nuevo.';
      setSubmitError(errMsg);
      toast.error(errMsg);
    } finally {
      setSubmitting(false);
    }
  };

  // Launch Wompi Checkout Widget
  const lanzarWidgetWompi = (txData) => {
    try {
      if (typeof window.WidgetCheckout !== 'undefined') {
        const rawPhone = (adminForm.telefono || '').replace(/\D/g, '');
        const cleanPhone =
          rawPhone.length > 10 && rawPhone.startsWith('57') ? rawPhone.slice(2) : rawPhone;

        const customerData = {
          email: txData.adminEmail,
          fullName:
            `${adminForm.primerNombre || ''} ${adminForm.primerApellido || ''}`.trim() ||
            undefined,
          ...(cleanPhone
            ? {
                phoneNumber: cleanPhone,
                phoneNumberPrefix: '+57',
              }
            : {}),
          ...(adminForm.numeroDocumento
            ? {
                legalId: adminForm.numeroDocumento.trim(),
                legalIdType: adminForm.tipoDocumento || 'CC',
              }
            : {}),
        };

        const checkout = new window.WidgetCheckout({
          currency: txData.moneda || 'COP',
          amountInCents: txData.montoCentavos,
          reference: txData.referencia,
          publicKey: txData.wompiPublicKey,
          signature: { integrity: txData.firmaIntegridad },
          customerData,
        });

        checkout.open(async (result) => {
          const transaction = result?.transaction;
          if (transaction) {
            if (transaction.status === 'APPROVED') {
              setPagoAprobado(true);
              setPollingPago(false);
              toast.success('¡Transacción aprobada!');
            } else if (transaction.status === 'DECLINED') {
              toast.error('La transacción fue rechazada por la entidad bancaria.');
            } else if (transaction.status === 'ERROR') {
              toast.error('Ocurrió un error procesando el pago en la pasarela.');
            }
            try {
              const res = await api.get(
                `/auth/onboarding/estado-pago?referencia=${encodeURIComponent(txData.referencia)}`
              );
              const data = res?.data || res;
              const status = data?.estadoPasarela || data?.estado;
              if (status === 'APROBADO' || data?.estadoOrganizacion === 'ACTIVA') {
                setPagoAprobado(true);
                setPollingPago(false);
              }
            } catch {
              // Sondeo opcional de estado en background, continuar si falla
            }
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
      toast.error(
        'No se pudo inicializar la pasarela de pagos. Por favor intenta abrirla nuevamente.'
      );
    }
  };
  const verificarEstadoPago = async () => {
    try {
      const res = await api.get(
        `/auth/onboarding/estado-pago?referencia=${encodeURIComponent(registroResultado.referencia)}`
      );
      const data = res?.data || res;
      const status = data?.estadoPasarela || data?.estado;
      if (status === 'APROBADO' || data?.estadoOrganizacion === 'ACTIVA') {
        setPagoAprobado(true);
        toast.success('¡Pago confirmado exitosamente!');
      } else {
        toast.info('La transacción aún está en proceso o pendiente.');
      }
    } catch {
      toast.error('Error verificando estado.');
    }
  };

  const totalLabel =
    pricing.totalPesos > 0
      ? `$${pricing.totalPesos.toLocaleString('es-CO')} COP`
      : trialLabel(currentPlan);

  return (
    <div className={`saed-manual m-root ${theme === 'dark' ? 'dark' : ''}`}>
      <header className="m-strip">
        <div className="m-container m-strip-inner">
          <Link to="/" className="m-brand">
            <img src={SAED_EMBLEM} alt="" className="m-brand-mark" width="32" height="32" />
            <span className="m-brand-name">SAED</span>
            <span className="m-brand-ver">2.0</span>
            <span className="m-sr">, ir al inicio</span>
          </Link>
          <div className="m-strip-actions">
            <Link to="/suscripciones" className="m-btn m-btn--quiet m-hide-mobile">
              <ArrowLeft aria-hidden="true" />
              <span>Volver a planes</span>
            </Link>
            <Link to="/login" className="m-btn m-btn--glass">
              Iniciar sesión
            </Link>
            <button
              type="button"
              onClick={toggleTheme}
              className="m-icon-btn"
              aria-label={theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              title={theme === 'dark' ? 'Modo claro' : 'Modo oscuro'}
            >
              {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
            </button>
          </div>
        </div>
      </header>

      <main className="m-container m-pub-main">
        <div className="m-pub-spread">
          {/* Left page: assembly steps and the order being built */}
          <aside className="m-pub-rail m-studs" aria-label="Pasos del registro">
            <p className="m-pub-rail-title">Registro de la organización</p>
            <ol className="m-pub-steps">
              {STEPS.map((s) => {
                const state = step > s.num ? 'done' : step === s.num ? 'now' : 'next';
                return (
                  <li
                    key={s.num}
                    className="m-pub-step"
                    data-state={state}
                    aria-current={state === 'now' ? 'step' : undefined}
                  >
                    <span className="m-pub-step-mark">
                      <span className="m-pub-step-num" aria-hidden="true">
                        {state === 'done' ? <Check /> : s.num}
                      </span>
                    </span>
                    <span className="m-pub-step-label">
                      {s.label}
                      <span className="m-sr">
                        {state === 'done' ? ', completado' : state === 'now' ? ', paso actual' : ''}
                      </span>
                    </span>
                  </li>
                );
              })}
            </ol>

            {currentPlan && (
              <div className="m-pub-rail-summary">
                <p className="m-pub-rail-title">Tu pedido</p>
                <dl className="m-pub-dl">
                  <div>
                    <dt>Plan</dt>
                    <dd>{currentPlan.nombre}</dd>
                  </div>
                  <div>
                    <dt>Ciclo</dt>
                    <dd>{billingCycle === 'ANUAL' ? 'Anual' : 'Mensual'}</dd>
                  </div>
                  <div className="is-total">
                    <dt>Total</dt>
                    <dd>{totalLabel}</dd>
                  </div>
                </dl>
              </div>
            )}
          </aside>

          {/* Right page: the current step */}
          <div className="m-pub-content m-pub-enter" key={step}>
            {/* PASO 1: Selección de plan y ciclo */}
            {step === 1 && (
              <>
                <div className="m-pub-head">
                  <h1 className="m-h3">Elige la escala para tu organización</h1>
                  <p className="m-body">
                    Selecciona el plan y el ciclo de facturación. Podrás administrar varias propiedades
                    según los límites del plan.
                  </p>
                </div>

                <div className="m-pub-seg" role="group" aria-label="Ciclo de facturación">
                  <button
                    type="button"
                    aria-pressed={billingCycle === 'MENSUAL'}
                    onClick={() => setBillingCycle('MENSUAL')}
                  >
                    Facturación mensual
                  </button>
                  <button
                    type="button"
                    aria-pressed={billingCycle === 'ANUAL'}
                    onClick={() => setBillingCycle('ANUAL')}
                  >
                    <span>Facturación anual</span>
                    <span className="m-pub-seg-tag">-20% DCTO</span>
                  </button>
                </div>

                {loadingPlanes ? (
                  <div role="status" aria-live="polite" style={{ display: 'grid', gap: 14 }}>
                    <p className="m-pub-status">
                      <Loader2 className="m-pub-spin" aria-hidden="true" />
                      Cargando el catálogo de planes...
                    </p>
                    <div className="m-pub-choices m-pub-choices--plans" aria-hidden="true">
                      <div className="m-pub-skel-card" />
                      <div className="m-pub-skel-card" />
                      <div className="m-pub-skel-card" />
                    </div>
                  </div>
                ) : planesError ? (
                  <div className="m-pub-note is-error" role="alert">
                    <AlertCircle aria-hidden="true" />
                    <div style={{ display: 'grid', gap: 10, justifyItems: 'start' }}>
                      <div>
                        <p className="m-pub-note-title">Catálogo de planes no disponible</p>
                        <p className="m-pub-note-text">{planesError}</p>
                      </div>
                      <button type="button" onClick={loadPlanes} className="m-btn m-btn--glass">
                        <RefreshCw aria-hidden="true" />
                        <span>Reintentar</span>
                      </button>
                    </div>
                  </div>
                ) : planes.length === 0 ? (
                  <div className="m-pub-note">
                    <Building2 aria-hidden="true" />
                    <div>
                      <p className="m-pub-note-title">No hay planes disponibles</p>
                      <p className="m-pub-note-text">
                        En este momento no hay planes activos.{' '}
                        <Link to="/suscripciones" className="m-link">
                          Consulta los planes
                        </Link>{' '}
                        o intenta más tarde.
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="m-pub-choices m-pub-choices--plans">
                    {planes.map((plan) => {
                      const isSelected = selectedPlanId === plan.idPlan;
                      const isCommercial = (plan.precioMensual || 0) > 0;
                      const annualRate =
                        plan.precioAnual || Math.round((plan.precioMensual || 0) * 12 * 0.8);
                      const displayRate = isCommercial
                        ? billingCycle === 'ANUAL'
                          ? Math.round(annualRate / 12)
                          : plan.precioMensual
                        : 0;
                      const PlanIcon =
                        plan.codigo === 'ENTERPRISE' ? Crown : plan.codigo === 'PRO' ? Layers : Building2;

                      return (
                        <button
                          type="button"
                          key={plan.idPlan}
                          aria-pressed={isSelected}
                          onClick={() => handleSelectPlan(plan.idPlan)}
                          className="m-pub-choice"
                        >
                          <span className="m-pub-choice-head">
                            <span className="m-pub-choice-icon" aria-hidden="true">
                              <PlanIcon />
                            </span>
                            <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                              {plan.codigo === 'PRO' && <span className="m-pub-tag is-new">Recomendado</span>}
                              <span className="m-pub-choice-tick" aria-hidden="true">
                                <Check />
                              </span>
                            </span>
                          </span>

                          <span>
                            <span className="m-pub-choice-title">{plan.nombre}</span>
                            {plan.descripcion && (
                              <span className="m-pub-choice-text">{plan.descripcion}</span>
                            )}
                          </span>

                          <span className="m-pub-price">
                            {isCommercial ? (
                              <>
                                <span className="m-pub-price-value">
                                  ${displayRate.toLocaleString('es-CO')}
                                  <span className="m-pub-price-unit">/ mes</span>
                                </span>
                                {billingCycle === 'ANUAL' && (
                                  <span className="m-pub-price-note is-ok">
                                    Cobro anual de ${(displayRate * 12).toLocaleString('es-CO')} COP
                                  </span>
                                )}
                              </>
                            ) : (
                              <>
                                <span className="m-pub-price-value">{trialLabel(plan)}</span>
                                <span className="m-pub-price-note">Sin pago para iniciar</span>
                              </>
                            )}
                          </span>

                          <span className="m-pub-limits">
                            <span>
                              <span className="m-pub-limits-k">Propiedades</span>
                              <span className="m-pub-limits-v">Hasta {plan.limitePropiedades}</span>
                            </span>
                            <span>
                              <span className="m-pub-limits-k">Unidades</span>
                              <span className="m-pub-limits-v">Hasta {plan.limiteUnidades}</span>
                            </span>
                            <span>
                              <span className="m-pub-limits-k">Usuarios administradores</span>
                              <span className="m-pub-limits-v">Hasta {plan.limiteUsuarios}</span>
                            </span>
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}

                <div className="m-pub-actions">
                  <Link to="/suscripciones" className="m-btn m-btn--glass">
                    <ArrowLeft aria-hidden="true" />
                    <span>Volver a planes</span>
                  </Link>
                  <button
                    type="button"
                    onClick={() => setStep(2)}
                    disabled={!selectedPlanId}
                    className="m-btn m-btn--primary m-btn--lg"
                  >
                    <span>Continuar a datos de la organización</span>
                    <ArrowRight aria-hidden="true" />
                  </button>
                </div>
              </>
            )}

            {/* PASO 2: Datos de la empresa o copropiedad */}
            {step === 2 && (
              <>
                <button type="button" onClick={() => setStep(1)} className="m-btn m-btn--quiet m-pub-back">
                  <ArrowLeft aria-hidden="true" />
                  <span>Volver a planes</span>
                </button>
                <div className="m-pub-head">
                  <h1 className="m-h3">Datos de la Empresa o Copropiedad</h1>
                  <p className="m-body">
                    Selecciona la modalidad de registro e ingresa la información correspondiente según
                    la normativa colombiana.
                  </p>
                </div>

                <div className="m-pub-choices m-pub-choices--2" role="group" aria-label="Modalidad de registro">
                  <button
                    type="button"
                    aria-pressed={tipoRegistro === 'JURIDICA'}
                    onClick={() => handleSelectTipoRegistro('JURIDICA')}
                    className="m-pub-choice"
                  >
                    <span className="m-pub-choice-head">
                      <span className="m-pub-choice-icon" aria-hidden="true">
                        <Building2 />
                      </span>
                      <span className="m-pub-choice-tick" aria-hidden="true">
                        <Check />
                      </span>
                    </span>
                    <span>
                      <span className="m-pub-choice-title">Empresa / Persona jurídica</span>
                      <span className="m-pub-choice-text">
                        Inmobiliaria, constructora o administración de propiedad horizontal con NIT.
                      </span>
                    </span>
                    <span className="m-pub-tag">Requiere NIT</span>
                  </button>

                  <button
                    type="button"
                    aria-pressed={tipoRegistro === 'NATURAL'}
                    onClick={() => handleSelectTipoRegistro('NATURAL')}
                    className="m-pub-choice"
                  >
                    <span className="m-pub-choice-head">
                      <span className="m-pub-choice-icon" aria-hidden="true">
                        <User />
                      </span>
                      <span className="m-pub-choice-tick" aria-hidden="true">
                        <Check />
                      </span>
                    </span>
                    <span>
                      <span className="m-pub-choice-title">Persona natural (propietario)</span>
                      <span className="m-pub-choice-text">
                        Propietario particular o titular de un edificio o copropiedad.
                      </span>
                    </span>
                    <span className="m-pub-tag">Cédula CC / CE</span>
                  </button>
                </div>

                <div className="m-pub-form">
                  {tipoRegistro === 'JURIDICA' ? (
                    <div className="m-pub-group">
                      <Field id="nombreOrg" label="Nombre de la organización / razón social" required>
                        <input
                          id="nombreOrg"
                          placeholder="ej. Inversiones Inmobiliarias del Norte S.A.S."
                          maxLength={80}
                          value={orgForm.nombreOrganizacion}
                          onChange={(e) => {
                            setOrgForm({
                              ...orgForm,
                              nombreOrganizacion: e.target.value.slice(0, 80),
                            });
                            markTouched('nombreOrganizacion');
                          }}
                          onBlur={() => markTouched('nombreOrganizacion')}
                          className={getInputClass('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                          {...fieldA11y('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                        />
                        {renderFieldFeedback(
                          'nombreOrganizacion',
                          orgErrors.nombreOrganizacion,
                          'Razón social completa inscrita en la Cámara de Comercio'
                        )}
                      </Field>

                      <div className="m-pub-grid">
                        <Field id="nit" label="NIT / identificación tributaria" required>
                          <input
                            id="nit"
                            inputMode="numeric"
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
                            {...fieldA11y('nit', orgErrors.nit)}
                          />
                          {renderFieldFeedback(
                            'nit',
                            orgErrors.nit,
                            '8 a 10 dígitos, guion y dígito de verificación opcionales'
                          )}
                        </Field>

                        <Field id="emailContacto" label="Correo electrónico corporativo" required>
                          <input
                            id="emailContacto"
                            type="email"
                            maxLength={60}
                            placeholder="admin@tuempresa.com"
                            autoComplete="email"
                            value={orgForm.emailContacto}
                            onChange={(e) => {
                              setOrgForm({ ...orgForm, emailContacto: e.target.value.slice(0, 60) });
                              markTouched('emailContacto');
                            }}
                            onBlur={() => markTouched('emailContacto')}
                            className={getInputClass('emailContacto', orgErrors.emailContacto)}
                            {...fieldA11y('emailContacto', orgErrors.emailContacto)}
                          />
                          {renderFieldFeedback(
                            'emailContacto',
                            orgErrors.emailContacto,
                            'Para facturación y notificaciones'
                          )}
                        </Field>
                      </div>

                      <Field id="telContacto" label="Teléfono corporativo o celular (10 dígitos)" required>
                        <input
                          id="telContacto"
                          inputMode="tel"
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
                          {...fieldA11y('telefonoContacto', orgErrors.telefonoContacto)}
                        />
                        {renderFieldFeedback(
                          'telefonoContacto',
                          orgErrors.telefonoContacto,
                          'Número colombiano de 10 dígitos'
                        )}
                      </Field>
                    </div>
                  ) : (
                    <>
                      <div className="m-pub-group">
                        <Field id="nombreEdificio" label="Nombre del edificio, inmueble o copropiedad" required>
                          <input
                            id="nombreEdificio"
                            placeholder="ej. Edificio Las Palmas o Reserva del Parque"
                            maxLength={80}
                            value={orgForm.nombreOrganizacion}
                            onChange={(e) => {
                              setOrgForm({
                                ...orgForm,
                                nombreOrganizacion: e.target.value.slice(0, 80),
                              });
                              markTouched('nombreOrganizacion');
                            }}
                            onBlur={() => markTouched('nombreOrganizacion')}
                            className={getInputClass('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                            {...fieldA11y('nombreOrganizacion', orgErrors.nombreOrganizacion)}
                          />
                          {renderFieldFeedback(
                            'nombreOrganizacion',
                            orgErrors.nombreOrganizacion,
                            'Nombre comercial o identificador de la propiedad'
                          )}
                        </Field>
                      </div>

                      <div className="m-pub-group">
                        <p className="m-pub-group-title">
                          <User aria-hidden="true" />
                          Datos personales del propietario
                        </p>

                        <div className="m-pub-grid">
                          <Field id="pNombreDueno" label="Primer nombre" required>
                            <input
                              id="pNombreDueno"
                              placeholder="ej. Carlos"
                              maxLength={25}
                              value={orgForm.primerNombreDueno}
                              onChange={(e) => {
                                setOrgForm({
                                  ...orgForm,
                                  primerNombreDueno: soloLetras(e.target.value, 25),
                                });
                                markTouched('primerNombreDueno');
                              }}
                              onBlur={() => markTouched('primerNombreDueno')}
                              className={getInputClass('primerNombreDueno', orgErrors.primerNombreDueno)}
                              {...fieldA11y('primerNombreDueno', orgErrors.primerNombreDueno)}
                            />
                            {renderFieldFeedback(
                              'primerNombreDueno',
                              orgErrors.primerNombreDueno,
                              'Máx. 25 caracteres, solo letras'
                            )}
                          </Field>

                          <Field id="sNombreDueno" label="Segundo nombre">
                            <input
                              id="sNombreDueno"
                              placeholder="ej. Andrés"
                              maxLength={25}
                              value={orgForm.segundoNombreDueno}
                              onChange={(e) => {
                                setOrgForm({
                                  ...orgForm,
                                  segundoNombreDueno: soloLetras(e.target.value, 25),
                                });
                                markTouched('segundoNombreDueno');
                              }}
                              onBlur={() => markTouched('segundoNombreDueno')}
                              className={getInputClass('segundoNombreDueno', orgErrors.segundoNombreDueno)}
                              {...fieldA11y('segundoNombreDueno', orgErrors.segundoNombreDueno)}
                            />
                            {renderFieldFeedback(
                              'segundoNombreDueno',
                              orgErrors.segundoNombreDueno,
                              'Opcional, máx. 25 caracteres'
                            )}
                          </Field>

                          <Field id="pApellidoDueno" label="Primer apellido" required>
                            <input
                              id="pApellidoDueno"
                              placeholder="ej. Rodríguez"
                              maxLength={25}
                              value={orgForm.primerApellidoDueno}
                              onChange={(e) => {
                                setOrgForm({
                                  ...orgForm,
                                  primerApellidoDueno: soloLetras(e.target.value, 25),
                                });
                                markTouched('primerApellidoDueno');
                              }}
                              onBlur={() => markTouched('primerApellidoDueno')}
                              className={getInputClass('primerApellidoDueno', orgErrors.primerApellidoDueno)}
                              {...fieldA11y('primerApellidoDueno', orgErrors.primerApellidoDueno)}
                            />
                            {renderFieldFeedback(
                              'primerApellidoDueno',
                              orgErrors.primerApellidoDueno,
                              'Máx. 25 caracteres, solo letras'
                            )}
                          </Field>

                          <Field id="sApellidoDueno" label="Segundo apellido">
                            <input
                              id="sApellidoDueno"
                              placeholder="ej. Pérez"
                              maxLength={25}
                              value={orgForm.segundoApellidoDueno}
                              onChange={(e) => {
                                setOrgForm({
                                  ...orgForm,
                                  segundoApellidoDueno: soloLetras(e.target.value, 25),
                                });
                                markTouched('segundoApellidoDueno');
                              }}
                              onBlur={() => markTouched('segundoApellidoDueno')}
                              className={getInputClass('segundoApellidoDueno', orgErrors.segundoApellidoDueno)}
                              {...fieldA11y('segundoApellidoDueno', orgErrors.segundoApellidoDueno)}
                            />
                            {renderFieldFeedback(
                              'segundoApellidoDueno',
                              orgErrors.segundoApellidoDueno,
                              'Opcional, máx. 25 caracteres'
                            )}
                          </Field>
                        </div>

                        <div className="m-pub-grid m-pub-grid--doc">
                          <Field id="tipoDocDueno" label="Tipo de documento" required>
                            <select
                              id="tipoDocDueno"
                              value={orgForm.tipoDocumentoDueno}
                              onChange={(e) => {
                                setOrgForm({ ...orgForm, tipoDocumentoDueno: e.target.value });
                                markTouched('numeroDocumentoDueno');
                              }}
                              className={selectClass}
                            >
                              <option value="CC">C.C. Cédula de ciudadanía</option>
                              <option value="CE">C.E. Cédula de extranjería</option>
                              <option value="PAS">Pasaporte</option>
                              <option value="PPT">Permiso por protección temporal</option>
                              <option value="NIT">NIT persona natural</option>
                            </select>
                          </Field>

                          <Field id="numDocDueno" label="Número de documento" required>
                            <input
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
                              {...fieldA11y('numeroDocumentoDueno', orgErrors.numeroDocumentoDueno)}
                            />
                            {renderFieldFeedback(
                              'numeroDocumentoDueno',
                              orgErrors.numeroDocumentoDueno,
                              getDocHint(orgForm.tipoDocumentoDueno)
                            )}
                          </Field>
                        </div>

                        <div className="m-pub-grid">
                          <Field id="emailDueno" label="Correo electrónico" required>
                            <input
                              id="emailDueno"
                              type="email"
                              maxLength={60}
                              placeholder="propietario@correo.com"
                              autoComplete="email"
                              value={orgForm.emailContacto}
                              onChange={(e) => {
                                setOrgForm({
                                  ...orgForm,
                                  emailContacto: e.target.value.slice(0, 60),
                                });
                                markTouched('emailContacto');
                              }}
                              onBlur={() => markTouched('emailContacto')}
                              className={getInputClass('emailContacto', orgErrors.emailContacto)}
                              {...fieldA11y('emailContacto', orgErrors.emailContacto)}
                            />
                            {renderFieldFeedback(
                              'emailContacto',
                              orgErrors.emailContacto,
                              'Para recibir las notificaciones'
                            )}
                          </Field>

                          <Field id="telDueno" label="Teléfono celular (10 dígitos)" required>
                            <input
                              id="telDueno"
                              inputMode="tel"
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
                              {...fieldA11y('telefonoContacto', orgErrors.telefonoContacto)}
                            />
                            {renderFieldFeedback(
                              'telefonoContacto',
                              orgErrors.telefonoContacto,
                              'Exactamente 10 dígitos'
                            )}
                          </Field>
                        </div>

                        <label htmlFor="esMismoAdmin" className="m-pub-check">
                          <input
                            type="checkbox"
                            id="esMismoAdmin"
                            checked={esMismoAdmin}
                            onChange={(e) => setEsMismoAdmin(e.target.checked)}
                          />
                          <span>
                            <span className="m-pub-check-title">
                              Soy el administrador principal de la cuenta
                            </span>
                            <span className="m-pub-check-text">
                              Tus datos pasarán al siguiente paso y solo tendrás que definir tu usuario de
                              acceso.
                            </span>
                          </span>
                        </label>
                      </div>
                    </>
                  )}

                  {/* Ubicación territorial en cascada (común a ambas modalidades) */}
                  <div className="m-pub-group">
                    <p className="m-pub-group-title">
                      <MapPin aria-hidden="true" />
                      Ubicación en Colombia
                    </p>

                    <div className="m-pub-grid">
                      <Field id="departamento" label="Departamento" required>
                        <select
                          id="departamento"
                          value={orgForm.departamento}
                          onChange={(e) => {
                            const newDep = e.target.value;
                            const fallbackCities = COLOMBIA_LOCATIONS[newDep] || [];
                            setOrgForm({
                              ...orgForm,
                              departamento: newDep,
                              ciudad: fallbackCities.length > 0 ? fallbackCities[0] : '',
                            });
                            markTouched('departamento');
                          }}
                          onBlur={() => markTouched('departamento')}
                          className={selectClass}
                          {...fieldA11y('departamento', orgErrors.departamento)}
                        >
                          <option value="">Selecciona un departamento</option>
                          {deptosList.map((dep) => (
                            <option key={dep} value={dep}>
                              {dep}
                            </option>
                          ))}
                        </select>
                        {renderFieldFeedback(
                          'departamento',
                          orgErrors.departamento,
                          '32 departamentos y Bogotá D.C.'
                        )}
                      </Field>

                      <Field id="ciudad" label="Ciudad o municipio" required>
                        <select
                          id="ciudad"
                          value={orgForm.ciudad}
                          onChange={(e) => {
                            setOrgForm({ ...orgForm, ciudad: e.target.value });
                            markTouched('ciudad');
                          }}
                          onBlur={() => markTouched('ciudad')}
                          className={selectClass}
                          aria-busy={loadingCiudades}
                          {...fieldA11y('ciudad', orgErrors.ciudad)}
                        >
                          <option value="">
                            {orgForm.departamento
                              ? loadingCiudades && ciudadesList.length === 0
                                ? 'Cargando municipios...'
                                : 'Selecciona una ciudad o municipio'
                              : 'Primero selecciona el departamento'}
                          </option>
                          {ciudadesList.map((city) => (
                            <option key={city} value={city}>
                              {city}
                            </option>
                          ))}
                        </select>
                        {renderFieldFeedback(
                          'ciudad',
                          orgErrors.ciudad,
                          loadingCiudades
                            ? 'Consultando los municipios del departamento...'
                            : 'Municipio según el departamento'
                        )}
                      </Field>
                    </div>

                    <Field
                      id="direccion"
                      label={`Dirección ${tipoRegistro === 'JURIDICA' ? 'comercial' : 'del inmueble o edificio'}`}
                    >
                      <input
                        id="direccion"
                        placeholder="ej. Calle 100 # 15-20 Of. 501"
                        maxLength={150}
                        autoComplete="street-address"
                        value={orgForm.direccion}
                        onChange={(e) =>
                          setOrgForm({ ...orgForm, direccion: e.target.value.slice(0, 150) })
                        }
                        className={getInputClass('direccion', null)}
                        aria-describedby="fb-direccion"
                      />
                      <p id="fb-direccion" className="m-pub-hint">
                        Opcional, para facturación o ubicación del inmueble
                      </p>
                    </Field>
                  </div>
                </div>

                <div className="m-pub-actions">
                  <button type="button" onClick={() => setStep(1)} className="m-btn m-btn--glass">
                    <ArrowLeft aria-hidden="true" />
                    <span>Volver</span>
                  </button>
                  <button type="button" onClick={handleAvanzarPaso2} className="m-btn m-btn--primary m-btn--lg">
                    <span>Continuar a administrador</span>
                    <ArrowRight aria-hidden="true" />
                  </button>
                </div>
              </>
            )}

            {/* PASO 3: Administrador principal */}
            {step === 3 && (
              <>
                <button type="button" onClick={() => setStep(2)} className="m-btn m-btn--quiet m-pub-back">
                  <ArrowLeft aria-hidden="true" />
                  <span>Volver a organización</span>
                </button>
                <div className="m-pub-head">
                  <h1 className="m-h3">Administrador Principal de la Cuenta</h1>
                  <p className="m-body">
                    Esta persona administrará la organización en SAED y recibirá el enlace para activar
                    su acceso.
                  </p>
                </div>

                {tipoRegistro === 'NATURAL' && esMismoAdmin && (
                  <div className="m-pub-note is-ok" role="status">
                    <CheckCircle2 aria-hidden="true" />
                    <div>
                      <p className="m-pub-note-title">
                        Datos personales transferidos de tu registro de propietario
                      </p>
                      <p className="m-pub-note-text">
                        Precargamos tus datos. Define tu <strong>nombre de usuario</strong> para iniciar
                        sesión en SAED.
                      </p>
                    </div>
                  </div>
                )}

                <div className="m-pub-form">
                  <div className="m-pub-group">
                    <div className="m-pub-grid">
                      <Field id="pNombre" label="Primer nombre" required>
                        <input
                          id="pNombre"
                          placeholder="ej. Carlos"
                          maxLength={25}
                          autoComplete="given-name"
                          value={adminForm.primerNombre}
                          onChange={(e) => {
                            setAdminForm({
                              ...adminForm,
                              primerNombre: soloLetras(e.target.value, 25),
                            });
                            markTouched('primerNombre');
                          }}
                          onBlur={() => markTouched('primerNombre')}
                          className={getInputClass('primerNombre', adminErrors.primerNombre)}
                          {...fieldA11y('primerNombre', adminErrors.primerNombre)}
                        />
                        {renderFieldFeedback(
                          'primerNombre',
                          adminErrors.primerNombre,
                          'Máx. 25 caracteres, solo letras'
                        )}
                      </Field>

                      <Field id="sNombre" label="Segundo nombre">
                        <input
                          id="sNombre"
                          placeholder="ej. Andrés"
                          maxLength={25}
                          value={adminForm.segundoNombre}
                          onChange={(e) => {
                            setAdminForm({
                              ...adminForm,
                              segundoNombre: soloLetras(e.target.value, 25),
                            });
                            markTouched('segundoNombre');
                          }}
                          onBlur={() => markTouched('segundoNombre')}
                          className={getInputClass('segundoNombre', adminErrors.segundoNombre)}
                          {...fieldA11y('segundoNombre', adminErrors.segundoNombre)}
                        />
                        {renderFieldFeedback(
                          'segundoNombre',
                          adminErrors.segundoNombre,
                          'Opcional, máx. 25 caracteres'
                        )}
                      </Field>

                      <Field id="pApellido" label="Primer apellido" required>
                        <input
                          id="pApellido"
                          placeholder="ej. Rodríguez"
                          maxLength={25}
                          autoComplete="family-name"
                          value={adminForm.primerApellido}
                          onChange={(e) => {
                            setAdminForm({
                              ...adminForm,
                              primerApellido: soloLetras(e.target.value, 25),
                            });
                            markTouched('primerApellido');
                          }}
                          onBlur={() => markTouched('primerApellido')}
                          className={getInputClass('primerApellido', adminErrors.primerApellido)}
                          {...fieldA11y('primerApellido', adminErrors.primerApellido)}
                        />
                        {renderFieldFeedback(
                          'primerApellido',
                          adminErrors.primerApellido,
                          'Máx. 25 caracteres, solo letras'
                        )}
                      </Field>

                      <Field id="sApellido" label="Segundo apellido">
                        <input
                          id="sApellido"
                          placeholder="ej. Pérez"
                          maxLength={25}
                          value={adminForm.segundoApellido}
                          onChange={(e) => {
                            setAdminForm({
                              ...adminForm,
                              segundoApellido: soloLetras(e.target.value, 25),
                            });
                            markTouched('segundoApellido');
                          }}
                          onBlur={() => markTouched('segundoApellido')}
                          className={getInputClass('segundoApellido', adminErrors.segundoApellido)}
                          {...fieldA11y('segundoApellido', adminErrors.segundoApellido)}
                        />
                        {renderFieldFeedback(
                          'segundoApellido',
                          adminErrors.segundoApellido,
                          'Opcional, máx. 25 caracteres'
                        )}
                      </Field>
                    </div>

                    <div className="m-pub-grid m-pub-grid--doc">
                      <Field id="tipoDoc" label="Tipo de documento" required>
                        <select
                          id="tipoDoc"
                          value={adminForm.tipoDocumento}
                          onChange={(e) => {
                            setAdminForm({ ...adminForm, tipoDocumento: e.target.value });
                            markTouched('numeroDocumento');
                          }}
                          className={selectClass}
                        >
                          <option value="CC">C.C. Cédula de ciudadanía</option>
                          <option value="CE">C.E. Cédula de extranjería</option>
                          <option value="PAS">Pasaporte</option>
                          <option value="PPT">Permiso por protección temporal</option>
                          <option value="NIT">NIT</option>
                        </select>
                      </Field>

                      <Field id="numDoc" label="Número de documento" required>
                        <input
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
                          {...fieldA11y('numeroDocumento', adminErrors.numeroDocumento)}
                        />
                        {renderFieldFeedback(
                          'numeroDocumento',
                          adminErrors.numeroDocumento,
                          getDocHint(adminForm.tipoDocumento)
                        )}
                      </Field>
                    </div>
                  </div>

                  <div className="m-pub-group">
                    <div className="m-pub-grid">
                      <Field id="adminUsername" label="Usuario para iniciar sesión" required>
                        <input
                          id="adminUsername"
                          placeholder="ej. carlos.admin"
                          maxLength={30}
                          autoComplete="username"
                          value={adminForm.adminUsername}
                          onChange={(e) => {
                            const val = e.target.value
                              .toLowerCase()
                              .replace(/[^a-z0-9._-]/g, '')
                              .slice(0, 30);
                            setAdminForm({ ...adminForm, adminUsername: val });
                            markTouched('adminUsername');
                          }}
                          onBlur={() => markTouched('adminUsername')}
                          className={getInputClass('adminUsername', adminErrors.adminUsername)}
                          {...fieldA11y('adminUsername', adminErrors.adminUsername)}
                        />
                        {renderFieldFeedback(
                          'adminUsername',
                          adminErrors.adminUsername,
                          'Mínimo 3 caracteres: minúsculas, números, punto o guion'
                        )}
                      </Field>

                      <Field id="adminEmail" label="Correo electrónico (recibe el enlace de activación)" required>
                        <input
                          id="adminEmail"
                          type="email"
                          maxLength={60}
                          placeholder="carlos@tuempresa.com"
                          autoComplete="email"
                          value={adminForm.email}
                          onChange={(e) => {
                            setAdminForm({ ...adminForm, email: e.target.value.trim().slice(0, 60) });
                            markTouched('email');
                          }}
                          onBlur={() => markTouched('email')}
                          className={getInputClass('email', adminErrors.email)}
                          {...fieldA11y('email', adminErrors.email)}
                        />
                        {renderFieldFeedback(
                          'email',
                          adminErrors.email,
                          'Aquí enviaremos el enlace para crear tu contraseña'
                        )}
                      </Field>
                    </div>

                    <Field id="adminTel" label="Teléfono celular (10 dígitos)" required>
                      <input
                        id="adminTel"
                        inputMode="tel"
                        placeholder="ej. 3101234567"
                        maxLength={10}
                        autoComplete="tel-national"
                        value={adminForm.telefono}
                        onChange={(e) => {
                          const val = soloNumeros(e.target.value, 10);
                          setAdminForm({ ...adminForm, telefono: val });
                          markTouched('telefono');
                        }}
                        onBlur={() => markTouched('telefono')}
                        className={getInputClass('telefono', adminErrors.telefono)}
                        {...fieldA11y('telefono', adminErrors.telefono)}
                      />
                      {renderFieldFeedback('telefono', adminErrors.telefono, 'Celular colombiano de 10 dígitos')}
                    </Field>
                  </div>

                  <div className="m-pub-note is-info">
                    <Mail aria-hidden="true" />
                    <div>
                      <p className="m-pub-note-title">La contraseña la defines tú</p>
                      <p className="m-pub-note-text">
                        Al completar el registro enviaremos un enlace de activación al correo del
                        administrador para que cree su contraseña.
                      </p>
                    </div>
                  </div>

                  <dl className="m-pub-dl">
                    <div>
                      <dt>Plan seleccionado</dt>
                      <dd>
                        {currentPlan?.nombre}{' '}
                        <span className="m-pub-tag">{billingCycle === 'ANUAL' ? 'Anual' : 'Mensual'}</span>
                      </dd>
                    </div>
                    <div className="is-total">
                      <dt>Total</dt>
                      <dd>{totalLabel}</dd>
                    </div>
                  </dl>
                </div>

                {submitError && (
                  <div className="m-pub-note is-error" role="alert">
                    <AlertCircle aria-hidden="true" />
                    <div style={{ display: 'grid', gap: 8, justifyItems: 'start' }}>
                      <div>
                        <p className="m-pub-note-title">No se pudo completar el registro</p>
                        <p className="m-pub-note-text">{submitError}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          setSubmitError(null);
                          setStep(2);
                        }}
                        className="m-link"
                        style={{ minHeight: 40, fontWeight: 700 }}
                      >
                        Modificar los datos de la organización (paso 2)
                      </button>
                    </div>
                  </div>
                )}

                <div className="m-pub-actions">
                  <button type="button" onClick={() => setStep(2)} className="m-btn m-btn--glass">
                    <ArrowLeft aria-hidden="true" />
                    <span>Volver</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleSubmitRegistro}
                    disabled={submitting}
                    className="m-btn m-btn--primary m-btn--lg"
                  >
                    {submitting ? (
                      <>
                        <Loader2 className="m-pub-spin" aria-hidden="true" />
                        <span>Creando organización...</span>
                      </>
                    ) : (
                      <>
                        <span>
                          {pricing.totalPesos > 0
                            ? 'Confirmar y proceder al pago Wompi'
                            : 'Crear organización y activar prueba'}
                        </span>
                        <ArrowRight aria-hidden="true" />
                      </>
                    )}
                  </button>
                </div>
              </>
            )}

            {/* PASO 4: Confirmación, pago Wompi o activación */}
            {step === 4 && registroResultado && (
              <>
                {!registroResultado.requierePago ? (
                  <div className="m-pub-result">
                    <span className="m-pub-result-piece is-ok" aria-hidden="true">
                      <CheckCircle2 />
                    </span>
                    <div className="m-pub-head">
                      <h1 className="m-h3">Organización creada</h1>
                      <p className="m-body">
                        El período de prueba quedó activo para{' '}
                        <strong className="m-strong">{orgForm.nombreOrganizacion}</strong>.
                      </p>
                    </div>
                    <dl className="m-pub-dl m-pub-block">
                      <div>
                        <dt>Usuario de acceso</dt>
                        <dd className="m-pub-mono">
                          {registroResultado.adminUsername || adminForm.adminUsername}
                        </dd>
                      </div>
                      <div>
                        <dt>Correo registrado</dt>
                        <dd>{registroResultado.email || adminForm.email}</dd>
                      </div>
                    </dl>
                    <div className="m-pub-note is-ok m-pub-block">
                      <Mail aria-hidden="true" />
                      <div>
                        <p className="m-pub-note-title">Revisa tu correo</p>
                        <p className="m-pub-note-text">
                          Enviamos un enlace de activación para que crees tu contraseña. Después podrás
                          iniciar sesión.
                        </p>
                      </div>
                    </div>
                    <Link to="/login" className="m-btn m-btn--primary m-btn--lg">
                      <span>Ir al inicio de sesión</span>
                      <ArrowRight aria-hidden="true" />
                    </Link>
                  </div>
                ) : !pagoAprobado ? (
                  <>
                    <div className="m-pub-head">
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
                        <h1 className="m-h3">Pago de la membresía</h1>
                        <span className="m-pub-tag is-warn">Pendiente de pago</span>
                      </div>
                      <p className="m-body">
                        Organización: <strong className="m-strong">{orgForm.nombreOrganizacion}</strong>
                      </p>
                    </div>

                    <dl className="m-pub-dl">
                      <div>
                        <dt>Referencia de pago</dt>
                        <dd className="m-pub-mono">{registroResultado.referencia}</dd>
                      </div>
                      <div>
                        <dt>Plan contratado</dt>
                        <dd>
                          {currentPlan?.nombre} ({billingCycle === 'ANUAL' ? 'anual' : 'mensual'})
                        </dd>
                      </div>
                      <div className="is-total">
                        <dt>Total a pagar</dt>
                        <dd>
                          ${(registroResultado.montoPesos || pricing.totalPesos).toLocaleString('es-CO')}{' '}
                          COP
                        </dd>
                      </div>
                    </dl>

                    <div className="m-pub-note is-info">
                      <CreditCard aria-hidden="true" />
                      <div>
                        <p className="m-pub-note-title">Pago seguro con Wompi</p>
                        <p className="m-pub-note-text">
                          Puedes pagar con PSE o con tarjeta. Cuando la pasarela confirme el pago, la
                          cuenta se activa y enviamos el enlace de activación al administrador.
                        </p>
                      </div>
                    </div>

                    <div className="m-pub-actions">
                      <button type="button" onClick={verificarEstadoPago} className="m-btn m-btn--glass">
                        <RefreshCw aria-hidden="true" />
                        <span>Verificar estado</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => lanzarWidgetWompi(registroResultado)}
                        className="m-btn m-btn--primary m-btn--lg"
                      >
                        <CreditCard aria-hidden="true" />
                        <span>Abrir pasarela de pago Wompi</span>
                      </button>
                    </div>

                    {pollingPago && (
                      <p className="m-pub-status" role="status" aria-live="polite">
                        <Loader2 className="m-pub-spin" aria-hidden="true" />
                        Esperando la confirmación automática de la pasarela...
                      </p>
                    )}
                  </>
                ) : (
                  <div className="m-pub-result" role="status">
                    <span className="m-pub-result-piece is-ok" aria-hidden="true">
                      <CheckCircle2 />
                    </span>
                    <div className="m-pub-head">
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
                        <h1 className="m-h3">Pago aprobado y organización activada</h1>
                        <span className="m-pub-tag is-ok">Pago aprobado</span>
                      </div>
                      <p className="m-body">
                        Recibimos el pago de la membresía. Enviamos el enlace de activación del usuario{' '}
                        <strong className="m-strong m-pub-mono">{adminForm.adminUsername}</strong> a{' '}
                        <strong className="m-strong">{adminForm.email}</strong>.
                      </p>
                    </div>
                    <Link to="/login" className="m-btn m-btn--primary m-btn--lg">
                      <span>Ir a la plataforma SAED</span>
                      <ArrowRight aria-hidden="true" />
                    </Link>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

function Field({ id, label, required = false, children }) {
  return (
    <div className="m-pub-field">
      <label htmlFor={id} className="m-pub-label">
        {label}
        {required && (
          <span className="m-pub-req" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
    </div>
  );
}
