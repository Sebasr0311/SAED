import { useEffect, useState, useMemo } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import {
  ShieldCheck,
  Lock,
  Eye,
  EyeOff,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  ArrowRight,
  RefreshCw,
  Mail,
  User,
} from 'lucide-react';
import api from '../lib/api.js';
import { Button } from '../components/ui/button.tsx';
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '../components/ui/card.tsx';
import { toast } from 'sonner';

export default function ActivarCuentaPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [tokenInfo, setTokenInfo] = useState(null);
  const [tokenError, setTokenError] = useState(null);

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  // Reenvío
  const [reenvioEmail, setReenvioEmail] = useState('');
  const [reenviando, setReenviando] = useState(false);
  const [reenvioExito, setReenvioExito] = useState(false);

  useEffect(() => {
    async function validar() {
      if (!token) {
        setTokenError('No se encontró ningún token de activación en el enlace.');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const res = await api.get(`/auth/activar/validar?token=${encodeURIComponent(token)}`);
        const data = res?.data || res;
        if (data && data.valido) {
          setTokenInfo(data);
          setTokenError(null);
        } else {
          setTokenError(data?.mensaje || 'El enlace de activación no es válido o ha expirado.');
        }
      } catch (err) {
        setTokenError(err.message || 'Error al verificar el token de activación.');
      } finally {
        setLoading(false);
      }
    }

    validar();
  }, [token]);

  // Validaciones de robustez de contraseña
  const criteria = useMemo(() => {
    return {
      length: password.length >= 8,
      upper: /[A-Z]/.test(password),
      lower: /[a-z]/.test(password),
      number: /[0-9]/.test(password),
      special: /[@#$%^&+=!._*-]/.test(password),
      match: password.length > 0 && password === confirmPassword,
    };
  }, [password, confirmPassword]);

  const isValidPassword =
    criteria.length &&
    criteria.upper &&
    criteria.lower &&
    criteria.number &&
    criteria.special &&
    criteria.match;

  async function handleActivar(e) {
    e.preventDefault();
    if (!isValidPassword) {
      toast.error('Por favor cumple con todos los requisitos de seguridad de la contraseña.');
      return;
    }

    try {
      setSubmitting(true);
      await api.post('/auth/activar/confirmar', {
        token,
        password,
        confirmPassword,
      });
      setSuccess(true);
      toast.success('¡Cuenta activada con éxito!');
    } catch (err) {
      toast.error(err.message || 'No se pudo activar la cuenta. Intente nuevamente.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSolicitarReenvio(e) {
    e.preventDefault();
    if (!reenvioEmail.trim()) return;

    try {
      setReenviando(true);
      await api.post('/auth/activar/solicitar-reenvio', {
        identificador: reenvioEmail.trim(),
      });
      setReenvioExito(true);
      toast.success('Si la cuenta existe, se ha enviado un nuevo enlace.');
    } catch (err) {
      toast.error(err.message || 'Error al solicitar el enlace.');
    } finally {
      setReenviando(false);
    }
  }

  return (
    <div className="min-h-screen bg-[#0A1628] flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden">
      {/* Background glow effects */}
      <div className="absolute top-1/4 -left-32 w-96 h-96 bg-primary/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-32 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

      {/* Header / Logo */}
      <div className="mb-8 text-center">
        <Link to="/" className="inline-flex items-center gap-2">
          <span className="text-3xl font-extrabold tracking-tight text-white">
            SAED <span className="text-emerald-400">2.0</span>
          </span>
        </Link>
        <p className="text-xs text-slate-400 mt-1 uppercase tracking-widest font-mono">
          Activación y Seguridad de Acceso
        </p>
      </div>

      <div className="w-full max-w-md">
        {loading ? (
          <Card className="bg-[#0F213A] border-slate-800 text-slate-100 p-8 text-center space-y-4">
            <RefreshCw className="h-10 w-10 animate-spin mx-auto text-primary" />
            <div className="space-y-1">
              <h3 className="text-base font-semibold text-white">Verificando enlace de activación...</h3>
              <p className="text-xs text-slate-400">Validando autenticidad del token criptográfico</p>
            </div>
          </Card>
        ) : success ? (
          <Card className="bg-[#0F213A] border-emerald-500/30 text-slate-100 p-8 text-center space-y-5 shadow-2xl">
            <div className="h-16 w-16 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="h-8 w-8" />
            </div>
            <div className="space-y-2">
              <h2 className="text-xl font-bold text-white">¡Cuenta Activada con Éxito!</h2>
              <p className="text-xs sm:text-sm text-slate-300">
                Tu contraseña ha sido establecida de forma segura. Ya puedes ingresar al portal con tu nombre de usuario o correo.
              </p>
            </div>
            <Button
              variant="primary"
              className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-semibold py-2.5"
              onClick={() => navigate('/login')}
            >
              Ir a Iniciar Sesión
              <ArrowRight className="h-4 w-4 ml-1.5" />
            </Button>
          </Card>
        ) : tokenError ? (
          <Card className="bg-[#0F213A] border-rose-500/30 text-slate-100 shadow-2xl overflow-hidden">
            <CardHeader className="text-center pb-3">
              <div className="h-12 w-12 bg-rose-500/10 border border-rose-500/30 text-rose-400 rounded-full flex items-center justify-center mx-auto mb-2">
                <AlertTriangle className="h-6 w-6" />
              </div>
              <CardTitle className="text-lg font-bold text-white">Enlace No Válido o Expirado</CardTitle>
              <p className="text-xs text-slate-400 mt-1">{tokenError}</p>
            </CardHeader>

            <CardContent className="space-y-4 pt-2">
              {reenvioExito ? (
                <div className="p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-lg text-xs text-emerald-300 text-center">
                  Si la cuenta existe, recibirás un nuevo enlace en tu correo electrónico registrado.
                </div>
              ) : (
                <form onSubmit={handleSolicitarReenvio} className="space-y-3">
                  <p className="text-xs text-slate-300">
                    Ingresa tu correo o usuario para recibir un nuevo enlace de activación:
                  </p>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500" />
                    <input
                      type="text"
                      required
                      value={reenvioEmail}
                      onChange={(e) => setReenvioEmail(e.target.value)}
                      placeholder="correo@ejemplo.com o usuario"
                      className="w-full pl-9 pr-3 py-2 text-xs bg-[#070B14] border border-slate-800 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-primary/40"
                    />
                  </div>
                  <Button
                    type="submit"
                    variant="ghost"
                    size="sm"
                    disabled={reenviando}
                    className="w-full bg-slate-900 hover:bg-slate-800 border border-slate-700 text-xs font-semibold text-white shadow-sm transition-colors"
                  >
                    {reenviando ? 'Enviando enlace...' : 'Solicitar Nuevo Enlace'}
                  </Button>
                </form>
              )}
            </CardContent>

            <CardFooter className="border-t border-slate-800 pt-3 justify-center">
              <Link to="/login" className="text-xs text-sky-400 hover:text-sky-300 hover:underline flex items-center gap-1 font-medium">
                Regresar al Inicio de Sesión
              </Link>
            </CardFooter>
          </Card>
        ) : (
          <Card className="bg-[#0F213A] border-slate-800 text-slate-100 shadow-2xl">
            <CardHeader className="pb-3 border-b border-slate-800">
              <div className="flex items-center gap-3">
                <div className="h-10 w-10 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0">
                  <ShieldCheck className="h-5 w-5" />
                </div>
                <div>
                  <CardTitle className="text-base font-bold text-white">
                    Configuración de Contraseña
                  </CardTitle>
                  <p className="text-xs text-slate-400">
                    Hola <strong className="text-slate-200">{tokenInfo?.primerNombre || tokenInfo?.nombreUsuario}</strong>, define tu clave de acceso
                  </p>
                </div>
              </div>
            </CardHeader>

            <form onSubmit={handleActivar}>
              <CardContent className="space-y-4 pt-4">
                {/* Datos de cuenta */}
                <div className="bg-[#070B14] border border-slate-800 rounded-lg p-3 space-y-1 text-xs text-slate-400">
                  <div className="flex justify-between">
                    <span>Usuario:</span>
                    <span className="font-mono text-slate-200 font-semibold">{tokenInfo?.nombreUsuario}</span>
                  </div>
                  {tokenInfo?.email && (
                    <div className="flex justify-between">
                      <span>Correo:</span>
                      <span className="text-slate-200">{tokenInfo?.email}</span>
                    </div>
                  )}
                </div>

                {/* Password input */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-200 block">
                    Nueva Contraseña *
                  </label>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••••••"
                      className="w-full px-3 py-2 pr-10 text-xs sm:text-sm bg-[#070B14] border border-slate-800 rounded-lg text-white placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-primary/40"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white"
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                {/* Confirm Password input */}
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-200 block">
                    Confirmar Contraseña *
                  </label>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className="w-full px-3 py-2 text-xs sm:text-sm bg-[#070B14] border border-slate-800 rounded-lg text-white placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-primary/40"
                  />
                </div>

                {/* Checklist de requisitos de seguridad */}
                <div className="bg-[#070B14]/70 border border-slate-800/80 rounded-lg p-3 space-y-1.5 text-[11px]">
                  <p className="font-semibold text-slate-300 mb-1">Requisitos de seguridad:</p>
                  <div className="grid grid-cols-1 gap-1">
                    <RequirementItem met={criteria.length} text="Mínimo 8 caracteres" />
                    <RequirementItem met={criteria.upper} text="Al menos una letra mayúscula (A-Z)" />
                    <RequirementItem met={criteria.lower} text="Al menos una letra minúscula (a-z)" />
                    <RequirementItem met={criteria.number} text="Al menos un número (0-9)" />
                    <RequirementItem met={criteria.special} text="Al menos un carácter especial (@#$%^&+=!._*-)" />
                    <RequirementItem met={criteria.match} text="Las contraseñas coinciden" />
                  </div>
                </div>
              </CardContent>

              <CardFooter className="border-t border-slate-800 pt-4 flex flex-col gap-2">
                <Button
                  type="submit"
                  variant="primary"
                  disabled={!isValidPassword || submitting}
                  className="w-full bg-primary hover:bg-primary/90 text-white font-semibold py-2.5 disabled:opacity-50"
                >
                  {submitting ? 'Activando cuenta...' : 'Activar Cuenta y Establecer Contraseña'}
                </Button>
                <div className="text-center">
                  <Link to="/login" className="text-xs text-slate-400 hover:text-white">
                    ¿Ya tienes cuenta activa? Iniciar sesión
                  </Link>
                </div>
              </CardFooter>
            </form>
          </Card>
        )}
      </div>
    </div>
  );
}

function RequirementItem({ met, text }) {
  return (
    <div className={`flex items-center gap-1.5 ${met ? 'text-emerald-400' : 'text-slate-500'}`}>
      {met ? <CheckCircle2 className="h-3 w-3 shrink-0" /> : <XCircle className="h-3 w-3 shrink-0" />}
      <span>{text}</span>
    </div>
  );
}
