const Avisos = (() => {
  let apartamentosPorPiso = {}; // Mapa de piso -> array de apartamentos
  
  async function cargarApartamentos() {
    try {
      var apts = await API.get('/apartamentos');
      apts = apts.filter(function(a) { return a.estado !== 'DISPONIBLE'; });
      
      // Ordenar apartamentos por número
      apts.sort(function(a, b) {
        var numA = parseInt(a.numero) || 0;
        var numB = parseInt(b.numero) || 0;
        return numA - numB;
      });
      
      // Agrupar por piso (primer dígito del número de apartamento)
      apartamentosPorPiso = {};
      apts.forEach(function(a) {
        var piso = Math.floor((parseInt(a.numero) || 0) / 100);
        if (piso >= 2) {
          if (!apartamentosPorPiso[piso]) apartamentosPorPiso[piso] = [];
          apartamentosPorPiso[piso].push(a);
        }
      });
      
      var dropdown = document.getElementById('aviso-apt-dropdown');
      dropdown.innerHTML = '';
      
      // Opción "Todos los apartamentos"
      var divTodos = document.createElement('div');
      divTodos.className = 'multi-select-option multi-select-todos';
      divTodos.dataset.value = 'todos';
      divTodos.innerHTML = '<strong>Todos los apartamentos</strong>';
      divTodos.onclick = function() { Avisos.seleccionarTodos(this); };
      dropdown.appendChild(divTodos);
      
      // Separador
      var sep1 = document.createElement('div');
      sep1.className = 'multi-select-separator';
      dropdown.appendChild(sep1);
      
      // Opciones por piso
      var pisos = Object.keys(apartamentosPorPiso).map(Number).sort(function(a, b) { return a - b; });
      for (var i = 0; i < pisos.length; i++) {
        var p = pisos[i];
        var divPiso = document.createElement('div');
        divPiso.className = 'multi-select-option multi-select-piso';
        divPiso.dataset.value = 'piso-' + p;
        divPiso.dataset.piso = p;
        divPiso.innerHTML = '<strong>Piso ' + p + '</strong> (' + apartamentosPorPiso[p].length + ' apts)';
        divPiso.onclick = function() { Avisos.togglePiso(this); };
        dropdown.appendChild(divPiso);
      }
      
      // Separador
      var sep2 = document.createElement('div');
      sep2.className = 'multi-select-separator';
      dropdown.appendChild(sep2);
      
      // Apartamentos individuales organizados por piso
      for (var i = 0; i < pisos.length; i++) {
        var p = pisos[i];
        // Header del piso
        var header = document.createElement('div');
        header.className = 'multi-select-header';
        header.textContent = 'Piso ' + p;
        dropdown.appendChild(header);
        
        // Apartamentos del piso
        apartamentosPorPiso[p].forEach(function(a) {
          var div = document.createElement('div');
          div.className = 'multi-select-option multi-select-apt';
          div.dataset.value = a.idApartamento;
          div.dataset.piso = p;
          div.textContent = a.numero + (a.tipo ? ' - ' + a.tipo : '');
          div.onclick = function() { Avisos.toggleApt(this); };
          dropdown.appendChild(div);
        });
      }
    } catch (e) {
      console.error('Error cargando apartamentos', e);
    }
  }

  function toggleDropdown(e) {
    e.stopPropagation();
    var dd = document.getElementById('aviso-apt-dropdown');
    dd.classList.toggle('hidden');
  }

  function seleccionarTodos(el) {
    // Deseleccionar todo
    var opts = document.querySelectorAll('#aviso-apt-dropdown .multi-select-option');
    for (var i = 0; i < opts.length; i++) opts[i].classList.remove('selected');
    
    // Seleccionar solo "Todos"
    el.classList.add('selected');
    document.getElementById('aviso-apt-label').textContent = 'Todos los apartamentos';
    closeDropdown();
  }
  
  function togglePiso(el) {
    var piso = parseInt(el.dataset.piso);
    var wasSelected = el.classList.contains('selected');
    
    // Si "Todos" está seleccionado, deseleccionarlo
    var todosSel = document.querySelector('.multi-select-todos.selected');
    if (todosSel) todosSel.classList.remove('selected');
    
    // Toggle este piso
    el.classList.toggle('selected');
    
    // Si se está seleccionando, deseleccionar apartamentos individuales de este piso
    if (!wasSelected) {
      var aptsDelPiso = document.querySelectorAll('.multi-select-apt[data-piso="' + piso + '"]');
      for (var i = 0; i < aptsDelPiso.length; i++) {
        aptsDelPiso[i].classList.remove('selected');
      }
    }
    
    actualizarLabelDesdeSeleccion();
  }

  function toggleApt(el) {
    var piso = el.dataset.piso;
    
    // Si "Todos" está seleccionado, deseleccionarlo
    var todosSel = document.querySelector('.multi-select-todos.selected');
    if (todosSel) todosSel.classList.remove('selected');
    
    // Si el piso completo está seleccionado, no permitir seleccionar apartamentos individuales
    var pisoSel = document.querySelector('.multi-select-piso[data-piso="' + piso + '"].selected');
    if (pisoSel) {
      Utils.showToast('Ya seleccionaste todo el Piso ' + piso + '. Deselecciónalo primero.', 'warning');
      return;
    }
    
    // Toggle este apartamento
    el.classList.toggle('selected');
    
    // Verificar si todos los apartamentos de un piso están seleccionados
    verificarPisoCompleto(piso);
    
    actualizarLabelDesdeSeleccion();
  }
  
  function verificarPisoCompleto(piso) {
    var aptsDelPiso = document.querySelectorAll('.multi-select-apt[data-piso="' + piso + '"]');
    var selDelPiso = document.querySelectorAll('.multi-select-apt[data-piso="' + piso + '"].selected');
    
    // Si todos están seleccionados, ofrecer seleccionar el piso completo
    if (aptsDelPiso.length > 0 && aptsDelPiso.length === selDelPiso.length) {
      var pisoEl = document.querySelector('.multi-select-piso[data-piso="' + piso + '"]');
      if (pisoEl) {
        pisoEl.classList.add('selected');
        // Deseleccionar apartamentos individuales
        for (var i = 0; i < selDelPiso.length; i++) {
          selDelPiso[i].classList.remove('selected');
        }
      }
    }
  }
  
  function actualizarLabelDesdeSeleccion() {
    var todosEl = document.querySelector('.multi-select-todos.selected');
    if (todosEl) {
      document.getElementById('aviso-apt-label').textContent = 'Todos los apartamentos';
      return;
    }
    
    var pisosSelList = document.querySelectorAll('.multi-select-piso.selected');
    var aptsSelList = document.querySelectorAll('.multi-select-apt.selected');
    
    var labels = [];
    
    // Agregar pisos seleccionados
    for (var i = 0; i < pisosSelList.length; i++) {
      labels.push('Piso ' + pisosSelList[i].dataset.piso);
    }
    
    // Agregar apartamentos individuales
    for (var i = 0; i < aptsSelList.length; i++) {
      labels.push(aptsSelList[i].textContent.split(' - ')[0]);
    }
    
    if (labels.length === 0) {
      document.querySelector('.multi-select-todos').classList.add('selected');
      document.getElementById('aviso-apt-label').textContent = 'Todos los apartamentos';
    } else if (labels.length <= 3) {
      document.getElementById('aviso-apt-label').textContent = labels.join(', ');
    } else {
      document.getElementById('aviso-apt-label').textContent = labels.length + ' seleccionados';
    }
  }

  function actualizarLabel(sel) {
    if (sel.length <= 3) {
      var labels = [];
      for (var i = 0; i < sel.length; i++) labels.push(sel[i].textContent);
      document.getElementById('aviso-apt-label').textContent = labels.join(', ');
    } else {
      document.getElementById('aviso-apt-label').textContent = sel.length + ' apartamentos seleccionados';
    }
  }

  function closeDropdown() {
    var dd = document.getElementById('aviso-apt-dropdown');
    if (dd) dd.classList.add('hidden');
  }

  var _clickHandler = function(e) {
    var ms = document.getElementById('aviso-apartamentos-container');
    if (ms && !ms.contains(e.target)) closeDropdown();
  };

  function destroy() {
    document.removeEventListener('click', _clickHandler);
  }

  async function enviar(e) {
    e.preventDefault();
    Utils.limpiarErrores('form-aviso');
    var titulo = document.getElementById('aviso-titulo').value.trim();
    var cuerpo = document.getElementById('aviso-cuerpo').value.trim();
    var ok = true;
    if (!Utils.valRequerido(titulo, 'aviso-titulo', 'El título')) ok = false;
    if (!Utils.valRequerido(cuerpo, 'aviso-cuerpo', 'El mensaje')) ok = false;
    if (!ok) return false;

    var todosEl = document.querySelector('.multi-select-todos.selected');
    var esTodos = todosEl !== null;
    
    var payload = { titulo: titulo, cuerpo: cuerpo };
    
    if (!esTodos) {
      var ids = [];
      
      // Agregar apartamentos de pisos completos seleccionados
      var pisosSelList = document.querySelectorAll('.multi-select-piso.selected');
      for (var i = 0; i < pisosSelList.length; i++) {
        var piso = parseInt(pisosSelList[i].dataset.piso);
        if (apartamentosPorPiso[piso]) {
          apartamentosPorPiso[piso].forEach(function(a) {
            ids.push(a.idApartamento);
          });
        }
      }
      
      // Agregar apartamentos individuales seleccionados
      var aptsSelList = document.querySelectorAll('.multi-select-apt.selected');
      for (var i = 0; i < aptsSelList.length; i++) {
        ids.push(parseInt(aptsSelList[i].dataset.value));
      }
      
      if (ids.length > 0) payload.idApartamentos = ids;
    }

    try {
      await API.post('/buzon/aviso', payload);
      Utils.showToast('Aviso enviado correctamente', 'success');
      document.getElementById('aviso-titulo').value = '';
      document.getElementById('aviso-cuerpo').value = '';
      seleccionarTodos(document.querySelector('.multi-select-todos'));
      cargarAvisos();
    } catch (err) {
      Utils.showToast(err.message, 'error');
    }
    return false;
  }

  function init() {
    var title = document.getElementById('page-title');
    if (title) title.textContent = 'Avisos Generales';
    document.addEventListener('click', _clickHandler);
    cargarApartamentos();
    cargarAvisos();
  }

  async function cargarAvisos() {
    try {
      var lista = await API.get('/buzon/avisos');
      var tbody = document.getElementById('tbody-avisos');
      var empty = document.getElementById('avisos-empty');
      tbody.innerHTML = '';
      if (!lista || lista.length === 0) {
        empty.classList.remove('hidden');
        return;
      }
      empty.classList.add('hidden');
      lista.forEach(function(a) {
        var tr = document.createElement('tr');
        tr.innerHTML = '<td>' + a.idMensaje + '</td>'
          + '<td>' + Utils.escapeHtml(a.numeroApartamento || 'Todos') + '</td>'
          + '<td>' + Utils.escapeHtml(a.titulo || '') + '</td>'
          + '<td>' + Utils.escapeHtml(a.cuerpo || '') + '</td>'
          + '<td>' + (a.fechaCreacion ? a.fechaCreacion.substring(0, 19).replace('T', ' ') : '') + '</td>';
        tbody.appendChild(tr);
      });
    } catch (err) {
      console.error('Error cargando avisos', err);
    }
  }

  return { 
    init: init, 
    destroy: destroy, 
    enviar: enviar, 
    cargarAvisos: cargarAvisos, 
    toggleDropdown: toggleDropdown, 
    toggleApt: toggleApt, 
    togglePiso: togglePiso, 
    seleccionarTodos: seleccionarTodos 
  };
})();
