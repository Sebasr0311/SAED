# SAED 2.0 — PRESENTACIÓN MAESTRA (DIAPOSITIVAS DEMO DAY)
## ESTRUCTURA DEFINITIVA DE DIAPOSITIVAS PARA EVALUACIÓN ACADÉMICA

---

### METADATOS DE LA PRESENTACIÓN
- **Proyecto:** SAED 2.0 — Sistema de Administración de Edificios
- **Subtítulo:** Plataforma SaaS para la gestión integral de conjuntos residenciales
- **Tipo de Evaluación:** Sustentación de Proyecto de Grado / Evaluación de Jurado Académico
- **Duración Estimada:** 10 a 12 minutos (Diapositivas + Demostración en vivo)
- **Formato:** 12 diapositivas conceptuales de alto impacto visual y técnico

---

## DIAPOSITIVA 1: PORTADA

```
┌────────────────────────────────────────────────────────────────────────┐
│                                                                        │
│                               SAED 2.0                                 │
│                 Sistema de Administración de Edificios                 │
│                                                                        │
│    Plataforma SaaS para la gestión integral de conjuntos residenciales│
│                                                                        │
│  Presentado por: Estudiantes de Ingeniería de Sistemas                │
│  Contexto: Proyecto Integrador de Arquitectura y Desarrollo de Software│
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

- **Título:** SAED 2.0 — Sistema de Administración de Edificios
- **Subtítulo:** Plataforma SaaS para la gestión integral de conjuntos residenciales.
- **Contexto Académico:** Proyecto de software enfocado en la aplicación de arquitectura limpia, seguridad por capas, aislamiento de datos y desarrollo de una solución PropTech multi-inquilino.
- **Puntos Clave del Orador:**
  - Presentación del equipo y propósito del proyecto.
  - Motivación: modernizar y formalizar digitalmente la propiedad horizontal.

---

## DIAPOSITIVA 2: EL PROBLEMA

```
┌────────────────────────────────────────────────────────────────────────┐
│                              EL PROBLEMA                               │
│                   Realidad de la Propiedad Horizontal                  │
│                                                                        │
│  • Procesos Dispersos: Contabilidad, citofonía y bitácoras aisladas    │
│  • Comunicación Fragmentada: Grupos informales y avisos en cartelera   │
│  • Control Manual de Visitantes: Planillas físicas vulnerables         │
│  • Dificultad en Cartera: Retrasos en conciliación y cálculo de mora   │
│  • Gestión de Paquetes: Pérdidas por falta de custodia documentada     │
│  • Control de Parqueaderos: Sobrecupo y asignaciones arbitrarias       │
│  • Falta de Trazabilidad: Sin auditoría de quién autorizó cada evento  │
└────────────────────────────────────────────────────────────────────────┘
```

- **Idea Central:** En la actualidad, la mayoría de los edificios operan con herramientas desconectadas o en papel.
- **Impacto:**
  - Vulnerabilidad en seguridad física en las porterías.
  - Conflictos entre residentes y administradores por pagos no registrados a tiempo.
  - Extravío frecuente de encomiendas de comercio electrónico.

---

## DIAPOSITIVA 3: LA SOLUCIÓN

```
┌────────────────────────────────────────────────────────────────────────┐
│                              LA SOLUCIÓN                               │
│                     SAED 2.0: Ecosistema Unificado                     │
│                                                                        │
│        Administración                                                  │
│              +                                                         │
│          Residentes                                                    │
│              +                                                         │
│           Portería                                                     │
│              +              ══════►             SAED 2.0               │
│           Finanzas                                                     │
│              +                                                         │
│         Operaciones                                                    │
│                                                                        │
│    "Una única plataforma en la nube que conecta a todos los actores"   │
└────────────────────────────────────────────────────────────────────────┘
```

- **Concepto:** SAED 2.0 unifica los 5 pilares operativos en un solo sistema web reactivo y seguro.
- **Propuesta de Valor:**
  - La administración gobierna las finanzas y las unidades.
  - Los residentes autogestionan sus visitas y paquetes.
  - El portero cuenta con herramientas de acreditación rápida.

---

## DIAPOSITIVA 4: ARQUITECTURA DEL SISTEMA

```
┌────────────────────────────────────────────────────────────────────────┐
│                         ARQUITECTURA DE TRES CAPAS                     │
│                                                                        │
│                                SAED 2.0                                │
│                                   │                                    │
│                    +--------------+--------------+                     │
│                    │              │              │                     │
│                 Frontend       Backend       Database                  │
│                React / Vite  Spring Boot    Oracle ATP                 │
│                    │              │              │                     │
│                    +--------------+--------------+                     │
│                                   │                                    │
│                            Security Layer                              │
│                                   │                                    │
│                          RBAC + Multi-Tenant                           │
│                                   │                                    │
│                               RLS / VPD                                │
└────────────────────────────────────────────────────────────────────────┘
```

- **Frontend:** Single Page Application construida en React 18 con Vite y Tailwind CSS. Rápida, responsiva y orientada a componentes.
- **Backend:** API REST construida con Spring Boot 3 y Java, encargada de la lógica de negocio, validaciones y contratos seguros.
- **Base de Datos:** Oracle Autonomous Database (ATP) en la nube, con procedimientos PL/SQL y políticas de seguridad avanzadas.
- **Capa de Seguridad:** Filtro JWT en servidor, sincronización de contexto de sesión y aislamiento a nivel de motor de datos.

---

## DIAPOSITIVA 5: MODELO DE ROLES (RBAC)

```
┌────────────────────────────────────────────────────────────────────────┐
│                             JERARQUÍA RBAC                             │
│                                                                        │
│       ROL                   ALCANCE          RESPONSABILIDAD           │
│  ───────────────────────────────────────────────────────────────────   │
│   SUPERADMIN                GLOBAL           Negocio SaaS global       │
│       ↓                                                                │
│   ADMIN_ORGANIZACION        ORGANIZACIÓN     Cartera de copropiedades  │
│       ↓                                                                │
│   ADMIN_PROPIEDAD           PROPIEDAD        Operación del conjunto    │
│       ↓                                                                │
│   PORTERO                   PROPIEDAD / OP.  Garita y accesos físicos  │
│       ↓                                                                │
│   RESIDENTE                 UNIDAD           Autoservicio de su hogar  │
└────────────────────────────────────────────────────────────────────────┘
```

- **Principio de Mínimo Privilegio:** Cada usuario accede estrictamente a lo que requiere su función.
- **Contratos Claros:** El residente no puede acceder a reportes contables del edificio; el portero no puede alterar cuotas; el administrador no puede ver datos de otros conjuntos.

---

## DIAPOSITIVA 6: SEGURIDAD MULTI-TENANT Y RLS

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SEGURIDAD Y AISLAMIENTO                         │
│                                                                        │
│  • RBAC (Role-Based Access Control): Limita las acciones disponibles.  │
│  • X-Assignment-Id: Header obligatorio que asocia al usuario con su rol│
│    y propiedad activa en cada petición REST.                           │
│  • SAED_CTX: Contexto de sesión PL/SQL inyectado en el pool de conexión│
│  • Oracle RLS / VPD (Virtual Private Database):                        │
│    Aplica políticas de seguridad directamente en las tablas relacionales│
│                                                                        │
│  ═════════════════════════════════════════════════════════════════════ │
│  "Un usuario no solamente está limitado por lo que puede hacer,        │
│   sino también por los datos que puede consultar."                     │
│  ═════════════════════════════════════════════════════════════════════ │
└────────────────────────────────────────────────────────────────────────┘
```

- **Por qué es relevante:** Si un usuario intenta modificar manualmente el identificador de una propiedad o unidad en la URL o payload, la base de datos devuelve 0 filas porque el predicado de seguridad reside en el kernel de Oracle.

---

## DIAPOSITIVA 7: OPERACIÓN RESIDENCIAL INTEGRADA

```
┌────────────────────────────────────────────────────────────────────────┐
│                   FLUJO DE CONTROL DE ACCESO INTEGRADO                 │
│                                                                        │
│   [Residente]                                                          │
│        ↓ Genera invitación digital con QR temporal                     │
│   [Visitante]                                                          │
│        ↓ Llega a la garita con su código                               │
│   [Portería]                                                           │
│        ↓ Valida QR en sub-segundo (Procedimiento PL/SQL)               │
│        ↓ Registra ingreso y modalidad de transporte                    │
│   [Vehículo]                                                           │
│        ↓ Asignación automática de bahía de visitantes (ej: V-01)       │
│   [Salida]                                                             │
│        ↓ Registro de egreso en 1 clic y liberación de cupo             │
└────────────────────────────────────────────────────────────────────────┘
```

- **Transición a la Demostración:** Este flujo une en tiempo real la experiencia del habitante con la labor del oficial de seguridad.

---

## DIAPOSITIVA 8: DEMO EN VIVO

```
┌────────────────────────────────────────────────────────────────────────┐
│                              DEMO EN VIVO                              │
│                                                                        │
│                     "Del residente a la portería"                      │
│                                                                        │
│  1. Visión del Administrador (Dashboard y Cartera)                     │
│  2. Autogestión del Residente (Consulta y emisión de QR)               │
│  3. Operación en Garita (Escáner, acreditación y vehículo)             │
│  4. Control de Parqueaderos (Ocupación y egreso)                       │
│  5. Cadena de Custodia (Paquetería y entrega mediante PIN)             │
│                                                                        │
│  Duración demostración: ≤ 04:00 minutos                                │
└────────────────────────────────────────────────────────────────────────┘
```

*(En este punto, el expositor cambia a la pantalla del navegador para realizar la demostración funcional).*

---

## DIAPOSITIVA 9: FINANZAS Y CADENA DE CUSTODIA

```
┌────────────────────────────────────────────────────────────────────────┐
│                         FINANZAS Y PAQUETERÍA                          │
│                                                                        │
│   GESTIÓN DE CARTERA                  PAQUETERÍA SEGURA                │
│   ──────────────────                  ─────────────────                │
│   • Obligaciones mensuales            • Recepción con transportadora   │
│   • Cálculo de mora                   • Generación de PIN de retiro    │
│   • Pagos conciliados (Wompi)         • Notificación al residente      │
│   • Estados: Al día vs Mora           • Entrega con validación de PIN  │
│                                                                        │
│   Trazabilidad financiera clara       Cero extravíos de encomiendas    │
└────────────────────────────────────────────────────────────────────────┘
```

- **Resumen Post-Demo:** Destacar que los módulos no son islas; el estado financiero y la logística de paquetes quedan registrados en la base de datos de manera atómica.

---

## DIAPOSITIVA 10: STACK TECNOLÓGICO REAL

```
┌────────────────────────────────────────────────────────────────────────┐
│                            STACK TECNOLÓGICO                           │
│                                                                        │
│   FRONTEND              BACKEND               BASE DE DATOS            │
│   • React 18            • Spring Boot 3.2     • Oracle Autonomous DB   │
│   • Vite 5              • Java LTS            • PL/SQL Procedures      │
│   • Tailwind CSS        • Spring Security     • Virtual Private DB     │
│   • Lucide Icons        • JDBC Template       • HikariCP Pool          │
│                                                                        │
│   INTEGRACIONES EXTERNAS                      INFRAESTRUCTURA CLOUD    │
│   • Wompi (Pasarela de pagos Colombia)        • Vercel (Frontend)      │
│   • Brevo (Notificaciones transaccionales)    • Render (Backend API)   │
│                                               • OCI (Oracle Database)  │
└────────────────────────────────────────────────────────────────────────┘
```

- **Enfoque de Ingeniería:** Sin dependencias ficticias; herramientas de estándar industrial probadas en producción.

---

## DIAPOSITIVA 11: RESULTADOS CERTIFICADOS

```
┌────────────────────────────────────────────────────────────────────────┐
│                          RESULTADOS OBTENIDOS                          │
│                                                                        │
│  ✔ 5 Roles implementados y validados conceptualmente.                  │
│  ✔ Arquitectura Multi-Tenant con aislamiento a nivel de base de datos. │
│  ✔ Control de acceso mediante QR dinámico y validación sub-segundo.    │
│  ✔ Trazabilidad completa de cartera y conciliación de pagos.           │
│  ✔ Gestión de paquetería con PIN de retiro para entrega segura.        │
│  ✔ Control de bahías de parqueadero en tiempo real.                    │
│  ✔ 61 Pruebas automatizadas (unitarias, integración y adversariales).  │
│  ✔ 0 Errores en linter y build de producción optimizado.               │
└────────────────────────────────────────────────────────────────────────┘
```

- **Rigor Técnico:** Los resultados se respaldan en pruebas automatizadas y en el freeze de código vigente.

---

## DIAPOSITIVA 12: CIERRE Y CONCLUSIONES

```
┌────────────────────────────────────────────────────────────────────────┐
│                               CONCLUSIÓN                               │
│                                                                        │
│  "SAED 2.0 integra la administración residencial, los residentes,      │
│   la portería y las operaciones en una única plataforma SaaS,          │
│   manteniendo separación por roles y aislamiento de datos              │
│   entre los diferentes niveles de la organización."                    │
│                                                                        │
│                                SAED 2.0                                │
│               "Todo tu conjunto residencial, en un solo lugar."        │
│                                                                        │
│                      ¿Preguntas del jurado?                            │
└────────────────────────────────────────────────────────────────────────┘
```

- **Cierre:** Agradecimiento formal al jurado y apertura a la sesión de preguntas técnicas.
