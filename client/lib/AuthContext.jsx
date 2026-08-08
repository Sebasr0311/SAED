import { createContext, useContext, useState, useEffect } from 'react';
import api, { setOnUnauthorized } from './api.js';
import { TOKEN_KEY, USER_KEY } from './storage.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setOnUnauthorized(() => logout());
  }, []);

  async function login(username, password) {
    setLoading(true);
    try {
      const data = await api.post('/auth/login', { username, password });
      sessionStorage.setItem(TOKEN_KEY, data.token);
      sessionStorage.setItem(USER_KEY, JSON.stringify(data.usuario));
      setUser(data.usuario);
      return data.usuario;
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
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
