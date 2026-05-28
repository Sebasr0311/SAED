const Paquetes = (() => {
  var residentes = [];
  var fotoBase64 = null;
  var _stream = null;
  var _paquetesHistoricos = []; // Almacenar paquetes para el modal de detalle

  function onResidenteChange() {
    var idRes = document.getElementById('paq-residente').value;
    var container = document.getElementById('paq-lista');
    if (!idRes) { container.innerHTML = '<p style="color:var(--text-secondary)">Seleccione un apartamento para ver sus paquetes</p>'; return; }
    var res = residentes.find(function(r) { return r.id == idRes; });
    var aptId = res && res.idApartamento;
    if (!aptId) { container.innerHTML = '<p style="color:var(--text-secondary)">El residente no tiene apartamento asignado</p>'; return; }
    container.innerHTML = Utils.loadingSpinner();
    API.get('/buzon?idApartamento=' + aptId).then(function(msgs) {
      var paquetes = (msgs || []).filter(function(m) { return m.tipo === 'PAQUETE'; });
      _paquetesHistoricos = paquetes; // Guardar para uso posterior
      if (!paquetes.length) { container.innerHTML = Utils.emptyState('Sin paquetes registrados'); return; }
      container.innerHTML = '<div style="display:flex;flex-direction:column;gap:8px">' + paquetes.map(function(p, idx) {
        var estado = p.entregado ? '<span class="badge badge-success">Entregado</span>' : '<span class="badge badge-warning">Pendiente</span>';
        var imgHtml = p.fotoCaptura ? '<img src="' + p.fotoCaptura + '" style="width:60px;height:60px;object-fit:cover;border-radius:6px">' : '<div style="width:60px;height:60px;border-radius:6px;background:color-mix(in srgb,#f97316 10%,transparent);display:flex;align-items:center;justify-content:center"><span class="material-symbols-outlined" style="font-size:28px;color:#f97316">inventory_2</span></div>';
        return '<div style="display:flex;gap:10px;align-items:center;padding:8px;border:1px solid var(--border-color);border-radius:8px;cursor:pointer;transition:all 0.2s" onclick="Paquetes.verDetallePaquete(' + idx + ')" onmouseenter="this.style.background=\'var(--navy-50)\';this.style.borderColor=\'var(--navy-200)\'" onmouseleave="this.style.background=\'\';this.style.borderColor=\'var(--border-color)\'">' +
          imgHtml +
          '<div style="flex:1"><strong>Paquete/Domicilio</strong>' +
          '<br><span style="font-size:11px;color:var(--text-secondary)">' + Utils.formatDateTime(p.fechaCreacion) + '</span></div>' +
          '<div>' + estado + '</div>' +
          '<span class="material-symbols-outlined" style="color:var(--text-secondary);font-size:20px">chevron_right</span></div>';
      }).join('') + '</div>';
    }).catch(function() { container.innerHTML = Utils.emptyState('Error al cargar'); });
  }

  /* ─── Camera ─── */

  function detenerCamara() {
    if (_stream) {
      _stream.getTracks().forEach(function(t) { t.stop(); });
      _stream = null;
    }
    var video = document.getElementById('paq-video');
    if (video) video.srcObject = null;
  }

  async function abrirCamara() {
    var video = document.getElementById('paq-video');
    var btnCamara = document.getElementById('paq-btn-camara');
    var btnCapturar = document.getElementById('paq-btn-capturar');
    if (!video) return;
    try {
      _stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } }, audio: false });
      video.srcObject = _stream;
      await video.play();
      if (btnCamara) btnCamara.classList.add('hidden');
      if (btnCapturar) btnCapturar.classList.remove('hidden');
    } catch (e) {
      Utils.showToast('Error al abrir camara: ' + e.message, 'error');
    }
  }

  function capturar() {
    var video = document.getElementById('paq-video');
    var canvas = document.getElementById('paq-canvas');
    var img = document.getElementById('paq-foto-img');
    var preview = document.getElementById('paq-foto-preview');
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    fotoBase64 = canvas.toDataURL('image/jpeg', 0.80);
    if (img) img.src = fotoBase64;
    if (preview) preview.classList.remove('hidden');
    video.style.display = 'none';
    document.getElementById('paq-btn-capturar').classList.add('hidden');
    document.getElementById('paq-btn-retomar').classList.remove('hidden');
    detenerCamara();
  }

  function retomar() {
    fotoBase64 = null;
    var video = document.getElementById('paq-video');
    var preview = document.getElementById('paq-foto-preview');
    if (video) video.style.display = 'block';
    if (preview) preview.classList.add('hidden');
    document.getElementById('paq-btn-retomar').classList.add('hidden');
    abrirCamara();
  }

  /* ─── Actions ─── */

  async function cargarResidentes() {
    try {
      residentes = await API.get('/residentes');
      residentes = residentes || [];
      residentes.sort(function(a, b) { return (parseInt(a.numeroApartamento, 10) || 0) - (parseInt(b.numeroApartamento, 10) || 0); });
      var select = document.getElementById('paq-residente');
      select.innerHTML = '<option value="">Seleccione...</option>';
      (residentes || []).forEach(function(r) {
        var opt = document.createElement('option');
        opt.value = r.id;
        var aptLabel = r.numeroApartamento ? r.numeroApartamento + ' - ' : '';
        opt.textContent = aptLabel + (r.nombres || '') + ' ' + (r.apellidos || '');
        select.appendChild(opt);
      });
    } catch (e) { Utils.showToast('Error al cargar residentes: ' + e.message, 'error'); }
  }

  async function registrar(e) {
    e.preventDefault();
    Utils.limpiarErrores('form-paquete');
    var idRes = document.getElementById('paq-residente').value;
    if (!idRes) { Utils.mostrarError('paq-residente', 'Seleccione un apartamento'); return; }
    var res = residentes.find(function(r) { return r.id == idRes; });
    var aptId = res && res.idApartamento;
    if (!aptId) { Utils.showToast('El residente no tiene apartamento asignado', 'error'); return; }
    if (!fotoBase64) { Utils.showToast('Debe tomar una foto del paquete', 'error'); return; }
    var btn = document.getElementById('btn-registrar-paquete');
    if (btn) btn.disabled = true;
    try {
      await API.post('/buzon/paquete', {
        idApartamento: aptId,
        titulo: 'Paquete/Domicilio recibido',
        fotoCaptura: fotoBase64
      });
      Utils.showToast('Paquete notificado al residente', 'success');
      document.getElementById('form-paquete').reset();
      fotoBase64 = null;
      document.getElementById('paq-foto-preview').classList.add('hidden');
      document.getElementById('paq-video').style.display = 'block';
      document.getElementById('paq-btn-camara').classList.remove('hidden');
      document.getElementById('paq-btn-capturar').classList.add('hidden');
      document.getElementById('paq-btn-retomar').classList.add('hidden');
      onResidenteChange();
    } catch (err) { Utils.showToast(err.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  function init() {
    document.getElementById('page-title').textContent = 'Paquetes';
    cargarResidentes();
    document.getElementById('paq-residente').onchange = onResidenteChange;
    document.getElementById('form-paquete').onsubmit = registrar;
  }

  function abrirFoto(img) { window.open(img.src); }

  /* ─── Modal Detalle Paquete ─── */

  function verDetallePaquete(idx) {
    var p = _paquetesHistoricos[idx];
    if (!p) return;
    var fotoHtml = '';
    if (p.fotoCaptura) {
      fotoHtml = '<div class="form-group"><label>Foto de Evidencia</label>' +
        '<img src="' + p.fotoCaptura + '" style="width:100%;max-height:300px;object-fit:contain;border-radius:8px;border:1px solid var(--border);cursor:pointer" onclick="window.open(this.src)" title="Click para ver en tamaño completo"></div>';
    }
    var estadoHtml = p.entregado 
      ? '<div class="form-group"><label>Estado</label><div style="display:flex;align-items:center;gap:8px"><span class="badge badge-success">Entregado</span></div></div>'
      : '<div class="form-group"><label>Estado</label><div style="display:flex;align-items:center;gap:8px"><span class="badge badge-warning">Pendiente de Retirar</span></div></div>';
    
    var overlay = document.createElement('div');
    overlay.id = 'modal-detalle-paquete-historico';
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
      '<div class="form-group"><label>Título</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.escapeHtml(p.titulo || 'Paquete/Domicilio') + '</div></div>' +
      (p.cuerpo ? '<div class="form-group"><label>Descripción</label><div class="form-control" style="background:#f9fafb;cursor:default;min-height:60px;white-space:pre-wrap">' + Utils.escapeHtml(p.cuerpo) + '</div></div>' : '') +
      '<div class="form-group"><label>Fecha de Registro</label><div class="form-control" style="background:#f9fafb;cursor:default">' + Utils.formatDateTime(p.fechaCreacion) + '</div></div>' +
      estadoHtml +
      fotoHtml +
      '</div>' +
      '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
      '<button class="btn btn-ghost" onclick="Paquetes.cerrarDetallePaquete()">Cerrar</button>' +
      '</div></div>';
    
    document.body.appendChild(overlay);
    
    // Cerrar al hacer click fuera del modal
    overlay.addEventListener('click', function(e) {
      if (e.target === overlay) cerrarDetallePaquete();
    });
  }

  function cerrarDetallePaquete() {
    var overlay = document.getElementById('modal-detalle-paquete-historico');
    if (overlay) overlay.remove();
  }

  return { 
    init: init, 
    onResidenteChange: onResidenteChange, 
    abrirCamara: abrirCamara, 
    capturar: capturar, 
    retomar: retomar, 
    abrirFoto: abrirFoto,
    verDetallePaquete: verDetallePaquete,
    cerrarDetallePaquete: cerrarDetallePaquete
  };
})();

Router.register('paquetes', {
  html: document.getElementById('tpl-paquetes').innerHTML,
  js: () => { Paquetes.init(); }
});