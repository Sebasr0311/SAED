# SAED 2.0 — LISTA DE VERIFICACIÓN OPERATIVA PARA DEMO DAY
## PROTOCOLO DE PRE-VUELO Y CONTROL EN VIVO

---

### PREPARACIÓN Y CHECKLIST OPERATIVO OBLIGATORIO

Antes de iniciar la presentación académica o la demostración en vivo, verifique individualmente cada uno de los siguientes puntos críticos:

- [ ] **Laptop cargada:** Batería al 100% de capacidad.
- [ ] **Cargador disponible:** Cargador conectado a toma de corriente directa.
- [ ] **Internet probado:** Conectividad verificada (ping < 50ms, hotspot móvil de respaldo en espera).
- [ ] **Navegador preparado:** Navegador limpio, sin extensiones intrusivas y con perfiles/ventanas listos.
- [ ] **Frontend disponible:** Vercel o servidor Vite local levantado y respondiendo en HTTPS/HTTP.
- [ ] **Backend disponible:** Spring Boot 3.2 activo respondiendo peticiones REST sin errores.
- [ ] **Oracle ATP disponible:** Conexión segura mTLS activa y sin alertas de pool de conexiones.
- [ ] **Login probado:** Inicio de sesión verificado con cada uno de los roles de demostración.
- [ ] **Dataset demo disponible:** Copropiedad 1 con Apto 101, Apto 102, QR `SAED-DEMO-QR-2026-TOKEN`, Cupo V-01 y vehículo DEM-123.
- [ ] **Render calentado:** Petición de calentamiento (pre-warm) enviada al backend en Render para evitar retrasos por arranque en frío.
- [ ] **Cámara probada:** Permisos de acceso a la cámara concedidos en el navegador para el escáner táctico.
- [ ] **Audio/proyector probado si aplica:** Resolución configurada a 1920x1080 o 1440x900 con escala al 100%.
- [ ] **Pantalla compartida probada:** Verificado que la plataforma de videoconferencia/proyección comparta la ventana correcta.
- [ ] **Zoom del navegador adecuado:** Nivel de zoom fijado en 100% para preservar la jerarquía visual del Design System.
- [ ] **Pestañas innecesarias cerradas:** Cerradas todas las aplicaciones de mensajería (WhatsApp, Slack, Teams), correo y pestañas personales.
- [ ] **Terminales con secretos cerradas:** Ninguna consola con variables de entorno, logs con tokens o credenciales abierta en segundo plano.
- [ ] **.env nunca visible:** Archivos `.env`, claves de API o archivos de configuración ocultos de la vista pública.
- [ ] **Credenciales fuera de screenshots:** Ninguna contraseña real proyectada ni registrada en capturas de pantalla.

---

## 2. PREPARACIÓN DE PESTAÑAS DEL NAVEGADOR

Se recomienda disponer de ventanas de incógnito o perfiles independientes para cada rol:

1. **Ventana A — Administrador de Propiedad:**
   - Rol: `ADMIN_PROPIEDAD` (usuario: `admin`)
   - Vistas: `/dashboard` y `/cartera`
2. **Ventana B — Garita de Portería:**
   - Rol: `PORTERO` (usuario: `portero01`)
   - Vistas: `/porteria` (Escáner) y `/parqueaderos`
3. **Ventana C — Residente de la Copropiedad:**
   - Rol: `RESIDENTE` (usuario: `camartinez`, Apto 101)
   - Vistas: `/res-visita` y `/res-buzon`

---

## 3. CONTROL DE TIEMPO EN VIVO (MÁXIMO 6 MINUTOS)

| Hito | Tiempo Límite | Rol Activo | Pantalla / Módulo | Verificación |
| :---: | :---: | :--- | :--- | :--- |
| **1** | `00:30` | Presentador | Landing (`/`) | Mensaje inicial de valor PropTech. |
| **2** | `01:15` | `ADMIN_PROPIEDAD` | Dashboard (`/dashboard`) | Visión centralizada del conjunto, 4 KPIs clave. |
| **3** | `01:45` | `ADMIN_PROPIEDAD` | Cartera (`/cartera`) | Contraste Apto 101 ($250k debe) vs 102 ($0 al día). |
| **4** | `02:15` | `RESIDENTE` | Visitas (`/res-visita`) | Generación y token QR de invitación activa. |
| **5** | `03:15` | `PORTERO` | Garita (`/porteria`) | Validación QR, ingreso vehicular DEM-123 y cupo V-01. |
| **6** | `03:45` | `PORTERO` | Parqueaderos (`/parqueaderos`) | Bahía V-01 ocupada y liberación en un clic. |
| **7** | `04:30` | `PORTERO` | Paquetería (`/portero/paquetes`)| Recepción Servientrega y generación de PIN de retiro. |
| **8** | `05:00` | `RESIDENTE` | Buzón (`/res-buzon`) | Notificación, consulta de PIN de retiro y entrega. |
| **9** | `05:40` | Presentador | Arquitectura | Aislamiento en base de datos: RBAC, VPD/RLS, `SAED_CTX`. |
| **10**| `06:00` | Presentador | Cierre | Conclusiones y apertura a preguntas del jurado. |
