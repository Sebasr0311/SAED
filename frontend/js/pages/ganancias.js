const Ganancias = (() => {
  var _pagos = [];
  var _pagosFiltrados = [];
  const PAGE_SIZE = 8;
  var currentPage = 1;

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
    var container = document.getElementById('content-area');
    container.innerHTML = `
      <div class="card">
        <div class="card-title" style="display:flex;align-items:center;gap:12px">
          <span class="material-symbols-outlined" style="font-size:28px;color:var(--navy-500)">trending_up</span>
          <span>Historial de Ganancias</span>
        </div>
        <div class="card-body">

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
  }

  async function cargarPagos() {
    try {
      _pagos = await API.get('/pagos/registrados');
      _pagosFiltrados = _pagos.slice();
      currentPage = 1;
      renderTabla();
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

  function filtrarPagos() {
    var busqueda = (document.getElementById('gan-buscar').value || '').toLowerCase().trim();
    if (!busqueda) {
      _pagosFiltrados = _pagos.slice();
    } else {
      _pagosFiltrados = _pagos.filter(function(p) {
        return (p.apartamento || '').toLowerCase().includes(busqueda) ||
               (p.residente  || '').toLowerCase().includes(busqueda) ||
               (p.metodo     || '').toLowerCase().includes(busqueda) ||
               (p.descripcion|| '').toLowerCase().includes(busqueda) ||
               (p.tipoPago   || '').toLowerCase().includes(busqueda);
      });
    }
    currentPage = 1;
    renderTabla();
  }

  function renderTabla() {
    var resultsEl = document.getElementById('gan-resultados');
    if (!resultsEl) return;

    if (!_pagosFiltrados || _pagosFiltrados.length === 0) {
      resultsEl.innerHTML =
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">payments</span>' +
        '<p style="margin:8px 0 0">No se encontraron pagos registrados</p></div>';
      return;
    }

    var pg = Utils.paginate(_pagosFiltrados, currentPage, PAGE_SIZE);

    // Estadísticas
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

    // Botón exportar Excel
    html += `
      <div style="margin-top:20px;display:flex;justify-content:flex-end">
        <button class="btn btn-secondary" onclick="Ganancias.exportarExcel()" style="display:flex;align-items:center;gap:8px">
          <span class="material-symbols-outlined" style="font-size:20px">download</span>
          Exportar a Excel
        </button>
      </div>
    `;

    resultsEl.innerHTML = html;
  }

  function exportarExcel() {
    if (!_pagosFiltrados || _pagosFiltrados.length === 0) {
      Utils.showToast('No hay datos para exportar', 'warning');
      return;
    }

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

      var blob = new Blob([xls], { type: 'application/vnd.ms-excel' });
      var link = document.createElement('a');
      var url = URL.createObjectURL(blob);
      var filename = 'ganancias_' + Utils.todayStr() + '.xls';

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
    inicializar: inicializar,
    cargarPagos: cargarPagos,
    filtrarPagos: filtrarPagos,
    exportarExcel: exportarExcel,
    goToPage: goToPage
  };
})();

Router.register('ganancias', {
  js: function() {
    Ganancias.inicializar();
  }
});
