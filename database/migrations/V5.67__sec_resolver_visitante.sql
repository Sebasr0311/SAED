-- =============================================================================
-- SAED 2.0 - V5.67: VISITOR IDENTITY RESOLVER (security schema)
-- =============================================================================
-- RUN AS THE SECURITY SCHEMA OWNER (SAED_SEC_MASTER in production), not as the application schema owner.
-- The "__sec_" file-name marker tells the isolated installer to execute this file as that principal.
-- Requires V5.63 (VISITANTES scope columns) and V5.65 (grants).
--
-- A visitor is a person: UQ_VISITANTES_PERSONA is global (DEC-01, PERSONAS is global). The VISITANTES row-level
-- policy (V5.64) shows a caller only its own visitors (privacy between residents), so a caller that registers a
-- person that already is a visitor of another resident, another property, or an unclaimed legacy row cannot see that
-- row, inserted again and hit the unique key (409). Registration is therefore idempotent through this procedure:
--   * by person: returns the existing visitor id, or creates the visitor in the CONTEXT property registered by the
--     caller. Only the id is returned; nothing about the existing row (property, registrant) is disclosed.
--   * by visitor id: only a row nobody has claimed (ID_PROPIEDAD NULL, pre-V5.63 data) can be resolved, and it is then
--     adopted by the context property. A visitor that belongs to a property is never resolved by id here (the
--     application only reaches those through the row-level policy).
-- Session rules: authenticated tenant session (not anonymous/clearing/bootstrap) with a context property, role among
-- the gatehouse/household/administration roles (never SUPERADMIN, DEC-02).
-- Error codes: -20481 invalid context/arguments, -20482 role, -20483 unknown person.
-- Idempotent (CREATE OR REPLACE; synonyms and grants are repeatable).

DECLARE
    v_app_owner VARCHAR2(128);
    v_count     NUMBER;
BEGIN
    SELECT TABLE_OWNER INTO v_app_owner FROM USER_SYNONYMS WHERE SYNONYM_NAME = 'USUARIO_ASIGNACIONES';
    FOR t IN (SELECT COLUMN_VALUE AS NAME FROM TABLE(SYS.ODCIVARCHAR2LIST('VISITANTES', 'PERSONAS'))) LOOP
        SELECT COUNT(1) INTO v_count FROM USER_SYNONYMS WHERE SYNONYM_NAME = t.NAME;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SYNONYM ' || t.NAME || ' FOR '
                || DBMS_ASSERT.ENQUOTE_NAME(v_app_owner, FALSE) || '.' || t.NAME;
        END IF;
    END LOOP;
END;
/

CREATE OR REPLACE PROCEDURE SP_RESOLVER_VISITANTE(
    p_id_visitante     IN  NUMBER,
    p_id_persona       IN  NUMBER,
    p_es_frecuente     IN  VARCHAR2,
    p_empresa          IN  VARCHAR2,
    p_id_visitante_out OUT NUMBER
) AUTHID DEFINER AS
    v_state    VARCHAR2(30) := NVL(SYS_CONTEXT('SAED_CTX', 'STATE'), 'ANONYMOUS');
    v_rol      VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO');
    v_usr_txt  VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_USUARIO');
    v_prop_txt VARCHAR2(30) := SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD');
    v_usr      NUMBER;
    v_prop     NUMBER;
    v_id       NUMBER;
    v_prop_vis NUMBER;
    v_count    NUMBER;
BEGIN
    p_id_visitante_out := NULL;
    IF v_state IN ('ANONYMOUS', 'CLEARING', 'BOOTSTRAP') OR v_usr_txt IS NULL OR v_prop_txt IS NULL
       OR (p_id_visitante IS NULL AND p_id_persona IS NULL) THEN
        RAISE_APPLICATION_ERROR(-20481, 'Visitante: contexto o argumentos invalidos');
    END IF;
    IF v_rol IS NULL OR v_rol NOT IN ('RESIDENTE', 'PROPIETARIO', 'PROPIETARIO_UNIDAD', 'RESIDENTE_CONVIVENCIA',
                                      'PORTERO', 'ADMIN_PROPIEDAD', 'ADMIN_ORGANIZACION') THEN
        RAISE_APPLICATION_ERROR(-20482, 'Visitante: rol no autorizado');
    END IF;
    v_usr := TO_NUMBER(v_usr_txt);
    v_prop := TO_NUMBER(v_prop_txt);

    IF p_id_visitante IS NOT NULL THEN
        SELECT COUNT(1), MAX(ID_VISITANTE), MAX(ID_PROPIEDAD) INTO v_count, v_id, v_prop_vis
        FROM VISITANTES WHERE ID_VISITANTE = p_id_visitante;
        IF v_count = 0 THEN
            RETURN;
        END IF;
        IF v_prop_vis IS NOT NULL THEN
            RETURN;
        END IF;
    ELSE
        SELECT COUNT(1) INTO v_count FROM PERSONAS WHERE ID_PERSONA = p_id_persona;
        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20483, 'Visitante: persona inexistente');
        END IF;
        SELECT COUNT(1), MAX(ID_VISITANTE), MAX(ID_PROPIEDAD) INTO v_count, v_id, v_prop_vis
        FROM VISITANTES WHERE ID_PERSONA = p_id_persona;
        IF v_count = 0 THEN
            INSERT INTO VISITANTES (ID_PERSONA, ES_FRECUENTE, EMPRESA, ESTADO, ID_PROPIEDAD, REGISTRADO_POR)
            VALUES (p_id_persona, CASE WHEN p_es_frecuente = 'S' THEN 'S' ELSE 'N' END, p_empresa, 'ACTIVO', v_prop, v_usr)
            RETURNING ID_VISITANTE INTO v_id;
            p_id_visitante_out := v_id;
            RETURN;
        END IF;
    END IF;

    IF v_prop_vis IS NULL THEN
        UPDATE VISITANTES SET ID_PROPIEDAD = v_prop, REGISTRADO_POR = v_usr
        WHERE ID_VISITANTE = v_id AND ID_PROPIEDAD IS NULL;
    END IF;
    p_id_visitante_out := v_id;
END SP_RESOLVER_VISITANTE;
/

-- Same grantees as PKG_AUTH_BOOTSTRAP (the application principal of this installation).
BEGIN
    FOR g IN (SELECT DISTINCT GRANTEE FROM USER_TAB_PRIVS
              WHERE TABLE_NAME = 'PKG_AUTH_BOOTSTRAP' AND PRIVILEGE = 'EXECUTE') LOOP
        EXECUTE IMMEDIATE 'GRANT EXECUTE ON SP_RESOLVER_VISITANTE TO ' || DBMS_ASSERT.ENQUOTE_NAME(g.GRANTEE, FALSE);
    END LOOP;
END;
/
