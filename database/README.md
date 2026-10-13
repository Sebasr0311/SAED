# SAED 2.0 — Base de Datos Relacional Oracle ATP

Este directorio contiene la **única fuente de verdad** del esquema, migraciones, políticas de seguridad (RLS/VPD), secuencias y datos semilla de **SAED 2.0**.

---

## 1. Estructura del Directorio

```text
database/
├── migrations/          # Migraciones evolutivas secuenciales (nombre estilo Flyway; se aplican a mano, sin tabla de historial)
│   ├── V5.0__master_baseline.sql
│   ├── V5.1__audit_rls_hardening.sql
│   ├── V5.2__fix_propiedades_rls_recursion.sql
│   ├── ...
│   └── V5.90__seed_modulo_incidentes.sql
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
- **Migraciones Incrementales:** `V5.1` a `V5.90` (la numeración tiene huecos intencionales). Resumen por rango:
  - `V5.1`-`V5.12`: endurecimiento de auditoría/RLS, CLOB para fotos de paquetería, tokens de activación, roles propietario/residente/`RESIDENTE_CONVIVENCIA`, intentos de PIN.
  - `V5.13`-`V5.30`: módulos y planes, pipelines documentales (contratos, paz y salvos, actas), pagos manuales, domicilios, QR atómico, incidentes, reglamentos, asambleas, pólizas, turnos de portería, cuerpo completo de `PKG_SAED_SECURITY_RLS`, depuración de usuarios demo.
  - `V5.31`-`V5.37`: alcance de reportes por tenant, deriva de esquema de arranque, onboarding, perfil de autenticación determinista, accesos de hogar (las migraciones `__sec_` las ejecuta el propietario del esquema de seguridad `SAED_SEC_MASTER`).
  - `V5.40`-`V5.43`, `V5.50`-`V5.59`: reapertura de incidentes, mantenimiento recurrente, inspección de obras, débito único de cuotas, conciliaciones únicas, políticas RLS por padre, desafíos de eliminación de propiedad, cierre de votaciones, asignaciones con propiedad.
  - `V5.60`-`V5.71`: intercom, política de reservas de zonas comunes, lecturas de comunicados, visitantes, RLS de portería, procedimientos de seguridad (`__sec_`), backfill de organización en personas, sanciones, alertas de pólizas.
  - `V5.80`, `V5.90`: reinicio de medidor de consumo y semilla del módulo de incidentes.
  - Los archivos `V3.9`-`V4.15` son historial previo a la línea base consolidada `V5.0`.

---

## 3. Políticas de Seguridad (Virtual Private Database / RLS)

Todas las tablas multi-tenant están protegidas mediante políticas RLS gobernadas por el contexto `SAED_CTX`:
- `ORGANIZACIONES` -> `FN_FILTRO_ORGANIZACION`
- `PROPIEDADES` -> `FN_FILTRO_PROPIEDAD`
- Tablas Operativas (`UNIDADES`, `USUARIOS`, `PAQUETES`, `VISITAS`, `RESERVAS`, `PQRS`, `PAGOS`, etc.) -> Segregadas por propiedad y organización activa.

---

## 4. Datos Semilla y Ejecución

1. **Despliegue de Esquema:**
   - Instalación nueva: ejecutar las migraciones en orden numérico ascendente (`V5.0` -> `V5.90`) en el esquema de la base de datos Oracle (Autonomous Database ATP / XE); las que llevan `__sec_` en el nombre se ejecutan con la cuenta propietaria del esquema de seguridad.
   - Base existente (remediación `V5.31` -> `V5.90`): seguir [`docs/remediation/production-migration-runbook.md`](../docs/remediation/production-migration-runbook.md) (respaldo previo, ensayo en staging, orden, marcadores por migración, verificación y reversión). No existe tabla de historial de migraciones; el runbook lista un marcador por migración para saber cuáles ya están aplicadas.
   - Nunca editar una migración ya aplicada: añadir una nueva.
2. **Carga de Semillas Demo:**
   Ejecutar `seeds/demo/V5.99__demo_seeds.sql` para poblar el conjunto de organizaciones, copropiedades y usuarios de prueba.
