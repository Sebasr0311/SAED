import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import {
  User,
  Lock,
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  Building2,
  ShieldCheck,
  QrCode,
  CreditCard,
  Package,
  AlertCircle,
  Loader2,
  Sparkles
} from 'lucide-react';
import { useAuth } from '../lib/AuthContext.jsx';
import { ROLE_HOME, roleCanAccess } from '../lib/access.js';
import { valUsername, valPassword } from '../lib/validation.js';

export default function LoginPage() {
  const { login, loading, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState(() => localStorage.getItem('remembered_user') || '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => !!localStorage.getItem('remembered_user'));
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    document.title = 'Iniciar sesión — SAED';
  }, []);

  useEffect(() => {
    if (isAuthenticated && user) {
      const dest = ROLE_HOME[user.rol] || '/dashboard';
      const from = location.state?.from?.pathname;
      const target = from && roleCanAccess(from, user.rol) ? from : dest;
      navigate(target, { replace: true });
    }
  }, [isAuthenticated, user, navigate, location]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    const u = valUsername(username);
    if (!u.ok) { setError(u.mensaje); return; }
    const p = valPassword(password);
    if (!p.ok) { setError(p.mensaje); return; }
    try {
      await login(username.trim(), password);
      if (remember) localStorage.setItem('remembered_user', username.trim());
      else localStorage.removeItem('remembered_user');
      toast.success(`Bienvenido, ${u.username}`);
    } catch (err) {
      setError(err.message);
      toast.error(err.message);
    }
  }

  return (
    <div className="min-h-screen bg-[#0A1628] text-white flex flex-col justify-between relative overflow-x-hidden selection:bg-emerald-500/20 selection:text-emerald-400">
      {/* Background Decorative Accents */}
      <div className="absolute inset-0 bg-[radial-gradient(#1E4080_1px,transparent_1px)] [background-size:24px_24px] opacity-25 pointer-events-none" />
      <div className="absolute top-1/4 left-1/4 w-[500px] h-[350px] bg-emerald-500/10 blur-[130px] rounded-full pointer-events-none" />
      <div className="absolute bottom-10 right-1/4 w-[450px] h-[300px] bg-blue-600/10 blur-[120px] rounded-full pointer-events-none" />

      {/* Top Bar with Return to Landing */}
      <header className="relative z-10 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-2">
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-xs sm:text-sm font-semibold text-slate-300 hover:text-white bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 transition-colors py-2 px-3.5 rounded-xl backdrop-blur-sm shadow-sm"
        >
          <ArrowLeft className="w-4 h-4 text-emerald-400" />
          <span>Volver al inicio</span>
        </Link>
      </header>

      {/* Main Grid: Form Card + Value Panel */}
      <main className="relative z-10 flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8">
        <div className="w-full max-w-6xl grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-12 items-center">
          
          {/* Form Card (Priority 1) */}
          <div className="w-full max-w-md lg:max-w-lg mx-auto">
            <div className="bg-[#0F172A]/95 border border-slate-800/90 rounded-2xl p-6 sm:p-8 lg:p-10 shadow-2xl shadow-black/60 backdrop-blur-xl">
              
              {/* Header */}
              <div className="text-center sm:text-left mb-8">
                <div className="flex items-center justify-center sm:justify-start gap-3 mb-4">
                  <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#1E4080] to-[#0A1628] border border-emerald-500/30 flex items-center justify-center shadow-md shadow-emerald-500/10">
                    <Building2 className="w-5 h-5 text-emerald-400" />
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-2xl font-black tracking-tight text-white font-['Plus_Jakarta_Sans']">
                      SAED
                    </span>
                    <span className="px-1.5 py-0.5 rounded text-xs font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      2.0
                    </span>
                  </div>
                </div>

                <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white font-['Plus_Jakarta_Sans']">
                  Iniciar sesión
                </h1>
                <p className="text-xs sm:text-sm text-slate-400 mt-1.5 leading-relaxed">
                  Ingresa tus credenciales para acceder a la gestión de tu copropiedad
                </p>
              </div>

              {/* Login Form */}
              <form onSubmit={handleSubmit} className="space-y-5" noValidate>
                {/* Username Input */}
                <div className="space-y-1.5">
                  <label
                    htmlFor="login-username"
                    className="block text-xs sm:text-sm font-medium text-slate-300"
                  >
                    Usuario
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                      <User className="w-4 h-4" />
                    </div>
                    <input
                      id="login-username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Nombre de usuario"
                      autoComplete="username"
                      required
                      className="w-full h-12 pl-10 pr-4 rounded-xl bg-slate-900/90 border border-slate-700/80 text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 transition-colors"
                    />
                  </div>
                </div>

                {/* Password Input */}
                <div className="space-y-1.5">
                  <label
                    htmlFor="login-password"
                    className="block text-xs sm:text-sm font-medium text-slate-300"
                  >
                    Contraseña
                  </label>
                  <div className="relative">
                    <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                      <Lock className="w-4 h-4" />
                    </div>
                    <input
                      id="login-password"
                      type={showPwd ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      autoComplete="current-password"
                      required
                      className="w-full h-12 pl-10 pr-12 rounded-xl bg-slate-900/90 border border-slate-700/80 text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 transition-colors"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPwd((s) => !s)}
                      className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-white transition-colors focus:outline-none"
                      aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showPwd ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Remember & Forgot options */}
                <div className="flex items-center justify-between text-xs sm:text-sm pt-1">
                  <label className="flex items-center gap-2 cursor-pointer text-slate-300 select-none">
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(e) => setRemember(e.target.checked)}
                      className="w-4 h-4 rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500/30 focus:ring-offset-0 transition-colors"
                    />
                    <span>Recordarme</span>
                  </label>

                  <button
                    type="button"
                    onClick={() =>
                      toast.info('Contacte a la administración de su copropiedad para restablecer su acceso')
                    }
                    className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors focus:outline-none focus:underline"
                  >
                    ¿Olvidaste tu contraseña?
                  </button>
                </div>

                {/* Error message */}
                {error && (
                  <div
                    className="p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs sm:text-sm flex items-start gap-2.5"
                    role="alert"
                  >
                    <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                    <span>{error}</span>
                  </div>
                )}

                {/* Submit button */}
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full min-h-[48px] px-6 py-3.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-sm sm:text-base shadow-lg shadow-emerald-950/40 hover:shadow-emerald-900/50 transition-all flex items-center justify-center gap-2 focus:outline-none focus:ring-2 focus:ring-emerald-400 disabled:opacity-60 disabled:cursor-not-allowed transform active:scale-[0.99]"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span>Iniciando sesión...</span>
                    </>
                  ) : (
                    <>
                      <span>Iniciar Sesión</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>

              {/* Card Footer */}
              <div className="mt-8 pt-6 border-t border-slate-800 text-center">
                <p className="text-xs text-slate-500">
                  © {new Date().getFullYear()} SAED 2.0 — Sistema Automatizado para Edificios Digitales
                </p>
              </div>
            </div>
          </div>

          {/* Value Panel (Desktop: Priority 2 / Hidden on Tablet & Mobile) */}
          <div className="max-lg:hidden flex flex-col justify-between p-6 xl:p-8 space-y-6">
            <div className="space-y-6">
              <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-semibold tracking-wide">
                <Sparkles className="w-3.5 h-3.5" />
                <span>ECOSISTEMA INTEGRAL DE COPROPIEDAD</span>
              </div>

              <h2 className="text-2xl xl:text-3xl font-extrabold text-white tracking-tight leading-snug font-['Plus_Jakarta_Sans']">
                Seguridad, finanzas y control operativo en una sola plataforma
              </h2>

              <p className="text-sm text-slate-300 leading-relaxed">
                SAED conecta la administración, el personal de garita y a los residentes para una operación fluida, transparente y sin papel.
              </p>

              {/* Core Feature Value Pills */}
              <div className="space-y-3.5 pt-2">
                <div className="flex items-start gap-3 p-3.5 rounded-xl bg-slate-900/70 border border-slate-800 hover:border-slate-700 transition-colors">
                  <div className="w-9 h-9 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 shrink-0">
                    <QrCode className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold text-white">Control QR de Visitantes</h3>
                    <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                      Pases temporales con validación ágil en portería y bitácora de auditoría.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 p-3.5 rounded-xl bg-slate-900/70 border border-slate-800 hover:border-slate-700 transition-colors">
                  <div className="w-9 h-9 rounded-lg bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 shrink-0">
                    <CreditCard className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold text-white">Cartera con Pasarela Wompi</h3>
                    <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                      Recaudo digital mediante PSE y tarjetas con conciliación bancaria inmediata.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 p-3.5 rounded-xl bg-slate-900/70 border border-slate-800 hover:border-slate-700 transition-colors">
                  <div className="w-9 h-9 rounded-lg bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400 shrink-0">
                    <Package className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold text-white">Paquetería con PIN & Parqueaderos</h3>
                    <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                      Custodia de encomiendas por clave de seguridad y gestión dinámica de cupos.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 p-3.5 rounded-xl bg-slate-900/70 border border-slate-800 hover:border-slate-700 transition-colors">
                  <div className="w-9 h-9 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 shrink-0">
                    <ShieldCheck className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-xs sm:text-sm font-bold text-white">Aislamiento Multi-Tenant</h3>
                    <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                      Información protegida y separada para cada edificio o conjunto residencial.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Cloud Status Pill */}
            <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-500/20 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-xs font-semibold text-emerald-300">
                  Infraestructura Cloud Activa
                </span>
              </div>
              <span className="text-xs text-slate-400">
                Conexión segura TLS
              </span>
            </div>
          </div>

        </div>
      </main>

      {/* Footer */}
      <footer className="relative z-10 py-4 px-6 text-center text-xs text-slate-500 border-t border-slate-800/50">
        SAED 2.0 — Plataforma Integral de Gestión y Seguridad para Copropiedades
      </footer>
    </div>
  );
}
