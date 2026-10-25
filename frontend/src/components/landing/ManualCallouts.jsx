import { QRCodeSVG } from 'qrcode.react';
import { CheckCircle2, CreditCard, Landmark, Package, ScanLine } from 'lucide-react';

const cop = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
});

/** Square 1:1 call-out box pinned to a corner of a manual page. */
export function Callout({ title, children, className = '' }) {
  return (
    <figure className={`m-callout ${className}`}>
      <figcaption className="m-callout-head">
        <span>{title}</span>
        <span className="m-callout-scale" aria-hidden="true">
          1:1
        </span>
      </figcaption>
      <div className="m-callout-body">{children}</div>
      <p className="m-callout-note">Datos de ejemplo</p>
    </figure>
  );
}

function Row({ label, value, strong = false }) {
  return (
    <div className="m-row">
      <span className="m-row-label">{label}</span>
      <span className={`m-row-value${strong ? ' is-strong' : ''}`}>{value}</span>
    </div>
  );
}

export function QrInviteCallout({ className }) {
  return (
    <Callout title="Invitación QR" className={className}>
      <div className="m-qr">
        <QRCodeSVG
          value="SAED-INVITACION-DE-EJEMPLO"
          size={88}
          bgColor="transparent"
          fgColor="currentColor"
          level="M"
          aria-hidden="true"
        />
        <div className="m-qr-meta">
          <span className="m-strong">Laura Gómez</span>
          <span>Apto 302 · Torre 1</span>
          <span className="m-num">Hoy, 2:00 – 6:00 p. m.</span>
        </div>
      </div>
    </Callout>
  );
}

export function UnitsCallout({ className }) {
  return (
    <Callout title="Unidades y contratos" className={className}>
      <div className="m-mini-table" role="presentation">
        <Row label="Apto 302 · T1" value="Familia Ramírez" strong />
        <Row label="Rol" value="Propietario" />
        <Row label="Apto 303 · T1" value="Andrés Rojas" strong />
        <Row label="Contrato" value="Arriendo vigente" />
      </div>
    </Callout>
  );
}

export function GateCallout({ className }) {
  return (
    <Callout title="Escáner de portería" className={className}>
      <div className="m-status is-ok">
        <ScanLine aria-hidden="true" />
        <span>Invitación válida</span>
      </div>
      <Row label="Visitante" value="Laura Gómez" strong />
      <Row label="Destino" value="Apto 302 · Torre 1" />
      <Row label="Ingreso" value={<span className="m-num">2:14 p. m.</span>} />
    </Callout>
  );
}

const BAYS = [
  { id: 'V-01', plate: 'DEM-123' },
  { id: 'V-02', plate: null },
  { id: 'V-03', plate: 'KLR-901' },
  { id: 'V-04', plate: null },
];

export function PackagesParkingCallout({ className }) {
  return (
    <Callout title="Paquetes y parqueadero" className={className}>
      <div className="m-status">
        <Package aria-hidden="true" />
        <span>Paquete en custodia · Apto 504</span>
      </div>
      <ul className="m-bays" aria-label="Bahías de visitantes de ejemplo">
        {BAYS.map((bay) => (
          <li key={bay.id} className={bay.plate ? 'is-taken' : undefined}>
            <span className="m-num">{bay.id}</span>
            <span>{bay.plate ?? 'Libre'}</span>
          </li>
        ))}
      </ul>
    </Callout>
  );
}

export function DuesCallout({ className }) {
  return (
    <Callout title="Cartera y pagos" className={className}>
      <Row label="Cuota de administración" value={<span className="m-num">{cop.format(285000)}</span>} strong />
      <Row label="Periodo" value="Octubre" />
      <div className="m-chips" aria-label="Medios de pago">
        <span className="m-chip">PSE</span>
        <span className="m-chip">
          <CreditCard aria-hidden="true" />
          Tarjeta
        </span>
      </div>
      <div className="m-status is-ok">
        <CheckCircle2 aria-hidden="true" />
        <span>Pago recibido por Wompi</span>
      </div>
    </Callout>
  );
}

export function AssemblyCallout({ className }) {
  return (
    <Callout title="Asamblea y comunidad" className={className}>
      <div className="m-status">
        <Landmark aria-hidden="true" />
        <span>Asamblea ordinaria · 15 de marzo</span>
      </div>
      <Row label="Coeficiente Apto 302" value={<span className="m-num">0,842 %</span>} />
      <Row label="Acta" value="Borrador" />
      <Row label="PQRS" value="Petición en trámite" />
      <Row label="Reserva" value="Salón comunal · sábado" />
    </Callout>
  );
}

export function ResidentCallout({ className }) {
  return (
    <Callout title="Portal del residente" className={className}>
      <div className="m-phone-mini">
        <span className="m-strong">Apto 302 · Torre 1</span>
        <div className="m-phone-card">
          <span>Cuota de octubre</span>
          <span className="m-num m-strong">{cop.format(285000)}</span>
          <span className="m-phone-btn">Pagar</span>
        </div>
        <Row label="Visitas" value="1 invitación activa" />
        <Row label="Reservas" value="Salón comunal · sábado" />
      </div>
    </Callout>
  );
}
