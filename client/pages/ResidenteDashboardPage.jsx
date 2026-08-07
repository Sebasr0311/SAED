import { useEffect, useRef, useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency } from '../lib/utils.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

const CHART_COLORS = ['#0F2044', '#163060', '#3D6BBF', '#6B93D6', '#A8C4EC', '#D6E5F7', '#D97706', '#F59E0B', '#10B981', '#059669', '#DC2626', '#EF4444'];

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
    ctx.fillStyle = '#e2e8f0';
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
    ctx.fillStyle = d.color || CHART_COLORS[i % CHART_COLORS.length];
    ctx.fill();
    startAngle += sliceAngle;
  });
  // Centro con total
  ctx.fillStyle = '#0f172a';
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
  ctx.strokeStyle = '#e2e8f0';
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
    ctx.fillStyle = d.color || CHART_COLORS[i % CHART_COLORS.length];
    ctx.fillRect(x, y, w, h);
    // Label
    ctx.fillStyle = '#475569';
    ctx.font = '11px system-ui';
    ctx.textAlign = 'center';
    ctx.fillText(d.label.slice(0, 8), x + w / 2, padding + innerH + 14);
  });
}

function DonutChart({ data, title }) {
  const ref = useRef(null);
  useEffect(() => {
    drawDonut(ref.current, data);
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
                background: d.color || CHART_COLORS[i % CHART_COLORS.length],
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
  const { data } = useFetch(() => api.get(`/residentes/${user?.idResidente}/resumen`), [user]);
  const { data: pagosPorMes } = useFetch(
    () => api.get(`/pagos/registrados`).then((p) => p.filter((x) => x.idApartamento)),
    []
  );
  const resumen = data || {};

  const pagosPorEstado = (pagosPorMes || []).reduce((acc, p) => {
    const k = p.estado || 'OTROS';
    acc[k] = (acc[k] || 0) + Number(p.valorPagado || p.monto || 0);
    return acc;
  }, {});
  const donutData = Object.entries(pagosPorEstado).map(([label, value]) => ({
    label,
    value,
    color: label === 'PAGADA' ? '#10B981' : label === 'PENDIENTE' ? '#D97706' : '#3D6BBF',
  }));

  return (
    <div>
      <PageHeader
        title="Mi Panel"
        subtitle={`Bienvenido${user?.username ? `, ${user.username}` : ''}`}
      />
      <div className="card-grid-4">
        <Stat icon="apartment" value={resumen.apartamento || '—'} label="Apartamento" color="blue" />
        <Stat icon="description" value={resumen.estadoContrato || '—'} label="Estado Contrato" color="amber" />
        <Stat
          icon="payments"
          value={formatCurrency(resumen.cuotasArriendo)}
          label="Cuotas de Arriendo"
          color="green"
        />
        <Stat
          icon="gavel"
          value={formatCurrency(resumen.multas)}
          label="Multas Pendientes"
          color="amber"
        />
      </div>

      {donutData.length > 0 && (
        <div style={{ marginTop: '20px' }}>
          <h3 style={{ marginBottom: '12px', fontSize: '16px', fontWeight: 700 }}>Resumen de pagos</h3>
          <div className="card-grid-2">
            <DonutChart data={donutData} title="Distribución por estado" />
          </div>
        </div>
      )}
    </div>
  );
}
