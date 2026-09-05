# SAED 2.0 — BANCO DE PREGUNTAS Y RESPUESTAS DEL JURADO (JURY Q&A)
## PREPARACIÓN INTEGRAL ANTE PREGUNTAS TÉCNICAS Y CONCEPTUALES

---

### GUÍA DE USO PARA EL EQUIPO
- Las respuestas están agrupadas por áreas temáticas.
- Cada respuesta se basa estrictamente en lo que está **realmente implementado y certificado** en el proyecto.
- Si una pregunta indaga por algo que no está en el MVP, se responde con honestidad técnica reconociéndolo como trabajo futuro del roadmap.

---

```
ÍNDICE DE CATEGORÍAS
1. PRODUCTO Y NEGOCIO
2. ARQUITECTURA GENERAL
3. BASE DE DATOS Y ORACLE
4. SEGURIDAD Y AISLAMIENTO
5. BACKEND Y SPRING BOOT
6. FRONTEND Y EXPERIENCIA DE USUARIO
7. MULTI-TENANT
8. DEPLOYMENT E INFRAESTRUCTURA
9. ESCALABILIDAD Y RENDIMIENTO
10. ROADMAP Y FUTURO
```

---

## 1. PRODUCTO Y NEGOCIO

#### P01. ¿Qué problema resuelve SAED 2.0?
**Respuesta:** Resuelve la fragmentación operativa y la falta de trazabilidad en conjuntos residenciales. Unifica en un solo sistema la gestión de cartera, el control de acceso de visitantes, la asignación de parqueaderos y la custodia de paquetería, eliminando planillas en papel y grupos informales.

#### P02. ¿Por qué decidieron construirlo como un modelo SaaS?
**Respuesta:** Porque las empresas de administración de propiedad horizontal manejan múltiples copropiedades simultáneamente. Un modelo SaaS permite que la empresa administradora gestione toda su cartera de edificios desde una sola plataforma centralizada sin tener que desplegar servidores independientes por cada conjunto.

#### P03. ¿Qué diferencia hay entre SAED 2.0 y un software tradicional diseñado para un solo edificio?
**Respuesta:** La diferencia principal es el aislamiento multi-inquilino. Un software tradicional para un solo edificio comparte una base de datos plana sin segregación. SAED 2.0 permite que múltiples organizaciones y propiedades convivan en la misma infraestructura, pero con separación lógica y de seguridad garantizada por políticas de base de datos.

---

## 2. ARQUITECTURA GENERAL

#### P04. ¿Cómo está estructurada la arquitectura de SAED 2.0?
**Respuesta:** Sigue un patrón de arquitectura limpia en tres capas desacopladas: una Single Page Application (SPA) en React 18 en el frontend, una API REST con Spring Boot 3 en el backend y una base de datos relacional Oracle Autonomous Database en la nube, comunicadas mediante contratos JSON y seguridad basada en tokens JWT.

#### P05. ¿Qué parte del desarrollo fue la más compleja técnicamente?
**Respuesta:** La sincronización del contexto de seguridad entre el pool de conexiones de Spring Boot y la sesión de Oracle. Al usar HikariCP, las conexiones se reutilizan entre diferentes usuarios; lograr que `SaedDataSourceProxy` configurara y limpiara el contexto `SAED_CTX` en cada transacción sin que hubiera fuga de datos entre hilos concurrentes fue el desafío técnico más exigente.

#### P06. ¿Cómo se comunican el frontend y el backend?
**Respuesta:** A través de una API REST protegida por HTTPS. Cada petición autenticada incluye dos encabezados obligatorios: `Authorization: Bearer <token>` para la identidad del usuario y `X-Assignment-Id: <id>` para establecer con qué rol y en qué copropiedad está actuando.

---

## 3. BASE DE DATOS Y ORACLE

#### P07. ¿Por qué eligieron Oracle Autonomous Database en lugar de motores más comunes como PostgreSQL o MySQL?
**Respuesta:** Porque Oracle cuenta con **Virtual Private Database (VPD / RLS)** nativo a nivel de kernel, lo que nos permite aplicar reglas de seguridad transparentes en las tablas relacionales. Además, al ser una base de datos autónoma en Oracle Cloud, resuelve automáticamente la alta disponibilidad, parches de seguridad y copias de respaldo con conexiones mTLS.

#### P08. ¿Qué es RLS (Row Level Security) y qué es VPD (Virtual Private Database)?
**Respuesta:** Son dos nombres para el mismo concepto en Oracle. Es una característica donde el motor de base de datos intercepta cualquier consulta SQL y le adjunta dinámicamente una cláusula `WHERE` definida en una función PL/SQL, garantizando que el usuario solo pueda leer o modificar las filas autorizadas para su contexto.

#### P09. ¿Cómo se implementaron las políticas RLS en el código SQL?
**Respuesta:** Se asociaron mediante el paquete del sistema `DBMS_RLS.ADD_POLICY` a las tablas críticas del negocio (como `USUARIOS`, `PERSONAS`, `VISITAS`, `PAQUETES`, `CUOTAS`). Estas políticas llaman a funciones de predicado como `FN_FILTRO_ORGANIZACION` y `FN_FILTRO_PROPIEDAD`, las cuales leen las variables de sesión del contexto `SAED_CTX`.

---

## 4. SEGURIDAD Y AISLAMIENTO

#### P10. ¿Qué diferencia hay entre RBAC y RLS?
**Respuesta:** **RBAC (Role-Based Access Control)** define **qué acciones** puede ejecutar un usuario según su rol (por ejemplo: el portero puede registrar entradas pero no puede modificar cuotas contables). **RLS (Row Level Security)** define **sobre qué datos específicos** puede actuar ese usuario (por ejemplo: un residente puede ver cuotas, pero únicamente las de su propio apartamento).

#### P11. ¿Qué pasa si un usuario malicioso intercepta la petición HTTP y cambia el ID de la propiedad en la URL?
**Respuesta:** La consulta no devolverá datos ajenos. Aunque el atacante cambie el ID en la URL o en el cuerpo JSON, el backend ignora el ID provisto en la petición y utiliza el contexto criptográficamente validado en el token JWT y en `SAED_CTX`. Si el ID forzado no coincide con el contexto activo, la consulta en Oracle retorna cero filas o un error 403 Forbidden.

#### P12. ¿Cómo evitan que un residente vea información de los otros apartamentos del edificio?
**Respuesta:** Mediante el aislamiento por unidad. Cuando un usuario con rol `RESIDENTE` inicia sesión, su contexto se restringe a su `ID_UNIDAD`. Las consultas a tablas como cuotas, buzón de paquetes y visitas filtran automáticamente por esa unidad, impidiendo que la interfaz o la API expongan información vecina.

#### P13. ¿Qué función cumple la variable de sesión SAED_CTX?
**Respuesta:** Es un espacio de memoria de sesión en Oracle creado con `CREATE CONTEXT SAED_CTX`. En cada conexión, el procedimiento `PKG_SAED_SESSION.SET_CONTEXT` establece los identificadores de organización, propiedad, unidad y rol del usuario actual, sirviendo como la fuente de verdad que consultan las políticas RLS.

---

## 5. BACKEND Y SPRING BOOT

#### P14. ¿Por qué utilizaron JdbcTemplate en lugar de un ORM completo como Hibernate/JPA?
**Respuesta:** Porque necesitábamos control granular sobre las conexiones y el ciclo de vida de la sesión de Oracle para inyectar `PKG_SAED_SESSION.SET_CONTEXT` antes de cada consulta. Además, `JdbcTemplate` ofrece un rendimiento superior, evita problemas de sobrecarga por mapeo de entidades y nos permite invocar procedimientos almacenados PL/SQL de forma directa y parametrizada.

#### P15. ¿Cómo validan los datos que llegan en las peticiones REST?
**Respuesta:** El backend utiliza validaciones de beans (`@Valid`) en los DTOs de entrada para comprobar tipos, campos requeridos y formatos. Adicionalmente, las reglas de negocio críticas (como verificar que un pago no supere el saldo adeudado o que una visita tenga un código no expirado) se validan en los servicios Java y en los procedimientos de base de datos.

#### P16. ¿Cómo maneja el backend los errores y excepciones?
**Respuesta:** Implementamos un `GlobalExceptionHandler` con `@RestControllerAdvice`. Este componente intercepta excepciones comunes (como credenciales inválidas, accesos denegados o violaciones de integridad) y retorna respuestas JSON estandarizadas con códigos HTTP semánticos (400, 401, 403, 404, 409), cuidando nunca exponer trazas de error (stacktraces) al cliente.

---

## 6. FRONTEND Y EXPERIENCIA DE USUARIO

#### P17. ¿Por qué eligieron React y Vite para la interfaz?
**Respuesta:** React nos permite estructurar la aplicación en componentes modulares y reutilizables con un ciclo de vida predecible. Vite nos ofrece tiempos de compilación casi instantáneos en desarrollo y genera empaquetados minificados y optimizados para producción en pocos segundos.

#### P18. ¿Cómo manejan la autenticación y las rutas protegidas en el cliente?
**Respuesta:** Contamos con un `AuthContext` que administra el estado de autenticación y almacena el token de sesión. Todas las rutas de la aplicación están envueltas en componentes `ProtectedRoute` que consultan el rol activo del usuario; si un usuario intenta navegar hacia una URL a la que no tiene derecho, es redirigido automáticamente a su panel principal.

#### P19. ¿Cómo previenen envíos duplicados en los formularios?
**Respuesta:** Utilizamos referencias mutables síncronas (`savingRef = useRef(false)`). Al hacer clic en enviar, el ref se marca como activo antes de que ocurra el re-render de React; si el usuario hace doble clic impulsivo, la función bloquea el segundo llamado de forma inmediata, evitando registros duplicados.

---

## 7. OPERACIÓN RESIDENCIAL: QR, PARQUEADEROS Y PAQUETES

#### P20. ¿Cómo funciona la generación y lectura del código QR de visitas?
**Respuesta:** El residente agenda la visita y el sistema genera un token criptográfico efímero con vigencia temporal. En portería, el guardia apunta la cámara web o usa un lector USB; la librería jsQR decodifica el token y el backend ejecuta el procedimiento `SP_VALIDAR_CONSUMIR_QR`, verificando que el código sea válido y pertenezca a una unidad activa.

#### P21. ¿Qué pasa si una persona intenta reutilizar un código QR que ya fue utilizado?
**Respuesta:** El procedimiento almacenado en Oracle verifica el estado del registro de visita. Si el token ya fue consumido o la visita ya está marcada como `EN_CURSO` o `FINALIZADA`, el procedimiento rechaza la entrada y retorna un mensaje de error indicando que la credencial ya no es válida.

#### P22. ¿Cómo se garantiza la entrega correcta de paquetes en portería?
**Respuesta:** Mediante una cadena de custodia cerrada por un **PIN de retiro**. Cuando el vigilante registra el paquete que llega, el sistema genera automáticamente un PIN unívoco y le envía una alerta al residente en su buzón virtual. Al reclamar el paquete, el residente debe suministrar ese PIN en garita para que el sistema permita marcar el ítem como `ENTREGADO`.

#### P23. ¿Cómo se asignan y liberan los cupos de parqueadero?
**Respuesta:** Al momento en que el portero registra el ingreso de un visitante en automóvil o moto, el sistema consulta las bahías con tipo `VISITANTES` y estado `DISPONIBLE`, asignando la primera libre de forma autónoma. Al salir, un solo clic en la grilla cambia el estado del cupo nuevamente a `DISPONIBLE` y sella la hora de egreso.

---

## 8. FINANZAS Y PAGOS DIGITALES

#### P24. ¿Cómo manejan la cartera y el recaudo de cuotas de administración?
**Respuesta:** El módulo de cartera calcula los balances por apartamento consolidando cuotas ordinarias, extraordinarias y pagos realizados. El administrador puede consultar en tiempo real quién está al día y quién está en mora, con la posibilidad de registrar cobros manuales o consultar pagos en línea.

#### P25. ¿Cómo funciona la integración con la pasarela Wompi?
**Respuesta:** Integramos el widget de pago seguro de Wompi. Cuando el residente paga su administración con tarjeta de crédito, PSE o botón Bancolombia, Wompi procesa el cobro y envía un evento webhook a nuestro backend. Nuestro servicio verifica la firma digital del mensaje (checksum SHA-256) y, si es legítima, aplica el abono y salda la cuota de forma automática.

---

## 9. INFRAESTRUCTURA, DEPLOYMENT Y PRUEBAS

#### P26. ¿Cómo y dónde está desplegado el sistema actualmente?
**Respuesta:** El sistema está desplegado en una arquitectura multi-nube desacoplada: el frontend estático se sirve a través de la red global de **Vercel**; el backend Spring Boot opera en un contenedor en **Render Cloud**; y la base de datos corre en **Oracle Cloud (OCI)** en un servicio autónomo con billetera de certificados mTLS.

#### P27. ¿Qué tipo de pruebas automatizadas respaldan el proyecto?
**Respuesta:** Contamos con una suite de **61 pruebas automatizadas** en Spring Boot que cubren pruebas unitarias de servicios, pruebas de integración de base de datos con datasets de prueba y pruebas adversariales de seguridad (como validar que el pool de conexiones no sufra fuga de contexto y que los roles no puedan escalar privilegios).

---

## 10. ROADMAP Y TRABAJO FUTURO

#### P28. ¿Qué funcionalidades quedaron fuera del alcance del MVP y forman parte del futuro del proyecto?
**Respuesta:** Para asegurar la estabilidad y calidad de este MVP nos concentramos en el flujo central (administración, cartera, visitas QR, parqueaderos y paquetería). En nuestro roadmap post-MVP quedan planificadas la automatización completa de asambleas con votación en vivo, la gestión de pólizas de seguros, el control preventivo de mantenimientos de maquinaria y el desarrollo de aplicaciones móviles nativas para iOS y Android.
