const Utils = (() => {
  function _fixISO(dateStr) {
    if (!dateStr || typeof dateStr !== 'string') return dateStr;
    if (dateStr.indexOf('T') < 0) {
      // Fechas solas (yyyy-mm-dd): sin offset → el navegador las trata como hora local
      if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return dateStr + 'T00:00:00';
      return dateStr;
    }
    if (dateStr.endsWith('Z') || /[+-]\d{2}:\d{2}/.test(dateStr)) return dateStr;
    // El servidor (Oracle ATP / Railway) opera en UTC y envía timestamps sin offset.
    // Tratarlos como UTC evita que aparezcan 5 horas adelantados en Colombia (UTC-5).
    return dateStr + 'Z';
  }

  function parseDate(dateStr) {
    return new Date(_fixISO(dateStr));
  }

  function formatDate(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(_fixISO(dateStr));
    return d.toLocaleDateString('es-CO', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' });
  }

  var TZ = 'America/Bogota';

  function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(_fixISO(dateStr));
    return d.toLocaleDateString('es-CO', {
      timeZone: TZ,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    });
  }

  function formatTime(dateStr) {
    if (!dateStr) return '-';
    var d = new Date(_fixISO(dateStr));
    return d.toLocaleTimeString('es-CO', {
      timeZone: TZ,
      hour: '2-digit', minute: '2-digit'
    });
  }

  function formatCurrency(value) {
    if (value == null) return '$0';
    return '$' + Number(value).toLocaleString('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  }

  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => { toast.remove(); }, 4000);
  }

  function showConfirm(message) {
    return new Promise(function(resolve) {
      var overlay = document.createElement('div');
      overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.4);z-index:9999;display:flex;align-items:center;justify-content:center;padding:16px';
      var box = document.createElement('div');
      box.style.cssText = 'background:#fff;border-radius:12px;padding:24px;max-width:360px;width:100%;box-shadow:0 8px 32px rgba(0,0,0,0.2);text-align:center';
      box.innerHTML = '<p style="margin:0 0 20px;font-size:15px;color:#333;line-height:1.4">' + message + '</p>' +
        '<div style="display:flex;gap:8px;justify-content:center">' +
        '<button id="btn-confirm-no" class="btn btn-outline" style="flex:1">Cancelar</button>' +
        '<button id="btn-confirm-yes" class="btn btn-danger" style="flex:1">Aceptar</button></div>';
      overlay.appendChild(box);
      document.body.appendChild(overlay);
      document.getElementById('btn-confirm-yes').onclick = function() { overlay.remove(); resolve(true); };
      document.getElementById('btn-confirm-no').onclick = function() { overlay.remove(); resolve(false); };
      overlay.onclick = function(e) { if (e.target === overlay) { overlay.remove(); resolve(false); } };
    });
  }

  /**
   * Muestra un modal que pide la contraseña del administrador antes de continuar.
   * Llama a POST /api/auth/verify-password; si es correcta, resuelve true.
   * Si el usuario cancela o la contraseña es incorrecta, nunca resuelve true.
   * @param {string} descripcion  Texto de advertencia que describe la acción peligrosa.
   * @returns {Promise<boolean>}
   */
  function confirmarConPassword(descripcion) {
    return new Promise(function(resolve) {
      var user = Auth.getCurrentUser();
      if (!user) { resolve(false); return; }

      var overlay = document.createElement('div');
      overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(10,22,40,0.6);backdrop-filter:blur(4px);z-index:9999;display:flex;align-items:center;justify-content:center;padding:16px';

      var box = document.createElement('div');
      box.style.cssText = 'background:#fff;border-radius:16px;padding:28px;max-width:420px;width:100%;box-shadow:0 20px 60px rgba(0,0,0,0.25)';
      box.innerHTML =
        '<div style="display:flex;align-items:center;gap:12px;margin-bottom:18px">' +
        '<div style="width:44px;height:44px;border-radius:12px;background:var(--danger,#dc2626);display:flex;align-items:center;justify-content:center;flex-shrink:0">' +
        '<span class="material-symbols-outlined" style="font-size:24px;color:#fff">lock</span></div>' +
        '<div><h3 style="margin:0;font-size:17px;font-weight:700;color:var(--text,#1e293b)">Confirmar acción</h3>' +
        '<p style="margin:3px 0 0;font-size:13px;color:var(--text-secondary,#64748b)">Se requiere verificación de identidad</p></div></div>' +
        '<p style="margin:0 0 20px;font-size:14px;color:var(--danger,#dc2626);font-weight:500;line-height:1.5;padding:12px 14px;background:#fef2f2;border-radius:8px;border-left:3px solid var(--danger,#dc2626)">' +
        escapeHtml(descripcion) + '</p>' +
        '<div class="form-group" style="margin-bottom:4px">' +
        '<label style="font-size:13px;font-weight:600;color:var(--text,#1e293b);display:block;margin-bottom:6px">Contraseña de administrador</label>' +
        '<input type="password" id="dlg-confirm-pwd" class="form-control" placeholder="Ingrese su contraseña..." autocomplete="current-password">' +
        '<span id="dlg-confirm-pwd-error" style="display:block;font-size:12px;color:var(--danger,#dc2626);margin-top:4px;min-height:18px"></span>' +
        '</div>' +
        '<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:20px">' +
        '<button id="dlg-confirm-cancel" class="btn btn-outline" style="min-width:90px">Cancelar</button>' +
        '<button id="dlg-confirm-ok" class="btn btn-danger" style="min-width:130px">Confirmar</button>' +
        '</div>';

      overlay.appendChild(box);
      document.body.appendChild(overlay);

      var input = document.getElementById('dlg-confirm-pwd');
      var btn = document.getElementById('dlg-confirm-ok');
      var errorEl = document.getElementById('dlg-confirm-pwd-error');

      setTimeout(function() { if (input) input.focus(); }, 80);

      function cancelar() { overlay.remove(); resolve(false); }

      async function confirmar() {
        var pwd = input.value;
        if (!pwd) { errorEl.textContent = 'La contraseña es obligatoria'; input.focus(); return; }
        btn.disabled = true;
        btn.textContent = 'Verificando...';
        errorEl.textContent = '';
        try {
          await API.post('/auth/verify-password', { password: pwd });
          overlay.remove();
          resolve(true);
        } catch (e) {
          btn.disabled = false;
          btn.textContent = 'Confirmar';
          errorEl.textContent = 'Contraseña incorrecta. Inténtelo de nuevo.';
          input.value = '';
          input.focus();
        }
      }

      document.getElementById('dlg-confirm-cancel').addEventListener('click', cancelar);
      overlay.addEventListener('click', function(e) { if (e.target === overlay) cancelar(); });
      btn.addEventListener('click', confirmar);
      input.addEventListener('keydown', function(e) { if (e.key === 'Enter' && !btn.disabled) confirmar(); });
    });
  }

  function showAlert(title, message, type) {
    showToast(message || title, type);
  }

  function loadingSpinner() {
    return '<div class="spinner"></div>';
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function emptyState(msg) {
    return '<div class="empty-state"><div class="empty-icon"><span class="material-symbols-outlined" style="font-size:48px">info</span></div><div class="empty-title">' + escapeHtml(msg || 'No hay datos disponibles') + '</div></div>';
  }

  function serializeForm(form) {
    const data = {};
    const fd = new FormData(form);
    fd.forEach((value, key) => { data[key] = value; });
    return data;
  }

  function validateRequired(value, fieldName) {
    if (!value || (typeof value === 'string' && !value.trim())) {
      return 'El campo ' + fieldName + ' es obligatorio';
    }
    return null;
  }

  function validateEmail(email) {
    if (!email) return null;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? null : 'Email inv\u00e1lido';
  }

  function validatePhone(phone) {
    if (!phone) return null;
    return /^\d{7,15}$/.test(phone) ? null : 'Tel\u00e9fono inv\u00e1lido (solo d\u00edgitos, 7-15)';
  }

  /* ─── Validaciones mejoradas ─── */

  function limpiarErrores(formId) {
    var form = document.getElementById(formId);
    if (!form) return;
    form.querySelectorAll('.field-error').forEach(function(el) { el.textContent = ''; });
    form.querySelectorAll('.is-invalid').forEach(function(el) { el.classList.remove('is-invalid'); });
  }

  function mostrarError(inputId, mensaje) {
    var input = document.getElementById(inputId);
    if (!input) return;
    input.classList.add('is-invalid');
    var errorEl = input.parentNode.querySelector('.field-error');
    if (errorEl) errorEl.textContent = mensaje;

    if (!input.dataset.errorAutoClear) {
      input.dataset.errorAutoClear = '1';
      input.addEventListener('input', function autoClear() {
        input.classList.remove('is-invalid');
        var el = input.parentNode.querySelector('.field-error');
        if (el) el.textContent = '';
        input.removeEventListener('input', autoClear);
        delete input.dataset.errorAutoClear;
      });
    }
  }

  function valNombre(value, inputId, label) {
    var v = (value || '').trim();
    if (!v) { mostrarError(inputId, (label || 'El nombre') + ' es obligatorio'); return false; }
    if (v.length < 2) { mostrarError(inputId, 'Mínimo 2 caracteres'); return false; }
    if (v.length > 25) { mostrarError(inputId, 'Máximo 25 caracteres'); return false; }
    if (!/^[a-zA-ZÀ-ÿ\s'.]+$/.test(v)) { mostrarError(inputId, 'Solo letras y espacios'); return false; }
    return true;
  }

  function valApellido(value, inputId, label) {
    var v = (value || '').trim();
    if (!v) { mostrarError(inputId, (label || 'El apellido') + ' es obligatorio'); return false; }
    if (v.length < 2) { mostrarError(inputId, 'Mínimo 2 caracteres'); return false; }
    if (v.length > 25) { mostrarError(inputId, 'Máximo 25 caracteres'); return false; }
    if (!/^[a-zA-ZÀ-ÿ\s'.]+$/.test(v)) { mostrarError(inputId, 'Solo letras y espacios'); return false; }
    return true;
  }

  function valDocumento(value, inputId, tipoDocCodigo, label) {
    var v = (value || '').trim();
    if (!v) { mostrarError(inputId, (label || 'El documento') + ' es obligatorio'); return false; }
    
    // Validación específica por tipo de documento colombiano
    if (tipoDocCodigo) {
      switch(tipoDocCodigo) {
        case 'CC': // Cédula de Ciudadanía: solo números, 7-10 dígitos
          if (!/^\d{7,10}$/.test(v)) {
            mostrarError(inputId, 'Cédula: solo números, entre 7 y 10 dígitos');
            return false;
          }
          break;
        
        case 'TI': // Tarjeta de Identidad: solo números, 7-10 dígitos
          if (!/^\d{7,10}$/.test(v)) {
            mostrarError(inputId, 'TI: solo números, entre 7 y 10 dígitos');
            return false;
          }
          break;
        
        case 'CE': // Cédula de Extranjería: letra mayúscula + 5-11 dígitos
          if (!/^[A-Za-z]\d{5,11}$/.test(v)) {
            mostrarError(inputId, 'CE: letra seguida de 5 a 11 dígitos (ej: E123456)');
            return false;
          }
          break;
        
        case 'PP':       // Pasaporte (código interno legado)
        case 'PASAPORTE': // Pasaporte: alfanumérico, 7-15 caracteres
          if (!/^[A-Z0-9]{7,15}$/i.test(v)) {
            mostrarError(inputId, 'Pasaporte: letras y números, entre 7 y 15 caracteres');
            return false;
          }
          break;
        
        case 'PEP': // Permiso Especial de Permanencia: alfanumérico, 8-15 caracteres
          if (!/^[A-Z0-9]{8,15}$/i.test(v)) {
            mostrarError(inputId, 'PEP: alfanumérico, entre 8 y 15 caracteres');
            return false;
          }
          break;
        
        case 'RC': // Registro Civil: solo números, 7-10 dígitos
          if (!/^\d{7,10}$/.test(v)) {
            mostrarError(inputId, 'RC: solo números, entre 7 y 10 dígitos');
            return false;
          }
          break;
        
        case 'NIT': // NIT: solo números, 9-13 dígitos (sin guión ni DV)
          if (!/^\d{9,13}$/.test(v)) {
            mostrarError(inputId, 'NIT: solo números, entre 9 y 13 dígitos');
            return false;
          }
          break;
        
        default:
          // Validación genérica para tipos no especificados
          if (v.length < 4) { mostrarError(inputId, 'Mínimo 4 caracteres'); return false; }
          if (v.length > 30) { mostrarError(inputId, 'Máximo 30 caracteres'); return false; }
          if (!/^[a-zA-Z0-9-]+$/.test(v)) { mostrarError(inputId, 'Solo letras, números y guiones'); return false; }
      }
    } else {
      // Sin tipo especificado, validación genérica
      if (v.length < 4) { mostrarError(inputId, 'Mínimo 4 caracteres'); return false; }
      if (v.length > 30) { mostrarError(inputId, 'Máximo 30 caracteres'); return false; }
      if (!/^[a-zA-Z0-9-]+$/.test(v)) { mostrarError(inputId, 'Solo letras, números y guiones'); return false; }
    }
    
    return true;
  }

  function valTelefono(value, inputId, opts) {
    opts = opts || {};
    var v = (value || '').trim();
    if (!v) {
      if (opts.required) {
        mostrarError(inputId, 'El teléfono es obligatorio');
        return false;
      }
      return true; // opcional por defecto
    }
    if (!/^\d{10}$/.test(v)) { 
      mostrarError(inputId, 'Teléfono debe tener exactamente 10 dígitos'); 
      return false; 
    }
    return true;
  }

  function valEmail(value, inputId, opts) {
    opts = opts || {};
    var v = (value || '').trim();
    if (!v) {
      if (opts.required) {
        mostrarError(inputId, 'El email es obligatorio');
        return false;
      }
      return true; // opcional por defecto
    }
    
    // Validación robusta de email
    // Debe tener al menos 1 caracter antes del @
    if (v.indexOf('@') <= 0) {
      mostrarError(inputId, 'Email debe tener al menos 1 caracter antes del @');
      return false;
    }
    
    // Regex mejorado que valida formato estándar de email
    var emailRegex = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(v)) {
      mostrarError(inputId, 'Email inválido. Formato: ejemplo@dominio.com');
      return false;
    }
    
    // Validar que tenga un dominio válido después del @
    var parts = v.split('@');
    if (parts.length !== 2) {
      mostrarError(inputId, 'Email inválido');
      return false;
    }
    
    var domain = parts[1];
    if (!domain.includes('.') || domain.startsWith('.') || domain.endsWith('.')) {
      mostrarError(inputId, 'Dominio de email inválido');
      return false;
    }
    
    if (v.length > 40) { 
      mostrarError(inputId, 'Máximo 40 caracteres'); 
      return false; 
    }
    
    return true;
  }

  function valRequerido(value, inputId, label) {
    var v = (value || '').trim();
    if (!v) { mostrarError(inputId, (label || 'Este campo') + ' es obligatorio'); return false; }
    return true;
  }

  function valSelect(value, inputId, label) {
    if (!value || value === '') { mostrarError(inputId, (label || 'Seleccione una opci\u00f3n')); return false; }
    return true;
  }

  function valNumero(value, inputId, opts) {
    opts = opts || {};
    var v = (value !== null && value !== undefined && value !== '') ? Number(value) : NaN;
    if (isNaN(v)) { mostrarError(inputId, opts.label || 'Debe ser un n\u00famero v\u00e1lido'); return false; }
    if (opts.min !== undefined && v < opts.min) { mostrarError(inputId, 'M\u00ednimo ' + opts.min); return false; }
    if (opts.max !== undefined && v > opts.max) { mostrarError(inputId, 'M\u00e1ximo ' + opts.max); return false; }
    if (opts.positive && v <= 0) { mostrarError(inputId, 'Debe ser mayor a cero'); return false; }
    return true;
  }

  function valEntero(value, inputId, opts) {
    opts = opts || {};
    var s = (value !== null && value !== undefined) ? String(value).trim() : '';
    if (s === '') { mostrarError(inputId, (opts.label || 'Este campo') + ' es obligatorio'); return false; }
    var v = Number(s);
    if (isNaN(v) || !Number.isInteger(v)) { mostrarError(inputId, (opts.label || 'Debe ser un n\u00famero entero')); return false; }
    if (opts.min !== undefined && v < opts.min) { mostrarError(inputId, 'M\u00ednimo ' + opts.min); return false; }
    if (opts.max !== undefined && v > opts.max) { mostrarError(inputId, 'M\u00e1ximo ' + opts.max); return false; }
    if (opts.positive && v <= 0) { mostrarError(inputId, 'Debe ser mayor a cero'); return false; }
    return true;
  }

  function valFecha(value, inputId, label) {
    if (!value) { mostrarError(inputId, (label || 'La fecha') + ' es obligatoria'); return false; }
    var d = new Date(_fixISO(value));
    if (isNaN(d.getTime())) { mostrarError(inputId, 'Fecha inv\u00e1lida'); return false; }
    var year = d.getFullYear();
    if (year < 1930 || year > 2100) {
      mostrarError(inputId, 'Fecha fuera de rango permitido (1930-2100)');
      return false;
    }
    return true;
  }

  function valFechaNacimiento(value, inputId, opts) {
    opts = opts || {};
    if (!value) { mostrarError(inputId, 'La fecha de nacimiento es obligatoria'); return false; }
    
    var fecha = new Date(_fixISO(value));
    if (isNaN(fecha.getTime())) { mostrarError(inputId, 'Fecha inválida'); return false; }
    
    var year = fecha.getFullYear();
    if (year < 1930) {
      mostrarError(inputId, 'Fecha de nacimiento no v\u00e1lida (a\u00f1o anterior a 1930)');
      return false;
    }
    
    var hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    
    if (fecha > hoy) {
      mostrarError(inputId, 'La fecha de nacimiento no puede ser futura');
      return false;
    }
    
    var edad = hoy.getFullYear() - fecha.getFullYear();
    var mes = hoy.getMonth() - fecha.getMonth();
    if (mes < 0 || (mes === 0 && hoy.getDate() < fecha.getDate())) {
      edad--;
    }
    
    if (edad > 110) {
      mostrarError(inputId, 'Fecha de nacimiento no v\u00e1lida (edad mayor a 110 a\u00f1os)');
      return false;
    }
    
    var edadMinima = opts.edadMinima !== undefined ? opts.edadMinima : 0;
    if (edad < edadMinima) {
      mostrarError(inputId, 'Edad m\u00ednima requerida: ' + edadMinima + ' a\u00f1os');
      return false;
    }
    
    if (opts.edadMaxima !== undefined && edad > opts.edadMaxima) {
      mostrarError(inputId, 'Edad m\u00e1xima permitida: ' + opts.edadMaxima + ' a\u00f1os');
      return false;
    }
    
    return true;
  }

  function valPlaca(value, inputId, tipoVehiculo) {
    var v = (value || '').trim();
    if (!v) return true; // Placa opcional
    if (v.length > 10) { mostrarError(inputId, 'Máximo 10 caracteres'); return false; }
    
    // Validación específica para Colombia según tipo de vehículo
    var vSinEspacios = v.replace(/\s+/g, '');
    
    if (tipoVehiculo === 'VEHICULO') {
      // CARRO: 3 letras + 3 números (ABC123 o ABC 123)
      if (!/^[A-Z]{3}\s?\d{3}$/i.test(v)) {
        mostrarError(inputId, 'Formato de placa de carro: 3 letras + 3 números (Ej: ABC 123)');
        return false;
      }
    } else if (tipoVehiculo === 'MOTO') {
      // MOTO: 3 letras + 2 números + 1 letra (ABC12D o ABC 12D)
      if (!/^[A-Z]{3}\s?\d{2}[A-Z]$/i.test(v)) {
        mostrarError(inputId, 'Formato de placa de moto: 3 letras + 2 números + 1 letra (Ej: ABC 12D)');
        return false;
      }
    } else {
      // Para BICICLETA y OTRO, validación genérica (alfanumérico)
      if (!/^[A-Za-z0-9\s]{3,10}$/.test(v)) {
        mostrarError(inputId, 'Formato inválido. Use solo letras y números (3-10 caracteres)');
        return false;
      }
    }
    
    return true;
  }

  function valLongitud(value, inputId, opts) {
    var v = (value || '').trim();
    opts = opts || {};
    if (opts.required && !v) { mostrarError(inputId, (opts.label || 'Este campo') + ' es obligatorio'); return false; }
    if (opts.min !== undefined && v.length < opts.min) { mostrarError(inputId, 'M\u00ednimo ' + opts.min + ' caracteres'); return false; }
    if (opts.max !== undefined && v.length > opts.max) { mostrarError(inputId, 'M\u00e1ximo ' + opts.max + ' caracteres'); return false; }
    return true;
  }

  function valUsername(value, inputId) {
    var v = (value || '').trim();
    if (!v) { mostrarError(inputId, 'El usuario es obligatorio'); return false; }
    if (v.length < 3) { mostrarError(inputId, 'M\u00ednimo 3 caracteres'); return false; }
    if (v.length > 50) { mostrarError(inputId, 'M\u00e1ximo 50 caracteres'); return false; }
    if (!/^[a-zA-Z0-9_.]+$/.test(v)) { mostrarError(inputId, 'Solo letras, n\u00fameros, gui\u00f3n bajo y punto'); return false; }
    return true;
  }

  function valPassword(value, inputId, required) {
    var v = value || '';
    if (required && !v) { mostrarError(inputId, 'La contrase\u00f1a es obligatoria'); return false; }
    if (v && v.length < 6) { mostrarError(inputId, 'M\u00ednimo 6 caracteres'); return false; }
    if (v && v.length > 100) { mostrarError(inputId, 'M\u00e1ximo 100 caracteres'); return false; }
    return true;
  }

  function estadoBadge(estado) {
    const cls = 'badge-' + estado.toLowerCase().replace(/_/g, '-');
    return '<span class="badge ' + cls + '">' + estado.replace(/_/g, ' ') + '</span>';
  }

  function populateSelect(select, items, valueField, labelField, placeholder) {
    select.innerHTML = '';
    if (placeholder) {
      const opt = document.createElement('option');
      opt.value = ''; opt.textContent = placeholder; select.appendChild(opt);
    }
    items.forEach(item => {
      const opt = document.createElement('option');
      opt.value = item[valueField];
      opt.textContent = item[labelField] || item[valueField];
      select.appendChild(opt);
    });
  }

  function getSelectedText(select) {
    return select.options[select.selectedIndex] ? select.options[select.selectedIndex].text : '';
  }

  function modal(title, bodyHtml, footerHtml, staticBackdrop) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay show';
    overlay.innerHTML = `
      <div class="modal">
        <div class="modal-header">
          <h3>${title}</h3>
          <button class="close-btn" onclick="this.closest('.modal-overlay').remove()">&times;</button>
        </div>
        <div class="modal-body">${bodyHtml}</div>
        ${footerHtml ? '<div class="modal-footer">' + footerHtml + '</div>' : ''}
      </div>`;
    document.body.appendChild(overlay);
    if (!staticBackdrop) {
      overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });
    }
    return overlay.querySelector('.modal');
  }

  const months = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
  function monthName(m) { return months[m - 1] || m; }

  function periodoLabel(anio, mes) {
    return monthName(mes) + ' ' + anio;
  }

  function paginate(data, page, pageSize) {
    const total = data.length;
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    const start = (page - 1) * pageSize;
    return {
      items: data.slice(start, start + pageSize),
      page, pageSize, total, totalPages,
      from: total === 0 ? 0 : start + 1,
      to: Math.min(start + pageSize, total)
    };
  }

  function paginationHtml(pg, prefix) {
    if (pg.totalPages <= 1) return '';
    let h = '<div class="pagination">';
    h += `<button class="btn btn-ghost btn-sm" onclick="${prefix}(${pg.page - 1})" ${pg.page <= 1 ? 'disabled' : ''}>\u2039</button>`;
    for (let i = 1; i <= pg.totalPages; i++) {
      h += `<button class="btn btn-${i === pg.page ? 'primary' : 'ghost'} btn-sm" onclick="${prefix}(${i})">${i}</button>`;
    }
    h += `<button class="btn btn-ghost btn-sm" onclick="${prefix}(${pg.page + 1})" ${pg.page >= pg.totalPages ? 'disabled' : ''}>\u203A</button>`;
    h += `<span class="pagination-info">${pg.from}-${pg.to} de ${pg.total}</span>`;
    h += '</div>';
    return h;
  }

  function buscadorHtml(id, placeholder) {
    return '<div class="search-bar" style="position:relative;margin-bottom:12px">' +
      '<span class="material-symbols-outlined" style="position:absolute;margin:8px 0 0 12px;color:var(--text-secondary);font-size:20px">search</span>' +
      '<input type="text" id="' + id + '" class="form-control" style="padding-left:40px" placeholder="' + (placeholder || 'Buscar...') + '">' +
    '</div>';
  }

  function crearBuscador(inputId, data, fields, onFilter) {
    var input = document.getElementById(inputId);
    if (!input) return;
    var timeout = null;
    input.addEventListener('input', function() {
      clearTimeout(timeout);
      timeout = setTimeout(function() {
        var term = input.value.toLowerCase().trim();
        if (!term) {
          onFilter(data);
          return;
        }
        var filtered = data.filter(function(item) {
          return fields.some(function(field) {
            var val = item[field];
            if (val === null || val === undefined) return false;
            return String(val).toLowerCase().includes(term);
          });
        });
        onFilter(filtered);
      }, 250);
    });
  }

  function selectBusqueda(selectId, onChange) {
    var select = document.getElementById(selectId);
    if (!select) return;
    var container = document.createElement('div');
    container.className = 'searchable-select';
    container.style.cssText = 'position:relative;width:100%';
    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'form-control';
    input.id = selectId + '-search';
    input.placeholder = 'Buscar...';
    select.style.display = 'none';
    select.parentNode.insertBefore(container, select);
    container.appendChild(input);
    container.appendChild(select);
    var dropdown = document.createElement('div');
    dropdown.style.cssText = 'position:absolute;top:100%;left:0;right:0;max-height:200px;overflow-y:auto;background:var(--surface);border:1px solid var(--border);border-radius:4px;z-index:9999;display:none';
    container.appendChild(dropdown);
    function buildList(filter) {
      dropdown.innerHTML = '';
      var found = false;
      var term = filter ? filter.toLowerCase().trim() : '';
      Array.from(select.options).forEach(function(opt) {
        if (!opt.value) return;
        var text = opt.textContent || opt.innerText;
        if (term && !text.toLowerCase().includes(term)) return;
        found = true;
        var item = document.createElement('div');
        item.className = 'searchable-select-item';
        item.style.cssText = 'padding:8px 12px;cursor:pointer;font-size:14px;color:var(--text)';
        item.textContent = text;
        item.dataset.value = opt.value;
        if (opt.value === select.value) item.style.background = 'var(--primary-container)';
        item.addEventListener('mouseenter', function() { this.style.background = 'var(--surface-hover)'; });
        item.addEventListener('mouseleave', function() { this.style.background = ''; });
        function seleccionarItem() {
          select.value = this.dataset.value;
          input.value = this.textContent;
          dropdown.style.display = 'none';
          if (typeof onChange === 'function') {
            onChange(select.value);
          } else {
            select.dispatchEvent(new Event('change', { bubbles: true }));
          }
        }
        item.addEventListener('mousedown', function(e) {
          e.preventDefault();
          seleccionarItem.call(this);
        });
        item.addEventListener('touchstart', function(e) {
          e.preventDefault();
          seleccionarItem.call(this);
        });
        dropdown.appendChild(item);
      });
      if (!found) {
        var noRes = document.createElement('div');
        noRes.style.cssText = 'padding:8px 12px;color:var(--text-secondary);font-size:13px';
        noRes.textContent = 'Sin resultados';
        dropdown.appendChild(noRes);
      }
    }
    input.addEventListener('focus', function() {
      buildList(input.value);
      dropdown.style.display = 'block';
    });
    input.addEventListener('blur', function() {
      setTimeout(function() { dropdown.style.display = 'none'; }, 200);
    });
    input.addEventListener('input', function() {
      buildList(input.value);
      dropdown.style.display = 'block';
    });
    if (select.selectedIndex > 0 && select.options[select.selectedIndex]) {
      input.value = select.options[select.selectedIndex].textContent;
    }
  }

  const CHART_COLORS = ['#00F5FF','#FF007F','#7F00FF','#FFD700','#00FF7F','#FF4500','#00BFFF','#FF00FF','#39FF14','#FF1493'];

  function drawDonut(canvas, data, options = {}) {
    if (!canvas || !data || !data.length) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    const cx = w / 2, cy = h / 2;
    const outerR = Math.min(cx, cy) - 8;
    const innerR = outerR * 0.55;
    const total = data.reduce((s, d) => s + d.value, 0);
    if (!total) {
      ctx.clearRect(0, 0, w, h);
      ctx.fillStyle = '#e2e8f0';
      ctx.beginPath(); ctx.arc(cx, cy, outerR, 0, Math.PI * 2); ctx.arc(cx, cy, innerR, 0, Math.PI * 2, true); ctx.fill();
      ctx.fillStyle = '#94a3b8'; ctx.font = '13px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText('Sin datos', cx, cy);
      return;
    }
    ctx.clearRect(0, 0, w, h);
    let startAngle = -Math.PI / 2;
    data.forEach((d, i) => {
      const sliceAngle = (d.value / total) * Math.PI * 2;
      ctx.beginPath();
      ctx.arc(cx, cy, outerR, startAngle, startAngle + sliceAngle);
      ctx.arc(cx, cy, innerR, startAngle + sliceAngle, startAngle, true);
      ctx.closePath();
      ctx.fillStyle = d.color || CHART_COLORS[i % CHART_COLORS.length];
      ctx.fill();
      if (options.showLabels !== false && sliceAngle > 0.3) {
        const mid = startAngle + sliceAngle / 2;
        const lx = cx + Math.cos(mid) * (outerR + innerR) / 2;
        const ly = cy + Math.sin(mid) * (outerR + innerR) / 2;
        ctx.fillStyle = '#1e293b';
        ctx.font = 'bold 12px sans-serif';
        ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText(Math.round((d.value / total) * 100) + '%', lx, ly);
      }
      startAngle += sliceAngle;
    });
    ctx.beginPath(); ctx.arc(cx, cy, innerR, 0, Math.PI * 2); ctx.fillStyle = '#fff'; ctx.fill();
    ctx.fillStyle = '#1e293b'; ctx.font = 'bold 18px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(total, cx, cy);
  }

  function donutLegend(data, colors) {
    return '<div class="chart-legend">' + data.map((d, i) =>
      '<span class="legend-item"><span class="legend-dot" style="background:' + (d.color || colors[i % colors.length]) + '"></span>' + d.label + ': ' + d.value + '</span>'
    ).join('') + '</div>';
  }

  function drawBar(canvas, labels, values, colors) {
    if (!canvas || !labels || !labels.length) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    const pad = { top: 20, right: 16, bottom: 36, left: 16 };
    const chartW = w - pad.left - pad.right;
    const chartH = h - pad.top - pad.bottom;
    const maxVal = Math.max(...values, 1);
    const barW = Math.min(chartW / labels.length * 0.6, 48);
    const gap = chartW / labels.length;
    ctx.clearRect(0, 0, w, h);
    values.forEach((v, i) => {
      const barH = (v / maxVal) * chartH;
      const x = pad.left + gap * i + (gap - barW) / 2;
      const y = pad.top + chartH - barH;
      const grad = ctx.createLinearGradient(x, y, x, pad.top + chartH);
      const c = colors[i % colors.length];
      grad.addColorStop(0, c);
      grad.addColorStop(1, c + '88');
      ctx.fillStyle = grad;
      ctx.beginPath(); ctx.roundRect(x, y, barW, barH, [4, 4, 0, 0]); ctx.fill();
      ctx.fillStyle = '#475569'; ctx.font = '11px sans-serif'; ctx.textAlign = 'center';
      ctx.fillText(v, x + barW / 2, y - 6);
      ctx.fillStyle = '#64748b'; ctx.font = '10px sans-serif';
      ctx.fillText(labels[i].length > 10 ? labels[i].slice(0, 10) + '..' : labels[i], x + barW / 2, pad.top + chartH + 16);
    });
  }

  // Filtros en tiempo real para inputs
  function soloNumeros(inputId, maxLength) {
    var input = document.getElementById(inputId);
    if (!input) return;
    if (maxLength) input.setAttribute('maxlength', maxLength);
    input.addEventListener('input', function(e) {
      e.target.value = e.target.value.replace(/[^0-9]/g, '');
    });
    input.addEventListener('paste', function(e) {
      e.preventDefault();
      var pastedText = (e.clipboardData || window.clipboardData).getData('text');
      var cleaned = pastedText.replace(/[^0-9]/g, '');
      if (maxLength) cleaned = cleaned.substring(0, maxLength);
      e.target.value = cleaned;
    });
  }

  function soloLetras(inputId, maxLength) {
    var input = document.getElementById(inputId);
    if (!input) return;
    if (maxLength) input.setAttribute('maxlength', maxLength);
    input.addEventListener('input', function(e) {
      e.target.value = e.target.value.replace(/[^a-zA-ZÀ-ÿ\s'.]/g, '');
    });
    input.addEventListener('paste', function(e) {
      e.preventDefault();
      var pastedText = (e.clipboardData || window.clipboardData).getData('text');
      var cleaned = pastedText.replace(/[^a-zA-ZÀ-ÿ\s'.]/g, '');
      if (maxLength) cleaned = cleaned.substring(0, maxLength);
      e.target.value = cleaned;
    });
  }

  function soloAlfanumerico(inputId, maxLength, permitirGuiones) {
    var input = document.getElementById(inputId);
    if (!input) return;
    if (maxLength) input.setAttribute('maxlength', maxLength);
    var pattern = permitirGuiones ? /[^a-zA-Z0-9-]/g : /[^a-zA-Z0-9]/g;
    input.addEventListener('input', function(e) {
      e.target.value = e.target.value.replace(pattern, '').toUpperCase();
    });
    input.addEventListener('paste', function(e) {
      e.preventDefault();
      var pastedText = (e.clipboardData || window.clipboardData).getData('text');
      var cleaned = pastedText.replace(pattern, '').toUpperCase();
      if (maxLength) cleaned = cleaned.substring(0, maxLength);
      e.target.value = cleaned;
    });
  }

  function validarTelefonoTiempoReal(inputId) {
    var input = document.getElementById(inputId);
    if (!input) return;
    input.addEventListener('input', function(e) {
      var v = e.target.value;
      if (v.length > 0 && v.length < 10) {
        input.classList.add('is-invalid');
      } else {
        input.classList.remove('is-invalid');
      }
    });
    input.addEventListener('blur', function(e) {
      Utils.valTelefono(e.target.value, inputId);
    });
  }

  function validarEmailTiempoReal(inputId) {
    var input = document.getElementById(inputId);
    if (!input) return;
    input.addEventListener('input', function(e) {
      var v = e.target.value;
      if (v.length > 0 && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) {
        input.classList.add('is-invalid');
      } else {
        input.classList.remove('is-invalid');
      }
    });
    input.addEventListener('blur', function(e) {
      Utils.valEmail(e.target.value, inputId);
    });
  }

  function mostrarFotoGrande(src) {
    var overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.85);z-index:25000;display:flex;align-items:center;justify-content:center;padding:24px;cursor:pointer';
    overlay.onclick = function() { overlay.remove(); };
    var img = document.createElement('img');
    img.src = src;
    img.style.cssText = 'max-width:95%;max-height:95%;border-radius:8px;box-shadow:0 8px 40px rgba(0,0,0,0.5);object-fit:contain';
    overlay.appendChild(img);
    document.body.appendChild(overlay);
  }

  function todayStr() {
    var parts = new Intl.DateTimeFormat('es-CO', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    var y, m, d;
    parts.forEach(function(p) { if (p.type === 'year') y = p.value; else if (p.type === 'month') m = p.value; else if (p.type === 'day') d = p.value; });
    return y + '-' + m + '-' + d;
  }

  function dateToStr(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  return {
    parseDate, formatDate, formatDateTime, formatTime, formatCurrency,
    showToast, showConfirm, showAlert, confirmarConPassword, loadingSpinner, emptyState,
    serializeForm, validateRequired, validateEmail, validatePhone, escapeHtml,
    estadoBadge, populateSelect, getSelectedText, modal,
    monthName, periodoLabel, paginate, paginationHtml,
    drawDonut, donutLegend, drawBar, CHART_COLORS,
    limpiarErrores, mostrarError,
    valNombre, valApellido, valDocumento, valTelefono, valEmail,
    valRequerido, valSelect, valNumero, valEntero, valFecha, valFechaNacimiento,
    valPlaca, valLongitud, valUsername, valPassword,
    soloNumeros, soloLetras, soloAlfanumerico, validarTelefonoTiempoReal, validarEmailTiempoReal,
    mostrarFotoGrande, todayStr, dateToStr,
    buscadorHtml, crearBuscador, selectBusqueda
  };
})();
