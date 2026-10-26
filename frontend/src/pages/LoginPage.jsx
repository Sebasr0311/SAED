import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Eye,
  EyeOff,
  House,
  Loader2,
  Lock,
  Moon,
  Sun,
  UserRound,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '../lib/AuthContext.jsx';
import { ROLE_HOME, roleCanAccess } from '../lib/access.js';
import { valUsername, valPassword } from '../lib/validation.js';
import { warmUpBackend } from '../lib/api.js';
import { useSaedTheme } from '../lib/theme.js';
import ComplexModel from '../components/landing/ComplexModel.jsx';
import '../components/landing/manual.css';
import '../components/landing/manual-login.css';

const SAED_EMBLEM = '/imagenes/saed_logo_emblem_only.png';

export default function LoginPage() {
  const { login, loginDemo, loading, isAuthenticated, user } = useAuth();
  const { theme, toggleTheme } = useSaedTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState(() => localStorage.getItem('remembered_user') || '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => !!localStorage.getItem('remembered_user'));
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');
  const [slowNotice, setSlowNotice] = useState(false);

  useEffect(() => {
    document.title = 'Iniciar sesión — SAED 2.0';
    warmUpBackend();
  }, []);

  useEffect(() => {
    let timer;
    if (loading) timer = setTimeout(() => setSlowNotice(true), 3500);
    else setSlowNotice(false);
    return () => clearTimeout(timer);
  }, [loading]);

  useEffect(() => {
    if (isAuthenticated && user) {
      const dest = ROLE_HOME[user.rol] || '/dashboard';
      const from = location.state?.from?.pathname;
      navigate(from && roleCanAccess(from, user.rol) ? from : dest, { replace: true });
    }
  }, [isAuthenticated, user, navigate, location]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    const validatedUsername = valUsername(username);
    if (!validatedUsername.ok) {
      setError(validatedUsername.mensaje);
      return;
    }
    const validatedPassword = valPassword(password);
    if (!validatedPassword.ok) {
      setError(validatedPassword.mensaje);
      return;
    }
    try {
      await login(username.trim(), password);
      if (remember) localStorage.setItem('remembered_user', username.trim());
      else localStorage.removeItem('remembered_user');
      toast.success(`Bienvenido, ${validatedUsername.username}`);
    } catch (err) {
      setError(err.message);
      toast.error(err.message);
    }
  }

  function handleDemoLogin(rol) {
    const isConv = rol === 'RESIDENTE_CONVIVENCIA';
    const demoUser = {
      idUsuario: isConv ? 102 : 101,
      idPersona: isConv ? 102 : 101,
      idResidente: isConv ? 102 : 101,
      username: isConv ? 'conviviente_demo' : 'residente_titular_demo',
      nombreUsuario: isConv ? 'conviviente_demo' : 'residente_titular_demo',
      nombreCompleto: isConv ? 'Valentina Restrepo' : 'Carlos Andrés Mendoza',
      primerNombre: isConv ? 'Valentina' : 'Carlos Andrés',
      primerApellido: isConv ? 'Restrepo' : 'Mendoza',
      numeroDocumento: isConv ? '1098765432' : '79865432',
      tipoDocumentoNombre: 'Cédula de Ciudadanía (CC)',
      email: isConv ? 'valentina.conviviente@saed.com.co' : 'carlos.titular@saed.com.co',
      telefono: '3157894561',
      rol: rol,
      rolCodigo: rol,
      tipoResidente: isConv ? 'CONVIVIENTE' : 'TITULAR',
      tipoRelacion: isConv ? 'CONVIVIENTE' : 'TITULAR',
      idUnidad: 1,
      identificadorUnidad: 'Apto 402',
      numeroApartamento: 'Apto 402',
      numeroApto: 'Apto 402',
      nombreBloque: 'Torre Reserva',
      pisoApto: 4,
      areaApto: '84.5 m²',
      nombrePropiedad: 'Altos del Parque Residencial',
      direccionPropiedad: 'Calle 127 # 15 - 34',
      ciudadPropiedad: 'Bogotá D.C.',
    };
    loginDemo(demoUser);
    toast.success(`Iniciando sesión como ${isConv ? 'Residente Conviviente' : 'Residente Titular'}`);
    navigate('/residente-dashboard', { replace: true });
  }

  return (
    <div className={`saed-manual m-root m-login ${theme === 'dark' ? 'dark' : ''}`}>
      <header className="m-strip">
        <div className="m-container m-strip-inner">
          <Link to="/" className="m-brand">
            <img src={SAED_EMBLEM} alt="" className="m-brand-mark" width="32" height="32" />
            <span className="m-brand-name">SAED</span>
            <span className="m-brand-ver">2.0</span>
            <span className="m-sr">, ir al inicio</span>
          </Link>
          <div className="m-strip-actions">
            <Link to="/" className="m-btn m-btn--quiet m-hide-mobile">
              <ArrowLeft aria-hidden="true" />
              <span>Volver al inicio</span>
            </Link>
            <Link to="/suscripciones" className="m-btn m-btn--glass m-hide-mobile">
              Ver planes
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

      <main className="m-container m-login-main">
        <div className="m-spread m-login-spread">
          {/* Left page: the form, a glass piece on the stud grid */}
          <section className="m-page m-login-page m-studs" aria-labelledby="m-login-title">
            <div className="m-login-piece">
              <div className="m-login-head">
                <h1 id="m-login-title" className="m-h2">
                  Iniciar sesión
                </h1>
                <p className="m-body">
                  Ingresa tus credenciales para acceder al panel de tu copropiedad u organización.
                </p>
              </div>

              <form onSubmit={handleSubmit} className="m-login-form" noValidate>
                <div className="m-field">
                  <label htmlFor="login-username" className="m-field-label">
                    Usuario o correo electrónico
                  </label>
                  <div className="m-field-control">
                    <UserRound aria-hidden="true" className="m-field-icon" />
                    <input
                      id="login-username"
                      type="text"
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="ej. carlos.admin o correo@copropiedad.com"
                      autoComplete="username"
                      required
                      aria-invalid={Boolean(error)}
                      className="m-input"
                    />
                  </div>
                </div>

                <div className="m-field">
                  <label htmlFor="login-password" className="m-field-label">
                    Contraseña
                  </label>
                  <div className="m-field-control">
                    <Lock aria-hidden="true" className="m-field-icon" />
                    <input
                      id="login-password"
                      type={showPwd ? 'text' : 'password'}
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="Ingresa tu contraseña"
                      autoComplete="current-password"
                      required
                      aria-invalid={Boolean(error)}
                      className="m-input m-input--pwd"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPwd((value) => !value)}
                      aria-label={showPwd ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      className="m-field-toggle"
                    >
                      {showPwd ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                    </button>
                  </div>
                </div>

                <div className="m-login-row">
                  <label className="m-check">
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(event) => setRemember(event.target.checked)}
                    />
                    <span>Recordar mi usuario</span>
                  </label>
                  <button
                    type="button"
                    onClick={() =>
                      toast.info(
                        'Contacte a la administración de su copropiedad para restablecer su acceso o solicite un enlace en /activar-cuenta'
                      )
                    }
                    className="m-text-btn"
                  >
                    ¿Problemas para ingresar?
                  </button>
                </div>

                {error && (
                  <div role="alert" className="m-login-error">
                    <AlertCircle aria-hidden="true" />
                    <span>{error}</span>
                  </div>
                )}

                <button type="submit" disabled={loading} className="m-btn m-btn--primary m-btn--lg m-login-submit">
                  {loading ? (
                    <>
                      <Loader2 aria-hidden="true" className="m-spin" />
                      <span>Iniciando sesión...</span>
                    </>
                  ) : (
                    <>
                      <span>Iniciar sesión</span>
                      <ArrowRight aria-hidden="true" />
                    </>
                  )}
                </button>

                {slowNotice && (
                  <div className="m-login-slow" role="status">
                    <Loader2 aria-hidden="true" className="m-spin" />
                    <span>Conectando con el servidor seguro...</span>
                  </div>
                )}
              </form>

              <div className="m-login-demo">
                <p className="m-field-label">Acceso de demostración</p>
                <div className="m-login-demo-row">
                  <button
                    type="button"
                    onClick={() => handleDemoLogin('RESIDENTE_CONVIVENCIA')}
                    className="m-btn m-btn--glass"
                  >
                    <UsersRound aria-hidden="true" />
                    <span>Conviviente</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDemoLogin('RESIDENTE')}
                    className="m-btn m-btn--glass"
                  >
                    <House aria-hidden="true" />
                    <span>Titular</span>
                  </button>
                </div>
              </div>

              <dl className="m-login-links">
                <div>
                  <dt>¿Recibiste un enlace de bienvenida?</dt>
                  <dd>
                    <Link to="/activar-cuenta" className="m-link">
                      Activar mi cuenta
                    </Link>
                  </dd>
                </div>
                <div>
                  <dt>¿Aún no registras tu conjunto?</dt>
                  <dd>
                    <Link to="/registro-organizacion" className="m-link">
                      Registrar mi organización
                    </Link>
                  </dd>
                </div>
              </dl>
            </div>
          </section>

          {/* Right page: the finished build */}
          <aside className="m-page m-page--right m-login-aside" aria-label="Modelo ilustrativo">
            <p className="m-h3 m-login-aside-title">Un mismo registro para todo el conjunto</p>
            <p className="m-body">
              Administración, portería y residentes entran por aquí y ven la misma información, cada
              uno con sus permisos.
            </p>
            <ComplexModel units={240} activeStep={7} rows={19} className="m-login-model" />
            <p className="m-model-caption">Modelo ilustrativo</p>
          </aside>
        </div>
      </main>
    </div>
  );
}
