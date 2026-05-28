const PaquetesAdmin = (() => {
  var _paquetes = [];
  var _paquetesFiltrados = [];

  function inicializar() {
    document.getElementById('page-title').textContent = 'Paquetes';
    renderPage();
    configurarFechas();
    cargarPaquetes();
  }

  function renderPage() {
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayStr = today.toISOString().split('T')[0];
    var lastMonth = new Date(today);
    lastMonth.setDate(lastMonth.getDate() - 30);
    var lastMonthStr = lastMonth.toISOString().split('T')[0];

    var container = document.getElementById('content-area');
    container.innerHTML = `
      <div class="card">
        <div class="card-title" style="display:flex;align-items:center;gap:12px">
          <span class="material-symbols-outlined" style="font-size:28px;color:var(--navy-500)">inventory_2</span>
          <span>Historial de Paquetes</span>
        </div>
        <div class="card-body">
          <div class="form-row" style="margin-bottom:24px">
            <div class="form-group">
              <label>Fecha Inicio</label>
              <input type="date" id="paq-fecha-inicio" class="form-control" value="${lastMonthStr}" max="${todayStr}">
              <span class="field-error" id="paq-fecha-inicio-error"></span>
            </div>
            <div class="form-group">
              <label>Fecha Fin</label>
              <input type="date" id="paq-fecha-fin" class="form-control" value="${todayStr}" max="${todayStr}">
              <span class="field-error" id="paq-fecha-fin-error"></span>
            </div>
            <div class="form-group" style="display:flex;align-items:flex-end">
              <button class="btn btn-primary" onclick="PaquetesAdmin.cargarPaquetes()" style="height:40px">
                <span class="material-symbols-outlined" style="font-size:20px;margin-right:4px">search</span>
                Buscar
              </button>
            </div>
          </div>

          <div class="form-group" style="margin-bottom:24px">
            <label>Búsqueda rápida</label>
            <input type="text" id="paq-buscar" class="form-control"
              placeholder="Buscar por apartamento, residente..."
              oninput="PaquetesAdmin.filtrarPaquetes()">
          </div>

          <div id="paq-resultados">
            <div class="text-muted" style="text-align:center;padding:40px">
              <span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">search</span>
              <p style="margin:8px 0 0">Selecciona un rango de fechas y haz clic en Buscar</p>
            </div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('paq-fecha-inicio').addEventListener('change', validarFechas);
    document.getElementById('paq-fecha-fin').addEventListener('change', validarFechas);
  }

  function configurarFechas() {
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayStr = today.toISOString().split('T')[0];
    document.getElementById('paq-fecha-inicio').max = todayStr;
    document.getElementById('paq-fecha-fin').max = todayStr;
  }

  function validarFechas() {
    var fechaInicioInput = document.getElementById('paq-fecha-inicio');
    var fechaFinInput = document.getElementById('paq-fecha-fin');
    var fechaInicio = fechaInicioInput.value;
    var fechaFin = fechaFinInput.value;

    if (!fechaInicio || !fechaFin) return true;

    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayStr = today.toISOString().split('T')[0];

    if (fechaInicio > todayStr) {
      Utils.mostrarError('paq-fecha-inicio', 'No se pueden buscar fechas futuras');
      fechaInicioInput.value = todayStr;
      return false;
    }
    if (fechaFin > todayStr) {
      Utils.mostrarError('paq-fecha-fin', 'No se pueden buscar fechas futuras');
      fechaFinInput.value = todayStr;
      return false;
    }

    var errInicio = document.getElementById('paq-fecha-inicio');
    if (errInicio) { errInicio.classList.remove('is-invalid'); var e = errInicio.parentNode.querySelector('.field-error'); if (e) e.textContent = ''; }
    var errFin = document.getElementById('paq-fecha-fin');
    if (errFin) { errFin.classList.remove('is-invalid'); var e = errFin.parentNode.querySelector('.field-error'); if (e) e.textContent = ''; }
    return true;
  }

  async function cargarPaquetes() {
    try {
      var fechaInicio = document.getElementById('paq-fecha-inicio').value;
      var fechaFin = document.getElementById('paq-fecha-fin').value;

      if (!fechaInicio || !fechaFin) {
        Utils.showToast('Selecciona ambas fechas', 'warning');
        return;
      }

      if (!validarFechas()) return;
      if (fechaInicio > fechaFin) {
        Utils.showToast('La fecha de inicio no puede ser mayor que la fecha fin', 'warning');
        return;
      }

      var resultsEl = document.getElementById('paq-resultados');
      resultsEl.innerHTML = '<div class="text-muted" style="text-align:center;padding:40px">' + Utils.loadingSpinner() + '<p style="margin-top:12px">Cargando paquetes...</p></div>';

      _paquetes = await API.get('/buzon/paquetes');

      _paquetesFiltrados = _paquetes.filter(function(p) {
        if (!p.fechaCreacion) return false;
        var fechaStr = p.fechaCreacion.substring(0, 10);
        return fechaStr >= fechaInicio && fechaStr <= fechaFin;
      });

      aplicarBusquedaTexto();
      renderTabla();
    } catch (e) {
      console.error('[PaquetesAdmin] Error al cargar:', e);
      document.getElementById('paq-resultados').innerHTML =
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;color:var(--error);opacity:0.5">error</span>' +
        '<p style="margin:8px 0 0">Error al cargar paquetes: ' + Utils.escapeHtml(e.message) + '</p></div>';
    }
  }

  function filtrarPaquetes() {
    aplicarBusquedaTexto();
    renderTabla();
  }

  function aplicarBusquedaTexto() {
    var busqueda = document.getElementById('paq-buscar').value.toLowerCase();

    var filtradosPorFecha = _paquetes.filter(function(p) {
      var fechaInicio = document.getElementById('paq-fecha-inicio').value;
      var fechaFin = document.getElementById('paq-fecha-fin').value;
      if (!fechaInicio || !fechaFin || !p.fechaCreacion) return true;
      var fechaStr = p.fechaCreacion.substring(0, 10);
      return fechaStr >= fechaInicio && fechaStr <= fechaFin;
    });

    if (!busqueda) {
      _paquetesFiltrados = filtradosPorFecha;
    } else {
      _paquetesFiltrados = filtradosPorFecha.filter(function(p) {
        var apt = (p.numeroApartamento || '').toLowerCase();
        var res = (p.nombreResidente || '').toLowerCase();
        var tit = (p.titulo || '').toLowerCase();
        return apt.includes(busqueda) || res.includes(busqueda) || tit.includes(busqueda);
      });
    }
  }

  function renderTabla() {
    var resultsEl = document.getElementById('paq-resultados');

    if (!_paquetesFiltrados || _paquetesFiltrados.length === 0) {
      resultsEl.innerHTML =
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">inventory_2</span>' +
        '<p style="margin:8px 0 0">No se encontraron paquetes en el rango seleccionado</p></div>';
      return;
    }

    var total = _paquetesFiltrados.length;
    var entregados = _paquetesFiltrados.filter(function(p) { return p.entregado; }).length;
    var pendientes = total - entregados;

    var html = `
      <div style="display:flex;gap:16px;margin-bottom:20px;flex-wrap:wrap">
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Total Paquetes</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px">${total}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Entregados</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--success)">${entregados}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Pendientes</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--warning)">${pendientes}</span>
        </div>
      </div>

      <div style="overflow-x:auto;margin-bottom:16px">
        <table class="data-table" style="min-width:700px">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Apartamento</th>
              <th>Residente</th>
              <th>Título</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
    `;

    _paquetesFiltrados.forEach(function(p, idx) {
      var fecha = p.fechaCreacion ? Utils.formatDateTime(p.fechaCreacion) : '-';
      var estado = p.entregado
        ? '<span class="badge badge-success">Entregado</span>'
        : '<span class="badge badge-warning">Pendiente</span>';

      html += `
        <tr onclick="PaquetesAdmin.verDetalle(${idx})" style="cursor:pointer;transition:background 0.2s" onmouseenter="this.style.background='var(--navy-50)'" onmouseleave="this.style.background=''">
          <td style="white-space:nowrap">${fecha}</td>
          <td style="text-align:center;font-weight:600">${Utils.escapeHtml(p.numeroApartamento || '-')}</td>
          <td>${Utils.escapeHtml(p.nombreResidente || '-')}</td>
          <td>${Utils.escapeHtml(p.titulo || 'Paquete')}</td>
          <td>${estado}</td>
        </tr>
      `;
    });

    html += '</tbody></table></div>';
    resultsEl.innerHTML = html;
  }

  function verDetalle(idx) {
    var p = _paquetesFiltrados[idx];
    if (!p) return;

    var fotoHtml = '';
    if (p.fotoCaptura) {
      fotoHtml = '<div class="form-group"><label>Foto de Evidencia</label>' +
        '<img src="' + p.fotoCaptura + '" style="width:100%;max-height:300px;object-fit:contain;border-radius:8px;border:1px solid var(--border);cursor:pointer" onclick="window.open(this.src)" title="Click para ver en tamaño completo"></div>';
    }

    var estadoHtml = p.entregado
      ? '<span class="badge badge-success">Entregado</span>'
      : '<span class="badge badge-warning">Pendiente</span>';

    var overlay = document.createElement('div');
    overlay.id = 'modal-detalle-paquete-admin';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:16000;display:flex;align-items:center;justify-content:center;padding:16px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:16px;max-width:520px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
      '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, var(--navy-50) 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
      '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
      '<div style="width:44px;height:44px;border-radius:12px;background:var(--navy-500);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
      '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">inventory_2</span></div>' +
      '<div><h3 style="margin:0;font-size:18px;font-weight:600">Detalles del Paquete</h3>' +
      '<p class="text-xs text-muted" style="margin:2px 0 0">Información completa de la entrega</p></div></div></div>' +
      '<div style="padding:24px">' +
      '<div class="form-group"><label>Apartamento</label><div class="form-control" style="background:#f9fafb;cursor:default;font-weight:600">' + Utils.escapeHtml(p.numeroApartamento || 'Desconocido') + '</div></div>' +
      (p.nombreResidente ? '<div class="form-group"><label>Residente</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(p.nombreResidente) + '</div></div>' : '') +
      '<div class="form-group"><label>Título</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(p.titulo || 'Paquete/Domicilio') + '</div></div>' +
      (p.cuerpo ? '<div class="form-group"><label>Descripción</label><div class="form-control" style="background:#f9fafb;cursor:default;min-height:60px;white-space:pre-wrap">' + Utils.escapeHtml(p.cuerpo) + '</div></div>' : '') +
      '<div class="form-group"><label>Fecha de Registro</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDateTime(p.fechaCreacion) + '</div></div>' +
      '<div class="form-group"><label>Estado</label><div style="display:flex;align-items:center;gap:8px">' + estadoHtml + '</div></div>' +
      fotoHtml +
      '</div>' +
      '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
      '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-paquete-admin\').remove()">Cerrar</button>' +
      '</div></div>';

    document.body.appendChild(overlay);

    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) overlay.remove();
    });
  }

  return {
    inicializar: inicializar,
    cargarPaquetes: cargarPaquetes,
    filtrarPaquetes: filtrarPaquetes,
    verDetalle: verDetalle
  };
})();

Router.register('paquetes-admin', {
  js: function() {
    PaquetesAdmin.inicializar();
  }
});