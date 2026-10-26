import { useEffect, useState, useMemo } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Circle,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Lock,
  Mail,
  Moon,
  Sun,
} from 'lucide-react';
import { toast } from 'sonner';
import api from '../lib/api.js';
import { useSaedTheme } from '../lib/theme.js';
import '../components/landing/manual.css';
import '../components/landing/manual-public.css';

const SAED_EMBLEM = '/imagenes/saed_logo_emblem_only.png';

export default function ActivarCuentaPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const { theme, toggleTheme } = useSaedTheme();

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
    document.title = 'Activar cuenta — SAED 2.0';
  }, []);

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

  const metCount = useMemo(() => {
    return Object.values(criteria).filter(Boolean).length;
  }, [criteria]);

  const isValidPassword =
    criteria.length &&
    criteria.upper &&
    criteria.lower &&
    criteria.number &&
    criteria.special &&
    criteria.match;

  const mismatch = confirmPassword.length > 0 && password !== confirmPassword;

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
      toast.success('Cuenta activada con éxito.');
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
            <Link to="/" className="m-btn m-btn--quiet m-hide-mobile">
              <ArrowLeft aria-hidden="true" />
              <span>Volver al inicio</span>
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
        <div className="m-pub-narrow">
          <section className="m-pub-frame m-studs" aria-labelledby="activar-title" aria-busy={loading}>
            <div className="m-pub-frame-body">
              {loading ? (
                <div className="m-pub-enter" role="status" aria-live="polite">
                  <div className="m-pub-head">
                    <h1 id="activar-title" className="m-h3">
                      Verificando el enlace de activación
                    </h1>
                    <p className="m-pub-status">
                      <Loader2 className="m-pub-spin" aria-hidden="true" />
                      Comprobando que el enlace sea válido y siga vigente.
                    </p>
                  </div>
                  <div className="m-pub-skel" aria-hidden="true" style={{ marginTop: 20 }}>
                    <span />
                    <span />
                    <span />
                  </div>
                </div>
              ) : success ? (
                <div className="m-pub-result m-pub-enter">
                  <span className="m-pub-result-piece is-ok" aria-hidden="true">
                    <CheckCircle2 />
                  </span>
                  <div className="m-pub-head">
                    <h1 id="activar-title" className="m-h3">
                      Cuenta activada
                    </h1>
                    <p className="m-body">
                      Tu contraseña quedó establecida. Ya puedes ingresar con tu usuario o tu correo
                      electrónico.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="m-btn m-btn--primary m-btn--lg m-pub-block"
                    onClick={() => navigate('/login')}
                  >
                    <span>Ir a iniciar sesión</span>
                    <ArrowRight aria-hidden="true" />
                  </button>
                </div>
              ) : tokenError ? (
                <div className="m-pub-result m-pub-enter">
                  <span className="m-pub-result-piece is-error" aria-hidden="true">
                    <AlertTriangle />
                  </span>
                  <div className="m-pub-head">
                    <h1 id="activar-title" className="m-h3">
                      Enlace no válido o expirado
                    </h1>
                    <p className="m-body" role="alert">
                      {tokenError}
                    </p>
                  </div>

                  {reenvioExito ? (
                    <div className="m-pub-note is-ok m-pub-block" role="status">
                      <CheckCircle2 aria-hidden="true" />
                      <div>
                        <p className="m-pub-note-title">Solicitud recibida</p>
                        <p className="m-pub-note-text">
                          Si la cuenta existe, recibirás un nuevo enlace en el correo electrónico
                          registrado.
                        </p>
                      </div>
                    </div>
                  ) : (
                    <form onSubmit={handleSolicitarReenvio} className="m-pub-form m-pub-block">
                      <div className="m-pub-field">
                        <label htmlFor="reenvio-identificador" className="m-pub-label">
                          Correo electrónico o usuario
                        </label>
                        <div className="m-pub-control">
                          <Mail aria-hidden="true" />
                          <input
                            id="reenvio-identificador"
                            type="text"
                            required
                            value={reenvioEmail}
                            onChange={(e) => setReenvioEmail(e.target.value)}
                            placeholder="correo@ejemplo.com o usuario"
                            autoComplete="username"
                            className="m-pub-input"
                          />
                        </div>
                        <p className="m-pub-hint">Te enviaremos un nuevo enlace de activación.</p>
                      </div>
                      <button
                        type="submit"
                        disabled={reenviando || !reenvioEmail.trim()}
                        className="m-btn m-btn--primary m-pub-block"
                      >
                        {reenviando ? (
                          <>
                            <Loader2 className="m-pub-spin" aria-hidden="true" />
                            <span>Enviando enlace...</span>
                          </>
                        ) : (
                          <span>Solicitar nuevo enlace</span>
                        )}
                      </button>
                    </form>
                  )}

                  <Link to="/login" className="m-link">
                    Regresar al inicio de sesión
                  </Link>
                </div>
              ) : (
                <div className="m-pub-enter" style={{ display: 'grid', gap: 22 }}>
                  <div className="m-pub-head">
                    <h1 id="activar-title" className="m-h3">
                      Crea tu contraseña
                    </h1>
                    <p className="m-body">
                      Hola{' '}
                      <strong className="m-strong">
                        {tokenInfo?.primerNombre || tokenInfo?.nombreUsuario}
                      </strong>
                      , define la clave con la que ingresarás a SAED.
                    </p>
                  </div>

                  <dl className="m-pub-dl">
                    <div>
                      <dt>Usuario</dt>
                      <dd className="m-pub-mono">{tokenInfo?.nombreUsuario}</dd>
                    </div>
                    {tokenInfo?.email && (
                      <div>
                        <dt>Correo</dt>
                        <dd>{tokenInfo?.email}</dd>
                      </div>
                    )}
                  </dl>

                  <form onSubmit={handleActivar} className="m-pub-form" noValidate>
                    <div className="m-pub-field">
                      <label htmlFor="activar-password" className="m-pub-label">
                        Nueva contraseña<span className="m-pub-req" aria-hidden="true">*</span>
                      </label>
                      <div className="m-pub-control">
                        <Lock aria-hidden="true" />
                        <input
                          id="activar-password"
                          type={showPassword ? 'text' : 'password'}
                          required
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          autoComplete="new-password"
                          aria-describedby="activar-reqs"
                          className="m-pub-input m-pub-input--pwd"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="m-pub-toggle"
                          aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                          aria-pressed={showPassword}
                        >
                          {showPassword ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                        </button>
                      </div>
                    </div>

                    <div className="m-pub-field">
                      <label htmlFor="activar-confirm" className="m-pub-label">
                        Confirmar contraseña<span className="m-pub-req" aria-hidden="true">*</span>
                      </label>
                      <div className="m-pub-control">
                        <KeyRound aria-hidden="true" />
                        <input
                          id="activar-confirm"
                          type={showPassword ? 'text' : 'password'}
                          required
                          value={confirmPassword}
                          onChange={(e) => setConfirmPassword(e.target.value)}
                          autoComplete="new-password"
                          aria-invalid={mismatch}
                          aria-describedby={mismatch ? 'activar-confirm-error' : undefined}
                          className="m-pub-input"
                        />
                      </div>
                      {mismatch && (
                        <p id="activar-confirm-error" className="m-pub-error">
                          <AlertTriangle aria-hidden="true" />
                          <span>Las contraseñas no coinciden.</span>
                        </p>
                      )}
                    </div>

                    <div className="m-pub-reqs" id="activar-reqs">
                      <div className="m-pub-reqs-head">
                        <span>Requisitos de seguridad</span>
                        <span className="m-pub-mono" aria-live="polite">
                          {metCount} de 6
                        </span>
                      </div>
                      <div className="m-pub-meter" data-full={metCount === 6 ? '' : undefined} aria-hidden="true">
                        {Array.from({ length: 6 }, (_, i) => (
                          <span key={i} data-on={i < metCount ? '' : undefined} />
                        ))}
                      </div>
                      <ul>
                        <RequirementItem met={criteria.length} text="Mínimo 8 caracteres" />
                        <RequirementItem met={criteria.upper} text="Una letra mayúscula (A-Z)" />
                        <RequirementItem met={criteria.lower} text="Una letra minúscula (a-z)" />
                        <RequirementItem met={criteria.number} text="Un número (0-9)" />
                        <RequirementItem
                          met={criteria.special}
                          text="Un carácter especial (@#$%^&+=!._*-)"
                        />
                        <RequirementItem met={criteria.match} text="Las contraseñas coinciden" />
                      </ul>
                    </div>

                    <button
                      type="submit"
                      disabled={!isValidPassword || submitting}
                      className="m-btn m-btn--primary m-btn--lg m-pub-block"
                    >
                      {submitting ? (
                        <>
                          <Loader2 className="m-pub-spin" aria-hidden="true" />
                          <span>Activando cuenta...</span>
                        </>
                      ) : (
                        <span>Activar cuenta y establecer contraseña</span>
                      )}
                    </button>
                  </form>

                  <p className="m-pub-hint">
                    ¿Ya activaste tu cuenta?{' '}
                    <Link to="/login" className="m-link">
                      Iniciar sesión
                    </Link>
                  </p>
                </div>
              )}
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}

function RequirementItem({ met, text }) {
  return (
    <li data-met={met ? '' : undefined}>
      {met ? <CheckCircle2 aria-hidden="true" /> : <Circle aria-hidden="true" />}
      <span>{text}</span>
      <span className="m-sr">{met ? ', cumplido' : ', pendiente'}</span>
    </li>
  );
}
