import { createContext, useContext, useState, useEffect } from 'react';
import api, { setOnUnauthorized, setTokens, clearAuth } from './api.js';
import { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY } from './storage.js';
import { normalizeRole } from './access.js';

const AuthContext = createContext(null);

function normalizeUser(user) {
  if (!user) return user;
  const isConv =
    user.rol === 'RESIDENTE_CONVIVENCIA' ||
    user.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user.tipoResidente === 'CONVIVIENTE' ||
    user.tipoRelacion === 'CONVIVIENTE';

  return {
    ...user,
    rol: isConv ? 'RESIDENTE_CONVIVENCIA' : normalizeRole(user.rol),
    tipoResidente: isConv ? 'CONVIVIENTE' : user.tipoResidente,
    username: user.nombreUsuario || user.username,
    idPersona: user.idPersona || user.idResidente,
    idResidente: user.idPersona || user.idResidente,
  };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw ? normalizeUser(JSON.parse(raw)) : null;
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setOnUnauthorized(() => logout());
  }, []);

  async function login(username, password) {
    setLoading(true);
    try {
      clearAuth();
      const data = await api.post('/auth/login', { username, password });
      const usuario = normalizeUser(data.usuario);
      setTokens(data.token, data.refreshToken);
      sessionStorage.setItem(USER_KEY, JSON.stringify(usuario));
      setUser(usuario);
      return usuario;
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    clearAuth();
    setUser(null);
  }

  const isConv = user?.rol === 'RESIDENTE_CONVIVENCIA' || user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' || user?.tipoResidente === 'CONVIVIENTE';

  const value = {
    user,
    loading,
    login,
    logout,
    isAuthenticated: !!user,
    isAdmin: user?.rol === 'ADMIN_PROPIEDAD' || user?.rol === 'ADMIN_ORGANIZACION' || user?.rol === 'SUPERADMIN',
    isPropiedadAdmin: user?.rol === 'ADMIN_PROPIEDAD',
    isOrgAdmin: user?.rol === 'ADMIN_ORGANIZACION',
    isSuperAdmin: user?.rol === 'SUPERADMIN',
    isPortero: user?.rol === 'PORTERO',
    isResidente: (user?.rol === 'RESIDENTE' || user?.rolCodigo === 'RESIDENTE') && !isConv,
    isConviviente: isConv,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
