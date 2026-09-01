-- ============================================================================
-- SAED 2.0 - Migración V5.1: Blindaje RLS de AUDITORIA_LOG
-- Aplica aislamiento multi-tenant para lectura de logs de auditoría según rol y alcance.
-- ============================================================================

BEGIN
    BEGIN
        DBMS_RLS.DROP_POLICY(USER, 'AUDITORIA_LOG', 'POL_AUDITORIA_LOG_SELECT');
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;
    
    DBMS_RLS.ADD_POLICY(
        object_schema   => USER,
        object_name     => 'AUDITORIA_LOG',
        policy_name     => 'POL_AUDITORIA_LOG_SELECT',
        function_schema => USER,
        policy_function => 'PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD',
        statement_types => 'SELECT',
        enable          => TRUE
    );
END;
/
