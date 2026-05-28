const Pagos = (() => {
  var allCuotasPendientes = [];   // GET /cuotas?pendientes=true
  var allMultas = [];             // GET /multas/todas  (todos los estados)
  var residentes = [];            // filas agrupadas por apartamento
  var currentDetalleApto = null;  // apto que está abierto en el modal de detalle

  var METODOS = ['EFECTIVO', 'TRANSFERENCIA'];

  // ─────────────────────────────────────────────────────────────────────────
  // Agrupación por apartamento
  // ─────────────────────────────────────────────────────────────────────────
  function agrupar(cuotas, multas) {
    var map = {};

    cuotas.forEach(function(c) {
      var k = c.numeroApartamento || ('Apto #' + c.idContrato);
      if (!map[k]) {
        map[k] = {
          apto: k,
          residente: c.nombreResidente || '-',
          idContrato: c.idContrato,
          idApartamento: null,
          cuotas: [],
          multas: []
        };
      }
      map[k].cuotas.push(c);
    });

    multas.forEach(function(m) {
      if (m.estado !== 'PENDIENTE') return;
      var k = m.numeroApartamento || ('Apto #' + m.idApartamento);
      if (!map[k]) {
        map[k] = {
          apto: k,
          residente: m.nombreResidente || '-',
          idContrato: null,
          idApartamento: m.idApartamento,
          cuotas: [],
          multas: []
        };
      } else if (!map[k].idApartamento) {
        map[k].idApartamento = m.idApartamento;
      }
      map[k].multas.push(m);
    });

    return Object.values(map).sort(function(a, b) {
      return a.apto.localeCompare(b.apto, undefined, { numeric: true });
    });
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Render tabla principal
  // ─────────────────────────────────────────────────────────────────────────
  function renderTabla() {
    var tbody = document.getElementById('tbody-pagos-res');
    if (!tbody) return;

    if (!residentes.length) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center" ' +
        'style="color:var(--text-secondary);padding:32px">Sin cuotas ni multas pendientes</td></tr>';
      actualizarKpis();
      return;
    }

    tbody.innerHTML = residentes.map(function(r) {
      var totalCuotas = r.cuotas.reduce(function(s, c) { return s + (c.valorTotal || 0); }, 0);
      var totalMultas = r.multas.reduce(function(s, m) { return s + (parseFloat(m.monto) || 0); }, 0);

      var cBadge = r.cuotas.length
        ? '<span class="badge badge-warn">' +
            r.cuotas.length + ' cuota' + (r.cuotas.length > 1 ? 's' : '') +
            ' &middot; ' + Utils.formatCurrency(totalCuotas) +
          '</span>'
        : '<span style="color:var(--text-secondary);font-size:12px">\u2014</span>';

      var mBadge = r.multas.length
        ? '<span class="badge badge-danger">' +
            r.multas.length + ' multa' + (r.multas.length > 1 ? 's' : '') +
            ' &middot; ' + Utils.formatCurrency(totalMultas) +
          '</span>'
        : '<span style="color:var(--text-secondary);font-size:12px">\u2014</span>';

      return '<tr style="cursor:pointer" onclick="Pagos.verDetalle(\'' +
          r.apto.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + '\')">' +
        '<td><strong>' + Utils.escapeHtml(r.apto) + '</strong></td>' +
        '<td>' + Utils.escapeHtml(r.residente) + '</td>' +
        '<td>' + cBadge + '</td>' +
        '<td>' + mBadge + '</td>' +
        '<td><span class="btn btn-sm btn-outline" style="pointer-events:none">Ver detalle \u2192</span></td>' +
        '</tr>';
    }).join('');

    actualizarKpis();
  }

  function actualizarKpis() {
    var totalCuotas = residentes.reduce(function(s, r) {
      return s + r.cuotas.reduce(function(s2, c) { return s2 + (c.valorTotal || 0); }, 0);
    }, 0);
    var totalMultas = residentes.reduce(function(s, r) {
      return s + r.multas.reduce(function(s2, m) { return s2 + (parseFloat(m.monto) || 0); }, 0);
    }, 0);
    var el1 = document.getElementById('pagos-kpi-cuotas');
    var el2 = document.getElementById('pagos-kpi-multas');
    var el3 = document.getElementById('pagos-kpi-aptos');
    if (el1) el1.textContent = Utils.formatCurrency(totalCuotas);
    if (el2) el2.textContent = Utils.formatCurrency(totalMultas);
    if (el3) el3.textContent = residentes.length + ' apto' + (residentes.length !== 1 ? 's' : '');
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Cargar datos
  // ─────────────────────────────────────────────────────────────────────────
  async function cargar() {
    try {
      var resultados = await Promise.all([
        API.get('/cuotas?pendientes=true'),
        API.get('/multas/todas')
      ]);
      allCuotasPendientes = resultados[0];
      allMultas           = resultados[1];
      residentes = agrupar(allCuotasPendientes, allMultas);
      renderTabla();
    } catch (e) {
      Utils.showToast('Error al cargar pagos: ' + e.message, 'error');
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Modal de detalle
  // ─────────────────────────────────────────────────────────────────────────
  async function verDetalle(apto) {
    currentDetalleApto = apto;
    var fila = residentes.find(function(r) { return r.apto === apto; });
    if (!fila) return;

    // Abrir modal con spinner de carga
    var modalEl = Utils.modal(
      'Apto ' + Utils.escapeHtml(apto) + ' \u2014 ' + Utils.escapeHtml(fila.residente),
      '<div style="text-align:center;padding:40px;color:var(--text-secondary)">' +
        '<span class="material-symbols-outlined" style="font-size:36px;display:block;margin-bottom:8px">hourglass_top</span>' +
        'Cargando\u2026</div>',
      '<button class="btn btn-outline" onclick="this.closest(\'.modal-overlay\').remove()">Cerrar</button>'
    );
    // Ampliar modal para que quepan las tablas
    if (modalEl) modalEl.style.width = '860px';

    try {
      // Cuotas: traer TODAS (pagadas + pendientes) del contrato activo
      var todasCuotas = [];
      if (fila.idContrato) {
        todasCuotas = await API.get('/cuotas?contrato=' + fila.idContrato);
      }

      // Multas: filtrar desde el cache (ya tenemos todos los estados)
      var todasMultas = allMultas.filter(function(m) {
        var mismoApto = (m.numeroApartamento || '') === apto;
        var mismoId   = fila.idApartamento && m.idApartamento === fila.idApartamento;
        return mismoApto || mismoId;
      });

      // Reemplazar cuerpo del modal
      var bodyEl = document.querySelector('.modal-body');
      if (bodyEl) bodyEl.innerHTML = buildDetalleHtml(apto, todasCuotas, todasMultas);

    } catch (e) {
      var bodyEl = document.querySelector('.modal-body');
      if (bodyEl) bodyEl.innerHTML =
        '<p style="color:var(--danger);padding:16px">Error al cargar detalle: ' +
        Utils.escapeHtml(e.message) + '</p>';
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // HTML del cuerpo del modal de detalle
  // ─────────────────────────────────────────────────────────────────────────
  function buildDetalleHtml(apto, cuotas, multas) {
    var pagadas   = cuotas.filter(function(c) { return c.estado === 'PAGADA'; });
    var pendientes = cuotas.filter(function(c) { return c.estado !== 'PAGADA' && c.estado !== 'ANULADA'; });
    var anuladas  = cuotas.filter(function(c) { return c.estado === 'ANULADA'; });

    var mPagadas   = multas.filter(function(m) { return m.estado === 'PAGADA'; });
    var mPendientes = multas.filter(function(m) { return m.estado === 'PENDIENTE'; });
    var mAnuladas  = multas.filter(function(m) { return m.estado === 'ANULADA'; });

    var html = '';

    /* ── CUOTAS ── */
    html += '<div style="margin-bottom:28px">';
    html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">';
    html += '<span style="font-size:14px;font-weight:700;color:var(--text)">Cuotas de Arriendo / Administraci\u00f3n</span>';
    html += '<span class="badge badge-success">' + pagadas.length + ' pagada' + (pagadas.length !== 1 ? 's' : '') + '</span>';
    if (pendientes.length)
      html += '<span class="badge badge-warn">' + pendientes.length + ' pendiente' + (pendientes.length !== 1 ? 's' : '') + '</span>';
    if (anuladas.length)
      html += '<span class="badge badge-danger">' + anuladas.length + ' anulada' + (anuladas.length !== 1 ? 's' : '') + '</span>';
    html += '</div>';

    if (!cuotas.length) {
      html += '<p style="color:var(--text-secondary);font-size:13px;padding:8px 0">Sin cuotas registradas para este contrato.</p>';
    } else {
      html += '<div style="overflow-x:auto">';
      html += '<table class="data-table" style="font-size:13px">';
      html += '<thead><tr><th>Tipo</th><th>Periodo</th><th>Vencimiento</th><th>Valor</th><th>Estado</th><th></th></tr></thead>';
      html += '<tbody>';
      // Primero pendientes, luego pagadas, luego anuladas
      var orden = pendientes.concat(pagadas).concat(anuladas);
      orden.forEach(function(c) {
        var tipoBadge = c.tipoCuota === 'ADMINISTRACION'
          ? '<span class="badge badge-info">Admon</span>'
          : '<span class="badge badge-navy">Arriendo</span>';
        var btn = (c.estado !== 'PAGADA' && c.estado !== 'ANULADA')
          ? '<button class="btn btn-accent btn-sm" onclick="Pagos.abrirPagoCuota(' +
              c.idCuota + ',' + (c.valorTotal || 0) + ',\'' + apto.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + '\')">' +
              'Registrar Pago</button>'
          : '';
        html += '<tr>' +
          '<td>' + tipoBadge + '</td>' +
          '<td style="white-space:nowrap">' + Utils.escapeHtml(Utils.periodoLabel(c.anio, c.mes)) + '</td>' +
          '<td style="white-space:nowrap">' + Utils.formatDate(c.fechaLimite) + '</td>' +
          '<td style="white-space:nowrap">' + Utils.formatCurrency(c.valorTotal) + '</td>' +
          '<td>' + Utils.estadoBadge(c.estado || 'PENDIENTE') + '</td>' +
          '<td>' + btn + '</td>' +
          '</tr>';
      });
      html += '</tbody></table></div>';
    }
    html += '</div>';

    /* ── MULTAS ── */
    html += '<div>';
    html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px;flex-wrap:wrap">';
    html += '<span style="font-size:14px;font-weight:700;color:var(--text)">Multas</span>';
    html += '<span class="badge badge-success">' + mPagadas.length + ' pagada' + (mPagadas.length !== 1 ? 's' : '') + '</span>';
    if (mPendientes.length)
      html += '<span class="badge badge-danger">' + mPendientes.length + ' pendiente' + (mPendientes.length !== 1 ? 's' : '') + '</span>';
    if (mAnuladas.length)
      html += '<span class="badge badge-danger">' + mAnuladas.length + ' anulada' + (mAnuladas.length !== 1 ? 's' : '') + '</span>';
    html += '</div>';

    if (!multas.length) {
      html += '<p style="color:var(--text-secondary);font-size:13px;padding:8px 0">Sin multas registradas.</p>';
    } else {
      html += '<div style="overflow-x:auto">';
      html += '<table class="data-table" style="font-size:13px">';
      html += '<thead><tr><th>Tipo</th><th>Descripci\u00f3n</th><th>Fecha</th><th>Monto</th><th>Estado</th><th></th></tr></thead>';
      html += '<tbody>';
      var mOrden = mPendientes.concat(mPagadas).concat(mAnuladas);
      mOrden.forEach(function(m) {
        var tipoBadge = m.tipo === 'RUIDO'
          ? '<span class="badge badge-warn">Ruido</span>'
          : '<span class="badge badge-danger">Parqueadero</span>';
        var btn = m.estado === 'PENDIENTE'
          ? '<button class="btn btn-accent btn-sm" onclick="Pagos.abrirPagoMulta(' +
              m.idMulta + ',\'' + apto.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + '\')">' +
              'Registrar Pago</button>'
          : '';
        html += '<tr>' +
          '<td>' + tipoBadge + '</td>' +
          '<td>' + Utils.escapeHtml(m.descripcion || '\u2014') + '</td>' +
          '<td style="white-space:nowrap">' + Utils.formatDate(m.fechaCreacion) + '</td>' +
          '<td style="white-space:nowrap">' + Utils.formatCurrency(parseFloat(m.monto)) + '</td>' +
          '<td>' + Utils.estadoBadge(m.estado) + '</td>' +
          '<td>' + btn + '</td>' +
          '</tr>';
      });
      html += '</tbody></table></div>';
    }
    html += '</div>';

    return html;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Volver al modal de detalle (desde modal de pago → Cancelar)
  // ─────────────────────────────────────────────────────────────────────────
  function volverAlDetalle() {
    document.querySelectorAll('.modal-overlay').forEach(function(el) { el.remove(); });
    if (currentDetalleApto) verDetalle(currentDetalleApto);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Modal pago de CUOTA
  // ─────────────────────────────────────────────────────────────────────────
  function abrirPagoCuota(idCuota, valorTotal, apto) {
    currentDetalleApto = apto;
    document.querySelectorAll('.modal-overlay').forEach(function(el) { el.remove(); });

    var today   = new Date().toISOString().split('T')[0];
    var minDate = new Date();
    minDate.setMonth(minDate.getMonth() - 1);
    var minStr  = minDate.toISOString().split('T')[0];

    Utils.modal(
      'Registrar Pago de Cuota',
      '<form id="form-pago-cuota">' +
        '<input type="hidden" id="pago-id-cuota"    value="' + idCuota    + '">' +
        '<input type="hidden" id="pago-valor-total" value="' + valorTotal + '">' +
        '<div class="form-row">' +
          '<div class="form-group">' +
            '<label>Fecha de Pago</label>' +
            '<input type="date" id="pago-fecha" class="form-control"' +
              ' min="' + minStr + '" max="' + today + '" value="' + today + '">' +
            '<span class="field-error" id="pago-fecha-error"></span>' +
          '</div>' +
          '<div class="form-group">' +
            '<label>Valor Pagado</label>' +
            '<input type="number" id="pago-valor" class="form-control" step="100" min="1" max="' + valorTotal + '">' +
            '<span class="field-error" id="pago-valor-error"></span>' +
          '</div>' +
        '</div>' +
        '<div class="form-row">' +
          '<div class="form-group">' +
            '<label>M\u00e9todo de Pago</label>' +
            '<select id="pago-metodo" class="form-control" onchange="Pagos._toggleRefCuota()">' +
              '<option value="">Seleccione...</option>' +
              '<option value="EFECTIVO">Efectivo</option>' +
              '<option value="TRANSFERENCIA">Transferencia</option>' +
            '</select>' +
            '<span class="field-error" id="pago-metodo-error"></span>' +
          '</div>' +
          '<div class="form-group" id="pago-referencia-group" style="display:none">' +
            '<label>Referencia <span style="color:var(--danger)">*</span></label>' +
            '<input type="text" id="pago-referencia" class="form-control" maxlength="50"' +
              ' placeholder="Ej: TRF-20260527-001">' +
            '<span class="field-error" id="pago-referencia-error"></span>' +
          '</div>' +
        '</div>' +
        '<div class="form-group">' +
          '<label>Notas</label>' +
          '<textarea id="pago-notas" class="form-control" maxlength="500"></textarea>' +
        '</div>' +
      '</form>',
      '<button class="btn btn-outline" onclick="Pagos.volverAlDetalle()">Cancelar</button>' +
      '<button class="btn btn-primary" onclick="Pagos.confirmarPagoCuota()" id="btn-conf-cuota">Registrar Pago</button>'
    );
  }

  function _toggleRefCuota() {
    var metodo = document.getElementById('pago-metodo');
    var grupo  = document.getElementById('pago-referencia-group');
    if (!metodo || !grupo) return;
    var esTransf = metodo.value === 'TRANSFERENCIA';
    grupo.style.display = esTransf ? '' : 'none';
    if (!esTransf) {
      var refInput = document.getElementById('pago-referencia');
      if (refInput) refInput.value = '';
      var errSpan = document.getElementById('pago-referencia-error');
      if (errSpan) errSpan.textContent = '';
    }
  }

  async function confirmarPagoCuota() {
    var btn = document.getElementById('btn-conf-cuota');
    Utils.limpiarErrores('form-pago-cuota');

    // ── Fecha de pago ──────────────────────────────────────────────
    var today   = new Date().toISOString().split('T')[0];
    var minDate = new Date();
    minDate.setMonth(minDate.getMonth() - 1);
    var minStr  = minDate.toISOString().split('T')[0];

    var fecha = document.getElementById('pago-fecha').value;
    if (!fecha) {
      Utils.mostrarError('pago-fecha', 'La fecha de pago es obligatoria');
      return;
    }
    if (fecha > today) {
      Utils.mostrarError('pago-fecha', 'La fecha de pago no puede ser futura');
      return;
    }
    if (fecha < minStr) {
      Utils.mostrarError('pago-fecha', 'La fecha no puede ser anterior a 1 mes atrás');
      return;
    }

    // ── Valor pagado ───────────────────────────────────────────────
    var valorTotal = parseFloat(document.getElementById('pago-valor-total').value) || 0;
    var valorStr   = document.getElementById('pago-valor').value;
    var valorPagado = parseFloat(valorStr);
    if (!valorStr || isNaN(valorPagado) || valorPagado <= 0) {
      Utils.mostrarError('pago-valor', 'Ingrese un valor mayor a cero');
      return;
    }
    if (valorTotal > 0 && valorPagado > valorTotal) {
      Utils.mostrarError('pago-valor', 'El valor no puede superar ' + Utils.formatCurrency(valorTotal));
      return;
    }

    // ── Método de pago ─────────────────────────────────────────────
    var metodoPago = document.getElementById('pago-metodo').value;
    if (!metodoPago) {
      Utils.mostrarError('pago-metodo', 'Seleccione el método de pago');
      return;
    }

    // ── Referencia (solo TRANSFERENCIA) ───────────────────────────
    var referencia = '';
    if (metodoPago === 'TRANSFERENCIA') {
      referencia = (document.getElementById('pago-referencia').value || '').trim();
      if (!referencia) {
        Utils.mostrarError('pago-referencia', 'La referencia es obligatoria para transferencias');
        return;
      }
      if (referencia.length < 4) {
        Utils.mostrarError('pago-referencia', 'Mínimo 4 caracteres');
        return;
      }
      if (referencia.length > 50) {
        Utils.mostrarError('pago-referencia', 'Máximo 50 caracteres');
        return;
      }
      if (!/^[A-Za-z0-9\-]{4,50}$/.test(referencia)) {
        Utils.mostrarError('pago-referencia', 'Solo letras, números y guiones (sin espacios)');
        return;
      }
    }

    var d = {
      idCuota:     parseInt(document.getElementById('pago-id-cuota').value),
      fechaPago:   fecha,
      valorPagado: valorPagado,
      metodoPago:  metodoPago,
      referencia:  referencia,
      notas:       document.getElementById('pago-notas').value.trim()
    };

    if (btn) btn.disabled = true;
    try {
      await API.post('/pagos', d);
      Utils.showToast('Pago registrado exitosamente', 'success');
      document.querySelectorAll('.modal-overlay').forEach(function(el) { el.remove(); });
      await cargar();
      if (currentDetalleApto) verDetalle(currentDetalleApto);
    } catch (e) {
      Utils.showToast(e.message, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Modal pago de MULTA
  // ─────────────────────────────────────────────────────────────────────────
  function abrirPagoMulta(idMulta, apto) {
    currentDetalleApto = apto;
    document.querySelectorAll('.modal-overlay').forEach(function(el) { el.remove(); });

    var metOpts = METODOS.map(function(m) {
      return '<option value="' + m + '">' + m + '</option>';
    }).join('');

    Utils.modal(
      'Registrar Pago de Multa',
      '<form id="form-pago-multa">' +
        '<input type="hidden" id="multa-id" value="' + idMulta + '">' +
        '<div class="form-group">' +
          '<label>M\u00e9todo de Pago</label>' +
          '<select id="multa-metodo" class="form-control">' + metOpts + '</select>' +
          '<span class="field-error" id="multa-metodo-error"></span>' +
        '</div>' +
      '</form>',
      '<button class="btn btn-outline" onclick="Pagos.volverAlDetalle()">Cancelar</button>' +
      '<button class="btn btn-primary" onclick="Pagos.confirmarPagoMulta()" id="btn-conf-multa">Registrar Pago</button>'
    );
  }

  async function confirmarPagoMulta() {
    var btn = document.getElementById('btn-conf-multa');
    Utils.limpiarErrores('form-pago-multa');

    var metodoPago = document.getElementById('multa-metodo').value;
    if (!Utils.valSelect(metodoPago, 'multa-metodo', 'Seleccione el m\u00e9todo')) return;

    var idMulta = parseInt(document.getElementById('multa-id').value);
    if (btn) btn.disabled = true;
    try {
      await API.put('/multas/' + idMulta + '/pagar', { metodoPago: metodoPago });
      Utils.showToast('Multa pagada exitosamente', 'success');
      document.querySelectorAll('.modal-overlay').forEach(function(el) { el.remove(); });
      await cargar();
      if (currentDetalleApto) verDetalle(currentDetalleApto);
    } catch (e) {
      Utils.showToast(e.message, 'error');
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  return {
    cargar:             cargar,
    verDetalle:         verDetalle,
    volverAlDetalle:    volverAlDetalle,
    abrirPagoCuota:     abrirPagoCuota,
    confirmarPagoCuota: confirmarPagoCuota,
    abrirPagoMulta:     abrirPagoMulta,
    confirmarPagoMulta: confirmarPagoMulta,
    _toggleRefCuota:    _toggleRefCuota
  };
})();

Router.register('pagos', {
  html: document.getElementById('tpl-pagos').innerHTML,
  js: async function() {
    document.getElementById('page-title').textContent = 'Pagos';
    Pagos.cargar();
  }
});
