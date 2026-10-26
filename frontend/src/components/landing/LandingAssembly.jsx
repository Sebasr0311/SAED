import { useEffect, useRef, useState } from 'react';
import ComplexModel, { StepMark } from './ComplexModel.jsx';
import {
  AssemblyCallout,
  DuesCallout,
  GateCallout,
  PackagesParkingCallout,
  ResidentCallout,
  UnitsCallout,
} from './ManualCallouts.jsx';

const STEPS = [
  {
    title: 'Base: unidades, residentes y contratos',
    copy: 'Primero se registran torres, unidades, propietarios, residentes y contratos de arriendo. Todo lo demás se apoya sobre esta base.',
    Callout: UnitsCallout,
  },
  {
    title: 'Portería con invitación QR',
    copy: 'El residente crea la invitación desde su portal y la portería la valida con el escáner de SAED en el navegador.',
    Callout: GateCallout,
  },
  {
    title: 'Paquetes y parqueadero de visitantes',
    copy: 'La portería registra los paquetes que quedan en custodia y asigna las bahías de visitantes, con la unidad de destino a la vista.',
    Callout: PackagesParkingCallout,
  },
  {
    title: 'Cartera y pagos en línea con Wompi',
    copy: 'Cuotas, cartera, presupuestos y conciliaciones en un solo lugar. Los residentes pagan con PSE o tarjeta a través de Wompi.',
    Callout: DuesCallout,
  },
  {
    title: 'Asambleas, documentos, PQRS y reservas',
    copy: 'Asambleas con coeficientes de copropiedad y actas según la Ley 675, más documentos, reglamentos, PQRS y reservas de zonas comunes.',
    Callout: AssemblyCallout,
  },
  {
    title: 'El residente en su celular',
    copy: 'Desde el navegador del teléfono, cada residente consulta y paga sus cuotas, invita visitas y reserva zonas comunes.',
    Callout: ResidentCallout,
  },
];

export default function LandingAssembly({ units }) {
  const [active, setActive] = useState(1);
  const stepRefs = useRef([]);

  useEffect(() => {
    const nodes = stepRefs.current.filter(Boolean);
    if (!nodes.length || typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) setActive(Number(entry.target.dataset.step));
        });
      },
      { rootMargin: '-45% 0px -50% 0px', threshold: 0 }
    );
    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, []);

  const ActiveCallout = STEPS[active - 1].Callout;

  return (
    <section id="pasos" className="m-section" aria-labelledby="m-steps-title">
      <div className="m-container">
        <div className="m-section-head">
          <h2 id="m-steps-title" className="m-h2">
            Instrucciones de armado
          </h2>
          <p className="m-body">
            Seis pasos sobre la misma base. Cada pieza nueva se suma a lo que ya está armado y
            comparte el mismo registro.
          </p>
        </div>

        <div className="m-assembly">
          <ol className="m-steps">
            {STEPS.map(({ title, copy, Callout }, i) => {
              const step = i + 1;
              return (
                <li
                  key={title}
                  ref={(node) => {
                    stepRefs.current[i] = node;
                  }}
                  data-step={step}
                  className="m-step"
                  data-active={active === step ? 'true' : undefined}
                >
                  <div className="m-step-frame-model">
                    <ComplexModel units={units} activeStep={step} />
                  </div>
                  <div className="m-step-text">
                    <span className="m-numeral m-step-numeral" aria-hidden="true">
                      {step}
                    </span>
                    <h3 className="m-h3">
                      <span className="m-sr">Paso {step}: </span>
                      {title}
                    </h3>
                    <p className="m-body">{copy}</p>
                  </div>
                  <div className="m-step-callout">
                    <Callout />
                  </div>
                </li>
              );
            })}
          </ol>

          <div className="m-stage">
            <div className="m-stage-sticky m-studs" data-step={active}>
              <ComplexModel units={units} activeStep={active} showLabels>
                <StepMark step={active} className="m-stage-mark" />
                <ActiveCallout key={active} className="m-stage-callout m-glass m-callout-in" />
              </ComplexModel>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
