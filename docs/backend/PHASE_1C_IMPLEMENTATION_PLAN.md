# PHASE 1C IMPLEMENTATION PLAN

## PASOS (SECUENCIAL)
1. **Entidades DTO y Excepciones:** Crear clases de respuesta y validación (DataIntegrity exceptions mapping).
2. **Repositorios JDBC:** Implementar los queries CRUD para Organizaciones, Propiedades, Unidades y Asignaciones.
3. **Validadores Lógicos (Services):** Implementar la lógica Anti-Spoofing y Anti-Privilege Escalation.
4. **Capa HTTP (Controllers):** Exponer los endpoints detallados en el API Contract.
5. **Pruebas Unitarias:** Mockito para validación de lógica de negocio (fechas, alcance vs IDs).
6. **Pruebas de Integración (Oracle):** Validar inserciones reales a la BD usando un esquema de prueba, forzando ORA-00001 (duplicados) y ORA-2290 (check constraints).

## STATUS DE DISEÑO
**PHASE 1C DESIGN STATUS: READY FOR IMPLEMENTATION**

No existen incompatibilidades identificadas con la base de datos Oracle V3.9. Spring Boot orquestará las validaciones, pero el motor transaccional de restricciones funcionará como un escudo físico inquebrantable.
