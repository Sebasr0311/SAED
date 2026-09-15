# SAED 2.0 — Base de Datos Relacional Oracle ATP

Este directorio contiene la **única fuente de verdad** del esquema, migraciones, políticas de seguridad (RLS/VPD), secuencias y datos semilla de **SAED 2.0**.

---

## 1. Estructura del Directorio

```text
database/
├── migrations/          # Migraciones evolutivas secuenciales (Flyway format)
│   ├── V5.0__master_baseline.sql
│   ├── V5.1__audit_rls_hardening.sql
│   ├── V5.2__fix_propiedades_rls_recursion.sql
│   ├── ...
│   └── V5.12__paquetes_intentos_pin.sql
├── seeds/               # Datos semilla para pruebas, demo y configuración
│   ├── demo/            # Semillas para ambiente de demostración
│   │   └── V5.99__demo_seeds.sql
│   ├── test-data/       # Conjuntos de prueba automatizada
│   └── *.sql            # Scripts de datos masivos y parametrización inicial
└── README.md            # Este documento
```

---

## 2. Convención de Migraciones

Las migraciones siguen el estándar `V<Versión>__<Descripción>.sql`:
- **Línea Base Canónica:** `migrations/V5.0__master_baseline.sql` consolida las tablas multi-tenant, restricciones, paquetes de sesión (`PKG_SAED_SESSION`), paquetes de autenticación (`PKG_AUTH_BOOTSTRAP`), disparadores y políticas VPD (`DBMS_RLS`).
- **Migraciones Incrementales:** `V5.1` a `V5.12` aplican endurecimiento de seguridad, soporte CLOB para evidencias fotográficas de paquetería, segregación del rol `RESIDENTE_CONVIVENCIA`, tokens de activación de usuarios e integración con Wompi.

---

## 3. Políticas de Seguridad (Virtual Private Database / RLS)

Todas las tablas multi-tenant están protegidas mediante políticas RLS gobernadas por el contexto `SAED_CTX`:
- `ORGANIZACIONES` -> `FN_FILTRO_ORGANIZACION`
- `PROPIEDADES` -> `FN_FILTRO_PROPIEDAD`
- Tablas Operativas (`UNIDADES`, `USUARIOS`, `PAQUETES`, `VISITAS`, `RESERVAS`, `PQRS`, `PAGOS`, etc.) -> Segregadas por propiedad y organización activa.

---

## 4. Datos Semilla y Ejecución

1. **Despliegue de Esquema:**
   Ejecutar las migraciones en orden numérico ascendente (`V5.0` -> `V5.12`) en el esquema de la base de datos Oracle (Autonomous Database ATP / XE).
2. **Carga de Semillas Demo:**
   Ejecutar `seeds/demo/V5.99__demo_seeds.sql` para poblar el conjunto de organizaciones, copropiedades y usuarios de prueba.
