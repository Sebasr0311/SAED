# UI-REDESIGN-02 — SAED 2.0 LANDING RECONSTRUCCIÓN EDITORIAL
## Reporte de Certificación Arquitectónica y Editorial (Vimeo Reference Inspired)

**Fecha:** Septiembre 2026  
**Proyecto:** SAED 2.0 (Sistema Automatizado para Edificios Digitales)  
**Alcance:** Exclusivamente visual y editorial en la Landing Page pública (`/`)  
**Estado:** 🟢 CERTIFICADO — DEMO DAY READY  

---

### 1. Resumen Ejecutivo

Bajo el requerimiento **UI-REDESIGN-02**, se ejecutó la reconstrucción integral de la Landing Page pública de **SAED 2.0**, adoptando la arquitectura de información, el ritmo editorial y el tratamiento visual de producto inspirado conceptualmente en plataformas de referencia global como **Vimeo**.

La experiencia abandona el paradigma clásico de "mosaico de tarjetas" y adopta una narrativa editorial fluida, con tipografía monumental, whitespace generoso, grandes lienzos de producto interactivos y demostraciones operativas vivas (garita de portería, custodia de paquetería con PIN criptográfico y asignación de bahías vehiculares).

---

### 2. Principios Editoriales y Arquitectónicos Aplicados

1. **Ritmo y Composición Editorial (Inspiración Vimeo):**
   - Jerarquía clara: De la promesa monumental en el Hero al Gran Lienzo de Producto (*Showcase*), pasando por la fragmentación del problema, profundizaciones operativas modulares y cierre institucional de alta confianza.
   - Eliminación de clichés visuales: Cero fotos de stock genéricas de rascacielos o personas sonrientes con tablets. Todo el peso visual recae en la interfaz real, datos operativos y tipografía de precisión.
2. **Whitespace y Tipografía Monumental:**
   - Tipografía display clamp (`text-5xl sm:text-7xl lg:text-[5.5rem] font-black tracking-tight leading-[1.05]`) con transiciones suaves de color y contraste sobre fondos oscuros profundos (`#070D18`, `#0A1628`, `#0F172A`).
   - Bloques con espaciado vertical generoso (`py-28 sm:py-36 lg:py-44`) que permiten que cada sección funcione como un capítulo independiente dentro del recorrido del usuario.
3. **Product Showcase y Deep Dives Interactivos:**
   - **Grand Browser Mockup:** Marco de navegador web auténtico con 3 pestañas dinámicas conmutables en tiempo real (`Dashboard & Cartera`, `Portería & QR`, `Paquetes & Bahías`).
   - **Módulos de Inmersión Operativa:** Tres secciones profundas con datos representativos del software:
     - *Acceso & QR:* HUD de garita con verificación instantánea de documento, torre y vehículo.
     - *Paquetería:* Trazabilidad de custodia y entrega segura mediante PIN criptográfico de 6 dígitos.
     - *Parqueaderos:* Asignación de bahías en tiempo real con filtrado interactivo en vivo (`Todos`, `Libres`, `Ocupadas`).
4. **Gobernanza y Transparencia Corporativa:**
   - Matriz de 5 roles jerárquicos (`SUPERADMIN`, `ADMIN_ORGANIZACION`, `ADMIN_PROPIEDAD`, `PORTERO`, `RESIDENTE`).
   - Visualización del pipeline de seguridad y aislamiento por base de datos: `Usuario → JWT → Contexto → RLS/VPD → Tenant → Datos`.
   - Cumplimiento normativo explícito bajo la Ley 675 de 2001 y pasarela de pagos Wompi (Bancolombia).

---

### 3. Matriz de Componentes del Sistema

| Componente | Archivo | Responsabilidad y Nivel de Interacción |
|---|---|---|
| **LandingNavbar** | `src/components/landing/LandingNavbar.jsx` | Barra fija translúcida con `backdrop-blur-xl`, píldora de navegación, enlaces ancla a secciones clave, botón de acceso directo a `/login` y drawer móvil optimizado con targets $\ge 44\text{px}$. |
| **LandingHero** | `src/components/landing/LandingHero.jsx` | Headline monumental *"La plataforma que redefine la vida en copropiedad"*, propuesta de valor PropTech Enterprise, CTAs directos ("Conocer SAED", "Ver planes") y acceso a login. |
| **LandingProductShowcase** | `src/components/landing/LandingProductShowcase.jsx` | Ventana de navegador a escala real con 3 pestañas funcionales interactivas para explorar la interfaz de SAED. |
| **LandingTrust** | `src/components/landing/LandingTrust.jsx` | Estructura de gobernanza con los 5 roles certificados del sistema y métricas de seguridad operativa. |
| **LandingProblemSolution** | `src/components/landing/LandingProblemSolution.jsx` | Relato del dolor operativo de la fragmentación en conjuntos residenciales y la Ecuación Unificada: $\text{Admin} + \text{Residentes} + \text{Portería} + \text{Finanzas} = \text{SAED 2.0}$. |
| **LandingAccess** | `src/components/landing/LandingAccess.jsx` | Deep Dive 01: Ciclo de vida de autorización QR en 5 pasos secuenciales y simulación de pantalla de garita de portería. |
| **LandingPackages** | `src/components/landing/LandingPackages.jsx` | Deep Dive 02: Gestión de paquetería, registro de mensajería y entrega custodiada mediante PIN de seguridad. |
| **LandingParking** | `src/components/landing/LandingParking.jsx` | Deep Dive 03: Bahías de parqueadero de visitantes con filtro interactivo de disponibilidad en tiempo real. |
| **LandingFinance** | `src/components/landing/LandingFinance.jsx` | Transparencia financiera: cartera con balance demo ($250.000 COP), mora por edades y pasarela Wompi integrada. |
| **LandingSecurity** | `src/components/landing/LandingSecurity.jsx` | Arquitectura técnica de defensa en profundidad: RLS/VPD en Oracle, JWT sin estado, auditoría inmutable y Ley 675. |
| **LandingPricing** | `src/components/landing/LandingPricing.jsx` | Estructura de 3 planes (Básico, Profesional, Empresarial) con matriz comparativa desplegable y transparente. |
| **LandingFAQ** | `src/components/landing/LandingFAQ.jsx` | 8 preguntas frecuentes con acordeón interactivo accesible (atributos ARIA completos y animación de apertura). |
| **LandingCTA** | `src/components/landing/LandingCTA.jsx` | Bloque de cierre monumental con tipografía destacada: *"Una propiedad. Una plataforma. SAED."* y botones de acción. |
| **LandingFooter** | `src/components/landing/LandingFooter.jsx` | Pie de página institucional con navegación corporativa, enlaces de gobernanza y créditos legales. |
| **LandingPage** | `src/pages/LandingPage.jsx` | Orquestador raíz que ensambla secuencialmente los 14 bloques, configurando título de ventana y metadatos SEO. |

---

### 4. Certificación Técnica y Pruebas Automatizadas

1. **Linter (ESLint):**
   - Comando: `npx eslint src/pages/LandingPage.jsx src/components/landing/`
   - Resultado: **0 errores, 0 warnings (100% CLEAN)**.
2. **Build de Producción (Vite):**
   - Comando: `npm run build`
   - Resultado: **Exit code 0 en 13.78s**. Cero errores de sintaxis, cero importaciones rotas.
3. **Pruebas Automatizadas End-to-End (Playwright):**
   - Script de prueba: `scratch/test_landing_vimeo.js` ejecutado sobre la instancia activa de Vite.
   - Logs de consola y excepciones no controladas en el navegador: **0 errores**.
   - **Viewports Certificados y Evidencia Fotográfica:**
     - `1440x900` (Desktop Grande): `landing_desktop_1440.png` — Composición monumental balanceada.
     - `1280x800` (Desktop Estándar): `landing_desktop_1280.png` — Proporciones y márgenes consistentes.
     - `1024x768` (Tablet Horizontal / Desktop Compacto): `landing_desktop_1024.png` — Sin desbordamientos de texto.
     - `768x1024` (Tablet Vertical): `landing_tablet_768.png` — Reorganización de columnas a flujo vertical fluido.
     - `390x844` (Móvil Estándar): `landing_mobile_390.png` — Legibilidad óptima y navegación colapsada.
     - `360x740` (Móvil Compacto): `landing_mobile_360.png` — Ajuste estricto sin scroll horizontal.
4. **Interactividad Verificada en el Cliente:**
   - Conmutación dinámica de pestaña a *Portería & QR* en Showcase: `landing_showcase_tab_porteria.png`.
   - Conmutación dinámica de pestaña a *Operación & Parqueaderos* en Showcase: `landing_showcase_tab_operacion.png`.
   - Filtrado en vivo de bahías de parqueadero (`Todos` $\leftrightarrow$ `Libres` $\leftrightarrow$ `Ocupadas`).
   - Despliegue y contracción de matriz comparativa en sección de planes.
   - Expansión y colapso de acordeón en sección de preguntas frecuentes.

---

### 5. Respeto Incondicional de Restricciones del Proyecto

- **Backend Intacto:** Cero modificaciones en Spring Boot 3, Java, controladores, servicios, DTOs o repositorios.
- **Base de Datos Intacta:** Cero cambios en Oracle ATP / XE, esquemas, tablas, triggers, RLS/VPD o políticas de seguridad.
- **Autenticación y Login Congelados:** `LoginPage.jsx`, JWT, rutas protegidas y `AppShell.jsx` permanecen 100% inalterados. Todos los CTAs de la landing apuntan correctamente a la ruta unificada `/login`.
- **Cero Código Externo o Scrapeado:** Todo el código implementado es original, construido con React y TailwindCSS respetando los tokens de diseño de SAED 2.0.
- **Cero Git Commit / Push:** Se preserva el working tree para revisión y aprobación directa del equipo.
