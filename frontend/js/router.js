const Router = (() => {
  const pages = {};
  let currentPage = null;

  function register(name, config) {
    pages[name] = config;
  }

  async function navigate(name) {
    if (name === currentPage) return;
    const page = pages[name];
    if (!page) { console.error('Pagina no encontrada:', name); return; }

    if (currentPage && pages[currentPage] && pages[currentPage].onLeave) {
      try { pages[currentPage].onLeave(); } catch(e) { console.warn('Error en onLeave:', e); }
    }

    currentPage = name;
    const app = document.getElementById('app');

    if (name === 'login') {
      document.body.className = '';
      if (page.html) app.innerHTML = page.html;
      if (page.js) page.js();
      return;
    }

    if (!Auth.isAuthenticated()) {
      navigate('login');
      return;
    }

    document.body.className = 'app-shell';
    let contentArea = document.getElementById('content-area');
    if (!contentArea) {
      app.innerHTML = document.getElementById('tpl-shell').innerHTML;
      if (typeof buildSidebar === 'function') buildSidebar();
      contentArea = document.getElementById('content-area');
    }
    if (page.html && contentArea) contentArea.innerHTML = page.html;
    if (page.js) page.js();

    updateSidebar(name);
    if (typeof toggleSidebar === 'function') toggleSidebar(false);
  }

  function updateSidebar(current) {
    // Quitar active de todos
    document.querySelectorAll('.sidebar-item, .sidebar-item-btn').forEach(el => {
      el.classList.remove('active');
    });
    // Marcar el activo
    document.querySelectorAll('.sidebar-item[data-page="' + current + '"], .sidebar-item-btn[data-page="' + current + '"]').forEach(el => {
      el.classList.add('active');
    });
    // Auto-abrir grupo contenedor
    document.querySelectorAll('.sidebar-item.active').forEach(el => {
      var group = el.closest('.sidebar-group');
      if (group) group.classList.add('group-open');
    });
  }

  return {
    register,
    navigate,
    getCurrentPage: () => currentPage
  };
})();
