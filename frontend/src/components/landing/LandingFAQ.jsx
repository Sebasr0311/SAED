import { useId, useState } from 'react';
import { ChevronDown } from 'lucide-react';

const FAQS = [
  {
    q: '¿Qué es SAED?',
    a: 'Un sistema en la nube para administrar conjuntos residenciales de propiedad horizontal. Conecta administración, portería, residentes y finanzas sobre un mismo registro, para que nadie tenga que copiar datos de una herramienta a otra.',
  },
  {
    q: '¿Cómo se protegen los datos de cada conjunto?',
    a: 'Los datos de cada conjunto quedan aislados en la base de datos. Un conjunto no puede ver ni consultar los registros de otro, aunque ambos usen SAED.',
  },
  {
    q: '¿Cómo funcionan las invitaciones QR?',
    a: 'El residente crea la invitación desde su portal con los datos del visitante. En portería, el código se valida con el escáner de SAED desde el navegador y el ingreso queda registrado.',
  },
  {
    q: '¿Cómo pagan los residentes sus cuotas?',
    a: 'En línea, desde su portal, a través de Wompi con PSE o tarjeta.',
  },
  {
    q: '¿Sirve para la Ley 675 de 2001?',
    a: 'SAED está pensado para propiedad horizontal en Colombia: registra coeficientes de copropiedad y acompaña asambleas y actas, además de documentos y reglamentos del conjunto.',
  },
  {
    q: '¿Funciona para administradoras con varios conjuntos?',
    a: 'Sí. Las administradoras tienen una consola de organización para trabajar con varias copropiedades, y cada conjunto mantiene sus datos aislados.',
  },
  {
    q: '¿Necesito comprar equipos o instalar algo?',
    a: 'No. SAED funciona en el navegador de un computador, tableta o celular con conexión a internet.',
  },
  {
    q: '¿Cómo reciben su acceso los usuarios?',
    a: 'Cada persona recibe en su correo un enlace para activar su cuenta y crear su propia contraseña. SAED no envía contraseñas por correo.',
  },
  {
    q: '¿Dónde veo los precios?',
    a: 'En la página de planes, con los planes y precios vigentes según el número de unidades de tu conjunto.',
  },
];

export default function LandingFAQ({
  items = FAQS,
  id = 'preguntas',
  title = 'Preguntas frecuentes',
  intro = 'Respuestas cortas sobre cómo funciona SAED hoy.',
}) {
  const [openIdx, setOpenIdx] = useState(0);
  const baseId = useId();

  return (
    <section id={id} className="m-section" aria-labelledby={`${baseId}-title`}>
      <div className="m-container m-faq-layout">
        <div className="m-section-head">
          <h2 id={`${baseId}-title`} className="m-h2">
            {title}
          </h2>
          <p className="m-body">{intro}</p>
        </div>

        <div className="m-faq">
          {items.map((faq, idx) => {
            const isOpen = openIdx === idx;
            const buttonId = `${baseId}-q-${idx}`;
            const panelId = `${baseId}-a-${idx}`;
            return (
              <div key={faq.q} className="m-faq-item" data-open={isOpen ? 'true' : undefined}>
                <h3 className="m-faq-q">
                  <button
                    id={buttonId}
                    type="button"
                    aria-expanded={isOpen}
                    aria-controls={panelId}
                    onClick={() => setOpenIdx(isOpen ? -1 : idx)}
                  >
                    <span>{faq.q}</span>
                    <ChevronDown aria-hidden="true" />
                  </button>
                </h3>
                <div
                  id={panelId}
                  role="region"
                  aria-labelledby={buttonId}
                  hidden={!isOpen}
                  className="m-faq-a"
                >
                  <p>{faq.a}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
