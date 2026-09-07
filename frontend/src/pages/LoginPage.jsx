import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Building2,
  Check,
  CreditCard,
  Eye,
  EyeOff,
  LayoutDashboard,
  Loader2,
  Lock,
  Package,
  QrCode,
  Settings,
  ShieldCheck,
  Users,
  UserRound,
} from 'lucide-react';
import { useAuth } from '../lib/AuthContext.jsx';
import { ROLE_HOME, roleCanAccess } from '../lib/access.js';
import { valUsername, valPassword } from '../lib/validation.js';
import { warmUpBackend } from '../lib/api.js';

const SAED_LOGO = 'https://hebbkx1anhila5yf.public.blob.vercel-storage.com/saed_logo_final_blue%20%281%29-RiV76ZtVQPCe5rZu3uEXDaXwmdxT7w.png';
const menuItems = [
  ['Inicio', LayoutDashboard], ['Residentes', Users], ['Visitas', QrCode], ['Cartera', CreditCard],
  ['Pagos', CreditCard], ['Portería', ShieldCheck], ['Paquetería', Package], ['Configuración', Settings],
];
const activityItems = [
  ['Visita autorizada', 'María González · Apto 402', 'bg-cyan-400'],
  ['Paquete recibido', 'Mercado Libre · Apto 301', 'bg-amber-400'],
  ['Pago registrado', 'Apto 502 · $320.000', 'bg-emerald-400'],
  ['Nueva PQRS', 'Fuga de agua · Apto 201', 'bg-blue-400'],
];

function ProductShowcase() {
  return (
    <section className="login-showcase motion-safe:animate-[fadeUp_.7s_ease-out_both]" aria-label="Vista previa de SAED">
      <div className="mb-7 max-w-xl">
        <p className="mb-3 text-xs font-semibold uppercase tracking-[0.22em] text-cyan-300">Plataforma PropTech Enterprise</p>
        <h2 className="text-4xl font-extrabold leading-[1.05] tracking-[-0.04em] text-slate-50 xl:text-6xl">
          Todo tu conjunto,<br /><span className="text-cyan-300">bajo control.</span>
        </h2>
        <p className="mt-5 max-w-lg text-sm leading-6 text-slate-300 xl:text-base">SAED conecta administración, residentes y portería en una sola plataforma.</p>
      </div>
      <div className="relative rounded-2xl border border-cyan-300/20 bg-[#0d1b2f]/85 p-2 shadow-2xl shadow-cyan-950/40 backdrop-blur-xl transition-transform duration-500 hover:-translate-y-1">
        <div className="overflow-hidden rounded-xl border border-white/10 bg-[#081426]">
          <div className="flex h-10 items-center justify-between border-b border-white/10 px-3">
            <div className="flex items-center gap-2"><img src={SAED_LOGO} alt="SAED" className="h-6 w-auto rounded bg-white px-1 object-contain" /><span className="rounded bg-cyan-400/15 px-1.5 text-[9px] font-bold text-cyan-300">2.0</span></div>
            <div className="flex items-center gap-2"><div className="hidden h-6 w-32 rounded-full border border-white/10 bg-white/[.04] sm:block" /><span className="h-6 w-6 rounded-full border border-cyan-300/30 bg-cyan-400/20" /></div>
          </div>
          <div className="flex min-h-[340px]">
            <aside className="hidden w-32 shrink-0 border-r border-white/10 p-2 sm:block"><p className="px-2 pb-3 pt-1 text-[8px] font-semibold uppercase tracking-widest text-slate-500">SAED 2.0</p>{menuItems.map(([label, Icon], index) => <div key={label} className={`mb-0.5 flex items-center gap-2 rounded-md px-2 py-2 text-[9px] transition-colors ${index === 0 ? 'bg-cyan-400/15 text-cyan-200' : 'text-slate-500'}`}><Icon className="h-3 w-3" />{label}</div>)}</aside>
            <div className="min-w-0 flex-1 space-y-3 p-4"><div><p className="text-[9px] text-slate-500">Resumen de tu comunidad</p><h3 className="mt-1 text-base font-bold text-slate-100">Hola, María</h3></div>
              <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">{[['Residentes', '248', Users, 'text-cyan-300'], ['Visitas hoy', '18', QrCode, 'text-emerald-300'], ['Paquetes', '23', Package, 'text-blue-300'], ['Cartera', '$12.840.000', CreditCard, 'text-amber-300']].map(([label, value, Icon, color]) => <div key={label} className="rounded-lg border border-white/10 bg-white/[.035] p-2.5 transition-colors hover:border-cyan-300/30"><Icon className={`mb-2 h-3.5 w-3.5 ${color}`} /><p className="text-[8px] text-slate-500">{label}</p><p className={`mt-1 truncate text-xs font-bold ${color}`}>{value}</p></div>)}</div>
              <div className="grid gap-2 lg:grid-cols-[1.25fr_.75fr]"><div className="rounded-lg border border-white/10 bg-white/[.025] p-3"><div className="mb-3 flex items-center justify-between"><p className="text-[9px] font-semibold text-slate-300">Actividad de la comunidad</p><Activity className="h-3 w-3 text-cyan-300" /></div><div className="flex h-24 items-end gap-1">{[30,42,36,58,45,70,55,76,64,88,73,94].map((height, index) => <span key={index} className={`flex-1 origin-bottom rounded-t-sm motion-safe:animate-[grow_.8s_ease-out_both] ${index % 3 === 0 ? 'bg-cyan-400' : index % 3 === 1 ? 'bg-blue-500' : 'bg-emerald-400'}`} style={{ height: `${height}%`, animationDelay: `${index * 45}ms` }} />)}</div><div className="mt-2 flex justify-between text-[8px] text-slate-600"><span>Lun</span><span>Mié</span><span>Vie</span><span>Dom</span></div></div><div className="rounded-lg border border-white/10 bg-white/[.025] p-3"><p className="mb-1 text-[9px] font-semibold text-slate-300">Actividad reciente</p>{activityItems.map(([title, detail, color]) => <div key={title} className="flex items-center gap-2 border-b border-white/5 py-2 last:border-0"><span className={`h-5 w-5 shrink-0 rounded-md ${color}/20`}><span className={`m-1 block h-3 w-3 rounded-full ${color}`} /></span><span className="min-w-0"><strong className="block truncate text-[8px] font-medium text-slate-300">{title}</strong><small className="block truncate text-[7px] text-slate-500">{detail}</small></span></div>)}</div></div>
              <div className="flex gap-2 border-t border-white/5 pt-2">{['Registrar visita', 'Nuevo residente', 'Registrar pago'].map((item) => <div key={item} className="flex-1 rounded-md bg-white/[.03] px-2 py-2 text-center text-[8px] text-slate-400">{item}</div>)}</div>
            </div>
          </div>
        </div>
      </div>
      <div className="mt-6 flex items-center gap-6 text-xs text-slate-400"><span className="flex items-center gap-2"><ShieldCheck className="h-4 w-4 text-cyan-300" />Conexión segura · TLS</span><span className="hidden h-4 w-px bg-white/10 sm:block" /><span className="hidden sm:block">SAED 2.0 · Gestión residencial inteligente</span></div>
    </section>
  );
}

export default function LoginPage() {
  const { login, loading, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState(() => localStorage.getItem('remembered_user') || '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => !!localStorage.getItem('remembered_user'));
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');
  const [slowNotice, setSlowNotice] = useState(false);

  useEffect(() => { document.title = 'Iniciar sesión — SAED'; warmUpBackend(); }, []);
  useEffect(() => { let timer; if (loading) timer = setTimeout(() => setSlowNotice(true), 3500); else setSlowNotice(false); return () => clearTimeout(timer); }, [loading]);
  useEffect(() => { if (isAuthenticated && user) { const dest = ROLE_HOME[user.rol] || '/dashboard'; const from = location.state?.from?.pathname; navigate(from && roleCanAccess(from, user.rol) ? from : dest, { replace: true }); } }, [isAuthenticated, user, navigate, location]);

  async function handleSubmit(event) {
    event.preventDefault(); setError('');
    const validatedUsername = valUsername(username); if (!validatedUsername.ok) { setError(validatedUsername.mensaje); return; }
    const validatedPassword = valPassword(password); if (!validatedPassword.ok) { setError(validatedPassword.mensaje); return; }
    try { await login(username.trim(), password); if (remember) localStorage.setItem('remembered_user', username.trim()); else localStorage.removeItem('remembered_user'); toast.success(`Bienvenido, ${validatedUsername.username}`); }
    catch (err) { setError(err.message); toast.error(err.message); }
  }

  return <div className="min-h-screen overflow-x-hidden bg-[#061525] text-slate-50 selection:bg-cyan-300/20 selection:text-cyan-200"><div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_75%_35%,rgba(34,211,238,.11),transparent_28%),radial-gradient(circle_at_15%_80%,rgba(59,130,246,.12),transparent_30%)]" /><div className="pointer-events-none fixed inset-0 opacity-[.12] [background-image:linear-gradient(rgba(148,163,184,.18)_1px,transparent_1px),linear-gradient(90deg,rgba(148,163,184,.18)_1px,transparent_1px)] [background-size:48px_48px]" />
    <header className="relative z-10 flex items-center justify-between px-5 py-5 sm:px-8 lg:px-12"><Link to="/" className="inline-flex min-h-11 items-center gap-2 rounded-full border border-cyan-300/30 px-4 text-sm font-medium text-slate-300 transition-colors hover:border-cyan-300/70 hover:text-white"><ArrowLeft className="h-4 w-4 text-cyan-300" />Volver al inicio</Link><span className="flex items-center gap-2 text-xs text-slate-400"><ShieldCheck className="h-4 w-4 text-cyan-300" />Acceso seguro</span></header>
    <main className="relative z-10 mx-auto grid w-full max-w-[1440px] items-center gap-10 px-5 pb-10 pt-5 sm:px-8 md:grid-cols-[minmax(300px,.72fr)_minmax(0,1.28fr)] md:gap-8 lg:gap-16 lg:px-12 lg:pb-16 lg:pt-8"><section className="motion-safe:animate-[fadeUp_.6s_ease-out_both] lg:max-w-md"><div className="rounded-2xl border border-white/10 bg-[#0d1b2f]/90 p-6 shadow-2xl shadow-black/30 backdrop-blur-xl sm:p-8"><div className="mb-9"><div className="mb-7 flex items-center gap-3"><div className="rounded-lg bg-white px-2 py-1.5"><img src={SAED_LOGO} alt="SAED" className="h-10 w-auto object-contain" /></div><span className="rounded border border-cyan-300/30 bg-cyan-300/10 px-2 py-1 text-xs font-bold text-cyan-300">2.0</span></div><h1 className="text-3xl font-extrabold tracking-[-.03em] sm:text-4xl">Bienvenido de nuevo</h1><p className="mt-3 text-sm leading-6 text-slate-400">La plataforma que conecta tu comunidad.</p><p className="mt-1 text-sm leading-6 text-slate-500">Administra residentes, operación y finanzas en un solo lugar.</p></div>
      <form onSubmit={handleSubmit} className="space-y-5" noValidate><div><label htmlFor="login-username" className="mb-2 block text-sm font-semibold text-slate-200">Usuario</label><div className="relative"><UserRound className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" /><input id="login-username" type="text" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Nombre de usuario o correo electrónico" autoComplete="username" required aria-invalid={Boolean(error)} className="h-12 w-full rounded-xl border border-[#20344F] bg-[#081426] pl-11 pr-4 text-sm text-white outline-none transition-all placeholder:text-slate-600 focus:border-cyan-300 focus:ring-2 focus:ring-cyan-300/20" /></div></div><div><label htmlFor="login-password" className="mb-2 block text-sm font-semibold text-slate-200">Contraseña</label><div className="relative"><Lock className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" /><input id="login-password" type={showPwd ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Ingresa tu contraseña" autoComplete="current-password" required className="h-12 w-full rounded-xl border border-[#20344F] bg-[#081426] pl-11 pr-12 text-sm text-white outline-none transition-all placeholder:text-slate-600 focus:border-cyan-300 focus:ring-2 focus:ring-cyan-300/20" /><button type="button" onClick={() => setShowPwd((value) => !value)} aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'} className="absolute right-0 top-0 flex h-12 w-12 items-center justify-center text-slate-500 transition-colors hover:text-white focus:outline-none focus:ring-2 focus:ring-inset focus:ring-cyan-300">{showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div></div><div className="flex items-center justify-between gap-3 text-xs"><label className="flex min-h-11 items-center gap-2 text-slate-300"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} className="h-4 w-4 rounded border-[#20344F] bg-[#081426] text-cyan-300 focus:ring-cyan-300/30" />Recordarme</label><button type="button" onClick={() => toast.info('Contacte a la administración de su copropiedad para restablecer su acceso')} className="min-h-11 text-right font-medium text-cyan-300 transition-colors hover:text-cyan-200 focus:outline-none focus:underline">¿Necesitas ayuda para acceder?</button></div>{error && <div role="alert" className="flex items-start gap-2 rounded-xl border border-rose-400/20 bg-rose-400/10 p-3 text-sm text-rose-200"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-300" /><span>{error}</span></div>}<button type="submit" disabled={loading} className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-400 to-blue-500 px-5 text-sm font-bold text-[#061525] shadow-lg shadow-cyan-950/30 transition-all hover:from-cyan-300 hover:to-blue-400 active:scale-[.99] focus:outline-none focus:ring-2 focus:ring-cyan-300/60 disabled:cursor-not-allowed disabled:opacity-60">{loading ? <><Loader2 className="h-4 w-4 animate-spin" />Iniciando sesión...</> : <>Iniciar sesión<ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" /></>}</button>{slowNotice && <div className="flex items-center gap-2 text-xs text-cyan-200"><Loader2 className="h-3.5 w-3.5 animate-spin" />Conectando con el servidor seguro...</div>}</form><div className="mt-8 border-t border-white/10 pt-5 text-xs text-slate-500">SAED 2.0 <span className="mx-2 text-slate-700">·</span> Gestión residencial inteligente</div></div></section><ProductShowcase /></main>
    <style>{'@keyframes fadeUp{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}@keyframes grow{from{transform:scaleY(0);opacity:.2}to{transform:scaleY(1);opacity:1}}@media(prefers-reduced-motion:reduce){*,::before,::after{animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important}}'}</style>
  </div>;
}
