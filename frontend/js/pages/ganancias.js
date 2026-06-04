const Ganancias = (() => {
  var _pagos = [];           // todos los pagos cargados desde la API
  var _pagosFiltrados = [];  // pagos luego de aplicar filtro de fecha + búsqueda
  var _cargado = false;      // si ya se hizo la carga inicial
  const PAGE_SIZE = 8;
  var currentPage = 1;

  // ── helpers de fecha ────────────────────────────────────────────────────────

  function _yearStartStr() {
    return new Date().getFullYear() + '-01-01';
  }

  function _primerDiaMesStr() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-01';
  }

  function _limpiarErrorFecha(id) {
    var el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('is-invalid');
    var errEl = el.parentNode && el.parentNode.querySelector('.field-error');
    if (errEl) errEl.textContent = '';
  }

  // Devuelve true si las fechas son válidas, false y muestra error si no.
  function _validarFechas() {
    var inicioEl = document.getElementById('gan-fecha-inicio');
    var finEl    = document.getElementById('gan-fecha-fin');
    if (!inicioEl || !finEl) return true;

    var fi = inicioEl.value;
    var ff = finEl.value;

    if (!fi || !ff) {
      Utils.showToast('Selecciona ambas fechas para buscar', 'warning');
      return false;
    }

    var today     = Utils.todayStr();
    var yearStart = _yearStartStr();

    if (fi < yearStart) {
      Utils.mostrarError('gan-fecha-inicio', 'La fecha no puede ser anterior al inicio de este año');
      inicioEl.value = yearStart;
      return false;
    }
    if (ff < yearStart) {
      Utils.mostrarError('gan-fecha-fin', 'La fecha no puede ser anterior al inicio de este año');
      finEl.value = yearStart;
      return false;
    }
    if (fi > today) {
      Utils.mostrarError('gan-fecha-inicio', 'No se permiten fechas futuras');
      inicioEl.value = today;
      return false;
    }
    if (ff > today) {
      Utils.mostrarError('gan-fecha-fin', 'No se permiten fechas futuras');
      finEl.value = today;
      return false;
    }
    if (fi > ff) {
      Utils.mostrarError('gan-fecha-inicio', 'La fecha de inicio debe ser anterior o igual a la fecha fin');
      return false;
    }

    _limpiarErrorFecha('gan-fecha-inicio');
    _limpiarErrorFecha('gan-fecha-fin');
    return true;
  }

  // ── ciclo de vida ────────────────────────────────────────────────────────────

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(_pagosFiltrados.length / PAGE_SIZE)) return;
    currentPage = page;
    renderTabla();
  }

  function inicializar() {
    document.getElementById('page-title').textContent = 'Ganancias';
    renderPage();
    cargarPagos();
  }

  function renderPage() {
    var today      = Utils.todayStr();
    var yearStart  = _yearStartStr();
    var primerMes  = _primerDiaMesStr();

    var container = document.getElementById('content-area');
    container.innerHTML = `
      <div class="card">
        <div class="card-title" style="display:flex;align-items:center;gap:12px">
          <span class="material-symbols-outlined" style="font-size:28px;color:var(--navy-500)">trending_up</span>
          <span>Historial de Ganancias</span>
        </div>
        <div class="card-body">

          <!-- Filtro por fechas -->
          <div class="form-row" style="margin-bottom:24px">
            <div class="form-group">
              <label>Fecha Inicio</label>
              <input type="date" id="gan-fecha-inicio" class="form-control"
                value="${primerMes}" min="${yearStart}" max="${today}">
              <span class="field-error" id="gan-fecha-inicio-error"></span>
            </div>
            <div class="form-group">
              <label>Fecha Fin</label>
              <input type="date" id="gan-fecha-fin" class="form-control"
                value="${today}" min="${yearStart}" max="${today}">
              <span class="field-error" id="gan-fecha-fin-error"></span>
            </div>
            <div class="form-group" style="display:flex;align-items:flex-end">
              <button class="btn btn-primary" onclick="Ganancias.buscarConFechas()" style="height:40px">
                <span class="material-symbols-outlined" style="font-size:20px;margin-right:4px">search</span>
                Buscar
              </button>
            </div>
          </div>

          <!-- Búsqueda rápida -->
          <div class="form-group" style="margin-bottom:24px">
            <label>Búsqueda rápida</label>
            <input type="text" id="gan-buscar" class="form-control"
              placeholder="Buscar por apartamento, residente, método, descripción..."
              oninput="Ganancias.filtrarPagos()">
          </div>

          <!-- Resultados -->
          <div id="gan-resultados">
            <div class="text-muted" style="text-align:center;padding:40px">
              ${Utils.loadingSpinner()}
              <p style="margin-top:12px">Cargando historial de pagos...</p>
            </div>
          </div>

        </div>
      </div>
    `;

    // Validar en tiempo real al cambiar las fechas
    setTimeout(function() {
      var fi = document.getElementById('gan-fecha-inicio');
      var ff = document.getElementById('gan-fecha-fin');
      if (fi) fi.addEventListener('change', _validarFechas);
      if (ff) ff.addEventListener('change', _validarFechas);
    }, 0);
  }

  async function cargarPagos() {
    try {
      _pagos   = await API.get('/pagos/registrados');
      _cargado = true;
      // Aplicar filtro con las fechas por defecto ya puestas en los inputs
      filtrarPagos();
    } catch (e) {
      console.error('[Ganancias] Error al cargar:', e);
      var resultsEl = document.getElementById('gan-resultados');
      if (resultsEl) {
        resultsEl.innerHTML =
          '<div class="text-muted" style="text-align:center;padding:40px">' +
          '<span class="material-symbols-outlined" style="font-size:48px;color:var(--error);opacity:0.5">error</span>' +
          '<p style="margin:8px 0 0">Error al cargar pagos: ' + Utils.escapeHtml(e.message) + '</p></div>';
      }
    }
  }

  // Botón "Buscar": valida fechas y luego filtra
  function buscarConFechas() {
    if (!_cargado) {
      Utils.showToast('Los datos aún se están cargando, espera un momento', 'info');
      return;
    }
    if (!_validarFechas()) return;
    filtrarPagos();
  }

  // Filtra _pagos por fecha (inputs) + texto (búsqueda rápida) y actualiza _pagosFiltrados
  function filtrarPagos() {
    if (!_cargado) return;

    var fi       = ((document.getElementById('gan-fecha-inicio') || {}).value || '').trim();
    var ff       = ((document.getElementById('gan-fecha-fin')    || {}).value || '').trim();
    var busqueda = ((document.getElementById('gan-buscar')       || {}).value || '').toLowerCase().trim();

    var base = _pagos.slice();

    // Filtro de fecha (comparación de string ISO YYYY-MM-DD es lexicográficamente correcta)
    if (fi && ff) {
      base = base.filter(function(p) {
        var f = (p.fecha || '').substring(0, 10);
        return f >= fi && f <= ff;
      });
    } else if (fi) {
      base = base.filter(function(p) { return (p.fecha || '').substring(0, 10) >= fi; });
    } else if (ff) {
      base = base.filter(function(p) { return (p.fecha || '').substring(0, 10) <= ff; });
    }

    // Filtro de texto
    if (busqueda) {
      base = base.filter(function(p) {
        return (p.apartamento || '').toLowerCase().includes(busqueda) ||
               (p.residente   || '').toLowerCase().includes(busqueda) ||
               (p.metodo      || '').toLowerCase().includes(busqueda) ||
               (p.descripcion || '').toLowerCase().includes(busqueda) ||
               (p.tipoPago    || '').toLowerCase().includes(busqueda);
      });
    }

    _pagosFiltrados = base;
    currentPage = 1;
    renderTabla();
  }

  // ── renderizado ──────────────────────────────────────────────────────────────

  function renderTabla() {
    var resultsEl = document.getElementById('gan-resultados');
    if (!resultsEl) return;

    if (!_pagosFiltrados || _pagosFiltrados.length === 0) {
      var fi = ((document.getElementById('gan-fecha-inicio') || {}).value || '').trim();
      var ff = ((document.getElementById('gan-fecha-fin')    || {}).value || '').trim();
      var hayFiltroFecha = fi && ff;
      var msg = hayFiltroFecha
        ? 'No hay pagos registrados en el rango de fechas seleccionado'
        : 'No se encontraron pagos registrados';

      resultsEl.innerHTML =
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">payments</span>' +
        '<p style="margin:8px 0 0">' + msg + '</p></div>';
      return;
    }

    var pg = Utils.paginate(_pagosFiltrados, currentPage, PAGE_SIZE);

    // Estadísticas sobre el conjunto filtrado
    var totalPagos  = _pagosFiltrados.length;
    var totalValor  = _pagosFiltrados.reduce(function(s, p) { return s + (parseFloat(p.valor) || 0); }, 0);
    var totalCuotas = _pagosFiltrados.filter(function(p) { return (p.tipoPago || '').toUpperCase() === 'CUOTA'; }).length;
    var totalMultas = _pagosFiltrados.filter(function(p) { return (p.tipoPago || '').toUpperCase() === 'MULTA'; }).length;

    var html = `
      <div style="display:flex;gap:16px;margin-bottom:20px;flex-wrap:wrap">
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Total Pagos</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px">${totalPagos}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Ingresos Totales</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--success)">${Utils.formatCurrency(totalValor)}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Cuotas</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--navy-500)">${totalCuotas}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Multas</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--warning)">${totalMultas}</span>
        </div>
      </div>

      <div style="overflow-x:auto;margin-bottom:16px">
        <table class="data-table" style="min-width:800px">
          <thead>
            <tr>
              <th>#</th>
              <th>Fecha</th>
              <th>Tipo</th>
              <th>Apartamento</th>
              <th>Residente</th>
              <th>Descripción</th>
              <th>Método</th>
              <th style="text-align:right">Valor</th>
            </tr>
          </thead>
          <tbody>
    `;

    pg.items.forEach(function(p) {
      var tipo = (p.tipoPago || '').toUpperCase();
      var tipoBadge = tipo === 'CUOTA'
        ? '<span class="badge badge-info">Cuota</span>'
        : tipo === 'MULTA'
          ? '<span class="badge badge-warning">Multa</span>'
          : '<span class="badge">' + Utils.escapeHtml(p.tipoPago || '-') + '</span>';

      var fecha = p.fecha ? Utils.formatDate(p.fecha) : '-';

      html += '<tr>' +
        '<td style="color:var(--text-muted);font-size:12px">' + Utils.escapeHtml(String(p.id || '-')) + '</td>' +
        '<td style="white-space:nowrap">' + fecha + '</td>' +
        '<td>' + tipoBadge + '</td>' +
        '<td style="text-align:center;font-weight:600">' + Utils.escapeHtml(p.apartamento || '-') + '</td>' +
        '<td>' + Utils.escapeHtml(p.residente || '-') + '</td>' +
        '<td style="font-size:13px;color:var(--text-secondary)">' + Utils.escapeHtml(p.descripcion || '-') + '</td>' +
        '<td>' + Utils.escapeHtml(p.metodo || '-') + '</td>' +
        '<td style="text-align:right;font-weight:600;color:var(--success)">' + Utils.formatCurrency(p.valor) + '</td>' +
        '</tr>';
    });

    html += '</tbody></table></div>';
    html += '<div id="pagination-ganancias">' + Utils.paginationHtml(pg, 'Ganancias.goToPage') + '</div>';

    // Botón exportar Excel (solo visible cuando hay resultados)
    var fi = ((document.getElementById('gan-fecha-inicio') || {}).value || '').trim();
    var ff = ((document.getElementById('gan-fecha-fin')    || {}).value || '').trim();
    var rangoLabel = fi && ff ? ' (' + fi + ' al ' + ff + ')' : '';
    html += `
      <div style="margin-top:20px;display:flex;justify-content:flex-end">
        <button class="btn btn-secondary" onclick="Ganancias.exportarExcel()"
          style="display:flex;align-items:center;gap:8px">
          <span class="material-symbols-outlined" style="font-size:20px">download</span>
          Exportar a Excel${rangoLabel}
        </button>
      </div>
    `;

    resultsEl.innerHTML = html;
  }

  // ── exportar ─────────────────────────────────────────────────────────────────

  function exportarExcel() {
    if (!_pagosFiltrados || _pagosFiltrados.length === 0) {
      Utils.showToast('No hay registros en el rango seleccionado para exportar', 'warning');
      return;
    }

    var fi = ((document.getElementById('gan-fecha-inicio') || {}).value || Utils.todayStr()).trim();
    var ff = ((document.getElementById('gan-fecha-fin')    || {}).value || Utils.todayStr()).trim();

    try {
      var xls =
        '<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">' +
        '<head><meta charset="UTF-8"><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet>' +
        '<x:Name>Ganancias</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions>' +
        '</x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]-->' +
        '<style>td,th{padding:6px 10px;border:1px solid #ccc;font-size:12px;font-family:Arial}' +
        'th{background:#0F2044;color:#fff;font-weight:700}tr:nth-child(even){background:#f5f5f5}</style>' +
        '</head><body><table>' +
        '<thead><tr>' +
        '<th>#</th><th>Fecha</th><th>Tipo</th><th>Apartamento</th><th>Residente</th>' +
        '<th>Descripción</th><th>Método</th><th>Valor</th>' +
        '</tr></thead><tbody>';

      _pagosFiltrados.forEach(function(p) {
        xls += '<tr>' +
          '<td>' + Utils.escapeHtml(String(p.id || '')) + '</td>' +
          '<td>' + Utils.escapeHtml(p.fecha || '') + '</td>' +
          '<td>' + Utils.escapeHtml(p.tipoPago || '') + '</td>' +
          '<td>' + Utils.escapeHtml(p.apartamento || '') + '</td>' +
          '<td>' + Utils.escapeHtml(p.residente || '') + '</td>' +
          '<td>' + Utils.escapeHtml(p.descripcion || '') + '</td>' +
          '<td>' + Utils.escapeHtml(p.metodo || '') + '</td>' +
          '<td>' + (parseFloat(p.valor) || 0).toFixed(2) + '</td>' +
          '</tr>';
      });

      xls += '</tbody></table></body></html>';

      var blob    = new Blob([xls], { type: 'application/vnd.ms-excel' });
      var link    = document.createElement('a');
      var url     = URL.createObjectURL(blob);
      var filename = 'ganancias_' + fi + '_' + ff + '.xls';

      link.setAttribute('href', url);
      link.setAttribute('download', filename);
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      Utils.showToast('Archivo exportado (' + _pagosFiltrados.length + ' registros)', 'success');
    } catch (e) {
      console.error('[Ganancias] Error al exportar:', e);
      Utils.showToast('Error al exportar: ' + e.message, 'error');
    }
  }

  return {
    inicializar:      inicializar,
    cargarPagos:      cargarPagos,
    buscarConFechas:  buscarConFechas,
    filtrarPagos:     filtrarPagos,
    exportarExcel:    exportarExcel,
    goToPage:         goToPage
  };
})();

Router.register('ganancias', {
  js: function() {
    Ganancias.inicializar();
  }
});
