import { useState, useRef, useMemo } from 'react';
import {
  ChevronDown,
  HelpCircle,
  Search,
  Sparkles,
  ShieldCheck,
  CreditCard,
  QrCode,
  Package,
  Scale,
  MessageCircle,
} from 'lucide-react';
import { animate } from 'animejs';

const CATEGORIES = [
  { id: 'all', label: 'Todas' },
  { id: 'seguridad', label: 'Seguridad & RLS', icon: ShieldCheck },
  { id: 'acceso', label: 'Control de Acceso QR', icon: QrCode },
  { id: 'paqueteria', label: 'Paquetería con PIN', icon: Package },
  { id: 'finanzas', label: 'Finanzas & Wompi', icon: CreditCard },
  { id: 'legal', label: 'Marco Ley 675', icon: Scale },
];

const FAQS = [
  {
    q: '¿Qué es SAED 2.0 y en qué se diferencia de un software tradicional?',
    category: 'seguridad',
    a: 'SAED 2.0 es una plataforma SaaS PropTech de grado empresarial diseñada para conjuntos residenciales y administradoras en Colombia. A diferencia de softwares tradicionales que solo ofrecen formularios web básicos, SAED aplica políticas de seguridad en base de datos (Oracle Virtual Private Database / RLS), garantizando que ninguna copropiedad pueda ver ni acceder a los registros de otra.',
  },
  {
    q: '¿Cómo funciona el control de visitantes con código QR dinámico?',
    category: 'acceso',
    a: 'El habitante emite la invitación desde su portal personal estableciendo la vigencia en horas y los datos del invitado. El visitante presenta el código QR en garita, donde el guardia lo valida en pantalla con un lector óptico o cámara. Al confirmarse, el sistema asigna la bahía vehicular y asienta el evento de forma inmutable en la bitácora.',
  },
  {
    q: '¿Cómo evita el sistema las entregas equivocadas de correspondencia?',
    category: 'paqueteria',
    a: 'Al registrar una encomienda en garita, el sistema genera automáticamente un PIN criptográfico de 6 dígitos que solo el residente destinatario puede consultar en su portal privado. El operador no puede marcar el paquete como entregado hasta que el residente dicta o introduce este PIN en persona en la garita.',
  },
  {
    q: '¿Cómo se procesan los pagos y cómo se concilia la cartera?',
    category: 'finanzas',
    a: 'SAED cuenta con integración oficial con la pasarela Wompi de Bancolombia. Los residentes pueden liquidar sus expensas ordinarias o extraordinarias mediante PSE, Bancolombia, Nequi y tarjetas. Al autorizarse el pago, la base de datos concilia el saldo en milisegundos y emite el certificado de paz y salvo en PDF con firma digital.',
  },
  {
    q: '¿Cumple la plataforma con el régimen de Propiedad Horizontal (Ley 675 de 2001)?',
    category: 'legal',
    a: 'Absolutamente. La arquitectura de datos de SAED está parametrizada bajo los principios de la Ley 675 de 2001: registro oficial de coeficientes de copropiedad, quórum para asambleas ordinarias y extraordinarias, cálculo de mayorías calificadas y rendición de cuentas inalterable.',
  },
  {
    q: '¿Funciona para empresas que administran múltiples conjuntos residenciales?',
    category: 'seguridad',
    a: 'Sí. Mediante el perfil ADMIN_ORGANIZACION, una empresa administradora puede supervisar múltiples edificios desde una sola consola gerencial, comparando cartera, presupuestos y personal operativo, mientras cada conjunto mantiene sus datos 100% aislados a nivel de motor relacional.',
  },
  {
    q: '¿Se requiere comprar hardware propietario o instalar servidores locales?',
    category: 'acceso',
    a: 'No. SAED 2.0 opera 100% en la nube a través de navegadores web modernos en computadores, tablets o smartphones. El personal de portería solo necesita una tablet o PC estándar con conexión a internet para operar la garita.',
  },
  {
    q: '¿Cuánto tiempo toma la puesta en marcha de un conjunto?',
    category: 'legal',
    a: 'La parametrización inicial de torres, unidades residenciales y censo poblacional se realiza mediante importación asistida en menos de 48 horas. El personal de portería y los residentes pueden acceder de inmediato sin necesidad de descargas desde tiendas de aplicaciones.',
  },
];

export default function LandingFAQ() {
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [openIdx, setOpenIdx] = useState(0); // First one open by default

  const filteredFaqs = useMemo(() => {
    return FAQS.filter((faq) => {
      const matchesCat = selectedCategory === 'all' || faq.category === selectedCategory;
      const matchesSearch =
        faq.q.toLowerCase().includes(searchQuery.toLowerCase()) ||
        faq.a.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCat && matchesSearch;
    });
  }, [selectedCategory, searchQuery]);

  const toggleFaq = (idx) => {
    setOpenIdx(openIdx === idx ? null : idx);
  };

  return (
    <section
      id="faq"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 right-1/4 w-[600px] h-[340px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="text-center mb-12 sm:mb-16 space-y-4">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            RESOLUCIÓN DE DUDAS Y PREGUNTAS FRECUENTES
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Todo lo que necesitas saber sobre SAED 2.0
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Respuestas directas sobre la operación diaria, el aislamiento de base de datos y la puesta en marcha de la plataforma.
          </p>

          {/* Search Input Filter */}
          <div className="pt-4 max-w-md mx-auto relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              placeholder="Buscar por tema (ej. QR, Wompi, Ley 675, PIN)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-3 rounded-2xl bg-slate-900/90 border border-slate-800 focus:border-sky-400 text-xs sm:text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-sky-400/20 transition-all shadow-inner"
            />
          </div>

          {/* Category Filter Pills */}
          <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
            {CATEGORIES.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => setSelectedCategory(cat.id)}
                className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
                  selectedCategory === cat.id
                    ? 'bg-sky-500 text-slate-950 shadow-md shadow-sky-500/30'
                    : 'bg-slate-900/90 text-slate-400 border border-slate-800 hover:text-white hover:border-slate-700'
                }`}
              >
                {cat.label}
              </button>
            ))}
          </div>
        </div>

        {/* Accordion List */}
        <div className="space-y-3.5">
          {filteredFaqs.length > 0 ? (
            filteredFaqs.map((faq, idx) => {
              const isOpen = openIdx === idx;
              return (
                <div
                  key={idx}
                  className={`border rounded-2xl overflow-hidden transition-all duration-200 shadow-md ${
                    isOpen
                      ? 'bg-slate-900/90 border-sky-500/40 shadow-sky-950/20'
                      : 'bg-slate-900/60 border-slate-800/80 hover:border-slate-700 hover:bg-slate-900/80'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => toggleFaq(idx)}
                    className="w-full text-left p-5 sm:p-6 flex items-center justify-between gap-4 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[56px]"
                    aria-expanded={isOpen}
                  >
                    <span className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans'] pr-2">
                      {faq.q}
                    </span>
                    <div
                      className={`w-8 h-8 rounded-xl border flex items-center justify-center shrink-0 transition-transform duration-200 ${
                        isOpen
                          ? 'rotate-180 text-sky-300 bg-sky-500/20 border-sky-400/40'
                          : 'text-slate-400 bg-slate-800 border-slate-700'
                      }`}
                    >
                      <ChevronDown className="w-4 h-4" />
                    </div>
                  </button>

                  {isOpen && (
                    <div className="px-5 pb-6 sm:px-6 sm:pb-7 text-slate-300 text-xs sm:text-sm leading-relaxed border-t border-slate-800/60 pt-4 animate-in fade-in duration-200">
                      {faq.a}
                    </div>
                  )}
                </div>
              );
            })
          ) : (
            <div className="p-8 rounded-2xl bg-slate-900/40 border border-slate-800 text-center space-y-2">
              <p className="text-sm font-bold text-white">No se encontraron preguntas con ese término.</p>
              <p className="text-xs text-slate-400">Intenta con otra búsqueda o selecciona la categoría &apos;Todas&apos;.</p>
            </div>
          )}
        </div>

      </div>
    </section>
  );
}
