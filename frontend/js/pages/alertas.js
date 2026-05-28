const Alertas = (() => {
  let data = [];
  const PAGE_SIZE = 15;
  let currentPage = 1;

  function goToPage(page) {
    if (page < 1 || page > Math.ceil(data.length / PAGE_SIZE)) return;
    currentPage = page;
    render();
  }

  function render() {
    const tbody = document.getElementById('tbody-alertas');
    const pag = document.getElementById('pagination-alertas');
    if (!tbody) return;
    const pg = Utils.paginate(data, currentPage, PAGE_SIZE);
    tbody.innerHTML = pg.items.map(function(a) { return '<tr>' +
      '<td>' + a.idAlerta + '</td>' +
      '<td>' + Utils.escapeHtml(a.tipoAlerta || '-') + '</td>' +
      '<td>' + Utils.escapeHtml(a.numeroApartamento || '-') + '</td>' +
      '<td>' + Utils.escapeHtml(a.nombreResidente || '-') + '</td>' +
      '<td>' + (a.periodo || (a.anio && a.mes ? Utils.periodoLabel(a.anio, a.mes) : '-')) + '</td>' +
      '<td>' + Utils.escapeHtml(a.estadoCuota || '-') + '</td>' +
      '<td>' + Utils.escapeHtml(a.canal || '-') + '</td>' +
      '<td style="' + (a.leida ? 'color:var(--text-secondary)' : 'color:var(--danger);font-weight:600') + '">' + (a.leida ? 'Si' : 'No') + '</td>' +
      '<td>' + Utils.formatDateTime(a.enviadaEn) + '</td></tr>'; }).join('');
    if (pag) pag.innerHTML = Utils.paginationHtml(pg, 'Alertas.goToPage');
  }

  async function cargar() {
    try {
      const soloNoLeidas = document.getElementById('alerta-solo-no-leidas')?.checked;
      data = await API.get('/alertas' + (soloNoLeidas ? '?soloNoLeidas=true' : ''));
      currentPage = 1;
      render();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  async function marcarLeida() {
    const sel = document.querySelector('#tbl-alertas tbody tr.selected');
    if (!sel) { Utils.showToast('Seleccione una alerta de la tabla', 'warn'); return; }
    const id = parseInt(sel.cells[0].textContent);
    try {
      await API.put('/alertas/' + id + '/leer');
      Utils.showToast('Alerta marcada como leida', 'success');
      cargar();
    } catch (e) { Utils.showToast(e.message, 'error'); }
  }

  return { cargar: cargar, render: render, marcarLeida: marcarLeida, goToPage: goToPage };
})();

Router.register('alertas', {
  html: document.getElementById('tpl-alertas').innerHTML,
  js: function() {
    document.getElementById('page-title').textContent = 'Alertas';
    Alertas.cargar();
  }
});
// Row selection listener — attached once via delegation on #app
document.addEventListener('click', function(e) {
  var row = e.target.closest('#tbl-alertas tbody tr');
  if (row) {
    document.querySelectorAll('#tbl-alertas tbody tr').forEach(function(r) { r.classList.remove('selected'); });
    row.classList.add('selected');
  }
});