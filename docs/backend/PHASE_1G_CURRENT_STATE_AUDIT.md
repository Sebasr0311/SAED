# PHASE 1G - AUDITORÍA INICIAL Y ALCANCE

## 1. ESTADO DEL REPOSITORIO
- **main**: Sincronizado, limpio, incluye hasta Fase 1F (Visitas).
- **develop**: Sincronizado, limpio.
- **V3.9 - V4.4**: Inmutables y presentes.

## 2. DETERMINACIÓN DE LA FASE 1G
- Después de analizar el Roadmap, el esquema de BD (V3.9) y las rutas del frontend (App.jsx), el módulo que continúa orgánicamente las operaciones de portería (Fase 1F) es **Gestión de Paquetes y Correspondencia** (PAQUETES).
- Las páginas de UI correspondientes ya existen en versión mock: PaquetesAdminPage.jsx (para Porteros/Admins) y ResBuzonPage.jsx (para Residentes).
- La tabla PAQUETES interactúa directamente con PORTERIAS, UNIDADES, y PERSONAS.

## 3. IDENTIFICACIÓN DE BRECHA DE SEGURIDAD (CONTEXT BLEED)
- La tabla PAQUETES en V3.9 está protegida por POL_RLS_PROP_PAQUETES que utiliza FN_FILTRO_PROPIEDAD.
- Dado que un paquete está dirigido a una UNIDAD específica, usar el filtro de propiedad expone los paquetes de todas las unidades a cualquier residente del edificio (Context Bleed).
- **Acción a tomar en Fase 1G**: Crear V4.5__packages_rls_patch.sql para transferir la tabla PAQUETES a FN_FILTRO_UNIDAD. 

## 4. PLAN DE TRABAJO Y RESPONSABILIDADES
- **Sebasr0311**: Backend (DTO, Repo, Service, Controller).
- **srusso1**: DB (V4.5), RLS.
- **AnghelaD**: UI (conectar PaquetesAdminPage y ResBuzonPage al backend).
- **JoseReales-ui**: QA, Test de Integración RLS.
