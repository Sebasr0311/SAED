function toggleSidebar(force) {
  const sidebar = document.getElementById('sidebar');
  const overlay = document.querySelector('.sidebar-overlay');
  const isOpen = force !== undefined ? force : !sidebar.classList.contains('open');
  sidebar.classList.toggle('open', isOpen);
  if (overlay) overlay.classList.toggle('show', isOpen);
}
function buildSidebar() {
  const user = Auth.getCurrentUser();
  if (!user) return;
  const role = user.rol;
  const NAV_ITEMS = {
    ADMINISTRADOR: [
      { page: 'dashboard', icon: 'dashboard', label: 'Dashboard' },
      { page: 'residentes', icon: 'groups', label: 'Residentes' },
      { page: 'apartamentos', icon: 'domain', label: 'Apartamentos' },
      { page: 'contratos', icon: 'description', label: 'Contratos' },
      { page: 'pagos', icon: 'payments', label: 'Pagos' },
      { page: 'visitas', icon: 'how_to_reg', label: 'Visitas' },
      { page: 'historial-visitas', icon: 'history', label: 'Historial Visitas' },
      { page: 'parqueaderos', icon: 'local_parking', label: 'Parqueaderos' },
      { page: 'usuarios', icon: 'manage_accounts', label: 'Usuarios' },
      { page: 'alertas', icon: 'notifications', label: 'Alertas' },
      { page: 'avisos', icon: 'campaign', label: 'Avisos' },
      { page: 'quejas-admin', icon: 'feedback', label: 'Solicitudes' },
      { page: 'paquetes-admin', icon: 'inventory_2', label: 'Paquetes' },
      { page: 'escanner-qr', icon: 'qr_code_scanner', label: 'Escaner QR' }
    ],
    PORTERO: [
      { page: 'portero-dashboard', icon: 'admin_panel_settings', label: 'Panel' },
      { page: 'visitas', icon: 'how_to_reg', label: 'Visitas' },
      { page: 'escanner-qr', icon: 'qr_code_scanner', label: 'Escaner QR' },
      { page: 'parqueaderos', icon: 'local_parking', label: 'Parqueaderos' },
      { page: 'paquetes', icon: 'inventory_2', label: 'Paquetes' }
    ],
    RESIDENTE: [
      { page: 'residente-dashboard', icon: 'home', label: 'Mi Portal' }
    ]
  };
  const items = NAV_ITEMS[role] || NAV_ITEMS.RESIDENTE;
  const nav = document.getElementById('sidebar-nav');
  nav.innerHTML = items.map(item =>
    `<button class="sidebar-btn" data-page="${item.page}" onclick="Router.navigate('${item.page}')" title="${item.label}">
      <span class="material-symbols-outlined">${item.icon}</span>
      <span class="sidebar-label">${item.label}</span>
    </button>`
  ).join('');
  var userEl = document.getElementById('topbar-user');
  if (userEl) userEl.textContent = user.username;
  var roleEl = document.getElementById('topbar-role');
  if (roleEl) roleEl.textContent = role;
  var avatar = document.getElementById('topbar-avatar');
  if (avatar) avatar.textContent = (user.username || 'U')[0].toUpperCase();
}

(function init() {
  Router.register('dashboard', {
    html: document.getElementById('tpl-dashboard').innerHTML,
    onLeave: function() { Dash._reset(); },
    js: async () => {
      const title = document.getElementById('page-title');
      if (title) title.textContent = 'Dashboard';
      const setStat = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val ?? '-'; };

      // Load data in parallel — only what we need
      const [res, apts, conts] = await Promise.all([
        API.get('/residentes').catch(() => []),
        API.get('/apartamentos').catch(() => []),
        API.get('/contratos').catch(() => [])
      ]);

      // Stat cards
      setStat('stat-residentes', res.length);
      setStat('stat-apartamentos', apts.length);
      setStat('stat-contratos', (conts || []).filter(c => c.estado === 'ACTIVO').length);

      // Próximos Cobros
      const pagosContainer = document.getElementById('dash-proximos-cobros');
      if (pagosContainer) {
        const pagos = (conts || []).filter(c => c.estado === 'ACTIVO');
        if (pagos.length) {
          pagosContainer.innerHTML = pagos.map(c =>
            '<div class="payment-item" onclick="Dash.verDetalleContrato(' + c.idContrato + ')" style="cursor:pointer;transition:all 0.2s" onmouseenter="this.style.background=\'var(--navy-50)\';this.style.borderColor=\'var(--navy-200)\'" onmouseleave="this.style.background=\'\';this.style.borderColor=\'var(--border)\'">'
            + '<div style="display:flex;align-items:center;gap:10px">'
            + '<div class="payment-dot"></div>'
            + '<div>'
            + '<div class="payment-apt">Apt ' + (c.numeroApartamento || c.idApartamento) + '</div>'
            + '<div style="font-size:11px;color:var(--text-muted);margin-top:1px">Contrato #' + c.idContrato + '</div>'
            + '</div>'
            + '</div>'
            + '<span class="payment-amount">' + (c.valorMensual ? '$' + Number(c.valorMensual).toLocaleString('es-CO') : '-') + '</span>'
            + '</div>'
          ).join('');
        } else {
          pagosContainer.innerHTML = '<div class="empty-state" style="padding:28px 16px"><div class="empty-icon"><span class="material-symbols-outlined">payments</span></div><div class="empty-desc">Sin contratos activos</div></div>';
        }
      }

      // Multas Pendientes
      try {
        var multas = await API.get('/multas/todas');
        var pendientes = (multas || []).filter(function(m) { return m.estado === 'PENDIENTE'; });
        var countEl = document.getElementById('dash-multas-count');
        var contentEl = document.getElementById('dash-multas-content');
        if (countEl) countEl.textContent = pendientes.length + ' pendiente' + (pendientes.length !== 1 ? 's' : '');
        if (contentEl) {
          if (!pendientes.length) {
            contentEl.innerHTML = '<div class="empty-state" style="padding:32px 16px"><div class="empty-icon"><span class="material-symbols-outlined">gavel</span></div><div class="empty-title">Sin multas pendientes</div></div>';
          } else {
            var html = '';
            var agrupadas = {};
            pendientes.forEach(function(m) {
              var key = m.numeroApartamento || m.idApartamento;
              if (!agrupadas[key]) agrupadas[key] = { apto: key, residente: m.nombreResidente || '', multas: [] };
              agrupadas[key].multas.push(m);
            });
            Object.keys(agrupadas).forEach(function(key) {
              var g = agrupadas[key];
              html += '<div style="margin-bottom:0;padding:14px 0;border-bottom:1px solid var(--border-subtle)">'
                + '<div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">'
                + '<span style="font-size:13px;font-weight:700;color:var(--text)">Apto ' + Utils.escapeHtml(String(g.apto)) + '</span>'
                + (g.residente ? '<span style="font-size:12px;color:var(--text-muted);font-weight:400">' + Utils.escapeHtml(g.residente) + '</span>' : '')
                + '</div>';
              g.multas.forEach(function(m) {
                var icono = m.tipo === 'RUIDO' ? 'volume_up' : 'local_parking';
                var iconColor = m.tipo === 'RUIDO' ? 'var(--warn-500)' : 'var(--navy-500)';
                html += '<div style="display:flex;justify-content:space-between;align-items:center;padding:5px 0 5px 4px;font-size:13px;cursor:pointer;border-radius:4px;transition:all 0.2s" onclick="Dash.verDetalleMulta(' + m.idMulta + ')" onmouseenter="this.style.background=\'var(--navy-50)\'" onmouseleave="this.style.background=\'\'">'
                  + '<span style="display:flex;align-items:center;gap:6px;color:var(--text-secondary)">'
                  + '<span class="material-symbols-outlined" style="font-size:15px;color:' + iconColor + '">' + icono + '</span>'
                  + (m.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero') + ' — ' + Utils.formatCurrency(m.monto)
                  + '</span>'
                  + '<span style="display:flex;align-items:center;gap:8px">'
                  + '<span style="font-size:11px;color:var(--text-muted)">' + (m.fechaCreacion ? m.fechaCreacion.substring(0, 10) : '-') + '</span>'
                  + '<button class="btn-icon btn-ghost" onclick="event.stopPropagation();Dash.notificarUna(' + m.idMulta + ')" title="Notificar" style="width:28px;height:28px;color:var(--text-muted)">'
                  + '<span class="material-symbols-outlined" style="font-size:15px">notifications</span></button>'
                  + '</span></div>';
              });
              html += '</div>';
            });
            contentEl.innerHTML = '<div style="margin-top:-14px">' + html + '</div>';
          }
        }
      } catch(e) {
        var contentEl = document.getElementById('dash-multas-content');
        if (contentEl) contentEl.innerHTML = '<p class="text-muted text-sm">Error al cargar multas</p>';
      }
    }
  });

  Router.register('avisos', {
    html: document.getElementById('tpl-avisos').innerHTML,
    onLeave: function() { Avisos.destroy(); },
    js: function() { Avisos.init(); }
  });

  Router.register('quejas-admin', {
    html: document.getElementById('tpl-quejas-admin').innerHTML,
    onLeave: function() { QuejasAdmin.destroy(); },
    js: function() { QuejasAdmin.init(); }
  });

  function start() {
    document.body.className = 'app-shell';
    const shell = document.getElementById('tpl-shell').innerHTML;
    const app = document.getElementById('app');
    app.innerHTML = shell;

    if (Auth.isAuthenticated()) {
      buildSidebar();
      const user = Auth.getCurrentUser();
      if (user.rol === 'RESIDENTE') Router.navigate('residente-dashboard');
      else if (user.rol === 'PORTERO') Router.navigate('portero-dashboard');
      else Router.navigate('dashboard');
    } else {
      Router.navigate('login');
    }
  }

  window.addEventListener('DOMContentLoaded', start);
})();

const Dash = (() => {
  var _loaded = false;
  var _modalOverlay = null;

  function toggleDetalles() {
    var btn = document.getElementById('btn-ver-detalles');
    if (_modalOverlay) {
      _modalOverlay.remove();
      _modalOverlay = null;
      if (btn) btn.textContent = 'Ver detalles';
      return;
    }
    if (btn) btn.textContent = 'Cargando...';
    if (!_loaded) { _loaded = true; cargarDetalles(); }
    else { mostrarModal(); }
  }

  function mostrarModal() {
    var btn = document.getElementById('btn-ver-detalles');
    _modalOverlay = document.createElement('div');
    _modalOverlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);z-index:20000;display:flex;align-items:center;justify-content:center;padding:16px';
    _modalOverlay.onclick = function(e) { if (e.target === _modalOverlay) toggleDetalles(); };
    var box = document.createElement('div');
    box.style.cssText = 'background:#fff;border-radius:12px;max-width:400px;width:100%;max-height:80vh;overflow-y:auto;padding:20px;box-shadow:0 8px 32px rgba(0,0,0,0.2)';
    box.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">' +
      '<h3 style="margin:0;font-size:16px;color:var(--text)">Estado del Sistema</h3>' +
      '<button onclick="Dash.toggleDetalles()" style="background:none;border:none;cursor:pointer;font-size:20px;color:var(--text-muted);padding:4px">&times;</button></div>' +
      '<div style="font-size:13px;font-weight:600;margin-bottom:8px;color:var(--text)">Visitantes dentro del edificio</div>' +
      '<div id="dash-visitas-dentro" style="font-size:13px;color:var(--text-secondary)">' + (_modalData ? _modalData.visitasHtml || 'Cargando...' : 'Cargando...') + '</div>' +
      '<div style="font-size:13px;font-weight:600;margin:12px 0 8px;color:var(--text)">QR generados sin escanear</div>' +
      '<div id="dash-qr-pendientes" style="font-size:13px;color:var(--text-secondary)">' + (_modalData ? _modalData.qrHtml || 'Cargando...' : 'Cargando...') + '</div>';
    _modalOverlay.appendChild(box);
    document.body.appendChild(_modalOverlay);
    if (btn) btn.textContent = 'Cerrar';
  }

  var _modalData = null;

  async function cargarDetalles() {
    try {
      var [activos, visitas] = await Promise.all([
        API.get('/registros-acceso/activos'),
        API.get('/visitas')
      ]);
      var visitasHtml, qrHtml;
      if (activos && activos.length) {
        visitasHtml = activos.map(function(v) {
          var apto = v.numeroApartamento ? 'Apto ' + v.numeroApartamento + ' · ' : '';
          return '<div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid var(--border)">' +
            '<span><small style="color:var(--text-muted)">' + apto + '</small>' + Utils.escapeHtml(v.nombreResidente || 'Visitante #' + v.idVisita) + '</span>' +
            '<span style="font-size:11px;color:var(--text-muted)">' + (v.horaEntrada ? v.horaEntrada.substring(11, 19) : '-') + '</span></div>';
        }).join('');
      } else {
        visitasHtml = '<span style="color:var(--text-muted)">No hay visitantes dentro</span>';
      }
      var pendientes = (visitas || []).filter(function(v) { return v.estado === 'PENDIENTE'; });
      if (pendientes.length) {
        qrHtml = pendientes.map(function(v) {
          var apto = v.numeroApartamento ? 'Apto ' + v.numeroApartamento + ' · ' : '';
          return '<div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid var(--border)">' +
            '<span><small style="color:var(--text-muted)">' + apto + '</small>' + Utils.escapeHtml(v.nombreVisitante || 'Visitante #' + v.idVisita) + '</span>' +
            '<span style="font-size:11px;color:var(--text-muted)">' + (v.fechaRegistro ? v.fechaRegistro.substring(11, 19) : '-') + '</span></div>';
        }).join('');
      } else {
        qrHtml = '<span style="color:var(--text-muted)">No hay QR pendientes</span>';
      }
      _modalData = { visitasHtml: visitasHtml, qrHtml: qrHtml };
      mostrarModal();
    } catch(e) {
      _modalData = { visitasHtml: 'Error al cargar', qrHtml: 'Error al cargar' };
      mostrarModal();
    }
  }

  async function notificarTodas() {
    if (!(await Utils.showConfirm('\u00bfEnviar notificaci\u00f3n a todos los apartamentos con multas pendientes?'))) return;
    var btn = document.getElementById('btn-notificar-todas');
    if (btn) btn.disabled = true;
    try {
      var res = await API.post('/multas/notificar-todas');
      Utils.showToast(res.mensaje, 'success');
    } catch(e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function notificarUna(idMulta) {
    try {
      var res = await API.post('/multas/' + idMulta + '/notificar');
      Utils.showToast(res.mensaje, 'success');
    } catch(e) { Utils.showToast(e.message, 'error'); }
  }

  async function verDetalleContrato(idContrato) {
    try {
      var contrato = await API.get('/contratos/' + idContrato);
      var residente = contrato.idResidente ? await API.get('/residentes/' + contrato.idResidente) : null;
      var cuotas = await API.get('/cuotas?idContrato=' + idContrato).catch(() => []);
      
      var cuotasPagadas = (cuotas || []).filter(function(c) { return c.estado === 'PAGADA'; }).length;
      var cuotasPendientes = (cuotas || []).filter(function(c) { return c.estado !== 'PAGADA'; }).length;
      var totalPagado = (cuotas || []).filter(function(c) { return c.estado === 'PAGADA'; }).reduce(function(sum, c) { return sum + (c.valorTotal || 0); }, 0);
      var totalPendiente = (cuotas || []).filter(function(c) { return c.estado !== 'PAGADA'; }).reduce(function(sum, c) { return sum + (c.valorTotal || 0); }, 0);
      
      var overlay = document.createElement('div');
      overlay.id = 'modal-detalle-contrato';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
      
      var html = '<div style="background:#fff;border-radius:16px;max-width:600px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
        '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, var(--navy-50) 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:var(--navy-500);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">description</span></div>' +
        '<div><h3 style="margin:0;font-size:18px;font-weight:600">Detalles del Contrato #' + contrato.idContrato + '</h3>' +
        '<p class="text-xs text-muted" style="margin:2px 0 0">Apartamento ' + Utils.escapeHtml(contrato.numeroApartamento || 'N/A') + '</p></div></div></div>' +
        '<div style="padding:24px">';
      
      if (residente) {
        html += '<div class="card" style="background:var(--navy-50);border:1px solid var(--navy-200);margin-bottom:16px">' +
          '<div style="font-weight:600;margin-bottom:8px;color:var(--navy-700)">Titular del Contrato</div>' +
          '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
          '<div><span class="text-muted">Nombre:</span><br><strong>' + Utils.escapeHtml((residente.nombres || '') + ' ' + (residente.apellidos || '')) + '</strong></div>' +
          '<div><span class="text-muted">Documento:</span><br><strong>' + Utils.escapeHtml(residente.numeroDocumento || '-') + '</strong></div>' +
          '<div><span class="text-muted">Teléfono:</span><br><strong>' + Utils.escapeHtml(residente.telefono || '-') + '</strong></div>' +
          '<div><span class="text-muted">Email:</span><br><strong style="word-break:break-all">' + Utils.escapeHtml(residente.email || '-') + '</strong></div>' +
          '</div></div>';
      }
      
      html += '<div class="form-row">' +
        '<div class="form-group"><label>Fecha Inicio</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDate(contrato.fechaInicio) + '</div></div>' +
        '<div class="form-group"><label>Fecha Fin</label><div class="form-control" style="background:#f9fafb;cursor:default">' + (contrato.fechaFin ? Utils.formatDate(contrato.fechaFin) : 'Indefinido') + '</div></div>' +
        '</div>' +
        '<div class="form-row">' +
        '<div class="form-group"><label>Valor Mensual</label><div class="form-control" style="background:#f9fafb;cursor:default;font-weight:600;color:var(--accent)">' + Utils.formatCurrency(contrato.valorMensual) + '</div></div>' +
        '<div class="form-group"><label>Estado</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.estadoBadge(contrato.estado) + '</div></div>' +
        '</div>';
      
      if (cuotas && cuotas.length) {
        html += '<div style="margin-top:20px;padding-top:20px;border-top:1px solid var(--border)">' +
          '<h4 style="margin:0 0 12px;font-size:14px;font-weight:600;color:var(--text)">Resumen de Pagos</h4>' +
          '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-bottom:16px">' +
          '<div class="stat-card" style="padding:12px;background:var(--success-50);border:1px solid var(--success-200)">' +
          '<div class="text-xs text-muted">Cuotas Pagadas</div>' +
          '<div class="text-xl font-bold" style="color:var(--success-700)">' + cuotasPagadas + '</div>' +
          '<div class="text-xs text-muted" style="margin-top:4px">' + Utils.formatCurrency(totalPagado) + '</div>' +
          '</div>' +
          '<div class="stat-card" style="padding:12px;background:var(--warn-50);border:1px solid var(--warn-200)">' +
          '<div class="text-xs text-muted">Cuotas Pendientes</div>' +
          '<div class="text-xl font-bold" style="color:var(--warn-700)">' + cuotasPendientes + '</div>' +
          '<div class="text-xs text-muted" style="margin-top:4px">' + Utils.formatCurrency(totalPendiente) + '</div>' +
          '</div></div></div>';
      }
      
      html += '</div>' +
        '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
        '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-contrato\').remove()">Cerrar</button>' +
        '</div></div>';
      
      overlay.innerHTML = html;
      document.body.appendChild(overlay);
    } catch(e) {
      Utils.showToast('Error al cargar detalles: ' + e.message, 'error');
    }
  }

  async function verDetalleMulta(idMulta) {
    try {
      var multa = await API.get('/multas/' + idMulta);
      
      var overlay = document.createElement('div');
      overlay.id = 'modal-detalle-multa';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
      
      var iconoTipo = multa.tipo === 'RUIDO' ? 'volume_up' : 'local_parking';
      var colorTipo = multa.tipo === 'RUIDO' ? 'var(--warn-500)' : 'var(--navy-500)';
      
      var html = '<div style="background:#fff;border-radius:16px;max-width:600px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
        '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, var(--navy-50) 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:' + colorTipo + ';display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">' + iconoTipo + '</span></div>' +
        '<div><h3 style="margin:0;font-size:18px;font-weight:600">Multa por ' + (multa.tipo === 'RUIDO' ? 'Ruido' : 'Parqueadero') + '</h3>' +
        '<p class="text-xs text-muted" style="margin:2px 0 0">Apartamento ' + Utils.escapeHtml(multa.numeroApartamento || 'N/A') + '</p></div></div></div>' +
        '<div style="padding:24px">';
      
      html += '<div class="card" style="background:var(--navy-50);border:1px solid var(--navy-200);margin-bottom:16px">' +
        '<div style="font-weight:600;margin-bottom:8px;color:var(--navy-700)">Residente Afectado</div>' +
        '<div style="font-size:14px"><strong>' + Utils.escapeHtml(multa.nombreResidente || 'Sin información') + '</strong></div>' +
        '</div>';
      
      html += '<div class="form-row">' +
        '<div class="form-group"><label>Monto</label><div class="form-control" style="background:#f9fafb;cursor:default;font-weight:600;font-size:18px;color:var(--danger)">' + Utils.formatCurrency(multa.monto) + '</div></div>' +
        '<div class="form-group"><label>Estado</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.estadoBadge(multa.estado) + '</div></div>' +
        '</div>';
      
      html += '<div class="form-row">' +
        '<div class="form-group"><label>Fecha de Generación</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDateTime(multa.fechaCreacion) + '</div></div>' +
        '<div class="form-group"><label>Generada por</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(multa.nombrePortero || 'Sistema') + '</div></div>' +
        '</div>';
      
      var descripcionHtml = '';
      if (multa.descripcion) {
        descripcionHtml += Utils.escapeHtml(multa.descripcion);
      }
      if (multa.tipo === 'RUIDO' && multa.fechaAvisoRuido) {
        var avisoFecha = 'Aviso de ruido generado el: ' + Utils.formatDateTime(multa.fechaAvisoRuido);
        if (descripcionHtml) {
          descripcionHtml += '\n\n---\n' + avisoFecha;
        } else {
          descripcionHtml = avisoFecha;
        }
      }
      if (descripcionHtml) {
        html += '<div class="form-group"><label>Descripción</label><div class="form-control" style="background:#f9fafb;cursor:default;min-height:60px;white-space:pre-wrap">' + descripcionHtml + '</div></div>';
      }
      
      // Si es multa de RUIDO y hay mensaje asociado (aviso de ruido)
      if (multa.tipo === 'RUIDO' && multa.fechaAvisoRuido) {
        html += '<div style="margin-top:16px;padding:12px;background:var(--warn-50);border:1px solid var(--warn-200);border-radius:8px">' +
          '<div style="font-weight:600;margin-bottom:6px;color:var(--warn-700);display:flex;align-items:center;gap:6px">' +
          '<span class="material-symbols-outlined" style="font-size:18px">notifications</span>' +
          'Aviso Previo de Ruido</div>' +
          '<div style="font-size:13px;color:var(--text-secondary)">Fecha y hora: ' + Utils.formatDateTime(multa.fechaAvisoRuido) + '</div>' +
          '</div>';
      }
      
      // Si es multa de PARQUEADERO y hay foto de evidencia
      if (multa.tipo === 'PARQUEADERO' && multa.fotoEvidencia) {
        html += '<div class="form-group" style="margin-top:16px"><label>Foto de Evidencia</label>' +
          '<img src="' + multa.fotoEvidencia + '" style="width:100%;max-height:300px;object-fit:contain;border-radius:8px;border:1px solid var(--border);cursor:pointer" onclick="window.open(this.src)" title="Click para ver en tamaño completo"></div>';
      }
      
      html += '</div>' +
        '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
        '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-detalle-multa\').remove()">Cerrar</button>' +
        '<button class="btn btn-primary" onclick="event.stopPropagation();Dash.notificarUna(' + multa.idMulta + ');document.getElementById(\'modal-detalle-multa\').remove()">Notificar al Residente</button>' +
        '</div></div>';
      
      overlay.innerHTML = html;
      document.body.appendChild(overlay);
    } catch(e) {
      Utils.showToast('Error al cargar detalles: ' + e.message, 'error');
    }
  }

  function _reset() { _loaded = false; _modalData = null; if (_modalOverlay) { _modalOverlay.remove(); _modalOverlay = null; } }

  return { toggleDetalles: toggleDetalles, notificarTodas: notificarTodas, notificarUna: notificarUna, verDetalleContrato: verDetalleContrato, verDetalleMulta: verDetalleMulta, _reset: _reset };
})();
