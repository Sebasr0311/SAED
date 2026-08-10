import { useEffect, useRef, useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDateTime, formatDate, imageSrc } from '../lib/utils.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Button } from '../components/ui/Button.jsx';
import Toast from '../components/ui/Toast.jsx';

// Paleta de graficos: navy en light; tonos mas claros en dark para mantener
// contraste sobre superficies oscuras. Se resuelve en runtime leyendo el tema.
// Marcas (5) desde tokens --chart-*, recalculadas al cambiar tema; estados fijos.
const CHART_COLORS_LIGHT = [
  cssVar('--chart-1', '#0F2044'), cssVar('--chart-2', '#163060'), cssVar('--chart-3', '#3D6BBF'),
  cssVar('--chart-4', '#6B93D6'), cssVar('--chart-5', '#A8C4EC'),
  '#D6E5F7', '#D97706', '#F59E0B', '#10B981', '#059669', '#DC2626', '#EF4444',
];
const CHART_COLORS_DARK = [
  cssVar('--chart-1', '#93B4E8'), cssVar('--chart-2', '#6B93D6'), cssVar('--chart-3', '#3D6BBF'),
  cssVar('--chart-4', '#2855A0'), cssVar('--chart-5', '#A8C4EC'),
  '#D6E5F7', '#FBBF24', '#F59E0B', '#34D399', '#10B981', '#F87171', '#EF4444',
];

function isDarkTheme() {
  return typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'dark';
}
function chartColors() {
  return isDarkTheme() ? CHART_COLORS_DARK : CHART_COLORS_LIGHT;
}
/** Lee un token CSS (--x) del :root, con fallback. */
function cssVar(name, fallback) {
  if (typeof document === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body" style={{ minWidth: 0 }}>
        <div className="stat-value" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

function drawDonut(canvas, data) {
  if (!canvas || !data || data.length === 0) return;
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.clientWidth || 200;
  const cssH = canvas.clientHeight || 200;
  canvas.width = cssW * dpr;
  canvas.height = cssH * dpr;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, cssW, cssH);
  const cx = cssW / 2;
  const cy = cssH / 2;
  const outerR = Math.min(cx, cy) - 8;
  const innerR = outerR * 0.55;
  const total = data.reduce((s, d) => s + Number(d.value || 0), 0);
  if (total === 0) {
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
    ctx.arc(cx, cy, innerR, 0, Math.PI * 2, true);
    ctx.closePath();
    ctx.fillStyle = cssVar('--border', '#e2e8f0');
    ctx.fill();
    return;
  }
  let startAngle = -Math.PI / 2;
  data.forEach((d, i) => {
    const sliceAngle = (Number(d.value) / total) * Math.PI * 2;
    ctx.beginPath();
    ctx.arc(cx, cy, outerR, startAngle, startAngle + sliceAngle);
    ctx.arc(cx, cy, innerR, startAngle + sliceAngle, startAngle, true);
    ctx.closePath();
    ctx.fillStyle = d.color || chartColors()[i % chartColors().length];
    ctx.fill();
    startAngle += sliceAngle;
  });
  // Centro con total
    ctx.fillStyle = cssVar('--on-surface', '#0f172a');
  ctx.font = 'bold 14px system-ui';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(formatCurrency(total), cx, cy);
}

function drawBar(canvas, data) {
  if (!canvas || !data || data.length === 0) return;
  const ctx = canvas.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.clientWidth || 400;
  const cssH = canvas.clientHeight || 200;
  canvas.width = cssW * dpr;
  canvas.height = cssH * dpr;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, cssW, cssH);
  const max = Math.max(...data.map((d) => Number(d.value || 0)), 1);
  const padding = 30;
  const innerW = cssW - padding * 2;
  const innerH = cssH - padding * 2;
  const barW = innerW / data.length;
  // Eje Y
    ctx.strokeStyle = cssVar('--border', '#e2e8f0');
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(padding, padding);
  ctx.lineTo(padding, padding + innerH);
  ctx.lineTo(cssW - padding, padding + innerH);
  ctx.stroke();
  // Bars
  data.forEach((d, i) => {
    const h = (Number(d.value) / max) * innerH;
    const x = padding + barW * i + barW * 0.15;
    const w = barW * 0.7;
    const y = padding + innerH - h;
    ctx.fillStyle = d.color || chartColors()[i % chartColors().length];
    ctx.fillRect(x, y, w, h);
    // Label
    ctx.fillStyle = cssVar('--text-secondary', '#475569');
    ctx.font = '11px system-ui';
    ctx.textAlign = 'center';
    ctx.fillText(d.label.slice(0, 8), x + w / 2, padding + innerH + 14);
  });
}

function DonutChart({ data, title }) {
  const ref = useRef(null);
  useEffect(() => {
    drawDonut(ref.current, data);
    // Repintar al cambiar light/dark (los colores leen tokens CSS).
    const mo = new MutationObserver(() => drawDonut(ref.current, data));
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => mo.disconnect();
  }, [data]);
  return (
    <div className="card" style={{ textAlign: 'center' }}>
      {title && <h3 className="card-title">{title}</h3>}
      <canvas ref={ref} style={{ width: '100%', height: '200px', maxWidth: '220px', margin: '0 auto' }} />
      <div style={{ marginTop: '12px', display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: '8px' }}>
        {data.map((d, i) => (
          <div key={d.label} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px' }}>
            <span
              style={{
                width: '10px',
                height: '10px',
                background: d.color || chartColors()[i % chartColors().length],
                borderRadius: '2px',
              }}
            />
            {d.label}: {formatCurrency(d.value)}
          </div>
        ))}
      </div>
    </div>
  );
}

function BarChart({ data, title }) {
  const ref = useRef(null);
  useEffect(() => {
    drawBar(ref.current, data);
    // Repintar al cambiar light/dark (los colores leen tokens CSS).
    const mo = new MutationObserver(() => drawBar(ref.current, data));
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => mo.disconnect();
  }, [data]);
  return (
    <div className="card">
      {title && <h3 className="card-title">{title}</h3>}
      <canvas ref={ref} style={{ width: '100%', height: '200px' }} />
    </div>
  );
}

export default function ResidenteDashboardPage() {
  const { user } = useAuth();
  const { data: perfilResidente } = useFetch(
    () => (user?.idResidente ? api.get(`/residentes/${user.idResidente}`) : Promise.resolve(null)),
    [user]
  );
  const perfil = perfilResidente?.raw || perfilResidente || {};
  const nombreResidente = `${perfil?.nombres || ''} ${perfil?.apellidos || ''}`.trim();
  const { data, refetch: refetchDashboard } = useFetch(() => api.get(`/residentes/${user?.idResidente}/dashboard`), [user]);
  const { data: pagosPorMes } = useFetch(
    () => api.get(`/pagos/registrados`),
    []
  );
  const { data: qrsRaw } = useFetch(
    () => (user?.idResidente ? api.get(`/residentes/${user.idResidente}/qr-activos`) : Promise.resolve([])),
    [user]
  );
  const qrActivos = qrsRaw?.items || qrsRaw || [];
  const resumen = data?.raw || data || {};
  const apartamento = resumen.apartamento || {};
  const contrato = resumen.contrato || {};
  const cuotasArriendo = (resumen.cuotas || []).reduce((s, c) => s + Number(c.valorTotal || 0), 0);
  const multasPendientes = (resumen.multas || []).reduce(
    (s, m) => s + (m.estado === 'PENDIENTE' ? Number(m.monto || 0) : 0),
    0
  );

  const pagosPorEstado = (pagosPorMes?.items || pagosPorMes || []).reduce((acc, p) => {
    const k = p.tipoPago || 'OTROS';
    acc[k] = (acc[k] || 0) + Number(p.valor || 0);
    return acc;
  }, {});
  const donutData = Object.entries(pagosPorEstado).map(([label, value]) => ({
    label,
    value,
    color: label === 'CUOTA' ? cssVar('--accent-green', '#10B981') : label === 'MULTA' ? cssVar('--warn', '#D97706') : cssVar('--border-focus', '#3D6BBF'),
  }));

  // ==== Pagos en línea (Wompi) ====
  const MESES_W = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
  const { data: wompiHistorialRaw, refetch: refetchHistorialWompi } = useFetch(
    () => api.get('/pagos/wompi/historial'),
    []
  );
  const wompiHistorial = wompiHistorialRaw?.items || wompiHistorialRaw || [];
  const cuotasPendientes = (resumen.cuotas || []).filter((c) => c.estado !== 'PAGADA');
  const multasPendientesList = (resumen.multas || []).filter((m) => m.estado === 'PENDIENTE');
  const [pagando, setPagando] = useState(null); // {concepto, id, label}

  function cargarWidgetWompi() {
    // Sin wrapper: cada camino devuelve UNA promesa que SIEMPRE resuelve o
    // rechaza. (Antes se devolvia `window._wompiWidgetCargando` desde el
    // ejecutor de un `new Promise` externo, y ese return se ignora: cuando la
    // carga ya estaba en vuelo, el await quedaba colgado para siempre.)
    if (window.WidgetCheckout) return Promise.resolve();
    if (window._wompiWidgetCargando) return window._wompiWidgetCargando;
    const promesa = new Promise((res, rej) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.wompi.co/widget.js';
      s.async = true;
      const timer = setTimeout(() => {
        window._wompiWidgetCargando = null;
        rej(new Error('El widget de pago tarda demasiado en cargar. Revisá tu conexión e intentá de nuevo.'));
      }, 15000);
      s.onload = () => { clearTimeout(timer); window._wompiWidgetCargando = null; res(); };
      s.onerror = () => { clearTimeout(timer); window._wompiWidgetCargando = null; rej(new Error('No se pudo cargar el widget de pago')); };
      document.head.appendChild(s);
    });
    window._wompiWidgetCargando = promesa;
    return promesa;
  }

  // Precarga: el widget empieza a descargarse al entrar al dashboard, no al
  // clickear "Pagar". Asi el script suele estar listo cuando el usuario paga.
  useEffect(() => {
    cargarWidgetWompi().catch(() => { /* best-effort: se reintenta al pagar */ });
  }, []);

  // El widget de Wompi se inyecta ocupando todo el ancho disponible (en desktop
  // llega a ~1100px). Lo encuadramos con CSS en un checkout centrado tipo modal
  // (max 560px) con backdrop. IMPORTANTE: el iframe NO se mueve del DOM — el
  // widget.js lo localiza en su contenedor original para ajustar la altura por
  // postMessage; moverlo rompe ese ajuste.
  useEffect(() => {
    const obs = new MutationObserver(() => {
      const frame = document.querySelector('iframe[src*="checkout.wompi.co"]');
      if (frame) {
        if (!document.querySelector('.wompi-overlay')) {
          const overlay = document.createElement('div');
          overlay.className = 'wompi-overlay';
          frame.parentNode.insertBefore(overlay, frame);
        }
      } else {
        // El widget se cerro y removio su iframe: limpiar el backdrop
        const ov = document.querySelector('.wompi-overlay');
        if (ov) ov.remove();
      }
    });
    obs.observe(document.body, { childList: true, subtree: true });
    return () => obs.disconnect();
  }, []);

  async function pollEstadoWompi(referencia) {
    const t0 = Date.now();
    while (Date.now() - t0 < 180000) {
      await new Promise((r) => setTimeout(r, 2000));
      try {
        const est = await api.get(`/pagos/wompi/estado?referencia=${encodeURIComponent(referencia)}`);
        const estado = est.estado || 'PENDIENTE';
        if (['APROBADO', 'RECHAZADO', 'VENCIDO', 'ERROR'].includes(estado)) {
          setToast({
            message: estado === 'APROBADO' ? 'Pago confirmado. Recibirás el recibo por correo.' : `El pago fue ${estado.toLowerCase()}.`,
            type: estado === 'APROBADO' ? 'success' : 'error',
          });
          return;
        }
      } catch { /* reintentar */ }
    }
    setToast({ message: 'El pago quedó pendiente de confirmación; te avisaremos por correo.', type: 'info' });
  }

  async function pagarConWompi(concepto, id, label) {
    if (pagando) return;
    setPagando({ concepto, id, label });
    // Anti-colgado: si el widget se cierra sin completar, el callback de
    // WidgetCheckout.open() nunca corre y `pagando` quedaría activo para
    // siempre (botón "Abriendo…" que bloquea reintentos). Se resetea solo.
    const timer = setTimeout(() => {
      setPagando(null);
      setToast({ message: 'El pago se canceló o expiró. Podés volver a intentar.', type: 'info' });
    }, 60000);
    const finalizar = () => {
      clearTimeout(timer);
      setPagando(null);
      refetchHistorialWompi();
      if (typeof refetchDashboard === 'function') refetchDashboard();
    };
        try {
            const sol = await api.post('/pagos/wompi/solicitud', { concepto, id });
            // Idempotencia: si ya había un intento PENDIENTE con transacción creada en
      // Wompi, no se reabre el widget — solo se espera el estado final.
      if (sol.idTransaccionWompi) {
        setToast({ message: 'Ya hay un pago en curso para este ítem. Esperando confirmación…', type: 'info' });
        await pollEstadoWompi(sol.referencia);
        finalizar();
        return;
      }
      await cargarWidgetWompi();
            const customerData = user?.email
        ? { email: user.email, fullName: `${user.nombres || ''} ${user.apellidos || ''}`.trim() || undefined }
        : undefined;
      const checkout = new window.WidgetCheckout({
        currency: 'COP',
        amountInCents: sol.montoCentavos,
        reference: sol.referencia,
        publicKey: sol.publicKey,
        signature: { integrity: sol.firmaIntegridad },
        // Sin redirectUrl: Wompi bloquea con 403 los redirect-url de localhost
        // (entornos de desarrollo), y el resultado ya se maneja por callback +
        // polling (pollEstadoWompi). Evitar el redirect elimina el bloqueo en
        // cualquier entorno y mantiene la confirmacion por webhook/polling.
        // redirectUrl: `${window.location.origin}/residente-dashboard?pago=resultado`,
        ...(customerData ? { customerData } : {}),
      });
            checkout.open(async (result) => {
        // El widget devuelve la transaccion creada en Wompi: registrarla en el
        // backend para que el polling pueda consultar el estado real (el
        // webhook puede no estar configurado o perderse).
        if (result && result.transaction && result.transaction.id) {
          try {
            await api.post('/pagos/wompi/transaccion', { referencia: sol.referencia, idTransaccionWompi: result.transaction.id });
          } catch { /* best-effort */ }
        }
        await pollEstadoWompi(sol.referencia);
        finalizar();
      });
    } catch (err) {
      clearTimeout(timer);
      setToast({ message: err.message || 'No se pudo iniciar el pago', type: 'error' });
      setPagando(null);
    }
  }

  const badgeWompi = (estado) => {
    const map = {
      APROBADO: { label: 'Pagado', color: 'var(--accent-green)' },
      RECHAZADO: { label: 'Rechazado', color: 'var(--warn)' },
      VENCIDO: { label: 'Vencido', color: 'var(--warn)' },
      ERROR: { label: 'Error', color: 'var(--error)' },
      PENDIENTE: { label: 'Pendiente', color: 'var(--text-muted)' },
    };
    const e = map[estado] || map.PENDIENTE;
    return <span style={{ color: e.color, fontWeight: 700, fontSize: '12px' }}>{e.label}</span>;
  };

  // ==== B1: Poll de confirmación de visitas (recuperado del legacy) ====
  const [confirmarPendiente, setConfirmarPendiente] = useState(null); // mensaje en modal o null
  const [confirmando, setConfirmando] = useState(false);
  const confirmandoRef = useRef(false);
  const [toast, setToast] = useState(null);
  const failCountRef = useRef(0);
  const [backoffActivo, setBackoffActivo] = useState(false); // reactivo: recrea el intervalo al cambiar

  async function tickConfirmacion() {
    // Pausa mientras el modal está abierto (el estado actúa como "pause")
    if (confirmarPendiente) return;
    if (document.visibilityState !== 'visible') return;
    try {
      const pendientes = await api.get('/buzon/confirmar-pendiente');
      const lista = Array.isArray(pendientes) ? pendientes : pendientes?.items || [];
      failCountRef.current = 0;
      if (backoffActivo) setBackoffActivo(false); // red recuperada: el intervalo vuelve solo a 5s
      if (lista.length > 0) setConfirmarPendiente(lista[0]);
    } catch {
      // Best-effort: el modal es el feedback, no un toast por tick.
      // Umbral: 5 fallos consecutivos -> aviso único + backoff a 30s. El poll sigue vivo
      // (no se detiene): si la red se recupera sola, el tick exitoso restaura 5s sin
      // depender de que el residente abra/cierre el modal.
      failCountRef.current += 1;
      if (failCountRef.current >= 5 && !backoffActivo) {
        setBackoffActivo(true); // dispara re-render → el efecto recrea el intervalo con 30s
        setToast({ message: 'No se pudo verificar visitas pendientes. Se reintentará automáticamente.', type: 'warning' });
      }
    }
  }

  useEffect(() => {
    const interval = setInterval(tickConfirmacion, backoffActivo ? 30000 : 5000);
    return () => clearInterval(interval);
  }, [confirmarPendiente, backoffActivo]);

  // Al volver la pestaña visible: poll inmediato (no esperar el próximo tick)
  useEffect(() => {
    const onVisible = () => { if (document.visibilityState === 'visible') tickConfirmacion(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [confirmarPendiente]);

  async function responderConfirmacion(confirmado) {
    if (confirmandoRef.current) return; // doble submit (patrón generalizado)
    if (!confirmarPendiente) return;
    confirmandoRef.current = true;
    setConfirmando(true);
    try {
      await api.post('/buzon/confirmar', { idMensaje: confirmarPendiente.idMensaje, confirmado });
      setToast({ message: confirmado === 1 ? 'Acceso confirmado' : 'Acceso rechazado', type: 'success' });
      setConfirmarPendiente(null); // cierra; el próximo tick traerá el siguiente pendiente si hay
    } catch (err) {
      // El modal se MANTIENE abierto: el residente ve el error y puede reintentar;
      // el poll sigue pausado mientras haya modal.
      setToast({ message: err.message, type: 'error' });
    } finally {
      confirmandoRef.current = false;
      setConfirmando(false);
    }
  }

  // ==== B3: QR activos + compartir (recuperado del legacy) ====
  // Imagen QR via api.qrserver.com (fiel al legacy; sin dependencia nueva).
  // Seguridad: el QR es single-use + expira (el endpoint filtra usado=0 AND
  // fecha_expiracion>now); la validacion del portero es server-side — compartir
  // la imagen por error no la hace valida despues de usarse o expirar.
  function qrImageUrl(codigoQr) {
    return 'https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=' + encodeURIComponent(codigoQr);
  }

  function compartirTelegram(codigoQr, nombre) {
    const imgUrl = qrImageUrl(codigoQr);
    const text = encodeURIComponent(`Código QR de acceso para ${nombre || 'tu visita'}\n\nAbre esta imagen para escanear:\n${imgUrl}`);
    window.open(`https://t.me/share/url?url=${encodeURIComponent(imgUrl)}&text=${text}`, '_blank');
  }

  function compartirSMS(codigoQr, telefono) {
    const imgUrl = qrImageUrl(codigoQr);
    const body = encodeURIComponent(`Tu código QR de acceso: ${codigoQr} - Abre la imagen: ${imgUrl}`);
    window.open(telefono ? `sms:${telefono}?body=${body}` : `sms:?body=${body}`);
  }

  function compartirCorreo(codigoQr, nombre, email) {
    const imgUrl = qrImageUrl(codigoQr);
    const subject = encodeURIComponent('Código QR de Acceso');
    const body = encodeURIComponent(
      `Hola,\n\nHas recibido un código QR de acceso${nombre ? ` para ${nombre}` : ''}.\n\n` +
      `Código: ${codigoQr}\n\nO abre esta imagen para escanear:\n${imgUrl}\n\n` +
      `Preséntala en la entrada del edificio.`
    );
    window.open(email ? `mailto:${email}?subject=${subject}&body=${body}` : `mailto:?subject=${subject}&body=${body}`);
  }

  async function copiarQR(codigoQr) {
    try {
      await navigator.clipboard.writeText(codigoQr);
      setToast({ message: 'Código QR copiado al portapapeles', type: 'success' });
    } catch {
      setToast({ message: 'No se pudo copiar el código', type: 'error' });
    }
  }

  return (
    <div>
      <PageHeader
        title="Mi Panel"
        subtitle={`Bienvenido${nombreResidente || user?.username ? `, ${nombreResidente || user?.username}` : ''}`}
      />
      <div className="card-grid-4">
        <Stat icon="apartment" value={apartamento.numero || '—'} label="Apartamento" color="blue" />
        <Stat icon="description" value={contrato.estado || '—'} label="Estado Contrato" color="amber" />
        <Stat
          icon="payments"
          value={formatCurrency(cuotasArriendo)}
          label="Cuotas de Arriendo"
          color="green"
        />
        <Stat
          icon="gavel"
          value={formatCurrency(multasPendientes)}
          label="Multas Pendientes"
          color="amber"
        />
      </div>

      {donutData.length > 0 && (
        <div style={{ marginTop: '20px' }}>
                <h3 className="mb-3 text-base font-bold">Resumen de pagos</h3>
          <div className="card-grid-2">
            <DonutChart data={donutData} title="Distribución por tipo de pago" />
          </div>
        </div>
      )}

      {(cuotasPendientes.length > 0 || multasPendientesList.length > 0 || wompiHistorial.length > 0) && (
        <div className="card" style={{ marginTop: '20px' }}>
          <div style={{ fontWeight: 700, marginBottom: '2px' }}>Mis pagos</div>
          <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginBottom: '12px' }}>
            Pagá en línea tus cuotas y multas pendientes (tarjeta, Nequi, PSE o Bancolombia).
          </p>

          {cuotasPendientes.map((c) => (
            <div key={`wc-${c.idCuota}`} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 0', borderBottom: '1px solid var(--border)' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '13px' }}>
                  {c.tipoCuota === 'ADMINISTRACION' ? 'Cuota de administración' : 'Cuota de arriendo'} · {MESES_W[(c.mes || 1) - 1]} {c.anio}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  {c.fechaLimite ? `Vence ${formatDate(c.fechaLimite)} · ` : ''}{c.estado}
                </div>
              </div>
              <div style={{ fontWeight: 700, fontSize: '14px' }}>{formatCurrency(c.saldoPendiente ?? c.valorTotal)}</div>
              <Button size="sm" onClick={() => pagarConWompi('CUOTA', c.idCuota, `Cuota ${c.anio}/${String(c.mes).padStart(2, '0')}`)} disabled={!!pagando}>
                {pagando?.id === c.idCuota && pagando?.concepto === 'CUOTA' ? 'Abriendo…' : 'Pagar'}
              </Button>
            </div>
          ))}

          {multasPendientesList.map((m) => (
            <div key={`wm-${m.idMulta}`} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 0', borderBottom: '1px solid var(--border)' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '13px' }}>Multa · {m.tipo}</div>
                <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{m.descripcion || 'Multa pendiente'}</div>
              </div>
              <div style={{ fontWeight: 700, fontSize: '14px' }}>{formatCurrency(m.monto)}</div>
              <Button size="sm" onClick={() => pagarConWompi('MULTA', m.idMulta, `Multa ${m.tipo}`)} disabled={!!pagando}>
                {pagando?.id === m.idMulta && pagando?.concepto === 'MULTA' ? 'Abriendo…' : 'Pagar'}
              </Button>
            </div>
          ))}

          {wompiHistorial.length > 0 && (
            <div style={{ marginTop: '12px' }}>
              <div style={{ fontWeight: 600, fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>Intentos recientes</div>
              {wompiHistorial.slice(0, 5).map((h) => (
                <div key={h.referencia} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', padding: '4px 0' }}>
                  <span style={{ fontFamily: 'monospace', color: 'var(--text-muted)' }}>#{String(h.referencia).slice(0, 24)}</span>
                  <span>{formatCurrency(h.montoCentavos / 100)}</span>
                  {badgeWompi(h.estado)}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {qrActivos.length > 0 && (
        <div className="card" style={{ marginTop: '20px' }}>
          <div style={{ fontWeight: 700, marginBottom: '12px' }}>Códigos QR Activos</div>
          {qrActivos.map((qr) => (
            <div
              key={qr.idQr}
              style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '12px', border: '1px solid var(--border)', borderRadius: '8px', marginBottom: '8px' }}
            >
              <img
                src={qrImageUrl(qr.codigoQr)}
                alt={`QR de ${qr.nombreVisitante || 'visita'}`}
                style={{ borderRadius: '4px', width: '56px', height: '56px', flexShrink: 0 }}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontWeight: 600, fontSize: '13px' }}>{qr.nombreVisitante || 'Visitante'}</p>
                <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                  {qr.cantidadPersonas || 1} persona(s) · Vence: {formatDateTime(qr.fechaExpiracion)}
                </p>
                <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
                  #{String(qr.codigoQr).slice(0, 8)}...
                </p>
              </div>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                <Button size="sm" onClick={() => compartirTelegram(qr.codigoQr, qr.nombreVisitante)} title="Compartir por Telegram">Telegram</Button>
                <Button size="sm" onClick={() => compartirSMS(qr.codigoQr, '')} title="Compartir por SMS">SMS</Button>
                <Button size="sm" onClick={() => compartirCorreo(qr.codigoQr, qr.nombreVisitante, '')} title="Compartir por Correo">Correo</Button>
                <Button size="sm" variant="outline" onClick={() => copiarQR(qr.codigoQr)}>Copiar QR</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ==== B1: Modal de confirmación de visita ==== */}
      <Modal open={!!confirmarPendiente} onClose={() => setConfirmarPendiente(null)} title="Solicitud de Acceso">
        {confirmarPendiente && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <p style={{ fontSize: '14px', color: 'var(--text-muted)' }}>
              Un visitante está en portería esperando su confirmación
            </p>
            <p style={{ fontWeight: 600, fontSize: '14px' }}>{confirmarPendiente.titulo || ''}</p>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>{confirmarPendiente.cuerpo || ''}</p>
            {confirmarPendiente.fotoCaptura && (
              <img
                  src={imageSrc(confirmarPendiente.fotoCaptura)}
                  alt="Foto del visitante"
                  loading="lazy"
                style={{ maxWidth: '100%', maxHeight: '280px', objectFit: 'contain', borderRadius: '8px', border: '1px solid var(--border)' }}
              />
            )}
            <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
              <Button variant="outline" onClick={() => responderConfirmacion(0)} disabled={confirmando}>
                Rechazar
              </Button>
              <Button onClick={() => responderConfirmacion(1)} disabled={confirmando}>
                {confirmando ? 'Confirmando...' : 'Confirmar Acceso'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <Toast toast={toast} />
    </div>
  );
}
