import { createContext, useContext, useState, useEffect } from 'react';
import api, { setOnUnauthorized, setTokens, clearAuth } from './api.js';
import { TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY } from './storage.js';
import { normalizeRole } from './access.js';

const AuthContext = createContext(null);

function normalizeUser(user) {
  if (!user) return user;
  return { ...user, rol: normalizeRole(user.rol) };
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

  const value = {
    user,
    loading,
    login,
    logout,
    isAuthenticated: !!user,
    isAdmin: user?.rol === 'ADMINISTRADOR',
    isPortero: user?.rol === 'PORTERO',
    isResidente: user?.rol === 'RESIDENTE',
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
