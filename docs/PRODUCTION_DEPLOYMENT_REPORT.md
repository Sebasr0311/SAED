# 🔴 SAED 2.0 — DEPLOYMENT BLOCKED

## ESTADO DE EJECUCIÓN
- **Qué ya fue completado:** Fase D1 (Auditoría Pre-Deploy). El repositorio Git está limpio (main, tag v2.0.1). El código backend compila exitosamente (Java 17, 73/73 tests). El frontend compila exitosamente (npm run build). Los scripts de migración Oracle (V4.x) están localizados y verificados estáticamente. Se actualizó la configuración de ender.yaml y preparativos de pplication.yml.
- **Qué NO se ejecutó:** Fases D2 a D10 (Preparación Oracle ATP, Migración de Base de Datos, Preparación y Deploy en Render, Deploy en Vercel, Integraciones Externas, QA End-to-End, Auditoría Final).

---

## DETALLE DE BLOQUEOS

### Bloqueo 1: Falta acceso a Oracle ATP de SAED 2.0
- **Recurso requerido:** Archivo Wallet (Wallet*.zip) correspondiente al entorno de Producción de SAED 2.0, y credenciales DBA (URL/TNS, Usuario, Contraseña).
- **Por qué es necesario:** La regla crítica prohíbe explícitamente usar la wallet legacy encontrada en ackend_legacy/src/main/resources/wallet.zip y prohíbe ejecutar DDL sin confirmar primero la identidad del esquema destino. Sin la wallet de SAED 2.0, es imposible conectar a la base de datos para la Fase D2.
- **Comando/acción que quedó pendiente:** Ejecución del script database/modelo_relacional_v4_atp.sql vía JDBC/SQLcl contra el entorno ATP.
- **Cómo reanudar:** Depositar la wallet correcta en el entorno y proporcionar (vía variables de entorno) las credenciales DBA del ATP destino.

### Bloqueo 2: Falta autorización/acceso a Render y Vercel
- **Recurso requerido:** Tokens de API CLI para Vercel (VERCEL_TOKEN) y Render (RENDER_API_KEY).
- **Por qué es necesario:** Para configurar y ejecutar el despliegue automático del backend y frontend desde este entorno hacia las nubes productivas sin intervención manual de UI.
- **Comando/acción que quedó pendiente:** 
px vercel --prod y configuración remota del servicio en Render.
- **Cómo reanudar:** Autenticar los CLIs en el entorno local o inyectar los tokens de acceso CI/CD como variables de entorno.

### Bloqueo 3: Credenciales Externas Faltantes (Wompi, Correo)
- **Recurso requerido:** WOMPI_PUBLIC_KEY, WOMPI_PRIVATE_KEY, WOMPI_EVENTS_SECRET, SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD.
- **Por qué es necesario:** Las integraciones fallarán en producción o en el smoke test (QA End-to-End, Fase D9) impidiendo la validación requerida por la regla Anti-Falso-Pass.
- **Comando/acción que quedó pendiente:** Inyección de secretos en los entornos de Render y Vercel.
- **Cómo reanudar:** Suministrar las credenciales de negocio mediante el sistema seguro del entorno para que sean inyectadas en los servicios Cloud durante las fases D4 y D6.

---

## VEREDICTO FINAL
**🔴 SAED 2.0 — DEPLOYMENT BLOCKED**

El proceso se detiene estrictamente respetando los criterios de bloqueo definidos en la Directiva Maestra.