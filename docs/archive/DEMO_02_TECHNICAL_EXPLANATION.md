# SAED 2.0 — ARGUMENTARIO TÉCNICO PARA DEFENSA (TECHNICAL EXPLANATION)
## RESPUESTAS CONCISAS PARA PREGUNTAS DEL JURADO (3 A 5 FRASES POR TEMA)

---

### GUÍA DE RESPUESTA PARA EL ESTUDIANTE
- **Estructura mental:** Da primero la respuesta conceptual clara (1–2 frases). Luego añade el detalle de implementación de SAED 2.0 (2–3 frases).
- **Seguridad:** Mantén la calma; habla de lo que realmente construyeron en el código.

---

### A. ¿POR QUÉ REACT?
- **Explicación Sencilla:** Elegimos React porque nos permite construir una interfaz rápida y modular basada en componentes reutilizables, ideal para una aplicación de alta interactividad donde la información cambia en tiempo real sin recargar la página.
- **Detalle Técnico:** Usamos React 18 junto con Vite como empaquetador para optimizar tiempos de compilación y carga. La gestión de estado se apoya en contextos nativos (`AuthContext` y `TenantProvider`), evitando dependencias pesadas y garantizando que los formularios y tablas respondan de inmediato.

---

### B. ¿POR QUÉ SPRING BOOT?
- **Explicación Sencilla:** Spring Boot es el estándar de la industria en el ecosistema Java para crear microservicios y APIs REST empresariales robustas, seguras y escalables.
- **Detalle Técnico:** Ofrece un ecosistema maduro de seguridad con Spring Security para la validación de tokens JWT y control de acceso por roles. Además, su integración con `JdbcTemplate` nos permite interactuar de forma directa, eficiente y parametrizada con los procedimientos almacenados de Oracle.

---

### C. ¿POR QUÉ ORACLE AUTONOMOUS DATABASE?
- **Explicación Sencilla:** Necesitábamos un motor de base de datos relacional de grado bancario que no solo almacenara datos, sino que aplicara políticas de seguridad complejas directamente en el motor.
- **Detalle Técnico:** Oracle ATP nos ofrece **Virtual Private Database (VPD)** y seguridad a nivel de fila (RLS) nativa en el kernel de la base de datos. Además, al ser autónoma en Oracle Cloud Infrastructure, gestiona automáticamente el escalado, los respaldos y la alta disponibilidad mediante conexiones mTLS seguras.

---

### D. ¿QUÉ SIGNIFICA MULTI-TENANT?
- **Explicación Sencilla:** Significa que una sola instancia de la aplicación y una sola base de datos atienden a múltiples clientes u organizaciones independientes, garantizando que ninguna pueda ver la información de otra.
- **Detalle Técnico:** Implementamos el modelo multi-inquilino lógico mediante columnas de discriminación (`ID_ORGANIZACION` e `ID_PROPIEDAD`), blindadas con políticas RLS para que la separación de datos ocurra en la consulta SQL y no dependa exclusivamente de la memoria del frontend.

---

### E. ¿CÓMO FUNCIONA RBAC (CONTROL DE ACCESO BASADO EN ROLES)?
- **Explicación Sencilla:** Es un modelo de seguridad donde los permisos no se le asignan a cada persona de forma individual, sino al rol o cargo que desempeña (Superadmin, Administrador, Portero, Residente).
- **Detalle Técnico:** En el backend, cada endpoint REST está protegido con anotaciones `@PreAuthorize("hasRole(...)")`. En el frontend, las rutas están custodiadas por el componente `ProtectedRoute`, que redirige a los usuarios si intentan entrar a vistas que no corresponden a su rol.

---

### F. ¿QUÉ ES RLS / VPD (ROW LEVEL SECURITY / VIRTUAL PRIVATE DATABASE)?
- **Explicación Sencilla:** Es un mecanismo de seguridad de Oracle que intercepta automáticamente cualquier consulta SQL y le agrega de forma invisible una condición de filtrado según el usuario conectado.
- **Detalle Técnico:** Configuramos políticas con `DBMS_RLS.ADD_POLICY` que ejecutan funciones de predicado PL/SQL (`FN_FILTRO_ORGANIZACION` y `FN_FILTRO_PROPIEDAD`). Si un atacante ejecuta un `SELECT * FROM USUARIOS`, Oracle solo le devuelve los registros de su propia copropiedad.

---

### G. ¿QUÉ FUNCIÓN CUMPLE SAED_CTX?
- **Explicación Sencilla:** Es una variable de memoria segura dentro de la sesión de Oracle que guarda qué usuario, qué rol y qué edificio están ejecutando la consulta en ese momento.
- **Detalle Técnico:** Es un espacio de contexto creado con `CREATE CONTEXT SAED_CTX`. Nuestro componente de backend `SaedDataSourceProxy` ejecuta `PKG_SAED_SESSION.SET_CONTEXT` cada vez que toma una conexión del pool Hikari, asegurando que las funciones RLS lean esos valores de forma confiable.

---

### H. ¿QUÉ HACE EL ENCABEZADO X-ASSIGNMENT-ID?
- **Explicación Sencilla:** Es un identificador que viaja en cada petición HTTP indicando con qué cargo o asignación activa está operando el usuario en ese momento.
- **Detalle Técnico:** Permite que un usuario con múltiples roles (por ejemplo, alguien que es Administrador en un conjunto pero Residente en otro) no mezcle permisos. El filtro `JwtAuthenticationFilter` extrae el `X-Assignment-Id`, valida que pertenezca al usuario autenticado y configura el contexto de seguridad.

---

### I. ¿CÓMO SE CONTROLA EL ACCESO DE VISITANTES?
- **Explicación Sencilla:** A través de un flujo digital donde el residente crea una autorización previa y el vigilante acredita la entrada en la garita física en cuestión de segundos.
- **Detalle Técnico:** Cuando el residente agenda la visita, se crea un registro en estado programado con un token criptográfico efímero. Al llegar, el portero valida el código y la base de datos actualiza el estado a `EN_CURSO`, guardando fecha, hora exacta y vigilante responsable.

---

### J. ¿CÓMO FUNCIONA EL CÓDIGO QR?
- **Explicación Sencilla:** El QR no guarda los datos personales del visitante en texto plano, sino un token de autorización único y temporal que solo nuestro sistema sabe interpretar.
- **Detalle Técnico:** El frontend dibuja el QR en un canvas a partir del token. Al escanearlo en portería, el backend ejecuta el procedimiento `SP_VALIDAR_CONSUMIR_QR`, que verifica que el token no haya expirado, que no haya sido usado previamente y que la unidad receptora no esté bloqueada.

---

### K. ¿CÓMO SE CONTROLA EL PARQUEADERO?
- **Explicación Sencilla:** El sistema mantiene un inventario digital en tiempo real de todas las bahías (privadas y de visitantes), asignando cupos libres y registrando la placa del vehículo.
- **Detalle Técnico:** La tabla `PARQUEADEROS` rastrea el estado de cada espacio (`DISPONIBLE`, `OCUPADO`, `MANTENIMIENTO`). Cuando ingresa un carro o moto, el sistema busca la primera bahía de visitantes disponible, la vincula a la visita y la bloquea hasta que el portero registra la salida en un clic.

---

### L. ¿CÓMO FUNCIONA LA CADENA DE CUSTODIA DE PAQUETERÍA?
- **Explicación Sencilla:** Cuando llega una encomienda a portería, el sistema genera un **PIN de retiro** confidencial y se lo notifica al residente. El paquete solo se entrega cuando el residente presenta ese PIN.
- **Detalle Técnico:** Al guardar el paquete, el backend genera un código de seguridad unívoco que se almacena en la columna `CODIGO_RETIRO_PIN` y emite una notificación. Para completar la entrega, el portero envía el PIN recibido al endpoint `/paquetes/{id}/entrega`, el cual valida la coincidencia antes de transicionar el estado a `ENTREGADO`.

---

### M. ¿CÓMO SE INTEGRA LA PASARELA WOMPI?
- **Explicación Sencilla:** Permite que los residentes paguen sus cuotas de administración por internet mediante PSE, tarjeta de crédito o botón Bancolombia, conciliando la deuda de inmediato.
- **Detalle Técnico:** La integración utiliza el widget checkout de Wompi. Cuando el pago se completa en la pasarela, Wompi envía un webhook firmado criptográficamente con checksum SHA-256 a nuestro backend; nuestro servicio valida la firma, concilia la cuota pendiente y actualiza el saldo de cartera a cero.

---

### N. ¿CÓMO SE DESPLIEGA EL SISTEMA EN PRODUCCIÓN?
- **Explicación Sencilla:** Utilizamos una infraestructura moderna totalmente en la nube, separando el frontend, el backend y la base de datos en proveedores especializados.
- **Detalle Técnico:** El frontend se compila y distribuye globalmente a través de la red perimetral de **Vercel**. El backend en Spring Boot corre en un contenedor dentro de **Render Cloud** con reinicio automático y variables de entorno seguras. La base de datos reside en **Oracle Cloud (OCI)** bajo un servicio autónomo protegido con certificados mTLS.
