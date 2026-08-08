import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/AuthContext.jsx';
import { ROLE_HOME, roleCanAccess } from '../lib/access.js';
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
    <>
      <div className="hero-gallery">
        <div
          className="hero-track"
          style={{ transform: `translateX(-${index * 100}%)` }}
        >
          {HERO_IMAGES.map((img) => (
            <div
              key={img}
              className="hero-slide"
              style={{ backgroundImage: `url(${import.meta.env.BASE_URL}imagenes/${img})` }}
            />
          ))}
        </div>
      </div>
      <div className="hero-dots">
        {HERO_IMAGES.map((_, i) => (
          <button
            key={i}
            type="button"
            onClick={() => setIndex(i)}
            className={`hero-dot ${i === index ? 'active' : ''}`}
            aria-label={`Slide ${i + 1}`}
          />
        ))}
      </div>
      <p className="hero-slogan">SAED — Seguridad, organización y confianza</p>
    </>
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
      const dest = ROLE_HOME[user.rol] || '/dashboard';
      const from = location.state?.from?.pathname;
      const target = from && roleCanAccess(from, user.rol) ? from : dest;
      navigate(target, { replace: true });
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
    <div
      className="login-page"
      style={{
        backgroundImage: `linear-gradient(135deg, rgba(10,22,40,0.85) 0%, rgba(14,30,64,0.75) 100%), url(${import.meta.env.BASE_URL}imagenes/fondo%20del%20login.png)`,
      }}
    >
      <div className="login-container">
        <main className="login-main">
          <div className="login-card">
            <div className="login-header">
              <div className="login-logo">
                <img
                  src={`${import.meta.env.BASE_URL}imagenes/saed_logo_final_blue.png`}
                  alt="SAED"
                />
              </div>
              <p className="login-subtitle">Acceso seguro a tu edificio</p>
            </div>

            <form onSubmit={handleSubmit} className="login-form">
              <div className="floating-input">
                <input
                  id="login-username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder=" "
                  autoComplete="username"
                  required
                  className="floating-input-field"
                />
                <label htmlFor="login-username" className="floating-label">
                  Usuario
                </label>
              </div>

              <div className="floating-input" style={{ position: 'relative' }}>
                <input
                  id="login-password"
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder=" "
                  autoComplete="current-password"
                  required
                  className="floating-input-field"
                  style={{ paddingRight: '44px' }}
                />
                <label htmlFor="login-password" className="floating-label">
                  Contraseña
                </label>
                <button
                  type="button"
                  onClick={() => setShowPwd((s) => !s)}
                  className="password-toggle"
                  aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                >
                  <span className="material-symbols-outlined">
                    {showPwd ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>

              <div className="login-options">
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={remember}
                    onChange={(e) => setRemember(e.target.checked)}
                  />
                  Recordarme
                </label>
                <button
                  type="button"
                  onClick={() =>
                    setToast({ message: 'Contacte al administrador del sistema', type: 'info' })
                  }
                  className="forgot-link"
                >
                  ¿Olvidaste tu contraseña?
                </button>
              </div>

              {error && <div className="login-error-msg">{error}</div>}

              <button type="submit" disabled={loading} className="login-btn">
                {loading ? 'Ingresando...' : 'Iniciar Sesión'}
                <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>
                  arrow_forward
                </span>
              </button>
            </form>

            <p className="login-footer">
              © {new Date().getFullYear()} SAED Residential. All rights reserved.
            </p>
          </div>
        </main>
        <aside className="login-hero">
          <HeroGallery />
        </aside>
      </div>
      <Toast toast={toast} />
    </div>
  );
}
