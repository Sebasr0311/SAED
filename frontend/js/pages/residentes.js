const Residentes = (() => {
  let data = [];
  let editingId = null;
  const PAGE_SIZE = 15;
  let currentPage = 1;

  function calcularEdad(fechaNacimiento) {
    if (!fechaNacimiento) return 0;
    var nac = new Date(fechaNacimiento + 'T00:00:00');
    var hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    var edad = hoy.getFullYear() - nac.getFullYear();
    var m = hoy.getMonth() - nac.getMonth();
    if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
    return edad;
  }

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-residentes');
    const empty = document.getElementById('residentes-empty');
    const pag = document.getElementById('pagination-residentes');
    if (!tbody) return;
    const pg = Utils.paginate(data, currentPage, PAGE_SIZE);
    tbody.innerHTML = '';
    if (!data.length) {
      if (empty) empty.classList.remove('hidden');
      if (pag) pag.innerHTML = '';
      return;
    }
    if (empty) empty.classList.add('hidden');
    pg.items.forEach(r => {
      var edad = calcularEdad(r.fechaNacimiento);
      var esMenor = edad > 0 && edad < 18;
      var descripcionMenor = '';
      if (esMenor) {
        if (edad >= 16) {
          descripcionMenor = '<span style="display:inline-block;font-size:11px;padding:2px 8px;border-radius:10px;background:#fff3cd;color:#856404;margin-top:2px">Menor independiente (16-17)</span>';
        } else {
          descripcionMenor = '<span style="display:inline-block;font-size:11px;padding:2px 8px;border-radius:10px;background:#f8d7da;color:#721c24;margin-top:2px">Menor - requiere tutor</span>';
        }
      }
      tbody.innerHTML += `<tr>
        <td>${r.id}</td>
        <td>${r.nombres || ''} ${descripcionMenor}</td>
        <td>${r.apellidos || ''}</td>
        <td>${r.numeroDocumento || ''}</td>
        <td>${edad > 0 ? edad : '-'}</td>
        <td>${r.numeroApartamento || '-'}</td>
        <td>${r.telefono || '-'}</td>
        <td>${r.email || '-'}</td>
        <td class="actions-cell">
          <button class="btn btn-primary btn-sm" onclick="Residentes.editar(${r.id})">Editar</button>
          <button class="btn btn-danger btn-sm" onclick="Residentes.eliminar(${r.id})">Eliminar</button>
        </td>
      </tr>`;
    });
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Residentes.goToPage');
  }

  async function cargar() {
    try {
      data = await API.get('/residentes');
      data.sort((a, b) => (parseInt(a.numeroApartamento) || 0) - (parseInt(b.numeroApartamento) || 0));
      currentPage = 1;
      render();
    } catch (e) { Utils.showAlert('Error', e.message, 'error'); }
  }

  function mostrarFormulario(r) {
    editingId = r ? r.id : null;
    const isEdit = !!editingId;
    const tutor = r && r.tutor ? r.tutor : null;
    const body = `
      <form id="form-residente">
        <div class="form-row">
          <div class="form-group">
            <label>Tipo Documento</label>
            <select id="res-tipo-doc" class="form-control">
              <option value="">Seleccione...</option>
            </select>
            <span class="field-error" id="res-tipo-doc-error"></span>
          </div>
          <div class="form-group">
            <label>N\u00famero Documento</label>
            <input type="text" id="res-documento" class="form-control" value="${r ? r.numeroDocumento || '' : ''}">
            <span class="field-error" id="res-documento-error"></span>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Nombres</label>
            <input type="text" id="res-nombres" class="form-control" value="${r ? r.nombres || '' : ''}">
            <span class="field-error" id="res-nombres-error"></span>
          </div>
          <div class="form-group">
            <label>Apellidos</label>
            <input type="text" id="res-apellidos" class="form-control" value="${r ? r.apellidos || '' : ''}">
            <span class="field-error" id="res-apellidos-error"></span>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Tel\u00e9fono</label>
            <input type="text" id="res-telefono" class="form-control" value="${r ? r.telefono || '' : ''}">
            <span class="field-error" id="res-telefono-error"></span>
          </div>
          <div class="form-group">
            <label>Email</label>
            <input type="email" id="res-email" class="form-control" value="${r ? r.email || '' : ''}">
            <span class="field-error" id="res-email-error"></span>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Fecha de Nacimiento</label>
            <input type="date" id="res-fecha-nac" class="form-control" value="${r ? r.fechaNacimiento || '' : ''}" max="${new Date().toISOString().split('T')[0]}" onchange="Residentes.chequearEdad()">
            <span class="field-error" id="res-fecha-nac-error"></span>
          </div>
          <div class="form-group">
            <label>Apartamento</label>
            <input type="text" class="form-control" value="${r ? r.numeroApartamento || 'Sin asignar' : ''}" readonly disabled style="color:var(--text-secondary)">
          </div>
        </div>
        <div id="res-tutor-section" class="hidden" style="margin-top:16px;padding:16px;background:var(--surface-container);border-radius:12px;border:1px solid var(--border)">
          <p style="font-weight:600;font-size:14px;margin-bottom:4px;color:var(--text)"><span class="material-symbols-outlined" style="font-size:18px;vertical-align:middle;margin-right:4px">family_history</span> Datos del Tutor Legal</p>
          <p id="tutor-descripcion" style="font-size:12px;color:var(--text-secondary);margin-bottom:12px"></p>
          <div class="form-row">
            <div class="form-group">
              <label>Tipo Documento</label>
              <select id="tutor-tipo-doc" class="form-control"><option value="">Seleccione...</option></select>
              <span class="field-error" id="tutor-tipo-doc-error"></span>
            </div>
            <div class="form-group">
              <label>N\u00famero Documento</label>
              <input type="text" id="tutor-documento" class="form-control" value="${tutor ? tutor.numeroDocumento || '' : ''}">
              <span class="field-error" id="tutor-documento-error"></span>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Nombres</label>
              <input type="text" id="tutor-nombres" class="form-control" value="${tutor ? tutor.nombres || '' : ''}">
              <span class="field-error" id="tutor-nombres-error"></span>
            </div>
            <div class="form-group">
              <label>Apellidos</label>
              <input type="text" id="tutor-apellidos" class="form-control" value="${tutor ? tutor.apellidos || '' : ''}">
              <span class="field-error" id="tutor-apellidos-error"></span>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Tel\u00e9fono</label>
            <input type="text" id="tutor-telefono" class="form-control" value="${tutor ? tutor.telefono || '' : ''}">
            <span class="field-error" id="tutor-telefono-error"></span>
          </div>
          <div class="form-group">
            <label>Email</label>
            <input type="email" id="tutor-email" class="form-control" value="${tutor ? tutor.email || '' : ''}">
            <span class="field-error" id="tutor-email-error"></span>
          </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Parentesco</label>
              <select id="tutor-parentesco" class="form-control">
                <option value="">Seleccione...</option>
                <option value="PADRE" ${tutor && tutor.parentesco === 'PADRE' ? 'selected' : ''}>Padre</option>
                <option value="MADRE" ${tutor && tutor.parentesco === 'MADRE' ? 'selected' : ''}>Madre</option>
                <option value="ABUELO" ${tutor && tutor.parentesco === 'ABUELO' ? 'selected' : ''}>Abuelo</option>
                <option value="ABUELA" ${tutor && tutor.parentesco === 'ABUELA' ? 'selected' : ''}>Abuela</option>
                <option value="TIO" ${tutor && tutor.parentesco === 'TIO' ? 'selected' : ''}>T\u00edo</option>
                <option value="TIA" ${tutor && tutor.parentesco === 'TIA' ? 'selected' : ''}>T\u00eda</option>
                <option value="HERMANO" ${tutor && tutor.parentesco === 'HERMANO' ? 'selected' : ''}>Hermano</option>
                <option value="HERMANA" ${tutor && tutor.parentesco === 'HERMANA' ? 'selected' : ''}>Hermana</option>
                <option value="TUTOR_LEGAL" ${tutor && tutor.parentesco === 'TUTOR_LEGAL' ? 'selected' : ''}>Tutor Legal</option>
                <option value="OTRO" ${tutor && tutor.parentesco === 'OTRO' ? 'selected' : ''}>Otro</option>
              </select>
              <span class="field-error" id="tutor-parentesco-error"></span>
            </div>
            <div class="form-group"></div>
          </div>
        </div>
      </form>`;

    var modal = Utils.modal(isEdit ? 'Editar Residente' : 'Nuevo Residente', body,
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-guardar-residente" onclick="Residentes.guardar()">' + (isEdit ? 'Actualizar' : 'Guardar') + '</button>');

    cargarTiposDoc(modal);
    if (r) {
      setTimeout(() => document.getElementById('res-tipo-doc').value = r.idTipoDoc || '', 100);
      if (r.fechaNacimiento) {
        setTimeout(() => { chequearEdad(); if (r.esMenorEdad) cargarTutorTiposDoc(); }, 200);
      }
    }
    
    // Event listeners para validación en tiempo real
    setTimeout(() => {
      configurarValidacionTiempoReal();
    }, 300);
  }

  function configurarValidacionTiempoReal() {
    configurarFiltroDocumento('res-documento', 'res-tipo-doc', 'res-tipo-doc');
    configurarFiltroDocumento('tutor-documento', 'tutor-tipo-doc', 'tutor-tipo-doc');
    
    // Filtros para nombres y apellidos (residente)
    Utils.soloLetras('res-nombres', 25);
    Utils.soloLetras('res-apellidos', 25);
    
    // Filtros para nombres y apellidos (tutor)
    Utils.soloLetras('tutor-nombres', 25);
    Utils.soloLetras('tutor-apellidos', 25);
    
    // Filtros para teléfono (residente)
    Utils.soloNumeros('res-telefono', 10);
    Utils.validarTelefonoTiempoReal('res-telefono');
    
    // Filtros para teléfono (tutor)
    Utils.soloNumeros('tutor-telefono', 10);
    Utils.validarTelefonoTiempoReal('tutor-telefono');
    
    // Validación email del tutor en tiempo real
    Utils.validarEmailTiempoReal('tutor-email');
  }

  function configurarFiltroDocumento(inputId, selectId, errorPrefix) {
    var input = document.getElementById(inputId);
    var select = document.getElementById(selectId);
    if (!input || !select) return;

    function aplicarFiltro() {
      var codigo = getTipoDocCodigo(select.value);
      if (!codigo) return;
      var cfg = getConfigDocumento(codigo);
      input.maxLength = cfg.maxLength;
      input.setAttribute('data-filter', cfg.pattern.source);
      input.setAttribute('data-upper', cfg.uppercase ? '1' : '0');
      // Limpiar valor actual si no coincide
      if (input.value) {
        input.value = input.value.replace(new RegExp(cfg.pattern.source, 'g'), '');
        if (cfg.uppercase) input.value = input.value.toUpperCase();
      }
    }

    select.addEventListener('change', function() {
      input.value = '';
      input.classList.remove('is-invalid');
      var errorEl = input.parentNode.querySelector('.field-error');
      if (errorEl) errorEl.textContent = '';
      aplicarFiltro();
    });

    input.addEventListener('input', function(e) {
      var filterSrc = e.target.getAttribute('data-filter');
      if (!filterSrc) return;
      var re = new RegExp(filterSrc, 'g');
      var cleaned = e.target.value.replace(re, '');
      if (e.target.getAttribute('data-upper') === '1') cleaned = cleaned.toUpperCase();
      e.target.value = cleaned;
    });

    input.addEventListener('paste', function(e) {
      e.preventDefault();
      var text = (e.clipboardData || window.clipboardData).getData('text');
      var filterSrc = this.getAttribute('data-filter');
      if (filterSrc) {
        var re = new RegExp(filterSrc, 'g');
        text = text.replace(re, '');
      }
      if (this.getAttribute('data-upper') === '1') text = text.toUpperCase();
      var max = parseInt(this.maxLength, 10);
      if (max && max > 0) text = text.substring(0, max);
      this.value = text;
    });

    input.addEventListener('blur', function() {
      var tipoDocId = select.value;
      if (tipoDocId && this.value.trim()) {
        var tipoDocCodigo = getTipoDocCodigo(tipoDocId);
        Utils.valDocumento(this.value, inputId, tipoDocCodigo);
      }
    });

    // Aplicar filtro inicial si hay un tipo pre-seleccionado o valor existente
    aplicarFiltro();
  }

  function getConfigDocumento(codigo) {
    var cfg = { maxLength: 15, pattern: /[^a-zA-Z0-9-]/g, uppercase: false };
    if (['CC', 'TI', 'RC', 'NIT'].indexOf(codigo) >= 0) {
      cfg.pattern = /[^0-9]/g;
      cfg.uppercase = false;
      if (codigo === 'CC') cfg.maxLength = 10;
      else if (codigo === 'TI') cfg.maxLength = 10;
      else if (codigo === 'RC') cfg.maxLength = 10;
      else if (codigo === 'NIT') cfg.maxLength = 13;
    } else if (codigo === 'CE') {
      cfg.maxLength = 12;
      cfg.pattern = /[^a-zA-Z0-9]/g;
      cfg.uppercase = true;
    } else if (codigo === 'PP' || codigo === 'PASAPORTE') {
      cfg.maxLength = 15;
      cfg.pattern = /[^a-zA-Z0-9]/g;
      cfg.uppercase = true;
    } else if (codigo === 'PEP') {
      cfg.maxLength = 15;
      cfg.pattern = /[^a-zA-Z0-9]/g;
      cfg.uppercase = true;
    }
    return cfg;
  }
  
  function getTipoDocCodigo(idTipoDoc) {
    var select = document.getElementById('res-tipo-doc');
    if (!select) select = document.getElementById('tutor-tipo-doc');
    if (!select) return null;
    
    var option = Array.from(select.options).find(opt => opt.value === String(idTipoDoc));
    if (!option) return null;
    
    // Usar data-codigo si está disponible (más confiable que parsear el texto)
    var codigo = option.getAttribute('data-codigo');
    if (codigo) return codigo;
    
    // Fallback: extraer del texto
    var text = option.textContent.trim();
    var match = text.match(/^([A-Z]{2,9})\s*-/);
    if (match) return match[1];
    
    // Mapeo alternativo basado en descripción
    if (text.includes('Cédula de Ciudadanía') || text.includes('Ciudadan')) return 'CC';
    if (text.includes('Tarjeta de Identidad')) return 'TI';
    if (text.includes('Cédula de Extranjería') || text.includes('Extranjer')) return 'CE';
    if (text.includes('Pasaporte')) return 'PASAPORTE';
    if (text.includes('Permiso Especial')) return 'PEP';
    if (text.includes('Registro Civil')) return 'RC';
    if (text.includes('NIT')) return 'NIT';
    
    return null;
  }

  function chequearEdad() {
    var fn = document.getElementById('res-fecha-nac');
    var section = document.getElementById('res-tutor-section');
    var desc = document.getElementById('tutor-descripcion');
    if (!fn || !section) return;
    if (!fn.value) { section.classList.add('hidden'); return; }
    var nac = new Date(fn.value);
    var hoy = new Date();
    var edad = hoy.getFullYear() - nac.getFullYear();
    var m = hoy.getMonth() - nac.getMonth();
    if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
    if (edad < 18) {
      section.classList.remove('hidden');
      if (edad >= 16) {
        desc.textContent = 'Menor de edad (16-17 a\u00f1os) - Puede residir independientemente, pero debe tener un tutor legal registrado.';
        desc.style.color = 'var(--warning)';
      } else {
        desc.textContent = 'Menor de ' + edad + ' a\u00f1os - Requiere tutor legal obligatorio.';
        desc.style.color = 'var(--danger)';
      }
      cargarTutorTiposDoc();
    } else {
      section.classList.add('hidden');
    }
  }

  async function cargarTiposDoc(modal) {
    try {
      const tipos = await API.get('/tipos-documento');
      const sel = modal.querySelector('#res-tipo-doc');
      if (sel) {
        sel.innerHTML = '<option value="">Seleccione...</option>';
        tipos.forEach(t => {
          var opt = document.createElement('option');
          opt.value = t.idTipoDoc;
          opt.textContent = t.codigo + ' - ' + t.descripcion;
          opt.setAttribute('data-codigo', t.codigo);
          sel.appendChild(opt);
        });
      }
    } catch (e) { /* fallback */ }
  }

  async function cargarTutorTiposDoc() {
    try {
      const tipos = await API.get('/tipos-documento');
      const sel = document.getElementById('tutor-tipo-doc');
      if (sel && sel.options.length <= 1) {
        sel.innerHTML = '<option value="">Seleccione...</option>';
        tipos.forEach(t => {
          var opt = document.createElement('option');
          opt.value = t.idTipoDoc;
          opt.textContent = t.codigo + ' - ' + t.descripcion;
          opt.setAttribute('data-codigo', t.codigo);
          sel.appendChild(opt);
        });
      }
    } catch (e) { /* fallback */ }
  }

  async function guardar() {
    Utils.limpiarErrores('form-residente');
    
    // Validar tipo de documento
    if (!Utils.valSelect(document.getElementById('res-tipo-doc').value, 'res-tipo-doc', 'Seleccione el tipo de documento')) return;
    
    // Validar documento con código de tipo específico
    var tipoDocId = document.getElementById('res-tipo-doc').value;
    var tipoDocCodigo = getTipoDocCodigo(tipoDocId);
    if (!Utils.valDocumento(document.getElementById('res-documento').value, 'res-documento', tipoDocCodigo)) return;
    
    // Validar nombres y apellidos
    if (!Utils.valNombre(document.getElementById('res-nombres').value, 'res-nombres', 'El nombre')) return;
    if (!Utils.valApellido(document.getElementById('res-apellidos').value, 'res-apellidos', 'El apellido')) return;
    
    // Validar contacto
    if (!Utils.valTelefono(document.getElementById('res-telefono').value, 'res-telefono')) return;
    if (!Utils.valEmail(document.getElementById('res-email').value, 'res-email')) return;
    
    // Validar fecha de nacimiento con edad mínima de 1 año (bebés recién nacidos pueden vivir en el edificio)
    if (!Utils.valFechaNacimiento(document.getElementById('res-fecha-nac').value, 'res-fecha-nac', { edadMinima: 1 })) return;

    var fn = document.getElementById('res-fecha-nac').value;
    var esMenor = false;
    if (fn) {
      var nac = new Date(fn + 'T00:00:00');
      var hoy = new Date();
      hoy.setHours(0, 0, 0, 0);
      var edad = hoy.getFullYear() - nac.getFullYear();
      var m = hoy.getMonth() - nac.getMonth();
      if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
      esMenor = edad < 18;
    }

    var tutorSection = esMenor ? document.getElementById('res-tutor-section') : null;
    var conTutor = tutorSection && !tutorSection.classList.contains('hidden');
    if (conTutor) {
      if (!Utils.valSelect(document.getElementById('tutor-tipo-doc').value, 'tutor-tipo-doc', 'Seleccione el tipo de documento del tutor')) return;
      
      // Validar documento del tutor con código específico
      var tutorTipoDocId = document.getElementById('tutor-tipo-doc').value;
      var tutorTipoDocCodigo = getTipoDocCodigo(tutorTipoDocId);
      if (!Utils.valDocumento(document.getElementById('tutor-documento').value, 'tutor-documento', tutorTipoDocCodigo)) return;
      
      if (!Utils.valNombre(document.getElementById('tutor-nombres').value, 'tutor-nombres', 'El nombre del tutor')) return;
      if (!Utils.valApellido(document.getElementById('tutor-apellidos').value, 'tutor-apellidos', 'El apellido del tutor')) return;
      if (!Utils.valTelefono(document.getElementById('tutor-telefono').value, 'tutor-telefono')) return;
      if (!Utils.valEmail(document.getElementById('tutor-email').value, 'tutor-email')) return;
      if (!Utils.valSelect(document.getElementById('tutor-parentesco').value, 'tutor-parentesco', 'Seleccione el parentesco')) return;
    }

    const payload = {
      idTipoDoc: parseInt(tipoDocId),
      numeroDocumento: document.getElementById('res-documento').value.trim(),
      nombres: document.getElementById('res-nombres').value.trim(),
      apellidos: document.getElementById('res-apellidos').value.trim(),
      telefono: document.getElementById('res-telefono').value.trim(),
      email: document.getElementById('res-email').value.trim(),
      fechaNacimiento: fn
    };
    
    // Incluir datos del tutor en la misma petición si aplica
    if (conTutor) {
      payload.tutor = {
        idTipoDoc: parseInt(document.getElementById('tutor-tipo-doc').value),
        numeroDocumento: document.getElementById('tutor-documento').value.trim(),
        nombres: document.getElementById('tutor-nombres').value.trim(),
        apellidos: document.getElementById('tutor-apellidos').value.trim(),
        telefono: document.getElementById('tutor-telefono').value.trim(),
        email: document.getElementById('tutor-email').value.trim(),
        parentesco: document.getElementById('tutor-parentesco').value
      };
    }
    
    var btn = document.getElementById('btn-guardar-residente');
    if (btn) btn.disabled = true;
    try {
      if (editingId) {
        await API.put('/residentes/' + editingId, payload);
        Utils.showToast('Residente actualizado', 'success');
      } else {
        var res = await API.post('/residentes', payload);
        Utils.showToast('Residente creado', 'success');
      }
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function editar(id) {
    try {
      const r = await API.get('/residentes/' + id);
      mostrarFormulario(r);
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function eliminar(id) {
    if (!(await Utils.showConfirm('Desea desactivar este residente?'))) return;
    try {
      await API.del('/residentes/' + id);
      Utils.showToast('Residente desactivado', 'success');
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  return { cargar, render, mostrarFormulario, guardar, editar, eliminar, goToPage, chequearEdad };
})();

Router.register('residentes', {
  html: document.getElementById('tpl-residentes').innerHTML,
  js: () => { document.getElementById('page-title').textContent = 'Residentes'; Residentes.cargar(); }
});