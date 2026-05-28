const API = (() => {
  var BASE_URL = (window._API_BASE_URL || (location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api')).replace(/\/+$/, '');
  var TIMEOUT_MS = 30000;

  async function request(endpoint, options) {
    options = options || {};
    var token = Auth.getToken();
    var headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;

    var controller = new AbortController();
    var timer = setTimeout(function() { controller.abort(); }, TIMEOUT_MS);

    try {
      var res = await fetch(BASE_URL + endpoint, {
        ...options,
        headers: { ...headers, ...(options.headers || {}) },
        signal: controller.signal
      });
      clearTimeout(timer);

      if (res.status === 401) {
        Auth.logout();
        Router.navigate('login');
        throw new Error('Sesion expirada');
      }

      var contentType = res.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        var data = await res.json();
        if (!res.ok) throw new Error(data.mensaje || data.error || 'Error del servidor');
        return data;
      }
      if (!res.ok) throw new Error('Error del servidor');
      return await res.text();
    } catch (err) {
      clearTimeout(timer);
      if (err.name === 'AbortError') throw new Error('La solicitud tard\u00f3 demasiado, intente de nuevo');
      throw err;
    }
  }

  return {
    get: function(url) { return request(url); },
    post: function(url, body) { return request(url, { method: 'POST', body: JSON.stringify(body) }); },
    put: function(url, body) { return request(url, { method: 'PUT', body: JSON.stringify(body) }); },
    del: function(url) { return request(url, { method: 'DELETE' }); }
  };
})();