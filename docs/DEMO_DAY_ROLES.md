# SAED 2.0 — MATRIZ DE ROLES Y CAPACIDADES DEMOSTRABLES
## MODELO DE ACCESO BASADO EN ROLES (RBAC) Y ALCANCE CONCEPTUAL

---

### INTRODUCCIÓN Y REGLAS DE MODELADO
El sistema de seguridad de SAED 2.0 se fundamenta en un modelo **RBAC jerárquico estricto** respaldado por aislamiento contextual a nivel de base de datos (`SAED_CTX`).

En esta guía se describen únicamente las capacidades **realmente implementadas y certificadas** en el sistema, organizadas en los 5 roles conceptuales de la plataforma.

---

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                            JERARQUÍA Y ALCANCE RBAC                              │
├─────────────────────────┬────────────────────────────┬───────────────────────────┤
│ ROL                     │ CONTEXTO DE AISLAMIENTO    │ PROPÓSITO OPERACIONAL     │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ SUPERADMIN              │ Global (Multi-Tenant)      │ Negocio SaaS Global       │
│ ADMIN_ORGANIZACION      │ Organización (Tenant)      │ Cartera de Copropiedades  │
│ ADMIN_PROPIEDAD         │ Propiedad (Copropiedad)    │ Operación Integral        │
│ PORTERO                 │ Portería / Garita          │ Operación de Garita       │
│ RESIDENTE               │ Unidad Habitacional        │ Autoservicio de Unidad    │
└─────────────────────────┴────────────────────────────┴───────────────────────────┘
```

---

## 1. SUPERADMIN (ALCANCE: NEGOCIO SAAS GLOBAL)
- **Usuario Demo:** `admin_global`
- **Ámbito:** Plataforma global completa (acceso a todas las organizaciones).

### Capacidades Implementadas
1. **Directorio de Organizaciones:** Monitoreo y alta de clientes corporativos (empresas administradoras de propiedad horizontal).
2. **Planes y Suscripciones:** Gestión de planes comerciales SaaS (límites de unidades, propiedades y tarifas).
3. **Administradores de Plataforma:** Alta y control de credenciales de nivel superadministrador.
4. **Pista de Auditoría Global:** Bitácora inmutable de eventos del sistema y accesos transversales.

---

## 2. ADMIN_ORGANIZACION (ALCANCE: CARTERA DE COPROPIEDADES)
- **Ámbito:** Empresa administradora específica (`ID_ORGANIZACION = 1`).

### Capacidades Implementadas
1. **Perfil Organizacional:** Información legal, tributaria (NIT) y comercial de la entidad administradora.
2. **Cartera de Propiedades:** Gestión de las distintas copropiedades o conjuntos residenciales asignados a su organización.
3. **Administradores Delegados:** Asignación de administradores locales a propiedades específicas.
4. **Suscripción Organizacional:** Estado de facturación y cuota de consumo de la empresa en la plataforma.

---

## 3. ADMIN_PROPIEDAD (ALCANCE: OPERACIÓN INTEGRAL DEL CONJUNTO)
- **Usuario Demo:** `admin` (Copropiedad: *Torres del Parque*)
- **Ámbito:** Una copropiedad específica (`ID_PROPIEDAD = 1`).

### Capacidades Implementadas
1. **Dashboard Ejecutivo:** Vista panorámica con métricas consolidadas (Total de unidades, residentes registrados, saldo de cartera en mora y porcentaje de ocupación).
2. **Gestión de Personas y Residentes:**
   - Alta, edición y asignación de habitantes a unidades habitacionales.
   - Validación documental según catálogo oficial (C.C., T.I., C.E., Pasaporte, NIT).
   - Control de menores de edad con registro obligatorio de tutor legal.
3. **Gestión de Cartera y Cobros:**
   - Visualización de cuotas ordinarias y extraordinarias por apartamento.
   - Cálculo automático de moras y recálculo con debounce.
   - Consulta de conciliaciones y pagos digitales procesados vía Wompi.
4. **Gestión de Unidades:** Inventario de apartamentos, áreas, bloques y coeficientes de copropiedad.
5. **Administración de Usuarios y Seguridad:** Control de cuentas de acceso y vinculación de roles con validación cruzada.
6. **Supervisión Operacional:** Monitoreo de bitácoras de visitas, encomiendas y parqueaderos de la copropiedad.

---

## 4. PORTERO (ALCANCE: OPERACIÓN DE GARITA)
- **Usuario Demo:** `portero01` (Garita Principal)
- **Ámbito:** Punto de control físico y acceso de la copropiedad.

### Capacidades Implementadas
1. **Escáner Táctico de Acceso:**
   - Lectura óptica de códigos QR mediante cámara o entrada rápida desde portapapeles / lector USB.
   - Validación sub-segundo con procedimiento almacenado `SP_VALIDAR_CONSUMIR_QR`.
2. **Control Vehicular de Visitantes:**
   - Tipificación de transporte: A pie, Carro, Moto, Bicicleta.
   - Validación de formato de placa colombiana mediante `valPlaca` (Carro: 3 letras + 3 dígitos; Moto: 3 letras + 2 dígitos + letra).
   - Asignación inteligente y autónoma de bahía disponible de visitantes.
3. **Control y Liberación de Parqueaderos:**
   - Grilla interactiva de bahías en tiempo real (Disponibles, Ocupadas, En Mantenimiento).
   - Registro de egreso vehicular en un solo clic con liberación inmediata de cupo.
4. **Cadena de Custodia de Paquetería:**
   - Recepción de encomiendas asignadas a una unidad con transportadora y detalle.
   - Generación automática de **PIN de retiro** unívoco.
   - Despacho de paquetes validando el PIN de retiro o mediante entrega supervisada.

---

## 5. RESIDENTE (ALCANCE: AUTOSERVICIO DE SU UNIDAD)
- **Usuarios Demo:** `camartinez` (Apto 101, saldo pendiente $250.000 COP), `anagomez` (Apto 102, al día con pago conciliado)
- **Ámbito:** Exclusivamente su unidad habitacional asignada (`ID_UNIDAD = 1` o `2`). Bloqueo absoluto de cualquier dato ajeno vía VPD/RLS.

### Capacidades Implementadas
1. **Portal Mi Hogar:** Visualización personalizada de estado de cuenta, obligaciones pendientes y comprobantes de pago.
2. **Agendamiento Seguro de Visitas:**
   - Registro de visitantes esperados con búsqueda y autocompletado si ya existen en historial.
   - Generación de credenciales QR temporales con vigencia parametrizable (5 a 60 minutos) y cupo de personas.
3. **Buzón Virtual de Paquetería:**
   - Consulta de encomiendas recibidas en garita.
   - Visualización confidencial del **PIN de retiro** para reclamar paquetes.
4. **PQRS y Solicitudes:** Radicación de peticiones, quejas y reclamos con seguimiento de radicado.

---

## 6. REGLAS DE AISLAMIENTO ADVERSARIO COMPROBADAS

1. **Un Residente (`camartinez`) NUNCA puede:**
   - Ver las cuotas, pagos o paquetes de otros apartamentos (aislamiento RLS a nivel de base de datos).
   - Acceder a rutas administrativas (`/dashboard`, `/residentes`, `/cartera`, `/usuarios`).
2. **Un Portero (`portero01`) NUNCA puede:**
   - Modificar cuotas de administración, ver balances financieros ni acceder a configuraciones de contratos.
3. **Un Administrador de Propiedad (`admin`) NUNCA puede:**
   - Consultar o alterar datos pertenecientes a otra copropiedad u organización SaaS distinta.
