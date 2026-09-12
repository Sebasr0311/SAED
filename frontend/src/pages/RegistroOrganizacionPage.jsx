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
} from 'lucide-react';
import api from '../lib/api.js';
import { Button } from '../components/ui/button.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '../components/ui/card.tsx';
import { toast } from 'sonner';

export default function RegistroOrganizacionPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // URL Params pre-select
  const initialPlanCode = searchParams.get('plan') || 'PRO';
  const initialCycle = searchParams.get('cycle') || 'ANUAL';

  // Wizard state
  const [step, setStep] = useState(1);
  const [planes, setPlanes] = useState([]);
  const [loadingPlanes, setLoadingPlanes] = useState(true);

  // Selection
  const [selectedPlanId, setSelectedPlanId] = useState(null);
  const [billingCycle, setBillingCycle] = useState(initialCycle.toUpperCase() === 'MENSUAL' ? 'MENSUAL' : 'ANUAL');

  // Form State - Organización
  const [orgForm, setOrgForm] = useState({
    nombreOrganizacion: '',
    nit: '',
    ciudad: 'Bogotá D.C.',
    direccion: '',
    telefonoContacto: '',
    emailContacto: '',
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
        setPlanes(data);

        // Match initial plan
        if (data.length > 0) {
          const matched = data.find(
            (p) =>
              p.codigo?.toUpperCase() === initialPlanCode.toUpperCase() ||
              p.idPlan?.toString() === initialPlanCode
          );
          if (matched) {
            setSelectedPlanId(matched.idPlan);
          } else {
            // Default to first commercial or free
            const proPlan = data.find((p) => p.codigo === 'PRO');
            setSelectedPlanId(proPlan ? proPlan.idPlan : data[0].idPlan);
          }
        }
      } catch (err) {
        console.error('Error cargando planes:', err);
        toast.error('No se pudo cargar el catálogo de planes. Intenta de nuevo.');
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

  // Form Validations
  const validateOrg = () => {
    if (!orgForm.nombreOrganizacion.trim()) {
      toast.error('El nombre de la organización es obligatorio.');
      return false;
    }
    if (!orgForm.nit.trim()) {
      toast.error('El NIT o identificación tributaria es obligatorio.');
      return false;
    }
    if (!orgForm.emailContacto.trim() || !orgForm.emailContacto.includes('@')) {
      toast.error('Ingresa un correo de contacto corporativo válido.');
      return false;
    }
    return true;
  };

  const validateAdmin = () => {
    if (!adminForm.primerNombre.trim()) {
      toast.error('El primer nombre del administrador es obligatorio.');
      return false;
    }
    if (!adminForm.primerApellido.trim()) {
      toast.error('El primer apellido del administrador es obligatorio.');
      return false;
    }
    if (!adminForm.numeroDocumento.trim()) {
      toast.error('El número de documento es obligatorio.');
      return false;
    }
    if (!adminForm.adminUsername || adminForm.adminUsername.trim().length < 3) {
      toast.error('Ingresa un nombre de usuario para iniciar sesión (mínimo 3 caracteres alfanuméricos).');
      return false;
    }
    if (!adminForm.email.trim() || !adminForm.email.includes('@')) {
      toast.error('Ingresa un correo electrónico válido para el administrador.');
      return false;
    }
    if (!adminForm.telefono.trim()) {
      toast.error('El teléfono celular del administrador es obligatorio.');
      return false;
    }
    return true;
  };

  // Submit Handler
  const handleSubmitRegistro = async () => {
    if (!validateOrg() || !validateAdmin()) return;

    try {
      setSubmitting(true);
      const payload = {
        nombreOrganizacion: orgForm.nombreOrganizacion.trim(),
        nit: orgForm.nit.trim(),
        ciudad: orgForm.ciudad.trim(),
        direccion: orgForm.direccion.trim() || 'No especificada',
        telefonoContacto: orgForm.telefonoContacto.trim() || adminForm.telefono.trim(),
        emailContacto: orgForm.emailContacto.trim(),
        adminPrimerNombre: adminForm.primerNombre.trim(),
        adminSegundoNombre: adminForm.segundoNombre.trim() || null,
        adminPrimerApellido: adminForm.primerApellido.trim(),
        adminSegundoApellido: adminForm.segundoApellido.trim() || null,
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
        {/* PASO 2: Datos de la Organización */}
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
                Información institucional para la facturación y la creación de tu tenant aislado.
              </p>
            </div>

            <Card className="bg-slate-900/80 border-slate-800 rounded-3xl p-6 space-y-4 shadow-xl">
              <div className="space-y-2">
                <Label htmlFor="nombreOrg" className="text-xs text-slate-300 font-semibold">
                  Nombre de la Organización / Razón Social *
                </Label>
                <Input
                  id="nombreOrg"
                  placeholder="ej. Inversiones Inmobiliarias del Norte S.A.S."
                  value={orgForm.nombreOrganizacion}
                  onChange={(e) => setOrgForm({ ...orgForm, nombreOrganizacion: e.target.value })}
                  className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="nit" className="text-xs text-slate-300 font-semibold">
                    NIT / Identificación Tributaria *
                  </Label>
                  <Input
                    id="nit"
                    placeholder="ej. 901234567-8"
                    value={orgForm.nit}
                    onChange={(e) => setOrgForm({ ...orgForm, nit: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ciudad" className="text-xs text-slate-300 font-semibold">
                    Ciudad *
                  </Label>
                  <Input
                    id="ciudad"
                    placeholder="ej. Bogotá D.C."
                    value={orgForm.ciudad}
                    onChange={(e) => setOrgForm({ ...orgForm, ciudad: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="direccion" className="text-xs text-slate-300 font-semibold">
                  Dirección Comercial
                </Label>
                <Input
                  id="direccion"
                  placeholder="ej. Calle 100 # 15-20 Of. 501"
                  value={orgForm.direccion}
                  onChange={(e) => setOrgForm({ ...orgForm, direccion: e.target.value })}
                  className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="emailContacto" className="text-xs text-slate-300 font-semibold">
                    Correo Electrónico Corporativo *
                  </Label>
                  <Input
                    id="emailContacto"
                    type="email"
                    placeholder="admin@tuempresa.com"
                    value={orgForm.emailContacto}
                    onChange={(e) => setOrgForm({ ...orgForm, emailContacto: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="telContacto" className="text-xs text-slate-300 font-semibold">
                    Teléfono Corporativo
                  </Label>
                  <Input
                    id="telContacto"
                    placeholder="ej. 6013004000"
                    value={orgForm.telefonoContacto}
                    onChange={(e) => setOrgForm({ ...orgForm, telefonoContacto: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
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
                onClick={() => {
                  if (validateOrg()) setStep(3);
                }}
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
                Esta persona tendrá el rol de <strong className="text-cyan-400">ADMIN_ORGANIZACION</strong> y recibirá el enlace seguro de activación para definir su contraseña.
              </p>
            </div>

            <Card className="bg-slate-900/80 border-slate-800 rounded-3xl p-6 space-y-4 shadow-xl">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="pNombre" className="text-xs text-slate-300 font-semibold">
                    Primer Nombre *
                  </Label>
                  <Input
                    id="pNombre"
                    placeholder="ej. Carlos"
                    value={adminForm.primerNombre}
                    onChange={(e) => setAdminForm({ ...adminForm, primerNombre: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="sNombre" className="text-xs text-slate-300 font-semibold">
                    Segundo Nombre
                  </Label>
                  <Input
                    id="sNombre"
                    placeholder="ej. Andrés"
                    value={adminForm.segundoNombre}
                    onChange={(e) => setAdminForm({ ...adminForm, segundoNombre: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="pApellido" className="text-xs text-slate-300 font-semibold">
                    Primer Apellido *
                  </Label>
                  <Input
                    id="pApellido"
                    placeholder="ej. Rodríguez"
                    value={adminForm.primerApellido}
                    onChange={(e) => setAdminForm({ ...adminForm, primerApellido: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="sApellido" className="text-xs text-slate-300 font-semibold">
                    Segundo Apellido
                  </Label>
                  <Input
                    id="sApellido"
                    placeholder="ej. Pérez"
                    value={adminForm.segundoApellido}
                    onChange={(e) => setAdminForm({ ...adminForm, segundoApellido: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="tipoDoc" className="text-xs text-slate-300 font-semibold">
                    Tipo Doc. *
                  </Label>
                  <select
                    id="tipoDoc"
                    value={adminForm.tipoDocumento}
                    onChange={(e) => setAdminForm({ ...adminForm, tipoDocumento: e.target.value })}
                    className="w-full bg-slate-950 border border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 px-3 outline-none"
                  >
                    <option value="CC">C.C. Cédula</option>
                    <option value="CE">C.E. Extranjería</option>
                    <option value="PAS">Pasaporte</option>
                    <option value="NIT">NIT</option>
                  </select>
                </div>

                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="numDoc" className="text-xs text-slate-300 font-semibold">
                    Número de Documento *
                  </Label>
                  <Input
                    id="numDoc"
                    placeholder="ej. 1020304050"
                    value={adminForm.numeroDocumento}
                    onChange={(e) => setAdminForm({ ...adminForm, numeroDocumento: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="adminUsername" className="text-xs text-slate-300 font-semibold">
                    Usuario para Iniciar Sesión *
                  </Label>
                  <Input
                    id="adminUsername"
                    placeholder="ej. carlos.admin"
                    value={adminForm.adminUsername}
                    onChange={(e) =>
                      setAdminForm({
                        ...adminForm,
                        adminUsername: e.target.value.toLowerCase().replace(/[^a-z0-9._-]/g, '').slice(0, 30),
                      })
                    }
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3 font-mono"
                  />
                  <p className="text-[11px] text-slate-400">Este será su nombre de usuario único para ingresar a SAED.</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="adminEmail" className="text-xs text-slate-300 font-semibold">
                    Correo Electrónico (Recepción de Credenciales) *
                  </Label>
                  <Input
                    id="adminEmail"
                    type="email"
                    placeholder="carlos@tuempresa.com"
                    value={adminForm.email}
                    onChange={(e) => setAdminForm({ ...adminForm, email: e.target.value })}
                    className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                  />
                  <p className="text-[11px] text-slate-400">Aquí recibirá su usuario y contraseña temporal segura.</p>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="adminTel" className="text-xs text-slate-300 font-semibold">
                  Teléfono Celular *
                </Label>
                <Input
                  id="adminTel"
                  placeholder="ej. 3101234567"
                  value={adminForm.telefono}
                  onChange={(e) => setAdminForm({ ...adminForm, telefono: e.target.value.replace(/[^0-9+\s()-]/g, '').slice(0, 20) })}
                  className="bg-slate-950 border-slate-800 focus:border-cyan-400 text-white rounded-xl text-xs py-3"
                />
              </div>

              <div className="p-3.5 rounded-2xl bg-cyan-950/40 border border-cyan-800/40 text-xs text-cyan-300 flex items-start gap-3">
                <Lock className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold text-cyan-200">Asignación automática de contraseña por seguridad</p>
                  <p className="text-cyan-300/80 mt-0.5 text-[11px] leading-relaxed">
                    La contraseña de acceso será generada aleatoriamente por el sistema con cifrado de alta seguridad y despachada de inmediato a su correo electrónico. Una vez ingrese al sistema, tendrá la opción de cambiarla fácilmente en su perfil.
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
