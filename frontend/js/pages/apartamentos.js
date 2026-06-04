const Apartamentos = (() => {
  let data = [];
  let _allData = [];
  let editingId = null;
  const PAGE_SIZE = 15;
  let currentPage = 1;

  const TIPOS = ['ESTUDIO', '1HAB', '2HAB', '3HAB', 'PENTHOUSE', 'OTRO'];

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-apartamentos');
    const pag = document.getElementById('pagination-apartamentos');
    if (!tbody) return;
    const pg = Utils.paginate(data, currentPage, PAGE_SIZE);
    tbody.innerHTML = pg.items.map(a => {
      var residentesText = (a.cantidadResidentes || 0) + ' / ' + (a.capacidadMaxima || 2);
      var residentesColor = (a.cantidadResidentes || 0) >= (a.capacidadMaxima || 2) ? 'var(--danger)' : 'var(--success)';
      
      return '<tr>' +
        '<td>' + a.idApartamento + '</td>' +
        '<td>' + (a.numero || '') + '</td>' +
        '<td>' + (a.piso || '') + '</td>' +
        '<td>' + (a.tipo || '') + '</td>' +
        '<td>' + (a.areaM2 || '') + '</td>' +
        '<td>' + Utils.estadoBadge(a.estado || 'DISPONIBLE') + '</td>' +
        '<td style="text-align:center;font-weight:600;color:' + residentesColor + '">' + residentesText + '</td>' +
        '<td class="actions-cell">' +
        (a.estado === 'OCUPADO' ? '<button class="btn btn-outline btn-sm" onclick="Apartamentos.verDescripcion(' + a.idApartamento + ')" title="Ver residentes"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">info</span></button>' : '') +
        '<button class="btn btn-primary btn-sm" onclick="Apartamentos.editar(' + a.idApartamento + ')" title="Editar"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">edit</span></button>' +
        '<button class="btn btn-danger btn-sm" onclick="Apartamentos.eliminar(' + a.idApartamento + ')" title="Eliminar"><span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">delete</span></button>' +
        '</td></tr>';
    }).join('');
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Apartamentos.goToPage');
  }

  async function cargar() {
    try {
      data = await API.get('/apartamentos');
      _allData = data.slice();
      var tblContainer = document.querySelector('.table-container');
      if (tblContainer && !document.getElementById('search-apartamentos')) {
        tblContainer.insertAdjacentHTML('beforebegin', Utils.buscadorHtml('search-apartamentos', 'Buscar por n\u00famero, piso, torre o estado...'));
        Utils.crearBuscador('search-apartamentos', _allData, ['numero', 'piso', 'torre', 'estado'], function(f) { data = f; currentPage = 1; render(); });
      }
      currentPage = 1; render();
    } catch (e) { Utils.showAlert('Error', e.message, 'error'); }
  }

  var AREAS_POR_TIPO = {
    'ESTUDIO': 35, '1HAB': 50, '2HAB': 70, '3HAB': 90, 'PENTHOUSE': 120, 'OTRO': ''
  };

  var CAPACIDADES_POR_TIPO = {
    'ESTUDIO': 2, '1HAB': 3, '2HAB': 5, '3HAB': 7, 'PENTHOUSE': 8, 'OTRO': 2
  };

  function generarPisosOpts(selected) {
    var opts = '';
    for (var i = 1; i <= 15; i++) {
      opts += '<option value="' + i + '"' + (selected == i ? ' selected' : '') + '>Piso ' + i + '</option>';
    }
    return opts;
  }

  function generarAptOpts(piso, selected) {
    var opts = '';
    for (var i = 1; i <= 4; i++) {
      var val = String(i).padStart(2, '0');
      var fullNum = String(piso) + val;
      opts += '<option value="' + val + '"'
        + (selected == val ? ' selected' : '')
        + '>' + fullNum + '</option>';
    }
    return opts;
  }

  function actualizarNumeroApt() {
    var piso = document.getElementById('apt-piso');
    var apt = document.getElementById('apt-apt');
    var numero = document.getElementById('apt-numero');
    if (!piso || !apt || !numero) return;
    if (piso.value && apt.value) {
      numero.value = piso.value + String(apt.value);
    } else {
      numero.value = '';
    }
  }

  function rellenarPisosApt(pisoVal, aptVal) {
    var aptSel = document.getElementById('apt-apt');
    if (!aptSel) return;
    aptSel.innerHTML = '<option value="">Apto...</option>' + generarAptOpts(pisoVal || 1, aptVal || '');
    actualizarNumeroApt();
  }

  function mostrarFormulario(a) {
    editingId = a ? a.idApartamento : null;
    const isEdit = !!editingId;
    const tiposOpts = TIPOS.map(t => `<option value="${t}" ${a && a.tipo === t ? 'selected' : ''}>${t}</option>`).join('');

    // For edit mode, parse existing numero into piso + apt
    var pisoInicial = a ? (a.piso || 1) : 1;
    var aptInicial = '';
    if (a && a.numero) {
      var numStr = String(a.numero);
      aptInicial = numStr.length >= 2 ? numStr.slice(-2) : numStr;
    }

    Utils.modal(isEdit ? 'Editar Apartamento' : 'Nuevo Apartamento',
      '<form id="form-apartamento">' +
        '<div class="form-row">' +
          '<div class="form-group" style="flex:1">' +
            '<label>Piso</label>' +
            '<select id="apt-piso" class="form-control" onchange="Apartamentos.onPisoChange()">' +
              '<option value="">Seleccione piso...</option>' +
              generarPisosOpts(isEdit ? pisoInicial : null) +
            '</select>' +
            '<span class="field-error" id="apt-piso-error"></span>' +
          '</div>' +
          '<div class="form-group" style="flex:1">' +
            '<label>Apartamento</label>' +
            '<select id="apt-apt" class="form-control" onchange="Apartamentos.onAptChange()">' +
              (isEdit ? generarAptOpts(pisoInicial, aptInicial) : '<option value="">Primero seleccione piso...</option>') +
            '</select>' +
            '<input type="hidden" id="apt-numero" value="' + (a ? a.numero || '' : '') + '">' +
            '<span class="field-error" id="apt-numero-error"></span>' +
          '</div>' +
        '</div>' +
        '<div class="form-row">' +
          '<div class="form-group" style="flex:1">' +
            '<label>Tipo</label>' +
            '<select id="apt-tipo" class="form-control" onchange="Apartamentos.onTipoChange()">' +
              '<option value="">Seleccione tipo...</option>' +
              tiposOpts +
            '</select>' +
            '<span class="field-error" id="apt-tipo-error"></span>' +
          '</div>' +
          '<div class="form-group" style="flex:1">' +
            '<label>Area (m²)</label>' +
            '<input type="number" id="apt-area" class="form-control" step="0.01" value="' + (a ? a.areaM2 || '' : '') + '">' +
            '<span class="field-error" id="apt-area-error"></span>' +
          '</div>' +
        '</div>' +
        '<div class="form-row">' +
          '<div class="form-group" style="flex:1">' +
            '<label>Capacidad Maxima de Residentes</label>' +
            '<input type="number" id="apt-capacidad" class="form-control" min="1" max="8" value="' + (a ? a.capacidadMaxima || 2 : 2) + '">' +
            '<span class="field-error" id="apt-capacidad-error"></span>' +
            '<small class="text-muted" id="apt-capacidad-hint">Capacidad recomendada segun tipo de apartamento</small>' +
          '</div>' +
          '<div class="form-group" style="flex:1">' +
            '<label>Estado</label>' +
            '<select id="apt-estado" class="form-control">' +
              '<option value="DISPONIBLE"' + (a && a.estado === 'DISPONIBLE' ? ' selected' : '') + '>Disponible</option>' +
              '<option value="OCUPADO"' + (a && a.estado === 'OCUPADO' ? ' selected' : '') + '>Ocupado</option>' +
              '<option value="EN_MANTENIMIENTO"' + (a && a.estado === 'EN_MANTENIMIENTO' ? ' selected' : '') + '>Mantenimiento</option>' +
            '</select>' +
            '<span class="field-error" id="apt-estado-error"></span>' +
          '</div>' +
        '</div>' +
      '</form>',
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-guardar-apartamento" onclick="Apartamentos.guardar()">' + (isEdit ? 'Actualizar' : 'Guardar') + '</button>');

    if (!isEdit && pisoInicial) {
      // For new, set default piso=1 and populate apt options
      var pisoSel = document.getElementById('apt-piso');
      if (pisoSel) { pisoSel.value = '1'; rellenarPisosApt(1); }
    }
    // For edit mode, also trigger area/capacidad auto-fill
    setTimeout(function() {
      var tipoSel = document.getElementById('apt-tipo');
      if (tipoSel && a && a.tipo) tipoSel.value = a.tipo;
      actualizarAreaCapacidad();
    }, 50);
  }

  function onPisoChange() {
    var piso = document.getElementById('apt-piso');
    if (!piso || !piso.value) return;
    rellenarPisosApt(parseInt(piso.value));
  }

  function onAptChange() {
    actualizarNumeroApt();
  }

  function onTipoChange() {
    actualizarAreaCapacidad();
  }

  function actualizarAreaCapacidad() {
    var tipo = document.getElementById('apt-tipo');
    var area = document.getElementById('apt-area');
    var cap = document.getElementById('apt-capacidad');
    var hint = document.getElementById('apt-capacidad-hint');
    if (!tipo || !area || !cap || !hint) return;
    var t = tipo.value;
    // Auto-fill area unless editing
    if (!editingId && AREAS_POR_TIPO[t] !== undefined) {
      area.value = AREAS_POR_TIPO[t];
    }
    // Auto-fill capacity
    var capRec = CAPACIDADES_POR_TIPO[t] || 2;
    if (!editingId) cap.value = capRec;
    cap.max = capRec;
    hint.textContent = 'Capacidad maxima permitida: ' + capRec + ' personas';
    hint.style.color = 'var(--primary)';
    hint.style.fontWeight = '500';
  }

  async function guardar() {
    Utils.limpiarErrores('form-apartamento');
    if (!Utils.valSelect(document.getElementById('apt-piso').value, 'apt-piso', 'Seleccione el piso')) return;
    if (!Utils.valSelect(document.getElementById('apt-apt').value, 'apt-apt', 'Seleccione el n\u00famero del apartamento')) return;
    if (!Utils.valSelect(document.getElementById('apt-tipo').value, 'apt-tipo', 'Seleccione el tipo')) return;
    if (!Utils.valNumero(document.getElementById('apt-area').value, 'apt-area', { positive: true, label: 'El área' })) return;
    
    const tipo = document.getElementById('apt-tipo').value;
    const capacidadesPorTipo = { 'ESTUDIO': 2, '1HAB': 3, '2HAB': 5, '3HAB': 7, 'PENTHOUSE': 8, 'OTRO': 2 };
    const maxCapacidad = capacidadesPorTipo[tipo] || 8;
    
    if (!Utils.valEntero(document.getElementById('apt-capacidad').value, 'apt-capacidad', { min: 1, max: maxCapacidad, label: 'La capacidad máxima' })) return;

    var numero = document.getElementById('apt-numero').value.trim();
    // Validar que no exista ya un apartamento con el mismo numero
    var duplicado = data.some(function(a) {
      return a.numero === numero && (!editingId || a.idApartamento !== editingId);
    });
    if (duplicado) {
      Utils.mostrarError('apt-numero', 'Ya existe un apartamento con el n\u00famero ' + numero);
      return;
    }
    
    const d = {
      numero: numero,
      piso: parseInt(document.getElementById('apt-piso').value),
      tipo: document.getElementById('apt-tipo').value,
      areaM2: parseFloat(document.getElementById('apt-area').value),
      capacidadMaxima: parseInt(document.getElementById('apt-capacidad').value),
      estado: document.getElementById('apt-estado').value
    };
    var btn = document.getElementById('btn-guardar-apartamento');
    if (btn) btn.disabled = true;
    try {
      if (editingId) { await API.put('/apartamentos/' + editingId, d); Utils.showToast('Apartamento actualizado', 'success'); }
      else { await API.post('/apartamentos', d); Utils.showToast('Apartamento creado', 'success'); }
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function editar(id) {
    try { mostrarFormulario(await API.get('/apartamentos/' + id)); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function eliminar(id) {
    if (!(await Utils.confirmarConPassword('\u00bfEst\u00e1 seguro de eliminar este apartamento? Esta acci\u00f3n es permanente y no se puede deshacer.'))) return;
    try { await API.del('/apartamentos/' + id); Utils.showToast('Apartamento eliminado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function verDescripcion(idApartamento) {
    try {
      var apartamento = await API.get('/apartamentos/' + idApartamento);
      var residentes = await API.get('/residentes?idApartamento=' + idApartamento);
      
      var overlay = document.createElement('div');
      overlay.id = 'modal-descripcion-apartamento';
      overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:18000;display:flex;align-items:center;justify-content:center;padding:16px';
      overlay.onclick = function(e) { if (e.target === overlay) overlay.remove(); };
      
      var html = '<div style="background:#fff;border-radius:16px;max-width:700px;width:100%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.25)">' +
        '<div style="padding:24px 24px 20px;background:linear-gradient(135deg, var(--navy-50) 0%, var(--surface) 100%);border-bottom:1px solid var(--border)">' +
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:8px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:var(--navy-500);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">domain</span></div>' +
        '<div><h3 style="margin:0;font-size:18px;font-weight:600">Apartamento ' + Utils.escapeHtml(apartamento.numero || '') + '</h3>' +
        '<p class="text-xs text-muted" style="margin:2px 0 0">Piso ' + (apartamento.piso || '-') + ' • ' + (apartamento.tipo || '') + ' • ' + (apartamento.areaM2 || '-') + ' m²</p></div></div></div>' +
        '<div style="padding:24px">';
      
      if (!residentes || residentes.length === 0) {
        html += '<div class="text-muted" style="text-align:center;padding:40px">' +
          '<span class="material-symbols-outlined" style="font-size:48px;opacity:0.3">person_off</span>' +
          '<p style="margin:8px 0 0">No hay residentes registrados en este apartamento</p></div>';
      } else {
        html += '<h4 style="margin:0 0 16px;font-size:14px;font-weight:600;color:var(--text)">Residentes (' + residentes.length + ')</h4>';
        
        residentes.forEach(function(r, idx) {
          var isTitular = r.esTitular;
          var bgColor = isTitular ? 'var(--accent-50)' : 'var(--surface)';
          var borderColor = isTitular ? 'var(--accent-200)' : 'var(--border)';
          var badge = isTitular ? '<span style="display:inline-block;padding:2px 8px;background:var(--accent);color:#fff;border-radius:4px;font-size:10px;font-weight:600;margin-left:8px">TITULAR</span>' : '';
          
          html += '<div class="card" style="background:' + bgColor + ';border:1px solid ' + borderColor + ';margin-bottom:12px;padding:16px">' +
            '<div style="display:flex;justify-content:space-between;align-items:start;margin-bottom:12px">' +
            '<div style="font-weight:600;font-size:15px;color:var(--text)">' + Utils.escapeHtml((r.nombres || '') + ' ' + (r.apellidos || '')) + badge + '</div>' +
            '<div>' + Utils.estadoBadge(r.estado || 'ACTIVO') + '</div>' +
            '</div>' +
            '<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;font-size:13px">' +
            '<div><span class="text-muted">Documento:</span><br><strong>' + Utils.escapeHtml(r.numeroDocumento || '-') + '</strong></div>' +
            '<div><span class="text-muted">Fecha Nacimiento:</span><br><strong>' + (r.fechaNacimiento ? Utils.formatDate(r.fechaNacimiento) : '-') + '</strong></div>' +
            '<div><span class="text-muted">Teléfono:</span><br><strong>' + Utils.escapeHtml(r.telefono || '-') + '</strong></div>' +
            '<div><span class="text-muted">Email:</span><br><strong style="word-break:break-all">' + Utils.escapeHtml(r.email || '-') + '</strong></div>' +
            '</div>';
          
          if (r.tutor && (r.tutor.nombres || r.tutor.documento)) {
            html += '<div style="margin-top:12px;padding-top:12px;border-top:1px solid var(--border-subtle)">' +
              '<div style="font-size:12px;font-weight:600;color:var(--text-secondary);margin-bottom:6px">Tutor/Responsable</div>' +
              '<div style="font-size:12px;color:var(--text-secondary)">' +
              '<strong>' + Utils.escapeHtml((r.tutor.nombres || '') + ' ' + (r.tutor.apellidos || '')) + '</strong>' +
              (r.tutor.documento ? ' • Doc: ' + Utils.escapeHtml(r.tutor.documento) : '') +
              (r.tutor.telefono ? ' • Tel: ' + Utils.escapeHtml(r.tutor.telefono) : '') +
              '</div></div>';
          }
          
          html += '<div style="margin-top:12px;padding-top:12px;border-top:1px solid var(--border-subtle);display:flex;gap:8px">' +
            '<button class="btn btn-danger btn-sm" onclick="Apartamentos.removerResidente(' + idApartamento + ',' + r.id + ')" title="Quitar residente">' +
            '<span class="material-symbols-outlined" style="font-size:14px;vertical-align:middle">person_remove</span> Quitar</button></div>';

          html += '</div>';
        });
      }
      
      html += '</div>' +
        '<div style="padding:16px 24px;background:var(--navy-50);border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end">' +
        '<button class="btn btn-ghost" onclick="document.getElementById(\'modal-descripcion-apartamento\').remove()">Cerrar</button>' +
        '</div></div>';
      
      overlay.innerHTML = html;
      document.body.appendChild(overlay);
    } catch(e) {
      Utils.showToast('Error al cargar descripción: ' + e.message, 'error');
    }
  }

  async function removerResidente(idApartamento, idResidente) {
    if (!(await Utils.showConfirm('¿Está seguro de eliminar este residente del apartamento?'))) return;
    try {
      await API.del('/apartamentos/' + idApartamento + '/residentes/' + idResidente);
      Utils.showToast('Residente eliminado del apartamento', 'success');
      var overlay = document.getElementById('modal-descripcion-apartamento');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  return { cargar, render, mostrarFormulario, guardar, editar, eliminar, verDescripcion, removerResidente, goToPage,
           onPisoChange, onAptChange, onTipoChange };
})();

Router.register('apartamentos', {
  html: document.getElementById('tpl-apartamentos').innerHTML,
  js: () => { document.getElementById('page-title').textContent = 'Apartamentos'; Apartamentos.cargar(); }
});