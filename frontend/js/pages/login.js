function togglePasswordVisibility() {
  var input = document.getElementById('login-password');
  var icon = document.getElementById('visibility-icon');
  if (!input || !icon) return;
  if (input.type === 'password') {
    input.type = 'text';
    icon.textContent = 'visibility';
    icon.style.transform = 'scale(1.1)';
    setTimeout(function() { icon.style.transform = 'scale(1)'; }, 150);
  } else {
    input.type = 'password';
    icon.textContent = 'visibility_off';
    icon.style.transform = 'scale(0.9)';
    setTimeout(function() { icon.style.transform = 'scale(1)'; }, 150);
  }
}

var heroInterval = null;

function iniciarGaleria() {
  var track = document.getElementById('hero-track');
  var dotsContainer = document.getElementById('hero-dots');
  if (!track || !dotsContainer) return;

  var slides = track.querySelectorAll('.hero-slide');
  if (slides.length === 0) return;

  var images = [
    'imagenes/galeria1.png',
    'imagenes/galeria2.png',
    'imagenes/galeria3.png',
    'imagenes/galeria4.png',
    'imagenes/galeria5.png',
    'imagenes/galeria6.png'
  ];

  slides.forEach(function(slide, i) {
    slide.style.backgroundImage = 'url(' + images[i % images.length] + ')';
    slide.style.backgroundSize = 'cover';
    slide.style.backgroundPosition = 'center';
  });

  dotsContainer.innerHTML = '';
  for (var i = 0; i < slides.length; i++) {
    var dot = document.createElement('button');
    dot.className = 'hero-dot' + (i === 0 ? ' active' : '');
    dot.setAttribute('data-index', i);
    dot.onclick = function() { irASlide(parseInt(this.getAttribute('data-index'))); };
    dotsContainer.appendChild(dot);
  }

  var currentIndex = 0;

  function irASlide(index) {
    currentIndex = index;
    track.style.transform = 'translateX(-' + (index * 100) + '%)';
    dotsContainer.querySelectorAll('.hero-dot').forEach(function(d, i) {
      d.classList.toggle('active', i === index);
    });
  }

  function siguienteSlide() {
    irASlide((currentIndex + 1) % slides.length);
  }

  if (heroInterval) clearInterval(heroInterval);
  heroInterval = setInterval(siguienteSlide, 4000);
}

Router.register('login', {
  html: document.getElementById('tpl-login').innerHTML,
  js: function() {
    iniciarGaleria();

    var form = document.getElementById('login-form');
    var forgotLink = document.getElementById('forgot-link');
    var userField = document.getElementById('login-username');
    var rememberCheck = document.getElementById('login-remember');

    // Cargar usuario guardado
    var savedUser = localStorage.getItem('remembered_user');
    if (savedUser && userField) {
      userField.value = savedUser;
      if (rememberCheck) rememberCheck.checked = true;
    }

    if (forgotLink) {
      forgotLink.onclick = function(e) {
        e.preventDefault();
        Utils.showToast('Contacte al administrador del sistema', 'info');
      };
    }
    form.onsubmit = async function(e) {
      e.preventDefault();
      var passField = document.getElementById('login-password');
      var btn = form.querySelector('button[type="submit"]');
      var username = userField ? userField.value.trim() : '';
      var password = passField ? passField.value : '';
      Utils.limpiarErrores('login-form');
      if (!Utils.valUsername(username, 'login-username')) return;
      if (!Utils.valPassword(password, 'login-password', true)) return;
      if (btn) btn.disabled = true;
      try {
        var user = await Auth.login(username, password);
        if (rememberCheck && rememberCheck.checked) {
          localStorage.setItem('remembered_user', username);
        } else {
          localStorage.removeItem('remembered_user');
        }
        Utils.showToast('Bienvenido, ' + user.username, 'success');
        if (user.rol === 'RESIDENTE') Router.navigate('residente-dashboard');
        else if (user.rol === 'PORTERO') Router.navigate('portero-dashboard');
        else Router.navigate('dashboard');
      } catch (err) {
        Utils.showToast(err.message, 'error');
      } finally {
        if (btn) btn.disabled = false;
      }
    };
  }
});
