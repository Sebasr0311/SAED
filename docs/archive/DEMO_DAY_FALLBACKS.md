# SAED 2.0 — PROTOCOLO DE CONTINGENCIA (PLAN B) PARA DEMO DAY
## MATRIZ DE RESPUESTA OPERACIONAL ANTE INCIDENCIAS EN VIVO

---

### PRINCIPIOS INQUEBRANTABLES DE CONTINGENCIA
1. **No inventar código en vivo:** Nunca modificar código fuente, crear mocks de emergencia ni parchar componentes durante la presentación.
2. **No alterar producción:** No ejecutar DDL/DML de pánico ni reconfigurar variables de entorno en vivo.
3. **Apoyarse en mecanismos nativos existentes:** Todo fallo ambiental está previsto y cubierto por características que el sistema ya posee.

---

## MATRIZ DE CONTINGENCIA DETALLADA (7 CASOS PREVISTOS)

```
┌────────────────────────┬───────────────────────────────┬──────────────────────────────────────────┐
│ ESCENARIO DE FALLA     │ CAUSA RAÍZ                    │ ACCIÓN DE CONTINGENCIA INMEDIATA         │
├────────────────────────┼───────────────────────────────┼──────────────────────────────────────────┤
│ Caso 1: Cámara falla   │ Permiso denegado / conflicto  │ Usar mecanismo existente de token demo   │
│ Caso 2: Render frío    │ Hibernación de instancia nube │ Calentar backend previo a la sesión      │
│ Caso 3: QR tarda       │ Retardo en sensor óptico      │ Entrada manual o pegar desde portapapeles│
│ Caso 4: Wompi falla    │ Sandbox externo inactivo      │ No hacer pago real; mostrar conciliado   │
│ Caso 5: Notificación   │ Latencia transaccional        │ Esperar resultado real sin tocar código  │
│ Caso 6: Sesión expira  │ Token JWT caducado            │ Volver a login y continuar con el flujo  │
│ Caso 7: Internet cae   │ Corte general de conectividad │ Conmutar a stack local preparado         │
└────────────────────────┴───────────────────────────────┴──────────────────────────────────────────┘
```

---

### CASO 1: LA CÁMARA NO FUNCIONA EN PORTERÍA
- **Síntoma:** El navegador bloquea los permisos de la webcam o el dispositivo está ocupado por otra aplicación.
- **Acción:** Utilizar el mecanismo de entrada ya existente en la pantalla de portería: hacer clic en el botón táctico **"Pegar Portapapeles"** o digitar el token demo en el campo de texto disponible (`SAED-DEMO-QR-2026-TOKEN`).
- **Narrativa del Presentador:** *"La interfaz de garita cuenta con tolerancia a fallos de hardware: si el sensor óptico no responde, el guardia utiliza el lector de código de barras USB o el token digital de respaldo con la misma validación de seguridad."*

---

### CASO 2: RENDER ESTÁ FRÍO (COLD START)
- **Síntoma:** El primer request al backend en Render Cloud tarda entre 20 y 30 segundos debido a la hibernación de instancias de capa gratuita.
- **Acción:** Calentar el backend antes de iniciar la sesión (Pre-Warming) ejecutando una petición GET al endpoint de salud:
  ```bash
  curl -I https://sistema-administracion-edificios.onrender.com/api/v1/auth/health
  ```
- **Narrativa del Presentador (si ocurre en vivo):** *"La plataforma está inicializando el pool seguro de conexiones mTLS hacia la base de datos Oracle Autonomous en la nube. Observamos la pantalla de carga reactiva mientras se autentica la sesión."*

---

### CASO 3: EL ESCANEO DEL CÓDIGO QR TARDA O NO ENFOCA
- **Síntoma:** Mala iluminación o reflejo en la pantalla del visitante que dificulta la lectura del QR.
- **Acción:** Utilizar el flujo alternativo ya existente en la interfaz: digitar directamente el código alfanumérico o pegarlo desde el portapapeles.
- **Narrativa del Presentador:** *"El oficial dispone de modo de ingreso por código alternativo, asegurando que ningún visitante quede atascado en portería por fallas de lectura óptica."*

---

### CASO 4: LA PASARELA WOMPI NO RESPONDE O EL SANDBOX ESTÁ SATURADO
- **Síntoma:** La ventana modal de pago de Wompi muestra un spinner prolongado o error de pasarela externa.
- **Acción:** **NO intentar realizar un pago real.** Explicar el flujo arquitectónico y mostrar la evidencia ya existente y certificada en el dataset: señalar la **Unidad 102 (Ana Gómez)**, cuyo saldo es $0 COP gracias al pago y conciliación automática previa mediante webhook firmado criptográficamente.
- **Narrativa del Presentador:** *"Para demostrar la efectividad de la pasarela, observemos la Unidad 102: su obligación mensual fue saldada exitosamente a través del webhook seguro de Wompi, actualizando el balance contable a cero en tiempo real."*

---

### CASO 5: LA NOTIFICACIÓN DE PAQUETERÍA O VISITA TARDA UNOS SEGUNDOS
- **Síntoma:** La notificación en la campana o buzón del residente no aparece de inmediato.
- **Acción:** **Esperar el resultado real con calma.** No recargar compulsivamente ni modificar código. Refrescar la vista o esperar la sincronización habitual del backend.
- **Narrativa del Presentador:** *"El sistema procesa la transacción en base de datos garantizando atomicidad mediante el procedimiento almacenado antes de emitir la alerta al buzón del residente."*

---

### CASO 6: LA SESIÓN EXPIRA POR INACTIVIDAD
- **Síntoma:** Un endpoint retorna HTTP 401 Unauthorized y redirige al usuario a la pantalla de acceso.
- **Acción:** Volver a ingresar las credenciales del rol correspondiente en `/login` y continuar con naturalidad desde ese punto del flujo.
- **Narrativa del Presentador:** *"Observamos las políticas de expiración y seguridad de sesión: el token JWT protege los datos del copropietario ante inactividad prolongada."*

---

### CASO 7: INTERNET FALLA TOTALMENTE EN EL RECINTO
- **Síntoma:** Pérdida completa de conexión Wi-Fi o datos móviles en el auditorio.
- **Acción:** Continuar la presentación apoyándose en la explicación arquitectónica de diapositivas y conmutar al stack local previamente preparado en la laptop (`localhost:5173` para frontend y `localhost:8080` con Oracle XE local).
- **Narrativa del Presentador:** *"SAED 2.0 cuenta con diseño desacoplado listo para despliegue edge local, garantizando que una caída del proveedor de internet nunca interrumpa el control de acceso en la portería física."*
