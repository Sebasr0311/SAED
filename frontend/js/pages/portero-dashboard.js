const PorteroDash = (() => {
  console.log('[PorteroDash] Módulo inicializado');
  var _visitas = [];
  var _parqueaderos = [];
  var _paquetesPendientes = 0;
  var _multaFotoData = null;
  var _multaStream = null;

  async function inicializar() {
    document.getElementById('page-title').textContent = 'Panel del Portero';
    try {
      var [v, p, paq, vHoy] = await Promise.all([
        API.get('/visitas').catch(function() { return []; }),
        API.get('/parqueaderos').catch(function() { return []; }),
        API.get('/buzon/paquetes-pendientes').catch(function() { return {count: 0}; }),
        API.get('/visitas/hoy').catch(function() { return []; })
      ]);
      _visitas = v;
      _parqueaderos = p;
      _paquetesPendientes = paq.count || 0;
      renderStats(vHoy);
      renderVisitasTabla();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  function renderStats(visitasHoy) {
    visitasHoy = visitasHoy || [];
    var activas = _visitas.filter(function(v) { return v.estado === 'ACTIVA' || v.estado === 'PENDIENTE'; });
    var disponiblesVisitantes = _parqueaderos.filter(function(p) { return p.estado === 'DISPONIBLE' && p.esVisitante === true; });
    function s(id, v) { var el = document.getElementById(id); if (el) el.textContent = (v != null ? v : '-'); }
    s('por-visitas-hoy', visitasHoy.length);
    s('por-visitas-activas', activas.length);
    s('por-parq-disponibles', disponiblesVisitantes.length);
    s('por-paquetes-pendientes', _paquetesPendientes);
  }

  function renderVisitasTabla() {
    // Esta función ya no se usa, mantenida por compatibilidad
    var container = document.getElementById('por-visitas-tabla');
    if (!container) return;
    container.innerHTML = '';
  }

  /* ─── Modal Visitas Hoy ─── */

  var _modalVisitasAbierto = false;
  var _visitasHoyData = [];

  async function mostrarVisitasHoy() {
    if (_modalVisitasAbierto) return;
    _modalVisitasAbierto = true;
    var overlay = document.createElement('div');
    overlay.id = 'modal-visitas-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.4);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:12px;max-width:720px;width:100%;max-height:90vh;display:flex;flex-direction:column">' +
      '<div style="padding:24px;border-bottom:1px solid var(--border)">' +
      '<h3 style="margin:0;font-size:18px">Visitas de Hoy</h3>' +
      '<p class="text-sm text-muted" style="margin:4px 0 0">Todas las visitas registradas hoy, incluyendo las que ya registraron salida.</p>' +
      '</div>' +
      '<div id="visitas-hoy-list" style="flex:1;overflow-y:auto;padding:16px">Cargando...</div>' +
      '<div style="padding:16px;border-top:1px solid var(--border);text-align:right">' +
      '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalVisitas()">Cerrar</button></div></div>';
    document.body.appendChild(overlay);
    cargarVisitasHoy();
  }

  function cerrarModalVisitas() {
    var overlay = document.getElementById('modal-visitas-overlay');
    if (overlay) overlay.remove();
    _modalVisitasAbierto = false;
    _visitasHoyData = [];
  }

  async function cargarVisitasHoy() {
    var listEl = document.getElementById('visitas-hoy-list');
    if (!listEl) return;
    try {
      listEl.innerHTML = '<div class="text-muted" style="padding:24px;text-align:center">Cargando...</div>';
      
      // Cargar visitas de hoy desde el endpoint específico
      var visitasHoy = await API.get('/visitas/hoy');
      _visitasHoyData = visitasHoy;
      
      if (!_visitasHoyData || !_visitasHoyData.length) {
        listEl.innerHTML = '<div class="text-muted" style="padding:40px;text-align:center"><span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">event_busy</span><p style="margin:8px 0 0">No hay visitas registradas hoy</p></div>';
        return;
      }
      
      var html = '';
      for (var i = 0; i < _visitasHoyData.length; i++) {
        var v = _visitasHoyData[i];
        var entrada = v.fechaVisita ? Utils.formatTime(v.fechaVisita) : '-';
        var salida = v.fechaSalida ? Utils.formatTime(v.fechaSalida) : '-';
        var estado = v.estado || 'PENDIENTE';
        var estadoBadge = Utils.estadoBadge(estado);
        var iconColor = estado === 'FINALIZADA' ? '#10b981' : (estado === 'ACTIVA' ? '#3b82f6' : '#f59e0b');
        var icon = estado === 'FINALIZADA' ? 'check_circle' : (estado === 'ACTIVA' ? 'how_to_reg' : 'schedule');
        
        var fotoBtn = v.fotoCaptura ? '<button class="btn btn-ghost" style="padding:8px;min-width:auto;border-radius:8px;margin-left:8px" onclick="event.stopPropagation();PorteroDash.mostrarFoto(\'' + v.fotoCaptura + '\')" title="Ver foto del visitante">' +
          '<span class="material-symbols-outlined" style="font-size:20px">image</span></button>' : '';
        
        html += '<div class="card" onclick="PorteroDash.verDetalleVisita(' + v.idVisita + ')" style="padding:16px;margin-bottom:12px;display:flex;align-items:center;gap:12px;cursor:pointer;transition:all 0.2s" onmouseover="this.style.background=\'var(--navy-50)\'" onmouseout="this.style.background=\'white\'">' +
          '<div style="width:48px;height:48px;border-radius:8px;background:color-mix(in srgb,' + iconColor + ' 10%,transparent);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
          '<span class="material-symbols-outlined" style="font-size:28px;color:' + iconColor + '">' + icon + '</span></div>' +
          '<div style="flex:1;min-width:0">' +
          '<p style="font-weight:600;margin:0 0 4px;font-size:14px">' + Utils.escapeHtml(v.nombreVisitante || 'Visitante') + '</p>' +
          '<p class="text-xs text-muted" style="margin:0">Apto: ' + Utils.escapeHtml(v.numeroApartamento || '?') + ' • Entrada: ' + entrada + ' • Salida: ' + salida + '</p>' +
          '</div>' +
          '<div style="display:flex;align-items:center;gap:8px">' + estadoBadge + fotoBtn + '</div></div>';
      }
      listEl.innerHTML = html;
    } catch (e) {
      console.error('[PorteroDash] Error al cargar visitas de hoy:', e);
      listEl.innerHTML = '<p class="text-muted" style="padding:24px;text-align:center">Error al cargar visitas: ' + Utils.escapeHtml(e.message) + '</p>';
    }
  }

  /* ─── Modal Registrar Salida ─── */

  var _modalSalidaAbierto = false;
  var _visitasActivasData = [];

  async function mostrarRegistrarSalida() {
    console.log('[PorteroDash] Abriendo modal de Registrar Salida...');
    console.log('[PorteroDash] Modal ya abierto?', _modalSalidaAbierto);
    console.log('[PorteroDash] Visitas cargadas:', _visitas.length);
    
    if (_modalSalidaAbierto) {
      console.log('[PorteroDash] Modal ya está abierto, cancelando');
      return;
    }
    _modalSalidaAbierto = true;
    
    var overlay = document.createElement('div');
    overlay.id = 'modal-salida-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px;backdrop-filter:blur(4px)';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:16px;max-width:960px;width:100%;max-height:90vh;display:flex;flex-direction:column;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
      '<div style="padding:28px;border-bottom:1px solid var(--border);background:linear-gradient(135deg,var(--navy-50) 0%,var(--surface) 100%)">' +
      '<div style="display:flex;align-items:center;gap:12px;margin-bottom:6px">' +
      '<div style="width:44px;height:44px;background:var(--navy-600);border-radius:10px;display:flex;align-items:center;justify-content:center">' +
      '<span class="material-symbols-outlined" style="color:#fff;font-size:24px">logout</span></div>' +
      '<div><h3 style="margin:0;font-size:20px;font-weight:700;color:var(--text)">Registrar Salida de Visitas</h3>' +
      '<p class="text-sm text-muted" style="margin:0">Selecciona la visita activa para registrar su salida</p></div></div></div>' +
      '<div id="visitas-salida-list" style="flex:1;overflow-y:auto;padding:20px">Cargando...</div>' +
      '<div style="padding:20px;border-top:1px solid var(--border);background:var(--surface-container);text-align:right">' +
      '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalSalida()">Cerrar</button></div></div>';
    
    console.log('[PorteroDash] Modal creado, agregando al DOM');
    document.body.appendChild(overlay);
    console.log('[PorteroDash] Modal agregado, cargando visitas activas...');
    cargarVisitasActivas();
  }

  function cerrarModalSalida() {
    var overlay = document.getElementById('modal-salida-overlay');
    if (overlay) overlay.remove();
    _modalSalidaAbierto = false;
    _visitasActivasData = [];
  }

  async function cargarVisitasActivas() {
    var listEl = document.getElementById('visitas-salida-list');
    if (!listEl) {
      console.log('[PorteroDash] ERROR: No se encontró elemento visitas-salida-list');
      return;
    }
    
    console.log('[PorteroDash] Cargando visitas activas...');
    console.log('[PorteroDash] Total visitas:', _visitas.length);
    
    try {
      _visitasActivasData = _visitas.filter(function(v) { 
        return v.estado === 'ACTIVA' || v.estado === 'PENDIENTE'; 
      });
      
      console.log('[PorteroDash] Visitas activas filtradas:', _visitasActivasData.length);
      console.log('[PorteroDash] Estados encontrados:', _visitas.map(function(v) { return v.estado; }));
      
      if (!_visitasActivasData || !_visitasActivasData.length) {
        console.log('[PorteroDash] No hay visitas activas, mostrando empty state');
        listEl.innerHTML = '<div class="text-muted" style="padding:60px 20px;text-align:center">' +
          '<span class="material-symbols-outlined" style="font-size:64px;opacity:0.2;color:var(--navy-300)">event_busy</span>' +
          '<p style="margin:16px 0 0;font-size:16px;font-weight:600">No hay visitas activas</p>' +
          '<p style="margin:4px 0 0;font-size:13px;color:var(--text-secondary)">Todas las visitas de hoy ya registraron su salida</p></div>';
        return;
      }
      
      var html = '<div style="overflow-x:auto">' +
        '<table class="data-table" style="width:100%;border-collapse:separate;border-spacing:0">' +
        '<thead><tr style="background:var(--navy-50)">' +
        '<th style="padding:14px 16px;text-align:left;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Visitante</th>' +
        '<th style="padding:14px 16px;text-align:left;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Documento</th>' +
        '<th style="padding:14px 16px;text-align:left;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Apartamento</th>' +
        '<th style="padding:14px 16px;text-align:left;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Hora Ingreso</th>' +
        '<th style="padding:14px 16px;text-align:left;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Estado</th>' +
        '<th style="padding:14px 16px;text-align:center;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Foto</th>' +
        '<th style="padding:14px 16px;text-align:center;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:var(--navy-700);border-bottom:2px solid var(--navy-200)">Acción</th>' +
        '</tr></thead><tbody>';
      
      for (var i = 0; i < _visitasActivasData.length; i++) {
        var v = _visitasActivasData[i];
        var entrada = Utils.formatTime(v.fechaVisita);
        var estado = v.estado || 'PENDIENTE';
        var estadoBadge = Utils.estadoBadge(estado);
        var rowBg = i % 2 === 0 ? 'var(--surface)' : 'var(--surface-container)';
        var fotoBtn = v.fotoCaptura ? '<button class="btn btn-ghost" style="padding:6px;min-width:auto" onclick="event.stopPropagation();PorteroDash.mostrarFoto(\'' + v.fotoCaptura + '\')" title="Ver foto">' +
          '<span class="material-symbols-outlined" style="font-size:20px">image</span></button>' : '<span class="text-muted" style="font-size:12px">Sin foto</span>';
        
        html += '<tr style="background:' + rowBg + ';transition:all 0.15s;cursor:pointer" ' +
          'onmouseover="this.style.background=\'var(--navy-50)\'" onmouseout="this.style.background=\'' + rowBg + '\'" ' +
          'onclick="PorteroDash.verDetalleVisita(' + v.idVisita + ')">' +
          '<td style="padding:16px;font-size:14px;font-weight:600;color:var(--text);border-bottom:1px solid var(--border-subtle)">' + Utils.escapeHtml(v.nombreVisitante || v.residente || 'Sin nombre') + '</td>' +
          '<td style="padding:16px;font-size:13px;color:var(--text-secondary);border-bottom:1px solid var(--border-subtle)">' + Utils.escapeHtml(v.documentoVisitante || v.numeroDocumento || '-') + '</td>' +
          '<td style="padding:16px;font-size:13px;color:var(--text-secondary);border-bottom:1px solid var(--border-subtle)">' + Utils.escapeHtml(v.numeroApartamento || v.apartamento || '-') + '</td>' +
          '<td style="padding:16px;font-size:13px;color:var(--text-secondary);border-bottom:1px solid var(--border-subtle)">' + entrada + '</td>' +
          '<td style="padding:16px;border-bottom:1px solid var(--border-subtle)">' + estadoBadge + '</td>' +
          '<td style="padding:16px;text-align:center;border-bottom:1px solid var(--border-subtle)" onclick="event.stopPropagation()">' + fotoBtn + '</td>' +
          '<td style="padding:16px;text-align:center;border-bottom:1px solid var(--border-subtle)" onclick="event.stopPropagation()">' +
          '<button class="btn btn-sm" style="background:#f97316;color:#fff;border:none;padding:8px 16px;font-size:12px;font-weight:600;border-radius:6px;cursor:pointer;transition:all 0.15s" ' +
          'onmouseover="this.style.background=\'#ea580c\'" onmouseout="this.style.background=\'#f97316\'" ' +
          'onclick="PorteroDash.registrarSalidaVisita(' + v.idVisita + ')">Registrar Salida</button></td>' +
          '</tr>';
      }
      
      html += '</tbody></table></div>';
      console.log('[PorteroDash] Tabla HTML generada, longitud:', html.length);
      console.log('[PorteroDash] Actualizando contenido del modal...');
      listEl.innerHTML = html;
      console.log('[PorteroDash] Contenido actualizado exitosamente');
    } catch (e) {
      console.error('[PorteroDash] ERROR al cargar visitas:', e);
      listEl.innerHTML = '<p class="text-muted" style="padding:24px;text-align:center">Error al cargar visitas: ' + Utils.escapeHtml(e.message) + '</p>';
    }
  }

  async function registrarSalidaVisita(idVisita) {
    try {
      await API.put('/visitas/' + idVisita + '/salida');
      Utils.showToast('Salida registrada exitosamente', 'success');
      await inicializar(); // Recargar datos
      cargarVisitasActivas(); // Actualizar tabla del modal
    } catch (e) {
      Utils.showToast('Error al registrar salida: ' + e.message, 'error');
    }
  }

  /* ─── Ver Detalle de Visita ─── */

  async function verDetalleVisita(idVisita) {
    console.log('[PorteroDash] Abriendo detalle de visita:', idVisita);
    try {
      var detalle = await API.get('/visitas/' + idVisita + '/detalle');
      console.log('[PorteroDash] Detalle obtenido:', detalle);
      mostrarModalDetalleVisita(detalle);
    } catch (e) {
      console.error('[PorteroDash] Error al cargar detalle:', e);
      Utils.showToast('Error al cargar detalle de la visita: ' + e.message, 'error');
    }
  }

  function mostrarModalDetalleVisita(detalle) {
    var overlay = document.createElement('div');
    overlay.id = 'modal-detalle-visita';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.7);backdrop-filter:blur(5px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:20px;animation:fadeIn 0.2s';
    
    var nombreResidente = detalle.residente ? (detalle.residente.nombres + ' ' + detalle.residente.apellidos) : 'Desconocido';
    var nombreVisitante = detalle.visitante ? (detalle.visitante.nombres + ' ' + detalle.visitante.apellidos) : 'Desconocido';
    var documentoVisitante = detalle.visitante ? detalle.visitante.numeroDocumento : '-';
    var apartamento = detalle.numeroApartamento || 'Desconocido';
    var horaIngreso = detalle.horaEntrada ? Utils.formatDateTime(detalle.horaEntrada) : '-';
    var horaSalida = detalle.horaSalida ? Utils.formatDateTime(detalle.horaSalida) : '-';
    var estadoHtml = Utils.estadoBadge(detalle.estado);
    var parqueadero = detalle.codigoParqueadero || 'No asignado';
    var fotoHtml = '';
    
    if (detalle.fotoCaptura) {
      fotoHtml = '<div class="form-group" style="margin-top:20px">' +
        '<label style="display:block;font-weight:600;margin-bottom:8px;font-size:14px;color:var(--navy-700)">Foto del Visitante</label>' +
        '<div style="text-align:center;background:var(--surface-container);padding:12px;border-radius:12px">' +
        '<img src="' + detalle.fotoCaptura + '" style="max-width:100%;max-height:300px;border-radius:10px;border:2px solid var(--border);cursor:pointer" ' +
        'onclick="window.open(this.src)" title="Click para ampliar"></div></div>';
    }
    
    // Determinar número de columnas según si hay hora de salida
    var gridColumns = detalle.estado === 'FINALIZADA' ? 'repeat(3,1fr)' : 'repeat(2,1fr)';
    
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:20px;max-width:650px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 25px 70px rgba(0,0,0,0.4)">' +
      '<div style="padding:32px 32px 24px;background:linear-gradient(135deg,var(--navy-600) 0%,var(--navy-500) 100%);border-radius:20px 20px 0 0;text-align:center">' +
      '<div style="width:80px;height:80px;margin:0 auto 16px;border-radius:50%;background:rgba(255,255,255,0.2);backdrop-filter:blur(10px);display:flex;align-items:center;justify-content:center;box-shadow:0 8px 24px rgba(0,0,0,0.2)">' +
      '<span class="material-symbols-outlined" style="font-size:42px;color:#fff">person_search</span></div>' +
      '<h2 style="margin:0 0 6px;font-size:24px;font-weight:700;color:#fff">Detalle de Visita</h2>' +
      '<p style="margin:0;font-size:14px;color:rgba(255,255,255,0.85)">Información completa del registro</p></div>' +
      '<div style="padding:28px 32px">' +
      '<div style="display:grid;grid-template-columns:' + gridColumns + ';gap:20px 16px">' +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Residente (Generó Código)</label>' +
      '<div style="padding:12px 14px;background:var(--navy-50);border-radius:8px;font-size:14px;color:var(--text);font-weight:600">' + Utils.escapeHtml(nombreResidente) + '</div></div>' +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Apartamento</label>' +
      '<div style="padding:12px 14px;background:var(--navy-50);border-radius:8px;font-size:14px;color:var(--text);font-weight:600">' + Utils.escapeHtml(apartamento) + '</div></div>' +
      (detalle.estado === 'FINALIZADA' ? '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Estado</label>' +
      '<div style="padding:8px 0">' + estadoHtml + '</div></div>' : '') +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Visitante</label>' +
      '<div style="padding:12px 14px;background:var(--surface-container);border-radius:8px;font-size:14px;color:var(--text);font-weight:600">' + Utils.escapeHtml(nombreVisitante) + '</div></div>' +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Documento</label>' +
      '<div style="padding:12px 14px;background:var(--surface-container);border-radius:8px;font-size:14px;color:var(--text-secondary)">' + Utils.escapeHtml(documentoVisitante) + '</div></div>' +
      (detalle.estado === 'FINALIZADA' ? '' : '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Estado</label>' +
      '<div style="padding:8px 0">' + estadoHtml + '</div></div>') +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Hora de Ingreso</label>' +
      '<div style="padding:12px 14px;background:var(--surface-container);border-radius:8px;font-size:14px;color:var(--text-secondary)">' + horaIngreso + '</div></div>' +
      (detalle.estado === 'FINALIZADA' ? '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--success)">Hora de Salida</label>' +
      '<div style="padding:12px 14px;background:color-mix(in srgb,#10b981 10%,transparent);border-radius:8px;font-size:14px;color:var(--success);font-weight:600">' + horaSalida + '</div></div>' : '') +
      '<div class="form-group" style="margin:0"><label style="display:block;font-weight:600;margin-bottom:6px;font-size:13px;color:var(--navy-500)">Parqueadero Asignado</label>' +
      '<div style="padding:12px 14px;background:' + (detalle.codigoParqueadero ? 'color-mix(in srgb,#10b981 15%,transparent)' : 'var(--surface-container)') + ';border-radius:8px;font-size:16px;font-weight:700;color:' + (detalle.codigoParqueadero ? 'var(--success)' : 'var(--text-secondary)') + '">' + parqueadero + '</div></div>' +
      '</div>' +
      fotoHtml +
      (detalle.notas ? '<div class="form-group" style="margin-top:20px"><label style="display:block;font-weight:600;margin-bottom:8px;font-size:14px;color:var(--navy-700)">Notas</label>' +
      '<div style="padding:12px 16px;background:var(--surface-container);border-radius:10px;font-size:13px;color:var(--text-secondary);white-space:pre-wrap">' + Utils.escapeHtml(detalle.notas) + '</div></div>' : '') +
      '</div>' +
      '<div style="padding:20px 32px 28px;background:var(--surface-container);border-top:1px solid var(--border);display:flex;gap:12px;justify-content:flex-end">' +
      '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-visita\').remove()">Cerrar</button>' +
      (detalle.estado === 'ACTIVA' ? '<button class="btn btn-primary" onclick="PorteroDash.registrarSalidaDesdeDetalle(' + detalle.idVisita + ')">Registrar Salida</button>' : '') +
      '</div></div>';
    
    document.body.appendChild(overlay);
    
    // Cerrar con click fuera
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) overlay.remove();
    });
  }

  function registrarSalidaDesdeDetalle(idVisita) {
    document.getElementById('modal-detalle-visita').remove();
    registrarSalidaVisita(idVisita);
  }

  /* ─── Aviso de Ruido ─── */

  var _modalRuidoAbierto = false;
  var _quejasRuidoData = [];
  var _quejaSeleccionada = null;

  function mostrarModalRuido() {
    if (_modalRuidoAbierto) return;
    _modalRuidoAbierto = true;
    var overlay = document.createElement('div');
    overlay.id = 'modal-ruido-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.4);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:12px;max-width:420px;width:100%;padding:24px;">' +
      '<h3 style="margin:0 0 4px;font-size:18px">Aviso de Ruido</h3>' +
      '<p class="text-sm text-muted" style="margin:0 0 16px">Seleccione el apartamento y escriba un mensaje para notificar al residente sobre la queja de ruido.</p>' +
      '<div class="form-group"><label>Apartamento</label>' +
      '<select id="ruido-apartamento" class="form-control"><option value="">Cargando...</option></select></div>' +
      '<div class="form-group"><label>Mensaje (opcional)</label>' +
      '<textarea id="ruido-mensaje" class="form-control" rows="3" placeholder="Ej: Por favor reducir el volumen de la m&uacute;sica"></textarea></div>' +
      '<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalRuido()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-enviar-ruido" onclick="PorteroDash.enviarAvisoRuido()">Enviar Aviso</button></div></div>';
    document.body.appendChild(overlay);
    cargarApartamentosRuido();
  }

  function cerrarModalRuido() {
    detenerCamaraMulta();
    var overlay = document.getElementById('modal-ruido-overlay');
    if (overlay) overlay.remove();
    _modalRuidoAbierto = false;
    _multaFotoData = null;
  }

  async function cargarApartamentosRuido() {
    var sel = document.getElementById('ruido-apartamento');
    if (!sel) return;
    try {
      var apts = await API.get('/apartamentos');
      sel.innerHTML = '<option value="">Seleccione un apartamento</option>' +
        apts.map(function(a) { return '<option value="' + a.idApartamento + '">' + Utils.escapeHtml(a.numero || 'Apto #' + a.idApartamento) + '</option>'; }).join('');
    } catch (e) {
      sel.innerHTML = '<option value="">Error al cargar</option>';
      Utils.showToast('Error al cargar apartamentos', 'error');
    }
  }

  async function enviarAvisoRuido() {
    var sel = document.getElementById('ruido-apartamento');
    var msg = document.getElementById('ruido-mensaje');
    var btn = document.getElementById('btn-enviar-ruido');
    if (!sel || !msg || !btn) return;
    var idApartamento = sel.value;
    if (!idApartamento) { Utils.showToast('Seleccione un apartamento', 'warn'); return; }
    btn.disabled = true;
    btn.textContent = 'Enviando...';
    try {
      var res = await API.post('/buzon/aviso-ruido', {
        idApartamento: parseInt(idApartamento),
        cuerpo: msg.value.trim() || 'Se ha reportado ruido excesivo proveniente de su apartamento. Por favor tomar las medidas necesarias.'
      });
      Utils.showToast('Aviso de ruido enviado al residente', 'success');
      cerrarModalRuido();
    } catch (e) {
      Utils.showToast('Error al enviar aviso: ' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Enviar Aviso';
    }
  }

  /* ─── Generar Multa directa ─── */

  function mostrarModalRuidoConTipo(tipo) {
    if (_modalRuidoAbierto) return;
    _modalRuidoAbierto = true;
    _multaFotoData = null;
    var esRuido = tipo === 'RUIDO';
    var titulo = esRuido ? 'Multa por Ruido' : 'Multa por Parqueadero';
    var monto = esRuido ? Utils.formatCurrency(100000) : Utils.formatCurrency(50000);
    var placeholder = esRuido ? '' : 'Ej: Veh\u00edculo estacionado en espacio no autorizado';
    if (esRuido) {
      var overlay = document.createElement('div');
      overlay.id = 'modal-ruido-overlay';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.4);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.innerHTML =
        '<div style="background:#fff;border-radius:12px;max-width:480px;width:100%;padding:24px;">' +
        '<h3 style="margin:0 0 4px;font-size:18px">' + titulo + '</h3>' +
        '<p class="text-sm text-muted" style="margin:0 0 16px">Seleccione la queja de ruido enviada hoy a la que desea aplicar la multa de ' + monto + '.</p>' +
        '<div id="ruido-quejas-list" style="max-height:280px;overflow-y:auto">Cargando...</div>' +
        '<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">' +
        '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalMulta()">Cancelar</button></div></div>';
      document.body.appendChild(overlay);
      cargarQuejasRuidoPendientes();
    } else {
      var camaraHtml =
        '<div class="form-group"><label>Foto de Evidencia</label>' +
        '<div style="position:relative;background:#000;border-radius:8px;overflow:hidden;margin-bottom:8px">' +
        '<video id="multa-video" autoplay playsinline muted style="width:100%;max-height:200px;display:block;object-fit:cover"></video>' +
        '<canvas id="multa-canvas" hidden></canvas>' +
        '<img id="multa-preview" style="width:100%;max-height:200px;object-fit:cover;display:none;border-radius:8px">' +
        '</div>' +
        '<div style="display:flex;gap:8px">' +
        '<button class="btn btn-sm btn-primary" id="btn-multa-capturar" onclick="PorteroDash.capturarFotoMulta()">Capturar Foto</button>' +
        '<button class="btn btn-sm btn-outline hidden" id="btn-multa-retomar" onclick="PorteroDash.retomarFotoMulta()">Retomar</button>' +
        '</div></div>';
      var overlay = document.createElement('div');
      overlay.id = 'modal-ruido-overlay';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.4);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.innerHTML =
        '<div style="background:#fff;border-radius:12px;max-width:420px;width:100%;padding:24px;">' +
        '<h3 style="margin:0 0 4px;font-size:18px">' + titulo + '</h3>' +
        '<p class="text-sm text-muted" style="margin:0 0 16px">Seleccione el apartamento. El monto de la multa es ' + monto + '.</p>' +
        '<div class="form-group"><label>Apartamento</label>' +
        '<select id="ruido-apartamento" class="form-control"><option value="">Cargando...</option></select></div>' +
        '<div class="form-group"><label>Descripci\u00f3n</label>' +
        '<textarea id="ruido-mensaje" class="form-control" rows="3" placeholder="' + placeholder + '"></textarea></div>' +
        camaraHtml +
        '<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">' +
        '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalMulta()">Cancelar</button>' +
        '<button class="btn btn-primary" id="btn-enviar-ruido" onclick="PorteroDash.enviarMultaDirecta(\'' + tipo + '\')">Generar Multa</button></div></div>';
      document.body.appendChild(overlay);
      cargarApartamentosRuido();
      abrirCamaraMulta();
    }
  }

  async function cargarQuejasRuidoPendientes() {
    var listEl = document.getElementById('ruido-quejas-list');
    if (!listEl) return;
    try {
      _quejasRuidoData = await API.get('/buzon/quejas-ruido-pendientes');
      if (!_quejasRuidoData || !_quejasRuidoData.length) {
        listEl.innerHTML = '<div class="text-muted" style="padding:24px;text-align:center">No hay quejas de ruido pendientes hoy. Primero env\u00ede un aviso de ruido desde el bot\u00f3n "Aviso de Ruido".</div>';
        return;
      }
      var html = '';
      for (var i = 0; i < _quejasRuidoData.length; i++) {
        var q = _quejasRuidoData[i];
        var leidoClase = q.leido ? '' : 'border-left:3px solid var(--accent);background:#f0f7ff';
        html += '<div class="card" style="padding:12px;margin-bottom:8px;cursor:pointer;' + leidoClase + '" data-idx="' + i + '" onclick="PorteroDash.seleccionarQuejaRuido(' + i + ')">' +
          '<div style="display:flex;align-items:flex-start;gap:8px">' +
          '<span class="material-symbols-outlined" style="font-size:22px;color:var(--warn)">volume_up</span>' +
          '<div style="flex:1;min-width:0">' +
          '<p style="font-weight:600;margin:0 0 2px;font-size:13px">Apto ' + Utils.escapeHtml(q.numeroApartamento || '#?') + '</p>' +
          '<p class="text-xs text-muted" style="margin:0">' + Utils.escapeHtml(q.cuerpo || '') + '</p>' +
          (q.leido ? '<span class="text-xs text-muted" style="display:inline-block;margin-top:4px">Le\u00eddo</span>' : '<span class="text-xs" style="display:inline-block;margin-top:4px;color:var(--accent);font-weight:600">No le\u00eddo</span>') +
          '</div></div></div>';
      }
      listEl.innerHTML = html;
    } catch (e) {
      listEl.innerHTML = '<p class="text-muted" style="padding:24px;text-align:center">Error al cargar quejas</p>';
    }
  }

  function seleccionarQuejaRuido(idx) {
    var q = _quejasRuidoData[idx];
    if (!q) return;
    if (_quejaSeleccionada === idx) return;
    _quejaSeleccionada = idx;
    var listEl = document.getElementById('ruido-quejas-list');
    if (listEl) {
      listEl.querySelectorAll('.card').forEach(function(c) { c.style.borderLeft = ''; c.style.background = ''; });
      var card = listEl.querySelector('.card[data-idx="' + idx + '"]');
      if (card) { card.style.borderLeft = '4px solid var(--danger)'; card.style.background = '#fff5f5'; }
    }
    var overlay = document.querySelector('#modal-ruido-overlay > div');
    if (overlay) {
      var existingBtn = document.getElementById('btn-generar-multa-ruido');
      if (!existingBtn) {
        var footer = overlay.querySelector('div:last-child');
        if (footer) {
          footer.innerHTML += '<button class="btn btn-primary" id="btn-generar-multa-ruido" onclick="PorteroDash.enviarMultaDesdeQueja()">Generar Multa ' + Utils.formatCurrency(100000) + '</button>';
        }
      }
    }
  }

  async function enviarMultaDesdeQueja() {
    if (_quejaSeleccionada == null) { Utils.showToast('Seleccione una queja de ruido', 'warn'); return; }
    var q = _quejasRuidoData[_quejaSeleccionada];
    if (!q) return;
    var btn = document.getElementById('btn-generar-multa-ruido');
    if (btn) { btn.disabled = true; btn.textContent = 'Generando...'; }
    try {
      var res = await API.post('/multas/generar', {
        idApartamento: q.idApartamento,
        idMensaje: q.idMensaje,
        tipo: 'RUIDO',
        descripcion: 'Multa por ruido tras aviso previo'
      });
      Utils.showToast('Multa generada exitosamente. Monto: ' + Utils.formatCurrency(res.monto || 100000), 'success');
      cerrarModalMulta();
    } catch (e) {
      Utils.showToast('Error: ' + e.message, 'error');
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = 'Generar Multa'; }
    }
  }

  function cerrarModalMulta() {
    detenerCamaraMulta();
    cerrarModalRuido();
  }

  async function abrirCamaraMulta() {
    var video = document.getElementById('multa-video');
    if (!video) return;
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        Utils.showToast('Su navegador no soporta la c\u00e1mara', 'warn');
        return;
      }
      _multaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false
      });
      video.srcObject = _multaStream;
      await video.play();
    } catch (e) {
      console.warn('Error al abrir c\u00e1mara:', e);
      Utils.showToast('No se pudo abrir la c\u00e1mara: ' + (e.message || 'Error'), 'error');
    }
  }

  function detenerCamaraMulta() {
    if (_multaStream) {
      _multaStream.getTracks().forEach(function(t) { t.stop(); });
      _multaStream = null;
    }
  }

  function capturarFotoMulta() {
    var video = document.getElementById('multa-video');
    var canvas = document.getElementById('multa-canvas');
    var preview = document.getElementById('multa-preview');
    var btnCap = document.getElementById('btn-multa-capturar');
    var btnRet = document.getElementById('btn-multa-retomar');
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    _multaFotoData = canvas.toDataURL('image/jpeg', 0.80);
    if (preview) { preview.src = _multaFotoData; preview.style.display = 'block'; }
    if (video) video.style.display = 'none';
    if (btnCap) btnCap.classList.add('hidden');
    if (btnRet) btnRet.classList.remove('hidden');
    detenerCamaraMulta();
  }

  function retomarFotoMulta() {
    _multaFotoData = null;
    var video = document.getElementById('multa-video');
    var preview = document.getElementById('multa-preview');
    if (video) video.style.display = 'block';
    if (preview) preview.style.display = 'none';
    document.getElementById('btn-multa-capturar').classList.remove('hidden');
    document.getElementById('btn-multa-retomar').classList.add('hidden');
    abrirCamaraMulta();
  }

  async function enviarMultaDirecta(tipo) {
    var sel = document.getElementById('ruido-apartamento');
    var msg = document.getElementById('ruido-mensaje');
    var btn = document.getElementById('btn-enviar-ruido');
    if (!sel || !msg || !btn) return;
    var idApartamento = sel.value;
    if (!idApartamento) { Utils.showToast('Seleccione un apartamento', 'warn'); return; }
    if (tipo === 'PARQUEADERO' && !_multaFotoData) { Utils.showToast('Debe tomar una foto de evidencia', 'warn'); return; }
    btn.disabled = true;
    btn.textContent = 'Generando...';
    try {
      var payload = {
        idApartamento: parseInt(idApartamento),
        tipo: tipo,
        descripcion: msg.value.trim() || (tipo === 'RUIDO' ? 'Multa por ruido excesivo' : 'Multa por uso indebido de parqueadero')
      };
      if (_multaFotoData) payload.fotoEvidencia = _multaFotoData;
      var res = await API.post('/multas/generar', payload);
      Utils.showToast('Multa generada exitosamente. Monto: ' + Utils.formatCurrency(res.monto || (tipo === 'RUIDO' ? 100000 : 50000)), 'success');
      cerrarModalMulta();
    } catch (e) {
      Utils.showToast('Error: ' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = 'Generar Multa';
    }
  }

  /* ─── Paquetes Pendientes ─── */

  var _modalPaquetesAbierto = false;
  var _paquetesData = [];

  async function mostrarPaquetesPendientes() {
    if (_modalPaquetesAbierto) return;
    _modalPaquetesAbierto = true;
    var overlay = document.createElement('div');
    overlay.id = 'modal-paquetes-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.4);z-index:15000;display:flex;align-items:center;justify-content:center;padding:16px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:12px;max-width:640px;width:100%;max-height:90vh;display:flex;flex-direction:column">' +
      '<div style="padding:24px;border-bottom:1px solid var(--border)">' +
      '<h3 style="margin:0;font-size:18px">Paquetes Pendientes</h3>' +
      '<p class="text-sm text-muted" style="margin:4px 0 0">Paquetes registrados que aún no han sido retirados por los residentes.</p>' +
      '</div>' +
      '<div id="paquetes-list" style="flex:1;overflow-y:auto;padding:16px">Cargando...</div>' +
      '<div style="padding:16px;border-top:1px solid var(--border);text-align:right">' +
      '<button class="btn btn-ghost" onclick="PorteroDash.cerrarModalPaquetes()">Cerrar</button></div></div>';
    document.body.appendChild(overlay);
    cargarPaquetesPendientes();
  }

  function cerrarModalPaquetes() {
    var overlay = document.getElementById('modal-paquetes-overlay');
    if (overlay) overlay.remove();
    _modalPaquetesAbierto = false;
    _paquetesData = [];
  }

  async function cargarPaquetesPendientes() {
    var listEl = document.getElementById('paquetes-list');
    if (!listEl) return;
    try {
      _paquetesData = await API.get('/buzon/paquetes');
      if (!_paquetesData || !_paquetesData.length) {
        listEl.innerHTML = '<div class="text-muted" style="padding:40px;text-align:center"><span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">inventory_2</span><p style="margin:8px 0 0">No hay paquetes pendientes</p></div>';
        return;
      }
      var html = '';
      for (var i = 0; i < _paquetesData.length; i++) {
        var p = _paquetesData[i];
        var fecha = Utils.formatDateTime(p.fechaCreacion);
        var fotoIcon = p.fotoCaptura ? '<span class="material-symbols-outlined" style="font-size:18px;color:var(--accent)" title="Con foto">photo_camera</span>' : '';
        html += '<div class="card" style="padding:16px;margin-bottom:12px;display:flex;align-items:center;gap:12px">' +
          '<div style="width:48px;height:48px;border-radius:8px;background:color-mix(in srgb,#f97316 10%,transparent);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
          '<span class="material-symbols-outlined" style="font-size:28px;color:#f97316">inventory_2</span></div>' +
          '<div style="flex:1;min-width:0">' +
          '<p style="font-weight:600;margin:0 0 4px;font-size:14px">' + Utils.escapeHtml(p.titulo || 'Paquete') + ' ' + fotoIcon + '</p>' +
          '<p class="text-xs text-muted" style="margin:0">Apto: ' + Utils.escapeHtml(p.numeroApartamento || '?') + ' • ' + fecha + '</p>' +
          (p.cuerpo ? '<p class="text-xs" style="margin:4px 0 0">' + Utils.escapeHtml(p.cuerpo) + '</p>' : '') +
          '</div>' +
          '<button class="btn btn-sm btn-primary" onclick="PorteroDash.verDetallePaquete(' + i + ')">Ver</button></div>';
      }
      listEl.innerHTML = html;
    } catch (e) {
      listEl.innerHTML = '<p class="text-muted" style="padding:24px;text-align:center">Error al cargar paquetes: ' + Utils.escapeHtml(e.message) + '</p>';
    }
  }

  function verDetallePaquete(idx) {
    var p = _paquetesData[idx];
    if (!p) return;
    var fotoHtml = '';
    if (p.fotoCaptura) {
      fotoHtml = '<div class="form-group"><label>Foto de Evidencia</label>' +
        '<img src="' + p.fotoCaptura + '" style="width:100%;max-height:300px;object-fit:contain;border-radius:8px;border:1px solid var(--border)"></div>';
    }
    var overlay = document.createElement('div');
    overlay.id = 'modal-detalle-paquete';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);z-index:16000;display:flex;align-items:center;justify-content:center;padding:16px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:12px;max-width:480px;width:100%;max-height:90vh;overflow-y:auto;padding:24px">' +
      '<h3 style="margin:0 0 16px;font-size:18px">Detalles del Paquete</h3>' +
      '<div class="form-group"><label>Apartamento</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(p.numeroApartamento || 'Desconocido') + '</div></div>' +
      '<div class="form-group"><label>Título</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(p.titulo || 'Sin título') + '</div></div>' +
      (p.cuerpo ? '<div class="form-group"><label>Descripción</label><div class="form-control" style="background:#f9fafb;cursor:default;min-height:60px">' + Utils.escapeHtml(p.cuerpo) + '</div></div>' : '') +
      '<div class="form-group"><label>Fecha de Llegada</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDateTime(p.fechaCreacion) + '</div></div>' +
      fotoHtml +
      '<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">' +
      '<button class="btn btn-ghost" onclick="PorteroDash.cerrarDetallePaquete()">Cerrar</button>' +
      '<button class="btn btn-primary" onclick="PorteroDash.marcarPaqueteEntregado(' + p.idMensaje + ')">Marcar como Entregado</button></div></div>';
    document.body.appendChild(overlay);
  }

  function cerrarDetallePaquete() {
    var overlay = document.getElementById('modal-detalle-paquete');
    if (overlay) overlay.remove();
  }

  async function marcarPaqueteEntregado(idMensaje) {
    try {
      await API.put('/buzon/' + idMensaje + '/entregado');
      Utils.showToast('Paquete marcado como entregado', 'success');
      cerrarDetallePaquete();
      cargarPaquetesPendientes();
      inicializar(); // Recargar stats
    } catch (e) {
      Utils.showToast('Error: ' + e.message, 'error');
    }
  }

  /* ─── Lightbox Foto ─── */

  function mostrarFoto(urlFoto) {
    if (!urlFoto) {
      Utils.showToast('No hay foto disponible', 'warning');
      return;
    }
    
    var overlay = document.createElement('div');
    overlay.id = 'modal-foto-lightbox';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.9);z-index:25000;display:flex;align-items:center;justify-content:center;padding:20px;animation:fadeIn 0.2s;cursor:zoom-out';
    
    overlay.innerHTML = 
      '<div style="position:relative;max-width:90%;max-height:90%;display:flex;flex-direction:column;align-items:center">' +
      '<button onclick="document.getElementById(\'modal-foto-lightbox\').remove()" style="position:absolute;top:-40px;right:0;background:rgba(255,255,255,0.1);backdrop-filter:blur(10px);border:1px solid rgba(255,255,255,0.2);color:#fff;padding:8px 16px;border-radius:8px;cursor:pointer;font-size:14px;font-weight:600;transition:all 0.2s" onmouseover="this.style.background=\'rgba(255,255,255,0.2)\'" onmouseout="this.style.background=\'rgba(255,255,255,0.1)\'">' +
      '<span class="material-symbols-outlined" style="vertical-align:middle;margin-right:4px;font-size:18px">close</span>Cerrar</button>' +
      '<img src="' + urlFoto + '" style="max-width:100%;max-height:80vh;border-radius:12px;box-shadow:0 25px 80px rgba(0,0,0,0.5);border:3px solid rgba(255,255,255,0.1)" />' +
      '<p style="margin:16px 0 0;color:#fff;font-size:13px;opacity:0.7">Click fuera de la imagen para cerrar</p></div>';
    
    document.body.appendChild(overlay);
    
    // Cerrar con click fuera o en el overlay
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay || e.target.tagName === 'IMG') {
        overlay.remove();
      }
    });
  }

  var exported = {
    inicializar: inicializar, 
    mostrarModalRuido: mostrarModalRuido,
    cerrarModalRuido: cerrarModalRuido, 
    enviarAvisoRuido: enviarAvisoRuido,
    mostrarModalRuidoConTipo: mostrarModalRuidoConTipo,
    enviarMultaDirecta: enviarMultaDirecta,
    capturarFotoMulta: capturarFotoMulta, 
    retomarFotoMulta: retomarFotoMulta,
    cerrarModalMulta: cerrarModalMulta,
    seleccionarQuejaRuido: seleccionarQuejaRuido,
    enviarMultaDesdeQueja: enviarMultaDesdeQueja,
    mostrarPaquetesPendientes: mostrarPaquetesPendientes,
    cerrarModalPaquetes: cerrarModalPaquetes,
    verDetallePaquete: verDetallePaquete,
    cerrarDetallePaquete: cerrarDetallePaquete,
    marcarPaqueteEntregado: marcarPaqueteEntregado,
    mostrarVisitasHoy: mostrarVisitasHoy,
    cerrarModalVisitas: cerrarModalVisitas,
    mostrarRegistrarSalida: mostrarRegistrarSalida,
    cerrarModalSalida: cerrarModalSalida,
    registrarSalidaVisita: registrarSalidaVisita,
    verDetalleVisita: verDetalleVisita,
    registrarSalidaDesdeDetalle: registrarSalidaDesdeDetalle,
    mostrarFoto: mostrarFoto
  };
  
  console.log('[PorteroDash] Funciones exportadas:', Object.keys(exported));
  console.log('[PorteroDash] mostrarRegistrarSalida disponible?', typeof exported.mostrarRegistrarSalida);
  
  return exported;
})();

Router.register('portero-dashboard', {
  html: document.getElementById('tpl-portero-dashboard').innerHTML,
  js: function() { PorteroDash.inicializar(); }
});