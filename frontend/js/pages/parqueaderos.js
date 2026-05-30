const Parqueaderos = (() => {
  let data = [];
  let editingId = null;
  const PAGE_SIZE = 15;
  let currentPage = 1;
  let _apartamentos = [];

  function esPortero() {
    var user = Auth.getCurrentUser();
    return user && user.rol === 'PORTERO';
  }

  const TIPOS = ['VEHICULO', 'MOTO', 'BICICLETA'];
  const ESTADOS = ['DISPONIBLE', 'OCUPADO', 'EN_MANTENIMIENTO'];

  function prefijoParq(tipo, esVisitante) {
    if (tipo === 'MOTO') return 'M';
    if (tipo === 'BICICLETA') return 'B';
    return esVisitante ? 'V' : 'P';
  }

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-parqueaderos');
    const pag = document.getElementById('pagination-parqueaderos');
    if (!tbody) return;
    var soloLectura = esPortero();
    const pg = Utils.paginate(data, currentPage, PAGE_SIZE);
    tbody.innerHTML = pg.items.map(p => {
      var acciones = soloLectura
        ? '<span class="text-muted text-sm">Solo lectura</span>'
        : '<button class="btn btn-primary btn-sm" onclick="Parqueaderos.editar(' + p.idParqueadero + ')">Editar</button>' +
          '<button class="btn btn-danger btn-sm" onclick="Parqueaderos.eliminar(' + p.idParqueadero + ')">Eliminar</button>';
      return '<tr>' +
        '<td>' + p.idParqueadero + '</td>' +
        '<td>' + (p.codigo || '') + '</td>' +
        '<td>' + (p.tipo || '') + '</td>' +
        '<td>' + (p.esVisitante ? 'Visitante' : 'Residente') + '</td>' +
        '<td>' + Utils.estadoBadge(p.estado || 'DISPONIBLE') + '</td>' +
        '<td>' + (p.numeroApartamento || '-') + '</td>' +
        '<td>' + (p.nombrePropietario || '-') + '</td>' +
        '<td class="actions-cell">' + acciones + '</td>' +
        '</tr>';
    }).join('');
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Parqueaderos.goToPage');
  }

  async function cargar() {
    try { data = await API.get('/parqueaderos'); currentPage = 1; render(); }
    catch (e) { Utils.showAlert('Error', e.message, 'error'); }
  }

  async function aplicarFiltros() {
    const estado = document.getElementById('parq-filtro-estado').value;
    const tipo = document.getElementById('parq-filtro-tipo').value;
    try {
      data = await API.get('/parqueaderos?estado=' + estado + '&tipo=' + tipo);
      currentPage = 1;
      render();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function mostrarFormulario(p) {
    if (esPortero()) return;
    try {
      var todosApts = await API.get('/apartamentos');
      var todosParqs = await API.get('/parqueaderos');
      var aptsConParq = {};
      todosParqs.forEach(function(parq) {
        if (parq.idApartamento) aptsConParq[parq.idApartamento] = true;
      });
      if (p && p.idApartamento) delete aptsConParq[p.idApartamento];
      _apartamentos = todosApts.filter(function(a) {
        return a.estado !== 'DISPONIBLE' && !aptsConParq[a.idApartamento];
      });
    } catch (e) { _apartamentos = []; }
    editingId = p ? p.idParqueadero : null;
    const isEdit = !!editingId;
    var aptOptions = '<option value="">-- Seleccione --</option>';
    _apartamentos.forEach(function(a) {
      aptOptions += '<option value="' + a.idApartamento + '">' + (a.numero || a.idApartamento) + '</option>';
    });
    var residenteChecked = p && !p.esVisitante ? 'checked' : '';
    var visitanteChecked = !p || p.esVisitante ? 'checked' : '';
    var aptValue = p && p.idApartamento ? p.idApartamento : '';
    var aptDisplay = p && !p.esVisitante ? 'style="display:block"' : 'style="display:none"';
    var estadoSection = isEdit
      ? '<div class="form-group"><label>Estado</label><select id="parq-estado" class="form-control">' + ESTADOS.map(function(e) { return '<option value="' + e + '" ' + (p && p.estado === e ? 'selected' : '') + '>' + e.replace(/_/g, ' ') + '</option>'; }).join('') + '</select></div>'
      : '';
    var codigoHtml = isEdit
      ? '<div class="form-group"><label>C\u00f3digo</label><input type="text" id="parq-codigo" class="form-control" value="' + (p.codigo || '') + '" readonly><span class="field-error" id="parq-codigo-error"></span></div>'
      : '<div class="form-group"><label>N\u00famero</label><div style="display:flex;align-items:center;gap:6px"><span id="parq-prefijo" style="font-weight:bold;font-size:1.1em">P</span><input type="number" id="parq-numero" class="form-control" min="1" max="999" placeholder="M\u00e1x 3 d\u00edgitos (1-999)" style="flex:1"><span class="field-error" id="parq-numero-error"></span></div><input type="hidden" id="parq-codigo"></div>';

    Utils.modal(isEdit ? 'Editar Parqueadero' : 'Nuevo Parqueadero',
      '<form id="form-parq">' +
        '<div class="form-row">' +
          codigoHtml +
          '<div class="form-group"><label>Tipo</label><select id="parq-tipo" class="form-control">' + TIPOS.map(function(t) { return '<option value="' + t + '" ' + (p && p.tipo === t ? 'selected' : '') + '>' + t + '</option>'; }).join('') + '</select><span class="field-error" id="parq-tipo-error"></span></div>' +
        '</div>' +
        '<div class="form-row">' +
          estadoSection +
          '<div class="form-group"><label>Uso</label><div style="display:flex;gap:16px;padding-top:6px">' +
            '<label><input type="radio" name="parq-uso" value="visitante" ' + visitanteChecked + ' onchange="Parqueaderos.toggleUso()"> Visitante</label>' +
            '<label><input type="radio" name="parq-uso" value="residente" ' + residenteChecked + ' onchange="Parqueaderos.toggleUso()"> Residente</label>' +
          '</div></div>' +
        '</div>' +
        '<div class="form-group" id="parq-apt-group" ' + aptDisplay + '>' +
          '<label>Apartamento</label>' +
          '<select id="parq-apartamento" class="form-control">' + aptOptions + '</select>' +
          '<span class="field-error" id="parq-apt-error"></span>' +
        '</div>' +
      '</form>',
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-guardar-parq" onclick="Parqueaderos.guardar()">' + (isEdit ? 'Actualizar' : 'Guardar') + '</button>');
    if (!isEdit) {
      document.getElementById('parq-estado') && (document.getElementById('parq-estado').value = 'DISPONIBLE');
      function actualizarPrefijo() {
        var tipo = document.getElementById('parq-tipo').value;
        var uso = document.querySelector('input[name="parq-uso"]:checked');
        var esVisitante = uso ? uso.value === 'visitante' : true;
        document.getElementById('parq-prefijo').textContent = Parqueaderos.prefijoParq(tipo, esVisitante);
      }
      document.getElementById('parq-tipo').addEventListener('change', actualizarPrefijo);
      document.querySelectorAll('input[name="parq-uso"]').forEach(function(el) {
        el.addEventListener('change', actualizarPrefijo);
      });
      actualizarPrefijo();
    }
  }

  function toggleUso() {
    var aptGroup = document.getElementById('parq-apt-group');
    if (!aptGroup) return;
    var residente = document.querySelector('input[name="parq-uso"]:checked');
    aptGroup.style.display = residente && residente.value === 'residente' ? 'block' : 'none';
  }

  async function guardar() {
    if (esPortero()) return;
    Utils.limpiarErrores('form-parq');
    var codigo;
    if (editingId) {
      codigo = document.getElementById('parq-codigo').value.trim();
      if (!codigo) { Utils.mostrarError('parq-codigo', 'El c\u00f3digo es obligatorio'); return; }
    } else {
      var numero = document.getElementById('parq-numero').value;
      if (!numero) { Utils.mostrarError('parq-numero', 'Ingrese el n\u00famero del parqueadero'); return; }
      if (parseInt(numero) > 999) { Utils.mostrarError('parq-numero', 'El n\u00famero no puede tener m\u00e1s de 3 d\u00edgitos'); return; }
      var tipo = document.getElementById('parq-tipo').value;
      var uso = document.querySelector('input[name="parq-uso"]:checked');
      var esVisitante = uso ? uso.value === 'visitante' : true;
      codigo = prefijoParq(tipo, esVisitante) + numero;
    }
    if (!Utils.valSelect(document.getElementById('parq-tipo').value, 'parq-tipo', 'Seleccione el tipo')) return;
    var esVisitante = true;
    var residente = document.querySelector('input[name="parq-uso"]:checked');
    if (residente) esVisitante = residente.value === 'visitante';
    if (!esVisitante) {
      var aptId = document.getElementById('parq-apartamento').value;
      if (!aptId) { Utils.mostrarError('parq-apt', 'Debe seleccionar un apartamento'); return; }
    }
    const d = {
      codigo: codigo,
      tipo: document.getElementById('parq-tipo').value,
      esVisitante: esVisitante,
      idApartamento: esVisitante ? null : parseInt(document.getElementById('parq-apartamento').value)
    };
    if (editingId) {
      d.estado = document.getElementById('parq-estado').value;
    } else {
      d.estado = 'DISPONIBLE';
    }
    var btn = document.getElementById('btn-guardar-parq');
    if (btn) btn.disabled = true;
    try {
      if (editingId) { await API.put('/parqueaderos/' + editingId, d); Utils.showToast('Parqueadero actualizado', 'success'); }
      else { await API.post('/parqueaderos', d); Utils.showToast('Parqueadero creado', 'success'); }
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function editar(id) {
    if (esPortero()) return;
    try { mostrarFormulario(await API.get('/parqueaderos/' + id)); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function eliminar(id) {
    if (!(await Utils.showConfirm('Desea eliminar este parqueadero?'))) return;
    try { await API.del('/parqueaderos/' + id); Utils.showToast('Parqueadero eliminado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  return { cargar, render, aplicarFiltros, mostrarFormulario, guardar, editar, eliminar, goToPage, toggleUso, esPortero, prefijoParq };
})();

Router.register('parqueaderos', {
  html: document.getElementById('tpl-parqueaderos').innerHTML,
  js: function() {
    document.getElementById('page-title').textContent = 'Parqueaderos';
    if (Parqueaderos.esPortero()) {
      var btns = document.querySelectorAll('[onclick*="mostrarFormulario"]');
      btns.forEach(function(b) { if (b) b.remove(); });
    }
    Parqueaderos.cargar();
    if (window._parqRefreshInterval) clearInterval(window._parqRefreshInterval);
    window._parqRefreshInterval = setInterval(function() {
      if (document.visibilityState === 'visible') Parqueaderos.cargar();
    }, 10000);
  },
  onLeave: function() {
    if (window._parqRefreshInterval) { clearInterval(window._parqRefreshInterval); window._parqRefreshInterval = null; }
  }
});