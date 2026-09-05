# SAED 2.0 — HOJA RESUMEN DE EMERGENCIA (ONE PAGE CHEATSHEET)
## REVISIÓN RÁPIDA DE 2 MINUTOS ANTES DE SALIR A PRESENTAR

---

### SAED 2.0 EN UNA FRASE
> **"Plataforma SaaS para la administración integral de conjuntos residenciales."**

---

### STACK TECNOLÓGICO
- **Frontend:** React 18 + Vite 5 + Tailwind CSS
- **Backend:** Spring Boot 3.2 + Java LTS (17/24) + JDBC
- **Base de Datos:** Oracle Autonomous Database (ATP) en OCI
- **Integraciones:** Wompi (Pagos PSE/Tarjeta) + Brevo (Correos transaccionales)
- **Infraestructura:** Vercel (Frontend) + Render (Backend API) + Oracle Cloud

---

### LOS 5 ROLES DEL SISTEMA
1. **SUPERADMIN:** Negocio SaaS global y administración de organizaciones.
2. **ADMIN_ORGANIZACION:** Gestión de cartera de copropiedades de una empresa.
3. **ADMIN_PROPIEDAD:** Operación integral del conjunto residencial (`admin`).
4. **PORTERO:** Control físico de garita, accesos, bahías y encomiendas (`portero01`).
5. **RESIDENTE:** Autoservicio exclusivo de su unidad habitacional (`camartinez`, `anagomez`).

---

### PILARES DE SEGURIDAD
- **RBAC:** Permisos de acción por rol tanto en backend (`@PreAuthorize`) como en frontend (`ProtectedRoute`).
- **Multi-Tenant:** Aislamiento lógico por organización y propiedad.
- **X-Assignment-Id:** Encabezado HTTP obligatorio que enlaza al usuario con su rol y propiedad activa.
- **RLS / VPD:** Políticas de seguridad aplicadas directamente en el kernel de Oracle Database.
- **SAED_CTX:** Contexto de sesión inyectado en cada consulta de base de datos para filtrado automático.

---

### RUTA CRÍTICA DE LA DEMOSTRACIÓN (≤ 04:00 MINUTOS)
```
[1. DASHBOARD]  ──► 4 unidades, $250.000 COP en cartera consolidada
       ↓
[2. CARTERA]    ──► Apto 101 debe $250k; Apto 102 al día (conciliado por Wompi)
       ↓
[3. RESIDENTE]  ──► Carlos Martínez entra y consulta su unidad
       ↓
[4. QR VISITAS] ──► Invitación programada con código QR activo
       ↓
[5. PORTERÍA]   ──► Escáner táctico valida QR en procedimiento de base de datos
       ↓
[6. VEHÍCULO]   ──► Ingreso de auto DEM-123 con validación de placa
       ↓
[7. PARQUEO]    ──► Asignación de bahía V-01; liberación en un clic al salir
       ↓
[8. PAQUETES]   ──► Registro de encomienda Servientrega y generación de PIN de retiro
       ↓
[9. RESIDENTE]  ──► Visualización de encomienda y PIN en su buzón virtual
       ↓
[10. ENTREGA]   ──► Entrega validada en portería mediante el PIN de retiro
```

---

### REGLAS DE ORO: ¡NO OLVIDAR!
1. **No mostrar secretos:** Nunca abras consolas con variables `.env`, tokens JWT o contraseñas reales.
2. **No hacer pagos reales:** Muestra el pago ya conciliado de la Unidad 102.
3. **No improvisar:** Sigue estrictamente la secuencia de pantallas establecida.
4. **No navegar módulos secundarios:** Quédate en el flujo principal (no abras Asambleas ni Mantenimiento).
5. **Si algo tarda unos segundos:** Mantén la calma, continúa explicando el concepto arquitectónico y espera.
6. **Si falla la cámara web:** Da clic inmediato en el botón *"Pegar Portapapeles"* o digita el código demo.
7. **Si Render está frío:** El pre-calentamiento previo debe haberse ejecutado con `curl`.
8. **Si ocurre un imprevisto ambiental:** Explica el flujo funcional con tranquilidad y apóyate en el Plan B.

---
*¡Respira hondo, habla pausado y confía en el trabajo realizado! La arquitectura está probada y funciona.*
