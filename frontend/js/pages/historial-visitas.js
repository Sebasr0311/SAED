const HistorialVisitas = (() => {
  let _visitas = [];
  let _filteredVisitas = [];

  async function inicializar() {
    renderPage();
    await cargarVisitas();
  }

  function renderPage() {
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayStr = today.toISOString().split('T')[0];
    var lastWeek = new Date(today);
    lastWeek.setDate(lastWeek.getDate() - 7);
    var lastWeekStr = lastWeek.toISOString().split('T')[0];
    
    // Fecha mínima: 2 años atrás
    var dosAnosAtras = new Date(today);
    dosAnosAtras.setFullYear(dosAnosAtras.getFullYear() - 2);
    var minDateStr = dosAnosAtras.toISOString().split('T')[0];

    var html = `
      <div class="card">
        <div class="card-title" style="display:flex;align-items:center;gap:12px">
          <span class="material-symbols-outlined" style="font-size:28px;color:var(--navy-500)">history</span>
          <span>Historial de Visitas</span>
        </div>
        <div class="card-body">
          <!-- Filtros -->
          <div class="form-row" style="margin-bottom:24px">
            <div class="form-group">
              <label>Fecha Inicio</label>
              <input type="date" id="hist-fecha-inicio" class="form-control" value="${lastWeekStr}" min="${minDateStr}" max="${todayStr}">
              <span class="field-error" id="hist-fecha-inicio-error"></span>
            </div>
            <div class="form-group">
              <label>Fecha Fin</label>
              <input type="date" id="hist-fecha-fin" class="form-control" value="${todayStr}" min="${minDateStr}" max="${todayStr}">
              <span class="field-error" id="hist-fecha-fin-error"></span>
            </div>
            <div class="form-group" style="display:flex;align-items:flex-end">
              <button class="btn btn-primary" onclick="HistorialVisitas.cargarVisitas()" style="height:40px">
                <span class="material-symbols-outlined" style="font-size:20px;margin-right:4px">search</span>
                Buscar
              </button>
            </div>
          </div>

          <!-- Búsqueda rápida -->
          <div class="form-group" style="margin-bottom:24px">
            <label>Búsqueda rápida</label>
            <input type="text" id="hist-buscar" class="form-control" 
              placeholder="Buscar por visitante, apartamento, documento..."
              oninput="HistorialVisitas.filtrarVisitas()">
          </div>

          <!-- Tabla de resultados -->
          <div id="hist-resultados">
            <div class="text-muted" style="text-align:center;padding:40px">
              <span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">search</span>
              <p style="margin:8px 0 0">Selecciona un rango de fechas y haz clic en Buscar</p>
            </div>
          </div>
        </div>
      </div>
    `;

    var container = document.getElementById('content-area');
    container.innerHTML = html;
    
    // Configurar validación en tiempo real
    document.getElementById('hist-fecha-inicio').addEventListener('change', validarFechas);
    document.getElementById('hist-fecha-fin').addEventListener('change', validarFechas);
  }
  
  function validarFechas() {
    var fechaInicioInput = document.getElementById('hist-fecha-inicio');
    var fechaFinInput = document.getElementById('hist-fecha-fin');
    var fechaInicio = fechaInicioInput.value;
    var fechaFin = fechaFinInput.value;
    
    if (!fechaInicio || !fechaFin) return true;
    
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayStr = today.toISOString().split('T')[0];
    
    var dosAnosAtras = new Date(today);
    dosAnosAtras.setFullYear(dosAnosAtras.getFullYear() - 2);
    var minDateStr = dosAnosAtras.toISOString().split('T')[0];
    
    // Validar que no sean fechas futuras
    if (fechaInicio > todayStr) {
      Utils.mostrarError('hist-fecha-inicio', 'No se pueden buscar fechas futuras');
      fechaInicioInput.value = todayStr;
      return false;
    }
    if (fechaFin > todayStr) {
      Utils.mostrarError('hist-fecha-fin', 'No se pueden buscar fechas futuras');
      fechaFinInput.value = todayStr;
      return false;
    }
    
    // Validar que no sean fechas muy antiguas (más de 2 años)
    if (fechaInicio < minDateStr) {
      Utils.mostrarError('hist-fecha-inicio', 'No se pueden buscar fechas de más de 2 años atrás');
      fechaInicioInput.value = minDateStr;
      return false;
    }
    if (fechaFin < minDateStr) {
      Utils.mostrarError('hist-fecha-fin', 'No se pueden buscar fechas de más de 2 años atrás');
      fechaFinInput.value = minDateStr;
      return false;
    }
    
    // Limpiar errores si todo está bien
    var errInicio = document.getElementById('hist-fecha-inicio');
    if (errInicio) { errInicio.classList.remove('is-invalid'); var e = errInicio.parentNode.querySelector('.field-error'); if (e) e.textContent = ''; }
    var errFin = document.getElementById('hist-fecha-fin');
    if (errFin) { errFin.classList.remove('is-invalid'); var e = errFin.parentNode.querySelector('.field-error'); if (e) e.textContent = ''; }
    return true;
  }

  async function cargarVisitas() {
    try {
      var fechaInicio = document.getElementById('hist-fecha-inicio').value;
      var fechaFin = document.getElementById('hist-fecha-fin').value;

      if (!fechaInicio || !fechaFin) {
        Utils.showToast('Selecciona ambas fechas', 'warning');
        return;
      }
      
      // Validar fechas antes de buscar
      if (!validarFechas()) {
        return;
      }

      if (fechaInicio > fechaFin) {
        Utils.showToast('La fecha de inicio no puede ser mayor que la fecha fin', 'warning');
        return;
      }

      var resultsEl = document.getElementById('hist-resultados');
      resultsEl.innerHTML = '<div class="text-muted" style="text-align:center;padding:40px">' + Utils.loadingSpinner() + '<p style="margin-top:12px">Cargando visitas...</p></div>';

      _visitas = await API.get('/visitas/historial?fechaInicio=' + fechaInicio + '&fechaFin=' + fechaFin);
      _filteredVisitas = _visitas;

      renderTabla();
    } catch (e) {
      console.error('[HistorialVisitas] Error al cargar:', e);
      document.getElementById('hist-resultados').innerHTML = 
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;color:var(--error);opacity:0.5">error</span>' +
        '<p style="margin:8px 0 0">Error al cargar visitas: ' + Utils.escapeHtml(e.message) + '</p></div>';
    }
  }

  function filtrarVisitas() {
    var busqueda = document.getElementById('hist-buscar').value.toLowerCase();
    
    if (!busqueda) {
      _filteredVisitas = _visitas;
    } else {
      _filteredVisitas = _visitas.filter(function(v) {
        var nombreVisitante = (v.nombreVisitante || '').toLowerCase();
        var documentoVisitante = (v.documentoVisitante || '').toLowerCase();
        var apartamento = (v.numeroApartamento || '').toLowerCase();
        var nombreResidente = (v.nombreResidente || '').toLowerCase();
        
        return nombreVisitante.includes(busqueda) ||
               documentoVisitante.includes(busqueda) ||
               apartamento.includes(busqueda) ||
               nombreResidente.includes(busqueda);
      });
    }

    renderTabla();
  }

  function renderTabla() {
    var resultsEl = document.getElementById('hist-resultados');

    if (!_filteredVisitas || _filteredVisitas.length === 0) {
      resultsEl.innerHTML = 
        '<div class="text-muted" style="text-align:center;padding:40px">' +
        '<span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">inbox</span>' +
        '<p style="margin:8px 0 0">No se encontraron visitas en el rango seleccionado</p></div>';
      return;
    }

    // Contador de visitas
    var totalVisitas = _filteredVisitas.length;
    var visitasActivas = _filteredVisitas.filter(function(v) { return v.estado === 'ACTIVA'; }).length;
    var visitasFinalizadas = _filteredVisitas.filter(function(v) { return v.estado === 'FINALIZADA'; }).length;

    var html = `
      <div style="display:flex;gap:16px;margin-bottom:20px;flex-wrap:wrap">
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Total Visitas</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px">${totalVisitas}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Activas</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--info)">${visitasActivas}</span>
        </div>
        <div class="stat-card" style="flex:1;min-width:160px">
          <span class="text-xs text-muted">Finalizadas</span>
          <span class="text-2xl font-bold" style="display:block;margin-top:6px;color:var(--success)">${visitasFinalizadas}</span>
        </div>
      </div>

      <div style="overflow-x:auto;margin-bottom:16px">
        <table class="data-table" style="min-width:900px">
          <thead>
            <tr>
              <th>Fecha Registro</th>
              <th>Visitante</th>
              <th>Documento</th>
              <th>Apartamento</th>
              <th>Residente</th>
              <th>Entrada</th>
              <th>Salida</th>
              <th>Parqueadero</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
    `;

    _filteredVisitas.forEach(function(v) {
      var fechaRegistro = v.fechaRegistro ? Utils.formatDateTime(v.fechaRegistro) : '-';
      var entrada = v.fechaVisita ? Utils.formatTime(v.fechaVisita) : '-';
      var salida = v.fechaSalida ? Utils.formatTime(v.fechaSalida) : '-';
      var estado = v.estado || 'PENDIENTE';
      var estadoBadge = Utils.estadoBadge(estado);

      html += `
        <tr onclick="HistorialVisitas.verDetalleVisita(${v.idVisita})" style="cursor:pointer;transition:background 0.2s" onmouseenter="this.style.background='var(--navy-50)'" onmouseleave="this.style.background=''">
          <td style="white-space:nowrap">${fechaRegistro}</td>
          <td>${Utils.escapeHtml(v.nombreVisitante || 'Sin nombre')}</td>
          <td>${Utils.escapeHtml(v.documentoVisitante || '-')}</td>
          <td style="text-align:center;font-weight:600">${Utils.escapeHtml(v.numeroApartamento || '-')}</td>
          <td>${Utils.escapeHtml(v.nombreResidente || '-')}</td>
          <td style="white-space:nowrap">${entrada}</td>
          <td style="white-space:nowrap">${salida}</td>
          <td style="text-align:center;font-weight:600;color:var(--success)">${Utils.escapeHtml(v.codigoParqueadero || '-')}</td>
          <td>${estadoBadge}</td>
        </tr>
      `;
    });

    html += '</tbody></table></div>';

    // Botón de exportar
    html += `
      <div style="margin-top:20px;display:flex;justify-content:flex-end">
        <button class="btn btn-secondary" onclick="HistorialVisitas.exportarExcel()" style="display:flex;align-items:center;gap:8px">
          <span class="material-symbols-outlined" style="font-size:20px">download</span>
          Exportar a Excel
        </button>
      </div>
    `;

    resultsEl.innerHTML = html;
  }

  function exportarExcel() {
    if (!_filteredVisitas || _filteredVisitas.length === 0) {
      Utils.showToast('No hay datos para exportar', 'warning');
      return;
    }

    try {
      var xls = '<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">' +
        '<head><meta charset="UTF-8"><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>Historial</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]-->' +
        '<style>td,th{padding:6px 10px;border:1px solid #ccc;font-size:12px;font-family:Arial}th{background:#0F2044;color:#fff;font-weight:700}tr:nth-child(even){background:#f5f5f5}</style></head><body>' +
        '<table>' +
        '<thead><tr>' +
        '<th>Fecha Registro</th><th>Visitante</th><th>Documento</th><th>Apartamento</th><th>Residente</th><th>Entrada</th><th>Salida</th><th>Tipo Vehículo</th><th>Placa</th><th>Parqueadero</th><th>Estado</th>' +
        '</tr></thead><tbody>';

      _filteredVisitas.forEach(function(v) {
        xls += '<tr>' +
          '<td>' + Utils.escapeHtml(v.fechaRegistro ? Utils.formatDateTime(v.fechaRegistro) : '') + '</td>' +
          '<td>' + Utils.escapeHtml((v.nombreVisitante || '') + ' ' + (v.apellidoVisitante || '')) + '</td>' +
          '<td>' + Utils.escapeHtml(v.documentoVisitante || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.numeroApartamento || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.nombreResidente || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.fechaVisita ? Utils.formatDateTime(v.fechaVisita) : '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.fechaSalida ? Utils.formatDateTime(v.fechaSalida) : '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.tipoVehiculo || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.placaVehiculo || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.codigoParqueadero || '') + '</td>' +
          '<td>' + Utils.escapeHtml(v.estado || '') + '</td>' +
          '</tr>';
      });

      xls += '</tbody></table></body></html>';

      var blob = new Blob([xls], { type: 'application/vnd.ms-excel' });
      var link = document.createElement('a');
      var url = URL.createObjectURL(blob);

      var fechaInicio = document.getElementById('hist-fecha-inicio').value;
      var fechaFin = document.getElementById('hist-fecha-fin').value;
      var filename = 'historial_visitas_' + fechaInicio + '_' + fechaFin + '.xls';

      link.setAttribute('href', url);
      link.setAttribute('download', filename);
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      Utils.showToast('Archivo exportado exitosamente (' + _filteredVisitas.length + ' registros)', 'success');
    } catch (e) {
      console.error('[HistorialVisitas] Error al exportar:', e);
      Utils.showToast('Error al exportar: ' + e.message, 'error');
    }
  }

  async function verDetalleVisita(idVisita) {
    try {
      var visita = await API.get('/visitas/' + idVisita + '/detalle');
      
      var overlay = document.createElement('div');
      overlay.id = 'modal-detalle-visita-historial';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
      
      var estadoColor = visita.estado === 'FINALIZADA' ? 'var(--success)' : visita.estado === 'ACTIVA' ? 'var(--warning)' : 'var(--navy-500)';
      var estadoIcon = visita.estado === 'FINALIZADA' ? 'check_circle' : visita.estado === 'ACTIVA' ? 'schedule' : 'pending';
      
      var html = '<div style="background:#fff;border-radius:16px;max-width:800px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
        '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, ' + estadoColor + ' 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:' + estadoColor + ';display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">' + estadoIcon + '</span></div>' +
        '<div><h3 style="margin:0;font-size:18px;font-weight:600">Detalle de Visita</h3>' +
        '<p class="text-xs text-muted" style="margin:2px 0 0">' + Utils.estadoBadge(visita.estado || 'PENDIENTE') + '</p></div></div></div>' +
        '<div style="padding:24px">';
      
      // Información del visitante
      html += '<div class="card" style="background:var(--accent-50);border:1px solid var(--accent-200);margin-bottom:16px;padding:16px">' +
        '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text);display:flex;align-items:center;gap:8px">' +
        '<span class="material-symbols-outlined" style="font-size:20px">person</span>Visitante</h4>' +
        '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
        '<div><span class="text-muted">Nombre:</span><br><strong>' + Utils.escapeHtml((visita.nombreVisitante || '') + ' ' + (visita.apellidoVisitante || '')) + '</strong></div>' +
        '<div><span class="text-muted">Documento:</span><br><strong>' + Utils.escapeHtml(visita.documentoVisitante || '-') + '</strong></div>' +
        '<div><span class="text-muted">Teléfono:</span><br><strong>' + Utils.escapeHtml(visita.telefonoVisitante || '-') + '</strong></div>' +
        '<div><span class="text-muted">Email:</span><br><strong style="word-break:break-all">' + Utils.escapeHtml(visita.emailVisitante || '-') + '</strong></div>' +
        '</div></div>';
      
      // Información del residente
      html += '<div class="card" style="margin-bottom:16px;padding:16px">' +
        '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text);display:flex;align-items:center;gap:8px">' +
        '<span class="material-symbols-outlined" style="font-size:20px">home</span>Residente Anfitrión</h4>' +
        '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
        '<div><span class="text-muted">Nombre:</span><br><strong>' + Utils.escapeHtml(visita.nombreResidente || '-') + '</strong></div>' +
        '<div><span class="text-muted">Apartamento:</span><br><strong style="font-size:16px;color:var(--navy-500)">' + Utils.escapeHtml(visita.numeroApartamento || '-') + '</strong></div>' +
        '</div></div>';
      
      // Detalles de la visita
      html += '<div class="card" style="margin-bottom:16px;padding:16px">' +
        '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text);display:flex;align-items:center;gap:8px">' +
        '<span class="material-symbols-outlined" style="font-size:20px">event</span>Detalles de la Visita</h4>' +
        '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
        '<div><span class="text-muted">Fecha Registro:</span><br><strong>' + (visita.fechaRegistro ? Utils.formatDateTime(visita.fechaRegistro) : '-') + '</strong></div>' +
        '<div><span class="text-muted">Tipo:</span><br><strong>' + (visita.esFrecuente ? 'Frecuente' : 'Ocasional') + '</strong></div>' +
        '<div><span class="text-muted">Fecha/Hora Entrada:</span><br><strong>' + (visita.fechaVisita ? Utils.formatDateTime(visita.fechaVisita) : '-') + '</strong></div>' +
        '<div><span class="text-muted">Fecha/Hora Salida:</span><br><strong>' + (visita.fechaSalida ? Utils.formatDateTime(visita.fechaSalida) : '-') + '</strong></div>' +
        '</div>';
      
      if (visita.motivoVisita) {
        html += '<div style="margin-top:12px;padding-top:12px;border-top:1px solid var(--border-subtle)">' +
          '<span class="text-muted">Motivo:</span><br><strong>' + Utils.escapeHtml(visita.motivoVisita) + '</strong></div>';
      }
      
      html += '</div>';
      
      // Información de vehículo/parqueadero
      if (visita.tipoVehiculo || visita.codigoParqueadero) {
        html += '<div class="card" style="margin-bottom:16px;padding:16px">' +
          '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text);display:flex;align-items:center;gap:8px">' +
          '<span class="material-symbols-outlined" style="font-size:20px">directions_car</span>Vehículo y Parqueadero</h4>' +
          '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
          '<div><span class="text-muted">Tipo:</span><br><strong>' + Utils.escapeHtml(visita.tipoVehiculo || '-') + '</strong></div>' +
          '<div><span class="text-muted">Placa:</span><br><strong style="font-family:monospace;font-size:14px">' + Utils.escapeHtml(visita.placaVehiculo || '-') + '</strong></div>' +
          '<div><span class="text-muted">Parqueadero:</span><br><strong style="font-size:16px;color:var(--success)">' + Utils.escapeHtml(visita.codigoParqueadero || '-') + '</strong></div>';
        
        if (visita.descripcionVehiculo) {
          html += '<div><span class="text-muted">Descripción:</span><br><strong>' + Utils.escapeHtml(visita.descripcionVehiculo) + '</strong></div>';
        }
        
        html += '</div></div>';
      }
      
      // Foto captura (si existe)
      if (visita.fotoCaptura) {
        var srcFoto = visita.fotoCaptura.indexOf('data:') === 0 ? visita.fotoCaptura : 'data:image/jpeg;base64,' + visita.fotoCaptura;
        html += '<div class="card" style="margin-bottom:16px;padding:16px">' +
          '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text);display:flex;align-items:center;gap:8px">' +
          '<span class="material-symbols-outlined" style="font-size:20px">photo_camera</span>Foto de Captura</h4>' +
          '<div style="text-align:center">' +
          '<img src="' + srcFoto + '" onclick="Utils.mostrarFotoGrande(this.src)" ' +
          'style="max-width:100%;max-height:300px;border-radius:8px;cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,0.15);transition:transform 0.2s" ' +
          'onmouseenter="this.style.transform=\'scale(1.02)\'" onmouseleave="this.style.transform=\'scale(1)\'">' +
          '<p class="text-xs text-muted" style="margin:8px 0 0">Haz clic para ver en tamaño completo</p>' +
          '</div></div>';
      }
      
      html += '</div>' +
        '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
        '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-visita-historial\').remove()">Cerrar</button>' +
        '</div></div>';
      
      overlay.innerHTML = html;
      document.body.appendChild(overlay);
    } catch(e) {
      Utils.showToast('Error al cargar detalle: ' + e.message, 'error');
    }
  }

  var exported = {
    inicializar: inicializar,
    cargarVisitas: cargarVisitas,
    filtrarVisitas: filtrarVisitas,
    verDetalleVisita: verDetalleVisita,
    exportarExcel: exportarExcel
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = exported;
  }

  return exported;
})();

Router.register('historial-visitas', {
  js: function() {
    const title = document.getElementById('page-title');
    if (title) title.textContent = 'Historial de Visitas';
    HistorialVisitas.inicializar();
  }
});
