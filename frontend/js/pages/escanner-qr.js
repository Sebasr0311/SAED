const EscannerQR = (() => {
  var _stream = null;
  var _animFrame = null;
  var _scanning = false;
  var _captureStream = null;
  var _currentCodigoQr = null;
  var _currentIdVisita = null;
  var _notificarPollInterval = null;
  var _notificarState = null; // null | 'notifying' | 'waiting' | 'confirmed' | 'rejected'

  function cambiarTab(tabId) {
    detenerCamara();
    detenerCamaraCaptura();
    detenerPollNotificar();
    document.querySelectorAll('#qr-tabs .tab').forEach(function(t) { t.classList.remove('active'); });
    document.querySelectorAll('#qr-tabs ~ .tab-content').forEach(function(t) { t.classList.remove('active'); });
    var tab = document.querySelector('#qr-tabs .tab[data-tab="' + tabId.replace(/["\\]/g, '') + '"]');
    if (tab) tab.classList.add('active');
    document.getElementById(tabId).classList.add('active');
  }

  function renderValidar() {
    var container = document.getElementById('tab-qr-validar');
    if (!container) return;
    container.innerHTML =
      '<div class="card"><div class="card-title">Validar C\u00f3digo QR de Entrada</div>' +
      '<div id="qr-scanner-view">' +
      '<div class="qr-scanner-container" id="qr-scanner-box">' +
      '<video id="qr-video" autoplay playsinline muted disablepictureinpicture></video>' +
      '<canvas id="qr-canvas" hidden></canvas>' +
      '<div class="qr-scanner-overlay"><div class="scan-region"></div></div>' +
      '<div class="scanner-line"></div>' +
      '</div>' +
      '<div style="text-align:center;margin-top:12px">' +
      '<button class="btn btn-primary" id="btn-iniciar-camara" onclick="EscannerQR.iniciarCamara()">Iniciar C\u00e1mara</button>' +
      '<button class="btn btn-outline hidden" id="btn-detener-camara" onclick="EscannerQR.detenerCamara()" style="margin-left:8px">Detener</button>' +
      '</div>' +
      '<div class="form-group mt-16"><label>O ingrese el c\u00f3digo manualmente</label>' +
      '<div style="display:flex;gap:8px"><input type="text" id="qr-codigo-manual" class="form-control" placeholder="C\u00f3digo QR"><button class="btn btn-primary" onclick="EscannerQR.validarManual()">Validar</button></div></div>' +
      '</div>' +
      '<div id="qr-info" class="hidden mt-16"></div></div>';
  }

  function renderSalida() {
    var container = document.getElementById('tab-qr-salida');
    if (!container) return;
    container.innerHTML =
      '<div class="card"><div class="card-title">Registrar Salida</div>' +
      '<div class="table-container"><table class="data-table">' +
      '<thead><tr><th>ID Visita</th><th>Residente</th><th>Ingreso</th><th>Parqueadero</th><th>Accion</th></tr></thead>' +
      '<tbody id="tbody-ingresados"></tbody></table></div>' +
      '<div id="qr-salida-info" class="hidden mt-16"></div></div>';
    cargarIngresados();
    actualizarBadgesSalida();
  }

  async function actualizarBadgesSalida() {
    var badgeVisitas = document.getElementById('badge-visitas-activas');
    if (!badgeVisitas) return;
    try {
      var visitas = await API.get('/registros-acceso/activos');
      var count = visitas ? visitas.length : 0;
      if (count > 0) {
        badgeVisitas.textContent = count > 99 ? '99+' : count;
        badgeVisitas.classList.remove('hidden');
      } else {
        badgeVisitas.classList.add('hidden');
      }
    } catch (e) { 
      badgeVisitas.classList.add('hidden'); 
    }
  }

  function renderParqueaderos() {
    var container = document.getElementById('tab-qr-parqueaderos');
    if (!container) return;
    container.innerHTML =
      '<div class="card"><div class="card-title">Estado de Parqueaderos</div>' +
      '<div class="table-container"><table class="data-table">' +
      '<thead><tr><th>Codigo</th><th>Tipo</th><th>Estado</th><th>Visitante</th></tr></thead>' +
      '<tbody id="tbody-parq-qr"></tbody></table></div></div>';
    cargarParqueaderos();
  }

  /* ─── Camera ─── */

  async function iniciarCamara() {
    var video = document.getElementById('qr-video');
    var btnStart = document.getElementById('btn-iniciar-camara');
    var btnStop = document.getElementById('btn-detener-camara');
    if (!video) return;
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        Utils.showToast('Su navegador no soporta la c\u00e1mara. Use el campo manual.', 'warn');
        if (btnStart) btnStart.textContent = 'No disponible';
        return;
      }
      _stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false
      });
      video.srcObject = _stream;
      await video.play();
      if (btnStart) btnStart.classList.add('hidden');
      if (btnStop) btnStop.classList.remove('hidden');
      _scanning = true;
      escanearFrame();
    } catch (e) {
      console.warn('Error al abrir c\u00e1mara:', e);
      var msg = 'No se pudo abrir la camara. ';
      if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError') {
        msg += 'Permiso denegado. Vaya a configuraci\u00f3n del navegador y permita el acceso a la c\u00e1mara.';
      } else if (e.name === 'NotFoundError') {
        msg += 'No se encontr\u00f3 una c\u00e1mara en el dispositivo.';
      } else if (e.name === 'NotReadableError') {
        msg += 'La c\u00e1mara est\u00e1 siendo usada por otra aplicaci\u00f3n.';
      } else if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
        msg += 'Los navegadores m\u00f3viles requieren HTTPS para la c\u00e1mara. Use el campo manual o acceda desde localhost.';
      } else {
        msg += 'Use el campo manual para ingresar el c\u00f3digo.';
      }
      Utils.showToast(msg, 'error');
      if (btnStart) btnStart.textContent = 'Reintentar';
    }
  }

  function detenerCamara() {
    _scanning = false;
    if (_animFrame) { cancelAnimationFrame(_animFrame); _animFrame = null; }
    if (_stream) {
      _stream.getTracks().forEach(function(t) { t.stop(); });
      _stream = null;
    }
    var video = document.getElementById('qr-video');
    if (video) video.srcObject = null;
    var btnStart = document.getElementById('btn-iniciar-camara');
    var btnStop = document.getElementById('btn-detener-camara');
    if (btnStart) btnStart.classList.remove('hidden');
    if (btnStop) btnStop.classList.add('hidden');
  }

  function escanearFrame() {
    if (!_scanning) return;
    var video = document.getElementById('qr-video');
    var canvas = document.getElementById('qr-canvas');
    if (!video || !canvas || video.readyState < 2) {
      _animFrame = requestAnimationFrame(escanearFrame);
      return;
    }
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    var imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    var code = jsQR(imageData.data, imageData.width, imageData.height, { inversionAttempts: 'dontInvert' });
    if (code && code.data && code.data.length >= 5) {
      detenerCamara();
      procesarCodigo(code.data);
      return;
    }
    _animFrame = requestAnimationFrame(escanearFrame);
  }

  /* ─── Camera capture for "Notificar" ─── */

  function detenerCamaraCaptura() {
    if (_captureStream) {
      _captureStream.getTracks().forEach(function(t) { t.stop(); });
      _captureStream = null;
    }
    var video = document.getElementById('captura-video');
    if (video) video.srcObject = null;
    var overlay = document.getElementById('captura-overlay');
    if (overlay) overlay.remove();
  }

  function abrirCamaraCaptura() {
    var overlay = document.createElement('div');
    overlay.id = 'captura-overlay';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.8);z-index:15000;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:20px';
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:12px;max-width:420px;width:100%;padding:20px;text-align:center">' +
      '<h3 style="margin:0 0 8px;font-size:16px;color:var(--text)">Capturar Foto del Visitante</h3>' +
      '<p class="text-sm text-muted" style="margin-bottom:12px">Tome una foto para que el residente identifique al visitante</p>' +
      '<video id="captura-video" autoplay playsinline muted style="width:100%;max-height:300px;border-radius:8px;background:#000;object-fit:cover"></video>' +
      '<canvas id="captura-canvas" hidden></canvas>' +
      '<div id="captura-preview" style="display:none;margin-top:8px"><img id="captura-img" style="max-width:100%;max-height:300px;border-radius:8px;border:2px solid var(--accent)"></div>' +
      '<div style="display:flex;gap:8px;margin-top:12px;justify-content:center">' +
      '<button class="btn btn-primary" id="btn-capturar" onclick="EscannerQR.capturarFoto()">Capturar Foto</button>' +
      '<button class="btn btn-outline hidden" id="btn-retomar" onclick="EscannerQR.retomarCaptura()">Retomar</button>' +
      '<button class="btn btn-accent hidden" id="btn-confirmar-foto" onclick="EscannerQR.confirmarFoto()">Usar Foto</button>' +
      '</div>' +
      '<button class="btn btn-ghost btn-sm" style="margin-top:12px" onclick="EscannerQR.detenerCamaraCaptura()">Cancelar</button></div>';
    document.body.appendChild(overlay);
    iniciarCaptura();
  }

  async function iniciarCaptura() {
    var video = document.getElementById('captura-video');
    if (!video) return;
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        Utils.showToast('Su navegador no soporta la c\u00e1mara.', 'warn');
        detenerCamaraCaptura();
        return;
      }
      _captureStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user' },
        audio: false
      });
      video.srcObject = _captureStream;
      await video.play();
    } catch (e) {
      console.warn('Error al abrir c\u00e1mara de captura:', e);
      var msg = 'No se pudo abrir la camara. ';
      if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError') {
        msg += 'Permiso denegado. Vaya a configuraci\u00f3n del navegador y permita el acceso a la c\u00e1mara.';
      } else if (e.name === 'NotFoundError') {
        msg += 'No se encontr\u00f3 una c\u00e1mara frontal.';
      } else if (e.name === 'NotReadableError') {
        msg += 'La c\u00e1mara est\u00e1 siendo usada por otra aplicaci\u00f3n.';
      } else if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
        msg += 'Los navegadores m\u00f3viles requieren HTTPS para la c\u00e1mara.';
      }
      Utils.showToast(msg, 'error');
      detenerCamaraCaptura();
    }
  }

  var _fotoCapturaData = null;

  function capturarFoto() {
    var video = document.getElementById('captura-video');
    var canvas = document.getElementById('captura-canvas');
    var img = document.getElementById('captura-img');
    var preview = document.getElementById('captura-preview');
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    _fotoCapturaData = canvas.toDataURL('image/jpeg', 0.80);
    if (img) img.src = _fotoCapturaData;
    if (preview) preview.style.display = 'block';
    if (video) video.style.display = 'none';
    document.getElementById('btn-capturar').classList.add('hidden');
    document.getElementById('btn-retomar').classList.remove('hidden');
    document.getElementById('btn-confirmar-foto').classList.remove('hidden');
  }

  function retomarCaptura() {
    _fotoCapturaData = null;
    var video = document.getElementById('captura-video');
    var preview = document.getElementById('captura-preview');
    if (video) video.style.display = 'block';
    if (preview) preview.style.display = 'none';
    document.getElementById('btn-capturar').classList.remove('hidden');
    document.getElementById('btn-retomar').classList.add('hidden');
    document.getElementById('btn-confirmar-foto').classList.add('hidden');
  }

  async function confirmarFoto() {
    if (!_fotoCapturaData || !_currentCodigoQr) return;
    detenerCamaraCaptura();
    await notificarVisita(_currentCodigoQr, _fotoCapturaData);
    _fotoCapturaData = null;
  }

  /* ─── Notificar flow ─── */

  function detenerPollNotificar() {
    if (_notificarPollInterval) { clearInterval(_notificarPollInterval); _notificarPollInterval = null; }
  }

  async function notificarVisita(codigoQr, fotoCaptura) {
    _notificarState = 'notifying';
    actualizarBotonesAccion();
    try {
      var res = await API.post('/qr/notificar', { codigoQr: codigoQr, fotoCaptura: fotoCaptura });
      _currentIdVisita = res.idVisita;
      _notificarState = 'waiting';
      actualizarBotonesAccion();
      iniciarPollNotificar();
    } catch (e) {
      _notificarState = null;
      actualizarBotonesAccion();
      Utils.showToast('Error al notificar: ' + e.message, 'error');
    }
  }

  function iniciarPollNotificar() {
    detenerPollNotificar();
    _notificarPollInterval = setInterval(pollResultadoNotificar, 2000);
    pollResultadoNotificar();
  }

  async function pollResultadoNotificar() {
    if (!_currentIdVisita) return;
    try {
      var res = await API.get('/buzon/resultado-notificar?idVisita=' + _currentIdVisita);
      if (res.confirmado === 1) {
        _notificarState = 'confirmed';
        detenerPollNotificar();
        Utils.showToast('Residente confirm\u00f3 el acceso', 'success');
        actualizarBotonesAccion();
      } else if (res.confirmado === 0) {
        _notificarState = 'rejected';
        detenerPollNotificar();
        Utils.showToast('Residente rechaz\u00f3 el acceso', 'warn');
        actualizarBotonesAccion();
      }
    } catch(e) { /* silencioso */ }
  }

  /* ─── Modal Parqueadero Asignado ─── */

  function mostrarModalParqueaderoAsignado(codigoParqueadero, medioTransporte) {
    var overlay = document.createElement('div');
    overlay.id = 'modal-parq-asignado';
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.75);backdrop-filter:blur(6px);z-index:20000;display:flex;align-items:center;justify-content:center;padding:20px;animation:fadeIn 0.2s';
    
    var iconVehiculo = medioTransporte === 'CARRO' ? 'directions_car' : (medioTransporte === 'MOTO' ? 'two_wheeler' : 'local_parking');
    var colorVehiculo = medioTransporte === 'CARRO' ? '#3b82f6' : (medioTransporte === 'MOTO' ? '#f97316' : '#10b981');
    
    overlay.innerHTML =
      '<div style="background:#fff;border-radius:20px;max-width:480px;width:100%;text-align:center;padding:40px 32px;box-shadow:0 25px 70px rgba(0,0,0,0.35);animation:scaleIn 0.3s cubic-bezier(0.34,1.56,0.64,1)">' +
      '<div style="width:100px;height:100px;margin:0 auto 24px;border-radius:50%;background:linear-gradient(135deg,' + colorVehiculo + ' 0%,color-mix(in srgb,' + colorVehiculo + ' 70%,#000) 100%);display:flex;align-items:center;justify-content:center;box-shadow:0 12px 32px ' + colorVehiculo + '40;animation:pulse 2s infinite">' +
      '<span class="material-symbols-outlined" style="font-size:56px;color:#fff">' + iconVehiculo + '</span></div>' +
      '<h2 style="margin:0 0 12px;font-size:24px;font-weight:700;color:var(--navy-700)">¡Entrada Registrada!</h2>' +
      '<p style="margin:0 0 32px;font-size:15px;color:var(--text-secondary)">Indique al visitante el parqueadero asignado</p>' +
      '<div style="padding:28px;background:linear-gradient(135deg,var(--navy-50) 0%,var(--surface) 100%);border-radius:16px;border:2px solid var(--navy-200);margin-bottom:28px">' +
      '<p style="margin:0 0 8px;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:0.8px;color:var(--navy-500)">Parqueadero Asignado</p>' +
      '<p style="margin:0;font-size:42px;font-weight:900;color:var(--navy-700);text-shadow:0 2px 4px rgba(0,0,0,0.08)">' + Utils.escapeHtml(codigoParqueadero) + '</p></div>' +
      '<button class="btn btn-primary btn-lg" onclick="document.getElementById(\'modal-parq-asignado\').remove()" style="width:100%;font-size:16px;padding:16px;font-weight:600">Entendido</button></div>';
    
    document.body.appendChild(overlay);
    
    // Auto-cerrar después de 10 segundos
    setTimeout(function() {
      var modal = document.getElementById('modal-parq-asignado');
      if (modal) modal.remove();
    }, 10000);
    
    // Cerrar con click fuera
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) overlay.remove();
    });
  }

  /* ─── Validation (detailed card) ─── */

  function extraerCampo(obj) {
    for (var i = 0; i < arguments.length; i++) {
      var keys = arguments[i];
      if (typeof keys === 'string') keys = [keys];
      for (var k = 0; k < keys.length; k++) { if (obj[keys[k]] != null && obj[keys[k]] !== '') return obj[keys[k]]; }
    }
    return null;
  }

  function armarCardInfo(res, codigoQr) {
    var resApt = extraerCampo(res, ['numeroApartamento','apartamentoNumero','apartamento']);
    if (resApt && typeof resApt === 'object') resApt = resApt.numero || resApt.numeroApartamento || JSON.stringify(resApt);
    var docVisitante = extraerCampo(res, ['documentoVisitante','numeroDocumento','documento']);
    var defaultTransport = res.placaVehiculo ? 'CARRO' : 'A_PIE';
    var plateDisplay = defaultTransport === 'CARRO' || defaultTransport === 'MOTO' ? 'block' : 'none';
    var descDisplay = defaultTransport === 'OTRO' || defaultTransport === 'BICICLETA' ? 'block' : 'none';

    var html = '<div class="card" style="border-left:4px solid var(--accent)">' +
      '<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">' +
      '<span style="background:var(--accent);color:#fff;border-radius:50%;width:28px;height:28px;display:flex;align-items:center;justify-content:center;font-size:16px">&#10003;</span>' +
      '<span style="font-weight:700;font-size:16px;color:var(--accent)">Visita V\u00e1lida</span></div>' +
      '<div style="margin-bottom:10px"><div class="section-title" style="font-weight:600;font-size:14px;margin-bottom:6px;color:var(--text)">Datos del Residente</div>' +
      '<div class="info-row"><span class="info-label">Nombre:</span><span class="info-value">' + Utils.escapeHtml(res.nombreResidente || '-') + '</span></div>' +
      (resApt ? '<div class="info-row"><span class="info-label">Apartamento:</span><span class="info-value">' + Utils.escapeHtml(String(resApt)) + '</span></div>' : '') +
      '<div class="info-row"><span class="info-label">Generado:</span><span class="info-value">' + (res.fechaCreacion || res.fechaRegistro ? Utils.formatDateTime(res.fechaCreacion || res.fechaRegistro) : '-') + '</span></div>' +
      '<div class="info-row"><span class="info-label">Vence:</span><span class="info-value">' + Utils.formatDateTime(res.fechaExpiracion) + '</span></div></div>' +
      '<hr style="border:none;border-top:1px solid var(--border);margin:10px 0">' +
      '<div style="margin-bottom:10px"><div class="section-title" style="font-weight:600;font-size:14px;margin-bottom:6px;color:var(--text)">Visitante</div>' +
      '<div class="info-row"><span class="info-label">Nombre:</span><span class="info-value">' + Utils.escapeHtml(res.nombreVisitante || '-') + '</span></div>' +
      (docVisitante ? '<div class="info-row"><span class="info-label">Documento:</span><span class="info-value">' + Utils.escapeHtml(String(docVisitante)) + '</span></div>' : '') +
      '<div class="info-row"><span class="info-label">Personas:</span><span class="info-value">' + (res.cantidadPersonas || 1) + '</span></div></div>' +
      '<hr style="border:none;border-top:1px solid var(--border);margin:10px 0">' +
      '<div><div class="section-title" style="font-weight:600;font-size:14px;margin-bottom:8px;color:var(--text)">Registrar Ingreso</div>' +
      '<div class="form-row">' +
      '<div class="form-group"><label>\u00bfEn qu\u00e9 viene?</label>' +
      '<select id="qr-medio-transporte" class="form-control">' +
      '<option value="A_PIE"' + (defaultTransport === 'A_PIE' ? ' selected' : '') + '>A pie</option>' +
      '<option value="CARRO"' + (defaultTransport === 'CARRO' ? ' selected' : '') + '>Carro</option>' +
      '<option value="MOTO"' + (defaultTransport === 'MOTO' ? ' selected' : '') + '>Moto</option>' +
      '<option value="BICICLETA"' + (defaultTransport === 'BICICLETA' ? ' selected' : '') + '>Bicicleta</option>' +
      '<option value="OTRO"' + (defaultTransport === 'OTRO' ? ' selected' : '') + '>Otro</option></select></div>' +
      '<div class="form-group" id="qr-placa-group" style="display:' + plateDisplay + '">' +
      '<label id="qr-placa-label">Placa</label>' +
      '<input type="text" id="qr-placa" class="form-control" maxlength="10" value="' + Utils.escapeHtml(res.placaVehiculo || '') + '" placeholder="Ej: ABC 123 o ABC 12D" style="text-transform:uppercase">' +
      '<small id="qr-placa-error" class="text-danger" style="display:none;margin-top:4px;font-size:12px"></small></div>' +
      '<div class="form-group" id="qr-desc-group" style="display:' + descDisplay + '">' +
      '<label id="qr-desc-label">Descripci\u00f3n</label>' +
      '<input type="text" id="qr-descripcion" class="form-control" maxlength="100" placeholder="Ej: Marca, color, modelo"></div></div>' +
      (res.notas ? '<div class="mt-8" style="padding:8px 12px;background:#fff8e1;border-radius:6px;font-size:13px;color:#856404;border:1px solid #ffe082"><strong>Notas del residente:</strong> ' + Utils.escapeHtml(res.notas) + '</div>' : '') +
      '<div id="qr-acciones-flow" style="display:flex;gap:8px;margin-top:14px;flex-wrap:wrap">' +
      '<button class="btn btn-primary" id="btn-notificar-visita" onclick="EscannerQR.notificarClick()">Notificar visita</button>' +
      '<button class="btn btn-accent hidden" id="btn-registrar-entrada" onclick="EscannerQR.registrarEntrada(\'' + codigoQr.replace(/['\\]/g, '') + '\')">Registrar Entrada</button>' +
      '<button class="btn btn-outline" onclick="EscannerQR.reiniciarEscanner()">Escanear otro</button></div>' +
      '<div id="qr-flow-status" class="mt-8"></div></div></div>';
    return html;
  }

  function actualizarBotonesAccion() {
    var notificarBtn = document.getElementById('btn-notificar-visita');
    var entradaBtn = document.getElementById('btn-registrar-entrada');
    var statusEl = document.getElementById('qr-flow-status');
    if (!notificarBtn && !entradaBtn) return;
    if (_notificarState === null || !_notificarState) {
      if (notificarBtn) { notificarBtn.classList.remove('hidden'); notificarBtn.disabled = false; }
      if (entradaBtn) entradaBtn.classList.add('hidden');
      if (statusEl) statusEl.innerHTML = '';
    } else if (_notificarState === 'notifying') {
      if (notificarBtn) { notificarBtn.classList.add('hidden'); }
      if (entradaBtn) entradaBtn.classList.add('hidden');
      if (statusEl) statusEl.innerHTML = '<p class="text-sm" style="color:var(--text-muted)">Notificando al residente...</p>';
    } else if (_notificarState === 'waiting') {
      if (notificarBtn) notificarBtn.classList.add('hidden');
      if (entradaBtn) entradaBtn.classList.add('hidden');
      if (statusEl) statusEl.innerHTML = '<div style="display:flex;align-items:center;gap:8px"><span class="spinner-sm"></span><span class="text-sm" style="color:var(--text-muted)">Esperando confirmaci\u00f3n del residente...</span></div>';
    } else if (_notificarState === 'confirmed') {
      if (notificarBtn) notificarBtn.classList.add('hidden');
      if (entradaBtn) { entradaBtn.classList.remove('hidden'); entradaBtn.disabled = false; }
      if (statusEl) statusEl.innerHTML = '<p class="text-sm" style="color:var(--accent);font-weight:600">&#10003; Residente confirm\u00f3 el acceso. Puede registrar la entrada.</p>';
    } else if (_notificarState === 'rejected') {
      if (notificarBtn) notificarBtn.classList.add('hidden');
      if (entradaBtn) entradaBtn.classList.add('hidden');
      if (statusEl) statusEl.innerHTML = '<p class="text-sm" style="color:var(--danger);font-weight:600">&#10007; El residente rechaz\u00f3 el acceso.</p>';
    }
  }

  function notificarClick() {
    var btn = document.getElementById('btn-notificar-visita');
    if (btn) btn.disabled = true;
    abrirCamaraCaptura();
  }

  async function procesarCodigo(codigo) {
    _currentCodigoQr = codigo;
    _currentIdVisita = null;
    detenerPollNotificar();
    _notificarState = null;
    var info = document.getElementById('qr-info');
    var manualInput = document.getElementById('qr-codigo-manual');
    if (manualInput) manualInput.value = codigo;
    if (!info) return;
    info.innerHTML = Utils.loadingSpinner();
    info.classList.remove('hidden');
    try {
      var res = await API.post('/qr/validar', { codigoQr: codigo });
      var codigoQr = res.codigoQr || codigo;
      info.innerHTML = armarCardInfo(res, codigoQr);
      var sel = document.getElementById('qr-medio-transporte');
      if (sel) {
        sel.onchange = function() {
          actualizarCamposVehiculo();
        };
      }
      // Agregar validación en tiempo real para placas
      var placaInput = document.getElementById('qr-placa');
      if (placaInput) {
        placaInput.addEventListener('input', function(e) {
          e.target.value = e.target.value.toUpperCase().replace(/[^A-Z0-9\s]/g, '');
          validarPlacaInput();
        });
        placaInput.addEventListener('blur', validarPlacaInput);
      }
    } catch (e) {
      info.innerHTML = '<div class="card" style="border-left:4px solid var(--danger)"><p style="color:var(--danger)">' + Utils.escapeHtml(e.message) + '</p>' +
        '<button class="btn btn-outline mt-8" onclick="EscannerQR.reiniciarEscanner()">Intentar de nuevo</button></div>';
    }
  }

  function actualizarCamposVehiculo() {
    var sel = document.getElementById('qr-medio-transporte');
    if (!sel) return;
    var pg = document.getElementById('qr-placa-group');
    var dg = document.getElementById('qr-desc-group');
    var placaLabel = document.getElementById('qr-placa-label');
    var descLabel = document.getElementById('qr-desc-label');
    var descInput = document.getElementById('qr-descripcion');
    var placaError = document.getElementById('qr-placa-error');
    
    if (placaError) placaError.style.display = 'none';
    
    if (sel.value === 'CARRO') {
      if (pg) pg.style.display = 'block';
      if (dg) dg.style.display = 'none';
      if (placaLabel) placaLabel.textContent = 'Placa (AAA 123)';
    } else if (sel.value === 'MOTO') {
      if (pg) pg.style.display = 'block';
      if (dg) dg.style.display = 'none';
      if (placaLabel) placaLabel.textContent = 'Placa (AAA 12D)';
    } else if (sel.value === 'BICICLETA') {
      if (pg) pg.style.display = 'none';
      if (dg) dg.style.display = 'block';
      if (descLabel) descLabel.textContent = 'Descripción de la bicicleta';
      if (descInput) descInput.placeholder = 'Ej: Bicicleta de montaña roja, marca Trek';
    } else if (sel.value === 'OTRO') {
      if (pg) pg.style.display = 'none';
      if (dg) dg.style.display = 'block';
      if (descLabel) descLabel.textContent = 'Descripción';
      if (descInput) descInput.placeholder = 'Ej: Marca, color, modelo';
    } else {
      if (pg) pg.style.display = 'none';
      if (dg) dg.style.display = 'none';
    }
  }

  function validarPlacaColombia(placa, tipoVehiculo) {
    if (!placa) return { valido: false, mensaje: 'Placa requerida' };
    
    // Remover espacios para validación
    var placaSinEspacios = placa.replace(/\s+/g, '');
    
    if (tipoVehiculo === 'CARRO') {
      // Formato: 3 letras + 3 números (AAA123 o AAA 123)
      var regexCarro = /^[A-Z]{3}\s?\d{3}$/;
      if (!regexCarro.test(placa.trim())) {
        return { 
          valido: false, 
          mensaje: 'Formato incorrecto. Use: 3 letras + 3 números (Ej: ABC 123)' 
        };
      }
    } else if (tipoVehiculo === 'MOTO') {
      // Formato: 3 letras + 2 números + 1 letra (AAA12D o AAA 12D)
      var regexMoto = /^[A-Z]{3}\s?\d{2}[A-Z]$/;
      if (!regexMoto.test(placa.trim())) {
        return { 
          valido: false, 
          mensaje: 'Formato incorrecto. Use: 3 letras + 2 números + 1 letra (Ej: ABC 12D)' 
        };
      }
    }
    
    return { valido: true, mensaje: '' };
  }

  function validarPlacaInput() {
    var sel = document.getElementById('qr-medio-transporte');
    var placaInput = document.getElementById('qr-placa');
    var placaError = document.getElementById('qr-placa-error');
    
    if (!sel || !placaInput || !placaError) return true;
    if (sel.value !== 'CARRO' && sel.value !== 'MOTO') return true;
    
    var placa = placaInput.value.trim();
    if (!placa) {
      placaError.style.display = 'none';
      placaInput.classList.remove('is-invalid');
      return true; // Permitir vacío, validar en submit
    }
    
    var resultado = validarPlacaColombia(placa, sel.value);
    if (!resultado.valido) {
      placaError.textContent = resultado.mensaje;
      placaError.style.display = 'block';
      placaInput.classList.add('is-invalid');
      return false;
    } else {
      placaError.style.display = 'none';
      placaInput.classList.remove('is-invalid');
      return true;
    }
  }

  async function validarManual() {
    var input = document.getElementById('qr-codigo-manual');
    if (!input) return;
    var codigo = input.value.trim();
    input.classList.remove('is-invalid');
    if (!codigo) { input.classList.add('is-invalid'); Utils.showToast('Ingrese un c\u00f3digo QR', 'warn'); return; }
    if (codigo.length < 5) { input.classList.add('is-invalid'); Utils.showToast('El c\u00f3digo debe tener al menos 5 caracteres', 'warn'); return; }
    procesarCodigo(codigo);
  }

  function reiniciarEscanner() {
    detenerPollNotificar();
    detenerCamara();
    _currentCodigoQr = null;
    _currentIdVisita = null;
    _notificarState = null;
    var info = document.getElementById('qr-info');
    if (info) { info.classList.add('hidden'); info.innerHTML = ''; }
    var manualInput = document.getElementById('qr-codigo-manual');
    if (manualInput) manualInput.value = '';
    var btnStart = document.getElementById('btn-iniciar-camara');
    if (btnStart) btnStart.classList.remove('hidden');
  }

  async function registrarEntrada(codigo) {
    var btn = document.getElementById('btn-registrar-entrada');
    if (btn && btn.disabled) return; // Protección contra doble-click
    if (btn) btn.disabled = true;
    var medioTransporte = document.getElementById('qr-medio-transporte') ? document.getElementById('qr-medio-transporte').value : 'A_PIE';
    
    try {
      // Validar placa para CARRO y MOTO
      if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') {
        var placaInput = document.getElementById('qr-placa');
        var placa = placaInput ? placaInput.value.trim() : '';
        
        if (placa) {
          var validacion = validarPlacaColombia(placa, medioTransporte);
          if (!validacion.valido) {
            Utils.showToast(validacion.mensaje, 'error');
            if (btn) btn.disabled = false;
            return;
          }
        }
      }
      
      // Validar descripción para BICICLETA
      if (medioTransporte === 'BICICLETA') {
        var descInput = document.getElementById('qr-descripcion');
        var desc = descInput ? descInput.value.trim() : '';
        if (!desc) {
          Utils.showToast('Por favor ingrese la descripción de la bicicleta', 'warn');
          if (descInput) descInput.classList.add('is-invalid');
          if (btn) btn.disabled = false;
          return;
        }
      }
      
      var payload = { codigoQr: codigo, medioTransporte: medioTransporte };
      if (medioTransporte === 'OTRO' || medioTransporte === 'BICICLETA') {
        var desc = document.getElementById('qr-descripcion') ? document.getElementById('qr-descripcion').value.trim() : '';
        if (desc) payload.descripcion = desc;
      } else if (medioTransporte !== 'A_PIE') {
        var placa = document.getElementById('qr-placa') ? document.getElementById('qr-placa').value.trim() : '';
        if (placa) payload.placa = placa;
      }
      var res = await API.post('/qr/entrada', payload);
      var msg = 'Entrada registrada exitosamente';
      
      // Mostrar modal grande con parqueadero asignado
      if (res.parqueadero) {
        mostrarModalParqueaderoAsignado(res.parqueadero, medioTransporte);
      } else if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') {
        Utils.showToast('Entrada registrada. ADVERTENCIA: No hay parqueaderos disponibles para visitantes.', 'warn');
      } else {
        Utils.showToast(msg, 'success');
      }
      reiniciarEscanner();
      await cargarIngresados();
    } catch (e) {
      Utils.showToast(e.message, 'error');
      if (btn) btn.disabled = false; // Solo re-habilitar si hubo error
    }
  }

  async function cargarIngresados() {
    var tbody = document.getElementById('tbody-ingresados');
    if (!tbody) return;
    try {
      var visitas = await API.get('/registros-acceso/activos');
      if (!visitas.length) { tbody.innerHTML = '<tr><td colspan="5" class="text-center" style="color:var(--text-secondary);padding:20px">No hay visitantes dentro del edificio</td></tr>'; return; }
      tbody.innerHTML = visitas.map(function(v) {
        var parq = v.codigoParqueadero ? '<span class="badge badge-activo">' + Utils.escapeHtml(v.codigoParqueadero) + '</span>' : '<span class="text-muted">-</span>';
        return '<tr><td>' + v.idVisita + '</td><td>' + Utils.escapeHtml(v.nombreResidente || '-') + '</td><td>' + Utils.formatDateTime(v.horaEntrada) + '</td><td>' + parq + '</td>' +
          '<td><button class="btn btn-warn btn-sm" onclick="EscannerQR.registrarSalida(' + v.idAcceso + ')">Registrar Salida</button></td></tr>';
      }).join('');
    } catch (e) { tbody.innerHTML = '<tr><td colspan="5" class="text-center" style="color:var(--text-secondary)">Error al cargar</td></tr>'; }
    actualizarBadgesSalida();
  }

  async function registrarSalida(idAcceso) {
    var btn = event ? event.target : document.querySelector('button[onclick*="registrarSalida(' + idAcceso + ')"]');
    if (btn && btn.disabled) return;
    if (btn) btn.disabled = true;
    try {
      await API.post('/registros-acceso/' + idAcceso + '/salida');
      Utils.showToast('Salida registrada', 'success');
      await cargarIngresados();
      cargarParqueaderos();
      actualizarBadgesSalida();
    } catch (e) {
      Utils.showToast(e.message, 'error');
      if (btn) btn.disabled = false;
    }
  }

  async function cargarParqueaderos() {
    var tbody = document.getElementById('tbody-parq-qr');
    if (!tbody) return;
    try {
      var parqs = await API.get('/parqueaderos');
      tbody.innerHTML = parqs.map(function(p) {
        return '<tr><td>' + Utils.escapeHtml(p.codigo || '') + '</td><td>' + Utils.escapeHtml(p.tipo || '') + '</td><td>' + Utils.estadoBadge(p.estado || 'DISPONIBLE') + '</td><td>' + (p.esVisitante ? 'Si' : 'No') + '</td></tr>';
      }).join('');
    } catch (e) { tbody.innerHTML = '<tr><td colspan="4">Error al cargar</td></tr>'; }
  }

  function limpiar() {
    detenerPollNotificar();
    detenerCamara();
    detenerCamaraCaptura();
  }

  return {
    cambiarTab: cambiarTab, renderValidar: renderValidar, renderSalida: renderSalida,
    renderParqueaderos: renderParqueaderos,
    iniciarCamara: iniciarCamara, detenerCamara: detenerCamara,
    validarManual: validarManual, reiniciarEscanner: reiniciarEscanner,
    registrarEntrada: registrarEntrada, registrarSalida: registrarSalida,
    cargarIngresados: cargarIngresados, cargarParqueaderos: cargarParqueaderos,
    notificarClick: notificarClick, capturarFoto: capturarFoto,
    retomarCaptura: retomarCaptura, confirmarFoto: confirmarFoto,
    detenerCamaraCaptura: detenerCamaraCaptura, limpiar: limpiar
  };
})();

Router.register('escanner-qr', {
  html: document.getElementById('tpl-escanner-qr').innerHTML,
  js: function() { document.getElementById('page-title').textContent = 'Esc\u00e1ner QR'; EscannerQR.renderValidar(); EscannerQR.renderSalida(); EscannerQR.renderParqueaderos(); },
  onLeave: EscannerQR.limpiar
});
