import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/AuthContext.jsx';
import Toast from '../components/ui/Toast.jsx';

const HERO_IMAGES = [
  'galeria1.png',
  'galeria2.png',
  'galeria3.png',
  'galeria4.png',
  'galeria5.png',
  'galeria6.png',
];

function HeroGallery() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const t = setInterval(() => setIndex((i) => (i + 1) % HERO_IMAGES.length), 4000);
    return () => clearInterval(t);
  }, []);

  return (
    <div className="relative h-full w-full overflow-hidden">
      {HERO_IMAGES.map((img, i) => (
        <div
          key={img}
          className="absolute inset-0 bg-cover bg-center transition-opacity duration-700"
          style={{
            backgroundImage: `url(${import.meta.env.BASE_URL}imagenes/${img})`,
            opacity: i === index ? 1 : 0,
          }}
        />
      ))}
      <div className="absolute bottom-6 left-1/2 z-10 flex -translate-x-1/2 gap-2">
        {HERO_IMAGES.map((_, i) => (
          <button
            key={i}
            onClick={() => setIndex(i)}
            className={`h-2 rounded-full transition-all ${
              i === index ? 'w-8 bg-white' : 'w-2 bg-white/50'
            }`}
            aria-label={`Slide ${i + 1}`}
          />
        ))}
      </div>
      <p className="absolute bottom-2 left-1/2 z-10 -translate-x-1/2 text-xs font-medium text-white/90 drop-shadow">
        SAED — Seguridad, organización y confianza
      </p>
    </div>
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
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (isAuthenticated && user) {
      const dest =
        user.rol === 'RESIDENTE'
          ? '/residente-dashboard'
          : user.rol === 'PORTERO'
            ? '/portero-dashboard'
            : '/dashboard';
      navigate(location.state?.from?.pathname || dest, { replace: true });
    }
  }, [isAuthenticated, user, navigate, location]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) {
      setError('Usuario y contraseña son obligatorios');
      return;
    }
    try {
      const u = await login(username.trim(), password);
      if (remember) localStorage.setItem('remembered_user', username.trim());
      else localStorage.removeItem('remembered_user');
      setToast({ message: `Bienvenido, ${u.username}`, type: 'success' });
    } catch (err) {
      setError(err.message);
      setToast({ message: err.message, type: 'error' });
    }
  }

  return (
    <div className="grid h-screen w-full grid-cols-1 md:grid-cols-2">
      <div className="flex items-center justify-center bg-background p-6">
        <div className="w-full max-w-md">
          <div className="mb-8 flex flex-col items-center">
            <img
              src={`${import.meta.env.BASE_URL}imagenes/saed_logo_final_blue.png`}
              alt="SAED"
              className="mb-4 h-auto w-64 object-contain"
            />
            <p className="text-sm text-on-surface-variant">Acceso seguro a tu edificio</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <input
                id="login-username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Usuario"
                autoComplete="username"
                required
                className="w-full rounded-xl border border-outline-variant bg-surface px-4 py-3 text-sm transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <div className="relative">
              <input
                id="login-password"
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Contraseña"
                autoComplete="current-password"
                required
                className="w-full rounded-xl border border-outline-variant bg-surface px-4 py-3 pr-12 text-sm transition-colors focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
              <button
                type="button"
                onClick={() => setShowPwd((s) => !s)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-on-surface"
                aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              >
                <span className="material-symbols-outlined">
                  {showPwd ? 'visibility_off' : 'visibility'}
                </span>
              </button>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="flex cursor-pointer items-center gap-2 text-on-surface-variant">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                  className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                />
                Recordarme
              </label>
              <button
                type="button"
                onClick={() =>
                  setToast({ message: 'Contacte al administrador del sistema', type: 'info' })
                }
                className="text-primary hover:underline"
              >
                ¿Olvidaste tu contraseña?
              </button>
            </div>

            {error && (
              <div
                role="alert"
                className="rounded-lg border border-error bg-error-container px-3 py-2 text-sm text-error"
              >
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-4 py-3 text-sm font-semibold text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-50"
            >
              {loading ? 'Ingresando...' : 'Iniciar Sesión'}
              <span className="material-symbols-outlined text-lg">arrow_forward</span>
            </button>
          </form>

          <p className="mt-8 text-center text-xs text-on-surface-variant">
            © {new Date().getFullYear()} SAED Residential. All rights reserved.
          </p>
        </div>
      </div>

      <div className="relative hidden bg-primary md:block">
        <HeroGallery />
      </div>

      <Toast toast={toast} />
    </div>
  );
}
