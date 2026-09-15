# Documentación Técnica y Funcional - Fase D
## Módulo de Comunidad y Convivencia (SAED 2.0)

Este documento detalla la arquitectura, el diseño de seguridad y las funcionalidades implementadas en la **Fase D** del Sistema de Administración de Edificio Residencial (SAED).

---

## 1. Resumen Ejecutivo

La **Fase D** comprende el núcleo de interacción directa entre la administración y los residentes, gestionando la convivencia diaria y el cumplimiento de las normativas de la propiedad horizontal. Los módulos implementados son:

1. **Gestión de Sanciones y Debido Proceso:** Registro de multas y faltas al reglamento.
2. **Gestor Documental:** Repositorio centralizado de actas, normativas y circulares.
3. **Control de Obras y Remodelaciones:** Seguimiento de trabajos, fechas y depósitos de garantía.
4. **Bitácora de Incidentes:** Reporte de daños, novedades de seguridad y accidentes.

---

## 2. Arquitectura de Implementación

El desarrollo se llevó a cabo respetando el patrón de **Arquitectura Multicapa (Clean Architecture adaptada)**, asegurando un alto desacoplamiento y escalabilidad.

### 2.1 Backend (Spring Boot 3 + Java 17)
*   **Capa de Presentación (`Controller`):** Encargada exclusivamente del ruteo HTTP, parseo de JSON (usando `IncidenteDTO`, `ObraDTO`, etc.) y la validación perimetral de roles a través de las anotaciones `@PreAuthorize`.
*   **Capa de Negocio (`Service / ServiceImpl`):** Centraliza toda la lógica core. Aquí se inyecta el `SaedContextHolder` para recuperar el contexto del JWT (ID de Propiedad, ID de Unidad, Rol). Valida reglas estrictas como la prohibición de depósitos negativos o solapamiento de fechas.
*   **Capa de Persistencia (`Repository / RepositoryImpl`):** Implementada mediante **Spring JDBC Template** puro (`JdbcTemplate`). Se descartó ORM (Hibernate) en favor de consultas SQL parametrizadas nativas para maximizar el rendimiento y facilitar auditorías precisas sobre el aislamiento multi-tenant.

### 2.2 Frontend (React + Vite)
*   **Lazy Loading:** Integración mediante `React.lazy` en `App.jsx` para cargar las vistas (`ObrasAdminPage.jsx`, `IncidentesAdminPage.jsx`, etc.) bajo demanda, aligerando el peso del *bundle* inicial.
*   **Custom Hooks:** Manejo asíncrono optimizado a través del hook propio `useFetch`, que recibe promesas directas de la capa de red (`api.get`) para prevenir *memory leaks* en componentes desmontados.
*   **Componentes Protegidos:** Las rutas se envuelven en el componente `<ProtectedRoute>`, permitiendo renderizar distintas vistas dependiendo del rol (ej. `ADMINISTRADOR` vs `RESIDENTE`).

---

## 3. Funcionalidades por Módulo

### 3.1 Módulo de Sanciones
*   **Flujo de vida:** Permite iniciar un proceso sancionatorio, registrar descargos (Derecho al Debido Proceso) y finalmente emitir una resolución (Sanción Firme o Exoneración).
*   **Integración Financiera:** Al confirmar una sanción económica, el sistema asienta una "Multa" que impactará el estado de cuenta del residente.

### 3.2 Módulo Gestor Documental
*   **Clasificación:** Diferencia entre documentos organizacionales (accesibles en todas las copropiedades bajo la misma organización) y documentos locales (específicos de una unidad o propiedad).
*   **Control de Visibilidad:** Manejo de la bandera `ES_PUBLICO_RESIDENTES` para distinguir entre documentos de consumo masivo (Ej. Reglamento de Propiedad Horizontal) y archivos confidenciales de la administración.

### 3.3 Módulo de Obras y Remodelaciones
*   **Tracking de Tiempos y Dinero:** Registra `FECHA_INICIO`, `FECHA_FIN_ESTIMADA` y el `DEPOSITO_GARANTIA` retenido.
*   **Aprobaciones:** Residentes pueden solicitar obras desde su cuenta, pero la administración controla los cambios de estado (Aprobado, En Progreso, Finalizado, Cancelado).

### 3.4 Bitácora de Incidentes
*   **Clasificación de Severidad:** Niveles Baja, Moderada, Alta, y Crítica.
*   **Involucramiento de Autoridades:** Traqueo de si el evento requirió presencia de Policía, Ambulancia o Bomberos, guardando el registro oficial de la entidad.
*   **Cierre de Casos:** Obliga a redactar `CONCLUSIONES_CIERRE` al dar por terminado un evento.

---

## 4. Diseño de Seguridad y Aislamiento (RBAC y Multi-tenant)

La seguridad fue construida utilizando un enfoque *Shift-Left*, realizando auditorías de vulnerabilidad al código concurrente al desarrollo:

1.  **Mitigación de IDOR (Insecure Direct Object Reference):** 
    *   *Problema:* Un residente malintencionado podría enviar por POST un JSON alterado solicitando una obra a nombre de la unidad vecina (`{ idUnidad: 999 }`).
    *   *Solución:* Los servicios (`IncidenteServiceImpl`, `ObraServiceImpl`) ignoran el `idUnidad` enviado por el cliente si el usuario tiene rol de `RESIDENTE`. En su lugar, sobreescriben forzosamente el dato inyectando el valor extraído del JWT confiable.
2.  **Aislamiento de Propiedades:**
    *   Absolutamente todos los métodos de los repositorios inyectan obligatoriamente el `ID_PROPIEDAD` (ej. `SELECT * FROM INCIDENTES WHERE ID_PROPIEDAD = ?`). Se garantiza matemáticamente que los datos de un conjunto residencial no puedan filtrarse a otro, aun compartiendo la misma base de datos.
3.  **RBAC Estricto:**
    *   Los endpoints en Java exigen combinaciones específicas de roles: `hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')`. Acceder sin el privilegio emite un inmediato *403 Forbidden*.
