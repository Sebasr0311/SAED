# 🔴 DEPLOYMENT READINESS AUDIT

## 1. Git
- **Rama actual:** main
- **Commit actual:** 0d87b99 (o posterior)
- **Tag v2.0.1:** Creado correctamente apuntando al commit de cierre.
- **Estado de main:** Limpio.
- **Estado:** PASS

## 2. Backend
- **Java/JDK:** 17 configurado en pom.xml.
- **Spring Boot / Maven:** Build exitoso mediante mvn clean package -DskipTests.
- **Configuracion:** application.yml lee variables de entorno correctamente para DB y JWT, pero hardcodea spring.mail.host: localhost. Render.yaml existe pero despliega backend y frontend simultáneamente, requiriendo actualización o ignorarse a favor de configuración Vercel.
- **Estado:** WARN (Faltan variables SMTP nativas en properties y desvincular Frontend del render.yaml)

## 3. Frontend
- **Node / npm / React / Vite:** Build de producción exitoso mediante 
pm run build.
- **Rutas API:** api.js lee correctamente VITE_API_BASE_URL, con fallback a localhost.
- **CORS / URLs:** Preparado para recibir URL de producción.
- **Estado:** PASS

## 4. Base de Datos (Oracle ATP)
- **Migraciones:** Scripts consolidados en modelo_relacional_v4_atp.sql.
- **Políticas RLS:** Definidas y verificadas en local.
- **Estado ATP Real:** BLOCKED. No se poseen credenciales (Wallet, Usuario DBA/Admin) para ejecutar los scripts en Oracle ATP Cloud ni URL de conexión de producción.

## 5. Plataformas de Despliegue (Vercel / Render)
- **Vercel CLI:** BLOCKED. No hay sesión autenticada ni token disponible en el entorno para desplegar autónomamente.
- **Render CLI:** BLOCKED. No instalado ni autenticado en el entorno.
- **Wompi / Correo:** BLOCKED. No se poseen llaves productivas o sandbox inyectadas en el sistema para configurar las plataformas.

## VEREDICTO
**BLOCKER**

El despliegue automático hacia Vercel, Render y Oracle ATP no puede proceder sin intervención humana para aprovisionar y proporcionar: 1. Tokens de CI/CD para Vercel y Render. 2. Credenciales y Wallet de la base de datos Oracle ATP de destino. 3. Claves de integración de Wompi y Brevo para inyectar como secretos en Render.