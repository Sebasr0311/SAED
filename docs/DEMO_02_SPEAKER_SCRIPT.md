# SAED 2.0 — GUION DEL EXPOSITOR (SPEAKER SCRIPT)
## GUÍA VERBAL PALABRA POR PALABRA (CRONOMETRADO: 00:00 — 06:00 MINUTOS)

---

### INDICACIONES PARA EL ESTUDIANTE
- **Tono General:** Hablar con seguridad, naturalidad y buen ritmo. Habla como un estudiante de Ingeniería de Sistemas que domina su proyecto técnico, no como un vendedor comercial ni leyendo diapositivas.
- **Regla de Oro:** No te apures. Es mejor hablar pausado y claro durante 5 minutos que correr y atropellarse en 3.
- **Manejo de Pantalla:** Mientras cambias de pantalla o ventana, continúa hablando para que no haya silencios incómodos.

---

### 00:00 — 00:30 | INTRODUCCIÓN Y CONTEXTO
**Pantalla:** Diapositiva 1 / Landing Page de SAED 2.0  

> *"Buenos días profesor y miembros del jurado.*
> 
> *El proyecto que les vamos a presentar hoy es **SAED 2.0**, un Sistema de Administración de Edificios desarrollado como plataforma SaaS en la nube.*
> 
> *El problema que encontramos al investigar cómo funcionan los conjuntos residenciales es que la información suele estar completamente dispersa: la administración lleva planillas en Excel, la portería anota visitantes en un cuaderno de papel, los paquetes se pierden y los residentes se enteran tarde de sus cuentas de cobro.*
> 
> *SAED 2.0 busca centralizar toda esa operación en una sola plataforma web, conectando a la administración, a los vigilantes y a los residentes."*

---

### 00:30 — 01:00 | ARQUITECTURA Y ROLES
**Pantalla:** Diapositivas de Arquitectura y Roles  

> *"Para solucionar esto, diseñamos una arquitectura en tres capas: un frontend en React con Vite, un backend con Spring Boot y Java, y como base de datos utilizamos **Oracle Autonomous Database** en la nube de Oracle.*
> 
> *El sistema maneja cinco roles claramente separados: el Superadmin que gestiona el negocio SaaS; el Administrador de Organización que coordina empresas administradoras; el Administrador de Propiedad que opera el edificio; el Portero en garita; y el Residente.*
> 
> *Cada uno tiene un menú y permisos distintos. Pero lo más importante no es solo lo que pueden ver en pantalla, sino que a nivel de base de datos los datos están aislados para que nadie pueda acceder a información ajena."*

---

### 01:00 — 01:30 | LOGIN Y DASHBOARD DEL ADMINISTRADOR
**Pantalla:** Navegador ➔ Login (`/login`) ➔ Dashboard (`/dashboard`) con usuario `admin`  

> *"Pasemos a ver el sistema funcionando en vivo.*
> 
> *Iniciamos sesión primero como el **Administrador de Propiedad** del conjunto demo 'Torres del Parque'.*
> 
> *Al ingresar, lo primero que encontramos es este panel de control. Aquí el administrador ve de inmediato el estado general de su conjunto: cuántas unidades habitacionales tiene registradas, cuántos residentes viven en el edificio y el estado financiero de la cartera.*
> 
> *En este momento el sistema nos muestra que tenemos 4 apartamentos y una cartera pendiente por cobrar de $250.000 pesos colombianos. Todo esto se calcula con datos reales que vienen de la base de datos."*

---

### 01:30 — 02:00 | CARTERA Y FINANZAS
**Pantalla:** Módulo de Cartera (`/cartera`)  

> *"Vamos al módulo de Cartera para entender de dónde sale esa cifra.*
> 
> *Aquí vemos el listado por apartamento. El Apartamento 101, donde vive Carlos Martínez, tiene una cuota ordinaria pendiente de $250.000 pesos.*
> 
> *En cambio, el Apartamento 102, que corresponde a Ana Gómez, figura con saldo en cero y estado 'Al Día'. Esto es porque el sistema ya registró y concilió su pago a través de la pasarela digital Wompi.*
> 
> *El administrador puede filtrar, recalcular intereses si hubiera mora o revisar el detalle sin tener que hacer cuentas manuales."*

---

### 02:00 — 02:30 | AUTOGESTIÓN DEL RESIDENTE Y VISITAS
**Pantalla:** Cambio a usuario Residente (`camartinez`) ➔ `/res-visita`  

> *"Ahora pongámonos en el lugar del habitante. Cambiamos de sesión e ingresamos como **Carlos Martínez**, del Apartamento 101.*
> 
> *Por las políticas de seguridad que implementamos, Carlos solo puede ver su propio apartamento. No puede ver las cuotas de sus vecinos ni ingresar a pantallas de administración.*
> 
> *Carlos va a recibir un invitado hoy. En vez de llamar por citófono o avisar por WhatsApp, entra a 'Mis Visitas' y programa la llegada de su visitante. El sistema genera de inmediato este código QR temporal.*
> 
> *Este código es único, tiene un tiempo de validez limitado y viaja directamente al teléfono del visitante."*

---

### 02:30 — 03:00 | CONTROL EN PORTERÍA Y ESCÁNER QR
**Pantalla:** Cambio a usuario Portero (`portero01`) ➔ `/porteria`  

> *"Ahora vamos a la portería. Cerramos sesión e ingresamos como **portero01** en la garita principal.*
> 
> *El vigilante tiene una pantalla adaptada para trabajar rápido. Cuando el visitante llega a la entrada, el portero utiliza el escáner táctico. Puede leer el código con la cámara web o ingresar el token de respaldo si no hay cámara.*
> 
> *Al validar el código, el sistema consulta en milisegundos un procedimiento almacenado en Oracle. Nos confirma de inmediato que el código es auténtico, que no ha sido usado y que el visitante está autorizado para ir al Apartamento 101."*

---

### 03:00 — 03:30 | INGRESO VEHICULAR Y PARQUEADERO
**Pantalla:** Selección de Transporte en `/porteria` ➔ `/parqueaderos`  

> *"El visitante no llegó a pie, llegó en un carro con placa **DEM-123**.*
> 
> *El portero selecciona 'Vehículo', escribe la placa y el sistema valida que cumpla con el formato colombiano de tres letras y tres números. Al dar clic en 'Registrar Entrada', el sistema no solo autoriza el paso, sino que busca en las zonas comunes y le asigna automáticamente la bahía de visitantes **V-01**.*
> 
> *Si abrimos el módulo de Parqueaderos, confirmamos que la bahía V-01 aparece ocupada en rojo por el vehículo DEM-123 y empieza a contar el tiempo de estadía."*

---

### 03:30 — 04:00 | SALIDA DEL VEHÍCULO
**Pantalla:** Módulo de Parqueaderos (`/parqueaderos`) ➔ Registrar Salida  

> *"Cuando el visitante termina su visita y se retira del edificio, el portero no tiene que borrar planillas ni tachar libros.*
> 
> *Simplemente ubica la bahía V-01 en la grilla y presiona 'Registrar Salida'. En ese instante la visita pasa a estado finalizada, queda registrado el historial y la bahía vuelve a ponerse en verde como disponible para el próximo vehículo.*
> 
> *Así mantenemos el control de cupos sin riesgo de sobrecupo."*

---

### 04:00 — 04:30 | LOGÍSTICA DE PAQUETERÍA Y PIN DE RETIRO
**Pantalla:** Módulo de Paquetes (`/portero/paquetes`)  

> *"Otro proceso crítico en cualquier edificio es la paquetería de comercio electrónico.*
> 
> *En la pestaña de recepción, el portero registra un paquete que acaba de llegar por Servientrega para el Apartamento 101. Escribe los datos básicos y guarda.*
> 
> *En ese momento el sistema hace dos cosas automáticamente: primero, genera un **PIN de retiro** confidencial para esa encomienda; y segundo, envía una notificación directa al residente avisándole que su paquete está en custodia en la portería."*

---

### 04:30 — 05:00 | RETIRO DEL PAQUETE POR EL RESIDENTE
**Pantalla:** Login Residente (`camartinez`) ➔ `/res-buzon` ➔ Entrega en Portería  

> *"El residente Carlos entra a su aplicación y en 'Mi Buzón' ve la notificación con los datos de su paquete y su respectivo **PIN de retiro**.*
> 
> *Cuando Carlos baja a la portería a recogerlo, le da ese PIN al vigilante. El vigilante ingresa el PIN en el sistema y, si coincide, el paquete queda registrado formalmente como entregado.*
> 
> *Con este mecanismo de doble confirmación evitamos que un paquete se entregue a la persona equivocada o que se pierda la trazabilidad de quién lo recibió."*

---

### 05:00 — 05:30 | SEGURIDAD MULTI-TENANT Y BASE DE DATOS
**Pantalla:** Diapositiva 6 (Seguridad Multi-Tenant)  

> *"Queremos hacer un énfasis especial en cómo está protegido el sistema por detrás.*
> 
> *En aplicaciones web comunes, el filtrado de datos se suele hacer con un simple `WHERE` en el backend. Si el programador olvida esa condición en alguna consulta, un usuario podría llegar a ver información de otro conjunto residencial.*
> 
> *En SAED 2.0 usamos **Virtual Private Database (VPD) y Row-Level Security (RLS)** directamente dentro de Oracle Database. Cada vez que el backend hace una consulta, le pasa a Oracle el identificador de la propiedad a través de una variable de sesión llamada `SAED_CTX`.*
> 
> *Esto significa que las reglas de aislamiento las aplica el propio motor de base de datos. Si alguien intenta manipular la petición, Oracle simplemente devuelve cero registros."*

---

### 05:30 — 06:00 | CONCLUSIONES Y CIERRE
**Pantalla:** Diapositiva 12 (Cierre)  

> *"Para concluir:*
> 
> *SAED 2.0 integra la administración residencial, los residentes, la portería y las operaciones en una única plataforma SaaS, manteniendo separación por roles y aislamiento de datos entre los diferentes niveles de la organización.*
> 
> *Todo lo que les acabamos de mostrar está respaldado por 61 pruebas automatizadas de integración y seguridad, con el código congelado y certificado para operar en la nube.*
> 
> *Muchas gracias por su atención, y quedamos atentos a cualquier pregunta que tengan sobre el proyecto."*
