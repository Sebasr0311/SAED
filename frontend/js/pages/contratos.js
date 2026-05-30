const Contratos = (() => {
  let data = [];
  let editingId = null;
  let aptsData = [];      // cache de todos los apartamentos para lookups
  let residentesData = []; // cache de todos los residentes
  const PAGE_SIZE = 15;
  let currentPage = 1;

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-contratos');
    const pag = document.getElementById('pagination-contratos');
    if (!tbody) return;
    const pg = Utils.paginate(data, currentPage, PAGE_SIZE);
    tbody.innerHTML = pg.items.map(c => `<tr>
      <td>${c.idContrato}</td>
      <td>${c.numeroApartamento || 'Apto #' + c.idApartamento}</td>
      <td>${c.nombreResidente || '-'}</td>
      <td>${Utils.formatDate(c.fechaInicio)}</td>
      <td>${Utils.formatDate(c.fechaFin)}</td>
      <td><span class="badge badge-${getTipoBadgeClass(c.tipoContrato)}">${c.tipoContrato || 'INICIAL'}</span></td>
      <td>${Utils.formatCurrency(c.valorMensual)}</td>
      <td>${Utils.estadoBadge(c.estado || 'PENDIENTE_FIRMA')}</td>
      <td class="actions-cell">
        <div class="btn-group">
        ${c.estado === 'ACTIVO' || c.estado === 'PENDIENTE_FIRMA'
          ? `<button class="btn btn-icon btn-sm btn-outline" onclick="Contratos.descargarPDF(${c.idContrato})" title="Descargar PDF">
              <span class="material-symbols-outlined">download</span></button>` : ''}
        ${c.estado === 'PENDIENTE_FIRMA' ? `<button class="btn btn-primary btn-sm" onclick="Contratos.activar(${c.idContrato})">Activar</button>` : ''}
        ${c.estado === 'ACTIVO' ? `<button class="btn btn-warn btn-sm" onclick="Contratos.cancelar(${c.idContrato})">Cancelar</button>` : ''}
        ${c.estado === 'VENCIDO' ? `<button class="btn btn-primary btn-sm" onclick="Contratos.mostrarRenovar(${c.idContrato})">Renovar</button>` : ''}
        </div>
      </td>
    </tr>`).join('');
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Contratos.goToPage');
  }

  function getTipoBadgeClass(tipo) {
    switch(tipo) {
      case 'INICIAL': return 'info';
      case 'RENOVACION': return 'success';
      case 'PERMANENCIA': return 'primary';
      default: return 'secondary';
    }
  }

  async function cargar() {
    try {
      data = await API.get('/contratos');
      var filtro = document.getElementById('con-filtro-estado');
      if (filtro && filtro.value) {
        data = data.filter(function(c) { return c.estado === filtro.value; });
      }
      currentPage = 1; render();
    }
    catch (e) { Utils.showAlert('Error', e.message, 'error'); }
  }

  async function mostrarFormulario() {
    editingId = null;
    aptsData = [];
    residentesData = [];
    try { aptsData = await API.get('/apartamentos'); } catch(e) {}
    try { residentesData = await API.get('/residentes'); } catch(e) {}

    // Solo apartamentos DISPONIBLES para nuevo contrato
    var aptsDisponibles = aptsData.filter(function(a) { return a.estado === 'DISPONIBLE'; });
    var aptOpts = aptsDisponibles.map(function(a) {
      var adminStr = a.administracion != null ? Number(a.administracion) : 0;
      return '<option value="' + a.idApartamento + '"'
           + ' data-admin="' + adminStr + '"'
           + ' data-tipo="' + (a.tipo || '') + '">'
           + a.numero + ' \u2014 Piso ' + a.piso + ' \u2014 ' + (a.tipo || '')
           + '</option>';
    }).join('');

    if (!aptOpts) {
      aptOpts = '<option value="" disabled>No hay apartamentos disponibles actualmente</option>';
    }

    var resOpts = residentesData.map(function(r) {
      return '<option value="' + r.id + '">'
           + r.nombres + ' ' + r.apellidos
           + ' \u2014 ' + (r.tipoDocumento || 'Doc') + ': ' + (r.numeroDocumento || '')
           + '</option>';
    }).join('');

    if (!resOpts) {
      resOpts = '<option value="" disabled>No hay residentes registrados</option>';
    }

    var today = new Date(); today.setHours(0,0,0,0);
    var todayStr = today.toISOString().split('T')[0];

    Utils.modal('Nuevo Contrato',
      '<form id="form-contrato">' +

        // --- Apartamento ---
        '<div class="form-group">' +
          '<label>Apartamento</label>' +
          '<select id="con-apt" class="form-control" onchange="Contratos.onApartamentoChange()">' +
            '<option value="">Seleccione un apartamento...</option>' + aptOpts +
          '</select>' +
          '<span class="field-error" id="con-apt-error"></span>' +
        '</div>' +

        // --- Info del apartamento seleccionado ---
        '<div id="con-apt-info" style="display:none;margin-bottom:12px;padding:10px 14px;' +
          'background:var(--surface-container,#f3f4f6);border-radius:8px;border:1px solid var(--border,#e5e7eb);' +
          'font-size:13px;color:var(--text-secondary)">' +
        '</div>' +

        // --- Residente ---
        '<div class="form-group" style="margin-top:4px">' +
          '<label>Residente Arrendatario</label>' +
          '<select id="con-residente" class="form-control" onchange="Contratos.chequearResidenteMenor()">' +
            '<option value="">Seleccione un residente...</option>' + resOpts +
          '</select>' +
          '<span class="field-error" id="con-residente-error"></span>' +
        '</div>' +

        // --- Ficha del residente que firma ---
        '<div id="con-residente-card" style="display:none;margin-bottom:12px;padding:12px 14px;' +
          'background:var(--surface-container,#f3f4f6);border-radius:8px;border:1px solid var(--border,#e5e7eb)">' +
          '<p style="font-size:12px;font-weight:600;color:var(--text-muted);text-transform:uppercase;' +
            'letter-spacing:.05em;margin-bottom:8px">Datos del firmante</p>' +
          '<div id="con-residente-card-body" style="display:grid;grid-template-columns:1fr 1fr;gap:6px 20px;font-size:13px"></div>' +
        '</div>' +

        // --- Menor de edad / tutor ---
        '<div id="con-tutor-section" class="hidden" style="margin-bottom:12px;padding:12px;' +
          'background:#fffbeb;border-radius:8px;border:1px solid #fcd34d">' +
          '<p style="font-weight:600;font-size:13px;margin-bottom:6px">' +
            '<span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle;margin-right:4px">family_history</span>' +
            ' El residente es menor de edad. El contrato ser\u00e1 firmado por:' +
          '</p>' +
          '<p style="font-size:13px;color:var(--text-secondary)" id="con-tutor-info">Cargando datos del tutor...</p>' +
        '</div>' +

        // --- Tipo de contrato ---
        '<div class="form-group">' +
          '<label>Tipo de Contrato</label>' +
          '<select id="con-tipo" class="form-control" onchange="Contratos.calcularFechaFin()">' +
            '<option value="INICIAL">Inicial \u2014 3 meses (per\u00edodo de prueba)</option>' +
            '<option value="RENOVACION">Renovaci\u00f3n \u2014 6 meses (buen comportamiento)</option>' +
            '<option value="PERMANENCIA">Permanencia \u2014 sin fecha de vencimiento</option>' +
          '</select>' +
          '<div id="con-tipo-sugerencia" style="font-size:12px;color:var(--accent);margin-top:4px;display:none;">' +
            '<span class="material-symbols-outlined" style="font-size:14px;vertical-align:middle;">lightbulb</span> ' +
            'Tipo sugerido seg\u00fan historial: <strong id="con-tipo-sugerido-text">-</strong>' +
          '</div>' +
        '</div>' +

        // --- Fechas ---
        '<div class="form-row">' +
          '<div class="form-group">' +
            '<label>Fecha de Inicio <small style="color:var(--text-muted)">(d\u00eda de firma)</small></label>' +
            '<input type="date" id="con-fecha-inicio" class="form-control"' +
              ' min="' + todayStr + '" value="' + todayStr + '" onchange="Contratos.calcularFechaFin()">' +
            '<span class="field-error" id="con-fecha-inicio-error"></span>' +
          '</div>' +
          '<div class="form-group">' +
            '<label>Fecha de Fin <small style="color:var(--text-muted)">(seg\u00fan tipo)</small></label>' +
            '<input type="date" id="con-fecha-fin" class="form-control">' +
            '<span class="field-error" id="con-fecha-fin-error"></span>' +
          '</div>' +
        '</div>' +

        // --- Valor mensual ---
        '<div class="form-group">' +
          '<label>Valor Mensual (Canon de Arriendo)</label>' +
          '<input type="number" id="con-valor" class="form-control" step="1" min="0" placeholder="Se autocompleta al seleccionar apartamento">' +
          '<small id="con-valor-helper" style="color:var(--accent);display:none;margin-top:3px;display:block"></small>' +
          '<span class="field-error" id="con-valor-error"></span>' +
        '</div>' +

        // --- Notas ---
        '<div class="form-group">' +
          '<label>Notas / Observaciones</label>' +
          '<textarea id="con-notas" class="form-control" maxlength="1000" rows="3" placeholder="Observaciones adicionales..."></textarea>' +
        '</div>' +

      '</form>',
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-guardar-contrato" onclick="Contratos.guardar()">Crear Contrato</button>'
    );

    // Calcular fecha fin al abrir (con INICIAL por defecto)
    setTimeout(function() { calcularFechaFin(); }, 80);
  }

  // --- Cuando cambia el apartamento: auto-fill valor + sugerir tipo ---
  async function onApartamentoChange() {
    autoFillValorMensual();
    mostrarInfoApartamento();
    await sugerirTipo();
  }

  function mostrarInfoApartamento() {
    var aptSel = document.getElementById('con-apt');
    var infoDiv = document.getElementById('con-apt-info');
    if (!aptSel || !infoDiv) return;
    if (!aptSel.value) { infoDiv.style.display = 'none'; return; }
    var apto = aptsData.find(function(a) { return String(a.idApartamento) === String(aptSel.value); });
    if (!apto) { infoDiv.style.display = 'none'; return; }
    var estadoColor = apto.estado === 'DISPONIBLE' ? 'var(--success,#16a34a)' :
                      apto.estado === 'OCUPADO' ? 'var(--danger,#dc2626)' : 'var(--warn,#d97706)';
    infoDiv.style.display = 'block';
    infoDiv.innerHTML =
      '<span class="material-symbols-outlined" style="font-size:15px;vertical-align:middle;margin-right:4px">apartment</span>' +
      '<strong>Apto ' + apto.numero + '</strong> &nbsp;&bull;&nbsp; ' +
      'Piso: <strong>' + apto.piso + '</strong> &nbsp;&bull;&nbsp; ' +
      'Tipo: <strong>' + (apto.tipo || '-') + '</strong> &nbsp;&bull;&nbsp; ' +
      'Estado: <strong style="color:' + estadoColor + '">' + (apto.estado || '-') + '</strong>' +
      (apto.administracion ? ' &nbsp;&bull;&nbsp; Cuota adm.: <strong>$' + Number(apto.administracion).toLocaleString('es-CO') + '</strong>' : '');
  }

  function autoFillValorMensual() {
    var aptSel = document.getElementById('con-apt');
    var valorInput = document.getElementById('con-valor');
    var helper = document.getElementById('con-valor-helper');
    if (!aptSel || !valorInput || !aptSel.value) return;
    var apto = aptsData.find(function(a) { return String(a.idApartamento) === String(aptSel.value); });
    if (apto && apto.administracion != null) {
      valorInput.value = Number(apto.administracion);
      if (helper) {
        helper.textContent = 'Valor sugerido seg\u00fan la cuota de administraci\u00f3n del apartamento (' + (apto.tipo || '') + '). Puede ajustarlo.';
        helper.style.display = 'block';
      }
    } else {
      if (helper) helper.style.display = 'none';
    }
  }

  async function sugerirTipo() {
    var aptSel = document.getElementById('con-apt');
    var tipoSel = document.getElementById('con-tipo');
    var sugerenciaDiv = document.getElementById('con-tipo-sugerencia');
    var sugeridoText = document.getElementById('con-tipo-sugerido-text');
    if (!aptSel || !tipoSel || !sugerenciaDiv || !sugeridoText || !aptSel.value) {
      if (sugerenciaDiv) sugerenciaDiv.style.display = 'none';
      calcularFechaFin();
      return;
    }
    try {
      var tipo = await API.get('/contratos/sugerir-tipo/' + aptSel.value);
      if (tipo && tipo.tipoSugerido) {
        tipoSel.value = tipo.tipoSugerido;
        sugeridoText.textContent = tipo.tipoSugerido;
        sugerenciaDiv.style.display = 'block';
      } else {
        sugerenciaDiv.style.display = 'none';
      }
    } catch(e) {
      sugerenciaDiv.style.display = 'none';
    }
    calcularFechaFin();
  }

  function calcularFechaFin() {
    var tipoSel = document.getElementById('con-tipo');
    var inicioInput = document.getElementById('con-fecha-inicio');
    var finInput = document.getElementById('con-fecha-fin');
    if (!tipoSel || !inicioInput || !finInput) return;
    var tipo = tipoSel.value;
    var inicio = new Date(inicioInput.value + 'T12:00:00');
    if (tipo === 'PERMANENCIA') {
      finInput.value = '';
      finInput.disabled = true;
      finInput.placeholder = 'Sin fecha de vencimiento';
    } else {
      finInput.disabled = false;
      finInput.placeholder = '';
      var meses = (tipo === 'INICIAL') ? 3 : 6;
      var fin = new Date(inicio);
      fin.setMonth(fin.getMonth() + meses);
      var finStr = fin.toISOString().split('T')[0];
      finInput.value = finStr;
      finInput.setAttribute('min', finStr);
    }
  }

  async function chequearResidenteMenor() {
    var sel = document.getElementById('con-residente');
    var section = document.getElementById('con-tutor-section');
    var info = document.getElementById('con-tutor-info');
    var card = document.getElementById('con-residente-card');
    var cardBody = document.getElementById('con-residente-card-body');
    if (!sel || !section || !info) return;
    if (!sel.value) {
      section.classList.add('hidden');
      if (card) card.style.display = 'none';
      return;
    }
    try {
      var r = await API.get('/residentes/' + sel.value);

      // Mostrar ficha del residente (datos que se usan en la plantilla del contrato)
      if (card && cardBody) {
        card.style.display = 'block';
        var campo = function(label, val) {
          return '<div><span style="font-size:11px;color:var(--text-muted);display:block">' + label + '</span>'
               + '<strong style="font-size:13px">' + (val || '<em style=\"color:var(--text-muted)\">Sin dato</em>') + '</strong></div>';
        };
        cardBody.innerHTML =
          campo('Nombre completo', (r.nombres || '') + ' ' + (r.apellidos || '')) +
          campo('Documento', (r.tipoDocumento || 'CC') + ': ' + (r.numeroDocumento || '-')) +
          campo('Email', r.email || '-') +
          campo('Tel\u00e9fono', r.telefono || '-') +
          (r.esMenorEdad ? campo('Edad', '<span style="color:var(--warn,#d97706)">Menor de edad</span>') : '');
      }

      // Si es menor de edad, mostrar datos del tutor
      if (r.esMenorEdad && r.tutor) {
        section.classList.remove('hidden');
        info.textContent = r.tutor.nombres + ' ' + r.tutor.apellidos
          + ' (' + (r.tutor.parentesco || 'Tutor') + ')'
          + ' \u2014 Doc: ' + (r.tutor.numeroDocumento || '-');
      } else {
        section.classList.add('hidden');
      }
    } catch(e) {
      section.classList.add('hidden');
      if (card) card.style.display = 'none';
    }
  }

  function actualizarMinFechaFin() {
    calcularFechaFin();
  }

  async function guardar() {
    Utils.limpiarErrores('form-contrato');
    if (!Utils.valSelect(document.getElementById('con-apt').value, 'con-apt', 'Seleccione el apartamento')) return;
    if (!Utils.valSelect(document.getElementById('con-residente').value, 'con-residente', 'Seleccione el residente')) return;
    if (!Utils.valFecha(document.getElementById('con-fecha-inicio').value, 'con-fecha-inicio', 'La fecha de inicio')) return;
    if (!Utils.valNumero(document.getElementById('con-valor').value, 'con-valor', { positive: true, label: 'El valor mensual' })) return;
    var fechaInicio = document.getElementById('con-fecha-inicio').value;
    var fechaFin = document.getElementById('con-fecha-fin').value;
    var today = new Date(); today.setHours(0,0,0,0);
    var todayStr = today.toISOString().split('T')[0];
    if (fechaInicio < todayStr) { Utils.mostrarError('con-fecha-inicio', 'La fecha de inicio no puede ser anterior a hoy'); return; }
    var tipoContrato = document.getElementById('con-tipo').value;
    if (tipoContrato !== 'PERMANENCIA' && fechaFin && fechaFin < fechaInicio) {
      Utils.mostrarError('con-fecha-fin', 'La fecha de fin debe ser posterior a la de inicio');
      return;
    }
    var idResidente = parseInt(document.getElementById('con-residente').value);
    var idTutor = null;
    try {
      var r = await API.get('/residentes/' + idResidente);
      if (r.esMenorEdad && r.tutor && r.tutor.idTutor) idTutor = r.tutor.idTutor;
    } catch(e) {}
    const d = {
      idApartamento: parseInt(document.getElementById('con-apt').value),
      idResidente: idResidente,
      idTutor: idTutor,
      tipoContrato: tipoContrato,
      fechaInicio: fechaInicio,
      fechaFin: fechaFin || null,
      valorMensual: parseFloat(document.getElementById('con-valor').value),
      notas: document.getElementById('con-notas').value.trim()
    };
    var btn = document.getElementById('btn-guardar-contrato');
    if (btn) btn.disabled = true;
    try {
      await API.post('/contratos', d);
      Utils.showToast('Contrato creado', 'success');
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function descargarPDF(id) {
    var token = Auth.getToken();
    if (!token) { Utils.showToast('Sesión expirada, inicia sesión de nuevo', 'error'); return; }
    var base = (window._API_BASE_URL || (location.protocol === 'file:' ? 'http://localhost:8080/api' : '/api')).replace(/\/+$/, '');
    try {
      var response = await fetch(base + '/contratos/' + id + '/pdf', {
        headers: { 'Authorization': 'Bearer ' + token }
      });
      if (!response.ok) {
        var err = await response.json().catch(function(){ return {error: 'Error ' + response.status}; });
        Utils.showToast(err.error || 'Error al descargar PDF', 'error');
        return;
      }
      var blob = await response.blob();
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'contrato_' + id + '.pdf';
      document.body.appendChild(a);
      a.click();
      setTimeout(function() { document.body.removeChild(a); URL.revokeObjectURL(url); }, 100);
    } catch (e) {
      Utils.showToast('Error de conexión al descargar PDF', 'error');
    }
  }

  async function activar(id) {
    if (!(await Utils.showConfirm('Desea activar este contrato?'))) return;
    try { await API.post('/contratos/' + id + '/activar'); Utils.showToast('Contrato activado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function cancelar(id) {
    if (!(await Utils.showConfirm('Desea cancelar este contrato?'))) return;
    try { await API.post('/contratos/' + id + '/cancelar'); Utils.showToast('Contrato cancelado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function eliminar(id) {
    if (!(await Utils.showConfirm('Desea eliminar este contrato?'))) return;
    try { await API.del('/contratos/' + id); Utils.showToast('Contrato eliminado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function mostrarRenovar(id) {
    var today = new Date(); today.setHours(0,0,0,0);
    var todayStr = today.toISOString().split('T')[0];
    // Fecha fin por defecto: +6 meses (tipo RENOVACION)
    var fin6m = new Date(today); fin6m.setMonth(fin6m.getMonth() + 6);
    var fin6mStr = fin6m.toISOString().split('T')[0];

    // Cargar datos del contrato vencido para pre-rellenar
    var contrato = null;
    try { contrato = await API.get('/contratos/' + id); } catch(e) {}

    var infoHtml = '';
    var valorActual = '';
    if (contrato) {
      var nombreApt  = contrato.numeroApartamento || ('Apto #' + contrato.idApartamento);
      var nombreRes  = contrato.nombreResidente || '-';
      var valorViejo = contrato.valorMensual ? Utils.formatCurrency(contrato.valorMensual) : '-';
      valorActual    = contrato.valorMensual ? Number(contrato.valorMensual) : '';
      infoHtml =
        '<div style="margin-bottom:14px;padding:10px 14px;background:var(--surface-container,#f3f4f6);' +
        'border-radius:8px;border:1px solid var(--border,#e5e7eb);font-size:13px">' +
          '<span class="material-symbols-outlined" style="font-size:15px;vertical-align:middle;margin-right:4px">info</span>' +
          '<strong>' + nombreApt + '</strong> &nbsp;&bull;&nbsp; ' +
          'Residente: <strong>' + nombreRes + '</strong>' +
          (valorViejo !== '-' ? ' &nbsp;&bull;&nbsp; Valor anterior: <strong>' + valorViejo + '</strong>' : '') +
        '</div>';
    }

    Utils.modal('Renovar Contrato #' + id,
      '<form id="form-renovar">' +
        infoHtml +
        '<p style="margin-bottom:14px;color:var(--text-secondary);font-size:13px">' +
          'Se crear\u00e1 un <strong>nuevo contrato de Renovaci\u00f3n</strong>. El contrato vencido queda en el historial.' +
        '</p>' +
        '<div class="form-row">' +
          '<div class="form-group"><label>Nueva Fecha de Inicio</label>' +
            '<input type="date" id="ren-fecha-inicio" class="form-control"' +
            ' value="' + todayStr + '" min="' + todayStr + '" onchange="Contratos.calcularFechaFinRenovar()">' +
            '<span class="field-error" id="ren-fecha-inicio-error"></span></div>' +
          '<div class="form-group"><label>Nueva Fecha de Fin <small style="color:var(--text-muted)">(+6 meses sugerido)</small></label>' +
            '<input type="date" id="ren-fecha-fin" class="form-control" value="' + fin6mStr + '">' +
            '<span class="field-error" id="ren-fecha-fin-error"></span></div>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Valor Mensual</label>' +
          '<input type="number" id="ren-valor" class="form-control" step="1" min="0"' +
          ' value="' + valorActual + '" placeholder="Monto del arriendo...">' +
          '<span class="field-error" id="ren-valor-error"></span>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Notas / Observaciones</label>' +
          '<textarea id="ren-notas" class="form-control" rows="2" placeholder="Observaciones adicionales..."></textarea>' +
        '</div>' +
      '</form>',
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-renovar" onclick="Contratos.renovar(' + id + ')">Crear Renovaci\u00f3n</button>');
  }

  function calcularFechaFinRenovar() {
    var inicioInput = document.getElementById('ren-fecha-inicio');
    var finInput    = document.getElementById('ren-fecha-fin');
    if (!inicioInput || !finInput || !inicioInput.value) return;
    var inicio = new Date(inicioInput.value + 'T12:00:00');
    var fin = new Date(inicio);
    fin.setMonth(fin.getMonth() + 6);
    finInput.value = fin.toISOString().split('T')[0];
  }

  async function renovar(id) {
    Utils.limpiarErrores('form-renovar');
    var fechaInicio = document.getElementById('ren-fecha-inicio').value;
    var fechaFin    = document.getElementById('ren-fecha-fin').value;
    var valorInput  = document.getElementById('ren-valor');
    if (!Utils.valFecha(fechaInicio, 'ren-fecha-inicio', 'La fecha de inicio')) return;
    if (fechaFin && fechaFin < fechaInicio) {
      Utils.mostrarError('ren-fecha-fin', 'Debe ser posterior a la fecha de inicio');
      return;
    }
    if (valorInput && valorInput.value && parseFloat(valorInput.value) <= 0) {
      Utils.mostrarError('ren-valor', 'El valor mensual debe ser mayor que 0');
      return;
    }
    var payload = {
      fechaInicio: fechaInicio,
      fechaFin: fechaFin || null,
      notas: (document.getElementById('ren-notas') || {value: ''}).value.trim() || null
    };
    if (valorInput && valorInput.value) {
      payload.valorMensual = parseFloat(valorInput.value);
    }
    var btn = document.getElementById('btn-renovar');
    if (btn) btn.disabled = true;
    try {
      await API.post('/contratos/' + id + '/renovar', payload);
      Utils.showToast('Renovaci\u00f3n creada exitosamente', 'success');
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  return { cargar, render, mostrarFormulario, guardar, activar, cancelar, eliminar, renovar, mostrarRenovar,
           goToPage, chequearResidenteMenor, actualizarMinFechaFin, sugerirTipo, calcularFechaFin,
           calcularFechaFinRenovar, descargarPDF, onApartamentoChange, autoFillValorMensual, mostrarInfoApartamento };
})();

Router.register('contratos', {
  html: document.getElementById('tpl-contratos').innerHTML,
  js: () => { document.getElementById('page-title').textContent = 'Contratos'; Contratos.cargar(); }
});
