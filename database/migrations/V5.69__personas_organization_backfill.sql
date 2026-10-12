-- =============================================================================
-- SAED 2.0 - V5.69: BACK-FILL PERSONAS.ID_ORGANIZACION FROM THE PERSON'S LINKS
-- =============================================================================
-- DEC-01: PERSONAS has no row-level policy; a person is scoped in the application by PERSONAS.ID_ORGANIZACION.
-- Persons created through the account registration, cohabitant registration, visitor pre-registration and walk-in
-- visitor flows were stored with ID_ORGANIZACION = NULL. The application now binds the organization of the server-side
-- context on every insert; this migration repairs the rows that already exist.
--
-- Rule (deterministic, plain UPDATEs, only rows with ID_ORGANIZACION IS NULL, never overwrites a value):
--   The links of a person are read in three tiers, strongest first:
--     1. account assignments   USUARIOS -> USUARIO_ASIGNACIONES.ID_ORGANIZACION
--     2. housing               RESIDENTES_UNIDAD / PROPIETARIOS_UNIDAD -> UNIDADES -> PROPIEDADES.ID_ORGANIZACION
--     3. visits                VISITANTES.ID_PROPIEDAD and VISITANTES -> VISITAS -> UNIDADES -> PROPIEDADES
--   The first tier that has any link decides. The person receives the organization of that tier only when the tier
--   points to exactly ONE distinct organization. A tier that points to several organizations is ambiguous: the person
--   stays NULL and lower tiers are NOT consulted (a weaker link never overrides a stronger ambiguous one).
--   Persons without any link stay NULL as well (nothing to infer from).
-- Application behaviour for the rows left NULL: the person queries (PersonaRepositoryImpl) admit a person to an
-- organization-scoped caller only through PERSONAS.ID_ORGANIZACION or through an assignment, housing or ownership link
-- to a property of that organization, so an ambiguous person is still reachable by every organization it is linked to
-- and an unlinked NULL person is reachable by none (it is never guessed into an organization).
--
-- Idempotent: only NULL rows are touched, so a second run changes nothing new. Plain UPDATEs (no MERGE on tables
-- under policies); the organization comes from the data dictionary of the tenant tables, never from input.

BEGIN
    PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1);
    PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');
END;
/

-- Tier 1: account assignments.
UPDATE PERSONAS p
SET p.ID_ORGANIZACION = (
    SELECT MIN(ua.ID_ORGANIZACION)
    FROM USUARIOS u JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
    WHERE u.ID_PERSONA = p.ID_PERSONA AND ua.ID_ORGANIZACION IS NOT NULL)
WHERE p.ID_ORGANIZACION IS NULL
  AND (SELECT COUNT(DISTINCT ua.ID_ORGANIZACION)
       FROM USUARIOS u JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
       WHERE u.ID_PERSONA = p.ID_PERSONA AND ua.ID_ORGANIZACION IS NOT NULL) = 1;

-- Tier 2: housing, only for persons without any account assignment.
UPDATE PERSONAS p
SET p.ID_ORGANIZACION = (
    SELECT MIN(h.ID_ORGANIZACION) FROM (
        SELECT ru.ID_PERSONA, pr.ID_ORGANIZACION
        FROM RESIDENTES_UNIDAD ru
        JOIN UNIDADES un ON un.ID_UNIDAD = ru.ID_UNIDAD
        JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD
        UNION
        SELECT pu.ID_PERSONA, pr.ID_ORGANIZACION
        FROM PROPIETARIOS_UNIDAD pu
        JOIN UNIDADES un ON un.ID_UNIDAD = pu.ID_UNIDAD
        JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD) h
    WHERE h.ID_PERSONA = p.ID_PERSONA)
WHERE p.ID_ORGANIZACION IS NULL
  AND NOT EXISTS (SELECT 1 FROM USUARIOS u JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
                  WHERE u.ID_PERSONA = p.ID_PERSONA AND ua.ID_ORGANIZACION IS NOT NULL)
  AND (SELECT COUNT(DISTINCT h.ID_ORGANIZACION) FROM (
           SELECT ru.ID_PERSONA, pr.ID_ORGANIZACION
           FROM RESIDENTES_UNIDAD ru
           JOIN UNIDADES un ON un.ID_UNIDAD = ru.ID_UNIDAD
           JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD
           UNION
           SELECT pu.ID_PERSONA, pr.ID_ORGANIZACION
           FROM PROPIETARIOS_UNIDAD pu
           JOIN UNIDADES un ON un.ID_UNIDAD = pu.ID_UNIDAD
           JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD) h
       WHERE h.ID_PERSONA = p.ID_PERSONA) = 1;

-- Tier 3: visits, only for persons without an account assignment or housing link.
UPDATE PERSONAS p
SET p.ID_ORGANIZACION = (
    SELECT MIN(vo.ID_ORGANIZACION) FROM (
        SELECT v.ID_PERSONA, pr.ID_ORGANIZACION
        FROM VISITANTES v
        JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = v.ID_PROPIEDAD
        UNION
        SELECT v.ID_PERSONA, pr.ID_ORGANIZACION
        FROM VISITANTES v
        JOIN VISITAS vi ON vi.ID_VISITANTE = v.ID_VISITANTE
        JOIN UNIDADES un ON un.ID_UNIDAD = vi.ID_UNIDAD
        JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD) vo
    WHERE vo.ID_PERSONA = p.ID_PERSONA)
WHERE p.ID_ORGANIZACION IS NULL
  AND NOT EXISTS (SELECT 1 FROM USUARIOS u JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
                  WHERE u.ID_PERSONA = p.ID_PERSONA AND ua.ID_ORGANIZACION IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = p.ID_PERSONA)
  AND NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = p.ID_PERSONA)
  AND (SELECT COUNT(DISTINCT vo.ID_ORGANIZACION) FROM (
           SELECT v.ID_PERSONA, pr.ID_ORGANIZACION
           FROM VISITANTES v
           JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = v.ID_PROPIEDAD
           UNION
           SELECT v.ID_PERSONA, pr.ID_ORGANIZACION
           FROM VISITANTES v
           JOIN VISITAS vi ON vi.ID_VISITANTE = v.ID_VISITANTE
           JOIN UNIDADES un ON un.ID_UNIDAD = vi.ID_UNIDAD
           JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = un.ID_PROPIEDAD) vo
       WHERE vo.ID_PERSONA = p.ID_PERSONA) = 1;

COMMIT;
