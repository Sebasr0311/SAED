const Auth = (() => {
  const TOKEN_KEY = 'auth_token';
  const USER_KEY = 'auth_user';

  function setSession(token, user) {
    sessionStorage.setItem(TOKEN_KEY, token);
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  return {
    async login(username, password) {
      const data = await API.post('/auth/login', { username, password });
      setSession(data.token, data.usuario);
      return data.usuario;
    },

    logout() {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
    },

    getToken() {
      return sessionStorage.getItem(TOKEN_KEY);
    },

    getCurrentUser() {
      const raw = sessionStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    },

    isAuthenticated() {
      return !!this.getToken();
    },

    hasRole(role) {
      const user = this.getCurrentUser();
      return user && user.rol === role;
    },

    isAdmin() { return this.hasRole('ADMINISTRADOR'); },
    isPortero() { return this.hasRole('PORTERO'); },
    isResidente() { return this.hasRole('RESIDENTE'); }
  };
})();
