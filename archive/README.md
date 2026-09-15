# SAED — Repositorio Histórico y Archivo (Legacy)

Este directorio contiene componentes, herramientas, scripts y módulos históricos de fases anteriores del proyecto SAED.

---

## 1. Estructura

```text
archive/
├── legacy/
│   ├── backend_legacy/              # Antiguo monolito de escritorio JavaFX (com.edificio:admin-residencial)
│   ├── database_v4_final_release/   # Volcados estáticos y scripts previos de la versión 4
│   ├── database_legacy/             # Scripts SQL de comprobación y parches temporales superados
│   ├── database_utilities/          # Clases Java utilitarias para migraciones manuales pasadas
│   └── database_docs/               # Documentación y planes de trabajo de etapas tempranas
├── scratch/                         # Scripts experimentales de auditoría y pruebas de fuzzing
└── README.md
```

---

## 2. Política de Conservación

- **Propósito:** Mantener la trazabilidad y memoria técnica sin contaminar el código activo de SAED 2.0.
- **Estado:** Código congelado, de solo lectura, excluido del runtime y del ciclo de integración continua (CI/CD).
