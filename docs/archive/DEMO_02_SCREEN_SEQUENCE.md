# SAED 2.0 — SECUENCIA DE PANTALLAS (SCREEN SEQUENCE)
## GUÍA OPERATIVA PANTALLA POR PANTALLA PARA LA DEMOSTRACIÓN EN VIVO

---

### REGLAS DE NAVEGACIÓN EN VIVO
1. **Ruta Fija:** Sigue estrictamente los 15 pasos. No hagas clics en menús laterales que no estén listados.
2. **Cero Improvisación:** Si algo demora 2 segundos, mantén la explicación verbal con calma.
3. **Protección de Datos:** Nunca abras herramientas de desarrollador (F12) ni muestres terminales con contraseñas o variables `.env`.

---

## MAPA DE SECUENCIA Y TIEMPOS MÁXIMOS

```
[1. Landing] ──► [2. Login] ──► [3. Dashboard] ──► [4. Cartera] ──► [5. Residentes]
     │                                                                     │
     ▼                                                                     ▼
[10. Entrada] ◄── [9. Escáner] ◄── [8. Login Port] ◄── [7. QR] ◄── [6. Login Res]
     │
     ▼
[11. Vehículo] ──► [12. Parqueadero] ──► [13. Paquetes] ──► [14. Residente] ──► [15. Cierre]
```

---

### PANTALLA 1: LANDING PAGE PÚBLICA
- **URL / Ruta:** `/`
- **Tiempo Máximo:** 20 segundos
- **Qué Mostrar:** Encabezado principal, propuesta de valor y botón de acceso al sistema.
- **Qué Decir:** *"Esta es la puerta de entrada pública a SAED 2.0. El sistema está pensado tanto para los residentes como para las empresas administradoras de propiedad horizontal."*
- **Qué NO Mostrar:** No bajes hasta el pie de página ni abras enlaces externos. Mantente en la sección superior.

---

### PANTALLA 2: INICIO DE SESIÓN (LOGIN)
- **URL / Ruta:** `/login`
- **Tiempo Máximo:** 15 segundos
- **Qué Mostrar:** Formulario limpio de acceso con usuario y contraseña.
- **Qué Decir:** *"Vamos a ingresar primero con el rol de Administrador de la copropiedad 'Torres del Parque'."*
- **Qué NO Mostrar:** **NUNCA** digites contraseñas en voz alta ni muestres notas adhesivas con contraseñas en pantalla. Utiliza el autocompletado del navegador o digita rápidamente.

---

### PANTALLA 3: DASHBOARD ADMIN_PROPIEDAD
- **URL / Ruta:** `/dashboard`
- **Tiempo Máximo:** 40 segundos
- **Qué Mostrar:** Los 4 KPI Cards principales (4 unidades habitacionales, residentes registrados, cartera de $250.000 COP y ocupación).
- **Qué Decir:** *"El administrador cuenta con un centro de control consolidado. Aquí ve que el edificio tiene 4 unidades y una cartera por cobrar de $250.000 pesos."*
- **Qué NO Mostrar:** No des clic en botones de configuración secundaria ni intentes exportar reportes PDF durante esta pantalla.

---

### PANTALLA 4: GESTIÓN DE CARTERA
- **URL / Ruta:** `/cartera`
- **Tiempo Máximo:** 30 segundos
- **Qué Mostrar:** La tabla de estados de cuenta. Destacar la Unidad 101 (debe $250.000 COP) y la Unidad 102 (saldo $0 COP al día).
- **Qué Decir:** *"En la cartera vemos el desglose exacto: Carlos Martínez en el Apto 101 debe la cuota del mes, mientras que Ana Gómez en el 102 está al día gracias a su pago procesado por Wompi."*
- **Qué NO Mostrar:** No intentes procesar un pago real con tarjeta en este momento. Muestra el estado ya conciliado.

---

### PANTALLA 5: GESTIÓN DE RESIDENTES
- **URL / Ruta:** `/residentes`
- **Tiempo Máximo:** 25 segundos
- **Qué Mostrar:** La lista de residentes, el buscador rápido y el indicador de unidades asignadas.
- **Qué Decir:** *"El administrador gestiona aquí a todos los habitantes, validando sus documentos de identidad y asociándolos a cada apartamento."*
- **Qué NO Mostrar:** No abras el modal de eliminación de residentes ni edites registros existentes para no alterar el dataset.

---

### PANTALLA 6: LOGIN DE RESIDENTE
- **URL / Ruta:** `/login` (Cerrar sesión previa)
- **Tiempo Máximo:** 15 segundos
- **Qué Mostrar:** Ingreso con las credenciales de `camartinez` (Unidad 101).
- **Qué Decir:** *"Ahora cambiamos de rol y entramos como Carlos Martínez, habitante del Apartamento 101."*
- **Qué NO Mostrar:** No demores en el formulario; haz el inicio de sesión fluido.

---

### PANTALLA 7: PORTAL RESIDENTE Y CÓDIGO QR DE VISITA
- **URL / Ruta:** `/res-visita`
- **Tiempo Máximo:** 35 segundos
- **Qué Mostrar:** La invitación activa con el código QR generado (`SAED-DEMO-QR-2026-TOKEN`) para *Visitante Demo*.
- **Qué Decir:** *"Carlos tiene programada la llegada de un visitante. El sistema le generó este código QR seguro con tiempo de validez limitado que el invitado presentará al llegar."*
- **Qué NO Mostrar:** No crees una nueva visita desde cero para evitar retrasos de tipeo; muestra la visita demo ya lista.

---

### PANTALLA 8: LOGIN DE PORTERO
- **URL / Ruta:** `/login` (Cerrar sesión de residente)
- **Tiempo Máximo:** 15 segundos
- **Qué Mostrar:** Ingreso con el usuario `portero01`.
- **Qué Decir:** *"Nos trasladamos ahora al puesto de vigilancia e ingresamos como el oficial de portería."*
- **Qué NO Mostrar:** No abras pestañas ajenas a la garita.

---

### PANTALLA 9: ESCÁNER TÁCTICO DE PORTERÍA
- **URL / Ruta:** `/porteria`
- **Tiempo Máximo:** 30 segundos
- **Qué Mostrar:** La interfaz del escáner HUD láser con el botón de lectura y la opción de portapapeles.
- **Qué Decir:** *"Esta es la interfaz de alta velocidad del guardia. Si hay cámara la usa, y si no, cuenta con entrada rápida por código."*
- **Qué NO Mostrar:** Si la cámara web tarda en encender, no pierdas tiempo: da clic inmediato en *"Pegar Portapapeles"*.

---

### PANTALLA 10: VALIDACIÓN DE ENTRADA
- **URL / Ruta:** `/porteria` (Resultado de la consulta QR)
- **Tiempo Máximo:** 25 segundos
- **Qué Mostrar:** Los datos acreditados en pantalla: *Visitante Demo*, C.C. 1000000010, destino Apto 101.
- **Qué Decir:** *"El procedimiento en base de datos valida en milisegundos que la credencial es auténtica y acredita al visitante hacia el Apartamento 101."*
- **Qué NO Mostrar:** No modifiques los datos del visitante acreditado.

---

### PANTALLA 11: REGISTRO VEHICULAR Y CUPOS
- **URL / Ruta:** `/porteria` (Campos de transporte)
- **Tiempo Máximo:** 30 segundos
- **Qué Mostrar:** Selección del modo **CARRO**, ingreso de la placa **DEM-123** y clic en **Registrar Entrada**.
- **Qué Decir:** *"El visitante llega en vehículo. Escribimos la placa DEM-123 y al confirmar la entrada el sistema le asigna de forma autónoma el parqueadero V-01."*
- **Qué NO Mostrar:** No ingreses placas con formatos inválidos (ej: 2 letras); usa la placa demo `DEM-123`.

---

### PANTALLA 12: MÓDULO DE PARQUEADEROS
- **URL / Ruta:** `/parqueaderos`
- **Tiempo Máximo:** 35 segundos
- **Qué Mostrar:** La bahía **V-01** en rojo (`OCUPADO`) con la placa `DEM-123` y el botón para registrar salida.
- **Qué Decir:** *"En la grilla de parqueaderos confirmamos que V-01 está ocupada. Al marcharse el visitante, presionamos 'Registrar Salida' y el cupo queda inmediatamente liberado."*
- **Qué NO Mostrar:** No des clic en editar o eliminar otras bahías fijas de copropietarios.

---

### PANTALLA 13: RECEPCIÓN DE PAQUETERÍA
- **URL / Ruta:** `/portero/paquetes`
- **Tiempo Máximo:** 40 segundos
- **Qué Mostrar:** El formulario de recepción: Unidad **101**, Transportadora **Servientrega**, descripción. Clic en guardar y aparición del **PIN de retiro**.
- **Qué Decir:** *"Llega un paquete para el Apto 101. El guardia lo registra y el sistema genera automáticamente un PIN de retiro confidencial, notificando al residente."*
- **Qué NO Mostrar:** No digas "código de 6 dígitos"; usa siempre la expresión *"PIN de retiro"* o *"PIN de entrega"*.

---

### PANTALLA 14: BUZÓN DEL RESIDENTE Y RETIRO
- **URL / Ruta:** `/res-buzon` (en ventana paralela de residente)
- **Tiempo Máximo:** 35 segundos
- **Qué Mostrar:** El paquete listado en el buzón con su **PIN de retiro** visible para el titular.
- **Qué Decir:** *"El residente consulta su buzón virtual, ve su paquete y su PIN de retiro. Con ese PIN se acerca a portería para completar la entrega segura."*
- **Qué NO Mostrar:** No abras otras secciones de reclamos o quejas en esta pantalla.

---

### PANTALLA 15: REGRESO A DIAPOSITIVAS Y CIERRE
- **URL / Ruta:** Presentación Maestro (Diapositivas 11 y 12)
- **Tiempo Máximo:** 30 segundos
- **Qué Mostrar:** Resumen de tecnologías, arquitectura de seguridad RLS y conclusiones finales.
- **Qué Decir:** *"Con esto demostramos que todos los módulos operan de forma integrada y con aislamiento garantizado a nivel de base de datos. Muchas gracias."*
