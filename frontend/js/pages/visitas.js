const Visitas = (() => {
  const TIPOS_VEHICULO = ['VEHICULO', 'MOTO', 'BICICLETA', 'OTRO'];
  let ultimoCodigoQR = null;

  function cambiarTab(tabId) {
    document.querySelectorAll('#visitas-tabs .tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('#visitas-tabs ~ .tab-content').forEach(t => t.classList.remove('active'));
    var tab = document.querySelector('#visitas-tabs .tab[data-tab="' + tabId.replace(/["\\]/g, '') + '"]');
    if (tab) tab.classList.add('active');
    var content = document.getElementById(tabId);
    if (content) content.classList.add('active');
  }

  function renderTabNueva() {
    const container = document.getElementById('tab-visita-nueva');
    if (!container) return;
    container.innerHTML =
      '<div class="card"><div class="card-title">Registrar Nueva Visita</div>' +
      '<form id="form-visita">' +
      '<div class="form-row"><div class="form-group"><label>Residente Autorizante</label><select id="vis-residente" class="form-control"><option value="">Seleccione...</option></select><span class="field-error" id="vis-residente-error"></span></div>' +
      '<div class="form-group"><label>Tiempo Validez (min)</label><input type="number" id="vis-validez" class="form-control" value="30"><span class="field-error" id="vis-validez-error"></span></div></div>' +
      '<div class="form-row"><div class="form-group"><label>Cantidad Personas</label><input type="number" id="vis-personas" class="form-control" value="1"><span class="field-error" id="vis-personas-error"></span></div></div>' +
      '<h4 style="margin:12px 0 8px;font-size:14px">Visitante</h4>' +
      '<div class="form-row"><div class="form-group"><label>Tipo Documento</label><select id="vis-tipo-doc" class="form-control"></select><span class="field-error" id="vis-tipo-doc-error"></span></div>' +
      '<div class="form-group"><label>N\u00famero Documento</label><input type="text" id="vis-documento" class="form-control"><span class="field-error" id="vis-documento-error"></span></div></div>' +
      '<div class="form-row"><div class="form-group"><label>Nombres</label><input type="text" id="vis-nombres" class="form-control"><span class="field-error" id="vis-nombres-error"></span></div>' +
      '<div class="form-group"><label>Apellidos</label><input type="text" id="vis-apellidos" class="form-control"><span class="field-error" id="vis-apellidos-error"></span></div></div>' +
      '<div class="form-row"><div class="form-group"><label>Tel\u00e9fono</label><input type="text" id="vis-telefono" class="form-control"><span class="field-error" id="vis-telefono-error"></span></div>' +
      '<div class="form-group"><label>Email</label><input type="email" id="vis-email" class="form-control"><span class="field-error" id="vis-email-error"></span></div></div>' +
      '<h4 style="margin:12px 0 8px;font-size:14px">Veh\u00edculo (opcional)</h4>' +
      '<div class="form-row">' +
      '<div class="form-group"><label>Tipo</label><select id="vis-tipo-vehiculo" class="form-control"><option value="">Sin veh\u00edculo</option>' + TIPOS_VEHICULO.map(function(t) { return '<option value="' + t + '">' + t + '</option>'; }).join('') + '</select></div>' +
      '<div class="form-group" id="vis-placa-group"><label id="vis-placa-label">Placa</label><input type="text" id="vis-placa" class="form-control" maxlength="10" style="text-transform:uppercase" placeholder="Ej: ABC 123"><span class="field-error" id="vis-placa-error"></span></div>' +
      '<div class="form-group hidden" id="vis-descripcion-group"><label>Descripci\u00f3n</label><input type="text" id="vis-descripcion" class="form-control" maxlength="100" placeholder="Descripci\u00f3n del veh\u00edculo"><span class="field-error" id="vis-descripcion-error"></span></div>' +
      '</div>' +
      '<div class="form-group"><label>Notas</label><textarea id="vis-notas" class="form-control" maxlength="500"></textarea></div>' +
      '<div class="mt-16"><button type="submit" class="btn btn-primary" id="btn-registrar-visita">Generar QR y Registrar</button></div>' +
      '</form><div id="vis-qr-result" class="hidden mt-16"></div></div>';

    cargarResidentes();
    cargarTiposDoc();
    document.getElementById('form-visita').onsubmit = function(e) { e.preventDefault(); registrar(); };
    
    // Event listeners para validación en tiempo real
    configurarValidacionDocumento();
    
    // Event listeners para validación de placa y cambio de tipo de vehículo
    configurarEventListenersVehiculo();
  }

  function renderTabFrecuente() {
    const container = document.getElementById('tab-visita-frecuente');
    if (!container) return;
    container.innerHTML =
      '<div class="card"><div class="card-title">Visita Rapida desde Frecuentes</div>' +
      '<div class="form-group"><label>Residente</label><select id="vis-frec-residente" class="form-control" onchange="Visitas.cargarFrecuentes()"><option value="">Seleccione...</option></select></div>' +
      '<div id="vis-frec-lista" class="mt-16">' + Utils.loadingSpinner() + '</div></div>';
    cargarResidentesFrec();
  }

  async function cargarResidentes() {
    try {
      var r = await API.get('/residentes');
      r.sort(function(a, b) { return (parseInt(a.numeroApartamento, 10) || 0) - (parseInt(b.numeroApartamento, 10) || 0); });
      var select = document.getElementById('vis-residente');
      select.innerHTML = '<option value="">Seleccione...</option>';
      (r || []).forEach(function(res) {
        var opt = document.createElement('option');
        opt.value = res.id;
        var aptLabel = res.numeroApartamento ? res.numeroApartamento + ' - ' : '';
        opt.textContent = aptLabel + (res.nombres || '') + ' ' + (res.apellidos || '');
        select.appendChild(opt);
      });
    } catch (e) { Utils.showToast('Error al cargar residentes: ' + e.message, 'error'); }
  }

  async function cargarResidentesFrec() {
    try {
      var r = await API.get('/residentes');
      r.sort(function(a, b) { return (parseInt(a.numeroApartamento, 10) || 0) - (parseInt(b.numeroApartamento, 10) || 0); });
      var select = document.getElementById('vis-frec-residente');
      select.innerHTML = '<option value="">Seleccione...</option>';
      (r || []).forEach(function(res) {
        var opt = document.createElement('option');
        opt.value = res.id;
        var aptLabel = res.numeroApartamento ? res.numeroApartamento + ' - ' : '';
        opt.textContent = aptLabel + (res.nombres || '') + ' ' + (res.apellidos || '');
        select.appendChild(opt);
      });
    } catch (e) { Utils.showToast('Error al cargar residentes: ' + e.message, 'error'); }
  }

  async function cargarTiposDoc() {
    try {
      var t = await API.get('/tipos-documento');
      var select = document.getElementById('vis-tipo-doc');
      select.innerHTML = '<option value="">Seleccione...</option>';
      t.forEach(function(tipo) {
        var opt = document.createElement('option');
        opt.value = tipo.idTipoDoc;
        opt.textContent = tipo.codigo + ' - ' + tipo.descripcion;
        opt.setAttribute('data-codigo', tipo.codigo);
        select.appendChild(opt);
      });
    } catch (e) { Utils.showToast('Error al cargar tipos de documento', 'error'); }
  }

  function configurarValidacionDocumento() {
    var docInput = document.getElementById('vis-documento');
    var tipoDocSelect = document.getElementById('vis-tipo-doc');
    
    if (docInput && tipoDocSelect) {
      // Al cambiar tipo de documento
      tipoDocSelect.addEventListener('change', function() {
        docInput.value = '';
        docInput.classList.remove('is-invalid');
        var errorEl = docInput.parentNode.querySelector('.field-error');
        if (errorEl) errorEl.textContent = '';
        
        var tipoDocCodigo = getTipoDocCodigo(tipoDocSelect.value);
        aplicarFiltroDocumento(docInput, tipoDocCodigo);
      });
      
      // Al perder foco
      docInput.addEventListener('blur', function() {
        var tipoDocId = tipoDocSelect.value;
        if (tipoDocId && docInput.value.trim()) {
          var tipoDocCodigo = getTipoDocCodigo(tipoDocId);
          Utils.valDocumento(docInput.value, 'vis-documento', tipoDocCodigo);
        }
      });
    }
    
    // Filtros para nombres y apellidos
    Utils.soloLetras('vis-nombres', 25);
    Utils.soloLetras('vis-apellidos', 25);
    
    // Filtro para teléfono
    Utils.soloNumeros('vis-telefono', 10);
    Utils.validarTelefonoTiempoReal('vis-telefono');
  }
  
  function aplicarFiltroDocumento(input, tipoDocCodigo) {
    if (!input) return;
    
    // Remover listeners previos clonando el elemento
    var newInput = input.cloneNode(true);
    input.parentNode.replaceChild(newInput, input);
    input = newInput;
    
    var maxLength = 15;
    var pattern = /[^a-zA-Z0-9-]/g;
    
    var tiposNumericos = ['CC', 'TI', 'CE', 'RC', 'NIT'];
    if (tiposNumericos.includes(tipoDocCodigo)) {
      pattern = /[^0-9]/g;
      if (tipoDocCodigo === 'CC') maxLength = 10;
      else if (tipoDocCodigo === 'TI') maxLength = 11;
      else if (tipoDocCodigo === 'CE') maxLength = 12;
      else if (tipoDocCodigo === 'RC') maxLength = 11;
      else if (tipoDocCodigo === 'NIT') maxLength = 10;
    } else if (tipoDocCodigo === 'PP' || tipoDocCodigo === 'PASAPORTE') {
      maxLength = 20;
      pattern = /[^a-zA-Z0-9]/g;
    } else if (tipoDocCodigo === 'PEP') {
      maxLength = 15;
      pattern = /[^a-zA-Z0-9-]/g;
    }
    
    input.setAttribute('maxlength', maxLength);
    
    input.addEventListener('input', function(e) {
      var cleaned = e.target.value.replace(pattern, '');
      if (tipoDocCodigo === 'PP' || tipoDocCodigo === 'PASAPORTE' || tipoDocCodigo === 'PEP') {
        cleaned = cleaned.toUpperCase();
      }
      e.target.value = cleaned;
    });
    
    input.addEventListener('paste', function(e) {
      e.preventDefault();
      var pastedText = (e.clipboardData || window.clipboardData).getData('text');
      var cleaned = pastedText.replace(pattern, '').substring(0, maxLength);
      if (tipoDocCodigo === 'PP' || tipoDocCodigo === 'PASAPORTE' || tipoDocCodigo === 'PEP') {
        cleaned = cleaned.toUpperCase();
      }
      e.target.value = cleaned;
    });
  }
  
  function getTipoDocCodigo(idTipoDoc) {
    var select = document.getElementById('vis-tipo-doc');
    if (!select) return null;
    
    var option = Array.from(select.options).find(function(opt) { return opt.value === String(idTipoDoc); });
    if (!option) return null;
    
    var codigo = option.getAttribute('data-codigo');
    if (codigo) return codigo;
    
    // Fallback: extraer del texto
    var text = option.textContent.trim();
    var match = text.match(/^([A-Z]{2,9})\s*-/);
    if (match) return match[1];
    
    return null;
  }

  function configurarEventListenersVehiculo() {
    var tipoSelect = document.getElementById('vis-tipo-vehiculo');
    var placaInput = document.getElementById('vis-placa');
    var placaGroup = document.getElementById('vis-placa-group');
    var placaLabel = document.getElementById('vis-placa-label');
    var descripcionGroup = document.getElementById('vis-descripcion-group');

    if (!tipoSelect || !placaInput || !placaGroup || !descripcionGroup) return;

    // Cambio de tipo de vehículo
    tipoSelect.addEventListener('change', function() {
      actualizarCamposVehiculo();
    });

    // Auto-formato de placa a mayúsculas y filtro alfanumérico
    placaInput.addEventListener('input', function(e) {
      var val = e.target.value.toUpperCase().replace(/[^A-Z0-9\s]/g, '');
      e.target.value = val;
      validarPlacaInput();
    });

    placaInput.addEventListener('blur', function() {
      validarPlacaInput();
    });
  }

  function actualizarCamposVehiculo() {
    var tipo = document.getElementById('vis-tipo-vehiculo').value;
    var placaGroup = document.getElementById('vis-placa-group');
    var placaLabel = document.getElementById('vis-placa-label');
    var placaInput = document.getElementById('vis-placa');
    var descripcionGroup = document.getElementById('vis-descripcion-group');
    var placaError = document.getElementById('vis-placa-error');

    if (!placaGroup || !descripcionGroup) return;

    // Limpiar errores
    if (placaError) placaError.textContent = '';
    if (placaInput) placaInput.classList.remove('is-invalid');

    if (tipo === 'BICICLETA') {
      // Mostrar descripción, ocultar placa
      placaGroup.classList.add('hidden');
      descripcionGroup.classList.remove('hidden');
      if (placaInput) placaInput.value = '';
    } else if (tipo === '' || !tipo) {
      // Sin vehículo: ocultar ambos
      placaGroup.classList.add('hidden');
      descripcionGroup.classList.add('hidden');
      if (placaInput) placaInput.value = '';
      document.getElementById('vis-descripcion').value = '';
    } else {
      // VEHICULO, MOTO, OTRO: mostrar placa
      placaGroup.classList.remove('hidden');
      descripcionGroup.classList.add('hidden');
      document.getElementById('vis-descripcion').value = '';
      
      // Actualizar placeholder según tipo
      if (tipo === 'VEHICULO') {
        if (placaLabel) placaLabel.textContent = 'Placa (Carro)';
        if (placaInput) placaInput.placeholder = 'Ej: ABC 123';
      } else if (tipo === 'MOTO') {
        if (placaLabel) placaLabel.textContent = 'Placa (Moto)';
        if (placaInput) placaInput.placeholder = 'Ej: ABC 12D';
      } else {
        if (placaLabel) placaLabel.textContent = 'Placa';
        if (placaInput) placaInput.placeholder = 'Ej: ABC 123';
      }
    }
  }

  function validarPlacaInput() {
    var tipo = document.getElementById('vis-tipo-vehiculo').value;
    var placaInput = document.getElementById('vis-placa');
    var placaError = document.getElementById('vis-placa-error');
    
    if (!placaInput || !tipo || tipo === 'BICICLETA' || tipo === '') return;
    
    var placa = placaInput.value.trim();
    if (!placa) {
      placaInput.classList.remove('is-invalid');
      if (placaError) placaError.textContent = '';
      return;
    }

    var placaSinEspacios = placa.replace(/\s+/g, '');
    var valido = false;
    var mensaje = '';

    if (tipo === 'VEHICULO') {
      valido = /^[A-Z]{3}\s?\d{3}$/i.test(placa);
      mensaje = 'Formato: 3 letras + 3 números (Ej: ABC 123)';
    } else if (tipo === 'MOTO') {
      valido = /^[A-Z]{3}\s?\d{2}[A-Z]$/i.test(placa);
      mensaje = 'Formato: 3 letras + 2 números + 1 letra (Ej: ABC 12D)';
    } else {
      valido = /^[A-Z0-9\s]{3,10}$/i.test(placa);
      mensaje = 'Formato inválido';
    }

    if (!valido) {
      placaInput.classList.add('is-invalid');
      if (placaError) placaError.textContent = mensaje;
    } else {
      placaInput.classList.remove('is-invalid');
      if (placaError) placaError.textContent = '';
    }
  }

  async function cargarFrecuentes() {
    var idResidente = document.getElementById('vis-frec-residente').value;
    var container = document.getElementById('vis-frec-lista');
    if (!idResidente) { container.innerHTML = '<p style="color:var(--text-secondary)">Seleccione un residente</p>'; return; }
    container.innerHTML = Utils.loadingSpinner();
    try {
      var frecs = await API.get('/residentes/' + idResidente + '/frecuentes');
      if (!frecs.length) { container.innerHTML = Utils.emptyState('No hay visitantes frecuentes'); return; }
      container.innerHTML = '<div class="frecuentes-grid">' + frecs.map(function(f) {
        return '<div class="frecuente-card">' +
          '<div class="name">' + Utils.escapeHtml(f.nombreVisitante) + '</div>' +
          '<div class="meta">' + Utils.escapeHtml(f.documento || '') + '</div>' +
          '<div class="meta">' + (f.totalVisitas || 0) + ' visita(s) - Ultima: ' + Utils.formatDateTime(f.ultimaVisita) + '</div>' +
          '<div class="card-actions">' +
          '<button class="btn btn-primary btn-sm" onclick="Visitas.liberarFrecuente(' + f.idFrecuente + ',' + f.idVisitante + ')">Generar QR</button>' +
          '</div></div>';
      }).join('') + '</div>';
    } catch (e) { container.innerHTML = Utils.emptyState('Error al cargar'); }
  }

  async function registrar() {
    var btn = document.getElementById('btn-registrar-visita');
    Utils.limpiarErrores('form-visita');
    if (!Utils.valSelect(document.getElementById('vis-residente').value, 'vis-residente', 'Seleccione el residente')) return;
    if (!Utils.valSelect(document.getElementById('vis-tipo-doc').value, 'vis-tipo-doc', 'Seleccione el tipo de documento')) return;
    
    // Validar documento con tipo específico
    var tipoDocId = document.getElementById('vis-tipo-doc').value;
    var tipoDocCodigo = getTipoDocCodigo(tipoDocId);
    if (!Utils.valDocumento(document.getElementById('vis-documento').value, 'vis-documento', tipoDocCodigo)) return;
    
    if (!Utils.valNombre(document.getElementById('vis-nombres').value, 'vis-nombres', 'El nombre')) return;
    if (!Utils.valApellido(document.getElementById('vis-apellidos').value, 'vis-apellidos', 'El apellido')) return;
    if (!Utils.valTelefono(document.getElementById('vis-telefono').value, 'vis-telefono')) return;
    if (!Utils.valEmail(document.getElementById('vis-email').value, 'vis-email')) return;
    if (!Utils.valEntero(document.getElementById('vis-validez').value, 'vis-validez', { positive: true, label: 'El tiempo de validez' })) return;
    if (!Utils.valEntero(document.getElementById('vis-personas').value, 'vis-personas', { positive: true, label: 'La cantidad de personas' })) return;
    
    var tipoV = document.getElementById('vis-tipo-vehiculo').value;
    var placa = document.getElementById('vis-placa').value.trim();
    var descripcion = document.getElementById('vis-descripcion').value.trim();
    
    // Validación de vehículo según tipo
    if (tipoV === 'BICICLETA') {
      if (!descripcion) {
        Utils.mostrarError('vis-descripcion', 'La descripción es obligatoria para bicicletas');
        return;
      }
    } else if (tipoV && tipoV !== '') {
      if (placa && !Utils.valPlaca(placa, 'vis-placa', tipoV)) return;
    }
    
    var d = {
      idResidente: parseInt(document.getElementById('vis-residente').value),
      tiempoValidezMin: parseInt(document.getElementById('vis-validez').value),
      cantidadPersonas: parseInt(document.getElementById('vis-personas').value),
      visitante: {
        idTipoDoc: parseInt(tipoDocId),
        numeroDocumento: document.getElementById('vis-documento').value.trim(),
        nombres: document.getElementById('vis-nombres').value.trim(),
        apellidos: document.getElementById('vis-apellidos').value.trim(),
        telefono: document.getElementById('vis-telefono').value.trim(),
        email: document.getElementById('vis-email').value.trim()
      },
      notas: document.getElementById('vis-notas').value.trim()
    };
    
    // Agregar vehículo según tipo
    if (tipoV && tipoV !== '') {
      if (tipoV === 'BICICLETA') {
        d.vehiculo = { tipo: tipoV, descripcion: descripcion };
      } else if (placa) {
        d.vehiculo = { placa: placa, tipo: tipoV };
      }
    }
    
    if (btn) btn.disabled = true;
    try {
      var res = await API.post('/visitas', d);
      ultimoCodigoQR = res.codigoQr;
      var qrHtml = '<div class="qr-container"><p><strong>QR Generado:</strong></p>' +
        '<div><img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(res.codigoQr) + '" alt="C\u00f3digo QR de acceso"></div>' +
        '<p class="qr-code-text">#' + Utils.escapeHtml(res.codigoQr.substring(0,8)) + '...</p>' +
        '<div class="share-buttons">' +
        (d.visitante.email ? '<button class="btn btn-primary btn-sm" onclick="Visitas.compartirCorreo(\'' + res.codigoQr.replace(/['\\]/g, '') + '\',\'' + Utils.escapeHtml(d.visitante.email).replace(/'/g, '') + '\')">Correo</button>' : '') +
        '<button class="btn btn-primary btn-sm" onclick="Visitas.compartirTelegram(\'' + res.codigoQr.replace(/['\\]/g, '') + '\')">Telegram</button>' +
        '<button class="btn btn-primary btn-sm" onclick="Visitas.copiarQR(\'' + res.codigoQr.replace(/['\\]/g, '') + '\')">Copiar QR</button>' +
        '</div></div>';
      document.getElementById('vis-qr-result').innerHTML = qrHtml;
      document.getElementById('vis-qr-result').classList.remove('hidden');
      Utils.showToast('Visita registrada exitosamente', 'success');
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function liberarFrecuente(idFrec, idVis) {
    var idResidente = parseInt(document.getElementById('vis-frec-residente').value);
    if (!idResidente) { Utils.mostrarError('vis-frec-residente', 'Seleccione un residente'); return; }
    try {
      var res = await API.post('/visitas/rapida', { idFrecuente: idFrec, idVisitante: idVis, idResidente: idResidente });
      Utils.showToast('QR generado: ' + res.codigoQr, 'success');
      cargarFrecuentes();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  function compartirCorreo(qr, email) {
    window.open('mailto:' + email + '?subject=Codigo QR de Acceso&body=Su codigo QR es: ' + qr);
  }

  function compartirTelegram(qr) {
    window.open('https://t.me/share/url?text=Codigo%20QR%20de%20acceso%3A%20' + encodeURIComponent(qr), '_blank');
  }

  function copiarQR(qr) {
    navigator.clipboard.writeText(qr).then(function() { Utils.showToast('Codigo QR copiado al portapapeles', 'success'); });
  }

  return { cambiarTab: cambiarTab, renderTabNueva: renderTabNueva, renderTabFrecuente: renderTabFrecuente, cargarFrecuentes: cargarFrecuentes, registrar: registrar, liberarFrecuente: liberarFrecuente, compartirCorreo: compartirCorreo, compartirTelegram: compartirTelegram, copiarQR: copiarQR };
})();

Router.register('visitas', {
  html: document.getElementById('tpl-visitas').innerHTML,
  js: function() { document.getElementById('page-title').textContent = 'Visitas'; Visitas.renderTabNueva(); Visitas.renderTabFrecuente(); }
});