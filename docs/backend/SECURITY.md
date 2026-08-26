# SAED 2.0 Security Policies

## 1. Zero Trust Database
Ningún endpoint del backend asume privilegios sobre los datos transaccionales, sin importar qué tan fuerte sea la autenticación. Oracle RLS audita cada request físicamente.

## 2. API Security
- **JWT sin estado**: Evitamos vulnerabilidades de fijación de sesión o problemas de escalamiento.
- **BCrypt**: Contraseñas haseadas en base de datos.
- **Validaciones de Input**: Prevención de SQL Injection y validación estructural a través de Hibernate Validator y JSR-380.

## 3. Endpoints Públicos
- Solamente el login y endpoints básicos (`/api/v1/auth/login`) están abiertos. 
- Swagger (si se añade) sólo estará en entorno de desarrollo.

## 4. Auditoría Inmutable
Todo INSERT, UPDATE, DELETE es interceptado a nivel de base de datos. Modificar el Log lanza ORA-20099. El backend no puede "saltarse" o "apagar" este comportamiento.

## 5. Prevención de Riesgos de Despliegue
Cualquier archivo `.env` será bloqueado por `.gitignore`. 
El código de migración de base de datos no formará parte del runtime. No se ejecutarán sentencias DDL (ej. `ALTER TABLE`) desde el código de la aplicación.
