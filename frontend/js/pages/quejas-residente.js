const QuejasResidente = (() => {
  var quejas = [];
  var stream = null;
  var fotoCapturada = null;

  async function init() {
    await cargarQuejas();
    configurarFormulario();
  }

  function destroy() {
    if (stream) {
      stream.getTracks().forEach(function(track) { track.stop(); });
      stream = null;
    }
    quejas = [];
    fotoCapturada = null;
  }

  async function cargarQuejas() {
    try {
      quejas = await API.get('/quejas');
      renderizarHistorial();
    } catch (e) {
      Utils.showToast('Error al cargar quejas: ' + e.message, 'error');
    }
  }

  function configurarFormulario() {
    var form = document.getElementById('form-nueva-queja');
    if (form) {
      form.addEventListener('submit', enviarQueja);
    }
  }

  async function enviarQueja(e) {
    e.preventDefault();
    Utils.limpiarErrores('form-nueva-queja');

    var tipo = document.getElementById('queja-tipo').value;
    var categoria = document.getElementById('queja-categoria').value;
    var titulo = document.getElementById('queja-titulo').value.trim();
    var descripcion = document.getElementById('queja-descripcion').value.trim();

    var ok = true;
    if (!Utils.valRequerido(titulo, 'queja-titulo', 'El título')) ok = false;
    if (!Utils.valRequerido(descripcion, 'queja-descripcion', 'La descripción')) ok = false;
    if (!ok) return false;

    try {
      var payload = {
        tipo: tipo,
        categoria: categoria,
        titulo: titulo,
        descripcion: descripcion
      };

      if (fotoCapturada) {
        payload.fotoEvidencia = fotoCapturada;
      }

      await API.post('/quejas', payload);
      Utils.showToast('Queja/sugerencia enviada exitosamente', 'success');
      
      // Limpiar formulario
      document.getElementById('queja-tipo').value = 'QUEJA';
      document.getElementById('queja-categoria').value = 'LIMPIEZA';
      document.getElementById('queja-titulo').value = '';
      document.getElementById('queja-descripcion').value = '';
      cerrarCamara();
      
      await cargarQuejas();
    } catch (e) {
      Utils.showToast('Error: ' + e.message, 'error');
    }

    return false;
  }

  async function apelarMulta(idMulta) {
    // Cambiar a tab de quejas
    var tab = document.querySelector('#res-dash-tabs .tab[data-tab="res-tab-quejas"]');
    if (tab) tab.click();

    // Esperar un poco para que el tab cambie
    setTimeout(function() {
      // Pre-llenar formulario con tipo APELACION
      document.getElementById('queja-tipo').value = 'APELACION';
      document.getElementById('queja-categoria').value = 'OTRO';
      document.getElementById('queja-titulo').value = 'Apelación de Multa #' + idMulta;
      document.getElementById('queja-descripcion').value = 'Solicito la revisión de la multa #' + idMulta + '. Motivo: ';
      document.getElementById('queja-descripcion').focus();
      
      // Scroll al formulario
      var form = document.getElementById('form-nueva-queja');
      if (form) form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  }

  function renderizarHistorial() {
    var container = document.getElementById('quejas-historial');
    if (!container) return;

    if (quejas.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-icon"><span class="material-symbols-outlined" style="font-size:48px">feedback</span></div><div class="empty-title">No has enviado quejas o sugerencias</div></div>';
      return;
    }

    var html = '<div style="display:flex;flex-direction:column;gap:12px">';
    
    quejas.forEach(function(q) {
      var estadoBadge = getEstadoBadge(q.estado);
      var tipoBadge = getTipoBadge(q.tipo);
      var fecha = Utils.formatDate(q.fechaCreacion);
      
      html += '<div class="card" style="padding:16px">';
      html += '<div style="display:flex;justify-content:space-between;align-items:start;margin-bottom:8px">';
      html += '<div style="flex:1">';
      html += '<div style="display:flex;gap:8px;align-items:center;margin-bottom:4px">';
      html += tipoBadge + estadoBadge;
      html += '</div>';
      html += '<h4 style="margin:0;font-size:14px;font-weight:600">' + Utils.escapeHtml(q.titulo || '') + '</h4>';
      html += '<p style="margin:4px 0 0;font-size:12px;color:var(--text-secondary)">' + fecha;
      if (q.categoria) html += ' • ' + q.categoria;
      html += '</p>';
      html += '</div>';
      html += '</div>';
      
      html += '<p style="margin:8px 0;font-size:13px;color:var(--text-secondary);white-space:pre-wrap">' + Utils.escapeHtml(q.descripcion || '') + '</p>';
      
      if (q.fotoEvidencia) {
        html += '<div style="margin-top:8px"><img src="' + q.fotoEvidencia + '" alt="Evidencia" style="max-width:200px;max-height:150px;border-radius:8px;border:1px solid var(--border-color)"></div>';
      }
      
      if (q.respuestaAdmin) {
        html += '<div class="alert alert-success" style="margin-top:12px;padding:12px;background:#E8F5E9;border:1px solid #C8E6C9;border-radius:8px">';
        html += '<strong style="font-size:13px">Respuesta del Administrador:</strong>';
        html += '<p style="margin:4px 0 0;font-size:13px">' + Utils.escapeHtml(q.respuestaAdmin) + '</p>';
        html += '<p style="margin:4px 0 0;font-size:11px;color:var(--text-secondary)">' + Utils.formatDate(q.fechaRespuesta) + '</p>';
        html += '</div>';
      }
      
      html += '</div>';
    });
    
    html += '</div>';
    container.innerHTML = html;
  }

  function getTipoBadge(tipo) {
    var icon = tipo === 'QUEJA' ? '🔴' : (tipo === 'SUGERENCIA' ? '💡' : '⚖️');
    var color = tipo === 'QUEJA' ? '#EF5350' : (tipo === 'SUGERENCIA' ? '#FFA726' : '#42A5F5');
    return '<span class="badge" style="background:' + color + '">' + icon + ' ' + tipo + '</span>';
  }

  function getEstadoBadge(estado) {
    var color = estado === 'PENDIENTE' ? '#FFA726' :
                estado === 'EN_REVISION' ? '#42A5F5' :
                estado === 'RESUELTA' ? '#66BB6A' : '#757575';
    return '<span class="badge" style="background:' + color + '">' + estado.replace('_', ' ') + '</span>';
  }

  // Funciones de cámara
  async function abrirCamara() {
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      var video = document.getElementById('queja-video');
      if (video) {
        video.srcObject = stream;
        video.classList.remove('hidden');
        document.getElementById('queja-btn-camara').classList.add('hidden');
        document.getElementById('queja-btn-capturar').classList.remove('hidden');
      }
    } catch (e) {
      Utils.showToast('Error al acceder a la cámara: ' + e.message, 'error');
    }
  }

  function capturar() {
    var video = document.getElementById('queja-video');
    var canvas = document.getElementById('queja-canvas');
    if (!video || !canvas) return;

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0);
    fotoCapturada = canvas.toDataURL('image/jpeg', 0.8);

    // Mostrar preview
    var preview = document.getElementById('queja-foto-preview');
    var img = document.getElementById('queja-foto-img');
    if (preview && img) {
      img.src = fotoCapturada;
      preview.classList.remove('hidden');
    }

    // Ocultar video y cambiar botones
    video.classList.add('hidden');
    cerrarCamara();
    document.getElementById('queja-btn-capturar').classList.add('hidden');
    document.getElementById('queja-btn-retomar').classList.remove('hidden');
  }

  function retomar() {
    fotoCapturada = null;
    document.getElementById('queja-foto-preview').classList.add('hidden');
    document.getElementById('queja-btn-retomar').classList.add('hidden');
    document.getElementById('queja-btn-camara').classList.remove('hidden');
  }

  function cerrarCamara() {
    if (stream) {
      stream.getTracks().forEach(function(track) { track.stop(); });
      stream = null;
    }
    var video = document.getElementById('queja-video');
    if (video) {
      video.srcObject = null;
      video.classList.add('hidden');
    }
    fotoCapturada = null;
    document.getElementById('queja-foto-preview').classList.add('hidden');
    document.getElementById('queja-btn-camara').classList.remove('hidden');
    document.getElementById('queja-btn-capturar').classList.add('hidden');
    document.getElementById('queja-btn-retomar').classList.add('hidden');
  }

  return {
    init: init,
    destroy: destroy,
    cargarQuejas: cargarQuejas,
    apelarMulta: apelarMulta,
    abrirCamara: abrirCamara,
    capturar: capturar,
    retomar: retomar
  };
})();
