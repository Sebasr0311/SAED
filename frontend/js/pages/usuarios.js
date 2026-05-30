const Usuarios = (() => {
  let data = [];
  let editingId = null;
  const PAGE_SIZE = 15;
  let currentPage = 1;

  const ROLES = ['ADMINISTRADOR', 'PORTERO', 'RESIDENTE'];

  function ordenar(lista) {
    var copia = lista.slice();
    var pesoRol = { ADMINISTRADOR: 0, PORTERO: 1, RESIDENTE: 2 };
    copia.sort(function(a, b) {
      var ra = pesoRol[a.rol] !== undefined ? pesoRol[a.rol] : 99;
      var rb = pesoRol[b.rol] !== undefined ? pesoRol[b.rol] : 99;
      if (ra !== rb) return ra - rb;
      var na = parseInt(a.numeroApartamento, 10) || 0;
      var nb = parseInt(b.numeroApartamento, 10) || 0;
      return na - nb;
    });
    return copia;
  }

  function filtrar(lista) {
    var filtro = document.getElementById('usr-filtrar-residentes');
    if (!filtro || !filtro.checked) return lista;
    return lista.filter(function(u) { return u.rol === 'RESIDENTE'; });
  }

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-usuarios');
    const pag = document.getElementById('pagination-usuarios');
    if (!tbody) return;
    var lista = ordenar(filtrar(data));
    const pg = Utils.paginate(lista, currentPage, PAGE_SIZE);
    tbody.innerHTML = pg.items.map(u => `<tr>
      <td>${u.idUsuario}</td>
      <td>${u.username || ''}</td>
      <td>${Utils.estadoBadge(u.rol || '')}</td>
      <td>${u.nombreResidente || '-'}</td>
      <td>${u.numeroApartamento || '-'}</td>
      <td>${u.activo ? 'Si' : 'No'}</td>
      <td>${Utils.formatDateTime(u.ultimoLogin)}</td>
      <td class="actions-cell">
        <button class="btn btn-primary btn-sm" onclick="Usuarios.editar(${u.idUsuario})">Editar</button>
        <button class="btn btn-danger btn-sm" onclick="Usuarios.desactivar(${u.idUsuario})">Desactivar</button>
      </td>
    </tr>`).join('');
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Usuarios.goToPage');
  }

  async function cargar() {
    try { data = await API.get('/usuarios'); currentPage = 1; render(); }
    catch (e) { Utils.showAlert('Error', e.message, 'error'); }
  }

  function mostrarFormulario(u) {
    editingId = u ? u.idUsuario : null;
    const isEdit = !!editingId;
    const ropts = ROLES.map(r => '<option value="' + r + '" ' + (u && u.rol === r ? 'selected' : '') + '>' + r + '</option>').join('');
    Utils.modal(isEdit ? 'Editar Usuario' : 'Nuevo Usuario',
      `<form id="form-usuario">
        <div class="form-group"><label>Username</label><input type="text" id="usr-username" class="form-control" value="${u ? u.username || '' : ''}" maxlength="50"><span class="field-error" id="usr-username-error"></span></div>
        <div class="form-group"><label>Contrase\u00f1a ${isEdit ? '(dejar vac\u00edo para mantener actual)' : ''}</label><input type="password" id="usr-password" class="form-control" ${isEdit ? '' : ''}><span class="field-error" id="usr-password-error"></span></div>
        <div class="form-row">
          <div class="form-group"><label>Rol</label><select id="usr-rol" class="form-control">${ropts}</select><span class="field-error" id="usr-rol-error"></span></div>
          <div class="form-group"><label>ID Residente (opcional)</label><input type="number" id="usr-id-residente" class="form-control" value="${u ? u.idResidente || '' : ''}"></div>
        </div>
        <div class="form-group"><label><input type="checkbox" id="usr-activo" ${!u || u.activo ? 'checked' : ''}> Activo</label></div>
      </form>`,
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cancelar</button>' +
      '<button class="btn btn-primary" id="btn-guardar-usuario" onclick="Usuarios.guardar()">' + (isEdit ? 'Actualizar' : 'Guardar') + '</button>');
  }

  async function guardar() {
    Utils.limpiarErrores('form-usuario');
    if (!Utils.valUsername(document.getElementById('usr-username').value, 'usr-username')) return;
    if (!Utils.valPassword(document.getElementById('usr-password').value, 'usr-password', !editingId)) return;
    var idResidenteVal = parseInt(document.getElementById('usr-id-residente').value) || null;
    var rolVal = document.getElementById('usr-rol').value;
    if (idResidenteVal && rolVal !== 'RESIDENTE') {
      Utils.showToast('Si se asigna un ID de residente, el rol debe ser RESIDENTE', 'error');
      return;
    }
    if (!Utils.valSelect(rolVal, 'usr-rol', 'Seleccione el rol')) return;
    const d = {
      username: document.getElementById('usr-username').value.trim(),
      passwordHash: document.getElementById('usr-password').value,
      rol: rolVal,
      idResidente: idResidenteVal,
      activo: document.getElementById('usr-activo').checked
    };
    var btn = document.getElementById('btn-guardar-usuario');
    if (btn) btn.disabled = true;
    try {
      if (editingId) { await API.put('/usuarios/' + editingId, d); Utils.showToast('Usuario actualizado', 'success'); }
      else { await API.post('/usuarios', d); Utils.showToast('Usuario creado', 'success'); }
      var overlay = document.querySelector('.modal-overlay');
      if (overlay) overlay.remove();
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
    finally { if (btn) btn.disabled = false; }
  }

  async function editar(id) {
    try {
      const u = await API.get('/usuarios/' + id);
      u.passwordHash = '';
      mostrarFormulario(u);
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function desactivar(id) {
    if (!confirm('Desea desactivar este usuario?')) return;
    try { await API.del('/usuarios/' + id); Utils.showToast('Usuario desactivado', 'success'); cargar(); }
    catch (e) { Utils.showToast(e.message, 'error'); }
  }

  return { cargar, render, mostrarFormulario, guardar, editar, desactivar, goToPage };
})();

Router.register('usuarios', {
  html: document.getElementById('tpl-usuarios').innerHTML,
  js: () => { document.getElementById('page-title').textContent = 'Usuarios'; Usuarios.cargar(); }
});