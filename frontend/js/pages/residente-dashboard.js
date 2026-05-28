const ResidenteDash = (() => {
  var _residente = null;
  var _dashboard = null;
  var _frecs = [];
  var _tiposDoc = [];
  var _pollInterval = null;
  var _confirmarPendientes = [];

  function $(id) { return document.getElementById(id); }
  function setText(id, v) { var el = $(id); if (el) el.textContent = (v != null ? v : '-'); }

  function cambiarTab(tabId) {
    document.querySelectorAll('#res-dash-tabs .tab').forEach(function(t) { t.classList.remove('active'); });
    var resContainer = document.getElementById('res-dash-tabs').parentElement;
    resContainer.querySelectorAll('.tab-content').forEach(function(t) { t.classList.remove('active'); });
    var tab = document.querySelector('#res-dash-tabs .tab[data-tab="' + tabId.replace(/["\\]/g, '') + '"]');
    if (tab) tab.classList.add('active');
    var content = $(tabId);
    if (content) content.classList.add('active');
    if (tabId === 'res-tab-resumen') renderResumen();
    else if (tabId === 'res-tab-perfil') renderPerfilForm();
    else if (tabId === 'res-tab-apartamento') renderApartamento();
    else if (tabId === 'res-tab-cuotas') renderCuotasFull();
    else if (tabId === 'res-tab-frecuentes') renderFrecuentes();
    else if (tabId === 'res-tab-buzon') renderBuzon();
    else if (tabId === 'res-tab-nueva-visita') renderNuevaVisita();
    else if (tabId === 'res-tab-quejas') { QuejasResidente.init(); }
  }

  async function inicializar() {
    var user = Auth.getCurrentUser();
    if (!user || !user.idResidente) { Utils.showToast('No hay residente asociado', 'error'); return; }
    try {
      _residente = await API.get('/residentes/' + user.idResidente);
      _dashboard = await API.get('/residentes/' + user.idResidente + '/dashboard');
      _frecs = await API.get('/residentes/' + user.idResidente + '/frecuentes').catch(function() { return []; });
      _tiposDoc = await API.get('/tipos-documento').catch(function() { return []; });
      var titleEl = document.getElementById('page-title');
      if (titleEl) titleEl.textContent = 'Mi Portal — Bienvenido, ' + (_residente.nombres || 'Residente');
      renderKPIs();
      renderResumen();
      iniciarPollConfirmacion();
      actualizarBadgeBuzon();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  function limpiar() {
    detenerPollConfirmacion();
  }

  function iniciarPollConfirmacion() {
    detenerPollConfirmacion();
    _pollInterval = setInterval(pollConfirmacionPendiente, 2000);
    pollConfirmacionPendiente();
  }

  function detenerPollConfirmacion() {
    if (_pollInterval) { clearInterval(_pollInterval); _pollInterval = null; }
  }

  async function pollConfirmacionPendiente() {
    try {
      var pendientes = await API.get('/buzon/confirmar-pendiente');
      if (!pendientes || !pendientes.length) return;
      for (var i = 0; i < pendientes.length; i++) {
        var p = pendientes[i];
        var yaMostrada = false;
        for (var j = 0; j < _confirmarPendientes.length; j++) {
          if (_confirmarPendientes[j].idMensaje === p.idMensaje) { yaMostrada = true; break; }
        }
        if (!yaMostrada) {
          _confirmarPendientes.push(p);
          mostrarModalConfirmacion(p);
        }
      }
    } catch(e) { /* silencioso */ }
  }

  function mostrarModalConfirmacion(mensaje) {
    var overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.6);z-index:20000;display:flex;align-items:center;justify-content:center;padding:20px';
    overlay.addEventListener('click', function(e) { if (e.target === overlay) return; });

    var modal = document.createElement('div');
    modal.style.cssText = 'background:#fff;border-radius:12px;max-width:460px;width:100%;padding:28px;box-shadow:0 8px 32px rgba(0,0,0,0.2)';

    modal.innerHTML =
      '<div style="text-align:center;margin-bottom:20px">' +
      '<span class="material-symbols-outlined" style="font-size:40px;color:var(--text-secondary)">how_to_reg</span>' +
      '<h2 style="margin:12px 0 4px;font-size:18px;color:var(--text)">Solicitud de Acceso</h2>' +
      '<p class="text-muted text-sm">Un visitante est\u00e1 en porter\u00eda esperando su confirmaci\u00f3n</p></div>' +
      '<div style="margin-bottom:16px">' +
      '<p style="font-weight:600;font-size:14px;margin-bottom:8px">' + Utils.escapeHtml(mensaje.titulo || '') + '</p>' +
      '<p class="text-sm" style="color:var(--text-secondary)">' + Utils.escapeHtml(mensaje.cuerpo || '') + '</p></div>' +
      (mensaje.fotoCaptura ? '<div style="margin-bottom:16px;text-align:center"><img src="' + mensaje.fotoCaptura + '" alt="Foto del visitante" style="max-width:100%;max-height:280px;border-radius:8px;border:1px solid var(--border);box-shadow:0 2px 8px rgba(0,0,0,0.1)"></div>' : '') +
      '<div style="display:flex;gap:12px">' +
      '<button class="btn btn-outline" style="flex:1" onclick="ResidenteDash.rechazarVisita(' + mensaje.idMensaje + ', this)">Rechazar</button>' +
      '<button class="btn btn-accent" style="flex:1" onclick="ResidenteDash.confirmarVisita(' + mensaje.idMensaje + ', this)">Confirmar Acceso</button></div>';

    overlay.appendChild(modal);
    document.body.appendChild(overlay);
    _confirmarOverlay = overlay;
  }

  async function confirmarVisita(idMensaje, btn) {
    if (btn) btn.disabled = true;
    try {
      await API.post('/buzon/confirmar', { idMensaje: idMensaje, confirmado: 1 });
      Utils.showToast('Acceso confirmado', 'success');
      marcarPendienteResuelto(idMensaje);
      cerrarModalConfirmacion();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function rechazarVisita(idMensaje, btn) {
    if (btn) btn.disabled = true;
    try {
      await API.post('/buzon/confirmar', { idMensaje: idMensaje, confirmado: 0 });
      Utils.showToast('Visita rechazada', 'warn');
      marcarPendienteResuelto(idMensaje);
      cerrarModalConfirmacion();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  function marcarPendienteResuelto(idMensaje) {
    _confirmarPendientes = _confirmarPendientes.filter(function(p) { return p.idMensaje !== idMensaje; });
  }

  function cerrarModalConfirmacion() {
    var overlay = document.querySelector('.modal-overlay');
    if (overlay && overlay.style.zIndex === '20000') overlay.remove();
  }

  function renderKPIs() {
    var apt = _dashboard ? _dashboard.apartamento : null;
    var ctr = _dashboard ? _dashboard.contrato : null;
    var cuotas = _dashboard ? (_dashboard.cuotas || []) : [];
    var multas = _dashboard ? (_dashboard.multas || []) : [];
    var pendientes = cuotas.filter(function(c) { return c.estado !== 'PAGADA'; }).length;
    var multasPen = multas.filter(function(m) { return m.estado === 'PENDIENTE'; }).length;
    setText('res-kpi-apartamento', apt ? (apt.numero || '-') : 'Sin asignar');
    setText('res-kpi-contrato', ctr ? ctr.estado.replace(/_/g, ' ') : 'Sin contrato');
    setText('res-kpi-cuotas-arriendo', pendientes);
    setText('res-kpi-multas', multasPen);
  }

  /* ─── Resumen tab ─── */

  function renderResumen() {
    var container = $('res-tab-resumen');
    if (!container) return;
    var r = _residente;
    var cuotas = _dashboard ? (_dashboard.cuotas || []) : [];
    var last5 = cuotas.slice(-5).reverse();
    var cuotasRows = last5.map(function(c) {
      return '<tr><td>' + Utils.escapeHtml(Utils.periodoLabel(c.anio, c.mes)) + '</td><td>' + Utils.formatCurrency(c.valorTotal) + '</td><td>' + Utils.estadoBadge(c.estado || 'PENDIENTE') + '</td></tr>';
    }).join('');
    container.innerHTML = '' +
      '<div class="card"><div class="card-title">Informaci&oacute;n Personal</div>' +
      '<div class="form-row">' +
      '<div class="form-group"><label>Nombre</label><p style="font-weight:600;color:var(--text)">' + Utils.escapeHtml(r ? ((r.nombres || '') + ' ' + (r.apellidos || '')) : '-') + '</p></div>' +
      '<div class="form-group"><label>Documento</label><p style="font-weight:600;color:var(--text)">' + Utils.escapeHtml(r ? (r.numeroDocumento || '-') : '-') + '</p></div>' +
      '</div>' +
      '<div class="form-row">' +
      '<div class="form-group"><label>Tel&eacute;fono</label><p style="font-weight:600;color:var(--text)">' + Utils.escapeHtml(r ? (r.telefono || '-') : '-') + '</p></div>' +
      '<div class="form-group"><label>Email</label><p style="font-weight:600;color:var(--text)">' + Utils.escapeHtml(r ? (r.email || '-') : '-') + '</p></div>' +
      '</div></div>' +
      '<div class="card"><div class="card-title">&Uacute;ltimas Cuotas</div>' +
      (cuotas.length ? '<div class="table-container" style="box-shadow:none;border:1px solid var(--border)"><table class="data-table"><thead><tr><th>Periodo</th><th>Total</th><th>Estado</th></tr></thead><tbody>' + cuotasRows + '</tbody></table></div>' : '<p class="text-muted text-sm">No hay cuotas registradas</p>') +
      (cuotas.length > 5 ? '<div class="mt-8" style="text-align:right"><button class="btn btn-ghost btn-sm" onclick="ResidenteDash.cambiarTab(\'res-tab-cuotas\')">Ver todas las cuotas &rarr;</button></div>' : '') +
      '</div>';
    renderMultasResumen();
    renderQRActivos();
  }

  async function renderQRActivos() {
    var container = $('res-tab-resumen');
    if (!container) return;
    try {
      var user = Auth.getCurrentUser();
      var qrs = await API.get('/residentes/' + user.idResidente + '/qr-activos');
      if (!qrs || !qrs.length) return;
      var html = '<div class="card" style="margin-top:16px"><div class="card-title">C\u00f3digos QR Activos</div>';
      qrs.forEach(function(qr) {
        html += '<div class="qr-activo-item" style="display:flex;align-items:center;gap:12px;padding:12px;border:1px solid var(--border);border-radius:8px;margin-bottom:8px">' +
          '<img src="https://api.qrserver.com/v1/create-qr-code/?size=80x80&data=' + encodeURIComponent(qr.codigoQr) + '" alt="QR" style="border-radius:4px;width:56px;height:56px">' +
          '<div style="flex:1;min-width:0">' +
          '<p style="font-weight:600;font-size:13px">' + Utils.escapeHtml(qr.nombreVisitante || 'Visitante') + '</p>' +
          '<p class="text-muted text-sm">' + (qr.cantidadPersonas || 1) + ' persona(s) &middot; Vence: ' + Utils.formatDateTime(qr.fechaExpiracion) + '</p>' +
          '<p class="qr-code-text" style="font-size:11px;margin-top:2px">#' + Utils.escapeHtml(qr.codigoQr.substring(0,8)) + '...</p></div>' +
          shareButtonsHtml(qr.codigoQr, qr.nombreVisitante, '', '') +
          '</div>';
      });
      html += '</div>';
      container.insertAdjacentHTML('beforeend', html);
    } catch(e) { /* silencioso */ }
  }

  function renderMultasResumen() {
    var container = $('res-tab-resumen');
    if (!container) return;
    var multas = _dashboard ? (_dashboard.multas || []) : [];
    var pendientes = multas.filter(function(m) { return m.estado === 'PENDIENTE'; });
    if (!pendientes.length) return;
    var rows = pendientes.map(function(m) {
      var icono = m.tipo === 'RUIDO' ? 'volume_up' : 'local_parking';
      return '<tr onclick="ResidenteDash.verDetalleMulta(' + m.idMulta + ')" style="cursor:pointer" onmouseenter="this.style.background=\'var(--navy-50)\'" onmouseleave="this.style.background=\'\'">' +
        '<td><span class="material-symbols-outlined" style="font-size:18px;vertical-align:middle;margin-right:4px">' + icono + '</span>' + (m.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero') + '</td>' +
        '<td>' + Utils.formatCurrency(m.monto) + '</td>' +
        '<td>' + Utils.estadoBadge(m.estado) + '</td></tr>';
    }).join('');
    container.insertAdjacentHTML('beforeend', '<div class="card" style="margin-top:16px;border-left:4px solid var(--danger)"><div class="card-title">Multas Pendientes</div><div class="table-container" style="box-shadow:none;border:1px solid var(--border)"><table class="data-table"><thead><tr><th>Tipo</th><th>Monto</th><th>Estado</th></tr></thead><tbody>' + rows + '</tbody></table></div></div>');
  }

  /* ─── Perfil tab ─── */

  function renderPerfilForm() {
    var container = $('res-tab-perfil');
    if (!container) return;
    var r = _residente;
    container.innerHTML = '<div class="card"><div class="card-title">Editar Perfil</div><form id="form-perfil"><div class="form-row"><div class="form-group"><label>Tel&eacute;fono *</label><input type="text" id="perf-telefono" class="form-control" value="' + Utils.escapeHtml(r ? r.telefono || '' : '') + '" maxlength="10"><span class="field-error" id="perf-tel-error"></span></div><div class="form-group"><label>Email</label><input type="email" id="perf-email" class="form-control" value="' + Utils.escapeHtml(r ? r.email || '' : '') + '" maxlength="100"><span class="field-error" id="perf-email-error"></span></div></div><div class="mt-16"><button type="button" class="btn btn-primary" id="btn-guardar-perfil" onclick="ResidenteDash.guardarPerfil()">Guardar Cambios</button></div></form></div>';
    configurarValidacionPerfil();
  }

  function configurarValidacionPerfil() {
    Utils.soloNumeros('perf-telefono', 10);
    Utils.validarTelefonoTiempoReal('perf-telefono');
  }

  async function guardarPerfil() {
    Utils.limpiarErrores('form-perfil');
    var telVal = ($('perf-telefono') ? $('perf-telefono').value : '').trim();
    var emailVal = ($('perf-email') ? $('perf-email').value : '').trim();
    
    // Validaciones
    if (!Utils.valTelefono(telVal, 'perf-telefono', { required: true })) return;
    if (emailVal && !Utils.valEmail(emailVal, 'perf-email', { required: false })) return;
    
    // Verificar si hay cambios
    var telAnterior = _residente.telefono || '';
    var emailAnterior = _residente.email || '';
    var hayCambios = telVal !== telAnterior || emailVal !== emailAnterior;
    
    if (!hayCambios) {
      Utils.showToast('No hay cambios para guardar', 'warn');
      return;
    }
    
    // Crear mensaje de confirmación con los cambios
    var mensaje = '\u00bfEst\u00e1 seguro de actualizar su perfil?\n\n';
    if (telVal !== telAnterior) {
      mensaje += 'Tel\u00e9fono: ' + telAnterior + ' \u2192 ' + telVal + '\n';
    }
    if (emailVal !== emailAnterior) {
      mensaje += 'Email: ' + (emailAnterior || '(vac\u00edo)') + ' \u2192 ' + (emailVal || '(vac\u00edo)');
    }
    
    var confirmado = await Utils.showConfirm(mensaje);
    if (!confirmado) return;
    
    var btn = $('btn-guardar-perfil');
    if (btn) btn.disabled = true;
    try {
      await API.put('/residentes/' + _residente.id + '/perfil', { telefono: telVal, email: emailVal });
      Utils.showToast('Perfil actualizado exitosamente', 'success');
      _residente = await API.get('/residentes/' + _residente.id);
      renderResumen();
      renderKPIs();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  /* ─── Apartamento tab ─── */

  function renderApartamento() {
    var container = $('res-tab-apartamento');
    if (!container) return;
    var apt = _dashboard ? _dashboard.apartamento : null;
    var ctr = _dashboard ? _dashboard.contrato : null;
    var ctrHtml = '';
    if (ctr) {
      ctrHtml = '<div class="mt-16" style="padding:16px;background:var(--bg);border-radius:var(--radius-sm)">' +
        '<p style="font-weight:600;margin-bottom:6px">Contrato #' + Utils.escapeHtml(ctr.idContrato) + '</p>' +
        '<p class="text-sm text-muted">Inicio: ' + Utils.formatDate(ctr.fechaInicio) + ' | Fin: ' + (ctr.fechaFin ? Utils.formatDate(ctr.fechaFin) : 'Indefinido') + '</p>' +
        '<p class="text-sm text-muted">Valor Mensual: ' + Utils.formatCurrency(ctr.valorMensual) + '</p></div>';
    }
    container.innerHTML = '<div class="card"><div class="card-title">Mi Apartamento</div><div class="card-grid">' +
      '<div class="stat-card"><div class="stat-icon blue"><span class="material-symbols-outlined">apartment</span></div><div class="stat-body"><div class="stat-value" style="font-size:22px">' + Utils.escapeHtml(apt ? (apt.numero || '-') : '-') + '</div><div class="stat-label">Apartamento</div></div></div>' +
      '<div class="stat-card"><div class="stat-icon cyan"><span class="material-symbols-outlined">category</span></div><div class="stat-body"><div class="stat-value" style="font-size:22px">' + Utils.escapeHtml(apt ? (apt.tipo || '-') : '-') + '</div><div class="stat-label">Tipo</div></div></div>' +
      '<div class="stat-card"><div class="stat-icon green"><span class="material-symbols-outlined">square_foot</span></div><div class="stat-body"><div class="stat-value" style="font-size:22px">' + Utils.escapeHtml(apt ? (apt.areaM2 || '-') : '-') + ' m&sup2;</div><div class="stat-label">Area</div></div></div>' +
      '<div class="stat-card"><div class="stat-icon amber"><span class="material-symbols-outlined">description</span></div><div class="stat-body"><div class="stat-value" style="font-size:16px">' + (ctr ? Utils.estadoBadge(ctr.estado) : '<span style="color:var(--text-muted);font-size:14px;font-weight:400">Sin contrato</span>') + '</div><div class="stat-label">Estado Contrato</div></div></div>' +
      '</div>' + ctrHtml + '</div>';
  }

  /* ─── Cuotas tab ─── */

  function renderCuotasFull() {
    var container = $('res-tab-cuotas');
    if (!container) return;
    var cuotas = _dashboard ? (_dashboard.cuotas || []) : [];
    var multas = _dashboard ? (_dashboard.multas || []) : [];
    var totalPend = cuotas.filter(function(c) { return c.estado !== 'PAGADA'; }).reduce(function(s, c) { return s + (c.valorTotal || 0); }, 0);
    var totalPag = cuotas.filter(function(c) { return c.estado === 'PAGADA'; }).reduce(function(s, c) { return s + (c.valorTotal || 0); }, 0);
    var rows = cuotas.map(function(c) {
      return '<tr><td>' + Utils.escapeHtml(Utils.periodoLabel(c.anio, c.mes)) + '</td><td>' + Utils.formatDate(c.fechaLimite) + '</td><td>' + Utils.formatCurrency(c.valorBase) + '</td><td>' + Utils.formatCurrency(c.valorMora) + '</td><td>' + Utils.formatCurrency(c.valorTotal) + '</td><td>' + Utils.estadoBadge(c.estado || 'PENDIENTE') + '</td></tr>';
    }).join('');
    var pag = cuotas.filter(function(c) { return c.estado === 'PAGADA'; }).length;
    var pen = cuotas.filter(function(c) { return c.estado !== 'PAGADA'; }).length;
    var chartHtml = cuotas.length ? '<div style="display:flex;gap:24px;align-items:center;margin-bottom:20px"><div style="text-align:center"><canvas id="chart-cuotas-full" width="160" height="160"></canvas></div><div><p class="text-sm text-muted">Total cuotas: ' + cuotas.length + '</p><p class="text-sm text-muted" style="color:var(--accent)">Pagadas: ' + pag + '</p><p class="text-sm text-muted" style="color:var(--warn)">Pendientes: ' + pen + '</p></div></div>' : '';
    var multasPen = multas.filter(function(m) { return m.estado === 'PENDIENTE'; });
    var multasRows = multasPen.map(function(m) {
      var icono = m.tipo === 'RUIDO' ? 'volume_up' : 'local_parking';
      return '<tr onclick="ResidenteDash.verDetalleMulta(' + m.idMulta + ')" style="cursor:pointer" onmouseenter="this.style.background=\'var(--navy-50)\'" onmouseleave="this.style.background=\'\'">' +
        '<td><span class="material-symbols-outlined" style="font-size:18px;vertical-align:middle;margin-right:4px">' + icono + '</span>' + (m.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero') + '</td>' +
        '<td>' + Utils.formatCurrency(m.monto) + '</td>' +
        '<td>' + Utils.estadoBadge(m.estado) + '</td>' +
        '<td>' + (m.fechaCreacion ? Utils.formatDate(m.fechaCreacion) : '-') + '</td>' +
        '<td><button class="btn btn-sm btn-outline" onclick="event.stopPropagation();QuejasResidente.apelarMulta(' + m.idMulta + ')"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">gavel</span> Apelar</button></td></tr>';
    }).join('');
    var multasTotal = multasPen.reduce(function(s, m) { return s + (m.monto || 0); }, 0);
    container.innerHTML = '<div class="card"><div class="card-title">Todas las Cuotas</div>' +
      '<div class="card-grid mb-16">' +
      '<div class="stat-card"><div class="stat-icon amber"><span class="material-symbols-outlined">hourglass_empty</span></div><div class="stat-body"><div class="stat-value" style="color:var(--warn);font-size:22px">' + Utils.formatCurrency(totalPend + multasTotal) + '</div><div class="stat-label">Pendiente</div></div></div>' +
      '<div class="stat-card"><div class="stat-icon green"><span class="material-symbols-outlined">check_circle</span></div><div class="stat-body"><div class="stat-value" style="color:var(--accent);font-size:22px">' + Utils.formatCurrency(totalPag) + '</div><div class="stat-label">Pagado</div></div></div>' +
      '</div>' + chartHtml +
      (cuotas.length ? '<div class="table-container"><table class="data-table"><thead><tr><th>Periodo</th><th>Limite</th><th>Valor Base</th><th>Mora</th><th>Total</th><th>Estado</th></tr></thead><tbody>' + rows + '</tbody></table></div>' : '<p class="text-muted">No hay cuotas registradas</p>') +
      (multasPen.length ? '<div class="card mt-16" style="border-left:4px solid var(--danger)"><div class="card-title">Multas Pendientes <span class="text-sm text-muted" style="font-weight:400">(' + Utils.formatCurrency(multasTotal) + ')</span></div><div class="table-container" style="box-shadow:none;border:1px solid var(--border)"><table class="data-table"><thead><tr><th>Tipo</th><th>Monto</th><th>Estado</th><th>Fecha</th><th>Acción</th></tr></thead><tbody>' + multasRows + '</tbody></table></div></div>' : '') +
      '</div>';
    setTimeout(function() {
      var c = document.getElementById('chart-cuotas-full');
      if (c && (pag || pen)) {
        Utils.drawDonut(c, [{ label: 'Pagadas', value: pag || 1, color: '#B5EAD7' }, { label: 'Pendientes', value: pen || 1, color: '#FFD3B6' }]);
      }
    }, 50);
  }

  async function actualizarBadgeBuzon() {
    var badge = document.getElementById('buzon-badge');
    if (!badge) return;
    try {
      var mensajes = await API.get('/buzon');
      var count = 0;
      for (var i = 0; i < mensajes.length; i++) {
        var m = mensajes[i];
        // Misma lógica de filtrado que renderBuzonList
        if (m.entregado) continue;
        if (m.tipo === 'CONFIRMAR_VISITA' && m.confirmado != null) continue;
        if (m.tipo === 'QUEJA_RUIDO' && m.leido) continue;
        if (m.tipo === 'AVISO' && m.leido) continue;
        count++;
      }
      if (count > 0) {
        badge.textContent = count > 99 ? '99+' : count;
        badge.classList.remove('hidden');
      } else {
        badge.classList.add('hidden');
      }
    } catch (e) {
      badge.classList.add('hidden');
    }
  }

  /* ─── Buzon tab ─── */

  async function renderBuzon() {
    var container = $('res-tab-buzon');
    if (!container) return;
    container.innerHTML = Utils.loadingSpinner();
    try {
      var mensajes = await API.get('/buzon');
      renderBuzonList(container, mensajes || []);
    } catch (e) {
      container.innerHTML = '<p class="text-muted">Error al cargar mensajes</p>';
    }
  }

  function renderBuzonList(container, mensajes) {
    var filtrados = [];
    for (var i = 0; i < mensajes.length; i++) {
      if (mensajes[i].entregado) continue;
      if (mensajes[i].tipo === 'CONFIRMAR_VISITA' && mensajes[i].confirmado != null) continue;
      if (mensajes[i].tipo === 'QUEJA_RUIDO' && mensajes[i].leido) continue;
      if (mensajes[i].tipo === 'AVISO' && mensajes[i].leido) continue;
      filtrados.push(mensajes[i]);
    }
    if (!filtrados.length) {
      container.innerHTML = '<div class="card"><div class="card-title">Buz\u00f3n</div>' + Utils.emptyState('No hay mensajes') + '</div>';
      return;
    }
    var html = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;flex-wrap:wrap;gap:4px">' +
      '<div style="display:flex;align-items:center;gap:8px">' +
      '<input type="checkbox" id="chk-select-all" onchange="ResidenteDash.toggleSeleccionarTodos(this.checked)" style="width:18px;height:18px;cursor:pointer">' +
      '<div class="card-title" style="margin-bottom:0">Buz\u00f3n de Mensajes</div></div>' +
      '<div style="display:flex;gap:4px;flex-wrap:wrap">' +
      '<button class="btn btn-ghost btn-sm" onclick="ResidenteDash.eliminarSeleccionados()" id="btn-eliminar-selec" disabled style="color:var(--danger);font-size:12px"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle;margin-right:2px">delete</span> Eliminar selec.</button>' +
      '<button class="btn btn-ghost btn-sm" onclick="ResidenteDash.vaciarBuzon()" title="Vaciar buzon" style="color:var(--danger);font-size:12px"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle;margin-right:2px">delete_sweep</span> Vaciar todo</button></div></div>';
    html += '<div id="buzon-lista">';
    filtrados.forEach(function(m) {
      var iconName = m.tipo === 'CONFIRMAR_VISITA' ? 'how_to_reg' : m.tipo === 'PAQUETE' ? 'inventory_2' : m.tipo === 'QUEJA_RUIDO' ? 'volume_up' : 'notifications';
      var estiloLeido = m.leido ? '' : ';border-left:3px solid var(--accent);background:#f0f7ff';
      var fotoHtml = '';
      if (m.tipo === 'PAQUETE' && m.fotoCaptura) {
        fotoHtml = '<div style="margin-top:8px"><img src="' + m.fotoCaptura + '" style="max-width:100%;max-height:160px;border-radius:8px;border:1px solid var(--border);cursor:pointer" onclick="window.open(\'' + m.fotoCaptura.replace(/'/g, '') + '\')"></div>';
      }
      var accionBtn = '';
      if (m.tipo === 'PAQUETE' && !m.entregado) {
        accionBtn = '<button class="btn btn-sm btn-primary" onclick="event.stopPropagation();ResidenteDash.marcarEntregado(' + m.idMensaje + ')">Recibido</button>';
      } else if (m.tipo === 'CONFIRMAR_VISITA' && m.confirmado === null) {
        accionBtn = '<span class="text-sm text-muted" style="font-style:italic">Pendiente de confirmaci\u00f3n en el modal...</span>';
      } else if (m.tipo === 'CONFIRMAR_VISITA' && m.confirmado !== null) {
        accionBtn = '<span class="text-sm" style="color:' + (m.confirmado === 1 ? 'var(--accent)' : 'var(--danger)') + '">' + (m.confirmado === 1 ? 'Confirmado' : 'Rechazado') + '</span>';
      }
      html += '<div class="card" style="padding:14px;margin-bottom:12px' + estiloLeido + '">' +
        '<div style="display:flex;align-items:flex-start;gap:12px">' +
        '<input type="checkbox" class="chk-mensaje" value="' + m.idMensaje + '" onchange="ResidenteDash.onCheckChange()" style="width:18px;height:18px;margin-top:4px;cursor:pointer;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:28px;flex-shrink:0;color:var(--text-secondary);margin-top:2px;cursor:pointer" onclick="ResidenteDash.marcarLeido(' + m.idMensaje + ')">' + iconName + '</span>' +
        '<div style="flex:1;min-width:0;cursor:pointer" onclick="ResidenteDash.marcarLeido(' + m.idMensaje + ')">' +
        '<p style="font-weight:600;margin-bottom:2px;font-size:14px">' + Utils.escapeHtml(m.titulo || '') + '</p>' +
        '<p class="text-sm text-muted" style="margin-bottom:4px">' + Utils.escapeHtml(m.cuerpo || '') + '</p>' +
        fotoHtml +
        '<div style="display:flex;justify-content:space-between;align-items:center;margin-top:4px">' +
        '<span class="text-xs text-muted">' + Utils.formatDateTime(m.fechaCreacion) + '</span>' +
        accionBtn + '</div></div></div></div>';
    });
    html += '</div>';
    container.innerHTML = html;
  }

  async function marcarLeido(idMensaje) {
    try {
      await API.put('/buzon/' + idMensaje + '/leido');
      actualizarBadgeBuzon();
      renderBuzon();
    } catch(e) { /* silencioso */ }
  }

  async function marcarEntregado(idMensaje) {
    try {
      await API.put('/buzon/' + idMensaje + '/entregado');
      await API.put('/buzon/' + idMensaje + '/leido');
      Utils.showToast('Paquete marcado como recibido', 'success');
      renderBuzon();
      actualizarBadgeBuzon();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  function toggleSeleccionarTodos(checked) {
    var cbs = document.querySelectorAll('.chk-mensaje');
    for (var i = 0; i < cbs.length; i++) cbs[i].checked = checked;
    actualizarBtnEliminar();
  }

  function onCheckChange() {
    var all = document.querySelectorAll('.chk-mensaje');
    var any = false;
    for (var i = 0; i < all.length; i++) { if (all[i].checked) { any = true; break; } }
    document.getElementById('chk-select-all').checked = any && (function() {
      for (var j = 0; j < all.length; j++) { if (!all[j].checked) return false; }
      return true;
    })();
    actualizarBtnEliminar();
  }

  function actualizarBtnEliminar() {
    var btn = document.getElementById('btn-eliminar-selec');
    if (!btn) return;
    var cbs = document.querySelectorAll('.chk-mensaje');
    var any = false;
    for (var i = 0; i < cbs.length; i++) { if (cbs[i].checked) { any = true; break; } }
    btn.disabled = !any;
  }

  function idsSeleccionados() {
    var ids = [];
    var cbs = document.querySelectorAll('.chk-mensaje:checked');
    for (var i = 0; i < cbs.length; i++) ids.push(parseInt(cbs[i].value));
    return ids;
  }

  async function eliminarSeleccionados() {
    var ids = idsSeleccionados();
    if (!ids.length) { Utils.showToast('Selecciona al menos un mensaje', 'warn'); return; }
    var ok = await Utils.showConfirm('\u00bfEst\u00e1s seguro de eliminar ' + ids.length + ' mensaje' + (ids.length > 1 ? 's' : '') + '?');
    if (!ok) return;
    try {
      await API.put('/buzon/vaciar-multi', { ids: ids });
      Utils.showToast(ids.length + ' mensaje' + (ids.length > 1 ? 's' : '') + ' eliminado' + (ids.length > 1 ? 's' : ''), 'success');
      renderBuzon();
      actualizarBadgeBuzon();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function vaciarBuzon() {
    var ok = await Utils.showConfirm('\u00bfEst\u00e1s seguro de vaciar el buz\u00f3n? Todos los mensajes se marcar\u00e1n como le\u00eddos y recibidos.');
    if (!ok) return;
    try {
      await API.put('/buzon/vaciar');
      Utils.showToast('Buz\u00f3n vaciado', 'success');
      renderBuzon();
      actualizarBadgeBuzon();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  /* ─── Frecuentes tab ─── */

  function renderFrecuentes() {
    var container = $('res-tab-frecuentes');
    if (!container) return;
    var frecs = _frecs || [];
    if (frecs.length) {
      container.innerHTML = '<div class="card"><div class="card-title">Mis Visitantes Frecuentes</div>' +
        '<p class="text-muted text-sm mb-16">Seleccione un visitante para renovar su visita. Antes de generar el QR puede actualizar los datos.</p>' +
        '<div class="frecuentes-grid">' +
        frecs.map(function(f) {
          return '<div class="frecuente-card" style="cursor:pointer" onclick="ResidenteDash.mostrarModalFrecuente(' + f.idVisitante + ')">' +
            '<div class="name">' + Utils.escapeHtml(f.nombreVisitante || '') + '</div>' +
            '<div class="meta">' + Utils.escapeHtml(f.documento || '') + '</div>' +
            '<div class="meta">' + (f.totalVisitas || 0) + ' visita(s)</div>' +
            '<div class="meta">Ultima: ' + Utils.formatDateTime(f.ultimaVisita) + '</div>' +
            (f.ultimaPlaca ? '<div class="meta">Veh\u00edculo: ' + Utils.escapeHtml(f.ultimaPlaca) + '</div>' : '') +
            '<div class="card-actions">' +
            '<span class="btn btn-primary btn-sm">Generar QR</span>' +
            '<span class="btn btn-danger btn-sm" onclick="event.stopPropagation();ResidenteDash.ocultarFrecuente(' + f.idFrecuente + ')" title="Ocultar visitante">Eliminar</span>' +
            '</div></div>';
        }).join('') + '</div></div>';
    } else {
      container.innerHTML = '<div class="card"><div class="card-title">Mis Visitantes Frecuentes</div>' + Utils.emptyState('No tiene visitantes frecuentes') + '</div>';
    }
  }

  async function ocultarFrecuente(idFrecuente) {
    if (!confirm('\u00bfEst\u00e1 seguro de ocultar este visitante frecuente?')) return;
    try {
      var user = Auth.getCurrentUser();
      await API.del('/residentes/' + user.idResidente + '/frecuentes/' + idFrecuente);
      Utils.showToast('Visitante ocultado', 'success');
      _frecs = await API.get('/residentes/' + user.idResidente + '/frecuentes').catch(function() { return []; });
      renderFrecuentes();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  function mostrarModalFrecuente(idVisitante) {
    var frec = null;
    for (var i = 0; i < (_frecs || []).length; i++) {
      if (_frecs[i].idVisitante === idVisitante) { frec = _frecs[i]; break; }
    }
    if (!frec) { Utils.showToast('Visitante no encontrado', 'error'); return; }
    var user = Auth.getCurrentUser();
    var bodyHtml =
      '<form id="form-frec-renovar">' +
      '<div class="form-group"><label>Visitante</label><p style="font-weight:600">' + Utils.escapeHtml(frec.nombreVisitante || '') + '</p></div>' +
      '<div class="form-group"><label>Documento</label><p style="font-weight:600">' + Utils.escapeHtml(frec.documento || '') + '</p></div>' +
      '<div class="form-row"><div class="form-group"><label>Personas</label><input type="number" id="frec-personas" class="form-control" value="1"><span class="field-error" id="frec-personas-error"></span></div>' +
      '<div class="form-group"><label>Validez (min)</label><input type="number" id="frec-validez" class="form-control" value="30"><span class="field-error" id="frec-validez-error"></span></div></div>' +
      '<h4 style="margin:12px 0 8px;font-size:14px;color:var(--text-secondary)">Veh\u00edculo (si aplica)</h4>' +
      '<div class="form-row"><div class="form-group"><label>Placa</label><input type="text" id="frec-placa" class="form-control" value="' + Utils.escapeHtml(frec.ultimaPlaca || '') + '" maxlength="10" style="text-transform:uppercase"><span class="field-error" id="frec-placa-error"></span></div>' +
      '<div class="form-group"><label>Tipo</label><select id="frec-tipo-vehiculo" class="form-control">' +
      '<option value="">Sin veh\u00edculo</option>' +
      '<option value="VEHICULO" ' + (frec.ultimoTipoVehiculo === 'VEHICULO' ? 'selected' : '') + '>Veh\u00edculo</option>' +
      '<option value="MOTO" ' + (frec.ultimoTipoVehiculo === 'MOTO' ? 'selected' : '') + '>Moto</option>' +
      '<option value="BICICLETA" ' + (frec.ultimoTipoVehiculo === 'BICICLETA' ? 'selected' : '') + '>Bicicleta</option>' +
      '<option value="OTRO" ' + (frec.ultimoTipoVehiculo === 'OTRO' ? 'selected' : '') + '>Otro</option></select></div></div>' +
      '<div class="form-group"><label>Notas</label><textarea id="frec-notas" class="form-control" maxlength="500" placeholder="Alguna indicaci\u00f3n para el portero"></textarea></div>' +
      '</form>';
    Utils.modal('Renovar Visita - ' + Utils.escapeHtml(frec.nombreVisitante || ''),
      bodyHtml,
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-renovar-frec" onclick="ResidenteDash.renovarFrecuente(' + frec.idVisitante + ')">Generar QR</button>');
    configurarValidacionPlacaFrecuente();
  }

  function configurarValidacionPlacaFrecuente() {
    var placaInput = document.getElementById('frec-placa');
    var tipoSelect = document.getElementById('frec-tipo-vehiculo');
    if (!placaInput || !tipoSelect) return;
    placaInput.addEventListener('input', function() {
      this.value = this.value.replace(/[^a-zA-Z0-9\s]/g, '').toUpperCase();
    });
    tipoSelect.addEventListener('change', function() {
      if (this.value === '') {
        placaInput.value = '';
        Utils.limpiarErrores('form-frec-renovar');
      }
    });
  }

  async function renovarFrecuente(idVisitante) {
    var frec = null;
    for (var i = 0; i < (_frecs || []).length; i++) {
      if (_frecs[i].idVisitante === idVisitante) { frec = _frecs[i]; break; }
    }
    if (!frec) return;
    Utils.limpiarErrores('form-frec-renovar');
    if (!Utils.valEntero(document.getElementById('frec-personas').value, 'frec-personas', { positive: true, min: 1, max: 99, label: 'Las personas' })) return;
    if (!Utils.valEntero(document.getElementById('frec-validez').value, 'frec-validez', { positive: true, min: 5, max: 120, label: 'La validez' })) return;
    
    var placa = document.getElementById('frec-placa').value.trim();
    var tipoV = document.getElementById('frec-tipo-vehiculo').value;
    
    // Validar placa si hay vehículo
    if (placa && tipoV && tipoV !== 'BICICLETA' && tipoV !== 'OTRO') {
      if (!validarPlacaColombia(placa, tipoV)) {
        var formato = tipoV === 'VEHICULO' ? 'AAA123 o AAA 123' : 'AAA12D o AAA 12D';
        Utils.mostrarError('frec-placa', 'Formato inv\u00e1lido. Use: ' + formato);
        return;
      }
    }
    if (tipoV === 'BICICLETA' && !placa) {
      Utils.mostrarError('frec-placa', 'Ingrese una descripci\u00f3n para la bicicleta');
      return;
    }
    
    var user = Auth.getCurrentUser();
    var btn = document.getElementById('btn-renovar-frec');
    var personas = parseInt(document.getElementById('frec-personas').value) || 1;
    var validez = parseInt(document.getElementById('frec-validez').value) || 30;
    var notas = document.getElementById('frec-notas').value.trim();
    var d = {
      idFrecuente: frec.idFrecuente,
      idVisitante: idVisitante,
      idResidente: user.idResidente,
      cantidadPersonas: personas,
      tiempoValidezMin: validez,
      notas: notas
    };
    if (placa && tipoV) { d.tipoVehiculo = tipoV; d.placa = placa; }
    if (btn) btn.disabled = true;
    try {
      var res = await API.post('/visitas/rapida', d);
      Utils.showToast('Visita registrada exitosamente', 'success');
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      renderFrecuentes();
      mostrarQRResultado(res.codigoQr, frec.nombreVisitante);
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  function qrImageUrl(codigoQr) {
    return 'https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=' + encodeURIComponent(codigoQr);
  }

  function compartirTelegram(codigoQr, nombre) {
    var imgUrl = qrImageUrl(codigoQr);
    var text = encodeURIComponent('C\u00f3digo QR de acceso para ' + (nombre || 'tu visita') + '\n\nAbre esta imagen para escanear:\n' + imgUrl);
    window.open('https://t.me/share/url?url=' + encodeURIComponent(imgUrl) + '&text=' + text, '_blank');
  }

  function compartirSMS(codigoQr, telefono) {
    var imgUrl = qrImageUrl(codigoQr);
    var body = encodeURIComponent('Tu c\u00f3digo QR de acceso: ' + codigoQr + ' - Abre la imagen: ' + imgUrl);
    window.open(telefono ? 'sms:' + telefono + '?body=' + body : 'sms:?body=' + body);
  }

  function compartirCorreo(codigoQr, nombre, email) {
    var imgUrl = qrImageUrl(codigoQr);
    var subject = encodeURIComponent('C\u00f3digo QR de Acceso');
    var body = encodeURIComponent(
      'Hola,\n\n' +
      'Has recibido un c\u00f3digo QR de acceso' + (nombre ? ' para ' + nombre : '') + '.\n\n' +
      'C\u00f3digo: ' + codigoQr + '\n\n' +
      'O abre esta imagen para escanear:\n' + imgUrl + '\n\n' +
      'Pres\u00e9ntala en la entrada del edificio.'
    );
    window.open(email ? 'mailto:' + email + '?subject=' + subject + '&body=' + body : 'mailto:?subject=' + subject + '&body=' + body);
  }

  function shareButtonsHtml(codigoQr, nombre, telefono, email) {
    var q = codigoQr.replace(/['\\]/g, '');
    var n = (nombre || '').replace(/'/g, '');
    var t = (telefono || '').replace(/'/g, '');
    var e = (email || '').replace(/'/g, '');
    return '<div class="share-buttons" style="margin-top:10px">' +
      '<button class="btn btn-primary btn-sm" onclick="ResidenteDash.compartirTelegram(\'' + q + '\',\'' + n + '\')" title="Compartir por Telegram">Telegram</button>' +
      '<button class="btn btn-primary btn-sm" onclick="ResidenteDash.compartirSMS(\'' + q + '\',\'' + t + '\')" title="Compartir por SMS">SMS</button>' +
      '<button class="btn btn-primary btn-sm" onclick="ResidenteDash.compartirCorreo(\'' + q + '\',\'' + n + '\',\'' + e + '\')" title="Compartir por Correo">Correo</button>' +
      '<button class="btn btn-outline btn-sm" onclick="ResidenteDash.copiarQR(\'' + q + '\')">Copiar QR</button></div>';
  }

  function mostrarQRResultado(codigoQr, nombreVisitante) {
    var container = $('res-tab-frecuentes') || $('res-tab-nueva-visita');
    if (!container) return;
    if (container) container.innerHTML +=
      '<div class="card" style="border-left:4px solid var(--accent);margin-top:16px">' +
      '<div class="card-title">QR Generado</div>' +
      '<div class="qr-container" style="text-align:center">' +
      '<p><strong>Visitante: ' + Utils.escapeHtml(nombreVisitante || '') + '</strong></p>' +
      '<div style="margin:12px 0"><img src="' + qrImageUrl(codigoQr) + '" alt="QR" style="border-radius:8px"></div>' +
      '<p class="qr-code-text" style="font-size:12px;color:var(--text-muted)">#' + Utils.escapeHtml(codigoQr.substring(0,8)) + '...</p>' +
      shareButtonsHtml(codigoQr, nombreVisitante, '', '') +
      '</div></div>';
  }

  function copiarQR(qr) {
    navigator.clipboard.writeText(qr).then(function() { Utils.showToast('C\u00f3digo QR copiado al portapapeles', 'success'); });
  }

  /* ─── Nueva Visita tab ─── */

  function getTipoDocCodigo(idTipoDoc) {
    if (!idTipoDoc) return null;
    for (var i = 0; i < (_tiposDoc || []).length; i++) {
      if (_tiposDoc[i].idTipoDoc == idTipoDoc) {
        return _tiposDoc[i].codigo || null;
      }
    }
    return null;
  }

  function aplicarFiltroDocumentoVisita(input, tipoDocCodigo) {
    if (!input || !tipoDocCodigo) return;
    var nuevoInput = input.cloneNode(true);
    input.parentNode.replaceChild(nuevoInput, input);
    var esNumerico = ['CC', 'TI', 'CE', 'RC', 'NIT'].indexOf(tipoDocCodigo) >= 0;
    var pattern = esNumerico ? /[^0-9]/g : (tipoDocCodigo === 'PEP' ? /[^a-zA-Z0-9-]/g : /[^a-zA-Z0-9]/g);
    var maxLen = { 'CC': 10, 'TI': 11, 'CE': 12, 'RC': 11, 'NIT': 10, 'PP': 20, 'PASAPORTE': 20, 'PEP': 15 }[tipoDocCodigo] || 20;
    nuevoInput.maxLength = maxLen;
    nuevoInput.addEventListener('input', function(e) {
      var cursorPos = this.selectionStart;
      var valAntes = this.value;
      this.value = this.value.replace(pattern, '');
      if (['PP', 'PASAPORTE', 'PEP'].indexOf(tipoDocCodigo) >= 0) this.value = this.value.toUpperCase();
      if (this.value !== valAntes && cursorPos > 0) this.setSelectionRange(cursorPos - 1, cursorPos - 1);
    });
    nuevoInput.addEventListener('paste', function(e) {
      e.preventDefault();
      var texto = (e.clipboardData || window.clipboardData).getData('text');
      var limpio = texto.replace(pattern, '');
      if (['PP', 'PASAPORTE', 'PEP'].indexOf(tipoDocCodigo) >= 0) limpio = limpio.toUpperCase();
      this.value = limpio.substring(0, maxLen);
    });
  }

  function configurarValidacionNuevaVisita() {
    var tipoDocSelect = document.getElementById('res-vis-tipo-doc');
    var docInput = document.getElementById('res-vis-documento');
    if (tipoDocSelect && docInput) {
      tipoDocSelect.addEventListener('change', function() {
        docInput.value = '';
        Utils.limpiarErrores('form-visita-residente');
        var codigo = getTipoDocCodigo(this.value);
        if (codigo) aplicarFiltroDocumentoVisita(docInput, codigo);
      });
    }
    Utils.soloLetras('res-vis-nombres', 20);
    Utils.soloLetras('res-vis-apellidos', 20);
    Utils.soloNumeros('res-vis-telefono', 10);
    Utils.validarTelefonoTiempoReal('res-vis-telefono');
    configurarValidacionPlacaVisita();
  }

  function configurarValidacionPlacaVisita() {
    var placaInput = document.getElementById('res-vis-placa');
    var tipoSelect = document.getElementById('res-vis-tipo-vehiculo');
    if (!placaInput || !tipoSelect) return;
    placaInput.addEventListener('input', function() {
      this.value = this.value.replace(/[^a-zA-Z0-9\s]/g, '').toUpperCase();
    });
    tipoSelect.addEventListener('change', function() {
      if (this.value === '') placaInput.value = '';
    });
  }

  function validarPlacaColombia(placa, tipo) {
    if (!placa || !tipo) return true;
    placa = placa.replace(/\s/g, '');
    if (tipo === 'VEHICULO') return /^[A-Z]{3}\d{3}$/.test(placa);
    if (tipo === 'MOTO') return /^[A-Z]{3}\d{2}[A-Z]$/.test(placa);
    return true;
  }

  function renderNuevaVisita() {
    var container = $('res-tab-nueva-visita');
    if (!container) return;
    var user = Auth.getCurrentUser();
    var tiposOpts = '<option value="">Seleccione...</option>' +
      (_tiposDoc || []).map(function(t) { return '<option value="' + t.idTipoDoc + '" data-codigo="' + Utils.escapeHtml(t.codigo || '') + '">' + Utils.escapeHtml(t.codigo) + ' - ' + Utils.escapeHtml(t.descripcion) + '</option>'; }).join('');
    container.innerHTML =
      '<div class="card"><div class="card-title">Registrar Nueva Visita</div>' +
      '<form id="form-visita-residente">' +
      '<div class="form-row"><div class="form-group"><label>Tiempo Validez (min)</label><input type="number" id="res-vis-validez" class="form-control" value="30"></div>' +
      '<div class="form-group"><label>Personas</label><input type="number" id="res-vis-personas" class="form-control" value="1"></div></div>' +
      '<h4 style="margin:12px 0 8px;font-size:14px">Datos del Visitante</h4>' +
      '<div class="form-row"><div class="form-group"><label>Tipo Documento *</label><select id="res-vis-tipo-doc" class="form-control">' + tiposOpts + '</select><span class="field-error" id="res-vis-tipo-doc-error"></span></div>' +
      '<div class="form-group"><label>N\u00famero Documento *</label><input type="text" id="res-vis-documento" class="form-control"><span class="field-error" id="res-vis-documento-error"></span></div></div>' +
      '<div class="form-row"><div class="form-group"><label>Nombres *</label><input type="text" id="res-vis-nombres" class="form-control" maxlength="20"><span class="field-error" id="res-vis-nombres-error"></span></div>' +
      '<div class="form-group"><label>Apellidos *</label><input type="text" id="res-vis-apellidos" class="form-control" maxlength="20"><span class="field-error" id="res-vis-apellidos-error"></span></div></div>' +
      '<div class="form-row"><div class="form-group"><label>Tel\u00e9fono</label><input type="text" id="res-vis-telefono" class="form-control" maxlength="10"><span class="field-error" id="res-vis-telefono-error"></span></div>' +
      '<div class="form-group"><label>Email</label><input type="email" id="res-vis-email" class="form-control" maxlength="100"><span class="field-error" id="res-vis-email-error"></span></div></div>' +
      '<h4 style="margin:12px 0 8px;font-size:14px">Veh\u00edculo (opcional)</h4>' +
      '<div class="form-row"><div class="form-group"><label>Placa</label><input type="text" id="res-vis-placa" class="form-control" maxlength="10" style="text-transform:uppercase"><span class="field-error" id="res-vis-placa-error"></span></div>' +
      '<div class="form-group"><label>Tipo</label><select id="res-vis-tipo-vehiculo" class="form-control"><option value="">Sin veh\u00edculo</option><option value="VEHICULO">Veh\u00edculo</option><option value="MOTO">Moto</option><option value="BICICLETA">Bicicleta</option><option value="OTRO">Otro</option></select></div></div>' +
      '<div class="form-group"><label>Notas</label><textarea id="res-vis-notas" class="form-control" maxlength="500"></textarea></div>' +
      '<div class="mt-16"><button type="submit" class="btn btn-primary" id="btn-guardar-visita-res">Generar QR y Registrar</button></div>' +
      '</form><div id="res-vis-qr-result" class="hidden mt-16"></div></div>';
    document.getElementById('form-visita-residente').onsubmit = function(e) { e.preventDefault(); guardarVisita(); };
    document.getElementById('res-vis-documento').addEventListener('blur', async function() {
      var doc = this.value.trim();
      if (doc.length < 3) return;
      try {
        var data = await API.get('/visitas/buscar?documento=' + encodeURIComponent(doc));
        if (data && data.nombres) {
          document.getElementById('res-vis-nombres').value = data.nombres || '';
          document.getElementById('res-vis-apellidos').value = data.apellidos || '';
          if (data.telefono) document.getElementById('res-vis-telefono').value = data.telefono || '';
          if (data.email) document.getElementById('res-vis-email').value = data.email || '';
        }
      } catch(e) { /* silencioso */ }
    });
    configurarValidacionNuevaVisita();
  }

  async function guardarVisita() {
    var btn = document.getElementById('btn-guardar-visita-res');
    Utils.limpiarErrores('form-visita-residente');
    
    // Validaciones con tipo de documento
    if (!Utils.valSelect(document.getElementById('res-vis-tipo-doc').value, 'res-vis-tipo-doc', 'Seleccione el tipo de documento')) return;
    var tipoDocId = document.getElementById('res-vis-tipo-doc').value;
    var tipoDocCodigo = getTipoDocCodigo(tipoDocId);
    if (!Utils.valDocumento(document.getElementById('res-vis-documento').value, 'res-vis-documento', tipoDocCodigo)) return;
    if (!Utils.valNombre(document.getElementById('res-vis-nombres').value, 'res-vis-nombres', 'El nombre')) return;
    if (!Utils.valApellido(document.getElementById('res-vis-apellidos').value, 'res-vis-apellidos', 'El apellido')) return;
    
    // Teléfono y email opcionales
    var telVal = document.getElementById('res-vis-telefono').value.trim();
    var emailVal = document.getElementById('res-vis-email').value.trim();
    if (telVal && !Utils.valTelefono(telVal, 'res-vis-telefono', { required: false })) return;
    if (emailVal && !Utils.valEmail(emailVal, 'res-vis-email', { required: false })) return;
    
    // Validación de placa si hay vehículo
    var placa = document.getElementById('res-vis-placa').value.trim();
    var tipoV = document.getElementById('res-vis-tipo-vehiculo').value;
    if (placa && tipoV && tipoV !== 'BICICLETA' && tipoV !== 'OTRO') {
      if (!validarPlacaColombia(placa, tipoV)) {
        var formato = tipoV === 'VEHICULO' ? 'AAA123 o AAA 123' : 'AAA12D o AAA 12D';
        Utils.mostrarError('res-vis-placa', 'Formato inv\u00e1lido. Use: ' + formato);
        return;
      }
    }
    if (tipoV === 'BICICLETA' && !placa) {
      Utils.mostrarError('res-vis-placa', 'Ingrese una descripci\u00f3n para la bicicleta');
      return;
    }
    
    var user = Auth.getCurrentUser();
    var d = {
      idResidente: user.idResidente,
      tiempoValidezMin: parseInt(document.getElementById('res-vis-validez').value) || 30,
      cantidadPersonas: parseInt(document.getElementById('res-vis-personas').value) || 1,
      visitante: {
        idTipoDoc: parseInt(tipoDocId),
        numeroDocumento: document.getElementById('res-vis-documento').value.trim(),
        nombres: document.getElementById('res-vis-nombres').value.trim(),
        apellidos: document.getElementById('res-vis-apellidos').value.trim(),
        telefono: telVal,
        email: emailVal
      },
      notas: document.getElementById('res-vis-notas').value.trim()
    };
    if (placa && tipoV) { d.vehiculo = { placa: placa, tipo: tipoV }; }
    if (btn) btn.disabled = true;
    try {
      var res = await API.post('/visitas', d);
      Utils.showToast('Visita registrada exitosamente', 'success');
      mostrarQRResultadoVisita(res.codigoQr, d.visitante.nombres + ' ' + d.visitante.apellidos, d.visitante.telefono, d.visitante.email);
      document.getElementById('form-visita-residente').reset();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function verDetalleMulta(idMulta) {
    try {
      var m = await API.get('/multas/' + idMulta);

      var overlay = document.createElement('div');
      overlay.id = 'modal-detalle-multa-residente';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };

      var icono = m.tipo === 'RUIDO' ? 'volume_up' : 'local_parking';
      var color = m.tipo === 'RUIDO' ? 'var(--warn-500)' : 'var(--navy-500)';
      var tipoLabel = m.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero';

      var html = '<div style="background:#fff;border-radius:16px;max-width:520px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
        '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, var(--navy-50) 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:' + color + ';display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">' + icono + '</span></div>' +
        '<div><h3 style="margin:0;font-size:18px;font-weight:600">Multa por ' + tipoLabel + '</h3>' +
        '<p class="text-xs text-muted" style="margin:2px 0 0">' + Utils.estadoBadge(m.estado) + '</p></div></div></div>' +
        '<div style="padding:24px">' +
        '<div class="form-group"><label>Monto</label><div class="form-control" style="background:#f9fafb;cursor:default;font-weight:600;font-size:18px;color:var(--danger)">' + Utils.formatCurrency(m.monto) + '</div></div>';

      if (m.descripcion) {
        html += '<div class="form-group"><label>Descripción</label><div class="form-control" style="background:#f9fafb;cursor:default;min-height:60px;white-space:pre-wrap">' + Utils.escapeHtml(m.descripcion) + '</div></div>';
      }

      html += '<div class="form-group"><label>Fecha de Generación</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDateTime(m.fechaCreacion) + '</div></div>' +
        '<div class="form-group"><label>Generada por</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(m.nombrePortero || 'Sistema') + '</div></div>';

      if (m.tipo === 'RUIDO' && m.fechaAvisoRuido) {
        var fechaAviso = new Date(m.fechaAvisoRuido);
        var fechaMulta = new Date(m.fechaCreacion);
        var diffMin = Math.round((fechaMulta - fechaAviso) / 60000);
        var tiempoLabel;
        if (diffMin < 0) {
          tiempoLabel = 'No disponible';
        } else if (diffMin < 60) {
          tiempoLabel = diffMin + ' minuto' + (diffMin !== 1 ? 's' : '');
        } else {
          var horas = Math.floor(diffMin / 60);
          var mins = diffMin % 60;
          tiempoLabel = horas + ' hora' + (horas !== 1 ? 's' : '');
          if (mins > 0) tiempoLabel += ' y ' + mins + ' minuto' + (mins !== 1 ? 's' : '');
        }
        html += '<div style="margin-top:16px;padding:12px;background:var(--warn-50);border:1px solid var(--warn-200);border-radius:8px">' +
          '<div style="font-weight:600;margin-bottom:8px;color:var(--warn-700);display:flex;align-items:center;gap:6px">' +
          '<span class="material-symbols-outlined" style="font-size:18px">notifications</span>' +
          'Aviso de Ruido Previo</div>' +
          '<div style="font-size:13px;color:var(--text-secondary);margin-bottom:4px">' +
          '<span style="font-weight:600">Hora del aviso:</span> ' + Utils.formatDateTime(m.fechaAvisoRuido) + '</div>' +
          '<div style="font-size:13px;color:var(--text-secondary)">' +
          '<span style="font-weight:600">Tiempo hasta la multa:</span> ' +
          '<span style="color:var(--warn-700);font-weight:600">' + tiempoLabel + '</span>' +
          ' despu\u00e9s del aviso</div></div>';
      }

      if (m.tipo === 'PARQUEADERO' && m.fotoEvidencia) {
        html += '<div class="form-group" style="margin-top:16px"><label>Foto de Evidencia</label>' +
          '<img src="' + m.fotoEvidencia + '" style="width:100%;max-height:300px;object-fit:contain;border-radius:8px;border:1px solid var(--border);cursor:pointer" onclick="window.open(this.src)" title="Click para ver en tamaño completo"></div>';
      }

      html += '</div>' +
        '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
        '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-multa-residente\').remove()">Cerrar</button>' +
        '</div></div>';

      overlay.innerHTML = html;
      document.body.appendChild(overlay);
    } catch (e) {
      Utils.showToast('Error al cargar detalle: ' + e.message, 'error');
    }
  }

  function mostrarQRResultadoVisita(codigoQr, nombreCompleto, telefono, email) {
    var container = document.getElementById('res-vis-qr-result');
    if (!container) return;
    container.innerHTML =
      '<div class="card" style="border-left:4px solid var(--accent)"><div class="card-title">QR Generado</div>' +
      '<div class="qr-container" style="text-align:center">' +
      '<p><strong>Visitante: ' + Utils.escapeHtml(nombreCompleto || '') + '</strong></p>' +
      '<div style="margin:12px 0"><img src="' + qrImageUrl(codigoQr) + '" alt="QR" style="border-radius:8px"></div>' +
      '<p class="qr-code-text" style="font-size:12px;color:var(--text-muted)">#' + Utils.escapeHtml(codigoQr.substring(0,8)) + '...</p>' +
      shareButtonsHtml(codigoQr, nombreCompleto, telefono, email) +
      '</div></div>';
    container.classList.remove('hidden');
  }

  return {
    inicializar: inicializar, limpiar: limpiar, cambiarTab: cambiarTab, guardarPerfil: guardarPerfil,
    mostrarModalFrecuente: mostrarModalFrecuente, renovarFrecuente: renovarFrecuente,
    ocultarFrecuente: ocultarFrecuente,
    copiarQR: copiarQR, compartirTelegram: compartirTelegram,
    compartirSMS: compartirSMS, compartirCorreo: compartirCorreo,
    confirmarVisita: confirmarVisita, rechazarVisita: rechazarVisita,
    marcarLeido: marcarLeido, marcarEntregado: marcarEntregado, vaciarBuzon: vaciarBuzon,
    toggleSeleccionarTodos: toggleSeleccionarTodos, onCheckChange: onCheckChange,
    eliminarSeleccionados: eliminarSeleccionados,
    verDetalleMulta: verDetalleMulta
  };
})();

Router.register('residente-dashboard', {
  html: document.getElementById('tpl-residente-dashboard').innerHTML,
  js: function() { document.getElementById('page-title').textContent = 'Mi Portal'; ResidenteDash.inicializar(); },
  onLeave: ResidenteDash.limpiar
});
