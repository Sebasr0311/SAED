-- ==============================================================================
-- V5.13: Catálogo Canónico de Módulos y Matriz de Entitlements (PLAN_MODULOS)
-- Cierre de GAP-ENT-03 (P1) — Entitlements de Módulos SAED 2.0
-- ==============================================================================

-- 1. Catálogo canónico de Módulos
MERGE INTO MODULOS m USING (
    SELECT 'ASAMBLEAS' AS CODIGO, 'Asambleas y Votaciones Ley 675' AS NOMBRE, 'Gestión de asambleas, votaciones en tiempo real y quórum con coeficientes.' AS DESCRIPCION FROM DUAL UNION ALL
    SELECT 'OBRAS', 'Gestión de Obras y Reformas', 'Seguimiento, aprobación y control de obras y reformas en unidades privadas.' FROM DUAL UNION ALL
    SELECT 'POLIZAS', 'Pólizas de Seguro', 'Control de coberturas, vencimientos y pólizas de seguro de copropiedad.' FROM DUAL UNION ALL
    SELECT 'RESERVAS', 'Reservas de Zonas Comunes', 'Gestión, disponibilidad y reservas de zonas comunes y amenidades.' FROM DUAL UNION ALL
    SELECT 'PAQUETES', 'Paquetería y Correspondencia', 'Custodia de paquetes con PIN de seguridad de 6 dígitos.' FROM DUAL UNION ALL
    SELECT 'PARQUEADEROS', 'Control de Parqueaderos', 'Control de bahías de visitantes y asignación vehicular.' FROM DUAL UNION ALL
    SELECT 'PQRS', 'PQRS y Convivencia', 'Radicación y seguimiento de peticiones, quejas, reclamos y solicitudes.' FROM DUAL UNION ALL
    SELECT 'FINANZAS', 'Finanzas y Pagos', 'Emisión de cuotas, recaudos, conciliación y pasarela de pago.' FROM DUAL
) s ON (m.CODIGO = s.CODIGO)
WHEN MATCHED THEN
    UPDATE SET m.NOMBRE = s.NOMBRE, m.DESCRIPCION = s.DESCRIPCION
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, DESCRIPCION)
    VALUES (s.CODIGO, s.NOMBRE, s.DESCRIPCION);

-- 2. Matriz de Entitlements por Plan (FREE=1, PRO=2, ENTERPRISE=3)
MERGE INTO PLAN_MODULOS pm USING (
    -- Plan 1: FREE (Básico / Gratuito - Módulos operativos avanzados deshabilitados)
    SELECT 1 AS ID_PLAN, m.ID_MODULO, 'N' AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('ASAMBLEAS', 'OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS')
    UNION ALL
    -- Plan 2: PRO (Profesional - Incluye reservas, obras, pólizas, paquetería, finanzas, parqueaderos, pqrs; Asambleas es Enterprise-only)
    SELECT 2 AS ID_PLAN, m.ID_MODULO, 'N' AS HABILITADO FROM MODULOS m WHERE m.CODIGO = 'ASAMBLEAS'
    UNION ALL
    SELECT 2 AS ID_PLAN, m.ID_MODULO, 'S' AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS')
    UNION ALL
    -- Plan 3: ENTERPRISE (Empresarial - Suite completa habilitada, incluyendo Asambleas Ley 675)
    SELECT 3 AS ID_PLAN, m.ID_MODULO, 'S' AS HABILITADO FROM MODULOS m WHERE m.CODIGO IN ('ASAMBLEAS', 'OBRAS', 'POLIZAS', 'RESERVAS', 'PAQUETES', 'PARQUEADEROS', 'PQRS', 'FINANZAS')
) s ON (pm.ID_PLAN = s.ID_PLAN AND pm.ID_MODULO = s.ID_MODULO)
WHEN MATCHED THEN
    UPDATE SET pm.HABILITADO = s.HABILITADO
WHEN NOT MATCHED THEN
    INSERT (ID_PLAN, ID_MODULO, HABILITADO)
    VALUES (s.ID_PLAN, s.ID_MODULO, s.HABILITADO);

COMMIT;
