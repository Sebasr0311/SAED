# SAED 2.0 — Suites de Pruebas Automatizadas

Este directorio alberga las suites de pruebas integrales y de extremo a extremo (E2E) de **SAED 2.0**.

---

## 1. Estructura

```text
tests/
├── e2e/                # Pruebas End-to-End con Playwright (JavaScript)
│   ├── 00-smoke.spec.js
│   ├── 01-auth.spec.js
│   ├── ...
│   ├── 23-residente-perfil-conviviente.spec.js
│   └── helpers/auth.js
├── testsprite/         # Pruebas automatizadas generadas con TestSprite (Python)
│   ├── testsprite_superadmin_test.py
│   ├── testsprite_admin_organizacion_test.py
│   ├── testsprite_admin_propiedad_test.py
│   ├── testsprite_portero_test.py
│   ├── testsprite_residente_titular_test.py
│   ├── testsprite_conviviente_test.py
│   └── testsprite_paquetes_test.py
└── README.md
```

---

## 2. Ejecución de Pruebas E2E (Playwright)

Requiere el frontend y backend en ejecución (`http://localhost:5173` y `http://localhost:8080`).

```bash
# Ejecutar toda la suite E2E
npx playwright test tests/e2e/

# Ejecutar una prueba específica
npx playwright test tests/e2e/01-auth.spec.js
```

---

## 3. Ejecución de Pruebas TestSprite (Python)

```bash
# Requiere entorno virtual Python con dependencias instaladas
python -m pytest tests/testsprite/
```
