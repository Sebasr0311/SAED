const QuejasAdmin = (() => {
  var quejas = [];
  var quejaActual = null;
  var _pollInterval = null;

  async function init() {
    await cargarQuejas();
    await cargarEstadisticas();
    configurarFiltros();
    configurarModal();
    iniciarAutoRefresh();
  }

  function destroy() {
    detenerAutoRefresh();
    quejas = [];
    quejaActual = null;
  }

  function iniciarAutoRefresh() {
    detenerAutoRefresh();
    _pollInterval = setInterval(function() {
      cargarQuejas();
      cargarEstadisticas();
    }, 1500);
  }

  function detenerAutoRefresh() {
    if (_pollInterval) {
      clearInterval(_pollInterval);
      _pollInterval = null;
    }
  }

  async function cargarQuejas() {
    try {
      quejas = await API.get('/quejas/todas');
      aplicarFiltros();
    } catch (e) {
      Utils.showToast('Error al cargar quejas: ' + e.message, 'error');
    }
  }

  async function cargarEstadisticas() {
    try {
      var total = quejas.length;
      var pendientes = quejas.filter(function(q) { return q.estado === 'PENDIENTE'; }).length;
      var enRevision = quejas.filter(function(q) { return q.estado === 'EN_REVISION'; }).length;
      var resueltas = quejas.filter(function(q) { return q.estado === 'RESUELTA'; }).length;

      var elTotal = document.getElementById('stat-total');
      if (elTotal) elTotal.textContent = total;
      var elPend = document.getElementById('stat-pendientes');
      if (elPend) elPend.textContent = pendientes;
      var elRev = document.getElementById('stat-revision');
      if (elRev) elRev.textContent = enRevision;
      var elRes = document.getElementById('stat-resueltas');
      if (elRes) elRes.textContent = resueltas;
    } catch (e) {
      console.error('Error calculando estadísticas', e);
    }
  }

  function configurarFiltros() {
    var elTipo = document.getElementById('filtro-tipo');
    var elEstado = document.getElementById('filtro-estado');
    var elPrioridad = document.getElementById('filtro-prioridad');
    if (elTipo) elTipo.addEventListener('change', aplicarFiltros);
    if (elEstado) elEstado.addEventListener('change', aplicarFiltros);
    if (elPrioridad) elPrioridad.addEventListener('change', aplicarFiltros);
  }

  function aplicarFiltros() {
    var selTipo = document.getElementById('filtro-tipo');
    var selEstado = document.getElementById('filtro-estado');
    var selPrioridad = document.getElementById('filtro-prioridad');
    if (!selTipo || !selEstado || !selPrioridad) return;
    var tipo = selTipo.value;
    var estado = selEstado.value;
    var prioridad = selPrioridad.value;

    var filtradas = quejas.filter(function(q) {
      if (tipo !== 'TODAS' && q.tipo !== tipo) return false;
      if (estado !== 'TODAS' && q.estado !== estado) return false;
      if (prioridad !== 'TODAS' && q.prioridad !== prioridad) return false;
      return true;
    });

    renderizarTabla(filtradas);
  }

  function renderizarTabla(lista) {
    var tbody = document.getElementById('tabla-quejas-body');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (lista.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:20px;color:#999;">No hay quejas que mostrar</td></tr>';
      return;
    }

    lista.forEach(function(q) {
      var tr = document.createElement('tr');
      
      var fecha = Utils.formatDate(q.fechaCreacion);
      var tipoBadge = getTipoBadge(q.tipo);
      var estadoBadge = getEstadoBadge(q.estado);
      var prioridadBadge = getPrioridadBadge(q.prioridad);
      
      tr.innerHTML = '<td>' + fecha + '</td>' +
                     '<td>' + tipoBadge + '</td>' +
                     '<td>' + (q.numeroApartamento || 'N/A') + '</td>' +
                     '<td>' + (q.nombreResidente || 'N/A') + '</td>' +
                     '<td title="' + (q.titulo || '') + '">' + (q.titulo ? q.titulo.substring(0, 30) + (q.titulo.length > 30 ? '...' : '') : '') + '</td>' +
                     '<td>' + (q.categoria || 'N/A') + '</td>' +
                     '<td>' + estadoBadge + '</td>' +
                     '<td>' + prioridadBadge + '</td>' +
                     '<td><button class="btn-sm btn-primary" onclick="QuejasAdmin.abrirDetalle(' + q.idQueja + ')">Ver / Responder</button></td>';
      
      tbody.appendChild(tr);
    });
  }

  function getTipoBadge(tipo) {
    var icon = tipo === 'QUEJA' ? '🔴' : (tipo === 'SUGERENCIA' ? '💡' : '⚖️');
    return '<span class="badge badge-tipo-' + tipo.toLowerCase() + '">' + icon + ' ' + tipo + '</span>';
  }

  function getEstadoBadge(estado) {
    var color = estado === 'PENDIENTE' ? '#FFA726' :
                estado === 'EN_REVISION' ? '#42A5F5' :
                estado === 'RESUELTA' ? '#66BB6A' : '#757575';
    return '<span class="badge" style="background:' + color + '">' + estado.replace('_', ' ') + '</span>';
  }

  function getPrioridadBadge(prioridad) {
    var icon = prioridad === 'ALTA' ? '🔴' : (prioridad === 'MEDIA' ? '🟡' : '🟢');
    return '<span class="badge-prioridad">' + icon + ' ' + prioridad + '</span>';
  }

  async function abrirDetalle(idQueja) {
    try {
      var q = await API.get('/quejas/' + idQueja);
      quejaActual = q;
      mostrarModal(q);
    } catch (e) {
      Utils.showToast('Error al cargar detalle: ' + e.message, 'error');
    }
  }

  function mostrarModal(q) {
    document.getElementById('detalle-tipo').textContent = q.tipo;
    document.getElementById('detalle-apartamento').textContent = q.numeroApartamento || 'N/A';
    document.getElementById('detalle-residente').textContent = q.nombreResidente || 'N/A';
    document.getElementById('detalle-fecha').textContent = Utils.formatDate(q.fechaCreacion);
    document.getElementById('detalle-categoria').textContent = q.categoria || 'N/A';
    document.getElementById('detalle-titulo').textContent = q.titulo || '';
    document.getElementById('detalle-descripcion').textContent = q.descripcion || '';
    
    var fotoContainer = document.getElementById('detalle-foto-container');
    if (q.fotoEvidencia) {
      fotoContainer.innerHTML = '<img src="' + q.fotoEvidencia + '" alt="Foto evidencia" style="max-width:100%;border-radius:8px;">';
      fotoContainer.classList.remove('hidden');
    } else {
      fotoContainer.classList.add('hidden');
    }

    // Si es apelación, mostrar link a multa
    var multaInfo = document.getElementById('detalle-multa-info');
    if (q.tipo === 'APELACION' && q.idMulta) {
      multaInfo.innerHTML = '<p><strong>Multa relacionada:</strong> <a href="#" onclick="Utils.showToast(\'Ver multa #' + q.idMulta + '\', \'info\'); return false;">Multa #' + q.idMulta + '</a></p>';
      multaInfo.classList.remove('hidden');
    } else {
      multaInfo.classList.add('hidden');
    }

    // Respuesta actual si existe
    if (q.respuestaAdmin) {
      document.getElementById('respuesta-actual').innerHTML = '<div class="alert alert-success"><strong>Respuesta enviada (' + Utils.formatDate(q.fechaRespuesta) + '):</strong><br>' + q.respuestaAdmin + '<br><small>Por: ' + (q.nombreAdmin || 'Admin') + '</small></div>';
      document.getElementById('respuesta-actual').classList.remove('hidden');
    } else {
      document.getElementById('respuesta-actual').classList.add('hidden');
    }

    // Pre-llenar estado y prioridad
    document.getElementById('modal-estado').value = q.estado;
    document.getElementById('modal-prioridad').value = q.prioridad;
    document.getElementById('modal-respuesta').value = '';

    // Mostrar modal
    document.getElementById('modal-detalle-queja').classList.add('show');
  }

  function configurarModal() {
    document.getElementById('btn-cerrar-modal').addEventListener('click', cerrarModal);
    document.getElementById('btn-cancelar-modal').addEventListener('click', cerrarModal);
    document.getElementById('btn-guardar-respuesta').addEventListener('click', guardarRespuesta);
    // Cerrar al hacer clic fuera del modal
    document.getElementById('modal-detalle-queja').addEventListener('click', function(e) {
      if (e.target === this) cerrarModal();
    });
  }

  function cerrarModal() {
    document.getElementById('modal-detalle-queja').classList.remove('show');
    quejaActual = null;
  }

  async function guardarRespuesta() {
    if (!quejaActual) return;

    Utils.limpiarErrores('form-respuesta-queja');
    var respuesta = document.getElementById('modal-respuesta').value.trim();
    var nuevoEstado = document.getElementById('modal-estado').value;
    var nuevaPrioridad = document.getElementById('modal-prioridad').value;

    try {
      // Si hay respuesta escrita, enviar respuesta (auto-marca como RESUELTA)
      if (respuesta) {
        await API.put('/quejas/' + quejaActual.idQueja + '/responder', { respuesta: respuesta });
        Utils.showToast('Respuesta enviada exitosamente', 'success');
      } else {
        // Si no hay respuesta pero cambió el estado, actualizar estado
        if (nuevoEstado !== quejaActual.estado) {
          await API.put('/quejas/' + quejaActual.idQueja + '/estado', { estado: nuevoEstado });
          Utils.showToast('Estado actualizado', 'success');
        }
      }

      // Actualizar prioridad si cambió
      if (nuevaPrioridad !== quejaActual.prioridad) {
        await API.put('/quejas/' + quejaActual.idQueja + '/prioridad', { prioridad: nuevaPrioridad });
        Utils.showToast('Prioridad actualizada', 'success');
      }

      cerrarModal();
      await cargarQuejas();
      await cargarEstadisticas();
    } catch (e) {
      Utils.showToast('Error: ' + e.message, 'error');
    }
  }

  return {
    init: init,
    destroy: destroy,
    abrirDetalle: abrirDetalle,
    cargarQuejas: cargarQuejas
  };
})();
