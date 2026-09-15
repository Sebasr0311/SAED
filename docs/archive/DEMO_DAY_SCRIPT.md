# SAED 2.0 — GUION OFICIAL DEMO DAY (PRESENTACIÓN ACADÉMICA)
## GUÍA MAESTRA DE PRESENTACIÓN EN VIVO (DURACIÓN MÁXIMA: ≤ 06:00 MINUTOS)
### Referencia de ejecución en seco previa (MVP-06): ~03:58 minutos

---

### METADATOS Y LINEAMIENTOS GENERALES
- **Proyecto:** SAED 2.0 — Sistema Autónomo de Edificios Digitales
- **Audiencia:** Jurado Académico, Evaluadores de Arquitectura e Inversionistas
- **Objetivo Central:** Demostrar el funcionamiento armónico, robustez técnica y aislamiento multi-tenant de SAED 2.0 en un recorrido secuencial sin fricciones.
- **Regla Fundamental:** *No narrar clics superficiales; explicar conceptos de ingeniería, valor de negocio PropTech y seguridad de datos.*
- **Privacidad de Credenciales:** Todas las contraseñas reales se mantienen estrictamente locales y fuera de pantallas proyectadas.

---

## CRONOGRAMA DE EJECUCIÓN (00:00 — 06:00)

```
00:00        00:30        01:15        01:45        02:15        03:15        03:45        04:30        05:00        05:40   06:00
┌────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┐
│ ¿QUÉ ES    │ ADMIN_     │  CARTERA   │ RESIDENTE  │  PORTERÍA  │ PARQUEA-   │ PAQUETERÍA │ RESIDENTE  │ SEGURIDAD  │  CIERRE    │
│  SAED?     │ PROPIEDAD  │ FINANCIERA │ VISITA/QR  │ ACCESO/VEH │ DERO/SALIDA│ RECEP/PIN  │ NOTIFIC/PIN│ MULTITENANT│ ARQUITECT. │
└────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┘
```

---

### 00:00 — 00:30 | ¿QUÉ ES SAED?
**Pantalla:** Landing Page Pública (`/`)  
**Actor:** Presentador  

#### Mensaje Obligatorio
> *"SAED 2.0 es una plataforma SaaS para administrar conjuntos residenciales, conectando administración, residentes, portería y operaciones."*

#### Explicación Conceptual
> *"Frente al problema histórico de la propiedad horizontal —silos de datos, registros en papel, fraudes en garita y morosidad sin control— SAED 2.0 propone una arquitectura moderna en la nube respaldada por **Oracle Autonomous Database**, Spring Boot 3 y React 18, donde la seguridad y el aislamiento multi-inquilino están garantizados a nivel de motor de datos."*

---

### 00:30 — 01:15 | ADMIN_PROPIEDAD
**Pantalla:** Login (`/login`) ➔ Dashboard del Administrador (`/dashboard`)  
**Rol Activo:** `ADMIN_PROPIEDAD` (Usuario `admin` en *Torres del Parque*)  

#### Mensaje Obligatorio
> *"El administrador tiene una visión centralizada del conjunto."*

#### Qué Mostrar
- Panel de control ejecutivo con indicadores clave en tiempo real: Total de Unidades (4), Residentes Registrados, Estado de Cartera ($250.000 COP) y Nivel de Ocupación.
- Tabla consolidada de morosidad y alertas operacionales sin recargas de página.

#### Explicación Conceptual
> *"Al autenticarse el administrador, el backend resuelve la sesión y activa el contexto en la base de datos mediante `PKG_SAED_SESSION.SET_CONTEXT`. El administrador solo tiene visibilidad de su propia copropiedad: el aislamiento no depende de un simple filtro web, sino del kernel relacional."*

---

### 01:15 — 01:45 | CARTERA
**Pantalla:** Módulo de Cartera (`/cartera`)  
**Rol Activo:** `ADMIN_PROPIEDAD`  

#### Qué Mostrar (Datos Reales del Dataset Certificado)
- Cartera consolidada pendiente: **$250.000 COP**.
- **Apto 101 (Carlos Martínez):** Cartera pendiente de $250.000 COP (Cuota ordinaria de administración en curso).
- **Apto 102 (Ana Gómez):** Saldo $0 COP (Estado: AL DÍA, obligación conciliada mediante la integración con Wompi).
- Unidades 201 y 202 en balance equilibrado.

#### Explicación Conceptual
> *"La plataforma calcula deudas, intereses y estados de cuenta en tiempo real. Aquí vemos el contraste entre una unidad pendiente de cobro y otra que canceló digitalmente vía pasarela de pagos conciliada de forma automática."*

---

### 01:45 — 02:15 | RESIDENTE
**Pantalla:** Dashboard Residente (`/residente`) ➔ Mis Visitas (`/res-visita`)  
**Rol Activo:** `RESIDENTE` (Usuario `camartinez`, Apto 101)  

#### Qué Mostrar
- Portal de autogestión de la unidad 101: solo ve su estado de cuenta personal ($250.000 COP adeudados, sin acceso a unidades vecinas).
- Módulo de visitas: Invitación activa y generación de credencial efímera con código QR.
- Token de demostración programado: `SAED-DEMO-QR-2026-TOKEN`.

#### Explicación Conceptual
> *"El residente no depende de avisos informales por citófono: autogestiona la autorización de sus invitados mediante un modelo Zero-Trust. El sistema genera un código QR criptográfico, efímero y unívoco que viaja al visitante."*

---

### 02:15 — 03:15 | PORTERÍA
**Pantalla:** Garita de Portería (`/portero`) ➔ Escáner HUD Láser (`/porteria`)  
**Rol Activo:** `PORTERO` (Usuario `portero01`, Garita Principal)  

#### Qué Mostrar
- Interfaz táctica de garita diseñada para alta legibilidad del guardia.
- Validación del token de acceso (`SAED-DEMO-QR-2026-TOKEN`) por cámara o portapapeles.
- Acreditación inmediata: *Visitante Demo*, C.C. 1000000010, Autorizado por Apto 101.
- Registro vehicular: Selección modo **CARRO**, placa **DEM-123**.
- Asignación autónoma de parqueadero de visitantes: **Bahía V-01**.
- Clic en **Registrar Entrada**: Estado pasa a `EN_CURSO` y se dispara notificación al residente.

#### Explicación Conceptual
> *"El procedimiento almacenado en base de datos valida en milisegundos que el token sea vigente, que la unidad destino exista y que no haya sido reutilizado. Al registrar el ingreso del vehículo DEM-123, el sistema le adjudica automáticamente el cupo V-01."*

---

### 03:15 — 03:45 | PARQUEADERO
**Pantalla:** Módulo de Parqueaderos (`/parqueaderos`)  
**Rol Activo:** `PORTERO`  

#### Qué Mostrar
- Grilla interactiva de bahías: Bahía **V-01** en estado `OCUPADO` con la placa **DEM-123**.
- Monitoreo del tiempo de estadía del vehículo.
- Registro de egreso en 1 solo clic.
- Transición inmediata de la bahía **V-01** a estado `DISPONIBLE`.

#### Explicación Conceptual
> *"El inventario físico de parqueaderos se actualiza en tiempo real. Al marcharse el visitante, un clic sella la bitácora de egreso y libera el cupo para el siguiente vehículo sin necesidad de planillas de papel."*

---

### 03:45 — 04:30 | PAQUETERÍA
**Pantalla:** Recepción de Paquetería (`/portero/paquetes`)  
**Rol Activo:** `PORTERO`  

#### Qué Mostrar
- Recepción de encomienda para el **Apto 101**.
- Transportadora: **Servientrega**; Remitente/Detalle: **MercadoLibre / Paquete Mediano**.
- Confirmación de recepción: El sistema genera un **PIN de retiro** confidencial y unívoco.
- Notificación automática enviada a la unidad destinataria.
- El paquete entra a estado `EN_CUSTODIA`.

#### Explicación Conceptual
> *"En copropiedades modernas el volumen de paquetería genera extravíos constantes. SAED 2.0 digitaliza la cadena de custodia: el guardia recepciona el ítem y el sistema emite una credencial segura de retiro."*

---

### 04:30 — 05:00 | RESIDENTE (RETIRO & CUSTODIA)
**Pantalla:** Buzón Virtual del Residente (`/res-buzon`) ➔ Entrega en Portería  
**Rol Activo:** `RESIDENTE` (`camartinez`) / `PORTERO` (`portero01`)  

#### Qué Mostrar
- El residente abre su buzón virtual y visualiza la encomienda recibida con su **PIN de retiro** personal.
- En garita: el portero ingresa el PIN de retiro proporcionado por el residente.
- El paquete transiciona de forma irreversible a estado `ENTREGADO`.

#### Explicación Conceptual
> *"La entrega se completa únicamente cuando el residente suministra su PIN de retiro en recepción. Se erradica por completo la entrega errónea o el retiro por personas no autorizadas."*

---

### 05:00 — 05:40 | SEGURIDAD & AISLAMIENTO
**Pantalla:** Diagrama Conceptual / Consola de Contexto  
**Actor:** Presentador  

#### Explicación Arquitectónica Clave
> *"¿Qué diferencia a SAED 2.0 de otras soluciones de mercado? La seguridad arquitectónica:*
> 
> 1. *Control de Acceso Basado en Roles (**RBAC**) estricto en frontend y backend.*
> 2. *Multi-tenancy real con aislamiento mediante **Virtual Private Database (VPD / RLS)** en Oracle Autonomous Database.*
> 3. *Contexto de sesión inviolable mediante el encabezado `X-Assignment-Id` y la variable de sesión `SAED_CTX`.*
> 4. *Ningún usuario puede acceder a datos de otra propiedad o unidad alterando URLs o peticiones, porque las políticas de seguridad residen en el motor relacional."*

---

### 05:40 — 06:00 | CIERRE
**Pantalla:** Vista General de la Aplicación  
**Actor:** Presentador  

#### Mensaje Obligatorio de Cierre
> *"SAED no solamente centraliza la administración; aplica aislamiento por rol y por organización desde la arquitectura de datos."*

#### Frase de Despedida
> *"Con 61 pruebas de integración y seguridad en verde y un frontend completamente estable y congelado para producción, SAED 2.0 demuestra estar listo para transformar la gestión de comunidades residenciales. Muchas gracias."*
