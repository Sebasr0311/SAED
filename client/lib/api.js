import { TOKEN_KEY, USER_KEY } from './storage.js';

const RAW_BASE_URL =
  (typeof window !== 'undefined' && window._API_BASE_URL) ||
  (window.location.protocol === 'file:'
    ? 'http://localhost:8080/api'
    : 'https://sistema-administracion-edificios.onrender.com/api');

const BASE_URL = RAW_BASE_URL.replace(/\/+$/, '');
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

    if (res.status === 401) {
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
      if (!res.ok) throw new Error(data.mensaje || data.error || 'Error del servidor');
      return data;
    }
    if (!res.ok) throw new Error('Error del servidor');
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
  del: (url, body) =>
    request(url, { method: 'DELETE', body: body ? JSON.stringify(body) : undefined }),
};

export default api;
