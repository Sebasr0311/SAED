import { BellRing, CarFront, Vote } from 'lucide-react';

const SPECS = [
  { part: 'Datos', spec: 'Aislados por conjunto en la base de datos. Un conjunto no ve los registros de otro.' },
  { part: 'Pagos', spec: 'Pagos en línea con Wompi: PSE y tarjetas.' },
  { part: 'Portería', spec: 'Invitaciones QR para visitantes y escáner en portería.' },
  { part: 'Cuentas', spec: 'Activación por enlace enviado al correo. SAED no envía contraseñas.' },
  { part: 'Equipos', spec: 'Funciona en el navegador, sin hardware adicional.' },
  { part: 'Marco legal', spec: 'Pensado para propiedad horizontal (Ley 675 de 2001): coeficientes, asambleas y actas.' },
  { part: 'Roles', spec: 'Administración, portería y residentes, más una consola para administradoras con varios conjuntos.' },
];

const UNBUILT = [
  { label: 'Reconocimiento de placas', icon: CarFront },
  { label: 'Quórum en vivo para asambleas virtuales', icon: Vote },
  { label: 'Notificaciones push', icon: BellRing },
];

export default function LandingSpecs() {
  return (
    <section id="especificaciones" className="m-section" aria-labelledby="m-specs-title">
      <div className="m-container">
        <div className="m-section-head">
          <h2 id="m-specs-title" className="m-h2">
            Especificaciones
          </h2>
          <p className="m-body">Lo que SAED hace hoy, sin cifras que no podamos demostrar.</p>
        </div>

        <div className="m-specs-layout">
          <table className="m-spec-table">
            <caption className="m-sr">Especificaciones verificadas de SAED</caption>
            <thead>
              <tr>
                <th scope="col">Componente</th>
                <th scope="col">Especificación</th>
              </tr>
            </thead>
            <tbody>
              {SPECS.map((row) => (
                <tr key={row.part}>
                  <th scope="row">{row.part}</th>
                  <td>{row.spec}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="m-unbuilt">
            <h3 className="m-h3">Piezas en fabricación</h3>
            <p className="m-body">
              Todavía no vienen en la caja. Las mostramos para que sepas qué no incluye SAED hoy.
            </p>
            <ul className="m-unbuilt-list">
              {UNBUILT.map(({ label, icon: Icon }) => (
                <li key={label} className="m-unbuilt-piece">
                  <Icon aria-hidden="true" strokeWidth={1.75} />
                  <span className="m-unbuilt-label">{label}</span>
                  <span className="m-stamp">Próximamente</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}
