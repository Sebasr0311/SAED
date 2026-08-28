import { TOKEN_KEY, USER_KEY } from './storage.js';

const isLocalhost =
  typeof window !== 'undefined' &&
  (window.location.protocol === 'file:' ||
    window.location.hostname === 'localhost' ||
    window.location.hostname === '127.0.0.1');

const RAW_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL || '') ||
  (typeof window !== 'undefined' && window._API_BASE_URL) ||
  (isLocalhost
    ? 'http://localhost:8080/api/v1'
    : 'https://sistema-administracion-edificios.onrender.com/api/v1');

export const BASE_URL = RAW_BASE_URL.replace(/\/+$/, '');
const TIMEOUT_MS = 30000;

let onUnauthorized = null;

export function setOnUnauthorized(handler) {
  onUnauthorized = handler;
}

function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

async function request(endpoint, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const res = await fetch(BASE_URL + endpoint, {
      ...options,
      headers,
      signal: controller.signal,
    });
    clearTimeout(timer);

    // Un 401 en /auth/login significa credenciales invalidas (el backend
    // devuelve el motivo real); solo se trata como sesion expirada en el
    // resto de endpoints.
    const isLogin = endpoint.startsWith('/auth/login');
    if (res.status === 401 && !isLogin) {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(USER_KEY);
      if (typeof onUnauthorized === 'function') {
        onUnauthorized();
      } else {
        window.location.href = `${import.meta.env.BASE_URL}login`;
      }
      throw new Error('Sesión expirada');
    }

    const contentType = res.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      const data = await res.json();
      if (!res.ok) throw new Error(data.mensaje || data.error || 'No se pudo completar la operación. Intente de nuevo.');
      return data;
    }
    if (!res.ok) throw new Error('No se pudo completar la operación. Intente de nuevo.');
    return await res.text();
  } catch (err) {
    clearTimeout(timer);
    if (err.name === 'AbortError') throw new Error('La solicitud tardó demasiado, intente de nuevo');
    throw err;
  }
}

export const api = {
  get: (url) => request(url),
  post: (url, body) => request(url, { method: 'POST', body: JSON.stringify(body) }),
  put: (url, body) => request(url, { method: 'PUT', body: JSON.stringify(body) }),
  patch: (url, body) => request(url, { method: 'PATCH', body: JSON.stringify(body) }),
  del: (url, body) =>
    request(url, { method: 'DELETE', body: body ? JSON.stringify(body) : undefined }),
};

export default api;
